@file:OptIn(UnstableApi::class)

package xyz.saltedchips.bookyplayer.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Makes quiet speech louder: makeup gain, then a compressor that holds peaks down, then a soft
 * limiter so nothing clips. Disabled it copies audio through untouched, and switching is
 * cross-faded over a short ramp so there is no click.
 */
class VolumeBoostProcessor : BaseAudioProcessor() {
    @Volatile
    var enabled: Boolean = false

    private var encoding = C.ENCODING_INVALID
    private var channels = 0
    private var sampleRate = 0
    private var attackCoeff = 0f
    private var releaseCoeff = 0f
    private var rampStep = 0f
    private var amount = 0f
    private var envelopeDb = FLOOR_DB

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat,
    ): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        encoding = inputAudioFormat.encoding
        channels = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        attackCoeff = exp(-1f / (ATTACK_SECONDS * sampleRate))
        releaseCoeff = exp(-1f / (RELEASE_SECONDS * sampleRate))
        rampStep = 1f / (RAMP_SECONDS * sampleRate)
        return inputAudioFormat
    }

    override fun onFlush() {
        envelopeDb = FLOOR_DB
        amount = if (enabled) 1f else 0f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size)
        val target = if (enabled) 1f else 0f
        if (amount == 0f && target == 0f) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frameBytes = bytesPerSample * channels
        val frames = size / frameBytes
        val dry = FloatArray(channels)
        val wet = FloatArray(channels)
        repeat(frames) {
            var peak = 0f
            for (c in 0 until channels) {
                val sample = if (encoding == C.ENCODING_PCM_FLOAT) {
                    inputBuffer.getFloat()
                } else {
                    inputBuffer.getShort() / 32768f
                }
                dry[c] = sample
                peak = max(peak, abs(sample))
            }
            amount = when {
                amount < target -> (amount + rampStep).coerceAtMost(target)
                amount > target -> (amount - rampStep).coerceAtLeast(target)
                else -> amount
            }
            val levelDb = 20f * ln(max(peak, MIN_LEVEL)) / LN_10
            val coeff = if (levelDb > envelopeDb) attackCoeff else releaseCoeff
            envelopeDb = coeff * envelopeDb + (1f - coeff) * levelDb
            val overDb = (envelopeDb - THRESHOLD_DB).coerceAtLeast(0f)
            val gainDb = MAKEUP_DB - overDb * (1f - 1f / RATIO)
            val gain = 10f.pow(gainDb / 20f)
            for (c in 0 until channels) {
                wet[c] = softLimit(dry[c] * gain)
                val mixed = dry[c] + (wet[c] - dry[c]) * amount
                if (encoding == C.ENCODING_PCM_FLOAT) {
                    output.putFloat(mixed)
                } else {
                    output.putShort((mixed * 32767f).coerceIn(-32768f, 32767f).toInt().toShort())
                }
            }
        }
        // Any trailing bytes that do not make a whole frame pass through unchanged.
        output.put(inputBuffer)
        output.flip()
    }

    private fun softLimit(x: Float): Float {
        val magnitude = abs(x)
        if (magnitude <= KNEE) return x
        val shaped = KNEE + (CEILING - KNEE) * tanh((magnitude - KNEE) / (CEILING - KNEE))
        return if (x < 0f) -shaped else shaped
    }

    private companion object {
        const val MAKEUP_DB = 12f
        const val THRESHOLD_DB = -26f
        const val RATIO = 3f
        const val ATTACK_SECONDS = 0.010f
        const val RELEASE_SECONDS = 0.150f
        const val RAMP_SECONDS = 0.050f
        const val KNEE = 0.7f
        const val CEILING = 0.891f
        const val FLOOR_DB = -90f
        const val MIN_LEVEL = 1e-5f
        const val LN_10 = 2.3025851f
    }
}
