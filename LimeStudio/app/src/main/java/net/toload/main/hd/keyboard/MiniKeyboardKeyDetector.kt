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

package net.toload.main.hd.keyboard

import net.toload.main.hd.keyboard.LIMEBaseKeyboard.Key

class MiniKeyboardKeyDetector(slideAllowance: Float) : KeyDetector() {
    private val mSlideAllowanceSquare: Int = (slideAllowance * slideAllowance).toInt()
    private val mSlideAllowanceSquareTop: Int = mSlideAllowanceSquare * 2

    override fun getMaxNearbyKeys(): Int = MAX_NEARBY_KEYS

    override fun getKeyIndexAndNearbyCodes(x: Int, y: Int, allKeys: IntArray?): Int {
        val keys = getKeys()
        val touchX = getTouchX(x)
        val touchY = getTouchY(y)
        var closestKeyIndex = LIMEKeyboardBaseView.NOT_A_KEY
        var closestKeyDist = if (y < 0) mSlideAllowanceSquareTop else mSlideAllowanceSquare
        val keyCount = keys.size
        for (i in 0 until keyCount) {
            val key = keys[i]
            val dist = key.squaredDistanceFrom(touchX, touchY)
            if (dist < closestKeyDist) {
                closestKeyIndex = i
                closestKeyDist = dist
            }
        }
        if (allKeys != null && closestKeyIndex != LIMEKeyboardBaseView.NOT_A_KEY) {
            allKeys[0] = keys[closestKeyIndex].codes[0]
        }
        return closestKeyIndex
    }

    companion object {
        private const val MAX_NEARBY_KEYS = 1
    }
}
