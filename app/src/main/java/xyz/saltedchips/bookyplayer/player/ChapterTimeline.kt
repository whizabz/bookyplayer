package xyz.saltedchips.bookyplayer.player

fun chapterBookStartMs(durations: List<Long>, index: Int): Long {
    if (index <= 0 || durations.isEmpty()) return 0L
    val last = index.coerceAtMost(durations.size)
    var start = 0L
    for (i in 0 until last) {
        start += durations[i].coerceAtLeast(0L)
    }
    return start
}

fun inChapterPositionMs(
    currentPositionMs: Long,
    chapterStartMs: Long,
    chapterDurationMs: Long,
    playerDurationMs: Long? = null,
): Long {
    val pos = currentPositionMs.coerceAtLeast(0L)
    val catalog = chapterDurationMs.coerceAtLeast(0L)
    if (catalog <= 0L) return pos
    val start = chapterStartMs.coerceAtLeast(0L)
    val playerDuration = playerDurationMs?.takeIf { it > 0L }
    val unclipped = playerDuration == null ||
        playerDuration > catalog + 2_000L ||
        pos > catalog
    return if (unclipped) {
        (pos - start).coerceIn(0L, catalog)
    } else {
        pos.coerceAtMost(catalog)
    }
}

fun inWindowSeekPosition(
    remainingInWindowMs: Long,
    windowDurationMs: Long?,
    catalogDurationMs: Long?,
): Long {
    val remaining = remainingInWindowMs.coerceAtLeast(0L)
    val maxOffset = windowDurationMs?.takeIf { it > 0L }
        ?: catalogDurationMs?.takeIf { it > 0L }
        ?: remaining
    return remaining.coerceIn(0L, maxOffset)
}

fun seedCompletedChapterIndices(
    durations: List<Long>,
    bookPositionMs: Long,
    nearEndMs: Long = 2_000L,
): Set<Int> {
    if (durations.isEmpty()) return emptySet()
    val done = mutableSetOf<Int>()
    var start = 0L
    durations.forEachIndexed { index, duration ->
        val end = start + duration.coerceAtLeast(0L)
        if (bookPositionMs >= end - nearEndMs) done += index
        start = end
    }
    return done
}
