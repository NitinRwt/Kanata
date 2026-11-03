package app.kanata.novel

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.kanata.R
import app.kanata.anilist.Anilist
import app.kanata.databinding.ActivityNovelReaderBinding
import app.kanata.databinding.ItemNovelChapterEndBinding
import app.kanata.hideSystemBars
import app.kanata.initActivity
import app.kanata.loadData
import app.kanata.manga.MangaChapter
import app.kanata.media.Media
import app.kanata.navBarHeight
import app.kanata.others.getSerialized
import app.kanata.parsers.NovelParser
import app.kanata.parsers.NovelSources
import app.kanata.saveData
import app.kanata.statusBarHeight
import app.kanata.toast
import app.kanata.updateAnilistProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Reads a novel chapter as text, with its own theme, font and spacing, and remembers where you stopped */
class NovelReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNovelReaderBinding
    lateinit var settings: NovelReaderSettings

    private lateinit var media: Media
    private lateinit var parser: NovelParser
    private lateinit var chapters: Map<String, MangaChapter>
    private lateinit var keys: List<String>
    private var index = 0

    private val adapter = ParagraphAdapter()
    private lateinit var layoutManager: LinearLayoutManager
    private var loadJob: Job? = null
    private var barsVisible = true
    private var markedRead = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNovelReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)

        settings = loadData(NovelReaderSettings.KEY, this, toast = false) ?: NovelReaderSettings()
        media = intent.getSerialized("media") ?: return finish()
        chapters = media.manga?.chapters ?: return finish()
        keys = chapters.keys.toList()
        index = keys.indexOf(media.manga!!.selectedChapter).coerceAtLeast(0)
        parser = NovelSources.novel(media.selected!!.source)

        binding.novelReaderTopBar.updatePadding(top = binding.novelReaderTopBar.paddingTop + statusBarHeight)
        binding.novelReaderBottomBar.updatePadding(bottom = binding.novelReaderBottomBar.paddingBottom + navBarHeight)
        binding.novelReaderText.updatePadding(
            top = binding.novelReaderText.paddingTop + statusBarHeight,
            bottom = binding.novelReaderText.paddingBottom + navBarHeight
        )

        layoutManager = LinearLayoutManager(this)
        binding.novelReaderText.layoutManager = layoutManager
        binding.novelReaderText.adapter = adapter
        binding.novelReaderText.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = onPositionChanged()
        })

        binding.novelReaderTitle.text = media.userPreferredName
        binding.novelReaderBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        onBackPressedDispatcher.addCallback(this) {
            savePosition()
            finish()
        }
        binding.novelReaderSettings.setOnClickListener {
            NovelReaderSettingsDialogFragment().show(supportFragmentManager, "novel_settings")
        }
        binding.novelReaderChapterSelect.setOnClickListener { pickChapter() }
        binding.novelReaderPrevious.setOnClickListener {
            if (index > 0) open(index - 1) else toast(getString(R.string.novel_no_previous))
        }
        binding.novelReaderNext.setOnClickListener { next() }
        binding.novelReaderRetry.setOnClickListener { load() }
        binding.novelReaderSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser && adapter.itemCount > 0)
                layoutManager.scrollToPositionWithOffset(((adapter.itemCount - 1) * value / 100).roundToInt(), 0)
        }

        applySettings()
        load()
    }

    private fun load() {
        loadJob?.cancel()
        val key = keys[index]
        val chapter = chapters.getValue(key)
        markedRead = false
        media.manga!!.selectedChapter = key
        saveData("${media.id}_current_chp", key, this)
        binding.novelReaderChapter.text = chapterName(chapter)
        binding.novelReaderError.visibility = View.GONE
        binding.novelReaderProgress.visibility = View.VISIBLE
        adapter.show(null, listOf(), false)

        loadJob = lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    parser.loadText(chapter.link)
                } catch (e: Exception) {
                    null
                }
            }
            binding.novelReaderProgress.visibility = View.GONE
            if (text.isNullOrEmpty()) {
                binding.novelReaderError.visibility = View.VISIBLE
                return@launch
            }
            adapter.show(chapterName(chapter), text, index < keys.size - 1)
            val saved = loadData<Int>(positionKey(key), this@NovelReaderActivity, toast = false) ?: 0
            layoutManager.scrollToPositionWithOffset(saved.coerceIn(0, adapter.itemCount - 1), 0)
            binding.novelReaderText.post { onPositionChanged() }
        }
    }

    private fun open(newIndex: Int) {
        savePosition()
        index = newIndex
        load()
    }

    private fun next() {
        if (index < keys.size - 1) {
            markRead()
            open(index + 1)
        } else toast(getString(R.string.novel_no_next))
    }

    private fun pickChapter() {
        val names = keys.map { chapterName(chapters.getValue(it)) }.toTypedArray()
        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle(R.string.novel_chapters)
            .setSingleChoiceItems(names, index) { dialog, which ->
                dialog.dismiss()
                if (which != index) open(which)
            }
            .show()
    }

    private fun chapterName(chapter: MangaChapter) =
        chapter.title?.takeIf { it.isNotBlank() && it != "null" } ?: "Chapter ${chapter.number}"

    private fun positionKey(key: String) = "${media.id}_${key}_novel"

    private fun savePosition() {
        if (adapter.itemCount == 0) return
        saveData(positionKey(keys[index]), layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0), this)
    }

    private fun onPositionChanged() {
        val count = adapter.itemCount
        if (count == 0) return
        val first = layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)
        val percent = if (count <= 1) 100 else (first * 100f / (count - 1)).roundToInt().coerceIn(0, 100)
        binding.novelReaderPercent.text = "$percent%"
        binding.novelReaderSlider.value = percent.toFloat()
        // Reaching the end of the text counts as having read the chapter
        if (!markedRead && layoutManager.findLastVisibleItemPosition() == count - 1) markRead()
    }

    /** Moves AniList progress up to this chapter, never back, unless progress saving was turned off for this title */
    private fun markRead() {
        if (markedRead) return
        markedRead = true
        if (Anilist.userid == null || loadData<Boolean>("${media.id}_save_progress", this, toast = false) == false) return
        val key = keys[index]
        val number = key.toFloatOrNull()?.roundToInt() ?: return
        if (number > (media.userProgress ?: 0)) updateAnilistProgress(media, key)
    }

    fun applySettings() {
        val theme = settings.theme
        binding.novelReaderRoot.setBackgroundColor(theme.background)
        val barColor = ColorUtils.setAlphaComponent(theme.background, 0xEE)
        binding.novelReaderTopBar.setBackgroundColor(barColor)
        binding.novelReaderBottomBar.setBackgroundColor(barColor)
        listOf(binding.novelReaderTitle, binding.novelReaderChapter, binding.novelReaderPercent, binding.novelReaderErrorText)
            .forEach { it.setTextColor(theme.text) }
        binding.novelReaderChapter.compoundDrawablesRelative.forEach { it?.setTint(theme.text) }
        listOf(binding.novelReaderBack, binding.novelReaderSettings, binding.novelReaderPrevious, binding.novelReaderNext)
            .forEach { it.setColorFilter(theme.text) }

        val lightPage = ColorUtils.calculateLuminance(theme.background) > 0.5
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightPage
            isAppearanceLightNavigationBars = lightPage
        }

        if (settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        adapter.typeface = when (settings.font) {
            NovelReaderSettings.Fonts.SANS -> Typeface.SANS_SERIF
            NovelReaderSettings.Fonts.SERIF -> Typeface.SERIF
            NovelReaderSettings.Fonts.POPPINS -> ResourcesCompat.getFont(this, R.font.poppins) ?: Typeface.DEFAULT
        }
        adapter.restyle()
        saveData(NovelReaderSettings.KEY, settings, this)
    }

    private fun toggleBars() {
        barsVisible = !barsVisible
        val duration = 200L
        binding.novelReaderTopBar.animate().alpha(if (barsVisible) 1f else 0f)
            .translationY(if (barsVisible) 0f else -binding.novelReaderTopBar.height.toFloat()).setDuration(duration)
            .withStartAction { if (barsVisible) binding.novelReaderTopBar.visibility = View.VISIBLE }
            .withEndAction { if (!barsVisible) binding.novelReaderTopBar.visibility = View.INVISIBLE }
        binding.novelReaderBottomBar.animate().alpha(if (barsVisible) 1f else 0f)
            .translationY(if (barsVisible) 0f else binding.novelReaderBottomBar.height.toFloat()).setDuration(duration)
            .withStartAction { if (barsVisible) binding.novelReaderBottomBar.visibility = View.VISIBLE }
            .withEndAction { if (!barsVisible) binding.novelReaderBottomBar.visibility = View.INVISIBLE }
        if (!barsVisible) hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        savePosition()
    }

    /** The chapter heading, then its paragraphs, then a button on to the next chapter */
    private inner class ParagraphAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var heading: String? = null
        private var paragraphs: List<String> = listOf()
        private var hasNext = false
        var typeface: Typeface = Typeface.SERIF

        @SuppressLint("NotifyDataSetChanged")
        fun show(heading: String?, paragraphs: List<String>, hasNext: Boolean) {
            this.heading = heading
            this.paragraphs = paragraphs
            this.hasNext = hasNext
            notifyDataSetChanged()
        }

        @SuppressLint("NotifyDataSetChanged")
        fun restyle() = notifyDataSetChanged()

        override fun getItemCount() = if (paragraphs.isEmpty()) 0 else paragraphs.size + 2

        override fun getItemViewType(position: Int) = when (position) {
            0 -> HEADING
            itemCount - 1 -> END
            else -> TEXT
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val view = if (viewType == END) ItemNovelChapterEndBinding.inflate(inflater, parent, false).root
            else inflater.inflate(R.layout.item_novel_paragraph, parent, false)
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val view = holder.itemView
            if (getItemViewType(position) == END) {
                val end = ItemNovelChapterEndBinding.bind(view)
                end.novelChapterEndNext.visibility = if (hasNext) View.VISIBLE else View.GONE
                end.novelChapterEndNext.setTextColor(settings.theme.text)
                end.novelChapterEndNext.setOnClickListener { next() }
                view.setOnClickListener { toggleBars() }
                return
            }
            (view as TextView).apply {
                val isHeading = position == 0
                text = if (isHeading) heading else paragraphs[position - 1]
                setTextColor(if (isHeading) settings.theme.text else ColorUtils.setAlphaComponent(settings.theme.text, 0xF2))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isHeading) settings.textSize * 1.3f else settings.textSize)
                setTypeface(typeface, if (isHeading) Typeface.BOLD else Typeface.NORMAL)
                setLineSpacing(0f, settings.lineSpacing)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) justificationMode =
                    if (settings.justify && !isHeading) Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE
                setOnClickListener { toggleBars() }
            }
        }
    }

    companion object {
        private const val HEADING = 0
        private const val TEXT = 1
        private const val END = 2
    }
}
