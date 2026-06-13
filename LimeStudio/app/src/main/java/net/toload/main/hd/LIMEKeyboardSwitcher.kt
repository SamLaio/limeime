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

import android.content.Context
import android.util.Log
import java.util.HashMap
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.data.Keyboard
import net.toload.main.hd.global.LIME
import net.toload.main.hd.global.LIMEPreferenceManager
import net.toload.main.hd.keyboard.LIMEKeyboard
import net.toload.main.hd.keyboard.LIMEKeyboardView

class LIMEKeyboardSwitcher(var mService: LIMEService, var mThemedContext: Context) {
    var mInputView: LIMEKeyboardView? = null

    //private KeyboardId mCurrentId;
    private val mKeyboards: MutableMap<KeyboardId?, LIMEKeyboard?>?

    var keyboardMode: Int = KEYBOARD_MODE_NORMAL
        private set

    //private int mChnMode = MODE_TEXT_DEFAULT;
    //private int mEngMode = MODE_TEXT;
    private var mImeOptions = 0

    private val mLIMEPref: LIMEPreferenceManager

    var isShifted: Boolean = false
        private set
    var isSymbols: Boolean = false
    var isChinese: Boolean = true
    private val mPreferSymbols = false
    private var mCurrentSymbolsKeyboard: Int = SYMBOLS_KEYBOARD_1

    private var mLastDisplayWidth = 0

    private var ImCode: String? = null

    private var kbMap: HashMap<String?, Keyboard?>? = null
    private var imConfigMap: HashMap<String?, String?>? = null

    private var mKeySizeScale: Float


    init {
        mLIMEPref = LIMEPreferenceManager(mService)
        mKeyboards = HashMap<KeyboardId?, LIMEKeyboard?>()

        mKeySizeScale = mLIMEPref.fontSize
    }

    fun setThemedContext(context: Context) {
        mThemedContext = context
    }

    val keyboardSize: Int
        get() {
            if (kbMap != null) {
                return kbMap!!.size
            }
            return 0
        }

    fun setKeyboardConfigList(list: MutableList<Keyboard?>?) {
        if (list == null || (list.isEmpty())) return  //Jeremy '12,4,10 avoid fc when database is locked.

        kbMap = HashMap<String?, Keyboard?>()
        for (o in list) {
            if (o != null) {
                kbMap!!.put(o.code, o)
            }
        }
    }

    fun getImConfigKeyboard(code: String?): String? {
        if (imConfigMap != null && imConfigMap!!.get(code) != null) {
            return imConfigMap!!.get(code)
        }
        return ""
    }

    fun setImConfigKeyboardList(list: MutableList<ImConfig?>?) {
        if (list == null || list.isEmpty()) return  //Jeremy '12,4,10 avoid fc when database is locked.

        imConfigMap = HashMap<String?, String?>()
        for (o in list) {
            if (o != null) {
                imConfigMap!!.put(o.code, o.keyboard)
            }
        }
    }

    fun setActivatedIMList(ImCodes: MutableList<String?>?, shortnames: MutableList<String?>?) {
        if (DEBUG) Log.i(TAG, "setActiveKeyboardList()")
        if (ImCodes == null || shortnames == null) return

        if (ImCodes == mActivatedIMList && shortnames == mActivatedIMShortnameList) return

        mActivatedIMList = ImCodes
        mActivatedIMShortnameList = shortnames
    }

    val activatedIMShortnameList: MutableList<String?>
        get() = mActivatedIMShortnameList!!

    val activeIMShortname: String?
        get() {
            if (DEBUG) Log.i(
                TAG,
                "getCurrentActiveKeyboardShortName() current IM:" + ImCode
            )
            for (i in mActivatedIMList!!.indices) {
                if (ImCode == mActivatedIMList!!.get(i)) {
                    if (DEBUG) Log.i(
                        TAG,
                        "getCurrentActiveKeyboardShortName()=" + mActivatedIMShortnameList!!.get(
                            i
                        )
                    )
                    return mActivatedIMShortnameList!!.get(i)
                }
            }
            return ""
        }
    val nextActivatedIMShortname: String?
        get() {
            for (i in mActivatedIMList!!.indices) {
                if (ImCode == mActivatedIMList!!.get(i)) {
                    if (i == mActivatedIMList!!.size - 1) return mActivatedIMShortnameList!!.get(
                        0
                    )
                    else return mActivatedIMShortnameList!!.get(i + 1)
                }
            }
            return ""
        }
    val prevActivatedIMShortname: String?
        get() {
            for (i in mActivatedIMList!!.indices) {
                if (ImCode == mActivatedIMList!!.get(i)) {
                    if (i == 0) return mActivatedIMShortnameList!!.get(
                        mActivatedIMList!!.size - 1
                    )
                    else return mActivatedIMShortnameList!!.get(i - 1)
                }
            }
            return ""
        }

