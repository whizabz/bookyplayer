package xyz.saltedchips.bookyplayer.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import xyz.saltedchips.bookyplayer.data.Audiobook
import xyz.saltedchips.bookyplayer.library.CoverLoader

@Composable
fun rememberArtworkColorScheme(
    book: Audiobook?,
    fallback: ColorScheme,
    darkTheme: Boolean = isSystemInDarkTheme(),
    contrast: Float = 0f,
): ColorScheme {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val contrastAmount = contrast.coerceIn(0f, 1f)
    return remember(
        book?.id,
        book?.coverUri,
        book?.artworkFileUri,
        book?.coverRes,
        darkTheme,
        fallback,
        contrastAmount,
    ) {
        val extracted = book?.let { extractArtworkSeed(context, it) }
        if (extracted != null) {
            prefs.edit().putInt(KEY_ARTWORK_SEED, extracted.toArgb()).apply()
            colorSchemeFromArtwork(extracted, darkTheme, contrastAmount)
        } else {
            cachedArtworkSeed(prefs)?.let { colorSchemeFromArtwork(it, darkTheme, contrastAmount) }
                ?: fallback
        }
    }
}

private fun cachedArtworkSeed(prefs: SharedPreferences): Color? {
    if (!prefs.contains(KEY_ARTWORK_SEED)) return null
    return Color(prefs.getInt(KEY_ARTWORK_SEED, 0))
}

private fun extractArtworkSeed(context: Context, book: Audiobook): Color? {
    val bitmap = CoverLoader.load(context, book, sampleSize = 8) ?: return null
    return try {
        val palette = Palette.from(bitmap).clearFilters().generate()
        val rgb = palette.vibrantSwatch?.rgb
            ?: palette.lightVibrantSwatch?.rgb
            ?: palette.darkVibrantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: return null
        Color(rgb)
    } finally {
        bitmap.recycle()
    }
}

private fun lerp(start: Float, stop: Float, amount: Float): Float =
    start + (stop - start) * amount

