package com.thindie.shadowcloud.feature.webdav

import com.thindie.shadowcloud.error.AppError
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel

private val PROPFIND_BODY =
  """
  <?xml version="1.0" encoding="utf-8"?>
  <propfind xmlns="DAV:">
    <prop>
      <resourcetype/>
      <getcontentlength/>
      <displayname/>
    </prop>
  </propfind>
  """.trimIndent()

private fun HttpStatusCode.isSuccessRange(): Boolean = value in 200..299

internal sealed class MkcolResult {
  data object Created : MkcolResult()
  data object AlreadyExists : MkcolResult()
  data class Failed(val status: HttpStatusCode) : MkcolResult()
}

internal fun webDavErrorFromStatus(
  status: HttpStatusCode,
  requestedUrl: String? = null,
): AppError.WebDav =
  when (status) {
    HttpStatusCode.Unauthorized -> AppError.WebDav.Unauthorized
    HttpStatusCode.Forbidden -> AppError.WebDav.Forbidden
    HttpStatusCode.NotFound -> AppError.WebDav.NotFound(requestedUrl = requestedUrl)
    HttpStatusCode.Conflict -> AppError.WebDav.Conflict
    HttpStatusCode.MethodNotAllowed -> AppError.WebDav.Conflict
    else -> AppError.WebDav.InvalidPropfindResponse
  }

internal suspend fun HttpClient.propfindDepth1(urlString: String): String {
  val response: HttpResponse =
    try {
      request(urlString) {
        method = HttpMethod("PROPFIND")
        header("Depth", "1")
        header(HttpHeaders.ContentType, ContentType.Application.Xml.toString())
        setBody(PROPFIND_BODY)
      }
    } catch (re: ResponseException) {
      val url = re.response.call.request.url.toString()
      throw webDavErrorFromStatus(re.response.status, url)
    }
  if (response.status != HttpStatusCode.MultiStatus && response.status != HttpStatusCode.OK) {
    val url = response.call.request.url.toString()
    throw webDavErrorFromStatus(response.status, url)
  }
  return response.bodyAsText()
}

internal suspend fun HttpClient.putStream(
  urlString: String,
  contentLength: Long?,
  bodyContentType: ContentType,
  body: ByteReadChannel,
) {
  val response: HttpResponse =
    try {
      put(urlString) {
        header(HttpHeaders.ContentType, bodyContentType.toString())
        if (contentLength != null && contentLength >= 0L) {
          header(HttpHeaders.ContentLength, contentLength.toString())
        }
        setBody(body)
      }
    } catch (re: ResponseException) {
      val url = re.response.call.request.url.toString()
      throw webDavErrorFromStatus(re.response.status, url)
    }
  if (!response.status.isSuccessRange()) {
    val url = response.call.request.url.toString()
    throw webDavErrorFromStatus(response.status, url)
  }
}

internal suspend fun HttpClient.mkcol(urlString: String): MkcolResult {
  val response: HttpResponse =
    try {
      request(urlString) {
        method = HttpMethod("MKCOL")
      }
    } catch (exception: ResponseException) {
      when (exception.response.status) {
        HttpStatusCode.Conflict, HttpStatusCode.MethodNotAllowed -> return MkcolResult.AlreadyExists
        else -> {
          val url = exception.response.call.request.url.toString()
          throw webDavErrorFromStatus(exception.response.status, url)
        }
      }
    }
  return when (response.status) {
    HttpStatusCode.Created, HttpStatusCode.NoContent -> MkcolResult.Created
    HttpStatusCode.Conflict, HttpStatusCode.MethodNotAllowed -> MkcolResult.AlreadyExists
    else ->
      if (response.status.isSuccessRange()) {
        MkcolResult.Created
      } else {
        MkcolResult.Failed(response.status)
      }
  }
}
