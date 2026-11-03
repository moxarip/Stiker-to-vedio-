package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val DarkStudioColorScheme = darkColorScheme(
    primary = WhatsAppNeonGreen,
    onPrimary = Color(0xFF042112),
    primaryContainer = WhatsAppEmerald,
    onPrimaryContainer = Color.White,
    secondary = CyberTeal,
    onSecondary = Color(0xFF00262B),
    secondaryContainer = StudioCardElevated,
    onSecondaryContainer = CyberTeal,
    tertiary = AmberGold,
    onTertiary = Color(0xFF281A00),
    background = StudioObsidian,
    onBackground = TextPrimaryDark,
    surface = StudioSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = StudioCard,
    onSurfaceVariant = TextSecondaryDark,
    outline = DividerDark,
    error = CoralPink
)

private val LightStudioColorScheme = lightColorScheme(
    primary = WhatsAppEmerald,
    onPrimary = Color.White,
    primaryContainer = StudioCardLight,
    onPrimaryContainer = Color(0xFF04382A),
    secondary = Color(0xFF008394),
    onSecondary = Color.White,
    background = StudioBackgroundLight,
    onBackground = TextPrimaryLight,
    surface = StudioSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = StudioCardLight,
    onSurfaceVariant = TextSecondaryLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkStudioColorScheme else LightStudioColorScheme

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
