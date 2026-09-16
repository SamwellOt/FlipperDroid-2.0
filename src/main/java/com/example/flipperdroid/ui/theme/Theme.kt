package com.example.flipperdroid.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val FlipperDarkColorScheme = darkColorScheme(
    primary = FlipperOrange,
    onPrimary = FlipperBlack,
    primaryContainer = FlipperDarkOrange,
    onPrimaryContainer = FlipperWhite,
    secondary = FlipperGray,
    onSecondary = FlipperWhite,
    secondaryContainer = FlipperDarkGray,
    onSecondaryContainer = FlipperWhite,
    tertiary = FlipperLightOrange,
    onTertiary = FlipperBlack,
    tertiaryContainer = FlipperOrange,
    onTertiaryContainer = FlipperBlack,
    error = FlipperError,
    onError = FlipperWhite,
    errorContainer = FlipperError,
    onErrorContainer = FlipperWhite,
    background = FlipperBackground,
    onBackground = FlipperWhite,
    surface = FlipperSurface,
    onSurface = FlipperWhite,
    surfaceVariant = FlipperCardBackground,
    onSurfaceVariant = FlipperOnSurfaceMuted,
    outline = FlipperOutlineDark
)

private val FlipperLightColorScheme = lightColorScheme(
    primary = FlipperOrange,
    onPrimary = FlipperWhite,
    primaryContainer = FlipperLightOrange,
    onPrimaryContainer = FlipperBlack,
    secondary = FlipperGray,
    onSecondary = FlipperWhite,
    secondaryContainer = FlipperLightGray,
    onSecondaryContainer = FlipperBlack,
    tertiary = FlipperDarkOrange,
    onTertiary = FlipperWhite,
    tertiaryContainer = FlipperOrange,
    onTertiaryContainer = FlipperWhite,
    error = FlipperError,
    onError = FlipperWhite,
    errorContainer = FlipperLightError,
    onErrorContainer = FlipperBlack,
    background = FlipperLightBackground,
    onBackground = FlipperBlack,
    surface = FlipperLightSurface,
    onSurface = FlipperBlack,
    surfaceVariant = FlipperLightCardBackground,
    onSurfaceVariant = FlipperBlack,
    outline = FlipperDarkGray
)

@Composable
fun FlipperDroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Désactivé par défaut : les couleurs "Material You" tirées du fond d'écran
    // écrasaient l'identité Flipper (orange/noir). On garde donc la marque.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> FlipperDarkColorScheme
        else -> FlipperLightColorScheme
    }
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}