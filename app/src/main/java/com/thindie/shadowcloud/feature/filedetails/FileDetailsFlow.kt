package com.thindie.shadowcloud.feature.filedetails

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.thindie.shadowcloud.R
import com.thindie.shadowcloud.engine.Command
import com.thindie.shadowcloud.engine.RouteFactory
import com.thindie.shadowcloud.engine.Router
import com.thindie.shadowcloud.engine.ScreenScope
import com.thindie.shadowcloud.engine.State
import com.thindie.shadowcloud.uikit.Action
import com.thindie.shadowcloud.uikit.AppScreen
import com.thindie.shadowcloud.uikit.AppTheme

@Immutable
data class FileDetailsState(val params: FileDetailsParams) : State

sealed interface FileDetailsCommand : Command {
  data object Back : FileDetailsCommand
}

class FileDetailsFlow(
  private val router: Router,
  private val imageLoader: ImageLoader,
  private val appContext: Context,
) {

  fun start(params: FileDetailsParams) {
    router.push(
      RouteFactory.create(
        initialState = FileDetailsState(params),
        execute = { command, state ->
          when (command) {
            FileDetailsCommand.Back -> {
              router.pop()
              state
            }
          }
        },
        routeContent = {
          FileDetailsRouteContent(
            imageLoader = imageLoader,
            appContext = appContext,
          )
        },
      ),
    )
  }
}

@Composable
private fun ScreenScope<FileDetailsState, FileDetailsCommand>.FileDetailsRouteContent(
  imageLoader: ImageLoader,
  appContext: Context,
) {
  val st by state.collectAsState()
  AppScreen(
    primary = Action(
      icon = R.drawable.ic_arrow_back_24,
      listener = { send(FileDetailsCommand.Back) },
    ),
  ) {
    BackHandler { send(FileDetailsCommand.Back) }
    when (val p = st.params) {
      is FileDetailsParams.Photo -> {
        AsyncImage(
          model = ImageRequest.Builder(appContext)
            .data(p.imageUrl)
            .crossfade(true)
            .build(),
          contentDescription = null,
          imageLoader = imageLoader,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Fit,
        )
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
