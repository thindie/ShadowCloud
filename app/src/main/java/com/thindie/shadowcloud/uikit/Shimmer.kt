package com.thindie.shadowcloud.uikit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
  val transition = rememberInfiniteTransition(label = "shimmer")
  val shift by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1100, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "shimmer_shift",
  )
  val base = AppTheme.colors.backgroundSecondary
  val highlight = AppTheme.colors.backgroundPrimary.copy(alpha = 0.42f)
  val brush = Brush.linearGradient(
    colors = listOf(base, highlight, base),
    start = Offset(x = shift * 900f - 350f, y = 0f),
    end = Offset(x = shift * 900f + 250f, y = 420f),
  )
  Box(modifier = modifier.background(brush))
}
