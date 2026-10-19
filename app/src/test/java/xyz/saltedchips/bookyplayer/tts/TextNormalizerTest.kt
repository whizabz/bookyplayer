package xyz.saltedchips.bookyplayer.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {
    private fun n(text: String) = TextNormalizer.normalize(text)

    @Test
    fun saintBeforeAName() {
        assertEquals("The Saint Bernard barked.", n("The St. Bernard barked."))
    }

    @Test
    fun streetAfterAName() {
        assertEquals("She lived on Baker Street and walked.", n("She lived on Baker St. and walked."))
    }

    @Test
    fun titles() {
        assertEquals("Mister Smith met Missus Jones and Doctor Watson.", n("Mr. Smith met Mrs. Jones and Dr. Watson."))
    }

    @Test
    fun commonLatinAbbreviations() {
        assertEquals("cats versus dogs, for example pugs", n("cats vs. dogs, e.g. pugs"))
        assertEquals("birds, et cetera.", n("birds, etc."))
        assertEquals("fish, et cetera. Then", n("fish, etc. Then"))
    }

    @Test
    fun timesOfDay() {
        assertEquals("At 5 PM or 7 AM.", n("At 5 p.m. or 7 a.m."))
    }

    @Test
    fun leavesOrdinaryTextAlone() {
        val text = "It was a dog. A big dog, St John's Wood is nearby."
        assertEquals(text, n(text))
    }
}
