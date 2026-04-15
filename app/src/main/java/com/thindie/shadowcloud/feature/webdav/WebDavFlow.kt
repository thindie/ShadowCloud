package com.thindie.shadowcloud.feature.webdav

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.thindie.shadowcloud.R
import com.thindie.shadowcloud.application.Application
import com.thindie.shadowcloud.engine.Command
import com.thindie.shadowcloud.engine.Route
import com.thindie.shadowcloud.engine.RouteFactory
import com.thindie.shadowcloud.engine.Router
import com.thindie.shadowcloud.engine.ScreenFlow
import com.thindie.shadowcloud.engine.ScreenScope
import com.thindie.shadowcloud.engine.ScreenScopeError
import com.thindie.shadowcloud.engine.ServiceCommand
import com.thindie.shadowcloud.engine.WorkState
import com.thindie.shadowcloud.engine.stateSink
import com.thindie.shadowcloud.error.AppError
import com.thindie.shadowcloud.feature.filedetails.FileDetailsFlow
import com.thindie.shadowcloud.feature.filedetails.FileDetailsParams
import com.thindie.shadowcloud.feature.webdav.common.Folder
import com.thindie.shadowcloud.feature.webdav.common.LocalImageLoader
import com.thindie.shadowcloud.feature.webdav.common.rememberImageRequest
import com.thindie.shadowcloud.feature.webdav.data.WebDavRepository
import com.thindie.shadowcloud.uikit.Action
import com.thindie.shadowcloud.uikit.AppScreen
import com.thindie.shadowcloud.uikit.AppTheme
import com.thindie.shadowcloud.uikit.SentenceRow
import com.thindie.shadowcloud.uikit.ShimmerBox
import com.thindie.shadowcloud.uikit.VSpacer
import com.thindie.shadowcloud.uikit.surface
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
    routeContent = { WebDavScreenBody() },
    errorMapper = { throwable ->
      ScreenScopeError(
        message = webDavErrorMessage(throwable),
        actions = mapOf(
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
    val selectedItems: Set<WebDavItem> = emptySet(),
    val openActionBottomSheet: Boolean = false,
    val mkdirDraft: String = "",
    val pendingIntent: Intent? = null,
  ) : com.thindie.shadowcloud.engine.State

  sealed interface WebDavCommand : Command {
    data object Back : WebDavCommand
    data object Refresh : WebDavCommand
    data object OpenSelectionActionSheet : WebDavCommand
    data object DismissSelectionActionSheet : WebDavCommand
    data object CancelSelectionAndDismissSheet : WebDavCommand
    data class SetMkdirDraft(val text: String) : WebDavCommand
    data class Open(val item: WebDavItem) : WebDavCommand
    data class Upload(val uri: Uri) : WebDavCommand
    data class Mkdir(val name: String) : WebDavCommand
    data class OpenPhoto(val fileName: String) : WebDavCommand
    data class ToggleImageSelection(val item: WebDavItem) : WebDavCommand
    data object ClearImageSelection : WebDavCommand
    data class ResolveClipboardPath(val data: ClipData) : WebDavCommand

    data object Download : WebDavCommand
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


      WebDavCommand.OpenSelectionActionSheet -> state.copy(openActionBottomSheet = true)
      WebDavCommand.DismissSelectionActionSheet -> {
        state.copy(openActionBottomSheet = false, pendingIntent = null)
      }
      WebDavCommand.CancelSelectionAndDismissSheet ->
        state.copy(selectedItems = emptySet(), openActionBottomSheet = false)

      is WebDavCommand.SetMkdirDraft -> state.copy(mkdirDraft = command.text)

      WebDavCommand.Refresh -> {
        withContext(Dispatchers.IO) {
          val loaded = repository.listChildren(state.segments)
          state.withRefreshedItems(loaded)
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
          state.withRefreshedItems(loaded)
        }
      }

      is WebDavCommand.ResolveClipboardPath -> {
        withContext(Dispatchers.IO) {
          val resolved = resolveClipboardForWebDavMove(
            context = appContext,
            clip = command.data,
            webDavBaseUrl = appContext.requireWebDav()
          ) ?: return@withContext state.copy(openActionBottomSheet = false)
          val selected = state.selectedItems.map { it.path }.toSet()
          repository.move(
            selected,
            destination = resolved,
            overwrite = true
          )
          val loaded = repository.listChildren(state.segments)
          state.withRefreshedItems(loaded).copy(
            openActionBottomSheet = false,
          )
        }
      }

      is WebDavCommand.Mkdir -> {
        withContext(Dispatchers.IO) {
          val name = command.name.trim().trim('/')
          if (name.isNotEmpty()) {
            repository.createFolder(state.segments, name)
          }
          val loaded = repository.listChildren(state.segments)
          state.withRefreshedItems(loaded).copy(mkdirDraft = "")
        }
      }

      is WebDavCommand.OpenPhoto -> {
        val buckets = partitionForBrowse(state.items)
        val imageFiles = buckets.imageFiles
        if (imageFiles.isEmpty()) return state
        val idx = imageFiles.indexOfFirst { it.name == command.fileName }
        if (idx < 0) return state
        val urls = imageFiles.map { repository.fileUrlForOriginal(state.segments, it.name) }
        FileDetailsFlow(
          router = router,
          imageLoader = appContext.requireImageLoader(),
          params = FileDetailsParams.Photo(
            segments = state.segments,
            imageUrls = urls,
            index = idx,
          ),
        ).start()
        state
      }

      is WebDavCommand.ToggleImageSelection -> {
        val buckets =
          partitionForBrowse(state.items)
        if (command.item !in buckets.imageFiles) return state
        val next =
          if (command.item in state.selectedItems) {
            state.selectedItems - command.item
          } else {
            state.selectedItems + command.item
          }
        state.copy(selectedItems = next)
      }

      is WebDavCommand.Download -> {
        val cacheDir = appContext.cacheDir
        val files = repository.download(
          items = state.selectedItems.toList(),
          destinationDir = cacheDir,
          segments = state.segments
        )
        if (files != null) {
          if (files.size > 1) {
            val uris = files.map { file ->
              FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.provider",
                file
              )
            }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
              type = "*/*"
              putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            }
            state.copy(
              pendingIntent = Intent.createChooser(
                intent,
                "Отправить файлы (${uris.size})"
              )
            )
          } else {
            val file = files.first()
            val uri =
              FileProvider.getUriForFile(appContext, "${appContext.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
              type = "*/*"
              putExtra(Intent.EXTRA_STREAM, uri)
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            state.copy(pendingIntent = Intent.createChooser(intent, "Поделиться файлом"))
          }
        } else state
      }

      WebDavCommand.ClearImageSelection -> state.copy(selectedItems = emptySet())
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
  return context.getString(R.string.webdav_upload_fallback_filename)
}

private fun WebDavFlow.State.withRefreshedItems(newItems: List<WebDavItem>): WebDavFlow.State {
  val buckets = partitionForBrowse(newItems)
  return copy(
    items = newItems,
    selectedItems = selectedItems.intersect(buckets.imageFiles),
  )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ScreenScope<WebDavFlow.State, WebDavFlow.WebDavCommand>.WebDavScreenBody() {
  val st by state.collectAsState()
  val buckets = remember(st.items) { partitionForBrowse(st.items) }
  val context = LocalContext.current
  val activity = LocalActivity.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val rootLabel = stringResource(R.string.webdav_root_folder)

  if (st.pendingIntent != null) {
    LaunchedEffect(st.pendingIntent) {
      activity?.startActivity(st.pendingIntent)
    }
  }

  val pickVisualMedia = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) {
      send(WebDavFlow.WebDavCommand.Upload(uri))
    }
  }

  AppScreen(
    title = null,
    subtitle = null,
    primary = Action(
      resRef = R.drawable.ic_arrow_back_24,
      listener = { send(WebDavFlow.WebDavCommand.Back) },
    ),
    secondary = if (st.segments.isNotEmpty()) {
      Action(
        resRef = R.drawable.ic_copy_24,
        listener = {
          val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
          clipboard.setPrimaryClip(
            ClipData.newPlainText(
              context.getString(R.string.webdav_copy_folder_path),
              breadcrumb(st.segments, rootLabel),
            ),
          )
        },
      )
    } else {
      null
    },
  ) {
    BackHandler { send(WebDavFlow.WebDavCommand.Back) }

    Box(modifier = Modifier.fillMaxSize()) {
      PullToRefreshBox(
        isRefreshing = false,
        onRefresh = { send(WebDavFlow.WebDavCommand.Refresh) },
        modifier = Modifier.fillMaxSize(),
        indicator = { }
      ) {
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 104.dp),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          stickyHeader {
            Column(
              modifier = Modifier
                .background(
                  color = AppTheme.colors.backgroundPrimary
                )
                .fillMaxWidth()
            ) {
              Text(
                style = AppTheme.typography.headlineLarge,
                color = AppTheme.colors.contentPrimary,
                text = stringResource(R.string.webdav_cloud_storage),
              )
              Text(
                style = AppTheme.typography.labelMedium,
                color = AppTheme.colors.contentSecondary,
                text = breadcrumb(st.segments, rootLabel),
              )
            }
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

              PhotoGridCell(
                photoUrl = file.previewPath ?: file.path,
                selected = file in st.selectedItems,
                onClick = { send(WebDavFlow.WebDavCommand.OpenPhoto(file.name)) },
                onLongClick = {
                  send(WebDavFlow.WebDavCommand.ToggleImageSelection(file))
                },
              )
            }
          }
        }
      }
      Column(
        Modifier
          .padding(16.dp)
          .surface(
            shadowElevation = 2f,
            shape = RoundedCornerShape(16.dp),
            onClick = null,
            backgroundColor = AppTheme.colors.backgroundSecondary
          )
          .padding(16.dp)
          .align(Alignment.BottomEnd)
      ) {
        Icon(
          modifier = Modifier
            .size(40.dp)
            .clickable(
              onClick = {
                if (activity != null) {
                  pickVisualMedia.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                  )
                }
              },
              indication = null,
              interactionSource = null
            ),
          painter = painterResource(R.drawable.ic_camera_32),
          contentDescription = stringResource(R.string.webdav_upload),
          tint = AppTheme.colors.accentPrimary
        )
        VSpacer(4.dp)
        Icon(
          modifier = Modifier
            .size(40.dp)
            .clickable(
              onClick = {
                sendEvent(
                  ServiceCommand.UiEvent.Decision(
                    content = {
                      Column {
                        Text(
                          text = stringResource(R.string.webdav_mkdir_hint),
                          style = AppTheme.typography.labelLarge,
                          color = AppTheme.colors.contentSecondary
                        )
                        VSpacer(2.dp)
                        BasicTextField(
                          modifier = Modifier
                            .fillMaxWidth()
                            .background(
                              AppTheme.colors.backgroundSecondary,
                              shape = RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp),
                          textStyle = TextStyle.Default.copy(
                            AppTheme.colors.contentSecondary
                          ),
                          value = st.mkdirDraft,
                          onValueChange = { send(WebDavFlow.WebDavCommand.SetMkdirDraft(it)) },
                        )
                      }
                    },
                    primaryAction = Action(
                      listener = {
                        send(WebDavFlow.WebDavCommand.Mkdir(st.mkdirDraft.trim()))
                      },
                      resRef = R.string.webdav_confirm_mkdir
                    ),
                  )
                )
              },
              indication = null,
              interactionSource = null
            ),
          painter = painterResource(R.drawable.ic_folder_24),
          contentDescription = null,
          tint = AppTheme.colors.accentPrimary
        )
        AnimatedVisibility(visible = st.selectedItems.isNotEmpty()) {
          Column {
            VSpacer(4.dp)
            Icon(
              modifier = Modifier
                .size(40.dp)
                .clickable(
                  onClick = { send(WebDavFlow.WebDavCommand.OpenSelectionActionSheet) },
                  indication = null,
                  interactionSource = null,
                ),
              painter = painterResource(R.drawable.ic_more_circles_24),
              contentDescription = stringResource(R.string.webdav_more_actions),
              tint = AppTheme.colors.accentPrimary,
            )
          }
        }
      }
    }

    if (st.openActionBottomSheet) {
      ModalBottomSheet(
        onDismissRequest = { send(WebDavFlow.WebDavCommand.DismissSelectionActionSheet) },
        sheetState = sheetState,
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
          VSpacer(8.dp)
          SentenceRow(
            modifier = Modifier.fillMaxWidth(),
            painter = null,
            title = stringResource(R.string.webdav_sheet_download),
            subtitle = null,
            loading = false,
            onClick = { send(WebDavFlow.WebDavCommand.Download) },
          )
          VSpacer(8.dp)
          SentenceRow(
            modifier = Modifier.fillMaxWidth(),
            painter = null,
            title = stringResource(R.string.webdav_sheet_move_from_clipboard),
            subtitle = null,
            loading = false,
            onClick = {
              val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
              val clip = clipboard?.primaryClip
              if (clip == null || clip.itemCount < 1) {
                sendEvent(snackbar(R.string.webdav_clipboard_empty))
              } else {
                send(WebDavFlow.WebDavCommand.ResolveClipboardPath(clip))
              }
            },
          )
          VSpacer(8.dp)
          SentenceRow(
            modifier = Modifier.fillMaxWidth(),
            painter = null,
            title = stringResource(R.string.webdav_sheet_delete),
            subtitle = null,
            enabled = false,
            loading = false,
            onClick = null,
          )
          VSpacer(8.dp)
          SentenceRow(
            modifier = Modifier.fillMaxWidth(),
            painter = null,
            title = stringResource(R.string.webdav_sheet_cancel),
            subtitle = null,
            loading = false,
            onClick = { send(WebDavFlow.WebDavCommand.CancelSelectionAndDismissSheet) },
          )
          VSpacer(16.dp)
        }
      }
    }
  }
}

