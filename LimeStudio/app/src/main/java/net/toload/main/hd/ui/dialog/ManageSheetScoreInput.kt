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

package net.toload.main.hd.ui.dialog

import com.google.android.material.textfield.TextInputEditText
import kotlin.math.max
import kotlin.math.min

/**
 * Shared score field helpers for manage add/edit sheets.
 */
object ManageSheetScoreInput {
    private const val MIN_SCORE = 0
    private const val MAX_SCORE = 9999

    @JvmStatic
    fun setScore(field: TextInputEditText, score: Int) {
        field.setText(clamp(score).toString())
        field.setSelection(field.text?.length ?: 0)
    }

    @JvmStatic
    fun readScore(field: TextInputEditText, fallback: Int): Int {
        val value = field.text?.toString()?.trim() ?: ""
        if (value.isEmpty()) {
            setScore(field, fallback)
            return clamp(fallback)
        }
        return try {
            val score = clamp(value.toInt())
            if (value != score.toString()) {
                setScore(field, score)
            }
            score
        } catch (e: NumberFormatException) {
            setScore(field, fallback)
            clamp(fallback)
        }
    }

    @JvmStatic
    fun decrement(field: TextInputEditText, fallback: Int): Int {
        val score = max(MIN_SCORE, readScore(field, fallback) - 1)
        setScore(field, score)
        return score
    }

    @JvmStatic
    fun increment(field: TextInputEditText, fallback: Int): Int {
        val score = min(MAX_SCORE, readScore(field, fallback) + 1)
        setScore(field, score)
        return score
    }

    private fun clamp(score: Int): Int {
        return max(MIN_SCORE, min(MAX_SCORE, score))
    }
}
