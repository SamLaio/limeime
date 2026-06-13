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
import net.toload.main.hd.global.LIME

/**
 * Represents an Input Method (IM) configuration record in the database.
 *
 * @author LimeIME Team
 */
class ImConfig {
    var id: Int = 0
    var code: String? = null
    var title: String? = null
    var desc: String? = null
    var keyboard: String? = null
    var isDisable: Boolean = false
    var selkey: String? = null
    var endkey: String? = null
    var spacestyle: String? = null
}
