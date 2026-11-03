package app.kanata.parsers.manga

import app.kanata.FileUrl
import app.kanata.client
import app.kanata.parsers.MangaChapter
import app.kanata.parsers.MangaImage
import app.kanata.parsers.MangaParser
import app.kanata.parsers.ShowResponse
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/**
 * Most manhwa and manhua sites are built on the Madara WordPress theme, so one parser reads all of them:
 * a new site only needs its name and link.
 */
open class Madara(
    override val name: String,
    override val saveName: String,
    override val hostUrl: String,
) : MangaParser() {

    override suspend fun search(query: String): List<ShowResponse> {
        val doc = client.get("$hostUrl/?s=${encode(query)}&post_type=wp-manga").document
        return doc.select("div.c-tabs-item__content").mapNotNull { item ->
            val link = item.selectFirst(".post-title a") ?: return@mapNotNull null
            val cover = item.selectFirst(".tab-thumb img")?.let { imageUrl(it) } ?: ""
            ShowResponse(link.text().trim(), link.attr("href"), FileUrl(cover, referer))
        }
    }

    override suspend fun loadChapters(mangaLink: String, extra: Map<String, String>?): List<MangaChapter> {
        val link = mangaLink.trimEnd('/') + "/"
        // Current versions of the theme load the list from this endpoint; older ones print it in the page
        val fromAjax: Elements? = try {
            client.post(
                "${link}ajax/chapters/",
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                referer = link
            ).document.select("li.wp-manga-chapter > a")
        } catch (e: Exception) {
            null
        }
        val items = if (fromAjax.isNullOrEmpty()) client.get(link).document.select("li.wp-manga-chapter > a") else fromAjax
        return items.reversed().map { a ->
            val title = a.text().trim()
            val href = a.attr("href")
            MangaChapter(number = chapterNumber(title, href), link = href, title = title)
        }
    }

    override suspend fun loadImages(chapterLink: String): List<MangaImage> {
        val doc = client.get(chapterLink).document
        return doc.select("div.reading-content img").mapNotNull { img ->
            val url = imageUrl(img)
            if (url.startsWith("http")) MangaImage(FileUrl(url, referer)) else null
        }
    }

    private val referer get() = mapOf("referer" to "$hostUrl/")

    /** Lazy-loading plugins move the real address into one of several attributes */
    private fun imageUrl(img: Element): String =
        listOf("data-src", "data-lazy-src", "data-cfsrc", "src")
            .map { img.attr(it).trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("data:") } ?: ""

    /** "Chapter 68.5" or ".../chapter-68-5/" become "68.5"; anything without a number keeps its title */
    private fun chapterNumber(title: String, link: String): String {
        Regex("""(?i)chapter\s*(\d+(?:\.\d+)?)""").find(title)?.let { return it.groupValues[1] }
        Regex("""chapter-(\d+)(?:[-.](\d+))?""").find(link)?.let {
            val (whole, part) = it.destructured
            return if (part.isEmpty()) whole else "$whole.$part"
        }
        return title
    }
}

class ManhuaPlus : Madara("ManhuaPlus", "manhuaplus", "https://manhuaplus.com")
