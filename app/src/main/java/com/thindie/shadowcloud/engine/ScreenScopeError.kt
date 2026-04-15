package com.thindie.shadowcloud.engine

import androidx.compose.runtime.Immutable
import com.thindie.shadowcloud.R

@Immutable
data class ScreenScopeError(
  val message: String? = null,
  val messageRes: Int? = null,
  val actions: Map<Actions, Command>,
) {
  sealed interface Actions {
    interface Common : Actions {
      val title: String?
      val titleRes: Int?

      data object ButtonMain : Common {
        override val title: String? = null
        override val titleRes: Int = R.string.btn_ok
      }

      data object ButtonSecondaryRetry : Common {
        override val title: String? = null
        override val titleRes: Int = R.string.btn_retry
      }

      data object DismissMain : Common {
        override val title: String?
          get() = null
        override val titleRes: Int?
          get() = null
      }
    }
  }
}

