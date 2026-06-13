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

abstract class KeyDetector {
    protected lateinit var mKeyboard: LIMEBaseKeyboard
    private var mKeys: Array<LIMEBaseKeyboard.Key>? = null
    @JvmField
    protected var mCorrectionX = 0
    @JvmField
    protected var mCorrectionY = 0
    @JvmField
    protected var mProximityCorrectOn = false
    @JvmField
    protected var mProximityThresholdSquare = 0

    fun setKeyboard(
        keyboard: LIMEBaseKeyboard?,
        correctionX: Float,
        correctionY: Float
    ): Array<LIMEBaseKeyboard.Key> {
        if (keyboard == null) {
            throw NullPointerException()
        }
        mCorrectionX = correctionX.toInt()
        mCorrectionY = correctionY.toInt()
        mKeyboard = keyboard
        val keys = mKeyboard.keys
        val array = keys.toTypedArray()
        mKeys = array
        return array
    }

    protected fun getTouchX(x: Int): Int = x + mCorrectionX

    protected fun getTouchY(y: Int): Int = y + mCorrectionY

    protected fun getKeys(): Array<LIMEBaseKeyboard.Key> {
        return mKeys ?: throw IllegalStateException("keyboard isn't set")
    }

    fun setProximityCorrectionEnabled(enabled: Boolean) {
        mProximityCorrectOn = enabled
    }

    fun isProximityCorrectionEnabled(): Boolean = mProximityCorrectOn

    fun setProximityThreshold(threshold: Int) {
        mProximityThresholdSquare = threshold * threshold
    }

    fun newCodeArray(): IntArray {
        val codes = IntArray(getMaxNearbyKeys())
        Arrays.fill(codes, LIMEKeyboardBaseView.NOT_A_KEY)
        return codes
    }

    protected abstract fun getMaxNearbyKeys(): Int

    abstract fun getKeyIndexAndNearbyCodes(x: Int, y: Int, allKeys: IntArray?): Int
}
