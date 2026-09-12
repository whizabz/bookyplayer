package com.booky.app.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.booky.app.data.Audiobook

data class LibraryScanResult(
    val treeFingerprint: String,
    val books: List<Audiobook>,
    val fileFingerprints: Map<String, String>,
)

class LibraryScanner(private val context: Context) {
    fun treeFingerprint(treeUri: Uri): String {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return ""
        val parts = mutableListOf<String>()
        collectFingerprint(root, parts)
        return parts.joinToString("\n")
    }

    fun scan(
        treeUri: Uri,
        cache: Map<String, Pair<Audiobook, String>> = emptyMap(),
    ): LibraryScanResult {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return LibraryScanResult("", emptyList(), emptyMap())
        val books = mutableListOf<Audiobook>()
        val fingerprints = mutableMapOf<String, String>()
        val parts = mutableListOf<String>()
        collect(
            dir = root,
            libraryRoot = true,
            authorHint = null,
            books = books,
            fingerprints = fingerprints,
            cache = cache,
        )
        collectFingerprint(root, parts)
        return LibraryScanResult(parts.joinToString("\n"), books, fingerprints)
    }

    private fun collectFingerprint(dir: DocumentFile, parts: MutableList<String>) {
        parts += "d|${dir.uri}|${dir.name.orEmpty()}|${dir.lastModified()}"
        val children = dir.listFiles()
        children.filter { it.isFile }.sortedBy { it.uri.toString() }.forEach { file ->
            parts += "f|${file.uri}|${file.name.orEmpty()}|${file.lastModified()}|${file.length()}"
        }
        children.filter { it.isDirectory }.sortedBy { it.uri.toString() }.forEach { child ->
            collectFingerprint(child, parts)
        }
    }

    private fun collect(
        dir: DocumentFile,
        libraryRoot: Boolean,
        authorHint: String?,
        books: MutableList<Audiobook>,
        fingerprints: MutableMap<String, String>,
        cache: Map<String, Pair<Audiobook, String>>,
    ) {
        val children = dir.listFiles()
        val files = children.filter { it.isFile }
        val dirs = children.filter { it.isDirectory }
        val media = files.filter { child ->
            val name = child.name ?: return@filter false
            MediaKinds.isMedia(name)
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name ?: "" })

        if (!libraryRoot && media.isNotEmpty()) {
            addBook(
                id = dir.uri.toString(),
                title = dir.name ?: MediaKinds.stem(media.first().name ?: "Audiobook"),
                author = authorHint?.takeIf { it.isNotBlank() } ?: "Unknown",
                media = media,
                coverUri = findCover(files)?.uri?.toString(),
                books = books,
                fingerprints = fingerprints,
                cache = cache,
            )
            return
        }

        if (libraryRoot) {
            for (file in media) {
                addBook(
                    id = file.uri.toString(),
                    title = MediaKinds.stem(file.name ?: "Audiobook"),
                    author = "Unknown",
                    media = listOf(file),
                    coverUri = null,
                    books = books,
                    fingerprints = fingerprints,
                    cache = cache,
                )
            }
        }

        val nestedHint = if (libraryRoot) null else (dir.name ?: authorHint)
        for (child in dirs) {
            collect(
                dir = child,
                libraryRoot = false,
                authorHint = nestedHint,
                books = books,
                fingerprints = fingerprints,
                cache = cache,
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
        val cached = cache[id]
        if (cached != null && cached.second == fingerprint) {
            val book = cached.first
            books += book.copy(
                title = title,
                author = if (book.author != "Unknown") book.author else author,
                coverUri = coverUri,
                fileName = media.first().name ?: book.fileName,
                addedAtMs = media.minOf { file -> file.lastModified().takeIf { it > 0L } ?: 0L },
            )
            return
        }
        books += bookFrom(id, title, author, media, coverUri)
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
                        chapterTitles += clip.title
                        chapterDurations += clip.durationMs
                        chapterStarts += clip.startMs
                    }
                } else {
                    mediaUris += uri
                    chapterTitles += MediaKinds.stem(file.name ?: "Chapter ${mediaUris.size}")
                    chapterDurations += fileDuration
                    chapterStarts += 0L
                }
            }
        } finally {
            retriever.release()
        }
        return Audiobook(
            id = id,
            title = title,
            author = taggedAuthor ?: author,
            narrator = narrator.ifBlank { "Unknown" },
            chapterCount = chapterTitles.size,
            fileCount = media.size,
            durationMs = chapterDurations.sum(),
            listenedMs = 0L,
            currentChapter = 1,
            currentChapterTitle = chapterTitles.first(),
            fileName = first.name ?: title,
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
