package com.thindie.shadowcloud.feature.webdav.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.core.graphics.scale

private const val JPEG_QUALITY = 82

fun generateJpegThumbnail(
  context: Context,
  uri: Uri,
  maxEdgePx: Int = 512,
): ByteArray {
  val resolver = context.contentResolver
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  resolver.openInputStream(uri)?.use {
    BitmapFactory.decodeStream(it, null, bounds)
  } ?: return ByteArray(0)
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ByteArray(0)

  var sample = 1
  val maxDim = max(bounds.outWidth, bounds.outHeight)
  while (maxDim / sample > maxEdgePx * 2) {
    sample *= 2
  }
  val decode = BitmapFactory.Options().apply {
    inSampleSize = sample
  }
  val bitmap = resolver.openInputStream(uri)?.use {
    BitmapFactory.decodeStream(it, null, decode)
  } ?: return ByteArray(0)

  val scaled = scaleToMaxEdge(bitmap, maxEdgePx)
  if (scaled != bitmap) {
    bitmap.recycle()
  }
  val out = ByteArrayOutputStream()
  scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
  scaled.recycle()
  return out.toByteArray()
}

fun generateJpegThumbnail(
  file: File,
  maxEdgePx: Int = 512,
): ByteArray {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  file.inputStream().use {
    BitmapFactory.decodeStream(it, null, bounds)
  }
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ByteArray(0)

  var sample = 1
  val maxDim = max(bounds.outWidth, bounds.outHeight)
  while (maxDim / sample > maxEdgePx * 2) {
    sample *= 2
  }
  val decode = BitmapFactory.Options().apply {
    inSampleSize = sample
  }
  val bitmap = file.inputStream().use {
    BitmapFactory.decodeStream(it, null, decode)
  } ?: return ByteArray(0)

  val scaled = scaleToMaxEdge(bitmap, maxEdgePx)
  if (scaled != bitmap) {
    bitmap.recycle()
  }
  val out = ByteArrayOutputStream()
  scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
  scaled.recycle()
  return out.toByteArray()
}

private fun scaleToMaxEdge(bitmap: Bitmap, maxEdgePx: Int): Bitmap {
  val w = bitmap.width
  val h = bitmap.height
  if (w <= 0 || h <= 0) return bitmap
  val longest = max(w, h)
  if (longest <= maxEdgePx) return bitmap
  val scale = maxEdgePx.toFloat() / longest
  val nw = (w * scale).roundToInt().coerceAtLeast(1)
  val nh = (h * scale).roundToInt().coerceAtLeast(1)
  return bitmap.scale(nw, nh)
}
