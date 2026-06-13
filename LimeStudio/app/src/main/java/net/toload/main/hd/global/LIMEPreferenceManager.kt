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
package net.toload.main.hd.global

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.Set
import kotlin.math.max
import net.toload.main.hd.data.ImConfig

class LIMEPreferenceManager(private val ctx: Context) {
    class ReverseLookupOption(@JvmField val label: String?, @JvmField val value: String?)

    fun getTableTotalRecords(table: String): String {
        var table = table
        table = preProcessTableName(table)

        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        var records: String = sp.getString(table + "total_record", "")!!
        if (records.isEmpty()) {
            val ssp = ctx.getSharedPreferences(table + "total_record", Context.MODE_PRIVATE)
            records = ssp.getString(table + "total_record", "")!!
            if (!records.isEmpty()) setTableTotalRecords(table, records)
        }
        return records
    }

    fun setTableTotalRecords(table: String, records: String?) {
        var table = table
        table = preProcessTableName(table)
        //SharedPreferences sp = ctx.getSharedPreferences(table + "total_record", 0);
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        sp.edit().putString(table + "total_record", records).apply()
    }


    fun getTableVersion(table: String): String {
        var table = table
        table = preProcessTableName(table)

        val sdp = PreferenceManager.getDefaultSharedPreferences(ctx)
        var version: String = sdp.getString(table + "mapping_version", "")!!
        // retain mapping_version saved in shared Preference and saved to default reference
        if (version.isEmpty()) {
            val ssp = ctx.getSharedPreferences(table + "mapping_version", Context.MODE_PRIVATE)
            version = ssp.getString(table + "mapping_version", "")!!
            if (!version.isEmpty()) setTableVersion(table, version)
        }
        return version
    }

    fun setTableVersion(table: String, version: String?) {
        var table = table
        table = preProcessTableName(table)
        //SharedPreferences sp = ctx.getSharedPreferences(table + "mapping_version", 0);
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        sp.edit().putString(table + "mapping_version", version).apply()
    }

    fun getTableMappingFilename(table: String): String {
        var table = table
        table = preProcessTableName(table)
        //SharedPreferences sp = ctx.getSharedPreferences(table + "mapping_file", 0);
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        return sp.getString(table + "mapping_file", "")!!
    }

    fun setTableMappingFilename(table: String, filename: String?) {
        var table = table
        table = preProcessTableName(table)
        //SharedPreferences sp = ctx.getSharedPreferences(table + "mapping_file", 0);
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        sp.edit().putString(table + "mapping_file", filename).apply()
    }

    fun getTableMappingTempFilename(table: String): String {
        var table = table
        table = preProcessTableName(table)
        //SharedPreferences sp = ctx.getSharedPreferences(table + "mapping_file_temp", 0);
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        return sp.getString(table + "mapping_file_temp", "")!!
    }

    fun setTableTempMappingFilename(table: String, filename: String?) {
        var table = table
        table = preProcessTableName(table)
        //SharedPreferences sp = ctx.getSharedPreferences(table + "mapping_file_temp", 0);
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        sp.edit().putString(table + "mapping_file_temp", filename).apply()
    }


