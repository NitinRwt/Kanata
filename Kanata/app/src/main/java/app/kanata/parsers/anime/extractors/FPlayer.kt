package app.kanata.parsers.anime.extractors

import app.kanata.asyncMap
import app.kanata.client
import app.kanata.getSize
import app.kanata.parsers.Video
import app.kanata.parsers.VideoContainer
import app.kanata.parsers.VideoExtractor
import app.kanata.parsers.VideoServer
import app.kanata.parsers.VideoType
import app.kanata.tryWithSuspend
import kotlinx.serialization.Serializable

class FPlayer(override val server: VideoServer) : VideoExtractor() {

    override suspend fun extract(): VideoContainer {
        val url = server.embed.url
        val apiLink = url.replace("/v/", "/api/source/")
        return  tryWithSuspend {
            val json = client.post(apiLink, referer = url).parsed<Json>()
            if (json.success) {
                VideoContainer(json.data?.asyncMap {
                    Video(
                        it.label.replace("p", "").toIntOrNull(),
                        VideoType.CONTAINER,
                        it.file,
                        getSize(it.file)
                    )
                }?: listOf())
            } else null
        } ?: VideoContainer(listOf())
    }

    @Serializable
    private data class Data(
        val file: String,
        val label: String
    )

    @Serializable
    private data class Json(
        val success: Boolean,
        val data: List<Data>?
    )
}