package xyz.saltedchips.bookyplayer.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun animateColorSchemeAsState(
    target: ColorScheme,
    animationSpec: FiniteAnimationSpec<Color> = MotionScheme.expressive().defaultEffectsSpec(),
): ColorScheme {
    @Composable
    fun color(value: Color, label: String): Color {
        val animated by animateColorAsState(
            targetValue = value,
            animationSpec = animationSpec,
            label = label,
        )
        return animated
    }
    return target.copy(
        primary = color(target.primary, "scheme-primary"),
        onPrimary = color(target.onPrimary, "scheme-onPrimary"),
        primaryContainer = color(target.primaryContainer, "scheme-primaryContainer"),
        onPrimaryContainer = color(target.onPrimaryContainer, "scheme-onPrimaryContainer"),
        inversePrimary = color(target.inversePrimary, "scheme-inversePrimary"),
        secondary = color(target.secondary, "scheme-secondary"),
        onSecondary = color(target.onSecondary, "scheme-onSecondary"),
        secondaryContainer = color(target.secondaryContainer, "scheme-secondaryContainer"),
        onSecondaryContainer = color(target.onSecondaryContainer, "scheme-onSecondaryContainer"),
        tertiary = color(target.tertiary, "scheme-tertiary"),
        onTertiary = color(target.onTertiary, "scheme-onTertiary"),
        tertiaryContainer = color(target.tertiaryContainer, "scheme-tertiaryContainer"),
        onTertiaryContainer = color(target.onTertiaryContainer, "scheme-onTertiaryContainer"),
        background = color(target.background, "scheme-background"),
        onBackground = color(target.onBackground, "scheme-onBackground"),
        surface = color(target.surface, "scheme-surface"),
        onSurface = color(target.onSurface, "scheme-onSurface"),
        surfaceVariant = color(target.surfaceVariant, "scheme-surfaceVariant"),
        onSurfaceVariant = color(target.onSurfaceVariant, "scheme-onSurfaceVariant"),
        surfaceTint = color(target.surfaceTint, "scheme-surfaceTint"),
        inverseSurface = color(target.inverseSurface, "scheme-inverseSurface"),
        inverseOnSurface = color(target.inverseOnSurface, "scheme-inverseOnSurface"),
        error = color(target.error, "scheme-error"),
        onError = color(target.onError, "scheme-onError"),
        errorContainer = color(target.errorContainer, "scheme-errorContainer"),
        onErrorContainer = color(target.onErrorContainer, "scheme-onErrorContainer"),
        outline = color(target.outline, "scheme-outline"),
        outlineVariant = color(target.outlineVariant, "scheme-outlineVariant"),
        scrim = color(target.scrim, "scheme-scrim"),
        surfaceBright = color(target.surfaceBright, "scheme-surfaceBright"),
        surfaceDim = color(target.surfaceDim, "scheme-surfaceDim"),
        surfaceContainer = color(target.surfaceContainer, "scheme-surfaceContainer"),
        surfaceContainerHigh = color(target.surfaceContainerHigh, "scheme-surfaceContainerHigh"),
        surfaceContainerHighest = color(target.surfaceContainerHighest, "scheme-surfaceContainerHighest"),
        surfaceContainerLow = color(target.surfaceContainerLow, "scheme-surfaceContainerLow"),
        surfaceContainerLowest = color(target.surfaceContainerLowest, "scheme-surfaceContainerLowest"),
        primaryFixed = color(target.primaryFixed, "scheme-primaryFixed"),
        primaryFixedDim = color(target.primaryFixedDim, "scheme-primaryFixedDim"),
        onPrimaryFixed = color(target.onPrimaryFixed, "scheme-onPrimaryFixed"),
        onPrimaryFixedVariant = color(target.onPrimaryFixedVariant, "scheme-onPrimaryFixedVariant"),
        secondaryFixed = color(target.secondaryFixed, "scheme-secondaryFixed"),
        secondaryFixedDim = color(target.secondaryFixedDim, "scheme-secondaryFixedDim"),
        onSecondaryFixed = color(target.onSecondaryFixed, "scheme-onSecondaryFixed"),
        onSecondaryFixedVariant = color(target.onSecondaryFixedVariant, "scheme-onSecondaryFixedVariant"),
        tertiaryFixed = color(target.tertiaryFixed, "scheme-tertiaryFixed"),
        tertiaryFixedDim = color(target.tertiaryFixedDim, "scheme-tertiaryFixedDim"),
        onTertiaryFixed = color(target.onTertiaryFixed, "scheme-onTertiaryFixed"),
        onTertiaryFixedVariant = color(target.onTertiaryFixedVariant, "scheme-onTertiaryFixedVariant"),
    )
}
