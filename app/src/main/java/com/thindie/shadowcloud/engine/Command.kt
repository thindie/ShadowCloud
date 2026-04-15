package com.thindie.shadowcloud.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.thindie.shadowcloud.uikit.Action
import java.io.Serializable


@Stable
interface Command

@Stable
interface State : Serializable

sealed interface ServiceCommand : Command {
  data object Dispose : ServiceCommand
  fun interface Prioritized : ServiceCommand {
    fun execute()
  }

  @Immutable
  sealed interface UiEvent : Command {
    @Immutable
    data class Decision(
      val content: @Composable () -> Unit,
      val primaryAction: Action,
      val secondaryAction: Action? = null,
    ) : UiEvent

    @Immutable
    data class Snack(
      val action: Action,
    ) : UiEvent

    @Immutable
    data class Content(val content: @Composable () -> Unit)
  }
}