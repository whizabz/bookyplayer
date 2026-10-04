package xyz.saltedchips.bookyplayer.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataTextTest {
    @Test
    fun repairsWindows1252CurlyApostrophe() {
        assertEquals("Stella’s Magic Mirror", MetadataText.repair("Stellaâ€™s Magic Mirror"))
    }

    @Test
    fun repairsLatin1ControlCurlyApostrophe() {
        assertEquals(
            "Stella’s Magic Mirror",
            MetadataText.repair("Stellaâ\u0080\u0099s Magic Mirror"),
        )
    }

    @Test
    fun leavesCorrectCurlyApostrophe() {
        val title = "Stella’s Magic Mirror"
        assertEquals(title, MetadataText.repair(title))
    }

    @Test
    fun leavesAsciiApostrophe() {
        val title = "Stella's Magic Mirror"
        assertEquals(title, MetadataText.repair(title))
    }

    @Test
    fun preferredLabelKeepsGoodFolderName() {
        assertEquals(
            "Pride and Prejudice",
            MetadataText.preferredLabel("Pride and Prejudice", "Tagged Title"),
        )
    }

    @Test
    fun preferredLabelUsesTagWhenDisplayIsGarbled() {
        assertEquals(
            "Stella’s Magic Mirror",
            MetadataText.preferredLabel("Stellaâ€™s Magic Mirror", "Stella’s Magic Mirror"),
        )
    }

    @Test
    fun preferredLabelUsesTagWhenNameIsGeneric() {
        assertEquals(
            "Stella’s Magic Mirror",
            MetadataText.preferredLabel("Track 01", "Stella’s Magic Mirror"),
        )
    }

    @Test
    fun looksGarbledDetectsMojibake() {
        assertTrue(MetadataText.looksGarbled("Stellaâ€™s"))
        assertFalse(MetadataText.looksGarbled("Stella’s"))
        assertFalse(MetadataText.looksGarbled("Stella's"))
    }
}
