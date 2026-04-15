package com.thindie.shadowcloud.feature.webdav

import androidx.compose.runtime.Immutable

@Immutable
data class WebDavItem(
  val name: String,
  val isDirectory: Boolean,
  val size: Long,
  val path: String,
  val previewPath: String?
)
