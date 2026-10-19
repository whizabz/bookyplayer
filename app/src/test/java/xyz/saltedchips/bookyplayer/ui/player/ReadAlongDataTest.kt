package xyz.saltedchips.bookyplayer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadAlongDataTest {
    private val data = ReadAlongData(listOf("a", "b", "c"), listOf(1000L, 4000L, 9000L))

    @Test
    fun beforeTheFirstPieceThereIsNoActiveLine() {
        assertEquals(-1, data.activeIndex(0))
        assertEquals(-1, data.activeIndex(999))
    }

    @Test
    fun followsThePositionThroughThePieces() {
        assertEquals(0, data.activeIndex(1000))
        assertEquals(0, data.activeIndex(3999))
        assertEquals(1, data.activeIndex(4000))
        assertEquals(2, data.activeIndex(120_000))
    }

    @Test
    fun emptyDataHasNoActiveLine() {
        assertEquals(-1, ReadAlongData.Empty.activeIndex(5000))
    }
}
