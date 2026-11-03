package app.kanata.parsers

import app.kanata.Lazier
import app.kanata.lazyList
import app.kanata.parsers.manga.*

object MangaSources : MangaReadSources() {
    override val list: List<Lazier<BaseParser>> = lazyList(
        "MangaKakalot" to ::MangaKakalot,
        "MangaBuddy" to ::MangaBuddy,
        "MangaPill" to ::MangaPill,
        "MangaDex" to ::MangaDex,
        "MangaReaderTo" to ::MangaReaderTo,
        "AllAnime" to ::AllAnime,
        "Toonily" to ::Toonily,
//        "MangaHub" to ::MangaHub,
        "MangaKatana" to ::MangaKatana,
        "Manga4Life" to ::Manga4Life,
        "MangaRead" to ::MangaRead,
        "ComickFun" to ::ComickFun,
        // Added later: kept at the end because each title's chosen source is saved by its position here
        "Webtoons" to ::Webtoons,
        "ManhuaPlus" to ::ManhuaPlus,
    )
}

object HMangaSources : MangaReadSources() {
    val aList: List<Lazier<BaseParser>> = lazyList(
        "NineHentai" to ::NineHentai,
        "Manhwa18" to ::Manhwa18,
        "NHentai" to ::NHentai,
    )
    override val list = listOf(aList,MangaSources.list).flatten()
}
