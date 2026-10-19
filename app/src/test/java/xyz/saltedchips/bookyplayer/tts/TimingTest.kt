package xyz.saltedchips.bookyplayer.tts

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.saltedchips.bookyplayer.epub.EpubChapter

class TimingTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val cues = listOf(
        Cue(-1, 0, 0, 0, 900),
        Cue(0, 0, 20, 1000, 3000),
        Cue(0, 21, 40, 3200, 5000),
        Cue(1, 0, 15, 5500, 7000),
    )

    @Test
    fun writesAndReadsBack() {
        val file = File(tmp.root, "000.timing.json")
        TimingFile.write(file, cues)
        assertEquals(cues, TimingFile.read(file))
    }

    @Test
    fun missingFileReadsAsNull() {
        assertNull(TimingFile.read(File(tmp.root, "none.json")))
    }

    @Test
    fun findsTheCueBeingSpoken() {
        assertNull(TimingFile.cueAt(cues, 500)) // the spoken title is not highlighted
        assertEquals(cues[1], TimingFile.cueAt(cues, 1000))
        assertEquals(cues[1], TimingFile.cueAt(cues, 3100)) // pause between pieces keeps the last one
        assertEquals(cues[2], TimingFile.cueAt(cues, 3200))
        assertEquals(cues[3], TimingFile.cueAt(cues, 60_000))
        assertNull(TimingFile.cueAt(emptyList(), 100))
    }

    @Test
    fun estimatesAccountForThePausesAndTheTitle() {
        val chapter = EpubChapter("One", "First paragraph here.\n\nSecond paragraph is a bit longer.")
        val estimated = TimingFile.estimate(chapter, durationMs = 10_000)
        assertEquals(2, estimated.size)
        // The spoken title and its pause come first, so the text starts after zero.
        assertTrue(estimated.first().startMs > 900L)
        assertTrue(estimated.last().endMs < 10_000L)
        // Paragraph pause sits between the two.
        assertTrue(estimated[1].startMs - estimated[0].endMs >= 450L)
        assertTrue(estimated[1].startMs > estimated[0].startMs)
    }

    @Test
    fun highlightLagScalesWithSpeed() {
        assertEquals(HIGHLIGHT_LAG_MS, highlightLagMs(1f))
        assertEquals(HIGHLIGHT_LAG_MS * 2, highlightLagMs(2f))
        assertTrue(highlightLagMs(0.1f) >= HIGHLIGHT_LAG_MS / 2)
        assertTrue(highlightLagMs(9f) <= HIGHLIGHT_LAG_MS * 3)
    }
}
