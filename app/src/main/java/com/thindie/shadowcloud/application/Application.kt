package com.thindie.shadowcloud.application

import android.app.Application
import com.thindie.shadowcloud.engine.Router
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

class Application : Application() {
  private var router: Router? = null

  val finishCommand = MutableSharedFlow<Unit>(
    replay = 0, extraBufferCapacity = 3, BufferOverflow.DROP_LATEST
  )


  fun requireRouter(): Router {
    if (router == null) {
      router = Router {
        finishCommand.tryEmit(Unit)
        router = null
      }
    }
    return requireNotNull(router)
  }
}
