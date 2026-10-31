package ani.saikou.parsers.anime

import ani.saikou.FileUrl
import ani.saikou.asyncMapNotNull
import ani.saikou.client
import ani.saikou.parsers.AnimeParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.anime.extractors.DoodStream
import ani.saikou.parsers.anime.extractors.EchoVideo
import ani.saikou.parsers.anime.extractors.MegaPlay
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

// The link, name and save key are parameters so the same parser also serves user-added mirrors of this site type
class AniWaves(
    override val hostUrl: String = "https://aniwaves.ru",
    override val name: String = "AniWaves",
    override val saveName: String = "aniwaves_ru",
) : AnimeParser() {

    override val isDubAvailableSeparately = false

    private val header = mapOf("X-Requested-With" to "XMLHttpRequest", "referer" to "$hostUrl/")

    override suspend fun search(query: String): List<ShowResponse> {
        return client.get("$hostUrl/filter?keyword=${encode(query)}").document
            .select("#list-items > .item").mapNotNull {
                val a = it.selectFirst(".ani.poster > a") ?: return@mapNotNull null
                val title = it.selectFirst("a.name")?.text() ?: a.select("img").attr("alt")
                ShowResponse(title, hostUrl + a.attr("href"), a.select("img").attr("src"))
            }
    }

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?): List<Episode> {
        val animeId = animeLink.trimEnd('/').substringAfterLast("-")
        val res = client.get("$hostUrl/ajax/episode/list/$animeId", header).parsed<Response>()
        return Jsoup.parse(res.result ?: return listOf()).select("ul.ep-range li a").map {
            Episode(
                number = it.attr("data-num"),
                link = it.attr("data-ids"),
                title = it.selectFirst("span.d-title")?.text(),
                isFiller = it.attr("data-filler") == "1"
            )
        }
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Any?): List<VideoServer> {
        val res = client.get("$hostUrl/ajax/server/list?servers=$episodeLink", header, cacheTime = 0).parsed<Response>()
        val servers = Jsoup.parse(res.result ?: return listOf()).select("div.type").flatMap { type ->
            type.select("li").map { "${type.attr("data-type").uppercase()} - ${it.text()}" to it.attr("data-link-id") }
        }
        return servers.asyncMapNotNull { (name, linkId) ->
            runCatching {
                val url = client.get("$hostUrl/ajax/sources?id=$linkId", header, cacheTime = 0).parsed<Sources>().result?.url
                    ?: return@runCatching null
                VideoServer(name, FileUrl(url.substringBefore("?"), mapOf("referer" to "$hostUrl/")))
            }.getOrNull()
        }
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? {
        val domain = server.embed.url.toHttpUrlOrNull()?.host ?: return null
        return when {
            "echovideo" in domain -> EchoVideo(server)
            "megaplay" in domain  -> MegaPlay(server)
            "playmogo" in domain  -> DoodStream(server)
            "dood" in domain      -> DoodStream(server)
            else                  -> null
        }
    }

    @Serializable
    private data class Response(val status: Int? = null, val result: String? = null)

    @Serializable
    private data class Sources(val result: Result? = null) {
        @Serializable
        data class Result(val url: String? = null)
    }
}
