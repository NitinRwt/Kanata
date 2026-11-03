package app.kanata.parsers.anime.extractors

import app.kanata.FileUrl
import app.kanata.client
import app.kanata.getSize
import app.kanata.parsers.Video
import app.kanata.parsers.VideoContainer
import app.kanata.parsers.VideoExtractor
import app.kanata.parsers.VideoServer
import app.kanata.parsers.VideoType

class Mp4Upload(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer {
        val link = client.get(server.embed.url).document
            .select("script").html()
            .substringAfter("src: \"").substringBefore("\"")
        val host = link.substringAfter("https://").substringBefore("/")
        val file = FileUrl(link, mapOf("host" to host))
        return VideoContainer(
            listOf(Video(null, VideoType.CONTAINER, file, getSize(file)))
        )
    }
}