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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.widget.ScrollView
import net.toload.main.hd.data.Mapping
import net.toload.main.hd.R

class CandidateExpandedView : CandidateView {
    private var mCollapsedCandidateView: CandidateView? = null
    private var mExpandedSuggestions: List<Mapping?>? = null
    private var mExpandedTouchX = OUT_OF_BOUNDS
    private var mTouchY = OUT_OF_BOUNDS
    private var mSelRow = 0
    private var mSelCol = 0
    private val mExpandedWordX = Array(MAX_SUGGESTIONS) { IntArray(MAX_SUGGESTIONS) }
    private val mExpandedWordWidth = Array(MAX_SUGGESTIONS) { IntArray(MAX_SUGGESTIONS) }
    private val mRowSize = IntArray(MAX_SUGGESTIONS)
    private val mRowStartingIndex = IntArray(MAX_SUGGESTIONS)
    private var mRows = 0
    private var mExpandedHeight: Int
    private var mTotalHeight = 0
    private var mParentScrollView: ScrollView? = null
    private var mDownTouch = false

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, R.attr.LIMECandidateView)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        mExpandedHeight = (context.resources.getDimensionPixelSize(R.dimen.candidate_stripe_height) *
            mLIMEPref.fontSize).toInt()
        setBackgroundColor(mColorBackground)
    }

    fun setParentScrollView(v: ScrollView?) {
        mParentScrollView = v
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desireWidth = resolveSize(mCollapsedCandidateView!!.width, widthMeasureSpec)
        val desiredHeight = resolveSize(mTotalHeight, heightMeasureSpec)
        setMeasuredDimension(desireWidth, desiredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val suggestions = mExpandedSuggestions ?: return
        if (DEBUG) Log.i(TAG, "OnDraw() mExpandedSuggestions.size:" + suggestions.size)

        if (mBgPadding == null) {
            mBgPadding = Rect(0, 0, 0, 0)
            background?.getPadding(mBgPadding!!)
        }

        val height = mExpandedHeight
        val rowHeight = rowHeight(height, mVerticalPadding)
        val bgPadding = mBgPadding!!
        val candidatePaint = mCandidatePaint
        val candidateEmojiPaint = mCandidatePaint
        val selKeyPaint = mSelKeyPaint

        if (mExpandedTouchX != OUT_OF_BOUNDS && mTouchY != OUT_OF_BOUNDS) {
            mSelRow = mTouchY / rowHeight

            for (i in 0 until mRowSize[mSelRow]) {
                if (mExpandedTouchX >= mExpandedWordX[mSelRow][i] &&
                    mExpandedTouchX < mExpandedWordX[mSelRow][i] + mExpandedWordWidth[mSelRow][i]
                ) {
                    mSelectedIndex = mRowStartingIndex[mSelRow] + i
                    mSelCol = i
                    break
                }
            }
            if (DEBUG) {
                Log.i(
                    TAG,
                    "onDraw(): new mSelectedIndex =$mSelectedIndex, mSelRow=$mSelRow, mSelCol=$mSelCol"
                )
            }
        }

        try {
            if (mSelectedIndex >= 0) {
                canvas.translate(mExpandedWordX[mSelRow][mSelCol].toFloat(), (mSelRow * rowHeight).toFloat())
                mDrawableSuggestHighlight!!.setBounds(0, bgPadding.top, mExpandedWordWidth[mSelRow][mSelCol], rowHeight)
                mDrawableSuggestHighlight!!.draw(canvas)
                canvas.translate(-mExpandedWordX[mSelRow][mSelCol].toFloat(), -(mSelRow * rowHeight).toFloat())
            }
        } catch (e: ArrayIndexOutOfBoundsException) {
            Log.e(TAG, "Error in candidate expanded view", e)
        }

        try {
            var y = rowBaseline(rowHeight, mCandidatePaint.textSize, mCandidatePaint.ascent())
            var index = 0
            for (i in 0 until mRows) {
                if (i != 0) y += rowHeight

                for (j in 0 until mRowSize[i]) {
                    if (suggestions.isEmpty() || suggestions.getOrNull(index) == null) {
                        continue
                    }
                    val mapping = suggestions[index]
                    val suggestion = displaySuggestion(index, suggestions)

                    when (mapping?.getRecordType()) {
                        Mapping.RECORD_EXACT_MATCH_TO_CODE,
                        Mapping.RECORD_PARTIAL_MATCH_TO_CODE,
                        Mapping.RECORD_COMPOSING_CODE -> {
                            selKeyPaint.color = mColorSpacer
                            if (i == 0 && j == 0) {
                                candidatePaint.color =
                                    if (mSelectedIndex == 0) mColorComposingCodeHighlight else mColorComposingCode
                            } else {
                                candidatePaint.color =
                                    if (index == mSelectedIndex) mColorNormalTextHighlight else mColorNormalText
                            }
                        }
                        else -> {
                            candidatePaint.color =
                                if (index == mSelectedIndex) mColorNormalTextHighlight else mColorNormalText
                        }
                    }
                    if (mapping?.isEmojiRecord() == true) {
                        canvas.drawText(suggestion, (mExpandedWordX[i][j] + X_GAP).toFloat(), Math.round(y * 0.95f).toFloat(), candidateEmojiPaint)
                    } else {
                        canvas.drawText(suggestion, (mExpandedWordX[i][j] + X_GAP).toFloat(), y.toFloat(), candidatePaint)
                    }

                    candidatePaint.color = mColorSpacer
                    val lineX = mExpandedWordX[i][j] + mExpandedWordWidth[i][j] + 0.5f
                    canvas.drawLine(
                        lineX,
                        bgPadding.top + rowLineTop(i, rowHeight, mVerticalPadding),
                        lineX,
                        rowLineBottom(i, rowHeight, mVerticalPadding),
                        candidatePaint
                    )
                    candidatePaint.isFakeBoldText = false
                    index++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in candidate expanded view", e)
        }
    }

    fun setParentCandidateView(v: CandidateView?) {
        mCollapsedCandidateView = v
    }

    fun prepareLayout() {
        if (DEBUG) Log.i(TAG, "prepareLayout()")
        val suggestions = mExpandedSuggestions
        if (suggestions.isNullOrEmpty()) return

        updateFontSize()
        mCandidatePaint.textSize = liveCandidateTextSize(mCandidatePaint.textSize)
        mExpandedHeight = configHeight

        val paint = mCandidatePaint
        val parentCandidate = mCollapsedCandidateView ?: return
        val dismissWidth = parentCandidate.popupDismissButtonWidth()
        val expandWidth = parentCandidate.popupExpandButtonWidth()
        var x = rowStartX(0, dismissWidth)
        var row = 0
        var indexInRow = 0
        mRowStartingIndex[0] = 0

        val count = mCount
        for (i in 0 until count) {
            val suggestion = displaySuggestion(i, suggestions)
            val wordWidth = wordWidth(paint, suggestion, X_GAP)

            if (x + wordWidth > rowEndX(row, mScreenWidth, expandWidth)) {
                mRowSize[row] = indexInRow
                row++
                mRowStartingIndex[row] = i
                indexInRow = 0
                x = rowStartX(row, dismissWidth)
            }

            mExpandedWordX[row][indexInRow] = x
            mExpandedWordWidth[row][indexInRow] = wordWidth
            x += wordWidth

            if (i == count - 1) {
                mRowSize[row] = indexInRow + 1
            }
            indexInRow++
        }
        mRows = row + 1
        mTotalHeight = rowHeight(mExpandedHeight, mVerticalPadding) * mRows
    }

    fun setSuggestions(suggestions: List<Mapping?>?) {
        if (DEBUG && suggestions != null) Log.i(TAG, "setSuggestions(), suggestions.size()=" + suggestions.size)
        val parentCandidate = mCollapsedCandidateView
        if (parentCandidate != null && parentCandidate.mSuggestions != null) {
            mExpandedSuggestions = parentCandidate.mSuggestions
            mCount = parentCandidate.mCount
            mSelectedIndex = parentCandidate.mSelectedIndex
            mExpandedTouchX = OUT_OF_BOUNDS
            mTouchY = OUT_OF_BOUNDS
            if (mSelectedIndex == -1) {
                mSelCol = -1
                mSelRow = -1
            } else {
                mSelCol = mSelectedIndex
                mSelRow = 0
            }
        }
        prepareLayout()
        invalidate()
    }

    override fun onTouchEvent(me: MotionEvent): Boolean {
        if (DEBUG) {
            Log.i(
                TAG,
                "onTouchEvent(): x =" + me.x + ", y=" + me.y + ", ScroolY=" +
                    (mParentScrollView?.scrollY ?: 0)
            )
        }
        val action = me.actionMasked
        val x = me.x.toInt()
        val y = me.y.toInt()
        mExpandedTouchX = x
        mTouchY = y
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (DEBUG) Log.i(TAG, "onTouchEvent(), Action_DONW")
                mDownTouch = true
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (DEBUG) Log.i(TAG, "onTouchEvent(), Action_MOVE")
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (DEBUG) Log.i(TAG, "onTouchEvent(), Action_UP")
                if (mDownTouch) {
                    mDownTouch = false
                    performClick()
                }
                mCollapsedCandidateView?.takeSelectedSuggestion(true)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun scrollToRow(row: Int) {
        val scrollView = mParentScrollView ?: return
        val selY = row * (mExpandedHeight + mVerticalPadding)
        val scrollY = scrollView.scrollY
        val scrollHeight = scrollView.height
        if (selY < scrollY || selY > scrollY + scrollHeight) {
            scrollView.scrollTo(0, row * (mExpandedHeight + mVerticalPadding))
        }
    }

    override fun selectNext() {
        if (mExpandedSuggestions == null) return
        if (mSelectedIndex == -1) {
            mSelectedIndex = 0
            mSelRow = 0
            mSelCol = 0
        } else if (mSelectedIndex < mCount - 1) {
            mSelectedIndex++
            if (mSelectedIndex >= mRowStartingIndex[mSelRow] + mRowSize[mSelRow]) {
                mSelRow++
                mSelCol = 0
                scrollToRow(mSelRow)
            } else {
                mSelCol++
            }
            invalidate()
        }
    }

    override fun selectPrev() {
        if (mExpandedSuggestions == null) return
        if (mSelectedIndex > 0) {
            mSelectedIndex--
            if (mSelectedIndex < mRowStartingIndex[mSelRow]) {
                mSelRow--
                mSelCol = mRowSize[mSelRow] - 1
                scrollToRow(mSelRow)
            } else {
                mSelCol--
            }
            invalidate()
        }
    }

    override fun selectNextRow() {
        if (mExpandedSuggestions == null) return
        if (mSelRow < mRows - 1) {
            mSelRow++
            if (mSelCol > mRowSize[mSelRow] - 1) {
                mSelCol = mRowSize[mSelRow] - 1
            } else if (mSelCol == -1) {
                mSelCol = 0
            }

            mSelectedIndex = mRowStartingIndex[mSelRow] + mSelCol
            scrollToRow(mSelRow)
            invalidate()
        }
    }

    override fun selectPrevRow() {
        if (mExpandedSuggestions == null) return
        if (mSelRow > 0) {
            mSelRow--
            if (mSelCol > mRowSize[mSelRow] - 1) {
                mSelCol = mRowSize[mSelRow] - 1
            }

            mSelectedIndex = mRowStartingIndex[mSelRow] + mSelCol
            scrollToRow(mSelRow)
            invalidate()
        }
    }

    companion object {
        private const val DEBUG = false
        private const val TAG = "CandidateExpandedView"
        private const val MAX_SUGGESTIONS = 200

        @JvmStatic
        fun rowHeight(contentHeight: Int, verticalPadding: Int): Int = contentHeight + verticalPadding

        @JvmStatic
        fun rowBaseline(rowHeight: Int, textSize: Float, ascent: Float): Int {
            return ((rowHeight - textSize) / 2 - ascent).toInt()
        }

        @JvmStatic
        fun rowLineTop(row: Int, rowHeight: Int, verticalPadding: Int): Float {
            return rowHeight * row + verticalPadding.toFloat() / 2
        }

        @JvmStatic
        fun rowLineBottom(row: Int, rowHeight: Int, verticalPadding: Int): Float {
            return rowHeight * (row + 1) - verticalPadding.toFloat() / 2
        }

        @JvmStatic
        fun displaySuggestion(index: Int, suggestions: List<Mapping?>): String {
            val suggestion = suggestions[index]?.getWord() ?: ""
            if (index == 0 &&
                suggestions.size > 1 &&
                suggestions[1]?.isRuntimeBuiltPhraseRecord() == true &&
                suggestion.length > 8
            ) {
                return suggestion.substring(0, 2) + ".."
            }
            return suggestion
        }

        @JvmStatic
        fun wordWidth(paint: Paint, suggestion: String?, xGap: Int): Int {
            if (suggestion == null) return xGap * 2
            val base = paint.measureText("。")
            var textWidth = paint.measureText(suggestion)
            if (textWidth < base) {
                textWidth = base
            }
            return textWidth.toInt() + xGap * 2
        }

        @JvmStatic
        fun rowStartX(row: Int, dismissWidth: Int): Int = if (row == 0) dismissWidth else 0

        @JvmStatic
        fun rowEndX(row: Int, screenWidth: Int, expandWidth: Int): Int {
            return if (row == 0) screenWidth - expandWidth else screenWidth
        }
    }
}
