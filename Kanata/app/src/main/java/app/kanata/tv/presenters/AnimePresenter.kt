package app.kanata.tv.presenters

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentActivity
import androidx.leanback.widget.Presenter
import app.kanata.*
import app.kanata.databinding.ItemMediaCompactBinding
import app.kanata.databinding.ItemMediaLargeBinding
import app.kanata.databinding.ItemMediaPageBinding
import app.kanata.databinding.TvAnimeCardBinding
import app.kanata.media.*
import app.kanata.settings.UserInterfaceSettings
import app.kanata.tv.TVAnimeDetailFragment
import app.kanata.tv.TVDetailActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.request.RequestOptions
import com.flaviofaria.kenburnsview.RandomTransitionGenerator
import jp.wasabeef.glide.transformations.BlurTransformation
import java.io.Serializable

class AnimePresenter(var type: Int,
                     private val activity: FragmentActivity,
                     private val matchParent:Boolean=false): Presenter() {

    private val uiSettings = loadData<UserInterfaceSettings>("ui_settings") ?: UserInterfaceSettings()

    override fun onCreateViewHolder(parent: ViewGroup?): ViewHolder {
        return MediaViewHolder(TvAnimeCardBinding.inflate(LayoutInflater.from(parent?.context ?: activity.applicationContext), parent, false))
    }

    override fun onBindViewHolder(viewHolder: ViewHolder?, item: Any?) {
        when (type){
            0->{
                val itemView = (viewHolder as MediaViewHolder).view
                val b = (viewHolder as MediaViewHolder).binding
                setAnimation(activity,b.root,uiSettings)
                val media = item as Media
                if(media!=null) {
                    if (matchParent) itemView.updateLayoutParams { width=-1 }

                    b.itemCompactImage.loadImage(media.cover)
                    b.itemCompactOngoing.visibility = if (media.status == "RELEASING") View.VISIBLE else View.GONE
                    b.itemCompactTitle.text = media.mainName()
                    b.itemCompactScore.text = ((if (media.userScore == 0) (media.meanScore ?: 0) else media.userScore) / 10.0).toString()
                    b.itemCompactScoreBG.background = ContextCompat.getDrawable(b.root.context, (if (media.userScore != 0) R.drawable.item_user_score else R.drawable.item_score))
                    b.itemCompactUserProgress.text = (media.userProgress ?: "~").toString()

                    if (media.anime != null) {
                        b.itemCompactTotal.text = " | ${if (media.anime.nextAiringEpisode != null) (media.anime.nextAiringEpisode.toString() + " | " + (media.anime.totalEpisodes ?: "~").toString()) else (media.anime.totalEpisodes ?: "~").toString()}"
                    }
                    else if (media.manga != null) {
                        b.itemCompactTotal.text = " | ${media.manga.totalChapters ?: "~"}"
                    }
                }
            }
            1->{
                val itemView = (viewHolder as MediaViewHolder).view
                val b = (viewHolder as MediaViewHolder).binding
                setAnimation(activity,b.root,uiSettings)
                val media = item as Media
                if(media!=null) {
                    if (matchParent) itemView.updateLayoutParams { width=-1 }

                    b.itemExtraInfo.visibility = View.GONE
                    b.itemCompactImage.loadImage(media.cover)
                    b.itemCompactOngoing.visibility = if (media.status == "RELEASING") View.VISIBLE else View.GONE
                    b.itemCompactTitle.text = media.mainName()
                    b.itemCompactScore.text = ((if (media.userScore == 0) (media.meanScore ?: 0) else media.userScore) / 10.0).toString()
                    b.itemCompactScoreBG.background = ContextCompat.getDrawable(b.root.context, (if (media.userScore != 0) R.drawable.item_user_score else R.drawable.item_score))
                    b.itemCompactUserProgress.text = (media.userProgress ?: "~").toString()

                    if (media.relation != null) {
                        b.itemCompactRelation.text = "${media.relation}  "
                        b.itemCompactType.visibility = View.VISIBLE
                        b.itemCompactTypeImage.imageTintList = ColorStateList.valueOf(Color.WHITE)
                    } else {
                        b.itemCompactType.visibility = View.GONE
                    }

                    if (media.anime != null) {
                        if (media.relation != null) b.itemCompactTypeImage.setImageDrawable(
                            AppCompatResources.getDrawable(
                                activity,
                                R.drawable.ic_round_movie_filter_24
                            )
                        )
                        b.itemCompactTotal.text = " | ${if (media.anime.nextAiringEpisode != null) (media.anime.nextAiringEpisode.toString() + " | " + (media.anime.totalEpisodes ?: "~").toString()) else (media.anime.totalEpisodes ?: "~").toString()}"
                    }
                    else if (media.manga != null) {
                        if (media.relation != null) b.itemCompactTypeImage.setImageDrawable(
                            AppCompatResources.getDrawable(
                                activity,
                                R.drawable.ic_round_import_contacts_24
                            )
                        )
                        b.itemCompactTotal.text = " | ${media.manga.totalChapters ?: "~"}"
                    }
                }
            }
            2->{
            }
        }
    }

    inner class MediaViewHolder(val binding: TvAnimeCardBinding) : Presenter.ViewHolder(binding.root) {}

    inner class MediaLargeViewHolder(val binding: ItemMediaLargeBinding) : Presenter.ViewHolder(binding.root) {}

    @SuppressLint("ClickableViewAccessibility")
    inner class MediaPageViewHolder(val binding: ItemMediaPageBinding) : Presenter.ViewHolder(binding.root) {}

    override fun onUnbindViewHolder(viewHolder: ViewHolder?) {
    }
}