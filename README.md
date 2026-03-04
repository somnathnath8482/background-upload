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
  requestNotificationPermissionOnStart?: boolean; // Android only, default true
};

startUpload(options: StartUploadOptions): Promise<void>
cancelUpload(uploadId: string): Promise<void>
addListener(event, callback): EmitterSubscription
requestNotificationPermission(): Promise<'granted' | 'denied' | 'never_ask_again' | 'unavailable'>
getNotificationPermissionStatus(): Promise<'granted' | 'denied' | 'never_ask_again' | 'unavailable'>
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
import React from 'react';
import {
  Button,
  type EmitterSubscription,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import * as BackgroundUploadImport from 'background-upload';
import { pickMedia } from './helper';

type BackgroundUploadApi = {
  UPLOAD_COMPLETION_EVENT: string;
  UPLOAD_PROGRESS_EVENT: string;
  addListener: (event: string, callback: (event: any) => void) => EmitterSubscription;
  cancelUpload: (uploadId: string) => Promise<void>;
  getNotificationPermissionStatus: () => Promise<string>;
  requestNotificationPermission: () => Promise<string>;
  startUpload: (options: {
    uploadId: string;
    fileUri: string;
    uploadUrl: string;
    contentType: string;
    requestNotificationPermissionOnStart?: boolean;
  }) => Promise<void>;
};

const BackgroundUpload: BackgroundUploadApi | undefined = (
  (BackgroundUploadImport as any).addListener
    ? BackgroundUploadImport
    : (BackgroundUploadImport as any).default
) as BackgroundUploadApi | undefined;

const DEFAULT_UPLOAD_ID = `upload-${Date.now()}`;

export default function App() {
  const [uploadId, setUploadId] = React.useState(DEFAULT_UPLOAD_ID);
  const [fileUri, setFileUri] = React.useState('');
  const [uploadUrl, setUploadUrl] = React.useState('');
  const [contentType, setContentType] = React.useState('');
  const [statusText, setStatusText] = React.useState('Idle');

  React.useEffect(() => {
    if (!BackgroundUpload) {
      setStatusText('BackgroundUpload API not found in JS import.');
      return;
    }

    void (async () => {
      try {
        const status = await BackgroundUpload.requestNotificationPermission();
        setStatusText(`Notification permission: ${status}`);
      } catch (error) {
        setStatusText(`Permission request failed: ${(error as Error).message}`);
      }
    })();

    let progressSub: EmitterSubscription | undefined;
    let completionSub: EmitterSubscription | undefined;
    try {
      progressSub = BackgroundUpload.addListener(BackgroundUpload.UPLOAD_PROGRESS_EVENT, event => {
        console.log(event.progress);
        setStatusText(
          `Uploading ${event.uploadId}: ${(event.progress * 100).toFixed(1)}% (${event.bytesSent}/${event.totalBytes})`
        );
      });
      completionSub = BackgroundUpload.addListener(
        BackgroundUpload.UPLOAD_COMPLETION_EVENT,
        event => {
          if (event.status === 'error') {
            setStatusText(`Upload ${event.uploadId} failed: ${event.error ?? 'Unknown error'}`);
            return;
          }
          setStatusText(`Upload ${event.uploadId} ${event.status}`);
        }
      );
    } catch (error) {
      setStatusText(`Listener setup failed: ${(error as Error).message}`);
      return;
    }

    return () => {
      progressSub?.remove();
      completionSub?.remove();
    };
  }, []);

  const onStartUpload = async () => {
    if (!BackgroundUpload) {
      setStatusText('BackgroundUpload API not found in JS import.');
      return;
    }
    try {
      setStatusText('Starting upload...');
      await BackgroundUpload.startUpload({
        uploadId,
        fileUri,
        uploadUrl,
        contentType,
        requestNotificationPermissionOnStart: true,
      });
      setStatusText(`Upload enqueued: ${uploadId}`);
    } catch (error) {
      setStatusText(`Failed to enqueue upload: ${(error as Error).message}`);
    }
  };

  const onCancelUpload = async () => {
    if (!BackgroundUpload) {
      setStatusText('BackgroundUpload API not found in JS import.');
      return;
    }
    try {
      await BackgroundUpload.cancelUpload(uploadId);
      setStatusText(`Cancel requested: ${uploadId}`);
    } catch (error) {
      setStatusText(`Failed to cancel upload: ${(error as Error).message}`);
    }
  };

  const picMedias = async () => {
    await pickMedia('image', 1, its => {
      if (!!its && its.length > 0 && its[0].uri) {
        console.log(its[0]);
        setFileUri(its[0].uri);
        setContentType(its[0].mimeType ?? '');
      }
    });
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.title}>Background Upload Example</Text>
        <Input label="Upload ID" value={uploadId} onChangeText={setUploadId} />
        <Input label="File URI" value={fileUri} onChangeText={setFileUri} />
        <Input label="Presigned PUT URL" value={uploadUrl} onChangeText={setUploadUrl} />
        <Input label="Content-Type" value={contentType} onChangeText={setContentType} />

        <View style={styles.buttonRow}>
          <Button title="Start upload" onPress={onStartUpload} />
        </View>
        <View style={styles.buttonRow}>
          <Button title="Cancel upload" onPress={onCancelUpload} />
        </View>
        <View style={styles.buttonRow}>
          <Button title="Pick Media" onPress={picMedias} />
        </View>
        <Text style={styles.status}>{statusText}</Text>
      </ScrollView>
    </SafeAreaView>
  );
}

type InputProps = {
  label: string;
  value: string;
  onChangeText: (value: string) => void;
};

function Input({ label, value, onChangeText }: InputProps) {
  return (
    <View style={styles.inputGroup}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        style={styles.input}
        value={value}
        onChangeText={onChangeText}
        autoCapitalize="none"
        autoCorrect={false}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
  },
  container: {
    padding: 16,
    gap: 12,
  },
  title: {
    fontSize: 22,
    fontWeight: '700',
  },
  inputGroup: {
    gap: 6,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
  },
  input: {
    borderWidth: 1,
    borderColor: '#c0c0c0',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  buttonRow: {
    marginTop: 8,
  },
  status: {
    marginTop: 16,
    fontSize: 14,
  },
});

```
