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

import android.os.Environment
import android.util.Log
import java.io.File
import java.text.DecimalFormat

/**
 * Global constants and utility methods for LimeIME.
 * Merged from Lime.java and LIME.java.
 */
class LIME {
    companion object {
    @JvmField
	var PACKAGE_NAME: String? = null

    // Database Settings
    const val DATABASE_NAME: String = "lime.db"
    const val DATABASE_EXT: String = ".db"
    const val DATABASE_JOURNAL: String = "lime.db-journal"
    const val DATABASE_BACKUP_NAME: String = "backup.zip"
    const val DATABASE_JOURNAL_BACKUP_NAME: String = "backupJournal.zip"
    const val SHARED_PREFS_BACKUP_NAME: String = "shared_prefs.bak"
    const val DATABASE_CLOUD_TEMP: String = "cloudtemp.zip"

    @JvmStatic
	val limeDataRootFolder: String
        get() = Environment.getDataDirectory().toString() + "/data/" + PACKAGE_NAME


    // Jeremy '25/12/20 Open Foundry service is closed.
    //public static final String DATABASE_OPENFOUNDRY_URL_BASED = "https://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2F";
    const val DATABASE_CLOUD_URL_BASED: String =
        "https://github.com/SamLaio/limeime/raw/master/Database/"

    // Database Source File Names
    const val DATABASE_SOURCE_DAYI: String = "dayi.cin"
    const val DATABASE_SOURCE_PHONETIC: String = "phonetic.lime"
    const val DATABASE_SOURCE_PHONETIC_CNS: String = "bopomofo.cin"
    const val DATABASE_SOURCE_PHONETICADV: String = "phonetic_adv_CJK.lime"
    const val DATABASE_SOURCE_CJ: String = "cj.lime"
    const val DATABASE_SOURCE_CJ_CNS: String = "cangjie.cin"
    const val DATABASE_SOURCE_CJ5: String = "cj5.lime"
    const val DATABASE_SOURCE_ECJ: String = "ecj.lime"
    const val DATABASE_SOURCE_SCJ: String = "scj.lime"
    const val DATABASE_SOURCE_ARRAY: String = "array.lime"
    const val DATABASE_SOURCE_ARRAY10: String = "array10.lime"
    const val DATABASE_SOURCE_WB: String = "stroke5.cin"
    const val DATABASE_SOURCE_EZ: String = "ez.lime"
    const val DATABASE_SOURCE_PINYIN_BIG5: String = "pinyinbig5.cin"
    const val DATABASE_SOURCE_PINYIN_GB: String = "pinyin.cin"
    const val DATABASE_SOURCE_PINYIN_LIME: String = "pinyin_CJK.cin"
    const val DATABASE_SOURCE_CJ_LIME: String = "cj_CJK.lime"
    const val DATABASE_SOURCE_ECJ_LIME: String = "ecj_CJK.lime"
    const val DATABASE_SOURCE_PHONETIC_LIME: String = "phonetic_CJK.lime"
    const val DATABASE_SOURCE_FILENAME: String = "lime.zip"
    const val DATABASE_SOURCE_FILENAME_EMPTY: String = "empty.zip"

    // Database Cloud URLs
	@JvmField
	val DATABASE_CLOUD_IM_WB: String = DATABASE_CLOUD_URL_BASED + "wb.zip"
    const val DATABASE_CLOUD_IM_WB_KEYBOARD: String = "wb"
    @JvmField
	val DATABASE_CLOUD_IM_PINYINGB: String = DATABASE_CLOUD_URL_BASED + "pinyingb.zip"
    const val DATABASE_CLOUD_IM_PINYINGB_KEYBOARD: String = "lime"
    @JvmField
	val DATABASE_CLOUD_IM_PINYIN: String = DATABASE_CLOUD_URL_BASED + "pinyin.zip"
    const val DATABASE_CLOUD_IM_PINYIN_KEYBOARD: String = "lime"
    @JvmField
	val DATABASE_CLOUD_IM_PHONETICCOMPLETE_BIG5: String =
        DATABASE_CLOUD_URL_BASED + "phoneticcompletebig5.zip"
    @JvmField
	val DATABASE_CLOUD_IM_PHONETICCOMPLETE: String =
        DATABASE_CLOUD_URL_BASED + "phoneticcomplete.zip"
    @JvmField
	val DATABASE_CLOUD_IM_PHONETIC_BIG5: String = DATABASE_CLOUD_URL_BASED + "phoneticbig5.zip"
    @JvmField
	val DATABASE_CLOUD_IM_PHONETIC: String = DATABASE_CLOUD_URL_BASED + "phonetic.zip"
    const val DATABASE_CLOUD_IM_PHONETIC_KEYBOARD: String = "phonetic"
    @JvmField
	val DATABASE_CLOUD_IM_EZ: String = DATABASE_CLOUD_URL_BASED + "ez.limedb"
    const val DATABASE_CLOUD_IM_EZ_KEYBOARD: String = "ez"
    @JvmField
	val DATABASE_CLOUD_IM_ECJHK: String = DATABASE_CLOUD_URL_BASED + "ecjhk.zip"
    const val DATABASE_CLOUD_IM_ECJHK_KEYBOARD: String = "cj"
    @JvmField
	val DATABASE_CLOUD_IM_ECJ: String = DATABASE_CLOUD_URL_BASED + "ecj.zip"
    const val DATABASE_CLOUD_IM_ECJ_KEYBOARD: String = "cj"
    @JvmField
	val DATABASE_CLOUD_IM_DAYI: String = DATABASE_CLOUD_URL_BASED + "dayi.zip"
    @JvmField
    val DATABASE_CLOUD_IM_DAYIUNI_BIG5: String = DATABASE_CLOUD_URL_BASED + "dayiunibig5.zip"
    @JvmField
	val DATABASE_CLOUD_IM_DAYIUNI: String = DATABASE_CLOUD_URL_BASED + "dayiuni.zip"
    @JvmField
    val DATABASE_CLOUD_IM_DAYIUNIP_BIG5: String = DATABASE_CLOUD_URL_BASED + "dayiunipbig5.zip"
    @JvmField
	val DATABASE_CLOUD_IM_DAYIUNIP: String = DATABASE_CLOUD_URL_BASED + "dayiunip.zip"
    const val DATABASE_CLOUD_IM_DAYI_KEYBOARD: String = "dayisym"
    @JvmField
	val DATABASE_CLOUD_IM_CJHK: String = DATABASE_CLOUD_URL_BASED + "cjhk.zip"
    const val DATABASE_CLOUD_IM_CJHK_KEYBOARD: String = "cj"
    @JvmField
	val DATABASE_CLOUD_IM_SCJ: String = DATABASE_CLOUD_URL_BASED + "scj.zip"
    const val DATABASE_CLOUD_IM_SCJ_KEYBOARD: String = "limenum"
    @JvmField
	val DATABASE_CLOUD_IM_CJ5: String = DATABASE_CLOUD_URL_BASED + "cj5.zip"
    const val DATABASE_CLOUD_IM_CJ5_KEYBOARD: String = "cj"
    @JvmField
	val DATABASE_CLOUD_IM_CJ4: String = DATABASE_CLOUD_URL_BASED + "hahacj.limedb"
    const val DATABASE_CLOUD_IM_CJ4_KEYBOARD: String = "cj"
    @JvmField
	val DATABASE_CLOUD_IM_CJ_BIG5: String = DATABASE_CLOUD_URL_BASED + "cjbig5.zip"
    @JvmField
	val DATABASE_CLOUD_IM_CJ: String = DATABASE_CLOUD_URL_BASED + "cj.zip"
    const val DATABASE_CLOUD_IM_CJ_KEYBOARD: String = "cj"
    @JvmField
	val DATABASE_CLOUD_IM_ARRAY10: String = DATABASE_CLOUD_URL_BASED + "array10.limedb"
    const val DATABASE_CLOUD_IM_ARRAY10_KEYBOARD: String = "phonenum"
    @JvmField
	val DATABASE_CLOUD_IM_ARRAY: String = DATABASE_CLOUD_URL_BASED + "array.limedb"
    const val DATABASE_CLOUD_IM_ARRAY_KEYBOARD: String = "arraynum"
    @JvmField
	val DATABASE_CLOUD_IM_HS: String = DATABASE_CLOUD_URL_BASED + "hs.zip"
    @JvmField
	val DATABASE_CLOUD_IM_HS_V1: String = DATABASE_CLOUD_URL_BASED + "hs1.zip"
    @JvmField
	val DATABASE_CLOUD_IM_HS_V2: String = DATABASE_CLOUD_URL_BASED + "hs2.zip"
    @JvmField
	val DATABASE_CLOUD_IM_HS_V3: String = DATABASE_CLOUD_URL_BASED + "hs3.zip"
    const val DATABASE_CLOUD_IM_HS_KEYBOARD: String = "hs"

    // Database Tables
    const val DB_TABLE_IMTABLE2: String = "imtable2"
    const val DB_TABLE_IMTABLE3: String = "imtable3"
    const val DB_TABLE_IMTABLE4: String = "imtable4"
    const val DB_TABLE_IMTABLE5: String = "imtable5"
    const val DB_TABLE_IMTABLE6: String = "imtable6"
    const val DB_TABLE_IMTABLE7: String = "imtable7"
    const val DB_TABLE_IMTABLE8: String = "imtable8"
    const val DB_TABLE_IMTABLE9: String = "imtable9"
    const val DB_TABLE_IMTABLE10: String = "imtable10"
    const val DB_TABLE_ARRAY: String = "array"
    const val DB_TABLE_ARRAY10: String = "array10"
    const val DB_TABLE_CJ: String = "cj"
    const val DB_TABLE_CJ4: String = "cj4"
    const val DB_TABLE_CJ5: String = "cj5"
    const val DB_TABLE_CUSTOM: String = "custom"
    const val DB_TABLE_DAYI: String = "dayi"
    const val DB_TABLE_ECJ: String = "ecj"
    const val DB_TABLE_EZ: String = "ez"
    const val DB_TABLE_HS: String = "hs"
    const val DB_TABLE_PHONETIC: String = "phonetic"
    const val DB_TABLE_PINYIN: String = "pinyin"
    const val DB_TABLE_SCJ: String = "scj"
    const val DB_TABLE_WB: String = "wb"

    // Input Method Types
    const val IM_ARRAY: String = "array"
    const val IM_ARRAY10: String = "array10"
    const val IM_CJ_BIG5: String = "cjbig5"
    const val IM_CJ: String = "cj"
    const val IM_CJHK: String = "cjhk"
    const val IM_CJ4: String = "cj4"
    const val IM_CJ5: String = "cj5"
    const val IM_CUSTOM: String = "custom"
    const val IM_DAYI: String = "dayi"
    const val IM_DAYIUNI: String = "dayiuni"
    const val IM_DAYIUNI_BIG5: String = "dayiunibig5"
    const val IM_DAYIUNIP: String = "dayiunip"
    const val IM_DAYIUNIP_BIG5: String = "dayiunipbig5"
    const val IM_ECJ: String = "ecj"
    const val IM_ECJHK: String = "ecjhk"
    const val IM_EZ: String = "ez"
    const val IM_HS: String = "hs"
    const val IM_HS_V1: String = "hs1"
    const val IM_HS_V2: String = "hs2"
    const val IM_HS_V3: String = "hs3"
    const val IM_PHONETIC: String = "phonetic"
    const val IM_PHONETIC_ADV: String = "phoneticadv"
    const val IM_PHONETIC_BIG5: String = "phoneticbig5"
    const val IM_PHONETIC_ADV_BIG5: String = "phoneticadvbig5"

    const val IM_PHONETIC_STANDARD: String = "standard"

    const val KEYBOARD_NORMAL: String = "normal_keyboard"

    const val IM_PHONETIC_KEYBOARD_PHONETIC: String = "phonetic"
    const val IM_PHONETIC_KEYBOARD_HSU: String = "hsu"
    const val IM_PHONETIC_KEYBOARD_TYPE_HSU: String = "hsu"
    const val IM_PHONETIC_KEYBOARD_ETEN: String = "phoneticet41"
    const val IM_PHONETIC_KEYBOARD_TYPE_ETEN: String = "eten"
    const val IM_PHONETIC_KEYBOARD_TYPE_ETEN26: String = "eten26"
    const val IM_PHONETIC_KEYBOARD_ETEN26: String = "et26"
    const val IM_PHONETIC_KEYBOARD_TYPE_ETEN26_SYMBOL: String = "eten26_symbol"
    const val IM_PINYIN: String = "pinyin"
    const val IM_PINYINGB: String = "pinyingb"
    const val IM_SCJ: String = "scj"
    const val IM_WB: String = "wb"

    // Database Columns
    const val DB_COLUMN_ID: String = "_id"
    const val DB_COLUMN_CODE: String = "code"
    const val DB_COLUMN_CODE3R: String = "code3r"
    const val DB_COLUMN_WORD: String = "word"
    const val DB_COLUMN_RELATED: String = "related"
    const val DB_COLUMN_SCORE: String = "score"
    const val DB_COLUMN_BASESCORE: String = "basescore"

    // IM Table Columns
    const val DB_TABLE_IM: String = "im"
    const val DB_KEYBOARD: String = "keyboard"
    const val DB_IM_COLUMN_ID: String = "_id"
    const val DB_IM_COLUMN_CODE: String = "code"
    const val DB_IM_COLUMN_TITLE: String = "title"
    const val DB_IM_COLUMN_DESC: String = "desc"
    const val DB_IM_COLUMN_KEYBOARD: String = "keyboard"
    const val DB_IM_COLUMN_DISABLE: String = "disable"
    const val DB_IM_COLUMN_SELKEY: String = "selkey"
    const val DB_IM_COLUMN_ENDKEY: String = "endkey"
    const val DB_IM_COLUMN_SPACESTYLE: String = "spacestyle"

    // Related Table Columns
    const val DB_TABLE_RELATED: String = "related"
    const val DB_RELATED_COLUMN_ID: String = "_id"
    const val DB_RELATED_COLUMN_PWORD: String = "pword"
    const val DB_RELATED_COLUMN_CWORD: String = "cword"
    const val DB_RELATED_COLUMN_BASESCORE: String = "basescore"
    const val DB_RELATED_COLUMN_USERSCORE: String = "score"

    // Keyboard Table Columns
    const val DB_TABLE_KEYBOARD: String = "keyboard"
    const val DB_KEYBOARD_COLUMN_ID: String = "_id"
    const val DB_KEYBOARD_COLUMN_CODE: String = "code"
    const val DB_KEYBOARD_COLUMN_NAME: String = "name"
    const val DB_KEYBOARD_COLUMN_DESC: String = "desc"
    const val DB_KEYBOARD_COLUMN_TYPE: String = "type"
    const val DB_KEYBOARD_COLUMN_IMAGE: String = "image"
    const val DB_KEYBOARD_COLUMN_IMKB: String = "imkb"
    const val DB_KEYBOARD_COLUMN_IMSHIFTKB: String = "imshiftkb"
    const val DB_KEYBOARD_COLUMN_ENGKB: String = "engkb"
    const val DB_KEYBOARD_COLUMN_ENGSHIFTKB: String = "engshiftkb"
    const val DB_KEYBOARD_COLUMN_SYMBOLKB: String = "symbolkb"
    const val DB_KEYBOARD_COLUMN_SYMBOLSHIFTKB: String = "symbolshiftkb"
    const val DB_KEYBOARD_COLUMN_DEFAULTKB: String = "defaultkb"
    const val DB_KEYBOARD_COLUMN_DEFAULTSHIFTKB: String = "defaultshiftkb"
    const val DB_KEYBOARD_COLUMN_EXTENDEDKB: String = "extendedkb"
    const val DB_KEYBOARD_COLUMN_EXTENDEDSHIFTKB: String = "extendedshiftkb"
    const val DB_KEYBOARD_COLUMN_DISABLE: String = "disable"
    const val DB_TOTAL_COUNT: String = "count"

    // IM Type Fields
    const val IM_FULL_NAME: String = "name"
    const val IM_SOURCE: String = "source"
    const val IM_AMOUNT: String = "amount"
    const val IM_IMPORT: String = "import"
    const val IM_KEYBOARD: String = "keyboard"
    const val IM_SELKEY: String = "selkey"
    const val IM_ENDKEY: String = "endkey"
    const val IM_LIME_ENDKEY: String = "limeendkey"
    const val IM_SPACESTYLE: String = "spacestyle"

    // Database and IM Status
    const val IM_MANAGE_DISPLAY_AMOUNT: Int = 100
    const val DB_CHECK_RELATED_USERSCORE: String = "db_user_score_check"
    const val DATABASE_DOWNLOAD_STATUS: String = "database_download_status"
    const val DOWNLOAD_START: String = "download_start"
    const val IM_CJ_STATUS: String = "im_cj_status"
    const val IM_SCJ_STATUS: String = "im_scj_status"
    const val IM_PHONETIC_STATUS: String = "im_phonetic_status"
    const val IM_DAYI_STATUS: String = "im_dayi_status"
    const val IM_CUSTOM_STATUS: String = "im_custom_status"
    const val IM_EZ_STATUS: String = "im_ez_status"
    const val IM_MAPPING_FILENAME: String = "im_mapping_filename"
    const val IM_MAPPING_VERSION: String = "im_mapping_version"
    const val IM_MAPPING_TOTAL: String = "im_mapping_total"
    const val IM_MAPPING_DATE: String = "im_mapping_date"
    const val CANDIDATE_SUGGESTION: String = "candidate_suggestion"
    const val TOTAL_USERDICT_RECORD: String = "total_userdict_record"
    const val LEARNING_SWITCH: String = "learning_switch"

    // Cache Settings
    const val SEARCHSRV_RESET_CACHE_SIZE: Int = 256
    const val LIMEDB_CACHE_SIZE: Int = 1024

    // News and Content
    const val LIME_NEWS_CONTENT: String = "lime_news_content"
    const val LIME_NEWS_CONTENT_URL: String =
        "https://github.com/SamLaio/limeime/raw/master/Resources/Message/content.html"

    // File System
    @JvmField
    val separator: String = File.separator
    const val DATABASE_IM_TEMP: String = "temp"
    const val DATABASE_IM_TEMP_EXT: String = "zip"


    // UI Constants
    const val HALF_ALPHA_VALUE: Float = .5f
    const val NORMAL_ALPHA_VALUE: Float = 1f

    // Buffer Sizes (in bytes)
    const val BUFFER_SIZE_1KB: Int = 1024
    const val BUFFER_SIZE_2KB: Int = 2048
    const val BUFFER_SIZE_4KB: Int = 4096
    const val BUFFER_SIZE_64KB: Int = 65536

    const val HANDLER_DELAY_MINIMAL_MS: Int = 1 // Minimal delay for handler messages

    // Progress Percentage Constants
    const val PROGRESS_COMPLETE_PERCENT: Int = 100 // 100% progress

    // Emoji Parameters
    const val EMOJI_EN: Int = 1
    const val EMOJI_TW: Int = 2
    const val EMOJI_CN: Int = 3
    const val EMOJI_FIELD_TAG: String = "tag"
    const val EMOJI_FIELD_VALUE: String = "value"
    @JvmField
	val KEYCODE_EMOJI_PANEL: Int = -201
    @JvmField
	val KEYCODE_EMOJI_ABC: Int = -202
    @JvmField
	val KEYCODE_EMOJI_CATEGORY_RECENT: Int = -203
    @JvmField
    val KEYCODE_EMOJI_CATEGORY_SMILEYS: Int = -204
    @JvmField
    val KEYCODE_EMOJI_CATEGORY_PEOPLE: Int = -205
    @JvmField
    val KEYCODE_EMOJI_CATEGORY_ANIMALS: Int = -206
    @JvmField
    val KEYCODE_EMOJI_CATEGORY_FOOD: Int = -207
    @JvmField
    val KEYCODE_EMOJI_CATEGORY_TRAVEL: Int = -208
    @JvmField
    val KEYCODE_EMOJI_CATEGORY_ACTIVITIES: Int = -209
    @JvmField
    val KEYCODE_EMOJI_CATEGORY_OBJECTS: Int = -210
    @JvmField
	val KEYCODE_EMOJI_CATEGORY_SYMBOLS: Int = -211
    @JvmField
	val KEYCODE_EMOJI_CATEGORY_FLAGS: Int = -212

    // ========== Input Method Arrays (for buildActivatedIMList) ==========
    // These arrays correspond to the order used in IM activation state
    // Index 0=custom, 1=cj, 2=scj, 3=cj5, 4=ecj, 5=dayi, 6=phonetic, 7=ez, 8=array, 9=array10, 10=wb, 11=hs, 12=pinyin, 13=cj4
    /** Input method internal codes (matches keyboard_codes in strings_settings.xml)  */
	@JvmField
	val IM_CODES: Array<String?> = arrayOf<String?>(
        "custom", "cj", "scj", "cj5", "ecj", "dayi", "phonetic", "ez",
        "array", "array10", "wb", "hs", "pinyin", "cj4"
    )

    /** Input method full names in Traditional Chinese (matches keyboard in strings_settings.xml)  */
	@JvmField
	val IM_FULL_NAMES: Array<String?> = arrayOf<String?>(
        "自建輸入法", "倉頡輸入法", "快倉輸入法", "倉頡五代輸入法", "速成輸入法",
        "大易輸入法", "注音輸入法", "輕鬆輸入法", "行列輸入法", "行列10輸入法",
        "筆順五碼輸入法", "華象直覺輸入法", "拼音輸入法", "四碼倉頡輸入法"
    )

    /** Input method short names in Traditional Chinese (matches keyboardShortname in strings_settings.xml)  */
	@JvmField
	val IM_SHORT_NAMES: Array<String?> = arrayOf<String?>(
        "自建", "倉頡", "快倉", "倉頡五代", "速成", "大易", "注音", "輕鬆",
        "行列", "行列10", "筆順五碼", "華象直覺", "拼音", "四碼倉頡"
    )

    // Global Utility Methods
    @JvmStatic
    fun format(number: Int): String {
        try {
            val df = DecimalFormat("###,###,###,###,###,###,##0")
            return df.format(number.toLong())
        } catch (e: Exception) {
            Log.e("LIME", "Error formatting number", e)
            return "0"
        }
    }

    @JvmStatic
    fun formatSqlValue(value: String?): String {
        var value = value
        if (value != null) {
            value = value.replace("\"".toRegex(), "\"\"")
            value = value.replace("'".toRegex(), "\\\'")
            return value
        } else {
            return ""
        }
    }
    }
}
