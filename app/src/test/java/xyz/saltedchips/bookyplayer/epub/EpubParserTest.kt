package xyz.saltedchips.bookyplayer.epub

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubParserTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun epub(entries: Map<String, String>): File {
        val file = tmp.newFile("book-${System.nanoTime()}.epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, body) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    private val container = """<?xml version="1.0"?>
        <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
          <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
        </container>"""

    private fun chapter(heading: String, body: String) =
        """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>x</title></head>
           <body><h1>$heading</h1><p>$body</p><p>Second paragraph of $heading with more words in it.</p></body></html>"""

    private val longText = "This is a long enough paragraph of prose to count as a real chapter."

    @Test
    fun parsesEpub3WithNavCoverAndSkipsFrontMatter() {
        val opf = """<?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Test Book</dc:title><dc:creator>Jane Author</dc:creator><dc:language>en</dc:language>
              </metadata>
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
                <item id="cp" href="copyright.xhtml" media-type="application/xhtml+xml"/>
                <item id="cv" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>
              </manifest>
              <spine><itemref idref="cp"/><itemref idref="c1"/><itemref idref="c2"/></spine>
            </package>"""
        val nav = """<html xmlns:epub="http://www.idpf.org/2007/ops"><body>
            <nav epub:type="toc"><ol>
              <li><a href="copyright.xhtml">Copyright</a></li>
              <li><a href="ch1.xhtml">The Beginning</a></li>
              <li><a href="text/ch2.xhtml#start">The Middle</a></li>
            </ol></nav></body></html>"""
        val book = EpubParser.parse(
            epub(
                mapOf(
                    "META-INF/container.xml" to container,
                    "OEBPS/content.opf" to opf,
                    "OEBPS/nav.xhtml" to nav,
                    "OEBPS/ch1.xhtml" to chapter("Ch One", longText),
                    "OEBPS/text/ch2.xhtml" to chapter("Ch Two", longText),
                    "OEBPS/copyright.xhtml" to chapter("Copyright", longText),
                    "OEBPS/cover.jpg" to "JPEGDATA",
                ),
            ),
        )
        assertEquals("Test Book", book.title)
        assertEquals("Jane Author", book.author)
        assertEquals("en", book.language)
        assertEquals(listOf("The Beginning", "The Middle"), book.chapters.map { it.title })
        assertTrue(book.chapters[0].text.contains("long enough paragraph"))
        assertTrue(book.chapters[0].text.contains("\n\n"))
        assertNotNull(book.coverBytes)
        assertEquals("jpg", book.coverExtension)
    }

    @Test
    fun parsesEpub2WithNcx() {
        val opf = """<?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Old Book</dc:title></metadata>
              <manifest>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="c1" href="ch1.html" media-type="application/xhtml+xml"/>
              </manifest>
              <spine toc="ncx"><itemref idref="c1"/></spine>
            </package>"""
        val ncx = """<?xml version="1.0"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap>
            <navPoint id="n1"><navLabel><text>Opening</text></navLabel><content src="ch1.html"/></navPoint>
            </navMap></ncx>"""
        val book = EpubParser.parse(
            epub(
                mapOf(
                    "META-INF/container.xml" to container,
                    "OEBPS/content.opf" to opf,
                    "OEBPS/toc.ncx" to ncx,
                    "OEBPS/ch1.html" to chapter("Whatever", longText),
                ),
            ),
        )
        assertEquals(listOf("Opening"), book.chapters.map { it.title })
        assertEquals("", book.author)
    }

    @Test
    fun refusesDrmProtectedBooks() {
        val encryption = """<?xml version="1.0"?>
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <EncryptedData><EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes128-cbc"/></EncryptedData>
            </encryption>"""
        try {
            EpubParser.parse(
                epub(mapOf("META-INF/container.xml" to container, "META-INF/encryption.xml" to encryption)),
            )
            fail("expected DRM exception")
        } catch (expected: EpubDrmException) {
        }
    }

    @Test
    fun allowsObfuscatedFontsOnly() {
        val encryption = """<?xml version="1.0"?>
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <EncryptedData><EncryptionMethod Algorithm="http://www.idpf.org/2008/embedding"/></EncryptedData>
            </encryption>"""
        val opf = """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0">
            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>T</dc:title></metadata>
            <manifest><item id="c1" href="c.xhtml" media-type="application/xhtml+xml"/></manifest>
            <spine><itemref idref="c1"/></spine></package>"""
        val book = EpubParser.parse(
            epub(
                mapOf(
                    "META-INF/container.xml" to container,
                    "META-INF/encryption.xml" to encryption,
                    "OEBPS/content.opf" to opf,
                    "OEBPS/c.xhtml" to chapter("One", longText),
                ),
            ),
        )
        assertEquals(1, book.chapters.size)
        assertEquals("One", book.chapters[0].title)
    }

    @Test
    fun resolvesRelativePaths() {
        assertEquals("OEBPS/text/ch2.xhtml", EpubParser.resolve("OEBPS", "text/ch2.xhtml#a"))
        assertEquals("OEBPS/ch1.xhtml", EpubParser.resolve("OEBPS/text", "../ch1.xhtml"))
        assertEquals("OEBPS/my file.xhtml", EpubParser.resolve("OEBPS", "my%20file.xhtml"))
    }
}
