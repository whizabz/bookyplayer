package xyz.saltedchips.bookyplayer.reader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.saltedchips.bookyplayer.epub.EpubChapter
import xyz.saltedchips.bookyplayer.epub.EpubDrmException
import xyz.saltedchips.bookyplayer.epub.EpubParseException
import xyz.saltedchips.bookyplayer.epub.EpubParser

data class ReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val chapters: List<EpubChapter> = emptyList(),
    val chapterIndex: Int = 0,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val textScale: Float = 1f,
)

/** Opens one ePub for reading and remembers the chapter and scroll position. */
class EpubReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("booky_prefs", 0)
    private var bookId: String? = null

    private val _state = MutableStateFlow(
        ReaderUiState(textScale = prefs.getFloat(KEY_TEXT_SCALE, 1f).coerceIn(MIN_SCALE, MAX_SCALE)),
    )
    val state: StateFlow<ReaderUiState> = _state

    fun load(id: String) {
        if (bookId == id && !_state.value.loading && _state.value.error == null) return
        bookId = id
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val temp = File.createTempFile("read", ".epub", app.cacheDir)
                    try {
                        app.contentResolver.openInputStream(Uri.parse(id))?.use { input ->
                            temp.outputStream().use { input.copyTo(it) }
                        } ?: error("Couldn't open that file")
                        EpubParser.parse(temp)
                    } finally {
                        temp.delete()
                    }
                }
            }
            result.onSuccess { book ->
                val saved = savedPosition(id)
                val chapter = saved.first.coerceIn(0, book.chapters.lastIndex)
                _state.update {
                    it.copy(
                        loading = false,
                        error = null,
                        chapters = book.chapters,
                        chapterIndex = chapter,
                        scrollIndex = if (chapter == saved.first) saved.second else 0,
                        scrollOffset = if (chapter == saved.first) saved.third else 0,
                    )
                }
            }.onFailure { t ->
                val message = when (t) {
                    is EpubDrmException -> t.message
                    is EpubParseException -> t.message
                    else -> "Couldn't open this ePub: ${t.message ?: "unknown error"}"
                }
                _state.update { it.copy(loading = false, error = message) }
            }
        }
    }

    fun openChapter(index: Int) {
        val last = _state.value.chapters.lastIndex
        if (last < 0) return
        _state.update {
            it.copy(chapterIndex = index.coerceIn(0, last), scrollIndex = 0, scrollOffset = 0)
        }
        savePosition(index.coerceIn(0, last), 0, 0)
    }

    fun savePosition(chapter: Int, scrollIndex: Int, scrollOffset: Int) {
        val id = bookId ?: return
        prefs.edit().putString(positionKey(id), "$chapter,$scrollIndex,$scrollOffset").apply()
    }

    fun adjustTextScale(delta: Float) {
        val next = (_state.value.textScale + delta).coerceIn(MIN_SCALE, MAX_SCALE)
        prefs.edit().putFloat(KEY_TEXT_SCALE, next).apply()
        _state.update { it.copy(textScale = next) }
    }

    private fun savedPosition(id: String): Triple<Int, Int, Int> {
        val parts = prefs.getString(positionKey(id), null)?.split(',')
        if (parts == null || parts.size != 3) return Triple(0, 0, 0)
        return Triple(
            parts[0].toIntOrNull() ?: 0,
            parts[1].toIntOrNull() ?: 0,
            parts[2].toIntOrNull() ?: 0,
        )
    }

    private fun positionKey(id: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
        return "reader_pos_" + digest.take(8).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val KEY_TEXT_SCALE = "reader_text_scale"
        const val MIN_SCALE = 0.8f
        const val MAX_SCALE = 1.8f
    }
}
