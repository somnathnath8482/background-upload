import { NativeModule, requireNativeModule } from 'expo';

import { BackgroundUploadModuleEvents } from './BackgroundUpload.types';

declare class BackgroundUploadModule extends NativeModule<BackgroundUploadModuleEvents> {
  PI: number;
  hello(): string;
  setValueAsync(value: string): Promise<void>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<BackgroundUploadModule>('BackgroundUpload');