    var totalUserdictRecords: String?
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            var records: String = sp.getString("total_userdict_record", "0")!!
            if (records == "0") {
                val ssp = ctx.getSharedPreferences(
                    "total_userdict_record",
                    Context.MODE_PRIVATE
                )
                records = ssp.getString("total_userdict_record", "0")!!
                if (records == "0") this.totalUserdictRecords = records
            }
            return records
        }
        set(records) {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            sp.edit().putString("total_userdict_record", records).apply()
        }

    var languageMode: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getString("language_mode", "no") == "yes"
        }
        set(englishOnly) {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            val loadingStatus = if (englishOnly) "yes" else "no"

            putStringAndBumpStartupConfigVersionIfChanged(sp, "language_mode", loadingStatus)
        }

    private fun getReverseLookupPreferenceKey(table: String?): String {
        var table = table
        if (table == null || table.isEmpty()) {
            table = LIME.DB_TABLE_PHONETIC
        }
        if (table == LIME.DB_TABLE_PHONETIC) {
            return "bpmf_im_reverselookup"
        }
        return table + "_im_reverselookup"
    }

    fun getReverseLookupTable(table: String?): String {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        return sp.getString(getReverseLookupPreferenceKey(table), "none")!!
    }

    fun getRerverseLookupTable(table: String?): String {
        return getReverseLookupTable(table)
    }

    fun setReverseLookupTable(table: String?, lookupTable: String?) {
        var lookupTable = lookupTable
        if (lookupTable == null || lookupTable.isEmpty()) {
            lookupTable = "none"
        }
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        sp.edit().putString(getReverseLookupPreferenceKey(table), lookupTable).apply()
    }

    val reverseLookupOptions: MutableList<ReverseLookupOption?>
        get() = getReverseLookupOptions("")

    fun getReverseLookupOptions(noneLabel: String?): MutableList<ReverseLookupOption?> {
        return buildReverseLookupOptions(this.iMActivatedState, noneLabel)
    }

    val fixedCandidateViewDisplay: Boolean
        get() = true


    val learnRelatedWord: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("candidate_suggestion", true)
        }

    val learnPhrase: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("learn_phrase", true)
        }

    val disablePhysicalSelKeyOption: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("disable_physical_selkey_option", false)
        }

    val englishPrediction: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("english_dictionary_enable", true)
        }

    val physicalKeyboardEnable: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("physical_keyboard_enable", true)
        }

    val englishPredictionOnPhysicalKeyboard: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("english_dictionary_physical_keyboard", false)
        }

    val sortSuggestions: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("learning_switch", true)
        }

    val candidateSuggestionPunctutation: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("candidate_suggestion_punctuation", true)
        }

    val physicalKeyboardSortSuggestions: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("physical_keyboard_sort", true)
        }

    val similiarEnable: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("similiar_enable", true)
        }

    val selectDefaultOnSliding: Boolean
        /**
         * Always returns `true`. The `candidate_switch` preference UI was
         * removed because free-scroll candidate selection is the only sensible behaviour
         * on modern Android; the paged alternative is unused. The stored value (if any)
         * is ignored.
         */
        get() = true

    val vibrateOnKeyPressed: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("vibrate_on_keypress", true)
        }


    val soundOnKeyPressed: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("sound_on_keypress", false)
        }

    val emojiDisplayPosition: Int?
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            if (sp.contains("enable_emoji")) {
                val editor = sp.edit().remove("enable_emoji")
                if (!sp.getBoolean("enable_emoji", true)) {
                    editor.putString("enable_emoji_position", "0")
                }
                editor.apply()
            }
            return sp.getString("enable_emoji_position", "5")!!.toInt()
        }

    val persistentLanguageMode: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("persistent_language_mode", false)
        }

    val showNumberRowInEnglish: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("number_row_in_english", true)
        }

    fun syncIMActivatedState(imlist: MutableList<ImConfig?>) {
        val state = StringBuilder()
        val imMap = HashMap<String?, String?>()
        for (i in imlist) {
            if (i == null || i.isDisable) {
                continue
            }
            imMap.put(i.code, i.code)
        }

        if (imMap.get(LIME.IM_CUSTOM) != null) {
            state.append("0")
        }

        if (imMap.get(LIME.IM_CJ) != null) {
            if (state.length > 0) {
                state.append(";")
            }
            state.append("1")
        }
        if (imMap.get(LIME.IM_SCJ) != null) {
            if (state.length > 0) {
                state.append(";")
            }
            state.append("2")
        }
        if (imMap.get(LIME.IM_CJ5) != null) {
            if (state.length > 0) {
                state.append(";")
            }
            state.append("3")
        }
        if (imMap.get(LIME.IM_ECJ) != null) {
            if (state.length > 0) {
                state.append(";")
            }
            state.append("4")
        }
        if (imMap.get(LIME.IM_DAYI) != null) {
            if (state.length > 0) {
                state.append(";")
            }
            state.append("5")
        }
        if (imMap.get(LIME.IM_PHONETIC) != null) {
            if (state.length > 0) {
                state.append(";")
            }
            state.append("6")
        }
        if (imMap.get(LIME.IM_EZ) != null) {
            if (state.length > 0) {
                state.append(";")
            }
            state.append("7")
        }
        if (imMap.get(LIME.IM_ARRAY) != null) {
            if (state.length > 0) {
                state.append(";")
            }
            state.append("8")
        }
        if (imMap.get(LIME.IM_ARRAY10) != null) {
            if (state.length > 0) {
                state.append(";")
            }
            state.append("9")
        }
        if (imMap.get(LIME.IM_WB) != null) {
            if (state.length > 0) {
                state.append(";")
            }
            state.append("10")
        }
        if (imMap.get(LIME.IM_HS) != null) {
            if (state.length > 0) {
                state.append(";")
            }
            state.append("11")
        }
        if (imMap.get(LIME.IM_PINYIN) != null) {
            if (state.length > 0) {
                state.append(";")
            }
            state.append("12")
        }

        this.iMActivatedState = state.toString()
    }

    var iMActivatedState: String?
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getString("keyboard_state", "0;1;2;3;4;5;6;7;8;9;10;11;12")
        }
        set(state) {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            putStringAndBumpStartupConfigVersionIfChanged(sp, "keyboard_state", state.toString())
        }

    var activeIM: String?
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getString("keyboard_list", LIME.DB_TABLE_PHONETIC)
        }
        set(activeIM) {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            putStringAndBumpStartupConfigVersionIfChanged(sp, "keyboard_list", activeIM.toString())
        }


    val threerowRemapping: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("three_rows_remapping", false)
        }

    val physicalKeyboardType: String
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getString("physical_keyboard_type", "normal_keyboard")!!
        }

    val autoCommitValue: Int
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getString("auto_commit", "0")!!.toInt()
        }

    val phoneticKeyboardType: String
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getString("phonetic_keyboard_type", LIME.DB_TABLE_PHONETIC)!!
        }

    val autoCaptalization: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("auto_cap", true)
        }

    val quickFixes: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("quick_fixes", true)
        }

    val autoComplete: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("auto_complete", true)
        }

    val disablePhysicalSelkey: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("disable_physical_selkey", false)
        }


    var hanCovertOption: Int?
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getString("han_convert_option", "0")!!.toInt()
        }
        set(value) {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            sp.edit().putString("han_convert_option", value.toString()).apply()
        }

    val selkeyOption: Int?
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getString("selkey_option", "0")!!.toInt()
        }

    val similarCodeCandidates: Int?
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getString("similiar_list", "20")!!.toInt()
        }

    val fontSize: Float
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getString("font_size", "1")!!.toFloat()
        }

    val keyboardSize: Float
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getString("keyboard_size", "1")!!.toFloat()
        }

    val smartChineseInput: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("smart_chinese_input", true)
        }

    val autoChineseSymbol: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("auto_chinese_symbol", true)
        }


    val vibrateLevel: Int?
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getString("vibrate_level", "40")!!.toInt()
        }


    val allowNumberMapping: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("accept_number_index", false)
        }

    val allowSymoblMapping: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("accept_symbol_index", false)
        }


    val switchEnglishModeHotKey: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("switch_english_mode", false)
        }

    val shiftSwitchEnglishMode: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("switch_english_mode_shift", true)
        }


    val autoHideSoftKeyboard: Boolean
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getBoolean("hide_software_keyboard_typing_with_physical", true)
        }

    var showArrowKeys: Int
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getString("show_arrow_key", "0")!!.toInt()
        }
        set(mode) {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            putStringAndBumpStartupConfigVersionIfChanged(sp, "show_arrow_key", mode.toString())
        }

    var splitKeyboard: Int
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getString("split_keyboard_mode", "0")!!.toInt()
        }
        set(mode) {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            putStringAndBumpStartupConfigVersionIfChanged(
                sp,
                "split_keyboard_mode",
                mode.toString()
            )
        }

    val keyboardTheme: Int
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getString("keyboard_theme", "6")!!.toInt()
        }

    val startupConfigVersion: Long
        get() {
            val sp =
                PreferenceManager.getDefaultSharedPreferences(ctx)
            return sp.getLong(STARTUP_CONFIG_VERSION, 0L)
        }

    @JvmName("getLanguageModeCompat")
    fun getLanguageMode(): Boolean = languageMode
    @JvmName("setLanguageModeCompat")
    fun setLanguageMode(englishOnly: Boolean) {
        languageMode = englishOnly
    }
    @JvmName("getFixedCandidateViewDisplayCompat")
    fun getFixedCandidateViewDisplay(): Boolean = fixedCandidateViewDisplay
    @JvmName("getLearnRelatedWordCompat")
    fun getLearnRelatedWord(): Boolean = learnRelatedWord
    @JvmName("getLearnPhraseCompat")
    fun getLearnPhrase(): Boolean = learnPhrase
    @JvmName("getDisablePhysicalSelKeyOptionCompat")
    fun getDisablePhysicalSelKeyOption(): Boolean = disablePhysicalSelKeyOption
    @JvmName("getEnglishPredictionCompat")
    fun getEnglishPrediction(): Boolean = englishPrediction
    @JvmName("getPhysicalKeyboardEnableCompat")
    fun getPhysicalKeyboardEnable(): Boolean = physicalKeyboardEnable
    @JvmName("getEnglishPredictionOnPhysicalKeyboardCompat")
    fun getEnglishPredictionOnPhysicalKeyboard(): Boolean = englishPredictionOnPhysicalKeyboard
    @JvmName("getSortSuggestionsCompat")
    fun getSortSuggestions(): Boolean = sortSuggestions
    @JvmName("getCandidateSuggestionPunctutationCompat")
    fun getCandidateSuggestionPunctutation(): Boolean = candidateSuggestionPunctutation
    @JvmName("getPhysicalKeyboardSortSuggestionsCompat")
    fun getPhysicalKeyboardSortSuggestions(): Boolean = physicalKeyboardSortSuggestions
    @JvmName("getSimiliarEnableCompat")
    fun getSimiliarEnable(): Boolean = similiarEnable
    @JvmName("getSelectDefaultOnSlidingCompat")
    fun getSelectDefaultOnSliding(): Boolean = selectDefaultOnSliding
    @JvmName("getVibrateOnKeyPressedCompat")
    fun getVibrateOnKeyPressed(): Boolean = vibrateOnKeyPressed
    @JvmName("getSoundOnKeyPressedCompat")
    fun getSoundOnKeyPressed(): Boolean = soundOnKeyPressed
    @JvmName("getEmojiDisplayPositionCompat")
    fun getEmojiDisplayPosition(): Int = emojiDisplayPosition ?: 5
    @JvmName("getPersistentLanguageModeCompat")
    fun getPersistentLanguageMode(): Boolean = persistentLanguageMode
    @JvmName("getShowNumberRowInEnglishCompat")
    fun getShowNumberRowInEnglish(): Boolean = showNumberRowInEnglish
    @JvmName("getIMActivatedStateCompat")
    fun getIMActivatedState(): String? = iMActivatedState
    @JvmName("setIMActivatedStateCompat")
    fun setIMActivatedState(state: String?) {
        iMActivatedState = state
    }
    @JvmName("getActiveIMCompat")
    fun getActiveIM(): String? = activeIM
    @JvmName("setActiveIMCompat")
    fun setActiveIM(activeIM: String?) {
        this.activeIM = activeIM
    }
    @JvmName("getThreerowRemappingCompat")
    fun getThreerowRemapping(): Boolean = threerowRemapping
    @JvmName("getPhysicalKeyboardTypeCompat")
    fun getPhysicalKeyboardType(): String = physicalKeyboardType
    @JvmName("getAutoCommitValueCompat")
    fun getAutoCommitValue(): Int = autoCommitValue
    @JvmName("getPhoneticKeyboardTypeCompat")
    fun getPhoneticKeyboardType(): String = phoneticKeyboardType
    @JvmName("getAutoCaptalizationCompat")
    fun getAutoCaptalization(): Boolean = autoCaptalization
    @JvmName("getQuickFixesCompat")
    fun getQuickFixes(): Boolean = quickFixes
    @JvmName("getAutoCompleteCompat")
    fun getAutoComplete(): Boolean = autoComplete
    @JvmName("getDisablePhysicalSelkeyCompat")
    fun getDisablePhysicalSelkey(): Boolean = disablePhysicalSelkey
    @JvmName("getHanCovertOptionCompat")
    fun getHanCovertOption(): Int = hanCovertOption ?: 0
    fun setHanCovertOption(value: Int) {
        hanCovertOption = value
    }
    @JvmName("getSelkeyOptionCompat")
    fun getSelkeyOption(): Int = selkeyOption ?: 0
    @JvmName("getSimilarCodeCandidatesCompat")
    fun getSimilarCodeCandidates(): Int = similarCodeCandidates ?: 20
    @JvmName("getFontSizeCompat")
    fun getFontSize(): Float = fontSize
    @JvmName("getKeyboardSizeCompat")
    fun getKeyboardSize(): Float = keyboardSize
    @JvmName("getSmartChineseInputCompat")
    fun getSmartChineseInput(): Boolean = smartChineseInput
    @JvmName("getAutoChineseSymbolCompat")
    fun getAutoChineseSymbol(): Boolean = autoChineseSymbol
    @JvmName("getVibrateLevelCompat")
    fun getVibrateLevel(): Int = vibrateLevel ?: 40
    @JvmName("getAllowNumberMappingCompat")
    fun getAllowNumberMapping(): Boolean = allowNumberMapping
    @JvmName("getAllowSymoblMappingCompat")
    fun getAllowSymoblMapping(): Boolean = allowSymoblMapping
    @JvmName("getSwitchEnglishModeHotKeyCompat")
    fun getSwitchEnglishModeHotKey(): Boolean = switchEnglishModeHotKey
    @JvmName("getShiftSwitchEnglishModeCompat")
    fun getShiftSwitchEnglishMode(): Boolean = shiftSwitchEnglishMode
    @JvmName("getAutoHideSoftKeyboardCompat")
    fun getAutoHideSoftKeyboard(): Boolean = autoHideSoftKeyboard
    @JvmName("getShowArrowKeysCompat")
    fun getShowArrowKeys(): Int = showArrowKeys
    @JvmName("setShowArrowKeysCompat")
    fun setShowArrowKeys(mode: Int) {
        showArrowKeys = mode
    }
    @JvmName("getSplitKeyboardCompat")
    fun getSplitKeyboard(): Int = splitKeyboard
    @JvmName("setSplitKeyboardCompat")
    fun setSplitKeyboard(mode: Int) {
        splitKeyboard = mode
    }
    @JvmName("getKeyboardThemeCompat")
    fun getKeyboardTheme(): Int = keyboardTheme
    @JvmName("getStartupConfigVersionCompat")
    fun getStartupConfigVersion(): Long = startupConfigVersion

    fun initializeStartupConfigVersion(): Long {
        val current = this.startupConfigVersion
        if (current > 0L) return current
        return bumpStartupConfigVersion()
    }

    fun resetStartupConfigVersion() {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        sp.edit().putLong(STARTUP_CONFIG_VERSION, 0L).apply()
    }

    fun resetStartupConfigVersionIfStartupPreferenceChanged(key: String?): Boolean {
        if (!isStartupConfigPreferenceKey(key)) return false
        resetStartupConfigVersion()
        return true
    }

    private fun putStringAndBumpStartupConfigVersionIfChanged(
        sp: SharedPreferences,
        key: String?,
        value: String
    ) {
        val current: String? = sp.getString(key, null)
        val editor = sp.edit().putString(key, value)
        if (value != current) {
            putNextStartupConfigVersion(sp, editor)
        }
        editor.apply()
    }

    private fun bumpStartupConfigVersion(): Long {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        val editor = sp.edit()
        val next = putNextStartupConfigVersion(sp, editor)
        editor.apply()
        return next
    }

    private fun putNextStartupConfigVersion(
        sp: SharedPreferences,
        editor: SharedPreferences.Editor
    ): Long {
        val current = sp.getLong(STARTUP_CONFIG_VERSION, 0L)
        val next = max(System.currentTimeMillis(), current + 1L)
        editor.putLong(STARTUP_CONFIG_VERSION, next)
        return next
    }

    fun getResetCacheFlag(defaultvalue: Boolean): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        return sp.getBoolean("searchsrv_reset_cache", defaultvalue)
    }


    fun setResetCacheFlag(value: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        sp.edit().putBoolean("searchsrv_reset_cache", value).apply()
    }


    /*
	 * INT Parameter SET/GET
	 */
    fun setParameter(label: String?, value: Int) {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        sp.edit().putInt(label, value).apply()
    }

    fun getParameterInt(label: String?): Int {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        return sp.getInt(label, 0)
    }

    fun getParameterInt(label: String?, defaultvalue: Int): Int {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        return sp.getInt(label, defaultvalue)
    }

    /*
	 * LONG Parameter SET/GET
	 */
    fun getParameterLong(label: String?, defaultvalue: Long): Long {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        return sp.getLong(label, defaultvalue)
    }

    fun getParameterLong(label: String?): Long {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        return sp.getLong(label, 0)
    }

    fun setParameter(label: String?, value: Long) {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        sp.edit().putLong(label, value).apply()
    }

    /*
	 * String Parameter SET/GET
	 */
    fun setParameter(label: String?, value: String?) {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        sp.edit().putString(label, value).apply()
    }

    fun getParameterString(label: String?): String {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        return sp.getString(label, "")!!
    }

    fun getParameterString(label: String?, defaultstring: String?): String {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        return sp.getString(label, defaultstring)!!
    }


    /*
	 * Boolean Parameter SET/GET
	 */
    fun setParameter(label: String?, value: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        sp.edit().putBoolean(label, value).apply()
    }

    fun getParameterBoolean(label: String?): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        return sp.getBoolean(label, false)
    }

    fun getParameterBoolean(label: String?, defaultvalue: Boolean): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        try {
            return sp.getBoolean(label, defaultvalue)
        } catch (e: Exception) {
            return defaultvalue
        }
    }

    private fun preProcessTableName(table: String): String {
        if (table.endsWith("_") || table.isEmpty()) {
            return table // processed already.
        } else if (table == LIME.DB_TABLE_PHONETIC) {
            return "bpmf_"
        } else if (table == "mapping" || table == "lime" || table == "phone") {
            return ""
        } else {
            return table + "_"
        }
    }

    companion object {
        private const val STARTUP_CONFIG_VERSION = "startup_config_version"

        @JvmStatic
        fun buildReverseLookupOptions(
            imList: List<ImConfig?>?,
            noneLabel: String?
        ): MutableList<ReverseLookupOption?> {
            val codes: MutableList<String?> = ArrayList<String?>()
            val labels: MutableList<String?> = ArrayList<String?>()
            if (imList != null) {
                for (im in imList) {
                    if (im == null || im.code == null || im.isDisable) continue
                    val code = im.code
                    if ("emoji" == code || indexOfIMCode(code) < 0) continue
                    codes.add(code)
                    val label = im.desc
                    labels.add(if (label == null || label.isEmpty()) fallbackIMLabel(code) else label)
                }
            }
            return buildReverseLookupOptions(codes, labels, noneLabel)
        }

        @JvmStatic
        fun buildReverseLookupOptions(
            codes: List<String?>?,
            labels: List<String?>?, noneLabel: String?
        ): MutableList<ReverseLookupOption?> {
            val options: MutableList<ReverseLookupOption?> = ArrayList<ReverseLookupOption?>()
            val seen: MutableSet<String?> = HashSet<String?>()
            val safeNoneLabel = if (noneLabel == null || noneLabel.isEmpty()) "none" else noneLabel
            options.add(ReverseLookupOption(safeNoneLabel, "none"))
            seen.add("none")
            if (codes != null) {
                for (i in codes.indices) {
                    val code = codes.get(i)
                    if (code == null || code.isEmpty() || seen.contains(code) || indexOfIMCode(code) < 0) {
                        continue
                    }
                    var label = if (labels != null && i < labels.size) labels.get(i) else null
                    if (label == null || label.isEmpty()) {
                        label = fallbackIMLabel(code)
                    }
                    options.add(ReverseLookupOption(label, code))
                    seen.add(code)
                }
            }
            return if (options.size > 1) options else fallbackReverseLookupOptions(safeNoneLabel)
        }

        @JvmStatic
		fun buildReverseLookupOptions(
            activeState: String?,
            noneLabel: String?
        ): MutableList<ReverseLookupOption?> {
            val codes: MutableList<String?> = ArrayList<String?>()
            val labels: MutableList<String?> = ArrayList<String?>()
            if (activeState != null && !activeState.trim { it <= ' ' }.isEmpty()) {
                for (raw in activeState.split(";".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()) {
                    if (raw.isEmpty()) continue
                    try {
                        val index = raw.toInt()
                        if (index < 0 || index >= LIME.IM_CODES.size) continue
                        codes.add(LIME.IM_CODES[index])
                        labels.add(if (index < LIME.IM_FULL_NAMES.size) LIME.IM_FULL_NAMES[index] else LIME.IM_SHORT_NAMES[index])
                    } catch (ignored: NumberFormatException) {
                    }
                }
            }
            return buildReverseLookupOptions(codes, labels, noneLabel)
        }

        @JvmOverloads
        @JvmStatic
        fun reverseLookupLabels(
            options: List<ReverseLookupOption?>?,
            noneLabel: String? = ""
        ): Array<String?> {
            val safeOptions: MutableList<ReverseLookupOption?> = ensureOptions(options, noneLabel)
            val labels = arrayOfNulls<String>(safeOptions.size)
            for (i in safeOptions.indices) {
                labels[i] = safeOptions.get(i)!!.label
            }
            return labels
        }

        @JvmOverloads
        @JvmStatic
        fun reverseLookupValues(
            options: List<ReverseLookupOption?>?,
            noneLabel: String? = ""
        ): Array<String?> {
            val safeOptions: MutableList<ReverseLookupOption?> = ensureOptions(options, noneLabel)
            val values = arrayOfNulls<String>(safeOptions.size)
            for (i in safeOptions.indices) {
                values[i] = safeOptions.get(i)!!.value
            }
            return values
        }

        private fun ensureOptions(
            options: List<ReverseLookupOption?>?,
            noneLabel: String?
        ): MutableList<ReverseLookupOption?> {
            return if (options == null || options.isEmpty()) fallbackReverseLookupOptions(noneLabel) else options.toMutableList()
        }

        private fun fallbackReverseLookupOptions(noneLabel: String?): MutableList<ReverseLookupOption?> {
            val options: MutableList<ReverseLookupOption?> = ArrayList<ReverseLookupOption?>()
            options.add(ReverseLookupOption(noneLabel, "none"))
            var i = 0
            while (i < LIME.IM_CODES.size && i < LIME.IM_FULL_NAMES.size) {
                options.add(ReverseLookupOption(LIME.IM_FULL_NAMES[i], LIME.IM_CODES[i]))
                i++
            }
            return options
        }

        private fun fallbackIMLabel(code: String?): String? {
            val index: Int = indexOfIMCode(code)
            if (index >= 0 && index < LIME.IM_FULL_NAMES.size) {
                return LIME.IM_FULL_NAMES[index]
            }
            if (index >= 0 && index < LIME.IM_SHORT_NAMES.size) {
                return LIME.IM_SHORT_NAMES[index]
            }
            return code
        }

        private fun indexOfIMCode(code: String?): Int {
            if (code == null) return -1
            for (i in 0..<LIME.IM_CODES.size) {
                if (code == LIME.IM_CODES[i]) {
                    return i
                }
            }
            return -1
        }


        private fun isStartupConfigPreferenceKey(key: String?): Boolean {
            if (key == null) return false
            when (key) {
                "keyboard_state", "keyboard_list", "show_arrow_key", "split_keyboard_mode", "keyboard_theme", "language_mode", "persistent_language_mode", "phonetic_keyboard_type", "number_row_in_english" -> return true
                else -> return key.endsWith("_keyboard_type")
            }
        }
    }
}
