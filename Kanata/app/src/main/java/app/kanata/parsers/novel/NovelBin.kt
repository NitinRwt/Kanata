package app.kanata.parsers.novel

import app.kanata.client
import app.kanata.parsers.MangaChapter
import app.kanata.parsers.NovelParser
import app.kanata.parsers.ShowResponse

class NovelBin : NovelParser() {

    override val name = "NovelBin"
    override val saveName = "novel_bin"
    override val hostUrl = "https://novelbin.com"

    // The site turns away the app's default (years-old desktop Chrome) user agent, so name a current browser
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    )

    override suspend fun search(query: String): List<ShowResponse> {
        val doc = client.get("$hostUrl/search?keyword=${encode(query)}", headers).document
        return doc.select("div.list-novel:not(.list-side):not(.list-genre) div.row").mapNotNull { row ->
            val link = row.selectFirst("h3.novel-title a") ?: return@mapNotNull null
            ShowResponse(
                name = link.attr("title").ifEmpty { link.text() },
                link = link.attr("href"),
                coverUrl = row.selectFirst("img.cover")?.attr("src") ?: ""
            )
        }
    }

    override suspend fun loadChapters(mangaLink: String, extra: Map<String, String>?): List<MangaChapter> {
        // The full list comes from this endpoint, keyed by the novel's slug: ".../b/shadow-slave" -> "shadow-slave"
        val slug = mangaLink.trimEnd('/').substringAfterLast('/')
        val doc = client.get("$hostUrl/ajax/chapter-archive?novelId=$slug", headers).document
        // Newer pages keep the items in <template> blocks that a script lays out; older ones in a plain list
        val chapters = doc.select("li[data-chapter-item] > a, ul.list-chapter li a").distinctBy { it.attr("href") }
            .map { it.attr("href") to it.attr("title").ifEmpty { it.text() } }
        return numberChapters(chapters)
    }

    override suspend fun loadText(chapterLink: String): List<String> {
        val doc = client.get(chapterLink, headers).document
        return paragraphs(doc.selectFirst("#chr-content") ?: return listOf())
    }
}
