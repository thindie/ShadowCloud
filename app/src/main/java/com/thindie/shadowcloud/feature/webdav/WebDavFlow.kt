package com.thindie.shadowcloud.feature.webdav

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thindie.shadowcloud.R
import com.thindie.shadowcloud.application.Application
import com.thindie.shadowcloud.engine.Command
import com.thindie.shadowcloud.engine.Route
import com.thindie.shadowcloud.engine.RouteFactory
import com.thindie.shadowcloud.engine.Router
import com.thindie.shadowcloud.engine.ScreenFlow
import com.thindie.shadowcloud.engine.ScreenScope
import com.thindie.shadowcloud.engine.ScreenScopeError
import com.thindie.shadowcloud.engine.stateSink
import com.thindie.shadowcloud.error.AppError
import com.thindie.shadowcloud.uikit.Action
import com.thindie.shadowcloud.uikit.AppScreen
import com.thindie.shadowcloud.uikit.AppTheme
import com.thindie.shadowcloud.uikit.Button
import com.thindie.shadowcloud.uikit.SentenceRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WebDavFlow(
  private val router: Router,
  private val repository: WebDavRepository,
  private val appContext: Application,
) : ScreenFlow<Route, Unit>(router) {

  init {
    onFinishBuilder {
      repository.close()
    }
  }

  override fun start() {
    router.push(main())
  }

  fun stateSink(screenScope: ScreenScope<State, WebDavCommand>) {
    screenScope.stateSink { }
  }

  fun main() = RouteFactory.create(
    initialState = State(),
    execute = ::exec,
    stateSink = ::stateSink,
    routeContent = { WebDavScreen() },
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
    data object Up : WebDavCommand
    data class Upload(val uri: Uri) : WebDavCommand
    data class Mkdir(val name: String) : WebDavCommand
  }

  private suspend fun exec(command: WebDavCommand, state: State): State {
    return when (command) {
      WebDavCommand.Back -> {
        finish(Unit)
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
        withContext(Dispatchers.IO) {
          if (!command.item.isDirectory) return@withContext state
          val nextSegments = state.segments + command.item.name.trim().trim('/')
          val loaded = repository.listChildren(nextSegments)
          state.copy(segments = nextSegments, items = loaded)
        }
      }

      WebDavCommand.Up -> {
        withContext(Dispatchers.IO) {
          if (state.segments.isEmpty()) return@withContext state
          val nextSegments = state.segments.dropLast(1)
          val loaded = repository.listChildren(nextSegments)
          state.copy(segments = nextSegments, items = loaded)
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenScope<WebDavFlow.State, WebDavFlow.WebDavCommand>.WebDavScreen() {
  val st by state.collectAsState()
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

    PullToRefreshBox(
      isRefreshing = this@AppScreen.processing.value is WebDavFlow.WebDavCommand.Refresh,
      onRefresh = { send(WebDavFlow.WebDavCommand.Refresh) },
    ) {
      LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Button(
              modifier = Modifier.fillMaxWidth(),
              text = stringResource(R.string.webdav_upload),
              onClick = {
                if (activity != null) {
                  pickVisualMedia.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                  )
                }
              },
            )
            Button(
              modifier = Modifier.fillMaxWidth(),
              text = stringResource(R.string.webdav_new_folder),
              onClick = {
                mkdirText = ""
                mkdirOpen = true
              },
            )
            if (st.segments.isNotEmpty()) {
              Button(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.webdav_up),
                onClick = { send(WebDavFlow.WebDavCommand.Up) },
              )
            }
          }
        }
        if (st.items.isEmpty()) {
          item {
            Text(
              text = stringResource(R.string.webdav_empty),
              style = AppTheme.typography.bodyMedium,
              color = AppTheme.colors.contentSecondary,
              modifier = Modifier.padding(vertical = 24.dp),
            )
          }
        } else {
          items(st.items, key = { it.path }) { item ->
            SentenceRow(
              modifier = Modifier
                .border(
                  border = BorderStroke(
                    width = 1.2.dp,
                    color = AppTheme.colors.backgroundSecondary,
                  ),
                  shape = RoundedCornerShape(20.dp),
                )
                .fillMaxWidth(),
              painter = null,
              title = item.name + if (item.isDirectory) "/" else "",
              subtitle = if (item.isDirectory) null else formatSize(item.size),
              loading = false,
              onClick = { send(WebDavFlow.WebDavCommand.Open(item)) },
            )
          }
        }
      }
    }
  }
}

private fun breadcrumb(segments: List<String>): String {
  if (segments.isEmpty()) return "/"
  return "/" + segments.joinToString("/")
}

private fun formatSize(bytes: Long): String {
  if (bytes <= 0L) return "—"
  val kb = bytes / 1024.0
  return if (kb < 1024) {
    "%.1f KB".format(kb)
  } else {
    "%.1f MB".format(kb / 1024.0)
  }
}