    fun setInputView(inputView: LIMEKeyboardView?) {
        mInputView = inputView
    }

    fun clearKeyboards() {
        if (DEBUG) Log.i(TAG, "clearKeyboards()")
        if (mKeyboards != null) {
            mKeyboards.clear()
        }
    }

    fun resetKeyboards(forceCreate: Boolean) {
        if (DEBUG) Log.i(TAG, "resetKeyboards(): forceCreate:" + forceCreate)
        if (forceCreate) clearKeyboards()
        // Configuration change is coming after the keyboard gets recreated. So don't rely on that.
        // If keyboards have already been made, check if we have a screen width change and 
        // create the keyboard layouts again at the correct orientation
        val displayWidth = mService.getMaxWidth()
        if (displayWidth != mLastDisplayWidth) {
            mLastDisplayWidth = displayWidth
            clearKeyboards()
        }
    }

    /**
     * Represents the parameters necessary to construct a new LIMEKeyboard,
     * which also serve as a unique identifier for each keyboard type.
     */
    private class KeyboardId @JvmOverloads constructor(
        @JvmField
        var mXml: Int,
        @JvmField
        var mMode: Int = 0,
        @JvmField
        var mEnableShiftLock: Boolean = false
    ) {
        override fun equals(other: Any?): Boolean {
            return other is KeyboardId && equals(other)
        }

        fun equals(other: KeyboardId): Boolean {
            return other.mXml == this.mXml && other.mMode == this.mMode
        }


        override fun hashCode(): Int {
            return (mXml + 1) * (mMode + 1) * (if (mEnableShiftLock) 2 else 1)
        }
    }


    private fun getKeyboard(id: KeyboardId?): LIMEKeyboard? {
        if (DEBUG) Log.i(TAG, "getKeyboard()")
        //Jeremy '11,9,3
        if (mLIMEPref.keyboardSize != mKeySizeScale) {
            clearKeyboards()
            mKeySizeScale = mLIMEPref.keyboardSize
        }
        if (id != null) {
            if (!mKeyboards!!.containsKey(id)) {
                if (DEBUG) Log.i(
                    TAG,
                    "getKeyboard() keyboard for id, " + id + ", is not exist. create one now."
                )
                val keyboard: LIMEKeyboard = LIMEKeyboard(
                    mThemedContext, id.mXml, id.mMode, mKeySizeScale,
                    mLIMEPref.showArrowKeys,  //Jeremy '12,5,21 add the show arrow keys option
                    mLIMEPref.splitKeyboard //Jeremy '12,5,27 add the split keyboard option
                )
                keyboard.setKeyboardSwitcher(this)
                if (id.mEnableShiftLock) {
                    keyboard.enableShiftLock()
                }
                mKeyboards.put(id, keyboard)
            }
            return mKeyboards.get(id)
        }
        return null
    }

