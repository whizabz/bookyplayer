package xyz.saltedchips.bookyplayer.tts

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xyz.saltedchips.bookyplayer.R

/** In-process view of the voice download, so the UI can show progress while the job runs. */
data class ModelDownloadStatus(
    val progress: Float? = null,
    val error: String? = null,
    val finishedAt: Long = 0L,
)

object ModelDownloader {
    private val _status = MutableStateFlow(ModelDownloadStatus())
    val status: StateFlow<ModelDownloadStatus> = _status

    internal fun running(progress: Float) {
        _status.value = ModelDownloadStatus(progress = progress)
    }

    internal fun finished() {
        _status.value = ModelDownloadStatus(finishedAt = System.nanoTime())
    }

    internal fun failed(message: String) {
        _status.value = ModelDownloadStatus(error = message)
    }

    internal fun idle() {
        _status.value = ModelDownloadStatus()
    }

    fun start(context: Context) {
        running(0f)
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ModelDownloadWorker>().build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        idle()
    }

    const val WORK_NAME = "download-voice-model"
}

/** Downloads the voice model in the background, with a progress and a finished notification. */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private var lastNotifyAt = 0L

    override suspend fun doWork(): Result {
        val manager = KokoroModelManager(applicationContext)
        if (manager.isInstalled()) {
            ModelDownloader.finished()
            return Result.success()
        }
        ModelDownloader.running(0f)
        setForeground(progressInfo(0f))
        return try {
            manager.download { progress ->
                ModelDownloader.running(progress)
                val now = System.currentTimeMillis()
                if (now - lastNotifyAt >= 1_000L) {
                    lastNotifyAt = now
                    val info = progressInfo(progress)
                    val notifications = applicationContext.getSystemService(NotificationManager::class.java)
                    runCatching { notifications.notify(info.notificationId, info.notification) }
                }
            }
            ModelDownloader.finished()
            runCatching { prepareSamples(manager) }
            notifyResult("Voice ready", "Open an ePub and tap the sparkle to make your audiobook.")
            Result.success()
        } catch (cancel: CancellationException) {
            ModelDownloader.idle()
            throw cancel
        } catch (t: Throwable) {
            val message = t.message ?: "Download failed"
            ModelDownloader.failed(message)
            notifyResult("Voice download failed", message)
            Result.failure()
        }
    }

    /** Makes the preview clips for every voice so they play instantly later. */
    private suspend fun prepareSamples(manager: KokoroModelManager) {
        if (VoicePreviews.hasAll(applicationContext)) return
        KokoroEngine(manager.modelDir, numThreads = 4).use { engine ->
            VoicePreviews.generateMissing(
                context = applicationContext,
                generate = { sid ->
                    VoiceSample(engine.generate(VoicePreviews.TEXT, sid), engine.sampleRate)
                },
                isCancelled = { isStopped },
                onProgress = { done, total ->
                    val info = progressInfo(done.toFloat() / total, "Preparing voice samples")
                    val notifications = applicationContext.getSystemService(NotificationManager::class.java)
                    runCatching { notifications.notify(info.notificationId, info.notification) }
                },
            )
        }
    }

    private fun progressInfo(progress: Float, title: String = "Downloading audiobook voice"): ForegroundInfo {
        ConvertEpubWorker.ensureChannel(applicationContext)
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        val notification = NotificationCompat.Builder(applicationContext, ConvertEpubWorker.CHANNEL_ID)
            .setSmallIcon(R.drawable.ms_book_2)
            .setContentTitle(title)
            .setContentText("$percent%")
            .setProgress(100, percent, false)
            .setContentIntent(ConversionsRequests.pendingIntent(applicationContext, 5, openSheet = false))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Cancel", cancel)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun notifyResult(title: String, text: String) {
        ConvertEpubWorker.ensureChannel(applicationContext)
        val open = ConversionsRequests.pendingIntent(applicationContext, 4, openSheet = false)
        val notification = NotificationCompat.Builder(applicationContext, ConvertEpubWorker.CHANNEL_ID)
            .setSmallIcon(R.drawable.ms_book_2)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        runCatching { manager.notify(RESULT_NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val NOTIFICATION_ID = 4103
        const val RESULT_NOTIFICATION_ID = 4104
    }
}
