package com.thindie.shadowcloud.feature.webdav

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private const val DAV_NS = "DAV:"

internal fun normalizeWebDavPathForCompare(rawPath: String): String {
  val trimmed = rawPath.trim().trimEnd('/')
  val decoded = safeDecode(trimmed)
  return decoded.trimEnd('/').lowercase()
}

internal fun webDavPathKeyFromHref(href: String): String {
  val decoded = safeDecode(href.trim())
  val path =
    if (decoded.startsWith("http://", ignoreCase = true) ||
      decoded.startsWith("https://", ignoreCase = true)
    ) {
      try {
        URI(decoded).rawPath ?: decoded
      } catch (_: java.net.URISyntaxException) {
        decoded
      }
    } else {
      decoded
    }
  return normalizeWebDavPathForCompare(path)
}

internal fun parseWebDavPropfind(
  xml: String,
  collectionPathForFilter: String,
): List<WebDavItem> {
  val factory = XmlPullParserFactory.newInstance()
  factory.isNamespaceAware = true
  val parser = factory.newPullParser()
  parser.setInput(StringReader(xml))

  val normalizedCollection = webDavPathKeyFromHref(collectionPathForFilter)
  val items = mutableListOf<WebDavItem>()
  var inResponse = false
  var currentHref = ""
  var currentDisplayName: String? = null
  var currentSize = 0L
  var isCollection = false

  var eventType = parser.eventType
  while (eventType != XmlPullParser.END_DOCUMENT) {
    when (eventType) {
      XmlPullParser.START_TAG -> {
        val local = parser.name ?: ""
        when {
          local.equals("response", ignoreCase = true) && isDavElement(parser) -> {
            inResponse = true
            currentHref = ""
            currentDisplayName = null
            currentSize = 0L
            isCollection = false
          }

          inResponse && local.equals("href", ignoreCase = true) && isDavElement(parser) -> {
            currentHref = readInnerText(parser).trim()
          }

          inResponse && local.equals("displayname", ignoreCase = true) && isDavElement(parser) -> {
            currentDisplayName = readInnerText(parser).trim().ifEmpty { null }
          }

          inResponse && local.equals("getcontentlength", ignoreCase = true) && isDavElement(parser) -> {
            currentSize = readInnerText(parser).trim().toLongOrNull() ?: 0L
          }

          inResponse && local.equals("collection", ignoreCase = true) && isDavElement(parser) -> {
            isCollection = true
          }
        }
      }

      XmlPullParser.END_TAG -> {
        val local = parser.name ?: ""
        if (local.equals("response", ignoreCase = true) && isDavElement(parser) && inResponse) {
          inResponse = false
          if (currentHref.isNotEmpty()) {
            val decoded = safeDecode(currentHref)
            val normalizedHref = webDavPathKeyFromHref(decoded)
            if (normalizedHref.isNotEmpty() && normalizedHref != normalizedCollection) {
              val name = currentDisplayName?.ifBlank { null }
                ?: fileNameFromHref(decoded)
              if (name.isNotEmpty()) {
                items.add(
                  WebDavItem(
                    name = name,
                    isDirectory = isCollection,
                    size = currentSize,
                    path = decoded,
                    previewPath = null
                  ),
                )
              }
            }
          }
        }
      }
    }
    eventType = parser.next()
  }

  return items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
}

private fun isDavElement(parser: XmlPullParser): Boolean {
  val ns = parser.namespace
  return ns.isNullOrEmpty() || ns == DAV_NS
}

private fun readInnerText(parser: XmlPullParser): String {
  if (parser.eventType != XmlPullParser.START_TAG) return ""
  val depth = parser.depth
  val builder = StringBuilder()
  var event = parser.next()
  while (!(event == XmlPullParser.END_TAG && parser.depth == depth)) {
    if (event == XmlPullParser.TEXT) {
      builder.append(parser.text)
    }
    event = parser.next()
  }
  return builder.toString()
}

private fun safeDecode(raw: String): String =
  try {
    URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
  } catch (_: IllegalArgumentException) {
    raw
  }

private fun fileNameFromHref(href: String): String {
  val decoded = safeDecode(href.trim())
  val noTrail = decoded.trimEnd('/')
  val slash = noTrail.lastIndexOf('/')
  return if (slash >= 0) noTrail.substring(slash + 1) else noTrail
}
