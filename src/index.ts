import {
  DeviceEventEmitter,
  NativeEventEmitter,
  NativeModules,
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

export async function startUpload(options: StartUploadOptions): Promise<void> {
  assertValidOptions(options);
  await getNativeModule().startUpload(options);
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
  addListener,
};

export default BackgroundUpload;

export {
  UPLOAD_COMPLETION_EVENT,
  UPLOAD_PROGRESS_EVENT,
  type StartUploadOptions,
  type UploadCompletionEvent,
  type UploadProgressEvent,
};
