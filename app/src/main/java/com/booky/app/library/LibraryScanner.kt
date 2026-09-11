package com.booky.app.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.booky.app.data.Audiobook

class LibraryScanner(private val context: Context) {
    fun scan(treeUri: Uri): List<Audiobook> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val books = mutableListOf<Audiobook>()
        collect(dir = root, libraryRoot = true, authorHint = null, books = books)
        return books
    }

    private fun collect(
        dir: DocumentFile,
        libraryRoot: Boolean,
        authorHint: String?,
        books: MutableList<Audiobook>,
    ) {
        val children = dir.listFiles()
        val files = children.filter { it.isFile }
        val dirs = children.filter { it.isDirectory }
        val media = files.filter { child ->
            val name = child.name ?: return@filter false
            MediaKinds.isMedia(name)
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name ?: "" })

        if (!libraryRoot && media.isNotEmpty()) {
            books += bookFrom(
                id = dir.uri.toString(),
                title = dir.name ?: MediaKinds.stem(media.first().name ?: "Audiobook"),
                author = authorHint?.takeIf { it.isNotBlank() } ?: "Unknown",
                media = media,
                coverUri = findCover(files)?.uri?.toString(),
            )
            return
        }

        if (libraryRoot) {
            for (file in media) {
                books += bookFrom(
                    id = file.uri.toString(),
                    title = MediaKinds.stem(file.name ?: "Audiobook"),
                    author = "Unknown",
                    media = listOf(file),
                    coverUri = null,
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
            )
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
