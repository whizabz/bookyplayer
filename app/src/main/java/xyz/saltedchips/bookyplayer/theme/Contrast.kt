package xyz.saltedchips.bookyplayer.theme

import android.app.UiModeManager
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import xyz.saltedchips.bookyplayer.settings.ContrastPreference

private const val ContrastEpsilon = 0.05f

/** System colour contrast: 0 Default, ~0.5 Medium, 1 High. */
fun ContrastPreference.contrastAmount(systemContrast: Float): Float = when (this) {
    ContrastPreference.System -> systemContrast.coerceIn(0f, 1f)
    ContrastPreference.Default -> 0f
    ContrastPreference.Medium -> 0.5f
    ContrastPreference.High -> 1f
}

fun ColorScheme.adjustedForContrast(
    darkTheme: Boolean,
    from: Float,
    to: Float,
): ColorScheme {
    val delta = (to - from).coerceIn(-1f, 1f)
    if (abs(delta) < ContrastEpsilon) return this
    return if (darkTheme) {
        copy(
            primary = primary.shiftLightness(0.10f * delta),
            secondary = secondary.shiftLightness(0.10f * delta),
            tertiary = tertiary.shiftLightness(0.08f * delta),
            background = background.shiftLightness(-0.07f * delta),
            surface = surface.shiftLightness(-0.07f * delta),
            onBackground = onBackground.shiftLightness(0.14f * delta),
            onSurface = onSurface.shiftLightness(0.14f * delta),
            onSurfaceVariant = onSurfaceVariant.shiftLightness(0.16f * delta),
            outline = outline.shiftLightness(0.22f * delta),
            outlineVariant = outlineVariant.shiftLightness(-0.10f * delta),
            surfaceContainer = surfaceContainer.shiftLightness(-0.05f * delta),
            surfaceContainerHigh = surfaceContainerHigh.shiftLightness(-0.05f * delta),
            surfaceContainerHighest = surfaceContainerHighest.shiftLightness(-0.04f * delta),
        )
    } else {
        copy(
            primary = primary.shiftLightness(-0.14f * delta),
            secondary = secondary.shiftLightness(-0.14f * delta),
            tertiary = tertiary.shiftLightness(-0.12f * delta),
            onBackground = onBackground.shiftLightness(-0.16f * delta),
            onSurface = onSurface.shiftLightness(-0.16f * delta),
            onSurfaceVariant = onSurfaceVariant.shiftLightness(-0.20f * delta),
            onPrimaryContainer = onPrimaryContainer.shiftLightness(-0.10f * delta),
            outline = outline.shiftLightness(-0.26f * delta),
            outlineVariant = outlineVariant.shiftLightness(-0.12f * delta),
        )
    }
}

private fun Color.shiftLightness(delta: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[2] = (hsl[2] + delta).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}
@Composable
fun rememberSystemContrast(): Float {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return 0f
    val manager = remember {
        context.applicationContext.getSystemService(UiModeManager::class.java)
    }
    var contrast by remember {
        mutableFloatStateOf(manager?.contrast?.coerceIn(0f, 1f) ?: 0f)
    }
    DisposableEffect(manager) {
        if (manager == null) return@DisposableEffect onDispose { }
        val listener = UiModeManager.ContrastChangeListener { value ->
            contrast = value.coerceIn(0f, 1f)
        }
        manager.addContrastChangeListener(
            ContextCompat.getMainExecutor(context),
            listener,
        )
        onDispose { manager.removeContrastChangeListener(listener) }
    }
    return contrast
}
