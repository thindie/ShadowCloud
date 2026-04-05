package com.thindie.shadowcloud.engine

import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow


@Stable
interface ScreenScope<S : State, C : Command> {
  val state: StateFlow<S>
  val processing: androidx.compose.runtime.State<C?>
  val error: androidx.compose.runtime.State<ScreenScopeError?>
  fun send(command: C)
  fun dispose()
  val scope: CoroutineScope?
  fun update(s: S)
}
