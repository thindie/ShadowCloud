package com.thindie.shadowcloud.feature.auth

import com.thindie.shadowcloud.application.AppVersion
import com.thindie.shadowcloud.application.SemanticVersion
import com.thindie.shadowcloud.error.AppError
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

private const val USERNAME = "USERNAME"
private const val PWD = "PWD"

internal const val URL = "URL"

internal fun apkUrlAdjacentToVersionFile(versionFileUrl: String): String {
  val slash = versionFileUrl.lastIndexOf('/')
  val base = if (slash >= 0) versionFileUrl.substring(0, slash + 1) else "$versionFileUrl/"
  return "${base}ShadowCloud.apk"
}

internal fun newAuthenticatedWebdavClient(userName: String, password: String): HttpClient =
  HttpClient(CIO) {
    install(Auth) {
      basic {
        credentials {
          BasicAuthCredentials(username = userName, password = password)
        }
        sendWithoutRequest { true }
      }
    }
  }

sealed class AuthGateResult {
  data object Allowed : AuthGateResult()
  data class AllowedWithUpdate(
    val remoteVersionRaw: String,
    val apkUrl: String,
  ) : AuthGateResult()
}

/**
 * Pure parsing / comparison of the version file body (already trimmed). For unit tests.
 */
internal fun authGateResultForVersionResponse(
  responseBodyTrimmed: String,
  localSemver: String,
  versionCheckUrl: String,
): AuthGateResult {
  if (responseBodyTrimmed.isEmpty()) {
    throw AppError.ServerError.RestrictedByOwner
  }
  if (responseBodyTrimmed.equals("ok", ignoreCase = true)) {
    return AuthGateResult.Allowed
  }
  val remote = SemanticVersion.parse(responseBodyTrimmed)
    ?: throw AppError.ServerError.RestrictedByOwner
  val local = SemanticVersion.parse(localSemver)
    ?: return AuthGateResult.Allowed
  return if (remote > local) {
    AuthGateResult.AllowedWithUpdate(
      remoteVersionRaw = responseBodyTrimmed,
      apkUrl = apkUrlAdjacentToVersionFile(versionCheckUrl),
    )
  } else {
    AuthGateResult.Allowed
  }
}

suspend fun performAuthGate(
  userName: String = USERNAME,
  password: String = PWD,
  url: String = URL,
): AuthGateResult {
  val client = newAuthenticatedWebdavClient(userName, password)
  return client.use { client ->
    val response = withTimeoutOrNull(5_000L) {
      client.get(url)
    }
    if (response == null) {
      throw AppError.ServerError.TimeOut
    }
    val body = try {
      response.bodyAsText().trim()
    } catch (_: IOException) {
      throw AppError.ServerError.TimeOut
    }
    authGateResultForVersionResponse(
      responseBodyTrimmed = body,
      localSemver = AppVersion.SEMVER,
      versionCheckUrl = url
    )
  }
}
