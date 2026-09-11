package com.booky.app.library

import android.content.Context
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.util.ArrayDeque

data class EmbeddedChapter(
    val title: String,
    val startMs: Long,
)

object Mp4ChapterReader {
    fun read(context: Context, uri: Uri): List<EmbeddedChapter> {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                    parse(channel)
                }
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clips(chapters: List<EmbeddedChapter>, fileDurationMs: Long): List<ChapterClip> {
        val sorted = chapters
            .filter { it.startMs >= 0L }
            .distinctBy { it.startMs }
            .sortedBy { it.startMs }
        if (sorted.size < 2) return emptyList()
        val endBound = fileDurationMs.coerceAtLeast(sorted.last().startMs)
        return sorted.mapIndexed { index, chapter ->
            val end = sorted.getOrNull(index + 1)?.startMs ?: endBound
            ChapterClip(
                title = chapter.title.ifBlank { "Chapter ${index + 1}" },
                startMs = chapter.startMs,
                durationMs = (end - chapter.startMs).coerceAtLeast(0L),
            )
        }.filterIndexed { index, clip ->
            clip.durationMs > 0L || index == sorted.lastIndex
        }
    }

    data class ChapterClip(
        val title: String,
        val startMs: Long,
        val durationMs: Long,
    )

    private fun parse(channel: FileChannel): List<EmbeddedChapter> {
        val file = AtomFile(channel)
        val tracks = mutableListOf<Track>()
        var current: Track? = null
        val nero = mutableListOf<EmbeddedChapter>()
        file.walk(0L, file.size) { type, payloadStart, payloadSize ->
            when (type) {
                TYPE_MOOV, TYPE_MDIA, TYPE_MINF, TYPE_STBL, TYPE_UDTA, TYPE_TREF -> Walk.Recurse
                TYPE_TRAK -> {
                    current = Track().also { tracks += it }
                    Walk.Recurse
                }
                TYPE_CHPL -> {
                    nero += parseChpl(file.read(payloadStart, payloadSize))
                    Walk.Skip
                }
                TYPE_TKHD -> {
                    current?.id = parseTkhd(file.read(payloadStart, payloadSize))
                    Walk.Skip
                }
                TYPE_HDLR -> {
                    current?.handler = parseHdlr(file.read(payloadStart, payloadSize))
                    Walk.Skip
                }
                TYPE_MDHD -> {
                    current?.timescale = parseMdhd(file.read(payloadStart, payloadSize))
                    Walk.Skip
                }
                TYPE_STTS -> {
                    current?.stts = parseStts(file.read(payloadStart, payloadSize))
                    Walk.Skip
                }
                TYPE_STSZ -> {
                    val stsz = parseStsz(file.read(payloadStart, payloadSize))
                    current?.defaultSampleSize = stsz.first
                    current?.sampleSizes = stsz.second
                    Walk.Skip
                }
                TYPE_STSC -> {
                    current?.stsc = parseStsc(file.read(payloadStart, payloadSize))
                    Walk.Skip
                }
                TYPE_STCO -> {
                    current?.chunkOffsets = parseStco(file.read(payloadStart, payloadSize), wide = false)
                    Walk.Skip
                }
                TYPE_CO64 -> {
                    current?.chunkOffsets = parseStco(file.read(payloadStart, payloadSize), wide = true)
                    Walk.Skip
                }
                TYPE_CHAP -> {
                    current?.chapterTrackIds = parseChap(file.read(payloadStart, payloadSize))
                    Walk.Skip
                }
                else -> Walk.Skip
            }
        }
        val byId = tracks.associateBy { it.id }
        val referenced = tracks.flatMap { it.chapterTrackIds }.mapNotNull { byId[it] }
        val textTracks = referenced.ifEmpty {
            tracks.filter { it.handler == "text" || it.handler == "sbtl" }
        }
        val quickTime = textTracks.flatMap { readTextSamples(channel, it) }
            .filter { it.startMs >= 0L }
            .distinctBy { it.startMs }
            .sortedBy { it.startMs }
        val neroChapters = nero.distinctBy { it.startMs }.sortedBy { it.startMs }
        return when {
            quickTime.size >= 2 -> quickTime
            neroChapters.size >= 2 -> neroChapters
            quickTime.isNotEmpty() -> quickTime
            else -> neroChapters
        }
    }

