package com.tim.futureai.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.tim.futureai.domain.settings.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun FutureAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun GatherAITheme(mode: ThemeMode, fontScale: Float = 1f, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalFutureColors provides if (dark) DarkFutureColors else LightFutureColors,
        LocalDensity provides Density(density.density, fontScale.coerceIn(0.85f, 1.25f)),
    ) {
        val scheme = if (dark) darkColorScheme(
            primary = FuturePalette.Violet,
            onPrimary = Color.White,
            primaryContainer = Color(0xFF43377D),
            onPrimaryContainer = FuturePalette.Text,
            background = FuturePalette.Night,
            onBackground = FuturePalette.Text,
            surface = FuturePalette.Panel,
            onSurface = FuturePalette.Text,
            surfaceVariant = FuturePalette.DeepBlue,
            onSurfaceVariant = FuturePalette.Muted,
            error = FuturePalette.Danger,
            errorContainer = Color(0xFF492638),
            onErrorContainer = FuturePalette.Text,
        ) else lightColorScheme(
            primary = FuturePalette.Violet,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE5DFFF),
            onPrimaryContainer = Color(0xFF241A50),
            background = FuturePalette.Night,
            onBackground = FuturePalette.Text,
            surface = FuturePalette.Panel,
            onSurface = FuturePalette.Text,
            surfaceVariant = FuturePalette.DeepBlue,
            onSurfaceVariant = FuturePalette.Muted,
            error = FuturePalette.Danger,
        )
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}
