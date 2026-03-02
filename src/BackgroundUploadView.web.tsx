import * as React from 'react';

import { BackgroundUploadViewProps } from './BackgroundUpload.types';

export default function BackgroundUploadView(props: BackgroundUploadViewProps) {
  return (
    <div>
      <iframe
        style={{ flex: 1 }}
        src={props.url}
        onLoad={() => props.onLoad({ nativeEvent: { url: props.url } })}
      />
    </div>
  );
}
