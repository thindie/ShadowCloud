package com.thindie.shadowcloud.uikit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Immutable
class AppColors(
  val contentPrimary: Color,
  val contentSecondary: Color,
  val buttonContentPrimary: Color,
  val backgroundPrimary: Color,
  val backgroundSecondary: Color,
  val accentPrimary: Color,
  val onAccentPrimary: Color,
  val successPrimary: Color,
  val errorPrimary: Color,
)

private val LightColorScheme = AppColors(
  contentPrimary = Color(0xFF1A1A1A),
  contentSecondary = Color(0xFF757575),
  backgroundPrimary = Color.White,
  backgroundSecondary = Color(0xFFF5F7FA),
  accentPrimary = Color(0xFF4766FF),
  onAccentPrimary = Color(0xFFF5F7FA),
  successPrimary = Color(0xFF2E7D32),
  errorPrimary = Color(0xFFD32F2F),
  buttonContentPrimary = Color(0xFFF5F7FA)
)

private val DarkColorScheme = AppColors(
  contentPrimary = Color(0xFFF5F5F5),
  contentSecondary = Color(0xFF9E9E9E),
  backgroundPrimary = Color(0xFF121212),
  backgroundSecondary = Color(0xFF1E1E1E),
  accentPrimary = Color(0xFF9DADFF),
  onAccentPrimary = Color(0xFF121212),
  successPrimary = Color(0xFF4CAF50),
  errorPrimary = Color(0xFFF44336),
  buttonContentPrimary = Color(0xFF121212)
)

class ThemeSwitcher {
  private val _themeFlow =
    MutableSharedFlow<Choice>(
      replay = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
      extraBufferCapacity = 1
    )
  val themeFlow = _themeFlow.asSharedFlow()

  enum class Choice {
    Dark,
    Light,
    Auto
  }

  fun set(choice: Choice) {
    _themeFlow.tryEmit(choice)
  }
}

private val LocalAppColors = compositionLocalOf { LightColorScheme }
private val LocalAppTypo = staticCompositionLocalOf { AppTypography }
val LocalThemeSwitcher = staticCompositionLocalOf { ThemeSwitcher() }


object AppTheme {
  val colors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current

  val typography: AppTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalAppTypo.current
}

@Composable
fun AppTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val targetColors = if (darkTheme) DarkColorScheme else LightColorScheme

  val colorSpec: AnimationSpec<Color> = tween(durationMillis = 400)

  @Composable
  fun animateColor(target: Color) = animateColorAsState(target, colorSpec, label = "color").value

  val animatedColors = AppColors(
    contentPrimary = animateColor(targetColors.contentPrimary),
    contentSecondary = animateColor(targetColors.contentSecondary),
    backgroundPrimary = animateColor(targetColors.backgroundPrimary),
    backgroundSecondary = animateColor(targetColors.backgroundSecondary),
    accentPrimary = animateColor(targetColors.accentPrimary),
    successPrimary = animateColor(targetColors.successPrimary),
    onAccentPrimary = animateColor(targetColors.onAccentPrimary),
    errorPrimary = animateColor(targetColors.errorPrimary),
    buttonContentPrimary = animateColor(targetColors.buttonContentPrimary),
  )

  CompositionLocalProvider(
    LocalAppColors provides animatedColors,
    LocalAppTypo provides AppTypography
  ) {
    content()
  }
}


@Immutable
object AppTypography {
  val headlineLarge = TextStyle(
    fontSize = 40.sp,
    lineHeight = 36.sp,
    letterSpacing = 1.26.sp,
    fontWeight = FontWeight.W400,
  )

  val headlineMedium = TextStyle(
    fontSize = 32.sp,
    lineHeight = 28.sp,
    letterSpacing = 1.24.sp,
    fontWeight = FontWeight.W400,
  )

  val headlineSmall = TextStyle(
    fontSize = 24.sp,
    lineHeight = 28.sp,
    fontWeight = FontWeight.W400,
  )

  val titleLarge = TextStyle(
    fontSize = 20.sp,
    lineHeight = 24.sp,
    fontWeight = FontWeight.W400,
  )

  val titleMedium = TextStyle(
    fontSize = 18.sp,
    lineHeight = 22.sp,
    letterSpacing = 1.2.sp,
    fontWeight = FontWeight.W500,
  )
  val titleSmall = TextStyle(
    fontSize = 16.sp,
    lineHeight = 20.sp,
    fontWeight = FontWeight.W700,
  )

  val button = TextStyle(
    fontSize = 16.sp,
    lineHeight = 20.sp,
    letterSpacing = 1.2.sp,
    fontWeight = FontWeight.W700,
  )


  val bodyMedium = TextStyle(
    fontSize = 14.sp,
    lineHeight = 18.sp,
    fontWeight = FontWeight.W500,
  )

  val bodySmall = TextStyle(
    fontSize = 14.sp,
    lineHeight = 18.sp,
    fontWeight = FontWeight.W400,
  )

  val labelLarge = TextStyle(
    fontSize = 12.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight.W400,
  )

  val labelMedium = TextStyle(
    fontSize = 10.sp,
    lineHeight = 14.sp,
    fontWeight = FontWeight.W400,
  )
}

