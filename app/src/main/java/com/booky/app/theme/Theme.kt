package com.booky.app.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.booky.app.data.Audiobook
import com.booky.app.settings.AppearanceMode

val BookyCoverOuter = Color(0xFF2A1018)
val BookyCoverInner = Color(0xFF8B3A48)
val BookyCoverText = Color(0xFFE8C9C4)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookyTheme(
    appearance: AppearanceMode = AppearanceMode.System,
    book: Audiobook? = null,
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
    val systemScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> expressiveLightColorScheme()
    }
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
    MaterialExpressiveTheme(
        colorScheme = rememberArtworkColorScheme(book, systemScheme, darkTheme),
        motionScheme = MotionScheme.expressive(),
        typography = bookyTypography(),
        content = content,
    )
}
