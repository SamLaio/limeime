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

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import kotlin.math.abs
import net.toload.main.hd.keyboard.LIMEBaseKeyboard.Key
import net.toload.main.hd.R

class LIMEKeyboardView : LIMEKeyboardBaseView {
    private val mKeyHeight: Int
    private val mSpaceCaretDeadZone: Int
    private val mSpaceCaretStepPx: Int
    private var mSpaceCaretPointerId = -1
    private var mSpaceCaretStartX = 0
    private var mLastSpaceCaretStep = 0
    private var mSpaceCaretMoved = false
    private var mSpaceCaretCancelled = false

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        mKeyHeight = context.resources.getDimensionPixelSize(R.dimen.key_height)
        mSpaceCaretDeadZone = context.resources.getDimensionPixelSize(R.dimen.space_caret_dead_zone)
        mSpaceCaretStepPx = context.resources.getDimensionPixelSize(R.dimen.space_caret_step)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        mKeyHeight = context.resources.getDimensionPixelSize(R.dimen.key_height)
        mSpaceCaretDeadZone = context.resources.getDimensionPixelSize(R.dimen.space_caret_dead_zone)
        mSpaceCaretStepPx = context.resources.getDimensionPixelSize(R.dimen.space_caret_step)
    }

    override fun onLongPress(key: Key): Boolean {
        if (DEBUG) {
            Log.i(
                TAG,
                "onLongPress, keycode = " + key.codes[0] +
                    "; spaceDragDiff = " + (keyboard as LIMEKeyboard).spaceDragDiff +
                    "; key_height = " + mKeyHeight
            )
        }
        return if (key.codes[0] == LIMEBaseKeyboard.KEYCODE_DONE) {
            keyboardActionListener.onKey(KEYCODE_OPTIONS, null, 0, 0)
            true
        } else if (key.codes[0] == LIMEBaseKeyboard.KEYCODE_SPACE &&
            abs((keyboard as LIMEKeyboard).spaceDragDiff) < mKeyHeight / 5
        ) {
            keyboardActionListener.onKey(KEYCODE_SPACE_LONGPRESS, null, 0, 0)
            true
        } else {
            super.onLongPress(key)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(me: MotionEvent): Boolean {
        if (DEBUG) Log.i(TAG, "OnTouchEvent(), me.getActionMasked() =" + me.actionMasked)
        val limeKeyboard = keyboard as LIMEKeyboard
        val action = me.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            if (DEBUG) Log.i(TAG, "OnTouchEvent(), ACTION_DOWN")
            limeKeyboard.keyReleased()
            mSpaceCaretPointerId = me.getPointerId(0)
            mSpaceCaretStartX = me.getX(0).toInt()
            mLastSpaceCaretStep = 0
            mSpaceCaretMoved = false
            mSpaceCaretCancelled = false
            if (!isTouchOnSpaceKey(me.getX(0).toInt(), me.getY(0).toInt())) {
                mSpaceCaretPointerId = -1
            }
        }

        if (action == MotionEvent.ACTION_MOVE && handleSpaceCaretMove(me, limeKeyboard)) {
            return true
        }

        if (isEndingActiveSpaceCaret(action, me)) {
            val consumed = mSpaceCaretMoved
            resetSpaceCaretState()
            if (consumed) {
                limeKeyboard.keyReleased()
                return true
            }
        }

        return super.onTouchEvent(me)
    }

    private fun handleSpaceCaretMove(me: MotionEvent, limeKeyboard: LIMEKeyboard): Boolean {
        if (mSpaceCaretPointerId == -1) {
            return false
        }
        val pointerIndex = me.findPointerIndex(mSpaceCaretPointerId)
        if (pointerIndex < 0) {
            return false
        }

        val dx = me.getX(pointerIndex).toInt() - mSpaceCaretStartX
        if (abs(dx) < mSpaceCaretDeadZone) {
            return false
        }

        if (!mSpaceCaretCancelled) {
            mSpaceCaretCancelled = true
            mSpaceCaretMoved = true
            val cancelEvent = MotionEvent.obtain(me)
            cancelEvent.action = MotionEvent.ACTION_CANCEL
            super.onTouchEvent(cancelEvent)
            cancelEvent.recycle()
            limeKeyboard.keyReleased()
        }

        val step = (if (dx < 0) -1 else 1) * stepsForSpaceDisplacement(abs(dx))
        val delta = step - mLastSpaceCaretStep
        if (delta != 0) {
            mLastSpaceCaretStep = step
            mSpaceCaretMoved = true
            keyboardActionListener.moveCaretBy(delta)
        }
        return true
    }

    private fun isEndingActiveSpaceCaret(action: Int, me: MotionEvent): Boolean {
        if (mSpaceCaretPointerId == -1) {
            return false
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            return true
        }
        if (action == MotionEvent.ACTION_POINTER_UP) {
            return me.getPointerId(me.actionIndex) == mSpaceCaretPointerId
        }
        return false
    }

    private fun resetSpaceCaretState() {
        mSpaceCaretPointerId = -1
        mSpaceCaretStartX = 0
        mLastSpaceCaretStep = 0
        mSpaceCaretMoved = false
        mSpaceCaretCancelled = false
    }

    private fun isTouchOnSpaceKey(x: Int, y: Int): Boolean {
        val currentKeyboard = keyboard ?: return false
        for (key in currentKeyboard.keys) {
            if (key.codes != null && key.codes.isNotEmpty() &&
                key.codes[0] == LIMEBaseKeyboard.KEYCODE_SPACE &&
                x >= key.x && x < key.x + key.width &&
                y >= key.y && y < key.y + key.height
            ) {
                return true
            }
        }
        return false
    }

    private fun stepsForSpaceDisplacement(absDx: Int): Int {
        val travel = absDx - mSpaceCaretDeadZone
        if (travel <= 0) {
            return 0
        }

        val density = resources.displayMetrics.density
        val t1 = 60f * density
        val t2 = 140f * density
        val step = mSpaceCaretStepPx.toFloat()

        val steps = if (travel <= t1) {
            travel / step
        } else if (travel <= t2) {
            t1 / step + (travel - t1) / (step / 2f)
        } else {
            t1 / step + (t2 - t1) / (step / 2f) + (travel - t2) / (step / 4f)
        }
        return steps.toInt()
    }

    companion object {
        private const val DEBUG = false
        private const val TAG = "LIMEKeyboardView"

        const val KEYCODE_OPTIONS = -100
        const val KEYCODE_SPACE_LONGPRESS = -102
        const val KEYCODE_NEXT_IM = -104
        const val KEYCODE_PREV_IM = -105
    }
}