    /**
     * Get XML resource ID for keyboard layout.
     * Uses direct R.xml references for all keyboard layouts (more efficient and compile-time verified).
     */
    private fun getKeyboardXMLID(keyboardId: String?): Int {
        if (keyboardId == null || keyboardId.isEmpty()) {
            return 0
        }


        // Use direct R.xml references for all keyboard layouts (compile-time verified, more efficient)
        when (keyboardId) {
            "symbols1" -> return R.xml.symbols1
            "symbols2" -> return R.xml.symbols2
            "symbols3" -> return R.xml.symbols3
            "symbols" -> return R.xml.symbols1 // Default to symbols1
            "symbols_shift" -> return R.xml.symbols1 // Default to symbols1

            "phone_number" -> return R.xml.phone_number
            "phone" -> return R.xml.phone
            "phone_shift" -> return R.xml.phone_shift
            "phone_simple" -> return R.xml.phone_simple

            "lime_english_number_shift" -> return R.xml.lime_english_number_shift
            "lime_english_number" -> return R.xml.lime_english_number
            "lime_english_shift" -> return R.xml.lime_english_shift
            "lime_english" -> return R.xml.lime_english

            "lime_abc_shift" -> return R.xml.lime_abc_shift
            "lime_abc" -> return R.xml.lime_abc

            "lime" -> return R.xml.lime
            "lime_shift" -> return R.xml.lime_shift

            "lime_cj" -> return R.xml.lime_cj
            "lime_cj_shift" -> return R.xml.lime_cj_shift
            "lime_cj_number" -> return R.xml.lime_cj_number
            "lime_cj_number_shift" -> return R.xml.lime_cj_number_shift

            "lime_dayi" -> return R.xml.lime_dayi
            "lime_dayi_shift" -> return R.xml.lime_dayi_shift
            "lime_dayi_sym" -> return R.xml.lime_dayi_sym
            "lime_dayi_sym_shift" -> return R.xml.lime_dayi_sym_shift

            "lime_ez" -> return R.xml.lime_ez
            "lime_ez_shift" -> return R.xml.lime_ez_shift

            "lime_array" -> return R.xml.lime_array
            "lime_array_shift" -> return R.xml.lime_array_shift
            "lime_array_number" -> return R.xml.lime_array_number
            "lime_array_number_shift" -> return R.xml.lime_array_number_shift

            "lime_phonetic" -> return R.xml.lime_phonetic
            "lime_phonetic_shift" -> return R.xml.lime_phonetic_shift

            "lime_hs" -> return R.xml.lime_hs
            "lime_hs_shift" -> return R.xml.lime_hs_shift

            "lime_hsu" -> return R.xml.lime_hsu
            "lime_hsu_shift" -> return R.xml.lime_hsu_shift

            "lime_wb" -> return R.xml.lime_wb
            "lime_wb_shift" -> return R.xml.lime_wb_shift

            "lime_et26" -> return R.xml.lime_et26
            "lime_et26_shift" -> return R.xml.lime_et26_shift

            "lime_et_41" -> return R.xml.lime_et_41
            "lime_et_41_shift" -> return R.xml.lime_et_41_shift

            "lime_number" -> return R.xml.lime_number
            "lime_number_shift" -> return R.xml.lime_number_shift
            "lime_number_symbol" -> return R.xml.lime_number_symbol
            "lime_number_symbol_shift" -> return R.xml.lime_number_symbol_shift

            else -> {
                // Return 0 for unknown keyboard layouts (should not happen with valid database entries)
                if (DEBUG) {
                    Log.w(TAG, "Unknown keyboard layout: " + keyboardId)
                }
                return 0
            }
        }
    }

    private fun getKeyboardXMLID(keyboardId: String?, fallbackKeyboardId: String): Int {
        val resolved = getKeyboardXMLID(keyboardId)
        return if (resolved != 0) resolved else getKeyboardXMLID(fallbackKeyboardId)
    }

    private fun fallbackKeyboardConfig(imCode: String?, isIm: Boolean): Keyboard {
        val keyboard = Keyboard()
        keyboard.code = if (isIm) fallbackImKeyboardCode(imCode) else "lime"
        keyboard.imkb = fallbackImKeyboardLayout(imCode, false)
        keyboard.imshiftkb = fallbackImKeyboardLayout(imCode, true)
        keyboard.engkb = "lime_english"
        keyboard.engshiftkb = "lime_english_shift"
        keyboard.symbolkb = "symbols1"
        keyboard.symbolshiftkb = "symbols1"
        keyboard.defaultkb = "lime"
        keyboard.defaultshiftkb = "lime_shift"
        return keyboard
    }

