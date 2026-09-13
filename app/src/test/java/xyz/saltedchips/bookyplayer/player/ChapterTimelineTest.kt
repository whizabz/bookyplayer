package xyz.saltedchips.bookyplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterTimelineTest {
    private val file1 = listOf(1_800_000L, 1_800_000L, 1_800_000L)
    private val file2 = listOf(1_800_000L, 1_800_000L)
    private val durations = file1 + file2
    private val starts = listOf(0L, 1_800_000L, 3_600_000L, 0L, 1_800_000L)

    @Test
    fun chapterBookStartUsesCumulativeDurationsNotFileOffsets() {
        assertEquals(0L, chapterBookStartMs(durations, 0))
        assertEquals(1_800_000L, chapterBookStartMs(durations, 1))
        assertEquals(5_400_000L, chapterBookStartMs(durations, 3))
        assertEquals(7_200_000L, chapterBookStartMs(durations, 4))
        assertEquals(0L, starts[3])
    }

    @Test
    fun seedCompletedDoesNotMarkFutureChaptersInLaterFiles() {
        val listened = 3_600_000L
        val done = seedCompletedChapterIndices(durations, listened)
        assertEquals(setOf(0, 1), done)
    }

    @Test
    fun seedCompletedNearEndMarksOnlyFinishedChapter() {
        val listened = 1_800_000L - 1_000L
        val done = seedCompletedChapterIndices(durations, listened)
        assertEquals(setOf(0), done)
    }

    @Test
    fun fileAbsolutePositionSubtractsChapterStart() {
        val inChapter = inChapterPositionMs(
            currentPositionMs = 1_810_000L,
            chapterStartMs = 1_800_000L,
            chapterDurationMs = 1_800_000L,
            playerDurationMs = 10_800_000L,
        )
        assertEquals(10_000L, inChapter)
    }

    @Test
    fun clipRelativePositionIsUnchanged() {
        val inChapter = inChapterPositionMs(
            currentPositionMs = 10_000L,
            chapterStartMs = 1_800_000L,
            chapterDurationMs = 1_800_000L,
            playerDurationMs = 1_800_000L,
        )
        assertEquals(10_000L, inChapter)
    }

    @Test
    fun unsetPlayerDurationTreatsLargePositionAsFileAbsolute() {
        val inChapter = inChapterPositionMs(
            currentPositionMs = 1_800_000L,
            chapterStartMs = 1_800_000L,
            chapterDurationMs = 1_800_000L,
            playerDurationMs = null,
        )
        assertEquals(0L, inChapter)
    }

    @Test
    fun seekOffsetClampsUnsetWindowToCatalogDuration() {
        val seekPos = inWindowSeekPosition(
            remainingInWindowMs = 7_230_000L,
            windowDurationMs = null,
            catalogDurationMs = 1_800_000L,
        )
        assertEquals(1_800_000L, seekPos)
    }

    @Test
    fun unplayingLastChapterRewindsBookPosition() {
        val durations = listOf(3_600_000L)
        val allPlayed = setOf(0)
        assertEquals(3_600_000L, listenedMsFromChapterMarks(durations, allPlayed))
        assertEquals(true, allChaptersComplete(durations, allPlayed))
        assertEquals(0L, listenedMsFromChapterMarks(durations, emptySet()))
        assertEquals(false, allChaptersComplete(durations, emptySet()))
    }

    @Test
    fun unplayingMiddleChapterUsesFirstIncompleteStart() {
        val completed = setOf(0, 2, 3, 4)
        assertEquals(1_800_000L, listenedMsFromChapterMarks(durations, completed))
        assertEquals(false, allChaptersComplete(durations, completed))
    }
}
