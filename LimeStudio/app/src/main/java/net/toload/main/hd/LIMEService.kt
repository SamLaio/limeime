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
package net.toload.main.hd

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.RemoteException
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.RecognizerIntent
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedList
import java.util.Locale
import java.util.Objects
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import net.toload.main.hd.candidate.CandidateInInputViewContainer
import net.toload.main.hd.candidate.CandidateView
import net.toload.main.hd.data.ChineseSymbol
import net.toload.main.hd.data.ChineseSymbol.Companion.getChineseSymoblList
import net.toload.main.hd.data.ChineseSymbol.Companion.getSymbol
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.data.Keyboard
import net.toload.main.hd.data.Mapping
import net.toload.main.hd.global.DiagnosticLog
import net.toload.main.hd.global.LIME
import net.toload.main.hd.global.LIMEPreferenceManager
import net.toload.main.hd.global.LIMEPreferenceManager.ReverseLookupOption
import net.toload.main.hd.global.LIMEUtilities
import net.toload.main.hd.global.SystemAccentColor
import net.toload.main.hd.global.SystemAccentColor.dynamicColorOptions
import net.toload.main.hd.global.SystemAccentColor.resolveSeedColor
import net.toload.main.hd.keyboard.LIMEBaseKeyboard
import net.toload.main.hd.keyboard.LIMEKeyboard
import net.toload.main.hd.keyboard.LIMEKeyboardBaseView
import net.toload.main.hd.keyboard.LIMEKeyboardView
import net.toload.main.hd.keyboard.LIMEMetaKeyKeyListener
import net.toload.main.hd.keepass.KeepassAutofillLock
import net.toload.main.hd.keepass.LimeKeepassImeSelectActivity
import net.toload.main.hd.keepass.LimeKeepassImeUnlockActivity
import net.toload.main.hd.limedb.LimeDB
import net.toload.main.hd.ui.LIMEPreference
import net.toload.main.hd.voice.AndroidSpeechRecognizerAdapter
import net.toload.main.hd.voice.DictationResultListener
import net.toload.main.hd.voice.DictationState
import net.toload.main.hd.voice.LIMEDictationController
import net.toload.main.hd.voice.LIMEVoiceInputRouter
import net.toload.main.hd.voice.LIMEVoiceInputRouter.chooseRoute
import net.toload.main.hd.voice.VoiceInputMode
import net.toload.main.hd.voice.VoiceInputRoute
import net.toload.main.hd.voice.VoicePermissionHelper
import net.toload.main.hd.voice.VoicePermissionHelper.hasRecordAudioPermission
import net.toload.main.hd.voice.VoicePermissionHelper.wasRecordAudioPermissionPrompted
import net.toload.main.hd.voice.VoicePermissionState
import net.toload.main.hd.VoiceInputActivity.Companion.consumePendingVoiceText

open class LIMEService : InputMethodService(), LIMEKeyboardBaseView.OnKeyboardActionListener,
    DictationResultListener {
    // True only immediately after an English suggestion pick auto-appended a space.
    private var mPickedAutoSpace = false

    private var mInputView: LIMEKeyboardView? = null
    private var mCandidateInInputView: CandidateInInputViewContainer? = null //Jeremy'12,5,3

    //private final boolean mFixedCandidateViewOn = true; //Jeremy'12,5,3 - Always true, kept for backward compatibility
    private var mCandidateView: CandidateView? = null
    private var mCandidateViewInInputView: CandidateView? = null
    private var mCompletions: Array<CompletionInfo?>? = null

    private var mComposing: StringBuilder? = StringBuilder()

    private var mPredictionOn = false
    private var mCompletionOn = false
    private var mCapsLock = false
    private var mLastShiftTime: Long = -1
    private var mAutoCap = false
    private var mHasShift = false

    private var mEnglishOnly = false
    private var mEnglishFlagShift = false
    private var mEmojiKeyboardShown = false
    private var mEmojiSourceWasEnglish = true
    var emojiKeyboardViewForTesting: View? = null
        private set
    private var mEmojiScroll: HorizontalScrollView? = null
    private var mEmojiPages: LinearLayout? = null
    private var mEmojiRoot: LinearLayout? = null
    private var mEmojiBottomBar: LinearLayout? = null
    private var mEmojiCategoryBar: LinearLayout? = null
    private var mEmojiSearchField: TextView? = null
    private var mEmojiAbcButton: TextView? = null
    var isEmojiContentRenderedForTesting: Boolean = false
        private set
    private var mEmojiCategoryIndex = 0
    private var mInputCandidateStripVisibilityBeforeEmoji = View.VISIBLE
    private var mEmojiSearchMode = false
    private var mEmojiSearchFocused = false
    private val mEmojiSearchQuery = StringBuilder()
    private var mEmojiCategoryPages: MutableList<MutableList<String?>?>? = null
    private var mEmojiPageCategoryIndexes: MutableList<Int?> = ArrayList<Int?>()
    private var mEmojiCategoryPageStarts: IntArray? = IntArray(0)
    private var mEmojiCategoryStartOffsets: IntArray? = IntArray(0)
    private var mPersistentLanguageMode = false //Jeremy '12,5,1
    private var mShowArrowKeys =
        0 //Jeremy '12,5,22 force recreate keyboard if show arrow keys mode changes.
    private var mSplitKeyboard =
        0 //Jeremy '12,5,26 force recreate keyboard if split keyboard settings changes; 6/19 changed to int

    @JvmField
    var hasMappingList: Boolean = false

    private var mMetaState: Long = 0
    private var mImeOptions = 0

    @JvmField
    var mKeyboardSwitcher: LIMEKeyboardSwitcher? = null

    private var mOrientation = 0
    private var mHardkeyboardHidden = 0
    private var mPredicting = false

    private var mThemeContext: Context? = null

    private var selectedCandidate: Mapping? = null //Jeremy '12,5,7 renamed from firstMacthed

    //private int selectedIndex; //Jeremy '12,5,7 the index in resultList of selectedCandidate
    private var committedCandidate: Mapping? = null //Jeremy '12,5,7 renamed from tempMatched

    private var tempEnglishWord: StringBuffer? = null
    private var tempEnglishList: MutableList<Mapping?>? = null

    private var hasPhysicalKeyPressed = false
    private var preservePhysicalComposingOnNextInputView = false

    // Voice input monitoring
    private var mInputMethodObserver: ContentObserver? = null
    private var mIsVoiceInputActive = false
    private var mPendingVoiceText: String? =
        null // text to commit once InputConnection is re-established
    private var mLIMEId: String? = null
    private var mDictationController: LIMEDictationController? = null
    private var mVoiceInputReceiver: BroadcastReceiver? = null
    private var mKeepassImeReceiver: BroadcastReceiver? = null
    private var mKeepassPanel: View? = null
    private var mKeepassSelectedTitle: String = ""
    private var mKeepassSelectedUsername: String = ""
    private var mKeepassSelectedPassword: String = ""
    private var mKeepassSelectedUrl: String = ""
    private var mKeepassSelectedNotes: String = ""
    private var mKeepassDetachedKeyboardIndex = -1
    private var mKeepassDetachedKeyboardLayoutParams: ViewGroup.LayoutParams? = null
    private val mKeepassAutoLockHandler = Handler(Looper.getMainLooper())
    private val mKeepassAutoLockRunnable = Runnable {
        if (KeepassAutofillLock.isUnlocked(this)) {
            scheduleKeepassAutoLock()
        } else {
            handleKeepassLocked(KeepassAutofillLock.lockReasonAuto)
        }
    }

    //private String mWordSeparators;
    //private String misMatched;  //Removed by Jeremy '13,1,10
    private var mCandidateList: LinkedList<Mapping?>? = null //Jeremy '12,5,7 renamed from templist

    private var mVibrator: Vibrator? = null
    private var mAudioManager: AudioManager? = null


    private var hasVibration = false
    private var hasSound = false
    private var hasNumberMapping = false
    private var hasSymbolMapping = false
    private var hasQuickSwitch = false

    // Hard Keyboad Shift + Space Status
    private var hasShiftPress = false
    private var onlyShiftPress = false //Jeremy '15,5,30 shift only to switch between chi/eng

    private var hasCtrlPress = false // Jeremy '11,5,13
    private var lastKeyCtrl =
        false // Jeremy '15,5,30 for process physical keyboard ctrl-space with missing space down event
    private var spaceKeyPress =
        false // Jeremy '15,5,30 for process physical keyboard ctrl-space with missing space down event
    private var hasWinPress =
        false // Jeremy '12,4,29 windows start key on standard windows keyboard

    //private boolean hasCtrlProcessed = false; // Jeremy '11,6.18
    private var hasDistinctMultitouch = false // Jeremy '11,8,3
    private var hasShiftCombineKeyPressed = false //Jeremy ,11,8, 3
    private var hasMenuPress = false // Jeremy '11,5,29
    private var hasMenuProcessed = false // Jeremy '11,5,29

    //private boolean hasSearchPress = false; // Jeremy '11,5,29
    //private boolean hasSearchProcessed = false; // Jeremy '11,5,29
    private var hasEnterProcessed = false // Jeremy '11,6.18
    private var hasSpaceProcessed = false
    private var hasKeyProcessed = false // Jeremy '11,8,15 for long pressed key
    private var mLongPressKeyTimeout = 0 //Jeremy '11,8, 15 read long press timeout from config

    private var hasSymbolEntered = false //Jeremy '11,5,24

    // private boolean hasSpacePress = false;
    // Hard Keyboad Shift + Space Status
    //private boolean hasAltPress = false;
    private var mIMActivatedState = "" // Jeremy '12,5,3, renamed from keyboardSelectedState
    @JvmField
    var activeIM: String? = null //Jeremy '12,4,30 renamed from keyboardSelection
    private var activatedIMFullNameList: MutableList<String?>? =
        null //Jeremy '12,4,30 renamed from keyboardList
    private var activatedIMShortNameList: MutableList<String?>? =
        null //Jeremy '12,4,30 renamed from keyboardShortname
    private var activatedIMList: MutableList<String?>? =
        null //Jeremy '12,4,30 renamed from keybaordCodeList
    private var currentSoftKeyboard: String? = "" //Jeremy '12,4,30 renamed from keybaord_xml;

    // To keep key press time
    //private long keyPressTime = 0;
    // Keep keydown event
    var mKeydownEvent: KeyEvent? = null

    //private int previousKeyCode = 0;
    //private final float moveLength = 15;
    //private ISearchService SearchSrv = null;
    private var SearchSrv: SearchServer? = null

    // Auto Commmit Value
    private var auto_commit = 0


    // Disable physical keyboard candidate words selection
    private var disable_physical_selection = false

    private var LDComposingBuffer = "" //Jeremy '11,7,30 for learning continuous typing phrases

    private var mLIMEPref: LIMEPreferenceManager? = null

    private var hasChineseSymbolCandidatesShown = false
    private var hasCandidatesShown = false

    // Track last known good bottom padding for older APIs (21-25) where window insets
    // might incorrectly include keyboard height when keyboard is restored
    private val mLastKnownBottomPadding = -1
    private var mLastUiNightMode = -1
    private var mAppliedStartupConfigVersion = -1L
    private var mStartupConfigSnapshotReady = false
    private var mStartupKeyboardConfigList: MutableList<Keyboard?>? = null
    private var mStartupImKeyboardConfigList: MutableList<ImConfig?>? = null
    var inputViewGenerationForTesting: Int = 0
        private set
    private var mAppliedFollowSystemAccent = 0
    private var mAppliedFollowSystemDarkTheme = false
    private var mAppliedFollowSystemInputView: View? = null
    private var mAppliedFollowSystemCandidateView: View? = null
    private var mAppliedFollowSystemEmbeddedCandidateView: View? = null
    private var mNavigationBarThemeApplied = false
    private var mAppliedNavigationBarWindow: Window? = null
    private var mAppliedNavigationBarCandidateView: View? = null
    private var mAppliedNavigationBarColor = 0
    private var mAppliedNavigationBarLightBackground = false


    /**
     * Main initialization of the input method component. Be sure to call to
     * super class.
     */
    @Suppress("deprecation")
    override fun onCreate() {
        if (DEBUG) Log.i(TAG, "OnCreate()")

        DiagnosticLog.record(this, TAG, "onCreate() start before super")
        super.onCreate()
        DiagnosticLog.record(this, TAG, "onCreate() after super")

        DiagnosticLog.record(this, TAG, "onCreate() creating SearchServer")
        SearchSrv = SearchServer(this)
        mEnglishOnly = false
        mEnglishFlagShift = false

        // Initialize default preferences from XML on first run
        // This must be called before creating LIMEPreferenceManager
        // PreferenceManager.setDefaultValues() loads XML defaults into SharedPreferences
        DiagnosticLog.record(this, TAG, "onCreate() loading default preferences")
        PreferenceManager.setDefaultValues(this, R.xml.preference, false)
        Log.i(TAG, "onCreate() - Default preferences initialized from XML")
        DiagnosticLog.record(this, TAG, "onCreate() default preferences loaded")

        // Construct Preference Access Tool
        DiagnosticLog.record(this, TAG, "onCreate() creating preference and dictation controllers")
        mLIMEPref = LIMEPreferenceManager(this)
        mDictationController = LIMEDictationController(AndroidSpeechRecognizerAdapter(this), this)

        // Initialize hasVibration flag from preferences immediately (so it's available for first keypress)
        hasVibration = mLIMEPref!!.getVibrateOnKeyPressed()
        Log.i(TAG, "onCreate() - initialized hasVibration: " + hasVibration)
        DiagnosticLog.record(this, TAG, "onCreate() hasVibration=$hasVibration")

        // Initialize vibrator for haptic feedback
        DiagnosticLog.record(this, TAG, "onCreate() initializing vibrator")
        Log.i(
            TAG,
            "onCreate() - Initializing Vibrator service, API level: " + Build.VERSION.SDK_INT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+: use VibratorManager
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager?
            if (vibratorManager != null) {
                mVibrator = vibratorManager.getDefaultVibrator()
            }
        } else {
            // API 22-30: use deprecated VIBRATOR_SERVICE
            mVibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator?
        }
        Log.i(TAG, "onCreate() - mVibrator = " + (if (mVibrator != null) "valid" else "null"))
        DiagnosticLog.record(this, TAG, "onCreate() vibrator=${if (mVibrator != null) "valid" else "null"}")

        // Initialize AudioManager for sound feedback
        mAudioManager = getSystemService(AUDIO_SERVICE) as AudioManager?
        Log.i(
            TAG,
            "onCreate() - AudioManager obtained, mAudioManager = " + (if (mAudioManager != null) "valid" else "null")
        )
        DiagnosticLog.record(this, TAG, "onCreate() audioManager=${if (mAudioManager != null) "valid" else "null"}")

        // mFixedCandidateViewOn is always true, so we can remove the variable
        // mFixedCandidateViewOn = mLIMEPref.getFixedCandidateViewDisplay();
        mLongPressKeyTimeout =
            getResources().getInteger(R.integer.config_long_press_key_timeout) // Jeremy '11,8,15 read longpress timeout from config resources.


        // initial keyboard list
        activatedIMFullNameList = ArrayList<String?>()
        activatedIMList = ArrayList<String?>()
        activatedIMShortNameList = ArrayList<String?>()
        activeIM = mLIMEPref!!.getActiveIM()
        DiagnosticLog.record(this, TAG, "onCreate() activeIM=$activeIM, building activated IM list")
        buildActivatedIMList()
        DiagnosticLog.record(this, TAG, "onCreate() activatedIMList size=${activatedIMList?.size ?: -1}")

        // Register receiver for voice input results
        DiagnosticLog.record(this, TAG, "onCreate() registering receivers")
        registerVoiceInputReceiver()
        registerKeepassImeReceiver()
        DiagnosticLog.record(this, TAG, "onCreate() complete")
        DiagnosticLog.exportToDownloadsAsync(this, "lime-service-onCreate")
    }


    /**
     * This is the point where you can do all of your UI initialization. It is
     * called after creation and any configuration change.
     */
    override fun onInitializeInterface() {
        if (DEBUG) Log.i(TAG, "onInitializeInterface()")

        DiagnosticLog.record(this, TAG, "onInitializeInterface() start")
        initialViewAndSwitcher(false)
        DiagnosticLog.record(this, TAG, "onInitializeInterface() initialViewAndSwitcher complete")
        initCandidateView() //Force the oncreatedcandidate to be called
        DiagnosticLog.record(this, TAG, "onInitializeInterface() initCandidateView complete")
        mKeyboardSwitcher!!.resetKeyboards(true)
        DiagnosticLog.record(this, TAG, "onInitializeInterface() resetKeyboards complete")
        super.onInitializeInterface()
        DiagnosticLog.record(this, TAG, "onInitializeInterface() complete")
    }

    override fun onCancel() {
        if (DEBUG) Log.i(TAG, "onCancel()")
    }

    /**
     * Override show_ime_with_hard_keyboard=0 which  prevent inputView shown
     * 
     * @return always true
     */
    override fun onEvaluateInputViewShown(): Boolean {
        val result = super.onEvaluateInputViewShown()
        val config = getResources().getConfiguration()
        if (DEBUG) Log.i(
            TAG, ("onEvaluateInputViewShown():" + result
                    + " config.keyboard :" + config.keyboard
                    + " config.hardKeyboardHidden :" + config.hardKeyboardHidden)
        )
        return true
        //        return result;
//        return config.keyboard == Configuration.KEYBOARD_NOKEYS
//                || config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES;
    }

    /**
     * Called by the system when the device configuration changes while your activity is running.
     */
    override fun onConfigurationChanged(conf: Configuration) {
        if (DEBUG) Log.i(TAG, "LIMEService:OnConfigurationChanged()")


        //Jeremy '12,4,7 add hard keyboard hidden configuration changed event and clear composing to avoid fc.
        if (conf.orientation != mOrientation || conf.hardKeyboardHidden != mHardkeyboardHidden) {
            //Jeremy '12,4,21 force clear the composing buffer
            clearComposing(true)


            mOrientation = conf.orientation
            mHardkeyboardHidden = conf.hardKeyboardHidden
        }
        val newUiMode = conf.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val oldUiMode = mLastUiNightMode
        mLastUiNightMode = newUiMode
        if (mKeyboardThemeIndex == 6 && newUiMode != oldUiMode) {
            mThemeContext = null // force theme rebuild
            clearAppliedFollowSystemAccentState()
            clearAppliedNavigationBarThemeState()
        }

        initialViewAndSwitcher(true)
        mKeyboardSwitcher!!.resetKeyboards(true)
        super.onConfigurationChanged(conf)
    }

    /**
     * Called by the framework when your view for creating input needs to be
     * generated. This will be called the first time your input method is
     * displayed, and every time it needs to be re-created such as due to a
     * configuration change.
     */
    override fun onCreateInputView(): View? {
        if (DEBUG) Log.i(TAG, "OnCreateInputView()")

        DiagnosticLog.record(this, TAG, "onCreateInputView() start")

        if (mInputView != null) mInputView = null

        initialViewAndSwitcher(true) //Jeremy '12,4,29.  will do buildactivekeyboardlist in init startInput
        DiagnosticLog.record(this, TAG, "onCreateInputView() initialViewAndSwitcher complete")

        val inputView: View?
        // mFixedCandidateViewOn is always true
        if (DEBUG) Log.i(TAG, "Fixed candidateView in on, return nInputViewContainer ")
        inputView = mCandidateInInputView
        DiagnosticLog.record(this, TAG, "onCreateInputView() inputView=${inputView?.javaClass?.simpleName ?: "null"}")

        // For API 35+, apply window insets to prevent overlap with system gesture navigation bar
        // Apply padding to the entire container to ensure both candidate view and keyboard view
        // have proper spacing from the navigation bar
        if (inputView != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ViewCompat.setOnApplyWindowInsetsListener(
                mCandidateInInputView!!,
                OnApplyWindowInsetsListener { v: View?, insets: WindowInsetsCompat? ->
                    val systemBarsType = WindowInsetsCompat.Type.systemBars()
                    val bottomInset = insets!!.getInsets(systemBarsType).bottom
                    v!!.setPadding(
                        v.getPaddingLeft(), 0,
                        v.getPaddingRight(), bottomInset
                    )

                    if (DEBUG) {
                        Log.i(
                            TAG,
                            ("Applied window insets to InputView container - bottom: " + bottomInset
                                    + ", API: " + Build.VERSION.SDK_INT
                                    + ", keyboard visible: " + (mInputView != null && mInputView!!.getVisibility() == View.VISIBLE)
                                    + ", saved: " + mLastKnownBottomPadding)
                        )
                    }
                    insets
                })
        }

        // Touch listeners will be set up in onStartInputView() after views are fully initialized

        // Issue #46: Tint nav bar to match active keyboard theme
        applyNavigationBarTheme()
        DiagnosticLog.record(this, TAG, "onCreateInputView() applyNavigationBarTheme complete")
        DiagnosticLog.exportToDownloadsAsync(this, "lime-service-input-view")

        return inputView
    }

    /**
     * Create and return the view hierarchy used to show candidates.
     * This will be called once, when the candidates are first displayed.
     * You can return null to have no candidates view; the default implementation returns null.
     */
    override fun onCreateCandidatesView(): View? {
        if (DEBUG) Log.i(TAG, "onCreateCandidatesView()")

        // Candidates are embedded in R.layout.inputcandidate. Returning a
        // framework candidates view here creates a second strip above the IME.
        return null
    }

    /**
     * Override this to control when the input method should run in fullscreen mode.
     * Jeremy '11,5,31
     * Override fullscreen editing mode settings for larger screen  (>1.4in)
     */
    override fun onEvaluateFullscreenMode(): Boolean {
        val dm = getResources().getDisplayMetrics()
        val displayHeight = dm.heightPixels.toFloat()
        // If the display is more than X inches high, don't go to fullscreen mode
        val max = getResources().getDimension(R.dimen.max_height_for_fullscreen)
        if (DEBUG) Log.i(
            TAG, ("onEvaluateFullScreenMode() DisplayHeight:" + displayHeight + " limit:" + max
                    + "super.onEvaluateFullscreenMode():" + super.onEvaluateFullscreenMode())
        )
        //Jeremy '12,4,30 Turn off evaluation only for tablet and xhdpi phones (required horizontal >900pts)
        return !(displayHeight > max && this.getMaxWidth() > 900) && super.onEvaluateFullscreenMode()
    }

    /**
     * This is called when the user is done editing a field. We can use this to
     * reset our state.
     */
    override fun onFinishInput() {
        if (DEBUG) {
            Log.i(TAG, "onFinishInput()")
        }
        // Stop monitoring IME changes when input finishes, except while a delegated
        // VoiceIME handoff is in progress. The handoff itself triggers onFinishInput().
        if (!mIsVoiceInputActive) {
            stopMonitoringIMEChanges()
        }
        cancelInlineDictationIfActive()
        // Don't unregister voice input receiver if voice input is in progress,
        // otherwise the broadcast carrying recognized text will be lost.
        if (!mIsVoiceInputActive) {
            unregisterVoiceInputReceiver()
        }
        super.onFinishInput()

        // mFixedCandidateViewOn is always true, so this branch is never executed
        // if (!mFixedCandidateViewOn && mInputView != null) {
        //     mInputView.closing();
        // }
        try {
            if (!LDComposingBuffer.isEmpty()) { // Force interrupt the LD process
                LDComposingBuffer = ""
                SearchSrv!!.addLDPhrase(null, true)
            }
            // Jeremy '11,8,1 do postfinishinput in searchSrv (learn userdic and LDPhrase).
            SearchSrv!!.postFinishInput()
        } catch (e: RemoteException) {
            Log.e(TAG, "Error in postFinishInput", e)
        }
        // Clear current composing text and candidates.
        //Jeremy '12,5,21
        finishComposing()

        // -> 26.May.2011 by Art : Update keyboard list when user click the keyboard.
        try {
            mKeyboardSwitcher!!.setKeyboardConfigList(SearchSrv!!.getKeyboardConfigList())
            mKeyboardSwitcher!!.setImConfigKeyboardList(SearchSrv!!.getAllImKeyboardConfigList())
        } catch (e: RemoteException) {
            Log.e(TAG, "Error setting keyboard/IM list in onFinishInput", e)
        }
    }

    /**
     * add by Jeremy '12,4,21
     * Send ic.finishComposingText upon composing is about to end
     */
    private fun finishComposing() {
        if (DEBUG) Log.i(TAG, "finishComposing()")
        //Jeremy '11,8,14
        if (mComposing != null && mComposing!!.length > 0) mComposing!!.setLength(0)

        val ic = getCurrentInputConnection()
        if (ic != null) ic.finishComposingText()

        selectedCandidate = null

        //selectedIndex = 0;
        if (mCandidateList != null) mCandidateList!!.clear()
        if (mCandidateView != null) mCandidateView!!.clear()
    }

    /**
     * add by Jeremy '12,4,21
     * clearComposing buffer upon composing is about to end
     * add forceClearComposing parameter to control forced clear the system composing buffer
     */
    private fun clearComposing(forceClearComposing: Boolean) {
        if (DEBUG) Log.i(TAG, "clearComposing()")

        //Log.i(TAG, "===========> clear composing");
        try {
            //Jeremy '11,8,14
            if (mComposing != null && mComposing!!.length > 0) mComposing!!.setLength(0)
            if (mCandidateList != null) mCandidateList!!.clear()

            if (forceClearComposing) {
                val ic = getCurrentInputConnection()
                if (ic != null) ic.commitText("", 0)
            }

            selectedCandidate = null

            //selectedIndex = 0;
            clearSuggestions()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing candidates", e)
            // ignore candidate clear error
        }
    }

    /**
     * Clear suggestions or candidates in candidate view.
     */
    @Synchronized
    private fun clearSuggestions() {
        if (mCandidateView != null) {
            if (DEBUG) Log.i(
                TAG, ("clearSuggestions(): "
                        + ", hasCandidatesShown:" + hasCandidatesShown)
            )

            // mFixedCandidateViewOn is always true, so (hasCandidatesShown || mFixedCandidateViewOn) is always true
            if (!mEnglishOnly && mLIMEPref!!.getAutoChineseSymbol()) {   // Change isCandiateShown() to hasCandiatesShown
                mCandidateView!!.clear()
                if (hasCandidatesShown) updateChineseSymbol() // Jeremy '12.5,23 do not show chinesesymbol when init for fixed candidate view.
            } else {
                mCandidateView!!.clear()
                hideCandidateView()
            }

            // Update CandidateView width constraint after clearing suggestions
            if (mCandidateInInputView != null) {
                mCandidateInInputView!!.updateCandidateViewWidthConstraint()
            }
        }
    }

    /**
     * Jeremy '15,7,8 to avoid candidateView shift up and down when it's not fixed.
     */
    override fun onComputeInsets(outInsets: Insets?) {
        super.onComputeInsets(outInsets)
        // Always use embedded candidate view in InputView, so no need to compute insets
        // The embedded candidate view is part of the inputView, so insets are handled automatically
    }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application. At this point we have been bound to
     * the client, and are now receiving all of the detailed information about
     * the target of our edits.
     */
    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        if (DEBUG) Log.i(TAG, "onStartInput()")
        super.onStartInput(attribute, restarting)
        if (!restarting) {
            preservePhysicalComposingOnNextInputView = false
            hasPhysicalKeyPressed = false
        }
        initOnStartInput(attribute)

        // Don't restore keyboard view here - only restore when user explicitly touches
        // the soft keyboard area (candidate view or InputView container)
    }

    override fun onStartInputView(attribute: EditorInfo, restarting: Boolean) {
        if (DEBUG) Log.i(TAG, "onStartInputView()")
        super.onStartInputView(attribute, restarting)
        resetEmojiKeyboardState()

        // Ensure InputView container is visible
        if (mCandidateInInputView != null) {
            mCandidateInInputView!!.setVisibility(View.VISIBLE)
        }

        // Save composing text before initOnStartInput() in case it clears state
        val savedComposing =
            if (mComposing != null && mComposing!!.length > 0) mComposing.toString() else null
        // Save hasPhysicalKeyPressed state before initOnStartInput()
        val savedHasPhysicalKeyPressed =
            hasPhysicalKeyPressed && preservePhysicalComposingOnNextInputView

        initOnStartInput(attribute)

        // Restore composing text if it was set by a physical key press before InputView was shown
        if (savedComposing != null && savedHasPhysicalKeyPressed) {
            // Restore hasPhysicalKeyPressed state and composing text
            hasPhysicalKeyPressed = true
            mComposing!!.setLength(0)
            mComposing!!.append(savedComposing)
            val ic = getCurrentInputConnection()
            if (ic != null && mPredictionOn) {
                ic.setComposingText(mComposing, 1)
            }
            // Update candidates to show the composing text and candidates for the first key
            updateCandidates()
            // Ensure candidate view is shown
            hasCandidatesShown = true
            // Hide keyboard view when physical key was pressed
            if (mInputView != null) {
                mInputView!!.setVisibility(View.GONE)
            }
            preservePhysicalComposingOnNextInputView = false
        } else {
            // No composing text to preserve, reset hasPhysicalKeyPressed and show keyboard view
            hasPhysicalKeyPressed = false
            preservePhysicalComposingOnNextInputView = false
            if (mInputView != null) {
                mInputView!!.setVisibility(View.VISIBLE)
            }
        }

        // Don't restore keyboard view here - only restore when user explicitly touches
        // the soft keyboard area (candidate view or InputView container)
        // This prevents restoring when InputView is shown but user is still using physical keyboard

        // Commit any voice text: check static field first (primary), then instance field (backup)
        var voiceText = consumePendingVoiceText()
        if (voiceText == null && mPendingVoiceText != null) {
            voiceText = mPendingVoiceText
            mPendingVoiceText = null
        }
        if (voiceText != null) {
            val ic = getCurrentInputConnection()
            if (ic != null) {
                val textToCommit = prepareVoiceTextForCommit(voiceText)
                ic.commitText(textToCommit, 1)
                Log.i(TAG, "onStartInputView(): Committed voice text: '" + textToCommit + "'")
            } else {
                Log.w(TAG, "onStartInputView(): IC still null, storing voice text for retry")
                mPendingVoiceText = voiceText
            }
            mIsVoiceInputActive = false
        }

        // Issue #46: Tint nav bar to match active keyboard theme (re-apply in case theme changed)
        applyNavigationBarTheme()
    }

    /**
     * Initialization for IM and softkeybaords, and also choose wring lanaguage mode
     * according the input attrubute in editorInfo
     */
    private fun initOnStartInput(attribute: EditorInfo) {
        if (DEBUG) Log.i(
            TAG, ("initOnStartInput(): attribute.inputType & EditorInfo.TYPE_MASK_CLASS: "
                    + (attribute.inputType and EditorInfo.TYPE_MASK_CLASS) + "; attribute.inputType & EditorInfo.TYPE_MASK_VARIATION: "
                    + (attribute.inputType and EditorInfo.TYPE_MASK_VARIATION))
        )


        //Jeremy '12,5,29 override the fixCandidateMode setting in Landscape mode (in landscape mode the candidate bar is always not fixed).
        // mFixedCandidateViewOn is always true, so we don't need to check fixedCandidateMode
        //Jeremy '12,5,6 recreate inputView if fixedCandidateView setting is altered - REMOVED: always true now
        //Jeremy '15,7,15 recreate inputView if keyboard theme changed
        // mFixedCandidateViewOn is always true, so mFixedCandidateViewOn != fixedCandidateMode is always false
        if (mKeyboardThemeIndex != mLIMEPref!!.getKeyboardTheme()) {
            requestHideSelf(0)
            mInputView!!.closing()
            initialViewAndSwitcher(true)

            // mFixedCandidateViewOn is always true
            if (DEBUG) Log.i(TAG, "Fixed candidateView in on, return nInputViewContainer ")
            if (mCandidateInInputView != null) setInputView(mCandidateInInputView)
        }

        // Don't reset hasPhysicalKeyPressed if it was just set by a physical key press
        // This prevents losing the first key when InputView is shown after physical key press
        if (!hasPhysicalKeyPressed) {
            // Show keyboard view when hasPhysicalKeyPressed is false
            if (mInputView != null) {
                mInputView!!.setVisibility(View.VISIBLE)
            }
        } else {
            // Hide keyboard view when hasPhysicalKeyPressed is true
            if (mInputView != null) {
                mInputView!!.setVisibility(View.GONE)
            }
        }
        // Don't reset hasCandidatesShown if a physical key was just pressed and composing text exists
        // This prevents losing the first key when InputView is shown after physical key press
        if (!hasPhysicalKeyPressed || (mComposing == null || mComposing!!.length == 0)) {
            hasCandidatesShown = false
        }

        activeIM = mLIMEPref!!.getActiveIM()
        val startupConfigRefreshed = refreshStartupConfigSnapshotIfNeeded()
        applyStartupConfigSnapshotToKeyboardSwitcher()

        mKeyboardSwitcher!!.resetKeyboards(
            startupConfigRefreshed
                    || mShowArrowKeys != mLIMEPref!!.getShowArrowKeys() //Jeremy '12,5,22 recreate keyboard if the setting altered.
                    || mSplitKeyboard != mLIMEPref!!.getSplitKeyboard()
        ) //Jeremy '12,5,26 recreate keyboard if the setting altered.

        loadSettings()
        mImeOptions = attribute.imeOptions

        if (mKeyboardSwitcher != null) {
            mKeyboardSwitcher!!.setActivatedIMList(activatedIMList, activatedIMShortNameList)
        }
        mPredictionOn = true
        mCompletionOn = false
        mCompletions = null
        mCapsLock = false
        mHasShift = false


        tempEnglishWord = StringBuffer()
        tempEnglishList = LinkedList<Mapping?>()


        when (attribute.inputType and EditorInfo.TYPE_MASK_CLASS) {
            EditorInfo.TYPE_CLASS_NUMBER -> {
                mEnglishOnly = true
                mKeyboardSwitcher!!.setKeyboardMode(
                    activeIM,
                    getRestrictedFieldKeyboardMode(attribute.inputType), mImeOptions, false,
                    getRestrictedFieldSymbolFlag(attribute.inputType), false
                )
            }

            EditorInfo.TYPE_CLASS_DATETIME -> {
                mEnglishOnly = true
                mKeyboardSwitcher!!.setKeyboardMode(
                    activeIM,
                    LIMEKeyboardSwitcher.MODE_TEXT,
                    mImeOptions,
                    false,
                    true,
                    false
                )
            }

            EditorInfo.TYPE_CLASS_PHONE -> {
                mEnglishOnly = true
                mKeyboardSwitcher!!.setKeyboardMode(
                    activeIM,
                    LIMEKeyboardSwitcher.MODE_PHONE, mImeOptions, false, false, false
                )
            }

            EditorInfo.TYPE_CLASS_TEXT -> {
                // Make sure that passwords are not displayed in candidate view
                val variation = (attribute.inputType
                        and EditorInfo.TYPE_MASK_VARIATION)
                /*
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        || variation == EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME) {
                    //mAutoSpace = false;
                } else {
                    //mAutoSpace = true;
                }
                */
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
                    mPredictionOn = false
                }
                /*
                if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0) {
                    //disableAutoCorrect = true;
                }*/
                // If NO_SUGGESTIONS is set, don't do prediction.
                if ((attribute.inputType and EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
                    mPredictionOn = false
                    //disableAutoCorrect = true;
                }
                // If it's not multiline and the autoCorrect flag is not set, then
                // don't correct
                /*
                if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0
                        && (attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) == 0) {
                    //disableAutoCorrect = true;
                }*/
                if ((attribute.inputType and EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    mPredictionOn = false
                    mCompletionOn = isFullscreenMode()
                }

                // Switch keyboard here.
                if (isForcedEnglishTextVariation(variation)) {
                    mPredictionOn = false
                    mEnglishOnly = true
                    mKeyboardSwitcher!!.setKeyboardMode(
                        activeIM,
                        LIMEKeyboardSwitcher.MODE_EMAIL, mImeOptions, false, false, false
                    )
                } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
                    mEnglishOnly = false
                    mKeyboardSwitcher!!.setKeyboardMode(
                        activeIM,
                        LIMEKeyboardSwitcher.MODE_IM,
                        mImeOptions,
                        true,
                        false,
                        false
                    )
                }
                if (mPersistentLanguageMode) mEnglishOnly =
                    mLIMEPref!!.getLanguageMode() //Jeremy '12,4,30 restore lanaguage mode from preference.


                if (mPersistentLanguageMode && mEnglishOnly) {
                    mPredictionOn = true
                    //mEnglishOnly = true;
                    //onIM = false; //Jeremy '12,4,29 use mEnglishOnly instead of onIM
                    mKeyboardSwitcher!!.setKeyboardMode(
                        activeIM, LIMEKeyboardSwitcher.MODE_TEXT,
                        mImeOptions, false, false, false
                    )
                } else {
                    mEnglishOnly = false
                    initialIMKeyboard() //'12,4,29 intial chinese IM keybaord
                }
            }

            else -> {
                if (mPersistentLanguageMode) mEnglishOnly = mLIMEPref!!.getLanguageMode()

                if (mPersistentLanguageMode && mEnglishOnly) {
                    mPredictionOn = true
                    mKeyboardSwitcher!!.setKeyboardMode(
                        activeIM, LIMEKeyboardSwitcher.MODE_TEXT,
                        mImeOptions, false, false, false
                    )
                } else {
                    mEnglishOnly = false
                    initialIMKeyboard()
                }
            }
        }


        if (!(mEnglishOnly && !mPredictionOn)) {
            clearComposing(false) //Jeremy '12,5,24 clear the suggesions and also restore the height of fixed candaiteview if it's hide before
            //clearSuggestions();  // do this in clearcomposing already.
        }
        // Keep toolbar visible for mic/emoji even when no candidates are active.
        showEmptyCandidateToolbar()

        mPredicting = false
        updateShiftKeyState(getCurrentInputEditorInfo())


        //initCandidateView(); //Force the oncreatedcandidate to be called
        //clearComposing(false);
    }

    private fun loadSettings() {
        hasVibration = mLIMEPref!!.getVibrateOnKeyPressed()
        hasSound = mLIMEPref!!.getSoundOnKeyPressed()
        mPersistentLanguageMode = mLIMEPref!!.getPersistentLanguageMode()
        activeIM = mLIMEPref!!.getActiveIM()
        hasQuickSwitch = mLIMEPref!!.getSwitchEnglishModeHotKey()
        mAutoCap = mLIMEPref!!.getAutoCaptalization()

        mPersistentLanguageMode = mLIMEPref!!.getPersistentLanguageMode()
        mShowArrowKeys = mLIMEPref!!.getShowArrowKeys()
        mSplitKeyboard = mLIMEPref!!.getSplitKeyboard()

        disable_physical_selection = mLIMEPref!!.getDisablePhysicalSelkey()

        auto_commit = mLIMEPref!!.getAutoCommitValue()
        currentSoftKeyboard = mKeyboardSwitcher!!.getImConfigKeyboard(activeIM)
    }

    private val isStartupConfigSnapshotDirty: Boolean
        get() {
            if (mLIMEPref == null || !mStartupConfigSnapshotReady) return true
            val currentVersion = mLIMEPref!!.getStartupConfigVersion()
            return currentVersion == 0L || currentVersion != mAppliedStartupConfigVersion
        }

    private fun invalidateStartupConfigSnapshot() {
        mStartupConfigSnapshotReady = false
        mAppliedStartupConfigVersion = -1L
    }

    private fun refreshStartupConfigSnapshotIfNeeded(rebuildActivatedIMList: Boolean = true): Boolean {
        if (!this.isStartupConfigSnapshotDirty) return false

        if (rebuildActivatedIMList) {
            buildActivatedIMList()
        }

        if (SearchSrv != null) {
            try {
                mStartupKeyboardConfigList = SearchSrv!!.getKeyboardConfigList()
                mStartupImKeyboardConfigList = SearchSrv!!.getAllImKeyboardConfigList()
            } catch (e: RemoteException) {
                Log.e(TAG, "Error refreshing startup keyboard config snapshot", e)
            }
        }

        mStartupConfigSnapshotReady = true
        markStartupConfigSnapshotApplied()
        return true
    }

    private fun markStartupConfigSnapshotApplied() {
        if (mLIMEPref == null) return
        var currentVersion = mLIMEPref!!.getStartupConfigVersion()
        if (currentVersion == 0L) {
            currentVersion = mLIMEPref!!.initializeStartupConfigVersion()
        }
        mAppliedStartupConfigVersion = currentVersion
    }

    private fun applyStartupConfigSnapshotToKeyboardSwitcher() {
        if (mKeyboardSwitcher == null) return
        if (mStartupKeyboardConfigList != null && !mStartupKeyboardConfigList!!.isEmpty()) {
            mKeyboardSwitcher!!.setKeyboardConfigList(mStartupKeyboardConfigList)
        }
        if (mStartupImKeyboardConfigList != null && !mStartupImKeyboardConfigList!!.isEmpty()) {
            mKeyboardSwitcher!!.setImConfigKeyboardList(mStartupImKeyboardConfigList)
        }
    }

    private fun advanceInputViewGeneration() {
        this.inputViewGenerationForTesting++
        clearAppliedFollowSystemAccentState()
        clearAppliedNavigationBarThemeState()
    }

    private fun postAfterFirstFrame(task: Runnable?) {
        if (mCandidateInInputView == null || task == null) return
        val generation = this.inputViewGenerationForTesting
        mCandidateInInputView!!.post(Runnable { runIfCurrentInputViewGeneration(generation, task) })
    }

    private fun runIfCurrentInputViewGeneration(generation: Int, task: Runnable?) {
        if (task == null || generation != this.inputViewGenerationForTesting) return
        task.run()
    }

    fun advanceInputViewGenerationForTesting() {
        advanceInputViewGeneration()
    }

    fun runIfCurrentInputViewGenerationForTesting(generation: Int, task: Runnable?) {
        runIfCurrentInputViewGeneration(generation, task)
    }

    /**
     * Deal with the editor reporting movement of its cursor.
     */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int, candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd,
            candidatesStart, candidatesEnd
        )

        if (DEBUG) Log.i(
            TAG, ("onUpdateSelection():oldSelStart" + oldSelStart
                    + " oldSelEnd:" + oldSelEnd
                    + " newSelStart:" + newSelStart + " newSelEnd:" + newSelEnd
                    + " candidatesStart:" + candidatesStart + " candidatesEnd:" + candidatesEnd)
        )

        val ic = getCurrentInputConnection()

        if (mComposing!!.length > 0 && (candidatesEnd != candidatesStart) && candidatesStart >= 0 && candidatesEnd > 0 // in composing
        ) {
            if (newSelStart < candidatesStart || newSelStart > candidatesEnd) { // cursor is moved before or after composing area

                if (mCandidateList != null) mCandidateList!!.clear()
                //mCandidateView.clear();
                hideCandidateView()

                if (mComposing != null && mComposing!!.length > 0) {
                    mComposing!!.setLength(0)


                    if (ic != null) ic.finishComposingText()
                }
            }


            // Jeremy '13,8,25 setSelection cause inputbox in Chorme failed to input
            // Jeremy '12,5,23 Select the composing text and forbidded moving cursor within the composing text.
            //if (ic != null)	ic.setSelection(candidatesStart, candidatesEnd);
        }
    }

    /**
     * This tells us about completions that the editor has determined based on
     * the current text in it. We want to use this in fullscreen mode to show
     * the completions ourself, since the editor can not be seen in that
     * situation.
     */
    override fun onDisplayCompletions(completions: Array<CompletionInfo?>?) {
        if (DEBUG) Log.i(TAG, "onDisplayCompletions()")
        if (mCompletionOn) {
            mCompletions = completions
            if (!mEnglishOnly) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
                if (mComposing!!.length == 0) updateRelatedPhrase(false)
            }
            if (mEnglishOnly && !mPredictionOn) {
                setSuggestions(buildCompletionList(), false, "")
            }
        }
    }

    /**
     * This translates incoming hard key events in to edit operations on an
     * InputConnection. It is only needed when using the PROCESS_HARD_KEYS
     * option.
     */
    private fun translateKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        hasPhysicalKeyPressed = true
        // Hide keyboard view when physical key is pressed
        if (mInputView != null) {
            mInputView!!.setVisibility(View.GONE)
        }

        // Request layout update for candidate view container to show buttons
        if (mCandidateInInputView != null) {
            mCandidateInInputView!!.post(Runnable { mCandidateInInputView!!.requestLayout() })
        }

        // Show InputView when physical key is pressed to display embedded candidate view
        // Store flag to show InputView after key is processed to avoid losing the first key
        val needToShowInputView = !isInputViewShown()
        preservePhysicalComposingOnNextInputView = needToShowInputView


        //Jeremy '25/12/14 Always use fix candidateView even for physical keyboard. (API 34+ cannot shown candidateView well)
        // If user use the physical keyboard then not fixed the candidate view also use the transparent background
