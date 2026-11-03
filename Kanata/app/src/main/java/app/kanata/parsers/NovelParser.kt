package app.kanata.parsers

import app.kanata.Lazier
import app.kanata.lazyList
import app.kanata.media.Media
import app.kanata.parsers.novel.NovelBin
import app.kanata.parsers.novel.NovelFull
import org.jsoup.nodes.Element

/**
 * A site with light novels and web novels. Chapters are text instead of pages, so it shares the manga screens
 * (source picker, chapter list, progress) and only the reader is its own.
 */
abstract class NovelParser : MangaParser() {

    /**
     * Takes MangaChapter.link & returns the chapter's paragraphs, in reading order, as plain text
     * **/
    abstract suspend fun loadText(chapterLink: String): List<String>

    final override suspend fun loadImages(chapterLink: String): List<MangaImage> = listOf()

    // Novel sites list translated works under their English titles, not the romaji that manga sites use
    override suspend fun autoSearch(mediaObj: Media): ShowResponse? {
        var response = loadSavedShowResponse(mediaObj.id)
        if (response != null) {
            saveShowResponse(mediaObj.id, response, true)
        } else {
            setUserText("Searching : ${mediaObj.mainName()}")
            response = search(mediaObj.mainName()).firstOrNull()
            if (response == null) {
                setUserText("Searching : ${mediaObj.nameRomaji}")
                response = search(mediaObj.nameRomaji).firstOrNull()
            }
            saveShowResponse(mediaObj.id, response)
        }
        return response
    }

    /**
     * Turns (link, title) pairs into chapters numbered from their titles. Numbers decide AniList progress and
     * must be unique, so a repeat ("Chapter 12 part 2") gets a suffix and a title without one uses its position.
     */
    protected fun numberChapters(chapters: List<Pair<String, String>>): List<MangaChapter> {
        val used = mutableSetOf<String>()
        return chapters.mapIndexed { index, (link, title) ->
            val found = Regex("""(?i)chapter\s*(\d+(?:\.\d+)?)""").find(title)?.groupValues?.get(1) ?: "${index + 1}"
            var number = found
            var copy = 2
            while (!used.add(number)) number = "$found-${copy++}"
            MangaChapter(number = number, link = link, title = title)
        }
    }

    /** The readable paragraphs of a chapter's text block, without the ads and scripts sites mix in */
    protected fun paragraphs(content: Element): List<String> {
        content.select("script, style, iframe, ins, noscript, div[class*=ads], div[id*=ads]").remove()
        val fromTags = content.select("p").map { it.text().trim() }.filter { it.isNotEmpty() }
        if (fromTags.isNotEmpty()) return fromTags
        // Some chapters are a single block broken up with <br> instead of paragraphs
        content.select("br").append("\\n")
        return content.text().split("\\n").map { it.trim() }.filter { it.isNotEmpty() }
    }
}

object NovelSources : MangaReadSources() {
    override val list: List<Lazier<BaseParser>> = lazyList(
        "NovelBin" to ::NovelBin,
        "NovelFull" to ::NovelFull,
    )

    fun novel(i: Int) = get(i) as NovelParser
}
