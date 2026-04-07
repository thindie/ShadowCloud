package com.thindie.shadowcloud.feature.webdav.common

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.request.ImageRequest
import com.thindie.shadowcloud.R
import com.thindie.shadowcloud.uikit.AppTheme
import com.thindie.shadowcloud.uikit.SentenceRow

@Composable
fun Folder(
  modifier: Modifier = Modifier,
  title: String,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)? = null,
) {
  SentenceRow(
    modifier = modifier
      .border(
        color = AppTheme.colors.contentSecondary,
        width = 1.4.dp,
        shape = RoundedCornerShape(16.dp)
      ),
    painter = painterResource(R.drawable.ic_folder_24),
    title = title,
    subtitle = null,
    onClick = onClick,
    onLongClick = onLongClick,
    loading = false
  )
}

val LocalImageLoader = staticCompositionLocalOf<ImageLoader> {
  error("No ImageLoader Provided")
}

@Composable
fun rememberImageRequest(url: String): ImageRequest {
  val context = LocalContext.current
  return remember(url, context) {
    ImageRequest.Builder(context)
      .data(url)
      .crossfade(false)
      .build()
  }
}
