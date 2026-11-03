package app.kanata.anime

import android.app.Activity
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.kanata.BottomSheetDialogFragment
import app.kanata.R
import app.kanata.databinding.BottomSheetSubtitlesBinding
import app.kanata.databinding.ItemSubtitleTextBinding
import app.kanata.loadData
import app.kanata.media.MediaDetailsViewModel
import app.kanata.parsers.Subtitle
import app.kanata.saveData
import com.google.android.material.color.MaterialColors

class SubtitleDialogFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetSubtitlesBinding? = null
    private val binding get() = _binding!!
    val model: MediaDetailsViewModel by activityViewModels()
    private lateinit var episode: Episode

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetSubtitlesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        model.getMedia().observe(viewLifecycleOwner) { media ->
            episode = media?.anime?.episodes?.get(media.anime.selectedEpisode) ?: return@observe
            val currentExtractor = episode.extractors?.find { it.server.name == episode.selectedExtractor } ?: return@observe
            binding.subtitlesRecycler.layoutManager = LinearLayoutManager(requireContext())
            binding.subtitlesRecycler.adapter = SubtitleAdapter(currentExtractor.subtitles)
        }
    }

    inner class SubtitleAdapter(val subtitles: List<Subtitle>) : RecyclerView.Adapter<SubtitleAdapter.StreamViewHolder>() {
        inner class StreamViewHolder(val binding: ItemSubtitleTextBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreamViewHolder =
            StreamViewHolder(ItemSubtitleTextBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: StreamViewHolder, position: Int) {
            val binding = holder.binding
            // Highlight the track the player will actually use, resolved the same way the player does
            model.getMedia().observe(viewLifecycleOwner) { media ->
                val selSubs: String? = loadData("subLang_${media.id}", activity)
                val current = resolveSubtitle(subtitles, selSubs, episode.selectedSubtitle)
                val selected = if (position == 0) current == null else current == subtitles[position - 1]
                binding.root.setCardBackgroundColor(
                    if (selected) ColorUtils.setAlphaComponent(MaterialColors.getColor(binding.root, R.attr.colorSecondary), 82)
                    else TRANSPARENT
                )
            }
            if (position == 0) {
                binding.subtitleTitle.setText(R.string.none)
                binding.root.setOnClickListener {
                    episode.selectedSubtitle = null
                    model.setEpisode(episode, "Subtitle")
                    model.getMedia().observe(viewLifecycleOwner){media ->
                        val mediaID: Int = media.id
                        saveData("subLang_${mediaID}", "none", activity)
                    }
                    dismiss()
                }
            } else {
                binding.subtitleTitle.text = when (subtitles[position - 1].language) {
                    "ja-JP" -> "[ja-JP] Japanese"
                    "en-US" -> "[en-US] English"
                    "de-DE" -> "[de-DE] German"
                    "es-ES" -> "[es-ES] Spanish"
                    "es-419" -> "[es-419] Spanish"
                    "fr-FR" -> "[fr-FR] French"
                    "it-IT" -> "[it-IT] Italian"
                    "pt-BR" -> "[pt-BR] Portuguese (Brazil)"
                    "pt-PT" -> "[pt-PT] Portuguese (Portugal)"
                    "ru-RU" -> "[ru-RU] Russian"
                    "zh-CN" -> "[zh-CN] Chinese (Simplified)"
                    "tr-TR" -> "[tr-TR] Turkish"
                    "ar-ME" -> "[ar-ME] Arabic"
                    "ar-SA" -> "[ar-SA] Arabic (Saudi Arabia)"
                    "uk-UK" -> "[uk-UK] Ukrainian"
                    "he-IL" -> "[he-IL] Hebrew"
                    "pl-PL" -> "[pl-PL] Polish"
                    "ro-RO" -> "[ro-RO] Romanian"
                    "sv-SE" -> "[sv-SE] Swedish"
                    else -> if(subtitles[position - 1].language matches Regex("([a-z]{2})-([A-Z]{2}|\\d{3})")) "[${subtitles[position - 1].language}]" else subtitles[position - 1].language
                }
                val activity: Activity = requireActivity() as ExoplayerView
                binding.root.setOnClickListener {
                    episode.selectedSubtitle = position - 1
                    model.setEpisode(episode, "Subtitle")
                    model.getMedia().observe(viewLifecycleOwner){media ->
                        val mediaID: Int = media.id
                        saveData("subLang_${mediaID}", subtitles[position - 1].language, activity)
                    }
                    dismiss()
                }
            }
        }

        override fun getItemCount(): Int = subtitles.size + 1
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }
}

private val languageNames = mapOf(
    "ja-JP" to "Japanese", "en-US" to "English", "de-DE" to "German", "es-ES" to "Spanish",
    "es-419" to "Spanish", "fr-FR" to "French", "it-IT" to "Italian", "pt-BR" to "Portuguese (Brazil)",
    "pt-PT" to "Portuguese (Portugal)", "ru-RU" to "Russian", "zh-CN" to "Chinese", "tr-TR" to "Turkish",
    "ar-ME" to "Arabic",
)

/**
 * The subtitle to play, shared by the player and the subtitle picker so both agree.
 * [savedLanguage] is the language picked for this anime ("none" turns subtitles off), [selectedIndex] the
 * track picked for this episode. Exact language matches win, then language codes (en-US → English), and
 * English is only the fallback when the picked language isn't offered by this server.
 */
internal fun resolveSubtitle(subtitles: List<Subtitle>, savedLanguage: String?, selectedIndex: Int?): Subtitle? {
    fun english() = subtitles.find { it.language == "English" || it.language == "en-US" }
    if (savedLanguage.equals("none", ignoreCase = true)) return null
    if (savedLanguage == null) {
        return selectedIndex?.takeIf { it >= 0 }?.let { subtitles.getOrNull(it) } ?: english()
    }
    return subtitles.find { it.language == savedLanguage }
        ?: subtitles.find { it.language == languageNames[savedLanguage] }
        ?: subtitles.find { languageNames[it.language] == savedLanguage }
        ?: english()
}