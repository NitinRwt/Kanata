package app.kanata.feed

import android.content.Context
import android.net.Uri
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabsIntent
import androidx.recyclerview.widget.RecyclerView
import app.kanata.databinding.ItemCommunityPostBinding
import app.kanata.databinding.ItemNewsBinding
import app.kanata.loadImage
import app.kanata.openLinkInBrowser
import app.kanata.others.SpoilerPlugin
import app.kanata.tryWith
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin

object FeedText {
    /** "3 hours ago"; empty when the time is unknown */
    fun ago(millis: Long): CharSequence =
        if (millis <= 0) "" else DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)

    /**
     * AniList markdown with its own embeds (img(), youtube(), webm()), HTML and centring marks removed so a post
     * reads as text. Spoilers (~!...!~) are kept: [SpoilerPlugin] hides them until tapped.
     */
    fun cleanAniList(text: String?): String = (text ?: "")
        .replace(Regex("(?i)img\\d*%?\\(.*?\\)"), "")
        .replace(Regex("(?i)(youtube|webm)\\(.*?\\)"), "")
        // A spoiler that only wrapped an image is empty once the image is gone
        .replace(Regex("~!\\s*!~"), "")
        .replace(Regex("<[^>]+>"), "")
        .replace("~~~", "")
        // Headings would turn a post into a giant banner in the feed
        .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    fun markwon(context: Context): Markwon =
        Markwon.builder(context).usePlugin(SoftBreakAddsNewLinePlugin.create()).usePlugin(SpoilerPlugin()).build()

    /** Articles open in an in-app browser tab, falling back to the browser */
    fun openArticle(context: Context, url: String) {
        tryWith { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url)) }
            ?: openLinkInBrowser(url)
    }
}

class NewsAdapter(private val items: List<NewsItem>) : RecyclerView.Adapter<NewsAdapter.Holder>() {

    inner class Holder(val binding: ItemNewsBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener { view ->
                items.getOrNull(bindingAdapterPosition)?.let { FeedText.openArticle(view.context, it.link) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val b = holder.binding
        b.newsImageContainer.visibility = if (item.image != null) View.VISIBLE else View.GONE
        if (item.image != null) b.newsImage.loadImage(item.image)
        val ago = FeedText.ago(item.date)
        b.newsMeta.text = if (ago.isEmpty()) item.source else "${item.source} · $ago"
        b.newsTitle.text = item.title
        b.newsDescription.text = item.description
        b.newsDescription.visibility = if (item.description.isBlank()) View.GONE else View.VISIBLE
    }

    override fun getItemCount() = items.size
}

class CommunityAdapter(
    private val posts: List<CommunityPost>,
    private val onUser: (AniUser) -> Unit,
) : RecyclerView.Adapter<CommunityAdapter.Holder>() {

    private var markwon: Markwon? = null

    inner class Holder(val binding: ItemCommunityPostBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.postAuthor.setOnClickListener {
                posts.getOrNull(bindingAdapterPosition)?.user?.let(onUser)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        if (markwon == null) markwon = FeedText.markwon(parent.context)
        return Holder(ItemCommunityPostBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val post = posts[position]
        val b = holder.binding
        b.postAvatar.setImageDrawable(null)
        b.postAvatar.loadImage(post.user?.avatar?.medium)
        b.postName.text = post.user?.name
        b.postTime.text = FeedText.ago(post.createdAt * 1000)
        markwon?.setMarkdown(b.postText, FeedText.cleanAniList(post.body))
        b.postLikes.text = post.likeCount.toString()
        b.postReplies.text = post.replyCount.toString()
    }

    override fun getItemCount() = posts.size
}
