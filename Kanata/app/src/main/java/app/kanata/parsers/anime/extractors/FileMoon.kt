package app.kanata.parsers.anime.extractors

import app.kanata.FileUrl
import app.kanata.client
import app.kanata.getSize
import app.kanata.others.JsUnpacker
import app.kanata.parsers.*

private val packedRegex = Regex("""eval\(function\(p,a,c,k,e,.*\)\)""")
private fun getPacked(string: String): String? {
    return packedRegex.find(string)?.value
}

private fun getAndUnpack(string: String): String {
    val packedText = getPacked(string)
    return JsUnpacker(packedText).unpack() ?: string
}
class FileMoon(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer {
        val page = client.get(server.embed.url)
        val unpacked = getAndUnpack(page.text)
        val link = Regex("file:\"(.+?)\"").find(unpacked)?.groupValues?.last()

        link?.let {
            return VideoContainer(
                listOf(
                    Video(
                        null,
                        VideoType.M3U8,
                        FileUrl(link, mapOf("Referer" to server.embed.url)),
                        getSize(link)
                    )
                )
            )
        }
        return VideoContainer(listOf())

    }

}