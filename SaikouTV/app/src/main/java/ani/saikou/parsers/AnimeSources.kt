package ani.saikou.parsers

import ani.saikou.Lazier
import ani.saikou.lazyList
import ani.saikou.parsers.anime.Haho
import ani.saikou.parsers.anime.HentaiFF
import ani.saikou.parsers.anime.HentaiMama
import ani.saikou.parsers.anime.HentaiStream

/**
 * Anime sources: the built-in ones (with any link the user changed) followed by the user's own.
 * Older parsers (AllAnime, AniWave, AnimeDao, AnimePahe, Gogo, Kaido, Marin) are kept in the code but no
 * longer listed, as their sites are gone.
 */
object AnimeSources : WatchSources() {

    override var list: List<Lazier<BaseParser>> = build()
        private set

    /** Rebuilds the list after a source link is changed or a source is added or removed */
    fun reload() {
        list = build()
    }

    private fun build(): List<Lazier<BaseParser>> =
        SourceRegistry.builtIns.map { source ->
            Lazier<BaseParser>({ source.type.create(SourceRegistry.host(source), source.name, source.saveName) }, source.name)
        } + SourceRegistry.customs().map { source ->
            Lazier<BaseParser>({ source.type.create(source.url, source.name, source.saveName) }, source.name)
        }
}

object HAnimeSources : WatchSources() {
    private val aList: List<Lazier<BaseParser>>  = lazyList(
        "HentaiMama" to ::HentaiMama,
        "Haho" to ::Haho,
        "HentaiStream" to ::HentaiStream,
        "HentaiFF" to ::HentaiFF,
    )

    override val list get() = listOf(aList, AnimeSources.list).flatten()
}
