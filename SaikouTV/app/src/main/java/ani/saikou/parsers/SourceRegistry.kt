package ani.saikou.parsers

import ani.saikou.client
import ani.saikou.loadData
import ani.saikou.parsers.anime.AniWatch
import ani.saikou.parsers.anime.AniWaves
import ani.saikou.parsers.anime.AnimePaheNg
import ani.saikou.parsers.anime.HiAnime
import ani.saikou.saveData
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.Serializable

/**
 * The kinds of anime site Kamisama can read. Many sites are copies of the same few website themes, so one
 * parser serves every site built on that theme: a new mirror only needs its link.
 * [fingerprint] is text found in the homepage of every site of that type, used to detect it.
 */
enum class SiteType(val label: String, val example: String, private val fingerprint: String) {
    ANIWATCH("AniWatch theme", "aniwatch.co.at", "wp-json/hianime/v1"),
    HIANIME("HiAnime theme", "hianime.ro", "otakuthemes"),
    ANIMESTREAM("AnimeStream theme", "animepahe.ng", "ts_configs"),
    ANIWAVE("AniWave clone", "aniwaves.ru", "ajax/anime");

    fun create(host: String, name: String, saveName: String): AnimeParser = when (this) {
        ANIWATCH -> AniWatch(host, name, saveName)
        HIANIME -> HiAnime(host, name, saveName)
        ANIMESTREAM -> AnimePaheNg(host, name, saveName)
        ANIWAVE -> AniWaves(host, name, saveName)
    }

    fun matches(homepage: String) = fingerprint in homepage
}

/** A built-in source. Its link can be changed by the user when the site moves to a new domain */
data class BuiltInSource(val name: String, val saveName: String, val defaultHost: String, val type: SiteType)

/** A source the user added: any site of a [SiteType] */
data class CustomSource(val name: String, val url: String, val type: SiteType) : Serializable {
    /** Key for this source's saved show matches; unique per site */
    val saveName: String get() = "custom_" + (url.toHttpUrlOrNull()?.host ?: url).replace(Regex("[^A-Za-z0-9]"), "_")

    companion object {
        private const val serialVersionUID = 1L
    }
}

data class SourceTest(val ok: Boolean, val message: String)

object SourceRegistry {

    val builtIns = listOf(
        BuiltInSource("AniWatch", "aniwatch_co_at", "https://aniwatch.co.at", SiteType.ANIWATCH),
        BuiltInSource("HiAnime", "hianime_ro", "https://hianime.ro", SiteType.HIANIME),
        BuiltInSource("AniWaves", "aniwaves_ru", "https://aniwaves.ru", SiteType.ANIWAVE),
        BuiltInSource("AnimePahe.ng", "animepahe_ng", "https://animepahe.ng", SiteType.ANIMESTREAM),
    )

    private const val LINKS = "source_links"
    private const val CUSTOM = "custom_sources"

    private fun links(): HashMap<String, String> = loadData<HashMap<String, String>>(LINKS, toast = false) ?: hashMapOf()

    /** The link in use for a built-in source: the user's replacement if set, otherwise the default */
    fun host(source: BuiltInSource): String = links()[source.saveName] ?: source.defaultHost

    fun isChanged(source: BuiltInSource) = links().containsKey(source.saveName)

    /** Sets (or with null, resets) the link of a built-in source */
    fun setHost(source: BuiltInSource, host: String?) {
        val links = links()
        if (host == null || host == source.defaultHost) links.remove(source.saveName) else links[source.saveName] = host
        saveData(LINKS, links)
    }

    fun customs(): List<CustomSource> = loadData<ArrayList<CustomSource>>(CUSTOM, toast = false) ?: arrayListOf()

    fun saveCustoms(sources: List<CustomSource>) = saveData(CUSTOM, ArrayList(sources))

    /**
     * "hianime.to/home" or "https://www.site.cc/" becomes "https://hianime.to" / "https://www.site.cc":
     * parsers build every request from the bare origin. Null if it isn't a web address.
     */
    fun normalize(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(text)) text else "https://$text"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        if (!url.host.contains('.')) return null
        val port = if (url.port == if (url.isHttps) 443 else 80) "" else ":${url.port}"
        return "${url.scheme}://${url.host}$port"
    }

    /** Which kind of site this is, from its homepage. Null if it isn't one Kamisama can read */
    suspend fun detect(host: String): SiteType? {
        val homepage = client.get(host, cacheTime = 0).text
        return SiteType.values().firstOrNull { it.matches(homepage) }
    }

    /**
     * Checks a source really works by searching it and loading the first result's episodes.
     * (Playback isn't tested: that depends on each video server.)
     */
    suspend fun test(parser: AnimeParser): SourceTest = try {
        val shows = listOf("one piece", "naruto", "frieren").firstNotNullOfOrNull { query ->
            parser.search(query).takeIf { it.isNotEmpty() }
        }
        if (shows == null) SourceTest(false, "The site answered, but searching it found nothing.")
        else {
            val show = shows.first()
            val episodes = parser.loadEpisodes(show.link, show.extra)
            if (episodes.isEmpty()) SourceTest(false, "Search works, but no episodes were found for \"${show.name}\".")
            else SourceTest(true, "Works: found \"${show.name}\" with ${episodes.size} episodes.")
        }
    } catch (e: Exception) {
        SourceTest(false, "Couldn't read the site: ${e.message ?: e.javaClass.simpleName}")
    }
}
