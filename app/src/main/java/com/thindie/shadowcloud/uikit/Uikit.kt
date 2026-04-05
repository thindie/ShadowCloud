package com.thindie.shadowcloud.uikit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.thindie.shadowcloud.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thindie.shadowcloud.engine.Command
import com.thindie.shadowcloud.engine.ScreenScope
import com.thindie.shadowcloud.engine.ScreenScopeError
import com.thindie.shadowcloud.engine.ServiceCommand
import com.thindie.shadowcloud.engine.State

private object ContentAlpha {
  const val disabled: Float = 0.3f
}

@Composable
fun Button(
  modifier: Modifier = Modifier,
  text: String,
  onClick: () -> Unit,
  loading: Boolean = false,
  enabled: Boolean = true,
) {
  val contentColor by animateColorAsState(
    if (enabled) AppTheme.colors.buttonContentPrimary
    else AppTheme.colors.contentSecondary
  )

  val backgroundColor by animateColorAsState(
    if (enabled) AppTheme.colors.accentPrimary
    else AppTheme.colors.backgroundSecondary
  )
  Box(
    modifier = modifier
      .height(52.dp)
      .widthIn(min = 328.dp)
      .surface(
        shape = RoundedCornerShape(16.dp),
        backgroundColor = backgroundColor,
        shadowElevation = 0f,
        border = null,
        enabled = enabled && !loading,
        onClick = onClick
      )
      .fillMaxSize()
      .padding(horizontal = 24.dp),
    contentAlignment = Alignment.Center
  ) {
    AnimatedVisibility(
      visible = loading,
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      CircularProgress(
        modifier = Modifier.size(24.dp),
      )
    }

    AnimatedVisibility(
      visible = !loading,
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      Text(
        text = text.uppercase(),
        style = AppTheme.typography.button,
        color = contentColor,
        textAlign = TextAlign.Center
      )
    }
  }
}

@Stable
fun Modifier.surface(
  shape: Shape = RoundedCornerShape(16.dp),
  backgroundColor: Color = Color.Transparent,
  border: BorderStroke? = null,
  shadowElevation: Float = 0f,
  enabled: Boolean = true,
  onClick: (() -> Unit)? = null,
) = this
  .graphicsLayer(shadowElevation = shadowElevation, shape = shape, clip = false)
  .then(if (border != null) Modifier.border(border, shape) else Modifier)
  .background(color = backgroundColor, shape = shape)
  .clip(shape)
  .clickable(
    onClick = if (onClick != null && enabled) onClick else {
      { }
    },
    enabled = onClick != null && enabled,
  )


@Composable
fun <S : State, C : Command> ScreenScope<S, C>.ErrorMessage() {
  val error = this@ErrorMessage.error.value ?: return
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = error.message,
      style = AppTheme.typography.titleMedium,
    )
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.padding(top = 16.dp),
    ) {
      error.actions[ScreenScopeError.Actions.Common.DismissMain]?.let { cmd ->
        Button(
          text = stringResource(R.string.btn_close),
          onClick = {
            when {
              cmd as? ServiceCommand.Prioritized != null -> cmd.execute()
              else -> send(cmd as C)
            }
          },
          loading = processing.value == cmd
        )
      }
      error.actions[ScreenScopeError.Actions.Common.ButtonSecondaryRetry]?.let { cmd ->
        val action = error.actions.keys.filterIsInstance<ScreenScopeError.Actions.Common>()
          .first { it is ScreenScopeError.Actions.Common.ButtonSecondaryRetry }
        Button(
          text = action?.title.orEmpty(),
          onClick = {
            when {
              cmd as? ServiceCommand.Prioritized != null -> cmd.execute()
              else -> send(cmd as C)
            }
          },
          loading = processing.value == cmd
        )
      }
      error.actions[ScreenScopeError.Actions.Common.ButtonMain]?.let { cmd ->
        val action = error.actions.keys.filterIsInstance<ScreenScopeError.Actions.Common>()
          .first { it is ScreenScopeError.Actions.Common.ButtonMain }
        Button(
          text = action?.title.orEmpty(),
          onClick = {
            when {
              cmd as? ServiceCommand.Prioritized != null -> cmd.execute()
              else -> send(cmd as C)
            }
          },
          loading = processing.value == cmd
        )
      }
    }
  }
}

