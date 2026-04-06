package com.thindie.shadowcloud.feature.webdav

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.thindie.shadowcloud.R
import com.thindie.shadowcloud.application.Application
import com.thindie.shadowcloud.engine.Command
import com.thindie.shadowcloud.engine.Route
import com.thindie.shadowcloud.engine.RouteFactory
import com.thindie.shadowcloud.engine.Router
import com.thindie.shadowcloud.engine.ScreenFlow
import com.thindie.shadowcloud.engine.ScreenScope
import com.thindie.shadowcloud.engine.ScreenScopeError
import com.thindie.shadowcloud.engine.WorkState
import com.thindie.shadowcloud.engine.stateSink
import com.thindie.shadowcloud.error.AppError
import com.thindie.shadowcloud.feature.filedetails.FileDetailsFlow
import com.thindie.shadowcloud.feature.filedetails.FileDetailsParams
import com.thindie.shadowcloud.feature.webdav.common.Folder
import com.thindie.shadowcloud.feature.webdav.data.WebDavRepository
import com.thindie.shadowcloud.uikit.Action
import com.thindie.shadowcloud.uikit.AppScreen
import com.thindie.shadowcloud.uikit.AppTheme
import com.thindie.shadowcloud.uikit.Button
import com.thindie.shadowcloud.uikit.ShimmerBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WebDavFlow(
  private val router: Router,
  private val repository: WebDavRepository,
  private val appContext: Application,
) : ScreenFlow<Route, Unit>(router) {

  override fun start() {
    router.push(browseRoute(emptyList()))
  }

  fun stateSink(screenScope: ScreenScope<State, WebDavCommand>) {
    screenScope.stateSink { }
  }

  private fun browseRoute(segments: List<String>) = RouteFactory.create(
    initialState = State(segments = segments, items = emptyList()),
    execute = ::exec,
    stateSink = ::stateSink,
    routeContent = {
      WebDavScreenBody(
        thumbnailsUrl = { list, name -> repository.fileUrlForOriginal(list, name) },
        imageLoader = appContext.requireWebDavImageLoader(),
      )
    },
    errorMapper = { throwable ->
      ScreenScopeError(
        message = webDavErrorMessage(throwable),
        actions = mapOf(
          ScreenScopeError.Actions.Common.DismissMain to WebDavCommand.DismissError,
          ScreenScopeError.Actions.Common.ButtonSecondaryRetry to WebDavCommand.Refresh,
        ),
      )
    },
    initialCommand = RouteFactory.InitialCommand { WebDavCommand.Refresh },
  )

  private fun webDavErrorMessage(t: Throwable): String =
    when (t) {
      is AppError.WebDav.Unauthorized -> appContext.getString(R.string.webdav_error_unauthorized)
      is AppError.WebDav.Forbidden -> appContext.getString(R.string.webdav_error_forbidden)
      is AppError.WebDav.NotFound ->
        t.requestedUrl?.let { url ->
          appContext.getString(R.string.webdav_error_not_found_url, url)
        } ?: appContext.getString(R.string.webdav_error_not_found)

      is AppError.WebDav.Conflict -> appContext.getString(R.string.webdav_error_conflict)
      is AppError.WebDav.InvalidPropfindResponse -> appContext.getString(R.string.webdav_error_invalid_response)
      is AppError.WebDav.UploadOpenFailed -> appContext.getString(R.string.webdav_error_upload_open)
      else -> appContext.getString(R.string.error_unexpected)
    }

  @Immutable
  data class State(
    val segments: List<String> = emptyList(),
    val items: List<WebDavItem> = emptyList(),
  ) : com.thindie.shadowcloud.engine.State

  sealed interface WebDavCommand : Command {
    data object Back : WebDavCommand
    data object Refresh : WebDavCommand
    data object DismissError : WebDavCommand
    data class Open(val item: WebDavItem) : WebDavCommand
    data class Upload(val uri: Uri) : WebDavCommand
    data class Mkdir(val name: String) : WebDavCommand
    data class OpenPhoto(val fileName: String) : WebDavCommand
  }

  private suspend fun exec(command: WebDavCommand, state: State): State {
    return when (command) {
      WebDavCommand.Back -> {
        if (state.segments.isNotEmpty()) {
          back()
        } else {
          finish(Unit)
        }
        state
      }

      WebDavCommand.DismissError -> state

      WebDavCommand.Refresh -> {
        withContext(Dispatchers.IO) {
          val loaded = repository.listChildren(state.segments)
          state.copy(items = loaded)
        }
      }

      is WebDavCommand.Open -> {
        if (!command.item.isDirectory) return state
        val nextSegments = state.segments + command.item.name.trim().trim('/')
        go(browseRoute(nextSegments))
        state
      }

      is WebDavCommand.Upload -> {
        withContext(Dispatchers.IO) {
          val fileName = displayName(appContext, command.uri)
          repository.uploadPhoto(state.segments, command.uri, fileName)
          val loaded = repository.listChildren(state.segments)
          state.copy(items = loaded)
        }
      }

      is WebDavCommand.Mkdir -> {
        withContext(Dispatchers.IO) {
          val name = command.name.trim().trim('/')
          if (name.isNotEmpty()) {
            repository.createFolder(state.segments, name)
          }
          val loaded = repository.listChildren(state.segments)
          state.copy(items = loaded)
        }
      }

      is WebDavCommand.OpenPhoto -> {
        val url = repository.fileUrlForOriginal(state.segments, command.fileName)
        FileDetailsFlow(
          router = router,
          imageLoader = appContext.requireWebDavImageLoader(),
          appContext = appContext,
        ).start(FileDetailsParams.Photo(url))
        state
      }
    }
  }
}

