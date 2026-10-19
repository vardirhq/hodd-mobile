package no.vardir.hodd.ui

import android.graphics.Rect
import android.os.Build
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.Insets
import kotlin.math.max

/** Dialogs can receive consumed Compose insets. Measure overlap with the native safe window instead. */
@Composable
internal fun dialogSafePadding(): PaddingValues {
    val view = LocalView.current
    val density = LocalDensity.current
    var overlap by remember(view) { mutableStateOf(Insets.NONE) }
    DisposableEffect(view) {
        val position = IntArray(2)
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            if (view.width > 0 && view.height > 0) {
                val safe = if (Build.VERSION.SDK_INT >= 30) {
                    val metrics = (view.context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager).currentWindowMetrics
                    val bars = metrics.windowInsets.getInsets(android.view.WindowInsets.Type.systemBars() or
                        android.view.WindowInsets.Type.displayCutout() or android.view.WindowInsets.Type.ime())
                    Rect(metrics.bounds).apply { left += bars.left; top += bars.top; right -= bars.right; bottom -= bars.bottom }
                } else Rect().also(view::getWindowVisibleDisplayFrame)
                view.getLocationOnScreen(position)
                overlap = Insets.of(max(0, safe.left - position[0]), max(0, safe.top - position[1]),
                    max(0, position[0] + view.width - safe.right), max(0, position[1] + view.height - safe.bottom))
            }
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        view.post { if (view.isAttachedToWindow) listener.onGlobalLayout() }
        onDispose { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return with(density) { PaddingValues(overlap.left.toDp(), overlap.top.toDp(), overlap.right.toDp(), overlap.bottom.toDp()) }
}