    private fun fallbackImKeyboardCode(imCode: String?): String {
        return when (imCode) {
            LIME.IM_PHONETIC,
            LIME.IM_PHONETIC_ADV,
            LIME.IM_PHONETIC_BIG5,
            LIME.IM_PHONETIC_ADV_BIG5 -> "phonetic"

            LIME.IM_CJ,
            LIME.IM_CJ_BIG5,
            LIME.IM_CJHK,
            LIME.IM_CJ4,
            LIME.IM_CJ5 -> "cj"

            LIME.IM_SCJ -> "limenum"
            LIME.IM_DAYI,
            LIME.IM_DAYIUNI,
            LIME.IM_DAYIUNI_BIG5,
            LIME.IM_DAYIUNIP,
            LIME.IM_DAYIUNIP_BIG5 -> "dayisym"

            LIME.IM_EZ -> "ez"
            LIME.IM_ARRAY -> "arraynum"
            LIME.IM_ARRAY10 -> "phonenum"
            LIME.IM_WB -> "wb"
            LIME.IM_HS,
            LIME.IM_HS_V1,
            LIME.IM_HS_V2,
            LIME.IM_HS_V3 -> "hs"

            else -> "lime"
        }
    }

    private fun fallbackImKeyboardLayout(imCode: String?, shifted: Boolean): String {
        return when (imCode) {
            LIME.IM_PHONETIC,
            LIME.IM_PHONETIC_ADV,
            LIME.IM_PHONETIC_BIG5,
            LIME.IM_PHONETIC_ADV_BIG5 -> if (shifted) "lime_phonetic_shift" else "lime_phonetic"

            LIME.IM_CJ,
            LIME.IM_CJ_BIG5,
            LIME.IM_CJHK,
            LIME.IM_CJ4,
            LIME.IM_CJ5 -> if (shifted) "lime_cj_shift" else "lime_cj"

            LIME.IM_SCJ -> if (shifted) "lime_number_shift" else "lime_number"
            LIME.IM_DAYI,
            LIME.IM_DAYIUNI,
            LIME.IM_DAYIUNI_BIG5,
            LIME.IM_DAYIUNIP,
            LIME.IM_DAYIUNIP_BIG5 -> if (shifted) "lime_dayi_sym_shift" else "lime_dayi_sym"

            LIME.IM_EZ -> if (shifted) "lime_ez_shift" else "lime_ez"
            LIME.IM_ARRAY -> if (shifted) "lime_array_number_shift" else "lime_array_number"
            LIME.IM_ARRAY10 -> if (shifted) "lime_phonetic_shift" else "lime_phonetic"
            LIME.IM_WB -> if (shifted) "lime_wb_shift" else "lime_wb"
            LIME.IM_HS,
            LIME.IM_HS_V1,
            LIME.IM_HS_V2,
            LIME.IM_HS_V3 -> if (shifted) "lime_hs_shift" else "lime_hs"

            else -> if (shifted) "lime_shift" else "lime"
        }
    }

