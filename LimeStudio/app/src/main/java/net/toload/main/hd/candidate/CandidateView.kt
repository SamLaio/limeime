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
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewParent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView.ScaleType
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import net.toload.main.hd.candidate.CandidateInInputViewContainer.Companion.dismissButtonBackgroundColor
import net.toload.main.hd.data.Mapping
import net.toload.main.hd.global.DiagnosticLog
import net.toload.main.hd.global.LIME
import net.toload.main.hd.global.LIMEPreferenceManager
import net.toload.main.hd.LIMEService
import net.toload.main.hd.R
import net.toload.main.hd.voice.DictationState

open class CandidateView @Suppress("deprecation") constructor(
    @JvmField
    val mContext: Context,
    attrs: AttributeSet?,
    defStyle: Int
) : View(
    mContext, attrs, defStyle
), View.OnClickListener {
    @JvmField
    var mService: LIMEService? = null
    @JvmField
    var mSuggestions: MutableList<Mapping?>? = null
    @JvmField
    var mCandidateView: CandidateView?
    private var embeddedComposing: TextView? = null

    @JvmField
    var mSelectedIndex: Int = 0
    @JvmField
    var mTouchX: Int = OUT_OF_BOUNDS

    //private boolean mTypedWordValid;
    private var mShowNumber = false //Jeremy '11,5,25 for showing physical keyboard number or not.

    @JvmField
    var mBgPadding: Rect? = null


    // Add by Jeremy '10, 3, 29.
    // Suggestions size. Set to MAX_SUGGESTIONS if larger then it.
    @JvmField
    var mCount: Int = 0

    //Composing view
    private var mComposingTextView: TextView? = null
    private var mComposingPopupTextView: TextView? = null

    //private String mComposingText = "";
    @JvmField
    var mWordWidth: IntArray = IntArray(MAX_SUGGESTIONS)
    @JvmField
    var mWordX: IntArray = IntArray(MAX_SUGGESTIONS)

    @JvmField
    var mHeight: Int
    @JvmField
    var configHeight: Int
    private var currentX = 0

    @JvmField
    var mColorBackground: Int = 0
    @JvmField
    var mColorNormalText: Int = 0
    @JvmField
    var mColorNormalTextHighlight: Int = 0
    @JvmField
    var mColorInvertedTextTransparent: Int = 0

    @JvmField
    var mColorComposingText: Int = 0
    @JvmField
    var mColorComposingBackground: Int = 0


    @JvmField
    var mColorComposingCodeHighlight: Int = 0
    @JvmField
    var mColorComposingCode: Int = 0

    @JvmField
    var mColorSpacer: Int = 0

    @JvmField
    var mDrawableSuggestHighlight: Drawable? = null
    @JvmField
    var mDrawableVoiceInput: Drawable? = null
    @JvmField
    var mDrawableEmojiInput: Drawable? = null
    @JvmField
    var mDrawableExpandDownButton: Drawable? = null
    @JvmField
    var mDrawableExpandUpButton: Drawable? = null
    @JvmField
    var mDrawableCloseButton: Drawable? = null
    @JvmField
    var mDrawableKeyboardShow: Drawable? = null

    @JvmField
    var mColorSelKey: Int = 0
    @JvmField
    var mColorSelKeyShifted: Int = 0

    @JvmField
    var mVerticalPadding: Int
    @JvmField
    var mExpandButtonWidth: Int

    @JvmField
    var mCandidatePaint: Paint
    @JvmField
    var mSelKeyPaint: Paint


    private var mScrolled = false
    @JvmField
    var mTargetScrollX: Int = 0
    private var mDisplaySelkey = "1234567890"

    @JvmField
    var mTotalWidth: Int = 0

    private var goLeft = false
    private var goRight = false
    private var hasSlide = false

    //private int bgcolor = 0;
    private var mCandidatePopupContainer: View? = null
    private var mCandidatePopupWindow: PopupWindow? = null

    @JvmField
    var mScreenWidth: Int = 0
    @JvmField
    var mScreenHeight: Int = 0

    @JvmField
    var mGestureDetector: GestureDetector?

    @JvmField
    var mLIMEPref: LIMEPreferenceManager

    private var mPopupCandidateView: CandidateExpandedView? = null
    private var mPopupScrollView: ScrollView? = null
    var isCandidateExpanded: Boolean = false
        private set
    private var mLimeToastPopup: PopupWindow? = null
    private var mLimeToastTextView: TextView? = null
    private var mPopupDismissButtonWidth = 0

    private var waitingForMoreRecords = false

    private var mTransparentCandidateView = false
    private var mDictationState: DictationState? = DictationState.IDLE
    private var mDictationText = ""

    //private Rect padding = null;
    /**
     * Construct a CandidateView for showing suggested words for completion.
     */
    constructor(context: Context, attrs: AttributeSet?) : this(
        context,
        attrs,
        R.attr.LIMECandidateView
    )

    fun applyFollowSystemAccentColor(accentColor: Int, darkTheme: Boolean) {
        if ((accentColor ushr 24) == 0) return

        mDrawableSuggestHighlight = createFollowSystemSuggestHighlight(accentColor, darkTheme)
        mColorComposingCode = accentColor
        mColorSelKeyShifted = accentColor
        mColorNormalTextHighlight = if (darkTheme) -0x1 else -0x1000000
        invalidate()
    }

    private fun createFollowSystemSuggestHighlight(accentColor: Int, darkTheme: Boolean): Drawable {
        val density = getResources().getDisplayMetrics().density
        val drawable = GradientDrawable()
        drawable.setShape(GradientDrawable.RECTANGLE)
        drawable.setCornerRadius(4f * density)
        drawable.setColor(withAlpha(accentColor, if (darkTheme) 0x66 else 0x44))
        return drawable
    }

    /*
    * New embedded composing view inside candidate container for floating candidate mode. Jeremy '15,6,14
    * (android 5.1 does not allow popup composing go over candidate area).
     */
    fun setEmbeddedComposingView(composingView: TextView?) {
        if (DEBUG) Log.i(TAG, "setEmbeddedComposingView()")
        embeddedComposing = composingView
    }

    private val mHandler = UIHandler(this)

    private class UIHandler(candiInstance: CandidateView?) : Handler(Looper.getMainLooper()) {
        private val mCandidateViewWeakReference: WeakReference<CandidateView?>


        init {
            mCandidateViewWeakReference = WeakReference<CandidateView?>(candiInstance)
        }

        override fun handleMessage(msg: Message) {
            if (DEBUG) Log.i(TAG, "UIHandler.handleMessage(): message:" + msg.what)

            val mCandiInstance = mCandidateViewWeakReference.get()
            if (mCandiInstance == null) return

            when (msg.what) {
                MSG_UPDATE_UI -> mCandiInstance.doUpdateUI()
                MSG_UPDATE_COMPOSING -> mCandiInstance.doUpdateComposing()
                MSG_HIDE_COMPOSING -> {
                    mCandiInstance.doHideComposing()
                }

                MSG_SHOW_CANDIDATE_POPUP -> {
                    mCandiInstance.doUpdateCandidatePopup()
                }

                MSG_HIDE_CANDIDATE_POPUP -> {
                    mCandiInstance.doHideCandidatePopup()
                }

                MSG_SET_COMPOSING -> {
                    val composingText = msg.obj as String?
                    if (DEBUG) Log.i(
                        TAG,
                        "UIHandler.handleMessage(): composingText" + composingText
                    )
                    mCandiInstance.doSetComposing(composingText)
                }

                MSG_SHOW_LIME_TOAST -> {
                    val text = msg.obj as CharSequence?
                    mCandiInstance.doShowLimeToast(text)
                }

                MSG_HIDE_LIME_TOAST -> {
                    mCandiInstance.doHideLimeToast()
                }
            }
        }

        fun updateUI(delay: Int) {
            sendMessageDelayed(obtainMessage(MSG_UPDATE_UI, 0, 0, null), delay.toLong())
        }

        fun setComposing(text: String?, delay: Int) {
            sendMessageDelayed(obtainMessage(MSG_SET_COMPOSING, 0, 0, text), delay.toLong())
        }

        fun updateComposing(delay: Int) {
            sendMessageDelayed(obtainMessage(MSG_UPDATE_COMPOSING, 0, 0, null), delay.toLong())
        }

        fun dismissComposing(delay: Int) {
            sendMessageDelayed(obtainMessage(MSG_HIDE_COMPOSING, 0, 0, null), delay.toLong())
        }

        fun showCandidatePopup(delay: Int) {
            sendMessageDelayed(obtainMessage(MSG_SHOW_CANDIDATE_POPUP, 0, 0, null), delay.toLong())
        }

        fun dismissCandidatePopup(delay: Int) {
            sendMessageDelayed(obtainMessage(MSG_HIDE_CANDIDATE_POPUP, 0, 0, null), delay.toLong())
        }

        fun showLimeToast(text: CharSequence?) {
            removeMessages(MSG_SHOW_LIME_TOAST)
            removeMessages(MSG_HIDE_LIME_TOAST)
            sendMessage(obtainMessage(MSG_SHOW_LIME_TOAST, 0, 0, text))
            sendMessageDelayed(obtainMessage(MSG_HIDE_LIME_TOAST, 0, 0, null), 1400)
        }

        fun showLimeToastUntilNextKey(text: CharSequence?) {
            removeMessages(MSG_SHOW_LIME_TOAST)
            removeMessages(MSG_HIDE_LIME_TOAST)
            sendMessage(obtainMessage(MSG_SHOW_LIME_TOAST, 0, 0, text))
        }

        fun hideLimeToast() {
            removeMessages(MSG_SHOW_LIME_TOAST)
            removeMessages(MSG_HIDE_LIME_TOAST)
            sendMessage(obtainMessage(MSG_HIDE_LIME_TOAST, 0, 0, null))
        }

        companion object {
            private const val MSG_UPDATE_UI = 1
            private const val MSG_UPDATE_COMPOSING = 2
            private const val MSG_HIDE_COMPOSING = 3
            private const val MSG_SHOW_CANDIDATE_POPUP = 4
            private const val MSG_HIDE_CANDIDATE_POPUP = 5
            private const val MSG_SET_COMPOSING = 6
            private const val MSG_SHOW_LIME_TOAST = 7
            private const val MSG_HIDE_LIME_TOAST = 8
        }
    }


    fun doUpdateUI() {
        if (DEBUG) Log.i(TAG, "doUpdateUI()")

        if ((mSuggestions == null || mSuggestions!!.isEmpty())
            && (mCandidatePopupWindow != null && mCandidatePopupWindow!!.isShowing())
        ) {
            doHideCandidatePopup()
            return
        }


        if (mCandidatePopupWindow != null && mCandidatePopupWindow!!.isShowing()) {
            doUpdateCandidatePopup()
        } else {
            if (!waitingForMoreRecords) {  // New suggestion list, reset scroll to (0,0);
                scrollTo(0, 0)
                mTargetScrollX = 0
            }
            resetWidth() // update layout width of this view
            invalidate() // caused onDraw and update mTotoalX
        }
        waitingForMoreRecords = false
    }

    fun updateFontSize() {
        val r = mContext.getResources()
        val scaling = mLIMEPref.getFontSize()
        mVerticalPadding =
            (r.getDimensionPixelSize(R.dimen.candidate_vertical_padding) * scaling).toInt()
        mCandidatePaint.setTextSize(r.getDimensionPixelSize(R.dimen.candidate_font_size) * scaling)
        mSelKeyPaint.setTextSize(r.getDimensionPixelSize(R.dimen.candidate_number_font_size) * scaling)
        configHeight = (r.getDimensionPixelSize(R.dimen.candidate_stripe_height) * scaling).toInt()
        if (DEBUG) Log.i(
            TAG,
            "updateFontSize(), scaling=" + scaling + ", mVerticalPadding=" + mVerticalPadding
        )
    }

    private fun doHideCandidatePopup() {
        if (DEBUG) Log.i(TAG, "doHideCandidatePopup()")

        if (mCandidatePopupWindow != null && mCandidatePopupWindow!!.isShowing()) {
            mCandidatePopupWindow!!.dismiss()
            //resetWidth();
        }
        this.isCandidateExpanded = false

        doUpdateUI()


        // Update CandidateView width constraint when popup is closed
        if (mService != null) {
            mService!!.updateCandidateViewWidthConstraint()
        }
    }

    /*
    * Contains requestLayout() which can only call from UI thread
    */
    private fun resetWidth() {
        if (DEBUG) Log.i(TAG, "resetWidth() mHeight:" + mHeight)
        var candiWidth = mScreenWidth
        if (!this.isEmpty) candiWidth -= mContext.getResources()
            .getDimensionPixelSize(R.dimen.candidate_dismiss_button_width)
        if (mTotalWidth > mScreenWidth || this.isEmpty) candiWidth -= mExpandButtonWidth
        if (DEBUG) Log.i(TAG, "resetWidth() candiWidth:" + candiWidth)
        this.setLayoutParams(LinearLayout.LayoutParams(candiWidth, mHeight))
        requestLayout()
    }


    open fun doUpdateCandidatePopup() {
        if (DEBUG) Log.i(TAG, "doUpdateCandidatePopup(), mHeight:" + mHeight)

        //Jeremy '11,8.27 do vibrate and sound on candidateview expand button pressed.
        if (!this.isCandidateExpanded) mService!!.doVibrateSound(0)

        this.isCandidateExpanded = true
        requestLayout()
        doHideComposing()

        checkHasMoreRecords()

        if (mCandidatePopupWindow == null) {
            mCandidatePopupWindow = PopupWindow(mContext)
            // Allow popup to extend beyond window bounds when expanding upward
            mCandidatePopupWindow!!.setClippingEnabled(false)
            mCandidatePopupWindow!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mCandidatePopupWindow!!.setElevation(0f)
            }
            val inflater = mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE
            ) as LayoutInflater
            mCandidatePopupContainer =
                inflater.inflate(R.layout.candidatepopup, this.getRootView() as ViewGroup?, false)

            mCandidatePopupContainer!!.setBackgroundColor(mColorBackground)

            mCandidatePopupWindow!!.setContentView(mCandidatePopupContainer)

            val popupDismiss =
                mCandidatePopupContainer!!.findViewById<ImageButton?>(R.id.candidate_dismiss)
            if (popupDismiss != null) {
                popupDismiss.setOnClickListener(this)
                popupDismiss.setPadding(0, 0, 0, 0)
                popupDismiss.setScaleType(ScaleType.CENTER)
                popupDismiss.setMinimumWidth(0)
                popupDismiss.setMinimumHeight(0)
                popupDismiss.setImageDrawable(makeDismissButtonGlyph())
                popupDismiss.setBackgroundColor(dismissButtonBackgroundColor())
                popupDismiss.measure(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                )
                storePopupDismissButtonWidth(popupDismiss)
            }

            val popupCollapse =
                mCandidatePopupContainer!!.findViewById<ImageButton?>(R.id.candidate_expand_collapse)
            if (popupCollapse != null) {
                popupCollapse.setOnClickListener(object : OnClickListener {
                    override fun onClick(v: View?) {
                        hideCandidatePopup()
                    }
                })
                popupCollapse.setPadding(0, 0, 0, 0)
                popupCollapse.setScaleType(ScaleType.CENTER)
                popupCollapse.setMinimumWidth(0)
                popupCollapse.setMinimumHeight(0)
                popupCollapse.setImageDrawable(mDrawableExpandUpButton)
                popupCollapse.setBackgroundColor(Color.TRANSPARENT)
            }

            mPopupScrollView = mCandidatePopupContainer!!.findViewById<ScrollView>(R.id.sv)

            val popupCandidate =
                mCandidatePopupContainer!!.findViewById<CandidateExpandedView>(R.id.candidatePopup)
            popupCandidate.setParentCandidateView(this)
            popupCandidate.setParentScrollView(mPopupScrollView)
            popupCandidate.setService(mService)

            mPopupCandidateView = popupCandidate
        }

        if (mSuggestions!!.isEmpty()) return


        mCandidatePopupWindow!!.setContentView(mCandidatePopupContainer)
        val offsetOnScreen = IntArray(2)
        this.getLocationOnScreen(offsetOnScreen)


        // Determine expansion direction based on keyboard visibility
        val expandUpward = (mService != null) && mService!!.isKeyboardViewHidden
        val candidateViewHeight = getHeight()
        configurePopupOverlayControls(candidateViewHeight)
        val availableSpaceAbove = offsetOnScreen[1]
        val availableSpaceBelow = mScreenHeight - offsetOnScreen[1] - candidateViewHeight

        val myHeight = getHeight()
        mPopupCandidateView!!.setSuggestions(mSuggestions?.filterNotNull())
        mPopupCandidateView!!.prepareLayout()

        mPopupCandidateView!!.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )

        val popHeight: Int
        val popupYOffset: Int
        if (expandUpward) {
            // Physical keyboard path: the soft keyboard is hidden and only the candidate bar remains.
            popHeight = popupHeight(mScreenHeight, mPopupCandidateView!!.getMeasuredHeight(), true)
            popupYOffset = popupYOffset(myHeight, popHeight, false)
        } else {
            // Soft keyboard visible: cover the live candidate bar and keyboard view with one coherent popup.
            popHeight = popupHeight(
                visibleKeyboardPopupHeight(availableSpaceBelow, candidateViewHeight),
                mPopupCandidateView!!.getMeasuredHeight(), false
            )
            popupYOffset = popupYOffset(myHeight, popHeight, false)
        }

        if (DEBUG) Log.i(
            TAG, ("doUpdateCandidatePopup(), expandUpward=" + expandUpward
                    + ", mHeight=" + mHeight
                    + ", getHeight() = " + getHeight()
                    + ", mPopupCandidateView.getHeight() = " + mPopupCandidateView!!.getHeight()
                    + ", mPopupScrollView.getHeight() = " + mPopupScrollView!!.getHeight()
                    + ", offsetOnScreen[1] = " + offsetOnScreen[1]
                    + ", availableSpaceAbove = " + availableSpaceAbove
                    + ", availableSpaceBelow = " + availableSpaceBelow
                    + ", popHeight = " + popHeight
                    + ", popupYOffset = " + popupYOffset
                    + ", CandidateExpandedView.measureHeight = " + mPopupCandidateView!!.getMeasuredHeight())
        )



        if (mCandidatePopupWindow!!.isShowing()) {
            if (DEBUG) Log.i(TAG, "doUpdateCandidatePopup(),mCandidatePopup.isShowing ")
            mCandidatePopupWindow!!.update(mScreenWidth, popHeight)
        } else {
            mCandidatePopupWindow!!.setWidth(mScreenWidth)
            mCandidatePopupWindow!!.setHeight(popHeight)
            // Bottom-positioned popup covers the soft keyboard when present and pins to screen bottom
            // when physical key events hide the soft keyboard.
            mCandidatePopupWindow!!.showAtLocation(
                getRootView(),
                Gravity.BOTTOM or Gravity.START,
                0,
                0
            )
            mPopupScrollView!!.scrollTo(0, 0)
        }

        //Jeremy '12,5,31 do update layoutparams after popupWindow update or creation.
        mPopupCandidateView!!.setLayoutParams(
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                popupContentHeight(popHeight)
            )
        )

        mPopupScrollView!!.setLayoutParams(
            popupFrameContentLayoutParams(popHeight)
        )
    }

    open fun setComposingText(composingText: String) {
        if (DEBUG) Log.i(TAG, "setComposingText():composingText:" + composingText)
        if (!composingText.trim { it <= ' ' }.isEmpty()) {
            mHandler.setComposing(composingText, 0)
            showComposing()
        } else {
            hideComposing()
        }
    }

    fun doHideComposing() {
        if (DEBUG) Log.i(TAG, "doHideComposing()")

        if (mComposingTextView == null) return

        if (embeddedComposing != null ||  // for embedded composing in floating candidateView
            (mComposingTextPopup != null // for fixed candidate View
                    && (mComposingTextPopup!!.isShowing()) || mComposingTextView!!.getVisibility() == VISIBLE)
        ) {
            mComposingTextView!!.setVisibility(INVISIBLE)
            if (embeddedComposing == null && mComposingTextPopup != null && mComposingTextPopup!!.isShowing()) {
                mComposingTextPopup!!.dismiss()
            }
        }
    }

    /**
     * Jeremy '12,6,2 separated from doupdateComposing
     */
    fun doSetComposing(composingText: String?) {
        if (DEBUG) Log.i(
            TAG, "doSetComposing():" + composingText + "; this.isShown()" + this.isShown() +
                    "(mComposingTextView == null):" + (mComposingTextView == null) +
                    ";(embeddedComposing == null):" + (embeddedComposing != null)
        )

        // Initialize mComposingTextView as embedding composing or popup window for fixed candidate mode. Jeremy '15,6,4
        if (embeddedComposing != null) {
            if (mComposingTextView !== embeddedComposing) {
                mComposingTextView = embeddedComposing
                mComposingTextView!!.setBackgroundColor(mColorComposingBackground)
                mComposingTextView!!.setTextColor(mColorComposingText)
            }
        } else {
            if (mComposingPopupTextView == null) {
                val inflater =
                    mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                mComposingPopupTextView = inflater.inflate(
                    R.layout.composingtext,
                    getRootView() as ViewGroup?,
                    false
                ) as TextView?

                if (mComposingTextPopup == null) {
                    mComposingTextPopup = PopupWindow(mContext)
                    mComposingTextPopup!!.setTouchable(false)
                }
                //mComposingTextPopup.setWindowLayoutMode(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);  //Deprecated in API 23. Jeremy '16,7,16
                mComposingTextPopup!!.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT)
                mComposingTextPopup!!.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
                mComposingTextPopup!!.setContentView(mComposingPopupTextView)
                mComposingTextPopup!!.setBackgroundDrawable(null)
            }
            if (mComposingTextView !== mComposingPopupTextView) {
                mComposingTextView = mComposingPopupTextView
                mComposingTextView!!.setBackgroundColor(mColorComposingBackground)
                mComposingTextView!!.setTextColor(mColorComposingText)
            }
        }


        if (composingText != null) {
            mComposingTextView!!.setText(composingText)
            //The text size got is converted into PX already. Thus force setup the setTextSize in unit of PX.
            val scaledTextSize =
                mContext.getResources()
                    .getDimensionPixelSize(R.dimen.composing_text_size) * mLIMEPref.getFontSize()
            mComposingTextView!!.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaledTextSize)
        } else return

        mComposingTextView!!.invalidate() //Jeremy '12,6,2 invalidate and measure so as to get correct height and width later. 
        mComposingTextView!!.setVisibility(VISIBLE)
        if (this.isCandidateExpanded) {
            mComposingTextView!!.setVisibility(INVISIBLE)
            if (mComposingTextPopup != null && mComposingTextPopup!!.isShowing()) {
                mComposingTextPopup!!.dismiss()
            }
            return
        }

        //Jeremy '15,6, 4 bypass updating popup when composing view is embedded in candidate container
        if (embeddedComposing == null) doUpdateComposing()
    }

    /**
     * Update composing to correct location with a delay after setComposing.
     */
    open fun doUpdateComposing() {
        if (DEBUG) Log.i(
            TAG, "doUpdateComposing(): this.isShown()" + this.isShown() +
                    "; embeddedComposing is null:" + (embeddedComposing == null)
        )

        if (!shouldShowComposingPopup(this.isCandidateExpanded, this.isShown())) {
            doHideComposing()
            return
        }

        if (embeddedComposing != null) return  //Jeremy '15,6, 4 bypass updating popup when composing view is embedded in candidate container


        //mComposingTextView.measure(
        //MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        //MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        //final int popupWidth = mComposingTextView.getMeasuredWidth();  //Jeremy '12,6,2 use getWidth and getHeight instead
        //final int popupHeight = mComposingTextView.getMeasuredHeight();
        // getMeasuredWidth cannot get correct width of textVIEW in android 6 Jeremy '16,7,16
        val composingText = mComposingTextView!!.getText().toString()

        //if(composingText == null) return;  // avoid measureText on null object.  Jeremy '16/7/26
        val paint: Paint = mComposingTextView!!.getPaint()
        val metrics = paint.getFontMetrics()
        val popupWidth = paint.measureText(composingText).toInt()
        val popupHeight = (metrics.bottom - metrics.top).toInt()


        val offsetInWindow = IntArray(2)
        this.getLocationInWindow(offsetInWindow)
        var mPopupComposingY = offsetInWindow[1]
        val mPopupComposingX = popupBaseXInWindow(offsetInWindow[0])

        mPopupComposingY -= popupHeight


        if (DEBUG) Log.i(
            TAG, ("doUpdateComposing():mPopupComposingX:" + mPopupComposingX
                    + ". mPopupComposingY:" + mPopupComposingY
                    + ". popupWidth = " + popupWidth
                    + ". popupHeight = " + popupHeight
                    + ". mComposingTextPopup.isShowing()=" + mComposingTextPopup!!.isShowing())
        )


        try {
            if (mComposingTextPopup!!.isShowing()) {
                mComposingTextPopup!!.update(
                    mPopupComposingX, mPopupComposingY,
                    popupWidth, popupHeight
                )
            } else {
                mComposingTextPopup!!.setWidth(popupWidth)
                mComposingTextPopup!!.setHeight(popupHeight)
                mComposingTextPopup!!.showAtLocation(
                    this, Gravity.NO_GRAVITY, mPopupComposingX,
                    mPopupComposingY
                )
            }
        } catch (e: Exception) {
            // ignore candidate construct error
            Log.e(TAG, "Error in candidate view", e)
        }
    }

    fun showComposing() {
        if (DEBUG) Log.i(TAG, "showComposing()")
        //jeremy '12,6,3 moved the creation of mComposingTextPopup and mComposingTextView from doUpdateComposing
        //Jeremy '12,4,8 to avoid fc when hard keyboard is engaged and candidateview is not shown
        if (!shouldShowComposingPopup(this.isCandidateExpanded, this.isShown())) return

        val composingShowDelayMs = 50 // Delay before showing composing text
        mHandler.updateComposing(composingShowDelayMs)
    }

    fun hideComposing() {
        if (DEBUG) Log.i(TAG, "hideComposing()")
        val composingDismissDelayMs = 100 // Delay before dismissing composing text
        mHandler.dismissComposing(composingDismissDelayMs) //Jeremy '12,6,3 the same delay as showComposing to avoid showed after hided
    }

    fun showCandidatePopup() {
        if (DEBUG) Log.i(TAG, "showCandidatePopup()")

        mHandler.showCandidatePopup(0)
    }

    fun hideCandidatePopup() {
        if (DEBUG) Log.i(TAG, "hideCandidatePopup()")

        mHandler.dismissCandidatePopup(0)
    }

    private fun updateScreenSize(wm: WindowManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val metrics = wm.getCurrentWindowMetrics()
                mScreenWidth = metrics.getBounds().width()
                mScreenHeight = metrics.getBounds().height()
                if (mScreenWidth > 0 && mScreenHeight > 0) {
                    DiagnosticLog.record(
                        mContext,
                        TAG,
                        "updateScreenSize() currentWindowMetrics=${mScreenWidth}x$mScreenHeight"
                    )
                    return
                }
                DiagnosticLog.record(
                    mContext,
                    TAG,
                    "updateScreenSize() invalid currentWindowMetrics=${mScreenWidth}x$mScreenHeight"
                )
            } catch (t: Throwable) {
                Log.w(TAG, "updateScreenSize() failed, falling back to defaultDisplay: $t")
                DiagnosticLog.recordThrowable(
                    mContext,
                    "$TAG updateScreenSize() currentWindowMetrics failed",
                    t
                )
            }
        }

        updateScreenSizeFromDefaultDisplay(wm)
        DiagnosticLog.record(
            mContext,
            TAG,
            "updateScreenSize() defaultDisplay=${mScreenWidth}x$mScreenHeight"
        )
    }

    @Suppress("deprecation")
    private fun updateScreenSizeFromDefaultDisplay(wm: WindowManager) {
        val screenSize = Point()
        wm.getDefaultDisplay().getSize(screenSize)
        mScreenWidth = screenSize.x
        mScreenHeight = screenSize.y
    }

    fun dismissComposingFromCandidate() {
        hideCandidatePopup()
        if (mService != null) {
            mService!!.dismissCandidateComposing()
        } else {
            clear()
        }
    }

    fun makeDismissButtonBackground(): Drawable {
        val background = GradientDrawable()
        val color = mColorNormalText and 0x00ffffff
        background.setColor(color or 0x1a000000)
        background.setCornerRadius(dpToPx(6).toFloat())
        return background
    }

    fun makeDismissButtonGlyph(): Drawable {
        return DismissGlyphDrawable(mColorNormalText, dpToPx(14))
    }

    fun showLimeToast(text: CharSequence?) {
        if (text == null || text.length == 0) return
        mHandler.showLimeToast(text)
    }

    fun showLimeToastUntilNextKey(text: CharSequence?) {
        if (text == null || text.length == 0) return
        mHandler.showLimeToastUntilNextKey(text)
    }

    fun hideLimeToast() {
        mHandler.hideLimeToast()
    }

    private fun doShowLimeToast(text: CharSequence?) {
        if (!shouldShowLimeToast(getWindowToken() != null, text)) return

        if (mLimeToastTextView == null) {
            mLimeToastTextView = TextView(mContext)
            mLimeToastTextView!!.setSingleLine(true)
            val hPad = dpToPx(8)
            val vPad = dpToPx(3)
            mLimeToastTextView!!.setPadding(hPad, vPad, hPad, vPad)

            val background = GradientDrawable()
            background.setColor(mColorComposingBackground)
            background.setCornerRadius(dpToPx(6).toFloat())
            mLimeToastTextView!!.setBackground(background)
            mLimeToastTextView!!.setTextColor(mColorComposingText)
        }

        val scaledTextSize =
            mContext.getResources()
                .getDimensionPixelSize(R.dimen.composing_text_size) * mLIMEPref.getFontSize()
        mLimeToastTextView!!.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaledTextSize)
        mLimeToastTextView!!.setText(text)
        mLimeToastTextView!!.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )

        if (mLimeToastPopup == null) {
            mLimeToastPopup = PopupWindow(mContext)
            mLimeToastPopup!!.setTouchable(false)
            mLimeToastPopup!!.setClippingEnabled(false)
            mLimeToastPopup!!.setContentView(mLimeToastTextView)
            mLimeToastPopup!!.setBackgroundDrawable(null)
        }

        val toastWidth = mLimeToastTextView!!.getMeasuredWidth()
        val toastHeight = mLimeToastTextView!!.getMeasuredHeight()
        val offsetInWindow = IntArray(2)
        getLocationInWindow(offsetInWindow)

        val baseX = popupBaseXInWindow(offsetInWindow[0])
        var x = baseX
        var y = offsetInWindow[1] - toastHeight
        if (embeddedComposing != null && embeddedComposing!!.getVisibility() == VISIBLE) {
            val composingOffset = IntArray(2)
            embeddedComposing!!.getLocationInWindow(composingOffset)
            x = composingOffset[0] + embeddedComposing!!.getWidth() + dpToPx(8)
            y = composingOffset[1]
        } else if (mComposingTextView != null && mComposingTextView!!.getVisibility() == VISIBLE) {
            x = baseX + mComposingTextView!!.getWidth() + dpToPx(8)
        }

        val rightEdge = offsetInWindow[0] + max(getWidth(), 0)
        if (rightEdge > 0) {
            x = max(baseX, min(x, rightEdge - toastWidth))
        }

        try {
            if (mLimeToastPopup!!.isShowing()) {
                mLimeToastPopup!!.update(x, y, toastWidth, toastHeight)
            } else {
                mLimeToastPopup!!.setWidth(toastWidth)
                mLimeToastPopup!!.setHeight(toastHeight)
                mLimeToastPopup!!.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing lime toast", e)
        }
    }

    private fun doHideLimeToast() {
        if (mLimeToastPopup != null && mLimeToastPopup!!.isShowing()) {
            mLimeToastPopup!!.dismiss()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return Math.round(dp * mContext.getResources().getDisplayMetrics().density)
    }

    fun storePopupDismissButtonWidth(dismissButton: View?) {
        if (dismissButton == null || mPopupDismissButtonWidth > 0) return

        var width = dismissButton.getWidth()
        if (width <= 0) width = dismissButton.getMeasuredWidth()
        if (width <= 0 && dismissButton.getLayoutParams() != null) {
            width = dismissButton.getLayoutParams().width
        }

        if (width > 0) {
            mPopupDismissButtonWidth = width
        } else {
            dismissButton.post(Runnable { storePopupDismissButtonWidth(dismissButton) })
        }
    }

    private fun popupBaseXInWindow(candidateLeft: Int): Int {
        var rowLeft = candidateLeft
        val parent = getParent()
        if (parent is View) {
            val parentOffset = IntArray(2)
            (parent as View).getLocationInWindow(parentOffset)
            rowLeft = parentOffset[0]
        }
        return popupBaseX(rowLeft, mPopupDismissButtonWidth)
    }

    fun popupDismissButtonWidth(): Int {
        if (mPopupDismissButtonWidth > 0) return mPopupDismissButtonWidth
        return mContext.getResources().getDimensionPixelSize(R.dimen.candidate_dismiss_button_width)
    }

    fun popupExpandButtonWidth(): Int {
        return mExpandButtonWidth
    }

    private fun configurePopupOverlayControls(rowHeight: Int) {
        if (mCandidatePopupContainer == null || rowHeight <= 0) return
        val popupDismiss =
            mCandidatePopupContainer!!.findViewById<ImageButton?>(R.id.candidate_dismiss)
        configurePopupOverlayControl(
            popupDismiss,
            popupDismissButtonWidth(),
            rowHeight,
            Gravity.TOP or Gravity.START
        )

        val popupCollapse =
            mCandidatePopupContainer!!.findViewById<ImageButton?>(R.id.candidate_expand_collapse)
        configurePopupOverlayControl(
            popupCollapse,
            popupExpandButtonWidth(),
            rowHeight,
            Gravity.TOP or Gravity.END
        )
    }

    private fun configurePopupOverlayControl(
        button: ImageButton?,
        width: Int,
        height: Int,
        gravity: Int
    ) {
        if (button == null || width <= 0 || height <= 0) return
        val params: FrameLayout.LayoutParams?
        val current = button.getLayoutParams()
        if (current is FrameLayout.LayoutParams) {
            params = current
        } else {
            params = FrameLayout.LayoutParams(width, height)
        }
        params.width = width
        params.height = height
        params.gravity = gravity
        button.setLayoutParams(params)
    }

    private class DismissGlyphDrawable(color: Int, private val intrinsicSize: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            paint.setColor(color)
            paint.setStrokeCap(Paint.Cap.ROUND)
            paint.setStrokeJoin(Paint.Join.ROUND)
            paint.setStyle(Paint.Style.STROKE)
        }

        override fun draw(canvas: Canvas) {
            val bounds = getBounds()
            val size = min(bounds.width(), bounds.height()).toFloat()
            if (size <= 0) return

            val half = min(intrinsicSize.toFloat(), size) * 0.34f
            val centerX = bounds.exactCenterX()
            val centerY = bounds.exactCenterY()
            paint.setStrokeWidth(max(2f, min(intrinsicSize.toFloat(), size) * 0.12f))
            canvas.drawLine(
                centerX - half, centerY - half,
                centerX + half, centerY + half, paint
            )
            canvas.drawLine(
                centerX + half, centerY - half,
                centerX - half, centerY + half, paint
            )
        }

        override fun getIntrinsicWidth(): Int {
            return intrinsicSize
        }

        override fun getIntrinsicHeight(): Int {
            return intrinsicSize
        }

        override fun setAlpha(alpha: Int) {
            paint.setAlpha(alpha)
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.setColorFilter(colorFilter)
        }

        @Deprecated("Deprecated in Drawable")
        override fun getOpacity(): Int {
            return PixelFormat.TRANSLUCENT
        }
    }


    private var mHasRoomForExpanding = true


    init {
        mCandidateView = this
        // Jeremy '15,6,4 for embedded composing view in candidateView when floating candidateView (not fixed)

        mLIMEPref = LIMEPreferenceManager(mContext)

        val styledAttributes = mContext.getTheme().obtainStyledAttributes(
            attrs, R.styleable.LIMECandidateView, defStyle, R.style.LIMECandidateView
        )
        try {
            val a = styledAttributes
            val n = a.getIndexCount()
            for (i in 0..<n) {
                val attr = a.getIndex(i)

                if (attr == R.styleable.LIMECandidateView_suggestHighlight) {
                    mDrawableSuggestHighlight = a.getDrawable(attr)
                } else if (attr == R.styleable.LIMECandidateView_voiceInputIcon) {
                    mDrawableVoiceInput = a.getDrawable(attr)
                } else if (attr == R.styleable.LIMECandidateView_emojiButtonIcon) {
                    mDrawableEmojiInput = a.getDrawable(attr)
                } else if (attr == R.styleable.LIMECandidateView_ExpandDownButtonIcon) {
                    mDrawableExpandDownButton = a.getDrawable(attr)
                } else if (attr == R.styleable.LIMECandidateView_ExpandUpButtonIcon) {
                    mDrawableExpandUpButton = a.getDrawable(attr)
                } else if (attr == R.styleable.LIMECandidateView_closeButtonIcon) {
                    mDrawableCloseButton = a.getDrawable(attr)
                } else if (attr == R.styleable.LIMECandidateView_keyboardShowIcon) {
                    mDrawableKeyboardShow = a.getDrawable(attr)
                } else if (attr == R.styleable.LIMECandidateView_candidateBackground) {
                    mColorBackground = a.getColor(
                        attr,
                        ContextCompat.getColor(mContext, R.color.third_background_light)
                    )
                } else if (attr == R.styleable.LIMECandidateView_composingTextColor) {
                    mColorComposingText = a.getColor(
                        attr,
                        ContextCompat.getColor(mContext, R.color.second_foreground_light)
                    )
                } else if (attr == R.styleable.LIMECandidateView_composingBackgroundColor) {
                    mColorComposingBackground = a.getColor(
                        attr,
                        ContextCompat.getColor(mContext, R.color.composing_background_light)
                    )
                } else if (attr == R.styleable.LIMECandidateView_candidateNormalTextColor) {
                    mColorNormalText =
                        a.getColor(attr, ContextCompat.getColor(mContext, R.color.foreground_light))
                } else if (attr == R.styleable.LIMECandidateView_candidateNormalTextHighlightColor) {
                    mColorNormalTextHighlight =
                        a.getColor(attr, ContextCompat.getColor(mContext, R.color.foreground_light))
                } else if (attr == R.styleable.LIMECandidateView_composingCodeColor) {
                    mColorComposingCode = a.getColor(
                        attr,
                        ContextCompat.getColor(mContext, R.color.color_common_green_hl)
                    )
                } else if (attr == R.styleable.LIMECandidateView_composingCodeHighlightColor) {
                    mColorComposingCodeHighlight = a.getColor(
                        attr,
                        ContextCompat.getColor(mContext, R.color.third_background_light)
                    )
                } else if (attr == R.styleable.LIMECandidateView_spacerColor) {
                    mColorSpacer =
                        a.getColor(attr, ContextCompat.getColor(mContext, R.color.candidate_spacer))
                } else if (attr == R.styleable.LIMECandidateView_selKeyColor) {
                    mColorSelKey = a.getColor(
                        attr,
                        ContextCompat.getColor(mContext, R.color.candidate_selection_keys)
                    )
                } else if (attr == R.styleable.LIMECandidateView_selKeyShiftedColor) {
                    mColorSelKeyShifted = a.getColor(
                        attr,
                        ContextCompat.getColor(mContext, R.color.color_common_green_hl)
                    )
                }
            }
        } finally {
            styledAttributes.recycle()
        }
        val r = mContext.getResources()
        val wm = mContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        updateScreenSize(wm)

        mVerticalPadding =
            (r.getDimensionPixelSize(R.dimen.candidate_vertical_padding) * mLIMEPref.getFontSize()).toInt()
        configHeight =
            (r.getDimensionPixelSize(R.dimen.candidate_stripe_height) * mLIMEPref.getFontSize()).toInt()
        mHeight = configHeight + mVerticalPadding
        mExpandButtonWidth =
            r.getDimensionPixelSize(R.dimen.candidate_expand_button_width) // *mLIMEPref.getFontSize());

        mCandidatePaint = Paint()
        mCandidatePaint.setColor(mColorNormalText)
        mCandidatePaint.setAntiAlias(true)
        mCandidatePaint.setTextSize(r.getDimensionPixelSize(R.dimen.candidate_font_size) * mLIMEPref.getFontSize())
        mCandidatePaint.setStrokeWidth(0f)


        mSelKeyPaint = Paint()
        mSelKeyPaint.setColor(mColorSelKey)
        mSelKeyPaint.setAntiAlias(true)
        mSelKeyPaint.setTextSize(r.getDimensionPixelSize(R.dimen.candidate_number_font_size) * mLIMEPref.getFontSize())
        mSelKeyPaint.setStyle(Paint.Style.FILL_AND_STROKE)


        //final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        //Jeremy '12,4,23 add mContext parameter.  The constructor without context is deprecated
        mGestureDetector = GestureDetector(mContext, object : SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent,
                distanceX: Float, distanceY: Float
            ): Boolean {
                if (DEBUG) Log.i(
                    TAG,
                    "onScroll(): distanceX = " + distanceX + "; distanceY = " + distanceY
                )


                //Jeremy '12,4,8 filter out small scroll which is actually candidate selection.
                if (abs(distanceX) < mHeight.toFloat() / 5 && abs(distanceY) < mHeight.toFloat() / 5) return true

                mScrolled = true

                // Update full candidate list before scroll
                checkHasMoreRecords()


                var sx = getScrollX()
                sx += distanceX.toInt()
                if (sx < 0) {
                    sx = 0
                }
                if (sx + getWidth() > mTotalWidth) {
                    sx -= distanceX.toInt()
                }

                if (mLIMEPref.getSelectDefaultOnSliding()) {
                    hasSlide = true
                    mTargetScrollX = sx
                    scrollTo(sx, getScrollY())
                    currentX =
                        getScrollX() //Jeremy '12,7,6 set currentX to the left edge of current scrollview after scrolled
                } else {
                    hasSlide = false
                    if (distanceX < 0) {
                        goLeft = true
                        goRight = false
                    } else if (distanceX > 0) {
                        goLeft = false
                        goRight = true
                    } else {
                        mTargetScrollX = sx
                    }
                }

                return true
            }
        }, Handler(Looper.getMainLooper()))
    }

    /**
     * Check if there's room for expanding the popup in the specified direction.
     * @param expandUpward true to check space above CandidateView, false to check space below
     * @return true if there's enough room (more than 2 * mHeight)
     */
    fun hasRoomForExpanding(expandUpward: Boolean): Boolean {
        if (!mCandidatePopupWindow!!.isShowing()) {
            val offsetOnScreen = IntArray(2)
            this.getLocationOnScreen(offsetOnScreen)
            val candidateViewHeight = getHeight()

            if (expandUpward) {
                // Check space above CandidateView
                val availableSpaceAbove = offsetOnScreen[1]
                mHasRoomForExpanding = availableSpaceAbove > 2 * mHeight
            } else {
                // Check space below CandidateView
                val availableSpaceBelow = mScreenHeight - offsetOnScreen[1] - candidateViewHeight
                mHasRoomForExpanding = availableSpaceBelow > 2 * mHeight
            }
        }
        return mHasRoomForExpanding
    }

    @Deprecated("")
    fun setTransparentCandidateView(transparent: Boolean) {
        mTransparentCandidateView = transparent
    }

    /**
     * A connection back to the service to communicate with the text field
     */
    fun setService(listener: LIMEService?) {
        mService = listener
    }

    public override fun computeHorizontalScrollRange(): Int {
        return mTotalWidth
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (DEBUG) Log.i(TAG, "onMeasure()")
        val measuredWidth = resolveSize(mTotalWidth, widthMeasureSpec)

        val desiredHeight = mHeight

        // Maximum possible width and desired height
        setMeasuredDimension(
            measuredWidth,
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }


    /**
     * If the canvas is null, then only touch calculations are performed to pick the target
     * candidate.
     */
    @Synchronized
    override fun onDraw(canvas: Canvas) {
        doDraw(canvas)
    }

    private fun prepareLayout() {
        doDraw(null)
    }

    private fun doDraw(canvas: Canvas?) {
        if (this.isShowingDictationStatus) {
            drawDictationStatus(canvas)
            return
        }
        if (mSuggestions == null) return
        if (DEBUG) Log.i(
            TAG,
            "CandidateView:doDraw():Suggestion mCount:" + mCount + " mSuggestions.size:" + mSuggestions!!.size
        )
        mTotalWidth = 0

        updateFontSize()

        if (mBgPadding == null) {
            mBgPadding = Rect(0, 0, 0, 0)
            if (getBackground() != null) {
                getBackground().getPadding(mBgPadding!!)
            }
        }

        val height = mHeight
        val bgPadding = mBgPadding
        val candidatePaint = mCandidatePaint
        val candidateEmojiPaint = mCandidatePaint
        candidateEmojiPaint.setTextSize((candidateEmojiPaint.getTextSize() * 0.9).toFloat())

        val selKeyPaint = mSelKeyPaint
        val touchX = mTouchX
        val scrollX = getScrollX()
        val scrolled = mScrolled

        val textBaseLine =
            (((height - mCandidatePaint.getTextSize()) / 2) - mCandidatePaint.ascent()).toInt()

        // Modified by jeremy '10, 3, 29.  Update mselectedindex if touched and build wordX[i] and wordwidth[i]
        var x = 0
        val count = mCount //Cache count here '11,8,18
        for (i in 0..<count) {
            if (count != mCount || mSuggestions == null || count != mSuggestions!!.size)  //|| mSuggestions.isEmpty()|| i >= mSuggestions.size()) //redundant check
                return  // mSuggestion is updated, force abort


            var suggestion = mSuggestions!!.get(i)!!.getWord()
            if (i == 0 && mSuggestions!!.size > 1 && mSuggestions!!.get(1)!!
                    .isRuntimeBuiltPhraseRecord() && suggestion!!.length > 8
            ) {
                suggestion = suggestion.substring(0, 2) + ".."
            }
            val base = if (suggestion == null) 0f else candidatePaint.measureText("。")
            var textWidth = if (suggestion == null) 0f else candidatePaint.measureText(suggestion)

            if (textWidth < base) {
                textWidth = base
            }


            val wordWidth: Int = textWidth.toInt() + X_GAP * 2

            mWordX[i] = x

            mWordWidth[i] = wordWidth


            if (touchX + scrollX >= x && touchX + scrollX < x + wordWidth && !scrolled) {
                mSelectedIndex = i
            }
            x += wordWidth
        }

        mTotalWidth = x

        if (DEBUG) Log.i(
            TAG,
            "CandidateView:doDraw():mTotalWidth :" + mTotalWidth + "  this.getWidth():" + this.getWidth()
        )

        //Jeremy '11,8,11. If the candidate list is within 1 page and has more records, get full records first.
        if (mTotalWidth < this.getWidth()) checkHasMoreRecords()


        // Paint all the suggestions and lines.
        if (canvas != null) {
            // Moved from above by jeremy '10 3, 29. Paint mSelectedindex in highlight here

            if (count > 0 && mSelectedIndex >= 0) {
                //    candidatePaint.setColor(mColorComposingCode);
                //    canvas.drawRect(mWordX[mSelectedIndex],bgPadding.top, mWordWidth[mSelectedIndex] , height, candidatePaint);

                canvas.translate(mWordX[mSelectedIndex].toFloat(), 0f)
                mDrawableSuggestHighlight!!.setBounds(
                    0,
                    bgPadding!!.top,
                    mWordWidth[mSelectedIndex],
                    height
                )
                mDrawableSuggestHighlight!!.draw(canvas)
                canvas.translate(-mWordX[mSelectedIndex].toFloat(), 0f)
            }
            if (mTransparentCandidateView) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                val backgroundPaint = Paint()
                backgroundPaint.setColor(
                    ContextCompat.getColor(
                        mContext,
                        R.color.third_background_light
                    )
                )
                backgroundPaint.setAlpha(33)
                backgroundPaint.setStyle(Paint.Style.FILL)

                canvas.drawRect(
                    0.5f,
                    bgPadding!!.top.toFloat(),
                    mScreenWidth.toFloat(),
                    height.toFloat(),
                    backgroundPaint
                )
            }


            for (i in 0..<count) {
                if (count != mCount || mSuggestions == null || count != mSuggestions!!.size)  //    || mSuggestions.isEmpty() || i >= mSuggestions.size()) //redundant check
                    break

                val isEmoji = mSuggestions!!.get(i)!!.isEmojiRecord()
                var suggestion = mSuggestions!!.get(i)!!.getWord()
                if (i == 0 && mSuggestions!!.size > 1 && mSuggestions!!.get(1)!!
                        .isRuntimeBuiltPhraseRecord() && suggestion!!.length > 8
                ) {
                    suggestion = suggestion.substring(0, 2) + ".."
                }

                val c = i + 1
                when (mSuggestions!!.get(i)!!.getRecordType()) {
                    Mapping.RECORD_COMPOSING_CODE -> if (mSelectedIndex == 0) {
                        if (mTransparentCandidateView) {
                            candidatePaint.setColor(mColorInvertedTextTransparent)
                        } else {
                            candidatePaint.setColor(mColorComposingCodeHighlight)
                        }
                    } else candidatePaint.setColor(mColorComposingCode)

                    Mapping.RECORD_CHINESE_PUNCTUATION_SYMBOL, Mapping.RECORD_RELATED_PHRASE -> {
                        selKeyPaint.setColor(mColorSelKeyShifted)
                        if (i == mSelectedIndex) candidatePaint.setColor(mColorNormalTextHighlight)
                        else candidatePaint.setColor(mColorNormalText)
                    }

                    Mapping.RECORD_EXACT_MATCH_TO_CODE, Mapping.RECORD_PARTIAL_MATCH_TO_CODE, Mapping.RECORD_RUNTIME_BUILT_PHRASE, Mapping.RECORD_ENGLISH_SUGGESTION -> {
                        selKeyPaint.setColor(mColorSelKey)
                        if (i == mSelectedIndex) candidatePaint.setColor(mColorNormalTextHighlight)
                        else candidatePaint.setColor(mColorNormalText)
                    }

                    else -> {
                        selKeyPaint.setColor(mColorSelKey)
                        if (i == mSelectedIndex) candidatePaint.setColor(mColorNormalTextHighlight)
                        else candidatePaint.setColor(mColorNormalText)
                    }
                }

                if (isEmoji) {
                    canvas.drawText(
                        suggestion!!,
                        (mWordX[i] + X_GAP).toFloat(),
                        Math.round(textBaseLine * 0.95).toFloat(),
                        candidateEmojiPaint
                    )
                } else {
                    canvas.drawText(
                        suggestion!!,
                        (mWordX[i] + X_GAP).toFloat(),
                        textBaseLine.toFloat(),
                        candidatePaint
                    )
                }
                if (mShowNumber) {
                    //Jeremy '11,6,17 changed from <=10 to mDisplaySelkey length. The length maybe 11 or 12 if shifted with space.
                    if (c <= mDisplaySelkey.length) {
                        //Jeremy '11,6,11 Drawing text using relative font dimensions.
                        canvas.drawText(
                            mDisplaySelkey.substring(c - 1, c),
                            mWordX[i] + mWordWidth[i] - height * 0.3f, height * 0.4f, selKeyPaint
                        )
                    }
                }
                //Draw spacer
                candidatePaint.setColor(mColorSpacer)
                canvas.drawLine(
                    mWordX[i] + mWordWidth[i] + 0.5f,
                    bgPadding!!.top + (mVerticalPadding.toFloat() / 2),
                    mWordX[i] + mWordWidth[i] + 0.5f,
                    height - (mVerticalPadding.toFloat() / 2),
                    candidatePaint
                )
                candidatePaint.setFakeBoldText(false)
            }

            if (mTargetScrollX != getScrollX()) {
                if (DEBUG) Log.i(
                    TAG,
                    "CandidateView:doDraw():mTargetScrollX :" + mTargetScrollX + "  getScrollX():" + getScrollX()
                )
                scrollToTarget()
            }
        }
    }


    private fun checkHasMoreRecords() {
        if (DEBUG) Log.i(
            TAG,
            "checkHasMoreRecords(), waitingForMoreRecords = " + waitingForMoreRecords
        )

        if (waitingForMoreRecords) return  //Jeremy '12,7,6 avoid repeated calls of requestFullrecords().

        if (mSuggestions != null && !mSuggestions!!.isEmpty() && mSuggestions!!.get(mSuggestions!!.size - 1)!!
                .getCode() != null &&
            mSuggestions!!.get(mSuggestions!!.size - 1)!!.isHasMoreRecordsMarkRecord()
        ) {  //getCode().equals("has_more_records")) {
            waitingForMoreRecords = true
            val updatingThread: Thread = object : Thread() {
                override fun run() {
                    mService!!.requestFullRecords(mSuggestions!!.get(0)!!.isRelatedPhraseRecord())
                }
            }
            updatingThread.start()
        }
    }

    private fun scrollToTarget() {
        var sx = getScrollX()
        if (mTargetScrollX > sx) {
            sx += SCROLL_PIXELS
            if (sx >= mTargetScrollX) {
                sx = mTargetScrollX
                requestLayout()
            }
        } else {
            sx -= SCROLL_PIXELS
            if (sx <= mTargetScrollX) {
                sx = mTargetScrollX
                requestLayout()
            }
        }
        scrollTo(sx, getScrollY())

        invalidate()
    }


    open fun setSuggestions(
        suggestions: MutableList<Mapping?>?,
        showNumber: Boolean,
        displaySelkey: String
    ) {
        mDisplaySelkey = displaySelkey
        setSuggestions(suggestions, showNumber)
    }

    fun setSuggestionsWithoutHighlight(
        suggestions: MutableList<Mapping?>?,
        showNumber: Boolean,
        displaySelkey: String
    ) {
        mDisplaySelkey = displaySelkey
        setSuggestions(suggestions, showNumber, false)
    }

    @Synchronized
    fun setSuggestions(suggestions: MutableList<Mapping?>?, showNumber: Boolean) {
        setSuggestions(suggestions, showNumber, true)
    }

    @Synchronized
    private fun setSuggestions(
        suggestions: MutableList<Mapping?>?,
        showNumber: Boolean,
        seedDefaultSelection: Boolean
    ) {
        //clear();
        //Jeremy '11,8,14 moved from clear();
        var showNumber = showNumber
        if (DEBUG) Log.i(TAG, "setSuggestions()")

        val res = mContext.getResources()

        configHeight = (res.getDimensionPixelSize(
            R.dimen.candidate_stripe_height
        ) * mLIMEPref.getFontSize()).toInt()
        mVerticalPadding =
            (res.getDimensionPixelSize(R.dimen.candidate_vertical_padding) * mLIMEPref.getFontSize()).toInt()
        mHeight = configHeight + mVerticalPadding

        currentX = 0
        mTouchX = OUT_OF_BOUNDS
        mCount = 0
        mSelectedIndex = -1

        if (mLIMEPref.getDisablePhysicalSelKeyOption()) {
            showNumber = true
        }

        mShowNumber = showNumber

        if (mShowNumber) X_GAP =
            (res.getDimensionPixelSize(R.dimen.candidate_font_size) * 0.35f).toInt() //13;
        else X_GAP = (res.getDimensionPixelSize(R.dimen.candidate_font_size) * 0.25f).toInt()


        if (suggestions != null) {
            mSuggestions = LinkedList<Mapping?>(suggestions)

            if (!mSuggestions!!.isEmpty()) {
                // Add by Jeremy '10, 3, 29
                mCount = mSuggestions!!.size
                if (mCount > MAX_SUGGESTIONS) mCount = MAX_SUGGESTIONS

                if (DEBUG) Log.i(
                    TAG, ("setSuggestions():mSuggestions.size():" + mSuggestions!!.size
                            + " mCount=" + mCount)
                )

                if (seedDefaultSelection) {
                    mSelectedIndex =
                        LIMEService.defaultHighlightedCandidateIndex(mSuggestions, false)
                }
            } else {
                if (DEBUG) Log.i(TAG, "setSuggestions():mSuggestions=null")
            }
        } else {
            mSuggestions = LinkedList<Mapping?>()
            hideCandidatePopup()
        }

        prepareLayout()


        mHandler.updateUI(0)
    }


    fun clear() {
        if (DEBUG) Log.i(TAG, "clear()")
        mDictationState = DictationState.IDLE
        mDictationText = ""
        //mHeight =0; //Jeremy '12,5,6 hide candidate bar when candidateview is fixed.
        if (mSuggestions != null) mSuggestions!!.clear()
        mCount = 0
        // Jeremy 11,8,14 close all popup on clear
        setComposingText("")
        mTargetScrollX = 0
        mTotalWidth = 0
        hideComposing()


        prepareLayout()
        mHandler.updateUI(0)

        val r = mContext.getResources()
        configHeight = (r.getDimensionPixelSize(
            R.dimen.candidate_stripe_height
        ) * mLIMEPref.getFontSize()).toInt()
        mVerticalPadding =
            (r.getDimensionPixelSize(R.dimen.candidate_vertical_padding) * mLIMEPref.getFontSize()).toInt()
        configHeight =
            (r.getDimensionPixelSize(R.dimen.candidate_stripe_height) * mLIMEPref.getFontSize()).toInt()
        mHeight = configHeight + mVerticalPadding
    }

    val isEmpty: Boolean
        get() = mCount == 0

    fun startVoiceInput() {
        if (mService != null) mService!!.startVoiceInput()
    }

    //Jeremy '12,5,6 hide candidate bar when candidateView is fixed.
    fun forceHide() {
        if (DEBUG) Log.i(TAG, "forceHide()")
        mHeight = 0
        mDictationState = DictationState.IDLE
        mDictationText = ""
        //clear();
        //resetWidth();// will cause wrong thread exception. clear() will call updateUI() and will do resetWidth
        mSuggestions = EMPTY_LIST
        // Jeremy 11,8,14 close all popup on clear
        setComposingText("")
        mTargetScrollX = 0
        mTotalWidth = 0
        mHandler.dismissComposing(0)
        mHandler.updateUI(0)
    }

    override fun onTouchEvent(me: MotionEvent): Boolean {
        if (DEBUG) Log.i(TAG, "OnTouchEvent() action = " + me.getActionMasked())
        if (mGestureDetector != null && mGestureDetector!!.onTouchEvent(me)) {
            if (DEBUG) Log.i(TAG, "OnTouchEvent() event processed by mGestureDetector")
            return true
        }

        val action = me.getActionMasked()
        val x = me.getX().toInt()
        val y = me.getY().toInt()
        mTouchX = x
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                mScrolled = false
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                if (y <= 0) {
                    // Fling up!?
                    if (mSelectedIndex >= 0) {
                        takeSelectedSuggestion(true)
                        mSelectedIndex = -1
                    }
                }
                invalidate()
            }

            MotionEvent.ACTION_UP -> {
                if (DEBUG) Log.i(
                    TAG,
                    "OnTouchEvent():MotionEvent.ACTION_UP, mScrolled=" + mScrolled + "; mSelectedIndex = " + mSelectedIndex
                )
                if (!mScrolled && isExpandEdgeTap(x, getWidth(), mExpandButtonWidth, mTotalWidth)) {
                    mSelectedIndex = -1
                    removeHighlight()
                    showCandidatePopup()
                    performClick()
                    return true
                }
                if (!mScrolled) {
                    if (mSelectedIndex >= 0) {
                        takeSelectedSuggestion(true)
                    }
                }
                mSelectedIndex = -1
                removeHighlight()
                requestLayout()

                if (!hasSlide) {
                    if (goLeft) {
                        scrollPrev()
                    }
                    if (goRight) {
                        scrollNext()
                    }
                }
                performClick()
            }
        }

        return true
    }

    override fun performClick(): Boolean {
        // Calls the super implementation, which generates an AccessibilityEvent
        // and calls the onClick() listener on the view, if any
        super.performClick()
        return true
    }

    fun scrollPrev() {
        var i = 0
        //final int mCount = mSuggestions.size();
        var firstItem = 0 // Actually just before the first item, if at the boundary
        while (i < mCount) {
            if (mWordX[i] < currentX
                && mWordX[i] + mWordWidth[i] >= currentX - 1
            ) {
                firstItem = i
                break
            }
            i++
        }
        var leftEdge = mWordX[firstItem] + mWordWidth[firstItem] - getWidth()
        if (leftEdge < 0) {
            leftEdge = 0
        }
        currentX = leftEdge
        updateScrollPosition(leftEdge)
    }


    fun scrollNext() {
        if (DEBUG) Log.i(
            TAG,
            "scrollNext(), currentX = " + currentX + ", mSelectedIndex = " + mSelectedIndex
        )
        checkHasMoreRecords() //Jeremy '12,7,6 check if has more records before scroll
        var i = 0
        var targetX = currentX
        //final int mCount = mSuggestions.size();
        val rightEdge = currentX + getWidth()
        while (i < mCount) {
            if (mWordX[i] <= rightEdge &&
                mWordX[i] + mWordWidth[i] >= rightEdge
            ) {
                targetX = min(mWordX[i], mTotalWidth - getWidth())
                currentX = mWordX[i]
                break
            }
            i++
        }
        if (DEBUG) Log.i(
            TAG,
            "scrollNext(), new currentX = " + currentX + ", new mSelectedIndex = " + mSelectedIndex
        )
        updateScrollPosition(targetX)
    }

    private fun updateScrollPosition(targetX: Int) {
        if (targetX != mTouchX) {
            mTargetScrollX = targetX
            requestLayout()
            invalidate()
            mScrolled = true
        }
    }

    //Add by Jeremy '10, 3, 29 for DPAD (physical keyboard) selection.
    open fun selectNext() {
        if (DEBUG) Log.i(
            TAG,
            "selectNext(), currentX = " + currentX + ", mSelectedIndex = " + mSelectedIndex
        )
        if (mSuggestions == null) return
        if (mCandidatePopupWindow != null && mCandidatePopupWindow!!.isShowing()) {
            mPopupCandidateView!!.selectNext()
        } else {
            if (mSelectedIndex < mCount - 1) {
                mSelectedIndex++
                if (mWordX[mSelectedIndex] + mWordWidth[mSelectedIndex] > currentX + getWidth()) scrollNext()
                //Jeremy '12,7,6 if the selected index is not in current visible area, set the selected index to the fist item visible
                val rightEdge = currentX + getWidth()
                if (mWordX[mSelectedIndex] < currentX ||
                    mWordX[mSelectedIndex] + mWordWidth[mSelectedIndex] > rightEdge
                ) {
                    for (i in 0..<mCount - 1) if (mWordX[i] >= currentX) {
                        mSelectedIndex = i
                        break
                    }
                }
            }
            invalidate()
        }
    }

    open fun selectPrev() {
        if (DEBUG) Log.i(
            TAG,
            "selectPrev(), currentX = " + currentX + ", mSelectedIndex = " + mSelectedIndex
        )
        if (mSuggestions == null) return
        if (mCandidatePopupWindow != null && mCandidatePopupWindow!!.isShowing()) {
            mPopupCandidateView!!.selectPrev()
        } else {
            if (mSelectedIndex > 0) {
                mSelectedIndex--
                if (mWordX[mSelectedIndex] < currentX) scrollPrev()
            }
            //Jeremy '12,7,6 if the selected index is not in current visible area, set the selected index to the last item visible
            val rightEdge = currentX + getWidth()
            if (mSelectedIndex == -1 || mWordX[mSelectedIndex] < currentX || mWordX[mSelectedIndex] + mWordWidth[mSelectedIndex] > rightEdge) {
                var i = mCount - 2
                while (i >= 0) { //< mCount - 1) {  Jeremy '25/12/10 fixed for infinite loop risk
                    if (mWordX[i] + mWordWidth[i] <= rightEdge) {
                        mSelectedIndex = i
                        break
                    }
                    i--
                }
            }
            invalidate()
        }
    }

    //Jeremy '11,8,28
    open fun selectNextRow() {
        if (mSuggestions == null) return
        if (mCandidatePopupWindow != null && mCandidatePopupWindow!!.isShowing()) mPopupCandidateView!!.selectNextRow()
        else if (mScreenWidth < mTotalWidth) showCandidatePopup()
    }

    open fun selectPrevRow() {
        if (mSuggestions == null) return
        if (mCandidatePopupWindow != null && mCandidatePopupWindow!!.isShowing()) mPopupCandidateView!!.selectPrevRow()
    }

    fun takeSuggstionAtIndex(index: Int): Boolean {
        if (DEBUG) {
            Log.i(TAG, "takeSuggestion():mSelectedIndex:" + mSelectedIndex)
        }


        if (mSuggestions != null && index >= 0 && index < mSuggestions!!.size) {
            mService!!.pickCandidateManually(index)
            return true // Selection picked
        } else return false
    }

    fun showDictationStatus(state: DictationState?, text: String?) {
        mDictationState = if (state == null) DictationState.IDLE else state
        mDictationText = if (text == null) "" else text
        if (mDictationState == DictationState.IDLE || mDictationState == DictationState.CANCELLED) {
            clearDictationStatus()
            return
        }
        if (mSuggestions != null) {
            mSuggestions!!.clear()
        }
        mCount = 0
        mTargetScrollX = 0
        prepareLayout()
        mHandler.updateUI(0)
    }

    fun clearDictationStatus() {
        mDictationState = DictationState.IDLE
        mDictationText = ""
        mHandler.updateUI(0)
    }

    val isShowingDictationStatus: Boolean
        get() = mDictationState != null && mDictationState != DictationState.IDLE && mDictationState != DictationState.CANCELLED

    private fun drawDictationStatus(canvas: Canvas?) {
        updateFontSize()
        if (mBgPadding == null) {
            mBgPadding = Rect(0, 0, 0, 0)
            if (getBackground() != null) {
                getBackground().getPadding(mBgPadding!!)
            }
        }

        val displayText: String =
            dictationDisplayText(getResources(), mDictationState, mDictationText)
        if (displayText.length == 0) {
            mTotalWidth = 0
            return
        }

        val bgPadding = mBgPadding
        val base = mCandidatePaint.measureText("。")
        var textWidth = mCandidatePaint.measureText(displayText)
        if (textWidth < base) {
            textWidth = base
        }
        val wordWidth: Int = textWidth.toInt() + X_GAP * 2
        mWordX[0] = 0
        mWordWidth[0] = wordWidth
        mTotalWidth = wordWidth
        if (canvas == null) {
            return
        }

        if (mTransparentCandidateView) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            val backgroundPaint = Paint()
            backgroundPaint.setColor(
                ContextCompat.getColor(
                    mContext,
                    R.color.third_background_light
                )
            )
            backgroundPaint.setAlpha(33)
            backgroundPaint.setStyle(Paint.Style.FILL)

            canvas.drawRect(
                0.5f,
                bgPadding!!.top.toFloat(),
                mScreenWidth.toFloat(),
                mHeight.toFloat(),
                backgroundPaint
            )
        }

        mCandidatePaint.setColor(mColorNormalText)
        val textBaseLine =
            (((mHeight - mCandidatePaint.getTextSize()) / 2) - mCandidatePaint.ascent()).toInt()
        canvas.drawText(
            displayText,
            (mWordX[0] + X_GAP).toFloat(),
            textBaseLine.toFloat(),
            mCandidatePaint
        )

        mCandidatePaint.setColor(mColorSpacer)
        canvas.drawLine(
            mWordX[0] + mWordWidth[0] + 0.5f,
            bgPadding!!.top + (mVerticalPadding.toFloat() / 2),
            mWordX[0] + mWordWidth[0] + 0.5f,
            mHeight - (mVerticalPadding.toFloat() / 2),
            mCandidatePaint
        )
        mCandidatePaint.setFakeBoldText(false)
    }

    @JvmOverloads
    fun takeSelectedSuggestion(vibrateSound: Boolean = false): Boolean {
        if (DEBUG) {
            Log.i(TAG, "takeSelectedSuggestion():mSelectedIndex:" + mSelectedIndex)
        }
        //Jeremy '11,9,1 do vibrate and sound on suggestion picked from candidateview
        if (vibrateSound) mService!!.doVibrateSound(0)
        hideComposing() //Jeremy '12,5,6
        if (mCandidatePopupWindow != null && mCandidatePopupWindow!!.isShowing()) {
            hideCandidatePopup()
            return takeSuggstionAtIndex(mPopupCandidateView!!.mSelectedIndex)
        } else return takeSuggstionAtIndex(mSelectedIndex)
    }

    /**
     * For flick through from keyboard, call this method with the x coordinate of the flick
     * gesture.
     */
    /*public void takeSuggestionAt(float x) {

        mTouchX = (int) x;
        // To detect candidate
        onDraw(null);
        takeSelectedSuggestion();
        invalidate();
    }*/
    private fun removeHighlight() {
        mTouchX = OUT_OF_BOUNDS
        invalidate()
    }

    public override fun onDetachedFromWindow() {
        if (DEBUG) Log.i(TAG, "onDetachedFromWindow() ")
        super.onDetachedFromWindow()
        hideComposing()
        hideCandidatePopup()
    }

    override fun onClick(v: View?) {
        if (mService != null) {
            mService!!.doVibrateSound(0)
        }
        dismissComposingFromCandidate()
    }


    companion object {
        private const val DEBUG = false
        private const val TAG = "CandidateView"

        const val OUT_OF_BOUNDS: Int = -1

        const val MAX_SUGGESTIONS = 500
        private const val SCROLL_PIXELS = 20

        private var mComposingTextPopup: PopupWindow? = null

        @JvmField
        var X_GAP: Int = 12

        private val EMPTY_LIST: MutableList<Mapping?> = LinkedList<Mapping?>()


        private fun withAlpha(color: Int, alpha: Int): Int {
            return (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)
        }

        @JvmStatic
        fun shouldShowLimeToast(hasWindowToken: Boolean, text: CharSequence?): Boolean {
            return hasWindowToken && text != null && text.length > 0
        }

        @JvmStatic
        fun dictationDisplayText(
            resources: Resources?,
            state: DictationState?,
            text: String?
        ): String {
            if (state == null) {
                return ""
            }
            when (state) {
                DictationState.LISTENING -> return if (resources != null) resources.getString(R.string.dictation_status_listening) else ""
                DictationState.PARTIAL -> return if (text != null && text.length > 0)
                    text
                else
                    (if (resources != null) resources.getString(R.string.dictation_status_partial) else "")

                DictationState.FINALIZING -> return if (text != null && text.length > 0)
                    text
                else
                    (if (resources != null) resources.getString(R.string.dictation_status_finalizing) else "")

                DictationState.ERROR -> return if (resources != null) resources.getString(R.string.dictation_status_error) else ""
                DictationState.CANCELLED, DictationState.IDLE -> return ""
            }
        }

        @JvmStatic
        fun popupBaseX(rowLeft: Int, dismissWidth: Int): Int {
            return rowLeft + dismissWidth
        }

        @JvmStatic
        fun popupContentHeight(popHeight: Int): Int {
            return popHeight
        }

        @JvmStatic
        fun popupHeight(
            availableSpace: Int,
            measuredContentHeight: Int,
            keyboardViewHidden: Boolean
        ): Int {
            if (keyboardViewHidden && measuredContentHeight < availableSpace) {
                return measuredContentHeight
            }
            return availableSpace
        }

        @JvmStatic
        fun visibleKeyboardPopupHeight(
            availableSpaceBelowCandidateBar: Int,
            candidateViewHeight: Int
        ): Int {
            return availableSpaceBelowCandidateBar + candidateViewHeight
        }

        @JvmStatic
        fun popupYOffset(
            candidateViewHeight: Int,
            popHeight: Int,
            keyboardViewHidden: Boolean
        ): Int {
            return 0
        }

        @JvmStatic
        fun visibleKeyboardPopupY(candidateTopOnScreen: Int, candidateViewHeight: Int): Int {
            return candidateTopOnScreen + candidateViewHeight
        }

        @JvmStatic
        fun shouldShowComposingPopup(candidateExpanded: Boolean, viewShown: Boolean): Boolean {
            return viewShown && !candidateExpanded
        }

        @JvmStatic
        fun liveCandidateTextSize(configuredTextSize: Float): Float {
            return configuredTextSize * 0.9f
        }

        @JvmStatic
        fun isExpandEdgeTap(
            touchX: Int,
            viewWidth: Int,
            expandButtonWidth: Int,
            totalWidth: Int
        ): Boolean {
            return false
        }

        @JvmStatic
        fun popupFrameContentLayoutParams(popHeight: Int): FrameLayout.LayoutParams {
            return FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                popupContentHeight(popHeight)
            )
        }
    }
}
