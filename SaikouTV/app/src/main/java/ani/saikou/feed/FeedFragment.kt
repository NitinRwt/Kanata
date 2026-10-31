package ani.saikou.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.saikou.R
import ani.saikou.databinding.FragmentFeedBinding
import ani.saikou.media.Media
import ani.saikou.media.MediaAdaptor
import ani.saikou.navBarHeight
import ani.saikou.px
import ani.saikou.statusBarHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Feed tab: anime news, upcoming anime and the AniList community */
class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!

    private enum class Tab { NEWS, UPCOMING, COMMUNITY }

    private var tab = Tab.NEWS
    private var job: Job? = null
    private var page = 1
    private var hasNextPage = false
    private var loadingMore = false
    private val upcoming = arrayListOf<Media>()
    private val posts = arrayListOf<CommunityPost>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.feedHeader.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = statusBarHeight + 8f.px }
        // The list scrolls under the glass header and above the floating nav bar
        binding.feedHeader.doOnLayout { header ->
            val top = header.bottom + 8f.px
            binding.feedRecycler.updatePadding(top = top, bottom = navBarHeight + 120f.px)
            binding.feedRefresh.setProgressViewOffset(false, top - 48f.px, top + 32f.px)
        }

        binding.feedTabs.setOnCheckedStateChangeListener { _, checked ->
            tab = when (checked.firstOrNull()) {
                R.id.feedTabUpcoming -> Tab.UPCOMING
                R.id.feedTabCommunity -> Tab.COMMUNITY
                else -> Tab.NEWS
            }
            load()
        }
        binding.feedRefresh.setOnRefreshListener { load(refreshing = true) }
        binding.feedRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && !recyclerView.canScrollVertically(1)) loadMore()
            }
        })
        load()
    }

    private fun load(refreshing: Boolean = false) {
        job?.cancel()
        page = 1
        hasNextPage = false
        loadingMore = false
        binding.feedEmpty.visibility = View.GONE
        if (!refreshing) {
            binding.feedRecycler.adapter = null
            binding.feedProgress.visibility = View.VISIBLE
        }
        val current = tab
        job = viewLifecycleOwner.lifecycleScope.launch {
            val empty = when (current) {
                Tab.NEWS -> {
                    val news = withContext(Dispatchers.IO) { FeedRepository.news() }
                    binding.feedRecycler.layoutManager = LinearLayoutManager(requireContext())
                    binding.feedRecycler.adapter = NewsAdapter(news)
                    news.isEmpty()
                }
                Tab.UPCOMING -> {
                    val results = withContext(Dispatchers.IO) { FeedRepository.upcoming(1) }
                    upcoming.clear()
                    results?.results?.let(upcoming::addAll)
                    hasNextPage = results?.hasNextPage == true
                    binding.feedRecycler.layoutManager = GridLayoutManager(requireContext(), gridSpan())
                    binding.feedRecycler.adapter = MediaAdaptor(0, upcoming, requireActivity(), matchParent = true)
                    upcoming.isEmpty()
                }
                Tab.COMMUNITY -> {
                    val result = withContext(Dispatchers.IO) { FeedRepository.community(1) }
                    posts.clear()
                    posts.addAll(readable(result?.activities))
                    hasNextPage = result?.pageInfo?.hasNextPage == true
                    binding.feedRecycler.layoutManager = LinearLayoutManager(requireContext())
                    binding.feedRecycler.adapter = CommunityAdapter(posts) { UserProfileActivity.open(requireContext(), it.id) }
                    posts.isEmpty()
                }
            }
            binding.feedProgress.visibility = View.GONE
            binding.feedRefresh.isRefreshing = false
            binding.feedEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        }
    }

    /** Next page of upcoming anime or community posts when the end of the list is reached */
    private fun loadMore() {
        if (loadingMore || !hasNextPage || tab == Tab.NEWS) return
        loadingMore = true
        val current = tab
        val next = page + 1
        job = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val adapter = binding.feedRecycler.adapter
                when (current) {
                    Tab.UPCOMING -> {
                        val results = withContext(Dispatchers.IO) { FeedRepository.upcoming(next) } ?: return@launch
                        val start = upcoming.size
                        upcoming.addAll(results.results)
                        hasNextPage = results.hasNextPage
                        adapter?.notifyItemRangeInserted(start, results.results.size)
                    }
                    Tab.COMMUNITY -> {
                        val result = withContext(Dispatchers.IO) { FeedRepository.community(next) } ?: return@launch
                        val start = posts.size
                        val more = readable(result.activities)
                        posts.addAll(more)
                        hasNextPage = result.pageInfo?.hasNextPage == true
                        adapter?.notifyItemRangeInserted(start, more.size)
                    }
                    Tab.NEWS -> Unit
                }
                page = next
            } finally {
                // A failed page must not block loading more later
                loadingMore = false
            }
        }
    }

    /** Posts that are only images or videos have nothing left to read once embeds are stripped */
    private fun readable(activities: List<CommunityPost>?) =
        activities.orEmpty().filter { it.user != null && FeedText.cleanAniList(it.body).isNotBlank() }

    private fun gridSpan(): Int {
        val widthDp = resources.displayMetrics.run { widthPixels / density }
        return (widthDp / 124).toInt().coerceAtLeast(2)
    }

    override fun onDestroyView() {
        job?.cancel()
        _binding = null
        super.onDestroyView()
    }
}
