package xyz.saltedchips.bookyplayer.library

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import xyz.saltedchips.bookyplayer.data.Audiobook
import xyz.saltedchips.bookyplayer.data.BookKind
import xyz.saltedchips.bookyplayer.epub.EpubMetadata
import xyz.saltedchips.bookyplayer.epub.EpubParser

data class LibraryScanResult(
    val treeFingerprint: String,
    val books: List<Audiobook>,
    val fileFingerprints: Map<String, String>,
)

class LibraryScanner(private val context: Context) {
    fun treeFingerprint(treeUri: Uri): String {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return ""
        val parts = mutableListOf<String>()
        discover(root, libraryRoot = true, authorHint = null, pending = mutableListOf(), parts = parts)
        return parts.joinToString("\n")
    }

    fun scan(
        treeUri: Uri,
        cache: Map<String, Pair<Audiobook, String>> = emptyMap(),
        onProgress: (done: Int, total: Int, title: String?) -> Unit = { _, _, _ -> },
        onDiscovered: (List<String>) -> Unit = {},
        onBooks: (List<Audiobook>, Map<String, String>) -> Unit = { _, _ -> },
    ): LibraryScanResult {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return LibraryScanResult("", emptyList(), emptyMap())
        val pending = mutableListOf<PendingBook>()
        val parts = mutableListOf<String>()
        onProgress(0, 0, null)
        discover(
            dir = root,
            libraryRoot = true,
            authorHint = null,
            pending = pending,
            parts = parts,
        )
        val total = pending.size
        onDiscovered(pending.map { it.id })
        onProgress(0, total, null)
        val books = mutableListOf<Audiobook>()
        val fingerprints = mutableMapOf<String, String>()
        pending.forEachIndexed { index, item ->
            if (item.epub) {
                addEpub(item.id, item.title, item.media.first(), books, fingerprints, cache)
                onProgress(index + 1, total, item.title)
                onBooks(books.toList(), fingerprints.toMap())
                return@forEachIndexed
            }
            addBook(
                id = item.id,
                title = item.title,
                author = item.author,
                media = item.media,
                coverUri = item.coverUri,
                books = books,
                fingerprints = fingerprints,
                cache = cache,
            )
            val done = index + 1
            onProgress(done, total, item.title)
            onBooks(books.toList(), fingerprints.toMap())
        }
        return LibraryScanResult(parts.joinToString("\n"), books, fingerprints)
    }

    private data class PendingBook(
        val id: String,
        val title: String,
        val author: String,
        val media: List<DocumentFile>,
        val coverUri: String?,
        val epub: Boolean = false,
    )

    private fun discover(
        dir: DocumentFile,
        libraryRoot: Boolean,
        authorHint: String?,
        pending: MutableList<PendingBook>,
        parts: MutableList<String>,
    ) {
        parts += "d|${dir.uri}|${dir.name.orEmpty()}|${dir.lastModified()}"
        val children = dir.listFiles()
        val files = children.filter { it.isFile }
        val dirs = children.filter { it.isDirectory }
        files.sortedBy { it.uri.toString() }.forEach { file ->
            parts += "f|${file.uri}|${file.name.orEmpty()}|${file.lastModified()}|${file.length()}"
        }
        val media = files.filter { child ->
            val name = child.name ?: return@filter false
            MediaKinds.isMedia(name)
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name ?: "" })

        files.filter { it.name?.let(MediaKinds::isEpub) == true }
            .sortedBy { it.uri.toString() }
            .forEach { file ->
                pending += PendingBook(
                    id = file.uri.toString(),
                    title = MetadataText.repair(MediaKinds.stem(file.name ?: "ePub")),
                    author = "Unknown",
                    media = listOf(file),
                    coverUri = null,
                    epub = true,
                )
            }

