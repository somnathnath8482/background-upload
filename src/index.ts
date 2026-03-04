import {
  DeviceEventEmitter,
  NativeEventEmitter,
  NativeModules,
  PermissionsAndroid,
  Platform,
  type EmitterSubscription,
} from 'react-native';

import NativeBackgroundUploadModule, {
  type Spec as NativeBackgroundUploadModuleSpec,
} from './NativeBackgroundUploadModule';
import {
  UPLOAD_COMPLETION_EVENT,
  UPLOAD_PROGRESS_EVENT,
  type StartUploadOptions,
  type NotificationPermissionStatus,
  type UploadCompletionEvent,
  type UploadEventName,
  type UploadProgressEvent,
} from './types';

type UploadEventPayloadMap = {
  [UPLOAD_PROGRESS_EVENT]: UploadProgressEvent;
  [UPLOAD_COMPLETION_EVENT]: UploadCompletionEvent;
};

type EventEmitterLike = {
  addListener: (
    eventName: string,
    callback: (...args: unknown[]) => void
  ) => EmitterSubscription;
};

const moduleEmitter =
  NativeModules.BackgroundUploadModule != null
    ? (new NativeEventEmitter(
        NativeModules.BackgroundUploadModule
      ) as unknown as EventEmitterLike)
    : undefined;

const deviceEmitter =
  DeviceEventEmitter != null
    ? (DeviceEventEmitter as unknown as EventEmitterLike)
    : undefined;

const eventEmitter: EventEmitterLike | undefined =
  Platform.OS === 'android'
    ? deviceEmitter ?? moduleEmitter
    : moduleEmitter ?? deviceEmitter;

const isAndroidNotificationRuntimePermissionRequired = (): boolean =>
  Platform.OS === 'android' && typeof Platform.Version === 'number' && Platform.Version >= 33;

function getNativeModule(): NativeBackgroundUploadModuleSpec {
  const module =
    NativeBackgroundUploadModule ??
    (NativeModules.BackgroundUploadModule as NativeBackgroundUploadModuleSpec | undefined);
  if (!module) {
    throw new Error(
      'BackgroundUpload: native module "BackgroundUploadModule" is unavailable. Rebuild the app after native changes.'
    );
  }
  return module;
}

function assertNonEmptyString(value: string, fieldName: string): void {
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new Error(`BackgroundUpload: "${fieldName}" must be a non-empty string.`);
  }
}

function assertValidOptions(options: StartUploadOptions): void {
  assertNonEmptyString(options.uploadId, 'uploadId');
  assertNonEmptyString(options.fileUri, 'fileUri');
  assertNonEmptyString(options.uploadUrl, 'uploadUrl');
  assertNonEmptyString(options.contentType, 'contentType');
}

export async function getNotificationPermissionStatus(): Promise<NotificationPermissionStatus> {
  if (!isAndroidNotificationRuntimePermissionRequired()) {
    return 'unavailable';
  }

  const granted = await PermissionsAndroid.check(
    PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS
  );
  return granted ? 'granted' : 'denied';
}

export async function requestNotificationPermission(): Promise<NotificationPermissionStatus> {
  if (!isAndroidNotificationRuntimePermissionRequired()) {
    return 'unavailable';
  }

  const result = await PermissionsAndroid.request(
    PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
    {
      title: 'Notification permission',
      message:
        'Background uploads need notification permission to run foreground upload service on Android.',
      buttonPositive: 'Allow',
      buttonNegative: 'Deny',
    }
  );

  if (result === PermissionsAndroid.RESULTS.GRANTED) {
    return 'granted';
  }
  if (result === PermissionsAndroid.RESULTS.NEVER_ASK_AGAIN) {
    return 'never_ask_again';
  }
  return 'denied';
}

export async function startUpload(options: StartUploadOptions): Promise<void> {
  assertValidOptions(options);

  const shouldRequestNotificationPermission =
    options.requestNotificationPermissionOnStart ?? true;

  if (isAndroidNotificationRuntimePermissionRequired()) {
    const existingStatus = await getNotificationPermissionStatus();
    const status =
      existingStatus === 'granted'
        ? existingStatus
        : shouldRequestNotificationPermission
          ? await requestNotificationPermission()
          : existingStatus;

    if (status !== 'granted') {
      throw new Error(
        'BackgroundUpload: POST_NOTIFICATIONS permission is required for foreground upload on Android 13+.'
      );
    }
  }

  await getNativeModule().startUpload({
    uploadId: options.uploadId,
    fileUri: options.fileUri,
    uploadUrl: options.uploadUrl,
    contentType: options.contentType,
  });
}

export async function cancelUpload(uploadId: string): Promise<void> {
  assertNonEmptyString(uploadId, 'uploadId');
  await getNativeModule().cancelUpload(uploadId);
}

export function addListener<E extends UploadEventName>(
  event: E,
  callback: (payload: UploadEventPayloadMap[E]) => void
): EmitterSubscription {
  if (event !== UPLOAD_PROGRESS_EVENT && event !== UPLOAD_COMPLETION_EVENT) {
    throw new Error(`BackgroundUpload: unsupported event "${event}".`);
  }
  if (!eventEmitter) {
    throw new Error('BackgroundUpload: event emitter is unavailable on this runtime.');
  }
  return eventEmitter.addListener(
    event,
    callback as unknown as (...args: unknown[]) => void
  );
}

const BackgroundUpload = {
  startUpload,
  cancelUpload,
  getNotificationPermissionStatus,
  requestNotificationPermission,
  addListener,
};

export default BackgroundUpload;

export {
  UPLOAD_COMPLETION_EVENT,
  UPLOAD_PROGRESS_EVENT,
  type NotificationPermissionStatus,
  type StartUploadOptions,
  type UploadCompletionEvent,
  type UploadProgressEvent,
};
