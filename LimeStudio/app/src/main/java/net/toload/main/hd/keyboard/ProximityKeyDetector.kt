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

import java.util.Arrays
import net.toload.main.hd.keyboard.LIMEBaseKeyboard.Key

class ProximityKeyDetector : KeyDetector() {
    private val mDistances = IntArray(MAX_NEARBY_KEYS)

    override fun getMaxNearbyKeys(): Int = MAX_NEARBY_KEYS

    override fun getKeyIndexAndNearbyCodes(x: Int, y: Int, allKeys: IntArray?): Int {
        val keys = getKeys()
        val touchX = getTouchX(x)
        val touchY = getTouchY(y)
        var primaryIndex = LIMEKeyboardBaseView.NOT_A_KEY
        var closestKey = LIMEKeyboardBaseView.NOT_A_KEY
        var closestKeyDist = mProximityThresholdSquare + 1
        val distances = mDistances
        Arrays.fill(distances, Int.MAX_VALUE)
        val nearestKeyIndices = mKeyboard.getNearestKeys(touchX, touchY) ?: return LIMEKeyboardBaseView.NOT_A_KEY
        for (nearestKeyIndex in nearestKeyIndices) {
            val key = keys[nearestKeyIndex]
            var dist = 0
            val isInside = key.isInside(touchX, touchY)
            if (isInside) {
                primaryIndex = nearestKeyIndex
            }

            if (((mProximityCorrectOn &&
                    key.squaredDistanceFrom(touchX, touchY).also { dist = it } < mProximityThresholdSquare) ||
                    isInside) &&
                key.codes[0] > 32
            ) {
                val nCodes = key.codes.size
                if (dist < closestKeyDist) {
                    closestKeyDist = dist
                    closestKey = nearestKeyIndex
                }

                if (allKeys == null) continue

                for (j in distances.indices) {
                    if (distances[j] > dist) {
                        System.arraycopy(distances, j, distances, j + nCodes, distances.size - j - nCodes)
                        System.arraycopy(allKeys, j, allKeys, j + nCodes, allKeys.size - j - nCodes)
                        System.arraycopy(key.codes, 0, allKeys, j, nCodes)
                        Arrays.fill(distances, j, j + nCodes, dist)
                        break
                    }
                }
            }
        }
        if (primaryIndex == LIMEKeyboardBaseView.NOT_A_KEY) {
            primaryIndex = closestKey
        }
        return primaryIndex
    }

    companion object {
        private const val MAX_NEARBY_KEYS = 12
    }
}
