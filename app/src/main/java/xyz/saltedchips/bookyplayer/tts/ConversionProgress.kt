package xyz.saltedchips.bookyplayer.tts

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import xyz.saltedchips.bookyplayer.MainActivity

/** Live overall progress (0..1, including the chapter in progress) of each running conversion. */
object ConversionProgress {
    private val _byBook = MutableStateFlow<Map<String, Float>>(emptyMap())
    val byBook: StateFlow<Map<String, Float>> = _byBook

    fun set(uuid: String, fraction: Float) {
        _byBook.update { it + (uuid to fraction.coerceIn(0f, 1f)) }
    }

    fun clear(uuid: String) {
        _byBook.update { it - uuid }
    }
}

/** Lets a tapped notification ask the app to open the generating-audiobooks sheet. */
object ConversionsRequests {
    const val EXTRA_OPEN_CONVERSIONS = "open_conversions"

    private val _signal = MutableStateFlow(0L)
    val signal: StateFlow<Long> = _signal

    fun request() {
        _signal.value = System.nanoTime()
    }

    /** A pending intent that opens the app, and the progress sheet when [openSheet] is set. */
    fun pendingIntent(context: Context, requestCode: Int, openSheet: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_CONVERSIONS, openSheet)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