        if (!libraryRoot && media.isNotEmpty()) {
            pending += PendingBook(
                id = dir.uri.toString(),
                title = MetadataText.repair(dir.name ?: MediaKinds.stem(media.first().name ?: "Audiobook")),
                author = MetadataText.repair(authorHint?.takeIf { it.isNotBlank() } ?: "Unknown"),
                media = media,
                coverUri = findCover(files)?.uri?.toString(),
            )
            dirs.sortedBy { it.uri.toString() }.forEach { child ->
                parts += "d|${child.uri}|${child.name.orEmpty()}|${child.lastModified()}"
            }
            return
        }

        if (libraryRoot) {
            for (file in media) {
                pending += PendingBook(
                    id = file.uri.toString(),
                    title = MetadataText.repair(MediaKinds.stem(file.name ?: "Audiobook")),
                    author = "Unknown",
                    media = listOf(file),
                    coverUri = null,
                )
            }
        }

        val nestedHint = if (libraryRoot) null else (dir.name ?: authorHint)
        for (child in dirs.sortedBy { it.uri.toString() }) {
            discover(
                dir = child,
                libraryRoot = false,
                authorHint = nestedHint,
                pending = pending,
                parts = parts,
            )
        }
    }

    private fun addBook(
        id: String,
        title: String,
        author: String,
        media: List<DocumentFile>,
        coverUri: String?,
        books: MutableList<Audiobook>,
        fingerprints: MutableMap<String, String>,
        cache: Map<String, Pair<Audiobook, String>>,
    ) {
        val fingerprint = fileFingerprint(media, coverUri)
        fingerprints[id] = fingerprint
        val cleanedTitle = MetadataText.repair(title)
        val cached = cache[id]
        if (cached != null && cached.second == fingerprint && !MetadataText.isWeakName(cleanedTitle)) {
            val book = cached.first.withRepairedText()
            books += book.copy(
                title = cleanedTitle,
                author = if (book.author != "Unknown") book.author else MetadataText.repair(author),
                coverUri = coverUri,
                fileName = MetadataText.repair(media.first().name ?: book.fileName),
                addedAtMs = media.minOf { file -> file.lastModified().takeIf { it > 0L } ?: 0L },
            )
            return
        }
        books += bookFrom(id, cleanedTitle, MetadataText.repair(author), media, coverUri)
    }

    private fun addEpub(
        id: String,
        stemTitle: String,
        file: DocumentFile,
        books: MutableList<Audiobook>,
        fingerprints: MutableMap<String, String>,
        cache: Map<String, Pair<Audiobook, String>>,
    ) {
        val fingerprint = "epub|${file.uri}|${file.lastModified()}|${file.length()}"
        fingerprints[id] = fingerprint
        val cached = cache[id]
        if (cached != null && cached.second == fingerprint && cached.first.kind == BookKind.Epub) {
            books += cached.first
            return
        }
        books += epubBookFrom(id, stemTitle, file)
    }

    private fun epubBookFrom(id: String, stemTitle: String, file: DocumentFile): Audiobook {
        var meta: EpubMetadata? = null
        val temp = File.createTempFile("scan", ".epub", context.cacheDir)
        try {
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                temp.outputStream().use { input.copyTo(it) }
            }
            meta = runCatching { EpubParser.readMetadata(temp) }.getOrNull()
        } catch (_: Exception) {
            // Listed anyway; opening it explains what's wrong.
        } finally {
            temp.delete()
        }
        var coverUri: String? = null
        meta?.coverBytes?.let { bytes ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 900 || bounds.outHeight / sample > 900) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
            if (bitmap != null) coverUri = CoverStore.save(context, id, bitmap)
        }
        val titles = meta?.chapterTitles.orEmpty()
        return Audiobook(
            id = id,
            title = MetadataText.repair(meta?.title?.takeIf { it.isNotBlank() } ?: stemTitle),
            author = MetadataText.repair(meta?.author?.takeIf { it.isNotBlank() } ?: "Unknown"),
            narrator = "",
            chapterCount = titles.size.coerceAtLeast(1),
            fileCount = 1,
            durationMs = 0L,
            listenedMs = 0L,
            currentChapter = 1,
            currentChapterTitle = titles.firstOrNull().orEmpty(),
            fileName = MetadataText.repair(file.name ?: stemTitle),
            coverUri = coverUri,
            chapterTitles = titles,
            addedAtMs = file.lastModified().takeIf { it > 0L } ?: 0L,
            kind = BookKind.Epub,
        )
    }

    private fun fileFingerprint(media: List<DocumentFile>, coverUri: String?): String {
        return buildString {
            media.forEach { file ->
                append(file.uri)
                append('|')
                append(file.lastModified())
                append('|')
                append(file.length())
                append(';')
            }
            append(coverUri.orEmpty())
        }
    }

    private fun findCover(files: List<DocumentFile>): DocumentFile? {
        val named = files.firstOrNull { file ->
            val name = file.name ?: return@firstOrNull false
            MediaKinds.isCoverFile(name)
        }
        if (named != null) return named
        return files.firstOrNull { file ->
            val name = file.name ?: return@firstOrNull false
            MediaKinds.isImage(name)
        }
    }

    private fun bookFrom(
        id: String,
        title: String,
        author: String,
        media: List<DocumentFile>,
        coverUri: String?,
    ): Audiobook {
        val first = media.first()
        val mediaUris = mutableListOf<String>()
        val chapterTitles = mutableListOf<String>()
        val chapterDurations = mutableListOf<Long>()
        val chapterStarts = mutableListOf<Long>()
        var narrator = ""
        var taggedAuthor: String? = null
        var taggedTitle: String? = null
        val retriever = MediaMetadataRetriever()
        try {
            for (file in media) {
                val uri = file.uri.toString()
                var fileDuration = 0L
                try {
                    retriever.setDataSource(context, file.uri)
                    fileDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                    if (file == first) {
                        taggedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                            ?.trim()?.takeIf { it.isNotEmpty() }
                            ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                                ?.trim()?.takeIf { it.isNotEmpty() }
                        narrator = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                            ?.trim().orEmpty()
                        taggedAuthor = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                            ?.trim()?.takeIf { it.isNotEmpty() }
                            ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                ?.trim()?.takeIf { it.isNotEmpty() }
                    }
                } catch (_: RuntimeException) {
                    // Skip unreadable files; still list the book.
                }
                val embedded = if (MediaKinds.isMp4Container(file.name.orEmpty())) {
                    Mp4ChapterReader.clips(
                        chapters = Mp4ChapterReader.read(context, file.uri),
                        fileDurationMs = fileDuration,
                    )
                } else {
                    emptyList()
                }
                if (embedded.size >= 2) {
                    for (clip in embedded) {
                        mediaUris += uri
                        chapterTitles += MetadataText.repair(clip.title)
                        chapterDurations += clip.durationMs
                        chapterStarts += clip.startMs
                    }
                } else {
                    mediaUris += uri
                    chapterTitles += MetadataText.repair(
                        MediaKinds.stem(file.name ?: "Chapter ${mediaUris.size}"),
                    )
                    chapterDurations += fileDuration
                    chapterStarts += 0L
                }
            }
        } finally {
            retriever.release()
        }
        return Audiobook(
            id = id,
            title = MetadataText.preferredLabel(title, taggedTitle),
            author = taggedAuthor?.let(MetadataText::repair) ?: author,
            narrator = MetadataText.repair(narrator).ifBlank { "Unknown" },
            chapterCount = chapterTitles.size,
            fileCount = media.size,
            durationMs = chapterDurations.sum(),
            listenedMs = 0L,
            currentChapter = 1,
            currentChapterTitle = chapterTitles.first(),
            fileName = MetadataText.repair(first.name ?: title),
            coverUri = coverUri,
            artworkFileUri = first.uri.toString(),
            mediaUris = mediaUris,
            chapterTitles = chapterTitles,
            chapterDurationsMs = chapterDurations,
            chapterStartMs = chapterStarts,
            addedAtMs = media.minOf { file -> file.lastModified().takeIf { it > 0L } ?: 0L },
        )
    }
}
