/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
 *  **
 *  **    Project Url: https://github.com/SamLaio/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

package net.toload.main.hd.ui.view

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.toload.main.hd.data.Record
import net.toload.main.hd.R

/**
 * RecyclerView adapter for ManageIm grid using ListAdapter with DiffUtil for efficient updates.
 */
open class ManageImAdapter : ListAdapter<Record, ManageImAdapter.ViewHolder>(DIFF_CALLBACK) {
    private var onItemClickListener: OnItemClickListener? = null

    fun interface OnItemClickListener {
        fun onItemClick(record: Record?, position: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.word, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = getItem(position)
        if (record != null) {
            var wordtext = record.getWord() ?: ""
            if (wordtext.length > MAX_WORD_LENGTH) {
                wordtext = wordtext.substring(0, MAX_WORD_LENGTH - TRUNCATION_SUFFIX.length) +
                    TRUNCATION_SUFFIX
            }
            holder.txtCode.text = record.getCode()
            holder.txtWord.text = wordtext
            holder.txtScore.text = record.getScore().toString()

            holder.itemView.setOnClickListener {
                onItemClickListener?.onItemClick(record, holder.adapterPosition)
            }
        }
    }

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        onItemClickListener = listener
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtWord: TextView = itemView.findViewById(R.id.txtWord)
        val txtCode: TextView = itemView.findViewById(R.id.txtCode)
        val txtScore: TextView = itemView.findViewById(R.id.txtScore)
    }

    companion object {
        private const val MAX_WORD_LENGTH = 12
        private const val TRUNCATION_SUFFIX = "..."

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Record>() {
            override fun areItemsTheSame(oldItem: Record, newItem: Record): Boolean {
                return oldItem.getIdAsInt() == newItem.getIdAsInt()
            }

            override fun areContentsTheSame(oldItem: Record, newItem: Record): Boolean {
                return oldItem.getCode() == newItem.getCode() &&
                    oldItem.getWord() == newItem.getWord() &&
                    oldItem.getScore() == newItem.getScore()
            }
        }
    }
}
