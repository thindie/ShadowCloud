package com.thindie.shadowcloud.feature.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavPropfindParserTest {

  @Test
  fun parsesMixedDavPrefixesAndFiltersCollectionSelf() {
    val xml =
      """
      <?xml version="1.0" encoding="utf-8"?>
      <D:multistatus xmlns:D="DAV:">
        <D:response>
          <D:href>/webdav/</D:href>
          <D:propstat>
            <D:prop>
              <D:resourcetype><D:collection/></D:resourcetype>
            </D:prop>
          </D:propstat>
        </D:response>
        <D:response>
          <D:href>/webdav/Photos/</D:href>
          <D:propstat>
            <D:prop>
              <D:resourcetype><D:collection/></D:resourcetype>
              <D:displayname>Photos</D:displayname>
            </D:prop>
          </D:propstat>
        </D:response>
        <d:response xmlns:d="DAV:">
          <d:href>/webdav/readme.txt</d:href>
          <d:propstat>
            <d:prop>
              <d:getcontentlength>12</d:getcontentlength>
              <d:displayname>readme.txt</d:displayname>
            </d:prop>
          </d:propstat>
        </d:response>
      </D:multistatus>
      """.trimIndent()

    val items = parseWebDavPropfind(xml, collectionPathForFilter = "/webdav")

    assertEquals(2, items.size)
    val photos = items.first { it.name == "Photos" }
    assertTrue(photos.isDirectory)
    val readme = items.first { it.name == "readme.txt" }
    assertEquals(false, readme.isDirectory)
    assertEquals(12L, readme.size)
  }
}
