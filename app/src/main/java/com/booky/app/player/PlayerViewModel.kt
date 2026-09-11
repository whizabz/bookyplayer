package com.booky.app.player

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.booky.app.data.Audiobook
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

data class PlayerUiState(
    val book: Audiobook? = null,
    val isPlaying: Boolean = false,
    val chapterPositionMs: Long = 0L,
    val chapterDurationMs: Long = 1L,
    val bookPositionMs: Long = 0L,
    val bookDurationMs: Long = 1L,
    val currentChapterIndex: Int = 0,
    val chapterPositionsMs: Map<Int, Long> = emptyMap(),
    val speed: Float = 1f,
    val sleepTimer: SleepTimer? = null,
    val repeatEnabled: Boolean = false,
    val finished: Boolean = false,
)

sealed interface SleepTimer {
    data class Minutes(val minutes: Int) : SleepTimer
    data class EndOfChapters(val chapters: Int) : SleepTimer
    data object EndOfBook : SleepTimer

    val chipLabel: String
        get() = when (this) {
            is Minutes -> "${minutes}m"
            is EndOfChapters -> if (chapters == 1) "Ch" else "${chapters}ch"
            EndOfBook -> "End"
        }
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("booky_prefs", 0)
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var positionJob: Job? = null
    private var sleepJob: Job? = null
    private var lastPersistAt = 0L
    private var playWhenReadyAfterConnect = false
    private val chapterProgress = mutableMapOf<Int, Long>()
    private var sleepUntilMediaIndex: Int? = null
    private var queue: List<Audiobook> = emptyList()
    private var handledEndId: String? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishFromPlayer()
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                if (player.isPlaying) startPositionUpdates() else {
                    positionJob?.cancel()
                    persist()
                }
            }
        }
    }

    init {
        viewModelScope.launch { connect() }
    }

    fun selectBook(book: Audiobook, play: Boolean = false) {
        val sameBook = _state.value.book?.id == book.id
        if (sameBook) {
            _state.update { current ->
                current.copy(book = book.withChapter(current.book))
            }
            persist()
            loadCurrentBook(playWhenReady = controller?.isPlaying == true, resetPosition = false)
            return
        }
        val storedId = prefs.getString(KEY_BOOK_ID, null)
        val storedPosition = prefs.getLong(KEY_POSITION, book.listenedMs)
        val bookPosition = when {
            storedId == book.id && storedPosition > book.listenedMs -> {
                storedPosition.coerceIn(0L, book.durationMs.coerceAtLeast(1L))
            }
            else -> book.listenedMs.coerceIn(0L, book.durationMs.coerceAtLeast(1L))
        }
        rememberCurrentChapter()
        persistChapterProgress()
        val (index, offset) = windowForBookPosition(bookPosition, book.chapterDurationsMs)
        _state.update {
            it.copy(
                book = book.copy(
                    currentChapter = index + 1,
                    currentChapterTitle = book.chapterTitles.getOrNull(index) ?: book.currentChapterTitle,
                ),
                isPlaying = false,
                chapterPositionMs = offset,
                chapterDurationMs = book.chapterDurationsMs.getOrNull(index)?.coerceAtLeast(1L) ?: 1L,
                bookPositionMs = bookPosition,
                bookDurationMs = book.durationMs.coerceAtLeast(1L),
                currentChapterIndex = index,
                finished = bookPosition >= book.durationMs && book.durationMs > 0,
            )
        }
        loadChapterProgress(book.id)
        persist(captureChapter = false)
        handledEndId = null
        loadCurrentBook(playWhenReady = play, resetPosition = true)
    }

    fun patchDisplayedBook(book: Audiobook) {
        _state.update { current ->
            val playing = current.book ?: return@update current
            if (playing.id != book.id) current
            else current.copy(
                book = playing.copy(
                    title = book.title,
                    author = book.author,
                    narrator = book.narrator,
                    coverUri = book.coverUri,
                ),
            )
        }
    }

    fun bindLibrary(books: List<Audiobook>) {
        queue = books
        val current = _state.value.book
        if (current != null) {
            val match = books.find { it.id == current.id } ?: return
            _state.update {
                it.copy(book = match.withChapter(current))
            }
            loadCurrentBook(playWhenReady = controller?.isPlaying == true, resetPosition = false)
            return
        }
        val id = prefs.getString(KEY_BOOK_ID, null) ?: return
        val book = books.find { it.id == id } ?: return
        val bookDuration = book.durationMs.coerceAtLeast(1L)
        val bookPosition = book.listenedMs.coerceIn(0L, bookDuration)
        val (index, offset) = windowForBookPosition(bookPosition, book.chapterDurationsMs)
        _state.value = PlayerUiState(
            book = book.copy(
                currentChapter = index + 1,
                currentChapterTitle = book.chapterTitles.getOrNull(index) ?: book.currentChapterTitle,
            ),
            isPlaying = false,
            chapterPositionMs = offset,
            chapterDurationMs = book.chapterDurationsMs.getOrNull(index)?.coerceAtLeast(1L) ?: 1L,
            bookPositionMs = bookPosition,
            bookDurationMs = bookDuration,
            currentChapterIndex = index,
            speed = prefs.getFloat(KEY_SPEED, 1f),
            repeatEnabled = prefs.getBoolean(KEY_REPEAT, false),
            finished = bookPosition >= bookDuration && bookDuration > 1L,
        )
        loadChapterProgress(book.id)
        loadCurrentBook(playWhenReady = false, resetPosition = true)
    }

    fun togglePlay() {
        val book = _state.value.book ?: return
        val player = controller
        if (player == null) {
            playWhenReadyAfterConnect = true
            return
        }
        if (!isBookLoaded(book)) {
            loadCurrentBook(playWhenReady = true, resetPosition = true)
            return
        }
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skip(deltaMs: Long) {
        val player = controller ?: return
        if (_state.value.book == null) return
        rememberCurrentChapter()
        val duration = player.bookDurationMs(_state.value.bookDurationMs)
        val target = (player.bookPositionMs() + deltaMs).coerceIn(0L, duration)
        player.seekToBookPosition(target, _state.value.book?.chapterDurationsMs.orEmpty())
        publishFromPlayer()
        persist()
    }

    fun seek(positionMs: Long) {
        val player = controller ?: return
        val duration = _state.value.chapterDurationMs.coerceAtLeast(1L)
        player.seekTo(positionMs.coerceIn(0L, duration))
        publishFromPlayer()
        persist()
    }

    fun seekToChapter(index: Int) {
        val player = controller ?: return
        rememberCurrentChapter()
        persistChapterProgress()
        val last = (player.mediaItemCount - 1).coerceAtLeast(0)
        val target = index.coerceIn(0, last)
        player.seekTo(target, restoredChapterPosition(target))
        publishFromPlayer()
        persist()
    }

    fun restartChapter() {
        val index = controller?.currentMediaItemIndex ?: _state.value.currentChapterIndex
        controller?.seekTo(index, 0L)
        _state.update {
            it.copy(
                chapterPositionMs = 0L,
                finished = false,
            )
        }
        persist()
    }

    fun setSpeed(speed: Float) {
        val snapped = ((speed * 10f).roundToInt() / 10f).coerceIn(0.1f, 3f)
        _state.update { it.copy(speed = snapped) }
        controller?.setPlaybackSpeed(snapped)
        persist()
    }

    fun markChapterPlayed() {
        val player = controller ?: return
        rememberCurrentChapter()
        persistChapterProgress()
        val index = player.currentMediaItemIndex
        val last = (player.mediaItemCount - 1).coerceAtLeast(0)
        if (index >= last) {
            markBookPlayed()
            return
        }
        chapterProgress.remove(index)
        player.seekTo(index + 1, restoredChapterPosition(index + 1))
        publishFromPlayer()
        persist()
    }

    fun markBookPlayed() {
        handledEndId = _state.value.book?.id
        val player = controller
        val duration = player?.bookDurationMs(_state.value.bookDurationMs)
            ?: _state.value.bookDurationMs
        player?.pause()
        player?.seekToBookPosition(duration, _state.value.book?.chapterDurationsMs.orEmpty())
        publishFromPlayer()
        _state.update { it.copy(finished = true, isPlaying = false) }
        persist()
    }

    fun toggleRepeat() {
        val enabled = !_state.value.repeatEnabled
        _state.update { it.copy(repeatEnabled = enabled) }
        controller?.repeatMode = if (enabled) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        persist()
    }

    fun setSleepTimer(timer: SleepTimer?) {
        sleepJob?.cancel()
        sleepUntilMediaIndex = null
        if (timer == null) {
            _state.update { it.copy(sleepTimer = null) }
            return
        }
        _state.update { it.copy(sleepTimer = timer) }
        when (timer) {
            is SleepTimer.Minutes -> {
                sleepJob = viewModelScope.launch {
                    delay(timer.minutes * 60_000L)
                    pauseForSleep()
                }
            }
            is SleepTimer.EndOfChapters -> {
                sleepUntilMediaIndex = _state.value.currentChapterIndex + timer.chapters
            }
            SleepTimer.EndOfBook -> Unit
        }
    }

    fun closeBook() {
        positionJob?.cancel()
        sleepJob?.cancel()
        sleepUntilMediaIndex = null
        playWhenReadyAfterConnect = false
        rememberCurrentChapter()
        persistChapterProgress()
        controller?.pause()
        controller?.stop()
        controller?.clearMediaItems()
        chapterProgress.clear()
        _state.value = PlayerUiState()
        persist(captureChapter = false)
    }

    private fun pauseForSleep() {
        sleepJob?.cancel()
        sleepUntilMediaIndex = null
        controller?.pause()
        _state.update { it.copy(sleepTimer = null, isPlaying = false) }
    }

    private fun checkSleep(index: Int, ended: Boolean) {
        when (_state.value.sleepTimer) {
            is SleepTimer.Minutes -> Unit
            is SleepTimer.EndOfChapters -> {
                val until = sleepUntilMediaIndex ?: return
                if (ended || index >= until) {
                    pauseForSleep()
                } else {
                    val left = (until - index).coerceAtLeast(1)
                    if (left != (_state.value.sleepTimer as? SleepTimer.EndOfChapters)?.chapters) {
                        _state.update { it.copy(sleepTimer = SleepTimer.EndOfChapters(left)) }
                    }
                }
            }
            SleepTimer.EndOfBook -> if (ended) pauseForSleep()
            null -> Unit
        }
    }

    override fun onCleared() {
        positionJob?.cancel()
        sleepJob?.cancel()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        controllerFuture?.cancel(true)
        super.onCleared()
    }

    private suspend fun connect() {
        val app = getApplication<Application>()
        val future = MediaController.Builder(
            app,
            SessionToken(app, ComponentName(app, PlaybackService::class.java)),
        ).buildAsync()
        controllerFuture = future
        val player = future.awaitController() ?: return
        controller = player
        player.addListener(listener)
        val shouldPlay = playWhenReadyAfterConnect
        playWhenReadyAfterConnect = false
        if (_state.value.book != null) {
            loadCurrentBook(
                playWhenReady = shouldPlay,
                resetPosition = !isBookLoaded(_state.value.book!!),
            )
        }
        publishFromPlayer()
    }

    private fun loadCurrentBook(playWhenReady: Boolean, resetPosition: Boolean) {
        val player = controller ?: return
        val book = _state.value.book ?: return
        val items = book.toMediaItems()
        if (items.isEmpty()) return
        val position = _state.value.bookPositionMs
        if (resetPosition || !isBookLoaded(book)) {
            val (index, offset) = windowForBookPosition(position, book.chapterDurationsMs)
            player.setMediaItems(items, index, offset)
            player.prepare()
        }
        player.setPlaybackSpeed(_state.value.speed)
        player.repeatMode = if (_state.value.repeatEnabled) {
            Player.REPEAT_MODE_ALL
        } else {
            Player.REPEAT_MODE_OFF
        }
        player.playWhenReady = playWhenReady
    }

    private fun isBookLoaded(book: Audiobook): Boolean {
        val player = controller ?: return false
        val expected = book.mediaUris.ifEmpty { listOfNotNull(book.artworkFileUri) }
        if (player.mediaItemCount != expected.size || expected.isEmpty()) return false
        return player.getMediaItemAt(0).mediaId.startsWith("${book.id}#")
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (isActive) {
                publishFromPlayer(throttlePersist = true)
                delay(250)
                if (controller?.isPlaying != true) break
            }
        }
    }

    private fun publishFromPlayer(throttlePersist: Boolean = false) {
        val player = controller ?: return
        val book = _state.value.book ?: return
        val index = player.currentMediaItemIndex.coerceAtLeast(0)
        val chapterTitle = book.chapterTitles.getOrNull(index) ?: book.currentChapterTitle
        val bookDuration = player.bookDurationMs(book.durationMs.coerceAtLeast(1L))
        val bookPosition = player.bookPositionMs().coerceIn(0L, bookDuration)
        val chapterDuration = player.currentChapterDurationMs(
            book.chapterDurationsMs.getOrNull(index)?.coerceAtLeast(1L) ?: 1L,
        )
        val chapterPosition = player.currentPosition.coerceAtLeast(0L).let { position ->
            if (chapterDuration > 1L) position.coerceAtMost(chapterDuration) else position
        }
        val ended = player.playbackState == Player.STATE_ENDED
        _state.update {
            it.copy(
                book = book.copy(
                    currentChapter = index + 1,
                    currentChapterTitle = chapterTitle,
                ),
                isPlaying = player.isPlaying,
                chapterPositionMs = if (ended) chapterDuration else chapterPosition,
                chapterDurationMs = chapterDuration,
                bookPositionMs = if (ended) bookDuration else bookPosition,
                bookDurationMs = bookDuration,
                currentChapterIndex = index,
                speed = player.playbackParameters.speed,
                finished = ended || (bookDuration > 1L && bookPosition >= bookDuration),
                chapterPositionsMs = chapterProgress.toMap() + (index to (if (ended) chapterDuration else chapterPosition)),
            )
        }
        checkSleep(index, ended)
        maybeAdvanceQueue(book, index, ended)
        if (throttlePersist) {
            val now = System.currentTimeMillis()
            if (now - lastPersistAt >= 5_000L) persist()
        }
    }

    private fun maybeAdvanceQueue(book: Audiobook, index: Int, ended: Boolean) {
        if (!ended || _state.value.repeatEnabled) return
        val last = (controller?.mediaItemCount ?: 1) - 1
        if (index < last) return
        if (handledEndId == book.id) return
        handledEndId = book.id
        val position = queue.indexOfFirst { it.id == book.id }
        val next = queue.getOrNull(position + 1) ?: return
        selectBook(next, play = true)
    }

    private fun persist(captureChapter: Boolean = true) {
        lastPersistAt = System.currentTimeMillis()
        if (captureChapter) {
            rememberCurrentChapter()
            persistChapterProgress()
        }
        val state = _state.value
        val book = state.book
        if (book == null) {
            prefs.edit()
                .remove(KEY_BOOK_ID)
                .remove(KEY_POSITION)
                .remove(KEY_SPEED)
                .remove(KEY_REPEAT)
                .apply()
            return
        }
        prefs.edit()
            .putString(KEY_BOOK_ID, book.id)
            .putLong(KEY_POSITION, state.bookPositionMs)
            .putFloat(KEY_SPEED, state.speed)
            .putBoolean(KEY_REPEAT, state.repeatEnabled)
            .apply()
    }

    private suspend fun <T> ListenableFuture<T>.awaitController(): T? {
        return suspendCancellableCoroutine { continuation ->
            addListener(
                {
                    if (!continuation.isActive) return@addListener
                    try {
                        continuation.resume(get())
                    } catch (error: ExecutionException) {
                        continuation.resumeWithException(error.cause ?: error)
                    } catch (error: Exception) {
                        continuation.resumeWithException(error)
                    }
                },
                ContextCompat.getMainExecutor(getApplication()),
            )
            continuation.invokeOnCancellation { cancel(true) }
        }
    }

    private fun rememberCurrentChapter() {
        val index = controller?.currentMediaItemIndex ?: _state.value.currentChapterIndex
        val position = controller?.currentPosition?.coerceAtLeast(0L) ?: _state.value.chapterPositionMs
        val duration = _state.value.chapterDurationMs.coerceAtLeast(1L)
        val stored = when {
            position < 1_500L -> 0L
            position >= duration - 2_000L -> 0L
            else -> position
        }
        if (stored == 0L) {
            chapterProgress.remove(index)
        } else {
            chapterProgress[index] = stored
        }
        _state.update { it.copy(chapterPositionsMs = chapterProgress.toMap()) }
    }

    private fun restoredChapterPosition(index: Int): Long {
        val saved = chapterProgress[index] ?: return 0L
        val duration = _state.value.book?.chapterDurationsMs?.getOrNull(index)?.coerceAtLeast(1L)
            ?: _state.value.chapterDurationMs.coerceAtLeast(1L)
        if (saved >= duration - 2_000L) return 0L
        return saved.coerceIn(0L, (duration - 500L).coerceAtLeast(0L))
    }

    private fun loadChapterProgress(bookId: String) {
        chapterProgress.clear()
        val raw = prefs.getString(KEY_CHAPTER_PROGRESS + bookId.hashCode(), null)
        raw?.split(',')?.forEach { part ->
            val pieces = part.split(':')
            if (pieces.size != 2) return@forEach
            val index = pieces[0].toIntOrNull() ?: return@forEach
            val position = pieces[1].toLongOrNull() ?: return@forEach
            if (position > 0L) chapterProgress[index] = position
        }
        _state.update { it.copy(chapterPositionsMs = chapterProgress.toMap()) }
    }

    private fun persistChapterProgress() {
        val bookId = _state.value.book?.id ?: return
        val encoded = chapterProgress.entries
            .filter { it.value > 0L }
            .joinToString(",") { "${it.key}:${it.value}" }
        val key = KEY_CHAPTER_PROGRESS + bookId.hashCode()
        val editor = prefs.edit()
        if (encoded.isBlank()) editor.remove(key) else editor.putString(key, encoded)
        editor.apply()
    }

    private companion object {
        const val KEY_BOOK_ID = "last_book_id"
        const val KEY_POSITION = "last_position_ms"
        const val KEY_SPEED = "last_speed"
        const val KEY_REPEAT = "last_repeat"
        const val KEY_CHAPTER_PROGRESS = "chapter_progress_"
    }
}

private fun Audiobook.withChapter(previous: Audiobook?): Audiobook {
    if (previous == null || previous.id != id) return this
    return copy(
        currentChapter = previous.currentChapter,
        currentChapterTitle = previous.currentChapterTitle,
    )
}
