package com.booky.app.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.graphics.shapes.RoundedPolygon
import com.booky.app.R
import com.booky.app.settings.PlaceholderCoverStyle
import com.booky.app.settings.PlaceholderFont
import com.booky.app.settings.PlaceholderShapeOption

val LocalPlaceholderCoverStyle = compositionLocalOf { PlaceholderCoverStyle.Default }

val GoogleSansFontFamily = FontFamily(
    Font(R.font.google_sans_regular, FontWeight.Normal),
    Font(R.font.google_sans_medium, FontWeight.Medium),
    Font(R.font.google_sans_semibold, FontWeight.SemiBold),
    Font(R.font.google_sans_bold, FontWeight.Bold),
)

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

fun bookyTypography(): Typography {
    val base = Typography()
    val fontFamily = GoogleSansFontFamily
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily),
    )
}