@Stable
private fun snackbar(resRef: Int) = ServiceCommand.UiEvent.Snack(
  action = Action(
    listener = { },
    resRef = resRef
  )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGridCell(
  photoUrl: String,
  selected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val context = LocalContext.current
  var state by remember(photoUrl) { mutableStateOf<WorkState>(WorkState.Running) }
  val tileShape = RoundedCornerShape(12.dp)
  val borderStroke =
    if (selected) {
      BorderStroke(3.dp, AppTheme.colors.accentPrimary)
    } else {
      BorderStroke(1.dp, AppTheme.colors.backgroundSecondary)
    }
  Box(
    modifier = Modifier
      .aspectRatio(1f)
      .clip(tileShape)
      .border(
        border = borderStroke,
        shape = tileShape,
      )
      .combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick,
      ),
  ) {
    AnimatedContent(
      modifier = Modifier.fillMaxSize(),
      targetState = state,
      transitionSpec = {
        fadeIn(
          animationSpec = tween(
            easing = LinearOutSlowInEasing,
            durationMillis = 400
          )
        ) togetherWith fadeOut(
          animationSpec = tween(
            easing = LinearOutSlowInEasing,
            durationMillis = 400
          )
        )
      }
    ) { s ->
      when (s) {
        is WorkState.Error -> {
          Text(stringResource(R.string.photos))
        }

        WorkState.Idle,
        WorkState.Running,
          -> {
          val imageLoader = LocalImageLoader.current
          var onceSucceeded by remember(photoUrl) { mutableStateOf(false) }
          key(photoUrl, onceSucceeded) {
            AsyncImage(
              model = rememberImageRequest(photoUrl),
              contentDescription = null,
              imageLoader = imageLoader,
              modifier = Modifier
                .clip(tileShape)
                .fillMaxSize(),
              contentScale = ContentScale.Crop,
              onError = {
                state = WorkState.Error(context.getString(R.string.error_image_load_failed))
              },
              onLoading = {
                state = WorkState.Running
              },
              onSuccess = {
                onceSucceeded = true
                state = WorkState.Idle
              },
            )
            if (s is WorkState.Running && !onceSucceeded) {
              ShimmerBox(modifier = Modifier.fillMaxSize())
            }
          }
        }

        WorkState.NotStarted -> ShimmerBox(modifier = Modifier.fillMaxSize())
      }
    }
  }
}

private fun resolveClipboardForWebDavMove(
  context: Context,
  clip: ClipData,
  webDavBaseUrl: String,
): String? {
  for (i in 0 until clip.itemCount) {
    val item = clip.getItemAt(i)
    val raw = item.coerceToText(context)?.toString()
    if (raw.isNullOrEmpty()) continue
    val trimmed = raw.trim()
    if (trimmed.startsWith("/") && webDavBaseUrl.endsWith("/")) {
      val resolved = webDavBaseUrl.dropLastWhile { it.toString() == "/" } + trimmed
      return resolved
    }
    if (!trimmed.startsWith("/") && webDavBaseUrl.endsWith("/")) {
      return webDavBaseUrl.dropLastWhile { it.toString() == "/" } + "/" + trimmed
    }
    return null
  }
  return null
}

private fun breadcrumb(segments: List<String>, rootLabel: String): String {
  if (segments.isEmpty()) return rootLabel
  return "/" + segments.joinToString("/")
}
