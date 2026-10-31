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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.ByteString.Companion.decodeBase64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MegaPlay(override val server: VideoServer) : VideoExtractor() {

    override suspend fun extract(): VideoContainer {
        var embedUrl = server.embed.url
        var page = client.get(embedUrl, server.embed.headers, cacheTime = 0).document

        // Some sites wrap the megaplay player in their own page (e.g. 1anime.site/megaplay/...)
        if (page.selectFirst("[data-realid]") == null) {
            val inner = page.selectFirst("iframe[src*=megaplay]")?.attr("src") ?: return VideoContainer(listOf())
            page = client.get(inner, mapOf("referer" to embedUrl), cacheTime = 0).document
            embedUrl = inner
        }

        val host = embedUrl.toHttpUrlOrNull()?.host ?: return VideoContainer(listOf())
        val id = page.selectFirst("[data-realid]")?.attr("data-id") ?: return VideoContainer(listOf())

        val res = client.get(
            "https://$host/stream/getSourcesNew?id=$id",
            mapOf("X-Requested-With" to "XMLHttpRequest", "referer" to embedUrl),
            cacheTime = 0
        ).parsed<SourceResponse>()

        val files = (res.sources?.mapNotNull { it.file } ?: listOf()) +
                (res.enc?.let { fileRegex.findAll(decrypt(it)).map { m -> m.groupValues[1].replace("\\/", "/") }.toList() } ?: listOf())

        val headers = mapOf("referer" to "https://$host/")
        val videos = files.map { Video(null, VideoType.M3U8, FileUrl(it, headers)) }
        val subtitles = res.tracks?.mapNotNull {
            if (it.kind == "captions" && it.file != null) Subtitle(it.label ?: "Unknown", FileUrl(it.file, headers)) else null
        } ?: listOf()

        return VideoContainer(videos, subtitles)
    }

    private val fileRegex = Regex(""""file"\s*:\s*"([^"]+)"""")

    private fun decrypt(enc: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(pad(KEY, 32), "AES"), IvParameterSpec(pad(IV, 16)))
        val bytes = enc.decodeBase64()?.toByteArray() ?: return ""
        return String(cipher.doFinal(bytes))
    }

    private fun pad(text: String, size: Int) = text.toByteArray().copyOf(size)

    @Serializable
    private data class SourceResponse(
        val sources: List<Track>? = null,
        val tracks: List<Track>? = null,
        val enc: String? = null,
    )

    @Serializable
    private data class Track(
        val file: String? = null,
        val label: String? = null,
        val kind: String? = null,
    )

    companion object {
        // Same key megaplay.buzz/lib/app.main.js uses to decrypt its payloads
        private const val KEY = "i?LMTAx0Q6,:}50U"
        private const val IV = "W0;27ToaUpl_P%'c"
    }
}
