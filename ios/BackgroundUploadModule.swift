import Foundation
import React

@objc(BackgroundUploadModule)
class BackgroundUploadModule: RCTEventEmitter, URLSessionTaskDelegate, URLSessionDelegate {
  private static let progressEvent = "BackgroundUploadProgress"
  private static let completionEvent = "BackgroundUploadCompletion"
  private static let pendingCompletionsKey = "BackgroundUploadModulePendingCompletions"
  private static let completionHandlerQueue = DispatchQueue(label: "com.easy.bg.completion-handler-queue")
  private static var completionHandlers: [String: () -> Void] = [:]

  private let stateQueue = DispatchQueue(label: "com.easy.bg.state-queue", attributes: .concurrent)
  private var hasListeners = false
  private var uploadIdByTaskIdentifier: [Int: String] = [:]
  private var taskIdentifierByUploadId: [String: Int] = [:]
  private var completedUploadIds: Set<String> = []

  private lazy var backgroundSession: URLSession = {
    let config = URLSessionConfiguration.background(withIdentifier: Self.backgroundSessionIdentifier())
    config.sessionSendsLaunchEvents = true
    config.isDiscretionary = false
    config.waitsForConnectivity = true
    config.httpMaximumConnectionsPerHost = 1
    return URLSession(configuration: config, delegate: self, delegateQueue: nil)
  }()

  override init() {
    super.init()
    reattachExistingTasks()
  }

  override static func requiresMainQueueSetup() -> Bool {
    false
  }

  override func supportedEvents() -> [String]! {
    [Self.progressEvent, Self.completionEvent]
  }

  override func startObserving() {
    stateQueue.async(flags: .barrier) {
      self.hasListeners = true
    }
    flushPendingCompletionEvents()
  }

  override func stopObserving() {
    stateQueue.async(flags: .barrier) {
      self.hasListeners = false
    }
  }

  @objc(startUpload:resolver:rejecter:)
  func startUpload(
    _ options: [String: Any],
    resolver resolve: @escaping RCTPromiseResolveBlock,
    rejecter reject: @escaping RCTPromiseRejectBlock
  ) {
    do {
      let uploadId = try requiredString(options: options, key: "uploadId")
      let fileUri = try requiredString(options: options, key: "fileUri")
      let uploadUrl = try requiredString(options: options, key: "uploadUrl")
      let contentType = try requiredString(options: options, key: "contentType")

      guard let fileURL = URL(string: fileUri), fileURL.isFileURL else {
        throw UploadError.invalidParameter("fileUri must be a valid file URL.")
      }
      guard FileManager.default.fileExists(atPath: fileURL.path) else {
        throw UploadError.invalidParameter("File does not exist at path: \(fileURL.path)")
      }
      guard let destinationURL = URL(string: uploadUrl) else {
        throw UploadError.invalidParameter("uploadUrl must be a valid URL.")
      }

      var request = URLRequest(url: destinationURL)
      request.httpMethod = "PUT"
      request.timeoutInterval = 60
      request.setValue(contentType, forHTTPHeaderField: "Content-Type")

      stateQueue.async(flags: .barrier) {
        self.completedUploadIds.remove(uploadId)
      }
      let task = backgroundSession.uploadTask(with: request, fromFile: fileURL)
      task.taskDescription = uploadId
      setTaskMapping(uploadId: uploadId, taskIdentifier: task.taskIdentifier)
      task.resume()
      resolve(nil)
    } catch let error as UploadError {
      reject("E_START_UPLOAD", error.localizedDescription, nil)
    } catch {
      reject("E_START_UPLOAD", error.localizedDescription, error)
    }
  }

  @objc(cancelUpload:resolver:rejecter:)
  func cancelUpload(
    _ uploadId: String,
    resolver resolve: @escaping RCTPromiseResolveBlock,
    rejecter reject: @escaping RCTPromiseRejectBlock
  ) {
    guard !uploadId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
      reject("E_CANCEL_UPLOAD", "uploadId must be a non-empty string.", nil)
      return
    }

    let taskIdentifier = stateQueue.sync { taskIdentifierByUploadId[uploadId] }
    guard let targetTaskIdentifier = taskIdentifier else {
      emitCompletion(uploadId: uploadId, status: "cancelled", error: nil)
      resolve(nil)
      return
    }

