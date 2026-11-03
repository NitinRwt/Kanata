package app.kanata.parsers.anime

import app.kanata.FileUrl
import app.kanata.client
import app.kanata.parsers.AnimeParser
import app.kanata.parsers.Episode
import app.kanata.parsers.ShowResponse
import app.kanata.parsers.VideoExtractor
import app.kanata.parsers.VideoServer
import app.kanata.parsers.anime.extractors.MegaPlay
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.ByteString.Companion.decodeBase64
import org.jsoup.Jsoup

// Same WordPress theme as hianime.ro, but the servers are only listed on the episode page
// The link, name and save key are parameters so the same parser also serves user-added mirrors of this site type
class AniWatch(
    override val hostUrl: String = "https://aniwatch.co.at",
    override val name: String = "AniWatch",
    override val saveName: String = "aniwatch_co_at",
) : AnimeParser() {

    override val isDubAvailableSeparately = false

    private val api = "$hostUrl/wp-json/hianime/v1"
    private val embedHeaders = mapOf("referer" to "$hostUrl/")

    override suspend fun search(query: String): List<ShowResponse> {
        return client.get("$hostUrl/?s=${encode(query)}").document.select(".flw-item").mapNotNull {
            val a = it.selectFirst("a.film-poster-ahref") ?: return@mapNotNull null
            val cover = it.selectFirst("img.film-poster-img")?.attr("data-src") ?: ""
            ShowResponse(a.attr("title"), a.attr("data-id"), FileUrl(cover))
        }
    }

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?): List<Episode> {
        val res = client.get("$api/episode/list/$animeLink").parsed<HtmlResponse>()
        return Jsoup.parse(res.html ?: return listOf()).select("a.ep-item").map {
            Episode(number = it.attr("data-number"), link = it.attr("href"), title = it.attr("title"))
        }
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Any?): List<VideoServer> {
        return client.get(episodeLink, cacheTime = 0).document.select("div.server-item").mapNotNull {
            val url = it.attr("data-hash").decodeBase64()?.utf8() ?: return@mapNotNull null
            VideoServer("${it.attr("data-type").uppercase()} - ${it.attr("data-server-name")}", FileUrl(url, embedHeaders))
        }
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? {
        val url = server.embed.url.toHttpUrlOrNull() ?: return null
        return when {
            "megaplay" in url.host || url.encodedPath.startsWith("/megaplay/") -> MegaPlay(server)
            else                                                               -> null
        }
    }

    @Serializable
    private data class HtmlResponse(val status: Boolean? = null, val html: String? = null)
}
