package app.kanata.tv.presenters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.leanback.widget.Presenter
import app.kanata.databinding.TvGenreCardBinding
import app.kanata.databinding.TvItemCharacterBinding
import app.kanata.loadImage
import app.kanata.media.Character
import app.kanata.px

class CharacterPresenter: Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup?): ViewHolder {
        return CharacterViewHolder(TvItemCharacterBinding.inflate(LayoutInflater.from(parent?.context), parent, false))
    }

    override fun onBindViewHolder(viewHolder: ViewHolder?, item: Any?) {
        val character = item as Character
        val holder = viewHolder as CharacterViewHolder
        val binding = holder.binding
        binding.itemCompactRelation.text = character.role + "  "
        binding.itemCompactImage.loadImage(character.image)
        binding.itemCompactTitle.text = character.name
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder?) {}

    inner class CharacterViewHolder(val binding: TvItemCharacterBinding) : Presenter.ViewHolder(binding.root) {}
}