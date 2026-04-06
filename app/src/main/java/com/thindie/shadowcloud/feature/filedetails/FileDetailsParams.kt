package com.thindie.shadowcloud.feature.filedetails

sealed interface FileDetailsParams {
  data class Photo(val imageUrl: String) : FileDetailsParams
  data class Document(val title: String) : FileDetailsParams
  data class Pdf(val title: String) : FileDetailsParams
  data class Other(val label: String) : FileDetailsParams
}
