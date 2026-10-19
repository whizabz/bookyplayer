package xyz.saltedchips.bookyplayer.tts

import android.content.Context
import android.net.Uri
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.saltedchips.bookyplayer.epub.EpubParseException
import xyz.saltedchips.bookyplayer.epub.EpubParser
import xyz.saltedchips.bookyplayer.library.ConversionStatus
import xyz.saltedchips.bookyplayer.library.GeneratedBookRepository
import xyz.saltedchips.bookyplayer.library.GeneratedBookStore

/** Stages a picked ePub as a generated book and starts (or resumes) its conversion job. */
object EpubImporter {
    /**
     * Parses the ePub at [uri], stores its chapters, and starts converting. Returns the new book id.
     * Throws [xyz.saltedchips.bookyplayer.epub.EpubDrmException] or
     * [xyz.saltedchips.bookyplayer.epub.EpubParseException] when the file can't be used.
     */
    suspend fun import(
        context: Context,
        uri: Uri,
        voiceSid: Int = KokoroVoices.default.sid,
        speed: Float = 1f,
    ): String = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val temp = File.createTempFile("import", ".epub", app.cacheDir)
        try {
            app.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { input.copyTo(it) }
            } ?: error("Couldn't open that file")
            val repo = GeneratedBookStore.of(app)
            val sourceId = uri.toString()
            if (repo.all().any { it.sourceId == sourceId && it.status != ConversionStatus.Failed }) {
                throw EpubParseException("An audiobook from this ePub already exists or is being generated.")
            }
            val epub = EpubParser.parse(temp)
            val book = repo.create(epub, voiceSid, speed, sourceId = sourceId)
            enqueue(app, book.uuid)
            GeneratedBookStore.notifyChanged()
            book.id
        } finally {
            temp.delete()
        }
    }

    /**
     * Speaks chapters again with the book's voice. [chapters] null means every chapter. The old
     * audio keeps playing until each new chapter replaces it.
     */
    fun regenerate(context: Context, bookId: String, chapters: Set<Int>? = null) {
        val app = context.applicationContext
        val repo = GeneratedBookStore.of(app)
        val uuid = GeneratedBookRepository.uuidOf(bookId)
        val book = repo.load(uuid) ?: return
        val targets = chapters ?: book.chapters.indices.toSet()
        targets.forEach { index ->
            val audio = repo.chapterAudioFile(uuid, index)
            listOf(".part", ".part.pcm", ".part.state", ".part.cues").forEach { suffix ->
                File(audio.parentFile, audio.name + suffix).delete()
            }
        }
        repo.save(
            book.copy(
                chapters = book.chapters.mapIndexed { i, chapter ->
                    if (i in targets && chapter.done) chapter.copy(stale = true) else chapter.copy(
                        done = chapter.done && i !in targets,
                    )
                },
                status = ConversionStatus.Converting,
                error = null,
            ),
        )
        enqueue(app, uuid)
        GeneratedBookStore.notifyChanged()
    }

    /** Resumes a stopped or failed conversion where it left off. */
    fun resume(context: Context, bookId: String) {
        val app = context.applicationContext
        val repo = GeneratedBookStore.of(app)
        val uuid = GeneratedBookRepository.uuidOf(bookId)
        val book = repo.load(uuid) ?: return
        repo.save(book.copy(status = ConversionStatus.Converting, error = null))
        enqueue(app, uuid)
        GeneratedBookStore.notifyChanged()
    }

    /** Pauses right away in the UI, then lets the running job wind down at the next sentence. */
    fun pause(context: Context, bookId: String) {
        val app = context.applicationContext
        val repo = GeneratedBookStore.of(app)
        val uuid = GeneratedBookRepository.uuidOf(bookId)
        repo.load(uuid)?.let { book ->
            if (book.status == ConversionStatus.Converting) {
                repo.save(book.copy(status = ConversionStatus.Paused))
            }
        }
        GeneratedBookStore.notifyChanged()
        cancel(app, bookId)
    }

    fun cancel(context: Context, bookId: String) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(workName(GeneratedBookRepository.uuidOf(bookId)))
    }

    /** Stops any running job and removes the book's files. */
    fun delete(context: Context, bookId: String) {
        cancel(context, bookId)
        GeneratedBookStore.of(context).delete(GeneratedBookRepository.uuidOf(bookId))
        GeneratedBookStore.notifyChanged()
    }

    private fun enqueue(context: Context, uuid: String) {
        val request = OneTimeWorkRequestBuilder<ConvertEpubWorker>()
            .setInputData(Data.Builder().putString(ConvertEpubWorker.KEY_UUID, uuid).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(uuid), ExistingWorkPolicy.REPLACE, request)
    }

    private fun workName(uuid: String) = "convert-$uuid"
}
