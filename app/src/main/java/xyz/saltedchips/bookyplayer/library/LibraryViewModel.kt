package xyz.saltedchips.bookyplayer.library

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import xyz.saltedchips.bookyplayer.data.Audiobook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class LibrarySort(val label: String) {
    LastPlayed("Last played"),
    DateAdded("Date added"),
    Alphabetical("Alphabetical"),
}

data class LibraryUiState(
    val folderUri: String? = null,
    val folderName: String? = null,
    val folderPath: String? = null,
    val books: List<Audiobook> = emptyList(),
    val playQueue: List<Audiobook> = emptyList(),
    val scanning: Boolean = false,
    val scanDone: Int = 0,
    val scanTotal: Int = 0,
    val scanLabel: String? = null,
    val sort: LibrarySort = LibrarySort.Alphabetical,
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("booky_prefs", 0)
    private val scanner = LibraryScanner(application)
    private var scanned = emptyList<Audiobook>()
    private var fileFingerprints = emptyMap<String, String>()
    private var treeFingerprint: String? = null
    private val hiddenIds = prefs.getStringSet(KEY_HIDDEN, emptySet())!!.toMutableSet()
    private val lastPlayed = loadLongMap(KEY_LAST_PLAYED)
    private val listened = loadLongMap(KEY_LISTENED)
    private val metadata = loadMetadata()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scanGeneration = 0
    private var lastScanPublishMs = 0L

    private val _state = MutableStateFlow(
        LibraryUiState(
            folderUri = prefs.getString(KEY_FOLDER_URI, null),
            sort = loadSort(),
        ),
    )
    val state: StateFlow<LibraryUiState> = _state

    init {
        seedProgressFromLastSession()
        val folderUri = _state.value.folderUri
        if (folderUri != null) {
            refreshFolderName(Uri.parse(folderUri))
            val catalog = LibraryCatalogStore.load(getApplication())
            if (catalog != null && catalog.folderUri == folderUri) {
                scanned = catalog.books
                fileFingerprints = catalog.fileFingerprints
                treeFingerprint = catalog.treeFingerprint
                publishBooks(scanning = false)
            }
            scan(force = false)
        }
    }

    fun setListened(bookId: String, positionMs: Long) {
        val value = positionMs.coerceAtLeast(0L)
        if (listened[bookId] == value) return
        listened[bookId] = value
        persistLongMap(KEY_LISTENED, listened)
        _state.update { state ->
            fun List<Audiobook>.withProgress() = map { book ->
                if (book.id == bookId) book.copy(listenedMs = value) else book
            }
            state.copy(
                books = state.books.withProgress(),
                playQueue = state.playQueue.withProgress(),
            )
        }
    }

    fun setFolder(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        _state.value.folderUri?.let { previous ->
            try {
                resolver.releasePersistableUriPermission(Uri.parse(previous), flags)
            } catch (_: SecurityException) {
                // Already gone.
            }
        }
        try {
            resolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // Some providers grant persistable access without this extra call.
        }
        prefs.edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
        scanned = emptyList()
        fileFingerprints = emptyMap()
        treeFingerprint = null
        LibraryCatalogStore.clear(getApplication())
        _state.value = _state.value.copy(
            folderUri = uri.toString(),
            books = emptyList(),
            playQueue = emptyList(),
            scanning = true,
            scanDone = 0,
            scanTotal = 0,
            scanLabel = null,
        )
        refreshFolderName(uri)
        scan(force = true)
    }

    fun scan(force: Boolean = true) {
        val uri = _state.value.folderUri ?: return
        val generation = ++scanGeneration
        val showSpinner = force || scanned.isEmpty()
        if (showSpinner) {
            _state.value = _state.value.copy(
                scanning = true,
                scanDone = 0,
                scanTotal = 0,
                scanLabel = null,
            )
        } else {
            _state.update {
                it.copy(scanning = true, scanDone = 0, scanTotal = 0, scanLabel = null)
            }
        }
        viewModelScope.launch {
            val tree = Uri.parse(uri)
            val snapshotBooks = scanned
            val snapshotPrints = fileFingerprints
            val storedFingerprint = treeFingerprint
            val result = withContext(Dispatchers.IO) {
                try {
                    if (!force && snapshotBooks.isNotEmpty() && storedFingerprint != null) {
                        val fingerprint = scanner.treeFingerprint(tree)
                        if (fingerprint == storedFingerprint) {
                            return@withContext LibraryScanResult(
                                fingerprint,
                                snapshotBooks,
                                snapshotPrints,
                            )
                        }
                    }
                    scanner.scan(
                        tree,
                        snapshotBooks.mapNotNull { book ->
                            val print = snapshotPrints[book.id] ?: return@mapNotNull null
                            book.id to (book to print)
                        }.toMap(),
                        onProgress = { done, total, title ->
                            postScanUpdate(generation, force = done == 0 || (total > 0 && done == total)) {
                                _state.update { state ->
                                    state.copy(
                                        scanning = true,
                                        scanDone = done,
                                        scanTotal = total,
                                        scanLabel = title,
                                    )
                                }
                            }
                        },
                        onBooks = { books, prints ->
                            postScanUpdate(generation, force = books.size <= 1) {
                                scanned = books
                                fileFingerprints = prints
                                publishBooks(
                                    scanning = true,
                                    scanDone = books.size,
                                    scanTotal = _state.value.scanTotal.coerceAtLeast(books.size),
                                    scanLabel = books.lastOrNull()?.title,
                                )
                            }
                        },
                    )
                } catch (_: SecurityException) {
                    LibraryScanResult("", emptyList(), emptyMap())
                }
            }
            if (generation != scanGeneration) return@launch
            scanned = result.books
            fileFingerprints = result.fileFingerprints
            treeFingerprint = result.treeFingerprint.ifBlank { treeFingerprint }
            if (result.books.isNotEmpty() && result.treeFingerprint.isNotBlank()) {
                LibraryCatalogStore.save(
                    getApplication(),
                    LibraryCatalog(uri, result.treeFingerprint, result.books, result.fileFingerprints),
                )
            }
            publishBooks(
                scanning = false,
                scanDone = 0,
                scanTotal = 0,
                scanLabel = null,
            )
        }
    }

    private fun postScanUpdate(generation: Int, force: Boolean, block: () -> Unit) {
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastScanPublishMs < 100L) return
        lastScanPublishMs = now
        mainHandler.post {
            if (generation != scanGeneration) return@post
            block()
        }
    }

    fun setSort(sort: LibrarySort) {
        prefs.edit().putString(KEY_SORT, sort.name).apply()
        _state.update { it.copy(sort = sort) }
        publishBooks()
    }

    fun touchLastPlayed(bookId: String) {
        lastPlayed[bookId] = System.currentTimeMillis()
        persistLongMap(KEY_LAST_PLAYED, lastPlayed)
        publishBooks()
    }

    fun markPlayed(ids: Collection<String>) {
        val durationById = (scanned).associate { it.id to it.durationMs }
        ids.forEach { id ->
            listened[id] = durationById[id] ?: scanned.firstOrNull { it.id == id }?.durationMs ?: 0L
        }
        persistLongMap(KEY_LISTENED, listened)
        publishBooks()
    }

    fun delete(ids: Collection<String>) {
        val app = getApplication<Application>()
        ids.forEach { id ->
            hiddenIds += id
            lastPlayed.remove(id)
            listened.remove(id)
            metadata.remove(id)
            CoverStore.delete(app, id)
            val uri = Uri.parse(id)
            val file = DocumentFile.fromSingleUri(app, uri)
                ?: DocumentFile.fromTreeUri(app, uri)
            try {
                file?.delete()
            } catch (_: SecurityException) {
                // Stay hidden even if the provider refused the delete.
            }
        }
        persistIds(KEY_HIDDEN, hiddenIds)
        persistLongMap(KEY_LAST_PLAYED, lastPlayed)
        persistLongMap(KEY_LISTENED, listened)
        persistMetadata()
        publishBooks()
    }

    fun updateMetadata(
        bookId: String,
        title: String,
        author: String,
        narrator: String,
        chapterTitles: List<String>,
        cover: Bitmap? = null,
    ) {
        val extra = metadata[bookId] ?: JSONObject()
        extra.put("title", title.trim())
        extra.put("author", author.trim())
        extra.put("narrator", narrator.trim())
        extra.put("chapters", JSONArray(chapterTitles.map { it.trim() }))
        if (cover != null) {
            extra.put("coverUri", CoverStore.save(getApplication(), bookId, cover))
        }
        metadata[bookId] = extra
        persistMetadata()
        publishBooks()
    }

    private fun publishBooks(
        scanning: Boolean = _state.value.scanning,
        scanDone: Int = _state.value.scanDone,
        scanTotal: Int = _state.value.scanTotal,
        scanLabel: String? = _state.value.scanLabel,
    ) {
        val decorated = scanned
            .filterNot { it.id in hiddenIds }
            .map { book ->
                val extra = metadata[book.id]
                val titles = extra?.optJSONArray("chapters")?.let { array ->
                    List(array.length()) { index -> array.optString(index) }
                        .takeIf { it.size == book.chapterTitles.size }
                }
                val chapterIndex = (book.currentChapter - 1).coerceAtLeast(0)
                val customCover = extra?.optString("coverUri").orEmpty().ifBlank { null }
                book.copy(
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
        val books = sortBooks(decorated)
        _state.update {
            it.copy(
                books = books,
                playQueue = books,
                scanning = scanning,
                scanDone = scanDone,
                scanTotal = scanTotal,
                scanLabel = scanLabel,
            )
        }
    }

    private fun sortBooks(books: List<Audiobook>): List<Audiobook> {
        return when (_state.value.sort) {
            LibrarySort.LastPlayed -> books.sortedByDescending { it.lastPlayedMs }
            LibrarySort.DateAdded -> books.sortedByDescending { it.addedAtMs }
            LibrarySort.Alphabetical -> books.sortedBy { it.title.lowercase() }
        }
    }

    private fun loadSort(): LibrarySort {
        val named = runCatching { prefs.getString(KEY_SORT, null) }.getOrNull()
        when (named) {
            LibrarySort.LastPlayed.name -> return LibrarySort.LastPlayed
            LibrarySort.DateAdded.name -> return LibrarySort.DateAdded
            LibrarySort.Alphabetical.name -> return LibrarySort.Alphabetical
        }
        val ordinal = runCatching { prefs.getInt(KEY_SORT, -1) }.getOrDefault(-1)
        return when (ordinal) {
            1 -> LibrarySort.LastPlayed
            2 -> LibrarySort.DateAdded
            else -> LibrarySort.Alphabetical
        }
    }

    private fun seedProgressFromLastSession() {
        val lastId = prefs.getString("last_book_id", null) ?: return
        val lastPos = prefs.getLong("last_position_ms", 0L)
        if (lastPos > (listened[lastId] ?: 0L)) {
            listened[lastId] = lastPos
            persistLongMap(KEY_LISTENED, listened)
        }
    }

    private fun refreshFolderName(uri: Uri) {
        val name = DocumentFile.fromTreeUri(getApplication(), uri)?.name
            ?: uri.lastPathSegment
        _state.value = _state.value.copy(folderName = name, folderPath = treeUriDisplayPath(uri))
    }

    private fun treeUriDisplayPath(uri: Uri): String {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: uri.lastPathSegment?.substringAfter(':')
            ?: return uri.toString()
        return when {
            documentId.startsWith("raw:") -> documentId.removePrefix("raw:")
            documentId.startsWith("primary:") -> {
                val rest = documentId.removePrefix("primary:")
                if (rest.isBlank()) "/storage/emulated/0" else "/storage/emulated/0/$rest"
            }
            else -> {
                val colon = documentId.indexOf(':')
                if (colon >= 0) {
                    val volume = documentId.take(colon)
                    val rest = documentId.substring(colon + 1)
                    if (rest.isBlank()) "/$volume" else "/$volume/$rest"
                } else {
                    documentId
                }
            }
        }
    }

    private fun loadLongMap(key: String): MutableMap<String, Long> {
        val map = mutableMapOf<String, Long>()
        prefs.getString(key, null)?.split(',')?.forEach { part ->
            val pieces = part.split('=')
            if (pieces.size != 2) return@forEach
            val value = pieces[1].toLongOrNull() ?: return@forEach
            map[pieces[0]] = value
        }
        return map
    }

    private fun persistLongMap(key: String, map: Map<String, Long>) {
        val encoded = map.entries.joinToString(",") { "${it.key}=${it.value}" }
        prefs.edit().putString(key, encoded).apply()
    }

    private fun persistIds(key: String, ids: Set<String>) {
        prefs.edit().putStringSet(key, ids.toSet()).apply()
    }

    private fun loadMetadata(): MutableMap<String, JSONObject> {
        val map = mutableMapOf<String, JSONObject>()
        val raw = prefs.getString(KEY_METADATA, null) ?: return map
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return map
        root.keys().forEach { key ->
            map[key] = root.optJSONObject(key) ?: return@forEach
        }
        return map
    }

    private fun persistMetadata() {
        val root = JSONObject()
        metadata.forEach { (id, value) -> root.put(id, value) }
        prefs.edit().putString(KEY_METADATA, root.toString()).apply()
    }

    override fun onCleared() {
        scanGeneration += 1
        mainHandler.removeCallbacksAndMessages(null)
        super.onCleared()
    }

    private companion object {
        const val KEY_FOLDER_URI = "library_folder_uri"
        const val KEY_HIDDEN = "library_hidden_ids"
        const val KEY_LAST_PLAYED = "library_last_played"
        const val KEY_LISTENED = "library_listened"
        const val KEY_METADATA = "library_metadata"
        const val KEY_SORT = "library_sort"
    }
}
