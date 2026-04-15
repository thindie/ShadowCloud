package com.thindie.shadowcloud.feature.webdav.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.thindie.shadowcloud.error.AppError
import com.thindie.shadowcloud.feature.webdav.THUMBNAILS_SEGMENT
import com.thindie.shadowcloud.feature.webdav.WebDavItem
import com.thindie.shadowcloud.feature.webdav.isImageFileName
import com.thindie.shadowcloud.feature.webdav.parseWebDavPropfind
import com.thindie.shadowcloud.feature.webdav.thumbnailSidecarFileName
import com.thindie.shadowcloud.feature.webdav.thumbs.ThumbnailDownscaler
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyTo
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.io.readByteArray
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.net.URL

class WebDavRepository(
  private val appContext: Context,
  private val baseUrl: String,
  private val userName: String,
  private val password: String,
) {

  private val resolver: ContentResolver get() = appContext.contentResolver

  private var httpClient: HttpClient? = null
  private val listThumbnailsParallelism = Semaphore(permits = 4)

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
    val props = parseWebDavPropfind(xml, filter)
    return coroutineScope {
      props.map { item ->
        async {
          if (isImageFileName(item.name)) {
            try {
              listThumbnailsParallelism.withPermit {
                ensureThumbnailExists(segments, item.name)
              }
            } catch (e: CancellationException) {
              throw e
            } catch (_: AppError) {

            }
            item.copy(previewPath = fileUrlForThumbnail(segments, item.name))
          } else {
            item
          }
        }
      }.awaitAll()
    }
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

  fun fileUrlForOriginal(segments: List<String>, fileName: String): String =
    buildFileUrl(segments, fileName.trim().trimStart('/'))

  fun fileUrlForThumbnail(segments: List<String>, originalFileName: String): String =
    buildFileUrl(
      segments + listOf(THUMBNAILS_SEGMENT),
      thumbnailSidecarFileName(originalFileName),
    )

  private suspend fun ensureThumbnailsCollection(segments: List<String>) {
    val url = buildCollectionUrl(segments + listOf(THUMBNAILS_SEGMENT))
    when (val result = client().mkcol(url)) {
      is MkcolResult.Failed -> throw webDavErrorFromStatus(result.status, url)
      else -> Unit
    }
  }

  suspend fun ensureThumbnailExists(segments: List<String>, originalFileName: String) {
    val trimmedName = originalFileName.trim().trimStart('/')
    if (trimmedName.isEmpty()) return

    val thumbUrl = fileUrlForThumbnail(segments, trimmedName)
    val originalUrl = fileUrlForOriginal(segments, trimmedName)

    when (val head = client().headObject(thumbUrl)) {
      WebDavObjectCheck.Exists -> return
      WebDavObjectCheck.Missing -> Unit
      WebDavObjectCheck.HeadNotSupported -> {
        val probe = client().getChannel(thumbUrl)
        if (probe != null) {
          probe.cancel(CancellationException("thumbnail exists"))
          return
        }
      }
    }

    val originalBytes: ByteArray? =
      client().getChannel(originalUrl)?.readRemaining()?.readByteArray() ?: return
    val jpegThumb = ByteArrayInputStream(originalBytes).use { input ->
      ThumbnailDownscaler.downscaleToJpeg(
        input = input,
        maxSidePx = 384,
        jpegQuality = 75,
      )
    }
    if (jpegThumb.isEmpty()) return

    ensureThumbnailsCollection(segments)
    ByteArrayInputStream(jpegThumb).use { thumbIn ->
      client().putStream(
        urlString = thumbUrl,
        contentLength = jpegThumb.size.toLong(),
        bodyContentType = ContentType.Image.JPEG,
        body = thumbIn.toByteReadChannel(),
      )
    }
  }

  suspend fun move(
    sources: Set<String>,
    overwrite: Boolean,
    destination: String,
  ) {
    for (sourcePath in sources) {
      val trimmedSource = sourcePath.trim()
      if (trimmedSource.isEmpty()) continue

      val fileName = trimmedSource.substringAfterLast('/')
      val fullDestinationPath =
        if (destination.endsWith("/")) {
          "$destination$fileName"
        } else {
          "$destination/$fileName"
        }
      val fullSourcePath = sourcePath.replace("/webdav/", baseUrl)

      client().moveObject(
        source = fullSourcePath,
        destination = fullDestinationPath,
        overwrite = overwrite,
      )
    }
  }

  suspend fun uploadPhoto(segments: List<String>, uri: Uri, remoteFileName: String) {
    val name = remoteFileName.trim().trimStart('/')
    if (name.isEmpty()) return
    ensureThumbnailsCollection(segments)
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val contentType = ContentType.Companion.parse(mime)
    val size = queryOpenableSize(uri)
    val stream = try {
      resolver.openInputStream(uri)
    } catch (_: SecurityException) {
      throw AppError.WebDav.UploadOpenFailed
    } catch (_: FileNotFoundException) {
      throw AppError.WebDav.UploadOpenFailed
    }
    if (stream == null) throw AppError.WebDav.UploadOpenFailed
    stream.use { input ->
      val channel = input.toByteReadChannel()
      client().putStream(
        urlString = buildFileUrl(segments, name),
        contentLength = size,
        bodyContentType = contentType,
        body = channel,
      )
    }
    val thumbBytes = generateJpegThumbnail(appContext, uri)
    if (thumbBytes.isNotEmpty()) {
      val thumbName = thumbnailSidecarFileName(name)
      val thumbUrl = buildFileUrl(segments + listOf(THUMBNAILS_SEGMENT), thumbName)
      ByteArrayInputStream(thumbBytes).use { thumbIn ->
        val thumbChannel = thumbIn.toByteReadChannel()
        client().putStream(
          urlString = thumbUrl,
          contentLength = thumbBytes.size.toLong(),
          bodyContentType = ContentType.Image.JPEG,
          body = thumbChannel,
        )
      }
    }
  }

  suspend fun download(items: List<WebDavItem>, destinationDir: File): List<File>? {
    val files = coroutineScope {
      items.map { item ->
        async(Dispatchers.IO) {
          try {
            val fileName = item.path.substringAfterLast("/")
            val file = File(destinationDir, fileName)

            client().prepareGet(item.path).execute { response ->
              val channel: ByteReadChannel = response.bodyAsChannel()
              file.outputStream().use { os ->
                channel.copyTo(channel = os.channel)
              }
            }
            file
          } catch (e: CancellationException) {
            throw e
          } catch (_: Throwable) {
            null
          }
        }
      }.awaitAll()
    }
    return files.filterNotNull().ifEmpty { null }
  }

  suspend fun uploadPhotoFromFile(
    segments: List<String>,
    file: File,
    remoteFileName: String,
    mimeType: String?,
  ) {
    val name = remoteFileName.trim().trimStart('/')
    if (name.isEmpty()) return
    ensureThumbnailsCollection(segments)
    val mime = mimeType ?: "application/octet-stream"
    val contentType = ContentType.Companion.parse(mime)
    val size = file.length().takeIf { it > 0L }
    file.inputStream().use { input ->
      val channel = input.toByteReadChannel()
      client().putStream(
        urlString = buildFileUrl(segments, name),
        contentLength = size,
        bodyContentType = contentType,
        body = channel,
      )
    }
    val thumbBytes = generateJpegThumbnail(file)
    if (thumbBytes.isNotEmpty()) {
      val thumbName = thumbnailSidecarFileName(name)
      val thumbUrl = buildFileUrl(segments + listOf(THUMBNAILS_SEGMENT), thumbName)
      ByteArrayInputStream(thumbBytes).use { thumbIn ->
        val thumbChannel = thumbIn.toByteReadChannel()
        client().putStream(
          urlString = thumbUrl,
          contentLength = thumbBytes.size.toLong(),
          bodyContentType = ContentType.Image.JPEG,
          body = thumbChannel,
        )
      }
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