@Composable
fun SentenceRow(
  modifier: Modifier = Modifier,
  painter: Painter?,
  title: String,
  subtitle: String?,
  enabled: Boolean = true,
  loading: Boolean?,
  onClick: (() -> Unit)? = null,
  onLongClick: (() -> Unit)? = null,
) {
  val colorsPrimary = if (enabled) AppTheme.colors.backgroundPrimary else {
    AppTheme.colors.backgroundPrimary.copy(alpha = ContentAlpha.disabled)
  }

  val tint = if (enabled) AppTheme.colors.accentPrimary else {
    AppTheme.colors.accentPrimary.copy(alpha = ContentAlpha.disabled)
  }

  val colorsSecondary = if (enabled) AppTheme.colors.backgroundSecondary else {
    AppTheme.colors.backgroundSecondary.copy(alpha = ContentAlpha.disabled)
  }
  val interactionModifier = when {
    !enabled -> Modifier
    onLongClick != null && onClick != null -> Modifier.combinedClickable(
      onClick = onClick,
      onLongClick = onLongClick,
    )

    onClick != null -> Modifier.clickable(onClick = onClick)
    onLongClick != null -> Modifier.combinedClickable(
      onClick = { },
      onLongClick = onLongClick,
    )

    else -> Modifier
  }
  Row(
    modifier = modifier
      .background(color = colorsPrimary, shape = RoundedCornerShape(20.dp))
      .clip(shape = RoundedCornerShape(20.dp))
      .then(interactionModifier)
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(contentAlignment = Alignment.Center) {
      if (loading != null) {
        Crossfade(
          modifier = Modifier.size(40.dp),
          targetState = loading
        ) { loading ->
          if (loading) {
            CircularProgress(
              modifier = Modifier
                .padding(8.dp)
                .size(32.dp),
            )
          } else {
            if (painter == null) {
              HSpacer(40.dp)
            } else {
              Icon(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                  .background(color = colorsSecondary, shape = RoundedCornerShape(20.dp))
                  .padding(8.dp)
                  .size(32.dp),
                tint = tint
              )
            }
          }
        }
      } else {
        if (painter == null) {
          HSpacer(40.dp)
        } else {
          Icon(
            painter = painter,
            contentDescription = null,
            modifier = Modifier
              .background(color = colorsSecondary, shape = RoundedCornerShape(20.dp))
              .padding(8.dp)
              .size(32.dp),
            tint = tint
          )
        }
      }
    }
    HSpacer(12.dp)
    if (subtitle != null) {
      Column {
        Text(
          text = title,
          style = AppTheme.typography.titleMedium,
          color = AppTheme.colors.contentPrimary
        )
        VSpacer(2.dp)
        Text(
          text = subtitle,
          style = AppTheme.typography.bodyMedium,
          color = AppTheme.colors.contentSecondary
        )
      }
    } else {
      Text(
        text = title,
        style = AppTheme.typography.titleMedium,
        color = AppTheme.colors.contentPrimary
      )
    }
  }
}

@Composable
fun CircularProgress(modifier: Modifier = Modifier) {
  CircularProgressIndicator(
    modifier = modifier,
    color = AppTheme.colors.accentPrimary,
    strokeWidth = 1.2.dp,
    strokeCap = StrokeCap.Round
  )
}


@Immutable
data class Action(
  val listener: () -> Unit,
  val icon: Int,
)

@Composable
fun TopAppBar(
  title: String? = null,
  description: String? = null,
  primary: Action? = null,
  secondary: Action? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (primary != null) {
      IconButton(onClick = primary.listener) {
        Icon(
          painter = painterResource(primary.icon),
          contentDescription = null,
          tint = AppTheme.colors.accentPrimary,
        )
      }
    } else HSpacer(12.dp)
    if (description != null) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = title.orEmpty(),
          style = AppTheme.typography.titleLarge,
          color = AppTheme.colors.contentSecondary,
        )
        VSpacer(2.dp)
        Text(
          text = description,
          style = AppTheme.typography.labelLarge,
          color = AppTheme.colors.accentPrimary,
        )
      }
    } else {
      Text(
        text = title.orEmpty(),
        style = AppTheme.typography.titleLarge,
        color = AppTheme.colors.contentSecondary,
      )
    }
    if (secondary != null) {
      IconButton(onClick = secondary.listener) {
        Icon(
          painter = painterResource(secondary.icon),
          contentDescription = null,
          tint = AppTheme.colors.accentPrimary,
        )
      }
    } else HSpacer(12.dp)
  }
}
