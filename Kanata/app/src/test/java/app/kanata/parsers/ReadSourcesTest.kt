package app.kanata.parsers

import app.kanata.Mapper
import app.kanata.client
import app.kanata.defaultHeaders
import app.kanata.parsers.manga.ManhuaPlus
import app.kanata.parsers.manga.MangaRead
import app.kanata.parsers.manga.Webtoons
import app.kanata.parsers.novel.NovelBin
import app.kanata.parsers.novel.NovelFull
import com.lagradost.nicehttp.Requests
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Live end-to-end checks of the comic and novel sources against the real sites:
 * search -> chapters -> pages (or text) of the first chapter.
 * Needs internet access, run with `gradle testDebugUnitTest`.
 */
class ReadSourcesTest {

    private val http = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()

    @Before
    fun setup() {
        client = Requests(http, defaultHeaders, defaultCacheTime = 0, responseParser = Mapper)
    }

    @Test fun webtoons() = checkComic(Webtoons(), "omniscient reader")
    @Test fun manhuaPlus() = checkComic(ManhuaPlus(), "martial peak")
    @Test fun mangaRead() = checkComic(MangaRead(), "solo leveling")
    @Test fun novelBin() = checkNovel(NovelBin(), "shadow slave")
    @Test fun novelFull() = checkNovel(NovelFull(), "shadow slave")

    private fun checkComic(parser: MangaParser, query: String) = runBlocking {
        val chapter = firstChapter(parser, query)
        val images = parser.loadImages(chapter.link)
        println("[${parser.name}] chapter ${chapter.number}: ${images.size} pages, first ${images.firstOrNull()?.url?.url}")
        assertTrue("no pages", images.isNotEmpty())
        assertTrue("page isn't a link", images.all { it.url.url.startsWith("http") })
    }

    private fun checkNovel(parser: NovelParser, query: String) = runBlocking {
        val chapter = firstChapter(parser, query)
        val text = parser.loadText(chapter.link)
        println("[${parser.name}] chapter ${chapter.number}: ${text.size} paragraphs, starts \"${text.firstOrNull()?.take(60)}\"")
        assertTrue("no text", text.size > 5)
    }

    private suspend fun firstChapter(parser: MangaParser, query: String): MangaChapter {
        val results = parser.search(query)
        println("[${parser.name}] search '$query': ${results.size} results, first '${results.firstOrNull()?.name}'")
        assertTrue("no search results", results.isNotEmpty())
        val show = results.first()
        val chapters = parser.loadChapters(show.link, show.extra)
        println("[${parser.name}] '${show.name}': ${chapters.size} chapters, first '${chapters.firstOrNull()?.title}' (${chapters.firstOrNull()?.number}), last ${chapters.lastOrNull()?.number}")
        assertTrue("no chapters", chapters.isNotEmpty())
        assertTrue("chapter numbers repeat", chapters.map { it.number }.toSet().size == chapters.size || parser !is NovelParser)
        return chapters.first()
    }
}
