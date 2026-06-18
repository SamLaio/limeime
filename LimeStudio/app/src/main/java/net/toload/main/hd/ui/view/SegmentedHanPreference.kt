/*
 *
 *  *
 *  **    Copyright 2026, The LimeIME Open Source Project
 *  **
 *  **    Project Url: https://github.com/SamLaio/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *
 */

package net.toload.main.hd.ui.view

import android.content.Context
import android.content.res.TypedArray
import android.text.Layout
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButtonToggleGroup
import net.toload.main.hd.R

/**
 * Renders the han_convert_option preference as an inline segmented control while
 * preserving the original persisted String values: "0", "1", and "2".
 */
class SegmentedHanPreference(
    context: Context,
    attrs: AttributeSet?
) : Preference(context, attrs) {

    private var currentValue = "0"

    init {
        layoutResource = R.layout.preference_han_segmented
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        return a.getString(index)
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        val fallback = defaultValue as? String ?: "0"
        currentValue = getPersistedString(fallback)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.itemView.isClickable = false

        val group = holder.findViewById(R.id.han_toggle_group) as? MaterialButtonToggleGroup
            ?: return
        group.clearOnButtonCheckedListeners()
        group.check(buttonIdFor(currentValue))
        group.addOnButtonCheckedListener { toggleGroup, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newValue = valueForButton(checkedId)
            if (newValue == currentValue) return@addOnButtonCheckedListener
            if (callChangeListener(newValue)) {
                currentValue = newValue
                persistString(newValue)
            } else {
                toggleGroup.check(buttonIdFor(currentValue))
            }
        }
        stackIfClipped(group)
    }

    private fun buttonIdFor(value: String?): Int {
        return when (value ?: "0") {
            "1" -> R.id.han_opt_t2s
            "2" -> R.id.han_opt_s2t
            else -> R.id.han_opt_none
        }
    }

    private fun valueForButton(id: Int): String {
        return when (id) {
            R.id.han_opt_t2s -> "1"
            R.id.han_opt_s2t -> "2"
            else -> "0"
        }
    }

    companion object {
        @JvmStatic
        fun stackIfClipped(group: MaterialButtonToggleGroup) {
            group.post {
                var clipped = false
                for (i in 0 until group.childCount) {
                    val child = group.getChildAt(i)
                    if (child is Button) {
                        val layout: Layout? = child.layout
                        if (layout != null) {
                            val lines = layout.lineCount
                            if (lines > 0 && layout.getEllipsisCount(lines - 1) > 0) {
                                clipped = true
                                break
                            }
                        }
                    }
                }
                if (!clipped || group.orientation == LinearLayout.VERTICAL) return@post
                group.orientation = LinearLayout.VERTICAL
                for (i in 0 until group.childCount) {
                    val child: View = group.getChildAt(i)
                    val params = child.layoutParams as? LinearLayout.LayoutParams ?: continue
                    params.width = LinearLayout.LayoutParams.MATCH_PARENT
                    params.height = LinearLayout.LayoutParams.WRAP_CONTENT
                    params.weight = 0f
                    child.layoutParams = params
                }
            }
        }
    }
}
