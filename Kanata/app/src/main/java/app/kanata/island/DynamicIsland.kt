package app.kanata.island

import android.app.Activity
import android.app.Application
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.Transition
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import app.kanata.R
import app.kanata.anilist.Anilist
import app.kanata.anime.ExoplayerView
import app.kanata.databinding.ItemIslandDownloadBinding
import app.kanata.databinding.ViewDynamicIslandBinding
import app.kanata.isOnTV
import app.kanata.loadData
import app.kanata.loadImage
import app.kanata.manga.mangareader.MangaReaderActivity
import app.kanata.media.MediaDetailsActivity
import app.kanata.saveData
import app.kanata.statusBarHeight
import app.kanata.toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class IslandSettings(
    var enabled: Boolean = true,
    var showRecent: Boolean = true,
    /** 0 start, 1 centre, 2 end */
    var anchor: Int = 1,
    /** Distance from the top of the screen in px, null for just below the status bar */
    var top: Int? = null,
) : Serializable

data class RecentWatch(
    val mediaId: Int,
    val title: String,
    val cover: String?,
    val episode: String,
    val position: Long,
    val duration: Long,
) : Serializable {
    val fraction get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

/**
 * A floating glass pill on every regular screen. It shows the last watched episode with its progress as a ring
 * around the cover, and the progress of downloads; tapping it opens per anime progress bars and actions.
 *
 * It's left out of the player, the manga reader and the TV interface, where it would cover the content.
 */
object DynamicIsland : Application.ActivityLifecycleCallbacks {

    private const val SETTINGS = "dynamic_island"
    private const val RECENT = "island_recent"

    private val islands = WeakHashMap<Activity, Island>()

    private var settingsCache: IslandSettings? = null
    fun settings(context: Context): IslandSettings =
        settingsCache ?: (loadData<IslandSettings>(SETTINGS, context, false) ?: IslandSettings()).also { settingsCache = it }

    /** Saves [settings] and re-applies them to the island on [activity] */
    fun updateSettings(activity: Activity, settings: IslandSettings) {
        settingsCache = settings
        saveData(SETTINGS, settings, activity)
        islands[activity]?.restart()
    }

    private var recentLoaded = false
    private var recentCache: RecentWatch? = null
    private fun recent(context: Context): RecentWatch? {
        if (!recentLoaded) {
            recentCache = loadData(RECENT, context, false)
            recentLoaded = true
        }
        return recentCache
    }

    /** Called by the player when it pauses, so the island can offer to continue that episode */
    fun recordWatch(context: Context, watch: RecentWatch) {
        recentCache = watch
        recentLoaded = true
        saveData(RECENT, watch, context)
    }

    private fun clearRecent(context: Context) {
        recentCache = null
        recentLoaded = true
        saveData(RECENT, null, context)
    }

    private fun eligible(activity: Activity) = activity is AppCompatActivity &&
            activity !is ExoplayerView &&
            activity !is MangaReaderActivity &&
            !activity.javaClass.name.startsWith("app.kanata.tv.") &&
            !isOnTV(activity)

    override fun onActivityStarted(activity: Activity) {
        if (!eligible(activity)) return
        val island = islands[activity] ?: Island.create(activity as AppCompatActivity)?.also { islands[activity] = it }
        island?.start()
    }

    override fun onActivityStopped(activity: Activity) {
        islands[activity]?.stop()
    }

    override fun onActivityDestroyed(activity: Activity) {
        islands.remove(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    private class Island(
        private val activity: AppCompatActivity,
        private val content: FrameLayout,
        private val backdrop: View,
    ) {
        private val binding = ViewDynamicIslandBinding.inflate(activity.layoutInflater, content, false)
        private val view = binding.root
        private val res = activity.resources
        private val margin = res.getDimensionPixelSize(R.dimen.island_margin)
        private val expandedWidth = res.getDimensionPixelSize(R.dimen.island_width_expanded)
        private val rows = mutableListOf<ItemIslandDownloadBinding>()

        private var settings = settings(activity)
        private var summaries = emptyList<DownloadTracker.Summary>()
        private var recent: RecentWatch? = null
        private var cover: String? = null
        private var expanded = false
        private var shown = false
        private var collecting: Job? = null
        private var clearing: Job? = null

        init {
            view.isVisible = false
            content.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            view.onTap = { setExpanded(!expanded) }
            view.onMoved = { anchor, top ->
                settings.anchor = anchor
                settings.top = top
                saveData(SETTINGS, settings, activity)
                position()
            }
            binding.islandWatch.setOnClickListener { openRecent() }
            binding.islandContinue.setOnClickListener { openRecent() }
            binding.islandDownloads.setOnClickListener {
                try {
                    activity.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                } catch (e: ActivityNotFoundException) {
                    toast("Couldn't find the Downloads app", activity)
                }
            }
            binding.islandDismiss.setOnClickListener {
                DownloadTracker.clearFinished()
                clearRecent(activity)
                recent = null
                setExpanded(false)
                render()
            }
            // Keep it on screen when the window changes size, e.g. on rotation
            content.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
                if (bottom - top != oldBottom - oldTop) position()
            }
        }

        fun start() {
            settings = settings(activity)
            if (!settings.enabled) {
                stop()
                hide(false)
                return
            }
            recent = if (settings.showRecent) recent(activity) else null
            position()
            render()
            if (collecting == null) collecting = activity.lifecycleScope.launch {
                DownloadTracker.summaries.collect {
                    summaries = it
                    render()
                }
            }
            DownloadTracker.refresh()
        }

        fun stop() {
            collecting?.cancel()
            collecting = null
            clearing?.cancel()
            clearing = null
        }

        fun restart() {
            stop()
            start()
        }

        private fun minTop(): Int {
            val inset = ViewCompat.getRootWindowInsets(content)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
            return max(statusBarHeight, inset) + margin / 2
        }

        private fun position() {
            val minTop = minTop()
            view.minTop = minTop
            val lp = view.layoutParams as FrameLayout.LayoutParams
            lp.gravity = Gravity.TOP or when (settings.anchor) {
                0 -> Gravity.START
                2 -> Gravity.END
                else -> Gravity.CENTER_HORIZONTAL
            }
            lp.marginStart = margin
            lp.marginEnd = margin
            val maxTop = if (content.height > 0) content.height - res.getDimensionPixelSize(R.dimen.island_height) - margin else Int.MAX_VALUE
            lp.topMargin = (settings.top ?: minTop).coerceIn(minTop, max(minTop, maxTop))
            lp.width = if (expanded && content.width > 0) min(content.width - margin * 2, expandedWidth)
            else ViewGroup.LayoutParams.WRAP_CONTENT
            view.layoutParams = lp
        }

        private fun setExpanded(open: Boolean) {
            if (expanded == open) return
            expanded = open
            // Only the island morphs; the screen under it is left out so the transition stays cheap
            fun Transition.islandOnly() = excludeTarget(backdrop, true).excludeChildren(backdrop, true)
            TransitionManager.beginDelayedTransition(
                content,
                TransitionSet()
                    .addTransition(ChangeBounds().islandOnly())
                    .addTransition(Fade().islandOnly())
                    .setOrdering(TransitionSet.ORDERING_TOGETHER)
                    .setDuration(260)
                    .setInterpolator(FastOutSlowInInterpolator())
            )
            binding.islandExpanded.isVisible = open
            position()
            if (open) renderExpanded()
        }

        private fun show() {
            if (shown) return
            shown = true
            // Blur only while visible: a hidden island shouldn't redraw whenever the screen behind it does
            view.setBlurTarget(backdrop)
            view.animate().cancel()
            view.isVisible = true
            view.alpha = 0f
            view.scaleX = 0.85f
            view.scaleY = 0.85f
            view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240)
                .setInterpolator(FastOutSlowInInterpolator()).start()
        }

        private fun hide(animate: Boolean = true) {
            if (!shown && !view.isVisible) return
            shown = false
            expanded = false
            binding.islandExpanded.isVisible = false
            fun gone() {
                view.isVisible = false
                view.setBlurTarget(null)
                position()
            }
            view.animate().cancel()
            if (animate) view.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(180).withEndAction(::gone).start()
            else gone()
        }

        private fun render() {
            val recent = recent
            val downloads = summaries
            if (recent == null && downloads.isEmpty()) return hide()
            show()

            binding.islandWatch.isVisible = recent != null
            if (recent != null) {
                if (cover != recent.cover) {
                    binding.islandCover.loadImage(recent.cover)
                    cover = recent.cover
                }
                binding.islandWatchRing.setProgressCompat((recent.fraction * 1000).roundToInt(), true)
            }

            if (downloads.isEmpty()) {
                binding.islandTitle.text = recent!!.title
                binding.islandSubtitle.text = "Episode ${recent.episode} · ${(recent.fraction * 100).roundToInt()}% watched"
                binding.islandDownload.isVisible = false
                clearing?.cancel()
                clearing = null
            } else {
                val total = downloads.sumOf { it.total }
                val done = downloads.sumOf { it.downloaded }
                val failed = downloads.sumOf { it.failed }
                val skipped = downloads.sumOf { it.skipped }
                val running = downloads.any { it.running }
                val fetch = downloads.mapNotNull { it.fetch }
                val fraction = if (total == 0) 0f else downloads.sumOf { it.fraction.toDouble() * it.total }.toFloat() / total

                binding.islandTitle.text = downloads.singleOrNull()?.anime ?: "${downloads.size} anime downloading"
                binding.islandSubtitle.text = when {
                    fetch.isNotEmpty() -> "Fetching links ${fetch.sumOf { it.done }} / ${fetch.sumOf { it.total }}" +
                            if (done > 0) " · $done done" else ""
                    running -> "Downloaded $done of $total"
                    total == 0 -> "No direct links for $skipped episodes"
                    failed > 0 || skipped > 0 -> "Downloaded $done of $total" + issues(failed, skipped, " · ")
                    else -> "Downloaded all $total"
                }
                val clean = !running && failed == 0 && skipped == 0
                binding.islandDownload.isVisible = true
                binding.islandDownloadRing.setProgressCompat((fraction * 1000).roundToInt(), true)
                binding.islandDownloadIcon.setImageResource(if (clean) R.drawable.ic_round_done_24 else R.drawable.ic_round_download_24)

                // A clean finish is shown for a moment, then cleared; failed or skipped episodes stay until dismissed
                if (clean) {
                    if (clearing == null) clearing = activity.lifecycleScope.launch {
                        delay(6000)
                        DownloadTracker.clearFinished()
                        clearing = null
                    }
                } else {
                    clearing?.cancel()
                    clearing = null
                }
            }
            if (expanded) renderExpanded()
        }

        private fun renderExpanded() {
            val list = summaries.take(MAX_ROWS)
            while (rows.size < list.size)
                rows.add(ItemIslandDownloadBinding.inflate(activity.layoutInflater, binding.islandDownloadList, true))
            while (rows.size > list.size) binding.islandDownloadList.removeView(rows.removeAt(rows.size - 1).root)

            list.forEachIndexed { i, s ->
                val row = rows[i]
                row.islandItemTitle.text = s.anime
                row.islandItemCount.isVisible = s.total > 0
                row.islandItemCount.text = "${s.downloaded} / ${s.total}"
                row.islandItemProgress.setProgressCompat((s.fraction * 1000).roundToInt(), true)
                val note = when {
                    s.fetch != null -> "Fetching links ${s.fetch.done} / ${s.fetch.total}"
                    s.running -> "Downloading ${s.total - s.downloaded - s.failed} more"
                    s.failed > 0 || s.skipped > 0 -> issues(s.failed, s.skipped, "") +
                            if (s.skipped > 0) " (stream only, no mp4)" else ""
                    else -> null
                }
                row.islandItemNote.isVisible = note != null
                row.islandItemNote.text = note
            }
            binding.islandContinue.isVisible = recent != null
            binding.islandDownloads.isVisible = summaries.isNotEmpty()
        }

        private fun issues(failed: Int, skipped: Int, prefix: String) = listOfNotNull(
            "$failed failed".takeIf { failed > 0 },
            "$skipped skipped".takeIf { skipped > 0 },
        ).joinToString(" · ", prefix)

        private fun openRecent() {
            val watch = recent ?: return
            toast("Opening ${watch.title}", activity)
            activity.lifecycleScope.launch {
                val media = withContext(Dispatchers.IO) {
                    try {
                        Anilist.query.getMedia(watch.mediaId)
                    } catch (e: Exception) {
                        null
                    }
                }
                if (media == null) return@launch toast("Couldn't load ${watch.title}", activity)
                // Lets the details page jump straight to the episode to continue
                media.cameFromContinue = true
                activity.startActivity(Intent(activity, MediaDetailsActivity::class.java).putExtra("media", media as Serializable))
            }
        }

        companion object {
            const val MAX_ROWS = 5

            fun create(activity: AppCompatActivity): Island? {
                val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return null
                val backdrop = content.getChildAt(0) ?: return null
                return Island(activity, content, backdrop)
            }
        }
    }
}
