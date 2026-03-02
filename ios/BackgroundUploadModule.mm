#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RCT_EXTERN_MODULE(BackgroundUploadModule, RCTEventEmitter)

RCT_EXTERN_METHOD(startUpload:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(cancelUpload:(NSString *)uploadId
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

@end

#ifdef RCT_NEW_ARCH_ENABLED
#if __has_include("BackgroundUploadSpec.h")
#import "BackgroundUploadSpec.h"
#elif __has_include(<BackgroundUploadSpec/BackgroundUploadSpec.h>)
#import <BackgroundUploadSpec/BackgroundUploadSpec.h>
#endif
#import <ReactCommon/RCTTurboModule.h>

@interface BackgroundUploadModule () <NativeBackgroundUploadModuleSpec>
@end

@implementation BackgroundUploadModule (TurboModule)
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
  return std::make_shared<facebook::react::NativeBackgroundUploadModuleSpecJSI>(params);
}
@end
#endif
