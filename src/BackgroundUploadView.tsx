import { requireNativeView } from 'expo';
import * as React from 'react';

import { BackgroundUploadViewProps } from './BackgroundUpload.types';

const NativeView: React.ComponentType<BackgroundUploadViewProps> =
  requireNativeView('BackgroundUpload');

export default function BackgroundUploadView(props: BackgroundUploadViewProps) {
  return <NativeView {...props} />;
}
