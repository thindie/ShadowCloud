package com.thindie.shadowcloud.feature.intro

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.thindie.shadowcloud.R
import com.thindie.shadowcloud.application.Application
import com.thindie.shadowcloud.engine.Command
import com.thindie.shadowcloud.engine.Route
import com.thindie.shadowcloud.engine.RouteFactory
import com.thindie.shadowcloud.engine.Router
import com.thindie.shadowcloud.engine.ScreenFlow
import com.thindie.shadowcloud.engine.ScreenScope
import com.thindie.shadowcloud.engine.ScreenScopeError
import com.thindie.shadowcloud.feature.home.HomeFlow
import com.thindie.shadowcloud.feature.webdav.data.WebDavRepository
import com.thindie.shadowcloud.uikit.AppScreen
import com.thindie.shadowcloud.uikit.AppTheme
import com.thindie.shadowcloud.uikit.Button
import com.thindie.shadowcloud.uikit.SentenceRow

private fun Context.intentAppNotificationSettings(): Intent =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
      putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    }
  } else {
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
      data = Uri.fromParts("package", packageName, null)
    }
  }

private fun Context.intentApplicationDetailsSettings(): Intent =
  Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
    data = Uri.fromParts("package", packageName, null)
  }

class PermissionsFlow(
  private val router: Router,
  private val appContext: Application,
  private val repository: WebDavRepository,
) : ScreenFlow<Route, PermissionsFlow.Result>(router) {

  fun startAppFlow() {
    HomeFlow(router = router, appContext = appContext, repository = repository)
      .onFinishBuilder { finish(Result.Success) }
      .start()
  }

  enum class Result {
    Success,
  }

  override fun start() {
    startAppFlow()
  }

  private fun hasVpnPermission(): Boolean = VpnService.prepare(appContext) == null

  fun main() = RouteFactory.create(
    initialState = State(),
    execute = ::exec,
    routeContent = { IntroScreenContent(this) },
    errorMapper = {
      ScreenScopeError(
        message = appContext.getString(R.string.error_unexpected),
        actions = emptyMap(),
      )
    },
    initialCommand = RouteFactory.InitialCommand {
      CommandIntro.Start as CommandIntro
    },
  )

  @Immutable
  data class State(
    val permissionScope: List<Permission> = emptyList(),
    val permit: List<Permission> = emptyList(),
    val current: Permission = Permission.Vpn,
    val stage: Stage = Stage.Loading,
    val hint: String? = null,
  ) : com.thindie.shadowcloud.engine.State

  enum class Stage {
    Loading,
    SoftRequest,
    Rationale,

    RationaleDismissedOnce,
  }

  enum class Permission {
    Vpn,
    Push,
  }

  sealed interface CommandIntro : Command {
    data object Start : CommandIntro
    data object Dismiss : CommandIntro
    data object AcceptSoftRequest : CommandIntro
    data object DeclineSoftRequest : CommandIntro
    data object ConfirmRationale : CommandIntro
    data object PermissionDenied : CommandIntro
  }

  private suspend fun exec(command: CommandIntro, state: State): State {
    return when (command) {
      CommandIntro.Start -> {
        when {
          else -> {
            startAppFlow()
            state
          }
        }
      }

      CommandIntro.AcceptSoftRequest -> {
        state.copy(
          stage = Stage.Rationale,
          hint = null
        )
      }

      CommandIntro.DeclineSoftRequest -> {
        state.copy(
          stage = Stage.SoftRequest,
        )
      }

      CommandIntro.ConfirmRationale -> {
        when (state.current) {
          Permission.Vpn -> {
            if (hasVpnPermission()) {
              state.copy(
                current = Permission.Push,
                stage = Stage.SoftRequest,
              )
            } else {
              state.copy(
                stage = Stage.SoftRequest,
              )
            }
          }

          Permission.Push -> {
            startAppFlow()
            state
          }
        }
      }

      CommandIntro.PermissionDenied -> {
        when (state.current) {
          Permission.Vpn -> {
            state.copy(
              stage = Stage.SoftRequest,
            )
          }

          Permission.Push -> {
            state.copy(
              stage = Stage.RationaleDismissedOnce
            )
          }
        }
      }

      CommandIntro.Dismiss -> {
        when (state.current) {
          Permission.Vpn -> Unit
          Permission.Push -> when (state.stage) {
            Stage.Loading -> Unit
            Stage.Rationale -> startAppFlow()
            Stage.SoftRequest -> startAppFlow()
            Stage.RationaleDismissedOnce -> startAppFlow()
          }
        }
        state
      }
    }
  }
}

