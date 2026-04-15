package com.thindie.shadowcloud.error

sealed class AppError : Exception() {
  data class UnexpectedError(
    override val cause: Throwable?,
    override val message: String?,
  ) : AppError()

  sealed class ServerError : AppError() {
    data object RestrictedByOwner : ServerError()
    data object TimeOut : ServerError()
  }

  sealed class WebDav : AppError() {
    data object Unauthorized : WebDav()
    data object Forbidden : WebDav()
    data class NotFound(val requestedUrl: String? = null) : WebDav()
    data object Conflict : WebDav()
    data object InvalidPropfindResponse : WebDav()
    data object UploadOpenFailed : WebDav()
  }
}
