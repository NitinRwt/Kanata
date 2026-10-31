package ani.saikou.feed

import ani.saikou.Mapper
import ani.saikou.client
import ani.saikou.defaultHeaders
import com.lagradost.nicehttp.Requests
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Live checks of the Feed sources (needs internet), plus the offline text cleanup */
class FeedRepositoryTest {

    @Before
    fun setup() {
        client = Requests(OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build(), defaultHeaders, defaultCacheTime = 0, responseParser = Mapper)
    }

    @Test
    fun `news merges the feeds newest first`() = runBlocking {
        val news = FeedRepository.news()
        println("news: ${news.size} items from ${news.map { it.source }.distinct()}, ${news.count { it.image != null }} with images")
        news.take(3).forEach { println("  ${it.source} | ${it.title}") }
        assertTrue("no news", news.size >= 20)
        assertTrue("several sources", news.map { it.source }.distinct().size >= 2)
        assertTrue("images", news.any { it.image != null })
        assertTrue("links", news.all { it.link.startsWith("http") })
        assertEquals("newest first", news.map { it.date }, news.map { it.date }.sortedDescending())
    }

    @Test
    fun `upcoming anime are not released yet`() = runBlocking {
        val results = FeedRepository.upcoming(1)!!
        println("upcoming: ${results.results.take(5).map { it.userPreferredName }} statuses: ${results.results.map { it.status }.distinct()}")
        assertTrue(results.results.isNotEmpty())
        // Media keeps AniList's status with spaces (MediaStatus.toString)
        assertTrue(results.results.all { it.status == "NOT YET RELEASED" })
    }

    @Test
    fun `community posts have authors`() = runBlocking {
        val page = FeedRepository.community(1)!!
        val posts = page.activities.orEmpty()
        println("community: ${posts.size} posts, next page: ${page.pageInfo?.hasNextPage}")
        assertTrue(posts.isNotEmpty())
        assertTrue(posts.any { it.user?.name?.isNotBlank() == true })
        // Newest first: without a sort AniList returns posts from 2014
        val newest = posts.filter { it.user != null }.maxOf { it.createdAt }
        assertTrue("posts are recent", System.currentTimeMillis() / 1000 - newest < 7 * 24 * 3600)
    }

    @Test
    fun `profile loads`() = runBlocking {
        val profile = FeedRepository.profile(888157)!!
        println("profile: ${profile.name}, ${profile.statistics?.anime?.count} anime, ${profile.favourites?.anime?.nodes?.size} favourites")
        assertTrue(profile.name.isNotBlank())
        assertTrue((profile.statistics?.anime?.count ?: 0) > 0)
    }

    @Test
    fun `anilist embeds are stripped and spoilers kept`() {
        val text = "Loved it! img220(https://x/y.png)\n\n\n\nyoutube(abc) ~!Frieren wins!~ <b>bold</b> ~~~centre~~~"
        assertEquals("Loved it! \n\n ~!Frieren wins!~ bold centre", FeedText.cleanAniList(text))
    }

    @Test
    fun `list updates read as a sentence`() {
        val post = CommunityPost(status = "watched episode", progress = "1 - 4", media = CommunityPost.ActivityMedia(Profile.Title("Black Clover")))
        assertEquals("watched episode 1 - 4 of **Black Clover**", post.body)
        assertEquals("completed **Frieren**", CommunityPost(status = "completed", media = CommunityPost.ActivityMedia(Profile.Title("Frieren"))).body)
        assertEquals("hello", CommunityPost(text = "hello").body)
    }

    @Test
    fun `headings are flattened and image-only spoilers removed`() {
        val bio = "#Anime's good\n###SOCIALS\n~!\nimg(https://x/badge.png)\n!~"
        assertEquals("Anime's good\nSOCIALS", FeedText.cleanAniList(bio))
    }
}
