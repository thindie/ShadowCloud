package com.thindie.shadowcloud.engine

import androidx.compose.runtime.Stable
import java.io.Serializable


@Stable
interface Command {
  infix fun Command.processing(command: Command): Boolean = this == command
}

@Stable
interface State : Serializable

sealed interface ServiceCommand : Command {
  data object Dispose : ServiceCommand
  fun interface Prioritized : ServiceCommand {
    fun execute()
  }
}