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
): ColorScheme {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    return remember(book?.id, book?.coverUri, book?.artworkFileUri, book?.coverRes, darkTheme, fallback) {
        val extracted = book?.let { extractArtworkSeed(context, it) }
        if (extracted != null) {
            prefs.edit().putInt(KEY_ARTWORK_SEED, extracted.toArgb()).apply()
            colorSchemeFromArtwork(extracted, darkTheme)
        } else {
            cachedArtworkSeed(prefs)?.let { colorSchemeFromArtwork(it, darkTheme) } ?: fallback
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

private fun colorSchemeFromArtwork(seed: Color, darkTheme: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    val hue = hsl[0]
    val chroma = hsl[1].coerceIn(0.18f, 0.72f)

    fun tone(lightness: Float, saturation: Float = chroma) =
        Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))))

    fun onTone(background: Color): Color {
        return if (ColorUtils.calculateLuminance(background.toArgb()) > 0.38) {
            tone(0.12f, chroma * 0.25f)
        } else {
            Color.White
        }
    }

    val secondaryChroma = (chroma * 0.45f).coerceIn(0.08f, 0.4f)
    fun secondary(lightness: Float) = tone(lightness, secondaryChroma)

    return if (darkTheme) {
        val primary = tone(0.80f)
        val primaryContainer = tone(0.30f)
        val secondary = secondary(0.80f)
        val secondaryContainer = secondary(0.28f)
        val tertiary = tone(0.80f, (chroma * 0.7f).coerceAtMost(0.5f))
        val tertiaryContainer = tone(0.28f, (chroma * 0.5f).coerceAtMost(0.4f))
        val surface = tone(0.10f, 0.08f)
        val surfaceContainer = tone(0.14f, 0.10f)
        val surfaceContainerHigh = tone(0.17f, 0.10f)
        val surfaceContainerHighest = tone(0.22f, 0.12f)
        val surfaceContainerLow = tone(0.12f, 0.08f)
        val onSurface = tone(0.92f, 0.06f)
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
            onSurfaceVariant = tone(0.80f, 0.10f),
            inverseSurface = tone(0.90f, 0.06f),
            inverseOnSurface = tone(0.16f, 0.08f),
            outline = tone(0.55f, 0.12f),
            outlineVariant = tone(0.28f, 0.10f),
            surfaceBright = tone(0.24f, 0.10f),
            surfaceDim = tone(0.08f, 0.08f),
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerLowest = tone(0.06f, 0.06f),
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
        val primary = tone(0.40f)
        val primaryContainer = tone(0.90f)
        val secondary = secondary(0.40f)
        val secondaryContainer = secondary(0.90f)
        val tertiary = tone(0.40f, (chroma * 0.55f).coerceAtMost(0.45f))
        val tertiaryContainer = tone(0.90f, 0.18f)
        val surface = tone(0.97f, 0.06f)
        val surfaceContainer = tone(0.94f, 0.07f)
        val surfaceContainerHigh = tone(0.92f, 0.08f)
        val surfaceContainerHighest = tone(0.90f, 0.10f)
        val surfaceContainerLow = tone(0.96f, 0.06f)
        val onSurface = tone(0.12f, 0.18f)
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
            onSurfaceVariant = tone(0.32f, 0.16f),
            inverseSurface = tone(0.16f, 0.08f),
            inverseOnSurface = tone(0.92f, 0.06f),
            outline = tone(0.50f, 0.12f),
            outlineVariant = tone(0.82f, 0.10f),
            surfaceBright = tone(0.98f, 0.05f),
            surfaceDim = tone(0.90f, 0.08f),
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