internal fun colorSchemeFromArtwork(seed: Color, darkTheme: Boolean, contrast: Float): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    val hue = hsl[0]
    val chroma = hsl[1].coerceIn(0.18f, 0.72f)
    val t = contrast.coerceIn(0f, 1f)

    fun tone(lightness: Float, saturation: Float = chroma) =
        Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))))

    fun onTone(background: Color): Color {
        val threshold = lerp(0.42f, 0.34f, t)
        val lightOn = lerp(0.22f, 0.06f, t)
        return if (ColorUtils.calculateLuminance(background.toArgb()) > threshold) {
            tone(lightOn, chroma * 0.25f)
        } else {
            Color.White
        }
    }

    val secondaryChroma = (chroma * 0.45f).coerceIn(0.08f, 0.4f)
    fun secondary(lightness: Float) = tone(lightness, secondaryChroma)

    return if (darkTheme) {
        val primary = tone(lerp(0.76f, 0.86f, t))
        val primaryContainer = tone(lerp(0.34f, 0.24f, t))
        val secondary = secondary(lerp(0.76f, 0.86f, t))
        val secondaryContainer = secondary(lerp(0.32f, 0.22f, t))
        val tertiary = tone(lerp(0.76f, 0.86f, t), (chroma * 0.7f).coerceAtMost(0.5f))
        val tertiaryContainer = tone(lerp(0.32f, 0.22f, t), (chroma * 0.5f).coerceAtMost(0.4f))
        val surface = tone(lerp(0.13f, 0.06f, t), 0.08f)
        val surfaceContainer = tone(lerp(0.16f, 0.11f, t), 0.10f)
        val surfaceContainerHigh = tone(lerp(0.19f, 0.14f, t), 0.10f)
        val surfaceContainerHighest = tone(lerp(0.24f, 0.18f, t), 0.12f)
        val surfaceContainerLow = tone(lerp(0.14f, 0.09f, t), 0.08f)
        val onSurface = tone(lerp(0.84f, 0.98f, t), 0.06f)
        darkColorScheme(
            primary = primary,
            onPrimary = onTone(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = onTone(primaryContainer),
            inversePrimary = tone(0.40f),
            secondary = secondary,
            onSecondary = onTone(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onTone(secondaryContainer),
            tertiary = tertiary,
            onTertiary = onTone(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTone(tertiaryContainer),
            background = surface,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceContainerHighest,
            onSurfaceVariant = tone(lerp(0.72f, 0.88f, t), 0.10f),
            inverseSurface = tone(0.90f, 0.06f),
            inverseOnSurface = tone(0.16f, 0.08f),
            outline = tone(lerp(0.46f, 0.68f, t), 0.12f),
            outlineVariant = tone(lerp(0.32f, 0.22f, t), 0.10f),
            surfaceBright = tone(lerp(0.26f, 0.20f, t), 0.10f),
            surfaceDim = tone(lerp(0.10f, 0.05f, t), 0.08f),
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerLowest = tone(lerp(0.08f, 0.03f, t), 0.06f),
            primaryFixed = tone(0.90f),
            primaryFixedDim = tone(0.80f),
            onPrimaryFixed = tone(0.12f),
            onPrimaryFixedVariant = tone(0.28f),
            secondaryFixed = secondary(0.90f),
            secondaryFixedDim = secondary(0.80f),
            onSecondaryFixed = secondary(0.12f),
            onSecondaryFixedVariant = secondary(0.28f),
        )
    } else {
        val primary = tone(lerp(0.46f, 0.32f, t))
        val primaryContainer = tone(lerp(0.92f, 0.86f, t))
        val secondary = secondary(lerp(0.46f, 0.32f, t))
        val secondaryContainer = secondary(lerp(0.92f, 0.86f, t))
        val tertiary = tone(lerp(0.46f, 0.32f, t), (chroma * 0.55f).coerceAtMost(0.45f))
        val tertiaryContainer = tone(lerp(0.92f, 0.86f, t), 0.18f)
        val surface = tone(lerp(0.98f, 0.96f, t), 0.06f)
        val surfaceContainer = tone(lerp(0.95f, 0.92f, t), 0.07f)
        val surfaceContainerHigh = tone(lerp(0.93f, 0.90f, t), 0.08f)
        val surfaceContainerHighest = tone(lerp(0.91f, 0.88f, t), 0.10f)
        val surfaceContainerLow = tone(lerp(0.97f, 0.94f, t), 0.06f)
        val onSurface = tone(lerp(0.24f, 0.08f, t), 0.18f)
        lightColorScheme(
            primary = primary,
            onPrimary = onTone(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = onTone(primaryContainer),
            inversePrimary = tone(0.80f),
            secondary = secondary,
            onSecondary = onTone(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onTone(secondaryContainer),
            tertiary = tertiary,
            onTertiary = onTone(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTone(tertiaryContainer),
            background = surface,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceContainerHighest,
            onSurfaceVariant = tone(lerp(0.42f, 0.22f, t), 0.16f),
            inverseSurface = tone(0.16f, 0.08f),
            inverseOnSurface = tone(0.92f, 0.06f),
            outline = tone(lerp(0.64f, 0.38f, t), 0.12f),
            outlineVariant = tone(lerp(0.86f, 0.74f, t), 0.10f),
            surfaceBright = tone(0.98f, 0.05f),
            surfaceDim = tone(lerp(0.92f, 0.88f, t), 0.08f),
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerLowest = Color.White,
            primaryFixed = primaryContainer,
            primaryFixedDim = tone(0.80f),
            onPrimaryFixed = tone(0.12f),
            onPrimaryFixedVariant = tone(0.28f),
            secondaryFixed = secondaryContainer,
            secondaryFixedDim = secondary(0.80f),
            onSecondaryFixed = secondary(0.12f),
            onSecondaryFixedVariant = secondary(0.28f),
        )
    }
}

private const val PREFS_NAME = "booky_prefs"
private const val KEY_ARTWORK_SEED = "last_artwork_seed"
