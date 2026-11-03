package app.kanata.parsers.anime.extractors

import app.kanata.client
import app.kanata.findBetween
import app.kanata.parsers.Video
import app.kanata.parsers.VideoContainer
import app.kanata.parsers.VideoExtractor
import app.kanata.parsers.VideoServer
import app.kanata.parsers.VideoType

class VidStreaming(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer {
        if(server.embed.url.contains("srcd")) {
            val link = client.get(server.embed.url).text.findBetween("\"file\": '", "',")!!
            return VideoContainer(listOf(Video(null, VideoType.M3U8, link, null)))
        }
        val url = client.get(server.embed.url).document.select("iframe").attr("src")
        if(url.contains("filemoon")) {
            return FileMoon(VideoServer("FileMoon", url)).extract()
        }
        return GogoCDN(VideoServer("GogoCDN", url)).extract()
    }
}