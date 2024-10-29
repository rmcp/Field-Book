package com.fieldbook.tracker.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 *
 * A generic list adapter implementation for a list of items with checkboxes.
 * Each supplied item must have a unique key, and a displayable label.
 */

class CheckboxListAdapter(
    private val listener: Listener,
) :
    ListAdapter<CheckboxListAdapter.Model, CheckboxListAdapter.ViewHolder>(DiffCallback()) {

    fun interface Listener {
        fun onCheckChanged(checked: Boolean, position: Int)
    }

    data class Model(
        var checked: Boolean,
        val id: String,
        val label: String,
        val subLabel: String
    ) {
        override fun equals(other: Any?): Boolean {
            return id == (other as? Model)?.id
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_label_checkbox, parent, false)
        return ViewHolder(v as CardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        with(currentList[position]) {

            holder.textView.text = label

            holder.subTitleView.text = subLabel

            holder.checkBox.isChecked = checked
        }
    }

    override fun getItemCount(): Int {
        return currentList.size
    }

    inner class ViewHolder(v: CardView) : RecyclerView.ViewHolder(v) {

        var textView: TextView = v.findViewById(R.id.list_item_brapi_filter_tv)
        var checkBox: CheckBox = v.findViewById(R.id.list_item_brapi_filter_cb)
        var subTitleView: TextView = v.findViewById(R.id.list_item_brapi_filter_subtitle_tv)
        var card: CardView = v.findViewById(R.id.list_item_brapi_filter_cv)

        init {

            //get model from tag and call listener when checkbox is clicked
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                listener.onCheckChanged(isChecked, bindingAdapterPosition)
            }

            card.setOnClickListener {
                checkBox.isChecked = !checkBox.isChecked
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Model>() {

        override fun areItemsTheSame(oldItem: Model, newItem: Model): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Model, newItem: Model): Boolean {
            return oldItem.checked == newItem.checked
        }
    }
}