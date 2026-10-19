package xyz.saltedchips.bookyplayer.tts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import xyz.saltedchips.bookyplayer.R
import xyz.saltedchips.bookyplayer.epub.EpubChapter
import xyz.saltedchips.bookyplayer.library.ConversionStatus
import xyz.saltedchips.bookyplayer.library.GeneratedBook
import xyz.saltedchips.bookyplayer.library.GeneratedBookStore

/**
 * Speaks every unfinished chapter of a generated book, in order. Each finished chapter is saved
 * immediately, so the book is listenable early and a stopped job resumes at the next chapter.
 */
class ConvertEpubWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val repo = GeneratedBookStore.of(context)
    private var lastNotifyAt = 0L
    private var weights: List<Double> = emptyList()
    private var startedAt = 0L
    private var startFraction = 0f

    override suspend fun doWork(): Result {
        val uuid = inputData.getString(KEY_UUID) ?: return Result.failure()
        // A paused job takes a moment to wind down. Wait for it so two jobs never write the same
        // chapter checkpoint at once.
        return locks.getOrPut(uuid) { Mutex() }.withLock { convert(uuid) }
    }

    private suspend fun convert(uuid: String): Result {
        var book = repo.load(uuid) ?: return Result.failure()
        val manager = KokoroModelManager(applicationContext)
        if (!manager.isInstalled()) {
            return fail(book, "The voice model isn't installed.")
        }
        book = book.copy(status = ConversionStatus.Converting, error = null)
        repo.save(book)
        GeneratedBookStore.notifyChanged()
        weights = book.chapters.indices.map { repo.chapterTextFile(uuid, it).length().toDouble().coerceAtLeast(1.0) }
        startedAt = System.currentTimeMillis()
        val firstPending = book.chapters.indexOfFirst { !it.done || it.stale }
        val resumedFraction = if (firstPending >= 0) {
            val finalFile = repo.chapterAudioFile(uuid, firstPending)
            ChapterSynthesizer.savedFraction(
                chapter = EpubChapter(book.chapters[firstPending].title, repo.chapterTextFile(uuid, firstPending).readText()),
                output = File(finalFile.parentFile, finalFile.name + ".part"),
                locale = book.language?.let(Locale::forLanguageTag)?.takeIf { it.language.isNotEmpty() } ?: Locale.ENGLISH,
            )
        } else {
            0f
        }
        startFraction = overallFraction(book, firstPending.takeIf { it >= 0 }, resumedFraction)
        Log.d(TAG, "start: pending=$firstPending done=${book.doneChapters}/${book.chapters.size} saved=$resumedFraction start=$startFraction")
        ConversionProgress.set(uuid, LiveProgress(startFraction))
        setForeground(foregroundInfo(book, LiveProgress(startFraction)))

        val engine = try {
            KokoroEngine(manager.modelDir)
        } catch (t: Throwable) {
            return fail(book, "Couldn't start the voice engine: ${t.message}")
        }
        try {
            val synthesizer = ChapterSynthesizer(engine)
            val locale = book.language?.let(Locale::forLanguageTag)?.takeIf { it.language.isNotEmpty() }
                ?: Locale.ENGLISH
            for (index in book.chapters.indices) {
                if (book.chapters[index].done && !book.chapters[index].stale) continue
                val text = repo.chapterTextFile(uuid, index).readText()
                val finalFile = repo.chapterAudioFile(uuid, index)
                val partFile = File(finalFile.parentFile, finalFile.name + ".part")
                val duration = withContext(Dispatchers.Default) {
                    val job = coroutineContext.job
                    synthesizer.synthesize(
                        chapter = EpubChapter(book.chapters[index].title, text),
                        output = partFile,
                        voiceSid = book.voiceSid,
                        speed = book.speed,
                        locale = locale,
                        timingOutput = repo.chapterTimingFile(uuid, index),
                        isCancelled = { !job.isActive },
                        onProgress = { fraction -> maybeNotify(book, index, fraction) },
                    )
                }
                check(partFile.renameTo(finalFile)) { "Couldn't save chapter ${index + 1}" }
                // Keep whatever status the user set (for example Paused) while saving the chapter.
                book = (repo.load(uuid) ?: book).withChapterDone(index, duration)
                repo.save(book)
                GeneratedBookStore.notifyChanged()
                if (book.doneChapters == 1 && book.chapters.size > 1) notifyFirstChapter(book)
            }
            ConversionProgress.clear(book.uuid)
            repo.save((repo.load(uuid) ?: book).copy(status = ConversionStatus.Ready, error = null))
            GeneratedBookStore.notifyChanged()
            notifyDone(book)
            return Result.success()
        } catch (cancel: SynthesisCancelled) {
            pause(book)
            throw CancellationException("Conversion stopped")
        } catch (cancel: CancellationException) {
            pause(book)
            throw cancel
        } catch (t: Throwable) {
            return fail(book, t.message ?: "Conversion failed")
        } finally {
            engine.close()
        }
    }

    private fun pause(book: GeneratedBook) {
        repo.load(book.uuid)?.let { repo.save(it.copy(status = ConversionStatus.Paused)) }
        GeneratedBookStore.notifyChanged()
    }

    private fun fail(book: GeneratedBook, message: String): Result {
        ConversionProgress.clear(book.uuid)
        repo.load(book.uuid)?.let { repo.save(it.copy(status = ConversionStatus.Failed, error = message)) }
        GeneratedBookStore.notifyChanged()
        return Result.failure()
    }

    /** Share of all text spoken so far, weighting each chapter by its length. */
    private fun overallFraction(book: GeneratedBook, currentIndex: Int?, currentFraction: Float): Float {
        val total = weights.sum().coerceAtLeast(1.0)
        var done = 0.0
        book.chapters.forEachIndexed { i, chapter ->
            if (chapter.done && !chapter.stale) done += weights.getOrElse(i) { 0.0 }
        }
        if (currentIndex != null) done += weights.getOrElse(currentIndex) { 0.0 } * currentFraction
        return (done / total).toFloat().coerceIn(0f, 1f)
    }

    private fun maybeNotify(book: GeneratedBook, chapterIndex: Int, fraction: Float) {
        val overall = overallFraction(book, chapterIndex, fraction)
        val now = System.currentTimeMillis()
        val live = LiveProgress(overall, estimateEtaMs(now - startedAt, startFraction, overall))
        ConversionProgress.set(book.uuid, live)
        if (now - lastNotifyAt < 1_000L) return
        lastNotifyAt = now
        val info = foregroundInfo(book, live)
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        runCatching { manager.notify(info.notificationId, info.notification) }
    }

    private fun foregroundInfo(book: GeneratedBook, live: LiveProgress): ForegroundInfo {
        ensureChannel(applicationContext)
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val percent = (live.fraction * 100).toInt().coerceIn(0, 100)
        val detail = live.etaMs?.let { " · ${formatEta(it).replaceFirstChar { c -> c.lowercase() }}" }.orEmpty()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ms_book_2)
            .setContentTitle("Generating audiobook")
            .setContentText("${book.title} · $percent%$detail")
            .setProgress(100, percent, false)
            .setContentIntent(ConversionsRequests.pendingIntent(applicationContext, 1, openSheet = true))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Pause", cancel)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun notifyFirstChapter(book: GeneratedBook) {
        ensureChannel(applicationContext)
        val open = ConversionsRequests.pendingIntent(applicationContext, 2, openSheet = true)
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ms_book_2)
            .setContentTitle("Chapter 1 is ready")
            .setContentText("${book.title}: start listening while the rest is generated.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(FIRST_CHAPTER_NOTIFICATION_ID, notification) }
    }

    private fun notifyDone(book: GeneratedBook) {
        ensureChannel(applicationContext)
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ms_book_2)
            .setContentTitle("Audiobook ready")
            .setContentText(book.title)
            .setContentIntent(ConversionsRequests.pendingIntent(applicationContext, 3, openSheet = false))
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(DONE_NOTIFICATION_ID, notification) }
    }

    companion object {
        const val KEY_UUID = "uuid"
        private const val TAG = "ConvertEpub"
        private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
        internal const val CHANNEL_ID = "epub_conversion"
        private const val NOTIFICATION_ID = 4101
        private const val DONE_NOTIFICATION_ID = 4102
        private const val FIRST_CHAPTER_NOTIFICATION_ID = 4105

        internal fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Audiobook conversion", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
    }
}
