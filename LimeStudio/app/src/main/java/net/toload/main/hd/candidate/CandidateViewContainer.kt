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

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import net.toload.main.hd.LIMEService
import net.toload.main.hd.R

class CandidateViewContainer(context: Context, attrs: AttributeSet?) :
    LinearLayout(context, attrs),
    View.OnTouchListener {
    private var mButtonDismiss: ImageButton? = null
    private var mButtonExpand: ImageButton? = null
    private var mButtonExpandLayout: View? = null
    private var mActionRow: View? = null
    private var mCandidateView: CandidateView? = null

    @SuppressLint("ClickableViewAccessibility")
    fun initViews() {
        if (mCandidateView == null) {
            mButtonDismiss = findViewById(R.id.candidate_dismiss)
            mButtonExpandLayout = findViewById(R.id.candidate_right_parent)
            mButtonExpand = findViewById(R.id.candidate_right)
            val dismissButton = mButtonDismiss
            if (dismissButton != null && dismissButton.parent is View) {
                mActionRow = dismissButton.parent as View
            }
            dismissButton?.setOnTouchListener(this)
            mButtonExpand?.setOnTouchListener(this)

            val candidateView = findViewById<CandidateView>(R.id.candidates)
            mCandidateView = candidateView
            val embeddedTextView = findViewById<TextView>(R.id.embeddedComposing)

            candidateView.setEmbeddedComposingView(embeddedTextView)
            val currentContext = context
            if (currentContext is LIMEService) {
                candidateView.setService(currentContext)
            }
            candidateView.setBackgroundColor(candidateView.mColorBackground)
            mActionRow?.setBackgroundColor(
                CandidateInInputViewContainer.actionRowBackgroundColor(candidateView.mColorBackground)
            )
            mButtonExpandLayout?.setBackgroundColor(
                CandidateInInputViewContainer.actionRowBackgroundColor(candidateView.mColorBackground)
            )
            if (dismissButton != null) {
                dismissButton.setPadding(0, 0, 0, 0)
                dismissButton.scaleType = ImageView.ScaleType.CENTER
                dismissButton.minimumWidth = 0
                dismissButton.minimumHeight = 0
                dismissButton.setImageDrawable(candidateView.makeDismissButtonGlyph())
                dismissButton.setBackgroundColor(CandidateInInputViewContainer.dismissButtonBackgroundColor())
                dismissButton.post { candidateView.storePopupDismissButtonWidth(dismissButton) }
            }
            val expandButton = mButtonExpand
            if (expandButton != null) {
                expandButton.setBackgroundColor(candidateView.mColorBackground)
                expandButton.setImageDrawable(candidateView.mDrawableExpandDownButton)
            }
        }
    }

    override fun requestLayout() {
        val candidateView = mCandidateView
        if (candidateView != null) {
            val availableWidth = candidateView.width
            val neededWidth = candidateView.computeHorizontalScrollRange()

            var rightVisible = availableWidth < neededWidth
            if (candidateView.isCandidateExpanded) {
                rightVisible = true
            }

            mButtonExpandLayout?.visibility = if (rightVisible) VISIBLE else GONE
            mButtonDismiss?.visibility = if (candidateView.isEmpty) GONE else VISIBLE
        }
        super.requestLayout()
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val candidateView = mCandidateView ?: return false
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (v === mButtonDismiss) {
                candidateView.dismissComposingFromCandidate()
            } else if (v === mButtonExpand) {
                candidateView.showCandidatePopup()
            }
        }
        return false
    }
}