    fun setKeyboardMode(
        imCode: String?,
        mode: Int,
        imeOptions: Int,
        isIm: Boolean,
        isSymbol: Boolean,
        isShift: Boolean
    ) {
        if (DEBUG) {
            Log.i(
                TAG,
                "setKeyboardMode () imCode:" + imCode + ", mode:" + mode + ", imOptions:" + imeOptions +
                        ", isIM:" + isIm + ", isSymbol:" + isSymbol + ", isShift:" + isShift
            )
        }
        this.ImCode = if (imCode.isNullOrEmpty()) "phonetic" else imCode


        // Jeremy '11,6,2.  Has to preserve these options for toggle keyboard controls.
        this.mImeOptions = imeOptions
        if (isSymbol && !this.isSymbols) this.mCurrentSymbolsKeyboard =
            SYMBOLS_KEYBOARD_1 //reset the symbol keyboard to first one if it's switching from non-symbol keyboards

        this.isSymbols = isSymbol
        this.isShifted = isShift
        if (mode != 0) this.keyboardMode = mode

        var localImCode: String? = ""
        if (imCode != "wb" && imCode != "hs") {
            if (imConfigMap != null) localImCode = imConfigMap!!.get(imCode)
        } else {
            localImCode = imCode
        }

        var keyboardConfig: Keyboard? = null

        if (localImCode == null || localImCode.isEmpty() || localImCode == "custom") {
            localImCode = "lime"
            if (kbMap != null) keyboardConfig = kbMap!!.get(localImCode)
        } else if (kbMap != null) {
            keyboardConfig = kbMap!!.get(localImCode)
        }
        val kConfig = keyboardConfig ?: fallbackKeyboardConfig(this.ImCode, isIm).also {
            if (DEBUG) {
                Log.w(TAG, "setKeyboardMode(): using fallback keyboard config for imCode=" + this.ImCode)
            }
        }

        val kid: KeyboardId?

        this.isChinese = false
        if (isSymbol) {
            when (mCurrentSymbolsKeyboard) {
                SYMBOLS_KEYBOARD_2 -> kid = KeyboardId(getKeyboardXMLID("symbols2"))
                SYMBOLS_KEYBOARD_3 -> kid = KeyboardId(getKeyboardXMLID("symbols3"))
                SYMBOLS_KEYBOARD_1 -> kid = KeyboardId(getKeyboardXMLID("symbols1"))
                else -> kid = KeyboardId(getKeyboardXMLID("symbols1"))
            }
        } else {
            when (mode) {
                MODE_PHONE ->                    //Log.i("ART","KBMODE ->: phone");
                    kid = KeyboardId(getKeyboardXMLID("phone_number"))

                MODE_URL ->                    //Log.i("ART","KBMODE ->: url");
                    kid = KeyboardId(
                        getKeyboardXMLID(
                            resolveEnglishLayoutId(
                                kConfig,
                                mLIMEPref.showNumberRowInEnglish,
                                isShift
                            )
                        ),
                        KEYBOARD_MODE_URL, true
                    )

                MODE_EMAIL ->                    //Log.i("ART","KBMODE ->: email");
                    kid = KeyboardId(
                        getKeyboardXMLID(
                            resolveEnglishLayoutId(
                                kConfig,
                                mLIMEPref.showNumberRowInEnglish,
                                isShift
                            )
                        ),
                        KEYBOARD_MODE_EMAIL, true
                    )

                else -> if (isIm) {  // Chinese IM keyboards
                    if (isShift) {
                        //Log.i("ART","KBMODE ->: " + kConfig.getImshiftkb());
                        kid = KeyboardId(
                            getKeyboardXMLID(kConfig.imshiftkb, fallbackImKeyboardLayout(this.ImCode, true)),
                            KEYBOARD_MODE_NORMAL,
                            true
                        )
                    } else {
                        //Log.i("ART","KBMODE ->: " + kConfig.getImkb());
                        kid = KeyboardId(
                            getKeyboardXMLID(kConfig.imkb, fallbackImKeyboardLayout(this.ImCode, false)),
                            KEYBOARD_MODE_NORMAL,
                            true
                        )
                    }
                    this.isChinese = true
                } else { //if(!isIm){  //English normal keyboard
                    kid = KeyboardId(
                        getKeyboardXMLID(
                            resolveEnglishLayoutId(
                                kConfig,
                                mLIMEPref.showNumberRowInEnglish,
                                isShift
                            )
                        ),
                        KEYBOARD_MODE_NORMAL, true
                    )
                }
            }
        }

        val inputView = mInputView ?: return


        val keyboard: LIMEKeyboard? = getKeyboard(kid)


        // mCurrentId = kid;
        if (keyboard == null) {
            Log.e(TAG, "setKeyboardMode(): unable to create keyboard for imCode=" + this.ImCode)
            return
        }
        inputView.keyboard = keyboard

        keyboard.setShiftLocked(keyboard.isShiftLocked())
        keyboard.setShifted(this.isShifted)
        inputView.keyboard = inputView.keyboard //instead of invalidateAllKeys();

        keyboard.setImeOptions(mThemedContext.getResources(), this.keyboardMode, imeOptions)
    }


    val isTextMode: Boolean
        get() = this.keyboardMode == MODE_TEXT

    @JvmName("getKeyboardSizeCompat")
    fun getKeyboardSize(): Int = keyboardSize

    fun getTextMode(): Int {
        return MODE_TEXT_QWERTY
    }

    fun getTextModeCount(): Int {
        return MODE_TEXT_COUNT
    }


    val isAlphabetMode: Boolean
        get() = false

