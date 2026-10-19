package xyz.saltedchips.bookyplayer.library

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import xyz.saltedchips.bookyplayer.data.Audiobook
import xyz.saltedchips.bookyplayer.epub.EpubBook

enum class ConversionStatus { Converting, Paused, Ready, Failed }

data class GeneratedChapter(val title: String, val done: Boolean = false, val durationMs: Long = 0L)

/** An ePub being (or already) turned into audio. Files live in `generated_books/<uuid>/`. */
data class GeneratedBook(
    val uuid: String,
    val title: String,
    val author: String,
    val language: String?,
    val voiceSid: Int,
    val speed: Float,
    val createdAtMs: Long,
    val chapters: List<GeneratedChapter>,
    val status: ConversionStatus,
    val error: String? = null,
    val sourceId: String? = null,
) {
    val id: String get() = GeneratedBookRepository.ID_PREFIX + uuid
    val doneChapters: Int get() = chapters.count { it.done }

    fun withChapterDone(index: Int, durationMs: Long) = copy(
        chapters = chapters.mapIndexed { i, c -> if (i == index) c.copy(done = true, durationMs = durationMs) else c },
    )
}

/** Plain-file storage for generated books, so it can be unit-tested without Android. */
class GeneratedBookRepository(private val root: File) {
    fun dir(uuid: String) = File(root, uuid)
    fun chapterTextFile(uuid: String, index: Int) = File(dir(uuid), "text/%03d.txt".format(index))
    fun chapterAudioFile(uuid: String, index: Int) = File(dir(uuid), "audio/%03d.m4a".format(index))
    private fun metaFile(uuid: String) = File(dir(uuid), "book.json")
    fun coverFile(uuid: String): File? =
        dir(uuid).listFiles()?.firstOrNull { it.name.startsWith("cover.") }

    fun create(
        epub: EpubBook,
        voiceSid: Int,
        speed: Float,
        now: Long = System.currentTimeMillis(),
        sourceId: String? = null,
    ): GeneratedBook {
        val uuid = UUID.randomUUID().toString()
        epub.chapters.forEachIndexed { index, chapter ->
            chapterTextFile(uuid, index).apply { parentFile?.mkdirs() }.writeText(chapter.text)
        }
        val coverBytes = epub.coverBytes
        if (coverBytes != null) {
            File(dir(uuid), "cover.${epub.coverExtension ?: "jpg"}").writeBytes(coverBytes)
        }
        val book = GeneratedBook(
            uuid = uuid,
            title = epub.title,
            author = epub.author,
            language = epub.language,
            voiceSid = voiceSid,
            speed = speed,
            createdAtMs = now,
            chapters = epub.chapters.map { GeneratedChapter(it.title) },
            status = ConversionStatus.Converting,
            sourceId = sourceId,
        )
        save(book)
        return book
    }

    fun save(book: GeneratedBook) {
        val json = JSONObject()
            .put("uuid", book.uuid)
            .put("title", book.title)
            .put("author", book.author)
            .put("language", book.language ?: JSONObject.NULL)
            .put("voiceSid", book.voiceSid)
            .put("speed", book.speed.toDouble())
            .put("createdAtMs", book.createdAtMs)
            .put("status", book.status.name)
            .put("error", book.error ?: JSONObject.NULL)
            .put("sourceId", book.sourceId ?: JSONObject.NULL)
            .put(
                "chapters",
                JSONArray(
                    book.chapters.map {
                        JSONObject().put("title", it.title).put("done", it.done).put("durationMs", it.durationMs)
                    },
                ),
            )
        val target = metaFile(book.uuid)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "book.json.tmp")
        temp.writeText(json.toString())
        temp.renameTo(target)
    }

    fun load(uuid: String): GeneratedBook? = runCatching {
        val json = JSONObject(metaFile(uuid).readText())
        val chapters = json.getJSONArray("chapters")
        GeneratedBook(
            uuid = json.getString("uuid"),
            title = json.getString("title"),
            author = json.optString("author"),
            language = if (json.isNull("language")) null else json.optString("language"),
            voiceSid = json.optInt("voiceSid"),
            speed = json.optDouble("speed", 1.0).toFloat(),
            createdAtMs = json.optLong("createdAtMs"),
            chapters = List(chapters.length()) { i ->
                val c = chapters.getJSONObject(i)
                GeneratedChapter(c.getString("title"), c.optBoolean("done"), c.optLong("durationMs"))
            },
            status = ConversionStatus.entries.firstOrNull { it.name == json.optString("status") }
                ?: ConversionStatus.Failed,
            error = if (json.isNull("error")) null else json.optString("error"),
            sourceId = if (json.isNull("sourceId")) null else json.optString("sourceId"),
        )
    }.getOrNull()

    fun all(): List<GeneratedBook> =
        root.listFiles()?.filter { it.isDirectory }?.mapNotNull { load(it.name) }.orEmpty()

    fun delete(uuid: String) {
        dir(uuid).deleteRecursively()
    }

    /** The playable part of a book: its leading run of finished chapters. Null until chapter 1 is done. */
    fun toAudiobook(book: GeneratedBook): Audiobook? {
        val ready = book.chapters.takeWhile { it.done }
        if (ready.isEmpty()) return null
        val titles = ready.map { it.title }
        val durations = ready.map { it.durationMs }
        val cover = coverFile(book.uuid)?.let { "file://${it.absolutePath}" }
        return Audiobook(
            id = book.id,
            title = book.title,
            author = book.author.ifBlank { "Unknown" },
            narrator = "Kokoro voice",
            chapterCount = ready.size,
            fileCount = ready.size,
            durationMs = durations.sum(),
            listenedMs = 0L,
            currentChapter = 1,
            currentChapterTitle = titles.first(),
            fileName = "${book.title}.m4a",
            coverUri = cover,
            mediaUris = ready.indices.map { "file://${chapterAudioFile(book.uuid, it).absolutePath}" },
            chapterTitles = titles,
            chapterDurationsMs = durations,
            chapterStartMs = List(ready.size) { 0L },
            addedAtMs = book.createdAtMs,
        )
    }

    fun audiobooks(): List<Audiobook> = all().mapNotNull(::toAudiobook)

    companion object {
        const val ID_PREFIX = "gen:"
        fun isGenerated(id: String) = id.startsWith(ID_PREFIX)
        fun uuidOf(id: String) = id.removePrefix(ID_PREFIX)
    }
}

/** App-wide access to generated books, plus a change signal the library listens to. */
object GeneratedBookStore {
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes

    fun of(context: Context) =
        GeneratedBookRepository(File(context.applicationContext.filesDir, "generated_books"))

    fun notifyChanged() {
        _changes.value = System.nanoTime()
    }
}
