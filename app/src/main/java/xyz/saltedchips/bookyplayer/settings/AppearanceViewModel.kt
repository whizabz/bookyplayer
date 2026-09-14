package xyz.saltedchips.bookyplayer.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppearanceMode {
    System,
    Light,
    Dark,
}

enum class ColorTheme {
    System,
    Book,
}

class AppearanceViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("booky_prefs", 0)

    private val _mode = MutableStateFlow(
        AppearanceMode.entries.getOrElse(prefs.getInt(KEY_APPEARANCE, 0)) { AppearanceMode.System },
    )
    val mode: StateFlow<AppearanceMode> = _mode

    private val _colorTheme = MutableStateFlow(
        ColorTheme.entries.getOrElse(prefs.getInt(KEY_COLOR_THEME, ColorTheme.System.ordinal)) {
            ColorTheme.System
        },
    )
    val colorTheme: StateFlow<ColorTheme> = _colorTheme

    private val _placeholderCover = MutableStateFlow(loadPlaceholderCover())
    val placeholderCover: StateFlow<PlaceholderCoverStyle> = _placeholderCover

    private val _notificationsPrompted = MutableStateFlow(
        prefs.getBoolean(KEY_NOTIFICATIONS_PROMPTED, false),
    )
    val notificationsPrompted: StateFlow<Boolean> = _notificationsPrompted

    init {
        prefs.edit()
            .remove(KEY_FONT)
            .remove(KEY_USE_INTER)
            .remove(KEY_CIRCULAR_PROGRESS)
            .apply()
    }

    fun setMode(mode: AppearanceMode) {
        prefs.edit().putInt(KEY_APPEARANCE, mode.ordinal).apply()
        _mode.value = mode
    }

    fun setColorTheme(theme: ColorTheme) {
        prefs.edit().putInt(KEY_COLOR_THEME, theme.ordinal).apply()
        _colorTheme.value = theme
    }

    fun setPlaceholderCover(style: PlaceholderCoverStyle) {
        val next = style.copy(weight = style.weight.coerceIn(100f, 900f))
        prefs.edit()
            .putString(KEY_PLACEHOLDER_SHAPE, next.shapeId)
            .putString(KEY_PLACEHOLDER_FONT, next.font.name)
            .putFloat(KEY_PLACEHOLDER_WEIGHT, next.weight)
            .remove(KEY_PLACEHOLDER_OPSZ)
            .remove(KEY_PLACEHOLDER_SOFT)
            .remove(KEY_PLACEHOLDER_WONK)
            .apply()
        _placeholderCover.value = next
    }

    fun setNotificationsPrompted(prompted: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_PROMPTED, prompted).apply()
        _notificationsPrompted.value = prompted
    }

    private fun loadPlaceholderCover(): PlaceholderCoverStyle {
        val stored = prefs.getString(KEY_PLACEHOLDER_FONT, null)
        val font = when (stored) {
            PlaceholderFont.Sans.name -> PlaceholderFont.Sans
            else -> PlaceholderFont.Serif
        }
        return PlaceholderCoverStyle(
            shapeId = prefs.getString(KEY_PLACEHOLDER_SHAPE, PlaceholderCoverStyle.SHAPE_AUTO)
                ?: PlaceholderCoverStyle.SHAPE_AUTO,
            font = font,
            weight = prefs.getFloat(KEY_PLACEHOLDER_WEIGHT, 600f),
        )
    }

    private companion object {
        const val KEY_FONT = "app_font"
        const val KEY_USE_INTER = "use_inter"
        const val KEY_CIRCULAR_PROGRESS = "circular_library_progress"
        const val KEY_APPEARANCE = "appearance_mode"
        const val KEY_COLOR_THEME = "color_theme"
        const val KEY_PLACEHOLDER_SHAPE = "placeholder_cover_shape"
        const val KEY_PLACEHOLDER_FONT = "placeholder_cover_font"
        const val KEY_PLACEHOLDER_WEIGHT = "placeholder_cover_weight"
        const val KEY_PLACEHOLDER_OPSZ = "placeholder_cover_opsz"
        const val KEY_PLACEHOLDER_SOFT = "placeholder_cover_soft"
        const val KEY_PLACEHOLDER_WONK = "placeholder_cover_wonk"
        const val KEY_NOTIFICATIONS_PROMPTED = "notifications_prompted"
    }
}
