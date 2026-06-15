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

import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.inputmethod.EditorInfo
import net.toload.main.hd.LIMEKeyboardSwitcher
import net.toload.main.hd.R

/**
 * @author Art Hung
 */
class LIMEKeyboard(
    context: Context,
    xmlLayoutResId: Int,
    mode: Int,
    keySizeScale: Float,
    showArrowKeys: Int,
    splitKeyboard: Int
) : LIMEBaseKeyboard(context, xmlLayoutResId, mode, keySizeScale, showArrowKeys, splitKeyboard) {
    private var mShiftKey: Key? = null
    private var mEnterKey: Key? = null
    private var mShiftState = SHIFT_OFF
    private var mThemedIconLoaded = false
    private var mCurrentlyInSpace = false

    init {
        if (DEBUG) Log.i(TAG, "LIMEKeyboard()")
    }

    private fun loadThemedIcons(context: Context) {
        if (DEBUG) Log.i(TAG, "loadThemedIcons()")

        val styledAttributes = context.theme.obtainStyledAttributes(
            null,
            R.styleable.LIMEKeyboard,
            R.attr.LIMEKeyboardStyle,
            R.style.LIMEKeyboard
        )
        try {
            val a = styledAttributes
            mSpaceKeyIcon = a.getDrawable(R.styleable.LIMEKeyboard_spaceKeyIcon)
            mSpaceKeyPreviewIcon = a.getDrawable(R.styleable.LIMEKeyboard_spaceKeyPreviewIcon)
            mEnterKeyIcon = a.getDrawable(R.styleable.LIMEKeyboard_enterKeyIcon)
            mSearchKeyIcon = a.getDrawable(R.styleable.LIMEKeyboard_searchKeyIcon)
            mDoneKeyIcon = a.getDrawable(R.styleable.LIMEKeyboard_doneKeyIcon)
            mDeleteKeyIcon = a.getDrawable(R.styleable.LIMEKeyboard_deleteKeyIcon)
            mShiftKeyIcon = a.getDrawable(R.styleable.LIMEKeyboard_shiftKeyIcon)
            mShiftKeyShiftedIcon = a.getDrawable(R.styleable.LIMEKeyboard_shiftKeyShiftedIcon)
            mSpaceKeyVerticalCorrection =
                a.getDimensionPixelSize(R.styleable.LIMEKeyboard_spaceKeyVerticalCorrection, 0)
        } finally {
            styledAttributes.recycle()
        }
    }

    override fun createKeyFromXml(context: Context, parent: Row, x: Int, y: Int, parser: XmlResourceParser?): Key {
        if (DEBUG) Log.i(TAG, "createKeyFromXml() mThemedIconLoaded = $mThemedIconLoaded")

        if (!mThemedIconLoaded) {
            loadThemedIcons(context)
            mThemedIconLoaded = true
        }

        val key: Key = LIMEKey(context.resources, parent, x, y, parser)
        if (key.codes.isEmpty()) {
            return key
        }
        when (key.codes[0]) {
            KEYCODE_ENTER -> {
                mEnterKey = key
                if (mEnterKeyIcon != null) key.icon = mEnterKeyIcon
            }
            KEYCODE_SPACE -> {
                if (mSpaceKeyIcon != null) key.icon = mSpaceKeyIcon
                if (mSpaceKeyPreviewIcon != null) key.iconPreview = mSpaceKeyPreviewIcon
            }
            KEYCODE_DELETE -> {
                if (mDeleteKeyIcon != null) key.icon = mDeleteKeyIcon
            }
            KEYCODE_DONE -> {
                if (mDoneKeyIcon != null) key.icon = mDoneKeyIcon
            }
            KEYCODE_SHIFT -> {
                if (mShiftKeyIcon != null) {
                    key.icon = if (isShifted) mShiftKeyShiftedIcon else mShiftKeyIcon
                    mShiftKey = key
                }
            }
        }

        return key
    }

    fun enableShiftLock() {
        val shiftKey = mShiftKey
        if (shiftKey is LIMEKey) {
            shiftKey.enableShiftLock()
        }
    }

    fun setShiftLocked(shiftLocked: Boolean) {
        if (DEBUG) Log.i(TAG, "setShiftLocked: $shiftLocked")
        val shiftKey = mShiftKey
        if (shiftKey != null) {
            if (shiftLocked) {
                shiftKey.on = true
                mShiftState = SHIFT_LOCKED
            } else {
                shiftKey.on = false
                mShiftState = SHIFT_ON
            }
        }
    }

    fun isShiftLocked(): Boolean = mShiftState == SHIFT_LOCKED

    override fun setShifted(shiftState: Boolean): Boolean {
        if (DEBUG) Log.i(TAG, "setShifted: $shiftState")
        var shiftChanged = false
        val shiftKey = mShiftKey
        if (shiftKey != null) {
            if (!shiftState) {
                shiftChanged = mShiftState != SHIFT_OFF
                mShiftState = SHIFT_OFF
                shiftKey.on = false
                shiftKey.icon = mShiftKeyIcon
            } else {
                if (mShiftState == SHIFT_OFF) {
                    shiftChanged = true
                    mShiftState = SHIFT_ON
                }
                shiftKey.icon = mShiftKeyShiftedIcon
            }
            shiftKey.icon?.invalidateSelf()
        } else {
            return super.setShifted(shiftState)
        }
        return shiftChanged
    }

    override var isShifted: Boolean
        get() = if (mShiftKey != null) {
            mShiftState != SHIFT_OFF
        } else {
            super.isShifted
        }
        protected set(value) {
            super.isShifted = value
        }

    fun setImeOptions(res: Resources, options: Int) {
        setImeOptions(res, LIMEKeyboardSwitcher.MODE_TEXT, options)
    }

    fun setImeOptions(res: Resources, mode: Int, options: Int) {
        val enterKey = mEnterKey
        if (enterKey != null) {
            enterKey.popupCharacters = null
            enterKey.popupResId = 0
            enterKey.text = null
            when (options and (EditorInfo.IME_MASK_ACTION or EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
                EditorInfo.IME_ACTION_GO -> {
                    enterKey.iconPreview = null
                    enterKey.icon = null
                    enterKey.label = res.getText(R.string.label_go_key)
                }
                EditorInfo.IME_ACTION_NEXT -> {
                    enterKey.iconPreview = null
                    enterKey.icon = null
                    enterKey.label = res.getText(R.string.label_next_key)
                }
                EditorInfo.IME_ACTION_DONE -> {
                    enterKey.iconPreview = null
                    enterKey.icon = null
                    enterKey.label = res.getText(R.string.label_done_key)
                }
                EditorInfo.IME_ACTION_SEARCH -> {
                    enterKey.icon = mSearchKeyIcon
                    enterKey.label = null
                }
                EditorInfo.IME_ACTION_SEND -> {
                    enterKey.iconPreview = null
                    enterKey.icon = null
                    enterKey.label = res.getText(R.string.label_send_key)
                }
                else -> {
                    if (mode == LIMEKeyboardSwitcher.MODE_IM) {
                        enterKey.icon = null
                        enterKey.label = ":-)"
                        enterKey.popupResId = R.xml.popup_smileys
                    } else {
                        enterKey.icon = mEnterKeyIcon
                        enterKey.label = null
                    }
                }
            }
            val iconPreview = enterKey.iconPreview
            if (iconPreview != null) {
                iconPreview.setBounds(
                    0,
                    0,
                    enterKey.height * iconPreview.intrinsicWidth / iconPreview.intrinsicHeight,
                    enterKey.height
                )
            }
        }
    }

    fun isInside(key: LIMEKey, x: Int, y: Int): Boolean {
        var touchX = x
        var touchY = y
        if (DEBUG) {
            Log.i(
                TAG,
                "isInside(), keycode = " + key.codes[0] + ". x=" + touchX + ". y=" + touchY +
                    ". mSpaceDragStartX=" + mSpaceDragStartX +
                    ". mSpaceDragLastDiff=" + mSpaceDragLastDiff
            )
        }
        val code = key.codes[0]
        if (code == KEYCODE_SHIFT || code == KEYCODE_DELETE) {
            touchY -= key.height / KEY_POSITION_ADJUSTMENT_DIVISOR
            if (code == KEYCODE_SHIFT) touchX += key.width / 6
            if (code == KEYCODE_DELETE) touchX -= key.width / 6
        } else if (code == KEYCODE_SPACE) {
            touchY += mSpaceKeyVerticalCorrection

            if (mCurrentlyInSpace) {
                mSpaceDragLastDiff = touchX - mSpaceDragStartX
                return true
            } else {
                val insideSpace = key.isInsideSuper(touchX, touchY)
                if (insideSpace) {
                    mCurrentlyInSpace = true
                    mSpaceDragStartX = touchX
                    mSpaceDragLastDiff = 0
                }
                return insideSpace
            }
        }

        return !mCurrentlyInSpace && key.isInsideSuper(touchX, touchY)
    }

    fun keyReleased() {
        mCurrentlyInSpace = false
        mSpaceDragLastDiff = 0
    }

    val spaceDragDiff: Int
        get() = mSpaceDragLastDiff

    inner class LIMEKey(res: Resources, parent: Row, x: Int, y: Int, parser: XmlResourceParser?) :
        LIMEBaseKeyboard.Key(res, parent, x, y, parser) {
        private var mShiftLockEnabled = false

        init {
            if (DEBUG) Log.i(TAG, "LIMEKey():" + codes[0])
            val popupChars = popupCharacters
            if (popupChars != null && popupChars.length == 0) {
                popupResId = 0
            }
        }

        override fun onReleased(inside: Boolean) {
            if (!mShiftLockEnabled) {
                super.onReleased(inside)
            } else {
                pressed = !pressed
            }
        }

        fun enableShiftLock() {
            mShiftLockEnabled = true
        }

        override fun isInside(x: Int, y: Int): Boolean {
            return this@LIMEKeyboard.isInside(this, x, y)
        }

        fun isInsideSuper(x: Int, y: Int): Boolean {
            return super.isInside(x, y)
        }
    }

    fun setKeyboardSwitcher(keyboardswitcher: LIMEKeyboardSwitcher?) {
    }

    companion object {
        private const val DEBUG = false
        private const val TAG = "LIMEKeyboard"

        private const val SHIFT_OFF = 0
        private const val SHIFT_ON = 1
        private const val SHIFT_LOCKED = 2
        private const val KEY_POSITION_ADJUSTMENT_DIVISOR = 10

        private var mSpaceKeyIcon: Drawable? = null
        private var mSpaceKeyPreviewIcon: Drawable? = null
        private var mEnterKeyIcon: Drawable? = null
        private var mDeleteKeyIcon: Drawable? = null
        private var mShiftKeyIcon: Drawable? = null
        private var mShiftKeyShiftedIcon: Drawable? = null
        private var mDoneKeyIcon: Drawable? = null
        private var mSearchKeyIcon: Drawable? = null
        private var mSpaceKeyVerticalCorrection = 0
        private var mSpaceDragStartX = 0
        private var mSpaceDragLastDiff = 0
    }
}
