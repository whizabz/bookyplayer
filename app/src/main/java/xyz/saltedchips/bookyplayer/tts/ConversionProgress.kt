package xyz.saltedchips.bookyplayer.tts

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import xyz.saltedchips.bookyplayer.MainActivity

/** Overall progress (0..1, including the chapter in progress) and a time estimate, if known yet. */
data class LiveProgress(val fraction: Float, val etaMs: Long? = null)

/** Live progress of each running conversion. */
object ConversionProgress {
    private val _byBook = MutableStateFlow<Map<String, LiveProgress>>(emptyMap())
    val byBook: StateFlow<Map<String, LiveProgress>> = _byBook

    fun set(uuid: String, progress: LiveProgress) {
        _byBook.update { it + (uuid to progress.copy(fraction = progress.fraction.coerceIn(0f, 1f))) }
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

/** "About 2 h 10 min left" style text for a remaining time. */
fun formatEta(etaMs: Long): String {
    if (etaMs < 60_000L) return "Less than a minute left"
    val minutes = (etaMs + 59_999L) / 60_000L
    return when {
        minutes < 1 -> "Less than a minute left"
        minutes < 60 -> "About $minutes min left"
        else -> {
            val rounded = ((minutes + 2) / 5) * 5
            val hours = rounded / 60
            val rest = rounded % 60
            if (rest == 0L) "About $hours h left" else "About $hours h $rest min left"
        }
    }
}

/** Estimates time left from how much was done in this run so far; null until it is trustworthy. */
fun estimateEtaMs(elapsedMs: Long, startFraction: Float, currentFraction: Float): Long? {
    val gained = currentFraction - startFraction
    if (elapsedMs < 30_000L || gained < 0.005f) return null
    return Math.round((1.0 * (1f - currentFraction) / gained) * elapsedMs).coerceAtLeast(0L)
}
