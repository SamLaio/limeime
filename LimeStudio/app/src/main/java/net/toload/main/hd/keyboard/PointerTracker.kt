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

import android.content.res.Resources
import android.util.Log
import android.view.MotionEvent
import kotlin.math.min
import net.toload.main.hd.keyboard.LIMEBaseKeyboard.Key
import net.toload.main.hd.keyboard.LIMEKeyboardBaseView.OnKeyboardActionListener
import net.toload.main.hd.R

class PointerTracker(
    @JvmField val mPointerId: Int,
    private val mHandler: LIMEKeyboardBaseView.UIHandler,
    private val mKeyDetector: KeyDetector,
    private val mProxy: UIProxy,
    res: Resources
) {
    interface UIProxy {
        fun invalidateKey(key: Key?)
        fun showPreview(keyIndex: Int, tracker: PointerTracker?)
        fun hasDistinctMultitouch(): Boolean
    }

    private val mDelayBeforeKeyRepeatStart: Int
    private val mLongPressKeyTimeout: Int
    private val mMultiTapKeyTimeout: Int
    private var mListener: OnKeyboardActionListener? = null
    private val mHasDistinctMultitouch: Boolean
    private lateinit var mKeys: Array<Key>
    private var mKeyHysteresisDistanceSquared = -1
    private val mKeyState: KeyState = KeyState(mKeyDetector)
    private var mKeyboardLayoutHasBeenChanged = false
    private var mKeyAlreadyProcessed = false
    private var mIsRepeatableKey = false
    private var mIsInSlidingKeyInput = false
    private var mLastSentIndex = NOT_A_KEY
    private var mTapCount = 0
    private var mLastTapTime = 0L
    private var mInMultiTap = false
    private val mPreviewLabel = StringBuilder(1)
    private var mPreviousKey = NOT_A_KEY

    init {
        mHasDistinctMultitouch = mProxy.hasDistinctMultitouch()
        mDelayBeforeKeyRepeatStart = res.getInteger(R.integer.config_delay_before_key_repeat_start)
        mLongPressKeyTimeout = res.getInteger(R.integer.config_long_press_key_timeout)
        mMultiTapKeyTimeout = res.getInteger(R.integer.config_multi_tap_key_timeout)
        resetMultiTap()
    }

    fun setOnKeyboardActionListener(listener: OnKeyboardActionListener?) {
        mListener = listener
    }

    fun setKeyboard(keys: Array<Key>?, keyHysteresisDistance: Float) {
        if (keys == null || keyHysteresisDistance < 0) {
            throw IllegalArgumentException()
        }
        mKeys = keys
        mKeyHysteresisDistanceSquared = (keyHysteresisDistance * keyHysteresisDistance).toInt()
        mKeyboardLayoutHasBeenChanged = true
    }

    fun isInSlidingKeyInput(): Boolean = mIsInSlidingKeyInput

    private fun isValidKeyIndex(keyIndex: Int): Boolean {
        return keyIndex >= 0 && keyIndex < mKeys.size
    }

    fun getKey(keyIndex: Int): Key? {
        return if (isValidKeyIndex(keyIndex)) mKeys[keyIndex] else null
    }

    private fun isModifierInternal(keyIndex: Int): Boolean {
        val key = getKey(keyIndex) ?: return false
        val primaryCode = key.codes[0]
        return primaryCode == LIMEBaseKeyboard.KEYCODE_SHIFT ||
            primaryCode == LIMEBaseKeyboard.KEYCODE_MODE_CHANGE
    }

    fun isModifier(): Boolean = isModifierInternal(mKeyState.keyIndex)

    fun isFunctionalKey(): Boolean {
        val key = getKey(mKeyState.keyIndex) ?: return false
        return key.isFunctionalKey
    }

    fun isOnModifierKey(x: Int, y: Int): Boolean {
        return isModifierInternal(mKeyDetector.getKeyIndexAndNearbyCodes(x, y, null))
    }

    fun isSpaceKey(keyIndex: Int): Boolean {
        val key = getKey(keyIndex)
        return key != null && key.codes[0] == ' '.code
    }

    fun updateKey(keyIndex: Int) {
        if (mKeyAlreadyProcessed) return
        val oldKeyIndex = mPreviousKey
        mPreviousKey = keyIndex
        if (keyIndex != oldKeyIndex) {
            if (isValidKeyIndex(oldKeyIndex)) {
                val inside = keyIndex == NOT_A_KEY
                mKeys[oldKeyIndex].onReleased(inside)
                mProxy.invalidateKey(mKeys[oldKeyIndex])
            }
            if (isValidKeyIndex(keyIndex)) {
                mKeys[keyIndex].onPressed()
                mProxy.invalidateKey(mKeys[keyIndex])
            }
        }
    }

    fun setAlreadyProcessed() {
        mKeyAlreadyProcessed = true
    }

    fun onTouchEvent(action: Int, x: Int, y: Int, eventTime: Long) {
        when (action) {
            MotionEvent.ACTION_MOVE -> onMoveEvent(x, y, eventTime)
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> onDownEvent(x, y, eventTime)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> onUpEvent(x, y, eventTime)
            MotionEvent.ACTION_CANCEL -> onCancelEvent(x, y, eventTime)
        }
    }

    fun onDownEvent(x: Int, y: Int, eventTime: Long) {
        if (DEBUG) Log.i(TAG, "onDownEvent(): x = $x, y=$y")
        if (DEBUG_MOVE) debugLog("onDownEvent:", x, y)
        var keyIndex = mKeyState.onDownKey(x, y, eventTime)
        mKeyboardLayoutHasBeenChanged = false
        mKeyAlreadyProcessed = false
        mIsRepeatableKey = false
        mIsInSlidingKeyInput = false
        checkMultiTap(eventTime, keyIndex)
        val listener = mListener
        if (listener != null && isValidKeyIndex(keyIndex)) {
            listener.onPress(mKeys[keyIndex].codes[0])
            if (mKeyboardLayoutHasBeenChanged) {
                mKeyboardLayoutHasBeenChanged = false
                keyIndex = mKeyState.onDownKey(x, y, eventTime)
            }
        }
        if (isValidKeyIndex(keyIndex)) {
            if (mKeys[keyIndex].repeatable && mKeys[keyIndex].codes[0] != LIMEBaseKeyboard.KEYCODE_SPACE) {
                repeatKey(keyIndex)
                mHandler.startKeyRepeatTimer(mDelayBeforeKeyRepeatStart.toLong(), keyIndex, this)
                mIsRepeatableKey = true
            }
            startLongPressTimer(keyIndex)
        }
        showKeyPreviewAndUpdateKey(keyIndex)
    }

    fun onMoveEvent(x: Int, y: Int, eventTime: Long) {
        if (DEBUG) Log.i(TAG, "onMoveEvent(): x = $x, y=$y")
        if (DEBUG_MOVE) debugLog("onMoveEvent:", x, y)
        if (mKeyAlreadyProcessed) return
        val keyState = mKeyState
        var keyIndex = keyState.onMoveKey(x, y)
        val oldKey = getKey(keyState.keyIndex)
        if (isValidKeyIndex(keyIndex)) {
            if (oldKey == null) {
                val listener = mListener
                if (listener != null) {
                    listener.onPress(getKey(keyIndex)!!.codes[0])
                    if (mKeyboardLayoutHasBeenChanged) {
                        mKeyboardLayoutHasBeenChanged = false
                        keyIndex = keyState.onMoveKey(x, y)
                    }
                }
                keyState.onMoveToNewKey(keyIndex, x, y)
                startLongPressTimer(keyIndex)
            } else if (!isMinorMoveBounce(x, y, keyIndex)) {
                mIsInSlidingKeyInput = true
                mListener?.onRelease(oldKey.codes[0])
                resetMultiTap()
                val listener = mListener
                if (listener != null) {
                    listener.onPress(getKey(keyIndex)!!.codes[0])
                    if (mKeyboardLayoutHasBeenChanged) {
                        mKeyboardLayoutHasBeenChanged = false
                        keyIndex = keyState.onMoveKey(x, y)
                    }
                }
                keyState.onMoveToNewKey(keyIndex, x, y)
                startLongPressTimer(keyIndex)
            }
        } else {
            if (oldKey != null && !isMinorMoveBounce(x, y, keyIndex)) {
                mIsInSlidingKeyInput = true
                mListener?.onRelease(oldKey.codes[0])
                resetMultiTap()
                keyState.onMoveToNewKey(keyIndex, x, y)
                mHandler.cancelLongPressTimer()
            }
        }
        showKeyPreviewAndUpdateKey(keyState.keyIndex)
    }

    fun onUpEvent(xInput: Int, yInput: Int, eventTime: Long) {
        var x = xInput
        var y = yInput
        if (DEBUG) Log.i(TAG, "onUpEvent(): x = $x, y=$y")
        if (DEBUG_MOVE) debugLog("onUpEvent  :", x, y)
        mHandler.cancelKeyTimers()
        showKeyPreviewAndUpdateKey(NOT_A_KEY)
        mIsInSlidingKeyInput = false
        if (mKeyAlreadyProcessed) return
        var keyIndex = mKeyState.onUpKey(x, y)
        if (isMinorMoveBounce(x, y, keyIndex)) {
            keyIndex = mKeyState.keyIndex
            x = mKeyState.keyX
            y = mKeyState.keyY
        }
        if (!mIsRepeatableKey) {
            detectAndSendKey(keyIndex, x, y, eventTime)
        }

        if (isValidKeyIndex(keyIndex)) {
            mProxy.invalidateKey(mKeys[keyIndex])
        }
    }

    fun onCancelEvent(x: Int, y: Int, eventTime: Long) {
        if (DEBUG) Log.i(TAG, "onCancelEvent(): x = $x, y=$y")
        if (DEBUG_MOVE) debugLog("onCancelEvent(): ", x, y)
        mHandler.cancelKeyTimers()
        mHandler.cancelPopupPreview()
        showKeyPreviewAndUpdateKey(NOT_A_KEY)
        mIsInSlidingKeyInput = false
        val keyIndex = mKeyState.keyIndex
        if (isValidKeyIndex(keyIndex)) {
            mProxy.invalidateKey(mKeys[keyIndex])
        }
    }

    fun repeatKey(keyIndex: Int) {
        val key = getKey(keyIndex)
        if (key != null) {
            detectAndSendKey(keyIndex, key.x, key.y, -1)
        }
    }

    fun getLastX(): Int = mKeyState.lastX

    fun getLastY(): Int = mKeyState.lastY

    fun getDownTime(): Long = mKeyState.downTime

    fun getStartX(): Int = mKeyState.startX

    fun getStartY(): Int = mKeyState.startY

    private fun isMinorMoveBounce(x: Int, y: Int, newKey: Int): Boolean {
        if (!::mKeys.isInitialized || mKeyHysteresisDistanceSquared < 0) {
            throw IllegalStateException("keyboard and/or hysteresis not set")
        }
        val curKey = mKeyState.keyIndex
        return if (newKey == curKey) {
            true
        } else if (isValidKeyIndex(curKey)) {
            getSquareDistanceToKeyEdge(x, y, mKeys[curKey]) < mKeyHysteresisDistanceSquared
        } else {
            false
        }
    }

    private fun showKeyPreviewAndUpdateKey(keyIndex: Int) {
        if (DEBUG) Log.i(TAG, "showKeyPreviewAndUpdateKey() keyIndex=$keyIndex, isModifier() = " + isModifier())
        updateKey(keyIndex)
        if (mHasDistinctMultitouch && isFunctionalKey() && !isSpaceKey(keyIndex)) {
            mProxy.showPreview(NOT_A_KEY, this)
        } else {
            mProxy.showPreview(keyIndex, this)
        }
    }

    private fun startLongPressTimer(keyIndex: Int) {
        mHandler.startLongPressTimer(mLongPressKeyTimeout.toLong(), keyIndex, this)
    }

    private fun detectAndSendKey(index: Int, x: Int, y: Int, eventTime: Long) {
        val listener = mListener
        val key = getKey(index)

        if (key == null) {
            listener?.onCancel()
        } else {
            if (key.text != null) {
                if (listener != null) {
                    listener.onText(key.text)
                    listener.onRelease(0)
                }
            } else {
                var code = key.codes[0]
                val codes = mKeyDetector.newCodeArray()
                mKeyDetector.getKeyIndexAndNearbyCodes(x, y, codes)
                if (mInMultiTap) {
                    if (mTapCount != -1) {
                        listener?.onKey(LIMEBaseKeyboard.KEYCODE_DELETE, KEY_DELETE, x, y)
                    } else {
                        mTapCount = 0
                    }
                    code = key.codes[mTapCount]
                }
                if (codes.size >= 2 && codes[0] != code && codes[1] == code) {
                    codes[1] = codes[0]
                    codes[0] = code
                }
                if (listener != null) {
                    listener.onKey(code, codes, x, y)
                    listener.onRelease(code)
                }
            }
            mLastSentIndex = index
            mLastTapTime = eventTime
        }
    }

    fun getPreviewText(key: Key): CharSequence? {
        return if (mInMultiTap) {
            mPreviewLabel.setLength(0)
            mPreviewLabel.append(key.codes[mTapCount.coerceAtLeast(0)].toChar())
            mPreviewLabel
        } else {
            key.label
        }
    }

    private fun resetMultiTap() {
        mLastSentIndex = NOT_A_KEY
        mTapCount = 0
        mLastTapTime = -1
        mInMultiTap = false
    }

    private fun checkMultiTap(eventTime: Long, keyIndex: Int) {
        val key = getKey(keyIndex) ?: return

        val isMultiTap = eventTime < mLastTapTime + mMultiTapKeyTimeout && keyIndex == mLastSentIndex
        if (key.codes.size > 1) {
            mInMultiTap = true
            mTapCount = if (isMultiTap) {
                (mTapCount + 1) % key.codes.size
            } else {
                -1
            }
            return
        }
        if (!isMultiTap) {
            resetMultiTap()
        }
    }

    private fun debugLog(title: String, x: Int, y: Int) {
        val keyIndex = mKeyDetector.getKeyIndexAndNearbyCodes(x, y, null)
        val key = getKey(keyIndex)
        val code = if (key == null) {
            "----"
        } else {
            val primaryCode = key.codes[0]
            String.format(if (primaryCode < 0) "%4d" else "0x%02x", primaryCode)
        }
        Log.d(
            TAG,
            String.format(
                "%s%s[%d] %3d,%3d %3d(%s) %s",
                title,
                if (mKeyAlreadyProcessed) "-" else " ",
                mPointerId,
                x,
                y,
                keyIndex,
                code,
                if (isModifier()) "modifier" else ""
            )
        )
    }

    private class KeyState(private val mKeyDetector: KeyDetector) {
        var startX = 0
            private set
        var startY = 0
            private set
        var downTime = 0L
            private set
        var keyIndex = NOT_A_KEY
            private set
        var keyX = 0
            private set
        var keyY = 0
            private set
        var lastX = 0
            private set
        var lastY = 0
            private set

        fun onDownKey(x: Int, y: Int, eventTime: Long): Int {
            startX = x
            startY = y
            downTime = eventTime
            return onMoveToNewKey(onMoveKeyInternal(x, y), x, y)
        }

        private fun onMoveKeyInternal(x: Int, y: Int): Int {
            lastX = x
            lastY = y
            return mKeyDetector.getKeyIndexAndNearbyCodes(x, y, null)
        }

        fun onMoveKey(x: Int, y: Int): Int = onMoveKeyInternal(x, y)

        fun onMoveToNewKey(keyIndex: Int, x: Int, y: Int): Int {
            this.keyIndex = keyIndex
            keyX = x
            keyY = y
            return keyIndex
        }

        fun onUpKey(x: Int, y: Int): Int = onMoveKeyInternal(x, y)
    }

    companion object {
        private const val TAG = "PointerTracker"
        private const val DEBUG = false
        private const val DEBUG_MOVE = false
        private val NOT_A_KEY = LIMEKeyboardBaseView.NOT_A_KEY
        private val KEY_DELETE = intArrayOf(LIMEBaseKeyboard.KEYCODE_DELETE)

        private fun getSquareDistanceToKeyEdge(x: Int, y: Int, key: Key): Int {
            val left = key.x
            val right = key.x + key.width
            val top = key.y
            val bottom = key.y + key.height
            val edgeX = if (x < left) left else min(x, right)
            val edgeY = if (y < top) top else min(y, bottom)
            val dx = x - edgeX
            val dy = y - edgeY
            return dx * dx + dy * dy
        }
    }
}
