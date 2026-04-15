package com.thindie.shadowcloud.feature.filedetails

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.thindie.shadowcloud.R
import com.thindie.shadowcloud.engine.Command
import com.thindie.shadowcloud.engine.Route
import com.thindie.shadowcloud.engine.RouteFactory
import com.thindie.shadowcloud.engine.Router
import com.thindie.shadowcloud.engine.ScreenFlow
import com.thindie.shadowcloud.engine.ScreenScope
import com.thindie.shadowcloud.engine.State
import com.thindie.shadowcloud.feature.webdav.common.rememberImageRequest
import com.thindie.shadowcloud.uikit.Action
import com.thindie.shadowcloud.uikit.AppScreen
import com.thindie.shadowcloud.uikit.AppTheme
import com.thindie.shadowcloud.uikit.pinchZoom
import com.thindie.shadowcloud.uikit.rememberZoomState

class FileDetailsFlow(
  private val router: Router,
  private val imageLoader: ImageLoader,
  private val params: FileDetailsParams,
) : ScreenFlow<Route, Unit>(router) {

  override fun start() {
    router.push(
      details(params)
    )
  }


  fun details(params: FileDetailsParams): Route {
    return RouteFactory.create(
      initialState = FileDetailsState(params),
      execute = ::execute,
      routeContent = {
        FileDetailsRouteContent(
          imageLoader = imageLoader,
        )
      },
    )
  }

  fun execute(command: FileDetailsCommand, state: FileDetailsState): FileDetailsState {
    return when (command) {
      FileDetailsCommand.Back -> {
        back()
        state
      }

      FileDetailsCommand.Next -> {
        when (val p = state.params) {
          is FileDetailsParams.Document -> Unit
          is FileDetailsParams.Other -> Unit
          is FileDetailsParams.Pdf -> Unit
          is FileDetailsParams.Photo -> {
            val next = p.index + 1
            if (next in p.imageUrls.indices) {
              go(
                details(
                  FileDetailsParams.Photo(
                    segments = p.segments,
                    imageUrls = p.imageUrls,
                    index = next,
                  ),
                )
              )
            }
          }
        }
        state
      }

      FileDetailsCommand.Finish -> {
        finish(Unit)
        state
      }
    }
  }
}

@Immutable
data class FileDetailsState(val params: FileDetailsParams) : State

sealed interface FileDetailsCommand : Command {
  data object Back : FileDetailsCommand
  data object Finish : FileDetailsCommand
  data object Next : FileDetailsCommand
}

@Composable
private fun ScreenScope<FileDetailsState, FileDetailsCommand>.FileDetailsRouteContent(
  imageLoader: ImageLoader,
) {
  val st by state.collectAsState()
  AppScreen(
    primary = Action(
      resRef = R.drawable.ic_arrow_back_24,
      listener = { send(FileDetailsCommand.Finish) },
    ),
  ) {
    BackHandler { send(FileDetailsCommand.Back) }
    when (val p = st.params) {
      is FileDetailsParams.Photo -> {
        val url = p.imageUrls.getOrNull(p.index)
        val zoomState = url?.let {
          rememberZoomState(
            key = it,
            minScale = 1f,
            maxScale = 4f,
          )
        }
        Box(modifier = Modifier.fillMaxSize()) {
          if (url != null && zoomState != null) {
            AnimatedVisibility(
              true,
              enter = fadeIn(
                animationSpec = tween(
                  durationMillis = 800,
                  easing = LinearOutSlowInEasing,
                )
              )
            ) {
              AsyncImage(
                modifier = Modifier
                  .blur(80.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)
                  .fillMaxSize(),
                model = rememberImageRequest(url),
                contentDescription = null,
                imageLoader = imageLoader,
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.None,
              )
            }

            AsyncImage(
              modifier = Modifier
                .align(Alignment.Center)
                .pinchZoom(state = zoomState, enabled = true)
                .fillMaxSize(),
              model = rememberImageRequest(url),
              contentDescription = null,
              imageLoader = imageLoader,
              contentScale = ContentScale.Fit,
            )
          }
          val enableEdgeNavigation = zoomState?.isZoomed != true
          Box(
            modifier = Modifier
              .align(Alignment.CenterStart)
              .fillMaxHeight()
              .fillMaxWidth(0.2f)
              .clickable(
                enabled = enableEdgeNavigation,
                onClick = {
                  send(FileDetailsCommand.Back)
                },
                indication = null,
                interactionSource = null,
              )
          )
          Box(
            modifier = Modifier
              .align(Alignment.CenterEnd)
              .fillMaxHeight()
              .fillMaxWidth(0.2f)
              .clickable(
                enabled = enableEdgeNavigation,
                onClick = {
                  send(FileDetailsCommand.Next)
                },
                indication = null,
                interactionSource = null,
              )
          )
        }
      }

      is FileDetailsParams.Document -> StubDetails(
        text = stringResource(R.string.file_details_stub_document, p.title),
      )

      is FileDetailsParams.Pdf -> StubDetails(
        text = stringResource(R.string.file_details_stub_pdf, p.title),
      )

      is FileDetailsParams.Other -> StubDetails(
        text = stringResource(R.string.file_details_stub_other, p.label),
      )
    }
  }
}

@Composable
private fun StubDetails(text: String) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = AppTheme.typography.bodyMedium,
      color = AppTheme.colors.contentSecondary,
    )
  }
}
