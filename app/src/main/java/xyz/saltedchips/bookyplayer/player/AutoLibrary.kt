package xyz.saltedchips.bookyplayer.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaSession
import org.json.JSONObject
import xyz.saltedchips.bookyplayer.data.Audiobook
import xyz.saltedchips.bookyplayer.library.LibraryCatalogStore
import xyz.saltedchips.bookyplayer.library.LibrarySort

class AutoLibrary(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, 0)

    fun libraryRoot(): MediaItem {
        return browsableItem(ROOT, "Booky Player")
    }

    fun rootTabs(): List<MediaItem> {
        val tabs = mutableListOf(browsableItem(TAB_LIBRARY, "Library"))
        if (continueBook() != null) {
            tabs += browsableItem(TAB_CONTINUE, "Continue")
        }
        return tabs
    }

    fun children(parentId: String): List<MediaItem> {
        return when (parentId) {
            ROOT -> rootTabs()
            TAB_LIBRARY -> visibleBooks().map { bookItem(it) }
            TAB_CONTINUE -> listOfNotNull(continueBook()?.let { playableBookItem(it) })
            else -> bookIdFromNode(parentId)?.let { id ->
                bookById(id)?.let(::chapterItems).orEmpty()
            }.orEmpty()
        }
    }

    fun item(mediaId: String): MediaItem? {
        return when (mediaId) {
            ROOT -> libraryRoot()
            TAB_LIBRARY -> browsableItem(TAB_LIBRARY, "Library")
            TAB_CONTINUE -> browsableItem(TAB_CONTINUE, "Continue")
            else -> {
                bookIdFromNode(mediaId)?.let { id -> bookById(id)?.let(::bookItem) }
                    ?: parseChapter(mediaId)?.let { (id, index) ->
                        bookById(id)?.let { chapterItem(it, index) }
                    }
            }
        }
    }

    fun resolvePlay(mediaId: String): MediaSession.MediaItemsWithStartPosition? {
        val chapter = parseChapter(mediaId)
        if (chapter != null) {
            val (bookId, index) = chapter
            val book = bookById(bookId) ?: return null
            val items = book.toMediaItems()
            if (items.isEmpty()) return null
            val target = index.coerceIn(0, items.lastIndex)
            markStarted(book.id)
            return MediaSession.MediaItemsWithStartPosition(
                items,
                target,
                restoredChapterPosition(book, target),
            )
        }
        val bookId = bookIdFromNode(mediaId) ?: mediaId
        val book = bookById(bookId) ?: return null
        val items = book.toMediaItems()
        if (items.isEmpty()) return null
        val position = resumePosition(book)
        val (index, offset) = windowForBookPosition(position, book.chapterDurationsMs)
        markStarted(book.id)
        return MediaSession.MediaItemsWithStartPosition(items, index, offset)
    }

    fun playbackResumption(): MediaSession.MediaItemsWithStartPosition? {
        val id = prefs.getString(KEY_BOOK_ID, null) ?: return null
        return resolvePlay(bookNode(id))
    }

    fun persistFromPlayer(player: Player) {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val bookId = bookIdFromMediaId(mediaId) ?: return
        val book = bookById(bookId) ?: return
        val duration = book.durationMs.coerceAtLeast(1L)
        val position = player.bookPositionMs().coerceIn(0L, duration)
        prefs.edit()
            .putString(KEY_BOOK_ID, bookId)
            .putLong(KEY_POSITION, position)
            .apply()
        writeLongMapValue(KEY_LISTENED, bookId, position)
    }

    fun skipBackMs(): Long = snapSkipSeconds(prefs.getInt(KEY_SKIP_BACK, 10)) * 1_000L

    fun skipForwardMs(): Long = snapSkipSeconds(prefs.getInt(KEY_SKIP_FORWARD, 10)) * 1_000L

    fun speed(): Float = prefs.getFloat(KEY_SPEED, 1f)

    fun repeatEnabled(): Boolean = prefs.getBoolean(KEY_REPEAT, false)

    fun bookById(bookId: String): Audiobook? = visibleBooks().find { it.id == bookId }

    fun visibleBooks(): List<Audiobook> {
        val catalog = LibraryCatalogStore.load(appContext) ?: return emptyList()
        val hidden = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty()
        val listened = loadLongMap(KEY_LISTENED)
        val lastPlayed = loadLongMap(KEY_LAST_PLAYED)
        val metadata = loadMetadata()
        val decorated = catalog.books
            .filterNot { it.id in hidden }
            .map { book -> decorate(book, metadata[book.id], listened, lastPlayed) }
        return sortBooks(decorated)
    }

    private fun continueBook(): Audiobook? {
        val id = prefs.getString(KEY_BOOK_ID, null) ?: return null
        return bookById(id)
    }

    private fun decorate(
        book: Audiobook,
        extra: JSONObject?,
        listened: Map<String, Long>,
        lastPlayed: Map<String, Long>,
    ): Audiobook {
        val titles = extra?.optJSONArray("chapters")?.let { array ->
            List(array.length()) { index -> array.optString(index) }
                .takeIf { it.size == book.chapterTitles.size }
        }
        val chapterIndex = (book.currentChapter - 1).coerceAtLeast(0)
        val customCover = extra?.optString("coverUri").orEmpty().ifBlank { null }
        return book.copy(
            title = extra?.optString("title")?.ifBlank { book.title } ?: book.title,
            author = extra?.optString("author")?.ifBlank { book.author } ?: book.author,
            narrator = extra?.optString("narrator")?.ifBlank { book.narrator } ?: book.narrator,
            chapterTitles = titles ?: book.chapterTitles,
            currentChapterTitle = titles?.getOrNull(chapterIndex) ?: book.currentChapterTitle,
            coverUri = customCover ?: book.coverUri,
            listenedMs = listened[book.id] ?: book.listenedMs,
            lastPlayedMs = lastPlayed[book.id] ?: 0L,
        )
    }

    private fun sortBooks(books: List<Audiobook>): List<Audiobook> {
        return when (loadSort()) {
            LibrarySort.LastPlayed -> books.sortedByDescending { it.lastPlayedMs }
            LibrarySort.DateAdded -> books.sortedByDescending { it.addedAtMs }
            LibrarySort.Alphabetical -> books.sortedBy { it.title.lowercase() }
        }
    }

    private fun loadSort(): LibrarySort {
        val named = runCatching { prefs.getString(KEY_SORT, null) }.getOrNull()
        return when (named) {
            LibrarySort.LastPlayed.name -> LibrarySort.LastPlayed
            LibrarySort.DateAdded.name -> LibrarySort.DateAdded
            else -> LibrarySort.Alphabetical
        }
    }

    private fun resumePosition(book: Audiobook): Long {
        val storedId = prefs.getString(KEY_BOOK_ID, null)
        val storedPosition = prefs.getLong(KEY_POSITION, book.listenedMs)
        return when {
            storedId == book.id && storedPosition > book.listenedMs -> {
                storedPosition.coerceIn(0L, book.durationMs.coerceAtLeast(1L))
            }
            else -> book.listenedMs.coerceIn(0L, book.durationMs.coerceAtLeast(1L))
        }
    }

    private fun restoredChapterPosition(book: Audiobook, index: Int): Long {
        val saved = decodeChapterProgress(book.id)[index] ?: return 0L
        val duration = book.chapterDurationsMs.getOrNull(index)?.coerceAtLeast(1L) ?: return 0L
        if (saved >= duration - 2_000L) return 0L
        return saved.coerceIn(0L, (duration - 500L).coerceAtLeast(0L))
    }

    private fun decodeChapterProgress(bookId: String): Map<Int, Long> {
        val raw = prefs.getString(KEY_CHAPTER_PROGRESS + bookId.hashCode(), null) ?: return emptyMap()
        val parsed = mutableMapOf<Int, Long>()
        raw.split(',').forEach { part ->
            val pieces = part.split(':')
            if (pieces.size != 2) return@forEach
            val index = pieces[0].toIntOrNull() ?: return@forEach
            val position = pieces[1].toLongOrNull() ?: return@forEach
            if (position > 0L) parsed[index] = position
        }
        return parsed
    }

    private fun markStarted(bookId: String) {
        writeLongMapValue(KEY_LAST_PLAYED, bookId, System.currentTimeMillis())
    }

    private fun writeLongMapValue(key: String, id: String, value: Long) {
        val map = loadLongMap(key)
        if (map[id] == value) return
        map[id] = value
        val encoded = map.entries.joinToString(",") { "${it.key}=${it.value}" }
        prefs.edit().putString(key, encoded).apply()
    }

    private fun loadLongMap(key: String): MutableMap<String, Long> {
        val map = mutableMapOf<String, Long>()
        prefs.getString(key, null)?.split(',')?.forEach { part ->
            val pieces = part.split('=')
            if (pieces.size != 2) return@forEach
            val parsed = pieces[1].toLongOrNull() ?: return@forEach
            map[pieces[0]] = parsed
        }
        return map
    }

    private fun loadMetadata(): Map<String, JSONObject> {
        val raw = prefs.getString(KEY_METADATA, null) ?: return emptyMap()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val map = mutableMapOf<String, JSONObject>()
        root.keys().forEach { key ->
            map[key] = root.optJSONObject(key) ?: return@forEach
        }
        return map
    }

    private fun artwork(book: Audiobook): Uri? {
        return (book.coverUri ?: book.artworkFileUri)?.let(Uri::parse)
    }

    private fun browsableItem(mediaId: String, title: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build(),
            )
            .build()
    }

    private fun bookItem(book: Audiobook): MediaItem {
        return MediaItem.Builder()
            .setMediaId(bookNode(book.id))
            .setMediaMetadata(bookMetadata(book, playable = true, browsable = true))
            .build()
    }

    private fun playableBookItem(book: Audiobook): MediaItem {
        return MediaItem.Builder()
            .setMediaId(bookNode(book.id))
            .setMediaMetadata(bookMetadata(book, playable = true, browsable = false))
            .build()
    }

    private fun bookMetadata(
        book: Audiobook,
        playable: Boolean,
        browsable: Boolean,
    ): MediaMetadata {
        return MediaMetadata.Builder()
            .setTitle(book.title)
            .setArtist(book.author)
            .setAlbumTitle(book.title)
            .setWriter(book.narrator.takeIf { it.isNotBlank() && it != "Unknown" })
            .setArtworkUri(artwork(book))
            .setIsPlayable(playable)
            .setIsBrowsable(browsable)
            .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
            .build()
    }

    private fun chapterItems(book: Audiobook): List<MediaItem> {
        val count = book.chapterCountForBrowse()
        return List(count) { index -> chapterItem(book, index) }
    }

    private fun chapterItem(book: Audiobook, index: Int): MediaItem {
        val title = book.chapterTitles.getOrNull(index)
            ?: book.currentChapterTitle.takeIf { index == 0 }
            ?: "Chapter ${index + 1}"
        return MediaItem.Builder()
            .setMediaId("${book.id}#$index")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(book.author)
                    .setAlbumTitle(book.title)
                    .setArtworkUri(artwork(book))
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setTrackNumber(index + 1)
                    .setTotalTrackCount(book.chapterCountForBrowse())
                    .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
                    .build(),
            )
            .build()
    }

    companion object {
        const val PREFS = "booky_prefs"
        const val ROOT = "root"
        const val TAB_LIBRARY = "tab_library"
        const val TAB_CONTINUE = "tab_continue"
        const val BOOK_PREFIX = "book:"
        const val KEY_BOOK_ID = "last_book_id"
        const val KEY_POSITION = "last_position_ms"
        const val KEY_SPEED = "last_speed"
        const val KEY_REPEAT = "last_repeat"
        const val KEY_HIDDEN = "library_hidden_ids"
        const val KEY_LAST_PLAYED = "library_last_played"
        const val KEY_LISTENED = "library_listened"
        const val KEY_METADATA = "library_metadata"
        const val KEY_SORT = "library_sort"
        const val KEY_CHAPTER_PROGRESS = "chapter_progress_"
        const val KEY_SKIP_BACK = "skip_back_seconds"
        const val KEY_SKIP_FORWARD = "skip_forward_seconds"
        private val SKIP_OPTIONS = listOf(5, 10, 15, 20, 30, 60)

        val contentStyleExtras
            get() = android.os.Bundle().apply {
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
                )
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
                )
            }

        fun bookNode(id: String) = "$BOOK_PREFIX$id"

        fun bookIdFromNode(mediaId: String): String? {
            if (!mediaId.startsWith(BOOK_PREFIX)) return null
            return mediaId.removePrefix(BOOK_PREFIX).ifBlank { null }
        }

        fun parseChapter(mediaId: String): Pair<String, Int>? {
            if (mediaId.startsWith(BOOK_PREFIX)) return null
            val sep = mediaId.lastIndexOf('#')
            if (sep <= 0) return null
            val index = mediaId.substring(sep + 1).toIntOrNull() ?: return null
            val id = mediaId.substring(0, sep)
            if (id.isBlank()) return null
            return id to index
        }

        fun bookIdFromMediaId(mediaId: String): String? {
            return bookIdFromNode(mediaId) ?: parseChapter(mediaId)?.first
        }

        fun snapSkipSeconds(seconds: Int): Int {
            return SKIP_OPTIONS.minBy { kotlin.math.abs(it - seconds) }
        }
    }
}

private fun Audiobook.chapterCountForBrowse(): Int {
    return chapterTitles.size.takeIf { it > 0 }
        ?: chapterDurationsMs.size.takeIf { it > 0 }
        ?: mediaUris.size.takeIf { it > 0 }
        ?: 1
}
