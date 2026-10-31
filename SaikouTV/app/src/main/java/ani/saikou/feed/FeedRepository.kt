package ani.saikou.feed

import ani.saikou.anilist.SearchResults
import ani.saikou.anilist.executeQuery
import ani.saikou.client
import ani.saikou.anilist.Anilist
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.text.SimpleDateFormat
import java.util.Locale

data class NewsItem(
    val title: String,
    val link: String,
    val description: String,
    val image: String?,
    val source: String,
    val date: Long,
)

/**
 * Data for the Feed tab: anime news from official RSS feeds, upcoming anime and the community from AniList.
 * Everything here works without logging in.
 */
object FeedRepository {

    private val newsFeeds = listOf(
        "Crunchyroll" to "https://cr-news-api-service.prd.crunchyrollsvc.com/v1/en-US/rss",
        "MyAnimeList" to "https://myanimelist.net/rss/news.xml",
        "Anime News Network" to "https://www.animenewsnetwork.com/all/rss.xml",
    )
    private const val PER_FEED = 30

    /** All news feeds merged newest-first. A feed that fails is skipped rather than failing the whole list */
    suspend fun news(): List<NewsItem> = coroutineScope {
        newsFeeds.map { (source, url) -> async { runCatching { readFeed(source, url) }.getOrDefault(emptyList()) } }
            .awaitAll()
            .flatten()
            .distinctBy { it.title.lowercase() }
            .sortedByDescending { it.date }
    }

    private suspend fun readFeed(source: String, url: String): List<NewsItem> {
        val xml = Jsoup.parse(client.get(url, cacheTime = 10).text, "", Parser.xmlParser())
        // RFC 822 dates, e.g. "Sat, 24 Oct 2026 03:00:00 GMT" or "Fri, 23 Oct 2026 23:59:00 -0400"
        val dates = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        return xml.select("item").take(PER_FEED).mapNotNull { item ->
            val title = item.selectFirst("title")?.text()?.let(::plain) ?: return@mapNotNull null
            val link = item.selectFirst("link")?.text()?.trim().takeUnless { it.isNullOrEmpty() } ?: return@mapNotNull null
            val thumbnail = item.selectFirst("media|thumbnail")
            NewsItem(
                title = title,
                link = link,
                description = plain(item.selectFirst("description")?.text() ?: ""),
                image = (thumbnail?.attr("url")?.ifBlank { null } ?: thumbnail?.text())?.ifBlank { null },
                source = source,
                date = runCatching { dates.parse(item.selectFirst("pubDate")!!.text().trim())!!.time }.getOrDefault(0L),
            )
        }
    }

    /** Feed text can carry HTML and double-escaped entities */
    private fun plain(text: String) = Jsoup.parse(Jsoup.parse(text).text()).text().trim()

    /** Most anticipated anime that hasn't started airing yet */
    suspend fun upcoming(page: Int): SearchResults? =
        Anilist.query.search("ANIME", page = page, perPage = 30, sort = "Popular", status = "NOT_YET_RELEASED")

    /**
     * AniList's global activity search is unreliable: a filter that works one day can fail with a server error
     * the next. So try the newest text posts first, then recent activity people are replying to (text posts
     * plus anime/manga updates). Message activities come back empty and are dropped by the UI.
     */
    private val communityFilters = listOf(
        "type_in:[TEXT,MESSAGE],sort:[ID_DESC]",
        "hasRepliesOrTypeText:true,sort:[ID_DESC]",
    )
    private const val ACTIVITY_FIELDS =
        "... on TextActivity{id text(asHtml:false) createdAt likeCount replyCount user{id name avatar{medium}}} " +
            "... on ListActivity{id status progress createdAt likeCount replyCount user{id name avatar{medium}} media{title{userPreferred}}}"

    // Later pages must use the filter that worked for the first page
    private var communityFilter = communityFilters.first()

    suspend fun community(page: Int): CommunityResponse.Page? {
        val filters = if (page == 1) communityFilters else listOf(communityFilter)
        for (filter in filters) {
            val result = executeQuery<CommunityResponse>(
                "{Page(page:$page,perPage:25){pageInfo{hasNextPage}activities($filter){$ACTIVITY_FIELDS}}}",
                force = true, useToken = false, cache = 2
            )?.data?.page
            if (result?.activities.orEmpty().any { it.user != null }) {
                communityFilter = filter
                return result
            }
        }
        return null
    }

    suspend fun profile(userId: Int): Profile? = executeQuery<ProfileResponse>(
        """{User(id:$userId){id name about(asHtml:false) avatar{large} bannerImage siteUrl statistics{anime{count episodesWatched meanScore minutesWatched} manga{count chaptersRead}} favourites{anime(perPage:15){nodes{id title{userPreferred} coverImage{large}}}}}}""",
        force = true, useToken = false
    )?.data?.user
}

@Serializable
data class CommunityResponse(val data: Data? = null) {
    @Serializable
    data class Data(@SerialName("Page") val page: Page? = null)

    @Serializable
    data class Page(val pageInfo: PageInfo? = null, val activities: List<CommunityPost>? = null)
}

@Serializable
data class PageInfo(val hasNextPage: Boolean? = null)

/** A text post, or an anime/manga update ("watched episode 4 of …") */
@Serializable
data class CommunityPost(
    val id: Int = 0,
    val text: String? = null,
    val createdAt: Long = 0,
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val user: AniUser? = null,
    val status: String? = null,
    val progress: String? = null,
    val media: ActivityMedia? = null,
) {
    /** The post itself, or the update written out, e.g. "watched episode 1 - 4 of **Black Clover**" */
    val body: String
        get() = text ?: media?.title?.userPreferred?.let { title ->
            listOfNotNull(status, progress?.let { "$it of" }).joinToString(" ") + " **$title**"
        } ?: ""

    @Serializable
    data class ActivityMedia(val title: Profile.Title? = null)
}

@Serializable
data class AniUser(val id: Int = 0, val name: String = "", val avatar: AniImage? = null)

@Serializable
data class AniImage(val medium: String? = null, val large: String? = null)

@Serializable
data class ProfileResponse(val data: Data? = null) {
    @Serializable
    data class Data(@SerialName("User") val user: Profile? = null)
}

@Serializable
data class Profile(
    val id: Int = 0,
    val name: String = "",
    val about: String? = null,
    val avatar: AniImage? = null,
    val bannerImage: String? = null,
    val siteUrl: String? = null,
    val statistics: Statistics? = null,
    val favourites: Favourites? = null,
) {
    @Serializable
    data class Statistics(val anime: AnimeStats? = null, val manga: MangaStats? = null)

    @Serializable
    data class AnimeStats(val count: Int = 0, val episodesWatched: Int = 0, val meanScore: Double = 0.0, val minutesWatched: Int = 0)

    @Serializable
    data class MangaStats(val count: Int = 0, val chaptersRead: Int = 0)

    @Serializable
    data class Favourites(val anime: FavouritePage? = null)

    @Serializable
    data class FavouritePage(val nodes: List<FavouriteMedia>? = null)

    @Serializable
    data class FavouriteMedia(val id: Int = 0, val title: Title? = null, val coverImage: AniImage? = null)

    @Serializable
    data class Title(val userPreferred: String? = null)
}
