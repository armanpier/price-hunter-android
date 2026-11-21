package com.teamwork.pricehunter.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Primary Brand Colors
val PrimaryBlue = Color(0xFF1E88E5)
val PrimaryBlueDark = Color(0xFF90CAF9)
val SecondaryTeal = Color(0xFF00897B)
val SecondaryTealDark = Color(0xFF80CBC4)

// Neutral Colors
val BackgroundLight = Color(0xFFF8F9FA)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFF1F3F5)
val TextPrimaryLight = Color(0xFF212529)
val TextSecondaryLight = Color(0xFF6C757D)
val OutlineLight = Color(0xFFDEE2E6)

val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1E1E1E)
val SurfaceVariantDark = Color(0xFF2A2A2A)
val TextPrimaryDark = Color(0xFFE0E0E0)
val TextSecondaryDark = Color(0xFFA0A0A0)
val OutlineDark = Color(0xFF383838)

// Provider Brand Colors
val DigikalaRed = Color(0xFFEF394E)
val TorobBlue = Color(0xFF2B5278)
val SnappGreen = Color(0xFF00AA5B)
val SnappFoodPink = Color(0xFFE91E63)
val TapsiFoodOrange = Color(0xFFFF6F00)
val OkalaGreen = Color(0xFF43A047)

// Status & Semantic Colors
val SuccessGreen = Color(0xFF2E7D32)
val WarningAmber = Color(0xFFF57F17)
val WarningAmberContainer = Color(0xFFFFF8E1)
val ErrorRed = Color(0xFFD32F2F)
val ErrorRedContainer = Color(0xFFFFEBEE)

val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = SecondaryTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E3),
    onSecondaryContainer = Color(0xFF00201C),
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRedContainer,
    onErrorContainer = Color(0xFF410002)
)

val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueDark,
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = SecondaryTealDark,
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF004F46),
    onSecondaryContainer = Color(0xFFCCE8E3),
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)
