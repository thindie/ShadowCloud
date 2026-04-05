package com.thindie.shadowcloud.application

data class SemanticVersion(
  val major: Int,
  val minor: Int,
  val patch: Int,
) : Comparable<SemanticVersion> {

  override fun compareTo(other: SemanticVersion): Int {
    if (major != other.major) return major.compareTo(other.major)
    if (minor != other.minor) return minor.compareTo(other.minor)
    return patch.compareTo(other.patch)
  }

  companion object {
    fun parse(raw: String): SemanticVersion? {
      val s = raw.trim().removePrefix("v").removePrefix("V")
      val parts = s.split('.')
      if (parts.size < 3) return null
      val major = parts[0].toIntOrNull() ?: return null
      val minor = parts[1].toIntOrNull() ?: return null
      val patch = parts[2].toIntOrNull() ?: return null
      return SemanticVersion(major, minor, patch)
    }
  }
}
