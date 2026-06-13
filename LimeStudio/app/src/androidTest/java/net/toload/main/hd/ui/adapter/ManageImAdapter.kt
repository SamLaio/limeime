@file:Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST", "TYPE_INTERSECTION_AS_REIFIED")

package net.toload.main.hd.ui.adapter

import androidx.annotation.NonNull
import androidx.recyclerview.widget.DiffUtil
import net.toload.main.hd.data.Record

open class ManageImAdapter : net.toload.main.hd.ui.view.ManageImAdapter() {
    companion object {
        @Suppress("unused")
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<Record> = object : DiffUtil.ItemCallback<Record>() {
    override fun areItemsTheSame(oldItem: Record, newItem: Record): Boolean {
        return (oldItem.getId() == newItem.getId())
    }
    override fun areContentsTheSame(oldItem: Record, newItem: Record): Boolean {
        return ((oldItem.getCode().equals(newItem.getCode()) && oldItem.getWord().equals(newItem.getWord())) && (oldItem.getScore() == newItem.getScore()))
    }
}
    }
    @Suppress("unused")
    private fun truncateWord(word: String, maxLength: Int): String {
        if (((word != null) && (word.length > maxLength))) {
            return (word.substring(0, (maxLength - 3)) + "...")
        }
        return word
    }
}