@Composable
private fun IntroScreenContent(scope: ScreenScope<PermissionsFlow.State, PermissionsFlow.CommandIntro>) =
  with(scope) {
    val st by state.collectAsState()
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(
      ActivityResultContracts.StartActivityForResult()
    ) { result ->
      if (result.resultCode == Activity.RESULT_OK) {
        send(PermissionsFlow.CommandIntro.ConfirmRationale)
      } else {
        send(PermissionsFlow.CommandIntro.PermissionDenied)
      }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) {
      if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
        ) {
          send(PermissionsFlow.CommandIntro.ConfirmRationale)
        } else {
          send(PermissionsFlow.CommandIntro.PermissionDenied)
        }
      }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
      ActivityResultContracts.StartActivityForResult()
    ) {
      if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
        ) {
          send(PermissionsFlow.CommandIntro.ConfirmRationale)
        }
      }
    }

    BackHandler { send(PermissionsFlow.CommandIntro.Dismiss) }

    AppScreen {
      Column(
        modifier = Modifier
          .fillMaxSize()
      ) {
        when (st.stage) {
          PermissionsFlow.Stage.Loading -> Unit
          PermissionsFlow.Stage.SoftRequest -> {
            AlertDialog(
              containerColor = AppTheme.colors.backgroundPrimary,
              text = {
                SentenceRow(
                  painter = painterResource(R.drawable.ic_attention_24),
                  onClick = null,
                  title = "",
                  subtitle = "",
                  loading = false,
                )
              },
              onDismissRequest = { },
              dismissButton = {
                Button(
                  modifier = Modifier.fillMaxWidth(),
                  text = stringResource(R.string.btn_close),
                  onClick = { send(PermissionsFlow.CommandIntro.DeclineSoftRequest) }
                )
              },
              confirmButton = {
                Button(
                  modifier = Modifier.fillMaxWidth(),
                  text = stringResource(R.string.btn_close),
                  onClick = { send(PermissionsFlow.CommandIntro.AcceptSoftRequest) }
                )
              }
            )
          }

          PermissionsFlow.Stage.Rationale -> {
            LaunchedEffect(st.stage, st.current) {
              when (st.current) {
                PermissionsFlow.Permission.Vpn -> {
                  val intent = if (activity != null) {
                    VpnService.prepare(activity)
                  } else {
                    null
                  }
                  if (intent == null) {
                    send(PermissionsFlow.CommandIntro.ConfirmRationale)
                  } else {
                    launcher.launch(intent)
                  }
                }

                PermissionsFlow.Permission.Push -> {
                  if (activity != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                      if (ContextCompat.checkSelfPermission(
                          activity,
                          Manifest.permission.POST_NOTIFICATIONS
                        ) !=
                        PackageManager.PERMISSION_GRANTED
                      ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                      } else {
                        send(PermissionsFlow.CommandIntro.ConfirmRationale)
                      }
                    }
                  } else {
                    send(PermissionsFlow.CommandIntro.ConfirmRationale)
                  }
                }
              }
            }
          }

          PermissionsFlow.Stage.RationaleDismissedOnce -> {
            AlertDialog(
              containerColor = AppTheme.colors.backgroundPrimary,
              text = {
                SentenceRow(
                  painter = painterResource(R.drawable.ic_attention_24),
                  onClick = null,
                  title = "mock",
                  subtitle = "mock",
                  loading = false,
                )
              },
              onDismissRequest = { },
              dismissButton = {
                Button(
                  modifier = Modifier.fillMaxWidth(),
                  text = "mock",
                  onClick = { send(PermissionsFlow.CommandIntro.ConfirmRationale) }
                )
              },
              confirmButton = {
                Button(
                  modifier = Modifier.fillMaxWidth(),
                  text = "mock",
                  onClick = {
                    activity ?: return@Button
                    val primary = activity.intentAppNotificationSettings()
                    val fallback = activity.intentApplicationDetailsSettings()
                    val pm = activity.packageManager
                    val target =
                      if (primary.resolveActivity(pm) != null) primary else fallback
                    settingsLauncher.launch(target)
                  }
                )
              }
            )
          }
        }
      }
    }
  }
