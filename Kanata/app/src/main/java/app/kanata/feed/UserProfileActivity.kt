package app.kanata.feed

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import app.kanata.R
import app.kanata.anilist.Anilist
import app.kanata.databinding.ActivityUserProfileBinding
import app.kanata.databinding.ItemMediaCompactBinding
import app.kanata.databinding.ItemProfileStatBinding
import app.kanata.glass.Depth3D
import app.kanata.initActivity
import app.kanata.loadData
import app.kanata.loadImage
import app.kanata.media.MediaDetailsActivity
import app.kanata.openLinkInBrowser
import app.kanata.settings.UserInterfaceSettings
import app.kanata.statusBarHeight
import app.kanata.toastString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable
import java.util.Locale

/** A public AniList profile: banner, avatar, stats, bio and favourite anime */
class UserProfileActivity : AppCompatActivity() {

    companion object {
        private const val USER_ID = "userId"

        fun open(context: Context, userId: Int) {
            context.startActivity(Intent(context, UserProfileActivity::class.java).putExtra(USER_ID, userId))
        }
    }

    private lateinit var binding: ActivityUserProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)

        binding.profileClose.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        binding.profileClose.setOnClickListener { finish() }

        val userId = intent.getIntExtra(USER_ID, 0)
        lifecycleScope.launch {
            val profile = withContext(Dispatchers.IO) { FeedRepository.profile(userId) }
            binding.profileProgress.visibility = View.GONE
            if (profile == null) {
                toastString("Couldn't load this profile")
                return@launch
            }
            show(profile)
        }
    }

    private fun show(profile: Profile) {
        if (profile.bannerImage != null) binding.profileBanner.loadImage(profile.bannerImage)
        binding.profileAvatar.loadImage(profile.avatar?.large)
        binding.profileName.text = profile.name

        val anime = profile.statistics?.anime
        stat(binding.statAnime, (anime?.count ?: 0).toString(), R.string.stat_anime)
        stat(binding.statEpisodes, (anime?.episodesWatched ?: 0).toString(), R.string.stat_episodes)
        stat(binding.statDays, String.format(Locale.US, "%.1f", (anime?.minutesWatched ?: 0) / 1440f), R.string.stat_days)
        stat(binding.statScore, anime?.meanScore?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f", it) } ?: "–", R.string.stat_score)

        val about = FeedText.cleanAniList(profile.about)
        if (about.isBlank()) binding.profileAbout.setText(R.string.profile_about_empty)
        else FeedText.markwon(this).setMarkdown(binding.profileAbout, about)

        val favourites = profile.favourites?.anime?.nodes.orEmpty()
        val hasFavourites = favourites.isNotEmpty()
        binding.profileFavouritesTitle.visibility = if (hasFavourites) View.VISIBLE else View.GONE
        binding.profileFavourites.visibility = if (hasFavourites) View.VISIBLE else View.GONE
        binding.profileFavourites.adapter = FavouritesAdapter(favourites)

        binding.profileOpenAniList.setOnClickListener {
            openLinkInBrowser(profile.siteUrl ?: "https://anilist.co/user/${profile.id}")
        }
        binding.profileContent.visibility = View.VISIBLE
    }

    private fun stat(stat: ItemProfileStatBinding, value: String, label: Int) {
        stat.statValue.text = value
        stat.statLabel.setText(label)
    }

    private fun openMedia(id: Int) {
        lifecycleScope.launch {
            val media = withContext(Dispatchers.IO) { Anilist.query.getMedia(id) } ?: return@launch
            startActivity(Intent(this@UserProfileActivity, MediaDetailsActivity::class.java).putExtra("media", media as Serializable))
        }
    }

    private inner class FavouritesAdapter(private val items: List<Profile.FavouriteMedia>) :
        RecyclerView.Adapter<FavouritesAdapter.Holder>() {

        private val depth3D = Depth3D.isEnabled(loadData<UserInterfaceSettings>("ui_settings") ?: UserInterfaceSettings())

        inner class Holder(val binding: ItemMediaCompactBinding) : RecyclerView.ViewHolder(binding.root) {
            init {
                // Only cover and title: no score or progress for someone else's favourites
                binding.itemCompactScoreBG.visibility = View.GONE
                binding.itemCompactOngoing.visibility = View.GONE
                binding.itemCompactType.visibility = View.GONE
                (binding.itemCompactUserProgress.parent as? View)?.visibility = View.GONE
                binding.root.setOnClickListener { items.getOrNull(bindingAdapterPosition)?.let { openMedia(it.id) } }
                if (depth3D) Depth3D.attachTilt(binding.root, binding.itemCompactCard)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemMediaCompactBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.binding.itemCompactImage.loadImage(item.coverImage?.large)
            holder.binding.itemCompactTitle.text = item.title?.userPreferred
        }

        override fun getItemCount() = items.size

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            if (depth3D) Depth3D.attachScroll(recyclerView, this)
        }
    }
}
