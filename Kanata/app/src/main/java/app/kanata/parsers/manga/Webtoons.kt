package app.kanata.parsers.manga

import app.kanata.FileUrl
import app.kanata.client
import app.kanata.parsers.MangaChapter
import app.kanata.parsers.MangaImage
import app.kanata.parsers.MangaParser
import app.kanata.parsers.ShowResponse
import kotlinx.serialization.Serializable

/**
 * The official WEBTOON site: free episodes of its Originals, plus Canvas (fan-made) series. Episodes it keeps
 * behind its paid "Daily Pass" aren't listed by the site, so they don't show up here either.
 */
class Webtoons : MangaParser() {

    override val name = "Webtoons"
    override val saveName = "webtoons"
    override val hostUrl = "https://www.webtoons.com"
    override val isWebtoon = true

    // Its image server refuses requests that don't come from the site
    private val referer = mapOf("referer" to "$hostUrl/")

    override suspend fun search(query: String): List<ShowResponse> {
        val doc = client.get("$hostUrl/en/search?keyword=${encode(query)}").document
        return doc.select("a._card_item").mapNotNull { card ->
            val id = card.attr("data-title-no").ifEmpty { return@mapNotNull null }
            ShowResponse(
                name = card.selectFirst(".title")?.text() ?: return@mapNotNull null,
                link = card.attr("href"),
                coverUrl = FileUrl(card.selectFirst("img")?.attr("src") ?: "", referer),
                extra = mapOf("id" to id, "canvas" to (card.attr("data-webtoon-type") != "WEBTOON").toString())
            )
        }
    }

    override suspend fun loadChapters(mangaLink: String, extra: Map<String, String>?): List<MangaChapter> {
        val id = extra?.get("id") ?: Regex("""title_no=(\d+)""").find(mangaLink)?.groupValues?.get(1) ?: return listOf()
        val canvas = extra?.get("canvas")?.toBoolean() ?: mangaLink.contains("/canvas/")
        val kind = if (canvas) "canvas" else "webtoon"
        val episodes = client.get("https://m.webtoons.com/api/v1/$kind/$id/episodes?pageSize=99999")
            .parsed<EpisodesResponse>().result.episodeList
        // episodeNo is the site's running count, so it stays unique even when titles repeat or lack numbers
        return episodes.map {
            MangaChapter(number = it.episodeNo.toString(), link = hostUrl + it.viewerLink, title = it.episodeTitle)
        }
    }

    override suspend fun loadImages(chapterLink: String): List<MangaImage> {
        val doc = client.get(chapterLink).document
        return doc.select("img._images").mapNotNull { img ->
            img.attr("data-url").ifEmpty { null }?.let { MangaImage(FileUrl(it, referer)) }
        }
    }

    @Serializable
    data class EpisodesResponse(val result: Result) {
        @Serializable
        data class Result(val episodeList: List<Episode> = listOf())

        @Serializable
        data class Episode(val episodeNo: Int, val episodeTitle: String, val viewerLink: String)
    }
}
