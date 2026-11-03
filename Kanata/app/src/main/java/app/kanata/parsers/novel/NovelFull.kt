package app.kanata.parsers.novel

import app.kanata.client
import app.kanata.parsers.MangaChapter
import app.kanata.parsers.NovelParser
import app.kanata.parsers.ShowResponse

class NovelFull : NovelParser() {

    override val name = "NovelFull"
    override val saveName = "novel_full"
    override val hostUrl = "https://novelfull.com"

    override suspend fun search(query: String): List<ShowResponse> {
        val doc = client.get("$hostUrl/search?keyword=${encode(query)}").document
        return doc.select("div.list-truyen:not(.list-side):not(.list-cat) div.row").mapNotNull { row ->
            val link = row.selectFirst("h3.truyen-title a") ?: return@mapNotNull null
            ShowResponse(
                name = link.attr("title").ifEmpty { link.text() },
                link = absolute(link.attr("href")),
                coverUrl = row.selectFirst("img.cover")?.attr("src")?.let { absolute(it) } ?: ""
            )
        }
    }

    override suspend fun loadChapters(mangaLink: String, extra: Map<String, String>?): List<MangaChapter> {
        // The novel page is paged 50 chapters at a time; this endpoint lists all of them by the page's numeric id
        val id = client.get(mangaLink).document.selectFirst("[data-novel-id]")?.attr("data-novel-id") ?: return listOf()
        val doc = client.get("$hostUrl/ajax-chapter-option?novelId=$id").document
        val chapters = doc.select("option").map { absolute(it.attr("value")) to it.text() }
        return numberChapters(chapters)
    }

    override suspend fun loadText(chapterLink: String): List<String> {
        val doc = client.get(chapterLink).document
        val text = paragraphs(doc.selectFirst("#chapter-content") ?: return listOf())
        // The site repeats the chapter heading as the first paragraph; the reader shows its own
        val heading = doc.selectFirst(".chapter-title, a.chapter-title")?.text()?.trim()
        return if (heading != null && text.firstOrNull() == heading) text.drop(1) else text
    }

    private fun absolute(path: String) = if (path.startsWith("http")) path else hostUrl + path
}
