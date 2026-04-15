package com.thindie.shadowcloud.feature.webdav

import androidx.compose.runtime.Immutable

@Immutable
data class WebDavBrowseBuckets(
  val folders: List<WebDavItem>,
  val imageFiles: List<WebDavItem>,
)

fun partitionForBrowse(items: List<WebDavItem>): WebDavBrowseBuckets {
  val folders = mutableListOf<WebDavItem>()
  val images = mutableListOf<WebDavItem>()
  for (item in items) {
    when {
      item.isDirectory -> {
        if (!isThumbnailsDirectory(item.name)) {
          folders.add(item)
        }
      }
      isThumbnailSidecarFileName(item.name) -> Unit
      isImageFileName(item.name) -> images.add(item)
    }
  }
  folders.sortWith(compareBy { it.name.lowercase() })
  images.sortWith(compareBy { it.name.lowercase() })
  return WebDavBrowseBuckets(folders, images)
}
