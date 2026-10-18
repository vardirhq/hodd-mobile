package no.vardir.hodd.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Hodd uses a light palette even when the phone's system theme is dark. */
internal fun ComponentActivity.configureHoddSystemBars() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK),
        navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK),
    )
}

/** The camera is the dark exception; restore the surrounding screen on exit. */
@Composable
internal fun CameraSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.activity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = controller?.isAppearanceLightStatusBars ?: false
        val lightNavigation = controller?.isAppearanceLightNavigationBars ?: false
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = lightStatus
            controller?.isAppearanceLightNavigationBars = lightNavigation
        }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
