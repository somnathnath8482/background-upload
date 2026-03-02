// Reexport the native module. On web, it will be resolved to BackgroundUploadModule.web.ts
// and on native platforms to BackgroundUploadModule.ts
export { default } from './BackgroundUploadModule';
export { default as BackgroundUploadView } from './BackgroundUploadView';
export * from  './BackgroundUpload.types';
