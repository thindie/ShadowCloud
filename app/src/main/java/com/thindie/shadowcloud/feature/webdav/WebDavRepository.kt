package com.thindie.shadowcloud.feature.webdav

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.thindie.shadowcloud.error.AppError
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import java.net.URL

class WebDavRepository(
  private val appContext: Context,
  private val baseUrl: String,
  private val userName: String,
  private val password: String,
) {

  private val resolver: ContentResolver get() = appContext.contentResolver

  private var httpClient: HttpClient? = null

  private fun client(): HttpClient {
    if (httpClient == null) {
      httpClient = newAuthenticatedWebdavClient(userName, password)
    }
    return httpClient!!
  }

  fun close() {
    httpClient?.close()
    httpClient = null
  }

  suspend fun listChildren(segments: List<String>): List<WebDavItem> {
    val url = buildCollectionUrl(segments)
    val filter = collectionPathFilter(segments)
    val xml = client().propfindDepth1(url)
    return parseWebDavPropfind(xml, filter)
  }

  suspend fun createFolder(segments: List<String>, folderName: String) {
    val trimmed = folderName.trim().trim('/')
    if (trimmed.isEmpty()) return
    val target = buildCollectionUrl(segments + trimmed)
    when (val result = client().mkcol(target)) {
      is MkcolResult.Failed -> throw webDavErrorFromStatus(result.status, target)
      else -> Unit
    }
  }

  suspend fun uploadPhoto(segments: List<String>, uri: Uri, remoteFileName: String) {
    val name = remoteFileName.trim().trimStart('/')
    if (name.isEmpty()) return
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val contentType = ContentType.parse(mime)
    val size = queryOpenableSize(uri)
    val stream = resolver.openInputStream(uri) ?: throw AppError.WebDav.UploadOpenFailed
    stream.use { input ->
      val channel = input.toByteReadChannel()
      client().putStream(
        urlString = buildFileUrl(segments, name),
        contentLength = size,
        bodyContentType = contentType,
        body = channel,
      )
    }
  }

  private fun queryOpenableSize(uri: Uri): Long? {
    resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
      val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
      if (idx >= 0 && cursor.moveToFirst()) {
        val v = cursor.getLong(idx)
        if (v > 0L) return v
      }
    }
    return null
  }

  private fun webdavRootPath(): String =
    URL(baseUrl.trim()).path.trimEnd('/')

  private fun collectionPathFilter(segments: List<String>): String {
    val root = webdavRootPath()
    val suffix = segments.joinToString("/") { it.trim().trim('/') }.takeIf { it.isNotEmpty() }
    val pathOnly = if (suffix == null) root else "$root/$suffix"
    return pathOnly
  }

  private fun buildCollectionUrl(segments: List<String>): String {
    val base = baseUrl.trim().trimEnd('/')
    if (segments.isEmpty()) return "$base/"
    val path = segments.joinToString("/") { it.trim().trim('/') }
    return "$base/$path/"
  }

  private fun buildFileUrl(segments: List<String>, fileName: String): String {
    val base = baseUrl.trim().trimEnd('/')
    val prefix =
      if (segments.isEmpty()) {
        ""
      } else {
        segments.joinToString("/") { it.trim().trim('/') } + "/"
      }
    return if (prefix.isEmpty()) "$base/$fileName" else "$base/$prefix$fileName"
  }
}
