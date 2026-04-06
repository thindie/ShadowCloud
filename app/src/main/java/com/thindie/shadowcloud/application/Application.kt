package com.thindie.shadowcloud.application

import android.app.Application
import android.util.Base64
import coil.ImageLoader
import com.thindie.shadowcloud.engine.Router
import com.thindie.shadowcloud.feature.auth.Creds
import com.thindie.shadowcloud.feature.webdav.data.WebDavRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.OkHttpClient
import kotlin.text.Charsets

class Application : Application() {
  private var router: Router? = null
  private var repository: WebDavRepository? = null
  private var webDavImageLoader: ImageLoader? = null

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

  fun requireWebDavRepository(): WebDavRepository {
    if (repository == null) {
      repository = WebDavRepository(
        appContext = this,
        baseUrl = Creds.URL,
        userName = Creds.USERNAME,
        password = Creds.PWD
      )
    }
    return requireNotNull(repository)
  }

  fun requireWebDavImageLoader(): ImageLoader {
    if (webDavImageLoader == null) {
      val token = Base64.encodeToString(
        "${Creds.USERNAME}:${Creds.PWD}".toByteArray(Charsets.UTF_8),
        Base64.NO_WRAP,
      )
      val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
          chain.proceed(
            chain.request().newBuilder()
              .header("Authorization", "Basic $token")
              .build(),
          )
        }
        .build()
      webDavImageLoader = ImageLoader.Builder(this)
        .okHttpClient(okHttpClient)
        .build()
    }
    return requireNotNull(webDavImageLoader)
  }
}
