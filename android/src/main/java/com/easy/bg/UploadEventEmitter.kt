package com.easy.bg

import android.content.Context
import android.content.SharedPreferences
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.Collections

internal object UploadEventEmitter {
  const val PROGRESS_EVENT = "BackgroundUploadProgress"
  const val COMPLETION_EVENT = "BackgroundUploadCompletion"

  private const val PREFS_NAME = "BackgroundUploadEvents"
  private const val KEY_PENDING_COMPLETIONS = "pendingCompletions"
  private const val MAX_PENDING_EVENTS = 50

  @Volatile
  private var reactContextRef: WeakReference<ReactApplicationContext>? = null

  @Volatile
  private var appContext: Context? = null

  private val completedUploadIds = Collections.synchronizedSet(mutableSetOf<String>())

  fun initialize(context: Context) {
    appContext = context.applicationContext
  }

  fun attachReactContext(reactContext: ReactApplicationContext) {
    initialize(reactContext)
    reactContextRef = WeakReference(reactContext)
    flushPendingCompletions()
  }

  fun detachReactContext(reactContext: ReactApplicationContext) {
    val current = reactContextRef?.get()
    if (current == reactContext) {
      reactContextRef = null
    }
  }

  fun emitProgress(uploadId: String, bytesSent: Long, totalBytes: Long) {
    val progressValue = if (totalBytes > 0) {
      (bytesSent.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0)
    } else {
      0.0
    }

    val payload = Arguments.createMap().apply {
      putString("uploadId", uploadId)
      putDouble("bytesSent", bytesSent.toDouble())
      putDouble("totalBytes", totalBytes.toDouble())
      putDouble("progress", progressValue)
    }
    emit(PROGRESS_EVENT, payload)
  }

  fun emitCompletion(uploadId: String, status: String, error: String? = null) {
    if (!completedUploadIds.add(uploadId)) {
      return
    }

    val payload = Arguments.createMap().apply {
      putString("uploadId", uploadId)
      putString("status", status)
      if (error != null) {
        putString("error", error)
      }
    }
    val emitted = emit(COMPLETION_EVENT, payload)
    if (!emitted) {
      persistCompletion(uploadId, status, error)
    }
  }

  fun resetUpload(uploadId: String) {
    completedUploadIds.remove(uploadId)
  }

  private fun emit(
    eventName: String,
    payload: com.facebook.react.bridge.WritableMap
  ): Boolean {
    val reactContext = reactContextRef?.get()
    if (reactContext != null && reactContext.hasActiveReactInstance()) {
      reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit(eventName, payload)
      return true
    }
    return false
  }

  private fun prefs(): SharedPreferences? {
    val context = appContext ?: return null
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  @Synchronized
  private fun persistCompletion(uploadId: String, status: String, error: String?) {
    val preferences = prefs() ?: return
    val existing = preferences.getString(KEY_PENDING_COMPLETIONS, "[]") ?: "[]"
    val events = JSONArray(existing)
    val event = JSONObject().apply {
      put("uploadId", uploadId)
      put("status", status)
      if (error != null) {
        put("error", error)
      }
    }
    events.put(event)

    while (events.length() > MAX_PENDING_EVENTS) {
      events.remove(0)
    }

    preferences.edit().putString(KEY_PENDING_COMPLETIONS, events.toString()).apply()
  }

  @Synchronized
  private fun flushPendingCompletions() {
    val reactContext = reactContextRef?.get() ?: return
    if (!reactContext.hasActiveReactInstance()) {
      return
    }
    val preferences = prefs() ?: return
    val existing = preferences.getString(KEY_PENDING_COMPLETIONS, "[]") ?: "[]"
    val events = JSONArray(existing)
    if (events.length() == 0) {
      return
    }

    for (index in 0 until events.length()) {
      val event = events.optJSONObject(index) ?: continue
      val payload = Arguments.createMap().apply {
        putString("uploadId", event.optString("uploadId"))
        putString("status", event.optString("status"))
        if (event.has("error")) {
          putString("error", event.optString("error"))
        }
      }
      reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit(COMPLETION_EVENT, payload)
    }

    preferences.edit().remove(KEY_PENDING_COMPLETIONS).apply()
  }
}
