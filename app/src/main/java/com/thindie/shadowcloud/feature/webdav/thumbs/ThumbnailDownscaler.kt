package com.thindie.shadowcloud.feature.webdav.thumbs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max

object ThumbnailDownscaler {
  fun downscaleToJpeg(
    input: InputStream,
    maxSidePx: Int,
    jpegQuality: Int,
  ): ByteArray {
    if (maxSidePx <= 0) return byteArrayOf()
    val sourceBytes = input.readBytes()
    if (sourceBytes.isEmpty()) return byteArrayOf()

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, bounds)
    val srcW = bounds.outWidth
    val srcH = bounds.outHeight
    if (srcW <= 0 || srcH <= 0) return byteArrayOf()

    val sample = calculateInSampleSize(srcW, srcH, maxSidePx)
    val decoded =
      BitmapFactory.decodeByteArray(
        sourceBytes,
        0,
        sourceBytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
      ) ?: return byteArrayOf()

    val scaled =
      tryScaleToMaxSide(decoded, maxSidePx).also {
        if (it !== decoded) decoded.recycle()
      }

    return ByteArrayOutputStream().use { out ->
      scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(0, 100), out)
      scaled.recycle()
      out.toByteArray()
    }
  }

  private fun calculateInSampleSize(srcW: Int, srcH: Int, maxSidePx: Int): Int {
    val srcMax = max(srcW, srcH)
    var sample = 1
    while (srcMax / sample > maxSidePx * 2) {
      sample *= 2
    }
    return sample.coerceAtLeast(1)
  }

  private fun tryScaleToMaxSide(bitmap: Bitmap, maxSidePx: Int): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    val srcMax = max(w, h)
    if (srcMax <= maxSidePx) return bitmap

    val scale = maxSidePx.toFloat() / srcMax.toFloat()
    val outW = (w * scale).toInt().coerceAtLeast(1)
    val outH = (h * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, outW, outH, true)
  }
}

