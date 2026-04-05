package com.thindie.shadowcloud.uikit

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thindie.shadowcloud.engine.Command
import com.thindie.shadowcloud.engine.ScreenScope
import com.thindie.shadowcloud.engine.State

@Composable
fun <S : State, C : Command> ScreenScope<S, C>.AppScreen(
  modifier: Modifier = Modifier,
  title: String? = null,
  subtitle: String? = null,
  primary: Action? = null,
  secondary: Action? = null,
  content: @Composable ScreenScope<S, C>.() -> Unit,
) {
  AnimatedContent(
    modifier = modifier
      .background(AppTheme.colors.backgroundPrimary)
    , targetState = this
  ) { screenScope ->
    if (error.value != null) {
      ErrorMessage()
    } else {
      Box(
        Modifier
          .fillMaxSize()
          .background(AppTheme.colors.backgroundPrimary)
      ) {
        Column(
          modifier = Modifier.systemBarsPadding()
        ) {
          TopAppBar(
            title = title,
            description = subtitle,
            primary = primary,
            secondary = secondary
          )
          content(screenScope)
        }
        if (this@AppScreen.processing.value != null) {
          Box(
            Modifier
              .fillMaxSize()
              .background(
                Color.Transparent.copy(alpha = 0.3f)
              )
              .clickable(onClick = {}, enabled = false)
          ) {
            CircularProgress(
              modifier = Modifier
                .align(Alignment.Center)
                .background(
                  color = AppTheme.colors.backgroundSecondary,
                  shape = RoundedCornerShape(20.dp)
                )
                .padding(16.dp)
            )
          }
        }
      }
    }
  }
}