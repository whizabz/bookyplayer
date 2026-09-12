package xyz.saltedchips.bookyplayer.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.graphics.shapes.RoundedPolygon
import xyz.saltedchips.bookyplayer.R
import xyz.saltedchips.bookyplayer.settings.PlaceholderCoverStyle
import xyz.saltedchips.bookyplayer.settings.PlaceholderFont
import xyz.saltedchips.bookyplayer.settings.PlaceholderShapeOption

val LocalPlaceholderCoverStyle = compositionLocalOf { PlaceholderCoverStyle.Default }

fun placeholderFontFamily(style: PlaceholderCoverStyle): FontFamily {
    val variation = FontVariation.Settings(
        FontVariation.weight(style.weight.toInt().coerceIn(100, 900)),
    )
    val res = when (style.font) {
        PlaceholderFont.Serif -> R.font.newsreader
        PlaceholderFont.Sans -> R.font.oswald
    }
    return FontFamily(Font(res, variationSettings = variation))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun placeholderShapeOptions(): List<PlaceholderShapeOption> = listOf(
    PlaceholderShapeOption(PlaceholderCoverStyle.SHAPE_AUTO, "Auto"),
    PlaceholderShapeOption("cookie9", "Cookie 9"),
    PlaceholderShapeOption("cookie12", "Cookie 12"),
    PlaceholderShapeOption("clover4", "Clover 4"),
    PlaceholderShapeOption("clover8", "Clover 8"),
    PlaceholderShapeOption("pill", "Pill"),
    PlaceholderShapeOption("gem", "Gem"),
    PlaceholderShapeOption("sunny", "Sunny"),
    PlaceholderShapeOption("flower", "Flower"),
    PlaceholderShapeOption("puffy", "Puffy"),
    PlaceholderShapeOption("oval", "Oval"),
    PlaceholderShapeOption("arch", "Arch"),
    PlaceholderShapeOption("slanted", "Slanted"),
    PlaceholderShapeOption("very_sunny", "Very sunny"),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun placeholderPolygon(shapeId: String, seed: String): RoundedPolygon {
    val catalog = listOf(
        "cookie9" to MaterialShapes.Cookie9Sided,
        "cookie12" to MaterialShapes.Cookie12Sided,
        "clover4" to MaterialShapes.Clover4Leaf,
        "clover8" to MaterialShapes.Clover8Leaf,
        "pill" to MaterialShapes.Pill,
        "gem" to MaterialShapes.Gem,
        "sunny" to MaterialShapes.Sunny,
        "flower" to MaterialShapes.Flower,
        "puffy" to MaterialShapes.Puffy,
        "oval" to MaterialShapes.Oval,
        "arch" to MaterialShapes.Arch,
        "slanted" to MaterialShapes.Slanted,
        "very_sunny" to MaterialShapes.VerySunny,
    )
    if (shapeId != PlaceholderCoverStyle.SHAPE_AUTO) {
        catalog.firstOrNull { it.first == shapeId }?.let { return it.second }
    }
    val index = seed.hashCode().mod(catalog.size)
    return catalog[index].second
}

fun bookyTypography(): Typography = Typography()
