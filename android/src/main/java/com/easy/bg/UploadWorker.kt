package com.easy.bg

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

internal class UploadWorker(
  appContext: Context,
  params: WorkerParameters
) : CoroutineWorker(appContext, params) {

  private val notificationManager =
    appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private val okHttpClient by lazy {
    OkHttpClient.Builder()
      .retryOnConnectionFailure(false)
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(0, TimeUnit.MILLISECONDS)
      .build()
  }

  override suspend fun doWork(): Result {
    UploadEventEmitter.initialize(applicationContext)

    val uploadId = inputData.getString(KEY_UPLOAD_ID)
      ?: return Result.failure()
    val fileUriString = inputData.getString(KEY_FILE_URI)
      ?: return failWithError(uploadId, "Missing fileUri")
    val uploadUrl = inputData.getString(KEY_UPLOAD_URL)
      ?: return failWithError(uploadId, "Missing uploadUrl")
    val contentType = inputData.getString(KEY_CONTENT_TYPE)
      ?: return failWithError(uploadId, "Missing contentType")

    createNotificationChannelIfRequired()
    setForeground(createForegroundInfo(uploadId, 0))

    val fileUri = Uri.parse(fileUriString)
    val totalBytes = resolveFileSize(fileUri)

    return try {
      val requestBody = UploadProgressRequestBody(
        contentResolver = applicationContext.contentResolver,
        fileUri = fileUri,
        contentType = contentType,
        declaredContentLength = totalBytes,
        isCancelled = { isStopped },
      ) { bytesSent, total ->
        if (isStopped) {
          return@UploadProgressRequestBody
        }
        val progressRatio = if (total > 0) {
          (bytesSent.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
        } else {
          0.0
        }
        val percentage = (progressRatio * 100).roundToInt().coerceIn(0, 100)
        notificationManager.notify(
          notificationId(uploadId),
          buildNotification(uploadId, percentage, inProgress = true)
        )
        UploadEventEmitter.emitProgress(uploadId, bytesSent, total)
      }

      val request = Request.Builder()
        .url(uploadUrl)
        .put(requestBody)
        .header("Content-Type", contentType)
        .build()

      okHttpClient.newCall(request).execute().use { response ->
        if (isStopped) {
          UploadEventEmitter.emitCompletion(uploadId, "cancelled")
          return Result.success()
        }

        if (!response.isSuccessful) {
          return failWithError(uploadId, "HTTP ${response.code}")
        }
      }

      UploadEventEmitter.emitCompletion(uploadId, "success")
      Result.success()
    } catch (cancelled: java.io.InterruptedIOException) {
      UploadEventEmitter.emitCompletion(uploadId, "cancelled")
      Result.success()
    } catch (error: Throwable) {
      failWithError(uploadId, error.message ?: "Upload failed")
    } finally {
      notificationManager.cancel(notificationId(uploadId))
    }
  }

  private fun failWithError(uploadId: String, errorMessage: String): Result {
    UploadEventEmitter.emitCompletion(uploadId, "error", errorMessage)
    return Result.failure()
  }

  private fun resolveFileSize(fileUri: Uri): Long {
    if ("content".equals(fileUri.scheme, ignoreCase = true)) {
      applicationContext.contentResolver.query(
        fileUri,
        arrayOf(OpenableColumns.SIZE),
        null,
        null,
        null
      )?.use { cursor: Cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
          return cursor.getLong(index)
        }
      }
    }

    if ("file".equals(fileUri.scheme, ignoreCase = true)) {
      return java.io.File(fileUri.path ?: "").length()
    }

    return -1L
  }

  private fun createNotificationChannelIfRequired() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }

    val channel = NotificationChannel(
      NOTIFICATION_CHANNEL_ID,
      NOTIFICATION_CHANNEL_NAME,
      NotificationManager.IMPORTANCE_LOW
    ).apply {
      description = "Background uploads"
      setShowBadge(false)
    }
    notificationManager.createNotificationChannel(channel)
  }

  private fun createForegroundInfo(uploadId: String, progressPercent: Int): ForegroundInfo {
    val notification = buildNotification(uploadId, progressPercent, inProgress = true)
    return ForegroundInfo(notificationId(uploadId), notification)
  }

  private fun buildNotification(
    uploadId: String,
    progressPercent: Int,
    inProgress: Boolean
  ): Notification {
    val title = "Uploading file"
    val text = "$progressPercent%"
    return NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_sys_upload)
      .setContentTitle(title)
      .setContentText(text)
      .setOnlyAlertOnce(true)
      .setOngoing(inProgress)
      .setProgress(100, progressPercent, false)
      .build()
  }

  private fun notificationId(uploadId: String): Int =
    uploadId.hashCode().let { hash -> if (hash == Int.MIN_VALUE) 1 else kotlin.math.abs(hash) }

  companion object {
    const val KEY_UPLOAD_ID = "uploadId"
    const val KEY_FILE_URI = "fileUri"
    const val KEY_UPLOAD_URL = "uploadUrl"
    const val KEY_CONTENT_TYPE = "contentType"

    const val NOTIFICATION_CHANNEL_ID = "background_upload_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Background Uploads"
  }
}
