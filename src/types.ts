export type StartUploadOptions = {
  uploadId: string;
  fileUri: string;
  uploadUrl: string;
  contentType: string;
  requestNotificationPermissionOnStart?: boolean;
};

export type NotificationPermissionStatus =
  | 'granted'
  | 'denied'
  | 'never_ask_again'
  | 'unavailable';

export type UploadProgressEvent = {
  uploadId: string;
  bytesSent: number;
  totalBytes: number;
  progress: number;
};

export type UploadCompletionEvent = {
  uploadId: string;
  status: 'success' | 'error' | 'cancelled';
  error?: string;
};

export const UPLOAD_PROGRESS_EVENT = 'BackgroundUploadProgress' as const;
export const UPLOAD_COMPLETION_EVENT = 'BackgroundUploadCompletion' as const;

export type UploadEventName =
  | typeof UPLOAD_PROGRESS_EVENT
  | typeof UPLOAD_COMPLETION_EVENT;
