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

package net.toload.main.hd.global


/**
 * Created by Jeremy on 2015/5/23.
 */
abstract class LIMEProgressListener {
    abstract fun onProgress(var1: Long, var2: Long, status: String?)

    open fun progressInterval(): Long = 500L

    open fun onError(code: Int, source: String?) {
    }

    open fun onPreExecute() {
    }

    open fun onPostExecute(success: Boolean, status: String?, code: Int) {
    }

    open fun onStatusUpdate(status: String?) {
    }
}
