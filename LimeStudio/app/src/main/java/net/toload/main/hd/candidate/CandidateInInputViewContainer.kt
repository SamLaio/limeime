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

package net.toload.main.hd.candidate

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import net.toload.main.hd.global.LIME
import net.toload.main.hd.LIMEService
import net.toload.main.hd.R

class CandidateInInputViewContainer(context: Context, attrs: AttributeSet?) :
    LinearLayout(context, attrs),
    View.OnClickListener {
    private var mDismissButton: ImageButton? = null
    private var mEmojiButton: ImageButton? = null
    private var mRightButton: ImageButton? = null
    private var mKeyboardButton: ImageButton? = null
    private var mActionRow: View? = null
    private var mRightButtonParent: View? = null
    private var mCandidateView: CandidateView? = null
    private var mService: LIMEService? = null
    private var mIdleToolsRevealReady = true
    private val mRevealIdleToolsRunnable = Runnable {
        mIdleToolsRevealReady = true
        requestLayout()
        updateCandidateViewWidthConstraint()
    }

    @JvmField
    var ctx: Context = context

    init {
        if (DEBUG) Log.i(TAG, "CandidateInInputViewContainer() constructor")
        clipChildren = false
        clipToPadding = false
    }

    fun initViews() {
        if (DEBUG) Log.i(TAG, "initViews()")
        if (mCandidateView == null) {
            val buttonRightExpand = findViewById<View>(R.id.candidate_right_parent)
            mRightButtonParent = buttonRightExpand
            if (buttonRightExpand is ViewGroup) {
                buttonRightExpand.clipChildren = false
                buttonRightExpand.clipToPadding = false
            }
            mDismissButton = findViewById(R.id.candidate_dismiss)
            mEmojiButton = findViewById(R.id.candidate_emoji)
            mRightButton = findViewById(R.id.candidate_right)
            mKeyboardButton = findViewById(R.id.candidate_keyboard)
            val emojiButton = mEmojiButton
            if (emojiButton != null && emojiButton.parent is View) {
                mActionRow = emojiButton.parent as View
            }

            mDismissButton?.setOnClickListener(this)
            mEmojiButton?.setOnClickListener(this)
            mRightButton?.setOnClickListener(this)
            mRightButtonParent?.setOnClickListener(this)
            mKeyboardButton?.setOnClickListener(this)
            if (DEBUG && mKeyboardButton == null) {
                Log.w(TAG, "Keyboard button not found!")
            }

            val candidateView = findViewById<CandidateView>(R.id.candidatesView)
            mCandidateView = candidateView

            candidateView.setBackgroundColor(candidateView.mColorBackground)
            mActionRow?.setBackgroundColor(actionRowBackgroundColor(candidateView.mColorBackground))
            mRightButtonParent?.setBackgroundColor(actionRowBackgroundColor(candidateView.mColorBackground))

            mDismissButton?.let { button ->
                button.setPadding(0, 0, 0, 0)
                button.scaleType = ImageView.ScaleType.CENTER
                button.minimumWidth = 0
                button.minimumHeight = 0
                button.setImageDrawable(candidateView.makeDismissButtonGlyph())
                button.setBackgroundColor(dismissButtonBackgroundColor())
                button.post { candidateView.storePopupDismissButtonWidth(button) }
            }
            mEmojiButton?.let { button ->
                button.setPadding(0, 0, 0, 0)
                button.scaleType = ImageView.ScaleType.CENTER
                button.minimumWidth = 0
                button.minimumHeight = 0
                button.clearColorFilter()
                button.setImageDrawable(candidateView.mDrawableEmojiInput)
                button.setBackgroundColor(actionButtonBackgroundColor())
            }
            mRightButton?.let { button ->
                button.setPadding(0, 0, 0, 0)
                button.scaleType = ImageView.ScaleType.CENTER
                button.minimumWidth = 0
                button.minimumHeight = 0
                button.setBackgroundColor(actionButtonBackgroundColor())
            }
            mKeyboardButton?.let { button ->
                button.setBackgroundColor(candidateView.mColorBackground)
                if (candidateView.mDrawableKeyboardShow != null) {
                    button.setImageDrawable(candidateView.mDrawableKeyboardShow)
                }
            }
        }
    }

    fun setService(service: LIMEService?) {
        mService = service
    }

    override fun requestLayout() {
        if (DEBUG) Log.i(TAG, "requestLayout()")

        val candidateView = mCandidateView
        if (candidateView != null) {
            val showKeyboardButton = mService != null && mService!!.isKeyboardViewHidden
            val isEmpty = candidateView.isEmpty
            val showIdleTools = updateIdleToolsRevealState(isEmpty)
            val showActiveChrome = shouldShowActiveChrome(isEmpty, showIdleTools, mIdleToolsRevealReady)

            mDismissButton?.visibility = if (showActiveChrome) View.VISIBLE else View.GONE
            mEmojiButton?.visibility = if (showIdleTools) View.VISIBLE else View.GONE
            mKeyboardButton?.visibility = if (showKeyboardButton) View.VISIBLE else View.GONE

            mRightButton?.let { button ->
                button.clearColorFilter()
                button.visibility = if (showIdleTools || showActiveChrome) View.VISIBLE else View.GONE
                if (showIdleTools) {
                    button.setImageDrawable(candidateView.mDrawableVoiceInput)
                } else {
                    val isKeyboardHidden = mService != null && mService!!.isKeyboardViewHidden
                    button.setImageDrawable(
                        if (shouldShowCollapseGlyph(isEmpty, candidateView.isCandidateExpanded, isKeyboardHidden)) {
                            candidateView.mDrawableExpandUpButton
                        } else {
                            candidateView.mDrawableExpandDownButton
                        }
                    )
                }
            }
            mRightButtonParent?.visibility = if (showIdleTools || showActiveChrome) View.VISIBLE else View.GONE
        }

        super.requestLayout()
    }

    private fun updateIdleToolsRevealState(isEmpty: Boolean): Boolean {
        val composingOrSearching = isComposingOrSearching()
        if (!isEmpty || composingOrSearching) {
            removeCallbacks(mRevealIdleToolsRunnable)
            mIdleToolsRevealReady = false
        } else if (!mIdleToolsRevealReady) {
            removeCallbacks(mRevealIdleToolsRunnable)
            postDelayed(mRevealIdleToolsRunnable, IDLE_TOOLS_REVEAL_DELAY_MS)
        }
        return shouldShowIdleTools(isEmpty, mIdleToolsRevealReady, composingOrSearching)
    }

    private fun isComposingOrSearching(): Boolean {
        return mService != null && mService!!.isComposingOrSearchingCandidates
    }

    fun updateCandidateViewWidthConstraint() {
        post {
            val containerWidth = width
            val candidateView = mCandidateView
            if (containerWidth > 0 && candidateView != null) {
                val isEmpty = candidateView.isEmpty
                val showIdleTools = shouldShowIdleTools(isEmpty, mIdleToolsRevealReady, isComposingOrSearching())
                val showActiveChrome = shouldShowActiveChrome(isEmpty, showIdleTools, mIdleToolsRevealReady)
                mDismissButton?.visibility = if (showActiveChrome) View.VISIBLE else View.GONE
                mEmojiButton?.visibility = if (showIdleTools) View.VISIBLE else View.GONE
                mRightButton?.visibility = if (showIdleTools || showActiveChrome) View.VISIBLE else View.GONE
                mRightButtonParent?.visibility = if (showIdleTools || showActiveChrome) View.VISIBLE else View.GONE

                val params = candidateView.layoutParams
                if (params is LayoutParams) {
                    val buttonWidth = resources.getDimensionPixelSize(R.dimen.candidate_expand_button_width)
                    val dismissWidth = resources.getDimensionPixelSize(R.dimen.candidate_dismiss_button_width)
                    val dismissVisible = mDismissButton?.visibility == View.VISIBLE
                    val emojiVisible = mEmojiButton?.visibility == View.VISIBLE
                    val keyboardVisible = mKeyboardButton?.visibility == View.VISIBLE
                    val rightVisible = mRightButton?.visibility == View.VISIBLE

                    var buttonsWidth = 0
                    if (dismissVisible) buttonsWidth += dismissWidth
                    if (emojiVisible) buttonsWidth += buttonWidth
                    if (keyboardVisible) buttonsWidth += buttonWidth
                    if (rightVisible) buttonsWidth += buttonWidth

                    val maxCandidateWidth = containerWidth - buttonsWidth
                    if (maxCandidateWidth > 0) {
                        if (params.width != maxCandidateWidth || params.weight != 0f) {
                            params.width = maxCandidateWidth
                            params.weight = 0f
                            candidateView.layoutParams = params
                        }
                    } else {
                        if (params.width != 0 || params.weight != 1.0f) {
                            params.width = 0
                            params.weight = 1.0f
                            candidateView.layoutParams = params
                        }
                    }
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val candidateView = mCandidateView
        if (ev.actionMasked == MotionEvent.ACTION_UP &&
            candidateView != null &&
            !candidateView.isEmpty
        ) {
            val candidateRowHeight = candidateRowHeight()
            val actionWidth = resources.getDimensionPixelSize(R.dimen.candidate_expand_button_width)
            if (isRightEdgeActionTap(ev.x, ev.y, width, candidateRowHeight, actionWidth)) {
                toggleCandidatePopup()
                performClick()
                return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun candidateRowHeight(): Int {
        val actionRow = mActionRow
        if (actionRow != null && actionRow.height > 0) {
            return actionRow.height
        }
        val candidateView = mCandidateView
        if (candidateView != null && candidateView.height > 0) {
            return candidateView.height
        }
        return 0
    }

    override fun onClick(v: View) {
        val candidateView = mCandidateView
        if (v === mDismissButton) {
            candidateView?.dismissComposingFromCandidate()
        } else if (v === mEmojiButton) {
            mService?.onKey(LIME.KEYCODE_EMOJI_PANEL, null, 0, 0)
        } else if (v === mKeyboardButton) {
            mService?.restoreKeyboardViewIfHidden(true)
            post { requestLayout() }
        } else if (isRightActionClick(v, mRightButton, mRightButtonParent)) {
            if (isShowingIdleTools()) {
                candidateView?.startVoiceInput()
            } else if (candidateView != null && !candidateView.isEmpty) {
                toggleCandidatePopup()
            }
        }
    }

    private fun isShowingIdleTools(): Boolean {
        val candidateView = mCandidateView
        return candidateView != null &&
            shouldShowIdleTools(candidateView.isEmpty, mIdleToolsRevealReady, isComposingOrSearching())
    }

    private fun toggleCandidatePopup() {
        val candidateView = mCandidateView ?: return
        if (candidateView.isCandidateExpanded) {
            candidateView.hideCandidatePopup()
        } else {
            candidateView.showCandidatePopup()
        }
        post { requestLayout() }
    }

    companion object {
        private const val DEBUG = false
        private const val TAG = "CandiInputViewContainer"
        private const val IDLE_TOOLS_REVEAL_DELAY_MS = 120L

        @JvmStatic
        fun actionRowBackgroundColor(candidateBackground: Int): Int = candidateBackground

        @JvmStatic
        fun actionButtonBackgroundColor(): Int = Color.TRANSPARENT

        @JvmStatic
        fun dismissButtonBackgroundColor(): Int = Color.TRANSPARENT

        @JvmStatic
        fun isRightActionClick(clicked: View?, rightButton: View?, rightButtonParent: View?): Boolean {
            return clicked === rightButton || clicked === rightButtonParent
        }

        @JvmStatic
        fun isRightEdgeActionTap(
            x: Float,
            y: Float,
            containerWidth: Int,
            candidateRowHeight: Int,
            actionWidth: Int
        ): Boolean {
            return containerWidth > 0 &&
                candidateRowHeight > 0 &&
                actionWidth > 0 &&
                y >= 0 &&
                y <= candidateRowHeight &&
                x >= containerWidth - actionWidth
        }

        @JvmStatic
        fun shouldShowCollapseGlyph(isEmpty: Boolean, isExpanded: Boolean, isKeyboardHidden: Boolean): Boolean {
            return !isEmpty && (isExpanded || isKeyboardHidden)
        }

        @JvmStatic
        fun shouldShowIdleTools(isEmpty: Boolean, idleRevealReady: Boolean, composingOrSearching: Boolean): Boolean {
            return isEmpty && idleRevealReady && !composingOrSearching
        }

        @JvmStatic
        fun shouldShowActiveChrome(isEmpty: Boolean, showIdleTools: Boolean, idleRevealReady: Boolean): Boolean {
            return !isEmpty || (isEmpty && !showIdleTools && !idleRevealReady)
        }
    }
}
