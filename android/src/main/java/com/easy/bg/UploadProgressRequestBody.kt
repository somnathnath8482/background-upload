package com.easy.bg

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.io.InterruptedIOException

internal class UploadProgressRequestBody(
  private val contentResolver: ContentResolver,
  private val fileUri: Uri,
  private val contentType: String,
  private val declaredContentLength: Long,
  private val isCancelled: () -> Boolean,
  private val onProgress: (bytesSent: Long, totalBytes: Long) -> Unit
) : RequestBody() {

  override fun contentType(): MediaType? = contentType.toMediaTypeOrNull()

  override fun contentLength(): Long = declaredContentLength

  @Throws(IOException::class)
  override fun writeTo(sink: BufferedSink) {
    contentResolver.openInputStream(fileUri).use { inputStream ->
      if (inputStream == null) {
        throw IOException("Unable to open input stream for $fileUri")
      }

      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      var bytesSent = 0L

      while (true) {
        if (isCancelled()) {
          throw InterruptedIOException("Upload cancelled")
        }

        val read = inputStream.read(buffer)
        if (read == -1) {
          break
        }

        sink.write(buffer, 0, read)
        bytesSent += read.toLong()
        onProgress(bytesSent, declaredContentLength)
      }
    }
  }

  companion object {
    private const val DEFAULT_BUFFER_SIZE = 64 * 1024
  }
}
