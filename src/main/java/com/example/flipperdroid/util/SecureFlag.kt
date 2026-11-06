package com.example.flipperdroid.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/** Retrouve l'Activity hôte en déroulant les ContextWrapper (null si absente). */
private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Active FLAG_SECURE sur la fenêtre de l'Activity tant que ce composable est présent :
 * captures d'écran, enregistrement et aperçu des tâches récentes sont bloqués.
 * Le flag est retiré quand le composable quitte la composition.
 */
@Composable
fun SecureWindow() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
