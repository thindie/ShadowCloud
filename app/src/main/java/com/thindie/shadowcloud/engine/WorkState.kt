package com.thindie.shadowcloud.engine

import androidx.compose.runtime.Immutable

@Immutable
sealed interface WorkState {
  @Immutable
  data object Idle : WorkState

  @Immutable
  data object Running : WorkState

  @Immutable
  data class Error(val message: String, val cause: Throwable? = null) : WorkState

  @Immutable
  data object NotStarted: WorkState
}