private fun displayName(context: Context, uri: Uri): String {
  context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    ?.use { cursor ->
      if (cursor.moveToFirst()) {
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0) {
          val n = cursor.getString(idx)
          if (!n.isNullOrBlank()) return n
        }
      }
    }
  return "upload.bin"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ScreenScope<WebDavFlow.State, WebDavFlow.WebDavCommand>.WebDavScreenBody(
  imageLoader: ImageLoader,
  thumbnailsUrl: (segments: List<String>, name: String) -> String,
) {
  val st by state.collectAsState()
  val buckets = remember(st.items) { partitionForBrowse(st.items) }
  val activity = LocalActivity.current
  var mkdirOpen by remember { mutableStateOf(false) }
  var mkdirText by remember { mutableStateOf("") }

  val pickVisualMedia = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) {
      send(WebDavFlow.WebDavCommand.Upload(uri))
    }
  }

  AppScreen(
    title = stringResource(R.string.webdav_title),
    subtitle = breadcrumb(st.segments),
    primary = Action(
      icon = R.drawable.ic_arrow_back_24,
      listener = { send(WebDavFlow.WebDavCommand.Back) },
    ),
  ) {
    BackHandler { send(WebDavFlow.WebDavCommand.Back) }

    if (mkdirOpen) {
      AlertDialog(
        containerColor = AppTheme.colors.backgroundPrimary,
        onDismissRequest = { mkdirOpen = false },
        title = { Text(stringResource(R.string.webdav_new_folder)) },
        text = {
          OutlinedTextField(
            value = mkdirText,
            onValueChange = { mkdirText = it },
            label = { Text(stringResource(R.string.webdav_mkdir_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        },
        confirmButton = {
          Button(
            text = stringResource(R.string.webdav_confirm_mkdir),
            onClick = {
              send(WebDavFlow.WebDavCommand.Mkdir(mkdirText))
              mkdirOpen = false
              mkdirText = ""
            },
          )
        },
        dismissButton = {
          Button(
            text = stringResource(R.string.webdav_cancel),
            onClick = {
              mkdirOpen = false
              mkdirText = ""
            },
          )
        },
      )
    }

    Box(modifier = Modifier.fillMaxSize()) {
      PullToRefreshBox(
        isRefreshing = this@AppScreen.processing.value is WebDavFlow.WebDavCommand.Refresh,
        onRefresh = { send(WebDavFlow.WebDavCommand.Refresh) },
        modifier = Modifier.fillMaxSize(),
      ) {
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 104.dp),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          item(span = { GridItemSpan(maxLineSpan) }) {
            Button(
              modifier = Modifier.fillMaxWidth(),
              text = stringResource(R.string.webdav_new_folder),
              onClick = {
                mkdirText = ""
                mkdirOpen = true
              },
            )
          }

          items(
            items = buckets.folders,
            key = { it.path },
            span = { GridItemSpan(maxLineSpan) },
          ) { item ->
            Folder(
              modifier = Modifier
                .fillMaxWidth(),
              title = item.name + "/",
              onClick = { send(WebDavFlow.WebDavCommand.Open(item)) },
            )
          }

          if (st.items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
              Text(
                text = stringResource(R.string.webdav_empty),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.contentSecondary,
                modifier = Modifier.padding(vertical = 24.dp),
              )
            }
          } else if (buckets.folders.isEmpty() && buckets.imageFiles.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
              Text(
                text = stringResource(R.string.webdav_no_images_here),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.contentSecondary,
                modifier = Modifier.padding(vertical = 24.dp),
              )
            }
          } else {
            items(
              items = buckets.imageFiles,
              key = { it.path },
            ) { file ->
              val thumbUrl = thumbnailsUrl(st.segments, file.name)
              PhotoGridCell(
                thumbUrl = thumbUrl,
                imageLoader = imageLoader,
                onClick = { send(WebDavFlow.WebDavCommand.OpenPhoto(file.name)) },
              )
            }
          }
        }
      }

      FloatingActionButton(
        onClick = {
          if (activity != null) {
            pickVisualMedia.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
          }
        },
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(16.dp),
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_camera_32),
          contentDescription = stringResource(R.string.webdav_upload),
        )
      }
    }
  }
}

