package com.thindie.shadowcloud.uikit

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

@Stable
class ZoomState internal constructor(
  private val minScale: Float,
  private val maxScale: Float,
) {
  var scale: Float by mutableFloatStateOf(1f)
    private set

  var offset: Offset by mutableStateOf(Offset.Zero)
    private set

  private var viewportSize: IntSize by mutableStateOf(IntSize.Zero)

  val isZoomed: Boolean
    get() = scale > 1f + 0.001f

  internal fun onViewportChanged(size: IntSize) {
    viewportSize = size
    clamp()
  }

  internal fun onTransform(pan: Offset, zoom: Float) {
    val nextScale = (scale * zoom).coerceIn(minScale, maxScale)
    scale = nextScale

    offset += pan
    clamp()
  }

  private fun clamp() {
    val w = viewportSize.width.toFloat()
    val h = viewportSize.height.toFloat()
    if (w <= 0f || h <= 0f) {
      if (scale <= 1f) offset = Offset.Zero
      return
    }

    if (scale <= 1f + 0.001f) {
      scale = 1f
      offset = Offset.Zero
      return
    }

    val maxX = w * (scale - 1f) / 2f
    val maxY = h * (scale - 1f) / 2f

    val clampedX = offset.x.coerceIn(-maxX, maxX)
    val clampedY = offset.y.coerceIn(-maxY, maxY)
    offset = Offset(clampedX, clampedY)

    if (abs(offset.x) < 0.01f && abs(offset.y) < 0.01f) {
      offset = Offset.Zero
    }
  }
}

@Composable
fun rememberZoomState(
  key: Any?,
  minScale: Float,
  maxScale: Float,
): ZoomState {
  return remember(key, minScale, maxScale) {
    ZoomState(
      minScale = minScale,
      maxScale = maxScale,
    )
  }
}

fun Modifier.pinchZoom(
  state: ZoomState,
  enabled: Boolean,
): Modifier = this
  .let { Modifier ->
    if (enabled) {
      Modifier
        .onSizeChanged { state.onViewportChanged(it) }
        .graphicsLayer {
          scaleX = state.scale
          scaleY = state.scale
          translationX = state.offset.x
          translationY = state.offset.y
        }
        .pointerInput(state) {
          detectTransformGestures { _, pan, zoom, _ ->
            state.onTransform(
              pan = pan,
              zoom = zoom,
            )
          }
        }
    } else this
  }

