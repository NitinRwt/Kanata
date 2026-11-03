package app.kanata.tv.presenters

import android.graphics.Color
import android.graphics.Paint
import android.graphics.fonts.FontStyle
import app.kanata.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.Action
import androidx.leanback.widget.Presenter
import app.kanata.databinding.TvDetailActionBinding

class DetailActionsPresenter(): Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup?): ViewHolder {
        return DetailActionsViewHolder(TvDetailActionBinding.inflate(LayoutInflater.from(parent?.context), parent, false))
    }

    override fun onBindViewHolder(viewHolder: ViewHolder?, item: Any?) {
        (viewHolder as DetailActionsViewHolder)?.let {
            if(item is SwitchAction) {
                it.binding.title.text = item.label1.toString()
                if(!item.state) {
                    it.binding.title.paintFlags = Paint.STRIKE_THRU_TEXT_FLAG
                    it.binding.title.setTextColor(Color.GRAY)
                }
            } else {
                it.binding.title.text = (item as Action).label1.toString().uppercase()
            }

            it.binding.background.setOnFocusChangeListener { view, b ->
                if(b){
                    view.setBackgroundColor(view.context.getColor(R.color.pink_700))
                } else {
                    view.setBackgroundColor(view.context.getColor(R.color.bg_black))
                }
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder?) {}

    inner class DetailActionsViewHolder(val binding: TvDetailActionBinding) : Presenter.ViewHolder(binding.root) {}

    class SourceAction(id: Long, label: String): Action(id, label) {}
    class ChangeAction(id: Long, label: String): Action(id, label) {}
    class InfoAction(id: Long, label: String): Action(id, label) {}
    class SwitchAction(id: Long, label: String, val state: Boolean): Action(id, label) {}
}

