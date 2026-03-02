package com.easy.bg

import com.facebook.react.TurboReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

class BackgroundUploadPackage : TurboReactPackage() {
  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
    return if (name == BackgroundUploadModule.NAME) {
      BackgroundUploadModule(reactContext)
    } else {
      null
    }
  }

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
    return ReactModuleInfoProvider {
      mapOf(
        BackgroundUploadModule.NAME to ReactModuleInfo(
          BackgroundUploadModule.NAME,
          BackgroundUploadModule.NAME,
          false,
          false,
          false,
          false,
          true
        )
      )
    }
  }
}
