package ani.saikou.anime

import ani.saikou.parsers.Subtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleSelectionTest {

    // Same order MegaPlay returns them in: English is second, so a loose match used to always land on it
    private val subs = listOf("Arabic", "English", "French", "German", "Spanish").map { Subtitle(it, "https://x/$it.vtt") }

    private fun pick(saved: String?, index: Int? = -1) = resolveSubtitle(subs, saved, index)?.language

    @Test fun `picked language after English is used`() = assertEquals("French", pick("French"))

    @Test fun `none turns subtitles off, whatever its case`() {
        assertNull(pick("none"))
        assertNull(pick("None"))
    }

    @Test fun `defaults to English`() = assertEquals("English", pick(null))

    @Test fun `episode pick is used when no language was saved`() = assertEquals("German", pick(null, 3))

    @Test fun `language codes match full names`() = assertEquals("Spanish", pick("es-ES"))

    @Test fun `falls back to English when the language is not offered`() = assertEquals("English", pick("Japanese"))

    @Test fun `no English and nothing matching means no subtitle`() =
        assertNull(resolveSubtitle(listOf(Subtitle("French", "https://x/f.vtt")), "Japanese", null))
}
