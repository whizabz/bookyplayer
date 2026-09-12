package xyz.saltedchips.bookyplayer.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import xyz.saltedchips.bookyplayer.data.Audiobook

fun Audiobook.toMediaItems(): List<MediaItem> {
    val uris = mediaUris.ifEmpty { listOfNotNull(artworkFileUri) }
    val artwork = (coverUri ?: artworkFileUri)?.let(Uri::parse)
    return uris.mapIndexed { index, uri ->
        val startMs = chapterStartMs.getOrNull(index) ?: 0L
        val durationMs = chapterDurationsMs.getOrNull(index) ?: 0L
        val nextSameFile = uris.getOrNull(index + 1) == uri
        val builder = MediaItem.Builder()
            .setUri(uri)
            .setMediaId("$id#$index")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(author)
                    .setAlbumTitle(title)
                    .setDisplayTitle(chapterTitles.getOrNull(index) ?: currentChapterTitle)
                    .setWriter(narrator.takeIf { it.isNotBlank() && it != "Unknown" })
                    .setArtworkUri(artwork)
                    .build(),
            )
        if (startMs > 0L || nextSameFile) {
            val clip = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs.coerceAtLeast(0L))
            if (durationMs > 0L) {
                clip.setEndPositionMs(startMs + durationMs)
            }
            builder.setClippingConfiguration(clip.build())
        }
        builder.build()
    }
}

fun windowForBookPosition(positionMs: Long, durations: List<Long>): Pair<Int, Long> {
    if (durations.isEmpty()) return 0 to positionMs.coerceAtLeast(0L)
    var remaining = positionMs.coerceAtLeast(0L)
    durations.forEachIndexed { index, duration ->
        val length = duration.coerceAtLeast(0L)
        if (index == durations.lastIndex || remaining < length) {
            return index to remaining
        }
        remaining -= length
    }
    return durations.lastIndex to remaining
}

fun Player.bookPositionMs(): Long {
    val timeline = currentTimeline
    if (timeline.isEmpty) return currentPosition.coerceAtLeast(0L)
    var prior = 0L
    val window = Timeline.Window()
    val index = currentMediaItemIndex.coerceAtLeast(0)
    for (i in 0 until index) {
        timeline.getWindow(i, window)
        val duration = window.durationMs
        if (duration != C.TIME_UNSET) prior += duration
    }
    return prior + currentPosition.coerceAtLeast(0L)
}

fun Player.currentChapterDurationMs(fallback: Long): Long {
    val duration = duration
    return if (duration == C.TIME_UNSET || duration <= 0L) fallback.coerceAtLeast(1L) else duration
}

fun Player.bookDurationMs(fallback: Long): Long {
    val timeline = currentTimeline
    if (timeline.isEmpty) {
        val duration = duration
        return if (duration == C.TIME_UNSET || duration <= 0L) fallback.coerceAtLeast(1L) else duration
    }
    var total = 0L
    val window = Timeline.Window()
    for (i in 0 until timeline.windowCount) {
        timeline.getWindow(i, window)
        val duration = window.durationMs
        if (duration == C.TIME_UNSET) return fallback.coerceAtLeast(1L)
        total += duration
    }
    return total.coerceAtLeast(1L)
}

fun Player.seekToBookPosition(positionMs: Long, durations: List<Long> = emptyList()) {
    val timeline = currentTimeline
    if (timeline.isEmpty) {
        if (durations.isNotEmpty()) {
            val (index, offset) = windowForBookPosition(positionMs, durations)
            seekTo(index, offset)
        } else {
            seekTo(positionMs.coerceAtLeast(0L))
        }
        return
    }
    var remaining = positionMs.coerceAtLeast(0L)
    val window = Timeline.Window()
    val last = timeline.windowCount - 1
    for (i in 0 until timeline.windowCount) {
        timeline.getWindow(i, window)
        val duration = window.durationMs
        val length = if (duration == C.TIME_UNSET || duration <= 0L) {
            durations.getOrNull(i)?.takeIf { it > 0L } ?: Long.MAX_VALUE
        } else {
            duration
        }
        if (remaining < length || i == last) {
            val seekPos = if (duration == C.TIME_UNSET) remaining else remaining.coerceAtMost(duration)
            seekTo(i, seekPos.coerceAtLeast(0L))
            return
        }
        remaining -= length
    }
}
