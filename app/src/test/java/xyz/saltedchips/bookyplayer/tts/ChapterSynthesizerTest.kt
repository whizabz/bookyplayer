package xyz.saltedchips.bookyplayer.tts

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.saltedchips.bookyplayer.epub.EpubChapter

class ChapterSynthesizerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val chapter = EpubChapter("One", "First paragraph here.\n\nSecond paragraph is a bit longer.")

    @Test
    fun noCheckpointMeansNoSavedProgress() {
        val output = File(tmp.root, "000.m4a.part")
        assertEquals(0f, ChapterSynthesizer.savedFraction(chapter, output), 0f)
    }

    @Test
    fun savedFractionFollowsFinishedPieces() {
        val output = File(tmp.root, "000.m4a.part")
        // Three pieces: the spoken title, then two paragraphs. The first two are done.
        File(tmp.root, "000.m4a.part.pcm").writeBytes(ByteArray(1000))
        File(tmp.root, "000.m4a.part.state").writeText("2,1000,3")
        val titleChars = "One.".length
        val first = "First paragraph here.".length
        val second = "Second paragraph is a bit longer.".length
        val expected = (titleChars + first).toFloat() / (titleChars + first + second)
        assertEquals(expected, ChapterSynthesizer.savedFraction(chapter, output), 0.0001f)
    }

    @Test
    fun ignoresACheckpointThatDoesNotMatchTheText() {
        val output = File(tmp.root, "000.m4a.part")
        File(tmp.root, "000.m4a.part.pcm").writeBytes(ByteArray(1000))
        File(tmp.root, "000.m4a.part.state").writeText("2,1000,9")
        assertEquals(0f, ChapterSynthesizer.savedFraction(chapter, output), 0f)
    }
}
