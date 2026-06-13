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

package net.toload.main.hd.data

import android.database.Cursor
import java.util.ArrayList
import net.toload.main.hd.global.LIME

/**
 * Represents a keyboard configuration in the database.
 *
 * @author LimeIME Team
 */
class Keyboard {
    var id: Int = 0
    var code: String? = null
    var name: String? = null
    var desc: String? = null
    var type: String? = null
    var image: String? = null
    var imkb: String? = null
    var imshiftkb: String? = null
    var engkb: String? = null
    var engshiftkb: String? = null
    var symbolkb: String? = null
    var symbolshiftkb: String? = null
    var defaultkb: String? = null
    var defaultshiftkb: String? = null
    var extendedkb: String? = null
    var extendedshiftkb: String? = null
    var isDisable: Boolean = false

    fun getDescription(): String? = desc

    fun setDescription(description: String?) {
        desc = description
    }

    fun getEngkb(showNumberRow: Boolean): String {
        return if (showNumberRow) "lime_english_number" else "lime_english"
    }

    fun getEngshiftkb(showNumberRow: Boolean): String {
        return if (showNumberRow) "lime_english_number_shift" else "lime_english_shift"
    }
}
