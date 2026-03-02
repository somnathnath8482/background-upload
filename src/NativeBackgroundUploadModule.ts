import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  startUpload(options: {
    uploadId: string;
    fileUri: string;
    uploadUrl: string;
    contentType: string;
  }): Promise<void>;
  cancelUpload(uploadId: string): Promise<void>;
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

export default TurboModuleRegistry.get<Spec>('BackgroundUploadModule');
