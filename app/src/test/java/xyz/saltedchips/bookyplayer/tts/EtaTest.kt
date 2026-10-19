package xyz.saltedchips.bookyplayer.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EtaTest {
    @Test
    fun formatsShortAndLongTimes() {
        assertEquals("Less than a minute left", formatEta(20_000))
        assertEquals("About 8 min left", formatEta(7 * 60_000L + 10_000))
        assertEquals("About 2 h left", formatEta(120 * 60_000L))
        assertEquals("About 2 h 10 min left", formatEta(131 * 60_000L))
    }

    @Test
    fun noEstimateUntilEnoughProgress() {
        assertNull(estimateEtaMs(10_000, 0f, 0.1f))
        assertNull(estimateEtaMs(60_000, 0.2f, 0.201f))
    }

    @Test
    fun estimatesFromThisRunsPace() {
        // 10% in 10 minutes means 90% more takes 90 minutes.
        assertEquals(90 * 60_000L, estimateEtaMs(10 * 60_000L, 0f, 0.1f))
        // Resumed at 50%, 10% more in 5 minutes: 40% left takes 20 minutes.
        assertEquals(20 * 60_000L, estimateEtaMs(5 * 60_000L, 0.5f, 0.6f))
    }
}
