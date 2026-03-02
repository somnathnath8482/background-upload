import { registerWebModule, NativeModule } from 'expo';

import { BackgroundUploadModuleEvents } from './BackgroundUpload.types';

class BackgroundUploadModule extends NativeModule<BackgroundUploadModuleEvents> {
  PI = Math.PI;
  async setValueAsync(value: string): Promise<void> {
    this.emit('onChange', { value });
  }
  hello() {
    return 'Hello world! 👋';
  }
}

export default registerWebModule(BackgroundUploadModule, 'BackgroundUploadModule');
