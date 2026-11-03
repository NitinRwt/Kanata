package app.kanata.parsers.anime

import android.net.Uri
import app.kanata.client
import app.kanata.parsers.AnimeParser
import app.kanata.parsers.Episode
import app.kanata.parsers.ShowResponse
import app.kanata.parsers.VideoExtractor
import app.kanata.parsers.VideoServer
import app.kanata.parsers.anime.extractors.DoodStream
import app.kanata.parsers.anime.extractors.GogoCDN
import app.kanata.parsers.anime.extractors.Mp4Upload
import app.kanata.parsers.anime.extractors.StreamSB
import app.kanata.parsers.anime.extractors.StreamTape

class AnimeDao : AnimeParser() {
    override val name = "AnimeDao"
    override val saveName = "anime_dao_bz"
    override val hostUrl = "https://animedao.bz"
    override val isDubAvailableSeparately = true

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?): List<Episode> {
        val res = client.get(animeLink).document
        return res.select(".episode_well_link").map {
            Episode(
                it.select(".anime-title").text().substringAfter("Episode "),
                hostUrl + it.attr("href")
            )
        }.reversed()
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Any?): List<VideoServer> {
        return client.get(episodeLink)
            .document
            .select(".anime_muti_link a")
            .map {
                VideoServer(it.text(), it.attr("data-video"))
            }
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? {
        val domain = Uri.parse(server.embed.url).host ?: ""
        val extractor: VideoExtractor? = when {
            "streamta" in domain -> StreamTape(server)
            "taku" in domain    -> GogoCDN(server)
            "sb" in domain      -> StreamSB(server)
            "dood" in domain    -> DoodStream(server)
            "mp4" in domain     -> Mp4Upload(server)
            else                 -> null
        }
        return extractor
    }

    override suspend fun search(query: String): List<ShowResponse> {
        return client.get("$hostUrl/search.html?keyword=$query${if(selectDub) " (Dub)" else ""}").document
            .select(".col-lg-4 a").map {
                ShowResponse(
                    it.attr("title"),
                    hostUrl + it.attr("href"),
                    it.select("img").attr("src")
                )
            }
    }
}