//        if(mCandidateView!=null) {
//            mFixedCandidateViewOn = false;
//            mCandidateView.setTransparentCandidateView(false);
//        }
        if (DEBUG) Log.i(
            TAG, ("translateKeyDown() LIMEMetaKeyKeyListener.getMetaState(mMetaState) = "
                    + Integer.toHexString(LIMEMetaKeyKeyListener.getMetaState(mMetaState))
                    + ", event.getMetaState()" + Integer.toHexString(event.getMetaState()))
        )

        //Jeremy '12,5,28 after honeycomb use the metastate sent form KeyEvent to process the shift/cap_lock etc...
        val metaState: Int
        if (mLIMEPref!!.getPhysicalKeyboardType() == LIME.IM_PHONETIC) metaState =
            event.getMetaState()
        else metaState = LIMEMetaKeyKeyListener.getMetaState(mMetaState)


        var c = event.getUnicodeChar(metaState)


        val ic = getCurrentInputConnection()

        /** Jeremy '12,4,1 XPERIA Pro force translating special keys */
        if (mLIMEPref!!.getPhysicalKeyboardType() == "xperiapro") {
            val isShift = LIMEMetaKeyKeyListener.getMetaState(
                mMetaState,
                LIMEMetaKeyKeyListener.META_SHIFT_ON
            ) > 0
            when (keyCode) {
                KeyEvent.KEYCODE_AT -> if (isShift) c = '/'.code
                else c = '!'.code

                KeyEvent.KEYCODE_APOSTROPHE -> if (isShift) c = '"'.code
                else c = '\''.code

                KeyEvent.KEYCODE_GRAVE -> if (isShift) c = '~'.code
                else c = '`'.code

                KeyEvent.KEYCODE_COMMA -> if (isShift) c = '?'.code
                else c = '.'.code

                KeyEvent.KEYCODE_PERIOD -> if (isShift) c = '>'.code
                else c = '@'.code
            }
        }

        if (c == 0 || ic == null) {
            return false
        }

        // Compact code by Jeremy '10, 3, 27
        if (keyCode == 59) { // Translate shift as -1
            c = -1
        }
        if (c != -1 && (c and KeyCharacterMap.COMBINING_ACCENT) != 0) {
            c = c and KeyCharacterMap.COMBINING_ACCENT_MASK
        }

        // Process the key first to ensure it's added to composing
        onKey(c, null)

        // Show InputView after key is processed to avoid losing the first key
        // Note: We manage InputView visibility directly via mInputView.setVisibility()
        // instead of using requestShowSelf() which causes IllegalAccessError on some Android versions
        if (needToShowInputView) {
            // Use post() to ensure onKey() completes first, then verify composing text is set
            Handler(Looper.getMainLooper()).post(Runnable {
                // Ensure composing text is set in InputConnection before showing InputView
                if (mComposing != null && mComposing!!.length > 0) {
                    val inputConn = getCurrentInputConnection()
                    if (inputConn != null && mPredictionOn) {
                        // Explicitly set composing text to ensure it's committed before showing InputView
                        inputConn.setComposingText(mComposing, 1)
                    }
                }
                // Show InputView directly by setting visibility
                // The system will show the IME when InputView becomes visible
                if (mInputView != null && mInputView!!.getVisibility() != View.VISIBLE) {
                    mInputView!!.setVisibility(View.VISIBLE)
                    if (mCandidateInInputView != null) {
                        mCandidateInInputView!!.setVisibility(View.VISIBLE)
                    }
                }
            })
        }

        return true
    }


    /**
     * Physical KeyBoard Event Handler Use this to monitor key events being
     * delivered to the application. We get first crack at them, and can either
     * resume them or let them continue to the app.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Clean code by jeremy '11,8,22
        if (DEBUG) Log.i(
            TAG, ("OnKeyDown():keyCode:" + keyCode
                    + ", mComposing = " + mComposing
                    + ", hasMenuPress = " + hasMenuPress
                    + ", hasCtrlPress = " + hasCtrlPress
                    + ", isCtrlPressed = " + event.isCtrlPressed()
                    + ", hasShiftPress = " + hasShiftPress
                    + ", onlyShiftPress = " + onlyShiftPress
                    + ", hasWinPress = " + hasWinPress
                    + ", event.getEventTime() -  event.getDownTime()" + (event.getEventTime() - event.getDownTime())
                    + ", event.getRepeatCount()" + event.getRepeatCount()
                    + ", event.getMetaState()" + Integer.toHexString(event.getMetaState()))
        )

        // Show InputView when physical key is pressed to display embedded candidate view
        // This ensures candidates are visible even when using physical keyboard
        // if (mInputView != null && !isInputViewShown()) {
        //     requestShowSelf(0);
        // }
        mKeydownEvent = KeyEvent(event)
        // Record key pressed time and set key processed flags(key down, for physical keys)
        //Jeremy '11,8,22 using getRepeatCount from event to set processed flags
        if (event.getRepeatCount() == 0) { //!keydown) {
            //keyPressTime = System.currentTimeMillis();
            //keydown = true;
            hasKeyProcessed = false
            hasMenuProcessed = false // only do this on first keydown event
            hasEnterProcessed = false
            hasSpaceProcessed = false
            hasSymbolEntered = false
            //Jeremy '15,5,30 for physical keyboard
            onlyShiftPress = false
            lastKeyCtrl = false
            spaceKeyPress = false
        }


        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> hasMenuPress = true
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (hasCandidatesShown) { //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
                mCandidateView!!.selectNext()
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> if (hasCandidatesShown) { //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
                mCandidateView!!.selectPrev()
                return true
            }

            KeyEvent.KEYCODE_DPAD_UP -> if (hasCandidatesShown) { //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
                mCandidateView!!.selectPrevRow()
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> if (hasCandidatesShown) { //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
                mCandidateView!!.selectNextRow()
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER -> if (hasCandidatesShown) { //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
                pickHighlightedCandidate()
                return true
            }

            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                hasShiftPress = true
                onlyShiftPress = true
                mMetaState = LIMEMetaKeyKeyListener.handleKeyDown(mMetaState, keyCode, event)
            }

            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> mMetaState =
                LIMEMetaKeyKeyListener.handleKeyDown(mMetaState, keyCode, event)

            MY_KEYCODE_CTRL_LEFT, MY_KEYCODE_CTRL_RIGHT -> {
                hasCtrlPress = true
                lastKeyCtrl = true
            }

            MY_KEYCODE_WINDOWS_START -> hasWinPress = true
            MY_KEYCODE_ESC, KeyEvent.KEYCODE_BACK -> {
                // The InputMethodService already takes care of the back
                // key for us, to dismiss the input method if it is shown.
                // However, our keyboard could be showing a pop-up window
                // that back should dismiss, so we first allow it to do that.
                if (event.getRepeatCount() == 0) {
                    if (mInputView != null && mInputView!!.handleBack()) {
                        Log.i(TAG, "KEYCODE_BACK mInputView handled the backed key")
                        return true
                    } else if (!mEnglishOnly && hasCandidatesShown
                        && (mComposing!!.length > 0
                                || (selectedCandidate != null && !selectedCandidate!!.isComposingCodeRecord() && !hasChineseSymbolCandidatesShown))
                    ) {
                        if (DEBUG) Log.i(TAG, "KEYCODE_BACK clear composing only.")
                        clearComposing(false)
                        return true
                    } else if (!mEnglishOnly && hasCandidatesShown) { //Jeremy '12,6,13
                        hideCandidateView()
                        return true
                    }
                }
                if (DEBUG) Log.i(TAG, "KEYCODE_BACK return to super.")
            }

            KeyEvent.KEYCODE_DEL -> {
                // Special handling of the delete key: if we currently are
                // composing text for the user, we want to modify that instead
                // of let the application to the delete itself.
                hasPhysicalKeyPressed = true
                // Hide keyboard view when physical key is pressed
                if (mInputView != null) {
                    mInputView!!.setVisibility(View.GONE)
                }
                onKey(LIMEBaseKeyboard.KEYCODE_DELETE, null)
                return true
            }

            KeyEvent.KEYCODE_ENTER -> {
                // Let the underlying text editor always handle these, if return
                // false from takeSelectedSuggestion().
                // Process enter for candidate view selection in OnKeyUp() to block
                // the real enter afterward.
                // return false;
                // Log.i("ART", "physical keyboard:"+ keyCode);
                mMetaState = LIMEMetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState)
                setInputConnectionMetaStateAsCurrentMetaKeyKeyListenerState()
                if (!mEnglishOnly) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
                    if (hasCandidatesShown) { //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
                        // To block a real enter after suggestion selection. We have to
                        // return true in OnKeyUp();
                        if (pickHighlightedCandidate()) {
                            hasEnterProcessed = true
                            return true
                        } else {
                            hideCandidateView()
                        }
                    }
                } else if ( //mLIMEPref.getEnglishPrediction() &&
                    mPredictionOn && mLIMEPref!!.getEnglishPredictionOnPhysicalKeyboard()) {
                    resetTempEnglishWord()
                    this.updateEnglishPrediction()
                } else {
                    // Jeremy '12',7,1 bug fixed on english mode enter not functioning in chrome
                }

                spaceKeyPress = true
                hasQuickSwitch = mLIMEPref!!.getSwitchEnglishModeHotKey()
                // If user enable Quick Switch Mode control then check if has
                // 	Shift+Space combination
                // '11,5,13 Jeremy added Ctrl-space switch chi/eng
                // '11,6,18 Jeremy moved from on_KEY_UP
                // '12,4,29 Jeremy add hasWinPress + space to switch chi/eng (earth key on zippy keyboard)
                // '12,5,8  Jeremy add send the space key to onKey with translatekeydown for candidate processing if it's not switching chi/eng
                if ((hasQuickSwitch && hasShiftPress) || hasCtrlPress || hasMenuPress || hasWinPress || event.isCtrlPressed()) {
                    if (!hasWinPress) this.switchChiEng() //Jeremy '12,5,20 move hasWinPress to winstartkey in onkeyUp()

                    if (hasMenuPress) hasMenuProcessed = true
                    hasSpaceProcessed = true
                    return true
                } else return translateKeyDown(keyCode, event)
            }

            KeyEvent.KEYCODE_SPACE -> {
                spaceKeyPress = true
                hasQuickSwitch = mLIMEPref!!.getSwitchEnglishModeHotKey()
                if ((hasQuickSwitch && hasShiftPress) || hasCtrlPress || hasMenuPress || hasWinPress || event.isCtrlPressed()) {
                    if (!hasWinPress) this.switchChiEng()
                    if (hasMenuPress) hasMenuProcessed = true
                    hasSpaceProcessed = true
                    return true
                } else return translateKeyDown(keyCode, event)
            }

            MY_KEYCODE_SWITCH_CHARSET, 1000 -> switchChiEng()
            KeyEvent.KEYCODE_SYM, KeyEvent.KEYCODE_AT -> {
                //Jeremy '11,8,22 use begintime and eventtime in event to see if long-pressed or not.
                if (!hasKeyProcessed && event.getRepeatCount() > 0 && event.getEventTime() - event.getDownTime() > mLongPressKeyTimeout) {
                    //&& System.currentTimeMillis() - keyPressTime > mLongPressKeyTimeout){
                    switchChiEng()
                    hasKeyProcessed = true
                }
                return true
            }

            KeyEvent.KEYCODE_TAB -> {
                if (!(LIMEMetaKeyKeyListener.getMetaState(
                        mMetaState,
                        LIMEMetaKeyKeyListener.META_ALT_ON
                    ) > 0
                            && mLIMEPref!!.getPhysicalKeyboardType() == "milestone2")
                ) {
                    if (!(hasCtrlPress || event.isCtrlPressed() || hasMenuPress)) {
                        if (translateKeyDown(keyCode, event)) {
                            if (DEBUG) Log.i(TAG, "Onkeydown():tranlatekeydown:true")
                            return true
                        }
                    }
                }
            }

            else -> if (!(hasCtrlPress || event.isCtrlPressed() || hasMenuPress)) {
                if (translateKeyDown(keyCode, event)) {
                    if (DEBUG) Log.i(TAG, "Onkeydown():tranlatekeydown:true")
                    return true
                }
            }

        }


        if ((hasCtrlPress || hasMenuPress) && !mEnglishOnly) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
            val primaryKey = event.getUnicodeChar(LIMEMetaKeyKeyListener.getMetaState(mMetaState))
            val t = primaryKey.toChar()


            if (hasCtrlPress &&  //Only working with ctrl Jeremy '11,8,22
                mCandidateList != null && !mCandidateList!!.isEmpty() && mCandidateView != null && hasCandidatesShown
            ) {
                when (keyCode) {
                    8 -> {
                        this.pickCandidateManually(0)
                        return true
                    }

                    9 -> {
                        this.pickCandidateManually(1)
                        return true
                    }

                    10 -> {
                        this.pickCandidateManually(2)
                        return true
                    }

                    11 -> {
                        this.pickCandidateManually(3)
                        return true
                    }

                    12 -> {
                        this.pickCandidateManually(4)
                        return true
                    }

                    13 -> {
                        this.pickCandidateManually(5)
                        return true
                    }

                    14 -> {
                        this.pickCandidateManually(6)
                        return true
                    }

                    15 -> {
                        this.pickCandidateManually(7)
                        return true
                    }

                    16 -> {
                        this.pickCandidateManually(8)
                        return true
                    }

                    7 -> {
                        this.pickCandidateManually(9)
                        return true
                    }
                }
            }
            if ((mComposing == null || mComposing!!.length == 0)) {
                // Jeremy '11,8,21.  Ctrl-/ to fetch full-shaped chinese symbols1 in candidateview.
                if (t == '/') {
                    if (hasMenuPress) hasMenuProcessed = true
                    updateChineseSymbol()
                    return true
                }
                // 27.May.2011 Art : when user click Ctrl + Symbol or number then send Chinese Symobl Characters
                val s = getSymbol(t)
                if (s != null) {
                    clearSuggestions()
                    getCurrentInputConnection().commitText(s, 0)
                    hasSymbolEntered = true
                    if (hasMenuPress) hasMenuProcessed = true
                    return true
                }
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun resetTempEnglishWord() {
        tempEnglishWord!!.delete(0, tempEnglishWord!!.length)
        tempEnglishList!!.clear()
    }

    private fun setInputConnectionMetaStateAsCurrentMetaKeyKeyListenerState() {
        val ic = getCurrentInputConnection()
        if (ic != null) {
            var clearStatesFlags = 0
            if (LIMEMetaKeyKeyListener.getMetaState(
                    mMetaState,
                    LIMEMetaKeyKeyListener.META_ALT_ON
                ) == 0
            ) clearStatesFlags += KeyEvent.META_ALT_ON
            if (LIMEMetaKeyKeyListener.getMetaState(
                    mMetaState,
                    LIMEMetaKeyKeyListener.META_SHIFT_ON
                ) == 0
            ) clearStatesFlags += KeyEvent.META_SHIFT_ON
            if (LIMEMetaKeyKeyListener.getMetaState(
                    mMetaState,
                    LIMEMetaKeyKeyListener.META_SYM_ON
                ) == 0
            ) clearStatesFlags += KeyEvent.META_SYM_ON
            ic.clearMetaKeyStates(clearStatesFlags)
        }
    }

    /**
     * Use this to monitor key events being delivered to the application. We get
     * first crack at them, and can either resume them or let them continue to
     * the app.
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (DEBUG) Log.i(
            TAG, ("OnKeyUp():keyCode:" + keyCode
                    + ", mComposing = " + mComposing
                    + ", hasCtrlPress:" + hasCtrlPress
                    + ", hasWinPress:" + hasWinPress
                    + ", hasShiftPress = " + hasShiftPress
                    + ", event.getEventTime() -  event.getDownTime()" + (event.getEventTime() - event.getDownTime()))

        )


        when (keyCode) {
            KeyEvent.KEYCODE_CAPS_LOCK -> {
                // Modified by Art 20130607
                // to switch the cap lock mode
                toggleCapsLock()
                hasMenuPress = false
                if (hasMenuProcessed) return true
            }

            KeyEvent.KEYCODE_MENU -> {
                hasMenuPress = false
                if (hasMenuProcessed) return true
            }

            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                hasShiftPress = false
                mMetaState = LIMEMetaKeyKeyListener.handleKeyUp(mMetaState, keyCode, event)
                // '11,8,28 Jeremy popup keyboard picker instead of nextIM when onIM
                // '11,5,14 Jeremy ctrl-shift switch to next available keyboard;
                // '11,5,24 blocking switching if full-shape symbol
                if (!hasSymbolEntered && !mEnglishOnly && (hasMenuPress || hasCtrlPress)) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
                    //nextActiveKeyboard(true);
                    showIMPicker() //Jeremy '11,8,28
                    if (hasMenuPress) {
                        hasMenuProcessed = true
                        hasMenuPress = false
                    }
                    mMetaState = LIMEMetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState)
                    setInputConnectionMetaStateAsCurrentMetaKeyKeyListenerState()
                    return true
                } else if (mLIMEPref!!.getShiftSwitchEnglishMode() && onlyShiftPress) {
                    this.switchChiEng()
                    return true
                }
            }

            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> mMetaState =
                LIMEMetaKeyKeyListener.handleKeyUp(mMetaState, keyCode, event)

            MY_KEYCODE_CTRL_LEFT, MY_KEYCODE_CTRL_RIGHT -> hasCtrlPress = false
            MY_KEYCODE_WINDOWS_START -> {
                if (hasSpaceProcessed)  //Jeremy '12,5,20 long press to show IM picker, switch chi/eng otherwise for the win+space or earth key on zippy
                    if (event.getEventTime() - event.getDownTime() > mLongPressKeyTimeout) showIMPicker()
                    else switchChiEng()
                hasWinPress = false
            }

            KeyEvent.KEYCODE_ENTER ->                 // Add by Jeremy '10, 3 ,29. Pick selected selection if candidates
                // shown.
                // Does not block real enter after select the suggestion. !! need
                // fix here!!
                // Let the underlying text editor always handle these, if return
                // false from takeSelectedSuggestion().
                if (hasEnterProcessed) {
                    return true
                }

            KeyEvent.KEYCODE_SYM, KeyEvent.KEYCODE_AT -> if (hasKeyProcessed) {  //(keyPressTime != 0
                //&& System.currentTimeMillis() - keyPressTime > 700) {
                //switchChiEng(); // Jeremy '11,8,15 moved to onKeyDown()
                return true
            } else if (LIMEMetaKeyKeyListener.getMetaState(
                    mMetaState,
                    LIMEMetaKeyKeyListener.META_SHIFT_ON
                ) > 0 && !mEnglishOnly //Jeremy '12,4,29 use mEnglishOnly instead of onIM
                && (mLIMEPref!!.getPhysicalKeyboardType() != "xperiapro")
            ) {  // '12,4,1 Jeremy XPERIA Pro does not use this key as @
                // alt-@ is conflict with symbol input thus altered to shift-@ Jeremy '11,8,15
                // alt-@ switch to next active keyboard.
                //nextActiveKeyboard(true);
                showIMPicker() //Jeremy '11,8,28
                mMetaState = LIMEMetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState)
                setInputConnectionMetaStateAsCurrentMetaKeyKeyListenerState()
                return true
                // Long press physical @ key to swtich chn/eng
            } else if ((!mEnglishOnly || mPredictionOn)
                && translateKeyDown(keyCode, event)
            ) {
                return true
            } else {
                translateKeyDown(keyCode, event)
                super.onKeyDown(keyCode, mKeydownEvent)
            }

            KeyEvent.KEYCODE_SPACE -> {
                //Jeremy move the chi/eng switching to on_KEY_UP '11,6,18
                if (!spaceKeyPress && lastKeyCtrl) { //missing space down event when ctrl-space is pressed
                    this.switchChiEng()
                    return true
                }

                if (hasSpaceProcessed) return true
            }

            else -> {}
        }

        // Update metakeystate of IC maintained by MetaKeyKeyListerner
        //setInputConnectionMetaStateAsCurrentMetaKeyKeyListenerState(); moved to OnKey by jeremy '12,6,13
        if (DEBUG) Log.i(
            TAG, ("OnKeyUp():keyCode:" + keyCode
                    + ";hasCtrlPress:" + hasCtrlPress
                    + ";hasWinPress:" + hasWinPress
                    + ", event.getEventTime() -  event.getDownTime()" + (event.getEventTime() - event.getDownTime())
                    + " call super.onKeyUp()")
        )


        return super.onKeyUp(keyCode, event)
    }


    /**
     * Helper function to commit any text being composed in to the editor.
     */
    private fun commitTyped(ic: InputConnection?) {
        if (DEBUG) Log.i(TAG, "commitTyped()")
        if (selectedCandidate == null) return
        try {
            if ((mComposing!!.length > 0 //denotes composing just finished
                        || !selectedCandidate!!.isComposingCodeRecord()) // commit selected candidate if it is not the composing text. '15,6,4 Jeremy  (like related phrase or English suggestions)
                && !(LIMEUtilities.isUnicodeSurrogate(selectedCandidate!!.getWord())
                        && selectedCandidate!!.isEmojiRecord())
            ) {   //emoji surrogate path bypasses related-phrase flow; CJK Ext-B (non-emoji surrogate) must use main flow for #62

                if (!mEnglishOnly || !selectedCandidate!!.isComposingCodeRecord() || !selectedCandidate!!.isEnglishSuggestionRecord()) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
                    if (selectedCandidate != null && selectedCandidate!!.getWord() != null && !selectedCandidate!!.getWord()!!
                            .isEmpty()
                    ) {
                        val firstMatchedLength = 1

                        //                        if (selectedCandidate.getCode() == null
//                                || selectedCandidate.getCode().isEmpty()) {
//                            firstMatchedLength = 1;
//                        }
                        val wordToCommit = selectedCandidate!!.getWord()

                        //                        if (selectedCandidate != null
//                                && selectedCandidate.getCode() != null
//                                && selectedCandidate.getWord() != null) {
//                            if (selectedCandidate
//                                    .getCode()
//                                    .toLowerCase(Locale.US)
//                                    .equals(selectedCandidate.getWord()
//                                            .toLowerCase(Locale.US))) {
//                                firstMatchedLength = 1;
//
//
//                            }
//                        }
                        if (DEBUG) Log.i(
                            TAG, "commitTyped() committed Length="
                                    + firstMatchedLength
                        )

                        // Do hanConvert before commit
                        // '10, 4, 17 Jeremy
                        if (mLIMEPref!!.getHanCovertOption() == 0) {
                            if (ic != null) ic.commitText(wordToCommit, firstMatchedLength)
                        } else {
                            if (ic != null) ic.commitText(
                                SearchSrv!!.hanConvert(wordToCommit),
                                firstMatchedLength
                            )
                        }
                        if (selectedCandidate!!.isEmojiRecord() && SearchSrv != null) {
                            SearchSrv!!.recordEmojiUsage(wordToCommit)
                            mEmojiCategoryPages = null
                        }

                        val committedCandidate = selectedCandidate ?: return

                        // Art '30,Sep,2011 when show related then clear composing
                        if (currentSoftKeyboard!!.contains("wb") || committedCandidate.isEmojiRecord() || committedCandidate.isChinesePunctuationSymbolRecord()) {
                            clearComposing(true)
                        }


                        // Jeremy '11,7,28 for continuous typing (LD)
                        // Jeremy '12,6,2 get real committed code length from searchserver
                        var composingNotFinish = false
                        //Jeremy '15,6,2 retrieve real code length with selectedCandidate using exact code match stack in search server
                        val committedCodeLength =
                            SearchSrv!!.getRealCodeLength(committedCandidate, mComposing.toString())

                        if (DEBUG) Log.i(
                            TAG,
                            "commitTyped(): committedCodeLength = " + committedCodeLength
                        )

                        if (mComposing!!.length > committedCandidate.getCode()!!.length) {
                            composingNotFinish = true
                        }

                        var shouldUpdateCandidates = false
                        if (composingNotFinish) {
                            if (LDComposingBuffer.isEmpty()) {
                                //starting LD process
                                LDComposingBuffer = mComposing.toString()
                                if (DEBUG) Log.i(
                                    TAG,
                                    "commitTyped():starting LD process, LDBuffer=" + LDComposingBuffer +
                                            ". just committed code= '" + selectedCandidate!!.getCode() + "'"
                                )
                                SearchSrv!!.addLDPhrase(selectedCandidate, false)
                            } else {
                                //Continuous LD process
                                if (DEBUG) Log.i(
                                    TAG,
                                    "commitTyped():Continuous LD process, LDBuffer='" + LDComposingBuffer +
                                            "'. just committed code=" + selectedCandidate!!.getCode()
                                )
                                SearchSrv!!.addLDPhrase(selectedCandidate, false)
                            }
                            mComposing = mComposing!!.delete(0, committedCodeLength)
                            if (DEBUG) Log.i(
                                TAG, "commitTyped(): trimmed mComposing = '" + mComposing + "', " +
                                        "+ mComposing.length = " + mComposing!!.length
                            )

                            if (mComposing.toString() != " ") {
                                if (mComposing.toString().startsWith(" ")) mComposing =
                                    mComposing!!.deleteCharAt(0)
                                if (DEBUG) Log.i(
                                    TAG,
                                    "commitTyped(): new mComposing:'" + mComposing + "'"
                                )
                                if (mComposing!!.length > 0) { //Jeremy '12,7,11 only fetch remaining composing when length >0
                                    if (ic != null && mPredictionOn) ic.setComposingText(
                                        mComposing,
                                        1
                                    )
                                    shouldUpdateCandidates = true
                                }
                            }
                        } else {
                            if (!LDComposingBuffer.isEmpty()) { // && LDComposingBuffer.contains(mComposing.toString())){
                                //Ending continuous LD process (last of LD process)
                                if (DEBUG) Log.i(
                                    TAG,
                                    "commitTyped():Ending LD process, LDBuffer=" + LDComposingBuffer +
                                            ". just committed code=" + selectedCandidate!!.getCode()
                                )
                                LDComposingBuffer = ""
                                SearchSrv!!.addLDPhrase(selectedCandidate, true)
                            } else {
                                //LD process interrupted.
                                if (DEBUG) Log.i(
                                    TAG,
                                    "commitTyped():LD process interrupted, LDBuffer=" + LDComposingBuffer +
                                            ". just committed code=" + selectedCandidate!!.getCode()
                                )
                                LDComposingBuffer = ""
                                SearchSrv!!.addLDPhrase(null, true)
                            }
                        }

                        //Jeremy '13,1,10 do update score and reverse lookup after updateRelatedPhrase to shorten the time user see related candidates after select a candidate.
                        if (shouldUpdateCandidates) {
                            updateCandidates()
                        } else {
                            val committedCandidate = Mapping(selectedCandidate!!)
                            selectedCandidate = null
                            clearComposing(false)
                            updateRelatedPhrase(false)

                            if (committedCandidate.getWord() != null) {
                                SearchSrv!!.learnRelatedPhraseAndUpdateScore(committedCandidate)

                                //do reverse lookup and display notification if required.
                                SearchSrv!!.getCodeListStringFromWord(committedCandidate.getWord())
                            }
                        }
                    } else {
                        if (ic != null) ic.commitText(
                            mComposing,
                            mComposing!!.length
                        )
                    }
                } else {  //English mode or composing code or English run-time suggestion
                    if (ic != null) {
                        ic.commitText(mComposing, mComposing!!.length)
                        if (!mEnglishOnly) clearComposing(false)
                    }
                }
            } else if (LIMEUtilities.isUnicodeSurrogate(selectedCandidate!!.getWord())
                && selectedCandidate!!.isEmojiRecord()
            ) { //Jeremy '15,7,16; narrowed to emoji-only so CJK Ext-B uses main flow (#62)
                ic!!.commitText(selectedCandidate!!.getWord(), 1)
                clearComposing(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in keyboard handling", e)
        }
    }


    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    fun updateShiftKeyState(attr: EditorInfo?) {
        if (DEBUG) Log.i(TAG, "updateShiftKeyState() ")
        val ic = getCurrentInputConnection()
        if (attr != null && mInputView != null && mKeyboardSwitcher!!.isAlphabetMode && ic != null) {
            var caps = 0
            val ei = getCurrentInputEditorInfo()
            if (mAutoCap && ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
                caps = ic.getCursorCapsMode(attr.inputType)
                if (caps == 0 && mEnglishOnly
                    && shouldAutoCapitalizeEnglishText(ic.getTextBeforeCursor(64, 0))
                ) {
                    caps = 1
                }
            }
            mInputView!!.setShifted(mCapsLock || caps != 0)
        } else {
            if (!mCapsLock && mHasShift) {
                mKeyboardSwitcher!!.toggleShift()
                mHasShift = false
            }
        }
    }

    private fun isValidLetter(code: Int): Boolean {
        return Character.isLetter(code)
    }

    private fun isValidDigit(code: Int): Boolean {
        return Character.isDigit(code)
    }

    private fun isValidSymbol(code: Int): Boolean {
        val checkCode = code.toChar().toString()
        // code has to < 256, a ascii character
        return code < 256 && checkCode.matches(".*?[^A-Z]".toRegex())
                && checkCode.matches(".*?[^a-z]".toRegex())
                && checkCode.matches(".*?[^0-9]".toRegex()) && code != 32
    }

    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private fun keyDownUp(keyEventCode: Int, sendToSelf: Boolean) {
        val ic = getCurrentInputConnection()

        val eventTime = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(
            eventTime, eventTime,
            KeyEvent.ACTION_DOWN, keyEventCode, 0, 0, 0, 0,
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
        )
        val upEvent = KeyEvent(
            SystemClock.uptimeMillis(), eventTime,
            KeyEvent.ACTION_UP, keyEventCode, 0, 0, 0, 0,
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
        )
        if (sendToSelf) {  //Jeremy '12,5,23 send to this.onKeyDown and onKeyUp if sendToSelf is true.
            if (!this.onKeyDown(keyEventCode, downEvent) && ic != null) ic.sendKeyEvent(downEvent)
            if (!this.onKeyUp(keyEventCode, upEvent) && ic != null) ic.sendKeyEvent(upEvent)
        } else if (ic != null) {
            ic.sendKeyEvent(downEvent)
            ic.sendKeyEvent(upEvent)
        }
    }


    fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        onKey(primaryCode, keyCodes, 0, 0)
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?, x: Int, y: Int) {
        var primaryCode = primaryCode
        if (DEBUG) Log.i(
            TAG, ("OnKey(): primaryCode:" + primaryCode
                    + " hasShiftPress:" + hasShiftPress)
        )

        hideLimeToast()

        // Modified by Art
        // This is to fixed the CapsLock issue on Physical keyboard
        if (mCapsLock) {
            if (primaryCode >= 97 && primaryCode <= 122) {
                primaryCode -= 32
            }
        }
        // Adjust metaKeyState on printed key pressed.
        if (hasPhysicalKeyPressed) {  //Jeremy '12,6,11 moved from handleCharacter()
            mMetaState = LIMEMetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState)
            setInputConnectionMetaStateAsCurrentMetaKeyKeyListenerState() //Jeremy '12,6,13 moved from OnkeyUP by Jeremy '12,6,13
            if (DEBUG) Log.i(TAG, "onKey(): adjustMetaAfterKeypress()")
        }

        if (mEmojiKeyboardShown && (mEmojiSearchFocused || mEmojiSearchMode) && handleEmojiSearchKey(
                primaryCode
            )
        ) {
            return
        }

        if (mLIMEPref!!.getEnglishPrediction()
            && primaryCode != LIMEBaseKeyboard.KEYCODE_DELETE
        ) {
            // Check if input character not valid English Character then reset
            // temp english string

            if (!Character.isLetter(primaryCode) && mEnglishOnly) {
                //Jeremy '11,6,10. Select english suggestion with shift+123457890

                if (hasPhysicalKeyPressed && (mCandidateView != null && hasCandidatesShown)) { //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
                    if (handleSelkey(primaryCode)) {
                        return
                    }
                    resetTempEnglishWord()
                    if (!hasCtrlPress) clearSuggestions() //Jeremy '12,4,29 moved from resetcandidateBar
                }
            }
        }

        // Handle English/Lime Keyboard switch
        if (!mEnglishFlagShift
            && (primaryCode == LIMEBaseKeyboard.KEYCODE_SHIFT)
        ) {
            mEnglishFlagShift = true
        }
        if (primaryCode == LIMEBaseKeyboard.KEYCODE_DELETE) {
            handleBackspace()
        } else if (primaryCode == LIMEBaseKeyboard.KEYCODE_SHIFT) {
            if (DEBUG) Log.i(TAG, "OnKey():KEYCODE_SHIFT")
            if (!(!hasPhysicalKeyPressed && hasDistinctMultitouch)) handleShift()
        } else if (primaryCode == LIMEBaseKeyboard.KEYCODE_DONE) { // long press on options and shift
            handleClose()
            // Jeremy '12,5,21 process the arrow keys on soft keyboard
        } else if (primaryCode == LIMEBaseKeyboard.KEYCODE_UP) {
            keyDownUp(KeyEvent.KEYCODE_DPAD_UP, hasCandidatesShown)
        } else if (primaryCode == LIMEBaseKeyboard.KEYCODE_DOWN) {
            keyDownUp(KeyEvent.KEYCODE_DPAD_DOWN, hasCandidatesShown)
        } else if (primaryCode == LIMEBaseKeyboard.KEYCODE_RIGHT) {
            keyDownUp(KeyEvent.KEYCODE_DPAD_RIGHT, hasCandidatesShown)
        } else if (primaryCode == LIMEBaseKeyboard.KEYCODE_LEFT) {
            keyDownUp(KeyEvent.KEYCODE_DPAD_LEFT, hasCandidatesShown)
        } else if (primaryCode == LIMEKeyboardView.KEYCODE_OPTIONS) {
            handleOptions()
        } else if (primaryCode == LIMEKeyboardView.KEYCODE_SPACE_LONGPRESS) {
            showIMPicker()
        } else if (primaryCode == KEYCODE_SWITCH_TO_SYMBOL_MODE && mInputView != null) { //->symbol keyboard
            switchKeyboard(primaryCode)
        } else if (primaryCode == KEYCODE_SWITCH_SYMBOL_KEYBOARD && mInputView != null) { //->switch symbols1 keyboards
            switchKeyboard(primaryCode)
        } else if (primaryCode == LIMEKeyboardView.KEYCODE_NEXT_IM) {
            switchToNextActivatedIM(true)
        } else if (primaryCode == LIMEKeyboardView.KEYCODE_PREV_IM) {
            switchToNextActivatedIM(false)
        } else if (primaryCode == LIME.KEYCODE_EMOJI_PANEL) {
            showEmojiKeyboard()
        } else if (primaryCode == LIME.KEYCODE_EMOJI_ABC) {
            hideEmojiKeyboard()
        } else if (primaryCode == KEYCODE_SWITCH_TO_ENGLISH_MODE && mInputView != null) { //chi->eng
            switchKeyboard(primaryCode)
            // Jeremy '11,5,31 Rewrite softkeybaord enter/space and english separator processing.
        } else if (primaryCode == KEYCODE_SWITCH_TO_IM_MODE && mInputView != null) { //eng -> chi
            switchKeyboard(primaryCode)
        } else if (handleEndkeyCommit(primaryCode)) {
            // End-key commit is opt-in per IM table metadata and consumes the trigger key.
        } else if ( //Jeremy '12,7,1 bug fixed on enter not functioning in english mode
            ((primaryCode == MY_KEYCODE_SPACE && !mEnglishOnly && (activeIM != LIME.IM_PHONETIC))
                    || (primaryCode == MY_KEYCODE_SPACE && !mEnglishOnly &&  //activeIM.equals(LIME.IM_PHONETIC) && //redundant
                    (mComposing.toString().endsWith(" ") || mComposing!!.length == 0))
                    || primaryCode == MY_KEYCODE_ENTER)
        ) {
            if (hasCandidatesShown) { //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
                if (!pickHighlightedCandidate()) { //Jeremy '12,5,11 fixed for not sending related.
                    if (mComposing!!.length == 0) hideCandidateView()
                    sendKeyChar(primaryCode.toChar())
                }
            } else {
                sendKeyChar(primaryCode.toChar())
            }
        } else {
            handleCharacter(primaryCode)

            // Art 11, 9, 26 Check if need to auto commit composing
            if (auto_commit > 0 && !mEnglishOnly) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
                if (mComposing != null && mComposing!!.length == auto_commit && currentSoftKeyboard != null && currentSoftKeyboard!!.contains(
                        "phone"
                    )
                ) {
                    val ic = getCurrentInputConnection()
                    commitTyped(ic)
                }
            }
        }
    }

    private fun handleEndkeyCommit(primaryCode: Int): Boolean {
        var endkey: String? = ""
        var imkeys: String? = ""
        if (SearchSrv != null && activeIM != null) {
            endkey = SearchSrv!!.getImConfig(activeIM, LIME.IM_LIME_ENDKEY)
            imkeys = SearchSrv!!.getImConfig(activeIM, IMKEYS_CONFIG)
        }
        if (!isEndkeyCommitKey(
                primaryCode,
                endkey,
                mEnglishOnly,
                mComposing!!.length,
                hasCandidatesShown
            )
        ) {
            return false
        }

        if (isKeyInImkeys(primaryCode, imkeys)) {
            return commitComposingWithAppendedEndkey(primaryCode)
        }

        if (mComposing!!.length > 0 && !commitCurrentEndkeyComposing()) {
            return false
        }

        return commitFreshEndkeyOrRaw(primaryCode)
    }

    private fun commitCurrentEndkeyComposing(): Boolean {
        if (hasCurrentEndkeySelectedCandidate() && pickHighlightedCandidate()) {
            if (mComposing!!.length > 0) {
                clearComposing(false)
            }
            hideCandidateView()
            return true
        }

        if (resolveEndkeySelectedCandidate() != null) {
            commitTyped(getCurrentInputConnection())
            if (mComposing!!.length > 0) {
                clearComposing(false)
            }
            hideCandidateView()
            return true
        }

        if (mComposing!!.length == 0) {
            hideCandidateView()
        }
        return false
    }

    private fun commitComposingWithAppendedEndkey(primaryCode: Int): Boolean {
        val code = primaryCode.toChar().toString()
        mComposing!!.append(code)
        val ic = getCurrentInputConnection()
        if (ic != null && mPredictionOn) {
            ic.setComposingText(mComposing, 1)
        }
        return commitResolvedEndkeyComposing()
    }

    private fun commitFreshEndkeyOrRaw(primaryCode: Int): Boolean {
        val code = primaryCode.toChar().toString()
        mComposing!!.append(code)
        val ic = getCurrentInputConnection()
        if (ic != null && mPredictionOn) {
            ic.setComposingText(mComposing, 1)
        }
        if (commitResolvedEndkeyComposing()) {
            return true
        }
        clearComposing(false)
        if (ic != null) {
            ic.commitText(code, 1)
        }
        finishComposing()
        return true
    }

    private fun commitResolvedEndkeyComposing(): Boolean {
        if (resolveEndkeySelectedCandidate() == null) {
            return false
        }
        commitTyped(getCurrentInputConnection())
        if (mComposing!!.length > 0) {
            clearComposing(false)
        }
        hideCandidateView()
        return true
    }

    private fun resolveEndkeySelectedCandidate(): Mapping? {
        if (hasCurrentEndkeySelectedCandidate()) {
            return selectedCandidate
        }
        if (SearchSrv == null || mComposing!!.length == 0) {
            return null
        }
        if (queryThread != null && queryThread!!.isAlive()) {
            queryThread!!.interrupt()
        }
        try {
            val candidates = SearchSrv!!.getMappingByCode(
                mComposing.toString(),
                !hasPhysicalKeyPressed, false
            )
            if (candidates.isEmpty()) {
                return null
            }
            mCandidateList = LinkedList<Mapping?>(candidates)
            selectedCandidate = Companion.endkeyCommitCandidateForSuggestions(mCandidateList!!)
            hasMappingList = selectedCandidate != null
            hasCandidatesShown = selectedCandidate != null
            return selectedCandidate
        } catch (e: RemoteException) {
            Log.e(TAG, "Error resolving end-key candidate", e)
            return null
        }
    }

    private fun hasCurrentEndkeySelectedCandidate(): Boolean {
        return selectedCandidate != null && !selectedCandidate!!.isComposingCodeRecord() && selectedCandidate!!.getCode() != null && mComposing.toString() == selectedCandidate!!.getCode()
    }

    private fun showEmojiKeyboard() {
        mEmojiSourceWasEnglish = mEnglishOnly
        mEmojiKeyboardShown = true
        mEmojiCategoryIndex = 0
        mEmojiSearchFocused = false
        mEmojiSearchQuery.setLength(0)
        if (mEmojiSearchField != null) {
            mEmojiSearchField!!.setText("")
        }
        updateEmojiAbcButtonLabel()
        clearComposing(true)
        mInputCandidateStripVisibilityBeforeEmoji = this.inputCandidateStripVisibility
        hideCandidateView()
        this.inputCandidateStripVisibility = View.GONE
        if (this.emojiKeyboardViewForTesting != null) {
            emojiKeyboardViewForTesting!!.setVisibility(View.VISIBLE)
        }
        renderEmojiContent("")
        enforceEmojiKeyboardVisibility()
    }

    private fun hideEmojiKeyboard() {
        mEmojiKeyboardShown = false
        mEmojiSearchFocused = false
        mEmojiSearchMode = false
        clearEmojiSearchCandidates()
        if (this.emojiKeyboardViewForTesting != null) {
            emojiKeyboardViewForTesting!!.setVisibility(View.GONE)
        }
        if (mInputView != null) {
            mInputView!!.setVisibility(View.VISIBLE)
            restoreEmojiSourceKeyboard()
            mInputView!!.invalidateAllKeys()
        }
        this.inputCandidateStripVisibility = mInputCandidateStripVisibilityBeforeEmoji
        refreshCandidateInputContainer()
    }

    private fun resetEmojiKeyboardState() {
        val wasEmojiKeyboardShown = mEmojiKeyboardShown
        mEmojiKeyboardShown = false
        mEmojiSearchFocused = false
        mEmojiSearchMode = false
        mEmojiSearchQuery.setLength(0)
        clearEmojiSearchCandidates()
        if (this.emojiKeyboardViewForTesting != null) {
            emojiKeyboardViewForTesting!!.setVisibility(View.GONE)
        }
        if (mInputView != null) {
            mInputView!!.setVisibility(View.VISIBLE)
            mInputView!!.invalidateAllKeys()
        }
        if (wasEmojiKeyboardShown) {
            this.inputCandidateStripVisibility = mInputCandidateStripVisibilityBeforeEmoji
        }
    }

    private fun setupEmojiKeyboardView() {
        if (this.emojiKeyboardViewForTesting !is FrameLayout) return

        val container = this.emojiKeyboardViewForTesting as FrameLayout
        container.removeAllViews()
        container.setBackgroundColor(Color.TRANSPARENT)
        mEmojiRoot = null
        mEmojiScroll = null
        mEmojiPages = null
        mEmojiBottomBar = null
        mEmojiCategoryBar = null
        mEmojiSearchField = null
        mEmojiAbcButton = null
        this.isEmojiContentRenderedForTesting = false

        mEmojiRoot = LinearLayout(mThemeContext)
        mEmojiRoot!!.setOrientation(LinearLayout.VERTICAL)
        mEmojiRoot!!.setPadding(
            dp(EMOJI_PANEL_HORIZONTAL_PADDING_DP.toFloat()),
            dp(EMOJI_PANEL_VERTICAL_PADDING_DP.toFloat()),
            dp(EMOJI_PANEL_HORIZONTAL_PADDING_DP.toFloat()),
            dp(EMOJI_PANEL_VERTICAL_PADDING_DP.toFloat())
        )
        mEmojiRoot!!.setMinimumHeight(dp(280f))
        container.addView(
            mEmojiRoot, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        mEmojiSearchField = TextView(mThemeContext)
        mEmojiSearchField!!.setTextSize(17f)
        applyEmojiSearchFieldStyle()
        updateEmojiSearchText()
        mEmojiSearchField!!.setCompoundDrawablePadding(dp(8f))
        mEmojiSearchField!!.setPadding(dp(14f), 0, dp(14f), 0)
        mEmojiSearchField!!.setGravity(Gravity.CENTER_VERTICAL)
        mEmojiSearchField!!.setOnClickListener(View.OnClickListener { v: View? -> enterEmojiSearchMode() })
        mEmojiSearchField!!.setOnTouchListener(OnTouchListener { v: View?, event: MotionEvent? ->
            if (event!!.getAction() == MotionEvent.ACTION_UP) {
                enterEmojiSearchMode()
            }
            true
        })
        mEmojiRoot!!.addView(
            mEmojiSearchField, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(EMOJI_SEARCH_FIELD_HEIGHT_DP.toFloat())
            )
        )

        mEmojiScroll = HorizontalScrollView(mThemeContext)
        mEmojiScroll!!.setFillViewport(false)
        mEmojiScroll!!.setHorizontalScrollBarEnabled(false)
        mEmojiScroll!!.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS)
        mEmojiPages = LinearLayout(mThemeContext)
        mEmojiPages!!.setOrientation(LinearLayout.HORIZONTAL)
        mEmojiScroll!!.addView(
            mEmojiPages, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mEmojiScroll!!.setOnScrollChangeListener(View.OnScrollChangeListener { v: View?, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int ->
                if (!mEmojiSearchMode) {
                    updateEmojiCategoryHighlight(categoryIndexForEmojiScroll(scrollX))
                }
            })
        }
        val gridParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0
        )
        gridParams.weight = 1f
        gridParams.topMargin = dp(8f)
        mEmojiRoot!!.addView(mEmojiScroll, gridParams)

        val emojiKeyboardSizeScale = this.emojiKeyboardSizeScale
        val emojiCategoryBottomBarHeight =
            dp(scaleDp(EMOJI_CATEGORY_BOTTOM_BAR_HEIGHT_DP, emojiKeyboardSizeScale).toFloat())
        val emojiCategoryTabHeight =
            dp(scaleDp(EMOJI_CATEGORY_TAB_HEIGHT_DP, emojiKeyboardSizeScale).toFloat())

        mEmojiBottomBar = LinearLayout(mThemeContext)
        mEmojiBottomBar!!.setGravity(Gravity.CENTER_VERTICAL)
        mEmojiBottomBar!!.setOrientation(LinearLayout.HORIZONTAL)
        mEmojiRoot!!.addView(
            mEmojiBottomBar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, emojiCategoryBottomBarHeight
            )
        )

        val emojiSideControlWidth = dp(emojiSideControlWidthDp(emojiKeyboardSizeScale).toFloat())
        val emojiModeControlGlyphSize: Int = emojiModeControlGlyphSize(emojiKeyboardSizeScale)
        val emojiBackspaceGlyphSize: Int = emojiBackspaceGlyphSize(emojiKeyboardSizeScale)

        val abc = createEmojiControl("ABC", emojiModeControlGlyphSize)
        mEmojiAbcButton = abc
        abc.setOnClickListener(View.OnClickListener { v: View? -> hideEmojiKeyboard() })
        mEmojiBottomBar!!.addView(
            abc, LinearLayout.LayoutParams(
                emojiSideControlWidth, emojiCategoryTabHeight
            )
        )

        val emojiCategoryScroll = HorizontalScrollView(mThemeContext)
        emojiCategoryScroll.setFillViewport(false)
        emojiCategoryScroll.setHorizontalScrollBarEnabled(false)
        emojiCategoryScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS)

        mEmojiCategoryBar = LinearLayout(mThemeContext)
        mEmojiCategoryBar!!.setGravity(Gravity.CENTER_VERTICAL)
        mEmojiCategoryBar!!.setOrientation(LinearLayout.HORIZONTAL)
        emojiCategoryScroll.addView(
            mEmojiCategoryBar, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                emojiCategoryBottomBarHeight
            )
        )
        val categoryParams = LinearLayout.LayoutParams(0, emojiCategoryBottomBarHeight)
        categoryParams.weight = 1f
        mEmojiBottomBar!!.addView(emojiCategoryScroll, categoryParams)

        val backspace = createEmojiControl("⌫", emojiBackspaceGlyphSize)
        backspace.setOnClickListener(View.OnClickListener { v: View? -> handleEmojiBackspace() })
        mEmojiBottomBar!!.addView(
            backspace, LinearLayout.LayoutParams(
                emojiSideControlWidth, emojiCategoryTabHeight
            )
        )

        emojiKeyboardViewForTesting!!.setVisibility(if (mEmojiKeyboardShown) View.VISIBLE else View.GONE)
    }

    private fun updateEmojiAbcButtonLabel() {
        if (mEmojiAbcButton != null) {
            mEmojiAbcButton!!.setText(if (mEmojiSourceWasEnglish) "ABC" else getString(R.string.emoji_abc_ime_label))
        }
    }

    private fun restoreEmojiSourceKeyboard() {
        if (mEmojiSourceWasEnglish) {
            if (!mEnglishOnly && mInputView != null) {
                switchKeyboard(KEYCODE_SWITCH_TO_ENGLISH_MODE)
            }
        } else if (mEnglishOnly && mInputView != null) {
            switchKeyboard(KEYCODE_SWITCH_TO_IM_MODE)
        }
    }

    private fun renderEmojiContent(query: String?) {
        if (mEmojiPages == null || mEmojiCategoryBar == null) return
        this.isEmojiContentRenderedForTesting = true

        val normalizedQuery = if (query == null) "" else query.trim { it <= ' ' }.lowercase()
        mEmojiSearchMode = mEmojiSearchFocused || normalizedQuery.length > 0
        this.inputCandidateStripVisibility =
            emojiSearchInputCandidateStripVisibility(
                mEmojiKeyboardShown,
                mEmojiSearchMode
            )
        mEmojiPages!!.removeAllViews()
        val searchPanelHeight = emojiSearchPanelHeight()

        if (this.emojiKeyboardViewForTesting != null) {
            val emojiParams = emojiKeyboardViewForTesting!!.getLayoutParams()
            if (emojiParams != null) {
                emojiParams.height =
                    if (mEmojiSearchMode) searchPanelHeight else ViewGroup.LayoutParams.WRAP_CONTENT
                emojiKeyboardViewForTesting!!.setLayoutParams(emojiParams)
            }
        }
        if (mEmojiRoot != null) {
            val horizontalPadding = if (mEmojiSearchMode) 0 else dp(
                EMOJI_PANEL_HORIZONTAL_PADDING_DP.toFloat()
            )
            val verticalPaddingBottom = if (mEmojiSearchMode) 0 else dp(
                EMOJI_PANEL_VERTICAL_PADDING_DP.toFloat()
            )
            mEmojiRoot!!.setPadding(
                horizontalPadding,
                dp(EMOJI_PANEL_VERTICAL_PADDING_DP.toFloat()),
                horizontalPadding,
                verticalPaddingBottom
            )
            mEmojiRoot!!.setMinimumHeight(if (mEmojiSearchMode) searchPanelHeight else dp(280f))
        }
        if (mEmojiSearchField != null) {
            val searchParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(EMOJI_SEARCH_FIELD_HEIGHT_DP.toFloat())
            )
            if (mEmojiSearchMode) {
                val horizontalMargin = dp(EMOJI_PANEL_HORIZONTAL_PADDING_DP.toFloat())
                searchParams.setMargins(horizontalMargin, 0, horizontalMargin, 0)
            }
            mEmojiSearchField!!.setLayoutParams(searchParams)
        }
        if (mEmojiScroll != null) {
            val scrollParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0
            )
            scrollParams.weight = (if (mEmojiSearchMode) 0 else 1).toFloat()
            scrollParams.topMargin = if (mEmojiSearchMode) 0 else dp(8f)
            mEmojiScroll!!.setLayoutParams(scrollParams)
            mEmojiScroll!!.setVisibility(if (mEmojiSearchMode) View.GONE else View.VISIBLE)
        }
        if (mEmojiBottomBar != null) {
            mEmojiBottomBar!!.setVisibility(if (mEmojiSearchFocused) View.GONE else View.VISIBLE)
        }
        enforceEmojiKeyboardVisibility()

        if (mEmojiSearchMode) {
            val matches = findEmojiSearchResults(normalizedQuery)
            val emojiCandidates = emojiSearchCandidateMappings(matches)
            showEmojiSearchCandidatesInInputStrip(emojiCandidates)
            updateEmojiCategoryHighlight(-1)
        } else {
            clearEmojiSearchCandidates()
            val pageWidth = this.emojiPageWidth
            val pages =
                this.emojiPanelPages
            mEmojiCategoryStartOffsets = IntArray(this.emojiCategoryCount)
            var nextOffset = 0
            for (i in pages.indices) {
                if (i < mEmojiCategoryStartOffsets!!.size) {
                    mEmojiCategoryStartOffsets!![i] = nextOffset
                }
                nextOffset += addEmojiSection(pages.get(i)!!.toTypedArray<String?>(), pageWidth, i)
            }
            updateEmojiCategoryHighlight(-1)
            if (mEmojiScroll != null) {
                val offset = getEmojiCategoryStartOffset(mEmojiCategoryIndex)
                mEmojiScroll!!.post(Runnable { mEmojiScroll!!.scrollTo(offset, 0) })
            }
        }
    }

    val emojiPageViewCountForTesting: Int
        get() = if (mEmojiPages == null) 0 else mEmojiPages!!.getChildCount()

    val emojiCategoryTabCountForTesting: Int
        get() = if (mEmojiCategoryBar == null) 0 else mEmojiCategoryBar!!.getChildCount()

    private fun enterEmojiSearchMode() {
        mEmojiSearchFocused = true
        mEmojiSearchQuery.setLength(0)
        setEmojiSearchKeyboard(emojiSearchInitialEnglishOnly(mEmojiSourceWasEnglish))
        updateEmojiSearchText()
        enforceEmojiKeyboardVisibility()
        refreshCandidateInputContainer()
    }

    private fun exitEmojiSearchToKeyboard() {
        mEmojiSearchFocused = false
        mEmojiSearchMode = false
        mEmojiSearchQuery.setLength(0)
        if (mEmojiSearchField != null) {
            mEmojiSearchField!!.setText("")
        }
        hideEmojiKeyboard()
    }

    private fun showEmojiSearchCandidatesInInputStrip(emojiCandidates: MutableList<Mapping?>?) {
        val candidates = LinkedList<Mapping?>()
        if (emojiCandidates != null) {
            candidates.addAll(emojiCandidates)
        }
        mCandidateList = candidates
        selectedCandidate = null
        hasCandidatesShown = !candidates.isEmpty()
        hasMappingList = !candidates.isEmpty()
        this.inputCandidateStripVisibility = View.VISIBLE
        if (mCandidateInInputView != null) {
            mCandidateInInputView!!.setVisibility(View.VISIBLE)
        }
        if (mCandidateViewInInputView != null) {
            mCandidateViewInInputView!!.setVisibility(View.VISIBLE)
        }
        if (mCandidateView != null) {
            mCandidateView!!.setSuggestions(candidates, false)
        }
        showCandidateView()
        if (mCandidateInInputView != null) {
            mCandidateInInputView!!.requestLayout()
            mCandidateInInputView!!.updateCandidateViewWidthConstraint()
            mCandidateInInputView!!.post(Runnable {
                if (mEmojiKeyboardShown && mEmojiSearchMode) {
                    this.inputCandidateStripVisibility = View.VISIBLE
                    mCandidateInInputView!!.setVisibility(View.VISIBLE)
                    if (mCandidateViewInInputView != null) {
                        mCandidateViewInInputView!!.setVisibility(View.VISIBLE)
                    }
                    mCandidateInInputView!!.requestLayout()
                    mCandidateInInputView!!.updateCandidateViewWidthConstraint()
                }
            })
        }
    }

    private fun clearEmojiSearchCandidates() {
        if (mCandidateView != null) {
            mCandidateView!!.hideCandidatePopup()
            mCandidateView!!.setSuggestions(null, false)
        }
        if (mCandidateList != null) {
            mCandidateList!!.clear()
        }
        selectedCandidate = null
        hasCandidatesShown = false
        hasMappingList = false
        if (mCandidateInInputView != null) {
            mCandidateInInputView!!.requestLayout()
            mCandidateInInputView!!.updateCandidateViewWidthConstraint()
        }
    }

    private fun setEmojiSearchKeyboard(englishOnly: Boolean) {
        mEnglishOnly = englishOnly
        if (mKeyboardSwitcher != null) {
            mKeyboardSwitcher!!.setKeyboardMode(
                activeIM,
                if (englishOnly) LIMEKeyboardSwitcher.MODE_TEXT else LIMEKeyboardSwitcher.MODE_IM,
                emojiSearchImeOptions(mImeOptions), !englishOnly, false, false
            )
        }
        if (mInputView != null) {
            mInputView!!.invalidateAllKeys()
        }
    }

    private fun handleEmojiSearchKey(primaryCode: Int): Boolean {
        if (primaryCode == LIMEBaseKeyboard.KEYCODE_DELETE) {
            handleEmojiBackspace()
            return true
        }
        if (shouldExitEmojiSearchToKeyboard(primaryCode)) {
            exitEmojiSearchToKeyboard()
            return true
        }
        if (isEmojiSearchKeyboardModeKey(primaryCode)) {
            setEmojiSearchKeyboard(
                resolveEmojiSearchEnglishOnlyForModeKey(
                    primaryCode,
                    mEnglishOnly
                )
            )
            return true
        }
        if (shouldEmojiSearchConsumePrintableKey(primaryCode, mEnglishOnly)) {
            mEmojiSearchQuery.append(primaryCode.toChar().lowercaseChar())
            updateEmojiSearchText()
            return true
        }
        return mEnglishOnly
    }

    private fun appendPickedCandidateToEmojiSearch(candidate: Mapping?): Boolean {
        if (candidate == null || candidate.getWord() == null || candidate.getWord()!!.isEmpty()) {
            return false
        }
        if (!shouldAppendPickedCandidateToEmojiSearch(
                mEmojiKeyboardShown, mEmojiSearchMode,
                candidate.isEmojiRecord(), candidate.isComposingCodeRecord()
            )
        ) {
            return false
        }
        mEmojiSearchQuery.append(candidate.getWord())
        selectedCandidate = null
        clearComposing(false)
        updateEmojiSearchText()
        return true
    }

    private fun handleEmojiBackspace() {
        if (mEmojiSearchFocused && mEmojiSearchQuery.length > 0) {
            mEmojiSearchQuery.deleteCharAt(mEmojiSearchQuery.length - 1)
            updateEmojiSearchText()
        } else {
            handleBackspace()
        }
    }

    private fun updateEmojiSearchText() {
        if (mEmojiSearchField != null) {
            val colors = currentEmojiPanelColors()
            if (mEmojiSearchQuery.length == 0 && !mEmojiSearchFocused) {
                mEmojiSearchField!!.setText(getString(R.string.emoji_search_hint))
                mEmojiSearchField!!.setTextColor(colors.searchHint)
            } else {
                mEmojiSearchField!!.setText(mEmojiSearchQuery.toString())
                mEmojiSearchField!!.setTextColor(colors.searchText)
            }
        }
        renderEmojiContent(mEmojiSearchQuery.toString())
    }

    private fun applyEmojiSearchFieldStyle() {
        if (mEmojiSearchField == null) return
        val colors = currentEmojiPanelColors()
        var searchIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_search)
        if (searchIcon != null) {
            searchIcon = DrawableCompat.wrap(searchIcon.mutate())
            DrawableCompat.setTint(searchIcon, colors.searchIcon)
        }
        mEmojiSearchField!!.setCompoundDrawablesWithIntrinsicBounds(searchIcon, null, null, null)
        mEmojiSearchField!!.setBackground(makeRoundRect(colors.searchBackground, dp(26f)))
    }

    private fun addEmojiSection(emojis: Array<String?>?, pageWidth: Int, categoryIndex: Int): Int {
        val page = GridLayout(mThemeContext)
        val emojiKeyboardSizeScale = this.emojiKeyboardSizeScale
        val keySize =
            max(dp(scaleDp(42, emojiKeyboardSizeScale).toFloat()), pageWidth / EMOJI_GRID_COLUMNS)
        val emojiGlyphSize: Int = emojiPanelGlyphSize(emojiKeyboardSizeScale)
        val emojiCellHeight = dp(scaleDp(50, emojiKeyboardSizeScale).toFloat())
        val realCount = if (emojis == null) 0 else emojis.size
        var columns = max(1, ceil(realCount.toDouble() / EMOJI_GRID_ROWS.toDouble()).toInt())
        if (categoryIndex == 0) {
            columns = max(EMOJI_GRID_COLUMNS, columns)
        }
        val visibleCellCount = max(realCount, columns * EMOJI_GRID_ROWS)
        page.setColumnCount(columns)
        page.setRowCount(EMOJI_GRID_ROWS)
        page.setPadding(0, 0, 0, 0)
        for (i in 0..<visibleCellCount) {
            val isRealEmoji = i < realCount
            val key = createEmojiControl(if (isRealEmoji) emojis!![i] else "•", emojiGlyphSize)
            if (isRealEmoji) {
                key.setOnClickListener(View.OnClickListener { v: View? ->
                    commitEmoji(
                        (v as TextView).getText().toString()
                    )
                })
            } else {
                key.setTextColor(Color.TRANSPARENT)
                key.setAlpha(0.01f)
                key.setOnClickListener(null)
            }
            val column: Int = i / EMOJI_GRID_ROWS
            val row: Int = i % EMOJI_GRID_ROWS
            val keyParams = GridLayout.LayoutParams(
                GridLayout.spec(row),
                GridLayout.spec(column)
            )
            keyParams.width = keySize
            keyParams.height = emojiCellHeight
            keyParams.setMargins(0, dp(1f), 0, dp(1f))
            page.addView(key, keyParams)
        }
        val contentWidth =
            if (categoryIndex == 0) max(pageWidth, keySize * columns) else keySize * columns
        mEmojiPages!!.addView(
            page,
            LinearLayout.LayoutParams(contentWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        return contentWidth
    }

    private fun updateEmojiCategoryHighlight(categoryIndex: Int) {
        if (mEmojiCategoryBar == null) return
        if (categoryIndex >= 0) {
            mEmojiCategoryIndex = max(0, min(categoryIndex, this.emojiCategoryCount - 1))
        }

        if (mEmojiCategoryBar!!.getChildCount() != this.emojiCategoryCount) {
            mEmojiCategoryBar!!.removeAllViews()
            val emojiKeyboardSizeScale = this.emojiKeyboardSizeScale
            val tabWidth = dp(emojiCategoryTabWidthDp(emojiKeyboardSizeScale).toFloat())
            val tabHeight =
                dp(scaleDp(EMOJI_CATEGORY_TAB_HEIGHT_DP, emojiKeyboardSizeScale).toFloat())
            for (i in 0..<this.emojiCategoryCount) {
                val index = i
                val tab = createEmojiCategoryIcon(index)
                tab.setOnClickListener(View.OnClickListener { v: View? ->
                    if (mEmojiSearchField != null && mEmojiSearchField!!.length() > 0) {
                        mEmojiSearchField!!.setText("")
                    }
                    mEmojiSearchMode = false
                    mEmojiCategoryIndex = index
                    renderEmojiContent("")
                    if (mEmojiScroll != null) {
                        mEmojiScroll!!.post(Runnable {
                            mEmojiScroll!!.smoothScrollTo(
                                getEmojiCategoryStartOffset(index), 0
                            )
                        })
                    }
                    updateEmojiCategoryHighlight(-1)
                })
                mEmojiCategoryBar!!.addView(tab, LinearLayout.LayoutParams(tabWidth, tabHeight))
            }
        }
        for (i in 0..<mEmojiCategoryBar!!.getChildCount()) {
            val tab = mEmojiCategoryBar!!.getChildAt(i)
            tab.setBackground(
                makeRoundRect(
                    if (!mEmojiSearchMode && i == mEmojiCategoryIndex)
                        currentEmojiPanelColors().categoryHighlight
                    else
                        Color.TRANSPARENT, dp(18f)
                )
            )
            tab.invalidate()
        }
    }

    private val emojiCategoryCount: Int
        get() = FALLBACK_EMOJI_CATEGORIES.size

    private val emojiKeyboardSizeScale: Float
        get() {
            var scale = 1.0f
            if (mLIMEPref != null) {
                scale = mLIMEPref!!.getKeyboardSize()
            }
            return max(0.8f, min(1.2f, scale))
        }

    private fun getEmojiCategoryStartPage(categoryIndex: Int): Int {
        if (mEmojiCategoryPageStarts == null || mEmojiCategoryPageStarts!!.size == 0) {
            return max(0, categoryIndex)
        }
        val safeIndex = max(0, min(categoryIndex, mEmojiCategoryPageStarts!!.size - 1))
        return mEmojiCategoryPageStarts!![safeIndex]
    }

    private fun getEmojiCategoryStartOffset(categoryIndex: Int): Int {
        if (mEmojiCategoryStartOffsets == null || mEmojiCategoryStartOffsets!!.size == 0) {
            return getEmojiCategoryStartPage(categoryIndex) * this.emojiPageWidth
        }
        val safeIndex = max(0, min(categoryIndex, mEmojiCategoryStartOffsets!!.size - 1))
        return mEmojiCategoryStartOffsets!![safeIndex]
    }

    private fun categoryIndexForEmojiScroll(scrollX: Int): Int {
        if (mEmojiCategoryStartOffsets == null || mEmojiCategoryStartOffsets!!.size == 0) {
            val pageWidth = this.emojiPageWidth
            return if (pageWidth > 0) Math.round(scrollX.toFloat() / pageWidth.toFloat()) else 0
        }
        var active = 0
        for (i in mEmojiCategoryStartOffsets!!.indices) {
            if (mEmojiCategoryStartOffsets!![i] <= scrollX + 1) {
                active = i
            } else {
                break
            }
        }
        return active
    }

    private fun createEmojiCategoryIcon(categoryIndex: Int): View {
        val view = EmojiCategoryIconButton(
            mThemeContext,
            categoryIndex,
            dp(emojiCategoryGlyphSizeDp(this.emojiKeyboardSizeScale).toFloat())
        )
        view.setClickable(true)
        return view
    }

    private val emojiPageWidth: Int
        get() {
            val viewWidth =
                if (this.emojiKeyboardViewForTesting == null) 0 else emojiKeyboardViewForTesting!!.getWidth()
            val fallbackWidth = getResources().getDisplayMetrics().widthPixels
            return max(
                dp(320f),
                (if (viewWidth > 0) viewWidth else fallbackWidth) - dp(24f)
            )
        }

    private fun findEmojiSearchResults(query: String?): MutableList<String?> {
        if (SearchSrv != null && query != null && query.length > 0) {
            val english = SearchSrv!!.searchEmoji(query, LimeDB.EmojiLocale.EN, 80)
            val traditional = SearchSrv!!.searchEmoji(query, LimeDB.EmojiLocale.TW, 80)
            val dbMatches: MutableList<String?> = ArrayList<String?>()
            if (english != null) {
                for (mapping in english) {
                    if (mapping != null && mapping.getWord() != null && !dbMatches.contains(mapping.getWord())) {
                        dbMatches.add(mapping.getWord())
                    }
                }
            }
            if (traditional != null) {
                for (mapping in traditional) {
                    if (mapping != null && mapping.getWord() != null && !dbMatches.contains(mapping.getWord())) {
                        dbMatches.add(mapping.getWord())
                    }
                }
            }
            if (!dbMatches.isEmpty()) {
                return dbMatches
            }
        }

        val matches: MutableList<String?> = ArrayList<String?>()
        for (category in FALLBACK_EMOJI_CATEGORIES) {
            for (emoji in category!!) {
                if (emoji!!.contains(query!!) || emojiKeywordMatches(emoji, query)) {
                    if (!matches.contains(emoji)) {
                        matches.add(emoji)
                    }
                }
            }
        }
        return matches
    }

    private fun emojiSearchCandidateMappings(emojis: MutableList<String?>?): MutableList<Mapping?> {
        val candidates: MutableList<Mapping?> = LinkedList<Mapping?>()
        if (emojis == null) return candidates
        for (emoji in emojis) {
            if (emoji == null || emoji.isEmpty()) continue
            val mapping = Mapping()
            mapping.setCode("")
            mapping.setWord(emoji)
            mapping.setEmojiRecord()
            candidates.add(mapping)
        }
        return candidates
    }

    private fun emojiSearchPanelHeight(): Int {
        return (dp(EMOJI_PANEL_VERTICAL_PADDING_DP.toFloat())
                + dp(EMOJI_SEARCH_FIELD_HEIGHT_DP.toFloat()))
    }

    private val emojiPanelPages: MutableList<MutableList<String?>?>
        get() {
            if (mEmojiCategoryPages == null) {
                val categories =
                    loadEmojiCategories()
                mEmojiCategoryPages = paginateEmojiCategories(categories)
            }
            return mEmojiCategoryPages!!
        }

    private fun loadEmojiCategories(): MutableList<MutableList<String?>?> {
        var categories: MutableList<MutableList<String?>?>? = null
        if (SearchSrv != null) {
            try {
                categories = SearchSrv!!.loadEmojiCategoryPages()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading DB-backed emoji categories", e)
            }
        }
        if (categories == null || categories.size < this.emojiCategoryCount) {
            categories = ArrayList<MutableList<String?>?>()
        } else {
            categories = copyEmojiStringPages(categories)
        }

        for (i in 0..<this.emojiCategoryCount) {
            val fallback = emojiArrayToList(FALLBACK_EMOJI_CATEGORIES[i])
            if (i >= categories.size) {
                categories.add(fallback)
            } else if (i == 0) {
                categories.set(
                    i,
                    mergeEmojiRecentSeedQueue(categories.get(i), fallback, EMOJI_PAGE_CAPACITY)
                )
            } else if (categories.get(i) == null || categories.get(i)!!.isEmpty()) {
                categories.set(i, fallback)
            }
        }
        while (categories.size > this.emojiCategoryCount) {
            categories.removeAt(categories.size - 1)
        }
        return categories
    }

    private fun mergeEmojiRecentSeedQueue(
        recent: MutableList<String?>?,
        fallback: MutableList<String?>?,
        limit: Int
    ): MutableList<String?> {
        val safeLimit = max(1, limit)
        val merged: MutableList<String?> = ArrayList<String?>()
        if (recent != null) {
            for (emoji in recent) {
                addEmojiSeedIfRoom(merged, emoji, safeLimit)
            }
        }
        if (fallback != null) {
            for (emoji in fallback) {
                addEmojiSeedIfRoom(merged, emoji, safeLimit)
            }
        }
        return merged
    }

    private fun addEmojiSeedIfRoom(merged: MutableList<String?>?, emoji: String?, limit: Int) {
        if (merged == null || emoji == null || emoji.isEmpty() || merged.contains(emoji)) {
            return
        }
        if (merged.size < max(1, limit)) {
            merged.add(emoji)
        }
    }

    private fun paginateEmojiCategories(categories: MutableList<MutableList<String?>?>): MutableList<MutableList<String?>?> {
        val pages: MutableList<MutableList<String?>?> = ArrayList<MutableList<String?>?>()
        mEmojiPageCategoryIndexes = ArrayList<Int?>()
        mEmojiCategoryPageStarts = IntArray(this.emojiCategoryCount)

        for (categoryIndex in 0..<this.emojiCategoryCount) {
            mEmojiCategoryPageStarts!![categoryIndex] = pages.size
            var items = if (categoryIndex < categories.size) categories.get(categoryIndex) else null
            if (items == null || items.isEmpty()) {
                items = emojiArrayToList(FALLBACK_EMOJI_CATEGORIES[categoryIndex])
            }
            pages.add(ArrayList<String?>(items))
            mEmojiPageCategoryIndexes.add(categoryIndex)
        }
        return pages
    }

    private fun copyEmojiStringPages(source: MutableList<MutableList<String?>?>): MutableList<MutableList<String?>?> {
        val copy: MutableList<MutableList<String?>?> = ArrayList<MutableList<String?>?>()
        for (page in source) {
            copy.add(if (page == null) ArrayList<String?>() else ArrayList<String?>(page))
        }
        return copy
    }

    private fun emojiArrayToList(source: Array<String?>?): MutableList<String?> {
        val values: MutableList<String?> = ArrayList<String?>()
        if (source == null) {
            return values
        }
        for (value in source) {
            if (value != null && !value.isEmpty() && !values.contains(value)) {
                values.add(value)
            }
        }
        return values
    }

    private fun emojiKeywordMatches(emoji: String, query: String): Boolean {
        if (query.length == 0) return true
        if ("😂🤣😆😅😭😢".contains(emoji)) return startsWithAny(
            query,
            "cry",
            "cr",
            "laugh",
            "lol",
            "tear"
        )
        if ("❤️🧡💛💚💙💜🖤🤍🤎💕💞💓💗💖😍🥰😘".contains(emoji)) return startsWithAny(
            query,
            "heart",
            "love",
            "lov",
            "kiss"
        )
        if ("🐶🐱🐭🐹🐰🦊🐻🐼🐨🐯🦁🐮🐷🐸🐵".contains(emoji)) return startsWithAny(
            query,
            "animal",
            "dog",
            "cat",
            "bear",
            "monkey"
        )
        if ("🍎🍐🍊🍋🍌🍉🍇🍓🍔🍟".contains(emoji)) return startsWithAny(
            query,
            "food",
            "fruit",
            "apple",
            "burger"
        )
        if ("🚗🚕🚙🚌🚎✈🚀🚁🚢🚉🚇🚆".contains(emoji)) return startsWithAny(
            query,
            "car",
            "travel",
            "train",
            "plane"
        )
        if ("⚽🏀🏈⚾🎾🏐".contains(emoji)) return startsWithAny(query, "sport", "ball", "soccer")
        if ("🇹🇼🇯🇵🇰🇷🇺🇸🇨🇦🇬🇧🇫🇷🇩🇪🇮🇹🇪🇸".contains(emoji)) return startsWithAny(query, "flag", "country")
        return false
    }

    private fun startsWithAny(query: String, vararg keywords: String): Boolean {
        for (keyword in keywords) {
            if (keyword.startsWith(query) || query.startsWith(keyword)) {
                return true
            }
        }
        return false
    }

    private fun commitEmoji(emoji: String?) {
        val ic = getCurrentInputConnection()
        if (ic != null) {
            ic.commitText(emoji, 1)
        }
        if (SearchSrv != null) {
            SearchSrv!!.recordEmojiUsage(emoji)
        }
        mEmojiCategoryPages = null
    }

    private fun createEmojiControl(text: String?, textSize: Int): TextView {
        val view = TextView(mThemeContext)
        view.setText(text)
        view.setTextSize(textSize.toFloat())
        view.setTextColor(currentEmojiPanelColors().iconText)
        view.setGravity(Gravity.CENTER)
        view.setIncludeFontPadding(false)
        view.setClickable(true)
        return view
    }

    private inner class EmojiCategoryIconButton(
        context: Context?,
        private val categoryIndex: Int,
        private val iconSizePx: Int
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            paint.setColor(currentEmojiPanelColors().iconText)
            paint.setStrokeCap(Paint.Cap.ROUND)
            paint.setStrokeJoin(Paint.Join.ROUND)
            setWillNotDraw(false)
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val size = min(iconSizePx, min(getWidth(), getHeight())).toFloat()
            val cx = getWidth() / 2f
            val cy = getHeight() / 2f
            paint.setStrokeWidth(max(2.2f, dp(2f).toFloat()))
            paint.setStyle(Paint.Style.STROKE)

            when (categoryIndex) {
                0 -> drawRecentIcon(canvas, cx, cy, size)
                1 -> drawSmileIcon(canvas, cx, cy, size)
                2 -> drawPeopleIcon(canvas, cx, cy, size)
                3 -> drawAnimalIcon(canvas, cx, cy, size)
                4 -> drawAppleIcon(canvas, cx, cy, size)
                5 -> drawCarIcon(canvas, cx, cy, size)
                6 -> drawBallIcon(canvas, cx, cy, size)
                7 -> drawBulbIcon(canvas, cx, cy, size)
                8 -> drawHeartIcon(canvas, cx, cy, size)
                9 -> drawFlagIcon(canvas, cx, cy, size)
                else -> {}
            }
        }

        fun drawRecentIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
            val r = size * 0.48f
            canvas.drawCircle(cx, cy, r, paint)
            canvas.drawLine(cx, cy, cx, cy - r * 0.62f, paint)
            canvas.drawLine(cx, cy, cx - r * 0.54f, cy, paint)
        }

        fun drawSmileIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
            val r = size * 0.45f
            canvas.drawCircle(cx, cy, r, paint)
            paint.setStyle(Paint.Style.FILL)
            canvas.drawCircle(cx - r * 0.34f, cy - r * 0.16f, dp(1.5f).toFloat(), paint)
            canvas.drawCircle(cx + r * 0.34f, cy - r * 0.16f, dp(1.5f).toFloat(), paint)
            paint.setStyle(Paint.Style.STROKE)
            val smile = RectF(cx - r * 0.42f, cy - r * 0.02f, cx + r * 0.42f, cy + r * 0.52f)
            canvas.drawArc(smile, 18f, 144f, false, paint)
        }

        fun drawPeopleIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
            val headR = size * 0.18f
            canvas.drawCircle(cx, cy - size * 0.22f, headR, paint)
            canvas.drawArc(
                RectF(
                    cx - size * 0.34f, cy - size * 0.02f,
                    cx + size * 0.34f, cy + size * 0.62f
                ), 205f, 130f, false, paint
            )
            canvas.drawCircle(cx + size * 0.28f, cy - size * 0.06f, headR * 0.74f, paint)
            canvas.drawArc(
                RectF(
                    cx + size * 0.08f, cy + size * 0.12f,
                    cx + size * 0.5f, cy + size * 0.58f
                ), 210f, 120f, false, paint
            )
        }

        fun drawAnimalIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
            val r = size * 0.31f
            canvas.drawCircle(cx - r * 0.85f, cy - r * 0.95f, r * 0.45f, paint)
            canvas.drawCircle(cx + r * 0.85f, cy - r * 0.95f, r * 0.45f, paint)
            canvas.drawCircle(cx, cy - r * 0.2f, r, paint)
            paint.setStyle(Paint.Style.FILL)
            canvas.drawCircle(cx - r * 0.34f, cy - r * 0.32f, dp(1.35f).toFloat(), paint)
            canvas.drawCircle(cx + r * 0.34f, cy - r * 0.32f, dp(1.35f).toFloat(), paint)
            canvas.drawOval(
                RectF(cx - r * 0.22f, cy - r * 0.02f, cx + r * 0.22f, cy + r * 0.24f),
                paint
            )
            paint.setStyle(Paint.Style.STROKE)
            canvas.drawArc(
                RectF(cx - r * 0.5f, cy + r * 0.04f, cx, cy + r * 0.54f),
                0f,
                70f,
                false,
                paint
            )
            canvas.drawArc(
                RectF(cx, cy + r * 0.04f, cx + r * 0.5f, cy + r * 0.54f),
                110f,
                70f,
                false,
                paint
            )
        }

        fun drawAppleIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
            val r = size * 0.37f
            val apple = Path()
            apple.moveTo(cx, cy - r * 0.72f)
            apple.cubicTo(
                cx - r * 0.95f,
                cy - r * 0.98f,
                cx - r * 1.15f,
                cy + r * 0.1f,
                cx - r * 0.62f,
                cy + r * 0.82f
            )
            apple.cubicTo(
                cx - r * 0.24f,
                cy + r * 1.25f,
                cx - r * 0.02f,
                cy + r * 0.92f,
                cx,
                cy + r * 0.92f
            )
            apple.cubicTo(
                cx + r * 0.02f,
                cy + r * 0.92f,
                cx + r * 0.24f,
                cy + r * 1.25f,
                cx + r * 0.62f,
                cy + r * 0.82f
            )
            apple.cubicTo(
                cx + r * 1.15f,
                cy + r * 0.1f,
                cx + r * 0.95f,
                cy - r * 0.98f,
                cx,
                cy - r * 0.72f
            )
            canvas.drawPath(apple, paint)
            canvas.drawLine(cx, cy - r * 0.8f, cx + r * 0.14f, cy - r * 1.22f, paint)
            canvas.drawArc(
                RectF(cx + r * 0.1f, cy - r * 1.42f, cx + r * 0.8f, cy - r * 0.9f),
                165f,
                150f,
                false,
                paint
            )
        }

        fun drawBallIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
            val r = size * 0.45f
            canvas.drawCircle(cx, cy, r, paint)
            canvas.drawLine(cx - r * 0.72f, cy - r * 0.28f, cx + r * 0.72f, cy + r * 0.28f, paint)
            canvas.drawLine(cx - r * 0.72f, cy + r * 0.28f, cx + r * 0.72f, cy - r * 0.28f, paint)
            canvas.drawArc(
                RectF(cx - r * 0.72f, cy - r, cx + r * 0.72f, cy + r),
                73f,
                214f,
                false,
                paint
            )
        }

        fun drawCarIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
            val w = size * 0.95f
            val h = size * 0.45f
            val car = Path()
            car.moveTo(cx - w * 0.48f, cy + h * 0.1f)
            car.lineTo(cx - w * 0.34f, cy - h * 0.28f)
            car.lineTo(cx - w * 0.14f, cy - h * 0.45f)
            car.lineTo(cx + w * 0.28f, cy - h * 0.45f)
            car.lineTo(cx + w * 0.48f, cy - h * 0.04f)
            car.lineTo(cx + w * 0.48f, cy + h * 0.28f)
            car.lineTo(cx - w * 0.48f, cy + h * 0.28f)
            car.close()
            canvas.drawPath(car, paint)
            canvas.drawCircle(cx - w * 0.26f, cy + h * 0.34f, h * 0.2f, paint)
            canvas.drawCircle(cx + w * 0.28f, cy + h * 0.34f, h * 0.2f, paint)
        }

        fun drawBulbIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
            val r = size * 0.35f
            canvas.drawArc(
                RectF(cx - r, cy - r * 1.15f, cx + r, cy + r * 0.85f),
                210f,
                120f,
                false,
                paint
            )
            canvas.drawLine(cx - r * 0.46f, cy + r * 0.48f, cx - r * 0.28f, cy + r * 1.05f, paint)
            canvas.drawLine(cx + r * 0.46f, cy + r * 0.48f, cx + r * 0.28f, cy + r * 1.05f, paint)
            canvas.drawLine(cx - r * 0.34f, cy + r * 1.05f, cx + r * 0.34f, cy + r * 1.05f, paint)
            canvas.drawLine(cx - r * 0.24f, cy + r * 1.3f, cx + r * 0.24f, cy + r * 1.3f, paint)
        }

        fun drawHeartIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
            val s = size * 0.5f
            val heart = Path()
            heart.moveTo(cx, cy + s * 0.72f)
            heart.cubicTo(
                cx - s * 1.1f,
                cy - s * 0.08f,
                cx - s * 0.98f,
                cy - s * 0.84f,
                cx - s * 0.42f,
                cy - s * 0.84f
            )
            heart.cubicTo(cx - s * 0.14f, cy - s * 0.84f, cx, cy - s * 0.6f, cx, cy - s * 0.42f)
            heart.cubicTo(
                cx,
                cy - s * 0.6f,
                cx + s * 0.14f,
                cy - s * 0.84f,
                cx + s * 0.42f,
                cy - s * 0.84f
            )
            heart.cubicTo(
                cx + s * 0.98f,
                cy - s * 0.84f,
                cx + s * 1.1f,
                cy - s * 0.08f,
                cx,
                cy + s * 0.72f
            )
            canvas.drawPath(heart, paint)
        }

        fun drawFlagIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
            val h = size * 0.9f
            val left = cx - size * 0.36f
            canvas.drawLine(left, cy - h * 0.5f, left, cy + h * 0.5f, paint)
            val flag = Path()
            flag.moveTo(left, cy - h * 0.48f)
            flag.cubicTo(
                cx - size * 0.02f,
                cy - h * 0.66f,
                cx + size * 0.22f,
                cy - h * 0.26f,
                cx + size * 0.48f,
                cy - h * 0.42f
            )
            flag.lineTo(cx + size * 0.48f, cy + h * 0.1f)
            flag.cubicTo(
                cx + size * 0.22f,
                cy + h * 0.26f,
                cx - size * 0.02f,
                cy - h * 0.14f,
                left,
                cy + h * 0.04f
            )
            flag.close()
            canvas.drawPath(flag, paint)
        }
    }

    private fun makeRoundRect(color: Int, radius: Int): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.setColor(color)
        drawable.setCornerRadius(radius.toFloat())
        return drawable
    }

    private fun dp(value: Float): Int {
        return (value * getResources().getDisplayMetrics().density + 0.5f).toInt()
    }

    private var inputCandidateStripVisibility: Int
        get() {
            val strip = inputCandidateStrip()
            if (strip != null) {
                return strip.getVisibility()
            }
            return View.VISIBLE
        }
        private set(visibility) {
            val strip = inputCandidateStrip()
            if (strip != null) {
                strip.setVisibility(visibility)
            }
        }

    private fun inputCandidateStrip(): View? {
        if (mCandidateInInputView == null) return null
        return mCandidateInInputView!!.findViewById<View?>(R.id.input_candidate_strip)
    }

    private fun enforceEmojiKeyboardVisibility() {
        if (!mEmojiKeyboardShown || mInputView == null) return
        this.inputCandidateStripVisibility =
            emojiSearchInputCandidateStripVisibility(
                mEmojiKeyboardShown,
                mEmojiSearchMode
            )
        if (mEmojiSearchFocused) {
            setEmojiSearchKeyboard(mEnglishOnly)
            mInputView!!.setVisibility(View.VISIBLE)
        } else {
            mInputView!!.setVisibility(View.GONE)
        }
        mInputView!!.invalidateAllKeys()
        if (mInputView!!.getHandler() != null) {
            mInputView!!.post(Runnable {
                if (mEmojiKeyboardShown) {
                    this.inputCandidateStripVisibility =
                        emojiSearchInputCandidateStripVisibility(
                            mEmojiKeyboardShown,
                            mEmojiSearchMode
                        )
                    mInputView!!.setVisibility(if (mEmojiSearchFocused) View.VISIBLE else View.GONE)
                    mInputView!!.invalidateAllKeys()
                }
            })
        }
    }

    private fun refreshCandidateInputContainer() {
        if (mCandidateInInputView == null) return
        mCandidateInInputView!!.post(Runnable {
            mCandidateInInputView!!.requestLayout()
            mCandidateInInputView!!.updateCandidateViewWidthConstraint()
            mCandidateInInputView!!.invalidate()
        })
    }


    private var mOptionsDialog: AlertDialog? = null

    /**
     * Add by Jeremy '10, 3, 24 for options menu in soft keyboard
     */
    private fun handleOptions() {
        if (DEBUG) Log.i(TAG, "handleOptions()")

        // Check if Looper is available (not in test environment)
        if (Looper.myLooper() == null) {
            Log.w(TAG, "handleOptions(): No Looper available, skipping dialog creation")
            return
        }

        val builder: AlertDialog.Builder?

        builder = createDialogBuilder()


        builder.setCancelable(true)
        builder.setIcon(R.drawable.logo)
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.setTitle(getResources().getString(R.string.ime_name))

        val itemSettings: CharSequence = getString(R.string.lime_setting_preference)
        val reverseLookupOptions =
            this.activeReverseLookupOptions
        val itemReverseLookup: CharSequence = getString(
            R.string.keyboard_menu_reverse_lookup,
            getReverseLookupLabel(mLIMEPref!!.getReverseLookupTable(activeIM), reverseLookupOptions)
        )
        val hanConvert: CharSequence = getString(R.string.han_convert_option_list)

        val itemSwitchIM: CharSequence = getString(R.string.keyboard_list)
        val itemKeepassKeyboard: CharSequence = getString(R.string.keepass_keyboard_mode)
        val itemSwitchSytemIM: CharSequence = getString(R.string.input_method)

        val dm = getResources().getDisplayMetrics()
        val displayWidth = dm.widthPixels
        val displayHeight = dm.heightPixels
        val isLandScape = displayWidth > displayHeight

        var itemSplitKeyboard: CharSequence = getString(R.string.split_keyboard)
        if ((mSplitKeyboard == LIMEBaseKeyboard.SPLIT_KEYBOARD_LANDSCAPD_ONLY && isLandScape)
            || mSplitKeyboard == LIMEBaseKeyboard.SPLIT_KEYBOARD_ALWAYS
        ) itemSplitKeyboard = getString(R.string.merge_keyboard)


        val itemVoiceInput: CharSequence = getString(R.string.voice_input)
        val options: MutableList<CharSequence?> = ArrayList<CharSequence?>()
        val actions: MutableList<Int?> = ArrayList<Int?>()

        //Jeremy '12,5,27 do not show split/merge keyboard option if in landscape mode and show arrow keys is on
        val hasSplitOption = !(isLandScape && mShowArrowKeys > 0)

        options.add(itemSettings)
        actions.add(ACTION_SETTINGS)
        options.add(itemReverseLookup)
        actions.add(ACTION_REVERSE_LOOKUP)
        options.add(hanConvert)
        actions.add(ACTION_HANCONVERT)
        options.add(itemSwitchIM)
        actions.add(ACTION_KEYBOARD)
        if (isKeepassImeEnabled()) {
            options.add(itemKeepassKeyboard)
            actions.add(ACTION_KEEPASS_KEYBOARD)
        }
        options.add(itemSwitchSytemIM)
        actions.add(ACTION_METHOD)
        if (hasSplitOption) {
            options.add(itemSplitKeyboard)
            actions.add(ACTION_SPLIT_KEYBOARD)
        }
        options.add(itemVoiceInput)
        actions.add(ACTION_VOICEINPUT)


        builder.setItems(
            options.toTypedArray<CharSequence?>(),
            DialogInterface.OnClickListener { di: DialogInterface?, position: Int ->
                di!!.dismiss()
                when (actions.get(position)) {
                    ACTION_SETTINGS -> launchPreference()
                    ACTION_REVERSE_LOOKUP -> showReverseLookupPicker()
                    ACTION_HANCONVERT -> showHanConvertPicker()
                    ACTION_KEYBOARD -> showIMPicker()
                    ACTION_KEEPASS_KEYBOARD -> {
                        DiagnosticLog.record(this, TAG, "handleOptions() selected KeePass")
                        startKeepassImeFlow()
                    }
                    ACTION_METHOD -> showSystemInputMethodPicker("options-menu")

                    ACTION_SPLIT_KEYBOARD -> {
                        if (mSplitKeyboard == LIMEBaseKeyboard.SPLIT_KEYBOARD_NEVER) {
                            if (isLandScape) mLIMEPref!!.setSplitKeyboard(LIMEBaseKeyboard.SPLIT_KEYBOARD_LANDSCAPD_ONLY)
                            else mLIMEPref!!.setSplitKeyboard(LIMEBaseKeyboard.SPLIT_KEYBOARD_ALWAYS)
                        } else if (mSplitKeyboard == LIMEBaseKeyboard.SPLIT_KEYBOARD_ALWAYS) {
                            if (isLandScape) mLIMEPref!!.setSplitKeyboard(LIMEBaseKeyboard.SPLIT_KEYBOARD_NEVER)
                            else mLIMEPref!!.setSplitKeyboard(LIMEBaseKeyboard.SPLIT_KEYBOARD_LANDSCAPD_ONLY)
                        } else { // LIMEBaseKeyboard.SPLIT_KEYBOARD_LANDSCAPD_ONLY
                            if (isLandScape) mLIMEPref!!.setSplitKeyboard(LIMEBaseKeyboard.SPLIT_KEYBOARD_NEVER)
                            else mLIMEPref!!.setSplitKeyboard(LIMEBaseKeyboard.SPLIT_KEYBOARD_ALWAYS)
                        }

                        invalidateStartupConfigSnapshot()
                        handleClose()
                        mKeyboardSwitcher!!.resetKeyboards(true)
                    }

                    ACTION_VOICEINPUT -> startVoiceInput()
                }
            })

        mOptionsDialog = builder.create()
        configureImeAttachedDialogWindow(mOptionsDialog!!, "handleOptions")
        try {
            mOptionsDialog!!.show()
            DiagnosticLog.record(this, TAG, "handleOptions() dialog shown")
        } catch (e: RuntimeException) {
            Log.e(TAG, "handleOptions(): failed to show dialog", e)
            DiagnosticLog.recordThrowable(this, "$TAG handleOptions() failed", e)
        }
    }

    private val activeReverseLookupOptions: MutableList<ReverseLookupOption?>
        get() {
            buildActivatedIMList()
            return LIMEPreferenceManager.buildReverseLookupOptions(
                activatedIMList,
                activatedIMFullNameList,
                getString(R.string.reverse_lookup_none)
            )
        }

    private fun getReverseLookupLabel(
        value: String?,
        options: MutableList<ReverseLookupOption?>?
    ): String? {
        val noneLabel = getString(R.string.reverse_lookup_none)
        val labels = LIMEPreferenceManager.reverseLookupLabels(options, noneLabel)
        val values = LIMEPreferenceManager.reverseLookupValues(options, noneLabel)
        var i = 0
        while (i < values.size && i < labels.size) {
            if (values[i] == value) {
                return labels[i]
            }
            i++
        }
        return if (labels.size > 0) labels[0] else "none"
    }

    private fun showReverseLookupPicker() {
        val options =
            this.activeReverseLookupOptions
        val noneLabel = getString(R.string.reverse_lookup_none)
        val labels = LIMEPreferenceManager.reverseLookupLabels(options, noneLabel)
        val values = LIMEPreferenceManager.reverseLookupValues(options, noneLabel)
        val current = mLIMEPref!!.getReverseLookupTable(activeIM)
        var selected = 0
        for (i in values.indices) {
            if (values[i] == current) {
                selected = i
                break
            }
        }

        val builder = createDialogBuilder()
        builder.setCancelable(true)
        builder.setIcon(R.drawable.logo)
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.setTitle(getString(R.string.im_reverse_lookup_screen_title))
        builder.setSingleChoiceItems(
            labels,
            selected,
            DialogInterface.OnClickListener { di: DialogInterface?, which: Int ->
                di!!.dismiss()
                if (which >= 0 && which < values.size) {
                    mLIMEPref!!.setReverseLookupTable(activeIM, values[which])
                    showLimeToast(getString(R.string.keyboard_menu_reverse_lookup, labels[which]))
                }
            })

        val dialog = builder.create()
        val window: Window? = checkNotNull(dialog.getWindow())
        val lp = window!!.getAttributes()
        lp.token = mInputView!!.getWindowToken()
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        window.setAttributes(lp)
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        dialog.show()
    }

    private fun launchPreference() {
        handleClose()
        val intent = Intent()
        /*if(android.os.Build.VERSION.SDK_INT < 11)  //Jeremy '12,4,30 Add for deprecated preferenceActivity after API 11 (HC)
            intent.setClass(LIMEService.this, LIMEPreference.class);
	    else*/
        intent.setClass(this@LIMEService, LIMEPreference::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }


    private fun switchToNextActivatedIM(forward: Boolean) { // forward: true, next IM; false prev. IM
        if (DEBUG) Log.i(TAG, "switchToNextActivatedIM()")
        buildActivatedIMList()
        var i: Int
        var activeIMName: CharSequence? = ""
        i = 0
        while (i < activatedIMList!!.size) {
            if (activeIM == activatedIMList!!.get(i)) {
                if (i == activatedIMList!!.size - 1 && forward) {
                    activeIM = activatedIMList!!.get(0)
                    activeIMName = activatedIMFullNameList!!.get(0)
                } else if (i == 0 && !forward) {
                    activeIM = activatedIMList!!.get(activatedIMList!!.size - 1)
                    activeIMName = activatedIMFullNameList!!.get(activatedIMList!!.size - 1)
                } else {
                    activeIM = activatedIMList!!.get(i + (if (forward) 1 else -1))
                    activeIMName = activatedIMFullNameList!!.get(i + (if (forward) 1 else -1))
                }
                break
            }
            i++
        }
        mLIMEPref!!.setActiveIM(activeIM)
        invalidateStartupConfigSnapshot()
        //Jeremy '12,4,21 force clear when switch to next keyboard
        clearComposing(false)
        // cancel candidate view if it's shown
        mEnglishOnly = false
        mLIMEPref!!.setLanguageMode(false)
        //initialKeyboard();
        initialIMKeyboard()

        showLimeToast(activeIMName)

        refreshStartupConfigSnapshotIfNeeded(false)
        applyStartupConfigSnapshotToKeyboardSwitcher()

        // Update keyboard xml information
        if (mKeyboardSwitcher != null) {
            currentSoftKeyboard = mKeyboardSwitcher!!.getImConfigKeyboard(activeIM)
        }
    }

    private fun buildActivatedIMList() {
        // Use LIME constants instead of resources for better testability

        val fullNames = LIME.IM_FULL_NAMES
        val shortNames = LIME.IM_SHORT_NAMES
        val IMs = LIME.IM_CODES
        if (SearchSrv != null) {
            val imConfigList = SearchSrv!!.getImConfigList(null, LIME.IM_FULL_NAME)
            activatedIMFullNameList!!.clear()
            activatedIMList!!.clear()
            activatedIMShortNameList!!.clear()

            val activeState = StringBuilder()
            for (im in imConfigList) {
                if (im == null || im.code == null) continue
                if ("emoji" == im.code) continue
                if (im.isDisable) continue

                val index = indexOfIMCode(IMs, im.code)
                if (index < 0) continue
                if (activeState.length > 0) activeState.append(";")
                activeState.append(index)
                activatedIMFullNameList!!.add(im.desc)
                activatedIMShortNameList!!.add(shortNames[index])
                activatedIMList!!.add(IMs[index]!!)
            }

            val liveState = activeState.toString()
            if (liveState != mIMActivatedState) {
                mIMActivatedState = liveState
                mLIMEPref!!.setIMActivatedState(liveState)
            }
            ensureActiveIMInActivatedList()
            return
        }

        val pIMActiveState = mLIMEPref!!.getIMActivatedState() ?: ""

        if (pIMActiveState.trim { it <= ' ' }.isEmpty()) {
            activatedIMFullNameList!!.clear()
            activatedIMList!!.clear()
            activatedIMShortNameList!!.clear()
            return
        }

        if (!(!mIMActivatedState.isEmpty() && mIMActivatedState == pIMActiveState)) {
            mIMActivatedState = pIMActiveState

            val activeState =
                pIMActiveState.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            activatedIMFullNameList!!.clear()
            activatedIMList!!.clear()
            activatedIMShortNameList!!.clear()

            for (value in activeState) {
                if (value.isEmpty()) continue
                val index = value.toInt()

                if (index < fullNames.size) {
                    activatedIMFullNameList!!.add(fullNames[index])
                    activatedIMShortNameList!!.add(shortNames[index])
                    activatedIMList!!.add(IMs[index]!!)
                    if (DEBUG) Log.i(
                        TAG, ("buildActivatedIMList()(): buildActivatedIMList()[" + index + "] = "
                                + IMs[index] + " ;" + shortNames[index])
                    )
                } else {
                    break
                }
            }
        }
        ensureActiveIMInActivatedList()
    }

    private fun indexOfIMCode(imCodes: Array<String?>, code: String?): Int {
        for (i in imCodes.indices) {
            if (imCodes[i] == code) {
                return i
            }
        }
        return -1
    }

    private fun ensureActiveIMInActivatedList() {
        if (DEBUG) Log.i(TAG, "current active IM:" + activeIM)
        var matched = false
        for (i in activatedIMList!!.indices) {
            if (activeIM == activatedIMList!!.get(i)) {
                if (DEBUG) Log.i(
                    TAG,
                    "buildActivatedIMList(): activatedIM[" + i + "] matches current active IM: " + activeIM
                )
                matched = true
                break
            }
        }
        if (!matched && SearchSrv != null && !activatedIMList!!.isEmpty()) {
            // if the selected keyboard is not in the active keyboard list.
            // set the keyboard to the first active keyboard
            try {
                val corrected = activatedIMList!!.get(0)
                if (corrected != activeIM) {
                    activeIM = corrected
                    // Persist the correction so the next onStartInput() (which reloads
                    // activeIM from preferences) does not revert to a stale/default IM
                    // that has no loaded keyboard config and falls back to English.
                    // This is what makes the first installed IM activate on a fresh
                    // install instead of showing the English keyboard.
                    if (mLIMEPref != null) {
                        mLIMEPref!!.setActiveIM(activeIM)
                    }
                }
            } catch (e: IndexOutOfBoundsException) {
                Log.e(TAG, "IndexOutOfBoundsException getting active IM", e)
            }
        }
    }

    /**
     * Add by Jeremy '11,9,17 for han convert (traditional <-> simplified) options
     */
    private fun showHanConvertPicker() {
        val builder: AlertDialog.Builder?

        builder = createDialogBuilder()

        builder.setCancelable(true)
        builder.setIcon(R.drawable.logo)
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.setTitle(getResources().getString(R.string.han_convert_option_list))
        val items: Array<CharSequence> =
            getResources().getStringArray(R.array.han_convert_options).map { it as CharSequence }.toTypedArray()
        builder.setSingleChoiceItems(
            items, mLIMEPref!!.getHanCovertOption(),
            DialogInterface.OnClickListener { di: DialogInterface?, position: Int ->
                di!!.dismiss()
                handleHanConvertSelection(position)
            })

        mOptionsDialog = builder.create()
        val window = mOptionsDialog!!.getWindow()
        if (window != null) {
            val lp = window.getAttributes()
            // Use InputView window token since we always use embedded candidate view now
            if (mInputView != null) {
                lp.token = mInputView!!.getWindowToken()
            }
            lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            window.setAttributes(lp)
            window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
        mOptionsDialog!!.show()
    }

    private fun handleHanConvertSelection(position: Int) {
        mLIMEPref!!.setHanCovertOption(position)
    }

    private fun configureImeAttachedDialogWindow(dialog: AlertDialog, source: String) {
        val window = dialog.getWindow()
        if (window == null) {
            DiagnosticLog.record(this, TAG, "$source dialog window is null before show()")
            return
        }
        val lp = window.getAttributes()
        val token = currentImeDialogToken()
        DiagnosticLog.record(
            this,
            TAG,
            "$source dialog token=${if (token != null) "available" else "null"}, " +
                    "inputView=${mInputView?.javaClass?.simpleName ?: "null"}, " +
                    "container=${mCandidateInInputView?.javaClass?.simpleName ?: "null"}"
        )
        if (token != null) {
            lp.token = token
        }
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        window.setAttributes(lp)
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }

    private fun currentImeDialogToken(): IBinder? {
        val inputViewToken = mInputView?.getWindowToken()
        if (inputViewToken != null) return inputViewToken
        val containerToken = mCandidateInInputView?.getWindowToken()
        if (containerToken != null) return containerToken
        return getWindow()?.getWindow()?.getDecorView()?.getWindowToken()
    }

    private fun showSystemInputMethodPicker(source: String) {
        try {
            DiagnosticLog.record(this, TAG, "showSystemInputMethodPicker($source)")
            (Objects.requireNonNull<Any?>(
                getSystemService(
                    INPUT_METHOD_SERVICE
                )
            ) as InputMethodManager).showInputMethodPicker()
        } catch (e: RuntimeException) {
            Log.e(TAG, "showSystemInputMethodPicker($source) failed", e)
            DiagnosticLog.recordThrowable(this, "$TAG showSystemInputMethodPicker($source) failed", e)
        }
    }

    /**
     * Add by Jeremy '10, 3, 24 for IM picker menu in options menu
     * renamed to showIMPicker from showKeybaordPicer to avoid confusion '12,3,40
     */
    private fun showIMPicker() {
        if (DEBUG) Log.i(TAG, "showIMPicker()")
        DiagnosticLog.record(this, TAG, "showIMPicker() start")
        buildActivatedIMList()
        val showKeepass = isKeepassImeEnabled()
        val limeItemCount = activatedIMFullNameList!!.size
        DiagnosticLog.record(
            this,
            TAG,
            "showIMPicker() limeItemCount=$limeItemCount, keepassEnabled=$showKeepass, activeIM=$activeIM"
        )
        if (limeItemCount == 0 && !showKeepass) {
            DiagnosticLog.record(this, TAG, "showIMPicker() no LIME/KeePass items; ignoring")
            return
        }
        val builder: AlertDialog.Builder?

        builder = createDialogBuilder()

        builder.setCancelable(true)
        builder.setIcon(R.drawable.logo)
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.setTitle(getResources().getString(R.string.keyboard_list))

        val hasCurrentFallback = showKeepass && limeItemCount == 0
        val items = arrayOfNulls<CharSequence>(if (hasCurrentFallback) 1 else limeItemCount) // =
        // getResources().getStringArray(R.array.keyboard);
        var curKB = -1
        if (hasCurrentFallback) {
            items[0] = getString(R.string.keepass_current_input_method)
            curKB = 0
        } else {
            for (i in activatedIMFullNameList!!.indices) {
                items[i] = activatedIMFullNameList!!.get(i)
                if (activeIM == activatedIMList!!.get(i)) curKB = i
            }
        }
        val displayItems =
            if (showKeepass) {
                arrayOfNulls<CharSequence>(items.size + 1).also {
                    System.arraycopy(items, 0, it, 0, items.size)
                    it[items.size] = getString(R.string.keepass_keyboard_mode)
                }
            } else {
                items
            }

        builder.setSingleChoiceItems(
            displayItems, curKB,
            DialogInterface.OnClickListener { di: DialogInterface?, position: Int ->
                di!!.dismiss()
                if (showKeepass && position == items.size) {
                    DiagnosticLog.record(this, TAG, "showIMPicker() selected KeePass")
                    startKeepassImeFlow()
                } else if (hasCurrentFallback && position == 0) {
                    DiagnosticLog.record(this, TAG, "showIMPicker() selected current input method fallback")
                } else if (position in items.indices) {
                    DiagnosticLog.record(this, TAG, "showIMPicker() selected LIME item index=$position")
                    handleIMSelection(position)
                } else {
                    DiagnosticLog.record(this, TAG, "showIMPicker() ignored invalid index=$position")
                }
            })

        mOptionsDialog = builder.create()
        configureImeAttachedDialogWindow(mOptionsDialog!!, "showIMPicker")
        try {
            mOptionsDialog!!.show()
            DiagnosticLog.record(this, TAG, "showIMPicker() dialog shown")
            DiagnosticLog.exportToDownloadsAsync(this, "lime-service-show-im-picker")
        } catch (e: RuntimeException) {
            Log.e(TAG, "showIMPicker(): failed to show dialog", e)
            DiagnosticLog.recordThrowable(this, "$TAG showIMPicker() failed", e)
            showSystemInputMethodPicker("showIMPicker-exception")
        }
    }

    private fun handleIMSelection(position: Int) {
        if (DEBUG) Log.i(TAG, "handleIMSelection() position = " + position)

        activeIM = activatedIMList!!.get(position)
        val activeIMName: CharSequence? = activatedIMFullNameList!!.get(position)

        mLIMEPref!!.setActiveIM(activeIM)
        invalidateStartupConfigSnapshot()


        //spe.putString("keyboard_list", keyboardSelection);
        //spe.commit();


        //Jeremy '12,4,21 foce clear when switch to selected keybaord
        if (!mEnglishOnly) clearComposing(true)

        mEnglishOnly =
            false //Jeremy '12,5,24 force to switch to Chinese mode if it's choosing in english mode.
        initialIMKeyboard()

        refreshStartupConfigSnapshotIfNeeded(false)
        applyStartupConfigSnapshotToKeyboardSwitcher()
        if (mKeyboardSwitcher != null) {
            currentSoftKeyboard = mKeyboardSwitcher!!.getImConfigKeyboard(activeIM)
        }

        showLimeToast(activeIMName)
    }

    private fun isKeepassImeEnabled(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(KEY_KEEPASS_ENABLED, false)
    }

    private fun startKeepassImeFlow() {
        if (!isKeepassImeEnabled()) {
            DiagnosticLog.record(this, TAG, "startKeepassImeFlow() ignored; KeePass disabled")
            return
        }
        DiagnosticLog.record(this, TAG, "startKeepassImeFlow() unlocked=${KeepassAutofillLock.isUnlocked(this)}")
        if (KeepassAutofillLock.isUnlocked(this)) {
            startKeepassSelection()
        } else {
            startKeepassUnlock()
        }
    }

    private fun startKeepassUnlock() {
        try {
            DiagnosticLog.record(this, TAG, "startKeepassUnlock() launching activity")
            startActivity(
                Intent(this, LimeKeepassImeUnlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "startKeepassUnlock(): unlock activity not found", e)
            DiagnosticLog.recordThrowable(this, "$TAG startKeepassUnlock() failed", e)
            showLimeToast(getString(R.string.keepass_keyboard_locked))
        }
    }

    private fun startKeepassSelection() {
        try {
            DiagnosticLog.record(this, TAG, "startKeepassSelection() launching activity")
            startActivity(
                Intent(this, LimeKeepassImeSelectActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "startKeepassSelection(): select activity not found", e)
            DiagnosticLog.recordThrowable(this, "$TAG startKeepassSelection() failed", e)
            showLimeToast(getString(R.string.keepass_open_database_failed, e.message.orEmpty()))
        }
    }

    private fun showKeepassFieldPanel() {
        if (!KeepassAutofillLock.isUnlocked(this)) {
            handleKeepassLocked(KeepassAutofillLock.lockReasonAuto)
            showLimeToast(getString(R.string.keepass_keyboard_locked))
            return
        }
        val container = mCandidateInInputView ?: return
        val context = mThemeContext ?: this
        removeKeepassFieldPanel()
        val insertIndex = detachKeyboardForKeepassPanel(container)

        val panel =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
                setBackgroundColor(keyboardBackgroundColorForCurrentTheme)
            }

        TextView(context).apply {
            text = mKeepassSelectedTitle.ifBlank { getString(R.string.keepass_entry_default_title) }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(keepassKeyboardTextColor())
            setPadding(0, 0, 0, dp(4f))
            panel.addView(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        addKeepassFieldGrid(
            panel,
            listOf(
                R.string.keepass_entry_username to mKeepassSelectedUsername,
                R.string.keepass_entry_password to mKeepassSelectedPassword,
                R.string.keepass_entry_url to mKeepassSelectedUrl,
                R.string.keepass_entry_notes to mKeepassSelectedNotes
            )
        )

        addKeepassControlRow(
            panel,
            listOf(
                R.string.keepass_keyboard_switch_entry to {
                    removeKeepassFieldPanel()
                    restoreNormalKeyboardView()
                    clearKeepassSelection()
                    cancelKeepassAutoLock()
                    startKeepassImeFlow()
                },
                R.string.keepass_keyboard_next_field to {
                    sendTabKey()
                },
                R.string.keepass_keyboard_backspace to {
                    handleBackspace()
                }
            )
        )
        addKeepassControlRow(
            panel,
            listOf(
                R.string.keepass_keyboard_return to {
                    removeKeepassFieldPanel()
                    restoreNormalKeyboardView()
                    clearKeepassSelection()
                    cancelKeepassAutoLock()
                },
                R.string.keepass_keyboard_lock to {
                    KeepassAutofillLock.lock(this@LIMEService)
                    handleKeepassLocked(KeepassAutofillLock.lockReasonManual)
                },
                R.string.keepass_keyboard_enter to {
                    onKey(MY_KEYCODE_ENTER, null)
                }
            )
        )

        mKeepassPanel = panel
        container.addView(
            panel,
            insertIndex.coerceIn(0, container.childCount),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        container.requestLayout()
        container.updateCandidateViewWidthConstraint()
        scheduleKeepassAutoLock()
    }

    private fun detachKeyboardForKeepassPanel(container: ViewGroup): Int {
        val keyboardView = mInputView ?: return container.childCount
        keyboardView.setVisibility(View.GONE)
        val parent = keyboardView.parent as? ViewGroup ?: return container.childCount
        val index = parent.indexOfChild(keyboardView).takeIf { it >= 0 } ?: return container.childCount
        mKeepassDetachedKeyboardIndex = index
        mKeepassDetachedKeyboardLayoutParams = keyboardView.layoutParams
        parent.removeView(keyboardView)
        return index
    }

    private fun addKeepassFieldGrid(panel: LinearLayout, fields: List<Pair<Int, String>>) {
        val nonEmptyFields = fields.filter { (_, value) -> value.isNotBlank() }
        nonEmptyFields.chunked(KEEPASS_FIELD_BUTTONS_PER_ROW).forEach { rowFields ->
            addKeepassControlRow(
                panel,
                rowFields.map { (labelRes, value) ->
                    labelRes to {
                        commitKeepassField(labelRes, value)
                    }
                }
            )
        }
    }

    private fun addKeepassControlRow(panel: LinearLayout, controls: List<Pair<Int, () -> Unit>>) {
        val context = mThemeContext ?: this
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.forEach { (labelRes, onClick) ->
            row.addView(
                createKeepassControlButton(labelRes, onClick),
                LinearLayout.LayoutParams(0, keepassKeyboardButtonHeight(), 1f).apply {
                    setMargins(dp(4f), dp(3f), dp(4f), dp(3f))
                }
            )
        }
        panel.addView(row, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun createKeepassControlButton(labelRes: Int, onClick: () -> Unit): Button {
        return Button(mThemeContext ?: this).apply {
            text = getString(labelRes)
            isAllCaps = false
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(keepassKeyboardTextColor())
            background = ContextCompat.getDrawable(this@LIMEService, keepassKeyboardKeyBackgroundRes())
            minHeight = keepassKeyboardButtonHeight()
            minimumHeight = keepassKeyboardButtonHeight()
            setPadding(dp(6f), 0, dp(6f), 0)
            setOnClickListener { onClick() }
        }
    }

    private fun keepassKeyboardButtonHeight(): Int {
        return resources.getDimensionPixelSize(R.dimen.key_height)
    }

    private fun keepassKeyboardKeyBackgroundRes(): Int {
        val resolvedThemeIndex =
            if (mKeyboardThemeIndex == 6) {
                if (isEffectiveDarkTheme) 1 else 0
            } else {
                mKeyboardThemeIndex
            }
        return when (resolvedThemeIndex) {
            1 -> R.drawable.btn_keyboard_key_dark
            2 -> R.drawable.btn_keyboard_key_pink
            3 -> R.drawable.btn_keyboard_key_tech_blue
            4 -> R.drawable.btn_keyboard_key_fashion_purple
            5 -> R.drawable.btn_keyboard_key_relax_green
            else -> R.drawable.btn_keyboard_key_light
        }
    }

    private fun keepassKeyboardTextColor(): Int {
        val context = mThemeContext ?: this
        val fallback =
            if (isColorLight(keyboardBackgroundColorForCurrentTheme)) {
                ContextCompat.getColor(this, R.color.foreground_light)
            } else {
                ContextCompat.getColor(this, R.color.foreground_dark)
            }
        return resolveThemeColor(context, R.attr.keyTextColorNormal, fallback)
    }

    private fun commitKeepassField(labelRes: Int, value: String) {
        if (!KeepassAutofillLock.isUnlocked(this)) {
            handleKeepassLocked(KeepassAutofillLock.lockReasonAuto)
            showLimeToast(getString(R.string.keepass_keyboard_locked))
            return
        }
        val ic = currentInputConnection ?: return
        ic.commitText(value, 1)
        sendTabKey()
        showLimeToast(getString(R.string.keepass_entry_committed, getString(labelRes)))
    }

    private fun sendTabKey() {
        keyDownUp(KeyEvent.KEYCODE_TAB, false)
    }

    private fun removeKeepassFieldPanel() {
        val panel = mKeepassPanel ?: return
        (panel.parent as? ViewGroup)?.removeView(panel)
        mKeepassPanel = null
    }

    private fun scheduleKeepassAutoLock() {
        mKeepassAutoLockHandler.removeCallbacks(mKeepassAutoLockRunnable)
        val remainingMillis = KeepassAutofillLock.remainingUnlockedMillis(this)
        if (remainingMillis <= 0L) {
            handleKeepassLocked(KeepassAutofillLock.lockReasonAuto)
            return
        }
        mKeepassAutoLockHandler.postDelayed(mKeepassAutoLockRunnable, remainingMillis + 250L)
    }

    private fun cancelKeepassAutoLock() {
        mKeepassAutoLockHandler.removeCallbacks(mKeepassAutoLockRunnable)
    }

    private fun handleKeepassLocked(reason: String) {
        cancelKeepassAutoLock()
        clearKeepassSelection()
        removeKeepassFieldPanel()
        restoreNormalKeyboardView()
        DiagnosticLog.record(this, TAG, "handleKeepassLocked() reason=$reason")
    }

    private fun clearKeepassSelection() {
        mKeepassSelectedTitle = ""
        mKeepassSelectedUsername = ""
        mKeepassSelectedPassword = ""
        mKeepassSelectedUrl = ""
        mKeepassSelectedNotes = ""
    }

    private fun restoreNormalKeyboardView() {
        val container = mCandidateInInputView
        val keyboardView = mInputView
        if (container != null && keyboardView != null && keyboardView.parent == null) {
            val params = mKeepassDetachedKeyboardLayoutParams
                ?: LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            val index = mKeepassDetachedKeyboardIndex
                .takeIf { it >= 0 }
                ?.coerceAtMost(container.childCount)
                ?: container.childCount
            container.addView(keyboardView, index, params)
        }
        mKeepassDetachedKeyboardIndex = -1
        mKeepassDetachedKeyboardLayoutParams = null
        hasPhysicalKeyPressed = false
        mInputView?.setVisibility(View.VISIBLE)
        mInputView?.requestLayout()
        mInputView?.invalidateAllKeys()
        mCandidateInInputView?.requestLayout()
        mCandidateInInputView?.updateCandidateViewWidthConstraint()
    }

    override fun onText(text: CharSequence?) {
        if (DEBUG) Log.i(TAG, "OnText()")
        val ic = getCurrentInputConnection()
        if (ic == null) return
        ic.beginBatchEdit()

        if (mPredicting) {
            commitTyped(ic)
            //mJustRevertedSeparator = null;
        } else if (!mEnglishOnly && mComposing!!.length > 0) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
            pickHighlightedCandidate()
            //	commitTyped(ic);
        }
        ic.commitText(text, 1)

        //ic.commitText(text, 0);
        ic.endBatchEdit()
        updateShiftKeyState(getCurrentInputEditorInfo())
    }

    private fun updateCandidates() {
        this.updateCandidates(false)
    }

    val isComposingOrSearchingCandidates: Boolean
        get() = (mComposing != null && mComposing!!.length > 0)
                || (queryThread != null && queryThread!!.isAlive())
                || mEmojiSearchMode

    private fun updateChineseSymbol() {
        //ChineseSymbol chineseSym = new ChineseSymbol();
        hasChineseSymbolCandidatesShown = true
        val list: List<Mapping> = getChineseSymoblList()
        if (!list.isEmpty()) {
            // Setup sel key display if

            var selkey = "1234567890"
            if (disable_physical_selection && hasPhysicalKeyPressed) {
                selkey = ""
            }

            setSuggestions(list, hasPhysicalKeyPressed, selkey)

            if (DEBUG) Log.i(
                TAG, ("updateChineseSymbol():"
                        + "mCandidateList.size:" + mCandidateList!!.size)
            )
        }
    }


    /**
     * Update the list of available candidates from the current composing text.
     * This will need to be filled in by however you are determining candidates.
     */
    fun updateCandidates(getAllRecords: Boolean) {
        if (DEBUG) Log.i(TAG, "updateCandidate():Update Candidate mComposing:" + mComposing)

        hasChineseSymbolCandidatesShown = false

        if (mComposing!!.length > 0) {
            val list = LinkedList<Mapping?>()

            var keyString = mComposing.toString()

            //Art '30,Sep,2011 restrict the length of composing text for Stroke5
            if (currentSoftKeyboard!!.contains("wb")) {
                if (keyString.length > 5) {
                    keyString = keyString.substring(0, 5)
                    mComposing = StringBuilder()
                    mComposing!!.append(keyString)
                    val ic = getCurrentInputConnection()
                    if (ic != null && mPredictionOn) ic.setComposingText(keyString, 1)
                }
            }

            val finalKeyString = keyString
            val finalHasPhysicalKeyPressed = hasPhysicalKeyPressed
            if (queryThread != null && queryThread!!.isAlive()) queryThread!!.interrupt()
            queryThread = object : Thread() {
                override fun run() {
                    try {
                        if (SearchSrv != null) {
                            list.addAll(
                                SearchSrv!!.getMappingByCode(
                                    finalKeyString,
                                    !finalHasPhysicalKeyPressed,
                                    getAllRecords
                                )
                            )
                        } else {
                            Log.w(TAG, "SearchSrv is null, skipping getMappingByCode")
                        }
                    } catch (e: RemoteException) {
                        Log.e(TAG, "Error in suggestion processing", e)
                    }
                    try {
                        sleep(THREAD_YIELD_DELAY_MS.toLong())
                    } catch (e: InterruptedException) {
                        Log.e(TAG, "Error in suggestion processing", e)
                        return  // terminate thread here, since it is interrupted and more recent getMappingByCode will update the suggestions.
                    }
                    //Jeremy '11,6,19 EZ and ETEN use "`" as IM Keys, and also custom may use "`".
                    if (!list.isEmpty()) {
                        // Setup sel key display if
                        var selkey: String? = null
                        if (disable_physical_selection && finalHasPhysicalKeyPressed) {
                            selkey = ""
                        } else {
                            try {
                                if (SearchSrv != null) {
                                    selkey = SearchSrv!!.getSelkey() ?: ""
                                }
                            } catch (e: RemoteException) {
                                Log.e(TAG, "Error in suggestion processing", e)
                            }
                            var mixedModeSelkey = "`"
                            if (hasSymbolMapping && (activeIM != LIME.IM_DAYI) && !(activeIM == LIME.IM_PHONETIC
                                        && mLIMEPref!!.getPhoneticKeyboardType() == LIME.IM_PHONETIC)
                            ) {
                                mixedModeSelkey = " "
                            }


                            val selkeyOption = mLIMEPref!!.getSelkeyOption()
                            if (selkeyOption == 1) selkey = mixedModeSelkey + selkey
                            else if (selkeyOption == 2) selkey = mixedModeSelkey + " " + selkey
                        }

                        try {
                            sleep(THREAD_YIELD_DELAY_MS.toLong())
                        } catch (e: InterruptedException) {
                            Log.e(TAG, "Error in suggestion processing", e)
                            return  // terminate thread here, since it is interrupted and more recent getMappingByCode will update the suggestions.
                        }


                        // Emoji Control
                        // Check the Emoji parameter setting and load icons into the suggestions list
                        var insertPosition = mLIMEPref!!.getEmojiDisplayPosition()
                        if (insertPosition > 0) {
                            val emojiCheck = HashMap<String?, String?>()
                            val emojiList: MutableList<Mapping?> = LinkedList<Mapping?>()

                            if (!list.isEmpty()) {
                                var item1: MutableList<Mapping?>? = null
                                val item2: MutableList<Mapping?>?
                                val item3: MutableList<Mapping?>?

                                if (list.size <= insertPosition) {
                                    insertPosition = list.size
                                }

                                if (list.get(0)!!.getWord()!!.matches("[A-Za-z]+".toRegex())) {
                                    item1 = SearchSrv!!.findEmojiForCandidate(
                                        list.get(0)!!.getWord(),
                                        LimeDB.EmojiLocale.EN,
                                        8
                                    )
                                    if (!item1.isNullOrEmpty()) {
                                        for (m in item1) {
                                            if (m != null && emojiCheck.get(m.getWord()) == null) {
                                                emojiList.add(m)
                                                emojiCheck.put(m.getWord(), m.getWord())
                                            }
                                        }
                                    }
                                }

                                if (item1.isNullOrEmpty()) {
                                    //Log.i("EMOJI Check:", ""+list.get(1).getWord().getBytes().length);

                                    if (list.size > 1 && list.get(1) != null && list.get(1)!!
                                            .getWord() != null && list.get(1)!!.getWord()!!
                                            .toByteArray().size > 1 && list.get(1)!!
                                            .getWord()!!.length < 4
                                    ) {
                                        item2 = SearchSrv!!.findEmojiForCandidate(
                                            list.get(1)!!.getWord(), LimeDB.EmojiLocale.TW, 8
                                        )
                                        if (!item2.isNullOrEmpty()) {
                                            for (m in item2) {
                                                if (m != null && emojiCheck.get(m.getWord()) == null) {
                                                    emojiList.add(m)
                                                    emojiCheck.put(m.getWord(), m.getWord())
                                                }
                                            }
                                        }
                                        if (item2.isNullOrEmpty()) {
                                            item3 = LinkedList<Mapping?>()
                                            if (!item3.isNullOrEmpty()) {
                                                for (m in item3) {
                                                    if (m != null && emojiCheck.get(m.getWord()) == null) {
                                                        emojiList.add(m)
                                                        emojiCheck.put(m.getWord(), m.getWord())
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (!emojiList.isEmpty()) {
                                    insertPosition =
                                        adjustedEmojiInsertionPosition(list, insertPosition)
                                    list.addAll(insertPosition, emojiList)
                                }
                            }
                        }

                        setSuggestions(list, finalHasPhysicalKeyPressed, selkey)

                        if (DEBUG) Log.i(
                            TAG, ("updateCandidates(): display selkey:" + selkey
                                    + ", list.size:" + list.size
                                    + ", mComposing = " + mComposing)
                        )
                    } else {
                        //Jeremy '11,8,14
                        clearSuggestions()
                    }

                    // Show composing window if keyToKeyname got different string. Revised by Jeremy '11,6,4
                    if (SearchSrv != null) {
                        val keynameString =
                            SearchSrv!!.keyToKeyname(finalKeyString) //.toLowerCase(Locale.US)); moved to LimeDB
                        if (mCandidateView != null && (keynameString.uppercase() != finalKeyString.uppercase()) && !keynameString.trim { it <= ' ' }
                                .isEmpty()
                        ) {
                            try {
                                sleep(THREAD_YIELD_DELAY_MS.toLong())
                            } catch (e: InterruptedException) {
                                Log.e(TAG, "Error in suggestion processing", e)
                                // terminate thread here, since it is interrupted and more recent getMappingByCode will update the suggestions.
                                return
                            }
                            mCandidateView!!.setComposingText(keynameString)
                        }
                    }
                }
            }
            queryThread!!.start()
        } else  //Jermy '11,8,14
            clearSuggestions()
    }

    /*
     * Update English suggestions view
     */
    private fun updateEnglishPrediction() {
        hasChineseSymbolCandidatesShown = false
        if (mPredictionOn && mLIMEPref!!.getEnglishPrediction()) {
            try {
                val list = LinkedList<Mapping?>()

                if (tempEnglishWord == null || tempEnglishWord!!.length == 0) {
                    //Jeremy '11,8,14
                    clearSuggestions()
                } else {
                    val ic = getCurrentInputConnection()
                    if (ic == null) return
                    var after = false
                    try {
                        val textAfterCursor = ic.getTextAfterCursor(1, 1)
                        if (textAfterCursor != null && textAfterCursor.length > 0) {
                            val c = textAfterCursor[0]
                            if (!Character.isLetterOrDigit(c)) {
                                after = true
                            }
                        } else {
                            after = true
                        }
                    } catch (e: StringIndexOutOfBoundsException) {
                        Log.e(TAG, "Error in suggestion processing", e)
                        after = true
                    }

                    var matchedtemp = false

                    if (tempEnglishWord!!.length > 0) {
                        try {
                            val textBeforeCursor =
                                ic.getTextBeforeCursor(tempEnglishWord.toString().length, 1)
                            if (tempEnglishWord.toString()
                                    .equals(textBeforeCursor?.toString(), ignoreCase = true)
                            ) {
                                matchedtemp = true
                            }
                        } catch (e: StringIndexOutOfBoundsException) {
                            Log.e(TAG, "Error in suggestion processing", e)
                        }
                    }

                    if (after || matchedtemp) {
                        tempEnglishList!!.clear()

                        val finalHasPhysicalKeyPressed = hasPhysicalKeyPressed
                        if (queryThread != null && queryThread!!.isAlive()) queryThread!!.interrupt()
                        queryThread = object : Thread() {
                            override fun run() {
                                var suggestions: MutableList<Mapping?>? = null
                                try {
                                    suggestions =
                                        SearchSrv!!.getEnglishSuggestions(tempEnglishWord.toString())
                                } catch (e: RemoteException) {
                                    Log.e(TAG, "Error in suggestion processing", e)
                                }
                                try {
                                    sleep(THREAD_YIELD_DELAY_MS.toLong())
                                } catch (e: InterruptedException) {
                                    Log.e(TAG, "Error in suggestion processing", e)
                                    return  // terminate thread here, since it is interrupted and more recent getMappingByCode will update the suggestions.
                                }

                                list.addAll(
                                    buildEnglishPredictionCandidates(
                                        tempEnglishWord.toString(),
                                        suggestions
                                    )
                                )

                                if (!list.isEmpty()) {
                                    // Setup sel key display if
                                    var selkey = "1234567890"
                                    if (disable_physical_selection && finalHasPhysicalKeyPressed) {
                                        selkey = ""
                                    }
                                    try {
                                        sleep(THREAD_YIELD_DELAY_MS.toLong())
                                    } catch (e: InterruptedException) {
                                        Log.e(TAG, "Error in suggestion processing", e)
                                        return  // terminate thread here, since it is interrupted and more recent getMappingByCode will update the suggestions.
                                    }


                                    // Emoji Control
                                    // Check the Emoji parameter setting and load icons into the suggestions list
                                    var insertPosition = mLIMEPref!!.getEmojiDisplayPosition()
                                    if (insertPosition > 0) {
                                        val emojiCheck = HashMap<String?, String?>()
                                        val emojiList: MutableList<Mapping?> =
                                            LinkedList<Mapping?>()

                                        if (!list.isEmpty()) {
                                            val item1: MutableList<Mapping?>?
                                            if (list.size <= insertPosition) {
                                                insertPosition = list.size
                                            }

                                            item1 = SearchSrv!!.findEmojiForCandidate(
                                                list.get(0)!!.getWord(), LimeDB.EmojiLocale.EN, 8
                                            )
                                            if (!item1.isNullOrEmpty()) {
                                                for (m in item1) {
                                            if (m != null && emojiCheck.get(m.getWord()) == null) {
                                                        emojiList.add(m)
                                                        emojiCheck.put(m.getWord(), m.getWord())
                                                    }
                                                }
                                            }

                                            if (!emojiList.isEmpty()) {
                                                insertPosition = adjustedEmojiInsertionPosition(
                                                    list,
                                                    insertPosition
                                                )
                                                list.addAll(insertPosition, emojiList)
                                            }
                                        }
                                    }


                                    //Log.i("EMOJIbefore:", tempEnglishList.size() + "");
                                    tempEnglishList!!.addAll(list)
                                    setEnglishPredictionSuggestions(
                                        list,
                                        finalHasPhysicalKeyPressed,
                                        selkey
                                    )

                                    //Log.i("EMOJIafter:", tempEnglishList.size() + "");
                                } else {
                                    //Jeremy '11,8,14
                                    clearSuggestions()
                                }
                            }
                        }
                        queryThread!!.start()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating English prediction", e)
            }
        }
    }

    /*
     * Update dictionary view
     */
    private fun updateRelatedPhrase(getAllRecords: Boolean) {
        if (DEBUG) Log.i(TAG, "updateRelatedPhrase()")
        hasChineseSymbolCandidatesShown = false

        // Also use this to control whether need to display the english
        // suggestions words.

        // If there is no Temp Matched word exist then not to display dictionary
        // Modified by Jeremy '10, 4,1. getCode -> getWord
        // if( tempMatched != null && tempMatched.getCode() != null &&
        // !tempMatched.getCode().equals("")){
        if (committedCandidate != null && committedCandidate!!.getWord() != null && !committedCandidate!!.getWord()!!
                .isEmpty()
        ) {
            val finalHasPhysicalKeyPressed = hasPhysicalKeyPressed
            if (queryThread != null && queryThread!!.isAlive()) queryThread!!.interrupt()
            queryThread = object : Thread() {
                override fun run() {
                    val list = LinkedList<Mapping?>()
                    //Jeremy '11,8,9 Insert completion suggestions from application
                    //in front of related dictionary list in full-screen mode
                    if (mCompletionOn) {
                        list.addAll(buildCompletionList())
                    }


                    if (committedCandidate != null && hasMappingList) {
                        if (queryThread != null && queryThread!!.isAlive()) queryThread!!.interrupt()
                        try {
                            if (!committedCandidate!!.isEmojiRecord() && !committedCandidate!!.isChinesePunctuationSymbolRecord()) {
                                list.addAll(
                                    SearchSrv!!.getRelatedByWord(
                                        committedCandidate!!.getWord(),
                                        getAllRecords
                                    )
                                )
                            }
                        } catch (e: RemoteException) {
                            Log.e(TAG, "Error in suggestion processing", e)
                        }

                        if (!list.isEmpty()) {
                            // Setup sel key display if


                            var selkey = "1234567890"
                            if (disable_physical_selection && finalHasPhysicalKeyPressed) {
                                selkey = ""
                            }

                            setSuggestions(
                                list,
                                finalHasPhysicalKeyPressed && !isFullscreenMode(),
                                selkey
                            )
                        } else {
                            committedCandidate = null
                            //Jermy '11,8,14
                            clearSuggestions()
                        }
                    }
                }
            }
            queryThread!!.start()
        }
    }

    private fun buildCompletionList(): MutableList<Mapping?> {
        val list = LinkedList<Mapping?>()
        for (i in 0..<(if (mCompletions != null) mCompletions!!.size else 0)) {
            val ci = mCompletions!![i]
            if (ci != null) {
                val temp = Mapping()
                temp.setWord(ci.getText().toString())
                temp.setCode("")
                temp.setCompletionSuggestionRecord()
                list.add(temp)
            }
        }
        return list
    }

    val isKeyboardViewHidden: Boolean
        /**
         * Check if the keyboard view is currently hidden.
         * 
         * @return true if keyboard view is hidden (GONE), false otherwise
         */
        get() = mInputView != null && mInputView!!.getVisibility() == View.GONE


    /**
     * Restore keyboard view if it's hidden.
     * 
     * @param forceRestore If true, restore even if there's active composing text (e.g., when user explicitly clicks keyboard button)
     */
    fun restoreKeyboardViewIfHidden(forceRestore: Boolean) {
        // Only restore if:
        // 1. hasPhysicalKeyPressed is true (user was using physical keys)
        // 2. keyboard view is actually hidden
        // 3. Either forceRestore is true OR there's no active composing text (to avoid restoring during composition when not explicitly requested)
        if (hasPhysicalKeyPressed && mInputView != null && mInputView!!.getVisibility() == View.GONE) {
            // If forceRestore is true (user explicitly clicked keyboard button), restore regardless of composing text
            // Otherwise, only restore if there's no active composing text
            if (forceRestore || (mComposing == null || mComposing!!.length == 0)) {
                hasPhysicalKeyPressed = false
                mInputView!!.setVisibility(View.VISIBLE)

                // Ensure candidate view container remains visible when keyboard is restored
                if (mCandidateInInputView != null) {
                    mCandidateInInputView!!.setVisibility(View.VISIBLE)
                    // Ensure candidate view itself is visible
                    if (mCandidateViewInInputView != null) {
                        mCandidateViewInInputView!!.setVisibility(View.VISIBLE)
                    }

                    // Explicitly show candidate view using the handler
                    showCandidateView()

                    // Request layout update and re-apply window insets
                    mCandidateInInputView!!.post(Runnable {
                        mCandidateInInputView!!.setVisibility(View.VISIBLE)
                        if (mCandidateViewInInputView != null) {
                            mCandidateViewInInputView!!.setVisibility(View.VISIBLE)
                        }
                        // Clear popup expansion state when keyboard is restored (popup should expand downward now)
                        mCandidateInInputView!!.requestApplyInsets()
                        mCandidateInInputView!!.requestLayout()
                        // Update width constraint when keyboard is restored (button visibility changes)
                        mCandidateInInputView!!.updateCandidateViewWidthConstraint()
                    })
                }

                if (DEBUG) {
                    Log.i(
                        TAG,
                        "Restored keyboard view on touch/click event" + (if (forceRestore) " (forced)" else "")
                    )
                }
            }
        }
    }


    private fun initCandidateView() {
        if (DEBUG) Log.i(TAG, "initCandidateView()")

        mCandidateViewHandler.showCandidateView()
        mCandidateViewHandler.hideCandidateView()
    }

    private fun showCandidateView() {
        if (DEBUG) Log.i(TAG, "showCandidateView()")
        mCandidateViewHandler.showCandidateView()
    }

    private fun hideCandidateView() {
        if (DEBUG) Log.i(TAG, "hideCandidateView()")
        if (mCandidateView != null) mCandidateView!!.clear()
        hasCandidatesShown = false
        hasChineseSymbolCandidatesShown = false
        // Always use embedded candidate view in InputView, regardless of physical or soft keyboard
        if (mCandidateViewInInputView == null) return

        mCandidateViewHandler.hideCandidateViewDelayed()
    }

    private fun showEmptyCandidateToolbar() {
        if (DEBUG) Log.i(TAG, "showEmptyCandidateToolbar()")

        if (mComposing != null && mComposing!!.length > 0) mComposing!!.setLength(0)

        selectedCandidate = null

        if (mCandidateList != null) mCandidateList!!.clear()

        if (mCandidateViewInInputView == null) return

        this.inputCandidateStripVisibility = View.VISIBLE
        mCandidateViewInInputView!!.setSuggestions(null, false)
        mCandidateViewHandler.showCandidateView()
        mCandidateInInputView!!.requestLayout()
        mCandidateInInputView!!.updateCandidateViewWidthConstraint()
    }

    private fun forceHideCandidateView() {
        if (DEBUG) Log.i(TAG, "forceHideCandidateView()")

        if (mComposing != null && mComposing!!.length > 0) mComposing!!.setLength(0)

        selectedCandidate = null

        //selectedIndex = 0;
        if (mCandidateList != null) mCandidateList!!.clear()

        // mFixedCandidateViewOn is always true
        mCandidateViewInInputView!!.forceHide()
    }


    private val mCandidateViewHandler: CandidateViewHandler = CandidateViewHandler(this)


    private class CandidateViewHandler(im: LIMEService?) : Handler(Looper.getMainLooper()) {
        private val mLIMEService: WeakReference<LIMEService?>
        private val MSG_SHOW_CANDIDATE_VIEW = 1
        private val MSG_HIDE_CANDIDATE_VIEW = 2

        init {
            mLIMEService = WeakReference<LIMEService?>(im)
        }

        override fun handleMessage(msg: Message) {
            if (DEBUG) Log.i(TAG, "CandidateViewHandler.handleMessage(): message:" + msg.what)
            val mLIMEInstance = mLIMEService.get()
            if (mLIMEInstance == null) return
            when (msg.what) {
                MSG_SHOW_CANDIDATE_VIEW -> mLIMEInstance.setCandidatesViewShown(true)
                MSG_HIDE_CANDIDATE_VIEW -> mLIMEInstance.setCandidatesViewShown(false)
            }
        }

        fun showCandidateView() {
            removeMessages(MSG_HIDE_CANDIDATE_VIEW) //cancel previous hide messages if any
            sendMessage(obtainMessage(MSG_SHOW_CANDIDATE_VIEW))
        }

        fun hideCandidateView() {
            sendMessage(obtainMessage(MSG_HIDE_CANDIDATE_VIEW))
        }

        fun hideCandidateViewDelayed() {
            sendMessageDelayed(
                obtainMessage(MSG_HIDE_CANDIDATE_VIEW),
                DELAY_BEFORE_HIDE_CANDIDATE_VIEW.toLong()
            )
        }
    }

    @Synchronized
    fun setSuggestions(
        suggestions: List<Mapping?>?,
        showNumber: Boolean,
        diplaySelkey: String?
    ) {
        if (suggestions != null && !suggestions.isEmpty()) {
            this.inputCandidateStripVisibility = View.VISIBLE

            if (DEBUG) Log.i(
                TAG, ("setSuggestion():suggestions.size=" + suggestions.size
                        + ", mComposing = " + mComposing
                        + ", hasPhysicalKeyPressed:" + hasPhysicalKeyPressed)
            )


            hasCandidatesShown =
                true //Jeremy '15,6,1 move after hideCandidateView if candidateView is fixed.
            hasMappingList = true

            if (mCandidateView != null) {
                mCandidateList = suggestions as LinkedList<Mapping?>
                try {
                    selectedCandidate =
                        defaultServiceSelectedCandidate(suggestions, hasPhysicalKeyPressed)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in suggestion processing", e)
                }
                mCandidateView!!.setSuggestions(suggestions, showNumber, diplaySelkey ?: "")
                if (DEBUG) Log.i(
                    TAG, ("setSuggestion(): mCandidateList.size: " + mCandidateList!!.size
                            + ", mComposing = " + mComposing)
                )
            }
            // Update CandidateView width constraint after setting suggestions
            if (mCandidateInInputView != null) {
                mCandidateInInputView!!.updateCandidateViewWidthConstraint()
            }
        } else {
            if (DEBUG) Log.i(TAG, "setSuggestion() with list=null")
            hasMappingList = false
            //Jeremy '11,8,15
            clearSuggestions()
        }
    }

    @Synchronized
    private fun setEnglishPredictionSuggestions(
        suggestions: MutableList<Mapping?>?,
        showNumber: Boolean,
        diplaySelkey: String?
    ) {
        if (suggestions != null && !suggestions.isEmpty()) {
            this.inputCandidateStripVisibility = View.VISIBLE
            hasCandidatesShown = true
            hasMappingList = true
            selectedCandidate = null

            if (mCandidateView != null) {
                mCandidateList = suggestions as LinkedList<Mapping?>
                mCandidateView!!.setSuggestionsWithoutHighlight(
                    suggestions,
                    showNumber,
                    diplaySelkey ?: ""
                )
                if (DEBUG) Log.i(
                    TAG, ("setEnglishPredictionSuggestions(): mCandidateList.size: "
                            + mCandidateList!!.size + ", tempEnglishWord = " + tempEnglishWord)
                )
            }
            if (mCandidateInInputView != null) {
                mCandidateInInputView!!.updateCandidateViewWidthConstraint()
            }
        } else {
            if (DEBUG) Log.i(TAG, "setEnglishPredictionSuggestions() with list=null")
            hasMappingList = false
            clearSuggestions()
        }
    }

    /**
     * Public method to update CandidateView width constraint.
     * Called by CandidateView when the expanded popup is closed.
     */
    fun updateCandidateViewWidthConstraint() {
        if (mCandidateInInputView != null) {
            mCandidateInInputView!!.updateCandidateViewWidthConstraint()
        }
    }

    fun dismissCandidateComposing() {
        if (mEmojiKeyboardShown && mEmojiSearchMode) {
            exitEmojiSearchToKeyboard()
            return
        }
        if (mCandidateView != null) {
            mCandidateView!!.hideCandidatePopup()
        }
        clearComposing(true)
        val ic = getCurrentInputConnection()
        if (ic != null) ic.finishComposingText()
    }

    fun showLimeToast(text: CharSequence?) {
        if (text == null || text.length == 0) return
        try {
            if (Looper.myLooper() != null) {
                var toastTarget = mCandidateView
                if (mCandidateViewInInputView != null && mCandidateViewInInputView!!.getWindowToken() != null) {
                    toastTarget = mCandidateViewInInputView
                }
                if (toastTarget != null) {
                    toastTarget.showLimeToast(text)
                }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Cannot show lime_toast: " + e.message)
        }
    }

    private fun showPersistentLimeToast(text: CharSequence?) {
        if (text == null || text.length == 0) return
        try {
            if (Looper.myLooper() != null) {
                var toastTarget = mCandidateView
                if (mCandidateViewInInputView != null && mCandidateViewInInputView!!.getWindowToken() != null) {
                    toastTarget = mCandidateViewInInputView
                }
                if (toastTarget != null) {
                    toastTarget.showLimeToastUntilNextKey(text)
                }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Cannot show persistent lime_toast: " + e.message)
        }
    }

    private fun hideLimeToast() {
        try {
            if (mCandidateView != null) {
                mCandidateView!!.hideLimeToast()
            }
            if (mCandidateViewInInputView != null && mCandidateViewInInputView !== mCandidateView) {
                mCandidateViewInInputView!!.hideLimeToast()
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Cannot hide lime_toast: " + e.message)
        }
    }

    fun showReverseLookup(text: CharSequence?) {
        if (text == null || text.length == 0) return
        showPersistentLimeToast(text)
    }


    private fun handleBackspace() {
        if (DEBUG) Log.i(TAG, "handleBackspace()")
        val length = mComposing!!.length
        val ic = getCurrentInputConnection()
        if (length > 1) {
            mComposing!!.delete(length - 1, length)
            if (ic != null && mPredictionOn) ic.setComposingText(mComposing, 1)
            updateCandidates()
        } else if (length == 1) {
            //Jeremy '12,4, 21 force clear the last characacter in composing
            clearComposing(true)
            //Jeremy '12,4,29 use mEnglishOnly instead of onIM
        } else if (!mEnglishOnly // composing length == 0 after here
            && (hasCandidatesShown) // repalce isCandaiteShwon() with hasCandidatesShwn by Jeremy '12,5,6
            //&& mLIMEPref.getAutoChineseSymbol()
            && !hasChineseSymbolCandidatesShown
        ) {
            // #78 Bug 2 backport (iOS parity, see docs/CANDI_FUNCTION_KEYS.md):
            // related-phrase suggestions are browse-only — Backspace must dismiss the
            // stale bar AND delete the previous character in one tap, rather than only
            // clearing candidates (which under autoChineseSymbol then surfaces the
            // Chinese-punctuation list and requires 2–3 taps to actually delete).
            // Pre-clearing hasCandidatesShown prevents clearSuggestions() inside
            // clearComposing(false) from sliding into updateChineseSymbol().
            hasCandidatesShown = false
            clearComposing(false)
            keyDownUp(KeyEvent.KEYCODE_DEL, false)
        } else if (!mEnglishOnly //&& mCandidateView !=null && isCandidateShown()
            && hasCandidatesShown //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
        //&& !mFixedCandidateViewOn //Jeremy '12,5,23 clear the chinese symbol list for arrow keys to do navigation inside document
        ) {
            hideCandidateView() //Jeremy '11,9,8
        } else {
            //Jeremy '11,8,15
            //clearSuggestions();
            try {
                if (mEnglishOnly && mLIMEPref!!.getEnglishPrediction() && mPredictionOn
                    && (!hasPhysicalKeyPressed || mLIMEPref!!.getEnglishPredictionOnPhysicalKeyboard()) //mPredictionOnPhysicalKeyboard)
                ) {
                    if (tempEnglishWord != null && tempEnglishWord!!.length > 0) {
                        tempEnglishWord!!.deleteCharAt(tempEnglishWord!!.length - 1)
                        updateEnglishPrediction()
                    }
                }
                keyDownUp(KeyEvent.KEYCODE_DEL, false)
            } catch (e: Exception) {
                Log.e(TAG, "Error in key handling", e)
            }
        }
    }

    override fun setCandidatesViewShown(shown: Boolean) {
        if (DEBUG) Log.i(TAG, "setCandidateViewShown():" + shown)
        // LIME renders candidates inside the input view. Do not show Android's
        // separate candidates window, or URL/email empty-toolbar fields can get
        // a blank band above the keyboard.
        super.setCandidatesViewShown(false)

        if (DEBUG) {
            if (mCandidateViewInInputView != null) {
                Log.i(
                    TAG,
                    "isCandidateViewShown (embedded):" + mCandidateViewInInputView!!.isShown()
                )
            }
        }
    }


    private fun handleShift() {
        if (DEBUG) Log.i(TAG, "handleShift()")
        if (mInputView == null) {
            return
        }

        val doubleTap = this.isShiftDoubleTap
        if (mKeyboardSwitcher!!.isAlphabetMode) {
            val nextState: ShiftTapState =
                nextShiftTapState(mInputView!!.isShifted, mCapsLock, doubleTap)
            applyAlphabetShiftState(nextState)
        } else {
            val nextState: ShiftTapState = nextShiftTapState(mHasShift, mCapsLock, doubleTap)
            applyImShiftState(nextState)
        }
    }

    private val isShiftDoubleTap: Boolean
        get() {
            val now = SystemClock.uptimeMillis()
            val doubleTap = mLastShiftTime > 0
                    && now - mLastShiftTime <= ViewConfiguration.getDoubleTapTimeout()
            mLastShiftTime = now
            return doubleTap
        }

    class ShiftTapState(@JvmField val shifted: Boolean, @JvmField val capsLock: Boolean)

    private fun applyAlphabetShiftState(state: ShiftTapState) {
        setCapsLockState(state.capsLock)
        mInputView!!.setShifted(state.shifted)
        mHasShift = state.shifted
        if (state.shifted && !mKeyboardSwitcher!!.isShifted) {
            mKeyboardSwitcher!!.toggleShift()
        } else if (!state.shifted && mKeyboardSwitcher!!.isShifted) {
            mKeyboardSwitcher!!.toggleShift()
        }
    }

    private fun applyImShiftState(state: ShiftTapState) {
        setCapsLockState(state.capsLock)
        if (state.shifted && !mKeyboardSwitcher!!.isShifted) {
            mKeyboardSwitcher!!.toggleShift()
        } else if (!state.shifted && mKeyboardSwitcher!!.isShifted) {
            mKeyboardSwitcher!!.toggleShift()
        }
        mHasShift = state.shifted
    }

    private fun setCapsLockState(capsLock: Boolean) {
        if (mCapsLock == capsLock) {
            if (mInputView != null && mInputView!!.keyboard is LIMEKeyboard) {
                (mInputView!!.keyboard as LIMEKeyboard).setShiftLocked(capsLock)
            }
            return
        }
        mCapsLock = capsLock
        if (mInputView != null && mInputView!!.keyboard is LIMEKeyboard) {
            (mInputView!!.keyboard as LIMEKeyboard).setShiftLocked(mCapsLock)
        }
    }

    /**
     * Integrated all soft keyboards switching in this function.
     */
    private fun switchKeyboard(primaryCode: Int) {
        if (DEBUG) Log.i(TAG, "switchKeyboard() primaryCode = " + primaryCode)
        if (mCapsLock) toggleCapsLock()
        if (mEmojiKeyboardShown) {
            hideEmojiKeyboard()
        }

        // Cancel active composition when switching Chi -> Eng; other switches keep
        // the legacy auto-commit behavior.
        try {
            if (primaryCode == KEYCODE_SWITCH_TO_ENGLISH_MODE) {
                if (mComposing != null && mComposing!!.length > 0) {
                    clearComposing(true)
                    val ic = getCurrentInputConnection()
                    if (ic != null) ic.finishComposingText()
                } else {
                    clearComposing(false)
                }
            } else if (mComposing != null && mComposing!!.length > 0) {
                getCurrentInputConnection().commitText(mComposing, 1)
                finishComposing()
                clearComposing(false)
            } else {
                clearComposing(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in composing finish", e)
            // ignore all possible error
        }

        hideCandidateView()


        if (primaryCode == KEYCODE_SWITCH_TO_SYMBOL_MODE) { //Symbol keyboard
            mEnglishOnly = true
            mKeyboardSwitcher!!.toggleSymbols()
            // mFixedCandidateViewOn is always true
            forceHideCandidateView()
        } else if (primaryCode == KEYCODE_SWITCH_SYMBOL_KEYBOARD) { //Symbol keyboard
            mEnglishOnly = true
            mKeyboardSwitcher!!.switchSymbols()
            // mFixedCandidateViewOn is always true
            forceHideCandidateView()
        } else if (primaryCode == KEYCODE_SWITCH_TO_ENGLISH_MODE) { //Chi --> Eng
            mEnglishOnly = true
            mLIMEPref!!.setLanguageMode(true)
            mKeyboardSwitcher!!.toggleChinese()
            // mFixedCandidateViewOn is always true
            if (!mPredictionOn) {
                showEmptyCandidateToolbar()
            } else {
                mCandidateViewInInputView!!.setSuggestions(
                    null,
                    false
                ) // reset the candidate view if it's force hided before
            }
        } else if (primaryCode == KEYCODE_SWITCH_TO_IM_MODE) { //Eng --> Chi moved from SwitchKeyboardIM by Jeremy '12,4,29
            mEnglishOnly = false
            mLIMEPref!!.setLanguageMode(false)
            initialIMKeyboard()
            // mFixedCandidateViewOn is always true
            mCandidateViewInInputView!!.setSuggestions(
                null,
                false
            ) // reset the candiate view if it's force hided before
        }


        mHasShift = false
        updateShiftKeyState(getCurrentInputEditorInfo())

        // Update keyboard xml information
        currentSoftKeyboard = mKeyboardSwitcher!!.getImConfigKeyboard(activeIM)
    }


    /**
     * For physical keybaord to switch between chinese and english mode.
     */
    private fun switchChiEng() {
        if (DEBUG) Log.i(TAG, "switchChiEng(): mEnglishOnly:" + mEnglishOnly)

        //Jeremy '12,4,21 force clear before switching chi/eng
        clearComposing(false)

        mKeyboardSwitcher!!.toggleChinese()
        mEnglishOnly = !mKeyboardSwitcher!!.isChinese
        mLIMEPref!!.setLanguageMode(mEnglishOnly)

        if (DEBUG) Log.i(TAG, "switchChiEng(): mEnglishOnly updated as " + mEnglishOnly)
        clearSuggestions() //Jeremy '11,9,5
    }


    @SuppressLint("InflateParams")
    private fun initialViewAndSwitcher(forceRecreate: Boolean) {
        if (DEBUG) Log.i(
            TAG,
            "initialViewAndSwitcher() mKeyboardThemeIndex = " + mKeyboardThemeIndex + ", mLIMEPref.getKeyboardTheme() = " + mLIMEPref!!.getKeyboardTheme()
        )

        var mForceRecreate = forceRecreate
        if (mKeyboardThemeIndex != mLIMEPref!!.getKeyboardTheme()) {
            mKeyboardThemeIndex = mLIMEPref!!.getKeyboardTheme()
            mForceRecreate = true
            mThemeContext = null
            clearAppliedFollowSystemAccentState()
            clearAppliedNavigationBarThemeState()
            if (mKeyboardSwitcher != null) mKeyboardSwitcher!!.resetKeyboards(true)
        }

        if (mThemeContext == null) {
            mThemeContext = ContextThemeWrapper(this, this.keyboardTheme)
            if (mKeyboardSwitcher != null) mKeyboardSwitcher!!.setThemedContext(mThemeContext!!)
        }

        val mIsHardwareAcceleratedDrawingEnabled = true
        // mFixedCandidateViewOn is always true - Have candidateView in InputView
        //Create inputView if it's null
        if (mCandidateInInputView == null || mForceRecreate) {
            mCandidateInInputView = LayoutInflater.from(mThemeContext).inflate(
                R.layout.inputcandidate, null
            ) as CandidateInInputViewContainer?
            mInputView = mCandidateInInputView!!.findViewById<LIMEKeyboardView?>(R.id.keyboard)
            mInputView!!.setOnKeyboardActionListener(this)
            hasDistinctMultitouch = mInputView!!.hasDistinctMultitouch()
            mInputView!!.setHardwareAcceleratedDrawingEnabled(mIsHardwareAcceleratedDrawingEnabled)
            mCandidateInInputView!!.initViews()
            mCandidateViewInInputView =
                mCandidateInInputView!!.findViewById<CandidateView?>(R.id.candidatesView)
            mCandidateViewInInputView!!.setService(this)
            mCandidateInInputView!!.setService(this)
            this.emojiKeyboardViewForTesting =
                mCandidateInInputView!!.findViewById<View?>(R.id.emoji_keyboard)
            setupEmojiKeyboardView()
            advanceInputViewGeneration()
        }
        if (mCandidateView !== mCandidateViewInInputView) mCandidateView = mCandidateViewInInputView
        applyFollowSystemAccentColors()


        // Check if mKeyboardSwitcher == null
        if (mKeyboardSwitcher == null) {
            mKeyboardSwitcher = LIMEKeyboardSwitcher(this, mThemeContext!!)
        }
        mKeyboardSwitcher!!.setInputView(mInputView)
        refreshStartupConfigSnapshotIfNeeded()
        mKeyboardSwitcher!!.setActivatedIMList(activatedIMList, activatedIMShortNameList)

        if (mKeyboardSwitcher!!.getKeyboardSize() == 0) {
            applyStartupConfigSnapshotToKeyboardSwitcher()
        }
    }

    /**
     * For initializing Chinese IM and corresponding soft keyboards.
     */
    private fun initialIMKeyboard() {
        if (DEBUG) Log.i(TAG, "initalizeIMKeyboard(): keyboardSelection:" + activeIM)

        //mEnglishOnly = false;
        //super.setCandidatesViewShown(false);
        if (mKeyboardSwitcher == null) {
            Log.w(
                TAG,
                "initialIMKeyboard(): mKeyboardSwitcher is null, skipping keyboard initialization"
            )
            return
        }

        when (activeIM) {
            "custom" -> {
                mKeyboardSwitcher!!.setKeyboardMode(
                    activeIM,
                    LIMEKeyboardSwitcher.MODE_TEXT, mImeOptions, true, false, false
                )

                hasNumberMapping = mLIMEPref!!.getAllowNumberMapping()
                hasSymbolMapping = mLIMEPref!!.getAllowSymoblMapping()
            }

            LIME.IM_CJ, LIME.IM_SCJ, LIME.IM_CJ5, LIME.IM_ECJ -> {
                mKeyboardSwitcher!!.setKeyboardMode(
                    activeIM,
                    LIMEKeyboardSwitcher.MODE_TEXT, mImeOptions, true, false, false
                )
                hasNumberMapping = false
                hasSymbolMapping = false
            }

            LIME.IM_PHONETIC -> {
                mKeyboardSwitcher!!.setKeyboardMode(
                    activeIM,
                    LIMEKeyboardSwitcher.MODE_TEXT, mImeOptions, true, false, false
                )
                //Jeremy '11,6,18 ETEN 26 has no number mapping
                val standardPhonetic =
                    !(mLIMEPref!!.getPhoneticKeyboardType() == LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26
                            || mLIMEPref!!.getPhoneticKeyboardType() == LIME.IM_PHONETIC_KEYBOARD_HSU)
                hasNumberMapping = standardPhonetic
                hasSymbolMapping = standardPhonetic
            }

            LIME.IM_EZ, LIME.IM_DAYI -> {
                mKeyboardSwitcher!!.setKeyboardMode(
                    activeIM,
                    LIMEKeyboardSwitcher.MODE_TEXT, mImeOptions, true, false, false
                )
                hasNumberMapping = true
                hasSymbolMapping = true
            }

            LIME.IM_ARRAY10, LIME.IM_PINYIN -> {
                hasNumberMapping = true
                hasSymbolMapping = false
                mKeyboardSwitcher!!.setKeyboardMode(
                    activeIM,
                    LIMEKeyboardSwitcher.MODE_TEXT, mImeOptions, true, false, false
                )
            }

            LIME.IM_ARRAY -> {
                hasNumberMapping =
                    true //Jeremy '12,4,28 array 30 actually use number combination keys to enter symbols1

                hasSymbolMapping = true
                mKeyboardSwitcher!!.setKeyboardMode(
                    activeIM,
                    LIMEKeyboardSwitcher.MODE_TEXT, mImeOptions, true, false, false
                )
            }

            LIME.IM_WB -> {
                hasNumberMapping = false
                hasSymbolMapping = true
                mKeyboardSwitcher!!.setKeyboardMode(
                    activeIM,
                    LIMEKeyboardSwitcher.MODE_TEXT, mImeOptions, true, false, false
                )
            }

            LIME.IM_HS -> {
                hasNumberMapping = true
                hasSymbolMapping = true
                mKeyboardSwitcher!!.setKeyboardMode(
                    activeIM,
                    LIMEKeyboardSwitcher.MODE_TEXT, mImeOptions, true, false, false
                )
            }

            else -> mKeyboardSwitcher!!.setKeyboardMode(
                activeIM,
                LIMEKeyboardSwitcher.MODE_TEXT, mImeOptions, true, false, false
            )
        }
        //Jeremy '11,9,3 for phone numeric key direct input on chacha
        if (mLIMEPref!!.getPhysicalKeyboardType() == "chacha") hasNumberMapping = false
        var tablename = activeIM
        if (tablename == "custom" || tablename == "phone") {
            tablename = "custom"
        }
        //Jeremy '11,6,10 pass hasnumbermapping and hassymbolmapping to searchservice for selkey validation.
        if (DEBUG) Log.i(
            TAG, "switchKeyboard() current keyboard:" +
                    tablename + " hasnumbermapping:" + hasNumberMapping + " hasSymbolMapping:" + hasSymbolMapping
        )
        SearchSrv!!.setTableName(tablename ?: LIME.DB_TABLE_PHONETIC, hasNumberMapping, hasSymbolMapping)
    }

    private fun handleSelkey(primaryCode: Int): Boolean {
        if (DEBUG) Log.i(TAG, "handleSelKey()")

        // Jeremy '12,4,1 only do selkey on starndard keyboard

        // Check if disable physical key option is open
        if ((disable_physical_selection && hasPhysicalKeyPressed)
            || mLIMEPref!!.getPhysicalKeyboardType() != "normal_keyboard"
        ) {
            return false
        }

        if (DEBUG) Log.i(TAG, "handleSelkey():primarycode:" + primaryCode)

        var i = -1
        if (mComposing!!.length > 0 && !mEnglishOnly) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
            var selkey = ""

            // Jeremy '12,7,5 rewrite the selkey processing
            if (!(disable_physical_selection && hasPhysicalKeyPressed)) {
                try {
                    selkey = SearchSrv!!.getSelkey() ?: ""
                } catch (e: RemoteException) {
                    Log.e(TAG, "Error getting selkey", e)
                }

                var mixedModeSelkey = "`"
                if (hasSymbolMapping && (activeIM != LIME.IM_DAYI) && !(activeIM == LIME.IM_PHONETIC
                            && mLIMEPref!!.getPhoneticKeyboardType() == LIME.IM_PHONETIC)
                ) {
                    mixedModeSelkey = " "
                }


                val selkeyOption = mLIMEPref!!.getSelkeyOption()
                if (selkeyOption == 1) selkey = mixedModeSelkey + selkey
                else if (selkeyOption == 2) selkey = mixedModeSelkey + " " + selkey


                i = selkey.indexOf(primaryCode.toChar())

                //Jeremy '12,7,11 bypass space as first tone for phonetic
                if (i >= 0 && selkey.get(i) == ' ' && primaryCode == MY_KEYCODE_SPACE && activeIM == LIME.IM_PHONETIC //&& mLIMEPref.getParameterBoolean("doLDPhonetic", true)
                    && !(mComposing.toString().endsWith(" ") || mComposing!!.length == 0)
                ) {
                    return false
                }
            }

            //Jeremy '12,4,29 use mEnglishOnly instead of onIM
        } else if (mEnglishOnly || (mComposing!!.length == 0)) {
            // related candidates view
            val relatedSelkey = "!@#$%^&*()"
            i = relatedSelkey.indexOf(primaryCode.toChar())
        }


        if (i < 0 || i >= mCandidateList!!.size) {
            return false
        } else {
            pickCandidateManually(i)
            return true
        }
    }

    /**
     * This method construct candidate view and add key code to composing object
     */
    private fun handleCharacter(primaryCode: Int) {
        //Jeremy '11,6,9 Cleaned code!!
        var primaryCode = primaryCode
        if (DEBUG) Log.i(
            TAG, ("handleCharacter():primaryCode:" + primaryCode
                    + ", metaState = " + mMetaState
                    + ", hasPhysicalKeyPressed = " + hasPhysicalKeyPressed
                    + ", currentSoftKeyboard=" + currentSoftKeyboard)
        )


        //Jeremy '11,6,6 processing physical keyboard selkeys.
        //Move here '11,6,9 to have lower priority than hasnumbermapping
        if (hasPhysicalKeyPressed && (mCandidateView != null && hasCandidatesShown)) { //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
            if (handleSelkey(primaryCode)) {
                updateShiftKeyState(getCurrentInputEditorInfo())
                if (DEBUG) Log.i(TAG, "handleCharacter() sel key found return now")
                return
            }
        }


        if (!mEnglishOnly) {
            val ic = getCurrentInputConnection()

            if (DEBUG) Log.i(
                TAG, ("HandleCharacter():"
                        + " ic != null:" + (ic != null)
                        + " isValidLetter:" + isValidLetter(primaryCode)
                        + " isValidDigit:" + isValidDigit(primaryCode)
                        + " isValidSymbol:" + isValidSymbol(primaryCode)
                        + " hasSymbolMapping:" + hasSymbolMapping
                        + " hasNumberMapping:" + hasNumberMapping
                        + " (primaryCode== MY_KEYCODE_SPACE && keyboardSelection.equals(phonetic):" + (primaryCode == MY_KEYCODE_SPACE && activeIM == LIME.IM_PHONETIC)
                        + " mEnglishOnly:" + mEnglishOnly)
            )


            if ((!hasSymbolMapping) && (primaryCode == ','.code || primaryCode == '.'.code)) { // Chinese , and . processing //Jeremy '12,4,29 use mEnglishOnly instead of onIM
                mComposing!!.append(primaryCode.toChar())
                //InputConnection ic=getCurrentInputConnection();
                if (ic != null && mPredictionOn) ic.setComposingText(mComposing, 1)
                updateCandidates()
                //misMatched = mComposing.toString();
            } else if (!hasSymbolMapping && !hasNumberMapping //Jeremy '11,10.19 fixed to bypass number key in et26 and hsu
                && (isValidLetter(primaryCode)
                        || (primaryCode == MY_KEYCODE_SPACE && activeIM == LIME.IM_PHONETIC)) //Jeremy '11,9,6 for et26 and hsu
                && !mEnglishOnly
            ) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
                //Log.i(TAG,"handlecharacter(), onIM and no number and no symbol mapping");
                mComposing!!.append(primaryCode.toChar())
                //InputConnection ic=getCurrentInputConnection();
                if (ic != null && mPredictionOn) ic.setComposingText(mComposing, 1)
                updateCandidates()
                //misMatched = mComposing.toString();
            } else if (!hasSymbolMapping && hasNumberMapping
                && (isValidLetter(primaryCode) || isValidDigit(primaryCode))
                && !mEnglishOnly
            ) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
                mComposing!!.append(primaryCode.toChar())
                //InputConnection ic=getCurrentInputConnection();
                if (ic != null && mPredictionOn) ic.setComposingText(mComposing, 1)
                updateCandidates()
                //misMatched = mComposing.toString();
            } else if (hasSymbolMapping
                && !hasNumberMapping && (isValidLetter(primaryCode) || isValidSymbol(primaryCode)
                        || (primaryCode == MY_KEYCODE_SPACE && activeIM == LIME.IM_PHONETIC)) //Jeremy '11,9,6 for chacha
                && !mEnglishOnly
            ) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
                mComposing!!.append(primaryCode.toChar())
                //InputConnection ic=getCurrentInputConnection();
                if (ic != null && mPredictionOn) ic.setComposingText(mComposing, 1)
                updateCandidates()
                //misMatched = mComposing.toString();
            } else if (hasSymbolMapping && !hasNumberMapping && activeIM == LIME.IM_ARRAY
                && mComposing != null && mComposing!!.length >= 1
                && (getCurrentInputConnection()?.getTextBeforeCursor(1, 1)?.let {
                    it.length > 0 && it[0] == 'w'
                } == true) && Character.isDigit(primaryCode.toChar())
                && !mEnglishOnly
            ) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
                // 27.May.2011 Art : This is the method to check user input type
                // if first previous character is w and second char is number then enable im mode.
                mComposing!!.append(primaryCode.toChar())
                //InputConnection ic=getCurrentInputConnection();
                if (ic != null && mPredictionOn) ic.setComposingText(mComposing, 1)
                updateCandidates()
                //misMatched = mComposing.toString();
            } else if (hasSymbolMapping
                && hasNumberMapping
                && (isValidSymbol(primaryCode)
                        || (primaryCode == MY_KEYCODE_SPACE && activeIM == LIME.IM_PHONETIC)
                        || isValidLetter(primaryCode) || isValidDigit(primaryCode)) && !mEnglishOnly
            ) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
                mComposing!!.append(primaryCode.toChar())
                //InputConnection ic=getCurrentInputConnection();
                if (ic != null && mPredictionOn) ic.setComposingText(mComposing, 1)
                updateCandidates()

                //misMatched = mComposing.toString();
            } else {
                pickHighlightedCandidate() // check here.

                if (ic != null) ic.commitText(primaryCode.toChar().toString(), 1)
                //Jeremy '12,4,21
                finishComposing()
            }
        } else {
            /*
             * Handle when user input English Characters
             */
            if (DEBUG) Log.i(
                TAG, "handleCharacter() english only mode without prediction, committext = "
                        + primaryCode.toChar()
            )
            if (isInputViewShown()) {
                if (mInputView!!.isShifted) {
                    primaryCode = primaryCode.toChar().uppercaseChar().code
                }
            }

            val ic = getCurrentInputConnection()

            // ENGLISH_KB.md #0 / §2a: read+clear the pick-auto-space flag up front so
            // every path below clears it; only the punctuation-swap path acts on it.
            val wasPickedAutoSpace = mPickedAutoSpace
            mPickedAutoSpace = false

            if (primaryCode == MY_KEYCODE_SPACE && mAutoCap && ic != null && shouldInsertPeriodForEnglishDoubleSpace(
                    ic.getTextBeforeCursor(64, 0)
                )
            ) {
                resetTempEnglishWord()
                if (mLIMEPref!!.getEnglishPrediction() && mPredictionOn) {
                    this.updateEnglishPrediction()
                }
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                if (!(!hasPhysicalKeyPressed && hasDistinctMultitouch)) updateShiftKeyState(
                    getCurrentInputEditorInfo()
                )
                return
            }

            if (mLIMEPref!!.getEnglishPrediction() && mPredictionOn && !mKeyboardSwitcher!!.isSymbols && (!hasPhysicalKeyPressed || mLIMEPref!!.getEnglishPredictionOnPhysicalKeyboard())
            ) {
                if (Character.isLetter(primaryCode.toChar())) {
                    this.tempEnglishWord!!.append(primaryCode.toChar())
                    this.updateEnglishPrediction()
                } else {
                    resetTempEnglishWord()
                    this.updateEnglishPrediction()
                }
            }

            if (ic != null) {
                // ENGLISH_KB.md #0 / §2a: if the previous action auto-appended a space
                // after a suggestion pick, swap it with the typed punctuation per the
                // LatinIME character classes. Falls through to a normal commit otherwise.
                if (!commitEnglishPunctuationWithSwap(
                        ic,
                        primaryCode.toChar(),
                        wasPickedAutoSpace
                    )
                ) {
                    ic.commitText(primaryCode.toChar().toString(), 1)
                }
            }
        }

        if (!(!hasPhysicalKeyPressed && hasDistinctMultitouch)) updateShiftKeyState(
            getCurrentInputEditorInfo()
        )
    }

    /**
     * ENGLISH_KB.md #0 / §2a — replicate LatinIME's pick-space punctuation swap.
     * 
     * 
     * When an English suggestion pick auto-appended a trailing space (`word `),
     * typing punctuation should produce `word, ` rather than `word ,`.
     * Returns `true` if this method fully handled committing the character (caller
     * must skip its own commit); `false` to let the caller commit normally.
     * 
     * 
     * Character classes mirror AOSP `donottranslate-config-spacing-and-punctuations.xml`:
     * 
     *  * followed-by-space (`. , ; : ! ? ) ] `}): delete the space, commit `punct + " "`.
     *  * preceded-by-space (`( [ {{}}): keep the space, commit the bracket.</li>   <li>strip ({ - / @ _ '}): delete the space, commit the punctuation bare.</li> </ul>`
     */
    private fun commitEnglishPunctuationWithSwap(
        ic: InputConnection, c: Char,
        wasPickedAutoSpace: Boolean
    ): Boolean {
        if (!wasPickedAutoSpace) return false

        val followed = ENG_SWAP_FOLLOWED_BY_SPACE.indexOf(c) >= 0
        val preceded = ENG_SWAP_PRECEDED_BY_SPACE.indexOf(c) >= 0
        val strip = ENG_SWAP_STRIP.indexOf(c) >= 0
        if (!followed && !preceded && !strip) return false // not a swap char (letters, &, etc.)


        // Preceded-by-space brackets keep the existing space; nothing special to do.
        if (preceded) return false

        // followed-by-space / strip both need to remove the auto-space first — but only
        // if it is actually still there (cursor-move safety).
        val before = ic.getTextBeforeCursor(1, 0)
        if (before == null || before.length != 1 || before.get(0) != ' ') {
            return false // no trailing space (e.g. user moved cursor) — commit normally
        }

        ic.beginBatchEdit()
        try {
            ic.deleteSurroundingText(1, 0)
            if (followed) {
                ic.commitText(c.toString() + " ", 1) // word,  (space moves after the punctuation)
            } else {
                ic.commitText(c.toString(), 1) // word-  (no space)
            }
        } finally {
            ic.endBatchEdit()
        }
        return true
    }

    private fun handleClose() {
        if (DEBUG) Log.i(TAG, "handleClose()")

        // cancel candidate view if it's shown

        //Jeremy '12,4,23 need to check here.
        finishComposing()

        requestHideSelf(0)
        if (mInputView != null) {
            mInputView!!.closing()
        }
    }

    private fun checkToggleCapsLock() {
        if (mInputView!!.keyboard?.isShifted == true) {
            toggleCapsLock()
        }
    }

    private fun toggleCapsLock() {
        mCapsLock = !mCapsLock
        if (mKeyboardSwitcher!!.isAlphabetMode) {
            (mInputView!!.keyboard as LIMEKeyboard).setShiftLocked(mCapsLock)
        } else {
            if (mCapsLock) {
                if (DEBUG) {
                    Log.i(TAG, "toggleCapsLock():mCapsLock:true")
                }
                if (!mKeyboardSwitcher!!.isShifted) mKeyboardSwitcher!!.toggleShift()
                (mInputView!!.keyboard as LIMEKeyboard).setShiftLocked(true)
            } else {
                if (DEBUG) {
                    Log.i(TAG, "toggleCapsLock():mCapsLock:false")
                }
                (mInputView!!.keyboard as LIMEKeyboard).setShiftLocked(false)
                if (mKeyboardSwitcher!!.isShifted) mKeyboardSwitcher!!.toggleShift()
            }
        }
    }

    /*
        public boolean isWordSeparator(int code) {
            //Jeremy '11,5,31
            String separators = getResources().getString(R.string.word_separators);
            return separators.contains(String.valueOf((char) code));

        }
    */
    //Jeremy '12,5,11 add return value from mCandidate.takeselectedsuggestion()
    fun pickHighlightedCandidate(): Boolean {
        return mCandidateView != null && mCandidateView!!.takeSelectedSuggestion()
    }

    fun requestFullRecords(isRelatedPhrase: Boolean) {
        if (DEBUG) Log.i(TAG, "requestFullRecords()")

        if (isRelatedPhrase) this.updateRelatedPhrase(true)
        else this.updateCandidates(true)
    }

    fun pickCandidateManually(index: Int) {
        if (DEBUG) Log.i(
            TAG, ("pickCandidateManually():"
                    + "Pick up candidate at index : " + index)
        )

        // This is to prevent if user select the index more than the list
        if (mCandidateList != null && index >= mCandidateList!!.size) {
            return
        }


        if (mCandidateList != null && !mCandidateList!!.isEmpty()) {
            selectedCandidate = mCandidateList!!.get(index)
            //selectedIndex = index;
        }

        if (mEmojiKeyboardShown && mEmojiSearchMode
            && selectedCandidate != null && selectedCandidate!!.isEmojiRecord()
        ) {
            commitEmoji(selectedCandidate!!.getWord())
            return
        }
        if (appendPickedCandidateToEmojiSearch(selectedCandidate)) {
            return
        }

        val ic = getCurrentInputConnection()

        if (mCompletionOn && mCompletions != null && index >= 0 && selectedCandidate!!.isPartialMatchToCodeRecord()
            && index < mCompletions!!.size
        ) {  // user picked the completion suggestion item.
            val ci = mCompletions!![index]
            if (ic != null) ic.commitCompletion(ci)
            if (DEBUG) Log.i(TAG, "pickSuggestionManually():mCompletionOn:" + mCompletionOn)
        } else if ((mComposing!!.length > 0 || (selectedCandidate != null && !selectedCandidate!!.isComposingCodeRecord()))
            && !mEnglishOnly
        ) {  // user picked candidates from composing candidate or related phrase candidates
            //Jeremy '12,4,29 use mEnglishOnly instead of onIM
            commitTyped(ic)
        } else if (mLIMEPref!!.getEnglishPrediction() && tempEnglishList != null && !tempEnglishList!!.isEmpty()) {  // user picked English prediction suggestions


            //Log.i("EMOJI-commit-index:", index + "");
            //Log.i("EMOJI-commit:", tempEnglishList.size() + "");


            if (this.tempEnglishList!!.get(index)!!.isEmojiRecord()) {
                if (ic != null) ic.commitText(
                    this.tempEnglishList!!.get(index)!!.getWord() + " ", 1
                )
                if (SearchSrv != null) {
                    SearchSrv!!.recordEmojiUsage(this.tempEnglishList!!.get(index)!!.getWord())
                    mEmojiCategoryPages = null
                }
            } else {
                val pickedWord = this.tempEnglishList!!.get(index)!!.getWord()
                if (ic != null) ic.commitText(
                    pickedWord!!.substring(tempEnglishWord!!.length) + " ", 1
                )
                // ENG_AUTO_COMPLETION.md "Learning": increment the picked word's score so
                // frequently chosen words rank higher (mirrors the emoji recordEmojiUsage hook).
                if (SearchSrv != null) SearchSrv!!.recordEnglishUsage(pickedWord)
            }

            // ENGLISH_KB.md #0 / §2a: both pick paths appended a trailing space.
            // Arm the punctuation swap so the next "," produces "word," not "word ,".
            mPickedAutoSpace = true

            resetTempEnglishWord()

            clearSuggestions()
        }

        if (currentSoftKeyboard!!.contains("wb")) {
            if (ic != null && mPredictionOn) ic.setComposingText("", 0)
        }
    }


    override fun swipeRight() {
        //if (mCompletionOn) {
        pickHighlightedCandidate()
        //}
    }

    override fun swipeLeft() {
        handleBackspace()
    }

    override fun moveCaretBy(steps: Int) {
        if (steps == 0 || mComposing == null || mComposing!!.length > 0) {
            return
        }

        val keyCode = if (steps < 0)
            KeyEvent.KEYCODE_DPAD_LEFT
        else
            KeyEvent.KEYCODE_DPAD_RIGHT
        val count = abs(steps)
        for (i in 0..<count) {
            keyDownUp(keyCode, false)
        }
    }

    override fun swipeDown() {
        handleClose()
    }

    override fun swipeUp() {
        handleOptions()
    }

    /**
     * First method to call after key press
     */
    override fun onPress(primaryCode: Int) {
        //Log.i(TAG, "onPress(): code = " + primaryCode + ", hasVibration = " + hasVibration + ", mVibrator = " + (mVibrator != null ? "valid" : "null"));

        // Record key press time (press down)
        //keyPressTime = System.currentTimeMillis();
        // To identify the source of character (Software keyboard or physical keyboard)
        // onPress() is only called from soft keyboard, so reset hasPhysicalKeyPressed

        hasPhysicalKeyPressed = false

        if (hasDistinctMultitouch && primaryCode == LIMEBaseKeyboard.KEYCODE_SHIFT) {
            hasShiftPress = true
            hasShiftCombineKeyPressed = false
            handleShift()
        } else if (hasDistinctMultitouch && hasShiftPress) {
            hasShiftCombineKeyPressed = true
        }
        doVibrateSound(primaryCode)
    }

    @get:Suppress("deprecation")
    private val vibrator: Vibrator?
        /**
         * Get Vibrator instance compatible with all API levels.
         * Uses VibratorManager for all API levels (recommended approach).
         */
        get() {
            if (mVibrator == null) {
                Log.w(
                    TAG,
                    "getVibrator() - mVibrator is null, re-initializing, API level: " + Build.VERSION.SDK_INT
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // API 31+: use VibratorManager
                    val vibratorManager =
                        getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager?
                    if (vibratorManager != null) {
                        mVibrator = vibratorManager.getDefaultVibrator()
                    }
                } else {
                    // API 22-30: use deprecated VIBRATOR_SERVICE
                    mVibrator =
                        getSystemService(VIBRATOR_SERVICE) as Vibrator?
                }
                Log.i(
                    TAG,
                    "getVibrator() - mVibrator = " + (if (mVibrator != null) "valid" else "null")
                )
            }
            return mVibrator
        }

    /**
     * Map vibration duration preference to a predefined VibrationEffect for API 29-30.
     * Predefined effects are optimized for device haptic hardware (especially Pixel LRA motors).
     * Vibrate level mapping:
     * 20ms (Very Weak)    -> EFFECT_TICK        (light tap)
     * 30ms (Weak)         -> EFFECT_TICK        (light tap)
     * 40ms (Medium)       -> EFFECT_CLICK       (standard click)
     * 50ms (Strong)       -> EFFECT_HEAVY_CLICK (strong thud)
     * 60ms (Very Strong)  -> EFFECT_HEAVY_CLICK (strong thud)
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private fun mapDurationToVibrationEffect(duration: Long): Int {
        if (duration <= 30) {
            return VibrationEffect.EFFECT_TICK // light tap
        } else if (duration <= 40) {
            return VibrationEffect.EFFECT_CLICK // standard click
        } else {
            return VibrationEffect.EFFECT_HEAVY_CLICK // strong thud
        }
    }

    /**
     * Vibrate with specified duration, compatible with all API levels.
     * API 31+: uses performHapticFeedback on the keyboard view — Vibrator.vibrate() from
     * an InputMethodService suffers USAGE_UNKNOWN classification on Android 12+
     * (API 31+) because the system treats services as background processes, which
     * restricts vibration. The View haptic pipeline avoids this: mInputView is
     * attached to the IME window (user-interactive context) so it is not subject
     * to background restrictions. VibrationAttributes.USAGE_TOUCH would be the
     * alternative, but vibrate(VibrationEffect, VibrationAttributes) requires API 33,
     * making performHapticFeedback the only clean solution for API 31-32 as well.
     * API 29-30: uses predefined VibrationEffect (hardware-optimized for Pixel LRA motors).
     * API 26-28: uses VibrationEffect.createOneShot().
     * API <26: uses deprecated vibrate(long).
     */
    @Suppress("deprecation")
    private fun vibrate(duration: Long) {
        if (duration <= 0) {
            Log.w(TAG, "vibrate() called with invalid duration: " + duration)
            return
        }

        // API 31+: use performHapticFeedback on the keyboard view.
        // Vibrator.vibrate() from a service is classified as USAGE_UNKNOWN on Android 12+,
        // making it subject to background vibration restrictions. Lowering the threshold from
        // API 33 to API 31 (where restrictions began) covers API 31-32 with the reliable path.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (mInputView != null) {
                // FLAG_IGNORE_VIEW_SETTING: fire even if the view's hapticFeedbackEnabled is off.
                // FLAG_IGNORE_GLOBAL_SETTING is deprecated on API 33+ and has no effect;
                // the system always respects the global haptic setting on API 33+.
                mInputView!!.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                )
            }
            return
        }

        val vibrator = this.vibrator
        if (vibrator == null) {
            Log.e(TAG, "vibrate() - vibrator is null! Failed to get vibrator service.")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29-30: use predefined effects optimized for device haptic hardware
                val effectId = mapDurationToVibrationEffect(duration)
                val effect = VibrationEffect.createPredefined(effectId)
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // API 26-28: use createOneShot
                val effect =
                    VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            } else {
                // API < 26
                vibrator.vibrate(duration)
            }
        } catch (e: Exception) {
            Log.e(TAG, "vibrate() failed to trigger vibration: " + e.message, e)
        }
    }

    fun doVibrateSound(primaryCode: Int) {
        //Log.i(TAG, "doVibrateSound() called with primaryCode: " + primaryCode + ", hasVibration: " + hasVibration);

        if (hasVibration) {
            //Jeremy '11,9,1 add preference on vibrate level
            val vibrateLevel = mLIMEPref!!.getVibrateLevel().toLong()
            //Log.i(TAG, "doVibrateSound() - hasVibration=true, vibrateLevel: " + vibrateLevel + "ms");
            vibrate(vibrateLevel)
        }

        if (hasSound && mAudioManager != null) {
            var sound = AudioManager.FX_KEYPRESS_STANDARD
            when (primaryCode) {
                LIMEBaseKeyboard.KEYCODE_DELETE -> sound = AudioManager.FX_KEYPRESS_DELETE
                MY_KEYCODE_ENTER -> sound = AudioManager.FX_KEYPRESS_RETURN
                MY_KEYCODE_SPACE -> sound = AudioManager.FX_KEYPRESS_SPACEBAR
            }
            val FX_VOLUME = 1.0f
            mAudioManager!!.playSoundEffect(sound, FX_VOLUME)
            //Log.i(TAG, "doVibrateSound() - sound played, sound code: " + sound);
        }
    }

    /**
     * Last method to execute when key release
     */
    override fun onRelease(primaryCode: Int) {
        if (DEBUG) Log.i(TAG, "onRelease(): code = " + primaryCode)
        if (hasDistinctMultitouch && primaryCode == LIMEBaseKeyboard.KEYCODE_SHIFT) {
            hasShiftPress = false
            if (hasShiftCombineKeyPressed) {
                hasShiftCombineKeyPressed = false
                updateShiftKeyState(getCurrentInputEditorInfo())
            }
        } else if (hasDistinctMultitouch && !hasShiftPress) {
            updateShiftKeyState(getCurrentInputEditorInfo())
        }
    }

    /*
    public boolean isValidTime(Date target) {
        Calendar srcCal = Calendar.getInstance();
        srcCal.setTime(new Date());
        Calendar destCal = Calendar.getInstance();
        destCal.setTime(target);

        return srcCal.getTimeInMillis() - destCal.getTimeInMillis() < 1800000;

    }
*/
    override fun onDestroy() {
        if (DEBUG) Log.i(TAG, "onDestroy()")

        // Stop monitoring IME changes when service is destroyed
        stopMonitoringIMEChanges()
        if (mDictationController != null) {
            mDictationController!!.destroy()
            mDictationController = null
        }
        cancelKeepassAutoLock()
        unregisterKeepassImeReceiver()
        unregisterVoiceInputReceiver()

        //jeremy 12,4,21 need to check again---
        //clearComposing(true); see no need to do this '12,4,21
        super.onDestroy()
    }

    /*
    @Override
    public void onUpdateCursor(Rect newCursor) {
        if(DEBUG)
            Log.i(TAG, "onUpdateCursor(): Top:"
                + newCursor.top + ". Right:" + newCursor.right
                + ". bottom:" + newCursor.bottom + ". left:" + newCursor.left );


                if (mCandidateView != null) {
                    // copy into a concrete list of Mapping so the rest of the code can mutate/inspect safely
                    mCandidateList = new LinkedList<>(suggestions == null ? Collections.emptyList() : suggestions);
                    try {
                        if (mCandidateList.size() > 1 && mCandidateList.get(1).isExactMatchToCodeRecord()) {
                            selectedCandidate = mCandidateList.get(1);
                        } else if (!mCandidateList.isEmpty()) {
                            selectedCandidate = mCandidateList.get(0);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in suggestion processing", e);
                    }
                    mCandidateView.setSuggestions(mCandidateList, showNumber, diplaySelkey);
        if (mInputView == null) return;
        if (DEBUG)
            Log.i(TAG, "updateInputViewShown(): mInputView.isShown(): " + mInputView.isShown());
        super.updateInputViewShown();
    
        // Don't restore keyboard view here - only restore when user explicitly touches
        // the soft keyboard area (candidate view or InputView container)
        // This prevents restoring when InputView visibility changes but user is still using physical keyboard
    
        if (!mInputView.isShown() && !hasPhysicalKeyPressed)
            hideCandidateView();
    }


    @Override
    public void onFinishInputView(boolean finishingInput) {
        if (DEBUG)
            Log.i(TAG, "onFinishInputView()");
        super.onFinishInputView(finishingInput);
        cancelInlineDictationIfActive();
        resetEmojiKeyboardState();
        hideCandidateView(); //Jeremy '12,5,7 hideCandiate when inputview is closed but not yet leave the original field (onfinishinput() will not called).
    }

    / **
     *  start voice input
     *  Prefer switching to a voice IME. RecognizerIntent is only the fallback.
     */
    fun startVoiceInput() {
        if (DEBUG) Log.i(TAG, "startVoiceInput(): API level: " + Build.VERSION.SDK_INT)

        val voiceIntent = this.voiceIntent
        val voiceID = LIMEUtilities.isVoiceSearchServiceExist(getBaseContext())
        val recognizerAvailable = true
        if (!isRecognizerFallbackAvailable(voiceIntent)) {
            Log.w(
                TAG,
                "startVoiceInput(): recognizer fallback was not visible during preflight; will still try helper activity if delegated VoiceIME is unavailable"
            )
        }
        val route = chooseRoute(
            this.isInlineDictationFeatureEnabled,
            VoiceInputMode.AUTO,
            this.inlineDictationPermissionState,
            this.isInlineDictationAvailable,
            voiceID != null,
            recognizerAvailable
        )
        Log.i(
            TAG, ("startVoiceInput(): voiceID=" + voiceID
                    + ", activeIM=" + activeIM
                    + ", route=" + route
                    + ", fallbackLanguage=" + voiceIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        )

        when (route) {
            VoiceInputRoute.INLINE_DICTATION -> {
                startInlineDictationOrFallback(voiceIntent, voiceID)
                return
            }

            VoiceInputRoute.VOICE_IME -> {
                startDelegatedVoiceInput(voiceIntent, voiceID)
                return
            }

            VoiceInputRoute.RECOGNIZER_INTENT -> {
                startRecognizerFallback(voiceIntent)
                return
            }

            VoiceInputRoute.UNAVAILABLE -> showLimeToast("Voice recognition not available on this device")
        }
    }

    private val isInlineDictationFeatureEnabled: Boolean
        get() {
            try {
                return getResources().getBoolean(R.bool.inline_dictation_feature_enabled)
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "isInlineDictationFeatureEnabled(): resource unavailable: " + e.message
                )
                return false
            }
        }

    private val isInlineDictationAvailable: Boolean
        get() = mDictationController != null && mDictationController!!.isRecognitionAvailable()

    private val inlineDictationPermissionState: VoicePermissionState
        get() {
            if (hasRecordAudioPermission(this)) {
                return VoicePermissionState.GRANTED
            }
            return if (wasRecordAudioPermissionPrompted(this))
                VoicePermissionState.DENIED_DO_NOT_ASK_AGAIN
            else
                VoicePermissionState.NOT_REQUESTED
        }

    private fun startInlineDictationOrFallback(voiceIntent: Intent?, voiceID: String?) {
        if (mDictationController != null && mDictationController!!.isRecognitionAvailable()) {
            mIsVoiceInputActive = true
            mDictationController!!.start(this.voiceRecognitionLanguageTag)
            return
        }
        if (DEBUG) Log.i(
            TAG,
            "startInlineDictationOrFallback(): inline controller unavailable, using delegated fallback"
        )
        startDelegatedVoiceInputOrRecognizerFallback(voiceIntent, voiceID)
    }

    private fun startDelegatedVoiceInputOrRecognizerFallback(
        voiceIntent: Intent?,
        voiceID: String?
    ) {
        if (voiceID != null) {
            startDelegatedVoiceInput(voiceIntent, voiceID)
        } else {
            startRecognizerFallback(voiceIntent)
        }
    }

    private fun startDelegatedVoiceInput(voiceIntent: Intent?, voiceID: String?) {
        if (voiceID == null) {
            startRecognizerFallback(voiceIntent)
            return
        }
        if (isGoogleSpeechServicesVoiceIme(voiceID)) {
            Log.w(
                TAG,
                "startDelegatedVoiceInput(): Google Speech Services VoiceIME cannot be direct-switched safely; using RecognizerIntent"
            )
            startRecognizerFallback(voiceIntent)
            return
        }
        if (DEBUG) Log.i(TAG, "startDelegatedVoiceInput(): Found voice IME: " + voiceID)

        // Get LIME IME ID for switching back
        if (mLIMEId == null) {
            mLIMEId = LIMEUtilities.getLIMEID(getBaseContext())
        }

        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?
        if (imm != null) {
            startMonitoringIMEChanges()
            try {
                mIsVoiceInputActive = true
                this.switchInputMethod(voiceID)
                if (DEBUG) Log.i(
                    TAG,
                    "startDelegatedVoiceInput(): Called switchInputMethod(" + voiceID + ")"
                )

                Handler(Looper.getMainLooper()).postDelayed(Runnable {
                    val currentIME = this.currentDefaultInputMethod
                    if (DEBUG) Log.i(
                        TAG,
                        "startDelegatedVoiceInput(): Current IME after switch: " + currentIME + " (expected: " + voiceID + ")"
                    )
                    if (voiceID == currentIME) {
                        if (DEBUG) Log.i(
                            TAG,
                            "startDelegatedVoiceInput(): Successfully switched to voice IME"
                        )
                        scheduleModernVoiceImeRecovery(voiceID, voiceIntent)
                    } else {
                        if (DEBUG) Log.w(
                            TAG,
                            "startDelegatedVoiceInput(): switchInputMethod() didn't work (still on " + currentIME + "), falling back to RecognizerIntent"
                        )
                        stopMonitoringIMEChanges()
                        mIsVoiceInputActive = false
                        startRecognizerFallback(voiceIntent)
                    }
                }, 200)

                return
            } catch (e: SecurityException) {
                if (DEBUG) Log.e(
                    TAG,
                    "startDelegatedVoiceInput(): SecurityException switching to voice IME: " + e.message,
                    e
                )
                stopMonitoringIMEChanges()
                mIsVoiceInputActive = false
            } catch (e: Exception) {
                if (DEBUG) Log.e(
                    TAG,
                    "startDelegatedVoiceInput(): Exception switching to voice IME: " + e.message,
                    e
                )
                stopMonitoringIMEChanges()
                mIsVoiceInputActive = false
            }
        } else if (DEBUG) {
            Log.e(TAG, "startDelegatedVoiceInput(): InputMethodManager is null")
        }
        startRecognizerFallback(voiceIntent)
    }

    private fun scheduleModernVoiceImeRecovery(voiceID: String?, voiceIntent: Intent?) {
        if (!isGoogleSpeechServicesVoiceIme(voiceID)) {
            return
        }
        Handler(Looper.getMainLooper()).postDelayed(Runnable {
            if (mIsVoiceInputActive) {
                val currentIME = this.currentDefaultInputMethod
                if (voiceID == currentIME) {
                    Log.w(
                        TAG,
                        "scheduleModernVoiceImeRecovery(): Google Speech Services VoiceIME did not auto-start; switching back to LIME and using RecognizerIntent"
                    )
                    switchBackToLIME()
                    Handler(Looper.getMainLooper()).postDelayed(
                        Runnable { startRecognizerFallback(voiceIntent) }, 300
                    )
                }
            }
        }, 1500)
    }

    private fun isGoogleSpeechServicesVoiceIme(voiceID: String?): Boolean {
        return ("com.google.android.tts/com.google.android.apps.speech.tts.googletts.settings.asr.voiceime.VoiceInputMethodService"
                == voiceID)
    }

    private fun startRecognizerFallback(voiceIntent: Intent?) {
        try {
            launchRecognizerIntent(voiceIntent)
            if (DEBUG) Log.i(
                TAG,
                "startRecognizerFallback(): launchRecognizerIntent() returned successfully"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error launching recognizer intent", e)
        }
    }

    private fun isRecognizerFallbackAvailable(voiceIntent: Intent?): Boolean {
        try {
            val intent = if (voiceIntent != null) voiceIntent else this.voiceIntent
            return getPackageManager() != null &&
                    !getPackageManager().queryIntentActivities(intent, 0).isEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "isRecognizerFallbackAvailable(): unable to query recognizer: " + e.message)
            return true
        }
    }

    private val voiceIntent: Intent
        get() {
            val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            voiceIntent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            val languageTag = this.voiceRecognitionLanguageTag
            voiceIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            Log.i(
                TAG,
                "getVoiceIntent() - Using voice recognition language: " + languageTag
            )

            // Add prompt text
            voiceIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")

            // Ensure we get results back
            voiceIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            return voiceIntent
        }

    private val voiceRecognitionLanguageTag: String
        get() {
            var systemLocale: Locale? = null
            try {
                systemLocale =
                    ConfigurationCompat.getLocales(getResources().getConfiguration()).get(0)
            } catch (e: Exception) {
                // getResources() or getConfiguration() may throw in test env
            }
            return resolveVoiceRecognitionLanguageTag(systemLocale)
        }

    /**
     * Launch RecognizerIntent as fallback for voice input
     */
    private fun launchRecognizerIntent(voiceIntent: Intent?) {
        var voiceIntent = voiceIntent
        if (voiceIntent == null) {
            Log.e(TAG, "launchRecognizerIntent(): voiceIntent is NULL! Creating default intent")
            voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            voiceIntent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            voiceIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, this.voiceRecognitionLanguageTag)
            //voiceIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now");
            voiceIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        val language = voiceIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)
        Log.i(
            TAG,
            "launchRecognizerIntent(): Intent language: " + language + ", Intent action: " + voiceIntent.getAction() +
                    ", API level: " + Build.VERSION.SDK_INT
        )

        // Use helper Activity to launch RecognizerIntent for all API levels
        // InputMethodService cannot receive onActivityResult, so we need VoiceInputActivity
        // to handle the result and broadcast it back to LIMEService
        try {
            val helperIntent = Intent(this, VoiceInputActivity::class.java)
            helperIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            helperIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // Pass the configured voiceIntent to VoiceInputActivity
            helperIntent.putExtra(VoiceInputActivity.EXTRA_VOICE_INTENT, voiceIntent)
            Log.i(
                TAG,
                "launchRecognizerIntent(): Passing voiceIntent to VoiceInputActivity with language: " + language
            )
            startActivity(helperIntent)
            mIsVoiceInputActive = true
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "launchRecognizerIntent(): VoiceInputActivity not found: " + e.message, e)
            showLimeToast("Voice input activity not found")
        } catch (e: SecurityException) {
            Log.e(
                TAG,
                "launchRecognizerIntent(): SecurityException launching VoiceInputActivity: " + e.message,
                e
            )
            showLimeToast("Cannot launch voice input (security restriction)")
        } catch (e: Exception) {
            Log.e(
                TAG,
                "launchRecognizerIntent(): Failed to launch VoiceInputActivity: " + e.message,
                e
            )
            showLimeToast("Voice input unavailable: " + e.message)
        }
    }

    private val currentDefaultInputMethod: String?
        get() {
            try {
                return Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD
                )
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "getCurrentDefaultInputMethod(): Unable to read default IME: " + e.message
                )
                return null
            }
        }


    /**
     * Start monitoring IME changes to switch back to LIME when voice input ends
     */
    private fun startMonitoringIMEChanges() {
        if (mInputMethodObserver != null) {
            return  // Already monitoring
        }

        mInputMethodObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (!mIsVoiceInputActive) {
                    return
                }

                val currentIME = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD
                )

                if (DEBUG) Log.d(TAG, "IME changed to: " + currentIME + ", LIME ID: " + mLIMEId)

                // If we're back on LIME, stop monitoring
                if (mLIMEId != null && mLIMEId == currentIME) {
                    stopMonitoringIMEChanges()
                    return
                }

                // Check if it's a voice IME - if so, wait
                val voiceID = LIMEUtilities.isVoiceSearchServiceExist(getBaseContext())
                if (voiceID != null && voiceID == currentIME) {
                    // Still on voice IME, wait
                    return
                }

                // IME changed to something else (not voice, not LIME), switch back to LIME
                // This handles the case where voice recognition ends and IME might have changed
                if (mLIMEId != null && mLIMEId != currentIME) {
                    // Delay slightly to allow voice recognition to complete
                    Handler(Looper.getMainLooper()).postDelayed(Runnable {
                        val checkIME = Settings.Secure.getString(
                            getContentResolver(),
                            Settings.Secure.DEFAULT_INPUT_METHOD
                        )
                        if (mLIMEId != null && mLIMEId != checkIME) {
                            switchBackToLIME()
                        }
                    }, 500) // Delay to allow voice recognition to complete
                }
            }
        }

        // Register observer
        getContentResolver().registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD),
            false,
            mInputMethodObserver!!
        )

        // Also set up a timeout handler to switch back after a reasonable time
        // This handles cases where IME doesn't change but voice recognition completes
        Handler(Looper.getMainLooper()).postDelayed(Runnable {
            if (mIsVoiceInputActive) {
                val currentIME = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD
                )
                if (mLIMEId != null && mLIMEId != currentIME) {
                    switchBackToLIME()
                } else {
                    // Already back on LIME, just stop monitoring
                    stopMonitoringIMEChanges()
                }
            }
        }, 30000) // 30 second timeout

        if (DEBUG) Log.i(TAG, "startMonitoringIMEChanges(): Started monitoring IME changes")
    }

    /**
     * Stop monitoring IME changes
     */
    private fun stopMonitoringIMEChanges() {
        if (mInputMethodObserver != null) {
            getContentResolver().unregisterContentObserver(mInputMethodObserver!!)
            mInputMethodObserver = null
            mIsVoiceInputActive = false
            if (DEBUG) Log.i(TAG, "stopMonitoringIMEChanges(): Stopped monitoring IME changes")
        }
    }

    /**
     * Switch back to LIME IME
     */
    private fun switchBackToLIME() {
        if (mLIMEId == null) {
            mLIMEId = LIMEUtilities.getLIMEID(getBaseContext())
        }
        if (mLIMEId == null) {
            if (DEBUG) Log.e(TAG, "switchBackToLIME(): LIME ID is null")
            stopMonitoringIMEChanges()
            return
        }

        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?
        if (imm == null) {
            if (DEBUG) Log.e(TAG, "switchBackToLIME(): InputMethodManager is null")
            stopMonitoringIMEChanges()
            return
        }

        // Try to switch back to LIME using InputMethodService.switchInputMethod()
        // This is the recommended method for IMEs and works on all API levels (21-36)
        // setInputMethod() is deprecated on API 28+ and doesn't work on API 36
        try {
            this.switchInputMethod(mLIMEId)
            if (DEBUG) Log.i(
                TAG,
                "switchBackToLIME(): Switched back to LIME IME using switchInputMethod()"
            )
        } catch (e: Exception) {
            if (DEBUG) Log.e(TAG, "switchBackToLIME(): Failed to switch back: " + e)
        }

        // Stop monitoring after switching back
        stopMonitoringIMEChanges()
    }

    /**
     * Try to commit voice text, retrying up to 3 times with 200ms delays if InputConnection is null.
     * Falls back to storing as pending text for onStartInputView() if all retries fail.
     */
    private fun commitVoiceTextWithRetry(text: String?, attempt: Int) {
        val ic = getCurrentInputConnection()
        val textToCommit = prepareVoiceTextForCommit(text)
        if (ic != null) {
            try {
                ic.commitText(textToCommit, 1)
                Log.i(TAG, "commitVoiceTextWithRetry(): Committed voice text on attempt " + attempt)
            } catch (e: Exception) {
                Log.e(TAG, "commitVoiceTextWithRetry(): Failed to commit: " + e.message)
                mPendingVoiceText = textToCommit
            }
        } else if (attempt < 3) {
            Log.w(TAG, "commitVoiceTextWithRetry(): IC null, retry " + (attempt + 1) + " in 200ms")
            Handler(Looper.getMainLooper()).postDelayed(
                Runnable { commitVoiceTextWithRetry(text, attempt + 1) }, 200
            )
            return  // Don't clear mIsVoiceInputActive yet
        } else {
            Log.w(
                TAG,
                "commitVoiceTextWithRetry(): IC still null after 3 retries, storing as pending"
            )
            mPendingVoiceText = textToCommit
        }
        mIsVoiceInputActive = false
    }

    private fun prepareVoiceTextForCommit(text: String?): String? {
        if (text == null || text.isEmpty()) {
            return text
        }
        try {
            if (mLIMEPref != null && mLIMEPref!!.getHanCovertOption() != 0 && SearchSrv != null) {
                val converted = SearchSrv!!.hanConvert(text)
                Log.i(TAG, "prepareVoiceTextForCommit(): Applied Han conversion to voice result")
                return converted
            }
        } catch (e: Exception) {
            Log.w(TAG, "prepareVoiceTextForCommit(): Han conversion skipped: " + e.message)
        }
        return text
    }

    override fun onDictationStateChanged(state: DictationState?) {
        if (DEBUG) {
            Log.i(TAG, "onDictationStateChanged(): " + state)
        }
        showDictationStatus(state, null)
    }

    override fun onDictationPartialText(text: String?) {
        if (DEBUG) {
            Log.i(TAG, "onDictationPartialText(): " + text)
        }
        showDictationStatus(DictationState.PARTIAL, text)
    }

    override fun onDictationFinalText(text: String?) {
        clearDictationStatus()
        if (text != null && !text.isEmpty()) {
            commitVoiceTextWithRetry(text, 0)
        } else {
            mIsVoiceInputActive = false
        }
    }

    override fun onDictationError(errorCode: Int, shouldFallback: Boolean) {
        Log.w(
            TAG,
            "onDictationError(): errorCode=" + errorCode + ", shouldFallback=" + shouldFallback
        )
        showDictationStatus(DictationState.ERROR, null)
        mIsVoiceInputActive = false
        if (!shouldFallback) {
            return
        }
        val voiceIntent = this.voiceIntent
        val voiceID = LIMEUtilities.isVoiceSearchServiceExist(getBaseContext())
        startDelegatedVoiceInputOrRecognizerFallback(voiceIntent, voiceID)
    }

    override fun onDictationCancelled() {
        clearDictationStatus()
        mIsVoiceInputActive = false
    }

    private fun cancelInlineDictationIfActive() {
        if (mDictationController != null && mDictationController!!.isActive) {
            mDictationController!!.cancel()
        }
    }

    private fun showDictationStatus(state: DictationState?, text: String?) {
        if (mCandidateView != null) {
            mCandidateView!!.showDictationStatus(state, text)
            showCandidateView()
            refreshCandidateInputContainer()
        }
    }

    private fun clearDictationStatus() {
        if (mCandidateView != null) {
            mCandidateView!!.clearDictationStatus()
            refreshCandidateInputContainer()
        }
    }

    /**
     * Register BroadcastReceiver to receive voice input results from VoiceInputActivity
     * Note: RECEIVER_NOT_EXPORTED flag is only available on API 33+, so we use conditional registration
     * Android 16+ may have delivery restrictions, so we handle null InputConnection by queuing the text
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag", "RegisterReceiverFlag")
    private fun registerVoiceInputReceiver() {
        if (mVoiceInputReceiver != null) {
            return  // Already registered
        }

        mVoiceInputReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                if (DEBUG) {
                    Log.i(
                        TAG,
                        "registerVoiceInputReceiver().onReceive(): Action: " + intent.getAction()
                    )
                }

                if (ACTION_VOICE_RESULT == intent.getAction()) {
                    val recognizedText = intent.getStringExtra(EXTRA_RECOGNIZED_TEXT)
                    if (DEBUG) {
                        Log.i(
                            TAG,
                            "registerVoiceInputReceiver().onReceive(): Recognized text: " + recognizedText
                        )
                    }

                    if (recognizedText != null && !recognizedText.isEmpty()) {
                        // Clear static field since we received it via broadcast
                        consumePendingVoiceText()
                        Log.i(
                            TAG,
                            "registerVoiceInputReceiver().onReceive(): Processing recognized text: " + recognizedText
                        )

                        // Try to commit with retry logic
                        commitVoiceTextWithRetry(recognizedText, 0)
                    } else if (recognizedText == null) {
                        Log.w(
                            TAG,
                            "registerVoiceInputReceiver().onReceive(): Recognized text is null"
                        )
                        mIsVoiceInputActive = false
                    }
                }
            }
        }

        val filter: IntentFilter = IntentFilter(ACTION_VOICE_RESULT)
        // On API 33+ (TIRAMISU), must specify RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED
        // This receiver is for internal app communication only, so use RECEIVER_NOT_EXPORTED
        // For API < 33, the flag doesn't exist, so we register without it (lint warning suppressed above)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(mVoiceInputReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                // RECEIVER_NOT_EXPORTED flag is not available on API < 33
                // This is safe because the broadcast is internal to the app only
                // Lint warning suppressed at method level with @SuppressLint
                // noinspection UnspecifiedRegisterReceiverFlag
                registerReceiver(mVoiceInputReceiver, filter)
            }
            Log.i(
                TAG,
                "registerVoiceInputReceiver(): Registered receiver successfully on API " + Build.VERSION.SDK_INT
            )
        } catch (e: Exception) {
            Log.e(TAG, "registerVoiceInputReceiver(): Failed to register receiver: " + e.message)
            mVoiceInputReceiver = null
        }
    }

    /**
     * Unregister BroadcastReceiver for voice input results
     */
    private fun unregisterVoiceInputReceiver() {
        if (mVoiceInputReceiver != null) {
            try {
                unregisterReceiver(mVoiceInputReceiver)
                mVoiceInputReceiver = null
                Log.i(TAG, "unregisterVoiceInputReceiver(): Unregistered receiver")
            } catch (e: Exception) {
                Log.w(TAG, "unregisterVoiceInputReceiver(): Failed to unregister: " + e.message)
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag", "RegisterReceiverFlag")
    private fun registerKeepassImeReceiver() {
        if (mKeepassImeReceiver != null) {
            return
        }

        mKeepassImeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                when (intent.action) {
                    LimeKeepassImeUnlockActivity.actionUnlockResult -> {
                        if (intent.getBooleanExtra(LimeKeepassImeUnlockActivity.extraUnlocked, false)) {
                            startKeepassSelection()
                        }
                    }
                    LimeKeepassImeSelectActivity.actionSelectResult -> {
                        if (!intent.getBooleanExtra(LimeKeepassImeSelectActivity.extraSelected, false)) {
                            return
                        }
                        mKeepassSelectedTitle =
                            intent.getStringExtra(LimeKeepassImeSelectActivity.extraTitle).orEmpty()
                        mKeepassSelectedUsername =
                            intent.getStringExtra(LimeKeepassImeSelectActivity.extraUsername).orEmpty()
                        mKeepassSelectedPassword =
                            intent.getStringExtra(LimeKeepassImeSelectActivity.extraPassword).orEmpty()
                        mKeepassSelectedUrl =
                            intent.getStringExtra(LimeKeepassImeSelectActivity.extraUrl).orEmpty()
                        mKeepassSelectedNotes =
                            intent.getStringExtra(LimeKeepassImeSelectActivity.extraNotes).orEmpty()
                        showKeepassFieldPanel()
                    }
                    KeepassAutofillLock.actionLocked -> {
                        handleKeepassLocked(
                            intent.getStringExtra(KeepassAutofillLock.extraLockReason)
                                ?: KeepassAutofillLock.lockReasonManual
                        )
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(LimeKeepassImeUnlockActivity.actionUnlockResult)
            addAction(LimeKeepassImeSelectActivity.actionSelectResult)
            addAction(KeepassAutofillLock.actionLocked)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(mKeepassImeReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(mKeepassImeReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "registerKeepassImeReceiver(): Failed to register receiver", e)
            mKeepassImeReceiver = null
        }
    }

    private fun unregisterKeepassImeReceiver() {
        if (mKeepassImeReceiver != null) {
            try {
                unregisterReceiver(mKeepassImeReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "unregisterKeepassImeReceiver(): Failed to unregister", e)
            } finally {
                mKeepassImeReceiver = null
            }
        }
    }

    private class KeyboardTheme(val mName: String?, val mThemeId: Int, val mStyleId: Int)

    private var mKeyboardThemeIndex = -1

    private fun createDialogBuilder(): AlertDialog.Builder {
        if (this.isEffectiveDarkTheme) {
            return AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
        }
        return AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
    }

    private val isEffectiveDarkTheme: Boolean
        get() {
            if (mKeyboardThemeIndex == 1) return true
            if (mKeyboardThemeIndex == 6) {
                val uiMode = (getResources().getConfiguration().uiMode
                        and Configuration.UI_MODE_NIGHT_MASK)
                return uiMode == Configuration.UI_MODE_NIGHT_YES
            }
            return false
        }

    class EmojiPanelColors(
        @JvmField val searchBackground: Int, val searchHint: Int, @JvmField val searchText: Int,
        @JvmField val searchIcon: Int, @JvmField val iconText: Int, @JvmField val categoryHighlight: Int
    )

    private fun currentEmojiPanelColors(): EmojiPanelColors {
        val fallbackAccent = if (this.isEffectiveDarkTheme) 0x33FFFFFF else 0x22000000
        val accent = if (this.isFollowSystemTheme) resolveSystemAccentColor(fallbackAccent) else 0
        return emojiPanelColorsForTheme(
            mKeyboardThemeIndex,
            this.isEffectiveDarkTheme, accent
        )
    }

    private val isFollowSystemTheme: Boolean
        get() = mKeyboardThemeIndex == 6

    private fun applyFollowSystemAccentColors() {
        if (!this.isFollowSystemTheme) return

        val accent = resolveSystemAccentColor(0)
        if (!isUsableAccentColor(accent)) return

        applyFollowSystemAccentColors(accent, this.isEffectiveDarkTheme)
    }

    private fun applyFollowSystemAccentColors(accent: Int, darkTheme: Boolean) {
        if (!isUsableAccentColor(accent) || isAppliedFollowSystemAccentCurrent(
                accent,
                darkTheme
            )
        ) return

        if (mInputView != null) {
            mInputView!!.applyFollowSystemAccentColor(accent, darkTheme)
        }
        if (mCandidateViewInInputView != null) {
            mCandidateViewInInputView!!.applyFollowSystemAccentColor(accent, darkTheme)
        }
        if (mCandidateView != null && mCandidateView !== mCandidateViewInInputView) {
            mCandidateView!!.applyFollowSystemAccentColor(accent, darkTheme)
        }
        mAppliedFollowSystemAccent = accent
        mAppliedFollowSystemDarkTheme = darkTheme
        mAppliedFollowSystemInputView = mInputView
        mAppliedFollowSystemCandidateView = mCandidateView
        mAppliedFollowSystemEmbeddedCandidateView = mCandidateViewInInputView
    }

    private fun isAppliedFollowSystemAccentCurrent(accent: Int, darkTheme: Boolean): Boolean {
        return mAppliedFollowSystemAccent == accent && mAppliedFollowSystemDarkTheme == darkTheme && mAppliedFollowSystemInputView === mInputView && mAppliedFollowSystemCandidateView === mCandidateView && mAppliedFollowSystemEmbeddedCandidateView === mCandidateViewInInputView
    }

    private fun clearAppliedFollowSystemAccentState() {
        mAppliedFollowSystemAccent = 0
        mAppliedFollowSystemInputView = null
        mAppliedFollowSystemCandidateView = null
        mAppliedFollowSystemEmbeddedCandidateView = null
    }

    fun applyFollowSystemAccentColorsForTesting(accent: Int, darkTheme: Boolean) {
        applyFollowSystemAccentColors(accent, darkTheme)
    }

    private fun resolveSystemAccentColor(fallbackColor: Int): Int {
        val systemSeed = resolveSeedColor(this, 0)
        if (isUsableAccentColor(systemSeed)) {
            return systemSeed
        }

        val dynamicColorContext = DynamicColors.wrapContextIfAvailable(
            this,
            dynamicColorOptions(this)
        )
        var resolved = resolveThemeColor(
            dynamicColorContext,
            com.google.android.material.R.attr.colorPrimary,
            0
        )
        if (!isUsableAccentColor(resolved)) {
            resolved = resolveThemeColor(
                dynamicColorContext,
                com.google.android.material.R.attr.colorSecondary,
                0
            )
        }
        if (!isUsableAccentColor(resolved)) {
            resolved = resolveThemeColor(dynamicColorContext, android.R.attr.colorAccent, 0)
        }
        if (!isUsableAccentColor(resolved)) {
            resolved = resolveThemeColor(com.google.android.material.R.attr.colorPrimary, 0)
        }
        if (!isUsableAccentColor(resolved)) {
            resolved = resolveThemeColor(com.google.android.material.R.attr.colorSecondary, 0)
        }
        if (!isUsableAccentColor(resolved)) {
            resolved = resolveThemeColor(android.R.attr.colorAccent, 0)
        }
        return if (isUsableAccentColor(resolved)) resolved else fallbackColor
    }

    private fun resolveThemeColor(attr: Int, fallbackColor: Int): Int {
        return resolveThemeColor(this, attr, fallbackColor)
    }

    private fun resolveThemeColor(context: Context, attr: Int, fallbackColor: Int): Int {
        val value = TypedValue()
        if (context.getTheme().resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) {
                return ContextCompat.getColor(context, value.resourceId)
            }
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT
            ) {
                return value.data
            }
        }
        return fallbackColor
    }

    private val keyboardTheme: Int
        get() {
            var idx = mKeyboardThemeIndex
            if (idx == 6) idx = if (this.isEffectiveDarkTheme) 1 else 0
            if (idx < 0 || idx >= KEYBOARD_THEMES.size) return KEYBOARD_THEMES[0]!!.mStyleId
            return KEYBOARD_THEMES[idx]!!.mStyleId
        }

    /**
     * Issue #46: Tint the system navigation bar to match the active keyboard theme,
     * and pick light/dark nav-bar icons based on the background's luminance so the
     * icons remain visible. Called from onCreateInputView() and onStartInputView().
     */
    @Suppress("deprecation")
    private fun applyNavigationBarTheme() {
        val dialog = getWindow()
        if (dialog == null) return
        val window = dialog.getWindow()
        if (window == null) return

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

        val bgColor = this.keyboardBackgroundColorForCurrentTheme
        val lightBackground: Boolean = isColorLight(bgColor)

        if (isAppliedNavigationBarThemeCurrent(window, bgColor, lightBackground)) {
            return
        }

        window.setNavigationBarColor(bgColor)

        // The IME container applies bottomInset padding to clear the gesture bar
        // (see onCreateInputView). That padded strip is transparent by default, so
        // the host app's nav bar shows through. Paint the container background
        // with the theme color so the strip visually matches the keyboard.
        if (mCandidateInInputView != null) {
            mCandidateInInputView!!.setBackgroundColor(bgColor)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val controller =
                WindowCompat.getInsetsController(window, window.getDecorView())
            // setAppearanceLightNavigationBars(true) => DARK icons on LIGHT bar
            controller.setAppearanceLightNavigationBars(lightBackground)
        }
        mNavigationBarThemeApplied = true
        mAppliedNavigationBarWindow = window
        mAppliedNavigationBarCandidateView = mCandidateInInputView
        mAppliedNavigationBarColor = bgColor
        mAppliedNavigationBarLightBackground = lightBackground
        // API 21-22 cannot toggle nav-bar icon brightness; the colored bar alone
        // still gives the user the matching look.
    }

    private fun isAppliedNavigationBarThemeCurrent(
        window: Window?,
        bgColor: Int,
        lightBackground: Boolean
    ): Boolean {
        return mNavigationBarThemeApplied
                && mAppliedNavigationBarWindow === window && mAppliedNavigationBarCandidateView === mCandidateInInputView && mAppliedNavigationBarColor == bgColor && mAppliedNavigationBarLightBackground == lightBackground
    }

    private fun clearAppliedNavigationBarThemeState() {
        mNavigationBarThemeApplied = false
        mAppliedNavigationBarWindow = null
        mAppliedNavigationBarCandidateView = null
    }

    private val keyboardBackgroundColorForCurrentTheme: Int
        get() {
            val colorRes: Int
            when (mKeyboardThemeIndex) {
                1 -> colorRes = R.color.keyboard_background_dark
                2 -> colorRes = R.color.keyboard_background_pink
                3 -> colorRes = R.color.keyboard_background_tech_blue
                4 -> colorRes = R.color.keyboard_background_fashion_purple
                5 -> colorRes = R.color.keyboard_background_relax_green
                6 -> colorRes = if (this.isEffectiveDarkTheme)
                    R.color.keyboard_background_dark
                else
                    R.color.keyboard_background_light

                0 -> colorRes = R.color.keyboard_background_light
                else -> colorRes = R.color.keyboard_background_light
            }
            return ContextCompat.getColor(this, colorRes)
        }

    companion object {
        private const val DEBUG = false
        private const val TAG = "LIMEService"
        private const val IMKEYS_CONFIG = "imkeys"

        private var queryThread: Thread? = null // queryThread for no-blocking I/O  Jeremy '15,6,1

        @JvmField
        val KEYCODE_SWITCH_TO_SYMBOL_MODE: Int = -2
        @JvmField
        val KEYCODE_SWITCH_TO_ENGLISH_MODE: Int = -9
        @JvmField
        val KEYCODE_SWITCH_TO_IM_MODE: Int = -10
        @JvmField
        val KEYCODE_SWITCH_SYMBOL_KEYBOARD: Int = -15

        @JvmStatic
        fun getRestrictedFieldKeyboardMode(inputType: Int): Int {
            if ((inputType and EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_NUMBER) {
                return LIMEKeyboardSwitcher.MODE_PHONE
            }
            return LIMEKeyboardSwitcher.MODE_TEXT
        }

        @JvmStatic
        fun getRestrictedFieldSymbolFlag(inputType: Int): Boolean {
            return (inputType and EditorInfo.TYPE_MASK_CLASS) != EditorInfo.TYPE_CLASS_NUMBER
        }

        @JvmStatic
        fun isForcedEnglishTextVariation(variation: Int): Boolean {
            return variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD || variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD || variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        }

        //Jeremy '16,7,22 To control delayed hiding candidate view and avoid hide and show candidate view in short time.
        private const val DELAY_BEFORE_HIDE_CANDIDATE_VIEW = 200

        // ENGLISH_KB.md #0 / §2a — pick-space punctuation swap (replicate LatinIME).
        // When the user picks an English suggestion we auto-append a space (word ).
        // Typing punctuation next should produce "word, " not "word ,".
        // Character classes from AOSP donottranslate-config-spacing-and-punctuations.xml (en_US).
        private const val ENG_SWAP_FOLLOWED_BY_SPACE =
            ".,;:!?)]}" // delete space, commit punct + " "
        private const val ENG_SWAP_PRECEDED_BY_SPACE = "([{" // keep space, commit bracket
        private const val ENG_SWAP_STRIP = "-/@_'" // delete space, commit punct bare
        const val THREAD_YIELD_DELAY_MS: Int = 0
        private const val EMOJI_SEARCH_FIELD_HEIGHT_DP = 52
        private const val EMOJI_PANEL_HORIZONTAL_PADDING_DP = 12
        private const val EMOJI_PANEL_VERTICAL_PADDING_DP = 8
        private const val EMOJI_PAGE_CAPACITY = 32
        private const val EMOJI_GRID_COLUMNS = 8
        private const val EMOJI_GRID_ROWS = 4
        private const val EMOJI_CATEGORY_TAB_WIDTH_DP = 56
        private const val EMOJI_CATEGORY_TAB_HEIGHT_DP = 46
        private const val EMOJI_CATEGORY_BOTTOM_BAR_HEIGHT_DP = 54
        private const val EMOJI_PANEL_GLYPH_SIZE = 28
        private const val ACTION_VOICE_RESULT = "net.toload.main.hd.VOICE_INPUT_RESULT"
        private const val EXTRA_RECOGNIZED_TEXT = "recognized_text"
        private const val KEY_KEEPASS_ENABLED = "enabled"
        private const val KEEPASS_FIELD_BUTTONS_PER_ROW = 5

        private val FALLBACK_EMOJI_CATEGORIES = arrayOf<Array<String?>?>(
            arrayOf<String?>(
                "😀",
                "😂",
                "😍",
                "🥰",
                "😘",
                "😭",
                "👍",
                "🙏",
                "👏",
                "🎉",
                "❤️",
                "✨",
                "🔥",
                "✅",
                "⭐",
                "💯"
            ),
            arrayOf<String?>(
                "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "😘",
                "😋", "😛", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥳", "😏", "😒", "😔", "😢", "😭", "😤", "😱"
            ),
            arrayOf<String?>(
                "👋", "🤚", "🖐", "✋", "🖖", "👌", "🤌", "🤏", "✌", "🤞", "🫰", "🤟", "🤘", "🤙", "👈", "👉",
                "👆", "👇", "☝", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🙏", "💪", "🦾"
            ),
            arrayOf<String?>(
                "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔",
                "🐧", "🐦", "🐤", "🦆", "🦅", "🦉", "🐺", "🐗", "🐴", "🦄", "🐝", "🦋", "🐌", "🐞", "🐢", "🐍"
            ),
            arrayOf<String?>(
                "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝",
                "🍅", "🥑", "🍆", "🥔", "🥕", "🌽", "🌶", "🥒", "🥬", "🥦", "🍄", "🥜", "🍞", "🧀", "🍔", "🍟"
            ),
            arrayOf<String?>(
                "🚗", "🚕", "🚙", "🚌", "🚎", "🏎", "🚓", "🚑", "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🛵", "🏍",
                "🛺", "🚲", "🛴", "🚨", "🚔", "🚍", "🚘", "🚖", "✈", "🚀", "🚁", "⛵", "🚢", "🚉", "🚇", "🚆"
            ),
            arrayOf<String?>(
                "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱", "🪀", "🏓", "🏸", "🏒", "🏑", "🥍",
                "🏏", "🪃", "🥅", "⛳", "🪁", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛼", "🛷", "⛸", "🥌"
            ),
            arrayOf<String?>(
                "💡", "🔦", "🕯", "🪔", "📱", "💻", "⌨", "🖥", "🖨", "🖱", "🖲", "💽", "💾", "💿", "📷", "🎥",
                "📺", "📻", "🎙", "⏰", "⌚", "📚", "✏", "📌", "✂", "🔒", "🔑", "🔨", "🧰", "🧲", "🧪", "🧬"
            ),
            arrayOf<String?>(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣", "💕", "💞", "💓", "💗", "💖",
                "✨", "⭐", "🌟", "💫", "⚡", "🔥", "💥", "☀", "🌙", "☁", "☔", "❄", "☃", "✅", "❌", "⭕"
            ),
            arrayOf<String?>(
                "🏳",
                "🏴",
                "🏁",
                "🚩",
                "🇹🇼",
                "🇯🇵",
                "🇰🇷",
                "🇺🇸",
                "🇨🇦",
                "🇬🇧",
                "🇫🇷",
                "🇩🇪",
                "🇮🇹",
                "🇪🇸",
                "🇦🇺",
                "🇳🇿",
                "🇸🇬",
                "🇭🇰",
                "🇲🇴",
                "🇹🇭",
                "🇻🇳",
                "🇵🇭",
                "🇲🇾",
                "🇮🇩",
                "🇮🇳",
                "🇧🇷",
                "🇲🇽",
                "🇳🇱",
                "🇸🇪",
                "🇨🇭",
                "🇪🇺",
                "🇺🇳"
            )
        )

        // Replace Keycode.KEYCODE_CTRL_LEFT/RIGHT, ESC on android 3.x
        // for backward compatibility of 2.x
        const val MY_KEYCODE_ESC: Int = 111
        const val MY_KEYCODE_CTRL_LEFT: Int = 113
        const val MY_KEYCODE_CTRL_RIGHT: Int = 114
        const val MY_KEYCODE_ENTER: Int = 10
        const val MY_KEYCODE_SPACE: Int = 32
        const val MY_KEYCODE_SWITCH_CHARSET: Int = 95
        const val MY_KEYCODE_WINDOWS_START: Int = 117 //Jeremy '12,4,29 windows start key

        @JvmStatic
        fun shouldAutoCapitalizeEnglishText(beforeCursor: CharSequence?): Boolean {
            if (beforeCursor == null || beforeCursor.length == 0) {
                return true
            }

            var end = beforeCursor.length
            var hasBoundaryWhitespace = false
            while (end > 0) {
                val c = beforeCursor.get(end - 1)
                if (c == ' ' || c == '\t') {
                    hasBoundaryWhitespace = true
                    end--
                } else {
                    break
                }
            }
            while (end > 0 && isEnglishClosingPunctuation(beforeCursor.get(end - 1))) {
                end--
            }
            if (end == 0) {
                return true
            }

            val term = beforeCursor.get(end - 1)
            if (term == '\n' || term == '\r') {
                return true
            }
            if (!hasBoundaryWhitespace) {
                return false
            }
            if (term != '.' && term != '!' && term != '?') {
                return false
            }
            return term != '.' || !isEnglishAbbreviationBeforeDot(beforeCursor, end - 1)
        }

        @JvmStatic
        fun shouldInsertPeriodForEnglishDoubleSpace(beforeCursor: CharSequence?): Boolean {
            if (beforeCursor == null || beforeCursor.length < 2 || beforeCursor.get(beforeCursor.length - 1) != ' ') {
                return false
            }

            val previousIndex = beforeCursor.length - 2
            val previous = beforeCursor.get(previousIndex)
            if (".!?,:;".indexOf(previous) >= 0) {
                return false
            }

            var tokenStart = previousIndex
            while (tokenStart > 0 && !Character.isWhitespace(beforeCursor.get(tokenStart - 1))) {
                tokenStart--
            }
            val token = beforeCursor.subSequence(tokenStart, previousIndex + 1).toString()
            if (token.contains("://") || token.contains(".")) {
                return false
            }

            return Character.isLetterOrDigit(previous) || isEnglishClosingPunctuation(previous)
        }

        private fun isEnglishClosingPunctuation(c: Char): Boolean {
            return c == '"' || c == '\'' || c == ')' || c == ']' || c == '}' || c == '\u201D' || c == '\u2019'
        }

        private fun isEnglishAbbreviationBeforeDot(text: CharSequence, dotIndex: Int): Boolean {
            if (dotIndex <= 0 || !Character.isLetter(text.get(dotIndex - 1))) {
                return false
            }
            if (dotIndex >= 2 && text.get(dotIndex - 2) == '.') {
                return true
            }

            var start = dotIndex - 1
            while (start > 0 && Character.isLetter(text.get(start - 1))) {
                start--
            }
            val word = text.subSequence(start, dotIndex).toString()
            return "Mr" == word || "Mrs" == word || "Ms" == word
                    || "Dr" == word || "Prof" == word || "Jr" == word
                    || "Sr" == word || "St" == word || "etc" == word
                    || "vs" == word || "Ltd" == word || "Inc" == word
                    || "Co" == word || "Mt" == word || "Ft" == word
        }

        @JvmStatic
        fun isEndkeyCommitKey(
            primaryCode: Int, endkey: String?, englishOnly: Boolean,
            composingLength: Int, candidatesShown: Boolean
        ): Boolean {
            return !englishOnly && endkey != null && endkey.indexOf(primaryCode.toChar()) >= 0
        }

        private fun isKeyInImkeys(primaryCode: Int, imkeys: String?): Boolean {
            if (imkeys == null || imkeys.isEmpty()) {
                return false
            }
            val key = primaryCode.toChar().toString()
            return imkeys.contains(key) || imkeys.contains(key.lowercase())
        }

        @JvmStatic
        fun endkeyCommitCandidateForSuggestions(suggestions: MutableList<Mapping?>): Mapping? {
            val selectedIndex: Int = endkeyCommitCandidateIndex(suggestions)
            if (selectedIndex < 0) {
                return null
            }
            return suggestions.get(selectedIndex)
        }

        private fun endkeyCommitCandidateIndex(suggestions: MutableList<Mapping?>?): Int {
            if (suggestions == null || suggestions.isEmpty()) {
                return -1
            }
            for (i in suggestions.indices) {
                if (isEndkeyCommitCandidate(suggestions.get(i))) {
                    return i
                }
            }
            return -1
        }

        private fun isEndkeyCommitCandidate(candidate: Mapping?): Boolean {
            return candidate != null && !candidate.isComposingCodeRecord() && (candidate.isExactMatchToCodeRecord()
                    || candidate.isPartialMatchToCodeRecord()
                    || candidate.isChinesePunctuationSymbolRecord())
        }

        // General highlight rule (not punctuation-specific):
        // - If the first real candidate after the composing-code echo is an exact match to the
        //   typed code, highlight that candidate (index 1).
        // - Otherwise highlight the composing-code echo (index 0).
        // "Exact match" means the candidate's code equals the composing-code echo's code (the
        // full code the user typed). This deliberately does NOT inspect the record type, so any
        // exact-code candidate -- a DB exact match or an auto-inserted comma/period whose code
        // equals the typed ',' / '.' -- is highlighted the same way. Partial-match records stay
        // highlighted as before.
        @JvmStatic
        fun defaultHighlightedCandidateIndex(
            suggestions: MutableList<Mapping?>?,
            physicalKeyPressed: Boolean
        ): Int {
            if (suggestions == null || suggestions.isEmpty()) {
                return -1
            }
            val first = suggestions.get(0)
            if (suggestions.size > 1
                && (suggestions.get(1)?.isExactMatchToCodeRecord() == true
                        || suggestions.get(1)?.isPartialMatchToCodeRecord() == true
                        || isExactMatchToComposing(suggestions.get(1), first))
            ) {
                return 1
            }
            if (first?.isComposingCodeRecord() == true || first?.isRuntimeBuiltPhraseRecord() == true) {
                return 0
            }
            return -1
        }

        // True when candidate is an exact match to the composing-code echo: the echo is a
        // composing-code record, candidate is not, both carry a non-empty code, and the codes
        // are equal (the full code the user typed). Generalizes "exact match" beyond
        // exactMatchToCode records so an auto-inserted full-width comma/period whose code equals
        // the typed ',' / '.' is highlighted the same way -- without any punctuation-specific
        // special case.
        private fun isExactMatchToComposing(candidate: Mapping?, composing: Mapping?): Boolean {
            if (candidate == null || composing == null) {
                return false
            }
            val composingCode = composing.getCode()
            val candidateCode = candidate.getCode()
            return composing.isComposingCodeRecord()
                    && !candidate.isComposingCodeRecord() && candidateCode != null && !candidateCode.isEmpty() && candidateCode == composingCode
        }

        private fun defaultServiceSelectedCandidate(
            suggestions: MutableList<Mapping?>?,
            physicalKeyPressed: Boolean
        ): Mapping? {
            if (suggestions == null || suggestions.isEmpty()) {
                return null
            }
            if (suggestions.size > 1
                && (!physicalKeyPressed || suggestions.get(1)!!.isExactMatchToCodeRecord()
                        || suggestions.get(1)!!.isPartialMatchToCodeRecord())
            ) {
                return suggestions.get(1)
            }
            return suggestions.get(0)
        }

        @JvmStatic
        fun buildEnglishPredictionCandidates(
            word: String?,
            suggestions: MutableList<Mapping?>?
        ): LinkedList<Mapping?> {
            val result = LinkedList<Mapping?>()
            if (word == null || word.isEmpty()) {
                return result
            }

            val self = Mapping()
            self.setWord(word)
            self.setComposingCodeRecord()
            result.add(self)

            if (suggestions != null) {
                result.addAll(suggestions)
            }
            return result
        }

        @JvmStatic
        fun emojiCategoryTabWidthDp(keyboardSizeScale: Float): Int {
            return scaleDp(EMOJI_CATEGORY_TAB_WIDTH_DP, keyboardSizeScale)
        }

        @JvmStatic
        fun emojiPanelGlyphSize(keyboardSizeScale: Float): Int {
            val clampedScale = max(0.8f, min(1.2f, keyboardSizeScale))
            val glyphScale = 1.0f + ((clampedScale - 1.0f) * 0.5f)
            return Math.round(EMOJI_PANEL_GLYPH_SIZE * glyphScale)
        }

        @JvmStatic
        fun emojiCategoryGlyphSizeDp(keyboardSizeScale: Float): Int {
            return emojiPanelGlyphSize(keyboardSizeScale)
        }

        @JvmStatic
        fun emojiSideControlWidthDp(keyboardSizeScale: Float): Int {
            return emojiCategoryTabWidthDp(keyboardSizeScale)
        }

        @JvmStatic
        fun emojiModeControlGlyphSize(keyboardSizeScale: Float): Int {
            return Math.round(emojiCategoryGlyphSizeDp(keyboardSizeScale) * 0.8f)
        }

        @JvmStatic
        fun emojiBackspaceGlyphSize(keyboardSizeScale: Float): Int {
            return emojiCategoryGlyphSizeDp(keyboardSizeScale)
        }

        private fun scaleDp(dpValue: Int, keyboardSizeScale: Float): Int {
            return Math.round(dpValue * max(0.8f, min(1.2f, keyboardSizeScale)))
        }

        // Contextual menu actions
        private const val ACTION_SETTINGS = 0
        private const val ACTION_REVERSE_LOOKUP = 1
        private const val ACTION_HANCONVERT = 2 //Jeremy '11,9,17
        private const val ACTION_KEYBOARD = 3
        private const val ACTION_METHOD = 4
        private const val ACTION_SPLIT_KEYBOARD = 5
        private const val ACTION_VOICEINPUT = 6
        private const val ACTION_KEEPASS_KEYBOARD = 7


        @JvmStatic
        fun adjustedEmojiInsertionPosition(
            list: MutableList<Mapping?>?,
            requestedPosition: Int
        ): Int {
            if (list == null || list.isEmpty()) {
                return 0
            }

            var position = max(0, min(requestedPosition, list.size))
            for (candidateIndex in position..<list.size) {
                val candidate = list.get(candidateIndex)
                if (candidate != null && isChinesePeriodOrComma(candidate)) {
                    position = candidateIndex + 1
                    break
                }
            }
            return position
        }

        @JvmStatic
        fun isEmojiSearchDoneKey(primaryCode: Int): Boolean {
            return primaryCode == LIMEBaseKeyboard.KEYCODE_DONE || primaryCode == MY_KEYCODE_ENTER
        }

        @JvmStatic
        fun shouldExitEmojiSearchToKeyboard(primaryCode: Int): Boolean {
            return isEmojiSearchDoneKey(primaryCode) || primaryCode == LIME.KEYCODE_EMOJI_PANEL
        }

        @JvmStatic
        fun emojiSearchInitialEnglishOnly(sourceWasEnglish: Boolean): Boolean {
            return sourceWasEnglish
        }

        @JvmStatic
        fun isEmojiSearchKeyboardModeKey(primaryCode: Int): Boolean {
            return primaryCode == KEYCODE_SWITCH_TO_ENGLISH_MODE || primaryCode == KEYCODE_SWITCH_TO_IM_MODE || primaryCode == LIME.KEYCODE_EMOJI_ABC
        }

        @JvmStatic
        fun resolveEmojiSearchEnglishOnlyForModeKey(
            primaryCode: Int,
            currentEnglishOnly: Boolean
        ): Boolean {
            if (primaryCode == KEYCODE_SWITCH_TO_ENGLISH_MODE) {
                return true
            }
            if (primaryCode == KEYCODE_SWITCH_TO_IM_MODE || primaryCode == LIME.KEYCODE_EMOJI_ABC) {
                return false
            }
            return currentEnglishOnly
        }

        @JvmStatic
        fun shouldEmojiSearchConsumePrintableKey(primaryCode: Int, englishOnly: Boolean): Boolean {
            return englishOnly && primaryCode >= 32 && primaryCode < 127
        }

        @JvmStatic
        fun shouldAppendPickedCandidateToEmojiSearch(
            emojiKeyboardShown: Boolean,
            searchMode: Boolean,
            emojiRecord: Boolean,
            composingCodeRecord: Boolean
        ): Boolean {
            return emojiKeyboardShown && searchMode && !emojiRecord && !composingCodeRecord
        }

        @JvmStatic
        fun emojiSearchImeOptions(imeOptions: Int): Int {
            return (imeOptions and EditorInfo.IME_MASK_ACTION.inv()) or EditorInfo.IME_ACTION_DONE
        }

        @JvmStatic
        fun emojiSearchInputCandidateStripVisibility(
            emojiKeyboardShown: Boolean,
            searchMode: Boolean
        ): Int {
            return if (emojiKeyboardShown && searchMode) View.VISIBLE else View.GONE
        }

        private fun isChinesePeriodOrComma(candidate: Mapping): Boolean {
            return candidate.isChinesePunctuationSymbolRecord()
                    || "，" == candidate.getWord()
                    || "。" == candidate.getWord()
        }


        @JvmStatic
        fun nextShiftTapState(
            shifted: Boolean,
            capsLock: Boolean,
            doubleTap: Boolean
        ): ShiftTapState {
            if (capsLock) {
                return ShiftTapState(false, false)
            }
            if (doubleTap) {
                return ShiftTapState(true, true)
            }
            return ShiftTapState(!shifted, false)
        }

        @JvmStatic
        fun resolveVoiceRecognitionLanguageTag(locale: Locale?): String {
            if (locale == null) {
                return "zh-TW"
            }
            val language = locale.getLanguage()
            val country = locale.getCountry()
            if (!"zh".equals(language, ignoreCase = true)) {
                return "zh-TW"
            }
            if ("TW".equals(country, ignoreCase = true)) {
                return "zh-TW"
            }
            if ("HK".equals(country, ignoreCase = true) || "MO".equals(
                    country,
                    ignoreCase = true
                )
            ) {
                return "zh-HK"
            }
            return "zh-TW"
        }

        private val KEYBOARD_THEMES: Array<KeyboardTheme?> = arrayOf<KeyboardTheme?>(
            KeyboardTheme("Light", 0, R.style.LIMETheme_Light),
            KeyboardTheme("Dark", 1, R.style.LIMETheme_Dark),
            KeyboardTheme("Pink", 2, R.style.LIMETheme_Pink),
            KeyboardTheme("TechBlue", 3, R.style.LIMETheme_TechBlue),
            KeyboardTheme("FashionPurple", 4, R.style.LIMETheme_FashionPurple),
            KeyboardTheme("RelaxGreen", 5, R.style.LIMETheme_RelaxGreen),
        )

        @JvmStatic
        @JvmOverloads
        fun emojiPanelColorsForTheme(
            themeIndex: Int,
            systemDark: Boolean,
            systemAccent: Int = 0
        ): EmojiPanelColors {
            val resolvedTheme = if (themeIndex == 6) (if (systemDark) 1 else 0) else themeIndex
            val accentOverlay =
                if (isUsableAccentColor(systemAccent)) withAlpha(systemAccent, 0x33) else 0
            when (resolvedTheme) {
                1 -> return EmojiPanelColors(
                    -0xdededf,
                    -0x716560,
                    -0x302724,
                    -0x302724,
                    -0x302724,
                    if (themeIndex == 6 && accentOverlay != 0) accentOverlay else 0x33FFFFFF
                )

                2 -> return EmojiPanelColors(
                    -0x10c09,
                    -0x38b58e,
                    -0x1000000,
                    -0xb653f,
                    -0x1000000,
                    0x33C74A72
                )

                3 -> return EmojiPanelColors(
                    -0x27180d,
                    -0xb19989,
                    -0xcebbad,
                    -0x643a1c,
                    -0xcebbad,
                    0x334167B0
                )

                4 -> return EmojiPanelColors(
                    -0x101201,
                    -0xbae691,
                    -0xbae691,
                    -0x4d7541,
                    -0xbae691,
                    0x3345196F
                )

                5 -> return EmojiPanelColors(
                    -0xd0a2b,
                    -0xff6bbc,
                    -0xffc5e9,
                    -0xc64ab6,
                    -0xffc5e9,
                    0x33006838
                )

                0 -> return EmojiPanelColors(
                    -0xd000001,
                    -0x757576,
                    -0x1000000,
                    -0x1000000,
                    -0x1000000,
                    if (themeIndex == 6 && accentOverlay != 0) accentOverlay else 0x22000000
                )

                else -> return EmojiPanelColors(
                    -0xd000001,
                    -0x757576,
                    -0x1000000,
                    -0x1000000,
                    -0x1000000,
                    if (themeIndex == 6 && accentOverlay != 0) accentOverlay else 0x22000000
                )
            }
        }

        private fun isUsableAccentColor(color: Int): Boolean {
            return Color.alpha(color) != 0
        }

        private fun withAlpha(color: Int, alpha: Int): Int {
            return (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)
        }

        private fun isColorLight(color: Int): Boolean {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            // Rec. 709 luma
            val luma = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
            return luma >= 0.5
        }
    }
}