    backgroundSession.getAllTasks { tasks in
      if let task = tasks.first(where: { $0.taskIdentifier == targetTaskIdentifier }) {
        task.cancel()
      }
      self.emitCompletion(uploadId: uploadId, status: "cancelled", error: nil)
      self.removeTaskMapping(taskIdentifier: targetTaskIdentifier)
      resolve(nil)
    }
  }

  func urlSession(
    _ session: URLSession,
    task: URLSessionTask,
    didSendBodyData bytesSent: Int64,
    totalBytesSent: Int64,
    totalBytesExpectedToSend: Int64
  ) {
    guard let uploadId = uploadId(for: task) else {
      return
    }
    let progress: Double
    if totalBytesExpectedToSend > 0 {
      progress = min(max(Double(totalBytesSent) / Double(totalBytesExpectedToSend), 0.0), 1.0)
    } else {
      progress = 0.0
    }

    emitProgress(
      uploadId: uploadId,
      bytesSent: totalBytesSent,
      totalBytes: totalBytesExpectedToSend,
      progress: progress
    )
  }

  func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
    guard let uploadId = uploadId(for: task) else {
      return
    }

    defer {
      removeTaskMapping(taskIdentifier: task.taskIdentifier)
    }

    if let nsError = error as NSError? {
      if nsError.domain == NSURLErrorDomain && nsError.code == NSURLErrorCancelled {
        emitCompletion(uploadId: uploadId, status: "cancelled", error: nil)
        return
      }
      emitCompletion(uploadId: uploadId, status: "error", error: nsError.localizedDescription)
      return
    }

    if let response = task.response as? HTTPURLResponse, !(200...299).contains(response.statusCode) {
      emitCompletion(uploadId: uploadId, status: "error", error: "HTTP \(response.statusCode)")
      return
    }

    emitCompletion(uploadId: uploadId, status: "success", error: nil)
  }

  func urlSessionDidFinishEvents(forBackgroundURLSession session: URLSession) {
    let identifier = session.configuration.identifier
    let completion = Self.consumeCompletionHandler(for: identifier)
    DispatchQueue.main.async {
      completion?()
    }
  }

  @objc(handleEventsForBackgroundURLSession:completionHandler:)
  public static func handleEventsForBackgroundURLSession(
    _ identifier: String,
    completionHandler: @escaping () -> Void
  ) {
    completionHandlerQueue.async {
      completionHandlers[identifier] = completionHandler
    }
  }

  private static func consumeCompletionHandler(for identifier: String?) -> (() -> Void)? {
    guard let identifier else {
      return nil
    }

    var handler: (() -> Void)?
    completionHandlerQueue.sync {
      handler = completionHandlers.removeValue(forKey: identifier)
    }
    return handler
  }

  private static func backgroundSessionIdentifier() -> String {
    let bundle = Bundle.main.bundleIdentifier ?? "com.easy.bg.default"
    return "\(bundle).background-upload-module"
  }

  private func emitProgress(uploadId: String, bytesSent: Int64, totalBytes: Int64, progress: Double) {
    let payload: [String: Any] = [
      "uploadId": uploadId,
      "bytesSent": Double(bytesSent),
      "totalBytes": Double(totalBytes),
      "progress": progress
    ]
    sendEventSafely(name: Self.progressEvent, body: payload, persistWhenUnavailable: false)
  }

  private func emitCompletion(uploadId: String, status: String, error: String?) {
    let shouldEmit = stateQueue.sync(flags: .barrier) { () -> Bool in
      if completedUploadIds.contains(uploadId) {
        return false
      }
      completedUploadIds.insert(uploadId)
      return true
    }
    if !shouldEmit {
      return
    }

    var payload: [String: Any] = [
      "uploadId": uploadId,
      "status": status
    ]
    if let error {
      payload["error"] = error
    }
    sendEventSafely(name: Self.completionEvent, body: payload, persistWhenUnavailable: true)
  }

  private func sendEventSafely(name: String, body: [String: Any], persistWhenUnavailable: Bool) {
    let canSend = stateQueue.sync { hasListeners } && bridge != nil
    if canSend {
      DispatchQueue.main.async {
        self.sendEvent(withName: name, body: body)
      }
      return
    }
    if persistWhenUnavailable && name == Self.completionEvent {
      persistPendingCompletion(body)
    }
  }

  private func persistPendingCompletion(_ completion: [String: Any]) {
    stateQueue.async(flags: .barrier) {
      var existing = UserDefaults.standard.array(forKey: Self.pendingCompletionsKey) as? [[String: Any]] ?? []
      existing.append(completion)
      if existing.count > 50 {
        existing = Array(existing.suffix(50))
      }
      UserDefaults.standard.set(existing, forKey: Self.pendingCompletionsKey)
    }
  }

  private func flushPendingCompletionEvents() {
    stateQueue.async(flags: .barrier) {
      let events = UserDefaults.standard.array(forKey: Self.pendingCompletionsKey) as? [[String: Any]] ?? []
      guard !events.isEmpty else {
        return
      }
      UserDefaults.standard.removeObject(forKey: Self.pendingCompletionsKey)
      DispatchQueue.main.async {
        for event in events {
          self.sendEvent(withName: Self.completionEvent, body: event)
        }
      }
    }
  }

  private func reattachExistingTasks() {
    backgroundSession.getAllTasks { tasks in
      self.stateQueue.async(flags: .barrier) {
        for task in tasks {
          guard let uploadId = task.taskDescription, !uploadId.isEmpty else {
            continue
          }
          self.uploadIdByTaskIdentifier[task.taskIdentifier] = uploadId
          self.taskIdentifierByUploadId[uploadId] = task.taskIdentifier
        }
      }
    }
  }

  private func setTaskMapping(uploadId: String, taskIdentifier: Int) {
    stateQueue.async(flags: .barrier) {
      self.taskIdentifierByUploadId[uploadId] = taskIdentifier
      self.uploadIdByTaskIdentifier[taskIdentifier] = uploadId
    }
  }

  private func removeTaskMapping(taskIdentifier: Int) {
    stateQueue.async(flags: .barrier) {
      if let uploadId = self.uploadIdByTaskIdentifier.removeValue(forKey: taskIdentifier) {
        self.taskIdentifierByUploadId.removeValue(forKey: uploadId)
      }
    }
  }

  private func uploadId(for task: URLSessionTask) -> String? {
    if let description = task.taskDescription, !description.isEmpty {
      return description
    }
    return stateQueue.sync { uploadIdByTaskIdentifier[task.taskIdentifier] }
  }

  private func requiredString(options: [String: Any], key: String) throws -> String {
    guard let value = options[key] as? String else {
      throw UploadError.invalidParameter("Missing required field: \(key)")
    }
    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else {
      throw UploadError.invalidParameter("Field \(key) must be a non-empty string.")
    }
    return trimmed
  }

  private enum UploadError: LocalizedError {
    case invalidParameter(String)

    var errorDescription: String? {
      switch self {
      case let .invalidParameter(message):
        return message
      }
    }
  }
}
