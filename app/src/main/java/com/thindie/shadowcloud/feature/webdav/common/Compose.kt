package com.thindie.shadowcloud.feature.webdav.common

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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