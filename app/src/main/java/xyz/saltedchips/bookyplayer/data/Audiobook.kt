package xyz.saltedchips.bookyplayer.data

import androidx.annotation.DrawableRes

data class Audiobook(
    val id: String,
    val title: String,
    val author: String,
    val narrator: String,
    val chapterCount: Int,
    val fileCount: Int,
    val durationMs: Long,
    val listenedMs: Long,
    val currentChapter: Int,
    val currentChapterTitle: String,
    val fileName: String,
    @param:DrawableRes val coverRes: Int = 0,
    val coverUri: String? = null,
    val artworkFileUri: String? = null,
    val mediaUris: List<String> = emptyList(),
    val chapterTitles: List<String> = emptyList(),
    val chapterDurationsMs: List<Long> = emptyList(),
    val chapterStartMs: List<Long> = emptyList(),
    val addedAtMs: Long = 0L,
    val lastPlayedMs: Long = 0L,
)

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

fun formatMinutes(ms: Long): String {
    val minutes = ((ms + 30_000L) / 60_000L).coerceAtLeast(if (ms > 0L) 1L else 0L)
    return if (minutes == 1L) "1 min" else "$minutes mins"
}

fun formatClock(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