    fun toggleShift() {
        if (DEBUG) Log.i(TAG, "toggleShift() KBMODE mode:" + this.keyboardMode)
        this.isShifted = !this.isShifted
        if (this.isChinese) this.setKeyboardMode(
            ImCode!!, 0, mImeOptions, true,
            this.isSymbols,
            this.isShifted
        )
        else {
            this.setKeyboardMode(
                ImCode!!,
                this.keyboardMode, mImeOptions, false,
                this.isSymbols,
                this.isShifted
            )
        }
    }

    fun toggleChinese() {
        this.isChinese = !this.isChinese

        if (this.isChinese) {
            this.setKeyboardMode(ImCode!!, 0, mImeOptions, true, this.isSymbols, this.isShifted)
        } else {
            this.setKeyboardMode(
                ImCode!!,
                this.keyboardMode, mImeOptions, false,
                this.isSymbols,
                this.isShifted
            )
        }
    }

    fun toggleSymbols() {
        if (this.isChinese) this.setKeyboardMode(
            ImCode!!,
            0,
            mImeOptions,
            true,
            !this.isSymbols,
            false
        )
        else this.setKeyboardMode(
            ImCode!!,
            this.keyboardMode, mImeOptions, false, !this.isSymbols, false
        )
    }

    fun switchSymbols() {
        when (mCurrentSymbolsKeyboard) {
            SYMBOLS_KEYBOARD_2 -> mCurrentSymbolsKeyboard = SYMBOLS_KEYBOARD_3
            SYMBOLS_KEYBOARD_3 -> mCurrentSymbolsKeyboard = SYMBOLS_KEYBOARD_1
            SYMBOLS_KEYBOARD_1 -> mCurrentSymbolsKeyboard = SYMBOLS_KEYBOARD_2
            else -> mCurrentSymbolsKeyboard = SYMBOLS_KEYBOARD_2
        }
        if (this.isChinese) this.setKeyboardMode(
            ImCode!!,
            0,
            mImeOptions,
            true,
            this.isSymbols,
            false
        )
        else this.setKeyboardMode(
            ImCode!!,
            this.keyboardMode, mImeOptions, false,
            this.isSymbols, false
        )
    }

    fun setIsChinese(value: Boolean) {
        isChinese = value
    }

    fun setIsSymbols(value: Boolean) {
        isSymbols = value
    }


    companion object {
        const val DEBUG: Boolean = false
        const val TAG: String = "LIMEKeyboardSwitcher"

        const val MODE_TEXT: Int = 1
        const val MODE_SYMBOLS: Int = 2
        const val MODE_PHONE: Int = 3
        const val MODE_URL: Int = 4
        const val MODE_EMAIL: Int = 5
        const val MODE_IM: Int = 6

        const val MODE_TEXT_QWERTY: Int = 0
        const val MODE_TEXT_ALPHA: Int = 1
        const val textModeCount: Int = 2
        const val MODE_TEXT_COUNT: Int = textModeCount


        @JvmField
		val KEYBOARD_MODE_NORMAL: Int = R.id.mode_normal
        @JvmField
		val KEYBOARD_MODE_URL: Int = R.id.mode_url
        @JvmField
		val KEYBOARD_MODE_EMAIL: Int = R.id.mode_email


        //public static final int KEYBOARD_MODE_IM = R.id.mode_im;
        //public static final int IM_KEYBOARD = 0;
        private const val SYMBOLS_KEYBOARD_1 = 1
        private const val SYMBOLS_KEYBOARD_2 = 2
        private const val SYMBOLS_KEYBOARD_3 = 3

        @JvmStatic
		fun resolveEnglishLayoutId(
            keyboard: Keyboard?,
            showNumberRow: Boolean,
            isShift: Boolean
        ): String {
            // There is no user-facing English layout picker, so ignore legacy engkb fields in lime.db.
            if (showNumberRow) return if (isShift) "lime_english_number_shift" else "lime_english_number"
            return if (isShift) "lime_english_shift" else "lime_english"
        }

        private var mActivatedIMList: MutableList<String?>? = null
        private var mActivatedIMShortnameList: MutableList<String?>? = null

        @JvmStatic
        fun getInstance(): LIMEKeyboardSwitcher? {
            return null
        }
    }
}
