package xyz.saltedchips.bookyplayer.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {
    @Test
    fun marksParagraphEnds() {
        val chunks = TextChunker.chunk("One. Two.\n\nThree.")
        assertEquals(2, chunks.size)
        assertEquals("One. Two.", chunks[0].text)
        assertTrue(chunks[0].endsParagraph)
        assertEquals("Three.", chunks[1].text)
        assertTrue(chunks[1].endsParagraph)
    }

    @Test
    fun keepsChunksUnderTheLimit() {
        val sentence = "word ".repeat(40).trim() + "."
        val chunks = TextChunker.chunk(List(10) { sentence }.joinToString(" "), maxChars = 120)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.text.length <= 120 })
    }

    @Test
    fun splitsOverlongSentencesAndKeepsAllWords() {
        val long = List(200) { "w$it" }.joinToString(" ")
        val chunks = TextChunker.chunk(long, maxChars = 100)
        assertTrue(chunks.all { it.text.length <= 100 })
        assertEquals(long, chunks.joinToString(" ") { it.text })
    }

    @Test
    fun ignoresBlankInput() {
        assertTrue(TextChunker.chunk("  \n\n  ").isEmpty())
    }
}
