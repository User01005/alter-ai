package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFECECEC),
    secondary = MutedText,
    tertiary = BorderGray,
    background = PitchBlack,
    surface = SlateGrayCard,
    surfaceVariant = SlateGrayCard, // Color of all popup boxes set to #191919 in dark/black theme
    onPrimary = PitchBlack,
    onSecondary = LightText,
    onBackground = LightText,
    onSurface = LightText,
    outline = Color.White.copy(alpha = 0.12f)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF111111),       // Very dark charcoal for high readability text/headers
    secondary = Color(0xFF666666),     // Slate-gray for subtext/secondary label
    tertiary = Color(0xFFEAEAEA),      // Light border/divider line gray
    background = Color(0xFFF7F8FA),    // Soft pristine off-white page background
    surface = Color(0xFFFFFFFF),       // Pure white for cards/surfaces
    surfaceVariant = Color(0xFFEAEAEA), // Grey for dialogs/popups instead of purple
    onPrimary = Color(0xFFFFFFFF),     // White text on dark elements
    onSecondary = Color(0xFF111111),   // Dark text on secondary
    onBackground = Color(0xFF111111),  // Dark text on background
    onSurface = Color(0xFF111111),     // Dark text on surface
    outline = Color.Black.copy(alpha = 0.08f) // Thin high-contrast edge outline
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color to maintain the pure design palette
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
