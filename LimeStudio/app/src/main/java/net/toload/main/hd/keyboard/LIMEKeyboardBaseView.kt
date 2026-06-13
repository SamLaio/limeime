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
/*
 * Jeremy '11,8,8
 * Derive from gingerbread Latin IME LatinKeyboardBaseView, 
 * modified to compatible with pre 2.2 devices, and disable
 * fling selection of popup minikeybaord on large screen.
 * 
 */
package net.toload.main.hd.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.util.StateSet
import android.util.TypedValue
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.PopupWindow
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedList
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import net.toload.main.hd.data.Keyboard
import net.toload.main.hd.global.LIME
import net.toload.main.hd.keyboard.LIMEBaseKeyboard.Key
import net.toload.main.hd.keyboard.PointerTracker.UIProxy
import net.toload.main.hd.R

@SuppressLint("UseSparseArrays")
open class LIMEKeyboardBaseView(context: Context, attrs: AttributeSet?, defStyle: Int) :
    View(context, attrs, defStyle), UIProxy {
    interface OnKeyboardActionListener {
        /**
         * Called when the user presses a key. This is sent before the
         * [.onKey] is called. For keys that repeat, this is only
         * called once.
         * 
         * @param primaryCode the unicode of the key being pressed. If the touch is
         * not on a valid key, the value will be zero.
         */
        fun onPress(primaryCode: Int)

        /**
         * Called when the user releases a key. This is sent after the
         * [.onKey] is called. For keys that repeat, this is only
         * called once.
         * 
         * @param primaryCode the code of the key that was released
         */
        fun onRelease(primaryCode: Int)

        /**
         * Send a key press to the listener.
         * 
         * @param primaryCode this is the key that was pressed
         * @param keyCodes    the codes for all the possible alternative keys with
         * the primary code being the first. If the primary key
         * code is a single character such as an alphabet or
         * number or symbol, the alternatives will include other
         * characters that may be on the same key or adjacent
         * keys. These codes are useful to correct for
         * accidental presses of a key adjacent to the intended
         * key.
         * @param x           x-coordinate pixel of touched event. If onKey is not called by onTouchEvent,
         * the value should be NOT_A_TOUCH_COORDINATE.
         * @param y           y-coordinate pixel of touched event. If onKey is not called by onTouchEvent,
         * the value should be NOT_A_TOUCH_COORDINATE.
         */
        fun onKey(primaryCode: Int, keyCodes: IntArray?, x: Int, y: Int)

        /**
         * Sends a sequence of characters to the listener.
         * 
         * @param text the sequence of characters to be displayed.
         */
        fun onText(text: CharSequence?)

        /**
         * Called when user released a finger outside any key.
         */
        fun onCancel()

        /**
         * Called when the user slides horizontally on the space key.
         * 
         * @param steps signed caret steps; negative moves left, positive moves right
         */
        fun moveCaretBy(steps: Int)

        /**
         * Called when the user quickly moves the finger from right to
         * left.
         */
        fun swipeLeft()

        /**
         * Called when the user quickly moves the finger from left to
         * right.
         */
        fun swipeRight()

        /**
         * Called when the user quickly moves the finger from up to down.
         */
        fun swipeDown()

        /**
         * Called when the user quickly moves the finger from down to up.
         */
        fun swipeUp()
    }

    //themed context
    lateinit var mContext: Context

    // Timing constants
    private val mKeyRepeatInterval: Int

    // XML attribute
    private var mKeyTextSize = 0
    private var mKeyTextColorNormal = 0
    private var mKeyTextColorPressed = 0 //Jeremy '15,5,13
    private var mFunctionKeyTextColorNormal = 0
    private var mFunctionKeyTextColorPressed = 0 //Jeremy '15,5,13
    private var mKeySubLabelTextColorNormal = 0 //Jeremy '12,4,29
    private var mKeySubLabelTextColorPressed = 0 //Jeremy '15,5,13
    private var mKeyTextStyle: Typeface? = Typeface.DEFAULT
    private var mLabelTextSize = 0
    private var mSmallLabelTextSize = 0
    private var mSubLabelTextSize = 0
    var symbolColorScheme: Int = 0
        private set
    private var mShadowColor = 0
    private var mShadowRadius = 0f
    private var mKeyBackground: Drawable? = null
    private var mBaseKeyBackground: Drawable? = null
    private var mBackgroundDimAmount = 0f
    private var mKeyHysteresisDistance = 0f
    private var mVerticalCorrection = 0f
    private var mPreviewOffset = 0
    private var mPreviewHeight = 0
    private var mPopupLayout = 0
    private var mSpacePreviewTopPadding = 0
    private var mPreviewTopPadding = 0

    private var mtHardwareAcceleratedDrawingEnabled = false


    // Main keyboard
    private var mKeyboard: LIMEBaseKeyboard? = null
    private var mKeys: Array<LIMEBaseKeyboard.Key> = emptyArray()

    // TODO this attribute should be gotten from Keyboard.
    private var mKeyboardVerticalGap = 0

    // Key preview popup
    private var mPreviewText: TextView? = null
    private lateinit var mPreviewPopup: PopupWindow
    private var mPreviewTextSizeLarge = 0
    private var mOffsetInWindow: IntArray? = null
    private var mOldPreviewKeyIndex: Int = NOT_A_KEY
    /**
     * Returns the enabled state of the key feedback popup.
     * 
     * @return whether or not the key feedback popup is enabled
     * @see .setPreviewEnabled
     */
    /**
     * Enables or disables the key feedback popup. This is a popup that shows a magnified
     * version of the depressed key. By default the preview is enabled.
     * 
     * @param previewEnabled whether or not to enable the key feedback popup
     * @see .isPreviewEnabled
     */
    var isPreviewEnabled: Boolean = true
    private var mPopupPreviewOffsetX = 0
    private var mPopupPreviewOffsetY = 0
    private var mWindowY = 0
    private var mPopupPreviewDisplayedY = 0
    private val mDelayBeforePreview: Int
    private val mDelayAfterPreview: Int

    // Popup mini keyboard
    private lateinit var mMiniKeyboardPopup: PopupWindow
    private var mMiniKeyboard: LIMEKeyboardBaseView? = null
    private var mMiniKeyboardParent: View? = null
    private val mMiniKeyboardCache = WeakHashMap<LIMEBaseKeyboard.Key?, View?>()
    private var mMiniKeyboardOriginX = 0
    private var mMiniKeyboardOriginY = 0
    private var mMiniKeyboardPopupTime: Long = 0
    private var mWindowOffset: IntArray? = null
    private val mMiniKeyboardSlideAllowance: Float
    private var mMiniKeyboardTrackerId = 0

    //key preview animation
    private var mKeyPreviewFadeInAnimator: Animation? = null
    private var mKeyPreviewFadeOutAnimator: Animation? = null

    /**
     * Listener for [OnKeyboardActionListener].
     */
    private var mKeyboardActionListener: OnKeyboardActionListener? = null

    private val mPointerTrackers = ArrayList<PointerTracker>()

    // TODO: Let the PointerTracker class manage this pointer queue
    private val mPointerQueue = PointerQueue()

    private val mHasDistinctMultitouch: Boolean
    private var mOldPointerCount = 1

    private var mKeyDetector: KeyDetector = ProximityKeyDetector()

    // Swipe gesture detector
    private var mGestureDetector: GestureDetector?
    private val mSwipeTracker = SwipeTracker()
    private val mSwipeThreshold: Int
    private val mDisambiguateSwipe: Boolean

    // Drawing
    /**
     * Whether the keyboard bitmap needs to be redrawn before it's blitted. *
     */
    private var mDrawPending = false

    /**
     * The dirty region in the keyboard bitmap
     */
    private val mDirtyRect = Rect()

    /**
     * Front/back keyboard bitmaps for faster updates. Touch events only collect
     * dirty regions; rendering happens on the next frame and swaps buffers.
     */
    private var mBuffer: Bitmap? = null
    private var mBackBuffer: Bitmap? = null

    /**
     * Notes if the keyboard just changed, so that we could possibly reallocate the mBuffer.
     */
    private var mKeyboardChanged = false
    private var mInvalidatedKey: LIMEBaseKeyboard.Key? = null

    /**
     * The canvas for the above mutable keyboard bitmap
     */
    private var mCanvas: Canvas? = null
    private var mBackCanvas: Canvas? = null
    private val mPaint: Paint
    private val mPadding: Rect
    private val mClipRegion = Rect(0, 0, 0, 0)

    // This map caches key label text height in pixel as value and key label text size as map key.
    private val mTextHeightCache = HashMap<Int?, Int?>()
    private val mTextWidthCache = HashMap<Int?, Int?>()

    private var mPopupHint: Drawable? = null //Jeremy /11,8,11

    private val isLargeScreen: Boolean // Jeremy //11,8,8 used for disable fling selection on minipopup keyboard for larger screen

    private val mHandler = UIHandler(this)


    class UIHandler(keyboardBaseView: LIMEKeyboardBaseView?) :
        Handler(Looper.getMainLooper()) {
        private var mInKeyRepeat = false

        private val mLIMEKeyboardBaseViewWeakReference: WeakReference<LIMEKeyboardBaseView?>

        init {
            mLIMEKeyboardBaseViewWeakReference =
                WeakReference<LIMEKeyboardBaseView?>(keyboardBaseView)
        }

        override fun handleMessage(msg: Message) {
            val mLIMEKeyboardBaseView = mLIMEKeyboardBaseViewWeakReference.get()
            if (mLIMEKeyboardBaseView == null) return
            when (msg.what) {
                MSG_POPUP_PREVIEW -> {
                    if (DEBUG) Log.i(TAG, "handleMessage()  MSG_POPUP_PREVIEW")
                    mLIMEKeyboardBaseView.showKey(msg.arg1, (msg.obj as PointerTracker?)!!)
                }

                MSG_DISMISS_PREVIEW -> {
                    if (DEBUG) Log.i(TAG, "handleMessage()  MSG_DISMISS_PREVIEW")
                    if (mLIMEKeyboardBaseView.mPreviewText != null) {
                        mLIMEKeyboardBaseView.mPreviewText!!.setVisibility(INVISIBLE)
                    }
                    if (mLIMEKeyboardBaseView.mPreviewPopup != null && mLIMEKeyboardBaseView.mPreviewPopup.isShowing()) mLIMEKeyboardBaseView.mPreviewPopup.dismiss()
                }

                MSG_REPEAT_KEY -> {
                    if (DEBUG) Log.i(TAG, "handleMessage()  MSG_REPEAT_KEY")
                    val tracker = msg.obj as PointerTracker
                    tracker.repeatKey(msg.arg1)
                    startKeyRepeatTimer(
                        mLIMEKeyboardBaseView.mKeyRepeatInterval.toLong(),
                        msg.arg1,
                        tracker
                    )
                }

                MSG_LONGPRESS_KEY -> {
                    if (DEBUG) Log.i(TAG, "handleMessage()  MSG_LONGPRESS_KEY")
                    val tracker = msg.obj as PointerTracker
                    mLIMEKeyboardBaseView.openPopupIfRequired(msg.arg1, tracker)
                }

                MSG_SHOW_PREVIEW -> {
                    if (DEBUG) Log.i(TAG, "handleMessage()  MSG_SHOW_PREVIEW")
                    val tracker = msg.obj as PointerTracker
                    if (!tracker.isSpaceKey(msg.arg1)) mLIMEKeyboardBaseView.startKeyPreviewFadeInAnimation()
                    if (mLIMEKeyboardBaseView.mPreviewText != null) {
                        mLIMEKeyboardBaseView.mPreviewText!!.setVisibility(VISIBLE)
                    }
                }
            }
        }

        fun showPreview(delay: Long, keyIndex: Int, tracker: PointerTracker?) {
            if (DEBUG) Log.i(TAG, "UIHandler.showPreview() delay = " + delay)
            removeMessages(MSG_DISMISS_PREVIEW)
            removeMessages(MSG_SHOW_PREVIEW)
            sendMessageDelayed(obtainMessage(MSG_SHOW_PREVIEW, keyIndex, 0, tracker), delay)
        }

        fun popupPreview(delay: Long, keyIndex: Int, tracker: PointerTracker) {
            if (DEBUG) Log.i(
                TAG,
                "UIHandler.popupPreview() delay=" + delay + "; keyIndex = " + keyIndex
            )
            val mLIMEKeyboardBaseView = mLIMEKeyboardBaseViewWeakReference.get()
            if (mLIMEKeyboardBaseView == null) return
            removeMessages(MSG_POPUP_PREVIEW)
            removeMessages(MSG_SHOW_PREVIEW)
            removeMessages(MSG_DISMISS_PREVIEW)
            if (mLIMEKeyboardBaseView.mPreviewPopup!!.isShowing() && mLIMEKeyboardBaseView.mPreviewText!!.getVisibility() == VISIBLE) {
                // Show right away, if it's already visible and finger is moving around
                mLIMEKeyboardBaseView.showKey(keyIndex, tracker)
            } else {
                sendMessageDelayed(obtainMessage(MSG_POPUP_PREVIEW, keyIndex, 0, tracker), delay)
            }
        }

        fun cancelPopupPreview() {
            if (DEBUG) Log.i(TAG, "UIHandler.cancelPopupPreview()")
            removeMessages(MSG_POPUP_PREVIEW)
        }

        fun dismissPreview(delay: Long) {
            if (DEBUG) Log.i(TAG, "UIHandler.dismissPreview() delay=" + delay)
            removeMessages(MSG_POPUP_PREVIEW)
            removeMessages(MSG_SHOW_PREVIEW)
            removeMessages(MSG_DISMISS_PREVIEW)
            val mLIMEKeyboardBaseView = mLIMEKeyboardBaseViewWeakReference.get()
            if (mLIMEKeyboardBaseView != null) mLIMEKeyboardBaseView.startKeyPreviewFadeOutAnimation()
            sendMessageDelayed(obtainMessage(MSG_DISMISS_PREVIEW), delay)
        }

        fun dismissPreviewNow() {
            if (DEBUG) Log.i(TAG, "UIHandler.dismissPreviewNow()")
            removeMessages(MSG_POPUP_PREVIEW)
            removeMessages(MSG_SHOW_PREVIEW)
            removeMessages(MSG_DISMISS_PREVIEW)
            val mLIMEKeyboardBaseView = mLIMEKeyboardBaseViewWeakReference.get()
            if (mLIMEKeyboardBaseView == null) return
            if (mLIMEKeyboardBaseView.mPreviewText != null) {
                mLIMEKeyboardBaseView.mPreviewText!!.clearAnimation()
                mLIMEKeyboardBaseView.mPreviewText!!.setVisibility(INVISIBLE)
            }
            if (mLIMEKeyboardBaseView.mPreviewPopup != null
                && mLIMEKeyboardBaseView.mPreviewPopup.isShowing()
            ) {
                mLIMEKeyboardBaseView.mPreviewPopup.dismiss()
            }
        }

        fun cancelDismissPreview() {
            if (DEBUG) Log.i(TAG, "UIHandler.cancelDismissPreview()")
            removeMessages(MSG_DISMISS_PREVIEW)
        }

        fun startKeyRepeatTimer(delay: Long, keyIndex: Int, tracker: PointerTracker?) {
            if (DEBUG) Log.i(
                TAG,
                "UIHandler.startKeyRepeatTimer() delay=" + delay + "keyIndex= " + keyIndex
            )
            mInKeyRepeat = true
            sendMessageDelayed(obtainMessage(MSG_REPEAT_KEY, keyIndex, 0, tracker), delay)
        }

        fun cancelKeyRepeatTimer() {
            if (DEBUG) Log.i(TAG, "UIHandler.cancelKeyRepeatTimer()")
            mInKeyRepeat = false
            removeMessages(MSG_REPEAT_KEY)
        }

        val isInKeyRepeat: Boolean
            get() {
                if (DEBUG) Log.i(
                    TAG,
                    "UIHandler.isInKeyRepeat(): " + mInKeyRepeat
                )
                return mInKeyRepeat
            }

        fun startLongPressTimer(delay: Long, keyIndex: Int, tracker: PointerTracker?) {
            if (DEBUG) Log.i(
                TAG,
                "UIHandler.startLongPressTimer() delay=" + delay + "keyIndex= " + keyIndex
            )
            removeMessages(MSG_LONGPRESS_KEY)
            sendMessageDelayed(obtainMessage(MSG_LONGPRESS_KEY, keyIndex, 0, tracker), delay)
        }

        fun cancelLongPressTimer() {
            if (DEBUG) Log.i(TAG, "UIHandler.cancelLongPressTimer()")
            removeMessages(MSG_LONGPRESS_KEY)
        }

        fun cancelKeyTimers() {
            cancelKeyRepeatTimer()
            cancelLongPressTimer()
        }

        fun cancelAllMessages() {
            cancelKeyTimers()
            cancelPopupPreview()
            cancelDismissPreview()
        }

        companion object {
            private const val MSG_POPUP_PREVIEW = 1
            private const val MSG_DISMISS_PREVIEW = 2
            private const val MSG_REPEAT_KEY = 3
            private const val MSG_LONGPRESS_KEY = 4
            private const val MSG_SHOW_PREVIEW = 5
        }
    }

    internal class PointerQueue {
        private val mQueue = LinkedList<PointerTracker>()

        fun add(tracker: PointerTracker?) {
            mQueue.add(tracker!!)
        }

        fun lastIndexOf(tracker: PointerTracker?): Int {
            val queue = mQueue
            for (index in queue.indices.reversed()) {
                val t: PointerTracker? = queue.get(index)
                if (t == tracker) return index
            }
            return -1
        }

        fun releaseAllPointersOlderThan(tracker: PointerTracker?, eventTime: Long) {
            val queue = mQueue
            var oldestPos = 0
            var t = queue.get(oldestPos)
            while (t != tracker) {
                if (t.isModifier()) {
                    oldestPos++
                } else {
                    t.onUpEvent(t.getLastX(), t.getLastY(), eventTime)
                    t.setAlreadyProcessed()
                    queue.removeAt(oldestPos)
                }
                t = queue.get(oldestPos)
            }
        }

        fun releaseAllPointersExcept(tracker: PointerTracker?, eventTime: Long) {
            for (t in mQueue) {
                if (t == tracker) continue
                t.onUpEvent(t.getLastX(), t.getLastY(), eventTime)
                t.setAlreadyProcessed()
            }
            mQueue.clear()
            if (tracker != null) mQueue.add(tracker)
        }

        fun remove(tracker: PointerTracker?) {
            mQueue.remove(tracker)
        }

        val isInSlidingKeyInput: Boolean
            get() {
                for (tracker in mQueue) {
                    if (tracker.isInSlidingKeyInput()) return true
                }
                return false
            }
    }

    private fun startKeyPreviewFadeInAnimation() {
        if (mKeyPreviewFadeOutAnimator != null && mPreviewText != null) {
            mKeyPreviewFadeInAnimator!!.reset()
            mPreviewText!!.clearAnimation()
            mPreviewText!!.startAnimation(mKeyPreviewFadeInAnimator)
        }
    }

    private fun startKeyPreviewFadeOutAnimation() {
        if (mKeyPreviewFadeOutAnimator != null && mPreviewText != null) {
            mKeyPreviewFadeOutAnimator!!.reset()
            mPreviewText!!.clearAnimation()
            mPreviewText!!.startAnimation(mKeyPreviewFadeOutAnimator)
        }
    }

    constructor(context: Context, attrs: AttributeSet?) : this(
        context,
        attrs,
        R.attr.LIMEKeyboardBaseView
    ) {
        mContext = context
    }

    fun setHardwareAcceleratedDrawingEnabled(enabled: Boolean) {
        if (!enabled) return
        setLayerType(LAYER_TYPE_HARDWARE, null)
        mtHardwareAcceleratedDrawingEnabled = true
    }


    init {
        mContext = context

        setLayerType(LAYER_TYPE_HARDWARE, null)

        val inflate =
            context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        var previewLayout = 0
        val keyTextSize = 0

        context.getTheme().obtainStyledAttributes(
            attrs, R.styleable.LIMEKeyboardBaseView, defStyle, R.style.LIMEBaseKeyboard
        ).use { a ->
            val n = a.getIndexCount()
            for (i in 0..<n) {
                val attr = a.getIndex(i)

                if (attr == R.styleable.LIMEKeyboardBaseView_keyBackground) mKeyBackground =
                    a.getDrawable(attr)
                else if (attr == R.styleable.LIMEKeyboardBaseView_keyHysteresisDistance) mKeyHysteresisDistance =
                    a.getDimensionPixelOffset(attr, 0).toFloat()
                else if (attr == R.styleable.LIMEKeyboardBaseView_verticalCorrection) mVerticalCorrection =
                    a.getDimensionPixelOffset(attr, 0).toFloat()
                else if (attr == R.styleable.LIMEKeyboardBaseView_keyPreviewLayout) previewLayout =
                    a.getResourceId(attr, 0)
                else if (attr == R.styleable.LIMEKeyboardBaseView_keyPreviewOffset) mPreviewOffset =
                    a.getDimensionPixelOffset(attr, 0)
                else if (attr == R.styleable.LIMEKeyboardBaseView_keyPreviewHeight) mPreviewHeight =
                    a.getDimensionPixelSize(attr, DEFAULT_PREVIEW_HEIGHT_PX)
                else if (attr == R.styleable.LIMEKeyboardBaseView_keyTextSize) mKeyTextSize =
                    a.getDimensionPixelSize(attr, DEFAULT_KEY_TEXT_SIZE_SP)
                else if (attr == R.styleable.LIMEKeyboardBaseView_functionKeyTextColorNormal) mFunctionKeyTextColorNormal =
                    a.getColor(attr, -0x1000000)
                else if (attr == R.styleable.LIMEKeyboardBaseView_functionKeyTextColorPressed) mFunctionKeyTextColorPressed =
                    a.getColor(attr, -0x1000000)
                else if (attr == R.styleable.LIMEKeyboardBaseView_keyTextColorNormal) mKeyTextColorNormal =
                    a.getColor(attr, -0x1000000)
                else if (attr == R.styleable.LIMEKeyboardBaseView_keyTextColorPressed) mKeyTextColorPressed =
                    a.getColor(attr, -0x1000000)
                else if (attr == R.styleable.LIMEKeyboardBaseView_keySubLabelTextColorNormal) mKeySubLabelTextColorNormal =
                    a.getColor(attr, -0x1000000)
                else if (attr == R.styleable.LIMEKeyboardBaseView_keySubLabelTextColorPressed) mKeySubLabelTextColorPressed =
                    a.getColor(attr, -0x1000000)
                else if (attr == R.styleable.LIMEKeyboardBaseView_labelTextSize) mLabelTextSize =
                    a.getDimensionPixelSize(attr, DEFAULT_LABEL_TEXT_SIZE_SP)
                else if (attr == R.styleable.LIMEKeyboardBaseView_smallLabelTextSize) mSmallLabelTextSize =
                    a.getDimensionPixelSize(attr, DEFAULT_LABEL_TEXT_SIZE_SP)
                else if (attr == R.styleable.LIMEKeyboardBaseView_subLabelTextSize) mSubLabelTextSize =
                    a.getDimensionPixelSize(attr, DEFAULT_LABEL_TEXT_SIZE_SP)
                else if (attr == R.styleable.LIMEKeyboardBaseView_popupLayout) mPopupLayout =
                    a.getResourceId(attr, 0)
                else if (attr == R.styleable.LIMEKeyboardBaseView_popupHint) mPopupHint =
                    a.getDrawable(attr)
                else if (attr == R.styleable.LIMEKeyboardBaseView_shadowColor) mShadowColor =
                    a.getColor(attr, 0)
                else if (attr == R.styleable.LIMEKeyboardBaseView_shadowRadius) mShadowRadius =
                    a.getFloat(attr, 0f)
                else if (attr == R.styleable.LIMEKeyboardBaseView_spacePreviewTopPadding) mSpacePreviewTopPadding =
                    a.getDimensionPixelSize(attr, DEFAULT_PREVIEW_TOP_PADDING_PX)
                else if (attr == R.styleable.LIMEKeyboardBaseView_previewTopPadding) mPreviewTopPadding =
                    a.getDimensionPixelSize(attr, 0)
                else if (attr == R.styleable.LIMEKeyboardBaseView_backgroundDimAmount) mBackgroundDimAmount =
                    a.getFloat(attr, 0.5f)
                else if (attr == R.styleable.LIMEKeyboardBaseView_symbolColorScheme) this.symbolColorScheme =
                    a.getInt(attr, 0)
                else if (attr == R.styleable.LIMEKeyboardBaseView_keyTextStyle) {
                    val textStyle = a.getInt(attr, 0)
                    when (textStyle) {
                        0 -> mKeyTextStyle = Typeface.DEFAULT
                        1 -> mKeyTextStyle = Typeface.DEFAULT_BOLD
                        else -> mKeyTextStyle = Typeface.defaultFromStyle(textStyle)
                    }
                }
            }
        }
        val res = getResources()


        isLargeScreen = true //large || xlarge;  //Force turn off fling selection now.


        mPreviewPopup = PopupWindow(context)
        if (previewLayout != 0) {
            mPreviewText = inflate.inflate(previewLayout, null) as TextView?
            if (mtHardwareAcceleratedDrawingEnabled) mPreviewText!!.setLayerType(
                LAYER_TYPE_HARDWARE,
                null
            )
            mKeyPreviewFadeInAnimator =
                AnimationUtils.loadAnimation(mContext, R.anim.key_preview_fadein)
            mKeyPreviewFadeOutAnimator =
                AnimationUtils.loadAnimation(mContext, R.anim.key_preview_fadeout)

            mPreviewTextSizeLarge = res.getDimension(R.dimen.key_preview_text_size_large).toInt()
            mPreviewPopup.setContentView(mPreviewText)
            mPreviewPopup.setBackgroundDrawable(null)
        } else {
            this.isPreviewEnabled = false
        }
        mPreviewPopup.setTouchable(false)
        mDelayBeforePreview = res.getInteger(R.integer.config_delay_before_preview)
        mDelayAfterPreview = res.getInteger(R.integer.config_delay_after_preview)

        mMiniKeyboardParent = this
        mMiniKeyboardPopup = PopupWindow(context)
        mMiniKeyboardPopup.setBackgroundDrawable(null)
        mMiniKeyboardPopup.setAnimationStyle(R.style.MiniKeyboardAnimation)

        mPaint = Paint()
        mPaint.setAntiAlias(true)
        mPaint.setTextSize(keyTextSize.toFloat())
        mPaint.setTextAlign(Align.CENTER)
        mPaint.setAlpha(255)

        mPadding = Rect(0, 0, 0, 0)
        if (mKeyBackground != null) {
            mBaseKeyBackground = mKeyBackground
            mKeyBackground!!.getPadding(mPadding)
        }

        mSwipeThreshold = (SWIPE_THRESHOLD_BASE_DP * res.getDisplayMetrics().density).toInt()
        // TODO: Refer frameworks/base/core/res/res/values/config.xml
        mDisambiguateSwipe = res.getBoolean(R.bool.config_swipeDisambiguation)
        mMiniKeyboardSlideAllowance = res.getDimension(R.dimen.mini_keyboard_slide_allowance)

        val listener: SimpleOnGestureListener =
            object : SimpleOnGestureListener() {
                override fun onFling(
                    me1: MotionEvent?, me2: MotionEvent, velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (me1 == null) return false
                    val absX = abs(velocityX)
                    val absY = abs(velocityY)
                    val deltaX = me2.getX() - me1.getX()
                    val deltaY = me2.getY() - me1.getY()
                    val travelX = getWidth() / 2 // Half the keyboard width
                    val travelY = getHeight() / 2 // Half the keyboard height
                    mSwipeTracker.computeCurrentVelocity(SWIPE_VELOCITY_UNITS_PER_SECOND)
                    val endingVelocityX = mSwipeTracker.xVelocity
                    val endingVelocityY = mSwipeTracker.yVelocity
                    if (velocityX > mSwipeThreshold && absY < absX && deltaX > travelX) {
                        if (mDisambiguateSwipe && endingVelocityX >= velocityX / 4) {
                            swipeRight()
                            return true
                        }
                    } else if (velocityX < -mSwipeThreshold && absY < absX && deltaX < -travelX) {
                        if (mDisambiguateSwipe && endingVelocityX <= velocityX / 4) {
                            swipeLeft()
                            return true
                        }
                    } else if (velocityY < -mSwipeThreshold && absX < absY && deltaY < -travelY) {
                        if (mDisambiguateSwipe && endingVelocityY <= velocityY / 4) {
                            swipeUp()
                            return true
                        }
                    } else if (velocityY > mSwipeThreshold && absX < absY / 2 && deltaY > travelY) {
                        if (mDisambiguateSwipe && endingVelocityY >= velocityY / 4) {
                            swipeDown()
                            return true
                        }
                    }
                    return false
                }
            }

        mGestureDetector = GestureDetector(getContext(), listener)

        mGestureDetector!!.setIsLongpressEnabled(false)

        mHasDistinctMultitouch = context.getPackageManager()
            .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT)
        mKeyRepeatInterval = res.getInteger(R.integer.config_key_repeat_interval)
    }

    fun applyFollowSystemAccentColor(accentColor: Int, darkTheme: Boolean) {
        if ((accentColor ushr 24) == 0) return

        mKeyTextColorPressed = accentColor
        mFunctionKeyTextColorPressed = accentColor
        mKeySubLabelTextColorPressed = accentColor
        if (mPreviewText != null) {
            mPreviewText!!.setTextColor(accentColor)
        }

        val keyBackground = StateListDrawable()
        keyBackground.addState(
            intArrayOf(android.R.attr.state_single, android.R.attr.state_pressed),
            createFollowSystemPressedKeyDrawable(accentColor, darkTheme)
        )
        keyBackground.addState(
            intArrayOf(android.R.attr.state_pressed),
            createFollowSystemPressedKeyDrawable(accentColor, darkTheme)
        )
        val baseBackground = if (mBaseKeyBackground != null) mBaseKeyBackground else mKeyBackground
        if (baseBackground != null) {
            keyBackground.addState(StateSet.WILD_CARD, baseBackground)
        }
        mKeyBackground = keyBackground
        mKeyBackground!!.getPadding(mPadding)

        mKeyboardChanged = true
        invalidateAllKeys()
    }

    private fun createFollowSystemPressedKeyDrawable(
        accentColor: Int,
        darkTheme: Boolean
    ): Drawable {
        val density = getResources().getDisplayMetrics().density
        val drawable = GradientDrawable()
        drawable.setShape(GradientDrawable.RECTANGLE)
        drawable.setCornerRadius(3f * density)
        drawable.setColor(withAlpha(accentColor, if (darkTheme) 0x66 else 0x44))
        drawable.setStroke(
            max(1, Math.round(2f * density)),
            if (darkTheme) -0xebebec else -0x1e1e1f
        )
        drawable.setSize(Math.round(30f * density), Math.round(46f * density))
        return drawable
    }

    protected var keyboardActionListener: OnKeyboardActionListener
        /**
         * Returns the [OnKeyboardActionListener] object.
         * 
         * @return the listener attached to this keyboard
         */
        get() = mKeyboardActionListener!!
        set(listener) {
            mKeyboardActionListener = listener
            for (tracker in mPointerTrackers) {
                tracker.setOnKeyboardActionListener(listener)
            }
        }

    fun setOnKeyboardActionListener(listener: OnKeyboardActionListener) {
        keyboardActionListener = listener
    }

    fun getOnKeyboardActionListener(): OnKeyboardActionListener? {
        return mKeyboardActionListener
    }

    var keyboard: LIMEBaseKeyboard?
        /**
         * Returns the current keyboard being displayed by this view.
         * 
         * @return the currently attached keyboard
         * // * @see #setKeyboard(Keyboard)
         */
        get() = mKeyboard
        /**
         * Attaches a keyboard to this view. The keyboard can be switched at any time and the
         * view will re-layout itself to accommodate the keyboard.
         * // * @see Keyboard
         * 
         * @param keyboard the keyboard to display in this view
         * @see .getKeyboard
         */
        set(keyboard) {
            if (mKeyboard != null) {
                dismissKeyPreview()
                dismissPopupKeyboard()
            }
            // Remove any pending messages, except dismissing preview
            mHandler.cancelKeyTimers()
            mHandler.cancelPopupPreview()
            mKeyboard = keyboard
            //LatinImeLogger.onSetKeyboard(keyboard);
            mKeys = mKeyDetector.setKeyboard(
                keyboard, -getPaddingLeft().toFloat(),
                -getPaddingTop() + mVerticalCorrection
            )
            mKeyboardVerticalGap = getResources().getDimension(R.dimen.key_bottom_gap).toInt()
            for (tracker in mPointerTrackers) {
                tracker.setKeyboard(mKeys, mKeyHysteresisDistance)
            }
            requestLayout()
            // Hint to reallocate the buffer if the size changed
            mOffsetInWindow = null //reset offset window.  keyboard changed.
            mKeyboardChanged = true
            invalidateAllKeys()
            computeProximityThreshold(keyboard)
            mMiniKeyboardCache.clear()
        }

    /**
     * Return whether the device has distinct multi-touch panel.
     * 
     * @return true if the device has distinct multi-touch panel.
     */
    override fun hasDistinctMultitouch(): Boolean {
        return mHasDistinctMultitouch
    }

    /**
     * Sets the state of the shift key of the keyboard, if any.
     * 
     * @param shifted whether or not to enable the state of the shift key
     * @return true if the shift key state changed, false if there was no change
     */
    fun setShifted(shifted: Boolean): Boolean {
        if (mKeyboard != null) {
            if (mKeyboard!!.setShifted(shifted)) {
                // The whole keyboard probably needs to be redrawn
                invalidateAllKeys()
                return true
            }
        }
        return false
    }

    val isShifted: Boolean
        /**
         * Returns the state of the shift key of the keyboard, if any.
         * 
         * @return true if the shift is in a pressed state, false otherwise. If there is
         * no shift key on the keyboard or there is no keyboard attached, it returns false.
         */
        get() = mKeyboard != null && mKeyboard!!.isShifted

    fun setPopupParent(v: View?) {
        mMiniKeyboardParent = v
    }

    fun setPopupOffset(x: Int, y: Int) {
        mPopupPreviewOffsetX = x
        mPopupPreviewOffsetY = y
        if (mPreviewPopup!!.isShowing()) {
            mPreviewPopup.dismiss()
        }
    }

    var isProximityCorrectionEnabled: Boolean
        /**
         * Returns true if proximity correction is enabled.
         */
        get() = mKeyDetector.isProximityCorrectionEnabled()
        /**
         * When enabled, calls to [OnKeyboardActionListener.onKey] will include key
         * codes for adjacent keys.  When disabled, only the primary key code will be
         * reported.
         * 
         * @param enabled whether or not the proximity correction is enabled
         */
        set(enabled) {
            mKeyDetector.setProximityCorrectionEnabled(enabled)
        }

    protected fun adjustCase(label: CharSequence?): CharSequence? {
        var label = label
        if (mKeyboard!!.isShifted && label != null && label.length <= 3 && Character.isLowerCase(
                label.get(0)
            )
        ) {
            label = label.toString().uppercase(Locale.getDefault())
        }
        return label
    }

    public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Round up a little
        if (mKeyboard == null) {
            setMeasuredDimension(
                getPaddingLeft() + getPaddingRight(), getPaddingTop() + getPaddingBottom()
            )
        } else {
            val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
            var width = mKeyboard!!.minWidth + getPaddingLeft() + getPaddingRight()
            if (parentWidth < width + DEFAULT_PREVIEW_TOP_PADDING_PX) {
                width = parentWidth
            }
            Log.i(
                TAG,
                "Width = " + width + "  height = " + mKeyboard!!.height + getPaddingTop() + getPaddingBottom() + "."
            )
            setMeasuredDimension(
                width, mKeyboard!!.height + getPaddingTop() + getPaddingBottom()
            )
        }
    }

    /**
     * Compute the average distance between adjacent keys (horizontally and vertically)
     * and square it to get the proximity threshold. We use a square here and in computing
     * the touch distance from a key's center to avoid taking a square root.
     * 
     */
    private fun computeProximityThreshold(keyboard: LIMEBaseKeyboard?) {
        if (keyboard == null) return
        val keys = mKeys
        if (keys == null) return
        val length = keys.size
        var dimensionSum = 0
        for (key in keys) {
            dimensionSum += min(key.width, key.height + mKeyboardVerticalGap) + key.gap
        }
        if (dimensionSum < 0 || length == 0) return
        mKeyDetector.setProximityThreshold((dimensionSum * 1.4f / length).toInt())
    }

    public override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Release the buffer, if any and it will be reallocated on the next draw
        mBuffer = null
        mBackBuffer = null
        mCanvas = null
        mBackCanvas = null
    }

    public override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mDrawPending || mBuffer == null || mKeyboardChanged) {
            onBufferDraw()
        }
        canvas.drawBitmap(mBuffer!!, 0f, 0f, null)
    }

    private fun onBufferDraw() {
        val width = max(1, getWidth())
        val height = max(1, getHeight())
        if (mBuffer == null || mBackBuffer == null || mBuffer!!.getWidth() != width || mBuffer!!.height != height || mBackBuffer!!.getWidth() != width || mBackBuffer!!.height != height) {
            mBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            mBackBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            mCanvas = Canvas(mBuffer!!)
            mBackCanvas = Canvas(mBackBuffer!!)
            mDirtyRect.set(0, 0, width, height)
            mInvalidatedKey = null
        }
        if (mKeyboardChanged) {
            mDirtyRect.set(0, 0, width, height)
            mInvalidatedKey = null
            mKeyboardChanged = false
        }
        if (mDirtyRect.isEmpty()) {
            mDrawPending = false
            return
        }

        val canvas = mBackCanvas
        canvas!!.drawBitmap(mBuffer!!, 0f, 0f, null)
        canvas.save()
        try {
            canvas.clipRect(mDirtyRect)

            //canvas.clipRect(mDirtyRect, Op.REPLACE);
            if (mKeyboard == null) return

            val paint = mPaint
            val keyBackground = mKeyBackground
            val clipRegion = mClipRegion
            val padding = mPadding
            val kbdPaddingLeft = getPaddingLeft()
            val kbdPaddingTop = getPaddingTop()
            val keys = mKeys
            val invalidKey = mInvalidatedKey


            var drawSingleKey = false
            if (invalidKey != null && canvas.getClipBounds(clipRegion)) {
                // TODO we should use Rect.inset and Rect.contains here.
                // Is clipRegion completely contained within the invalidated key?
                if (invalidKey.x + kbdPaddingLeft - 1 <= clipRegion.left && invalidKey.y + kbdPaddingTop - 1 <= clipRegion.top && invalidKey.x + invalidKey.width + kbdPaddingLeft + 1 >= clipRegion.right && invalidKey.y + invalidKey.height + kbdPaddingTop + 1 >= clipRegion.bottom) {
                    drawSingleKey = true
                }
            }
            canvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR)
            drawKeyboardBackground(canvas, width, height)
            //final int keyCount = keys.length;
            for (key in keys) {
                if (drawSingleKey && invalidKey !== key) {
                    continue
                }
                val drawableState = key.currentDrawableState
                keyBackground!!.setState(drawableState)


                // Switch the character to uppercase if shift is pressed
                var label = if (key.label == null) null else adjustCase(key.label).toString()

                val bounds = keyBackground.getBounds()
                if (key.width != bounds.right || key.height != bounds.bottom) {
                    keyBackground.setBounds(0, 0, key.width, key.height)
                }
                canvas.translate(
                    (key.x + kbdPaddingLeft).toFloat(),
                    (key.y + kbdPaddingTop).toFloat()
                )
                keyBackground.draw(canvas)

                var shouldDrawIcon = true
                if (label != null) {
                    // For characters, use large font. For labels like "Done", use small font.
                    val labelSize: Int

                    //                    if (DEBUG)
//                        Log.i(TAG, "onBufferDraw():" + label
//                                + " keySizeScale = " + mKeyboard.keySizeScale + " "
//                                + " labelSizeScale = " + key.labelSizeScale);
                    //Jeremy '11,8,11, Extended for sub-label display
                    //Jeremy '11,9,4 Scale label size
                    val keySizeScale = LIMEBaseKeyboard.keySizeScale
                    val labelSizeScale = key.labelSizeScale

                    //Jeremy '12,6,7 moved to LIMEbasekeyboard
                    /*if(key.height < mKeyboard.getKeyHeight())  //Jeremy '12,5,21 scaled the label size if the key height is smaller than default key height
                        labelSizeScale =  (float)(key.height) / (float)(mKeyboard.getKeyHeight());

                    if(key.width < mKeyboard.getKeyWidth())  //Jeremy '12,5,26 scaled the label size if the key width is smaller than default key width
                        labelSizeScale *=  (float)(key.width) / (float)(mKeyboard.getKeyWidth());*/
                    val hasSubLabel = label.contains("\n")
                    var hasSecondSubLabel = false
                    var subLabel = ""
                    var secondSubLabel = ""
                    if (hasSubLabel) {
                        val labelA = label.split("\n".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                        if (labelA.size > 0) label = labelA[1]
                        subLabel = labelA[0]

                        hasSecondSubLabel = subLabel.contains("\t")
                        if (hasSecondSubLabel) {
                            val subLabelA =
                                subLabel.split("\t".toRegex()).dropLastWhile { it.isEmpty() }
                                    .toTypedArray()
                            if (subLabelA.size > 0) subLabel = subLabelA[0]
                            secondSubLabel = subLabelA[1]
                        }
                    }
                    if (hasSubLabel) {
                        if (label.length > 1) { //Jeremy '12,6,6 shrink the font size for more characters on label
                            labelSize =
                                (mSmallLabelTextSize * keySizeScale * labelSizeScale * 0.8f).toInt()
                            paint.setTypeface(Typeface.DEFAULT_BOLD)
                        } else {
                            labelSize =
                                (mSmallLabelTextSize * keySizeScale * labelSizeScale).toInt()
                            paint.setTypeface(Typeface.DEFAULT_BOLD)
                        }
                    } else if (label.length > 1 && key.codes.size < 2) {
                        labelSize = (mLabelTextSize * keySizeScale * labelSizeScale).toInt()
                        paint.setTypeface(Typeface.DEFAULT_BOLD)
                    } else {
                        labelSize = (mKeyTextSize * keySizeScale * labelSizeScale).toInt()
                        paint.setTypeface(mKeyTextStyle)
                    }
                    paint.setTextSize(labelSize.toFloat())


                    var labelHeight = 0
                    var labelWidth = 0
                    val KEY_LABEL_HEIGHT_REFERENCE_CHAR = "W"
                    if (mTextHeightCache.get(labelSize) != null) {
                        val cachedHeight = mTextHeightCache.get(labelSize)
                        val cachedWidth = mTextWidthCache.get(labelSize)
                        if (cachedHeight != null && cachedWidth != null) {
                            labelHeight = cachedHeight
                            labelWidth = cachedWidth
                        }
                    } else {
                        val textBounds = Rect()
                        paint.getTextBounds(KEY_LABEL_HEIGHT_REFERENCE_CHAR, 0, 1, textBounds)
                        labelHeight = textBounds.height()
                        labelWidth = textBounds.width()
                        mTextHeightCache.put(labelSize, labelHeight)
                        mTextWidthCache.put(labelSize, labelWidth)
                    }

                    // Draw a drop shadow for the text
                    if (mShadowRadius > 0) paint.setShadowLayer(mShadowRadius, 0f, 0f, mShadowColor)
                    val centerX = (key.width + padding.left - padding.right) / 2
                    val centerY = (key.height + padding.top - padding.bottom) / 2
                    val keyColor = if (key.isFunctionalKey)
                        (if (key.pressed) mFunctionKeyTextColorPressed else mFunctionKeyTextColorNormal)
                    else
                        (if (key.pressed) mKeyTextColorPressed else mKeyTextColorNormal)
                    val subKeyColor = if (key.isFunctionalKey)
                        (if (key.pressed) mFunctionKeyTextColorPressed else mFunctionKeyTextColorNormal)
                    else
                        (if (key.pressed) mKeySubLabelTextColorPressed else mKeySubLabelTextColorNormal)

                    val KEY_LABEL_VERTICAL_ADJUSTMENT_FACTOR = 0.55f
                    var baseline = (centerY
                            + labelHeight * KEY_LABEL_VERTICAL_ADJUSTMENT_FACTOR)
                    if (hasSubLabel) {
                        val subLabelSize =
                            (mSubLabelTextSize * keySizeScale * labelSizeScale).toInt()
                        val subLabelHeight: Int
                        val subLabelWidth: Int
                        paint.setTypeface(Typeface.DEFAULT_BOLD)

                        paint.setTextSize(subLabelSize.toFloat())
                        if (mTextHeightCache.get(subLabelSize) != null) {
                            val cachedSubLabelHeight = mTextHeightCache.get(subLabelSize)
                            val cachedSubLabelWidth = mTextWidthCache.get(subLabelSize)
                            subLabelHeight =
                                if (cachedSubLabelHeight != null) cachedSubLabelHeight else 0
                            subLabelWidth =
                                if (cachedSubLabelWidth != null) cachedSubLabelWidth else 0
                        } else {
                            val textBounds = Rect()
                            paint.getTextBounds(KEY_LABEL_HEIGHT_REFERENCE_CHAR, 0, 1, textBounds)
                            subLabelHeight = textBounds.height()
                            subLabelWidth = textBounds.width()
                            mTextHeightCache.put(subLabelSize, subLabelHeight)
                            mTextWidthCache.put(subLabelSize, subLabelWidth)
                        }

                        //portrait keyboard
                        if (key.height > key.width || subLabel.length > 2 || hasSecondSubLabel) {
                            // Anchor sub-label from top and main label from bottom; split the
                            // leftover vertical space as top : gap : bottom = 1 : 1 : 1 so the
                            // inter-text gap scales with free space instead of being the residual
                            // of rigid H/3 and 2H/3 anchors (fixes label/sub-label crowding after
                            // labelSizeScale returned to 1.0 in 6.0.2).
                            val drawableH = (key.height - padding.top - padding.bottom).toFloat()
                            val stackPad = max(0f, (drawableH - subLabelHeight - labelHeight) / 3f)
                            baseline = (key.height - padding.bottom) - stackPad
                            val subBaseline = padding.top + stackPad + subLabelHeight
                            paint.setColor(subKeyColor)

                            if (hasSecondSubLabel) {
                                canvas.drawText(
                                    subLabel,
                                    centerX.toFloat() / 2,
                                    subBaseline,
                                    paint
                                )

                                paint.setColor(keyColor)
                                canvas.drawText(
                                    secondSubLabel,
                                    centerX.toFloat() / 2 * 3,
                                    subBaseline,
                                    paint
                                )
                            } else canvas.drawText(
                                subLabel,
                                centerX.toFloat(),
                                subBaseline,
                                paint
                            )

                            paint.setTextSize(labelSize.toFloat())
                            paint.setTypeface(mKeyTextStyle)
                            paint.setColor(keyColor)
                            canvas.drawText(label, centerX.toFloat(), baseline, paint)
                        } else {    //landscape keyboard
                            paint.setColor(subKeyColor)
                            //if (subLabel.length() > 2)  // draw sub keys as portrait keys in two rows.
                            //    paint.setTextSize(subLabelSize * 2 / 3);  //123 EN  in landscape is usually to wide.
                            /*if (hasSecondSubLabel) {
                                                        canvas.drawText(subLabel, centerX - subLabelWidth * 2, baseline, paint);
                                                        paint.setColor(keyColor);
                                                        canvas.drawText(secondSubLabel, centerX - subLabelWidth, baseline, paint);
                                                    } else*/
                            canvas.drawText(
                                subLabel,
                                (centerX - subLabelWidth).toFloat(),
                                baseline,
                                paint
                            )

                            paint.setTextSize(labelSize.toFloat())
                            paint.setTypeface(mKeyTextStyle)
                            paint.setColor(keyColor)
                            canvas.drawText(
                                label,
                                centerX + labelWidth.toFloat() / 2,
                                baseline,
                                paint
                            )
                        }
                    } else {
                        paint.setColor(keyColor)
                        canvas.drawText(label, centerX.toFloat(), baseline, paint)
                    }
                    // Turn off drop shadow
                    if (mShadowRadius > 0) paint.setShadowLayer(0f, 0f, 0f, 0)

                    // Usually don't draw icon if label is not null, but we draw icon for the number
                    // hint and popup hint.
                    shouldDrawIcon = shouldDrawLabelAndIcon(key)
                }
                if (shouldDrawIcon) {
                    var icon = key.icon
                    if (icon == null) icon = mPopupHint
                    else {
                        icon.setState(drawableState)
                    }


                    // Special handing for the upper-right number hint icons
                    val drawableWidth: Int
                    val drawableHeight: Int
                    val drawableX: Int
                    val drawableY: Int
                    if (shouldDrawIconFully(key)) {
                        drawableWidth = key.width
                        drawableHeight = key.height
                        drawableX = 0
                        drawableY = NUMBER_HINT_VERTICAL_ADJUSTMENT_PIXEL
                    } else {
                        drawableHeight = key.height // icon.getIntrinsicHeight();
                        drawableWidth =
                            icon!!.getIntrinsicWidth() * drawableHeight / icon.getIntrinsicHeight()
                        drawableX = (key.width + padding.left - padding.right - drawableWidth) / 2
                        drawableY = (key.height + padding.top - padding.bottom - drawableHeight) / 2
                    }
                    canvas.translate(drawableX.toFloat(), drawableY.toFloat())
                    icon!!.setBounds(0, 0, drawableWidth, drawableHeight)
                    icon.draw(canvas)
                    canvas.translate(-drawableX.toFloat(), -drawableY.toFloat())
                }
                canvas.translate(
                    (-key.x - kbdPaddingLeft).toFloat(),
                    (-key.y - kbdPaddingTop).toFloat()
                )
            }
            mInvalidatedKey = null
            // Overlay a dark rectangle to dim the keyboard
            if (mMiniKeyboard != null) {
                paint.setColor((mBackgroundDimAmount * 0xFF).toInt() shl 24)
                canvas.drawRect(0f, 0f, getWidth().toFloat(), getHeight().toFloat(), paint)
            }

            if (DEBUG) {
                //boolean mShowTouchPoints = true;
                for (tracker in mPointerTrackers) {
                    val startX = tracker.getStartX()
                    val startY = tracker.getStartY()
                    val lastX = tracker.getLastX()
                    val lastY = tracker.getLastY()
                    paint.setAlpha(128)
                    paint.setColor(-0x10000)
                    canvas.drawCircle(startX.toFloat(), startY.toFloat(), 3f, paint)
                    canvas.drawLine(
                        startX.toFloat(),
                        startY.toFloat(),
                        lastX.toFloat(),
                        lastY.toFloat(),
                        paint
                    )
                    paint.setColor(-0xffff01)
                    canvas.drawCircle(lastX.toFloat(), lastY.toFloat(), 3f, paint)
                    paint.setColor(-0xff0100)
                    canvas.drawCircle(
                        (startX + lastX).toFloat() / 2,
                        (startY + lastY).toFloat() / 2,
                        2f,
                        paint
                    )
                }
            }
        } finally {
            canvas.restore()
        }

        mDrawPending = false
        mDirtyRect.setEmpty()
        val drawnBuffer = mBackBuffer
        mBackBuffer = mBuffer
        mBuffer = drawnBuffer

        val drawnCanvas = mBackCanvas
        mBackCanvas = mCanvas
        mCanvas = drawnCanvas
    }

    private fun drawKeyboardBackground(canvas: Canvas, width: Int, height: Int) {
        val background = getBackground()
        if (background != null) {
            background.setBounds(0, 0, width, height)
            background.draw(canvas)
        }
    }

    // TODO: clean up this method.
    private fun dismissKeyPreview() {
        if (DEBUG) Log.i(TAG, "dismissKeyPreview() ")
        for (tracker in mPointerTrackers) tracker.updateKey(NOT_A_KEY)
        showPreview(NOT_A_KEY, null)
    }

    override fun showPreview(keyIndex: Int, tracker: PointerTracker?) {
        val oldKeyIndex = mOldPreviewKeyIndex
        mOldPreviewKeyIndex = keyIndex
        val previewPopup = mPreviewPopup

        if (DEBUG) Log.i(
            TAG,
            "showPreview() keyIndex =" + keyIndex + ", oldKeyIndex = " + oldKeyIndex
        )

        val hidePreviewOrShowSpaceKeyPreview =
            (tracker == null) || tracker.isSpaceKey(keyIndex) || tracker.isSpaceKey(oldKeyIndex)
        // If key changed and preview is on or the key is space (language switch is enabled)
        if (oldKeyIndex != keyIndex && this.isPreviewEnabled
            || (hidePreviewOrShowSpaceKeyPreview)
        ) {
            if (keyIndex == NOT_A_KEY) {
                mHandler.dismissPreviewNow()
            } else if (tracker != null) {
                mHandler.popupPreview(0, keyIndex, tracker)
            }
        }
    }

    private fun showKey(keyIndex: Int, tracker: PointerTracker) {
        if (DEBUG) Log.i(TAG, "showKey() keyIndex =" + keyIndex)
        val previewPopup = mPreviewPopup
        val key = tracker.getKey(keyIndex)
        if (key == null) return
        // Should not draw hint icon in key preview
        if (key.icon != null && !hasPopupKeyboard(key) || key.codes[0] == ' '.code) {
            mPreviewText!!.setCompoundDrawables(
                null,
                if (key.iconPreview != null) key.iconPreview else key.icon, null, null
            )
            mPreviewText!!.setText(null)
        } else if (key.label != null) {
            mPreviewText!!.setCompoundDrawables(null, null, null, null)
            mPreviewText!!.setText(adjustCase(tracker.getPreviewText(key)))
            val label = key.label
            if (label != null && label.length > 1 && key.codes.size < 2) {
                mPreviewText!!.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX, (mKeyTextSize
                            * key.labelSizeScale * LIMEBaseKeyboard.keySizeScale)
                ) //Jeremy '12,6,7 scale the preview key text size
                mPreviewText!!.setTypeface(Typeface.DEFAULT_BOLD)
            } else {
                mPreviewText!!.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX, (mPreviewTextSizeLarge
                            * key.labelSizeScale * LIMEBaseKeyboard.keySizeScale)
                ) //Jeremy '12,6,7 scale the preview key text size
                mPreviewText!!.setTypeface(mKeyTextStyle)
            }
        }
        mPreviewText!!.setPadding(
            mPreviewText!!.getPaddingLeft(),  //Jeremy '15,7,13
            (if (key.codes[0] == ' '.code) mSpacePreviewTopPadding else mPreviewTopPadding),
            mPreviewText!!.getPaddingRight(), 0
        )
        mPreviewText!!.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = max(
            mPreviewText!!.getMeasuredWidth(), (key.width
                    + mPreviewText!!.getPaddingLeft() + mPreviewText!!.getPaddingRight())
        )
        //Jeremy '15,7,13 minus key.height from popupHeight if it's space key for sliding IM switching preview
        val popupHeight =
            (mPreviewHeight * LIMEBaseKeyboard.keySizeScale - (if (key.codes[0] == ' '.code) key.height else 0)).toInt()

        val lp = mPreviewText!!.getLayoutParams()
        if (lp != null) {
            lp.width = popupWidth
            lp.height = popupHeight
        }

        var popupPreviewX = key.x - (popupWidth - key.width) / 2
        //Jeremy '15,7,13 add key.height to cover whole key if it's not space key
        var popupPreviewY =
            (key.y + (if (key.codes[0] == ' '.code) 0 else key.height) - popupHeight + mPreviewOffset)

        //mHandler.cancelDismissPreview();
        if (mOffsetInWindow == null) {
            mOffsetInWindow = IntArray(2)
            getLocationInWindow(mOffsetInWindow)
            mOffsetInWindow!![0] += mPopupPreviewOffsetX // Offset may be zero
            mOffsetInWindow!![1] += mPopupPreviewOffsetY // Offset may be zero
            val windowLocation = IntArray(2)
            getLocationOnScreen(windowLocation)
            mWindowY = windowLocation[1]
        }
        // Set the preview background state
        mPreviewText!!.getBackground().setState(
            if (key.popupResId != 0) PRESSED_STATE_SET else EMPTY_STATE_SET
        )
        popupPreviewX += mOffsetInWindow!![0]
        popupPreviewY += mOffsetInWindow!![1]

        // If the popup cannot be shown above the key, put it on the side
        if (popupPreviewY + mWindowY < 0) {
            // If the key you're pressing is on the left side of the keyboard, show the popup on
            // the right, offset by enough to see at least one key to the left/right.
            if (key.x + key.width <= getWidth() / 2) {
                popupPreviewX += (key.width * 2.5).toInt()
            } else {
                popupPreviewX -= (key.width * 2.5).toInt()
            }
            popupPreviewY += popupHeight
        }

        if (previewPopup!!.isShowing()) {
            previewPopup.update(popupPreviewX, popupPreviewY, popupWidth, popupHeight)
        } else {
            previewPopup.setWidth(popupWidth)
            previewPopup.setHeight(popupHeight)
            previewPopup.showAtLocation(
                mMiniKeyboardParent, Gravity.NO_GRAVITY,
                popupPreviewX, popupPreviewY
            )
        }
        // Record popup preview position to display mini-keyboard later at the same positon
        mPopupPreviewDisplayedY = popupPreviewY

        //Jeremy '16, 7, 30 Add delay before show preview to avoid ghost image when moving last location to current.
        mHandler.showPreview(mDelayBeforePreview.toLong(), keyIndex, tracker)
    }

    /**
     * Requests a redraw of the entire keyboard. Calling [.invalidate] is not sufficient
     * because the keyboard renders the keys to an off-screen buffer and an invalidate() only
     * draws the cached buffer.
     * 
     * @see .invalidateKey
     */
    fun invalidateAllKeys() {
        mDirtyRect.union(0, 0, getWidth(), getHeight())
        mInvalidatedKey = null
        scheduleBufferDraw()
    }

    /**
     * Invalidates a key so that it will be redrawn on the next repaint. Use this method if only
     * one key is changing it's content. Any changes that affect the position or size of the key
     * may not be honored.
     * // * @param key key in the attached [//Keyboard].
     * 
     * @see .invalidateAllKeys
     */
    override fun invalidateKey(key: LIMEBaseKeyboard.Key?) {
        if (key == null) return
        if (mDirtyRect.isEmpty() || mInvalidatedKey === key) {
            mInvalidatedKey = key
        } else {
            mInvalidatedKey = null
        }
        // TODO we should clean up this and record key's region to use in onBufferDraw.
        mDirtyRect.union(
            key.x + getPaddingLeft(), key.y + getPaddingTop(),
            key.x + key.width + getPaddingLeft(), key.y + key.height + getPaddingTop()
        )
        scheduleBufferDraw()
    }

    private fun scheduleBufferDraw() {
        if (!mDrawPending) {
            mDrawPending = true
            postInvalidateOnAnimation()
        }
    }

    private fun openPopupIfRequired(keyIndex: Int, tracker: PointerTracker) {
        // Check if we have a popup layout specified first.
        if (mPopupLayout == 0) {
            return
        }

        val popupKey = tracker.getKey(keyIndex)
        if (popupKey == null) return
        val result = onLongPress(popupKey)
        if (result) {
            dismissKeyPreview()
            mMiniKeyboardTrackerId = tracker.mPointerId
            // Mark this tracker "already processed" and remove it from the pointer queue
            tracker.setAlreadyProcessed()
            mPointerQueue.remove(tracker)
        }
    }

    private fun inflateMiniKeyboardContainer(popupKey: LIMEBaseKeyboard.Key): View {
        val popupKeyboardId = popupKey.popupResId
        //LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        val container = LayoutInflater.from(mContext).inflate(mPopupLayout, null)
        if (container == null) throw NullPointerException()

        val miniKeyboard =
            container.findViewById<LIMEKeyboardBaseView>(R.id.LIMEPopupKeyboard)
        miniKeyboard.keyboardActionListener =
            object : OnKeyboardActionListener {
                override fun onKey(primaryCode: Int, keyCodes: IntArray?, x: Int, y: Int) {
                    mKeyboardActionListener!!.onKey(primaryCode, keyCodes, x, y)
                    dismissPopupKeyboard()
                }

                override fun onText(text: CharSequence?) {
                    mKeyboardActionListener!!.onText(text)
                    dismissPopupKeyboard()
                }

                override fun onCancel() {
                    mKeyboardActionListener!!.onCancel()
                    dismissPopupKeyboard()
                }

                override fun moveCaretBy(steps: Int) {
                }

                override fun swipeLeft() {
                }

                override fun swipeRight() {
                }

                override fun swipeUp() {
                }

                override fun swipeDown() {
                }

                override fun onPress(primaryCode: Int) {
                    mKeyboardActionListener!!.onPress(primaryCode)
                }

                override fun onRelease(primaryCode: Int) {
                    mKeyboardActionListener!!.onRelease(primaryCode)
                }
            }
        // Override default ProximityKeyDetector.
        miniKeyboard.mKeyDetector = MiniKeyboardKeyDetector(mMiniKeyboardSlideAllowance)
        // Remove gesture detector on mini-keyboarda
        miniKeyboard.mGestureDetector = null

        val keyboard = getLimeBaseKeyboard(popupKey, popupKeyboardId)
        //mini keyboard in fling mode override with fling correction. Jeremy '12,5,27
        if (!isLargeScreen || keyboard.keys.size == 1) miniKeyboard.mVerticalCorrection =
            getResources().getDimension(R.dimen.mini_keyboard_fling_vertical_correction)
        miniKeyboard.keyboard = keyboard
        miniKeyboard.setPopupParent(this)

        container.measure(
            MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST)
        )

        return container
    }

    private fun getLimeBaseKeyboard(
        popupKey: LIMEBaseKeyboard.Key,
        popupKeyboardId: Int
    ): LIMEBaseKeyboard {
        val keyboard: LIMEBaseKeyboard
        val popupCharacters = popupKey.popupCharacters
        if (popupCharacters != null) {
            keyboard = LIMEBaseKeyboard(
                mContext, popupKeyboardId, popupCharacters,
                -1, getPaddingLeft() + getPaddingRight(),
                LIMEBaseKeyboard.keySizeScale
            )
        } else {
            keyboard = LIMEBaseKeyboard(
                mContext, popupKeyboardId,
                LIMEBaseKeyboard.keySizeScale, 0, 0
            ) //Jeremy '12,5,21 never show arrow keys in popup keyboard
        }
        return keyboard
    }

    /**
     * Called when a key is long pressed. By default this will open any popup keyboard associated
     * with this key through the attributes popupLayout and popupCharacters.
     * 
     * @param popupKey the key that was long pressed
     * @return true if the long press is handled, false otherwise. Subclasses should call the
     * method on the base class if the subclass doesn't wish to handle the call.
     */
    protected open fun onLongPress(popupKey: LIMEBaseKeyboard.Key): Boolean {
        // TODO if popupKey.popupCharacters has only one letter, send it as key without opening
        // mini keyboard.

        if (popupKey.popupResId == 0) return false

        var container = mMiniKeyboardCache.get(popupKey)
        if (container == null) {
            container = inflateMiniKeyboardContainer(popupKey)
            mMiniKeyboardCache.put(popupKey, container)
        }
        mMiniKeyboard = container.findViewById<LIMEKeyboardBaseView?>(R.id.LIMEPopupKeyboard)
        if (mWindowOffset == null) {
            mWindowOffset = IntArray(2)
            getLocationInWindow(mWindowOffset)
        }

        // Get width of a key in the mini popup keyboard = "miniKeyWidth".
        // On the other hand, "popupKey.width" is width of the pressed key on the main keyboard.
        // We adjust the position of mini popup keyboard with the edge key in it:
        //  a) When we have the leftmost key in popup keyboard directly above the pressed key
        //     Right edges of both keys should be aligned for consistent default selection
        //  b) When we have the rightmost key in popup keyboard directly above the pressed key
        //     Left edges of both keys should be aligned for consistent default selection
        val miniKeys = mMiniKeyboard!!.keyboard!!.keys
        val miniKeyWidth = if (!miniKeys.isEmpty()) miniKeys.get(0)!!.width else 0

        // HACK: Have the leftmost number in the popup characters right above the key
        val isNumberAtLeftmost =
            hasMultiplePopupChars(popupKey) && isNumberAtLeftmostPopupChar(popupKey)
        var popupX = popupKey.x + mWindowOffset!![0]
        popupX += getPaddingLeft()
        if (isNumberAtLeftmost) {
            popupX += popupKey.width - miniKeyWidth // adjustment for a) described above
            popupX -= container.getPaddingLeft()
        } else {
            popupX += miniKeyWidth // adjustment for b) described above
            popupX -= container.getMeasuredWidth()
            popupX += container.getPaddingRight()
        }
        var popupY = popupKey.y + mWindowOffset!![1]
        popupY += getPaddingTop()
        popupY -= container.getMeasuredHeight()
        popupY += container.getPaddingBottom()
        val x = popupX
        val y =
            if (this.isPreviewEnabled && isOneRowKeys(miniKeys)) mPopupPreviewDisplayedY else popupY

        var adjustedX = x
        if (x < 0) {
            adjustedX = 0
        } else if (x > (getMeasuredWidth() - container.getMeasuredWidth())) {
            adjustedX = getMeasuredWidth() - container.getMeasuredWidth()
        }
        mMiniKeyboardOriginX = adjustedX + container.getPaddingLeft() - mWindowOffset!![0]
        mMiniKeyboardOriginY = y + container.getPaddingTop() - mWindowOffset!![1]
        mMiniKeyboard!!.setPopupOffset(adjustedX, y)
        mMiniKeyboard!!.setShifted(this.isShifted)
        // Mini keyboard needs no pop-up key preview displayed.
        mMiniKeyboard!!.isPreviewEnabled =
            isLargeScreen && miniKeys.size > 1 // no fling on large screen
        mMiniKeyboardPopup.setContentView(container)
        mMiniKeyboardPopup.setWidth(container.getMeasuredWidth())
        mMiniKeyboardPopup.setHeight(container.getMeasuredHeight())
        mMiniKeyboardPopup.showAtLocation(this, Gravity.NO_GRAVITY, x, y)

        // Inject down event on the key to mini keyboard.
        val eventTime = SystemClock.uptimeMillis()
        mMiniKeyboardPopupTime = eventTime
        if (!isLargeScreen || miniKeys.size == 1) {   // disable fling on large screen; //Jeremy enable fling when popup keyboard only has 1 key '12,5,20
            val downEvent = generateMiniKeyboardMotionEvent(
                MotionEvent.ACTION_DOWN, popupKey.x
                        + popupKey.width / 2, popupKey.y + popupKey.height / 2, eventTime
            )
            mMiniKeyboard!!.onTouchEvent(downEvent)
            downEvent.recycle()
        }

        invalidateAllKeys()
        return true
    }

    private fun shouldDrawIconFully(key: LIMEBaseKeyboard.Key): Boolean {
        return (hasPopupKeyboard(key))

        //return isNumberAtEdgeOfPopupChars(key) || isLatinF1Key(key);
        //|| LIMEKeyboard.hasPuncOrSmileysPopup(key);
    }

    private fun shouldDrawLabelAndIcon(key: LIMEBaseKeyboard.Key): Boolean {
        return hasPopupKeyboard(key) || key.icon != null
    }

    private fun hasPopupKeyboard(key: LIMEBaseKeyboard.Key): Boolean {
        return key.popupResId != 0
    }

    private fun generateMiniKeyboardMotionEvent(
        action: Int,
        x: Int,
        y: Int,
        eventTime: Long
    ): MotionEvent {
        return MotionEvent.obtain(
            mMiniKeyboardPopupTime, eventTime, action,
            (x - mMiniKeyboardOriginX).toFloat(), (y - mMiniKeyboardOriginY).toFloat(), 0
        )
    }

    private fun getPointerTracker(id: Int): PointerTracker {
        val pointers = mPointerTrackers
        val keys = mKeys
        val listener = mKeyboardActionListener

        // Create pointer trackers until we can get 'id+1'-th tracker, if needed.
        for (i in pointers.size..id) {
            val tracker =
                PointerTracker(i, mHandler, mKeyDetector, this, getResources())
            if (keys != null) tracker.setKeyboard(keys, mKeyHysteresisDistance)
            if (listener != null) tracker.setOnKeyboardActionListener(listener)
            pointers.add(tracker)
        }

        return pointers.get(id)
    }

    private fun onDownEvent(tracker: PointerTracker, x: Int, y: Int, eventTime: Long) {
        if (DEBUG) Log.i(TAG, "onDownEvent() eventTime = " + eventTime)
        if (tracker.isOnModifierKey(x, y)) {
            // Before processing a down event of modifier key, all pointers already being tracked
            // should be released.
            mPointerQueue.releaseAllPointersExcept(null, eventTime)
        }
        tracker.onDownEvent(x, y, eventTime)
        mPointerQueue.add(tracker)
    }

    private fun onUpEvent(tracker: PointerTracker, x: Int, y: Int, eventTime: Long) {
        if (DEBUG) Log.i(TAG, "onUpEvent() eventTime = " + eventTime)

        if (tracker.isModifier()) {
            // Before processing an up event of modifier key, all pointers already being tracked
            // should be released.
            mPointerQueue.releaseAllPointersExcept(tracker, eventTime)
        } else {
            val index = mPointerQueue.lastIndexOf(tracker)
            if (index >= 0) {
                mPointerQueue.releaseAllPointersOlderThan(tracker, eventTime)
            } else {
                Log.w(
                    TAG, "onUpEvent: corresponding down event not found for pointer "
                            + tracker.mPointerId
                )
            }
        }
        tracker.onUpEvent(x, y, eventTime)
        mPointerQueue.remove(tracker)
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(me: MotionEvent): Boolean {
        if (DEBUG) Log.i(TAG, "onTouchEvent()")
        val action = me.getActionMasked()
        val pointerCount = me.getPointerCount()
        val oldPointerCount = mOldPointerCount
        mOldPointerCount = pointerCount

        if (DEBUG) Log.i(
            TAG,
            "onTouchEvent() pointerCount = " + pointerCount + ", oldPointerCount" + oldPointerCount
        )

        // TODO: cleanup this code into a multi-touch to single-touch event converter class?
        // If the device does not have distinct multi-touch support panel, ignore all multi-touch
        // events except a transition from/to single-touch.
        if (!mHasDistinctMultitouch && pointerCount > 1 && oldPointerCount > 1) {
            return true
        }

        // Track the last few movements to look for spurious swipes.
        mSwipeTracker.addMovement(me)

        // Gesture detector must be enabled only when mini-keyboard is not on the screen.
        if (mMiniKeyboard == null && mGestureDetector != null && mGestureDetector!!.onTouchEvent(me)) {
            dismissKeyPreview()
            mHandler.cancelKeyTimers()
            return true
        }

        val eventTime = me.getEventTime()
        val index = me.getActionIndex()
        val id = me.getPointerId(index)
        val x = me.getX(index).toInt()
        val y = me.getY(index).toInt()

        if (dismissPopupKeyboardOnOutsideTouch(action, x, y)) {
            return true
        }

        // Needs to be called after the gesture detector gets a turn, as it may have
        // displayed the mini keyboard
        if (mMiniKeyboard != null && (!isLargeScreen || mMiniKeyboard!!.keyboard!!.keys.size == 1)) {  //Jeremy enable fling when popup keyboard only has 1 key '12,5,20
            val miniKeyboardPointerIndex = me.findPointerIndex(mMiniKeyboardTrackerId)
            if (miniKeyboardPointerIndex >= 0 && miniKeyboardPointerIndex < pointerCount) {
                val miniKeyboardX = me.getX(miniKeyboardPointerIndex).toInt()
                val miniKeyboardY = me.getY(miniKeyboardPointerIndex).toInt()
                val translated = generateMiniKeyboardMotionEvent(
                    action,
                    miniKeyboardX, miniKeyboardY, eventTime
                )
                mMiniKeyboard!!.onTouchEvent(translated)
                translated.recycle()
            }
            return true
        }

        if (mHandler.isInKeyRepeat) {
            // It will keep being in the key repeating mode while the key is being pressed.
            if (action == MotionEvent.ACTION_MOVE) {
                return true
            }
            val tracker = getPointerTracker(id)
            // Key repeating timer will be canceled if 2 or more keys are in action, and current
            // event (UP or DOWN) is non-modifier key.
            if (pointerCount > 1 && !tracker.isModifier()) {
                mHandler.cancelKeyRepeatTimer()
            }
            // Up event will pass through.
        }

        // TODO: cleanup this code into a multi-touch to single-touch event converter class?
        // Translate mutli-touch event to single-touch events on the device that has no distinct
        // multi-touch panel.
        if (!mHasDistinctMultitouch) {
            // Use only main (id=0) pointer tracker.
            val tracker = getPointerTracker(0)
            if (pointerCount == 1 && oldPointerCount == 2) {
                // Multi-touch to single touch transition.
                // Send a down event for the latest pointer.
                tracker.onDownEvent(x, y, eventTime)
            } else if (pointerCount == 2 && oldPointerCount == 1) {
                // Single-touch to multi-touch transition.
                // Send an up event for the last pointer.
                tracker.onUpEvent(tracker.getLastX(), tracker.getLastY(), eventTime)
            } else if (pointerCount == 1 && oldPointerCount == 1) {
                tracker.onTouchEvent(action, x, y, eventTime)
            } else {
                Log.w(
                    TAG, ("Unknown touch panel behavior: pointer count is " + pointerCount
                            + " (old " + oldPointerCount + ")")
                )
            }
            return true
        }

        if (action == MotionEvent.ACTION_MOVE) {
            for (i in 0..<pointerCount) {
                val tracker = getPointerTracker(me.getPointerId(i))
                tracker.onMoveEvent(me.getX(i).toInt(), me.getY(i).toInt(), eventTime)
            }
        } else {
            val tracker = getPointerTracker(id)
            when (action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> onDownEvent(
                    tracker,
                    x,
                    y,
                    eventTime
                )

                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> onUpEvent(
                    tracker,
                    x,
                    y,
                    eventTime
                )

                MotionEvent.ACTION_CANCEL -> onCancelEvent(tracker, x, y, eventTime)
            }
        }

        return true
    }

    private fun dismissPopupKeyboardOnOutsideTouch(action: Int, x: Int, y: Int): Boolean {
        if (action != MotionEvent.ACTION_DOWN || !mMiniKeyboardPopup.isShowing() || !isTouchOutsideMiniKeyboard(
                x,
                y
            )
        ) {
            return false
        }
        mHandler.cancelKeyTimers()
        dismissKeyPreview()
        dismissPopupKeyboard()
        return true
    }

    private fun isTouchOutsideMiniKeyboard(x: Int, y: Int): Boolean {
        if (mMiniKeyboard == null) {
            return false
        }
        val left = mMiniKeyboardOriginX
        val top = mMiniKeyboardOriginY
        val width =
            if (mMiniKeyboard!!.getWidth() > 0) mMiniKeyboard!!.getWidth() else mMiniKeyboard!!.getMeasuredWidth()
        val height =
            if (mMiniKeyboard!!.height > 0) mMiniKeyboard!!.height else mMiniKeyboard!!.getMeasuredHeight()
        val right = left + width
        val bottom = top + height
        return x < left || x >= right || y < top || y >= bottom
    }

    private fun onCancelEvent(tracker: PointerTracker, x: Int, y: Int, eventTime: Long) {
        if (DEBUG) Log.i(TAG, "onCancelEvent() eventTime = " + eventTime)

        tracker.onCancelEvent(x, y, eventTime)
        mPointerQueue.remove(tracker)
    }

    protected fun swipeRight() {
        mKeyboardActionListener!!.swipeRight()
    }

    protected fun swipeLeft() {
        mKeyboardActionListener!!.swipeLeft()
    }

    protected fun swipeUp() {
        mKeyboardActionListener!!.swipeUp()
    }

    protected fun swipeDown() {
        mKeyboardActionListener!!.swipeDown()
    }

    fun closing() {
        if (DEBUG) Log.i(TAG, "closing()")

        mHandler.cancelAllMessages()
        dismissKeyPreview()
        dismissPopupKeyboard()
        mBuffer = null
        mBackBuffer = null
        mCanvas = null
        mBackCanvas = null
        mMiniKeyboardCache.clear()
    }

    public override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        closing()
    }

    private fun dismissPopupKeyboard() {
        if (mMiniKeyboardPopup.isShowing()) {
            mMiniKeyboardPopup.dismiss()
            mMiniKeyboard = null
            mMiniKeyboardOriginX = 0
            mMiniKeyboardOriginY = 0
            invalidateAllKeys()
        }
    }

    fun handleBack(): Boolean {
        if (mMiniKeyboardPopup.isShowing()) {
            dismissPopupKeyboard()
            return true
        }
        return false
    }

    companion object {
        private const val TAG = "LIMEKeyboardBaseView"
        private const val DEBUG = false

        @JvmField
        val NOT_A_TOUCH_COORDINATE: Int = -1

        // UI Dimension Constants (in pixels/dp)
        private const val DEFAULT_PREVIEW_HEIGHT_PX = 80 // Default key preview height
        private const val DEFAULT_KEY_TEXT_SIZE_SP = 18 // Default key text size
        private const val DEFAULT_LABEL_TEXT_SIZE_SP =
            14 // Default label text size (for sub-labels, small labels)
        private const val DEFAULT_PREVIEW_TOP_PADDING_PX = 10 // Default preview top padding
        private const val SWIPE_VELOCITY_UNITS_PER_SECOND = 1000 // Velocity calculation units

        // Swipe/Touch Constants
        private const val SWIPE_THRESHOLD_BASE_DP =
            500 // Base swipe threshold in density-independent pixels

        // Miscellaneous constants
        /* package */
        val NOT_A_KEY: Int = -1
        private val NUMBER_HINT_VERTICAL_ADJUSTMENT_PIXEL = -1

        private fun withAlpha(color: Int, alpha: Int): Int {
            return (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)
        }

        private fun isOneRowKeys(keys: List<LIMEBaseKeyboard.Key>): Boolean {
            if (keys.isEmpty()) return false
            val edgeFlags = keys.get(0).edgeFlags
            // HACK: The first key of mini keyboard which was inflated from xml and has multiple rows,
            // does not have both top and bottom edge flags on at the same time.  On the other hand,
            // the first key of mini keyboard that was created with popupCharacters must have both top
            // and bottom edge flags on.
            // When you want to use one row mini-keyboard from xml file, make sure that the row has
            // both top and bottom edge flags set.
            return (edgeFlags and LIMEBaseKeyboard.EDGE_TOP) != 0 && (edgeFlags and LIMEBaseKeyboard.EDGE_BOTTOM) != 0
        }

        private fun hasMultiplePopupChars(key: LIMEBaseKeyboard.Key): Boolean {
            val popupCharacters = key.popupCharacters
            return popupCharacters != null && popupCharacters.length > 1
        }

        private fun isNumberAtLeftmostPopupChar(key: LIMEBaseKeyboard.Key): Boolean {
            val popupCharacters = key.popupCharacters
            return popupCharacters != null && popupCharacters.length > 0 && isAsciiDigit(
                popupCharacters.get(
                    0
                )
            )
        }

        private fun isAsciiDigit(c: Char): Boolean {
            return (c.code < 0x80) && Character.isDigit(c)
        }
    }
}
