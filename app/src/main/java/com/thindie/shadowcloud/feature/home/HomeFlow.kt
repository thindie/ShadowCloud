package com.thindie.shadowcloud.feature.home

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
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
import com.thindie.shadowcloud.engine.WorkState
import com.thindie.shadowcloud.engine.stateSink
import com.thindie.shadowcloud.engine.sub
import com.thindie.shadowcloud.engine.transition
import com.thindie.shadowcloud.uikit.Action
import com.thindie.shadowcloud.uikit.AppScreen
import com.thindie.shadowcloud.uikit.AppTheme
import com.thindie.shadowcloud.uikit.Button
import com.thindie.shadowcloud.uikit.CircularProgress
import com.thindie.shadowcloud.uikit.HSpacer
import com.thindie.shadowcloud.uikit.LocalThemeSwitcher
import com.thindie.shadowcloud.uikit.SentenceRow
import com.thindie.shadowcloud.uikit.ThemeSwitcher
import com.thindie.shadowcloud.uikit.surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext


class HomeFlow(
  private val router: Router,
  private val appContext: Context,
) : ScreenFlow<Route, Unit>(router) {

  override fun start() {
    router.push(main())
  }


  fun stateSink(screenScope: ScreenScope<State, HomeCommand>) {
    screenScope.stateSink {

    }
  }

  fun main() = RouteFactory.create(
    initialState = State(),
    execute = ::exec,
    stateSink = ::stateSink,
    routeContent = { HomeScreen() },
    errorMapper = {
      ScreenScopeError(
        message = appContext.getString(R.string.error_unexpected),
        actions = emptyMap(),
      )
    },
  )

  @Immutable
  data class State(
    val types: List<MediaContent> = buildList {
      add(MediaContent.Photo)
    }
  ) : com.thindie.shadowcloud.engine.State

  sealed interface HomeCommand : Command {
    data object Back : HomeCommand
    data object Next : HomeCommand
    data class Select(val mediaContent: MediaContent) : HomeCommand

  }

  enum class MediaContent {
    Photo
  }

  private suspend fun exec(command: HomeCommand, homeState: State): State {

    return when (command) {
      is HomeCommand.Back -> {
        finish(Unit)
        homeState
      }

      HomeCommand.Next -> {
        homeState
      }

      is HomeCommand.Select -> {
        homeState
      }
    }
  }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScope<HomeFlow.State, HomeFlow.HomeCommand>.HomeScreen() {
  val themeSwitcher = LocalThemeSwitcher.current
  val themeColors = LocalThemeSwitcher.current.themeFlow.collectAsState(null)
  val isDark = when (themeColors.value) {
    null -> isSystemInDarkTheme()
    ThemeSwitcher.Choice.Dark -> true
    ThemeSwitcher.Choice.Light -> false
    ThemeSwitcher.Choice.Auto -> isSystemInDarkTheme()
  }
  val screenState by state.collectAsState()
  AppScreen(
    secondary = Action(
      icon = R.drawable.ic_shield_extra_24,
      listener = {
        themeSwitcher.set(
          if (isDark) ThemeSwitcher.Choice.Light else ThemeSwitcher.Choice.Dark
        )
      }
    )
  ) {
    BackHandler { send(HomeFlow.HomeCommand.Back) }
    val st by state.collectAsState()
    val height = LocalWindowInfo.current.containerSize.height.dp
    PullToRefreshBox(
      isRefreshing = false,
      modifier = Modifier.height(height),
      onRefresh = {

      }
    ) {
      LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        items(
          items = screenState.types,
        ) { item ->
          SentenceRow(
            modifier = Modifier
              .border(
                border = BorderStroke(
                  width = 1.2.dp,
                  color = AppTheme.colors.backgroundSecondary
                ),
                shape = RoundedCornerShape(20.dp)
              )
              .fillMaxWidth(),
            painter = null,
            title = stringResource(item.titleRef),
            subtitle = null,
            loading = false,
            onClick = { send(HomeFlow.HomeCommand.Select(item)) },
          )
        }
      }
    }
  }
}

private val HomeFlow.MediaContent.titleRef get() =
  when (this) {
    HomeFlow.MediaContent.Photo -> R.string.photos
  }
