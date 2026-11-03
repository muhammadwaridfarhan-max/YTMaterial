package com.example.ytclient.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.ytclient.settings.AppSettings
import com.example.ytclient.settings.ColorSource
import com.example.ytclient.settings.ThemeMode
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeContent

/** Build a full M3 ColorScheme from a single seed color (works on any Android version). */
fun colorSchemeFromSeed(seed: Int, dark: Boolean): ColorScheme {
    val scheme: DynamicScheme = SchemeContent(Hct.fromInt(seed), dark, 0.0)
    val c = MaterialDynamicColors()
    fun argb(color: DynamicColor) = Color(color.getArgb(scheme))
    return ColorScheme(
        primary = argb(c.primary()),
        onPrimary = argb(c.onPrimary()),
        primaryContainer = argb(c.primaryContainer()),
        onPrimaryContainer = argb(c.onPrimaryContainer()),
        inversePrimary = argb(c.inversePrimary()),
        secondary = argb(c.secondary()),
        onSecondary = argb(c.onSecondary()),
        secondaryContainer = argb(c.secondaryContainer()),
        onSecondaryContainer = argb(c.onSecondaryContainer()),
        tertiary = argb(c.tertiary()),
        onTertiary = argb(c.onTertiary()),
        tertiaryContainer = argb(c.tertiaryContainer()),
        onTertiaryContainer = argb(c.onTertiaryContainer()),
        background = argb(c.background()),
        onBackground = argb(c.onBackground()),
        surface = argb(c.surface()),
        onSurface = argb(c.onSurface()),
        surfaceVariant = argb(c.surfaceVariant()),
        onSurfaceVariant = argb(c.onSurfaceVariant()),
        surfaceTint = argb(c.surfaceTint()),
        inverseSurface = argb(c.inverseSurface()),
        inverseOnSurface = argb(c.inverseOnSurface()),
        error = argb(c.error()),
        onError = argb(c.onError()),
        errorContainer = argb(c.errorContainer()),
        onErrorContainer = argb(c.onErrorContainer()),
        outline = argb(c.outline()),
        outlineVariant = argb(c.outlineVariant()),
        scrim = argb(c.scrim()),
        surfaceBright = argb(c.surfaceBright()),
        surfaceDim = argb(c.surfaceDim()),
        surfaceContainerLowest = argb(c.surfaceContainerLowest()),
        surfaceContainerLow = argb(c.surfaceContainerLow()),
        surfaceContainer = argb(c.surfaceContainer()),
        surfaceContainerHigh = argb(c.surfaceContainerHigh()),
        surfaceContainerHighest = argb(c.surfaceContainerHighest()),
    )
}

@Composable
fun YTTheme(
    settings: AppSettings,
    content: @Composable () -> Unit
) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        settings.colorSource == ColorSource.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        settings.colorSource == ColorSource.DYNAMIC ->
            colorSchemeFromSeed(AppSettings.DEFAULT_SEED, dark)

        else -> colorSchemeFromSeed(settings.seedColor, dark)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
