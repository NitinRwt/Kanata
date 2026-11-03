package app.kanata.novel

import android.graphics.Color
import java.io.Serializable

data class NovelReaderSettings(
    var textSize: Float = 18f,
    var lineSpacing: Float = 1.5f,
    var theme: Themes = Themes.DARK,
    var font: Fonts = Fonts.SERIF,
    var justify: Boolean = false,
    var keepScreenOn: Boolean = true,
) : Serializable {

    enum class Themes(val background: Int, val text: Int) {
        LIGHT(Color.parseColor("#FAFAF7"), Color.parseColor("#1F1E24")),
        SEPIA(Color.parseColor("#F3E9D2"), Color.parseColor("#5B4636")),
        DARK(Color.parseColor("#17161D"), Color.parseColor("#D9D6E2")),
        BLACK(Color.BLACK, Color.parseColor("#B5B3BC")),
    }

    enum class Fonts { SANS, SERIF, POPPINS }

    companion object {
        const val KEY = "novel_reader_settings"
        private const val serialVersionUID = 1L
    }
}
