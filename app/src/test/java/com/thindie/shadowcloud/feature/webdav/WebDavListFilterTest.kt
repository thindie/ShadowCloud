package com.thindie.shadowcloud.feature.webdav

import org.junit.Assert.assertEquals
import org.junit.Test

class WebDavListFilterTest {

  @Test
  fun partition_hidesThumbnailsFolder_andSidecars() {
    val items = listOf(
      WebDavItem("photos", true, 0L, "/webdav/photos/"),
      WebDavItem(".thumbnails", true, 0L, "/webdav/.thumbnails/"),
      WebDavItem("a.jpg", false, 100L, "/webdav/a.jpg"),
      WebDavItem("a.thumb.jpg", false, 10L, "/webdav/.thumbnails/a.thumb.jpg"),
    )
    val buckets = partitionForBrowse(items)
    assertEquals(listOf("photos"), buckets.folders.map { it.name })
    assertEquals(listOf("a.jpg"), buckets.imageFiles.map { it.name })
  }
}