    private enum class Walk { Recurse, Skip }

    private class Track {
        var id: Int = 0
        var handler: String = ""
        var timescale: Long = 0L
        var chapterTrackIds: List<Int> = emptyList()
        var stts: List<Pair<Int, Int>> = emptyList()
        var defaultSampleSize: Int = 0
        var sampleSizes: IntArray = IntArray(0)
        var stsc: List<Triple<Int, Int, Int>> = emptyList()
        var chunkOffsets: LongArray = LongArray(0)
    }

    private class AtomFile(private val channel: FileChannel) {
        val size: Long = channel.size()
        private val header = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)

        fun walk(start: Long, end: Long, onAtom: (type: Int, payloadStart: Long, payloadSize: Long) -> Walk) {
            var pos = start
            while (pos + 8 <= end) {
                header.clear()
                header.limit(8)
                channel.position(pos)
                if (channel.read(header) < 8) return
                header.flip()
                var boxSize = header.int.toLong() and 0xFFFFFFFFL
                val type = header.int
                var headerSize = 8L
                if (boxSize == 1L) {
                    header.clear()
                    header.limit(8)
                    if (channel.read(header) < 8) return
                    header.flip()
                    boxSize = header.long
                    headerSize = 16L
                } else if (boxSize == 0L) {
                    boxSize = end - pos
                }
                if (boxSize < headerSize) return
                val payloadStart = pos + headerSize
                val payloadSize = (boxSize - headerSize).coerceAtLeast(0L)
                val atomEnd = (pos + boxSize).coerceAtMost(end)
                if (onAtom(type, payloadStart, payloadSize) == Walk.Recurse && payloadSize > 0L) {
                    walk(payloadStart, atomEnd, onAtom)
                }
                pos = atomEnd
            }
        }

