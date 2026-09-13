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

fun Player.chapterWindows(): Pair<List<Long>, List<Long>> {
    val count = mediaItemCount
    if (count <= 0) return emptyList<Long>() to emptyList()
    val starts = ArrayList<Long>(count)
    val durations = ArrayList<Long>(count)
    val window = Timeline.Window()
    val timeline = currentTimeline
    for (i in 0 until count) {
        val clip = getMediaItemAt(i).clippingConfiguration
        val start = clip.startPositionMs.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
        val end = clip.endPositionMs
        val fromClip = if (end != C.TIME_END_OF_SOURCE && end > start) end - start else C.TIME_UNSET
        val fromWindow = if (!timeline.isEmpty && i < timeline.windowCount) {
            timeline.getWindow(i, window)
            window.durationMs
        } else {
            C.TIME_UNSET
        }
        val length = when {
            fromClip != C.TIME_UNSET && fromClip > 0L -> fromClip
            fromWindow != C.TIME_UNSET && fromWindow > 0L -> fromWindow
            else -> 0L
        }
        starts += start
        durations += length
    }
    return starts to durations
}

fun Player.inChapterPositionMs(
    durations: List<Long> = emptyList(),
    starts: List<Long> = emptyList(),
): Long {
    val index = currentMediaItemIndex.coerceAtLeast(0)
    val catalogDuration = durations.getOrNull(index) ?: 0L
    val startMs = starts.getOrNull(index) ?: 0L
    val playerDuration = duration.takeIf { it != C.TIME_UNSET && it > 0L }
    return inChapterPositionMs(
        currentPositionMs = currentPosition,
        chapterStartMs = startMs,
        chapterDurationMs = catalogDuration,
        playerDurationMs = playerDuration,
    )
}

fun Player.bookPositionMs(
    durations: List<Long> = emptyList(),
    starts: List<Long> = emptyList(),
): Long {
    val index = currentMediaItemIndex.coerceAtLeast(0)
    val windows = if (durations.isEmpty() || starts.isEmpty()) chapterWindows() else null
    val catalogDurations = durations.ifEmpty { windows?.second.orEmpty() }
    val catalogStarts = starts.ifEmpty { windows?.first.orEmpty() }
    val prior = if (catalogDurations.isNotEmpty()) {
        chapterBookStartMs(catalogDurations, index)
    } else {
        priorWindowDurationMs(index)
    }
    return prior + inChapterPositionMs(catalogDurations, catalogStarts)
}

private fun Player.priorWindowDurationMs(index: Int): Long {
    val timeline = currentTimeline
    if (timeline.isEmpty) return 0L
    var prior = 0L
    val window = Timeline.Window()
    val last = index.coerceAtMost(timeline.windowCount)
    for (i in 0 until last) {
        timeline.getWindow(i, window)
        val duration = window.durationMs
        if (duration != C.TIME_UNSET) prior += duration
    }
    return prior
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
        val catalog = durations.getOrNull(i)?.takeIf { it > 0L }
        val length = if (duration == C.TIME_UNSET || duration <= 0L) {
            catalog ?: Long.MAX_VALUE
        } else {
            duration
        }
        if (remaining < length || i == last) {
            val windowDuration = duration.takeIf { it != C.TIME_UNSET && it > 0L }
            val seekPos = inWindowSeekPosition(remaining, windowDuration, catalog)
            seekTo(i, seekPos)
            return
        }
        remaining -= length
    }
}

fun Player.skipBookPosition(deltaMs: Long, durations: List<Long> = emptyList(), starts: List<Long> = emptyList()) {
    val catalogDurations = durations.ifEmpty { chapterWindows().second }
    val catalogStarts = starts.ifEmpty { chapterWindows().first }
    val fallbackDuration = catalogDurations.sumOf { it.coerceAtLeast(0L) }
    val duration = bookDurationMs(fallbackDuration.coerceAtLeast(1L))
    val target = (bookPositionMs(catalogDurations, catalogStarts) + deltaMs).coerceIn(0L, duration)
    seekToBookPosition(target, catalogDurations)
}
