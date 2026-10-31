package ani.saikou.parsers.anime

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.AnimeParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoContainer
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.VideoType
import ani.saikou.parsers.anime.extractors.MegaPlay
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.ByteString.Companion.decodeBase64
import org.jsoup.Jsoup

// The link, name and save key are parameters so the same parser also serves user-added mirrors of this site type
class HiAnime(
    override val hostUrl: String = "https://hianime.ro",
    override val name: String = "HiAnime",
    override val saveName: String = "hianime_ro",
) : AnimeParser() {

    override val isDubAvailableSeparately = false

    private val api = "$hostUrl/wp-json/v1/otakuthemes"
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
            Episode(number = it.attr("data-number"), link = it.attr("data-id"), title = it.attr("title"))
        }
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Any?): List<VideoServer> {
        val res = client.get("$api/episode/servers?episodeId=$episodeLink", cacheTime = 0).parsed<HtmlResponse>()
        return Jsoup.parse(res.html ?: return listOf()).select("div.server-item").mapNotNull {
            val url = it.attr("data-hash").decodeBase64()?.utf8() ?: return@mapNotNull null
            VideoServer("${it.attr("data-type").uppercase()} - ${it.attr("data-server-name")}", FileUrl(url, embedHeaders))
        }
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? {
        val url = server.embed.url.toHttpUrlOrNull() ?: return null
        return when {
            "megaplay" in url.host                                                -> MegaPlay(server)
            "hianime" in url.host && url.queryParameter("source") == "blogger" -> BloggerExtractor(server)
            else                                                                  -> null
        }
    }

    // The "Fast" server: hianime's own player page listing direct blogger (googlevideo) mp4s
    class BloggerExtractor(override val server: VideoServer) : VideoExtractor() {

        private val sourceRegex = Regex("""\{"file":"([^"]+)"[^}]*\}""")
        private val labelRegex = Regex(""""label":"(\d+)p"""")

        override suspend fun extract(): VideoContainer {
            val text = client.get(server.embed.url, server.embed.headers, cacheTime = 0).text
            val videos = sourceRegex.findAll(text).map {
                val url = it.groupValues[1].replace("\\/", "/").replace("\\u0026", "&")
                val quality = labelRegex.find(it.value)?.groupValues?.get(1)?.toIntOrNull()
                Video(quality, VideoType.CONTAINER, url)
            }.toList()
            return VideoContainer(videos)
        }
    }

    @Serializable
    private data class HtmlResponse(val status: Boolean? = null, val html: String? = null)
}
