# background-upload

Production-ready React Native native module for background file uploads to presigned `PUT` URLs.

## Features

- TurboModule-ready (`codegenConfig` + typed `NativeBackgroundUploadModule` spec)
- Android + iOS native implementation
- Background upload support
  - Android: `WorkManager` + foreground notification
  - iOS: `URLSessionConfiguration.background`
- Streaming upload (no full-file memory buffering)
- Progress + completion events
- Cancellation support
- No retry, no resume, no reboot persistence
- Strict header behavior (`Content-Type` only)

## JS API

```ts
type StartUploadOptions = {
  uploadId: string;
  fileUri: string;
  uploadUrl: string;
  contentType: string;
};

startUpload(options: StartUploadOptions): Promise<void>
cancelUpload(uploadId: string): Promise<void>
addListener(event, callback): EmitterSubscription
```

Event names:
- `BackgroundUploadProgress`
- `BackgroundUploadCompletion`

## Setup

### 1) Install the package

```bash
npm install background-upload
```

### 2) iOS: install pods

```bash
cd ios && pod install
```

### 3) iOS: forward background URLSession callbacks

In your app's `AppDelegate`, forward `handleEventsForBackgroundURLSession`:

```swift
func application(
  _ application: UIApplication,
  handleEventsForBackgroundURLSession identifier: String,
  completionHandler: @escaping () -> Void
) {
  BackgroundUploadModule.handleEventsForBackgroundURLSession(
    identifier,
    completionHandler: completionHandler
  )
}
```

### 4) Android: runtime notification permission (Android 13+)

Request `POST_NOTIFICATIONS` permission in app code to ensure foreground progress notifications are visible.

## Example usage

```ts
import {
  UPLOAD_COMPLETION_EVENT,
  UPLOAD_PROGRESS_EVENT,
  addListener,
  cancelUpload,
  startUpload,
} from 'background-upload';

const progressSub = addListener(UPLOAD_PROGRESS_EVENT, event => {
  console.log(event.uploadId, event.progress);
});

const completionSub = addListener(UPLOAD_COMPLETION_EVENT, event => {
  console.log(event.uploadId, event.status, event.error);
});

await startUpload({
  uploadId: 'my-upload-1',
  fileUri: 'file:///absolute/path/to/file.jpg',
  uploadUrl: 'https://presigned-put-url',
  contentType: 'image/jpeg',
});

// Later:
await cancelUpload('my-upload-1');

progressSub.remove();
completionSub.remove();
```
