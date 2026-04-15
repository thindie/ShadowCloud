package com.thindie.shadowcloud.uikit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thindie.shadowcloud.engine.Command
import com.thindie.shadowcloud.engine.ScreenScope
import com.thindie.shadowcloud.engine.ServiceCommand
import com.thindie.shadowcloud.engine.State
import kotlinx.coroutines.delay

@Composable
fun <S : State, C : Command> ScreenScope<S, C>.AppScreen(
  modifier: Modifier = Modifier,
  title: String? = null,
  subtitle: String? = null,
  primary: Action? = null,
  secondary: Action? = null,
  handleError: Boolean = true,
  handleLoading: Boolean = true,
  content: @Composable ScreenScope<S, C>.() -> Unit,
) {
  AnimatedContent(
    modifier = modifier
      .background(AppTheme.colors.backgroundPrimary)
    , targetState = this
  ) { screenScope ->
    if (error.value != null && handleError) {
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

        var showEvent by remember { mutableStateOf<ServiceCommand.UiEvent?>(null) }
        LaunchedEffect(screenScope) {
          event
            .collect {
              showEvent = it
            }
        }
        if (showEvent != null) {
          when (showEvent) {
            is ServiceCommand.UiEvent.Decision -> {
              Dialog(
                onDismiss = {
                  showEvent = null
                },
                content = {
                  (showEvent as ServiceCommand.UiEvent.Decision).content.invoke()
                },
                primary = (showEvent as ServiceCommand.UiEvent.Decision).primaryAction.let {
                  it.copy(
                    listener = {
                      it.listener()
                      showEvent = null
                    }
                  )
                },
                secondary = (showEvent as ServiceCommand.UiEvent.Decision).secondaryAction?.let {
                  it.copy(
                    listener = {
                      it.listener()
                      showEvent = null
                    }
                  )
                }
              )
            }

            is ServiceCommand.UiEvent.Snack -> {
              LaunchedEffect(showEvent) {
                delay(2000)
                showEvent = null
              }
              AnimatedVisibility(
                modifier = Modifier
                  .clickable(
                    onClick = {
                      (showEvent as ServiceCommand.UiEvent.Snack).action.listener.invoke()
                    },
                    indication = null,
                    interactionSource = null
                  )
                  .fillMaxWidth()
                  .padding(top = 56.dp)
                  .align(Alignment.TopStart)
                  .padding(all = 16.dp)
                  .background(AppTheme.colors.accentPrimary, shape = RoundedCornerShape(16.dp))
                  .padding(all = 16.dp),
                visible = showEvent is ServiceCommand.UiEvent.Snack
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(
                    text = stringResource((showEvent as ServiceCommand.UiEvent.Snack).action.resRef),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.onAccentPrimary,
                  )
                }
              }
            }

            null -> error("Must not be reached")
          }
        }
        if (this@AppScreen.processing.value != null && handleLoading) {
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