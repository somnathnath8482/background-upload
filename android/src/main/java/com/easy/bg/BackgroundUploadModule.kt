package com.easy.bg

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.module.annotations.ReactModule

@ReactModule(name = BackgroundUploadModule.NAME)
class BackgroundUploadModule(
  reactContext: ReactApplicationContext
) : NativeBackgroundUploadModuleSpec(reactContext) {

  private val appContext = reactContext
  private val workManager = WorkManager.getInstance(reactContext)
  private var listenerCount = 0

  init {
    UploadEventEmitter.attachReactContext(appContext)
  }

  override fun getName(): String = NAME

  override fun startUpload(options: ReadableMap, promise: Promise) {
    try {
      val uploadId = getRequiredString(options, UploadWorker.KEY_UPLOAD_ID)
      val fileUri = getRequiredString(options, UploadWorker.KEY_FILE_URI)
      val uploadUrl = getRequiredString(options, UploadWorker.KEY_UPLOAD_URL)
      val contentType = getRequiredString(options, UploadWorker.KEY_CONTENT_TYPE)

      val data = Data.Builder()
        .putString(UploadWorker.KEY_UPLOAD_ID, uploadId)
        .putString(UploadWorker.KEY_FILE_URI, fileUri)
        .putString(UploadWorker.KEY_UPLOAD_URL, uploadUrl)
        .putString(UploadWorker.KEY_CONTENT_TYPE, contentType)
        .build()

      val request = OneTimeWorkRequestBuilder<UploadWorker>()
        .setInputData(data)
        .addTag(uploadTag(uploadId))
        .build()

      UploadEventEmitter.resetUpload(uploadId)
      workManager.enqueueUniqueWork(workName(uploadId), ExistingWorkPolicy.REPLACE, request)
      promise.resolve(null)
    } catch (error: Throwable) {
      promise.reject("E_START_UPLOAD", error.message, error)
    }
  }

  override fun cancelUpload(uploadId: String, promise: Promise) {
    if (uploadId.isBlank()) {
      promise.reject("E_CANCEL_UPLOAD", "uploadId must be a non-empty string")
      return
    }

    try {
      workManager.cancelUniqueWork(workName(uploadId))
      UploadEventEmitter.emitCompletion(uploadId, "cancelled")
      promise.resolve(null)
    } catch (error: Throwable) {
      promise.reject("E_CANCEL_UPLOAD", error.message, error)
    }
  }

  override fun addListener(eventName: String) {
    listenerCount += 1
  }

  override fun removeListeners(count: Double) {
    listenerCount = (listenerCount - count.toInt()).coerceAtLeast(0)
  }

  override fun invalidate() {
    UploadEventEmitter.detachReactContext(appContext)
    super.invalidate()
  }

  private fun getRequiredString(options: ReadableMap, key: String): String {
    if (!options.hasKey(key)) {
      throw IllegalArgumentException("Missing required field: $key")
    }

    val value = options.getString(key)
    if (value.isNullOrBlank()) {
      throw IllegalArgumentException("Field \"$key\" must be a non-empty string")
    }

    return value
  }

  private fun workName(uploadId: String): String = "$WORK_NAME_PREFIX.$uploadId"
  private fun uploadTag(uploadId: String): String = "$WORK_TAG_PREFIX.$uploadId"

  companion object {
    const val NAME = "BackgroundUploadModule"
    private const val WORK_NAME_PREFIX = "background_upload_work"
    private const val WORK_TAG_PREFIX = "background_upload_tag"
  }
}
