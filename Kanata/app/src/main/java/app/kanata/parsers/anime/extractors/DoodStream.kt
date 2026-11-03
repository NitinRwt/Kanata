package app.kanata.parsers.anime.extractors

import android.net.Uri
import app.kanata.FileUrl
import app.kanata.client
import app.kanata.findBetween
import app.kanata.getSize
import app.kanata.parsers.Video
import app.kanata.parsers.VideoContainer
import app.kanata.parsers.VideoExtractor
import app.kanata.parsers.VideoServer
import app.kanata.parsers.VideoType
import java.util.*

class DoodStream(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer {
        val domain = "https://" + Uri.parse(server.embed.url).host!!
        val res = client.get(server.embed.url).text
        val hash = res.findBetween("/pass_md5/", "'")!!
        val token = res.findBetween("token=", "&")!!
        val url = client.get("$domain/pass_md5/$hash", referer = domain).text
        val link = FileUrl("$url?token=$token&expiry=${Date().time}}", mapOf("referer" to domain))
        return VideoContainer(
            listOf(Video(null, VideoType.CONTAINER, link, getSize(link)))
        )
    }
}