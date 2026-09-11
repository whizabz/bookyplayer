package com.booky.app.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppearanceMode {
    System,
    Light,
    Dark,
}

class AppearanceViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("booky_prefs", 0)

    private val _mode = MutableStateFlow(
        AppearanceMode.entries.getOrElse(prefs.getInt(KEY_APPEARANCE, 0)) { AppearanceMode.System },
    )
    val mode: StateFlow<AppearanceMode> = _mode

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

    private companion object {
        const val KEY_FONT = "app_font"
        const val KEY_USE_INTER = "use_inter"
        const val KEY_CIRCULAR_PROGRESS = "circular_library_progress"
        const val KEY_APPEARANCE = "appearance_mode"
    }
}
