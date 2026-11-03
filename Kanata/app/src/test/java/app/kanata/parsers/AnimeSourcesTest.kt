package app.kanata.parsers

import app.kanata.Mapper
import app.kanata.client
import app.kanata.defaultHeaders
import app.kanata.parsers.anime.AniWatch
import app.kanata.parsers.anime.AniWaves
import app.kanata.parsers.anime.AnimePaheNg
import app.kanata.parsers.anime.HiAnime
import com.lagradost.nicehttp.Requests
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Live end-to-end checks of each source against the real site:
 * search -> episodes -> servers -> extracted videos -> the stream itself responds.
 * Needs internet access, run with `gradle testDebugUnitTest`.
 */
class AnimeSourcesTest {

    private val http = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()

    @Before
    fun setup() {
        client = Requests(http, defaultHeaders, defaultCacheTime = 0, responseParser = Mapper)
    }

    @Test fun aniWatch() = check(AniWatch(), "frieren")
    @Test fun hiAnime() = check(HiAnime(), "frieren")
    @Test fun aniWaves() = check(AniWaves(), "frieren")
    @Test fun animePaheNg() = check(AnimePaheNg(), "frieren")

    private fun check(parser: AnimeParser, query: String) = runBlocking {
        val start = System.currentTimeMillis()
        fun log(msg: String) = println("[${parser.name}] +${System.currentTimeMillis() - start}ms $msg")

        val results = parser.search(query)
        log("search '$query': ${results.size} results")
        assertTrue("no search results", results.isNotEmpty())
        val show = results.first()

        val episodes = parser.loadEpisodes(show.link, show.extra)
        log("'${show.name}': ${episodes.size} episodes")
        assertTrue("no episodes", episodes.isNotEmpty())
        val episode = episodes.first()

        val servers = parser.loadVideoServers(episode.link, episode.extra)
        log("episode ${episode.number}: servers ${servers.map { it.name }}")
        assertTrue("no servers", servers.isNotEmpty())

        val working = servers.mapNotNull { server ->
            val extractor = parser.getVideoExtractor(server) ?: return@mapNotNull null.also { log("  ${server.name}: unsupported") }
            val loaded = runCatching { extractor.load() }.onFailure { log("  ${server.name}: FAILED $it") }.getOrNull()
                ?: return@mapNotNull null
            log("  ${server.name}: ${loaded.videos.size} videos, ${loaded.subtitles.size} subtitles")
            loaded.takeIf { it.videos.isNotEmpty() }
        }
        assertTrue("no server returned videos", working.isNotEmpty())

        // The app must be able to actually fetch the stream with the headers we hand it
        val video = working.first().videos.first()
        val request = Request.Builder().url(video.url.url).apply {
            defaultHeaders.forEach { header(it.key, it.value) }
            video.url.headers.forEach { header(it.key, it.value) }
            header("Range", "bytes=0-2047")
        }.build()
        http.newCall(request).execute().use { res ->
            val body = res.body.string()
            log("stream ${video.format} -> HTTP ${res.code}")
            assertTrue("stream returned HTTP ${res.code}", res.isSuccessful)
            if (video.format == VideoType.M3U8) assertTrue("not an HLS playlist", body.startsWith("#EXTM3U"))
        }
        working.first().subtitles.firstOrNull()?.let { sub ->
            val subReq = Request.Builder().url(sub.url.url).apply {
                defaultHeaders.forEach { header(it.key, it.value) }
                video.url.headers.forEach { header(it.key, it.value) }
            }.build()
            http.newCall(subReq).execute().use { log("subtitle '${sub.language}' -> HTTP ${it.code}") }
        }
        log("done")
    }
}
