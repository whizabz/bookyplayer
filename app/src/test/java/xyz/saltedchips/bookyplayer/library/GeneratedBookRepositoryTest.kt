package xyz.saltedchips.bookyplayer.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.saltedchips.bookyplayer.epub.EpubBook
import xyz.saltedchips.bookyplayer.epub.EpubChapter

class GeneratedBookRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun repo() = GeneratedBookRepository(tmp.newFolder("generated"))

    private val epub = EpubBook(
        title = "Test Book",
        author = "Jane",
        language = "en",
        chapters = listOf(
            EpubChapter("One", "Text one."),
            EpubChapter("Two", "Text two."),
            EpubChapter("Three", "Text three."),
        ),
        coverBytes = byteArrayOf(1, 2, 3),
        coverExtension = "png",
    )

    @Test
    fun createWritesTextCoverAndMetadata() {
        val repo = repo()
        val book = repo.create(epub, voiceSid = 3, speed = 1f)
        assertEquals("Text two.", repo.chapterTextFile(book.uuid, 1).readText())
        assertEquals("cover.png", repo.coverFile(book.uuid)?.name)
        val loaded = repo.load(book.uuid)
        assertNotNull(loaded)
        assertEquals(book, loaded)
        assertEquals(ConversionStatus.Converting, loaded!!.status)
        assertEquals(listOf("One", "Two", "Three"), loaded.chapters.map { it.title })
    }

    @Test
    fun hiddenUntilFirstChapterIsDone() {
        val repo = repo()
        val book = repo.create(epub, 3, 1f)
        assertNull(repo.toAudiobook(book))
        assertTrue(repo.audiobooks().isEmpty())
    }

    @Test
    fun exposesOnlyTheLeadingFinishedChapters() {
        val repo = repo()
        var book = repo.create(epub, 3, 1f)
        book = book.withChapterDone(0, 60_000L).withChapterDone(2, 30_000L)
        repo.save(book)
        val audio = repo.toAudiobook(repo.load(book.uuid)!!)!!
        assertEquals(book.id, audio.id)
        assertEquals(1, audio.mediaUris.size)
        assertEquals(60_000L, audio.durationMs)
        assertEquals(listOf("One"), audio.chapterTitles)
        assertTrue(audio.mediaUris[0].startsWith("file://"))
        assertTrue(audio.coverUri!!.endsWith("cover.png"))

        val more = repo.load(book.uuid)!!.withChapterDone(1, 45_000L)
        repo.save(more)
        val full = repo.toAudiobook(repo.load(book.uuid)!!)!!
        assertEquals(3, full.mediaUris.size)
        assertEquals(135_000L, full.durationMs)
        assertEquals(listOf(0L, 0L, 0L), full.chapterStartMs)
    }

    @Test
    fun deleteRemovesEverything() {
        val repo = repo()
        val book = repo.create(epub, 3, 1f)
        repo.delete(book.uuid)
        assertNull(repo.load(book.uuid))
        assertTrue(repo.all().isEmpty())
    }

    @Test
    fun recognisesGeneratedIds() {
        assertTrue(GeneratedBookRepository.isGenerated("gen:abc"))
        assertEquals("abc", GeneratedBookRepository.uuidOf("gen:abc"))
        assertEquals(false, GeneratedBookRepository.isGenerated("content://x"))
    }
}
