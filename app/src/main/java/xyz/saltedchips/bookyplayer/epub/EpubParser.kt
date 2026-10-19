package xyz.saltedchips.bookyplayer.epub

import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

data class EpubChapter(val title: String, val text: String)

class EpubBook(
    val title: String,
    val author: String,
    val language: String?,
    val chapters: List<EpubChapter>,
    val coverBytes: ByteArray?,
    val coverExtension: String?,
)

/** The ePub is copy-protected; Booky does not try to get around that. */
class EpubDrmException : Exception("This ePub is DRM-protected and can't be imported.")

class EpubParseException(message: String) : Exception(message)

/** Reads an ePub (2 or 3) into plain-text chapters ready for text-to-speech. */
object EpubParser {
    private const val MIN_CHAPTER_CHARS = 40
    private val FRONT_MATTER = Regex(
        "^(cover|title page|copyright|copyright page|contents|table of contents|dedication|" +
            "also by .*|about the author|acknowledg(e)?ments?)$",
        RegexOption.IGNORE_CASE,
    )
    private val FONT_ALGORITHMS = setOf(
        "http://www.idpf.org/2008/embedding",
        "http://ns.adobe.com/pdf/enc#RC",
    )
    private const val BLOCKS = "h1,h2,h3,h4,h5,h6,p,li,blockquote,pre"

    fun parse(file: File): EpubBook = ZipFile(file).use { zip ->
        fun bytes(path: String): ByteArray? = zip.getEntry(path)?.let { entry ->
            zip.getInputStream(entry).use { it.readBytes() }
        }
        fun xml(path: String): Document? =
            bytes(path)?.let { Jsoup.parse(it.inputStream(), "UTF-8", "", Parser.xmlParser()) }
        fun html(path: String): Document? =
            bytes(path)?.let { Jsoup.parse(it.inputStream(), "UTF-8", "") }

        checkDrm(zip.getEntry("META-INF/rights.xml") != null, xml("META-INF/encryption.xml"))

        val container = xml("META-INF/container.xml")
            ?: throw EpubParseException("Not a valid ePub (missing container.xml)")
        val opfPath = container.selectFirst("rootfile[full-path]")?.attr("full-path")
            ?: throw EpubParseException("Not a valid ePub (no package document)")
        val opf = xml(opfPath) ?: throw EpubParseException("Package document is missing")
        val opfDir = opfPath.substringBeforeLast('/', "")

        val title = opf.getElementsByTag("dc:title").firstOrNull()?.text()?.trim()
            ?.takeIf { it.isNotEmpty() } ?: file.nameWithoutExtension
        val author = opf.getElementsByTag("dc:creator").firstOrNull()?.text()?.trim().orEmpty()
        val language = opf.getElementsByTag("dc:language").firstOrNull()?.text()?.trim()

        class Item(val id: String, val path: String, val mediaType: String, val properties: String)
        val items = opf.select("manifest > item").associate { el ->
            val id = el.attr("id")
            id to Item(
                id,
                resolve(opfDir, el.attr("href")),
                el.attr("media-type"),
                el.attr("properties"),
            )
        }

        val coverItem = items.values.firstOrNull { "cover-image" in it.properties.split(' ') }
            ?: opf.select("metadata > meta[name=cover]").firstOrNull()
                ?.attr("content")?.let { items[it] }
        val coverBytes = coverItem?.let { bytes(it.path) }
        val coverExtension = coverItem?.path?.substringAfterLast('.', "")?.lowercase()
            ?.takeIf { it.isNotEmpty() }

        val tocTitles = LinkedHashMap<String, String>()
        val navItem = items.values.firstOrNull { "nav" in it.properties.split(' ') }
        val ncxItem = items.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
            ?: opf.selectFirst("spine[toc]")?.attr("toc")?.let { items[it] }
        if (navItem != null) {
            html(navItem.path)?.let { doc ->
                val nav = doc.select("nav").firstOrNull { it.attr("epub:type").contains("toc") }
                    ?: doc.selectFirst("nav")
                val navDir = navItem.path.substringBeforeLast('/', "")
                nav?.select("a[href]")?.forEach { a ->
                    val target = resolve(navDir, a.attr("href"))
                    val label = a.text().trim()
                    if (label.isNotEmpty()) tocTitles.putIfAbsent(target, label)
                }
            }
        }
        if (tocTitles.isEmpty() && ncxItem != null) {
            xml(ncxItem.path)?.let { doc ->
                val ncxDir = ncxItem.path.substringBeforeLast('/', "")
                doc.select("navPoint").forEach { point ->
                    val label = point.selectFirst("navLabel > text")?.text()?.trim().orEmpty()
                    val src = point.selectFirst("content[src]")?.attr("src").orEmpty()
                    if (label.isNotEmpty() && src.isNotEmpty()) {
                        tocTitles.putIfAbsent(resolve(ncxDir, src), label)
                    }
                }
            }
        }

        val chapters = mutableListOf<EpubChapter>()
        opf.select("spine > itemref").forEach { ref ->
            if (ref.attr("linear").equals("no", ignoreCase = true)) return@forEach
            val item = items[ref.attr("idref")] ?: return@forEach
            if (!item.mediaType.contains("html")) return@forEach
            val doc = html(item.path) ?: return@forEach
            val text = extractText(doc)
            val tocTitle = tocTitles[item.path]
            val heading = doc.selectFirst("h1,h2,h3")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            val chapterTitle = tocTitle ?: heading
            if (chapterTitle != null && FRONT_MATTER.matches(chapterTitle)) return@forEach
            if (text.length < MIN_CHAPTER_CHARS) return@forEach
            chapters += EpubChapter(chapterTitle ?: "Chapter ${chapters.size + 1}", text)
        }
        if (chapters.isEmpty()) throw EpubParseException("No readable text found in this ePub")
        EpubBook(title, author, language, chapters, coverBytes, coverExtension)
    }

    private fun checkDrm(hasRights: Boolean, encryption: Document?) {
        if (hasRights) throw EpubDrmException()
        val algorithms = encryption?.select("EncryptionMethod")?.map { it.attr("Algorithm") }.orEmpty()
        if (algorithms.any { it !in FONT_ALGORITHMS }) throw EpubDrmException()
    }

    internal fun extractText(doc: Document): String {
        doc.select("script,style,nav,head").remove()
        doc.select("[epub:type~=noteref|footnote|endnote]").remove()
        doc.select("sup:has(a)").remove()
        val blocks = doc.select(BLOCKS).filter { el -> el.select(BLOCKS).size == 1 }
        val paragraphs = blocks.map { cleanup(it.text()) }.filter { it.isNotEmpty() }
        val joined = if (paragraphs.isNotEmpty()) {
            paragraphs.joinToString("\n\n")
        } else {
            cleanup(doc.body()?.text().orEmpty())
        }
        return joined
    }

    private fun cleanup(raw: String): String = raw
        .replace("\u00AD", "")
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Resolves [href] against the folder [baseDir] inside the zip, dropping any #fragment. */
    internal fun resolve(baseDir: String, href: String): String {
        val noFragment = href.substringBefore('#')
        val decoded = URLDecoder.decode(noFragment.replace("+", "%2B"), "UTF-8")
        val parts = ArrayDeque<String>()
        val combined = if (decoded.startsWith("/")) decoded.removePrefix("/") else {
            if (baseDir.isEmpty()) decoded else "$baseDir/$decoded"
        }
        for (segment in combined.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> parts.removeLastOrNull()
                else -> parts.addLast(segment)
            }
        }
        return parts.joinToString("/")
    }
}
