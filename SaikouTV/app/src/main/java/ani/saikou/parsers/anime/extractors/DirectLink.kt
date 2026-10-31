package ani.saikou.parsers.anime.extractors

import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoContainer
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.VideoType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A link the user pasted for an episode. A direct video file plays as is; a player page from a host Kamisama
 * supports (MegaPlay, EchoVideo, DoodStream) is read with that host's extractor. Anything else gives no videos.
 */
class DirectLink(override val server: VideoServer) : VideoExtractor() {

    override suspend fun extract(): VideoContainer {
        val url = server.embed.url
        videoType(url)?.let { return VideoContainer(listOf(Video(null, it, server.embed))) }
        val host = url.toHttpUrlOrNull()?.host ?: return VideoContainer(listOf())
        val extractor = when {
            "megaplay" in host || "/megaplay/" in url -> MegaPlay(server)
            "echovideo" in host -> EchoVideo(server)
            "dood" in host || "playmogo" in host -> DoodStream(server)
            else -> return VideoContainer(listOf())
        }
        return extractor.extract()
    }

    companion object {
        fun videoType(url: String): VideoType? {
            val path = url.substringBefore('?').substringBefore('#').lowercase()
            return when {
                ".m3u8" in url.lowercase() -> VideoType.M3U8
                path.endsWith(".mpd") -> VideoType.DASH
                listOf(".mp4", ".mkv", ".webm", ".mov").any { path.endsWith(it) } -> VideoType.CONTAINER
                else -> null
            }
        }
    }
}
