package app.kanata.parsers

import app.kanata.Mapper
import app.kanata.client
import app.kanata.defaultHeaders
import app.kanata.parsers.anime.extractors.DirectLink
import com.lagradost.nicehttp.Requests
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class SourceRegistryTest {

    @Before
    fun setup() {
        client = Requests(OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build(), defaultHeaders, defaultCacheTime = 0, responseParser = Mapper)
    }

    @Test
    fun `links are reduced to the site origin`() {
        assertEquals("https://hianime.to", SourceRegistry.normalize("hianime.to"))
        assertEquals("https://hianime.to", SourceRegistry.normalize("  https://hianime.to/home?x=1  "))
        assertEquals("https://www.site.cc", SourceRegistry.normalize("https://www.site.cc/"))
        assertEquals("http://site.cc:8080", SourceRegistry.normalize("http://site.cc:8080/a"))
        assertNull(SourceRegistry.normalize(""))
        assertNull(SourceRegistry.normalize("not a link"))
    }

    @Test
    fun `each built-in site is detected as its own type`() = runBlocking {
        for (source in SourceRegistry.builtIns) {
            val detected = SourceRegistry.detect(source.defaultHost)
            println("${source.defaultHost} -> $detected")
            assertEquals(source.defaultHost, source.type, detected)
        }
    }

    @Test
    fun `a site added by link works through its type`() = runBlocking {
        // As a user would add it: the type's parser pointed at a link, under a custom name
        val result = SourceRegistry.test(SiteType.ANIWATCH.create("https://aniwatch.co.at", "My mirror", "custom_test"))
        println(result)
        assertTrue(result.message, result.ok)
    }

    @Test
    fun `the wrong type for a site fails with a message instead of crashing`() = runBlocking {
        val result = SourceRegistry.test(SiteType.ANIWAVE.create("https://animepahe.ng", "Wrong", "custom_wrong"))
        println(result)
        assertFalse(result.ok)
        assertTrue(result.message.isNotBlank())
    }

    @Test
    fun `direct video links are recognised`() {
        assertEquals(app.kanata.parsers.VideoType.M3U8, DirectLink.videoType("https://cdn.x/a/master.m3u8?token=1"))
        assertEquals(app.kanata.parsers.VideoType.CONTAINER, DirectLink.videoType("https://cdn.x/ep1.MP4"))
        assertEquals(app.kanata.parsers.VideoType.DASH, DirectLink.videoType("https://cdn.x/stream.mpd"))
        assertNull(DirectLink.videoType("https://megaplay.buzz/stream/s-2/1/sub"))
    }
}
