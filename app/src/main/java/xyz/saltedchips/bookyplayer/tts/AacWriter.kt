package xyz.saltedchips.bookyplayer.tts

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.Closeable
import java.io.File
import kotlin.math.min

/** Streams mono float PCM into an AAC-LC `.m4a` file, so a whole chapter never sits in memory. */
class AacWriter(
    file: File,
    private val sampleRate: Int,
    bitRate: Int = 48_000,
) : Closeable {
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var muxerStarted = false
    private var samplesWritten = 0L
    private var finished = false

    init {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    fun write(samples: FloatArray) {
        var offset = 0
        while (offset < samples.size) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index < 0) {
                drain(untilEnd = false)
                continue
            }
            val buffer = codec.getInputBuffer(index)!!
            buffer.clear()
            val count = min(samples.size - offset, buffer.remaining() / 2)
            for (i in 0 until count) {
                buffer.putShort((samples[offset + i] * 32767f).coerceIn(-32768f, 32767f).toInt().toShort())
            }
            codec.queueInputBuffer(index, 0, count * 2, presentationUs(samplesWritten), 0)
            samplesWritten += count
            offset += count
            drain(untilEnd = false)
        }
    }

    fun writeSilence(seconds: Float) {
        val total = (seconds * sampleRate).toInt()
        val block = FloatArray(min(total, sampleRate))
        var remaining = total
        while (remaining > 0) {
            val n = min(remaining, block.size)
            write(if (n == block.size) block else FloatArray(n))
            remaining -= n
        }
    }

    /** Finishes the file and returns its exact duration in milliseconds. */
    fun finish(): Long {
        if (finished) return durationMs()
        finished = true
        var queued = false
        while (!queued) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index >= 0) {
                codec.queueInputBuffer(
                    index, 0, 0, presentationUs(samplesWritten), MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
                queued = true
            } else {
                drain(untilEnd = false)
            }
        }
        drain(untilEnd = true)
        release()
        return durationMs()
    }

    fun durationMs(): Long = samplesWritten * 1000L / sampleRate

    override fun close() {
        if (!finished) {
            finished = true
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { muxer.release() }
        }
    }

    private fun presentationUs(sample: Long) = sample * 1_000_000L / sampleRate

    private fun drain(untilEnd: Boolean) {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (untilEnd) TIMEOUT_US else 0L)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!untilEnd) return
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                index >= 0 -> {
                    val buffer = codec.getOutputBuffer(index)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && muxerStarted) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buffer, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun release() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        if (muxerStarted) runCatching { muxer.stop() }
        runCatching { muxer.release() }
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}
