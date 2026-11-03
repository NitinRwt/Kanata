package app.kanata.parsers.anime

import app.kanata.FileUrl
import app.kanata.client
import app.kanata.parsers.AnimeParser
import app.kanata.parsers.Episode
import app.kanata.parsers.ShowResponse
import app.kanata.parsers.VideoExtractor
import app.kanata.parsers.VideoServer
import app.kanata.parsers.anime.extractors.DoodStream
import app.kanata.parsers.anime.extractors.MegaPlay
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.ByteString.Companion.decodeBase64
import org.jsoup.Jsoup

// animepahe.ng is a WordPress (AnimeStream theme) site, unrelated to the animepahe.si api
// The link, name and save key are parameters so the same parser also serves user-added mirrors of this site type
class AnimePaheNg(
    override val hostUrl: String = "https://animepahe.ng",
    override val name: String = "AnimePahe.ng",
    override val saveName: String = "animepahe_ng",
) : AnimeParser() {

    override val isDubAvailableSeparately = false

    private val embedHeaders = mapOf("referer" to "$hostUrl/")

    override suspend fun search(query: String): List<ShowResponse> {
        return client.get("$hostUrl/?s=${encode(query)}").document.select("div.bsx > a").map {
            ShowResponse(it.attr("title"), it.attr("href"), it.select("img").attr("src"))
        }
    }

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?): List<Episode> {
        return client.get(animeLink).document.select("div.eplister ul li a").map {
            Episode(
                number = it.selectFirst(".epl-num")?.text() ?: "",
                link = it.attr("href"),
                title = it.selectFirst(".epl-title")?.text()
            )
        }.reversed()
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Any?): List<VideoServer> {
        val document = client.get(episodeLink, cacheTime = 0).document
        val main = document.select("#pembed iframe").map { it.attr("src") }
        // Extra mirrors are base64 encoded iframes
        val mirrors = document.select("select.mirror option[value]").mapNotNull {
            val html = it.attr("value").decodeBase64()?.utf8() ?: return@mapNotNull null
            Jsoup.parse(html).selectFirst("iframe")?.attr("src")
        }
        return (main + mirrors).filter { it.isNotBlank() }.distinct().map {
            val url = if (it.startsWith("//")) "https:$it" else it
            VideoServer(url.toHttpUrlOrNull()?.host ?: name, FileUrl(url, embedHeaders))
        }
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? {
        val domain = server.embed.url.toHttpUrlOrNull()?.host ?: return null
        return when {
            "megaplay" in domain -> MegaPlay(server)
            "dood" in domain     -> DoodStream(server)
            else                 -> null
        }
    }
}