@Composable
private fun PhotoGridCell(
  thumbUrl: String,
  imageLoader: ImageLoader,
  onClick: () -> Unit,
) {
  var state by remember { mutableStateOf<WorkState>(WorkState.Running) }
  val context = LocalContext.current
  val tileShape = RoundedCornerShape(12.dp)
  Box(
    modifier = Modifier
      .aspectRatio(1f)
      .clip(tileShape)
      .border(
        border = BorderStroke(1.dp, AppTheme.colors.backgroundSecondary),
        shape = tileShape,
      )
      .clickable(onClick = onClick),
  ) {
    AnimatedContent(
      modifier = Modifier.fillMaxSize(),
      targetState = state
    ) { s ->
      when (s) {
        is WorkState.Error -> {
          Text(stringResource(R.string.photos))
        }

        WorkState.Idle,
        WorkState.Running,
          -> {
          AsyncImage(
            model = ImageRequest.Builder(context)
              .data(thumbUrl)
              .crossfade(true)
              .build(),
            contentDescription = null,
            imageLoader = imageLoader,
            modifier = Modifier
              .clip(tileShape)
              .fillMaxSize(),
            contentScale = ContentScale.Crop,
            onError = {
              state = WorkState.Error("AsyncImage loading fails")
            },
            onLoading = {
              state = WorkState.Running
            },
            onSuccess = {
              state = WorkState.Idle
            },
          )
          if (s is WorkState.Running) {
            ShimmerBox(modifier = Modifier.fillMaxSize())
          }
        }

        WorkState.NotStarted -> ShimmerBox(modifier = Modifier.fillMaxSize())
      }
    }
  }
}

private fun breadcrumb(segments: List<String>): String {
  if (segments.isEmpty()) return "/"
  return "/" + segments.joinToString("/")
}
