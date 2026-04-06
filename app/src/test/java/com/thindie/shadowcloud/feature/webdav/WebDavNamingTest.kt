package com.thindie.shadowcloud.feature.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavNamingTest {

  @Test
  fun thumbnailSidecarFileName_appendsThumbBeforeExtension() {
    assertEquals("IMG_1.thumb.jpg", thumbnailSidecarFileName("IMG_1.jpg"))
    assertEquals("photo.thumb.jpeg", thumbnailSidecarFileName("photo.jpeg"))
  }

  @Test
  fun isImageFileName_recognizesCommonExtensions() {
    assertTrue(isImageFileName("a.JPG"))
    assertTrue(isImageFileName("b.png"))
    assertFalse(isImageFileName("readme.txt"))
  }

  @Test
  fun isThumbnailsDirectory() {
    assertTrue(isThumbnailsDirectory(".thumbnails"))
    assertTrue(isThumbnailsDirectory(".thumbnails/"))
  }
}
