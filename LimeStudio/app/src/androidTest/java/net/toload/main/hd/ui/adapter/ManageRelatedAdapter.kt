@file:Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST", "TYPE_INTERSECTION_AS_REIFIED")

package net.toload.main.hd.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.toload.main.hd.R
import net.toload.main.hd.data.Related

open class ManageRelatedAdapter : ListAdapter<Related, ManageRelatedAdapter.ViewHolder> {
    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<Related> = object : DiffUtil.ItemCallback<Related>() {
    override fun areItemsTheSame(oldItem: Related, newItem: Related): Boolean {
        return (oldItem.getId() == newItem.getId())
    }
    override fun areContentsTheSame(oldItem: Related, newItem: Related): Boolean {
        return (oldItem.getPword().equals(newItem.getPword()) && oldItem.getCword().equals(newItem.getCword()))
    }
}
    }
    private lateinit var clickListener: OnItemClickListener
    interface OnItemClickListener {
        fun onItemClick(related: Related)
        fun onItemLongClick(related: Related)
    }
    constructor() : super(DIFF_CALLBACK) {
    }
    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.clickListener = listener
    }
    @NonNull
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        var view: View = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        var related: Related = getItem(position)
        holder.bind(related)
        holder.itemView.setOnClickListener({ v ->
    if ((clickListener != null)) {
        clickListener.onItemClick(related)
    }
})
        holder.itemView.setOnLongClickListener { _ ->
            if (::clickListener.isInitialized) {
                clickListener.onItemLongClick(related)
                true
            } else {
                false
            }
        }
    }
    open class ViewHolder : RecyclerView.ViewHolder {
        private lateinit var text1: TextView
        private lateinit var text2: TextView
        constructor(itemView: View) : super(itemView) {
            text1 = itemView.findViewById(android.R.id.text1)
            text2 = itemView.findViewById(android.R.id.text2)
        }
        fun bind(related: Related) {
            text1.setText(related.getPword())
            text2.setText(related.getCword())
        }
    }
}
