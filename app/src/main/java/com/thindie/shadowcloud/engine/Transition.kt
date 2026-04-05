package com.thindie.shadowcloud.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

fun <S : State, C : Command> ScreenScope<S, C>.stateSink(block: ScreenScope<S, C>.() -> Unit) {
  block.invoke(this)
}

fun <
    S : State,
    C : Command,
    R : Any,
    > ScreenScope<S, C>.sub(flow: Flow<R>): Pair<ScreenScope<S, C>, Flow<Pair<S, R>>> {
  return this to combine(
    this.state,
    flow.distinctUntilChanged(),
    ::Pair
  )
}

fun <S : State, C : Command, R : Any> Pair<ScreenScope<S, C>, Flow<Pair<S, R>>>.transition(
  action: (S, S, R) -> Unit = { _, _, _ -> },
  block: (S, R) -> S,
): ScreenScope<S, C> {
  val (screenScope, flow) = this
  screenScope.scope?.let { scope ->
    flow
      .onEach { (s, any) ->
        val state = block(s, any)
        val oldState = screenScope.state.value
        if (state != oldState) {
          action(oldState, state, any)
          screenScope.update(state)
        }
      }
      .launchIn(scope)
  }
  return screenScope
}
