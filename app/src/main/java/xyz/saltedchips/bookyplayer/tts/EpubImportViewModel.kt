package xyz.saltedchips.bookyplayer.tts

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.saltedchips.bookyplayer.epub.EpubDrmException
import xyz.saltedchips.bookyplayer.epub.EpubParseException

data class EpubImportUiState(
    val modelInstalled: Boolean = false,
    val modelBytes: Long = 0L,
    val downloadProgress: Float? = null,
    val downloadError: String? = null,
    val voiceSid: Int = KokoroVoices.default.sid,
    val previewingSid: Int? = null,
    val importing: Boolean = false,
    val importError: String? = null,
)

class EpubImportViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("booky_prefs", 0)
    private val manager = KokoroModelManager(application)
    private var downloadJob: Job? = null
    private var previewJob: Job? = null
    private var previewEngine: KokoroEngine? = null

    private val _state = MutableStateFlow(
        EpubImportUiState(
            modelInstalled = manager.isInstalled(),
            modelBytes = if (manager.isInstalled()) manager.installedBytes() else 0L,
            voiceSid = prefs.getInt(KEY_VOICE, KokoroVoices.default.sid),
        ),
    )
    val state: StateFlow<EpubImportUiState> = _state

    fun refresh() {
        val installed = manager.isInstalled()
        _state.update {
            it.copy(modelInstalled = installed, modelBytes = if (installed) manager.installedBytes() else 0L)
        }
    }

    fun setVoice(sid: Int) {
        prefs.edit().putInt(KEY_VOICE, sid).apply()
        _state.update { it.copy(voiceSid = sid) }
    }

    fun startDownload() {
        if (downloadJob?.isActive == true) return
        _state.update { it.copy(downloadProgress = 0f, downloadError = null) }
        downloadJob = viewModelScope.launch {
            try {
                var last = 0f
                manager.download { progress ->
                    if (progress - last >= 0.005f || progress >= 1f) {
                        last = progress
                        _state.update { it.copy(downloadProgress = progress) }
                    }
                }
                _state.update {
                    it.copy(
                        modelInstalled = true,
                        modelBytes = manager.installedBytes(),
                        downloadProgress = null,
                    )
                }
            } catch (cancel: CancellationException) {
                _state.update { it.copy(downloadProgress = null) }
                throw cancel
            } catch (t: Throwable) {
                _state.update {
                    it.copy(downloadProgress = null, downloadError = t.message ?: "Download failed")
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    fun deleteModel() {
        previewJob?.cancel()
        previewEngine?.close()
        previewEngine = null
        manager.delete()
        refresh()
    }

    /** Says a short sample in the chosen voice so people can compare before converting. */
    fun previewVoice(sid: Int) {
        if (!_state.value.modelInstalled) return
        previewJob?.cancel()
        _state.update { it.copy(previewingSid = sid) }
        previewJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    val engine = previewEngine ?: KokoroEngine(manager.modelDir).also { previewEngine = it }
                    val samples = engine.generate(
                        "Hello. This is how I will read your book.",
                        sid,
                    )
                    val track = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build(),
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                                .setSampleRate(engine.sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build(),
                        )
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .setBufferSizeInBytes(samples.size * 4)
                        .build()
                    try {
                        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                        track.play()
                        delay(samples.size * 1000L / engine.sampleRate + 200L)
                    } finally {
                        track.release()
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                // A failed preview shouldn't block converting.
            } finally {
                _state.update { it.copy(previewingSid = null) }
            }
        }
    }

    /** Parses [uri] and starts converting. [onStarted] runs once the job is queued. */
    fun import(uri: Uri, onStarted: () -> Unit) {
        if (_state.value.importing) return
        _state.update { it.copy(importing = true, importError = null) }
        viewModelScope.launch {
            try {
                EpubImporter.import(getApplication(), uri, _state.value.voiceSid)
                _state.update { it.copy(importing = false) }
                onStarted()
            } catch (drm: EpubDrmException) {
                fail(drm.message ?: "This ePub is DRM-protected.")
            } catch (parse: EpubParseException) {
                fail(parse.message ?: "Couldn't read this ePub.")
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                fail("Couldn't read this file: ${t.message ?: "unknown error"}")
            }
        }
    }

    fun clearImportError() {
        _state.update { it.copy(importError = null) }
    }

    private fun fail(message: String) {
        _state.update { it.copy(importing = false, importError = message) }
    }

    override fun onCleared() {
        previewJob?.cancel()
        previewEngine?.close()
        previewEngine = null
        super.onCleared()
    }

    private companion object {
        const val KEY_VOICE = "epub_voice_sid"
    }
}
