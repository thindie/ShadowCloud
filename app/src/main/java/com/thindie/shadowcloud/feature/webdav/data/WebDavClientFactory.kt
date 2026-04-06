package com.thindie.shadowcloud.feature.webdav.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic

fun newAuthenticatedWebdavClient(userName: String, password: String): HttpClient =
  HttpClient(CIO) {
    engine {
      maxConnectionsCount = 32
    }
    install(HttpTimeout) {
      requestTimeoutMillis = 300_000
      connectTimeoutMillis = 30_000
      socketTimeoutMillis = 300_000
    }
    install(Auth) {
      basic {
        credentials {
          BasicAuthCredentials(username = userName, password = password)
        }
        sendWithoutRequest { true }
      }
    }
  }
