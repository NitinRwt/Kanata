package ani.saikou.parsers.anime.extractors

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.Subtitle
import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoContainer
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.VideoType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class EchoVideo(override val server: VideoServer) : VideoExtractor() {

    override suspend fun extract(): VideoContainer {
        val url = server.embed.url.toHttpUrlOrNull() ?: return VideoContainer(listOf())
        val token = url.pathSegments.lastOrNull() ?: return VideoContainer(listOf())
        val path = url.encodedPath.substringBeforeLast("/")

        val res = client.get(
            "https://${url.host}$path/getSources?id=$token",
            mapOf("referer" to server.embed.url, "Accept" to "application/json"),
            cacheTime = 0
        ).parsed<SourceResponse>()

        val headers = mapOf("referer" to "https://${url.host}/")
        val videos = files(res.sources).map { Video(null, VideoType.M3U8, FileUrl(it, headers)) }
        val subtitles = res.tracks?.mapNotNull {
            if (it.kind != "thumbnails" && it.file != null) Subtitle(it.label ?: "Unknown", FileUrl(it.file, headers)) else null
        } ?: listOf()
        return VideoContainer(videos, subtitles)
    }

    // "sources" is either a plain url or a list of {file: url}
    private fun files(sources: JsonElement?): List<String> = when (sources) {
        is JsonPrimitive -> listOfNotNull(sources.contentOrNull)
        is JsonArray     -> sources.flatMap { files(it) }
        is JsonObject    -> listOfNotNull(sources["file"]?.jsonPrimitive?.contentOrNull)
        else             -> listOf()
    }

    @Serializable
    private data class SourceResponse(
        val sources: JsonElement? = null,
        val tracks: List<Track>? = null,
    )

    @Serializable
    private data class Track(
        val file: String? = null,
        val label: String? = null,
        val kind: String? = null,
    )
}
