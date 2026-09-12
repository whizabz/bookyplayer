package com.booky.app.library

import android.content.Context
import com.booky.app.data.Audiobook
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class LibraryCatalog(
    val folderUri: String,
    val treeFingerprint: String,
    val books: List<Audiobook>,
    val fileFingerprints: Map<String, String>,
)

object LibraryCatalogStore {
    fun load(context: Context): LibraryCatalog? {
        val file = file(context)
        if (!file.exists()) return null
        val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
        val folderUri = root.optString("folderUri").ifBlank { return null }
        val treeFingerprint = root.optString("treeFingerprint")
        val booksJson = root.optJSONArray("books") ?: JSONArray()
        val books = buildList {
            for (i in 0 until booksJson.length()) {
                decodeBook(booksJson.optJSONObject(i) ?: continue)?.let(::add)
            }
        }
        val prints = mutableMapOf<String, String>()
        val printsJson = root.optJSONObject("fileFingerprints") ?: JSONObject()
        printsJson.keys().forEach { key ->
            prints[key] = printsJson.optString(key)
        }
        return LibraryCatalog(folderUri, treeFingerprint, books, prints)
    }

    fun save(context: Context, catalog: LibraryCatalog) {
        val books = JSONArray()
        catalog.books.forEach { books.put(encodeBook(it)) }
        val prints = JSONObject()
        catalog.fileFingerprints.forEach { (id, value) -> prints.put(id, value) }
        val root = JSONObject()
            .put("folderUri", catalog.folderUri)
            .put("treeFingerprint", catalog.treeFingerprint)
            .put("books", books)
            .put("fileFingerprints", prints)
        file(context).writeText(root.toString())
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    private fun file(context: Context) = File(context.filesDir, "library_catalog.json")

    private fun encodeBook(book: Audiobook) = JSONObject()
        .put("id", book.id)
        .put("title", book.title)
        .put("author", book.author)
        .put("narrator", book.narrator)
        .put("chapterCount", book.chapterCount)
        .put("fileCount", book.fileCount)
        .put("durationMs", book.durationMs)
        .put("currentChapter", book.currentChapter)
        .put("currentChapterTitle", book.currentChapterTitle)
        .put("fileName", book.fileName)
        .put("coverUri", book.coverUri ?: JSONObject.NULL)
        .put("artworkFileUri", book.artworkFileUri ?: JSONObject.NULL)
        .put("mediaUris", JSONArray(book.mediaUris))
        .put("chapterTitles", JSONArray(book.chapterTitles))
        .put("chapterDurationsMs", JSONArray(book.chapterDurationsMs))
        .put("chapterStartMs", JSONArray(book.chapterStartMs))
        .put("addedAtMs", book.addedAtMs)

    private fun decodeBook(json: JSONObject): Audiobook? {
        val id = json.optString("id").ifBlank { return null }
        val titles = stringList(json.optJSONArray("chapterTitles"))
        return Audiobook(
            id = id,
            title = json.optString("title").ifBlank { "Audiobook" },
            author = json.optString("author").ifBlank { "Unknown" },
            narrator = json.optString("narrator").ifBlank { "Unknown" },
            chapterCount = json.optInt("chapterCount", titles.size.coerceAtLeast(1)),
            fileCount = json.optInt("fileCount", 1),
            durationMs = json.optLong("durationMs"),
            listenedMs = 0L,
            currentChapter = json.optInt("currentChapter", 1),
            currentChapterTitle = json.optString("currentChapterTitle").ifBlank {
                titles.firstOrNull() ?: json.optString("title")
            },
            fileName = json.optString("fileName"),
            coverUri = json.optString("coverUri").takeIf { it.isNotBlank() },
            artworkFileUri = json.optString("artworkFileUri").takeIf { it.isNotBlank() },
            mediaUris = stringList(json.optJSONArray("mediaUris")),
            chapterTitles = titles,
            chapterDurationsMs = longList(json.optJSONArray("chapterDurationsMs")),
            chapterStartMs = longList(json.optJSONArray("chapterStartMs")),
            addedAtMs = json.optLong("addedAtMs"),
        )
    }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return List(array.length()) { array.optString(it) }
    }

    private fun longList(array: JSONArray?): List<Long> {
        if (array == null) return emptyList()
        return List(array.length()) { array.optLong(it) }
    }
}
