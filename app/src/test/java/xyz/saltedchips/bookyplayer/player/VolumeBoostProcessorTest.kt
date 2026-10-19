@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package xyz.saltedchips.bookyplayer.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeBoostProcessorTest {
    private fun run(enabled: Boolean, amplitude: Float, seconds: Float = 1f): FloatArray {
        val processor = VolumeBoostProcessor()
        processor.enabled = enabled
        processor.configure(AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_FLOAT))
        processor.flush()
        val frames = (44100 * seconds).toInt()
        val input = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        repeat(frames) { input.putFloat(amplitude * sin(2.0 * PI * 220.0 * it / 44100.0).toFloat()) }
        input.flip()
        processor.queueInput(input)
        val out = processor.output.order(ByteOrder.nativeOrder())
        return FloatArray(out.remaining() / 4) { out.getFloat() }
    }

    @Test
    fun disabledPassesAudioThrough() {
        val out = run(enabled = false, amplitude = 0.1f)
        val expected = FloatArray(out.size) { 0.1f * sin(2.0 * PI * 220.0 * it / 44100.0).toFloat() }
        assertArrayEquals(expected, out, 1e-6f)
    }

    @Test
    fun enabledMakesQuietSpeechLouder() {
        val quiet = 0.03f
        val out = run(enabled = true, amplitude = quiet)
        val tail = out.drop(out.size / 2)
        val peak = tail.fold(0f) { acc, v -> max(acc, abs(v)) }
        assertTrue("peak $peak should exceed input $quiet by >4 dB", peak > quiet * 1.6f)
    }

    @Test
    fun enabledNeverExceedsCeiling() {
        val out = run(enabled = true, amplitude = 1f)
        val peak = out.fold(0f) { acc, v -> max(acc, abs(v)) }
        assertTrue("peak $peak must stay below 0.9", peak <= 0.9f)
    }
}
