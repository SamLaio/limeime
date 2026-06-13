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

import android.text.method.MetaKeyKeyListener
import android.view.KeyEvent

abstract class LIMEMetaKeyKeyListener : MetaKeyKeyListener() {
    companion object {
        private const val LOCKED_SHIFT = 8

        const val META_CAP_LOCKED = KeyEvent.META_SHIFT_ON shl LOCKED_SHIFT
        const val META_ALT_LOCKED = KeyEvent.META_ALT_ON shl LOCKED_SHIFT
        const val META_SYM_LOCKED = KeyEvent.META_SYM_ON shl LOCKED_SHIFT
        const val META_SELECTING = 1 shl 16
        const val META_SHIFT_ON = MetaKeyKeyListener.META_SHIFT_ON
        const val META_ALT_ON = MetaKeyKeyListener.META_ALT_ON
        const val META_SYM_ON = MetaKeyKeyListener.META_SYM_ON

        private const val USED_SHIFT = 24
        private const val PRESSED_SHIFT = 32
        private const val RELEASED_SHIFT = 40

        private const val META_CAP_USED = KeyEvent.META_SHIFT_ON.toLong() shl USED_SHIFT
        private const val META_ALT_USED = KeyEvent.META_ALT_ON.toLong() shl USED_SHIFT
        private const val META_SYM_USED = KeyEvent.META_SYM_ON.toLong() shl USED_SHIFT
        private const val META_CAP_PRESSED = KeyEvent.META_SHIFT_ON.toLong() shl PRESSED_SHIFT
        private const val META_ALT_PRESSED = KeyEvent.META_ALT_ON.toLong() shl PRESSED_SHIFT
        private const val META_SYM_PRESSED = KeyEvent.META_SYM_ON.toLong() shl PRESSED_SHIFT
        private const val META_CAP_RELEASED = KeyEvent.META_SHIFT_ON.toLong() shl RELEASED_SHIFT
        private const val META_ALT_RELEASED = KeyEvent.META_ALT_ON.toLong() shl RELEASED_SHIFT
        private const val META_SYM_RELEASED = KeyEvent.META_SYM_ON.toLong() shl RELEASED_SHIFT

        private const val META_SHIFT_MASK = MetaKeyKeyListener.META_SHIFT_ON.toLong() or
            META_CAP_LOCKED.toLong() or META_CAP_USED or META_CAP_PRESSED or META_CAP_RELEASED
        private const val META_ALT_MASK = MetaKeyKeyListener.META_ALT_ON.toLong() or
            META_ALT_LOCKED.toLong() or META_ALT_USED or META_ALT_PRESSED or META_ALT_RELEASED
        private const val META_SYM_MASK = MetaKeyKeyListener.META_SYM_ON.toLong() or
            META_SYM_LOCKED.toLong() or META_SYM_USED or META_SYM_PRESSED or META_SYM_RELEASED

        @Suppress("unused")
        private fun adjust(state: Long, what: Int, mask: Long): Long {
            return if (state and (what.toLong() shl PRESSED_SHIFT) != 0L) {
                state and mask.inv() or what.toLong() or (what.toLong() shl USED_SHIFT)
            } else if (state and (what.toLong() shl RELEASED_SHIFT) != 0L) {
                state and mask.inv()
            } else {
                state
            }
        }

        fun getMetaState(state: Long): Int = MetaKeyKeyListener.getMetaState(state)

        fun getMetaState(state: Long, meta: Int): Int = MetaKeyKeyListener.getMetaState(state, meta)

        @Suppress("ACCIDENTAL_OVERRIDE")
        @JvmStatic
        fun handleKeyDown(state: Long, keyCode: Int, event: KeyEvent): Long {
            return MetaKeyKeyListener.handleKeyDown(state, keyCode, event)
        }

        @Suppress("ACCIDENTAL_OVERRIDE")
        @JvmStatic
        fun handleKeyUp(state: Long, keyCode: Int, event: KeyEvent): Long {
            return MetaKeyKeyListener.handleKeyUp(state, keyCode, event)
        }

        @Suppress("ACCIDENTAL_OVERRIDE")
        @JvmStatic
        fun adjustMetaAfterKeypress(state: Long): Long = MetaKeyKeyListener.adjustMetaAfterKeypress(state)

        @Suppress("unused")
        private fun press(state: Long, what: Int, mask: Long): Long {
            var newState = state
            newState = if (newState and (what.toLong() shl RELEASED_SHIFT) != 0L) {
                newState and mask.inv() or what.toLong() or (what.toLong() shl LOCKED_SHIFT)
            } else if (newState and (what.toLong() shl LOCKED_SHIFT) != 0L) {
                newState and mask.inv()
            } else {
                (newState or what.toLong() or (what.toLong() shl PRESSED_SHIFT)) and
                    (what.toLong() shl RELEASED_SHIFT).inv()
            }
            return newState
        }

        @Suppress("unused")
        private fun release(state: Long, what: Int, mask: Long): Long {
            var newState = state
            if (newState and (what.toLong() shl USED_SHIFT) != 0L) {
                newState = newState and mask.inv()
            } else if (newState and (what.toLong() shl PRESSED_SHIFT) != 0L) {
                newState = (newState or what.toLong() or (what.toLong() shl RELEASED_SHIFT)) and
                    (what.toLong() shl PRESSED_SHIFT).inv()
            }
            return newState
        }
    }
}