        fun read(start: Long, size: Long): ByteBuffer {
            val length = size.coerceIn(0L, 2L * 1024 * 1024).toInt()
            val buffer = ByteBuffer.allocate(length).order(ByteOrder.BIG_ENDIAN)
            channel.position(start)
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) break
            }
            buffer.flip()
            return buffer
        }
    }

    private fun parseTkhd(data: ByteBuffer): Int {
        if (data.remaining() < 16) return 0
        val version = data.get().toInt() and 0xFF
        data.position(data.position() + 3)
        if (version == 1) {
            if (data.remaining() < 20) return 0
            data.position(data.position() + 16)
        } else {
            if (data.remaining() < 12) return 0
            data.position(data.position() + 8)
        }
        return data.int
    }

    private fun parseHdlr(data: ByteBuffer): String {
        if (data.remaining() < 12) return ""
        data.position(data.position() + 8)
        return fourCc(data.int)
    }

    private fun parseMdhd(data: ByteBuffer): Long {
        if (data.remaining() < 12) return 0L
        val version = data.get().toInt() and 0xFF
        data.position(data.position() + 3)
        if (version == 1) {
            if (data.remaining() < 24) return 0L
            data.position(data.position() + 16)
        } else {
            if (data.remaining() < 12) return 0L
            data.position(data.position() + 8)
        }
        return data.int.toLong() and 0xFFFFFFFFL
    }

    private fun parseChap(data: ByteBuffer): List<Int> {
        val ids = mutableListOf<Int>()
        while (data.remaining() >= 4) ids += data.int
        return ids
    }

    private fun parseStts(data: ByteBuffer): List<Pair<Int, Int>> {
        if (data.remaining() < 8) return emptyList()
        data.position(data.position() + 4)
        val count = data.int.coerceIn(0, 100_000)
        val entries = ArrayList<Pair<Int, Int>>(count)
        repeat(count) {
            if (data.remaining() < 8) return entries
            entries += data.int to data.int
        }
        return entries
    }

    private fun parseStsz(data: ByteBuffer): Pair<Int, IntArray> {
        if (data.remaining() < 12) return 0 to IntArray(0)
        data.position(data.position() + 4)
        val defaultSize = data.int
        val count = data.int.coerceIn(0, 100_000)
        if (defaultSize != 0) return defaultSize to IntArray(0)
        val sizes = IntArray(count)
        for (i in 0 until count) {
            if (data.remaining() < 4) break
            sizes[i] = data.int
        }
        return 0 to sizes
    }

    private fun parseStsc(data: ByteBuffer): List<Triple<Int, Int, Int>> {
        if (data.remaining() < 8) return emptyList()
        data.position(data.position() + 4)
        val count = data.int.coerceIn(0, 100_000)
        val entries = ArrayList<Triple<Int, Int, Int>>(count)
        repeat(count) {
            if (data.remaining() < 12) return entries
            entries += Triple(data.int, data.int, data.int)
        }
        return entries
    }

    private fun parseStco(data: ByteBuffer, wide: Boolean): LongArray {
        if (data.remaining() < 8) return LongArray(0)
        data.position(data.position() + 4)
        val count = data.int.coerceIn(0, 100_000)
        val offsets = LongArray(count)
        for (i in 0 until count) {
            if (wide) {
                if (data.remaining() < 8) break
                offsets[i] = data.long
            } else {
                if (data.remaining() < 4) break
                offsets[i] = data.int.toLong() and 0xFFFFFFFFL
            }
        }
        return offsets
    }

    private fun parseChpl(data: ByteBuffer): List<EmbeddedChapter> {
        if (data.remaining() < 5) return emptyList()
        val version = data.get().toInt() and 0xFF
        data.position(data.position() + 3)
        val chapters = mutableListOf<EmbeddedChapter>()
        fun readEntries(count: Int) {
            repeat(count) { index ->
                if (data.remaining() < 9) return
                val startMs = (data.long / 10_000L).coerceAtLeast(0L)
                val titleLen = data.get().toInt() and 0xFF
                if (data.remaining() < titleLen) return
                val bytes = ByteArray(titleLen)
                data.get(bytes)
                val title = bytes.toString(Charsets.UTF_8).trim().ifBlank { "Chapter ${index + 1}" }
                chapters += EmbeddedChapter(title, startMs)
            }
        }
        if (version != 0 && data.remaining() >= 5) {
            val mark = data.position()
            data.get()
            val count32 = data.int
            if (count32 in 1..10_000) {
                readEntries(count32)
                if (chapters.isNotEmpty()) return chapters
            }
            chapters.clear()
            data.position(mark)
            if (data.remaining() >= 4) data.int
        }
        if (data.remaining() < 1) return chapters
        readEntries(data.get().toInt() and 0xFF)
        return chapters
    }

    private fun readTextSamples(channel: FileChannel, track: Track): List<EmbeddedChapter> {
        if (track.timescale <= 0L || track.chunkOffsets.isEmpty() || track.stts.isEmpty()) {
            return emptyList()
        }
        val sampleCount = if (track.defaultSampleSize != 0) {
            track.stts.sumOf { it.first.coerceAtLeast(0) }
        } else {
            track.sampleSizes.size
        }
        if (sampleCount <= 0 || track.stsc.isEmpty()) return emptyList()
        val samplesPerChunk = IntArray(track.chunkOffsets.size)
        for (i in track.stsc.indices) {
            val firstChunk = (track.stsc[i].first - 1).coerceAtLeast(0)
            val nextFirst = track.stsc.getOrNull(i + 1)?.first?.minus(1) ?: track.chunkOffsets.size
            val spc = track.stsc[i].second.coerceAtLeast(0)
            for (chunk in firstChunk until nextFirst.coerceAtMost(track.chunkOffsets.size)) {
                samplesPerChunk[chunk] = spc
            }
        }
        val deltas = ArrayDeque<Int>()
        for ((count, delta) in track.stts) {
            repeat(count.coerceAtLeast(0)) { deltas.addLast(delta) }
        }
        val chapters = mutableListOf<EmbeddedChapter>()
        var sampleIndex = 0
        var timeTicks = 0L
        val header = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN)
        for (chunk in track.chunkOffsets.indices) {
            var offset = track.chunkOffsets[chunk]
            repeat(samplesPerChunk.getOrElse(chunk) { 0 }) {
                if (sampleIndex >= sampleCount) return chapters
                val size = if (track.defaultSampleSize != 0) {
                    track.defaultSampleSize
                } else {
                    track.sampleSizes.getOrElse(sampleIndex) { 0 }
                }.coerceAtLeast(0)
                val delta = if (deltas.isEmpty()) 0 else deltas.removeFirst()
                val startMs = timeTicks * 1000L / track.timescale
                chapters += EmbeddedChapter(
                    title = readTitle(channel, offset, size, header, chapters.size + 1),
                    startMs = startMs,
                )
                timeTicks += delta.toLong() and 0xFFFFFFFFL
                offset += size.toLong()
                sampleIndex++
            }
        }
        return chapters
    }

    private fun readTitle(
        channel: FileChannel,
        offset: Long,
        size: Int,
        header: ByteBuffer,
        fallbackNumber: Int,
    ): String {
        if (size <= 0 || size > 64 * 1024) return "Chapter $fallbackNumber"
        return try {
            header.clear()
            channel.position(offset)
            if (channel.read(header) < 2) return "Chapter $fallbackNumber"
            header.flip()
            val length = header.short.toInt() and 0xFFFF
            val titleSize = if (length in 1 until size) length else (size - 2).coerceAtLeast(0)
            val bytes = ByteArray(titleSize)
            channel.position(offset + 2)
            channel.read(ByteBuffer.wrap(bytes))
            decodeTitle(bytes).ifBlank { "Chapter $fallbackNumber" }
        } catch (_: Exception) {
            "Chapter $fallbackNumber"
        }
    }

    private fun decodeTitle(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            val bom = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
            if (bom == 0xFEFF || bom == 0xFFFE) {
                val charset = if (bom == 0xFEFF) Charsets.UTF_16BE else Charset.forName("UTF-16LE")
                return bytes.toString(charset).trim { it <= ' ' || it == '\u0000' }
            }
        }
        return bytes.toString(Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
    }

    private fun fourCc(value: Int): String {
        return charArrayOf(
            ((value ushr 24) and 0xFF).toChar(),
            ((value ushr 16) and 0xFF).toChar(),
            ((value ushr 8) and 0xFF).toChar(),
            (value and 0xFF).toChar(),
        ).concatToString()
    }

    private const val TYPE_MOOV = 0x6d6f6f76
    private const val TYPE_TRAK = 0x7472616b
    private const val TYPE_MDIA = 0x6d646961
    private const val TYPE_MINF = 0x6d696e66
    private const val TYPE_STBL = 0x7374626c
    private const val TYPE_UDTA = 0x75647461
    private const val TYPE_TREF = 0x74726566
    private const val TYPE_CHPL = 0x6368706c
    private const val TYPE_TKHD = 0x746b6864
    private const val TYPE_HDLR = 0x68646c72
    private const val TYPE_MDHD = 0x6d646864
    private const val TYPE_STTS = 0x73747473
    private const val TYPE_STSZ = 0x7374737a
    private const val TYPE_STSC = 0x73747363
    private const val TYPE_STCO = 0x7374636f
    private const val TYPE_CO64 = 0x636f3634
    private const val TYPE_CHAP = 0x63686170
}
