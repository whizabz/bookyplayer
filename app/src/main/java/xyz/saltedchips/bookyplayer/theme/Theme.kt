package xyz.saltedchips.bookyplayer.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import xyz.saltedchips.bookyplayer.data.Audiobook
import xyz.saltedchips.bookyplayer.settings.AccentColor
import xyz.saltedchips.bookyplayer.settings.AppearanceMode
import xyz.saltedchips.bookyplayer.settings.ColorTheme
import xyz.saltedchips.bookyplayer.settings.ContrastPreference
import xyz.saltedchips.bookyplayer.settings.PlaceholderCoverStyle

val LocalSystemColorScheme = compositionLocalOf { darkColorScheme() }

val BookyCoverOuter = Color(0xFF2A1018)
val BookyCoverInner = Color(0xFF8B3A48)
val BookyCoverText = Color(0xFFE8C9C4)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookyTheme(
    appearance: AppearanceMode = AppearanceMode.System,
    colorTheme: ColorTheme = ColorTheme.Default,
    accentColor: AccentColor = AccentColor.ElectricBlue,
    contrastPreference: ContrastPreference = ContrastPreference.System,
    book: Audiobook? = null,
    placeholderCover: PlaceholderCoverStyle = PlaceholderCoverStyle.Default,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (appearance) {
        AppearanceMode.System -> systemDark
        AppearanceMode.Light -> false
        AppearanceMode.Dark -> true
    }
    val context = LocalContext.current
    val view = LocalView.current
    val systemContrast = rememberSystemContrast()
    val contrast = contrastPreference.contrastAmount(systemContrast)
    val systemScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val wallpaper =
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            wallpaper.adjustedForContrast(darkTheme, systemContrast, contrast)
        }
        contrast >= 0.5f -> if (darkTheme) darkColorScheme() else lightColorScheme()
        darkTheme -> darkColorScheme()
        else -> expressiveLightColorScheme()
    }
    val targetScheme = when (colorTheme) {
        ColorTheme.Default -> colorSchemeFromAccent(accentColor.seed, darkTheme, contrast)
        ColorTheme.System -> systemScheme
        ColorTheme.Book -> rememberArtworkColorScheme(book, systemScheme, darkTheme, contrast)
    }
    val colorScheme = animateColorSchemeAsState(targetScheme)
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        (activity as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = if (darkTheme) {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            },
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT,
            ) { _ -> darkTheme },
        )
    }
    CompositionLocalProvider(
        LocalPlaceholderCoverStyle provides placeholderCover,
        LocalSystemColorScheme provides systemScheme,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = bookyTypography(),
            content = content,
        )
    }
}
