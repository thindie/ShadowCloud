package com.thindie.shadowcloud.feature.webdav

/**
 * Соглашение: оригинал `IMG_123.jpg` → миниатюра в коллекции `.thumbnails/` с именем `IMG_123.thumb.jpg`
 * (базовое имя без последнего расширения + `.thumb.` + исходное расширение).
 */
internal const val THUMBNAILS_SEGMENT = ".thumbnails"

fun thumbnailSidecarFileName(originalFileName: String): String {
  val name = originalFileName.trim().trimStart('/')
  val dot = name.lastIndexOf('.')
  if (dot <= 0) return "$name.thumb.jpg"
  val base = name.substring(0, dot)
  val ext = name.substring(dot + 1)
  return "$base.thumb.$ext"
}

fun isThumbnailsDirectory(name: String): Boolean =
  name.trim().trim('/').equals(THUMBNAILS_SEGMENT, ignoreCase = true)

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")

fun isImageFileName(fileName: String): Boolean {
  val dot = fileName.lastIndexOf('.')
  if (dot < 0 || dot == fileName.length - 1) return false
  val ext = fileName.substring(dot + 1).lowercase()
  return ext in IMAGE_EXTENSIONS
}

fun isThumbnailSidecarFileName(fileName: String): Boolean {
  val lower = fileName.lowercase()
  return lower.contains(".thumb.")
}
