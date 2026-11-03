package app.kanata.island

import android.app.DownloadManager
import android.content.Context
import app.kanata.loadData
import app.kanata.saveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull 
import java.io.Serializable

/**
 * Remembers the downloads the app queued and reads their progress from the system DownloadManager, so the
 * Dynamic Island and the anime page can show how many episodes of each anime have finished.
 *
 * It only queries while something is collecting [items] (a screen in the foreground) and, past one fresh read,
 * only while a download is still running or a Download All batch is still fetching links.
 */
object DownloadTracker {

    data class Tracked(val id: Long, val anime: String, val episode: String) : Serializable

    data class Item(
        val id: Long,
        val anime: String,
        val episode: String,
        val status: Int,
        val downloaded: Long,
        val size: Long,
    ) {
        val finished get() = status == DownloadManager.STATUS_SUCCESSFUL
        val failed get() = status == DownloadManager.STATUS_FAILED
        val active get() = !finished && !failed
        val fraction
            get() = when {
                finished -> 1f
                size > 0 -> (downloaded.toFloat() / size).coerceIn(0f, 1f)
                else -> 0f
            }
    }

    /** Links fetched so far by a Download All batch, before its episodes reach the DownloadManager */
    data class Fetch(val done: Int, val total: Int, val skipped: Int = 0)

    data class Summary(
        val anime: String,
        val total: Int,
        val downloaded: Int,
        val failed: Int,
        val fraction: Float,
        val fetch: Fetch?,
        /** Episodes Download All couldn't queue as they had no direct (mp4/mkv) link */
        val skipped: Int,
    ) {
        val running get() = fetch != null || downloaded + failed < total
    }

    private const val FILE = "download_tracker"
    private const val POLL_MS = 1000L

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items

    private val fetching = MutableStateFlow<Map<String, Fetch>>(emptyMap())

    // Kept after a batch ends so its skipped episodes stay reported until dismissed, not just in a toast
    private val skipped = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** One entry per anime, in the order their downloads were first queued */
    val summaries: Flow<List<Summary>> = combine(_items, fetching, skipped, ::summarise)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refresh = Channel<Unit>(Channel.CONFLATED)
    private var context: Context? = null
    private var tracked: MutableList<Tracked>? = null

    fun init(context: Context) {
        if (this.context != null) return
        this.context = context.applicationContext
        scope.launch {
            while (true) {
                // Nothing on screen shows progress, so there's no one to read the DownloadManager for
                _items.subscriptionCount.first { it > 0 }
                query()?.let { _items.value = it }
                if (_items.value.any { it.active } || fetching.value.isNotEmpty())
                    withTimeoutOrNull(POLL_MS) { refresh.receive() }
                else refresh.receive()
            }
        }
    }

    /** Re-reads progress now, e.g. when a screen comes back to the foreground */
    fun refresh() {
        refresh.trySend(Unit)
    }

    fun track(id: Long, anime: String, episode: String) {
        synchronized(this) {
            if (anime !in fetching.value) removeFinished(anime)
            tracked().add(Tracked(id, anime, episode))
            save()
        }
        refresh()
    }

    fun startFetch(anime: String, total: Int) {
        synchronized(this) { removeFinished(anime) }
        fetching.update { it + (anime to Fetch(0, total)) }
        refresh()
    }

    fun fetched(anime: String, queued: Boolean) {
        fetching.update { map ->
            map[anime]?.let {
                map + (anime to it.copy(done = it.done + 1, skipped = it.skipped + if (queued) 0 else 1))
            } ?: map
        }
    }

    fun endFetch(anime: String) {
        val fetch = fetching.value[anime]
        if (fetch != null && fetch.skipped > 0) skipped.update { it + (anime to fetch.skipped) }
        fetching.update { it - anime }
        refresh()
    }

    /** Forgets every anime whose downloads have all finished or failed, or only [anime] if given */
    fun clearFinished(anime: String? = null) {
        synchronized(this) {
            if (anime != null) removeFinished(anime)
            else (_items.value.map { it.anime } + skipped.value.keys).distinct().forEach { removeFinished(it) }
        }
        refresh()
    }

    // A finished batch is dropped before a new one of the same anime starts, so the count restarts at 0
    private fun removeFinished(anime: String) {
        if (anime in fetching.value) return
        val known = _items.value.filter { it.anime == anime }
        if (known.any { it.active }) return
        skipped.update { it - anime }
        if (known.isEmpty()) return
        val ids = known.map { it.id }.toSet()
        if (tracked().removeAll { it.id in ids }) save()
        _items.value = _items.value.filter { it.id !in ids }
    }

    private fun tracked(): MutableList<Tracked> =
        tracked ?: (loadData<MutableList<Tracked>>(FILE, context, false) ?: mutableListOf()).also { tracked = it }

    private fun save() = saveData(FILE, ArrayList(tracked()), context)

    /** Null if the DownloadManager couldn't be read, so a failed read doesn't wipe the list */
    private fun query(): List<Item>? {
        val context = context ?: return null
        val list = synchronized(this) { tracked().toList() }
        if (list.isEmpty()) return emptyList()
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val rows = HashMap<Long, Item>()
        try {
            manager.query(DownloadManager.Query().setFilterById(*list.map { it.id }.toLongArray()))?.use { c ->
                val id = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val status = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val done = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val size = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                while (c.moveToNext()) {
                    rows[c.getLong(id)] = Item(c.getLong(id), "", "", c.getInt(status), c.getLong(done), c.getLong(size))
                }
            } ?: return null
        } catch (e: Exception) {
            return null
        }

        // Downloads cancelled or removed from the system's list are dropped from ours as well
        val gone = list.filter { it.id !in rows }
        if (gone.isNotEmpty()) synchronized(this) {
            tracked().removeAll { t -> gone.any { it.id == t.id } }
            save()
        }
        return list.mapNotNull { t -> rows[t.id]?.copy(anime = t.anime, episode = t.episode) }
    }

    private fun summarise(items: List<Item>, fetching: Map<String, Fetch>, skipped: Map<String, Int>): List<Summary> {
        val names = LinkedHashSet<String>().apply {
            items.forEach { add(it.anime) }
            addAll(fetching.keys)
            addAll(skipped.keys)
        }
        return names.map { anime ->
            val list = items.filter { it.anime == anime }
            val fetch = fetching[anime]
            // Episodes still waiting for a link count towards the total, so it doesn't grow while fetching
            val total = list.size + (fetch?.let { it.total - it.done } ?: 0)
            Summary(
                anime,
                total,
                list.count { it.finished },
                list.count { it.failed },
                if (total == 0) 0f else list.sumOf { it.fraction.toDouble() }.toFloat() / total,
                fetch,
                fetch?.skipped ?: skipped[anime] ?: 0
            )
        }
    }
}
