package com.thindie.shadowcloud.engine

import androidx.compose.runtime.Immutable

@Immutable
data class ScreenScopeError(
  val message: String,
  val actions: Map<Actions, Command>,
) {
  sealed interface Actions {
    interface Common : Actions {
      val title: String?

      data object ButtonMain : Common {
        override val title: String = "Ок"
      }

      data object ButtonSecondaryRetry : Common {
        override val title: String = "Попробовать снова"
      }

      data object DismissMain : Common {
        override val title: String?
          get() = null
      }
    }
  }
}

