package app.kanata.novel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.kanata.BottomSheetDialogFragment
import app.kanata.R
import app.kanata.databinding.BottomSheetNovelSettingsBinding
import app.kanata.novel.NovelReaderSettings.Fonts
import app.kanata.novel.NovelReaderSettings.Themes

class NovelReaderSettingsDialogFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetNovelSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetNovelSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as NovelReaderActivity
        val settings = activity.settings

        val themes = mapOf(
            Themes.LIGHT to R.id.novelThemeLight,
            Themes.SEPIA to R.id.novelThemeSepia,
            Themes.DARK to R.id.novelThemeDark,
            Themes.BLACK to R.id.novelThemeBlack,
        )
        binding.novelSettingsTheme.check(themes.getValue(settings.theme))
        binding.novelSettingsTheme.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            settings.theme = themes.entries.first { it.value == id }.key
            activity.applySettings()
        }

        val fonts = mapOf(
            Fonts.SANS to R.id.novelFontSans,
            Fonts.SERIF to R.id.novelFontSerif,
            Fonts.POPPINS to R.id.novelFontPoppins,
        )
        binding.novelSettingsFont.check(fonts.getValue(settings.font))
        binding.novelSettingsFont.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            settings.font = fonts.entries.first { it.value == id }.key
            activity.applySettings()
        }

        fun labels() {
            binding.novelSettingsTextSizeLabel.text = "${getString(R.string.text_size)} · ${settings.textSize.toInt()}"
            binding.novelSettingsLineSpacingLabel.text = "${getString(R.string.line_spacing)} · ${"%.1f".format(settings.lineSpacing)}"
        }
        labels()

        binding.novelSettingsTextSize.value = settings.textSize.coerceIn(12f, 32f)
        binding.novelSettingsTextSize.addOnChangeListener { _, value, _ ->
            settings.textSize = value
            labels()
            activity.applySettings()
        }

        // Rounded to the slider's 0.1 steps, which it insists on
        binding.novelSettingsLineSpacing.value = (Math.round(settings.lineSpacing.coerceIn(1f, 2.4f) * 10) / 10f)
        binding.novelSettingsLineSpacing.addOnChangeListener { _, value, _ ->
            settings.lineSpacing = value
            labels()
            activity.applySettings()
        }

        binding.novelSettingsJustify.isChecked = settings.justify
        binding.novelSettingsJustify.setOnCheckedChangeListener { _, isChecked ->
            settings.justify = isChecked
            activity.applySettings()
        }

        binding.novelSettingsKeepScreenOn.isChecked = settings.keepScreenOn
        binding.novelSettingsKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            settings.keepScreenOn = isChecked
            activity.applySettings()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
