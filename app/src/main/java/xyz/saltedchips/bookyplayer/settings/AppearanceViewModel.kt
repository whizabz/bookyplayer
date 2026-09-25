package xyz.saltedchips.bookyplayer.settings

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppearanceMode {
    System,
    Light,
    Dark,
}

enum class ColorTheme {
    Default,
    System,
    Book,
}

enum class AccentColor(val seed: Color) {
    ElectricBlue(Color(0xFF1565C0)),
    Violet(Color(0xFF5B4BDB)),
    ElectricPink(Color(0xFFE91E8C)),
    Carmine(Color(0xFFC62828)),
    Ember(Color(0xFFE65100)),
    Marigold(Color(0xFFF9A825)),
    Fern(Color(0xFF2E7D32)),
    Graphite(Color(0xFF546E7A)),
}

enum class ContrastPreference {
    System,
    Default,
    Medium,
    High,
}

class AppearanceViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("booky_prefs", 0)

    private val _mode = MutableStateFlow(
        AppearanceMode.entries.getOrElse(prefs.getInt(KEY_APPEARANCE, 0)) { AppearanceMode.System },
    )
    val mode: StateFlow<AppearanceMode> = _mode

    private val _colorTheme = MutableStateFlow(loadColorTheme())
    val colorTheme: StateFlow<ColorTheme> = _colorTheme

    private val _accentColor = MutableStateFlow(loadAccentColor())
    val accentColor: StateFlow<AccentColor> = _accentColor

    private val _contrastPreference = MutableStateFlow(
        ContrastPreference.entries.getOrElse(
            prefs.getInt(KEY_CONTRAST, ContrastPreference.System.ordinal),
        ) { ContrastPreference.System },
    )
    val contrastPreference: StateFlow<ContrastPreference> = _contrastPreference

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
            .remove(KEY_PROGRESS_BAR)
            .apply()
    }

    fun setMode(mode: AppearanceMode) {
        prefs.edit().putInt(KEY_APPEARANCE, mode.ordinal).apply()
        _mode.value = mode
    }

    fun setColorTheme(theme: ColorTheme) {
        prefs.edit()
            .putString(KEY_COLOR_THEME_NAME, theme.name)
            .remove(KEY_COLOR_THEME)
            .apply()
        _colorTheme.value = theme
    }

    fun setAccentColor(accent: AccentColor) {
        prefs.edit().putString(KEY_ACCENT_COLOR, accent.name).apply()
        _accentColor.value = accent
    }

    fun setContrastPreference(preference: ContrastPreference) {
        prefs.edit().putInt(KEY_CONTRAST, preference.ordinal).apply()
        _contrastPreference.value = preference
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

    private fun loadColorTheme(): ColorTheme {
        val named = prefs.getString(KEY_COLOR_THEME_NAME, null)
        if (named != null) {
            return ColorTheme.entries.find { it.name == named } ?: ColorTheme.Default
        }
        val theme = if (prefs.getInt(KEY_COLOR_THEME, -1) == 1) {
            ColorTheme.Book
        } else {
            ColorTheme.Default
        }
        prefs.edit()
            .putString(KEY_COLOR_THEME_NAME, theme.name)
            .remove(KEY_COLOR_THEME)
            .apply()
        return theme
    }

    private fun loadAccentColor(): AccentColor {
        val named = prefs.getString(KEY_ACCENT_COLOR, null)
        return AccentColor.entries.find { it.name == named } ?: AccentColor.ElectricBlue
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
        const val KEY_COLOR_THEME_NAME = "color_theme_name"
        const val KEY_ACCENT_COLOR = "accent_color"
        const val KEY_CONTRAST = "contrast_preference"
        const val KEY_PROGRESS_BAR = "progress_bar_style"
        const val KEY_PLACEHOLDER_SHAPE = "placeholder_cover_shape"
        const val KEY_PLACEHOLDER_FONT = "placeholder_cover_font"
        const val KEY_PLACEHOLDER_WEIGHT = "placeholder_cover_weight"
        const val KEY_PLACEHOLDER_OPSZ = "placeholder_cover_opsz"
        const val KEY_PLACEHOLDER_SOFT = "placeholder_cover_soft"
        const val KEY_PLACEHOLDER_WONK = "placeholder_cover_wonk"
        const val KEY_NOTIFICATIONS_PROMPTED = "notifications_prompted"
    }
}
