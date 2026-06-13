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

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import java.util.Objects
import net.toload.main.hd.data.Related
import net.toload.main.hd.R

/**
 * RecyclerView adapter for displaying related-phrase items.
 */
class ManageRelatedAdapter(private val activity: Activity?) :
    ListAdapter<Related, ManageRelatedAdapter.ViewHolder>(DIFF_CALLBACK) {
    private var onItemClickListener: OnItemClickListener? = null

    fun interface OnItemClickListener {
        fun onItemClick(related: Related?, position: Int)
    }

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        onItemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.related, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val related = getItem(position)
        if (related != null) {
            val pword = related.getPword()
            val cword = truncateByCodePoint(related.getCword(), 10)
            val freq = related.getBasescore()

            holder.txtPword.text = pword
            holder.txtWord.text = cword
            holder.txtFreq.text = String.format(Locale.US, "%,d", freq)

            holder.itemView.setOnClickListener {
                onItemClickListener?.onItemClick(related, position)
            }
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtPword: TextView = itemView.findViewById(R.id.txtPword)
        val txtWord: TextView = itemView.findViewById(R.id.txtWord)
        val txtFreq: TextView = itemView.findViewById(R.id.txtFreq)
    }

    companion object {
        private fun truncateByCodePoint(text: String?, maxCodePoints: Int): String? {
            if (text == null || text.codePointCount(0, text.length) <= maxCodePoints) {
                return text
            }
            val end = text.offsetByCodePoints(0, maxCodePoints)
            return text.substring(0, end) + "..."
        }

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Related>() {
            override fun areItemsTheSame(oldItem: Related, newItem: Related): Boolean {
                return oldItem.getIdAsInt() == newItem.getIdAsInt()
            }

            override fun areContentsTheSame(oldItem: Related, newItem: Related): Boolean {
                return oldItem.getPword() == newItem.getPword() &&
                    oldItem.getCword() == newItem.getCword() &&
                    oldItem.getBasescore() == newItem.getBasescore() &&
                    oldItem.getUserscore() == newItem.getUserscore()
            }
        }
    }
}
