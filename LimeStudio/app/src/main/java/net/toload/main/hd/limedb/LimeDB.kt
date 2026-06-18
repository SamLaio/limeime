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
@file:Suppress("SENSELESS_COMPARISON")

package net.toload.main.hd.limedb

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Pair
import android.util.SparseArray
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.InputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.Date
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedList
import java.util.Locale
import java.util.Objects
import kotlin.math.min
import net.toload.main.hd.data.ChineseSymbol
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.data.Keyboard
import net.toload.main.hd.data.Mapping
import net.toload.main.hd.data.Record
import net.toload.main.hd.data.Related
import net.toload.main.hd.global.LIME
import net.toload.main.hd.global.LIMEPreferenceManager
import net.toload.main.hd.global.LIMEProgressListener
import net.toload.main.hd.global.LIMEUtilities
import net.toload.main.hd.R
import net.toload.main.hd.SearchServer

/**
 * Main database helper class for LIME Input Method Engine.
 * 
 * 
 * This class manages all database operations for the LIME IME, including:
 * 
 *  * Mapping file loading and storage
 *  * Code-to-word query operations
 *  * Related phrase management
 *  * Input method (IM) and keyboard configuration
 *  * User dictionary operations
 *  * Database backup and restore
 * 
 * 
 * 
 * The database uses a shared static connection that is accessible by both
 * DBServer and SearchServer instances. The connection is managed through
 * [.openDBConnection] and can be held during maintenance operations
 * to prevent concurrent access.
 * 
 * 
 * Key features:
 * 
 *  * Supports multiple input methods (phonetic, dayi, array, cj, etc.)
 *  * Handles dual-code mapping for physical keyboards
 *  * Provides code remapping for different keyboard layouts
 *  * Manages related phrase learning and suggestions
 *  * Includes blacklist caching for invalid codes
 * 
 * 
 * @author The LimeIME Open Source Project
 * @version 3.0+
 * @since API Level 21
 */
open class LimeDB(private val mContext: Context) :
    LimeSQLiteOpenHelper(mContext, LIME.DATABASE_NAME, DATABASE_VERSION) {
    enum class EmojiLocale {
        EN,
        TW
    }

    class EmojiDataRow(
        @JvmField
        val value: String?,
        @JvmField
        val cp: String?,
        @JvmField
        val groupName: String?,
        @JvmField
        val subgroup: String?,
        @JvmField
        val sortOrder: Int,
        @JvmField
        val nameEn: String?,
        @JvmField
        val nameTw: String?,
        @JvmField
        val tagsEn: String?,
        @JvmField
        val tagsTw: String?,
        @JvmField
        val version: Double
    )

    private val keysDefMap = HashMap<String?, HashMap<String?, String?>?>()
    private val keysReMap = HashMap<String?, HashMap<String?, String?>?>()
    private val keysDualMap = HashMap<String?, HashMap<String?, String?>?>()

    private var lastCode: String? = ""
    private var lastValidDualCodeList: String? = ""

    private var filename: File? = null
    private var tableName = "custom"

    /**
     * Gets the count of records loaded during the last file loading operation.
     * 
     * @return The number of records loaded, or 0 if no loading operation has occurred
     */
    var countImported: Int = 0
        private set

    /**
     * Gets the progress percentage of the current file loading operation.
     * 
     * 
     * This value ranges from 0 to 100, representing the percentage of the
     * file that has been processed. Useful for progress reporting during
     * mapping file imports.
     * 
     * @return Progress percentage (0-100), or 0 if no loading operation is in progress
     */
    // Jeremy '15,5,23 for new progress listener progress status update
    var progressPercentageDone: Int = 0
        private set
    private var progressStatus: String? = null

    private var finish = false

    private var isPhysicalKeyboardPressed = false

    private val mLIMEPref: LIMEPreferenceManager

    private var importThread: Thread? = null
    private var exportThread: Thread? = null
    private var threadAborted = false

    // Cache for Related Score
    private val relatedScore = HashMap<String?, Int?>()

    // Han and Emoji Databases
    private var hanConverter: LimeHanConverter? = null

    private val SLEEP_DELAY_100_MS = 100

    /**
     * Sets the finish flag indicating whether file loading is complete.
     * 
     * 
     * This flag is used internally during file loading operations to track
     * completion status. It's set to true when loading completes successfully.
     * 
     * @param value true if loading is complete, false otherwise
     */
    fun setFinish(value: Boolean) {
        this.finish = value
    }

    /**
     * Sets the filename for the mapping file to be loaded into the database.
     * 
     * 
     * This method is called by DBServer before importing a text mapping file.
     * The file will be processed by [.importTxtTable].
     * 
     * @param filename The file to load, or null to clear the filename
     */
    fun setFilename(filename: File?) {
        this.filename = filename
    }

    /**
     * Sets the table name for word mapping queries.
     * 
     * 
     * This method is called by LIMEService to set the active input method table.
     * All subsequent queries will use this table name unless explicitly overridden.
     * 
     * 
     * The table name must be valid according to [.isValidTableName]
     * to prevent SQL injection attacks.
     * 
     * @param tableName The table name to use for queries (e.g., LIME.DB_TABLE_PHONETIC, LIME.DB_TABLE_DAYI, "custom")
     */
    fun setTableName(tableName: String) {
        this.tableName = tableName
        //checkLengthColumn(tableName);
        if (DEBUG) {
            Log.i(
                TAG, ("settTableName(), tableName:" + tableName + " this.tableName:"
                        + this.tableName)
            )
        }
    }

    /**
     * Safely retrieves a String value from a Cursor by column name.
     * 
     * 
     * This helper method prevents IndexOutOfBoundsException when a column
     * doesn't exist in the cursor. Returns an empty string if the column is missing.
     * 
     * @param cursor The Cursor to read from
     * @param columnName The name of the column to retrieve
     * @return The string value, or empty string if column doesn't exist
     */
    private fun getCursorString(cursor: Cursor, columnName: String?): String {
        if (columnName == null) {
            return "" // defensive: avoid passing null into SQLiteCursor.getColumnIndex
        }
        val index = cursor.getColumnIndex(columnName)
        if (index != -1) {
            val value = cursor.getString(index)
            return if (value != null) value else "" // Return empty string if column value is NULL
        }
        return "" // Return empty string if column is missing
    }

    /**
     * Safely retrieves an integer value from a Cursor by column name.
     * 
     * 
     * This helper method prevents IndexOutOfBoundsException when a column
     * doesn't exist in the cursor. Returns 0 if the column is missing.
     * 
     * @param cursor The Cursor to read from
     * @param columnName The name of the column to retrieve
     * @return The integer value, or 0 if column doesn't exist
     */
    private fun getCursorInt(cursor: Cursor, columnName: String?): Int {
        if (columnName == null) {
            return 0 // defensive: avoid passing null into SQLiteCursor.getColumnIndex
        }
        val index = cursor.getColumnIndex(columnName)
        if (index != -1) {
            return cursor.getInt(index)
        }
        return 0 // Return 0 if column is missing
    }

    // ==================== Cursor to Object Conversion ====================
    /**
     * Creates a Record object from current cursor row.
     * 
     * 
     * This method reads the current row from the cursor and creates
     * a Record object with the column values. The cursor should be
     * positioned at the desired row before calling this method.
     * 
     * @param cursor The Cursor positioned at the desired row
     * @return A new Record object populated with cursor data
     */
    fun recordFromCursor(cursor: Cursor): Record {
        val record = Record()
        record.setId(getCursorString(cursor, LIME.DB_COLUMN_ID))
        record.setCode(getCursorString(cursor, LIME.DB_COLUMN_CODE))
        record.setCode3r(getCursorString(cursor, LIME.DB_COLUMN_CODE3R))
        record.setWord(getCursorString(cursor, LIME.DB_COLUMN_WORD))
        record.setRelated(getCursorString(cursor, LIME.DB_COLUMN_RELATED))
        record.setScore(getCursorInt(cursor, LIME.DB_COLUMN_SCORE))
        record.setBasescore(getCursorInt(cursor, LIME.DB_COLUMN_BASESCORE))
        return record
    }

    /**
     * Converts a Cursor to a List of Record objects.
     * 
     * 
     * This method iterates through all rows in the cursor and creates
     * Record objects for each row. The cursor is closed after processing.
     * 
     * @param cursor The Cursor containing database query results
     * @return List of Record objects
     */
    fun recordListFromCursor(cursor: Cursor): MutableList<Record?> {
        val list: MutableList<Record?> = ArrayList<Record?>()
        cursor.moveToFirst()
        while (!cursor.isAfterLast()) {
            list.add(recordFromCursor(cursor))
            cursor.moveToNext()
        }
        cursor.close()
        return list
    }

    /**
     * Creates a Related object from current cursor row (for related table).
     * 
     * 
     * This method reads from the related table columns (pword, cword, userscore).
     * 
     * @param cursor The Cursor positioned at the desired row
     * @return A new Related object populated with cursor data
     */
    fun relatedFromCursor(cursor: Cursor): Related {
        val record = Related()
        record.setId(getCursorString(cursor, LIME.DB_RELATED_COLUMN_ID))
        record.setPword(getCursorString(cursor, LIME.DB_RELATED_COLUMN_PWORD))
        record.setCword(getCursorString(cursor, LIME.DB_RELATED_COLUMN_CWORD))
        record.setUserscore(getCursorInt(cursor, LIME.DB_RELATED_COLUMN_USERSCORE))
        record.setBasescore(getCursorInt(cursor, LIME.DB_RELATED_COLUMN_BASESCORE))
        return record
    }

    /**
     * Converts a Cursor to a List of Related objects.
     * 
     * @param cursor The Cursor containing database query results from related table
     * @return List of Related objects
     */
    fun relatedListFromCursor(cursor: Cursor): MutableList<Related?> {
        val list: MutableList<Related?> = ArrayList<Related?>()
        cursor.moveToFirst()
        while (!cursor.isAfterLast()) {
            list.add(relatedFromCursor(cursor))
            cursor.moveToNext()
        }
        cursor.close()
        return list
    }

    /**
     * Validates table name against whitelist to prevent SQL injection.
     * 
     * 
     * This method checks if the provided table name is in the whitelist of
     * valid table names. It also supports backup table patterns (e.g., "phonetic_user").
     * 
     * 
     * Valid table names include standard IM tables (phonetic, dayi, array, etc.),
     * system tables (related, im, keyboard), and backup tables ending with "_user".
     * 
     * @param tableName The table name to validate
     * @return true if table name is valid and safe to use, false otherwise
     */
    open fun isValidTableName(tableName: String?): Boolean {
        if (tableName == null || tableName.isEmpty()) {
            return false
        }
        // Whitelist of valid table names
        val validTables = arrayOf<String?>(
            LIME.DB_TABLE_ARRAY, LIME.DB_TABLE_ARRAY10,
            LIME.DB_TABLE_CJ, LIME.DB_TABLE_CJ4, LIME.DB_TABLE_CJ5, LIME.DB_TABLE_CUSTOM,
            LIME.DB_TABLE_DAYI, LIME.DB_TABLE_ECJ, LIME.DB_TABLE_EZ,
            LIME.DB_TABLE_HS, LIME.DB_TABLE_PHONETIC, LIME.DB_TABLE_PINYIN,
            LIME.DB_TABLE_SCJ, LIME.DB_TABLE_WB,
            LIME.DB_TABLE_IMTABLE2, LIME.DB_TABLE_IMTABLE3, LIME.DB_TABLE_IMTABLE4,
            LIME.DB_TABLE_IMTABLE5, LIME.DB_TABLE_IMTABLE6, LIME.DB_TABLE_IMTABLE7,
            LIME.DB_TABLE_IMTABLE8, LIME.DB_TABLE_IMTABLE9, LIME.DB_TABLE_IMTABLE10,
            LIME.DB_TABLE_RELATED, LIME.DB_TABLE_IM, LIME.DB_TABLE_KEYBOARD
        )
        // Check exact match
        for (validTable in validTables) {
            if (validTable == tableName) {
                return true
            }
        }
        // Check for backup table pattern: "tableName_user" where tableName is valid
        if (tableName.endsWith("_user")) {
            val baseTable = tableName.substring(0, tableName.length - 5)
            for (validTable in validTables) {
                if (validTable == baseTable) {
                    return true
                }
            }
        }
        return false
    }


    /**
     * Gets the current table name used for queries.
     * 
     * @return The current table name, or "custom" if not set
     */
    fun getTableName(): String {
        return this.tableName
    }


    /**
     * Handles database schema upgrades when the database version changes.
     * 
     * 
     * Currently, all upgrade code has been removed. This method logs the
     * version change but does not perform any migration. Future upgrades
     * should be implemented here if needed.
     * 
     * @param db The database to upgrade
     * @param oldVersion The old database version
     * @param newVersion The new database version
     */
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        val dbin = db ?: return
        Log.i(TAG, "OnUpgrade() db old version = " + oldVersion + ", new version = " + newVersion)
        if (oldVersion < 102) {
            val startTime = System.currentTimeMillis()
            val cv = ContentValues()
            cv.put(LIME.DB_KEYBOARD_COLUMN_CODE, "wb")
            cv.put(LIME.DB_KEYBOARD_COLUMN_NAME, "筆順五碼")
            cv.put(LIME.DB_KEYBOARD_COLUMN_DESC, "筆順五碼建盤")
            cv.put(LIME.DB_KEYBOARD_COLUMN_TYPE, "phone")
            cv.put(LIME.DB_KEYBOARD_COLUMN_IMAGE, "wb_keyboard_preview")
            cv.put(LIME.DB_KEYBOARD_COLUMN_IMKB, "lime_wb")
            cv.put(LIME.DB_KEYBOARD_COLUMN_IMSHIFTKB, "lime_wb")
            cv.put(LIME.DB_KEYBOARD_COLUMN_ENGKB, "lime_abc")
            cv.put(LIME.DB_KEYBOARD_COLUMN_ENGSHIFTKB, "lime_abc_shift")
            cv.put(LIME.DB_KEYBOARD_COLUMN_SYMBOLKB, "symbols")
            cv.put(LIME.DB_KEYBOARD_COLUMN_SYMBOLSHIFTKB, "symbols_shift")
            cv.put(LIME.DB_KEYBOARD_COLUMN_DISABLE, false)
            dbin.insert(LIME.DB_TABLE_KEYBOARD, null, cv)

            cv.clear()
            cv.put(LIME.DB_KEYBOARD_COLUMN_CODE, "hs")
            cv.put(LIME.DB_KEYBOARD_COLUMN_NAME, "華象直覺")
            cv.put(LIME.DB_KEYBOARD_COLUMN_DESC, "華象直覺建盤")
            cv.put(LIME.DB_KEYBOARD_COLUMN_TYPE, "phone")
            cv.put(LIME.DB_KEYBOARD_COLUMN_IMAGE, "hs_keyboard_preview")
            cv.put(LIME.DB_KEYBOARD_COLUMN_IMKB, "lime_hs")
            cv.put(LIME.DB_KEYBOARD_COLUMN_IMSHIFTKB, "lime_hs_shift")
            cv.put(LIME.DB_KEYBOARD_COLUMN_ENGKB, "lime_abc")
            cv.put(LIME.DB_KEYBOARD_COLUMN_ENGSHIFTKB, "lime_abc_shift")
            cv.put(LIME.DB_KEYBOARD_COLUMN_SYMBOLKB, "symbols")
            cv.put(LIME.DB_KEYBOARD_COLUMN_SYMBOLSHIFTKB, "symbols_shift")
            cv.put(LIME.DB_KEYBOARD_COLUMN_DISABLE, false)
            dbin.insert(LIME.DB_TABLE_KEYBOARD, null, cv)

            val endTime = System.currentTimeMillis()
            Log.i(
                TAG,
                "OnUpgrade() upgrade database to verser 102.  Elapsed time = " + (endTime - startTime) + "ms."
            )
        }
        // Emoji payload currency is NOT decided here. ensureCurrentDatabase() ->
        // refreshEmojiDataIfNeeded() runs on every open/restore/factory-reset (right after
        // getWritableDatabase() in the constructor), re-creates the emoji tables
        // idempotently, and imports/refreshes data gated on the im-table version row
        // (im.code='emoji', title='version'), not on the DB user_version. Gating emoji on
        // an onUpgrade(oldVersion<103) line was insufficient and contributed to the #88
        // restore-crash family (a restored DB can claim a current version but carry stale
        // schema, skipping onUpgrade). Do NOT reintroduce a version-gated emoji line here;
        // the same rule applies to the English dictionary payload.
        if (oldVersion < 104) {
            ensureCj4Schema(dbin)
        }
    }


    /**
     * Checks and updates the related table structure if needed.
     * 
     * 
     * This method ensures the related table has the required columns and indexes:
     * 
     *  * Adds basescore column if missing
     *  * Creates pword index if missing
     *  * Creates cword index if missing
     * 
     * 
     * 
     * This is typically called during database initialization or upgrade.
     */
    fun checkAndUpdateRelatedTable() {
        // Check related table structure
        val CHECK_RELATED = "SELECT basescore FROM " + LIME.DB_TABLE_RELATED


        rawQuery(CHECK_RELATED).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) {
                try {
                    var add_column = "ALTER TABLE " + LIME.DB_TABLE_RELATED + " ADD "
                    add_column += LIME.DB_RELATED_COLUMN_BASESCORE + " INTEGER"

                    db!!.execSQL(add_column)

                    // Download and restore related DB
                } catch (e: SQLiteException) {
                    Log.e(TAG, "Error in database operation", e)
                }
            }
        }
        db!!.query(
            "sqlite_master",
            null,
            "type='index' and name = 'related_idx_pword'",
            null,
            null,
            null,
            null
        ).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) {
                try {
                    db!!.execSQL("create index 'related_idx_pword' on related (pword)")
                } catch (e: SQLiteException) {
                    Log.e(TAG, "Error in database operation", e)
                }
            }
        }
        db!!.query(
            "sqlite_master",
            null,
            "type='index' and name = 'related_idx_cword'",
            null,
            null,
            null,
            null
        ).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) {
                try {
                    db!!.execSQL("create index 'related_idx_cword' on related (cword)")
                } catch (e: SQLiteException) {
                    Log.e(TAG, "Error in database operation", e)
                }
            }
        }
    }


    //    public void checkPhoneticKeyboardSetting() {
    //        if (checkDBConnection()) return;
    //        try {
    //            checkPhoneticKeyboardSettingOnDB(db);
    //        } catch (Exception e) {
    //            Log.e(TAG, "Error in database operation", e);
    //        }
    //
    //
    //    }
    /**
     * Checks and updates phonetic keyboard settings consistency between preferences and database.
     * 
     * 
     * This method ensures that the keyboard configuration stored in the database
     * matches the user's preference setting. It handles different phonetic keyboard types:
     * 
     *  * hsu - Hsu phonetic keyboard
     *  * eten26 - ETEN 26-key phonetic keyboard
     *  * eten - ETEN 41-key phonetic keyboard
     *  * standard - Standard BPMF phonetic keyboard (default)
     * 
     * 
     * 
     * If the database configuration doesn't match the preference, it updates the
     * database to match the preference setting.
     */
    open fun checkPhoneticKeyboardSetting() {
        if (checkDBConnection()) return
        val selectedPhoneticKeyboardType = mLIMEPref.getPhoneticKeyboardType()
        if (DEBUG) Log.i(
            "OnUpgrade()",
            "checkPhoneticKeyboardSettingOnDB:" + selectedPhoneticKeyboardType
        )
        when (selectedPhoneticKeyboardType) {
            LIME.IM_PHONETIC_KEYBOARD_TYPE_HSU -> setIMConfigKeyboard(
                LIME.IM_PHONETIC,
                getKeyboardInfo(LIME.IM_PHONETIC_KEYBOARD_HSU, LIME.DB_IM_COLUMN_DESC),
                LIME.IM_PHONETIC_KEYBOARD_HSU
            ) //jeremy '12,6,6 new hsu and et26 keybaord

            LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26 -> setIMConfigKeyboard(
                LIME.IM_PHONETIC,
                getKeyboardInfo(LIME.IM_PHONETIC_KEYBOARD_ETEN26, LIME.DB_IM_COLUMN_DESC),
                LIME.IM_PHONETIC_KEYBOARD_ETEN26
            )

            LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN -> setIMConfigKeyboard(
                LIME.IM_PHONETIC,
                getKeyboardInfo(LIME.IM_PHONETIC_KEYBOARD_ETEN, LIME.DB_IM_COLUMN_DESC),
                LIME.IM_PHONETIC_KEYBOARD_ETEN
            )

            else -> setIMConfigKeyboard(
                LIME.IM_PHONETIC,
                getKeyboardInfo(LIME.IM_PHONETIC_KEYBOARD_PHONETIC, LIME.DB_IM_COLUMN_DESC),
                LIME.IM_PHONETIC_KEYBOARD_PHONETIC
            )
        }
    }


    /**
     * Opens or reopens the database connection.
     * 
     * 
     * This method manages the shared static database connection. If force_reload
     * is true, it closes any existing connection and opens a new one. Otherwise,
     * it returns true if a valid connection already exists.
     * 
     * 
     * When reopening, this method also clears the related phrase score cache.
     * 
     * @param force_reload If true, force close and reopen the connection; if false,
     * return true if connection already exists
     * @return true if database connection is open and ready, false otherwise
     */
    fun openDBConnection(force_reload: Boolean): Boolean {
        if (DEBUG) {
            Log.i(TAG, "openDBConnection(), force_reload = " + force_reload)
            if (db != null) Log.i(TAG, "db.isOpen()" + db!!.isOpen())
        }

        if (!force_reload && db != null && db!!.isOpen()) {
            try {
                db!!.rawQuery("SELECT 1", null).use { cursor ->
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "openDBConnection(): existing database handle is stale, reopening", e)
                try {
                    db!!.close()
                } catch (closeError: Exception) {
                    Log.e(TAG, "Error closing stale database handle", closeError)
                }
            }
        }

        // Reset related phrase score cache
        relatedScore.clear()

        if (force_reload) {
            try {
                if (db != null && db!!.isOpen()) {
                    db!!.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in database operation", e)
            }
        }
        db = this.writableDatabase
        isDatabaseOnHold = false
        return db != null && db!!.isOpen()
    }

    fun ensureCurrentDatabase() {
        if (checkDBConnection()) {
            return
        }

        Companion.ensureCj4Schema(db!!)
        if (db!!.getVersion() < DATABASE_VERSION) {
            db!!.setVersion(DATABASE_VERSION)
        }
        refreshEmojiDataIfNeeded()
        refreshDictionaryDataIfNeeded()
    }

    /**
     * Jeremy '12,5,1  checkDBConnection try to openDBConnection if db is not open.
     * Return true if the db connection is valid, return false if dbConnection is not valid
     * 
     * @return return true if db connection is ready.
     */
    private fun checkDBConnection(): Boolean {
        //Jeremy '12,5,1 mapping loading. db is locked
        if (DEBUG) Log.i(TAG, "checkDBConnection()")

        if (isDatabaseOnHold) {   //mapping loading in progress, database is not available for query
            if (DEBUG) Log.i(TAG, "checkDBConnection() : mapping loading ")


            // Only show Toast and loop on main thread (UI thread)
            // In test environments or background threads, don't block indefinitely
            val mainLooper = Looper.getMainLooper()
            if (mainLooper != null && Looper.myLooper() == mainLooper) {
                // We're on the main thread, safe to show Toast and loop
                //Toast.makeText(mContext, mContext.getText(R.string.l3_database_loading), Toast.LENGTH_SHORT).show();
                //Looper.loop();
                // After loop returns, check connection again
                return !openDBConnection(false)
            } else {
                // We're on a background thread or in test environment
                // Don't block indefinitely - wait with timeout instead
                if (DEBUG) Log.w(
                    TAG,
                    "checkDBConnection() : database on hold but not on main thread, waiting with timeout"
                )


                // Wait up to 5 seconds for database to become available
                val startTime = System.currentTimeMillis()
                val timeoutMs: Long = 5000 // 5 second timeout

                while (isDatabaseOnHold && (System.currentTimeMillis() - startTime) < timeoutMs) {
                    try {
                        Thread.sleep(100) // Check every 100ms
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }

                if (isDatabaseOnHold) {
                    Log.w(
                        TAG,
                        "checkDBConnection() : database still on hold after timeout, returning error"
                    )
                    return true // Return error (database not available)
                }
                // Database became available, fall through to check connection
            }
        }


        // Check database connection (either databaseOnHold was false, or it became false after waiting)
        return !openDBConnection(false)
    }

    /**
     * Deletes all records from the specified tableName.
     * 
     * 
     * This method:
     * 
     *  * Waits for any active loading thread to complete
     *  * Deletes all records from the tableName if any exist
     *  * Resets IM information for the tableName
     *  * Clears the blacklist cache
     * 
     * 
     * 
     * This is typically used when reloading a mapping file or resetting
     * an input method tableName.
     * 
     * @param tableName The tableName name to clear (must be a valid tableName name)
     */
    open fun clearTable(tableName: String) {
        if (DEBUG) Log.i(TAG, "clearTable()")
        if (importThread != null) {
            threadAborted = true
            while (importThread!!.isAlive()) {
                Log.d(TAG, "clearTable():waiting for loadingMappingThread stopped...")
                SystemClock.sleep(SLEEP_DELAY_100_MS.toLong())
            }
        }

        if (countRecords(tableName, null, null) > 0) db!!.delete(tableName, null, null)


        finish = false
        resetImConfig(tableName)

        if (blackListCache != null) blackListCache!!.clear() //Jeremy '12, 6,3 clear black list cache after mapping file updated 


        // Reset cache in SearchServer to ensure consistency
        SearchServer.resetCache(true)
    }


    /**
     * Counts records in a table with optional filtering.
     * 
     * 
     * This is the unified method for counting records. It supports optional
     * WHERE clause filtering and uses parameterized queries for security.
     * 
     * 
     * This method replaces the need for multiple count methods:
     * 
     *  * Use with null whereClause for all records
     *  * Use with WHERE clause for filtered records
     * 
     * 
     * @param table The table name to count records from
     * @param whereClause Optional WHERE clause (null for all records). Use "?" placeholders for values.
     * @param whereArgs Optional WHERE arguments for parameterized queries (null if whereClause is null)
     * @return The number of matching records, or 0 if error or empty
     */
    open fun countRecords(table: String?, whereClause: String?, whereArgs: Array<String?>?): Int {
        if (checkDBConnection()) return 0

        try {
            // Validate table name before using in query
            if (!isValidTableName(table)) {
                Log.e(TAG, "countRecords(): Invalid table name: " + table)
                return 0
            }

            // Verify table exists before querying
            val tableCheck: Cursor = db!!.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf<String?>(table)
            )
            val tableExists = tableCheck != null && tableCheck.getCount() > 0
            if (tableCheck != null) {
                tableCheck.close()
            }
            if (!tableExists) {
                Log.w(TAG, "countRecords(): Table not found: " + table)
                return 0
            }

            val queryBuilder = StringBuilder("SELECT COUNT(*) as count FROM ")
            queryBuilder.append(table)

            if (whereClause != null && !whereClause.isEmpty()) {
                queryBuilder.append(" WHERE ").append(whereClause)
            }

            val cursor: Cursor = db!!.rawQuery(queryBuilder.toString(), whereArgs)
            if (cursor == null) return 0

            var total = 0
            if (cursor.moveToFirst()) {
                total = getCursorInt(cursor, LIME.DB_TOTAL_COUNT)
            }
            cursor.close()

            if (DEBUG) {
                Log.i(TAG, "countRecords() Table: " + table + ", Count: " + total)
            }
            return total
        } catch (e: Exception) {
            Log.e(TAG, "countRecords(): Error in database operation", e)
        }
        return 0
    }


    /**
     * Resets a mapping tableName by deleting all records and clearing the cache.
     * 
     * 
     * This method safely deletes all records from the specified tableName and then
     * resets the SearchServer cache to ensure consistency. This is typically
     * used when reloading mapping data.
     * 
     * 
     * The method performs the following operations:
     * 
     *  * Validates the tableName name to prevent SQL injection
     *  * Checks database connection status
     *  * Deletes all records from the specified tableName
     *  * Resets the SearchServer cache to ensure consistency
     * 
     * 
     * 
     * If the tableName name is invalid or the database connection is unavailable,
     * the method will log an error and return without performing any operations.
     * 
     * @param tableName The tableName name to reset (must be valid according to [.isValidTableName])
     * @throws IllegalArgumentException if tableName name is null or empty
     */
    //    public void clearTable(String tableName) {
    //        if (tableName == null || tableName.isEmpty()) {
    //            Log.e(TAG, "clearTable(): Table name cannot be null or empty");
    //            throw new IllegalArgumentException("Table name cannot be null or empty");
    //        }
    //
    //        if (!isValidTableName(tableName)) {
    //            Log.e(TAG, "clearTable(): Invalid tableName name: " + tableName);
    //            return;
    //        }
    //
    //        if (checkDBConnection()) {
    //            Log.e(TAG, "resetMapping(): Database connection unavailable");
    //            return;
    //        }
    //
    //        if (DEBUG) {
    //            Log.i(TAG, "clearTable() on " + tableName);
    //        }
    //
    //        try {
    //            clearTable(tableName);
    //
    //            // Reset cache in SearchServer to ensure consistency
    //            net.toload.main.hd.SearchServer.resetCache(true);
    //        } catch (Exception e) {
    //            Log.e(TAG, "clearTable(): Error resetting mapping tableName: " + tableName, e);
    //        }
    //    }
    /**
     * Resets the SearchServer cache.
     * 
     * 
     * This method clears the cache maintained by SearchServer to ensure
     * that subsequent queries reflect the current database state. This should
     * be called after any database modifications that affect search results.
     */
    open fun resetCache() {
        SearchServer.resetCache(true)
    }


    /**
     * Adds or updates a related phrase record in the database.
     * 
     * 
     * This method handles user dictionary learning for related phrases:
     * 
     *  * If the phrase doesn't exist, creates a new record with score 1
     *  * If the phrase exists, increments the user score
     *  * Removes Chinese symbols if learning is enabled
     *  * Updates the total user dictionary record count
     * 
     * 
     * 
     * The method respects the user's "learn related words" preference setting.
     * If learning is disabled and cword is not null, the method returns -1.
     * 
     * @param pword The parent word (previous word in the phrase)
     * @param cword The child word (next word in the phrase), or null for frequency tracking
     * @return The updated score after the operation, or -1 if operation was skipped
     */
    @Synchronized
    open fun addOrUpdateRelatedPhraseRecord(pword: String?, cword: String?): Int {
        //Jeremy '12,4,17 !checkDBConnection() when db is restoring or replaced.

        var cword = cword
        if (checkDBConnection()) return -1

        // Jeremy '11,6,12
        // Return if not learning related words and cword is not null (recording word frequency in IM relatedlist field)
        if (!mLIMEPref.getLearnRelatedWord() && cword != null) return -1

        // Remove all the chinese symbols from the related words
        if (mLIMEPref.getLearnRelatedWord()) {
            try {
                // Remove Punctuation
                val chineseSymbols = ChineseSymbol.chineseSymbols.split("\\|".toRegex())
                    .dropLastWhile { it.isEmpty() }.toTypedArray()
                for (s in chineseSymbols) {
                    checkNotNull(cword)
                    cword = cword.replace(s.toRegex(), "")
                    if (cword.isEmpty()) {
                        return -1
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in database operation", e)
            }
        }

        var dictotal = mLIMEPref.totalUserdictRecords?.toInt() ?: 0

        if (DEBUG) Log.i(
            TAG,
            "addOrUpdateRelatedPhraseRecord(): pword:" + pword + " cword:" + cword + "dictotoal:" + dictotal
        )

        var score = 1

        val cv = ContentValues()
        try {
            val munit = this.isRelatedPhraseExistOnDB(db!!, pword, cword)

            if (munit == null) {
                cv.put(LIME.DB_RELATED_COLUMN_PWORD, pword)
                cv.put(LIME.DB_RELATED_COLUMN_CWORD, cword)
                cv.put(LIME.DB_RELATED_COLUMN_USERSCORE, score)
                db!!.insert(LIME.DB_TABLE_RELATED, null, cv)
                dictotal++
                mLIMEPref.totalUserdictRecords = dictotal.toString()
                if (DEBUG) Log.i(
                    TAG,
                    "addOrUpdateRelatedPhraseRecord(): new record, dictotal:" + dictotal
                )
            } else { //the item exist in preload related database.
                val existingScore = relatedScore.get(munit.getId())
                if (existingScore == null) {
                    score = munit.getScore() + 1
                    relatedScore.put(munit.getId(), score)
                } else {
                    score = existingScore + 1
                    relatedScore.put(munit.getId(), score)
                }
                cv.put(LIME.DB_RELATED_COLUMN_USERSCORE, score)
                db!!.update(LIME.DB_TABLE_RELATED, cv, FIELD_ID + " = " + munit.getId(), null)


                if (DEBUG) Log.i(
                    TAG,
                    "addOrUpdateRelatedPhraseRecord():update score on existing record; score:" + score
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in addOrUpdateRelatedPhraseRecord() database operation", e)
        }

        return score
    }

    /**
     * Adds or updates a mapping record in the current table with default score.
     * 
     * 
     * This is a convenience method that calls
     * [.addOrUpdateMappingRecord] with
     * the current tablename and a score of -1 (which triggers auto-increment).
     * 
     * @param code The input code
     * @param word The output word
     */
    @Synchronized
    open fun addOrUpdateMappingRecord(code: String, word: String) {
        addOrUpdateMappingRecord(this.tableName, code, word, -1)
    }

    /**
     * Adds or updates a mapping record in the specified table.
     * 
     * 
     * This method handles user dictionary learning for code-to-word mappings:
     * 
     *  * If the mapping doesn't exist, creates a new record
     *  * If the mapping exists, updates the score
     *  * For phonetic table, also creates/updates the no-tone code column
     *  * Removes the code from blacklist cache if it was previously blacklisted
     * 
     * 
     * 
     * If score is -1, the method will auto-increment the existing score
     * or set it to 1 for new records.
     * 
     * @param table The table name to update (must be valid)
     * @param code The input code to map
     * @param word The output word
     * @param score The score to set, or -1 to auto-increment existing score or set to 1 for new records
     */
    @Synchronized
    open fun addOrUpdateMappingRecord(table: String, code: String, word: String, score: Int) {
        //String code = preProcessingRemappingCode(raw_code);  //Jeremy '12,6,4 the code is build from mapping.getcode() should not do remap again.
        if (DEBUG) Log.i(
            TAG,
            "addOrUpdateMappingRecord(), code = '" + code + "'. word=" + word + ", score =" + score
        )
        //Jeremy '12,4,17 !checkDBConnection() when db is restoring or replaced.
        if (checkDBConnection()) return

        try {
            val munit = isMappingExistOnDB(db!!, table, code, word)
            val cv = ContentValues()

            if (munit == null) {
                if (!code.isEmpty() && !word.isEmpty()) {
                    cv.put(FIELD_CODE, code)
                    removeFromBlackList(code) // remove from black list if it listed. Jeremy 12,6, 4
                    if (table == LIME.DB_TABLE_PHONETIC) {
                        val noToneCode = code.replace("[ 3467]".toRegex(), "")
                        cv.put(FIELD_NO_TONE_CODE, noToneCode) //Jeremy '12,6,1, add missing space
                        removeFromBlackList(noToneCode) // remove from black list if it listed. Jeremy 12,6, 4
                    }
                    cv.put(FIELD_WORD, word)
                    cv.put(FIELD_SCORE, if (score == -1) 1 else score)
                    db!!.insert(table, null, cv)


                    if (DEBUG) Log.i(
                        TAG,
                        "addOrUpdateMappingRecord(): mapping does not exist, new record inserted"
                    )
                }
            } else { //the item exist in preload related database.

                val newScore = if (score == -1) munit.getScore() + 1 else score
                cv.put(FIELD_SCORE, newScore)
                db!!.update(table, cv, FIELD_ID + " = " + munit.getId(), null)
                if (DEBUG) Log.i(
                    TAG,
                    "addOrUpdateMappingRecord(): mapping exist, update score on existing record; score:" + score
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in addOrUpdateMappingRecord() database operation", e)
        }
    }

    /**
     * Increments the score of a mapping record.
     * 
     * 
     * This method increments the user score for a selected mapping:
     * 
     *  * For related phrase records: updates the userscore in the related table
     *  * For regular mapping records: updates the score in the IM table
     * 
     * 
     * 
     * The score increment helps the system learn which mappings are preferred
     * by the user, improving future suggestions.
     * 
     * @param srcUnit The Mapping object containing the record to update
     */
    @Synchronized
    open fun addScore(srcUnit: Mapping?) {
        //Jeremy '12,4,17 !checkDBConnection() when db is restoring or replaced.

        if (checkDBConnection()) return

        //Jeremy '11,7,31  even selected from related list, update the corresponding score in im table.
        // Jeremy '11,6,12 Id=null denotes selection from related list in im table
        //Jeremy '11,9,8 query highest score first.  Erase related list if new score is not highest.
        try {
            if (srcUnit != null && srcUnit.getWord() != null && !srcUnit.getWord()!!
                    .trim { it <= ' ' }.isEmpty()
            ) {
                if (DEBUG) Log.i(TAG, "addScore(): addScore on word:" + srcUnit.getWord())

                if (srcUnit.isRelatedPhraseRecord()) {
                    val score: Int
                    val existingScore = relatedScore.get(srcUnit.getId())
                    if (existingScore == null) {
                        score = srcUnit.getScore() + 1
                        relatedScore.put(srcUnit.getId(), score)
                    } else {
                        score = existingScore + 1
                        relatedScore.put(srcUnit.getId(), score)
                    }

                    val cv = ContentValues()
                    cv.put(LIME.DB_RELATED_COLUMN_USERSCORE, score)
                    db!!.update(LIME.DB_TABLE_RELATED, cv, FIELD_ID + " = " + srcUnit.getId(), null)

                    //Log.i("TAG RELATED B", srcUnit.getId() + " : Related ADD Score :" + score);
                } else {
                    val cv = ContentValues()
                    cv.put(FIELD_SCORE, srcUnit.getScore() + 1)
                    // Jeremy 11',7,29  update according to word instead of ID, may have multiple records matching word but with diff code/id
                    db!!.update(tableName, cv, FIELD_WORD + " = '" + srcUnit.getWord() + "'", null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in database operation", e)
        }
    }


    /**
     * Retrieves all mapping records that match a given word.
     * 
     * 
     * This method performs a reverse lookup - finding all codes that map to
     * a specific word. Results are sorted by score in descending order.
     * 
     * 
     * This is useful for:
     * 
     *  * Reverse lookup features
     *  * Finding alternative codes for a word
     *  * Phrase learning (getting code from word)
     * 
     * 
     * @param keyword The word to search for (must not be null or empty)
     * @param table The table name to search in
     * @return List of Mapping objects matching the word, or null if database error
     */
    open fun getMappingByWord(keyword: String?, table: String): MutableList<Mapping?>? {
        if (DEBUG) Log.i(TAG, "getMappingByWord(): table name:" + table + "  keyword:" + keyword)

        if (checkDBConnection()) return null

        val result: MutableList<Mapping?> = LinkedList<Mapping?>()

        try {
            if (keyword != null && !keyword.trim { it <= ' ' }.isEmpty()) {
                val cursor: Cursor?
                cursor = db!!.query(
                    table, null, FIELD_WORD + " = '" + keyword + "'", null, null,
                    null, FIELD_SCORE + " DESC", null
                )
                if (DEBUG) Log.i(
                    TAG, ("getMappingByWord():table name:" + table + "  keyword:"
                            + keyword + "  cursor.getCount:"
                            + cursor.getCount())
                )

                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        do {
                            val munit = Mapping()
                            munit.setId(getCursorString(cursor, FIELD_ID))
                            munit.setCode(getCursorString(cursor, FIELD_CODE))
                            munit.setWord(getCursorString(cursor, FIELD_WORD))
                            munit.setExactMatchToWordRecord()
                            munit.setScore(getCursorInt(cursor, FIELD_SCORE))
                            result.add(munit)
                        } while (cursor.moveToNext())
                    }
                    cursor.close()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getMappingByWord()", e)
        }

        if (DEBUG) Log.i(TAG, "getMappingByWord() Result.size() = " + result.size)


        return result
    }

    /**
     * Performs reverse lookup to get all codes for a given keyword.
     * 
     * 
     * This method finds all input codes that map to the given word and
     * returns them as a formatted string with key names converted using
     * [.keyToKeyName].
     * 
     * 
     * The result format is: "word=code1; code2; code3"
     * 
     * 
     * This method respects the reverse lookup table preference setting.
     * If reverse lookup is disabled or the table is set to "none", returns null.
     * 
     * @param keyword The word to look up codes for
     * @return Formatted string of codes, or null if not found or reverse lookup disabled
     */
    open fun getCodeListStringByWord(keyword: String?): String? {
        if (checkDBConnection()) return null

        val table = mLIMEPref.getRerverseLookupTable(tableName)

        if (table == "none") {
            return null
        }

        var result = StringBuilder()
        try {
            if (keyword != null && !keyword.trim { it <= ' ' }.isEmpty()) {
                val cursor: Cursor?
                cursor = db!!.query(
                    table, null, FIELD_WORD + " = '" + keyword + "'", null, null,
                    null, null, null
                )
                if (DEBUG) Log.i(
                    TAG, ("getCodeListStringByWord():table name:" + table + "  keyword:"
                            + keyword + "  cursor.getCount:"
                            + cursor.getCount())
                )

                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        // Use helper methods to safely get column values (validates column index >= 0)
                        val word = getCursorString(cursor, FIELD_WORD)
                        val code = getCursorString(cursor, FIELD_CODE)
                        result = StringBuilder(
                            (word + "="
                                    + keyToKeyName(code, table, false))
                        )
                        if (DEBUG) Log.i(TAG, "getCodeListStringByWord():Code:" + code)


                        while (cursor.moveToNext()) {
                            result.append("; ").append(
                                keyToKeyName(
                                    getCursorString(cursor, FIELD_CODE),
                                    table, false
                                )
                            )
                            if (DEBUG) Log.i(
                                TAG, "getCodeListStringByWord():Code:"
                                        + getCursorString(cursor, FIELD_CODE)
                            )
                        }
                    }

                    cursor.close()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getCodeListStringByWord()", e)
        }

        if (DEBUG) Log.i(TAG, "getCodeListStringByWord() Result:" + result)

        return result.toString()
    }


    /**
     * Converts input codes into readable key names (composing text).
     * 
     * 
     * This method maps keyboard keys to their phonetic/character representations:
     * 
     *  * For phonetic: converts codes like "1qaz" to "ㄅㄆㄇㄈ"
     *  * For dayi: converts codes to Chinese radicals
     *  * For array: converts codes to array notation
     * 
     * 
     * 
     * The conversion depends on:
     * 
     *  * Table type (phonetic, dayi, array, etc.)
     *  * Physical keyboard type (if physical keyboard is pressed)
     *  * Phonetic keyboard type (for phonetic table)
     *  * Whether composing text is being built (affects dual code handling)
     * 
     * 
     * 
     * If the code length exceeds COMPOSING_CODE_LENGTH_LIMIT and composingText
     * is true, returns the original code without conversion.
     * 
     * @param code The input code to convert
     * @param table The table name (determines conversion rules)
     * @param composingText If true, this is for composing text display (may use dual codes)
     * @return The converted key name string, or original code if conversion fails
     */
    open fun keyToKeyName(code: String, table: String, composingText: Boolean): String {
        //Jeremy '11,8,30 
        var code = code
        if (composingText && code.length > COMPOSING_CODE_LENGTH_LIMIT) return code

        var keyboardType = mLIMEPref.getPhysicalKeyboardType()
        var phonetickeyboardtype = mLIMEPref.getPhoneticKeyboardType()
        var keyTable: String? = table

        if (DEBUG) Log.i(
            TAG, "keyToKeyName():code:" + code +
                    " lastValidDualCodeList=" + lastValidDualCodeList +
                    " table:" + table + " tableName:" + tableName +
                    " isPhysicalKeyboardPressed:" + isPhysicalKeyboardPressed +
                    " keyboardType: " + keyboardType +
                    " composingText:" + composingText
        )


        if (isPhysicalKeyboardPressed) {
            if (composingText && table == LIME.DB_TABLE_PHONETIC) { // doing composing popup
                keyTable = table + keyboardType + phonetickeyboardtype
            } else if (composingText) keyTable = table + keyboardType
        } else if (composingText && tableName == LIME.DB_TABLE_PHONETIC) {
            keyTable = table + phonetickeyboardtype
        }
        if (DEBUG) Log.i(TAG, "keyToKeyName():keyTable:" + keyTable)

        if (composingText) { // building composing text and get dual mapped codes		

            if (code != lastCode) {
                // un-synchronized cache. do the preprocessing again.
                //preProcessingForExtraQueryConditions(preProcessingRemappingCode(code));
                getMappingByCode(code, false, false)
            }
            //String dualCodeList = lastValidDualCodeList;
            if (lastValidDualCodeList != null) {
                if (DEBUG) Log.i(
                    TAG, "keyToKeyName():lastValidDualCodeList:" + lastValidDualCodeList +
                            " table:" + table + " tableName:" + tableName
                )
                //code = dualCodeList;
                if (tableName == LIME.DB_TABLE_PHONETIC) {
                    keyTable = LIME.DB_TABLE_PHONETIC
                    keyboardType = LIME.KEYBOARD_NORMAL
                    phonetickeyboardtype = LIME.IM_PHONETIC_KEYBOARD_PHONETIC
                }
                if (tableName == LIME.DB_TABLE_DAYI) {
                    keyTable = LIME.DB_TABLE_DAYI
                    keyboardType = LIME.KEYBOARD_NORMAL
                }
            }
        }

        if (DEBUG) Log.i(
            TAG, "keyToKeyName():code:" + code +
                    " table:" + table + " tableName:" + tableName + " keyTable:" + keyTable
        )

        if (keysDefMap[keyTable].isNullOrEmpty()) {
            var keyString: String?
            var keynameString: String?
            var finalKeynameString: String? = null
            //Jeremy 11,6,4 Load keys and keynames from im table.
            keyString = getImConfig(table, "imkeys")
            keynameString = getImConfig(table, "imkeynames")

            // Force the system to use the Default KeyString for Array Keyboard
            if (table == LIME.DB_TABLE_ARRAY) {
                keyString = ""
                keynameString = ""
            }

            if (DEBUG) Log.i(
                TAG,
                "keyToKeyName(): load from db: imKeys:keyString=" + keyString + ", imKeynames=" + keynameString
            )

            if (table == LIME.DB_TABLE_PHONETIC || table == LIME.DB_TABLE_DAYI ||
                keyString.isEmpty() || keynameString.isEmpty()
            ) {
                when (table) {
                    LIME.DB_TABLE_CJ, LIME.DB_TABLE_CJ4, LIME.DB_TABLE_SCJ, LIME.DB_TABLE_CJ5, LIME.DB_TABLE_ECJ -> {
                        keyString = CJ_KEY
                        keynameString = CJ_CHAR
                    }

                    LIME.DB_TABLE_PHONETIC -> if (composingText) {  // building composing text popup
                        if (phonetickeyboardtype == LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN) {
                            keyString = ETEN_KEY
                            if (keyboardType == MILESTONE && isPhysicalKeyboardPressed) keynameString =
                                MILESTONE_ETEN_CHAR
                            else if (keyboardType == MILESTONE2 && isPhysicalKeyboardPressed) keynameString =
                                MILESTONE2_ETEN_CHAR
                            else if (keyboardType == MILESTONE3 && isPhysicalKeyboardPressed) keynameString =
                                MILESTONE3_ETEN_CHAR
                            else if (keyboardType == "desireZ" && isPhysicalKeyboardPressed) keynameString =
                                DESIREZ_ETEN_CHAR
                            else keynameString = ETEN_CHAR
                        } else if (phonetickeyboardtype.startsWith(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26)) {
                            keyString = ETEN26_KEY
                            keynameString = ETEN26_CHAR_INITIAL
                            finalKeynameString = ETEN26_CHAR_FINAL
                        } else if (phonetickeyboardtype.startsWith(LIME.IM_PHONETIC_KEYBOARD_HSU)) {
                            keyString = HSU_KEY
                            keynameString = HSU_CHAR_INITIAL
                            finalKeynameString = HSU_CHAR_FINAL
                        } else if ((keyboardType == MILESTONE || keyboardType == MILESTONE2)
                            && isPhysicalKeyboardPressed
                        ) {
                            keyString = MILESTONE_KEY
                            keynameString = MILESTONE_BPMF_CHAR
                        } else if (keyboardType == MILESTONE3 && isPhysicalKeyboardPressed) {
                            keyString = MILESTONE3_KEY
                            keynameString = MILESTONE3_BPMF_CHAR
                        } else if (keyboardType == "desireZ" && isPhysicalKeyboardPressed) {
                            keyString = DESIREZ_KEY
                            keynameString = DESIREZ_BPMF_CHAR
                        } else if (keyboardType == "chacha" && isPhysicalKeyboardPressed) {
                            keyString = CHACHA_KEY
                            keynameString = CHACHA_BPMF_CHAR
                        } else if (keyboardType == "xperiapro" && isPhysicalKeyboardPressed) {
                            keyString = XPERIAPRO_KEY
                            keynameString = BPMF_CHAR
                        } else {
                            keyString = BPMF_KEY
                            keynameString = BPMF_CHAR
                        }
                    } else {
                        keyString = BPMF_KEY
                        keynameString = BPMF_CHAR
                    }

                    LIME.DB_TABLE_ARRAY -> {
                        keyString = ARRAY_KEY
                        keynameString = ARRAY_CHAR
                    }

                    LIME.DB_TABLE_DAYI -> if (isPhysicalKeyboardPressed && composingText) { // only do this on composing mapping popup
                        when (keyboardType) {
                            MILESTONE, MILESTONE2 -> {
                                keyString = MILESTONE_KEY
                                keynameString = MILESTONE_DAYI_CHAR
                            }

                            MILESTONE3 -> {
                                keyString = MILESTONE3_KEY
                                keynameString = MILESTONE3_DAYI_CHAR
                            }

                            "desireZ" -> {
                                keyString = DESIREZ_KEY
                                keynameString = DESIREZ_DAYI_CHAR
                            }

                            else -> {
                                keyString = DAYI_KEY
                                keynameString = DAYI_CHAR
                            }
                        }
                    } else {
                        keyString = DAYI_KEY
                        keynameString = DAYI_CHAR
                    }
                }
            }
            if (DEBUG) Log.i(
                TAG,
                "keyToKeyname():keyboardType:" + keyboardType + " phonetickeyboardtype:" + phonetickeyboardtype +
                        " composing?:" + composingText +
                        " keyString:" + keyString + " keynameString:" + keynameString + " finalkeynameString:" + finalKeynameString
            )
            if (!keyString.isEmpty()) {
                val keyMap = HashMap<String?, String?>()
                var finalKeyMap: HashMap<String?, String?>? = null
                if (finalKeynameString != null) finalKeyMap = HashMap<String?, String?>()

                val charlist = keynameString.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                var finalCharlist: Array<String>? = null

                if (finalKeyMap != null) finalCharlist =
                    finalKeynameString!!.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()

                // Ignore the exception of key name mapping.
                try {
                    for (i in 0..<keyString.length) {
                        keyMap.put(keyString.substring(i, i + 1), charlist[i])
                        if (finalKeyMap != null) finalKeyMap.put(
                            keyString.substring(i, i + 1),
                            finalCharlist!![i]
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing key mapping", e)
                }

                keyMap.put("|", "|") //put the seperator for multi-code display
                keysDefMap.put(keyTable, keyMap)
                if (finalKeyMap != null) keysDefMap.put("final_" + keyTable, finalKeyMap)
            }
        }


        // Starting doing key to keyname conversion ------------------------------------
        if (keysDefMap[keyTable].isNullOrEmpty()) {
            if (DEBUG) Log.i(TAG, "keyToKeyName():nokeysDefMap found!!")
            return code
        } else {
            if (composingText &&
                (lastValidDualCodeList != null)
            )  //Jeremy '11,10,6 bug fixed on rmapping returning orignal code.
                code = lastValidDualCodeList!!
            if (DEBUG) Log.i(TAG, "keyToKeyName():lastValidDualCodeList=" + lastValidDualCodeList)

            var result = StringBuilder()
            val keyMap = keysDefMap.get(keyTable)
            val finalKeyMap = keysDefMap.get("final_" + keyTable)

            // do the real conversion
            if (finalKeyMap == null) {
                for (i in 0..<code.length) {
                    checkNotNull(keyMap)
                    val c = keyMap.get(code.substring(i, i + 1))
                    if (c != null) result.append(c)
                }
            } else {
                if (code.length == 1) {
                    var c: String? = ""
                    if (phonetickeyboardtype.startsWith(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26) &&
                        (code == "q" || code == "w"
                                || code == "d" || code == "f"
                                || code == "j" || code == "k")
                    ) {
                        // Dual mapped INITIALS have words mapped for ��and �� for ETEN26
                        checkNotNull(keyMap)
                        c = keyMap.get(code)
                    } else if (phonetickeyboardtype.startsWith(LIME.IM_PHONETIC_KEYBOARD_HSU))  //Jeremy '12,5,31 process hsu with dual code mapping only.
                    {
                        checkNotNull(keyMap)
                        c = keyMap.get(code)
                    }
                    //}else{
                    //	c = finalKeyMap.get(code);
                    //}
                    if (c != null) result = StringBuilder(c.trim { it <= ' ' })
                } else {
                    for (i in 0..<code.length) {
                        val c: String?
                        if (i > 0) {
                            //Jeremy '12,6,3 If the last character is a tone symbol, the preceding will be intial
                            if (tableName == LIME.DB_TABLE_PHONETIC
                                && i > 1 && code.substring(0, i).matches(".+[sdfj ]$".toRegex())
                                && phonetickeyboardtype.startsWith(LIME.IM_PHONETIC_KEYBOARD_HSU)
                            ) {
                                if (DEBUG) Log.i(
                                    TAG,
                                    "preProcessingRemappingCode() hsu finalremap, subcode = " + code.substring(
                                        0,
                                        i
                                    )
                                )
                                c = keyMap!!.get(code.substring(i, i + 1))
                            } else if (tableName == LIME.DB_TABLE_PHONETIC
                                && i > 1 && code.substring(0, i).matches(".+[dfjk ]$".toRegex())
                                && phonetickeyboardtype.startsWith(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26)
                            ) {
                                if (DEBUG) Log.i(
                                    TAG,
                                    "preProcessingRemappingCode() hsu finalremap, subcode = " + code.substring(
                                        0,
                                        i
                                    )
                                )
                                c = keyMap!!.get(code.substring(i, i + 1))
                            } else c = finalKeyMap.get(code.substring(i, i + 1))
                        } else {
                            checkNotNull(keyMap)
                            c = keyMap.get(code.substring(i, i + 1))
                        }
                        if (c != null) result.append(c.trim { it <= ' ' })
                    }
                }
            }
            if (DEBUG) Log.i(TAG, "keyToKeyName():returning:" + result)

            if (result.toString().isEmpty()) {
                return code
            } else {
                return result.toString()
            }
        }
    }

    open fun keyToKeyName(code: String, tablename: String, preferUserDef: Boolean?): String {
        return keyToKeyName(code, tablename, preferUserDef ?: false)
    }

    /**
     * Initializes the LIME database with the given context.
     * 
     * 
     * This constructor:
     * 
     *  * Initializes the SQLite database helper
     *  * Creates a LIMEPreferenceManager instance
     *  * Initializes the blacklist cache
     *  * Opens the database connection
     * 
     * 
     * 
     * The database connection is opened immediately in the constructor to ensure
     * it's ready for use. The connection is shared statically across all LimeDB instances.
     * 
     * @param mContext The Android context for accessing preferences and resources
     */
    init {
        mLIMEPref = LIMEPreferenceManager(mContext.getApplicationContext())


        blackListCache = ConcurrentHashMap<String?, Boolean?>(LIME.LIMEDB_CACHE_SIZE)


        // Jeremy '12,4,7 open DB connection in constructor
        // Reuse a healthy shared connection; openDBConnection(false) now verifies
        // the handle before reusing it and reopens stale handles.
        openDBConnection(false)
        ensureCurrentDatabase()
    }

    /**
     * Retrieves mapping records that match the given code.
     * 
     * 
     * This is the core query method for the IME. It performs a sophisticated
     * search that includes:
     * 
     *  * Code preprocessing and remapping
     *  * Dual code expansion (for physical keyboards)
     *  * Between search (finds partial matches like "ab", "abc" when searching "abcd")
     *  * No-tone code search (for phonetic table)
     *  * Result sorting by exact match, code length, and score
     * 
     * 
     * 
     * The method respects user preferences for:
     * 
     *  * Sort suggestions (different for soft vs physical keyboard)
     *  * Result limit (INITIAL_RESULT_LIMIT or FINAL_RESULT_LIMIT)
     * 
     * 
     * 
     * Results are marked with exact match flags and sorted to prioritize:
     * 
     *  1. Exact matches with single-character words
     *  1. Exact matches
     *  1. Longer code matches
     *  1. Shorter code matches (up to 5 characters)
     *  1. Score and base score (if sorting enabled)
     * 
     * 
     * @param code The input code to search for
     * @param softKeyboard If true, uses soft keyboard sorting preference; if false, uses physical keyboard preference
     * @param getAllRecords If true, returns up to FINAL_RESULT_LIMIT records; if false, returns up to INITIAL_RESULT_LIMIT
     * @return List of Mapping objects matching the code, sorted by relevance, or null if database error
     */
    open fun getMappingByCode(
        code: String,
        softKeyboard: Boolean,
        getAllRecords: Boolean
    ): MutableList<Mapping?>? {
        var code = code
        val codeOrig: String? = code

        var startTime: Long = 0
        if (DEBUG || probePerformance) {
            startTime = System.currentTimeMillis()
            Log.i(
                TAG,
                "getMappingByCode(): code='" + code + ", table=" + tableName + ", getAllRecords=" + getAllRecords
            )
        }

        //Jeremy '12,5,1 !checkDBConnection() when db is restoring or replaced.
        if (checkDBConnection()) return null


        val sort: Boolean
        if (softKeyboard) sort = mLIMEPref.getSortSuggestions()
        else sort = mLIMEPref.getPhysicalKeyboardSortSuggestions()
        isPhysicalKeyboardPressed = !softKeyboard

        // Add by Jeremy '10, 3, 27. Extension on multi table query.
        lastCode = code
        lastValidDualCodeList = null // reset the lastValidDualCodeList
        var result: MutableList<Mapping?>? = null

        //Two-steps query with code pre-processing. Jeremy '11,6,15
        // Step.1 Code re-mapping.  
        code = preProcessingRemappingCode(code)
        code = code.lowercase() //Jeremy '12,4,1 moved from SearchService.getMappingByCode();
        // Step.2 Build extra getMappingByCode conditions. (e.g. dualcode remap)
        val extraConditions = preProcessingForExtraQueryConditions(code)
        var extraSelectClause: String? = ""
        var extraExactMatchClause: String? = ""
        if (extraConditions != null) {
            extraSelectClause = extraConditions.first
            extraExactMatchClause = extraConditions.second
        }


        //Jeremy '11,6,11 separated suggestions sorting option for physical keyboard
        try {
            if (!code.isEmpty()) {
                try {
                    val cursor: Cursor?

                    // Jeremy '11,8,2 Query noToneCode instead of code for code contains no tone symbols
                    // Jeremy '12,6,5 rewrite to consistent with expanddualcode
                    // Jeremy '15,6,6 always search no tone code for phonetic. The db will be upgraded in onUprade if code3r is not present
                    var codeCol: String = FIELD_CODE

                    val tonePresent =
                        code.matches(".+[3467 ].*".toRegex()) // Tone symbols present in any locoation except the first character
                    val toneNotLast =
                        code.matches(".+[3467 ].+".toRegex()) // Tone symbols present in any locoation except the first and last character

                    if (tableName == LIME.DB_TABLE_PHONETIC) {
                        if (tonePresent) {
                            //LD phrase if tone symbols present but not in last character or in last character but the length > 4
                            // (phonetic combinations never has length >4)
                            if (toneNotLast || (code.length > 4)) code =
                                code.replace("[3467 ]".toRegex(), "")
                        } else { // no tone symbols present, check NoToneCode column
                            codeCol = FIELD_NO_TONE_CODE
                        }
                        code = code.trim { it <= ' ' }
                    }


                    val selectClause: String?
                    val sortClause: String?
                    val escapedCode = code.replace("'".toRegex(), "''")
                    val codeLen = code.length

                    val limitClause: String =
                        if (getAllRecords) FINAL_RESULT_LIMIT else INITIAL_RESULT_LIMIT

                    //Jeremy '15, 6, 1 between search clause without using related column for better sorting order.
                    //if(betweenSearch){
                    val exactMatchCondition =
                        " (" + codeCol + " ='" + escapedCode + "' " + extraExactMatchClause + ") "
                    val similarCodeCandidates = mLIMEPref.getSimilarCodeCandidates()
                    if (similarCodeCandidates <= 0) {
                        selectClause = exactMatchCondition
                    } else {
                        selectClause = expandBetweenSearchClause(codeCol, code) + extraSelectClause
                    }
                    // Sort key order (issue #49 follow-up):
                    //   1. exactmatch DESC                      -- exact hits always above partial hits
                    //   2. length(code) >= codeLen              -- at-least-as-long-as-typed first
                    //   3. exactmatch-with-score single-char priority / score DESC / basescore DESC
                    //                                           -- when `sort` pref is on, score now
                    //                                              dominates over code-length so picks
                    //                                              from the partial-match list can
                    //                                              float to the top after score bumps
                    //   4. (length(code) <= 5) * length(code)   -- tiebreaker among equal-score rows
                    //   5. _id ASC                              -- source insertion order for exact duplicate codes
                    sortClause =
                        "exactmatch desc, (length(" + codeCol + ") >= " + codeLen + " ) desc, "


                    val sortClauseBuilder = StringBuilder(sortClause)
                    if (sort) {
                        sortClauseBuilder.append("( exactmatch = 1 and ( score > 0 or  basescore >0) and length(word)=1) desc, ")
                        sortClauseBuilder.append("score desc, basescore desc, ")
                    }
                    sortClauseBuilder.append(
                        "(length(" + codeCol + ") <= " + (min(
                            codeLen,
                            5
                        )) + " )*length(" + codeCol + ") desc, "
                    )
                    sortClauseBuilder.append("_id asc")
                    val finalSortClause = sortClauseBuilder.toString()

                    val selectString =
                        "select _id, code, code3r, word, score, basescore, " + exactMatchCondition + " as exactmatch  " +
                                " from " + tableName + " where word is not null and " + selectClause +
                                " order by " + finalSortClause + " limit " + limitClause
                    cursor = db!!.rawQuery(selectString, null)

                    if (DEBUG) Log.i(
                        TAG,
                        "getMappingByCode() between search select string:" + selectString
                    )


                    // Jeremy '11,8,5 limit initial getMappingByCode to limited records
                    // Jeremy '11,6,15 Using getMappingByCode with preprocessed code and extra getMappingByCode conditions.
                    if (cursor != null) {
                        result = buildQueryResult(code, codeOrig, cursor, getAllRecords)
                        cursor.close()
                    }
                } catch (e: SQLiteException) {
                    Log.e(TAG, "Error in database operation", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in database operation", e)
        }

        if (DEBUG || probePerformance) {
            Log.i(
                TAG,
                "getMappingByCode() time elapsed = " + (System.currentTimeMillis() - startTime)
            )
        }

        return result
    }

    /**
     * Expands a search clause to include partial code matches using between search.
     * 
     * 
     * This method creates a SQL WHERE clause that matches codes in a hierarchical manner:
     * 
     *  * For code "abcd", it matches: "a", "ab", "abc", and codes starting with "abcd"
     *  * Prefix matches are limited to the first 5 characters (or code length if shorter)
     *  * Uses a BETWEEN-style range query (>= code AND < nextCode) for codes starting with the full code
     * 
     * 
     * 
     * Example: For code "abc", this generates:
     * <pre>
     * code = 'a' or code = 'ab' or (code >= 'abc' and code < 'abd')
    </pre> * 
     * 
     * 
     * This allows finding mappings even when the user hasn't typed the complete code yet,
     * improving the user experience by showing suggestions as they type.
     * 
     * 
     * The method properly escapes single quotes in the code to prevent SQL injection.
     * 
     * @param searchColumn The database column name to search in (e.g., "code" or "code3r")
     * @param code The input code to expand (will be escaped for SQL safety)
     * @return A SQL WHERE clause string with OR conditions for partial matches and a range query
     */
    private fun expandBetweenSearchClause(searchColumn: String?, code: String): String {
        val selectClause = StringBuilder()

        val len = code.length
        val end = if (len > 5) 6 else len

        if (len > 1) {
            for (j in 0..<end - 1) {
                selectClause.append(searchColumn).append("= '")
                    .append(code.substring(0, j + 1).replace("'".toRegex(), "''")).append("' or ")
            }
        }
        val chArray = code.toCharArray()
        chArray[code.length - 1]++
        val nextCode = String(chArray)
        selectClause.append(" (").append(searchColumn).append(" >= '")
            .append(code.replace("'".toRegex(), "''")).append("' and ").append(searchColumn)
            .append(" <'").append(nextCode.replace("'".toRegex(), "''")).append("') ")
        if (DEBUG) Log.i(TAG, "expandBetweenSearchClause() selectClause: " + selectClause)
        return selectClause.toString()
    }

    /**
     * Preprocesses and remaps input codes based on keyboard type.
     * 
     * 
     * This method handles keyboard-specific code remapping:
     * 
     *  * Physical keyboard remapping (e.g., Milestone, DesireZ, ChaCha)
     *  * Phonetic keyboard remapping (e.g., ETEN, ETEN26, HSU)
     *  * Shifted key remapping (for soft keyboards)
     * 
     * 
     * 
     * The remapping is cached in memory for performance. Different remapping
     * tables are used based on the combination of table name, physical keyboard type,
     * and phonetic keyboard type.
     * 
     * 
     * For phonetic keyboards with dual code support (ETEN26, HSU), this method
     * handles initial/final remapping where the first character uses initial mapping
     * and subsequent characters use final mapping.
     * 
     * @param code The original input code
     * @return The remapped code, or empty string if code is null
     */
    fun preProcessingRemappingCode(code: String?): String {
        if (DEBUG) Log.i(
            TAG,
            "preProcessingRemappingCode(): tableName = " + tableName + " , code=" + code
        )
        if (code != null) {
            val keyboardType = mLIMEPref.getPhysicalKeyboardType()
            val phoneticKeyboardType = mLIMEPref.getPhoneticKeyboardType()
            var keyString: String? = ""
            var keyRemapString: String? = ""
            var finalKeyRemapString: String? = null
            var newcode = StringBuilder(code)
            var remaptable = tableName

            // Build cached hashmap remapping table name 
            if (isPhysicalKeyboardPressed) {
                if (tableName == LIME.DB_TABLE_PHONETIC) remaptable =
                    tableName + keyboardType + phoneticKeyboardType
                else remaptable = tableName + keyboardType
            } else if (tableName == LIME.DB_TABLE_PHONETIC) remaptable =
                tableName + phoneticKeyboardType


            // Build cached hashmap remapping table if it's not exist
            if (keysReMap[remaptable].isNullOrEmpty()) {
                if (tableName == LIME.DB_TABLE_PHONETIC && phoneticKeyboardType.startsWith(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26)) {
                    keyString = ETEN26_KEY
                    keyRemapString = ETEN26_KEY_REMAP_INITIAL
                    finalKeyRemapString = ETEN26_KEY_REMAP_FINAL
                } else if (tableName == LIME.DB_TABLE_PHONETIC && phoneticKeyboardType.startsWith(
                        LIME.IM_PHONETIC_KEYBOARD_HSU
                    )
                ) {
                    keyString = HSU_KEY
                    keyRemapString = HSU_KEY_REMAP_INITIAL
                    finalKeyRemapString = HSU_KEY_REMAP_FINAL
                } else if (tableName == LIME.DB_TABLE_PHONETIC && phoneticKeyboardType == LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN) {
                    keyString = ETEN_KEY
                    //+ SHIFTED_NUMBERIC_KEY + SHIFTED_SYMBOL_KEY;
                    keyRemapString = ETEN_KEY_REMAP
                    //+ SHIFTED_NUMBERIC_ETEN_KEY_REMAP + SHIFTED_SYMBOL_ETEN_KEY_REMAP;
                } else if (isPhysicalKeyboardPressed
                    && tableName == LIME.DB_TABLE_PHONETIC && keyboardType == "desireZ"
                ) {
                    //Desire Z phonetic keyboard
                    keyString = DESIREZ_KEY
                    keyRemapString = DESIREZ_BPMF_KEY_REMAP
                } else if (isPhysicalKeyboardPressed
                    && tableName == LIME.DB_TABLE_PHONETIC && keyboardType == "chacha"
                ) {
                    //Desire Z phonetic keyboard
                    keyString = CHACHA_KEY
                    keyRemapString = CHACHA_BPMF_KEY_REMAP
                } else if (isPhysicalKeyboardPressed
                    && tableName == LIME.DB_TABLE_PHONETIC && keyboardType == "xperiapro"
                ) {
                    //XPERIA PRO phonetic keyboard
                    keyString = XPERIAPRO_KEY
                    keyRemapString = XPERIAPRO_BPMF_KEY_REMAP
                } else if (!isPhysicalKeyboardPressed) {
                    if (tableName == LIME.DB_TABLE_DAYI || tableName == "ez"
                        || tableName == LIME.DB_TABLE_PHONETIC && phoneticKeyboardType == LIME.DB_TABLE_PHONETIC
                    ) {
                        keyString = SHIFTED_NUMBERIC_KEY + SHIFTED_SYMBOL_KEY
                        keyRemapString = SHIFTED_NUMBERIC_KEY_REMAP + SHIFTED_SYMBOL_KEY_REMAP
                    } else if (tableName == LIME.DB_TABLE_ARRAY) {
                        keyString = SHIFTED_SYMBOL_KEY
                        keyRemapString = SHIFTED_SYMBOL_KEY_REMAP
                    }
                }

                if (DEBUG) Log.i(
                    TAG,
                    "preProcessingRemappingCode(): keyString=\"" + keyString + "\";keyRemapString=\"" + keyRemapString + "\""
                )


                if (!keyString!!.isEmpty()) {
                    val reMap = HashMap<String?, String?>()
                    var finalReMap: HashMap<String?, String?>? = null
                    if (finalKeyRemapString != null) finalReMap = HashMap<String?, String?>()

                    for (i in 0..<keyString!!.length) {
                        reMap.put(
                            keyString!!.substring(i, i + 1),
                            keyRemapString!!.substring(i, i + 1)
                        )
                        if (finalReMap != null) finalReMap.put(
                            keyString!!.substring(i, i + 1),
                            finalKeyRemapString!!.substring(i, i + 1)
                        )
                    }
                    keysReMap.put(remaptable, reMap)
                    if (finalReMap != null) keysReMap.put("final_" + remaptable, finalReMap)
                }
            }

            if (!keysReMap[remaptable].isNullOrEmpty()) {
                val reMap = keysReMap.get(remaptable)
                val finalReMap = keysReMap.get("final_" + remaptable)

                newcode = StringBuilder()
                var c: String?

                if (finalReMap == null) {
                    for (i in 0..<code.length) {
                        val s = code.substring(i, i + 1)
                        checkNotNull(reMap)
                        c = reMap.get(s)
                        newcode.append(Objects.requireNonNullElse<String?>(c, s))
                    }
                } else {
                    if (code.length == 1) {
                        if (phoneticKeyboardType.startsWith(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26) &&
                            (code == "q" || code == "w"
                                    || code == "d" || code == "f"
                                    || code == "j" || code == "k")
                        ) {
                            checkNotNull(reMap)
                            c = reMap.get(code)
                        } else if (phoneticKeyboardType.startsWith(LIME.IM_PHONETIC_KEYBOARD_HSU) &&
                            (code == "a" || code == "e" ||
                                    code == "s" || code == "d" || code == "f" || code == "j")
                        ) {
                            checkNotNull(reMap)
                            c = reMap.get(code)
                        } else {
                            c = finalReMap.get(code)
                        }
                        newcode = StringBuilder(c ?: code)
                    } else {
                        for (i in 0..<code.length) {
                            val s = code.substring(i, i + 1)
                            if (i > 0) {
                                //Jeremy '12,6,3 If the last character is a tone symbol, the preceding will be intial
                                if (tableName == LIME.DB_TABLE_PHONETIC
                                    && i > 1 && code.substring(0, i).matches(".+[sdfj ]$".toRegex())
                                    && phoneticKeyboardType.startsWith(LIME.IM_PHONETIC_KEYBOARD_HSU)
                                ) {
                                    if (DEBUG) Log.i(
                                        TAG,
                                        "preProcessingRemappingCode() hsu finalremap, subcode = " + code.substring(
                                            0,
                                            i
                                        )
                                    )
                                    c = reMap!!.get(s)
                                } else if (tableName == LIME.DB_TABLE_PHONETIC
                                    && i > 1 && code.substring(0, i).matches(".+[dfjk ]$".toRegex())
                                    && phoneticKeyboardType.startsWith(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26)
                                ) {
                                    if (DEBUG) Log.i(
                                        TAG,
                                        "preProcessingRemappingCode() hsu finalremap, subcode = " + code.substring(
                                            0,
                                            i
                                        )
                                    )
                                    c = reMap!!.get(s)
                                } else c = finalReMap.get(s)
                            } else {
                                checkNotNull(reMap)
                                c = reMap.get(s)
                            }

                            newcode.append(Objects.requireNonNullElse<String?>(c, s))
                        }
                    }
                }
            }
            if (DEBUG) Log.i(TAG, "preProcessingRemappingCode():newcode=" + newcode)
            return newcode.toString()
        } else return ""
    }

    /**
     * Preprocesses code to build extra query conditions for dual code mapping.
     * 
     * 
     * This method handles dual code mapping for physical keyboards where a single key
     * can map to multiple characters. It:
     * 
     *  * Builds dual key mapping tables based on keyboard type (Milestone, DesireZ, etc.)
     *  * Checks if the code contains dual-mapped characters
     *  * Expands dual codes if needed (e.g., for phonetic codes with tone symbols in the middle)
     *  * Returns additional SQL WHERE clause conditions for querying dual code variants
     * 
     * 
     * 
     * Dual code mapping is used to support physical keyboards where certain keys can
     * produce different characters. For example, on a Milestone keyboard, the "q" key
     * might map to both "q" and "1" depending on context.
     * 
     * 
     * The method returns a Pair containing:
     * 
     *  * First element: Additional SELECT clause conditions (OR conditions for dual codes)
     *  * Second element: Additional exact match conditions for dual codes
     * 
     * 
     * 
     * If no dual code expansion is needed (code doesn't contain dual-mapped characters
     * and doesn't match expansion criteria), returns null.
     * 
     * 
     * Special handling for phonetic table:
     * 
     *  * If code has tone symbols in the middle (e.g., "a3b4"), expands dual codes
     *  * Supports different phonetic keyboard types (ETEN, ETEN26, HSU)
     * 
     * 
     * 
     * The dual code mapping tables are cached in memory for performance.
     * 
     * @param code The input code to process (already remapped by preProcessingRemappingCode)
     * @return Pair containing (SELECT clause, exact match clause) for dual codes, or null if no expansion needed
     */
    private fun preProcessingForExtraQueryConditions(code: String?): Pair<String?, String?>? {
        if (DEBUG) Log.i(
            TAG, ("preProcessingForExtraQueryConditions(): code = '" + code
                    + "', isPhysicalKeyboardPressed=" + isPhysicalKeyboardPressed)
        )

        if (code != null) {
            val keyboardtype = mLIMEPref.getPhysicalKeyboardType()
            val phonetickeyboardtype = mLIMEPref.getPhoneticKeyboardType()
            val dualcode: StringBuilder?
            var dualKey = ""
            var dualKeyRemap = ""
            var remaptable = tableName
            if (isPhysicalKeyboardPressed) {
                if (tableName == LIME.DB_TABLE_PHONETIC) remaptable =
                    tableName + keyboardtype + phonetickeyboardtype
                else remaptable = tableName + keyboardtype
            } else if (tableName == LIME.DB_TABLE_PHONETIC) {
                remaptable = tableName + phonetickeyboardtype
            }


            if (keysDualMap[remaptable].isNullOrEmpty()) {
                if (tableName == LIME.DB_TABLE_PHONETIC && phonetickeyboardtype.startsWith(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26)) {
                    dualKey = ETEN26_DUALKEY
                    dualKeyRemap = ETEN26_DUALKEY_REMAP
                } else if (tableName == LIME.DB_TABLE_PHONETIC && phonetickeyboardtype.startsWith(
                        LIME.IM_PHONETIC_KEYBOARD_HSU
                    )
                ) {
                    dualKey = HSU_DUALKEY
                    dualKeyRemap = HSU_DUALKEY_REMAP
                } else if (keyboardtype == MILESTONE && isPhysicalKeyboardPressed) {
                    if (tableName == LIME.DB_TABLE_PHONETIC && phonetickeyboardtype == LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN) {
                        dualKey = MILESTONE_ETEN_DUALKEY
                        dualKeyRemap = MILESTONE_ETEN_DUALKEY_REMAP
                    } else {
                        dualKey = MILESTONE_DUALKEY
                        dualKeyRemap = MILESTONE_DUALKEY_REMAP
                    }
                } else if (keyboardtype == MILESTONE2 && isPhysicalKeyboardPressed) {
                    if (tableName == LIME.DB_TABLE_PHONETIC && phonetickeyboardtype == LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN) {
                        dualKey = MILESTONE2_ETEN_DUALKEY
                        dualKeyRemap = MILESTONE2_ETEN_DUALKEY_REMAP
                    } else {
                        dualKey = MILESTONE2_DUALKEY
                        dualKeyRemap = MILESTONE2_DUALKEY_REMAP
                    }
                } else if (keyboardtype == MILESTONE3 && isPhysicalKeyboardPressed) {
                    if (tableName == LIME.DB_TABLE_PHONETIC && phonetickeyboardtype == LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN) {
                        dualKey = MILESTONE3_ETEN_DUALKEY
                        dualKeyRemap = MILESTONE3_ETEN_DUALKEY_REMAP
                    } else if (tableName == LIME.DB_TABLE_PHONETIC && phonetickeyboardtype == LIME.DB_TABLE_PHONETIC) {
                        dualKey = MILESTONE3_BPMF_DUALKEY
                        dualKeyRemap = MILESTONE3_BPMF_DUALKEY_REMAP
                    } else {
                        dualKey = MILESTONE3_DUALKEY
                        dualKeyRemap = MILESTONE3_DUALKEY_REMAP
                    }
                } else if (keyboardtype == "desireZ" && isPhysicalKeyboardPressed) {
                    if (tableName == LIME.DB_TABLE_PHONETIC && phonetickeyboardtype == LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN) {
                        dualKey = DESIREZ_ETEN_DUALKEY
                        dualKeyRemap = DESIREZ_ETEN_DUALKEY_REMAP
                    } else if (tableName == LIME.DB_TABLE_PHONETIC && phonetickeyboardtype == LIME.DB_TABLE_PHONETIC) {
                        dualKey = DESIREZ_BPMF_DUALKEY
                        dualKeyRemap = DESIREZ_BPMF_DUALKEY_REMAP
                    } else {
                        dualKey = DESIREZ_DUALKEY
                        dualKeyRemap = DESIREZ_DUALKEY_REMAP
                    }
                } else if (keyboardtype == "chacha" && isPhysicalKeyboardPressed) {
                    if (tableName == LIME.DB_TABLE_PHONETIC && phonetickeyboardtype == LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN) {
                        dualKey = CHACHA_ETEN_DUALKEY
                        dualKeyRemap = CHACHA_ETEN_DUALKEY_REMAP
                    } else if (tableName == LIME.DB_TABLE_PHONETIC && phonetickeyboardtype == LIME.DB_TABLE_PHONETIC) {
                        dualKey = CHACHA_BPMF_DUALKEY
                        dualKeyRemap = CHACHA_BPMF_DUALKEY_REMAP
                    } else {
                        dualKey = CHACHA_DUALKEY
                        dualKeyRemap = CHACHA_DUALKEY_REMAP
                    }
                } else if (keyboardtype == "xperiapro" && isPhysicalKeyboardPressed) {  //Jeremy '12,4,1
                    if (tableName == LIME.DB_TABLE_PHONETIC && phonetickeyboardtype == LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN) {
                        dualKey = XPERIAPRO_ETEN_DUALKEY
                        dualKeyRemap = XPERIAPRO_ETEN_DUALKEY_REMAP
                    } else if (tableName == LIME.DB_TABLE_PHONETIC && phonetickeyboardtype == LIME.DB_TABLE_PHONETIC) {
                        // no dual key here
                        dualKey = ""
                        dualKeyRemap = ""
                    } else {
                        dualKey = XPERIAPRO_DUALKEY
                        dualKeyRemap = XPERIAPRO_DUALKEY_REMAP
                    }
                } else if (tableName == "ez" && !isPhysicalKeyboardPressed) { //jeremy '12,7,5 remap \ to `.
                    dualKey = "\\"
                    dualKeyRemap = "`"
                }

                val reMap = HashMap<String?, String?>()
                if (DEBUG) Log.i(
                    TAG,
                    "preProcessingForExtraQueryConditions(): dualKey=" + dualKey + " dualKeyRemap=" + dualKeyRemap
                )
                for (i in 0..<dualKey.length) {
                    val key = dualKey.substring(i, i + 1)
                    val value = dualKeyRemap.substring(i, i + 1)
                    reMap.put(key, value)
                    reMap.put(value, value)
                }
                keysDualMap.put(remaptable, reMap)
            }
            // do real precessing now
            if (keysDualMap[remaptable].isNullOrEmpty()) {
                isCodeDualMapped = false
                dualcode = StringBuilder(code)
            } else {
                isCodeDualMapped = true
                val reMap = keysDualMap.get(remaptable)
                dualcode = StringBuilder()
                // testing if code contains dual mapped characters. 
                for (i in 0..<code.length) {
                    checkNotNull(reMap)
                    val c = reMap.get(code.substring(i, i + 1))
                    if (c != null) dualcode.append(c)
                }
                if (DEBUG) Log.i(
                    TAG,
                    "preProcessingForExtraQueryConditions(): dualcode=" + dualcode
                )
            }
            //Jeremy '11,8,12 if phonetic has tone symbol in the middle do the expanddualcode
            if (!dualcode.toString().equals(code, ignoreCase = true) || !code.equals(
                    lastCode,
                    ignoreCase = true
                ) // '11,8,18 Jeremy
                || (tableName == LIME.DB_TABLE_PHONETIC && code.matches(".+[ 3467].+".toRegex()))
            ) {
                return expandDualCode(code, remaptable)
            }
        }
        return null
    }

    /**
     * Builds a tree structure of all possible dual code variants for a given code.
     * 
     * 
     * This method uses a tree-building algorithm to generate all possible code combinations
     * when dual key mapping is enabled. It processes the code character by character, creating
     * new variants at each level by replacing characters with their dual mappings.
     * 
     * 
     * Algorithm:
     * 
     *  1. Starts with the original code at level 0
     *  1. For each character position (level i), checks if that character has a dual mapping
     *  1. If dual mapping exists, creates new code variants by replacing the character
     *  1. Builds variants level by level, checking blacklist cache to skip invalid codes
     *  1. Stops when no more dual mappings are found or codes are blacklisted
     * 
     * 
     * 
     * Example: For code "qwe" with dual mapping q→1, w→2:
     * 
     *  * Level 0: ["qwe"]
     *  * Level 1: ["qwe", "1we"] (q can map to 1)
     *  * Level 2: ["qwe", "1we", "q2e", "12e"] (w can map to 2)
     *  * Level 3: ["qwe", "1we", "q2e", "12e"] (e has no dual mapping)
     * 
     * 
     * 
     * Blacklist checking:
     * 
     *  * Checks if codes are in the blacklist cache (invalid codes that return no results)
     *  * Checks prefixes with wildcard (e.g., "ab%") to avoid expanding invalid code prefixes
     *  * Skips codes shorter than DUALCODE_NO_CHECK_LIMIT without blacklist check
     * 
     * 
     * 
     * Special handling for phonetic table:
     * 
     *  * If a code has tone symbols in the middle (e.g., "a3b4"), also adds a no-tone variant
     *  * No-tone variants are checked against blacklist before adding
     * 
     * 
     * 
     * The resulting set contains all valid code variants that can be queried to find
     * mappings. This enables finding words even when the user types using different
     * key combinations on physical keyboards.
     * 
     * @param code The input code to build dual code variants for
     * @param keytablename The key table name used to look up dual mapping configuration
     * @return HashSet containing all valid dual code variants, including the original code
     */
    private fun buildDualCodeList(code: String, keytablename: String?): HashSet<String> {
        if (DEBUG) Log.i(
            TAG,
            "buildDualCodeList(): code:" + code + ", keytablename=" + keytablename
        )

        val codeDualMap = keysDualMap.get(keytablename)
        val treeDualCodeList = HashSet<String>()

        if (codeDualMap != null && !codeDualMap.isEmpty()) {
            //Jeremy '12,6,4 

            val treemap = SparseArray<MutableList<String?>?>()
            for (i in 0..<code.length) {
                if (DEBUG) Log.i(TAG, "buildDualCodeList() level : " + i)


                val levelnMap: MutableList<String?> = LinkedList<String?>()
                val lastLevelMap: MutableList<String?>?
                if (i == 0) {
                    lastLevelMap = LinkedList<String?>()
                    lastLevelMap.add(code)
                } else lastLevelMap = treemap.get(i - 1)

                var c: String?
                var n: String?

                if (lastLevelMap == null || (lastLevelMap.isEmpty())) {
                    if (DEBUG) Log.i(
                        TAG,
                        "buildDualCodeList() level : " + i + " ended because last level map is empty"
                    )
                    continue
                }
                if (DEBUG) Log.i(
                    TAG,
                    "buildDualCodeList() level : " + i + " lastlevelmap size = " + lastLevelMap.size
                )
                for (entry in lastLevelMap) {
                    if (entry == null) continue
                    if (DEBUG) Log.i(TAG, "buildDualCodeList() level : " + i + ", entry = " + entry)

                    if (entry.length == 1) c = entry
                    else c = entry.substring(i, i + 1)


                    var codeMapped = false
                    do {
                        if (DEBUG) Log.i(
                            TAG, ("buildDualCodeList() newCode = '" + entry
                                    + "' blacklistKey = '" + cacheKey(
                                entry.substring(
                                    0,
                                    i + 1
                                ) + "%"
                            )
                                    + "' blacklistValue = " + blackListCache!!.get(
                                cacheKey(
                                    entry.substring(
                                        0,
                                        i + 1
                                    ) + "%"
                                )
                            ))
                        )

                        if (entry.length == 1 && !levelnMap.contains(entry)) {
                            if (blackListCache!!.get(cacheKey(entry)) == null) treeDualCodeList.add(
                                entry
                            )
                            levelnMap.add(entry)
                            if (DEBUG) Log.i(
                                TAG, ("buildDualCodeList() entry.length()==1 new code = '" + entry
                                        + "' added. treeDualCodeList.size = " + treeDualCodeList.size)
                            )
                            codeMapped = true
                        } else if ((entry.length > 1 && !levelnMap.contains(entry))
                            && blackListCache!!.get(
                                cacheKey(
                                    entry.substring(
                                        0,
                                        i + 1
                                    ) + "%"
                                )
                            ) == null
                        ) {
                            if (blackListCache!!.get(cacheKey(entry)) == null) treeDualCodeList.add(
                                entry
                            )
                            levelnMap.add(entry)
                            if (DEBUG) Log.i(
                                TAG, ("buildDualCodeList() new code = '" + entry
                                        + "' added. treeDualCodeList.size = " + treeDualCodeList.size)
                            )
                            codeMapped = true
                        } else if (codeDualMap.get(c) != null && codeDualMap.get(c) != c) {
                            n = codeDualMap.get(c)
                            val newCode: String = Companion.getNewCode(entry, n!!, i)
                            if (DEBUG) {
                                checkNotNull(newCode)
                                Log.i(
                                    TAG, ("buildDualCodeList() newCode = '" + newCode
                                            + "' blacklistKey = '" + cacheKey(newCode)
                                            + "' blacklistValue = " + blackListCache!!.get(
                                        cacheKey(
                                            newCode
                                        )
                                    )
                                            + "' blacklistKey = '" + cacheKey(
                                        newCode.substring(
                                            0,
                                            i + 1
                                        ) + "%"
                                    )
                                            + "' blacklistValue = " + blackListCache!!.get(
                                        cacheKey(
                                            newCode.substring(0, i + 1) + "%"
                                        )
                                    ))
                                )
                            }

                            checkNotNull(newCode)
                            if (newCode.length == 1 && !levelnMap.contains(newCode)) {
                                if (blackListCache!!.get(cacheKey(newCode)) == null) treeDualCodeList.add(
                                    newCode
                                )
                                levelnMap.add(newCode)
                                if (DEBUG) Log.i(
                                    TAG,
                                    ("buildDualCodeList() newCode.length()==1 treeDualCodeList new code = '" + newCode
                                            + "' added. treeDualCodeList.size = " + treeDualCodeList.size)
                                )
                                codeMapped = true
                            } else if ((newCode.length > 1 && !levelnMap.contains(newCode))
                                && blackListCache!!.get(
                                    cacheKey(
                                        newCode.substring(
                                            0,
                                            i + 1
                                        ) + "%"
                                    )
                                ) == null
                            ) {
                                levelnMap.add(newCode)

                                if (blackListCache!!.get(cacheKey(newCode)) == null) treeDualCodeList.add(
                                    newCode
                                )
                                if (DEBUG) Log.i(
                                    TAG,
                                    ("buildDualCodeList() treeDualCodeList new code = '" + newCode
                                            + ", c = " + c
                                            + ", n = " + n
                                            + "' added. treeDualCodeList.size = " + treeDualCodeList.size)
                                )

                                codeMapped = true
                            } else if (DEBUG) Log.i(
                                TAG,
                                ("buildDualCodeList()  blacklisted code = '" + newCode.substring(
                                    0,
                                    i + 1
                                ) + "%"
                                        + "'")
                            )

                            c = n
                        } else {
                            if (DEBUG) Log.i(
                                TAG, ("buildDualCodeList() level : " + i
                                        + " ended. treeDualCodeList.size = " + treeDualCodeList.size)
                            )
                            codeMapped = false
                        }
                    } while (codeMapped)
                    treemap.put(i, levelnMap)
                }
            }


            //Jeremy '11,8,12 added for continuous typing.  
            if (tableName == LIME.DB_TABLE_PHONETIC) {
                val tempList = HashSet<String>(treeDualCodeList)
                for (iterator_code in tempList) {
                    if (iterator_code.matches(".+[ 3467].+".toRegex())) { // regular expression mathes tone in the middle
                        val newCode = iterator_code.replace("[3467 ]".toRegex(), "")
                        //Jeremy '12,6,3 look-up the blacklist cache before add to the list.
                        if (DEBUG) Log.i(
                            TAG,
                            "buildDualCodeList(): processing no tone code :" + newCode
                        )
                        if (!newCode.isEmpty() && !treeDualCodeList.contains(newCode) && !checkBlackList(
                                cacheKey(newCode)
                            )
                        ) {
                            treeDualCodeList.add(newCode)
                            if (DEBUG) Log.i(
                                TAG,
                                "buildDualCodeList(): no tone code added:" + newCode
                            )
                        }
                    }
                }
            }
        }


        if (DEBUG) Log.i(
            TAG,
            "buildDualCodeList(): treeDualCodeList.size()=" + treeDualCodeList.size
        )
        return treeDualCodeList
    }

    /**
     * Jeremy '12,6,4 check black list on code , code + wildcard and reduced code with wildcard
     * 
     * @param code blacklist query code
     * @return true if the cod is black listed
     */
    private fun checkBlackList(code: String): Boolean {
        var isBlacklisted = false
        if (code.length < DUALCODE_NO_CHECK_LIMIT) { //code too short, add anyway
            if (DEBUG) Log.i(
                TAG,
                "buildDualCodeList(): code too short add without check code=" + code
            )
        } else if (blackListCache!!.get(cacheKey(code)) != null) { //the code is blacklisted
            isBlacklisted = true
            if (DEBUG) Log.i(TAG, "buildDualCodeList(): black listed code:" + code)
            /*}else if(blackListCache.get(cacheKey(code+"%")) != null){ //the code with wildcard is blacklisted
			if(DEBUG) 
				Log.i(TAG, "buildDualCodeList(): check black list code:"+ code 
					+ ", blackListCache.get(cacheKey(codeToCheck+%))="+blackListCache.get(cacheKey(code+"%")));
			isBlacklisted = true;
			if(DEBUG) 
				Log.i(TAG, "buildDualCodeList(): black listed code:"+ code+"%");*/
        } else {
            for (i in DUALCODE_NO_CHECK_LIMIT - 1..code.length) {
                val codeToCheck = code.substring(0, i) + "%"
                if (blackListCache!!.get(cacheKey(codeToCheck)) != null) {
                    isBlacklisted = true
                    if (DEBUG) Log.i(TAG, "buildDualCodeList(): black listed code:" + codeToCheck)
                    break
                }
            }
        }
        return isBlacklisted
    }


    /**
     * Jeremy '12,6,4 check black list on code , code + wildcard and reduced code with wildcard
     */
    private fun removeFromBlackList(code: String) {
        if (blackListCache!!.get(cacheKey(code)) != null) blackListCache!!.remove(cacheKey(code))

        for (i in DUALCODE_NO_CHECK_LIMIT - 1..code.length) {
            val codeToCheck = code.substring(0, i) + "%"
            if (blackListCache!!.get(cacheKey(codeToCheck)) != null) blackListCache!!.remove(
                cacheKey(codeToCheck)
            )
        }
    }


    private fun expandDualCode(code: String, keytablename: String?): Pair<String?, String?> {
        if (DEBUG) Log.i(TAG, "expandDualCode() code=" + code + ", keytablename = " + keytablename)

        val dualCodeList = buildDualCodeList(code, keytablename)
        val selectClause = StringBuilder()
        val exactMatchClause = StringBuilder()
        var validDualCodeList = StringBuilder()

        val NOCheckOnExpand = code.length < DUALCODE_NO_CHECK_LIMIT
        val searchNoToneCode = tableName == LIME.DB_TABLE_PHONETIC

        for (dualcode in dualCodeList) {
            if (DEBUG) Log.i(
                TAG,
                "expandDualCode(): processing dual code = '" + dualcode + "'" + ". result = " + selectClause
            )


            var noToneCode: String? = dualcode
            var codeCol: String = FIELD_CODE
            val col = arrayOf<String?>(codeCol)

            if (tableName == LIME.DB_TABLE_PHONETIC) {
                val tonePresent =
                    dualcode.matches(".+[3467 ].*".toRegex()) // Tone symbols present in any locoation except the first character
                val toneNotLast =
                    dualcode.matches(".+[3467 ].+".toRegex()) // Tone symbols present in any locoation except the first and last character

                if (searchNoToneCode) { //noToneCode (phonetic combination without tones) is present
                    if (tonePresent) {
                        //LD phrase if tone symbols present but not in last character or in last character but the length > 4 (phonetic combinations never has length >4)
                        if (toneNotLast || (dualcode.length > 4)) noToneCode =
                            dualcode.replace("[3467 ]".toRegex(), "")
                    } else { // no tone symbols present, check noToneCode column
                        codeCol = FIELD_NO_TONE_CODE
                    }
                } else if (tonePresent && (toneNotLast || (dualcode.length > 4)))  //LD phrase and no noToneCode column present
                    noToneCode = dualcode.replace("[3467 ]".toRegex(), "")
            }
            // do escape code for codes
            val queryCode = dualcode.trim { it <= ' ' }.replace("'".toRegex(), "''")
            val queryNoToneCode = noToneCode!!.trim { it <= ' ' }.replace("'".toRegex(), "''")


            if (queryCode.isEmpty()) continue


            if (NOCheckOnExpand) {
                if (dualcode != code) {
                    //result = result + " OR " + codeCol + "= '" + queryCode + "'";
                    selectClause.append(" or (")
                        .append(expandBetweenSearchClause(codeCol, dualcode)).append(") ")
                    exactMatchClause.append(" or ").append(codeCol).append(" ='").append(queryCode)
                        .append("' ")
                }
            } else {
                //Jeremy '11,8, 26 move valid code list building to buildqueryresult to avoid repeat query.
                try {
                    var selectValidCodeClause = codeCol + " = '" + queryCode + "'"
                    if (dualcode != noToneCode) { //code with tones. should strip tone symbols and add to the select condition.
                        selectValidCodeClause =
                            FIELD_CODE + " = '" + queryCode + "' OR " + FIELD_NO_TONE_CODE + " = '" + queryNoToneCode + "'"
                    }

                    if (DEBUG) Log.i(
                        TAG,
                        "expandDualCode() selectClause for exactmatch = " + selectValidCodeClause
                    )

                    var cursor: Cursor = db!!.query(
                        tableName,
                        col,
                        selectValidCodeClause,
                        null,
                        null,
                        null,
                        null,
                        "1"
                    )
                    if (cursor != null) {
                        if (cursor.moveToFirst()) { //fist entry exist, the code is valid.
                            if (DEBUG) Log.i(
                                TAG,
                                "expandDualCode()  code = '" + dualcode + "' is valid code"
                            )
                            if (validDualCodeList.length == 0) validDualCodeList =
                                StringBuilder(dualcode)
                            else validDualCodeList.append("|").append(dualcode)
                            if (dualcode != code) {
                                //result = result + " OR " + codeCol + "= '" + queryCode + "'";
                                selectClause.append(" or (")
                                    .append(expandBetweenSearchClause(codeCol, dualcode))
                                    .append(") ")
                                exactMatchClause.append(" or (").append(codeCol).append(" ='")
                                    .append(queryCode).append("') ")
                            }
                        } else { //the code is not valid, keep it in the black list cache. Jeremy '12,6,3

                            var charray = dualcode.toCharArray()
                            charray[queryCode.length - 1]++
                            var nextcode = String(charray)
                            nextcode = nextcode.replace("'".toRegex(), "''")

                            selectValidCodeClause =
                                codeCol + " > '" + queryCode + "' AND " + codeCol + " < '" + nextcode + "'"

                            if (dualcode != noToneCode) { //code with tones. should strip tone symbols and add to the select condition.
                                charray = queryNoToneCode.toCharArray()
                                charray[noToneCode.length - 1]++
                                var nextNoToneCode = String(charray)
                                nextNoToneCode = nextNoToneCode.replace("'".toRegex(), "''")
                                selectValidCodeClause =
                                    ("(" + codeCol + " > '" + queryCode + "' AND " + codeCol + " < '" + nextcode + "') "
                                            + "OR (" + codeCol + " > '" + queryNoToneCode + "' AND " + codeCol + " < '" + nextNoToneCode + "')")
                            }
                            cursor.close()
                            if (DEBUG) Log.i(
                                TAG,
                                ("expandDualCode() dualcode = '" + dualcode + "' noToneCode = '"
                                        + noToneCode + "' selectValidCodeClause for no exact match = " + selectValidCodeClause)
                            )


                            cursor = db!!.query(
                                tableName, col, selectValidCodeClause,
                                null, null, null, null, "1"
                            )


                            if (cursor == null || !cursor.moveToFirst()) { //code* returns no valid records add the code with wildcard to blacklist
                                blackListCache!!.put(cacheKey(dualcode + "%"), true)
                                // if (DEBUG)
                                Log.i(
                                    TAG,
                                    (" expandDualCode() blackList wildcard code added, code = " + dualcode + "%"
                                            + ", cachekey = :" + cacheKey(dualcode + "%")
                                            + ", black list size = " + blackListCache!!.size
                                            + ", blackListCache.get() = " + blackListCache!!.get(
                                        cacheKey(dualcode + "%")
                                    ))
                                )
                            } else { //only add the code to black list
                                blackListCache!!.put(cacheKey(dualcode), true)
                                cursor.close()
                                if (DEBUG) Log.i(
                                    TAG,
                                    " expandDualCode() blackList code added, code = " + dualcode
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in database operation", e)
                }
            }
        }

        if (validDualCodeList.toString().isEmpty()) lastValidDualCodeList = null
        else lastValidDualCodeList = validDualCodeList.toString()

        if (DEBUG) Log.i(
            TAG,
            "expandDualCode(): result:" + selectClause + " validDualCodeList:" + validDualCodeList
        )
        return Pair<String?, String?>(selectClause.toString(), exactMatchClause.toString())
    }

    /**
     * Jeremy '12,6,3 Build unique cache key for black list cache.
     */
    private fun cacheKey(code: String?): String {
        return tableName + "_" + code
    }

    /**
     * Process search results
     */
    @Synchronized
    private fun buildQueryResult(
        query_code: String,
        codeorig: String?,
        cursor: Cursor,
        getAllRecords: Boolean
    ): MutableList<Mapping?> {
        var startTime: Long = 0
        if (DEBUG || probePerformance) {
            startTime = System.currentTimeMillis()
            Log.i(TAG, "buildQueryResult()")
        }


        val result: MutableList<Mapping?> = ArrayList<Mapping?>()


        val duplicateCheck = HashSet<String?>()
        val validCodeMap = HashSet<String?>() //Jeremy '11,8,26
        var rsize = 0
        //jeremy '11,8,30 reset lastValidDualCodeList first.
        val buildValidCodeList = lastValidDualCodeList == null

        val searchNoToneColumn = tableName == LIME.DB_TABLE_PHONETIC
                && !query_code.matches(".+[3467 ].*".toRegex())
        if (DEBUG) Log.i(
            TAG, ("buildQueryResutl(): cursor.getCount()=" + cursor.getCount()
                    + ". lastValidDualCodeList = " + lastValidDualCodeList)
        )
        if (cursor.moveToFirst()) {
            val sLimit = mLIMEPref.getSimilarCodeCandidates()
            var sCount = 0
            if (DEBUG) Log.i(
                TAG,
                "buildQueryResult(): code=" + query_code + ", similar code limit=" + sLimit
            )

            do {
                val word = getCursorString(cursor, FIELD_WORD)
                //skip if word is null
                if (word == null || word.trim { it <= ' ' }.isEmpty()) continue
                val code = getCursorString(cursor, FIELD_CODE)
                val m = Mapping()
                m.setCode(code)
                m.setCodeorig(codeorig)
                m.setWord(word)
                m.setId(getCursorString(cursor, FIELD_ID))
                m.setScore(getCursorInt(cursor, FIELD_SCORE))
                m.setBasescore(getCursorInt(cursor, FIELD_BASESCORE))

                //String relatedlist = (betweenSearch)?null: cursor.getString(relatedColumn);
                val exactMatch = getCursorString(
                    cursor,
                    FILE_EXACT_MATCH
                ) == "1" //Jeremy '15,6,3 new exact match virtual column built in query time.

                //m.setHighLighted((betweenSearch) && !exactMatch);//Jeremy '12,5,30 exact match, not from related list

                //Jeremy 15,6,3 new exact or partial record type
                if (exactMatch) m.setExactMatchToCodeRecord()
                else m.setPartialMatchToCodeRecord()

                //Jeremy '11,8,26 build valid code map
                //jeremy '11,8,30 add limit for valid code words for composing display
                if (buildValidCodeList) {
                    val noToneCode = getCursorString(cursor, FIELD_NO_TONE_CODE)
                    if (searchNoToneColumn && noToneCode != null && noToneCode.trim { it <= ' ' }.length == query_code.replace(
                            "[3467 ]".toRegex(),
                            ""
                        )
                            .trim { it <= ' ' }.length && validCodeMap.size < DUALCODE_COMPOSING_LIMIT) validCodeMap.add(
                        noToneCode
                    )
                    else if (code != null && code.length == query_code.length) validCodeMap.add(code)
                }


                // 06/Aug/2011 by Art: ignore the result when word == keyToKeyname(code)
                // Only apply to Array IM
                try {
                    if (code != null && code.length == 1 && tableName == LIME.DB_TABLE_ARRAY) {
                        if (keyToKeyName(code, tableName, false) == m.getWord()) {
                            continue
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking keyToKeyname", e)
                }

                if (duplicateCheck.add(m.getWord())) {
                    if (m.isPartialMatchToCodeRecord()) {
                        if (sCount >= sLimit) break
                        sCount++
                    }

                    result.add(m)
                }
                rsize++
                if (DEBUG) Log.i(
                    TAG,
                    "buildQueryResult():  current code = " + m.getCode() + ", current word =" + m.getWord() + ", similar code count=" + sCount + ", record counts" + rsize
                )
            } while (cursor.moveToNext())


            //Jeremy '11,8,26 build valid code map
            if (buildValidCodeList && !validCodeMap.isEmpty()) {
                val sb = StringBuilder()
                var first = true
                for (validCode in validCodeMap) {
                    if (DEBUG) Log.i(
                        TAG,
                        "buildQueryResult(): buildValidCodeList: valicode=" + validCode
                    )
                    if (first) {
                        sb.append(validCode)
                        first = false
                    } else {
                        sb.append("|").append(validCode)
                    }
                }
                lastValidDualCodeList = sb.toString()
            }
        }


        // Add full shaped punctuation symbol to the third place  , and .
        if (query_code.length == 1) {
            if ((query_code == "," || query_code == "<") && duplicateCheck.add("，")) {
                val temp = Mapping()
                temp.setCode(query_code)
                temp.setWord("，")
                temp.setChinesePunctuationSymbolRecord()
                if (result.size > 3) result.add(3, temp)
                else result.add(temp)
            }
            if ((query_code == "." || query_code == ">") && duplicateCheck.add("。")) {
                val temp = Mapping()
                temp.setCode(query_code)
                temp.setWord("。")
                temp.setChinesePunctuationSymbolRecord()
                if (result.size > 3) result.add(3, temp)
                else result.add(temp)
            }
        }


        val hasMore = Mapping()
        hasMore.setCode("has_more_records")
        hasMore.setWord("...")
        hasMore.setHasMoreRecordsMarkRecord()

        if (!getAllRecords && rsize == INITIAL_RESULT_LIMIT.toInt()) result.add(hasMore)

        if (DEBUG || probePerformance) Log.i(
            TAG,
            ("buildQueryResult():query_code:" + query_code + " query_code.length:" + query_code.length
                    + " result.size=" + result.size + " query size:" + rsize + ", time elapsed = " + (System.currentTimeMillis() - startTime))
        )
        return result
    }

    /*
     * @return Cursor for
     *
    public Cursor getDictionaryAll() {
    //Jeremy '12,5,1 !checkDBConnection() when db is restoring or replaced.
    if (!checkDBConnection()) return null;

    Cursor cursor;
    cursor = db.query("dictionary", null, null, null, null, null, null, null);
    return cursor;
    } */
    /**
     * Gets related phrase suggestions for a parent word.
     * 
     * 
     * This method retrieves related phrase candidates that can follow the
     * given parent word. It respects the "similar enable" preference setting.
     * 
     * 
     * If pword length > 1, it searches for phrases matching both the full
     * word and the last character, sorted by word length (longer first).
     * 
     * 
     * Results are sorted by userscore and basescore descending, and limited
     * based on the getAllRecords parameter.
     * 
     * @param pword The parent word to get related phrases for
     * @param getAllRecords If true, returns up to FINAL_RESULT_LIMIT; if false, returns up to INITIAL_RESULT_LIMIT
     * @return List of Mapping objects containing related phrase suggestions, or empty list if disabled or error
     */
    open fun getRelatedPhrase(pword: String?, getAllRecords: Boolean): MutableList<Mapping?> {
        if (DEBUG) Log.i(TAG, "getRelatedPhrase(), " + getAllRecords)

        val result: MutableList<Mapping?> = LinkedList<Mapping?>()


        if (mLIMEPref.getSimiliarEnable()) {
            if (pword != null && !pword.trim { it <= ' ' }.isEmpty()) {
                var cursor: Cursor?

                // Jeremy '11,8.23 remove group by condition to avoid sorting ordr
                // Jeremy '11,8,1 add group by cword to remove duplicate items.
                //Jeremy '11,6,12, Add constraint on cword is not null (cword =null is for recoding im related list selected count).
                //Jeremy '12,12,21 Add limitClause to limit candidates in only 1 page first.
                //					to do 2 stage query.
                //Jeremy '14,12,38 Add query on word length > 1 to include last character into query
                val limitClause: String?

                limitClause = if (getAllRecords) FINAL_RESULT_LIMIT else INITIAL_RESULT_LIMIT

                val pwordCodePointLength = pword.codePointCount(0, pword.length)
                if (pwordCodePointLength > 1) {
                    val last: String =
                        codePointSubstring(pword, pwordCodePointLength - 1, pwordCodePointLength)

                    var selectString =
                        ("SELECT " + FIELD_ID + ", " + FIELD_DIC_pword + ", " + FIELD_DIC_cword + ", "
                                + LIME.DB_RELATED_COLUMN_BASESCORE + ", " + LIME.DB_RELATED_COLUMN_USERSCORE
                                + ", length(" + FIELD_DIC_pword + ") as len FROM " + LIME.DB_TABLE_RELATED + " where "
                                + FIELD_DIC_pword + " = ? or " + FIELD_DIC_pword + " = ?"
                                + " and " + FIELD_DIC_cword + " is not null"
                                + " order by len desc, " + LIME.DB_RELATED_COLUMN_USERSCORE + " desc, "
                                + LIME.DB_RELATED_COLUMN_BASESCORE + " desc ")

                    selectString = selectString + " limit " + limitClause

                    if (DEBUG) Log.i(TAG, "getRelatedPhrase() selectString = " + selectString)

                    try {
                        cursor = db!!.rawQuery(selectString, arrayOf<String>(pword, last))
                    } catch (sqe: SQLiteException) {
                        if (DEBUG) Log.e(TAG, "Error in database operation", sqe)

                        cursor = null
                    }
                } else {
                    cursor = db!!.query(
                        LIME.DB_TABLE_RELATED,
                        null,
                        FIELD_DIC_pword + " = ? and " + FIELD_DIC_cword + " is not null ",
                        arrayOf<String>(pword),
                        null,
                        null,
                        (LIME.DB_RELATED_COLUMN_USERSCORE + " DESC, "
                                + LIME.DB_RELATED_COLUMN_BASESCORE + " DESC"),
                        limitClause
                    )
                }
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        var rsize = 0
                        do {
                            val munit = Mapping()

                            munit.setId(getCursorString(cursor, LIME.DB_RELATED_COLUMN_ID))
                            munit.setPword(getCursorString(cursor, LIME.DB_RELATED_COLUMN_PWORD))
                            munit.setWord(getCursorString(cursor, LIME.DB_RELATED_COLUMN_CWORD))
                            munit.setScore(getCursorInt(cursor, LIME.DB_RELATED_COLUMN_USERSCORE))
                            munit.setBasescore(
                                getCursorInt(
                                    cursor,
                                    LIME.DB_RELATED_COLUMN_BASESCORE
                                )
                            )
                            munit.setCode("")
                            munit.setRelatedPhraseRecord()
                            result.add(munit)
                            rsize++
                        } while (cursor.moveToNext())
                        val temp = Mapping()
                        temp.setCode("has_more_records")
                        temp.setWord("...")
                        temp.setHasMoreRecordsMarkRecord()

                        if ((!getAllRecords && rsize == INITIAL_RESULT_LIMIT.toInt())) result.add(
                            temp
                        )
                    }
                    cursor.close()
                }
            }
        }
        return result
    }

    /**
     * Prepares a backup of database tables to a target database file.
     * 
     * 
     * This is the unified method for preparing backups. It can backup:
     * 
     *  * One or more mapping tables (with IM information)
     *  * Related phrase table (optional)
     * 
     * 
     * 
     * The database connection is held during the operation to prevent concurrent access.
     * 
     * @param targetFile The target database file to write backup to
     * @param tableNames List of table names to backup (null or empty for none)
     * @param includeRelated If true, also backup the related phrase table
     */
    fun prepareBackup(
        targetFile: File?,
        tableNames: MutableList<String?>?,
        includeRelated: Boolean
    ) {
        if (checkDBConnection()) return
        if (targetFile == null) {
            Log.e(TAG, "prepareBackup(): targetFile is null")
            return
        }

        // Validate all table names
        if (tableNames != null) {
            for (tableName in tableNames) {
                if (!isValidTableName(tableName)) {
                    Log.e(TAG, "prepareBackup(): Invalid table name: " + tableName)
                    return
                }
            }
        }

        // Ensure parent directory exists before attaching database
        val parentDir = targetFile.getParentFile()
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                Log.e(
                    TAG,
                    "prepareBackup(): Failed to create parent directory: " + parentDir.getAbsolutePath()
                )
                return
            }
        }

        holdDBConnection()
        try {
            db!!.execSQL("attach database '" + targetFile.getAbsolutePath() + "' as sourceDB")

            // Backup mapping tables
            if (tableNames != null && !tableNames.isEmpty()) {
                for (tableName in tableNames) {
                    // Copy table data to sourceDB.custom (backup format)
                    db!!.execSQL("insert into sourceDB." + LIME.DB_TABLE_CUSTOM + " select * from " + tableName)
                }


                // Copy IM information for all tables
                if (tableNames.size == 1) {
                    // Single table: copy specific IM info
                    val tableName = tableNames.get(0)
                    db!!.execSQL("insert into sourceDB." + LIME.DB_TABLE_IM + " select * from " + LIME.DB_TABLE_IM + " WHERE " + LIME.DB_IM_COLUMN_CODE + "='" + tableName + "'")
                    db!!.execSQL("update sourceDB." + LIME.DB_TABLE_IM + " set " + LIME.DB_IM_COLUMN_CODE + "='" + tableName + "'")
                } else {
                    // Multiple tables: copy all IM info for these tables
                    val whereClause = StringBuilder(LIME.DB_IM_COLUMN_CODE + " IN (")
                    for (i in tableNames.indices) {
                        if (i > 0) whereClause.append(",")
                        whereClause.append("'").append(tableNames.get(i)).append("'")
                    }
                    whereClause.append(")")
                    db!!.execSQL("insert into sourceDB." + LIME.DB_TABLE_IM + " select * from " + LIME.DB_TABLE_IM + " WHERE " + whereClause)
                }
            }

            // Backup related table if requested
            if (includeRelated) {
                db!!.execSQL("insert into sourceDB." + LIME.DB_TABLE_RELATED + " select * from " + LIME.DB_TABLE_RELATED)
            }

            db!!.execSQL("detach database sourceDB")
        } catch (e: Exception) {
            Log.e(TAG, "prepareBackup(): Error during backup", e)
            try {
                db!!.execSQL("detach database sourceDB")
            } catch (e2: Exception) {
                // Ignore detach errors
            }
        } finally {
            unHoldDBConnection()
        }
    }

    /**
     * Prepares a backup of the related phrase database.
     * 
     * 
     * This method attaches the source database file and copies all related
     * phrase records into it. The database connection is held during the operation.
     * 
     * @param sourcedbfile The path to the backup database file
     * 
     * This is a convenience wrapper for [.prepareBackup]
     * with includeRelated=true.
     */
    fun prepareBackupRelatedDb(sourcedbfile: String) {
        prepareBackup(File(sourcedbfile), null, true)
    }

    /**
     * Prepares a backup of a mapping table database.
     * 
     * 
     * This method attaches the source database file and copies:
     * 
     *  * All records from the specified table
     *  * IM information for the table
     * 
     * 
     * 
     * The database connection is held during the operation.
     * 
     * @param sourcedbfile The path to the backup database file
     * @param sourcetable The table name to backup
     * 
     * This is a convenience wrapper for [.prepareBackup]
     * with includeRelated=false.
     */
    fun prepareBackupDb(sourcedbfile: String, sourcetable: String?) {
        val tableNames: MutableList<String?> = ArrayList<String?>()
        tableNames.add(sourcetable)
        prepareBackup(File(sourcedbfile), tableNames, false)
    }

    /**
     * Imports database tables from a backup database file.
     * 
     * 
     * This is the unified method for importing database backups. It can import:
     * 
     *  * One or more mapping tables (from sourceDB.custom for backup format, or sourceDB.{tableName} for direct format)
     *  * Related phrase table (optional)
     * 
     * 
     * 
     * The method first tries to import from sourceDB.custom (backup format), then falls back
     * to sourceDB.{tableName} (direct format) if custom table doesn't exist.
     * 
     * 
     * The database connection is held during the operation to prevent concurrent access.
     * 
     * @param sourceFile The backup database file to import from
     * @param tableNames List of table names to import (null or empty for none)
     * @param includeRelated If true, also import the related phrase table
     * @param overwriteExisting If true, delete existing data before importing
     */
    fun importDb(
        sourceFile: File?,
        tableNames: MutableList<String?>?,
        includeRelated: Boolean,
        overwriteExisting: Boolean
    ) {
        if (checkDBConnection()) return
        if (sourceFile == null || !sourceFile.exists()) {
            Log.e(TAG, "importDb(): sourceFile is null or doesn't exist")
            return
        }

        // Validate table names and filter out invalid ones
        val validTableNames: MutableList<String> = ArrayList<String>()
        if (tableNames != null) {
            for (tableName in tableNames) {
                if (isValidTableName(tableName)) {
                    validTableNames.add(tableName!!)
                } else {
                    Log.w(TAG, "importDb(): Skipping invalid table name: " + tableName)
                }
            }
        }

        // If no valid table names and not including related, nothing to import
        if ((validTableNames.isEmpty()) && !includeRelated) {
            Log.w(TAG, "importDb(): No valid tables to import")
            return
        }

        // Delete existing data if overwrite requested
        if (overwriteExisting) {
            if (!validTableNames.isEmpty()) {
                for (tableName in validTableNames) {
                    clearTable(tableName)
                }
                // Delete IM info for these tables
                if (validTableNames.size == 1) {
                    val tableName: String? = validTableNames.get(0)
                    db!!.execSQL("delete from " + LIME.DB_TABLE_IM + " where " + LIME.DB_IM_COLUMN_CODE + "='" + tableName + "'")
                } else if (validTableNames.size > 1) {
                    val whereClause = StringBuilder(LIME.DB_IM_COLUMN_CODE + " IN (")
                    for (i in validTableNames.indices) {
                        if (i > 0) whereClause.append(",")
                        whereClause.append("'").append(validTableNames.get(i)).append("'")
                    }
                    whereClause.append(")")
                    db!!.execSQL("delete from " + LIME.DB_TABLE_IM + " where " + whereClause)
                }
            }
            if (includeRelated) {
                clearTable(LIME.DB_TABLE_RELATED)
            }
        }

        holdDBConnection()
        try {
            db!!.execSQL("attach database '" + sourceFile.getAbsolutePath() + "' as sourceDB")

            // Import mapping tables
            if (!validTableNames.isEmpty()) {
                for (tableName in validTableNames) {
                    // Check if backup-format table exists in the attached source DB
                    var customCheck: Cursor? = null
                    var hasCustom = false
                    try {
                        customCheck = db!!.rawQuery(
                            "SELECT name FROM sourceDB.sqlite_master WHERE type='table' AND name=?",
                            arrayOf<String>(LIME.DB_TABLE_CUSTOM)
                        )
                        hasCustom = customCheck != null && customCheck.getCount() > 0
                    } catch (ignored: Exception) {
                        // Safe to ignore; we'll fallback to direct format below
                    } finally {
                        if (customCheck != null) customCheck.close()
                    }

                    if (hasCustom) {
                        importMappingRowsFromAttachedSource(db!!, tableName, LIME.DB_TABLE_CUSTOM)
                    } else {
                        Log.d(
                            TAG,
                            "importDb(): sourceDB.custom not found, using sourceDB." + tableName
                        )
                        importMappingRowsFromAttachedSource(db!!, tableName, tableName)
                    }
                }

                // Import and update IM information
                // For single table, update all IM records to use that table's code
                // For multiple tables, we can't update all to one code, so just import as-is
                checkNotNull(tableNames)
                if (tableNames.size == 1) {
                    val tableName = tableNames.get(0)
                    db!!.execSQL("update sourceDB." + LIME.DB_TABLE_IM + " set " + LIME.DB_IM_COLUMN_CODE + "='" + tableName + "'")
                }
                // Remove existing IM rows for incoming codes to avoid PK conflicts
                db!!.execSQL("delete from " + LIME.DB_TABLE_IM + " where " + LIME.DB_IM_COLUMN_CODE + " in (select " + LIME.DB_IM_COLUMN_CODE + " from sourceDB." + LIME.DB_TABLE_IM + ")")
                // Enumerate columns explicitly and omit `_id` so SQLite assigns fresh
                // AUTOINCREMENT ids; copying source `_id` values risks PK collisions on
                // a heavily-used lime.db (caught silently by the outer try/catch).
                // Mirrors the iOS importFromAttachedDB im-merge column list.
                db!!.execSQL(
                    ("insert into " + LIME.DB_TABLE_IM
                            + " (code, title, desc, keyboard, disable, selkey, endkey, spacestyle) "
                            + "select code, title, desc, keyboard, disable, selkey, endkey, spacestyle "
                            + "from sourceDB." + LIME.DB_TABLE_IM)
                )
                for (tableName in validTableNames) {
                    db!!.execSQL(
                        ("insert into " + LIME.DB_TABLE_IM
                                + " (code, title, desc) "
                                + "select ?, ?, ? where not exists (select 1 from " + LIME.DB_TABLE_IM
                                + " where code=? and title=?)"),
                        arrayOf<Any?>(
                            tableName,
                            LIME.IM_FULL_NAME,
                            defaultImFullName(tableName, tableName),
                            tableName,
                            LIME.IM_FULL_NAME
                        )
                    )
                }
            }

            // Import related table if requested
            if (includeRelated) {
                db!!.execSQL("insert into " + LIME.DB_TABLE_RELATED + " select * from sourceDB." + LIME.DB_TABLE_RELATED)
            }

            db!!.execSQL("detach database sourceDB")
        } catch (e: Exception) {
            Log.e(TAG, "importDb(): Error during import", e)
            try {
                db!!.execSQL("detach database sourceDB")
            } catch (e2: Exception) {
                // Ignore detach errors
            }
        } finally {
            unHoldDBConnection()
        }
    }

    /**
     * Imports related phrase data from a backup database file.
     * 
     * 
     * This method:
     * 
     *  * Deletes all existing related phrase records
     *  * Attaches the backup database
     *  * Copies all records from the backup
     *  * Detaches the backup database
     * 
     * 
     * 
     * The database connection is held during the operation.
     * 
     * @param sourcedbfile The backup database file to import from
     * 
     * This is a convenience wrapper for [.importDb]
     * with includeRelated=true and overwriteExisting=true.
     */
    fun importDbRelated(sourcedbfile: File?) {
        importDb(sourcedbfile, null, true, true)
    }

    private fun importMappingRowsFromAttachedSource(
        database: SQLiteDatabase,
        targetTable: String,
        sourceTable: String
    ) {
        val targetColumns = tableColumns(database, targetTable)
        val sourceColumns = tableColumns(database, "sourceDB.$sourceTable")
        if (!targetColumns.contains(LIME.DB_COLUMN_CODE) ||
            !targetColumns.contains(LIME.DB_COLUMN_WORD) ||
            !sourceColumns.contains(LIME.DB_COLUMN_CODE) ||
            !sourceColumns.contains(LIME.DB_COLUMN_WORD)
        ) {
            throw SQLiteException("Mapping import requires code and word columns")
        }

        val insertColumns = ArrayList<String>()
        val selectExpressions = ArrayList<String>()
        addMappingImportColumn(
            insertColumns, selectExpressions, targetColumns, sourceColumns,
            LIME.DB_COLUMN_CODE, LIME.DB_COLUMN_CODE, null
        )
        addMappingImportColumn(
            insertColumns, selectExpressions, targetColumns, sourceColumns,
            LIME.DB_COLUMN_WORD, LIME.DB_COLUMN_WORD, null
        )
        addMappingImportColumn(
            insertColumns, selectExpressions, targetColumns, sourceColumns,
            LIME.DB_COLUMN_SCORE, "COALESCE(${LIME.DB_COLUMN_SCORE}, 0)", "0"
        )
        addMappingImportColumn(
            insertColumns, selectExpressions, targetColumns, sourceColumns,
            LIME.DB_COLUMN_BASESCORE, "COALESCE(${LIME.DB_COLUMN_BASESCORE}, 0)", "0"
        )
        addMappingImportColumn(
            insertColumns, selectExpressions, targetColumns, sourceColumns,
            FIELD_NO_TONE_CODE, FIELD_NO_TONE_CODE, null
        )
        addMappingImportColumn(
            insertColumns, selectExpressions, targetColumns, sourceColumns,
            LIME.DB_COLUMN_RELATED, LIME.DB_COLUMN_RELATED, null
        )

        database.execSQL(
            "insert into $targetTable (${insertColumns.joinToString(", ")}) " +
                    "select ${selectExpressions.joinToString(", ")} " +
                    "from sourceDB.$sourceTable " +
                    "where ${LIME.DB_COLUMN_CODE} is not null and ${LIME.DB_COLUMN_WORD} is not null"
        )
    }

    private fun addMappingImportColumn(
        insertColumns: MutableList<String>,
        selectExpressions: MutableList<String>,
        targetColumns: Set<String>,
        sourceColumns: Set<String>,
        columnName: String,
        sourceExpression: String,
        defaultExpression: String?
    ) {
        if (!targetColumns.contains(columnName)) return
        if (sourceColumns.contains(columnName)) {
            insertColumns.add(columnName)
            selectExpressions.add(sourceExpression)
        } else if (defaultExpression != null) {
            insertColumns.add(columnName)
            selectExpressions.add(defaultExpression)
        }
    }

    private fun tableColumns(database: SQLiteDatabase, tableExpression: String): Set<String> {
        val columns = HashSet<String>()
        var cursor: Cursor? = null
        try {
            cursor = database.rawQuery("select * from $tableExpression limit 0", null)
            for (column in cursor.columnNames) {
                columns.add(column)
            }
        } finally {
            cursor?.close()
        }
        return columns
    }


    /**
     * Backs up user-learned records to a backup table.
     * 
     * 
     * This method creates a backup table (table + "_user") containing all
     * records from the specified table that have a score > 0. The backup table
     * can be used to restore user data after reloading a mapping file.
     * 
     * 
     * The backup table is created as a copy of the query results, sorted
     * by score descending.
     * 
     * @param table The table name to backup user records from
     */
    fun backupUserRecords(table: String) {
        if (DEBUG) Log.i(TAG, "backupUserRecords")
        if (checkDBConnection()) return
        val backupTableName = table + "_user"

        val selectString = "select * from " + table +
                " where " + FIELD_WORD + " is not null and " +
                FIELD_SCORE + " >0 order by " + FIELD_SCORE + " desc"
        val cursor: Cursor = db!!.rawQuery(selectString, null)
        val hasUserData = cursor != null && cursor.getCount() > 0
        if (cursor != null) {
            cursor.close()
        }

        // Always drop existing backup table so stale data does not leak between backups
        val tableCheck: Cursor = db!!.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf<String>(backupTableName)
        )
        val tableExists = tableCheck != null && tableCheck.getCount() > 0
        if (tableCheck != null) {
            tableCheck.close()
        }
        if (tableExists) {
            try {
                db!!.execSQL("drop table " + backupTableName)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing table " + backupTableName, e)
            }
        }

        if (hasUserData) {
            db!!.execSQL("create table " + backupTableName + " as " + selectString)
        }

        // Only count if backup table exists (created above or pre-existing)
        val backupCheck: Cursor = db!!.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf<String>(backupTableName)
        )
        val backupExists = backupCheck != null && backupCheck.getCount() > 0
        if (backupCheck != null) {
            backupCheck.close()
        }
        if (backupExists) {
            countRecords(backupTableName, null, null)
        } else {
            Log.w(TAG, "backupUserRecords(): Backup table not created (no records) for " + table)
        }
    }


    /**
     * Checks if a backup table exists and has records.
     * 
     * 
     * This method queries the backup table (table + "_user") to determine
     * if user data backup exists and contains records.
     * 
     * @param table The base table name to check backup for
     * @return true if backup table exists and has records, false otherwise
     */
    fun checkBackupTable(table: String?): Boolean {
        if (checkDBConnection()) return false
        if (table == null || table.isEmpty()) {
            Log.e(TAG, "checkBackupTable(): Table name cannot be null or empty")
            return false
        }

        val backupTableName = table + "_user"
        var tableCheck: Cursor? = null
        var cursor: Cursor? = null
        try {
            tableCheck = db!!.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf<String>(backupTableName)
            )
            val tableExists = tableCheck != null && tableCheck.getCount() > 0
            if (!tableExists) {
                Log.w(TAG, "checkBackupTable(): Backup table not found: " + backupTableName)
                return false
            }

            cursor = db!!.rawQuery("select COUNT(*) as total from " + backupTableName, null)
            cursor.moveToFirst()

            val total = getCursorInt(cursor, "total")
            if (total > 0) {
                Log.i("LIME", "Total size :" + total)
                return true
            }
            return false
        } catch (s: SQLiteException) {
            Log.e(TAG, "Error checking database table existence", s)
            return false
        } finally {
            if (tableCheck != null) {
                tableCheck.close()
            }
            if (cursor != null) {
                cursor.close()
            }
        }
    }

    /**
     * Drops a backup table if it exists.
     * 
     * 
     * This method safely drops a backup table (table + "_user") if it exists.
     * Used for cleanup operations, particularly in tests.
     * 
     * @param table The base table name (e.g., "custom", "cj")
     * @return true if table was dropped or didn't exist, false if error
     */
    fun dropBackupTable(table: String?): Boolean {
        if (checkDBConnection()) return false

        if (table == null || table.isEmpty()) {
            Log.e(TAG, "dropBackupTable(): Table name cannot be null or empty")
            return false
        }

        if (!isValidTableName(table)) {
            Log.e(TAG, "dropBackupTable(): Invalid table name: " + table)
            return false
        }

        val backupTableName = table + "_user"

        try {
            // Check if backup table exists before trying to drop it
            val tableCheck: Cursor = db!!.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf<String>(backupTableName)
            )
            val tableExists = tableCheck != null && tableCheck.getCount() > 0
            if (tableCheck != null) {
                tableCheck.close()
            }


            // Only drop table if it exists
            if (tableExists) {
                db!!.execSQL("DROP TABLE IF EXISTS " + backupTableName)
                if (DEBUG) {
                    Log.i(TAG, "dropBackupTable(): Dropped backup table: " + backupTableName)
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "dropBackupTable(): Error dropping backup table: " + backupTableName, e)
            return false
        }
    }


    /**
     * Imports a text mapping file into the database table.
     * 
     * 
     * This method performs a complete import of a text mapping file (.lime, .cin, or delimited text):
     * 
     *  * Reads the file line by line
     *  * Identifies the delimiter (comma, tab, pipe, or space)
     *  * Supports .cin format files
     *  * Parses code, word, score, and basescore
     *  * Gets basescore from han converter if not provided
     *  * Inserts records in a transaction for performance
     *  * Updates IM information (name, source, amount, import date)
     *  * Configures keyboard layout for the IM
     * 
     * 
     * 
     * The import operation runs in a background thread and reports progress
     * through the provided LIMEProgressListener. The database connection is held
     * during import to prevent concurrent access.
     * 
     * 
     * Supported file formats:
     * 
     *  * Text files with delimiters: comma, tab, pipe (|), or space
     *  * .cin format files (CIN input method format)
     *  * .lime format files
     * 
     * 
     * 
     * File format: code[delimiter]word[delimiter]score[delimiter]basescore
     * 
     * @param table The table name to import data into (must be valid)
     * @param progressListener Listener for progress updates and completion notification.
     * Can be null if progress reporting is not needed.
     * @throws IllegalStateException if filename is not set via setFilename()
     */
    @Synchronized
    fun importTxtTable(table: String, progressListener: LIMEProgressListener?) {
        if (DEBUG) Log.i(TAG, "importTxtTable()")
        //Jeremy '12,5,1 !checkDBConnection() when db is restoring or replaced.
        if (checkDBConnection()) {
            if (progressListener != null) {
                progressListener.onError(-1, "Database is not available. Please try to do it later")
            }
            return
        }

        // Validate table name
        if (!isValidTableName(table)) {
            Log.e(TAG, "importTxtTable(): Invalid table name: " + table)
            if (progressListener != null) {
                progressListener.onError(-1, "Invalid table name: " + table)
            }
            return
        }

        finish = false
        progressPercentageDone = 0
        this@LimeDB.countImported = 0
        if (importThread != null) {
            //threadAborted = true;
            while (importThread!!.isAlive()) {
                Log.d(TAG, "loadFile():waiting for last loading loadingMappingThread stopped...")
                SystemClock.sleep(SLEEP_DELAY_100_MS.toLong())
            }
            importThread = null
        }

        importThread = object : Thread() {
            override fun run() {
                var delimiter_symbol = ""

                // Reset Database Table		
                //SQLiteDatabase db = getSqliteDb(false);
                if (DEBUG) Log.i(TAG, "importTxtTable loadingMappingThread starting...")


                try {
                    if (countRecords(table, null, null) > 0) db!!.delete(table, null, null)

                    if (table == LIME.DB_TABLE_PHONETIC) {
                        if (DEBUG) Log.i(TAG, "loadfile(), build code3r index.")
                        // Drop existing index before creating to avoid "index already exists" error
                        try {
                            db!!.execSQL("DROP INDEX IF EXISTS phonetic_idx_code3r")
                        } catch (e: Exception) {
                            Log.d(TAG, "Index might not exist, continuing...")
                        }
                        mLIMEPref.setParameter("checkLDPhonetic", "doneV2")
                        db!!.execSQL("CREATE INDEX phonetic_idx_code3r ON phonetic(code3r)")
                    }
                } catch (e1: Exception) {
                    Log.e(TAG, "Error in database operation", e1)
                }


                resetImConfig(table)
                var isCinFormat = false
                val isRelatedTable = table == LIME.DB_TABLE_RELATED

                // Check if filename is null
                if (filename == null) {
                    Log.e(TAG, "importTxtTable: filename is null")
                    if (progressListener != null) {
                        progressListener.onError(-1, "Source file is not specified.")
                    }
                    return
                }

                var version = ""
                var imname = ""
                var line: String?
                var endkey = ""
                var limeendkey = ""
                var selkey = ""
                var spacestyle = ""
                var escapedFormat = false
                val imkeys = StringBuilder()
                var imkeynames = StringBuilder()
                var imkeysHeader = ""
                var imkeynamesHeader = ""


                // Check if source file is .cin format
                if (filename!!.getName().lowercase().endsWith(".cin")) {
                    isCinFormat = true
                }

                // Base on first 100 line to identify the Delimiter
                try {
                    // Prepare Source File
                    val fr = FileReader(filename)
                    val buf = BufferedReader(fr)
                    var i = 0
                    val maxLinesToProcess = 100 // Maximum lines to process in a batch
                    val templist: MutableList<String> = ArrayList<String>()
                    while ((buf.readLine().also { line = it }) != null
                        && !isCinFormat
                    ) {
                        if (line!!.trim { it <= ' ' }.isEmpty() || line!!.trim { it <= ' ' }
                                .startsWith("#")) {
                            continue
                        }
                        templist.add(line)
                        if (i >= maxLinesToProcess) {
                            break
                        } else {
                            i++
                        }
                    }
                    delimiter_symbol = identifyDelimiter(templist)
                    templist.clear()
                    buf.close()
                    fr.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Source file reading error", e)
                    if (progressListener != null) {
                        progressListener.onError(-1, "Source file reading error.")
                    }
                }

                // Check if file exists before proceeding
                if (filename == null || !filename!!.exists()) {
                    Log.e(
                        TAG,
                        "importTxtTable(): File does not exist: " + (if (filename != null) filename!!.getAbsolutePath() else "null")
                    )
                    if (progressListener != null) {
                        progressListener.onError(-1, "Source file does not exist.")
                    }
                    // Don't hold database connection if file doesn't exist
                    return
                }

                //HashSet<String> codeList = new HashSet<>();

                //db = getSqliteDb(false);

                //Jeremy '12,4,10 db will locked after beginTransaction();

                //Jeremy '15,5,23 new database on hold mechanism.
                holdDBConnection()
                db!!.beginTransaction()

                try {
                    // Prepare Source File
                    progressStatus = mContext.getString(R.string.l3_database_loading)
                    val fileLength = filename!!.length()
                    var processedLength: Long = 0
                    val fr = FileReader(filename)
                    val buf = BufferedReader(fr)
                    var firstline = true
                    var inChardefBlock = false
                    var inKeynameBlock = false

                    //String precode = "";
                    while ((buf.readLine().also { line = it }) != null && !threadAborted) {
                        processedLength += (line!!.toByteArray().size + 2).toLong() // +2 for the eol mark.
                        progressPercentageDone =
                            (processedLength.toFloat() / fileLength.toFloat() * LIME.PROGRESS_COMPLETE_PERCENT).toInt()
                        progressStatus =
                            mContext.getString(R.string.l3_database_loading_records) + progressPercentageDone + "%"

                        //Log.i(TAG, line + " / " + delimiter_symbol.equals(" ") + " / " + line.indexOf(delimiter_symbol));
                        //if(DEBUG)
                        //	Log.i(TAG, "loadFile():loadFile()"+ progressPercentageDone +"% processed"
                        //			+ ". processedLength:" + processedLength + ". fileLength:" + fileLength + ", threadAborted=" + threadAborted);
                        if (progressPercentageDone > 99) progressPercentageDone = 99

                        if (delimiter_symbol == " " && !line.contains(delimiter_symbol)) {
                            continue
                        }

                        if (delimiter_symbol == " ") {
                            line = line.replace(" {5}".toRegex(), " ")
                            line = line.replace(" {4}".toRegex(), " ")
                            line = line.replace(" {3}".toRegex(), " ")
                            line = line.replace(" {2}".toRegex(), " ")
                        }

                        if (line.length < 3) {
                            continue
                        }

                        /*
						 * If source is cin format start from the tag %chardef
						 * begin until %chardef end
						 */
                        if (isCinFormat) {
                            val bChardef =
                                line!!.trim { it <= ' ' }.lowercase().startsWith("%chardef")
                            val bKeyname =
                                line!!.trim { it <= ' ' }.lowercase().startsWith("%keyname")
                            if (!(inChardefBlock || inKeynameBlock)) {
                                // Modified by Jeremy '10, 3, 28. Some .cin have
                                // double space between $chardef and begin or
                                // end
                                val bBegin = line!!.trim { it <= ' ' }.lowercase().endsWith("begin")
                                if (bChardef && bBegin
                                ) {
                                    inChardefBlock = true
                                }
                                if (bKeyname && bBegin
                                ) {
                                    inKeynameBlock = true
                                }
                                // Add by Jeremy '10, 3 , 27
                                // use %cname as mapping_version of .cin
                                // Jeremy '11,6,5 add selkey, endkey and spacestyle support
                                if (!(line!!.trim { it <= ' ' }.lowercase().startsWith("%version")
                                            || line!!.trim { it <= ' ' }.lowercase()
                                        .startsWith("%cname")
                                            || line!!.trim { it <= ' ' }.lowercase()
                                        .startsWith("%selkey")
                                            || line!!.trim { it <= ' ' }.lowercase()
                                        .startsWith("%endkey")
                                            || line!!.trim { it <= ' ' }.lowercase()
                                        .startsWith("%limeendkey")
                                            || line!!.trim { it <= ' ' }.lowercase()
                                        .startsWith("%spacestyle")
                                            )) {
                                    continue
                                }
                            }
                            val bEnd = line!!.trim { it <= ' ' }.lowercase().endsWith("end")
                            if (bKeyname && bEnd
                            ) {
                                inKeynameBlock = false
                                continue
                            }
                            if (bChardef && bEnd
                            ) {
                                break
                            }
                            // Skip .cin comment lines (lines starting with '#').
                            // Without this, comment lines like "# Begin" inside the
                            // %chardef begin/end block were imported as mappings
                            // where code="#" and word="Begin"/"End"/etc.
                            if (line!!.trim { it <= ' ' }.startsWith("#")) {
                                continue
                            }
                        }

                        // Check if file contain BOM MARK at file header
                        if (firstline) {
                            val srcstring = line.toByteArray()
                            if (srcstring.size > 3) {
                                if (srcstring[0].toInt() == -17 && srcstring[1].toInt() == -69 && srcstring[2].toInt() == -65) {
                                    val tempstring = ByteArray(srcstring.size - 3)
                                    //int a = 0;
                                    System.arraycopy(
                                        srcstring,
                                        3,
                                        tempstring,
                                        0,
                                        srcstring.size - 3
                                    )
                                    line = String(tempstring)
                                }
                            }
                            firstline = false
                        } else if (line!!.trim { it <= ' ' }.isEmpty()) {
                            continue
                        }
                        if (!isCinFormat && line!!.trim { it <= ' ' }.startsWith("#")) {
                            continue
                        }

                        //else { line.length() }
                        try {
                            if (!isCinFormat && line!!.trim { it <= ' ' }.startsWith("@")) {
                                val metaParts = splitLimeMetadataFields(line, escapedFormat)
                                if (metaParts.size >= 2) {
                                    val metaKey = metaParts.get(0)!!.trim { it <= ' ' }.lowercase()
                                    val metaValue = metaParts.get(1)!!.trim { it <= ' ' }
                                    if ("@format@" == metaKey) {
                                        escapedFormat =
                                            "lime-text-v2".equals(metaValue, ignoreCase = true)
                                        continue
                                    } else if ("@version@" == metaKey) {
                                        version = metaValue
                                        continue
                                    } else if ("@cname@" == metaKey) {
                                        imname = metaValue
                                        if (version.isEmpty()) version = imname
                                        continue
                                    } else if ("@selkey@" == metaKey) {
                                        selkey = metaValue
                                        continue
                                    } else if ("@endkey@" == metaKey) {
                                        endkey = metaValue
                                        continue
                                    } else if ("@limeendkey@" == metaKey) {
                                        limeendkey = metaValue
                                        continue
                                    } else if ("@spacestyle@" == metaKey) {
                                        spacestyle = metaValue
                                        continue
                                    } else if ("@imkeys@" == metaKey) {
                                        imkeysHeader = metaValue
                                        continue
                                    } else if ("@imkeynames@" == metaKey) {
                                        imkeynamesHeader = metaValue
                                        continue
                                    }
                                }
                                continue
                            }

                            // Handle related table import format: pword|cword|basescore|userscore
                            if (isRelatedTable && delimiter_symbol == "|") {
                                try {
                                    val parts =
                                        splitEscapedFields(line!!, delimiter_symbol, escapedFormat)
                                    if (parts.size >= 4) {
                                        val pword = parts.get(0)!!.trim { it <= ' ' }
                                        val cword = parts.get(1)!!.trim { it <= ' ' }
                                        var basescore = 0
                                        var userscore = 0

                                        try {
                                            basescore = parts.get(2)!!.trim { it <= ' ' }.toInt()
                                        } catch (e: NumberFormatException) {
                                            if (DEBUG) Log.e(
                                                TAG,
                                                "Error parsing basescore from line: " + line,
                                                e
                                            )
                                        }

                                        try {
                                            userscore = parts.get(3)!!.trim { it <= ' ' }.toInt()
                                        } catch (e: NumberFormatException) {
                                            if (DEBUG) Log.e(
                                                TAG,
                                                "Error parsing userscore from line: " + line,
                                                e
                                            )
                                        }

                                        if (!pword.isEmpty() && !cword.isEmpty()) {
                                            val cv = ContentValues()
                                            cv.put(LIME.DB_RELATED_COLUMN_PWORD, pword)
                                            cv.put(LIME.DB_RELATED_COLUMN_CWORD, cword)
                                            cv.put(LIME.DB_RELATED_COLUMN_BASESCORE, basescore)
                                            cv.put(LIME.DB_RELATED_COLUMN_USERSCORE, userscore)
                                            val insertResult: Long = db!!.insert(table, null, cv)
                                            if (insertResult != -1L) {
                                                this@LimeDB.countImported++
                                            } else {
                                                if (DEBUG) Log.w(
                                                    TAG,
                                                    "Failed to insert related record: " + pword + "|" + cword
                                                )
                                            }
                                        }
                                        continue  // Skip regular parsing for related table
                                    } else if (parts.size == 3) {
                                        // Legacy format: pword+cword|basescore|userscore (backward compatibility)
                                        val pwordCword = parts.get(0)!!.trim { it <= ' ' }
                                        var basescore = 0
                                        var userscore = 0

                                        try {
                                            basescore = parts.get(1)!!.trim { it <= ' ' }.toInt()
                                        } catch (e: NumberFormatException) {
                                            if (DEBUG) Log.e(
                                                TAG,
                                                "Error parsing basescore from line: " + line,
                                                e
                                            )
                                        }

                                        try {
                                            userscore = parts.get(2)!!.trim { it <= ' ' }.toInt()
                                        } catch (e: NumberFormatException) {
                                            if (DEBUG) Log.e(
                                                TAG,
                                                "Error parsing userscore from line: " + line,
                                                e
                                            )
                                        }


                                        // Try to split pword+cword: heuristic - try first 1-2 characters as pword
                                        // This is not perfect but handles common cases
                                        var pword = ""
                                        var cword = ""

                                        if (!pwordCword.isEmpty()) {
                                            val relatedWords: Array<String> =
                                                splitLeadingCodePoint(pwordCword)
                                            pword = relatedWords[0]
                                            cword = relatedWords[1]
                                            // If cword is empty or too short, try 2 characters for pword
                                            if (cword.isEmpty() && pwordCword.length > 2) {
                                                val end = pwordCword.offsetByCodePoints(
                                                    0,
                                                    min(
                                                        2,
                                                        pwordCword.codePointCount(
                                                            0,
                                                            pwordCword.length
                                                        )
                                                    )
                                                )
                                                pword = pwordCword.substring(0, end)
                                                cword = pwordCword.substring(end)
                                            }
                                        }

                                        if (!pword.isEmpty() && !cword.isEmpty()) {
                                            val cv = ContentValues()
                                            cv.put(LIME.DB_RELATED_COLUMN_PWORD, pword)
                                            cv.put(LIME.DB_RELATED_COLUMN_CWORD, cword)
                                            cv.put(LIME.DB_RELATED_COLUMN_BASESCORE, basescore)
                                            cv.put(LIME.DB_RELATED_COLUMN_USERSCORE, userscore)
                                            val insertResult: Long = db!!.insert(table, null, cv)
                                            if (insertResult != -1L) {
                                                this@LimeDB.countImported++
                                            } else {
                                                if (DEBUG) Log.w(
                                                    TAG,
                                                    "Failed to insert related record (legacy format): " + pwordCword
                                                )
                                            }
                                        }
                                        continue  // Skip regular parsing for related table
                                    }
                                } catch (e: Exception) {
                                    if (DEBUG) Log.e(
                                        TAG,
                                        "Error parsing related table line: " + line,
                                        e
                                    )
                                    continue
                                }
                            }

                            var source_score = 0
                            var source_basescore = 0
                            var code: String? = null
                            var word: String? = null
                            if (isCinFormat) {
                                if (line.contains("\t")) {
                                    val parts = splitEscapedFields(line, "\t", false)
                                    try {
                                        code = parts.get(0)
                                        word = parts.get(1)
                                    } catch (e: Exception) {
                                        if (DEBUG) Log.e(
                                            TAG,
                                            "Error parsing line with tab delimiter: " + line,
                                            e
                                        )
                                        continue
                                    }
                                    try {
                                        // Simply ignore error and try to load score and basescore values
                                        source_score = parts.get(2)!!.trim { it <= ' ' }.toInt()
                                        source_basescore = parts.get(3)!!.trim { it <= ' ' }.toInt()
                                    } catch (e: Exception) {
                                        if (DEBUG) Log.e(
                                            TAG,
                                            "Error parsing score values from line: " + line,
                                            e
                                        )
                                    }
                                } else if (line.contains(" ")) {
                                    val parts = splitEscapedFields(line, " ", false)
                                    try {
                                        code = parts.get(0)
                                        word = parts.get(1)
                                    } catch (e: Exception) {
                                        if (DEBUG) Log.e(
                                            TAG,
                                            "Error parsing line with space delimiter: " + line,
                                            e
                                        )
                                        continue
                                    }
                                    try {
                                        // Simply ignore error and try to load score and basescore values
                                        source_score = parts.get(2)!!.trim { it <= ' ' }.toInt()
                                        source_basescore = parts.get(3)!!.trim { it <= ' ' }.toInt()
                                    } catch (e: Exception) {
                                        if (DEBUG) Log.e(
                                            TAG,
                                            "Error parsing score values from line: " + line,
                                            e
                                        )
                                    }
                                }
                            } else {
                                val parts =
                                    splitEscapedFields(line!!, delimiter_symbol, escapedFormat)
                                try {
                                    code = parts.get(0)
                                    word = parts.get(1)
                                } catch (e: Exception) {
                                    if (DEBUG) Log.e(
                                        TAG,
                                        "Error parsing line with delimiter: " + line,
                                        e
                                    )
                                    continue
                                }
                                try {
                                    // Simply ignore error and try to load score and basescore values
                                    source_score = parts.get(2)!!.trim { it <= ' ' }.toInt()
                                    source_basescore = parts.get(3)!!.trim { it <= ' ' }.toInt()
                                } catch (e: Exception) {
                                    if (DEBUG) Log.e(
                                        TAG,
                                        "Error parsing score values from line: " + line,
                                        e
                                    )
                                }
                            }
                            if (code == null || code.trim { it <= ' ' }.isEmpty()) {
                                continue
                            } else {
                                code = code.trim { it <= ' ' }
                            }
                            if (word == null || word.trim { it <= ' ' }.isEmpty()) {
                                continue
                            } else {
                                word = word.trim { it <= ' ' }
                            }

                            val codeLower = code.lowercase()
                            val escapedMetadataCode =
                                escapedFormat && line!!.trim { it <= ' ' }.startsWith("\\%")
                            var metadataWord = word.trim { it <= ' ' }
                            if (!escapedMetadataCode && codeLower.startsWith("%") && line.length > code.length) {
                                metadataWord = line!!.substring(code.length).trim { it <= ' ' }
                            }

                            if (!escapedMetadataCode && codeLower == "%version") {
                                version = metadataWord
                                continue
                            } else if (!escapedMetadataCode && codeLower == "%cname") {
                                imname = metadataWord
                                if (version.isEmpty()) version = imname
                                continue
                            } else if (!escapedMetadataCode && codeLower == "%selkey") {
                                selkey = metadataWord
                                if (DEBUG) Log.i(TAG, "loadfile(): selkey:" + selkey)
                                continue
                            } else if (!escapedMetadataCode && codeLower == "%endkey") {
                                endkey = metadataWord
                                if (DEBUG) Log.i(TAG, "loadfile(): endkey:" + endkey)
                                continue
                            } else if (!escapedMetadataCode && codeLower == "%limeendkey") {
                                limeendkey = metadataWord
                                if (DEBUG) Log.i(TAG, "loadfile(): limeendkey:" + limeendkey)
                                continue
                            } else if (!escapedMetadataCode && codeLower == "%spacestyle") {
                                spacestyle = metadataWord
                                continue
                            } else {
                                code = codeLower
                            }

                            if (inKeynameBlock) {  //Jeremy '11,6,5 preserve keyname blocks here.
                                imkeys.append(code.lowercase().trim { it <= ' ' })
                                val c = word.trim { it <= ' ' }
                                if (!c.isEmpty()) {
                                    if (imkeynames.length == 0) imkeynames = StringBuilder(c)
                                    else imkeynames.append("|").append(c)
                                }
                            } else {
                                this@LimeDB.countImported++
                                val cv = ContentValues()
                                cv.put(FIELD_CODE, code)

                                if (table == LIME.DB_TABLE_PHONETIC) {
                                    cv.put(
                                        FIELD_NO_TONE_CODE,
                                        code.replace("[3467 ]".toRegex(), "")
                                    )
                                }
                                cv.put(FIELD_WORD, word)
                                cv.put(FIELD_SCORE, source_score)
                                if (source_basescore == 0) {
                                    source_basescore = getBaseScore(word)
                                }
                                cv.put(FIELD_BASESCORE, source_basescore)
                                db!!.insert(table, null, cv)
                            }
                        } catch (e: StringIndexOutOfBoundsException) {
                            if (DEBUG) Log.e(TAG, "String index out of bounds", e)
                        }
                    }

                    buf.close()
                    fr.close()

                    db!!.setTransactionSuccessful()
                } catch (e: Exception) {
                    setImConfig(table, "amount", "0")
                    setImConfig(table, "source", "Failed!!!")
                    Log.e(TAG, "Error in database operation", e)
                    if (progressListener != null) {
                        progressListener.onError(-1, "Table file import failed!")
                    }
                } finally {
                    if (DEBUG) Log.i(TAG, "loadfile(): main import loop final section")
                    db!!.endTransaction()
                    //mLIMEPref.holdDatabaseCoonection(false); // Jeremy '12,4,10 reset mapping_loading status
                    unHoldDBConnection()
                }

                // Fill IM information into the IM Table
                if (!threadAborted && filename != null) {
                    progressPercentageDone = LIME.PROGRESS_COMPLETE_PERCENT
                    finish = true

                    mLIMEPref.setParameter("_table", "")

                    setImConfig(table, "source", filename!!.getName())
                    if (version.isEmpty()) {
                        version = filename!!.getName()
                    }
                    setImConfig(table, "version", version)
                    if (imname.isEmpty()) {
                        setImConfig(table, "name", defaultImFullName(table, filename!!.getName()))
                    } else {
                        setImConfig(table, "name", imname)
                    }
                    setImConfig(table, "amount", countImported.toString())
                    setImConfig(
                        table,
                        "import",
                        Date().toString()
                    ) //Jeremy '12,4,21 toLocaleString() is deprecated

                    if (DEBUG) Log.i(
                        "limedb:loadfile()", ("Fianlly section: source:"
                                + getImConfig(table, "source") + " amount:" + getImConfig(
                            table,
                            "amount"
                        ))
                    )

                    // If user download from LIME Default IM SET then fill in related information
                    if (filename!!.getName() == "phonetic.lime" || filename!!.getName() == "phonetic_adv.lime") {
                        setImConfig(LIME.DB_TABLE_PHONETIC, "selkey", "123456789")
                        setImConfig(
                            LIME.DB_TABLE_PHONETIC,
                            "endkey",
                            "3467'[]\\=<>?:\"{}|~!@#$%^&*()_+"
                        )
                        setImConfig(
                            LIME.DB_TABLE_PHONETIC,
                            "imkeys",
                            ",-./0123456789;abcdefghijklmnopqrstuvwxyz'[]\\=<>?:\"{}|~!@#$%^&*()_+"
                        )
                        setImConfig(
                            LIME.DB_TABLE_PHONETIC,
                            "imkeynames",
                            "ㄝ|ㄦ|ㄡ|ㄥ|ㄢ|ㄅ|ㄉ|ˇ|ˋ|ㄓ|ˊ|˙|ㄚ|ㄞ|ㄤ|ㄇ|ㄖ|ㄏ|ㄎ|ㄍ|ㄑ|ㄕ|ㄘ|ㄛ|ㄨ|ㄜ|ㄠ|ㄩ|ㄙ|ㄟ|ㄣ|ㄆ|ㄐ|ㄋ|ㄔ|ㄧ|ㄒ|ㄊ|ㄌ|ㄗ|ㄈ|、|「|」|＼|＝|，|。|？|：|；|『|』|│|～|！|＠|＃|＄|％|︿|＆|＊|（|）|－|＋"
                        )
                    }
                    if (filename!!.getName() == "array.lime") {
                        setImConfig(LIME.DB_TABLE_ARRAY, "selkey", "1234567890")
                        setImConfig(
                            LIME.DB_TABLE_ARRAY,
                            "imkeys",
                            "abcdefghijklmnopqrstuvwxyz./;,?*#1#2#3#4#5#6#7#8#9#0"
                        )
                        setImConfig(
                            LIME.DB_TABLE_ARRAY,
                            "imkeynames",
                            "1-|5⇣|3⇣|3-|3⇡|4-|5-|6-|8⇡|7-|8-|9-|7⇣|6⇣|9⇡|0⇡|1⇡|4⇡|2-|5⇡|7⇡|4⇣|2⇡|2⇣|6⇡|1⇣|9⇣|0⇣|0-|8⇣|？|＊|1|2|3|4|5|6|7|8|9|0"
                        )
                    } else {
                        if (!selkey.isEmpty()) setImConfig(table, "selkey", selkey)
                        if (!endkey.isEmpty()) setImConfig(table, "endkey", endkey)
                        if (!limeendkey.isEmpty()) setImConfig(
                            table,
                            LIME.IM_LIME_ENDKEY,
                            limeendkey
                        )
                        if (!spacestyle.isEmpty()) setImConfig(table, "spacestyle", spacestyle)
                        if (!imkeysHeader.isEmpty()) setImConfig(table, "imkeys", imkeysHeader)
                        else if (!imkeys.toString().isEmpty()) setImConfig(
                            table,
                            "imkeys",
                            imkeys.toString()
                        )
                        if (!imkeynamesHeader.isEmpty()) setImConfig(
                            table,
                            "imkeynames",
                            imkeynamesHeader
                        )
                        else if (!imkeynames.toString().isEmpty()) setImConfig(
                            table,
                            "imkeynames",
                            imkeynames.toString()
                        )
                    }
                    if (DEBUG) Log.i(
                        TAG,
                        "importTxtTable():update IM info: imkeys:" + imkeys + " imkeynames:" + imkeynames
                    )


                    // '11,5,23 by Jeremy: Preset keyboard info. by tablename
                    var kConfig = getKeyboardConfig(table)
                    if (table == LIME.DB_TABLE_PHONETIC) {
                        val selectedPhoneticKeyboardType =
                            mLIMEPref.getParameterString(
                                "phonetic_keyboard_type",
                                LIME.DB_TABLE_PHONETIC
                            )
                        when (selectedPhoneticKeyboardType) {
                            LIME.DB_TABLE_PHONETIC -> kConfig =
                                getKeyboardConfig(LIME.DB_TABLE_PHONETIC)

                            LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN -> kConfig =
                                getKeyboardConfig("phoneticet41")

                            LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26 -> if (mLIMEPref.getParameterBoolean(
                                    "number_row_in_english",
                                    false
                                )
                            ) {
                                kConfig = getKeyboardConfig("limenum")
                            } else {
                                kConfig = getKeyboardConfig("lime")
                            }

                            "eten26_symbol" -> kConfig = getKeyboardConfig("et26")
                            LIME.IM_PHONETIC_KEYBOARD_HSU -> if (mLIMEPref.getParameterBoolean(
                                    "number_row_in_english",
                                    false
                                )
                            ) {
                                kConfig = getKeyboardConfig("limenum")
                            } else {
                                kConfig = getKeyboardConfig("lime")
                            }

                            "hsu_symbol" -> kConfig =
                                getKeyboardConfig(LIME.IM_PHONETIC_KEYBOARD_HSU)
                        }
                    } else if (table == LIME.DB_TABLE_DAYI) {
                        kConfig = getKeyboardConfig("dayisym")
                    } else if (table == LIME.DB_TABLE_CJ4) {
                        kConfig = getKeyboardConfig("cj")
                    } else if (table == LIME.DB_TABLE_CJ5) {
                        kConfig = getKeyboardConfig("cj")
                    } else if (table == LIME.DB_TABLE_ECJ) {
                        kConfig = getKeyboardConfig("cj")
                    } else if (table == LIME.DB_TABLE_ARRAY) {
                        kConfig = getKeyboardConfig("arraynum")
                    } else if (table == "array10") {
                        kConfig = getKeyboardConfig("phonenum")
                    } else if (table == "wb") {
                        kConfig = getKeyboardConfig("wb")
                    } else if (table == "hs") {
                        kConfig = getKeyboardConfig("hs")
                    } else if (kConfig == null) {    //Jeremy '12,5,21 chose english with number keyboard if the optione is on for default keyboard.
                        if (mLIMEPref.getParameterBoolean("number_row_in_english", true)) {
                            kConfig = getKeyboardConfig("limenum")
                        } else {
                            kConfig = getKeyboardConfig("lime")
                        }
                    }
                    setIMConfigKeyboard(table, kConfig!!.getDescription(), kConfig.code)
                }

                //finishing
            }
        }


        val reportProgressThread: Thread = object : Thread() {
            override fun run() {
                if (progressListener == null) {
                    // If no progress listener, just wait for loading thread to complete
                    while (importThread!!.isAlive()) {
                        SystemClock.sleep(SLEEP_DELAY_100_MS.toLong())
                    }
                    return
                }

                val interval = progressListener.progressInterval()
                while (importThread!!.isAlive()) {
                    SystemClock.sleep(interval)
                    progressListener.onProgress(progressPercentageDone.toLong(), 0, progressStatus)
                }
                progressPercentageDone = 100
                progressListener.onPostExecute(true, null, 0)
            }
        }


        threadAborted = false
        importThread!!.start()
        reportProgressThread.start()
    }


    /**
     * Identifies the delimiter used in a mapping file.
     * 
     * 
     * This method analyzes the first lines of a file to determine which
     * delimiter is used: comma, tab, pipe (|), or space. It counts occurrences
     * of each delimiter and returns the most common one.
     * 
     * 
     * This is used internally during file loading to correctly parse the file format.
     * 
     * @param src List of sample lines from the file (typically first 100 lines)
     * @return The identified delimiter: ",", "\t", "|", or " " (space)
     */
    private fun identifyDelimiter(src: MutableList<String>): String {
        var commaCount = 0
        var tabCount = 0
        var pipeCount = 0
        var spaceCount = 0

        for (line in src) {
            if (line.contains("\t")) {
                tabCount++
            }
            if (line.contains(",")) {
                commaCount++
            }
            if (line.contains("|")) {
                pipeCount++
            }
            if (line.contains(" ")) {
                spaceCount++
            }
        }
        if (commaCount >= tabCount && commaCount >= pipeCount && commaCount >= spaceCount) {
            return ","
        } else if (tabCount >= commaCount && tabCount >= pipeCount && tabCount >= spaceCount) {
            return "\t"
        } else if (pipeCount >= tabCount && pipeCount >= commaCount && pipeCount >= spaceCount) {
            return "|"
        } else {
            return " "
        }
    }

    private fun splitLimeMetadataFields(
        line: String,
        escapedFormat: Boolean
    ): MutableList<String?> {
        val delimiters = arrayOf<String?>("|", "\t", ",", " ")
        for (delimiter in delimiters) {
            val parts = splitEscapedFields(line, delimiter, escapedFormat)
            if (parts.size >= 2 && parts.get(0)!!.trim { it <= ' ' }.startsWith("@")) {
                if (parts.size == 2) {
                    return parts
                }
                val merged: MutableList<String?> = ArrayList<String?>()
                merged.add(parts.get(0))
                val value = StringBuilder(parts.get(1).orEmpty())
                for (i in 2..<parts.size) {
                    value.append(delimiter).append(parts.get(i))
                }
                merged.add(value.toString())
                return merged
            }
        }
        val fallback: MutableList<String?> = ArrayList<String?>()
        fallback.add(line)
        return fallback
    }

    private fun defaultImFullName(table: String?, fallback: String?): String? {
        if (table != null) {
            var i = 0
            while (i < LIME.IM_CODES.size && i < LIME.IM_FULL_NAMES.size) {
                if (table == LIME.IM_CODES[i]) {
                    return LIME.IM_FULL_NAMES[i]
                }
                i++
            }
        }
        return fallback
    }

    private fun splitEscapedFields(
        line: String,
        delimiter: String?,
        escapedFormat: Boolean
    ): MutableList<String?> {
        val result: MutableList<String?> = ArrayList<String?>()
        if (delimiter == null || delimiter.isEmpty()) {
            result.add(decodeEscapedField(line, escapedFormat))
            return result
        }

        val delimiterChar = delimiter.get(0)
        val field = StringBuilder()
        var escaping = false
        for (i in 0..<line.length) {
            val c = line.get(i)
            if (escapedFormat && escaping) {
                field.append(decodeEscapedChar(c))
                escaping = false
            } else if (escapedFormat && c == '\\') {
                escaping = true
            } else if (c == delimiterChar) {
                result.add(field.toString())
                field.setLength(0)
            } else {
                field.append(c)
            }
        }
        if (escapedFormat && escaping) {
            field.append('\\')
        }
        result.add(field.toString())
        return result
    }

    private fun decodeEscapedField(value: String?, escapedFormat: Boolean): String? {
        if (!escapedFormat || value == null || value.indexOf('\\') < 0) {
            return value
        }
        val out = StringBuilder()
        var escaping = false
        for (i in 0..<value.length) {
            val c = value.get(i)
            if (escaping) {
                out.append(decodeEscapedChar(c))
                escaping = false
            } else if (c == '\\') {
                escaping = true
            } else {
                out.append(c)
            }
        }
        if (escaping) {
            out.append('\\')
        }
        return out.toString()
    }

    private fun decodeEscapedChar(c: Char): Char {
        if (c == 't') return '\t'
        if (c == 'n') return '\n'
        return c
    }

    private fun escapeField(value: String?, delimiter: String?): String {
        if (value == null) {
            return ""
        }
        val delimiterChar = if (delimiter == null || delimiter.isEmpty()) '|' else delimiter.get(0)
        val out = StringBuilder()
        for (i in 0..<value.length) {
            val c = value.get(i)
            if (c == '\\') out.append("\\\\")
            else if (c == delimiterChar) out.append('\\').append(c)
            else if (c == '@') out.append("\\@")
            else if (c == '%') out.append("\\%")
            else if (c == '\t') out.append("\\t")
            else if (c == '\n') out.append("\\n")
            else out.append(c)
        }
        return out.toString()
    }

    private fun needsEscapedField(value: String?, delimiter: String?, codeField: Boolean): Boolean {
        if (value == null) {
            return false
        }
        val delimiterChar = if (delimiter == null || delimiter.isEmpty()) '|' else delimiter.get(0)
        val lower = value.lowercase()
        return value.indexOf(delimiterChar) >= 0 || value.indexOf('\\') >= 0 || value.indexOf('\t') >= 0 || value.indexOf(
            '\n'
        ) >= 0 || (codeField && value.startsWith("@"))
                || (codeField && (lower.startsWith("%version")
                || lower.startsWith("%cname")
                || lower.startsWith("%selkey")
                || lower.startsWith("%endkey")
                || lower.startsWith("%limeendkey")
                || lower.startsWith("%spacestyle")))
    }

    /* */
    /**
     * Checks if a specific mapping exists in the database.
     * 
     * 
     * This private method queries the database to find a mapping record
     * matching the given code and optionally word. Used internally during
     * add/update operations to determine if a record already exists.
     * 
     * @param db The database to query
     * @param table The table name to search
     * @param code The input code to search for
     * @param word The output word to search for, or null/empty to match any word
     * @return Mapping object if found, null otherwise
     */
    private fun isMappingExistOnDB(
        db: SQLiteDatabase,
        table: String,
        code: String?,
        word: String?
    ): Mapping? {
        var code = code
        if (DEBUG) Log.i(TAG, "isMappingExistOnDB(), code = '" + code + "'")
        var munit: Mapping? = null
        if (code != null && !code.trim { it <= ' ' }.isEmpty()) {
            val cursor: Cursor?
            // Process the escape characters of query
            code = code.replace("'".toRegex(), "''")
            if (word == null || word.trim { it <= ' ' }.isEmpty()) {
                cursor = db.query(
                    table, null, (FIELD_CODE + " = '"
                            + code + "'"), null, null, null, null, null
                )
            } else {
                cursor = db.query(
                    table, null, (FIELD_CODE + " = '"
                            + code + "'" + " AND " + FIELD_WORD + " = '"
                            + word + "'"), null, null, null, null, null
                )
            }
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    munit = Mapping()

                    //int idColumn = cursor.getColumnIndex(FIELD_ID);
                    //int codeColumn = cursor.getColumnIndex(FIELD_CODE);
                    //int wordColumn = cursor.getColumnIndex(FIELD_WORD);
                    //int scoreColumn = cursor.getColumnIndex(FIELD_SCORE);
                    //int relatedColumn = cursor.getColumnIndex(FIELD_RELATED);
                    munit.setCode(getCursorString(cursor, FIELD_CODE))
                    munit.setWord(getCursorString(cursor, FIELD_WORD))
                    munit.setScore(getCursorInt(cursor, FIELD_SCORE))
                    //munit.setHighLighted(cursor.getString(relatedColumn));
                    //munit.setHighLighted(false);
                    munit.setExactMatchToCodeRecord()
                    if (DEBUG) Log.i(TAG, "isMappingExistOnDB(), mapping is exist")
                } else if (DEBUG) Log.i(TAG, "isMappingExistOnDB(), mapping is not exist")

                cursor.close()
            }
        }
        return munit
    }


    /**
     * Checks if a related phrase record exists in the user dictionary.
     * 
     * 
     * This method queries the related table to find a record matching
     * the parent word and child word combination. Used to determine if a
     * phrase should be added as new or updated as existing.
     * 
     * @param pword The parent word (previous word in phrase)
     * @param cword The child word (next word in phrase), or null to check for frequency record
     * @return Mapping object if found, null otherwise
     */
    open fun isRelatedPhraseExist(pword: String?, cword: String?): Mapping? {
        var startTime: Long = 0
        if (DEBUG || probePerformance) {
            startTime = System.currentTimeMillis()
            Log.i(TAG, "isRelatedPhraseExist(): pword='" + pword + ", cword=" + cword)
        }
        if (checkDBConnection()) return null
        var munit: Mapping? = null

        //SQLiteDatabase db = this.getSqliteDb(true);
        try {
            munit = isRelatedPhraseExistOnDB(db!!, pword, cword)
        } catch (e: Exception) {
            Log.e(TAG, "Error in database operation", e)
        }

        if (DEBUG || probePerformance) {
            Log.i(
                TAG,
                "isRelatedPhraseExist(): time elapsed = " + (System.currentTimeMillis() - startTime)
            )
        }

        return munit
    }

    /**
     * Jeremy '12/4/16 core of isUserDictExist()
     */
    private fun isRelatedPhraseExistOnDB(
        db: SQLiteDatabase,
        pword: String?,
        cword: String?
    ): Mapping? {
        var munit: Mapping? = null
        if (pword != null && !pword.trim { it <= ' ' }.isEmpty()) {
            val cursor: Cursor?

            if (cword == null || cword.trim { it <= ' ' }.isEmpty()) {
                cursor = db.query(
                    LIME.DB_TABLE_RELATED, null,
                    FIELD_DIC_pword + " = ? AND " + FIELD_DIC_cword + " IS NULL",
                    arrayOf<String>(pword), null, null, null, null
                )
            } else {
                cursor = db.query(
                    LIME.DB_TABLE_RELATED, null,
                    FIELD_DIC_pword + " = ? AND " + FIELD_DIC_cword + " = ?",
                    arrayOf<String>(pword, cword), null, null, null, null
                )
            }

            if (cursor.moveToFirst()) {
                munit = Mapping()
                munit.setId(getCursorString(cursor, LIME.DB_RELATED_COLUMN_ID))
                munit.setPword(getCursorString(cursor, LIME.DB_RELATED_COLUMN_PWORD))
                munit.setWord(getCursorString(cursor, LIME.DB_RELATED_COLUMN_CWORD))
                munit.setBasescore(getCursorInt(cursor, LIME.DB_RELATED_COLUMN_BASESCORE))
                munit.setScore(getCursorInt(cursor, LIME.DB_RELATED_COLUMN_USERSCORE))
                munit.setRelatedPhraseRecord()
            }
            cursor.close()
        }
        return munit
    }

    /**
     * Resets all IM information for the specified input method.
     * 
     * 
     * This method deletes all records from the im table for the given IM code.
     * Used when reloading an input method to clear old configuration.
     * 
     * @param im The IM code to reset (e.g., LIME.DB_TABLE_PHONETIC, LIME.DB_TABLE_DAYI, "custom")
     */
    @Synchronized
    fun resetImConfig(im: String?) {
        //Jeremy '12,5,1
        if (checkDBConnection()) return
        //String removeString = "DELETE FROM im WHERE code='" + im + "'";
        //db.execSQL(removeString);
        // Define the WHERE clause with a placeholder
        val selection = "code = ?"
        // Define the arguments for the placeholder
        val selectionArgs = arrayOf<String?>(im)
        // Execute the delete operation safely
        deleteRecord(LIME.DB_TABLE_IM, selection, selectionArgs)
    }

    /**
     * Gets IM information for a specific field.
     * 
     * 
     * Retrieves configuration information stored in the imCode table for the
     * specified input method and field. Common fields include:
     * 
     *  * name - Display name of the IM
     *  * source - Source filename
     *  * amount - Number of records
     *  * import - Import date
     *  * selkey - Selection keys
     *  * endkey - End keys
     *  * imkeys - Key mapping string
     *  * imkeynames - Key name mapping string
     *  * keyboard - Keyboard code
     * 
     * 
     * @param imCode The IM code (e.g., LIME.DB_TABLE_PHONETIC, LIME.DB_TABLE_DAYI)
     * @param field The field name to retrieve
     * @return The field value, or empty string if not found or database error
     */
    open fun getImConfig(imCode: String, field: String?): String {
        //Jeremy '12,5,1 !checkDBConnection() when db is restoring or replaced.
        if (checkDBConnection()) return ""

        var imConfig = ""
        try {
            //String value = "";
            val selectString =
                "SELECT * FROM im WHERE code='" + imCode + "' AND title='" + field + "'"

            val cursor: Cursor = db!!.rawQuery(selectString, null)

            if (cursor != null) {
                if (cursor.getCount() > 0) {
                    cursor.moveToFirst()
                    //int descCol = cursor.getColumnIndex(LIME.DB_IM_COLUMN_DESC);
                    imConfig = getCursorString(cursor, LIME.DB_IM_COLUMN_DESC)
                }
                cursor.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in database operation", e)
        }
        return imConfig
    }

    /**
     * Removes a specific IM information field.
     * 
     * 
     * Deletes the specified field record from the imCode table for the given IM.
     * 
     * @param imCode The IM code
     * @param field The field name to remove
     */
    @Synchronized
    fun removeImConfig(imCode: String?, field: String?) {
        if (DEBUG) Log.i(TAG, "removeImConfig()")
        if (checkDBConnection()) return
        //Use parameterized query to prevent SQL injection
        deleteRecord(
            LIME.DB_TABLE_IM,
            LIME.DB_IM_COLUMN_CODE + " = ? AND " + LIME.DB_IM_COLUMN_TITLE + " = ?",
            arrayOf<String?>(imCode, field)
        )
    }


    /**
     * Sets IM information for a specific field.
     * 
     * 
     * Stores or updates configuration information in the imCode table. If the
     * field already exists, it is removed and reinserted with the new value.
     * 
     * @param imCode The IM code (e.g., LIME.DB_TABLE_PHONETIC, LIME.DB_TABLE_DAYI)
     * @param field The field name to set
     * @param value The value to store
     */
    @Synchronized
    open fun setImConfig(imCode: String?, field: String?, value: String?) {
        //Jeremy '12,4,17 !checkDBConnection() when db is restoring or replaced.
        if (checkDBConnection()) return

        val cv = ContentValues()
        cv.put("code", imCode)
        cv.put("title", field)
        cv.put(LIME.DB_IM_COLUMN_DESC, value)

        // remove existing record first, and then insert new value back
        removeImConfig(imCode, field)
        addRecord(LIME.DB_TABLE_IM, cv)
    }


    /**
     * Gets keyboard object information for a specific keyboard code.
     * 
     * 
     * Retrieves keyboard configuration including layout definitions for
     * IM mode, English mode, symbol mode, etc. Special handling for "wb"
     * and "hs" keyboards which have hardcoded configurations.
     * 
     * @param keyboard The keyboard code (e.g., "lime", "limenum", "wb", "hs")
     * @return KeyboardObj with keyboard information, or null if not found or database error
     */
    fun getKeyboardConfig(keyboard: String?): Keyboard? {
        //Jeremy '12,5,1 !checkDBConnection() when db is restoring or replaced.

        if (checkDBConnection()) return null

        if (keyboard == null || keyboard.isEmpty()) return null
        var kConfig: Keyboard? = null

        if (keyboard != "wb" && keyboard != "hs") {
            try {
                val cursor = queryWithPagination(
                    LIME.DB_TABLE_KEYBOARD, FIELD_CODE + " = ?",
                    arrayOf<String?>(keyboard), null, 0, 0
                )
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        kConfig = Keyboard()
                        kConfig.code = getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_CODE)
                        kConfig.name = getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_NAME)
                        kConfig.desc = getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_DESC)
                        kConfig.type = getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_TYPE)
                        kConfig.image = getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_IMAGE)
                        kConfig.imkb = getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_IMKB)
                        kConfig.imshiftkb =
                            getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_IMSHIFTKB)
                        kConfig.engkb = getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_ENGKB)
                        kConfig.engshiftkb =
                            getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_ENGSHIFTKB)
                        kConfig.symbolkb = getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_SYMBOLKB)
                        kConfig.symbolshiftkb =
                            getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_SYMBOLSHIFTKB)
                        kConfig.defaultkb =
                            getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_DEFAULTKB)
                        kConfig.defaultshiftkb =
                            getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_DEFAULTSHIFTKB)
                        kConfig.extendedkb =
                            getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_EXTENDEDKB)
                        kConfig.extendedshiftkb =
                            getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_EXTENDEDSHIFTKB)
                    }

                    cursor.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in database operation", e)
            }
        } else if (keyboard == "wb") {
            //TODO: upgrade db to include these new keyboard info in keyboard table
            kConfig = Keyboard()
            kConfig.code = "wb"
            kConfig.name = "筆順五碼"
            kConfig.desc = "筆順五碼輸入法鍵盤"
            kConfig.type = "phone"
            kConfig.image = "wb_keyboard_preview"
            kConfig.imkb = "lime_wb"
            kConfig.imshiftkb = "lime_wb"
            kConfig.engkb = "lime_abc"
            kConfig.engshiftkb = "lime_abc_shift"
            kConfig.symbolkb = "symbols"
            kConfig.symbolshiftkb = "symbols_shift"
        } else {
            kConfig = Keyboard()
            kConfig.code = "hs"
            kConfig.name = "華象直覺"
            kConfig.desc = "華象直覺輸入法鍵盤"
            kConfig.type = "phone"
            kConfig.image = "hs_keyboard_preview"
            kConfig.imkb = "lime_hs"
            kConfig.imshiftkb = "lime_hs_shift"
            kConfig.engkb = "lime_abc"
            kConfig.engshiftkb = "lime_abc_shift"
            kConfig.symbolkb = "symbols"
            kConfig.symbolshiftkb = "symbols_shift"
        }

        return kConfig
    }

    /**
     * Gets a specific field value from keyboard information.
     * 
     * @param keyboardCode The keyboard code
     * @param field The field name to retrieve (e.g., "name", LIME.DB_IM_COLUMN_DESC, "imkb")
     * @return The field value, or null if not found or database error
     */
    fun getKeyboardInfo(keyboardCode: String?, field: String?): String? {
        if (DEBUG) Log.i(TAG, "getKeyboardInfo()")
        if (checkDBConnection()) return null

        var info: String? = null

        val cursor: Cursor = db!!.query(
            LIME.DB_TABLE_KEYBOARD, null, FIELD_CODE + " = '" + keyboardCode + "'",
            null, null, null, null, null
        )
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                info = getCursorString(cursor, field)
            }
            cursor.close()
        }
        if (DEBUG) Log.i(TAG, "getKeyboardInfo() info = " + info)

        return info
    }

    open val keyboardConfigList: MutableList<Keyboard?>?
        /**
         * Gets a list of all available keyboards.
         * 
         * 
         * Retrieves all keyboard configurations from the database, sorted by name.
         * 
         * @return List of Keyboard objects, or null if database error
         */
        get() {
            //Jeremy '12,5,1 !checkDBConnection() when db is restoring or replaced.

            if (checkDBConnection()) return null


            val result: MutableList<Keyboard?> =
                LinkedList<Keyboard?>()
            try {
                val cursor: Cursor = db!!.query(
                    LIME.DB_TABLE_KEYBOARD,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "name ASC",
                    null
                )
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        do {
                            val kConfig =
                                Keyboard()
                            kConfig.code = getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_CODE)
                            kConfig.name = getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_NAME)
                            kConfig.desc = getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_DESC)
                            kConfig.type = getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_TYPE)
                            kConfig.image = getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_IMAGE)
                            kConfig.imkb = getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_IMKB)
                            kConfig.imshiftkb =
                                getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_IMSHIFTKB)
                            kConfig.engkb = getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_ENGKB)
                            kConfig.engshiftkb =
                                getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_ENGSHIFTKB)
                            kConfig.symbolkb =
                                getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_SYMBOLKB)
                            kConfig.symbolshiftkb =
                                getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_SYMBOLSHIFTKB)
                            kConfig.defaultkb =
                                getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_DEFAULTKB)
                            kConfig.defaultshiftkb =
                                getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_DEFAULTSHIFTKB)
                            kConfig.extendedkb =
                                getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_EXTENDEDKB)
                            kConfig.extendedshiftkb =
                                getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_EXTENDEDSHIFTKB)
                            result.add(kConfig)
                        } while (cursor.moveToNext())
                    }

                    cursor.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in database operation", e)
            }
            return result
        }

    /**
     * Sets the keyboardCode assignment for an input method.
     * 
     * 
     * Stores the keyboardCode configuration in the imCode table, associating a
     * keyboardCode code with the IM. This determines which keyboardCode layout
     * is used when the IM is active.
     * 
     * @param imCode The IM code
     * @param desc The keyboardCode description/name
     * @param keyboardCode The keyboardCode code
     */
    @Synchronized
    open fun setIMConfigKeyboard(imCode: String?, desc: String?, keyboardCode: String?) {
        if (DEBUG) Log.i(
            TAG,
            "setIMKeyboard() imCode=" + imCode + " desc= " + desc + " keyboardCode= " + keyboardCode
        )
        if (checkDBConnection()) return

        val cv = ContentValues()
        cv.put(LIME.DB_IM_COLUMN_CODE, imCode)
        cv.put(LIME.DB_IM_COLUMN_TITLE, LIME.DB_KEYBOARD)
        cv.put(LIME.DB_IM_COLUMN_DESC, desc)
        cv.put(LIME.DB_IM_COLUMN_KEYBOARD, keyboardCode)

        removeImConfig(imCode, LIME.DB_KEYBOARD)

        //db.insert(LIME.DB_TABLE_IM, null, cv);
        addRecord(LIME.DB_TABLE_IM, cv)
    }


    /**
     * Gets English word suggestions based on a prefix.
     * 
     * 
     * This method queries the dictionary table using FTS (Full-Text Search)
     * to find words that start with the given prefix. Results are limited
     * by the similar code candidates preference setting.
     * 
     * 
     * Used for English prediction features in the IME.
     * 
     * @param word The word prefix to search for
     * @return List of suggested words, or null if database error
     */
    fun getEnglishSuggestions(word: String?): MutableList<String?>? {
        //Jeremy '12,5,1 checkDBConnection() when db is restoring or replaced.

        if (checkDBConnection()) return null

        // Lazy guard: make sure the scored dictionary is present/current even in a session
        // that never opened settings (mirrors emoji's checkEmojiDB()).
        refreshDictionaryDataIfNeeded()

        val result: MutableList<String?> = ArrayList<String?>()
        val prefix = if (word == null) "" else word
        val upper: String? = prefixUpperBound(prefix)
        if (prefix.isEmpty() || upper == null) return result

        try {
            val similarSize = mLIMEPref.getSimilarCodeCandidates()

            // Indexed prefix range scan (no FTS). Ranking: (score + basescore) DESC, word ASC.
            // Keeps the #103 exact-match filter (word <> prefix).
            val selectString =
                "SELECT word FROM " + DICTIONARY_TABLE +
                        " WHERE word >= ? AND word < ? AND word <> ?" +
                        " ORDER BY (score + basescore) DESC, word ASC LIMIT " + similarSize

            val cursor: Cursor = db!!.rawQuery(selectString, arrayOf<String>(prefix, upper, prefix))
            if (cursor != null) {
                if (cursor.getCount() > 0) {
                    cursor.moveToFirst()
                    do {
                        val w = getCursorString(cursor, "word")
                        if (w != null && !w.isEmpty()) {
                            result.add(w)
                        }
                    } while (cursor.moveToNext())
                }
                cursor.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting word list", e)
        }

        return result
    }

    /**
     * Converts text to emoji suggestions.
     * 
     * 
     * This method keeps the legacy public entry point while routing lookups
     * through the integrated emoji tables in the main LIME database.
     * 
     * @param source The source text to convert
     * @param emoji The emoji mode (e.g., LIME.EMOJI_EN for English emoji)
     * @return List of Mapping objects containing emoji suggestions
     */
    fun emojiConvert(source: String?, emoji: Int): MutableList<Mapping?>? {
        if (emoji == LIME.EMOJI_EN) {
            return findEmojiForCandidate(source, EmojiLocale.EN, 8)
        }
        if (emoji == LIME.EMOJI_TW) {
            return findEmojiForCandidate(source, EmojiLocale.TW, 8)
        }
        return LinkedList<Mapping?>()
    }

    fun findEmojiForCandidate(
        candidate: String?,
        locale: EmojiLocale?,
        limit: Int
    ): MutableList<Mapping?> {
        return queryEmojiFts(candidate, buildEmojiCandidateQuery(candidate), limit)
    }

    fun searchEmoji(queryText: String?, locale: EmojiLocale?, limit: Int): MutableList<Mapping?> {
        return queryEmojiFts(queryText, buildEmojiPanelSearchQuery(queryText), limit)
    }

    fun loadRecentEmoji(limit: Int): MutableList<Mapping?> {
        val output: MutableList<Mapping?> = LinkedList<Mapping?>()
        if (checkDBConnection()) {
            return output
        }
        checkEmojiDB()
        val safeLimit = if (limit > 0) limit else 32
        val sql = "SELECT d.value FROM " + EMOJI_TABLE_USER + " u " +
                "JOIN " + EMOJI_TABLE_DATA + " d ON d.value = u.value " +
                "WHERE u.last_used IS NOT NULL " +
                "ORDER BY u.last_used DESC, u.use_count DESC, d.sort_order ASC LIMIT ?"
        try {
            db!!.rawQuery(sql, arrayOf<String>(safeLimit.toString())).use { cursor ->
                while (cursor != null && cursor.moveToNext()) {
                    val word = cursor.getString(0)
                    if (word != null && !word.isEmpty()) {
                        val mapping = Mapping()
                        mapping.setCode("")
                        mapping.setWord(word)
                        mapping.setEmojiRecord()
                        output.add(mapping)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading recent emoji", e)
        }
        return output
    }

    fun loadEmojiCategoryPages(): MutableList<MutableList<String?>?> {
        val pages: MutableList<MutableList<String?>?> = ArrayList<MutableList<String?>?>()
        val groupOrder: Array<String> = arrayOf(
            "Smileys & Emotion",
            "People & Body",
            "Animals & Nature",
            "Food & Drink",
            "Travel & Places",
            "Activities",
            "Objects",
            "Symbols",
            "Flags"
        )
        if (checkDBConnection()) {
            return pages
        }
        checkEmojiDB()
        val sql = "SELECT value FROM " + EMOJI_TABLE_DATA +
                " WHERE group_name = ? ORDER BY sort_order ASC"
        for (group in groupOrder) {
            val values: MutableList<String?> = ArrayList<String?>()
            try {
                db!!.rawQuery(sql, arrayOf<String>(group!!)).use { cursor ->
                    while (cursor != null && cursor.moveToNext()) {
                        val value = cursor.getString(0)
                        if (value != null && !value.isEmpty()) {
                            values.add(value)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading emoji category: " + group, e)
            }
            pages.add(values)
        }
        return pages
    }

    private fun queryEmojiFts(
        sourceCode: String?,
        query: String,
        limit: Int
    ): MutableList<Mapping?> {
        val output: MutableList<Mapping?> = LinkedList<Mapping?>()
        if (query.isEmpty()) {
            return output
        }
        if (checkDBConnection()) {
            return output
        }
        checkEmojiDB()
        val safeLimit = if (limit > 0) limit else 8
        val sql = "SELECT d.value FROM " + EMOJI_TABLE_FTS + " f " +
                "JOIN " + EMOJI_TABLE_DATA + " d ON d.rowid = f.rowid " +
                "LEFT JOIN " + EMOJI_TABLE_USER + " u ON u.value = d.value " +
                "WHERE " + EMOJI_TABLE_FTS + " MATCH ? " +
                "ORDER BY (u.last_used IS NULL), u.last_used DESC, d.sort_order ASC LIMIT ?"
        try {
            db!!.rawQuery(sql, arrayOf<String>(query, safeLimit.toString())).use { cursor ->
                while (cursor != null && cursor.moveToNext()) {
                    val word = cursor.getString(0)
                    if (word != null && !word.isEmpty()) {
                        val mapping = Mapping()
                        mapping.setCode(if (sourceCode == null) "" else sourceCode)
                        mapping.setWord(word)
                        mapping.setEmojiRecord()
                        output.add(mapping)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying emoji FTS", e)
        }
        return output
    }

    fun recordEmojiUsage(value: String?, timestampSeconds: Long) {
        if (value == null || value.isEmpty() || checkDBConnection()) {
            return
        }
        ensureEmojiTables()
        try {
            val cv = ContentValues()
            cv.put("last_used", timestampSeconds)
            val updated: Int = db!!.update(EMOJI_TABLE_USER, cv, "value=?", arrayOf<String>(value))
            if (updated > 0) {
                db!!.execSQL(
                    "UPDATE " + EMOJI_TABLE_USER + " SET use_count = use_count + 1 WHERE value = ?",
                    arrayOf<Any>(value)
                )
                return
            }
            cv.put("value", value)
            cv.put("use_count", 1)
            db!!.insertWithOnConflict(EMOJI_TABLE_USER, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        } catch (e: Exception) {
            Log.e(TAG, "Error recording emoji usage", e)
        }
    }

    fun createEmojiTablesForTest(forceRecreate: Boolean) {
        if (checkDBConnection()) {
            return
        }
        Companion.createEmojiTables(db!!, forceRecreate)
    }

    fun replaceEmojiDataForTest(rows: MutableList<EmojiDataRow>, emojiVersion: String?) {
        if (checkDBConnection()) {
            return
        }
        ensureEmojiTables()
        db!!.beginTransaction()
        try {
            Companion.clearEmojiBaseData(db!!)
            Companion.insertEmojiRows(db!!, rows)
            Companion.rebuildEmojiFts(db!!)
            db!!.delete(LIME.DB_TABLE_IM, LIME.DB_IM_COLUMN_CODE + "=?", arrayOf<String>("emoji"))
            Companion.insertEmojiMetadata(db!!, emojiVersion, rows.size)
            db!!.execSQL("DELETE FROM " + EMOJI_TABLE_USER + " WHERE value NOT IN (SELECT value FROM " + EMOJI_TABLE_DATA + ")")
            db!!.setTransactionSuccessful()
        } finally {
            db!!.endTransaction()
        }
    }

    fun getEmojiUseCountForTest(value: String?): Int {
        if (checkDBConnection()) {
            return 0
        }
        try {
            db!!.rawQuery(
                "SELECT use_count FROM " + EMOJI_TABLE_USER + " WHERE value = ?",
                arrayOf<String?>(value)
            ).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    return cursor.getInt(0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading emoji use count", e)
        }
        return 0
    }

    private fun ensureEmojiTables() {
        Companion.createEmojiTables(db!!, false)
    }

    private fun refreshEmojiDataIfNeeded() {
        if (checkDBConnection()) {
            return
        }
        ensureEmojiTables()
        if (this.isEmojiDataCurrent) {
            return
        }

        val importFile = File(mContext.getCacheDir(), "emoji_import.db")
        try {
            mContext.getResources().openRawResource(R.raw.emoji).use { inputStream ->
                LIMEUtilities.copyRAWFile(inputStream, importFile)
                if (!tableExists(importFile.getAbsolutePath(), EMOJI_TABLE_DATA)) {
                    Log.w(
                        TAG,
                        "Bundled emoji.db does not contain emoji_data; keeping existing emoji data"
                    )
                    return
                }
                importEmojiData(importFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing emoji data", e)
        } finally {
            if (importFile.exists() && !importFile.delete()) {
                Log.w(TAG, "Failed to delete temporary emoji import database")
            }
            val legacyEmojiDb = mContext.getDatabasePath("emoji.db")
            if (legacyEmojiDb.exists() && !legacyEmojiDb.delete()) {
                Log.w(TAG, "Failed to delete legacy standalone emoji database")
            }
        }
    }

    private val isEmojiDataCurrent: Boolean
        get() {
            if (!hasEmojiDataRows()) {
                return false
            }
            try {
                db!!.rawQuery(
                    "SELECT desc FROM " + LIME.DB_TABLE_IM + " WHERE code=? AND title=?",
                    arrayOf<String>("emoji", "version")
                ).use { cursor ->
                    return cursor != null && cursor.moveToFirst() && EMOJI_DATA_VERSION == cursor.getString(
                        0
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking emoji data version", e)
                return false
            }
        }

    private fun importEmojiData(importFile: File) {
        val attachedPath: String = quoteSqlString(importFile.getAbsolutePath())
        db!!.beginTransaction()
        try {
            db!!.execSQL("ATTACH DATABASE " + attachedPath + " AS emoji_src")
            Companion.clearEmojiBaseData(db!!)
            db!!.execSQL(
                "INSERT INTO " + EMOJI_TABLE_DATA + " " +
                        "(value, cp, group_name, subgroup, sort_order, name_en, name_tw, tags_en, tags_tw, version) " +
                        "SELECT value, cp, group_name, subgroup, sort_order, name_en, name_tw, tags_en, tags_tw, version " +
                        "FROM emoji_src." + EMOJI_TABLE_DATA
            )
            Companion.rebuildEmojiFts(db!!)
            db!!.execSQL(
                "INSERT INTO " + LIME.DB_TABLE_IM + " " +
                        "(code, title, desc, keyboard, disable, selkey, endkey, spacestyle) " +
                        "SELECT code, title, desc, keyboard, disable, selkey, endkey, spacestyle " +
                        "FROM emoji_src." + LIME.DB_TABLE_IM + " WHERE code='emoji'"
            )
            db!!.execSQL("DELETE FROM " + EMOJI_TABLE_USER + " WHERE value NOT IN (SELECT value FROM " + EMOJI_TABLE_DATA + ")")
            db!!.setTransactionSuccessful()
        } finally {
            db!!.endTransaction()
            try {
                db!!.execSQL("DETACH DATABASE emoji_src")
            } catch (e: Exception) {
                Log.w(TAG, "Error detaching emoji import database", e)
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Scored English dictionary (docs/ENG_AUTO_COMPLETION.md "Scored Dictionary (Android)")
    //
    // Replaces the legacy fts3(word) dictionary with a plain indexed scored table
    // dictionary(word, basescore, score). Self-versioned via im(code='dictionary',
    // title='version') — checked on every open in ensureCurrentDatabase(), NOT via
    // lime.db user_version (which stays 104). Mirrors the emoji payload mechanism above.
    // ---------------------------------------------------------------------------------
    /**
     * Idempotent open-path dictionary check. Drops a legacy fts3 dictionary, creates the
     * scored table if missing/wrong-shape, then imports basescore from the bundled
     * R.raw.dictionary payload when the im version row is absent or stale. Gated on actual
     * schema state, never on user_version, so a restored DB that lies about its version is
     * still repaired.
     */
    private fun refreshDictionaryDataIfNeeded() {
        if (checkDBConnection()) {
            return
        }
        Companion.ensureDictionarySchema(db!!)
        if (this.isDictionaryDataCurrent) {
            return
        }

        val importFile = File(mContext.getCacheDir(), "dictionary_import.db")
        try {
            mContext.getResources().openRawResource(R.raw.dictionary).use { inputStream ->
                LIMEUtilities.copyRAWFile(inputStream, importFile)
                if (!tableExists(importFile.getAbsolutePath(), DICTIONARY_PAYLOAD_TABLE)) {
                    Log.w(
                        TAG,
                        "Bundled dictionary.db does not contain dictionary_data; keeping existing dictionary"
                    )
                    return
                }
                importDictionaryData(importFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing dictionary data", e)
        } finally {
            if (importFile.exists() && !importFile.delete()) {
                Log.w(TAG, "Failed to delete temporary dictionary import database")
            }
        }
    }

    private val isDictionaryDataCurrent: Boolean
        /** True when the dictionary has rows and the stored im version row matches the constant.  */
        get() {
            if (!Companion.hasDictionaryRows(db!!)) {
                return false
            }
            try {
                db!!.rawQuery(
                    "SELECT desc FROM " + LIME.DB_TABLE_IM + " WHERE code=? AND title=?",
                    arrayOf<String>("dictionary", "version")
                ).use { cursor ->
                    return cursor != null && cursor.moveToFirst()
                            && DICTIONARY_DATA_VERSION == cursor.getString(0)
                }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Error checking dictionary data version",
                    e
                )
                return false
            }
        }

    /**
     * Import basescore from the bundled dictionary.db payload. Upserts basescore for every
     * word while preserving any existing per-user score, then stamps the im version row.
     * Never clears existing data on a bad payload (guarded by the caller).
     */
    private fun importDictionaryData(importFile: File) {
        val attachedPath: String = quoteSqlString(importFile.getAbsolutePath())
        db!!.beginTransaction()
        try {
            db!!.execSQL("ATTACH DATABASE " + attachedPath + " AS dict_src")
            // Upsert basescore; keep score. INSERT defaults score to 0 for new words.
            db!!.execSQL(
                "INSERT INTO " + DICTIONARY_TABLE + " (word, basescore, score) " +
                        "SELECT word, basescore, 0 FROM dict_src." + DICTIONARY_PAYLOAD_TABLE + " " +
                        "WHERE true " +
                        "ON CONFLICT(word) DO UPDATE SET basescore = excluded.basescore"
            )
            db!!.execSQL(
                "INSERT OR REPLACE INTO " + LIME.DB_TABLE_IM + " (code, title, desc) " +
                        "VALUES ('dictionary', 'version', '" + DICTIONARY_DATA_VERSION + "')"
            )
            db!!.setTransactionSuccessful()
        } finally {
            db!!.endTransaction()
            try {
                db!!.execSQL("DETACH DATABASE dict_src")
            } catch (e: Exception) {
                Log.w(TAG, "Error detaching dictionary import database", e)
            }
        }
    }

    /**
     * Learn a picked English word by incrementing its score (+1). UPDATE-only: a picked word
     * is always already in the dictionary, so a missing row is a stale pick and is ignored.
     * Safe to call from any thread; guarded by checkDBConnection() like other writes.
     */
    @Synchronized
    fun recordEnglishUsage(word: String?) {
        if (word == null || word.trim { it <= ' ' }.isEmpty()) return
        if (checkDBConnection()) return
        try {
            db!!.execSQL(
                "UPDATE " + DICTIONARY_TABLE + " SET score = score + 1 WHERE word = ?",
                arrayOf<Any>(word)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error recording English usage for: " + word, e)
        }
    }

    /**
     * Converts between Traditional and Simplified Chinese.
     * 
     * 
     * This method uses the han converter database to perform conversion
     * based on the specified option. The han database is automatically
     * initialized if needed.
     * 
     * @param input The Chinese text to convert
     * @param hanOption The conversion option (see LIME constants for options)
     * @return The converted text
     */
    fun hanConvert(input: String?, hanOption: Int): String? {
        checkHanDB()
        if (input == null) return null
        return hanConverter!!.convert(input, hanOption)
    }

    /**
     * Gets the base frequency score for a word from the han converter database.
     * 
     * 
     * This score represents the frequency of the word in the han converter
     * database and is used as a basescore when loading mapping files.
     * 
     * @param input The word to get the base score for
     * @return The base score, or 0 if not found
     */
    fun getBaseScore(input: String?): Int {
        checkHanDB()
        return hanConverter!!.getBaseScore(input)
    }

    private fun checkEmojiDB() {
        refreshEmojiDataIfNeeded()
    }

    private fun checkHanDB() {
        if (hanConverter == null) {
            //Jeremy '11,9,8 update handconverdb to v2 with base score in TCSC table

            val hanDBFile = LIMEUtilities.isFileExist(
                mContext.getDatabasePath("hanconvert.db").getAbsolutePath()
            )
            if (hanDBFile != null &&
                !hanDBFile.delete()
            ) Log.w(TAG, "hanconvert.db file delete failed")
            var hanDBV2File = LIMEUtilities.isFileNotExist(
                mContext.getDatabasePath("hanconvertv2.db").getAbsolutePath()
            )

            if (DEBUG) Log.i(
                TAG, "LimeDB: checkHanDB(): hanDBV2Filepaht:" +
                        mContext.getDatabasePath("hanconvertv2.db").getAbsolutePath()
            )

            if (hanDBV2File != null) LIMEUtilities.copyRAWFile(
                mContext.getResources().openRawResource(R.raw.hanconvertv2), hanDBV2File
            )
            else { // Jeremy '11,9,14 copy the db file if it's newer.
                hanDBV2File = LIMEUtilities.isFileExist(
                    mContext.getDatabasePath("hanconvertv2.db").getAbsolutePath()
                )
                if (hanDBV2File != null && mLIMEPref.getParameterLong("hanDBDate") != hanDBV2File.lastModified()) LIMEUtilities.copyRAWFile(
                    mContext.getResources().openRawResource(R.raw.hanconvertv2),
                    hanDBV2File
                )
            }

            hanConverter = LimeHanConverter(mContext)
        }
    }


    /**
     * Renames a table in the database.
     * 
     * 
     * This method performs an ALTER TABLE RENAME operation. Use with caution
     * as it permanently changes the table name.
     * 
     * @param source The current table name
     * @param target The new table name
     */
    fun renameTableName(source: String?, target: String?) {
        if (checkDBConnection()) return

        try {
            //ALTER TABLE foo RENAME TO bar
            db!!.execSQL("ALTER TABLE " + source + " RENAME TO " + target)
        } catch (e: Exception) {
            Log.e(TAG, "Error in database operation", e)
        }
    }


    /**
     * Exports records from a table to a text file.
     * 
     * 
     * This method retrieves all records from the specified table and writes them
     * to a text file. The format depends on the table type:
     * 
     *  * **Regular mapping tables:** .lime format
     * 
     *  * Header lines with IM info (@format@, @version@, @cname@, @selkey@, @endkey@, @limeendkey@, @spacestyle@) if imConfig provided
     *  * Data lines: code|word|score|basescore
     * 
     * 
     *  * **Related table ([LIME.DB_TABLE_RELATED]):** .related format
     * 
     *  * Data lines: pword|cword|basescore|userscore
     *  * Legacy format (backward compatible): pword+cword|basescore|userscore
     * 
     * 
     * 
     * 
     * 
     * The file is written in UTF-8 encoding. If the target file exists, it will be deleted first.
     * 
     * @param table The table name to export (must be valid, use [LIME.DB_TABLE_RELATED] for related phrases)
     * @param targetFile The target file to write to
     * @param imConfig List of Im objects containing IM configuration info (can be null, only used for regular tables)
     * @return true if export successful, false otherwise
     */
    fun exportTxtTable(
        table: String,
        targetFile: File?,
        imConfig: MutableList<ImConfig>?,
        progressListener: LIMEProgressListener?
    ): Boolean {
        if (checkDBConnection()) return false
        if (targetFile == null) {
            Log.e(TAG, "exportTxtTable(): targetFile is null")
            return false
        }


        // Check if exporting related table
        val isRelatedTable = LIME.DB_TABLE_RELATED == table


        // For regular tables, validate table name
        if (!isRelatedTable && !isValidTableName(table)) {
            Log.e(TAG, "exportTxtTable(): Invalid table name: " + table)
            return false
        }


        // Wait for any existing export thread to finish
        if (exportThread != null) {
            while (exportThread!!.isAlive()) {
                Log.d(TAG, "exportTxtTable(): waiting for last export thread to finish...")
                SystemClock.sleep(SLEEP_DELAY_100_MS.toLong())
            }
            exportThread = null
        }


        // Reset progress
        progressPercentageDone = 0
        progressStatus = "Preparing export..."
        val exportSuccess = booleanArrayOf(false)


        // Create export thread
        exportThread = object : Thread() {
            override fun run() {
                try {
                    // Delete existing file if it exists
                    if (targetFile.exists() && !targetFile.delete()) {
                        Log.e(TAG, "exportTxtTable(): Error deleting existing file")
                        if (progressListener != null) {
                            progressListener.onError(-1, "Error deleting existing file")
                        }
                        return
                    }


                    // Write to file
                    val writer: Writer =
                        OutputStreamWriter(FileOutputStream(targetFile), StandardCharsets.UTF_8)

                    BufferedWriter(writer).use { fout ->
                        progressStatus = mContext.getString(R.string.l3_database_exporting)
                        if (isRelatedTable) {
                            // Export related table format: pword|cword|basescore|userscore
                            val relatedList = getRelated(null, 0, 0)
                            if (relatedList.isEmpty()) {
                                Log.w(TAG, "exportTxtTable(): No related records to export")
                                if (progressListener != null) {
                                    progressListener.onError(-1, "No related records to export")
                                }
                                return
                            }

                            val totalRecords = relatedList.size
                            var processedRecords = 0
                            var useEscapedFormat = false
                            for (w in relatedList) {
                                if (needsEscapedField(w.getPword(), "|", false)
                                    || needsEscapedField(w.getCword(), "|", false)
                                ) {
                                    useEscapedFormat = true
                                    break
                                }
                            }
                            if (useEscapedFormat) {
                                fout.write("@format@|lime-text-v2")
                                fout.newLine()
                            }
                            fout.write("%chardef begin")
                            fout.newLine()

                            // Write records
                            for (w in relatedList) {
                                if (threadAborted) break


                                // Skip records with null or empty pword/cword to match import validation
                                // Import requires both pword and cword to be non-empty (see importTxtTable line 3488)
                                if (w.getPword() == null || w.getCword() == null ||
                                    w.getPword()!!.isEmpty() || w.getCword()!!.isEmpty()
                                ) {
                                    Log.w(
                                        TAG,
                                        "Skipped record with pWord =" + w.getPword() + ", cWord= " + w.getCword() + ", base score= " + w.getBasescore() + ", user score= " + w.getUserscore() + "."
                                    )
                                    continue
                                }
                                val pword = if (useEscapedFormat) escapeField(
                                    w.getPword(),
                                    "|"
                                ) else w.getPword()
                                val cword = if (useEscapedFormat) escapeField(
                                    w.getCword(),
                                    "|"
                                ) else w.getCword()
                                val s =
                                    pword + "|" + cword + "|" + w.getBasescore() + "|" + w.getUserscore()
                                fout.write(s)
                                fout.newLine()

                                processedRecords++
                                progressPercentageDone =
                                    (processedRecords.toFloat() / totalRecords.toFloat() * 100).toInt()
                                progressStatus =
                                    mContext.getString(R.string.l3_database_exporting_records) + progressPercentageDone + "%"
                            }
                            fout.write("%chardef end")
                            fout.newLine()
                        } else {
                            // Export regular table format: code|word|score|basescore
                            val records = getRecordList(table, null, false, 0, 0)
                            if (records.isEmpty()) {
                                Log.w(TAG, "exportTxtTable(): No records to export")
                                if (progressListener != null) {
                                    progressListener.onError(-1, "No records to export")
                                }
                                return
                            }

                            val totalRecords = records.size
                            var processedRecords = 0
                            var useEscapedFormat = false
                            for (w in records) {
                                if (needsEscapedField(w.getCode(), "|", true)
                                    || needsEscapedField(w.getWord(), "|", false)
                                ) {
                                    useEscapedFormat = true
                                    break
                                }
                            }

                            // Write IM info headers if provided
                            if (imConfig != null && !imConfig.isEmpty()) {
                                var version: String? = ""
                                var name: String? = ""
                                var selkey: String? = ""
                                var endkey: String? = ""
                                var limeendkey: String? = ""
                                var spacestyle: String? = ""
                                var imkeys: String? = ""
                                var imkeynames: String? = ""
                                for (i in imConfig) {
                                    if (threadAborted) break

                                    if ("version" == i.title) version = i.desc
                                    else if (LIME.IM_FULL_NAME == i.title || "name" == i.title) name =
                                        i.desc
                                    else if (LIME.IM_SELKEY == i.title) selkey = i.desc
                                    else if (LIME.IM_ENDKEY == i.title) endkey = i.desc
                                    else if (LIME.IM_LIME_ENDKEY == i.title) limeendkey = i.desc
                                    else if (LIME.IM_SPACESTYLE == i.title) spacestyle = i.desc
                                    else if ("imkeys" == i.title) imkeys = i.desc
                                    else if ("imkeynames" == i.title) imkeynames = i.desc
                                }
                                if (needsEscapedField(version, "|", false)
                                    || needsEscapedField(name, "|", false)
                                    || needsEscapedField(selkey, "|", false)
                                    || needsEscapedField(endkey, "|", false)
                                    || needsEscapedField(limeendkey, "|", false)
                                    || needsEscapedField(spacestyle, "|", false)
                                    || needsEscapedField(imkeys, "|", false)
                                    || needsEscapedField(imkeynames, "|", false)
                                ) {
                                    useEscapedFormat = true
                                }
                                if (useEscapedFormat) {
                                    fout.write("@format@|lime-text-v2")
                                    fout.newLine()
                                }
                                val exportVersion: String =
                                    if (!version.isNullOrEmpty()) version else name.orEmpty()
                                if (!exportVersion.isEmpty()) {
                                    fout.write(
                                        "@version@|" + (if (useEscapedFormat) escapeField(
                                            exportVersion,
                                            "|"
                                        ) else exportVersion)
                                    )
                                    fout.newLine()
                                }
                                if (!name!!.isEmpty()) {
                                    fout.write(
                                        "@cname@|" + (if (useEscapedFormat) escapeField(
                                            name,
                                            "|"
                                        ) else name)
                                    )
                                    fout.newLine()
                                }
                                if (!selkey!!.isEmpty()) {
                                    fout.write(
                                        "@selkey@|" + (if (useEscapedFormat) escapeField(
                                            selkey,
                                            "|"
                                        ) else selkey)
                                    )
                                    fout.newLine()
                                }
                                if (!endkey!!.isEmpty()) {
                                    fout.write(
                                        "@endkey@|" + (if (useEscapedFormat) escapeField(
                                            endkey,
                                            "|"
                                        ) else endkey)
                                    )
                                    fout.newLine()
                                }
                                if (!limeendkey!!.isEmpty()) {
                                    fout.write(
                                        "@limeendkey@|" + (if (useEscapedFormat) escapeField(
                                            limeendkey,
                                            "|"
                                        ) else limeendkey)
                                    )
                                    fout.newLine()
                                }
                                if (!spacestyle!!.isEmpty()) {
                                    fout.write(
                                        "@spacestyle@|" + (if (useEscapedFormat) escapeField(
                                            spacestyle,
                                            "|"
                                        ) else spacestyle)
                                    )
                                    fout.newLine()
                                }
                                if (!imkeys!!.isEmpty()) {
                                    fout.write(
                                        "@imkeys@|" + (if (useEscapedFormat) escapeField(
                                            imkeys,
                                            "|"
                                        ) else imkeys)
                                    )
                                    fout.newLine()
                                }
                                if (!imkeynames!!.isEmpty()) {
                                    fout.write(
                                        "@imkeynames@|" + (if (useEscapedFormat) escapeField(
                                            imkeynames,
                                            "|"
                                        ) else imkeynames)
                                    )
                                    fout.newLine()
                                }
                            } else if (useEscapedFormat) {
                                fout.write("@format@|lime-text-v2")
                                fout.newLine()
                            }
                            fout.write("%chardef begin")
                            fout.newLine()

                            // Write records
                            for (w in records) {
                                if (threadAborted) break

                                if (w.getWord() == null || w.getWord() == "null") {
                                    Log.w(
                                        TAG,
                                        "Skipped record with code =" + w.getCode() + ", word= " + w.getWord() + ", base score= " + w.getBasescore() + ", user score= " + w.getScore() + "."
                                    )

                                    continue
                                }
                                val code = if (useEscapedFormat) escapeField(
                                    w.getCode(),
                                    "|"
                                ) else w.getCode()
                                val word = if (useEscapedFormat) escapeField(
                                    w.getWord(),
                                    "|"
                                ) else w.getWord()
                                val s =
                                    code + "|" + word + "|" + w.getScore() + "|" + w.getBasescore()
                                fout.write(s)
                                fout.newLine()

                                processedRecords++
                                progressPercentageDone =
                                    (processedRecords.toFloat() / totalRecords.toFloat() * 100).toInt()
                                progressStatus =
                                    mContext.getString(R.string.l3_database_exporting_records) + processedRecords + "/" + totalRecords
                            }
                            fout.write("%chardef end")
                            fout.newLine()
                        }
                        if (!threadAborted) {
                            exportSuccess[0] = true
                            progressPercentageDone = 100
                            progressStatus =
                                mContext.getString(R.string.l3_database_exporting_complete)
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "exportTxtTable(): Error writing to file", e)
                    if (progressListener != null) {
                        progressListener.onError(-1, "Error writing to file")
                    }
                }
            }
        }


        // Create progress reporting thread
        val reportProgressThread: Thread = object : Thread() {
            override fun run() {
                if (progressListener == null) {
                    // If no progress listener, just wait for export thread to complete
                    while (exportThread!!.isAlive()) {
                        SystemClock.sleep(SLEEP_DELAY_100_MS.toLong())
                    }
                    return
                }

                val interval = progressListener.progressInterval()
                while (exportThread!!.isAlive()) {
                    SystemClock.sleep(interval)
                    progressListener.onProgress(progressPercentageDone.toLong(), 0, progressStatus)
                }
                progressPercentageDone = 100
                progressListener.onPostExecute(exportSuccess[0], null, 0)
            }
        }


        // Start both threads
        threadAborted = false
        exportThread!!.start()
        reportProgressThread.start()


        // Wait for threads to complete
        try {
            exportThread!!.join()
            reportProgressThread.join()
        } catch (e: InterruptedException) {
            Log.e(TAG, "exportTxtTable(): Thread interrupted", e)
            return false
        }

        return exportSuccess[0]
    }

    // Keep backward compatibility with old method signature
    fun exportTxtTable(
        table: String,
        targetFile: File?,
        imConfigInfo: MutableList<ImConfig>?
    ): Boolean {
        return exportTxtTable(table, targetFile, imConfigInfo, null)
    }

    /**
     * Inserts a record into the specified table using parameterized ContentValues.
     * 
     * 
     * This method uses parameterized input to prevent SQL injection.
     * 
     * @param table The table name to insert into (must pass isValidTableName)
     * @param values The ContentValues representing column names and values
     * @return The row ID of the newly inserted row, or -1 if error
     */
    open fun addRecord(table: String, values: ContentValues?): Long {
        if (checkDBConnection()) return -1
        if (!isValidTableName(table)) {
            Log.e(TAG, "addRecord(): Invalid table name: " + table)
            return -1
        }
        try {
            return db!!.insert(table, null, values)
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting record into table: " + table, e)
            return -1
        }
    }


    /**
     * Deletes records from a table using parameterized queries.
     * 
     * 
     * This method uses parameterized queries to prevent SQL injection.
     * The table name is validated against a whitelist.
     * 
     * @param table The table name (must be valid according to [.isValidTableName])
     * @param whereClause The WHERE clause (e.g., "id = ?") with ? placeholders
     * @param whereArgs The arguments to replace ? placeholders in whereClause
     * @return The number of rows deleted, or -1 if error
     */
    open fun deleteRecord(table: String, whereClause: String?, whereArgs: Array<String?>?): Int {
        if (checkDBConnection()) return -1

        if (!isValidTableName(table)) {
            Log.e(TAG, "deleteRecord(): Invalid table name: " + table)
            return -1
        }

        try {
            return db!!.delete(table, whereClause, whereArgs)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting record from table: " + table, e)
            return -1
        }
    }


    /**
     * Updates records in a table using parameterized queries.
     * 
     * 
     * This method uses parameterized queries to prevent SQL injection.
     * The table name is validated against a whitelist.
     * 
     * @param table The table name (must be valid according to [.isValidTableName])
     * @param values The values to update
     * @param whereClause The WHERE clause (e.g., "id = ?") with ? placeholders
     * @param whereArgs The arguments to replace ? placeholders in whereClause
     * @return The number of rows updated, or -1 if error
     */
    open fun updateRecord(
        table: String,
        values: ContentValues?,
        whereClause: String?,
        whereArgs: Array<String?>?
    ): Int {
        if (checkDBConnection()) return -1

        if (!isValidTableName(table)) {
            Log.e(TAG, "updateRecord(): Invalid table name: " + table)
            return -1
        }

        try {
            return db!!.update(table, values, whereClause, whereArgs)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating record in table: " + table, e)
            return -1
        }
    }


    /**
     * Gets IM records filtered by code and/or configEntry.
     * 
     * 
     * Retrieves IM information records matching the specified code and configEntry.
     * Either parameter can be null/empty to match all values.
     * 
     * @param code The IM code to filter by, or null/empty for all
     * @param configEntry The IM configEntry to filter by, or null/empty for all
     * @return List of Im objects, or empty list if database error
     */
    open fun getImConfigList(code: String?, configEntry: String?): MutableList<ImConfig?> {
        val result: MutableList<ImConfig?> = ArrayList<ImConfig?>()
        if (checkDBConnection()) return result

        val queryBuilder = StringBuilder()
        if (code != null && code.length > 1) {
            queryBuilder.append(LIME.DB_IM_COLUMN_CODE).append("='").append(code).append("'")
        }
        if (configEntry != null && configEntry.length > 1) {
            if (queryBuilder.length > 0) {
                queryBuilder.append(" AND ")
            }
            queryBuilder.append(" ").append(LIME.DB_IM_COLUMN_TITLE).append("='")
                .append(configEntry).append("'")
        }
        val query = if (queryBuilder.length > 0) queryBuilder.toString() else null

        val cursor: Cursor = db!!.query(
            LIME.DB_TABLE_IM,
            null, query,
            null, null, null, LIME.DB_IM_COLUMN_DESC + " ASC"
        )
        if (cursor != null) {
            cursor.moveToFirst()
            while (!cursor.isAfterLast()) {
                //result.add(ImConfig.get(cursor));
                val record = getImConfigFromCursor(cursor)
                cursor.moveToNext()
                result.add(record)
            }
            cursor.close()
        }
        appendLegacyFullNameConfigs(result, code, configEntry)
        applyFullNameFallbacks(result, configEntry)
        return result
    }

    private fun getImConfigFromCursor(cursor: Cursor): ImConfig {
        val record = ImConfig()
        record.id = getCursorInt(cursor, LIME.DB_IM_COLUMN_ID)
        record.code = getCursorString(cursor, LIME.DB_IM_COLUMN_CODE)
        record.title = getCursorString(cursor, LIME.DB_IM_COLUMN_TITLE)
        record.desc = getCursorString(cursor, LIME.DB_IM_COLUMN_DESC)
        record.keyboard = getCursorString(cursor, LIME.DB_IM_COLUMN_KEYBOARD)
        val disableStr = getCursorString(cursor, LIME.DB_IM_COLUMN_DISABLE)
        record.isDisable = disableStr.toBoolean()
        record.selkey = getCursorString(cursor, LIME.DB_IM_COLUMN_SELKEY)
        record.endkey = getCursorString(cursor, LIME.DB_IM_COLUMN_ENDKEY)
        record.spacestyle = getCursorString(cursor, LIME.DB_IM_COLUMN_SPACESTYLE)
        return record
    }

    private fun appendLegacyFullNameConfigs(
        result: MutableList<ImConfig?>,
        code: String?,
        configEntry: String?
    ) {
        if (LIME.IM_FULL_NAME != configEntry) return

        val existingCodes: MutableSet<String?> = HashSet<String?>()
        for (record in result) {
            if (record != null && record.code != null) {
                existingCodes.add(record.code)
            }
        }

        var i = 0
        while (i < LIME.IM_CODES.size && i < LIME.IM_FULL_NAMES.size) {
            val imCode = LIME.IM_CODES[i]
            if (code != null && code.length > 1 && (imCode != code)) {
                i++
                continue
            }
            if (existingCodes.contains(imCode)) {
                i++
                continue
            }
            val legacy = getFirstImConfigForCode(imCode)
            if (legacy == null) {
                i++
                continue
            }
            legacy.title = LIME.IM_FULL_NAME
            legacy.desc = LIME.IM_FULL_NAMES[i]
            result.add(legacy)
            i++
        }
    }

    private fun applyFullNameFallbacks(result: MutableList<ImConfig?>, configEntry: String?) {
        if (LIME.IM_FULL_NAME != configEntry) return
        for (record in result) {
            if (record == null) continue
            val desc = record.desc
            if (desc == null || desc.isEmpty()) {
                record.desc = defaultImFullName(record.code, record.code)
            }
        }
    }

    private fun getFirstImConfigForCode(code: String?): ImConfig? {
        val cursor: Cursor = db!!.query(
            LIME.DB_TABLE_IM, null,
            LIME.DB_IM_COLUMN_CODE + " = ?",
            arrayOf<String?>(code), null, null, LIME.DB_IM_COLUMN_ID + " ASC", "1"
        )
        if (cursor == null) return null
        try {
            if (!cursor.moveToFirst()) return null
            return getImConfigFromCursor(cursor)
        } finally {
            cursor.close()
        }
    }

    /**
     * Get records from a table with optional filtering and pagination.
     * 
     * 
     * This method supports two search modes:
     * 
     *  * searchByCode=true: Searches by code prefix (code LIKE 'query%')
     *  * searchByCode=false: Searches by word substring (word LIKE '%query%')
     * 
     * 
     * 
     * Results can be limited and paginated using maximum and offset parameters.
     * 
     * @param code The table name to query
     * @param query The search query string, or null/empty for all records
     * @param searchByCode If true, search by code; if false, search by word
     * @param maximum Maximum number of records to return (0 for no limit)
     * @param offset Offset for pagination (0 for first page)
     * @return List of Record objects, or empty list if database error
     */
    open fun getRecordList(
        code: String,
        query: String?,
        searchByCode: Boolean,
        maximum: Int,
        offset: Int
    ): MutableList<Record> {
        var query = query
        val result: MutableList<Record> = ArrayList<Record>()
        if (checkDBConnection()) return result

        // Validate table name before using in query to prevent SQL injection
        if (!isValidTableName(code)) {
            Log.e(TAG, "getRecords(): Invalid table name: " + code)
            return result
        }

        val cursor: Cursor?
        if (query != null && !query.isEmpty()) {
            if (searchByCode) {
                query =
                    LIME.DB_COLUMN_CODE + " LIKE '" + query + "%' AND ifnull(" + LIME.DB_COLUMN_WORD + ", '') <> ''"
            } else {
                query =
                    LIME.DB_COLUMN_WORD + " LIKE '%" + query + "%' AND ifnull(" + LIME.DB_COLUMN_WORD + ", '') <> ''"
            }
        } else {
            query = "ifnull(" + LIME.DB_COLUMN_WORD + ", '') <> ''"
        }

        var order: String?

        if (searchByCode) {
            order = LIME.DB_COLUMN_CODE + " ASC"
        } else {
            order = LIME.DB_COLUMN_WORD + " ASC"
        }

        if (maximum > 0) {
            order += " LIMIT " + maximum + " OFFSET " + offset
        }


        cursor = db!!.query(
            code,
            null, query,
            null, null, null, order
        )

        cursor.moveToFirst()
        while (!cursor.isAfterLast()) {
            val r = recordFromCursor(cursor)
            result.add(r)
            cursor.moveToNext()
        }
        cursor.close()

        return result
    }

    /**
     * Gets a single record by ID.
     * 
     * @param code The table name
     * @param id The record ID (_id)
     * @return Record object, or null if not found or database error
     */
    open fun getRecord(code: String, id: Long): Record? {
        if (checkDBConnection()) return null
        var record: Record? = null
        val cursor: Cursor?

        val query = LIME.DB_COLUMN_ID + " = '" + id + "' "

        cursor = db!!.query(
            code,
            null, query,
            null, null, null, null
        )

        if (cursor != null && cursor.moveToFirst()) {
            record = recordFromCursor(cursor)
        }
        if (cursor != null) {
            cursor.close()
        }
        return record
    }

    /**
     * Sets the keyboard assignment for an IM using a Keyboard object.
     * 
     * 
     * This method stores the keyboard configuration in the im table,
     * replacing any existing keyboard assignment for the IM.
     * 
     * @param imCode The IM imCode
     * @param keyboard The Keyboard object containing keyboard information
     */
    open fun setImConfigKeyboard(imCode: String?, keyboard: Keyboard) {
        if (checkDBConnection()) return

        //removeImConfig(imCode, LIME.IM_KEYBOARD);
        setIMConfigKeyboard(imCode, keyboard.desc, keyboard.code)

        // Use ContentValues instead of raw SQL for better security
//        ContentValues cv = new ContentValues();
//        cv.put(LIME.DB_IM_COLUMN_CODE, imCode);
//        cv.put(LIME.DB_IM_COLUMN_TITLE, LIME.IM_KEYBOARD);
//        cv.put(LIME.DB_IM_COLUMN_DESC, keyboard.getDesc());
//        cv.put(LIME.DB_IM_COLUMN_KEYBOARD, keyboard.getCode());
//        cv.put(LIME.DB_IM_COLUMN_DISABLE, String.valueOf(false));
//        addRecord(LIME.DB_TABLE_IM, cv);
    }


    /**
     * Gets related phrase records for a given parent word.
     * 
     * 
     * This method searches for related phrases where the parent word matches
     * the given pword. If pword length > 1, it also searches for phrases matching
     * the last character. Results are sorted by userscore and basescore descending.
     * 
     * 
     * Supports pagination through maximum and offset parameters.
     * 
     * @param pword The parent word to search for
     * @param maximum Maximum number of records to return (0 for no limit)
     * @param offset Offset for pagination (0 for first page)
     * @return List of Related objects, or empty list if database error
     */
    open fun getRelated(pword: String?, maximum: Int, offset: Int): MutableList<Related> {
        var pword = pword
        val result: MutableList<Related> = ArrayList<Related>()
        if (checkDBConnection()) return result

        val cursor: Cursor?

        val queryBuilder = StringBuilder()
        val queryArgs: MutableList<String?> = ArrayList<String?>()
        var cword = ""

        if (pword != null && pword.codePointCount(0, pword.length) > 1) {
            val relatedWords: Array<String> = splitLeadingCodePoint(pword)
            pword = relatedWords[0]
            cword = relatedWords[1]
        }
        if (pword != null && !pword.isEmpty()) {
            queryBuilder.append(LIME.DB_RELATED_COLUMN_PWORD).append(" = ? AND ")
            queryArgs.add(pword)
        }
        if (!cword.isEmpty()) {
            queryBuilder.append(LIME.DB_RELATED_COLUMN_CWORD).append(" LIKE ? AND ")
            queryArgs.add(cword + "%")
        }

        queryBuilder.append("ifnull(").append(LIME.DB_RELATED_COLUMN_CWORD).append(", '') <> ''")
        val query = queryBuilder.toString()

        val orderBuilder = StringBuilder(LIME.DB_RELATED_COLUMN_USERSCORE)
        orderBuilder.append(" desc,").append(LIME.DB_RELATED_COLUMN_BASESCORE).append(" desc")

        if (maximum > 0) {
            orderBuilder.append(" LIMIT ").append(maximum).append(" OFFSET ").append(offset)
        }
        val order = orderBuilder.toString()

        cursor = db!!.query(
            LIME.DB_TABLE_RELATED,
            null, query,
            if (queryArgs.isEmpty()) null else queryArgs.toTypedArray<String?>(),
            null, null, order
        )

        cursor.moveToFirst()
        while (!cursor.isAfterLast()) {
            //Related r = Related.get(cursor);
            val record = Related()
            // Use helper methods to safely get column values (validates column index >= 0)
            record.setId(getCursorInt(cursor, LIME.DB_RELATED_COLUMN_ID))
            record.setPword(getCursorString(cursor, LIME.DB_RELATED_COLUMN_PWORD))
            record.setCword(getCursorString(cursor, LIME.DB_RELATED_COLUMN_CWORD))
            record.setUserscore(getCursorInt(cursor, LIME.DB_RELATED_COLUMN_USERSCORE))
            record.setBasescore(getCursorInt(cursor, LIME.DB_RELATED_COLUMN_BASESCORE))
            result.add(record)
            cursor.moveToNext()
        }
        cursor.close()

        return result
    }


    //    /**
    //     * Gets a single related phrase record by ID.
    //     *
    //     * @param id The record ID (_id)
    //     * @return Related object, or null if not found or database error
    //     */
    //    public Related getRelated(long id) {
    //        if (checkDBConnection()) return null;
    //        Related record = null;
    //        Cursor cursor;
    //
    //        String query = LIME.DB_RELATED_COLUMN_ID + " = '" + id + "' ";
    //
    //        cursor = db.query(LIME.DB_TABLE_RELATED,
    //                null, query,
    //                null, null, null, null);
    //
    //        if (cursor != null && cursor.moveToFirst()) {
    //            //w = Related.get(cursor);
    //            record = new Related();
    //            // Use helper methods to safely get column values (validates column index >= 0)
    //            record.setId(getCursorInt(cursor, LIME.DB_RELATED_COLUMN_ID));
    //            record.setPword(getCursorString(cursor, LIME.DB_RELATED_COLUMN_PWORD));
    //            record.setCword(getCursorString(cursor, LIME.DB_RELATED_COLUMN_CWORD));
    //            record.setUserscore(getCursorInt(cursor, LIME.DB_RELATED_COLUMN_USERSCORE));
    //            record.setBasescore(getCursorInt(cursor, LIME.DB_RELATED_COLUMN_BASESCORE));
    //        }
    //        if (cursor != null) {
    //            cursor.close();
    //        }
    //
    //        return record;
    //    }
    /**
     * Holds the database connection to prevent concurrent access during maintenance.
     * 
     * 
     * When the database is on hold, queries will show a toast message and wait
     * until the hold is released. This prevents corruption during operations like
     * file loading or backup/restore.
     * 
     * 
     * Must be paired with [.unHoldDBConnection] to release the hold.
     */
    fun holdDBConnection() {
        isDatabaseOnHold = true
    }

    /**
     * Releases the database connection hold.
     * 
     * 
     * Allows normal database operations to resume after maintenance is complete.
     */
    fun unHoldDBConnection() {
        isDatabaseOnHold = false
    }


    /**
     * Gets all records from a backup table.
     * 
     * 
     * This method retrieves all records from a backup table (typically named
     * "{tableName}_user"). The backup table name must end with "_user" and the base
     * table name must be valid.
     * 
     * 
     * This method is used when restoring user preferences from backup tables
     * during database import operations.
     * 
     * @param backupTableName The backup table name (must end with "_user", e.g., "cj_user")
     * @return Cursor with all records from the backup table, or null if invalid or error
     */
    fun getBackupTableRecords(backupTableName: String?): Cursor? {
        if (checkDBConnection()) return null


        // Validate backup table name format
        if (backupTableName == null || !backupTableName.endsWith("_user")) {
            Log.e(
                TAG,
                "getBackupTableRecords(): Invalid backup table name format: " + backupTableName
            )
            return null
        }


        // Extract base table name (remove "_user" suffix)
        val baseTableName = backupTableName.substring(0, backupTableName.length - 5)


        // Validate base table name
        if (!isValidTableName(baseTableName)) {
            Log.e(TAG, "getBackupTableRecords(): Invalid base table name: " + baseTableName)
            return null
        }

        try {
            // backupTableName is validated, safe to use in query
            return db!!.rawQuery("SELECT * FROM " + backupTableName, null)
        } catch (e: Exception) {
            Log.e(TAG, "getBackupTableRecords(): Error querying backup table", e)
            return null
        }
    }

    /**
     * Restores user-learned records from a backup table to the main table.
     * 
     * 
     * This method retrieves all records from a backup table (typically named
     * "{tableName}_user") and restores them to the main mapping table by calling
     * [.addOrUpdateMappingRecord] for each record.
     * 
     * 
     * This method is used when restoring user preferences from backup tables
     * during database import operations. The backup table must exist and contain
     * records for the restoration to proceed.
     * 
     * 
     * The method performs the following operations:
     * 
     *  * Validates the table name
     *  * Constructs the backup table name (table + "_user")
     *  * Counts records in the backup table
     *  * If records exist, retrieves all records from the backup table
     *  * Restores each record to the main table using addOrUpdateMappingRecord
     * 
     * 
     * @param table The base table name to restore records to (e.g., "cj", "phonetic")
     * @return The number of records restored, or 0 if no records to restore or error
     */
    open fun restoreUserRecords(table: String?): Int {
        if (DEBUG) Log.i(TAG, "restoreUserRecords")
        if (checkDBConnection()) return 0

        if (table == null || table.isEmpty()) {
            Log.e(TAG, "restoreUserRecords(): Table name cannot be null or empty")
            return 0
        }

        if (!isValidTableName(table)) {
            Log.e(TAG, "restoreUserRecords(): Invalid table name: " + table)
            return 0
        }

        val backupTableName = table + "_user"

        try {
            // Check if backup table exists before counting records
            val tableCheck: Cursor = db!!.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf<String>(backupTableName)
            )
            val tableExists = tableCheck != null && tableCheck.getCount() > 0
            if (tableCheck != null) {
                tableCheck.close()
            }

            if (!tableExists) {
                if (DEBUG) {
                    Log.i(
                        TAG,
                        "restoreUserRecords(): Backup table does not exist: " + backupTableName
                    )
                }
                return 0
            }


            // Count records in backup table
            val userRecordsCount = countRecords(backupTableName, null, null)
            if (userRecordsCount == 0) {
                if (DEBUG) {
                    Log.i(
                        TAG,
                        "restoreUserRecords(): No records to restore from " + backupTableName
                    )
                }
                return 0
            }


            // Get all records from backup table
            val cursorbackup = getBackupTableRecords(backupTableName)
            if (cursorbackup == null) {
                Log.e(
                    TAG,
                    "restoreUserRecords(): Failed to get backup table records from " + backupTableName
                )
                return 0
            }


            // Convert cursor to list of records (cursor closed by recordListFromCursor)
            val backuplist = recordListFromCursor(cursorbackup)


            if (backuplist.isEmpty()) {
                if (DEBUG) {
                    Log.i(TAG, "restoreUserRecords(): Backup list is empty")
                }
                return 0
            }


            // Restore each record
            var restoredCount = 0
            for (w in backuplist) {
                if (w != null && w.getCode() != null && w.getWord() != null) {
                    addOrUpdateMappingRecord(table, w.getCode()!!, w.getWord()!!, w.getScore())
                    restoredCount++
                }
            }

            if (DEBUG) {
                Log.i(
                    TAG,
                    "restoreUserRecords(): Restored " + restoredCount + " records from " + backupTableName + " to " + table
                )
            }

            return restoredCount
        } catch (e: Exception) {
            Log.e(
                TAG,
                "restoreUserRecords(): Error restoring user records from " + backupTableName,
                e
            )
            return 0
        }
    }


    //    /**
    //     * Builds a parameterized WHERE clause from a map of conditions.
    //     *
    //     * <p>This helper method constructs a WHERE clause with "?" placeholders
    //     * for parameterized queries, which helps prevent SQL injection.
    //     *
    //     * <p>Example:
    //     * <pre>
    //     * Map&lt;String, String&gt; conditions = new HashMap&lt;&gt;();
    //     * conditions.put("code", "abc");
    //     * conditions.put("score", "100");
    //     * Pair&lt;String, String[]&gt; result = buildWhereClause(conditions);
    //     * // result.first = "code = ? AND score = ?"
    //     * // result.second = ["abc", "100"]
    //     * </pre>
    //     *
    //     * @param conditions Map of column names to values
    //     * @return Pair containing WHERE clause string and arguments array, or null if conditions is empty
    //     */
    //    private Pair<String, String[]> buildWhereClause(java.util.Map<String, String> conditions) {
    //        if (conditions == null || conditions.isEmpty()) {
    //            return null;
    //        }
    //
    //        StringBuilder whereBuilder = new StringBuilder();
    //        List<String> whereArgs = new ArrayList<>();
    //
    //        boolean first = true;
    //        for (java.util.Map.Entry<String, String> entry : conditions.entrySet()) {
    //            if (!first) {
    //                whereBuilder.append(" AND ");
    //            }
    //            whereBuilder.append(entry.getKey()).append(" = ?");
    //            whereArgs.add(entry.getValue());
    //            first = false;
    //        }
    //
    //        return new Pair<>(whereBuilder.toString(), whereArgs.toArray(new String[0]));
    //    }
    /**
     * Executes a query with pagination support.
     * 
     * 
     * This helper method provides a consistent way to query tables with
     * WHERE clauses, ordering, and pagination (limit/offset).
     * 
     * 
     * Example:
     * <pre>
     * Cursor cursor = queryWithPagination("custom",
     * "code = ?", new String[]{"abc"},
     * "score DESC", 10, 0);
    </pre> * 
     * 
     * @param table The table name to query
     * @param whereClause Optional WHERE clause (null for all records)
     * @param whereArgs Optional WHERE arguments for parameterized queries
     * @param orderBy Optional ORDER BY clause (null for no ordering)
     * @param limit Maximum number of records to return (0 for no limit)
     * @param offset Number of records to skip (0 for no offset)
     * @return Cursor with query results, or null if error
     */
    fun queryWithPagination(
        table: String, whereClause: String?, whereArgs: Array<String?>?,
        orderBy: String?, limit: Int, offset: Int
    ): Cursor? {
        if (checkDBConnection()) return null

        // Validate table name
        if (!isValidTableName(table)) {
            Log.e(TAG, "queryWithPagination(): Invalid table name: " + table)
            return null
        }

        try {
            var limitClause: String? = null
            if (limit > 0) {
                limitClause = limit.toString()
                if (offset > 0) {
                    limitClause = offset.toString() + "," + limit
                }
            }

            return db!!.query(table, null, whereClause, whereArgs, null, null, orderBy, limitClause)
        } catch (e: Exception) {
            Log.e(TAG, "queryWithPagination(): Error executing query", e)
            return null
        }
    }

    /**
     * Executes a raw SQL query.
     * 
     * 
     * This method performs basic validation on SELECT queries to extract
     * and validate table names against the whitelist. However, for production
     * use, consider requiring table names as separate parameters for better
     * security.
     * 
     * 
     * **Warning:** Use with caution. While some validation is performed,
     * this method executes raw SQL which could be vulnerable to SQL injection
     * if not used carefully.
     * 
     * @param query The raw SQL query string to execute
     * @return Cursor with query results, or null if error or invalid table name
     */
    fun rawQuery(query: String?): Cursor? {
        if (checkDBConnection()) return null


        // Basic validation: check if query contains potentially dangerous patterns
        if (query != null && query.lowercase(Locale.getDefault()).contains("select")) {
            // Extract table name from SELECT query for validation
            // Simple pattern: "select * from tablename" or "SELECT * FROM tablename"
            val lowerQuery = query.lowercase(Locale.getDefault()).trim { it <= ' ' }
            if (lowerQuery.startsWith("select")) {
                // Try to extract table name (simplified - may not catch all cases)
                // For production, consider more robust parsing or requiring table name as separate parameter
                val parts = query.split("(?i)\\s+from\\s+".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                if (parts.size > 1) {
                    var tablePart: String? = parts[1].trim { it <= ' ' }.split("\\s+".toRegex())
                        .dropLastWhile { it.isEmpty() }.toTypedArray()[0].trim { it <= ' ' }
                    // Remove any trailing characters like WHERE, etc.
                    tablePart = tablePart!!.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[0]
                    if (!isValidTableName(tablePart)) {
                        Log.e(TAG, "rawQuery(): Invalid table name in query: " + tablePart)
                        return null
                    }
                }
            }
        }

        try {
            return db!!.rawQuery(query!!, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing raw query", e)
        }
        return null
    }

    //    public void execSQL(String insertsql) {
    //        if (checkDBConnection()) return;
    //        try {
    //            db.execSQL(insertsql);
    //        } catch (Exception e) {
    //            Log.w(TAG, "Ignore all possible exceptions~");
    //        }
    //    }
    /**
     * Resets all LIME settings to factory defaults.
     * 
     * 
     * This method:
     * 
     *  * Closes and deletes the main database, then restores from raw resource
     *  * Refreshes integrated emoji tables from raw resource
     *  * Closes and deletes the han converter database, then restores from raw resource
     *  * Reopens database connections
     * 
     * 
     * 
     * This is a destructive operation that will erase all user data including
     * learned mappings and related phrases.
     */
    fun restoredToDefault() {
        if (DEBUG) Log.i(TAG, "restoredToDefault")

        if (db != null) db!!.close()

        val dbFile = mContext.getDatabasePath(LIME.DATABASE_NAME)
        if (dbFile.exists() && !dbFile.delete()) Log.w(TAG, "Failed to delete database file")
        LIMEUtilities.copyRAWFile(mContext.getResources().openRawResource(R.raw.lime), dbFile)
        openDBConnection(true)
        ensureCurrentDatabase()

        if (hanConverter != null) hanConverter!!.close()

        hanConverter = null
        val hanDBFile = mContext.getDatabasePath("hanconvert.db")
        hanDBFile.deleteOnExit()
        val hanDB2File = mContext.getDatabasePath("hanconvertv2.db")
        hanDB2File.deleteOnExit()

        LIMEUtilities.copyRAWFile(
            mContext.getResources().openRawResource(R.raw.hanconvertv2),
            hanDB2File
        )
        hanConverter = LimeHanConverter(mContext)
    }

    fun isDatabaseOnHold(): Boolean = Companion.isDatabaseOnHold

    companion object {
        private const val DEBUG = false
        private const val TAG = "LimeDB"

        private fun splitLeadingCodePoint(text: String?): Array<String> {
            if (text == null || text.isEmpty()) {
                return arrayOf("", "")
            }
            val end = text.offsetByCodePoints(0, 1)
            return arrayOf<String>(text.substring(0, end), text.substring(end))
        }

        private fun codePointSubstring(
            text: String,
            beginCodePoint: Int,
            endCodePoint: Int
        ): String {
            val begin = text.offsetByCodePoints(0, beginCodePoint)
            val end = text.offsetByCodePoints(0, endCodePoint)
            return text.substring(begin, end)
        }

        private var db: SQLiteDatabase? =
            null //Jeremy '12,5,1 add static modifier. Shared db instance for dbserver and searchserver
        private const val DATABASE_VERSION = 104
        private const val EMOJI_DATA_VERSION = "17.0"

        // Scored English dictionary payload (docs/ENG_AUTO_COMPLETION.md). Self-versioned via
        // an im(code='dictionary', title='version') row — independent of DATABASE_VERSION.
        private const val DICTIONARY_DATA_VERSION = "1.0"
        private const val DICTIONARY_TABLE = "dictionary"
        private const val DICTIONARY_PAYLOAD_TABLE = "dictionary_data"
        private const val EMOJI_TABLE_DATA = "emoji_data"
        private const val EMOJI_TABLE_FTS = "emoji_fts"
        private const val EMOJI_TABLE_USER = "emoji_user"

        //Jeremy '15, 6, 1 between search clause without using related column for better sorting order.
        //private final static Boolean fuzzySearch = false;
        // hold database connection when database is in maintainable. Jeremy '15,5,23
        @get:JvmName("getDatabaseOnHoldProperty")
        var isDatabaseOnHold: Boolean = false
            /**
             * Checks if the database connection is currently on hold.
             * 
             * @return true if database is on hold (maintenance in progress), false otherwise
             */
            get() = field
            private set

        //Jeremy '11,8,5
        private const val INITIAL_RESULT_LIMIT = "15"
        private const val FINAL_RESULT_LIMIT = "210"

        //private final static int INITIAL_RELATED_LIMIT = 5;
        private const val COMPOSING_CODE_LENGTH_LIMIT =
            16 //Jeremy '12,5,30 changed from 12 to 16 because of improved performance using binary tree.
        private const val DUALCODE_COMPOSING_LIMIT =
            16 //Jeremy '12,5,30 changed from 7 to 16 because of improved performance using binary tree.
        private const val DUALCODE_NO_CHECK_LIMIT =
            2 //Jeremy '12,5,30 changed from 5 to 3 for phonetic correct valid code display.

        /**
         * Checks if the current code has dual mapping enabled.
         * 
         * 
         * Dual mapping allows a single key to map to multiple characters,
         * which is useful for certain physical keyboard layouts.
         * 
         * @return true if dual mapping is active, false otherwise
         */
        //private final static int BETWEEN_SEARCH_WAY_BACK_LEVELS = 5; //Jeremy '15,6,30
        @get:JvmName("getCodeDualMappedProperty")
        var isCodeDualMapped: Boolean = false
            private set

        @JvmStatic
        fun isCodeDualMapped(): Boolean = isCodeDualMapped

        /** Database column name for record ID  */
        const val FIELD_ID: String = "_id"

        /** Database column name for input code  */
        const val FIELD_CODE: String = "code"

        /** Database column name for output word  */
        const val FIELD_WORD: String = "word"

        /** Database column name for related words list  */
        @JvmField
        val FIELD_RELATED: String = LIME.DB_TABLE_RELATED

        /** Database column name for user score  */
        const val FIELD_SCORE: String = "score"

        /** Database column name for base frequency score from han converter  */
        const val FIELD_BASESCORE: String =
            "basescore" //jeremy '11,9,8 base frequency got from han converter when table loading.

        /** Database column name for phonetic code without tone symbols  */
        const val FIELD_NO_TONE_CODE: String = "code3r"

        /** Virtual column name for exact match flag in query results  */
        const val FILE_EXACT_MATCH: String = "exactmatch"

        //public final static String FIELD_DIC_id = "_id";
        //public final static String FIELD_DIC_pcode = "pcode";
        /** Database column name for parent word in related phrase table  */
        const val FIELD_DIC_pword: String = "pword"
        //public final static String FIELD_DIC_ccode = "ccode";
        /** Database column name for child word in related phrase table  */
        const val FIELD_DIC_cword: String = "cword"

        //public final static String FIELD_DIC_score = "score";
        //public final static String FIELD_DIC_is = "isDictionary";
        // for keyToChar
        private const val DAYI_KEY = "1234567890qwertyuiopasdfghjkl;zxcvbnm,./"
        private const val DAYI_CHAR =
            "言|牛|目|四|王|門|田|米|足|金|石|山|一|工|糸|火|艸|木|口|耳|人|革|日|土|手|鳥|月|立|女|虫|心|水|鹿|禾|馬|魚|雨|力|舟|竹"
        private const val ARRAY_KEY = "qazwsxedcrfvtgbyhnujmik,ol.p;/"
        private const val ARRAY_CHAR =
            "1^|1-|1v|2^|2-|2v|3^|3-|3v|4^|4-|4v|5^|5-|5v|6^|6-|6v|7^|7-|7v|8^|8-|8v|9^|9-|9v|0^|0-|0v|"
        private const val BPMF_KEY = "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/-"
        private const val BPMF_CHAR =
            "ㄅ|ㄆ|ㄇ|ㄈ|ㄉ|ㄊ|ㄋ|ㄌ|ˇ|ㄍ|ㄎ|ㄏ|ˋ|ㄐ|ㄑ|ㄒ|ㄓ|ㄔ|ㄕ|ㄖ|ˊ|ㄗ|ㄘ|ㄙ|˙|ㄧ|ㄨ|ㄩ|ㄚ|ㄛ|ㄜ|ㄝ|ㄞ|ㄟ|ㄠ|ㄡ|ㄢ|ㄣ|ㄤ|ㄥ|ㄦ"


        private const val SHIFTED_NUMBERIC_KEY = "!@#$%^&*()"
        private const val SHIFTED_NUMBERIC_KEY_REMAP = "1234567890"

        private const val SHIFTED_SYMBOL_KEY = "<>?_:+\""
        private const val SHIFTED_SYMBOL_KEY_REMAP = ",./-;='"

        private const val ETEN_KEY = "abcdefghijklmnopqrstuvwxyz12347890-=;',./!@#$&*()<>?_+:\""
        private const val ETEN_KEY_REMAP =
            "81v2uzrc9bdxasiqoknwme,j.l7634f0p;/-yh5tg7634f0p;5tg/yh-"

        //private final static String DESIREZ_ETEN_KEY_REMAP = 	"-`81v2uzrc9bdxasiqoknwme,j.l7634f0p;/-yh5tg/";
        //private final static String MILESTONE_ETEN_KEY_REMAP =  "-`81v2uzrc9bdxasiqoknwme,j.l7634f0p;/-yh5tg/";
        //private final static String MILESTONE3_ETEN_KEY_REMAP = "-h81v2uzrc9bdxasiqoknwme,j.l7634f0p;/-yh5tg/";
        private const val DESIREZ_ETEN_DUALKEY = "o,ukm9iq5axesa" // remapped from "qwer uiop,vlnm";
        private const val DESIREZ_ETEN_DUALKEY_REMAP =
            "7634f0p;thg/-h" // remapped from "1234 7890;-/='";
        private const val CHACHA_ETEN_DUALKEY = ",uknljvcrx1?" // remapped from "werszxchglb?" 
        private const val CHACHA_ETEN_DUALKEY_REMAP =
            "7634f0p/g-hy" // remapped from "1234789-/=';";
        private const val XPERIAPRO_ETEN_DUALKEY = "o,ukm9iqa52z" // remapped from "qweruiopm,df";
        private const val XPERIAPRO_ETEN_DUALKEY_REMAP =
            "7634f0p;th/-" // remapped from "12347890;'=-";
        private const val MILESTONE_ETEN_DUALKEY = "o,ukm9iq5aec" // remapped from "qweruiop,mvh";
        private const val MILESTONE_ETEN_DUALKEY_REMAP =
            "7634f0p;th/-" // remapped from "12347890;'=-";
        private const val MILESTONE2_ETEN_DUALKEY = "o,ukm9iq5aer" //remapped from "qweruiop,mvg";
        private const val MILESTONE2_ETEN_DUALKEY_REMAP = "7634f0p;th/-"
        private const val MILESTONE3_ETEN_DUALKEY = "5aew" // ",mvt"
        private const val MILESTONE3_ETEN_DUALKEY_REMAP = "th/-"
        private val ETEN_CHAR = "ㄚ|ㄅ|ㄒ|ㄉ|ㄧ|ㄈ|ㄐ|ㄏ|ㄞ|ㄖ|ㄎ|ㄌ|ㄇ|ㄋ|ㄛ|ㄆ|ㄟ|ㄜ|ㄙ|ㄊ|ㄩ|ㄍ|ㄝ|ㄨ|ㄡ|ㄠ" +
                "|˙|ˊ|ˇ|ˋ|ㄑ|ㄢ|ㄣ|ㄤ|ㄥ|ㄦ|ㄗ|ㄘ|ㄓ|ㄔ|ㄕ|˙|ˊ|ˇ|ˋ|ㄑ|ㄢ|ㄣ|ㄤ|ㄓ|ㄔ|ㄕ|ㄥ|ㄦ|ㄗ|ㄘ"
        private val DESIREZ_ETEN_CHAR =
            "@|`|ㄚ|ㄅ|ㄒ|ㄉ|(ㄧ/ˇ)|ㄈ|ㄐ|ㄏ|(ㄞ/ㄢ)|ㄖ|ㄎ|(ㄌ/ㄕ)|(ㄇ/ㄘ)|(ㄋ/ㄦ)|(ㄛ/ㄣ)|(ㄆ/ㄤ)|(ㄟ/˙)" +
                    "|(ㄜ/ˋ)|ㄙ|ㄊ|(ㄩ/ㄑ)|(ㄍ/ㄥ)|(ㄝ/ˊ)|ㄨ|ㄡ|ㄠ" +
                    "|˙|ˊ|ˇ|ˋ|ㄑ|ㄢ|ㄣ|ㄤ|ㄥ|ㄦ|ㄗ|ㄘ|(ㄓ/ㄗ)|ㄔ|ㄕ|?"
        private val MILESTONE_ETEN_CHAR =
            "ㄦ|`|ㄚ|ㄅ|ㄒ|ㄉ|(ㄧ/ˇ)|ㄈ|ㄐ|(ㄏ/ㄦ)|(ㄞ/ㄢ)|ㄖ|ㄎ|ㄌ|(ㄇ/ㄘ)|ㄋ|(ㄛ/ㄣ)|(ㄆ/ㄤ)|(ㄟ/˙)" +
                    "|(ㄜ/ˋ)|ㄙ|ㄊ|(ㄩ/ㄑ)|(ㄍ/ㄥ)|(ㄝ/ˊ)|ㄨ|ㄡ|ㄠ" +
                    "|˙|ˊ|ˇ|ˋ|ㄑ|ㄢ|ㄣ|ㄤ|ㄥ|ㄦ|ㄗ|ㄘ|(ㄓ/ㄗ)|ㄔ|ㄕ|ㄥ"
        private val MILESTONE2_ETEN_CHAR =
            "ㄦ|`|ㄚ|ㄅ|ㄒ|ㄉ|(ㄧ/ˇ)|ㄈ|(ㄐ/ㄦ)|ㄏ|(ㄞ/ㄢ)|ㄖ|ㄎ|ㄌ|(ㄇ/ㄘ)|ㄋ|(ㄛ/ㄣ)|(ㄆ/ㄤ)|(ㄟ/˙)" +
                    "|(ㄜ/ˋ)|ㄙ|ㄊ|(ㄩ/ㄑ)|(ㄍ/ㄥ)|(ㄝ/ˊ)|ㄨ|ㄡ|ㄠ" +
                    "|˙|ˊ|ˇ|ˋ|ㄑ|ㄢ|ㄣ|ㄤ|ㄥ|ㄦ|ㄗ|ㄘ|(ㄓ/ㄗ)|ㄔ|ㄕ|ㄥ"
        private val MILESTONE3_ETEN_CHAR =
            "ㄦ|ㄘ|ㄚ|ㄅ|ㄒ|ㄉ|ㄧ|ㄈ|ㄐ|ㄏ|ㄞ|ㄖ|ㄎ|ㄌ|(ㄇ/ㄘ)|ㄋ|ㄛ|ㄆ|ㄟ|ㄜ|ㄙ|(ㄊ/ㄦ)|ㄩ|ㄍ|ㄝ|ㄨ|ㄡ|ㄠ" +
                    "|˙|ˊ|ˇ|ˋ|ㄑ|ㄢ|ㄣ|ㄤ|(ㄍ/ㄥ)|ㄦ|ㄗ|ㄘ|(ㄓ/ㄗ)|ㄔ|ㄕ|ㄥ"

        private const val ETEN26_KEY = "qazwsxedcrfvtgbyhnujmikolp,."
        private const val ETEN26_KEY_REMAP_INITIAL = "y8lhnju2vkzewr1tcsmba9dixq<>"
        private const val ETEN26_KEY_REMAP_FINAL = "y8lhnju7vk6ewr1tcsm3a94ixq<>"
        private const val ETEN26_DUALKEY_REMAP = "o,gf;5p-s0/.pbdz2"
        private const val ETEN26_DUALKEY = "yhvewrscpaxqs3467"
        private const val ETEN26_CHAR_INITIAL =
            "(ㄗ/ㄟ)|ㄚ|ㄠ|(ㄘ/ㄝ)|ㄙ|ㄨ|ㄧ|ㄉ|(ㄕ/ㄒ)|ㄜ|ㄈ|(ㄍ/ㄑ)|(ㄊ/ㄤ)|(ㄐ/ㄓ)|ㄅ|ㄔ|(ㄏ/ㄦ)|(ㄋ/ㄣ)|ㄩ|ㄖ|(ㄇ/ㄢ)|ㄞ|ㄎ|ㄛ|(ㄌ/ㄥ)|(ㄆ/ㄡ)|，|。"
        private const val ETEN26_CHAR_FINAL =
            "(ㄗ/ㄟ)|ㄚ|ㄠ|(ㄘ/ㄝ)|ㄙ|ㄨ|ㄧ|˙|(ㄕ/ㄒ)|ㄜ|ˊ|(ㄍ/ㄑ)|(ㄊ/ㄤ)|(ㄐ/ㄓ)|ㄅ|ㄔ|(ㄏ/ㄦ)|(ㄋ/ㄣ)|ㄩ|ˇ|(ㄇ/ㄢ)|ㄞ|ˋ|ㄛ|(ㄌ/ㄥ)|(ㄆ/ㄡ)|，|。"

        //Jeremy '12,5,31 use dual codes instead of initial/final remap for Hsu phonetic keyboard
        private const val HSU_KEY = "azwsxedcrfvtgbyhnujmikolpq,."
        private const val HSU_KEY_REMAP_INITIAL = "hylnju2vbzfwe18csm5a9d.xq`<>"
        private const val HSU_KEY_REMAP_FINAL = "hyl7ju6vb3fwe18csm4a9d.xq`<>"
        private const val HSU_DUALKEY_REMAP = "g8t5r/-,okip0;n2z"
        private const val HSU_DUALKEY = "vbf45x/uhecsad763"
        private const val HSU_CHAR_INITIAL =
            "(ㄘ/ㄟ)|ㄗ|ㄠ|ㄙ|ㄨ|(ㄧ/ㄝ)|ㄉ|(ㄕ/ㄒ)|ㄖ|ㄈ|(ㄔ/ㄑ)|ㄊ|(ㄍ/ㄜ)|ㄅ|ㄚ|(ㄏ/ㄛ)|(ㄋ/ㄣ)|ㄩ|(ㄐ/ㄓ)|(ㄇ/ㄢ)|ㄞ|(ㄎ/ㄤ)|ㄡ|(ㄌ/ㄥ/ㄦ)|ㄆ|q|，|。"
        private const val HSU_CHAR_FINAL =
            "(ㄘ/ㄟ)|ㄗ|ㄠ|(ㄙ/˙)|ㄨ|(ㄧ/ㄝ)|(ㄉ/ˊ)|(ㄕ/ㄒ)|ㄖ|(ㄈ/ˇ)|(ㄔ/ㄑ)|ㄊ|(ㄍ/ㄜ)|ㄅ|ㄚ|(ㄏ/ㄛ)|(ㄋ/ㄣ)|ㄩ|(ㄐ/ㄓ/ˋ)|(ㄇ/ㄢ)|ㄞ|(ㄎ/ㄤ)|ㄡ|(ㄥ/ㄦ)|ㄆ|q|，|。"

        private const val DESIREZ_KEY = "@qazwsxedcrfvtgbyhnujmik?olp,."
        private const val DESIREZ_BPMF_KEY_REMAP = "1qaz2wsedc5tg6yh4uj8ik9ol0;-,."
        private const val DESIREZ_BPMF_DUALKEY_REMAP = "xrfvb3n7m,.p/"
        private const val DESIREZ_BPMF_DUALKEY = "sedcg6h4jkl0;"
        private const val DESIREZ_DUALKEY_REMAP = "1234567890;-/='"
        private const val DESIREZ_DUALKEY = "qwertyuiop,vlnm"
        private val DESIREZ_BPMF_CHAR =
            "ㄅ|ㄆ|ㄇ|ㄈ|ㄉ|ㄊ|(ㄋ/ㄌ)|(ㄍ/ㄐ)|(ㄎ/ㄑ)|(ㄏ/ㄒ)|ㄓ|ㄔ|(ㄕ/ㄖ)|(ˊ/ˇ)|ㄗ|(ㄘ/ㄙ)|(ˋ/˙)" +
                    "|ㄧ|(ㄨ/ㄩ)|ㄚ|ㄛ|(ㄜ/ㄝ)|ㄞ|ㄟ|(ㄠ/ㄡ)|(ㄢ/ㄣ)|(ㄤ/ㄥ)|ㄦ|,|."
        private val DESIREZ_DAYI_CHAR =
            ("@|(言/石)|人|心|(牛/山)|革|水|(目/一)|日|鹿|(四/工)|土|禾|(王/糸)|手|馬|(門/火)|鳥|魚|(田/艸)|月|雨|"
                    + "(米/木)|立|?|(足/口)|(女/竹)|(金/耳)|(力/虫)|舟")


        private const val CHACHA_KEY = "qazwsxedcrfvtgbyhnujmik?olp,."
        private const val CHACHA_BPMF_KEY_REMAP = "qax2scedb5t3yh4uj68k.9o/0p-<>"
        private const val CHACHA_BPMF_DUALKEY_REMAP = "1zwrfvnmgi,7l;"
        private const val CHACHA_BPMF_DUALKEY = "qxsedchjt8k6op"
        private const val CHACHA_DUALKEY_REMAP = "123456789-/=';"
        private const val CHACHA_DUALKEY = "wersdfzxchglb?"
        private val CHACHA_BPMF_CHAR =
            "(ㄅ/ㄆ)|(ㄇ/ㄈ)|ㄌ|ㄉ|(ㄊ/ㄋ)|(ㄏ/ㄒ)|(ㄍ/ㄐ)|(ㄎ/ㄑ)|ㄖ|ㄓ|(ㄔ/ㄕ)|ˇ|ㄗ|(ㄘ/ㄙ)|ˋ|ㄧ|(ㄨ/ㄩ)|(ˊ/˙)" +
                    "|(ㄚ/ㄛ)|(ㄜ/ㄝ)|ㄡ|ㄞ|(ㄟ/ㄠ)|ㄥ|ㄢ|(ㄣ/ㄤ)|ㄦ|,|."

        private const val XPERIAPRO_KEY = "qazZwsxXedcCrfvVtgbByhnNujmMik`~ol'\"pP!/@"
        private const val XPERIAPRO_BPMF_KEY_REMAP = "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/-"

        //private final static String XPERIAPRO_BPMF_DUALKEY_REMAP = 		"";
        //private final static String XPERIAPRO_BPMF_DUALKEY = 			"";
        private const val XPERIAPRO_DUALKEY_REMAP = "1234567890;,=-"
        private const val XPERIAPRO_DUALKEY = "qwertyuiopm.df"

        //private final static String XPERIAPRO_BPMF_CHAR =; // Use BPMF_CHAR 
        private const val MILESTONE = "milestone"
        private const val MILESTONE2 = "milestone2"
        private const val MILESTONE3 = "milestone3"
        private const val MILESTONE_DUALKEY_REMAP = "1234567890;'=-"
        private const val MILESTONE_DUALKEY = "qwertyuiop,mhv"
        private const val MILESTONE_KEY = "qazwsxedcrfvtgbyhnujmik,ol.p/?"
        private val MILESTONE_BPMF_CHAR =
            "(ㄅ/ㄆ)|ㄇ|ㄈ|(ㄉ/ㄊ)|ㄋ|ㄌ|(ㄍ/ˇ)|ㄎ|ㄏ|(ㄐ/ˋ)|ㄑ|ㄒ|(ㄓ/ㄔ)|ㄕ|ㄖ|(ㄗ/ˊ)|ㄘ|ㄙ|(ㄧ/˙)" +
                    "|ㄨ|ㄩ|(ㄚ/ㄛ)|ㄜ|(ㄝ/ㄤ)|(ㄞ/ㄟ)|ㄠ|ㄡ|(ㄢ/ㄣ)|ㄥ|ㄦ"
        private val MILESTONE_DAYI_CHAR =
            ("(言/石)|人|心|(牛/山)|革|水|(目/一)|日|鹿|(四/工)|土|禾|(王/糸)|手|馬|(門/火)|鳥|魚|(田/艸)|月|雨|"
                    + "(米/木)|立|(力/虫)|(足/口)|女|舟|(金/耳)|竹|?")

        private const val MILESTONE2_DUALKEY_REMAP = "1234567890;'=-"
        private const val MILESTONE2_DUALKEY = "qwertyuiop,mgv"


        private const val MILESTONE3_KEY = "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p/"
        private const val MILESTONE3_DUALKEY_REMAP = ";"
        private const val MILESTONE3_DUALKEY = ","
        private const val MILESTONE3_BPMF_DUALKEY_REMAP = ";/-"
        private const val MILESTONE3_BPMF_DUALKEY = "l.p"
        private val MILESTONE3_BPMF_CHAR = "ㄅ|ㄆ|ㄇ|ㄈ|ㄉ|ㄊ|ㄋ|ㄌ|ˇ|ㄍ|ㄎ|ㄏ|ˋ|ㄐ|ㄑ|ㄒ|ㄓ|ㄔ|ㄕ|ㄖ|ˊ|ㄗ|ㄘ|ㄙ|˙|" +
                "ㄧ|ㄨ|ㄩ|ㄚ|ㄛ|ㄜ|ㄝ|ㄞ|ㄟ|(ㄠ/ㄤ)|(ㄡ/ㄥ)|ㄢ|ㄣ|ㄥ"
        private val MILESTONE3_DAYI_CHAR =
            "言|石|人|心|牛|山|革|水|目|一|日|鹿|四|工|土|禾|王|糸|手|馬|門|火|鳥|魚|田|" +
                    "艸|月|雨|米|木|立|(力/虫)|足|口|女|舟|金|耳|竹"


        private const val CJ_KEY = "qwertyuiopasdfghjklzxcvbnm"
        private const val CJ_CHAR =
            "手|田|水|口|廿|卜|山|戈|人|心|日|尸|木|火|土|竹|十|大|中|重|難|金|女|月|弓|一"

        private var blackListCache: ConcurrentHashMap<String?, Boolean?>? = null

        private const val probePerformance = false

        private fun getNewCode(entry: String, n: String, i: Int): String {
            val newCode: String

            if (entry.length == 1) newCode = n
            else if (i == 0) newCode = n + entry.substring(1)
            else if (i == entry.length - 1) newCode = entry.substring(0, entry.length - 1) + n
            else newCode = (entry.substring(0, i) + n
                    + entry.substring(i + 1))
            return newCode
        }

        /**
         * Half-open upper bound for a prefix range scan: the prefix with its final code point
         * incremented ("sal" -> "sam"). If the prefix ends at the maximum code point, drop it
         * and increment the previous character (standard string-successor). Returns null when no
         * successor exists (empty prefix or all-max), in which case the caller skips the query.
         */
        private fun prefixUpperBound(prefix: String?): String? {
            if (prefix == null || prefix.isEmpty()) return null
            val chars = prefix.toCharArray()
            for (i in chars.indices.reversed()) {
                if (chars[i] != Character.MAX_VALUE) {
                    val out = CharArray(i + 1)
                    System.arraycopy(chars, 0, out, 0, i + 1)
                    out[i] = (chars[i].code + 1).toChar()
                    return String(out)
                }
            }
            return null // all characters at max — no successor
        }

        private fun hasEmojiDataRows(targetDb: SQLiteDatabase? = db): Boolean {
            val database = targetDb ?: return false
            try {
                database.rawQuery("SELECT COUNT(*) FROM " + EMOJI_TABLE_DATA, null).use { cursor ->
                    return cursor != null && cursor.moveToFirst() && cursor.getInt(0) > 0
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking emoji data row count", e)
                return false
            }
        }

        /**
         * Ensure the scored dictionary table exists in the expected shape. Drops a legacy fts3
         * dictionary defensively (BEFORE create), creates the scored table + indexes when the
         * current object is missing or not the scored shape. The legacy fts3(word) table has no
         * score column, so this is a clean drop-and-rebuild (nothing to preserve).
         */
        private fun ensureDictionarySchema(targetDb: SQLiteDatabase) {
            if (isLegacyFtsDictionary(targetDb)) {
                dropLegacyFtsDictionary(targetDb)
            }
            if (!isScoredDictionaryShape(targetDb)) {
                targetDb.execSQL("DROP TABLE IF EXISTS " + DICTIONARY_TABLE)
                targetDb.execSQL(
                    "CREATE TABLE IF NOT EXISTS " + DICTIONARY_TABLE + " (" +
                            "word TEXT PRIMARY KEY, " +
                            "basescore INTEGER NOT NULL DEFAULT 0, " +
                            "score INTEGER NOT NULL DEFAULT 0)"
                )
            }
            targetDb.execSQL("CREATE INDEX IF NOT EXISTS dictionary_word_idx ON " + DICTIONARY_TABLE + "(word)")
            targetDb.execSQL("CREATE INDEX IF NOT EXISTS dictionary_rank_idx ON " + DICTIONARY_TABLE + "(score + basescore)")
        }

        /** True when a 'dictionary' object exists and is an FTS virtual table.  */
        private fun isLegacyFtsDictionary(targetDb: SQLiteDatabase): Boolean {
            try {
                targetDb.rawQuery(
                    "SELECT sql FROM sqlite_master WHERE name=?", arrayOf<String>(DICTIONARY_TABLE)
                ).use { cursor ->
                    if (cursor != null && cursor.moveToFirst()) {
                        val sql = cursor.getString(0)
                        return sql != null && sql.lowercase().contains("virtual table")
                                && sql.lowercase().contains("using fts")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error probing legacy fts dictionary", e)
            }
            return false
        }

        /** True when 'dictionary' exists with both basescore and score columns.  */
        private fun isScoredDictionaryShape(targetDb: SQLiteDatabase): Boolean {
            var hasBase = false
            var hasScore = false
            try {
                targetDb.rawQuery("PRAGMA table_info(" + DICTIONARY_TABLE + ")", null)
                    .use { cursor ->
                        if (cursor != null) {
                            val nameIdx = cursor.getColumnIndex("name")
                            while (cursor.moveToNext()) {
                                val col = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                                if ("basescore" == col) hasBase = true
                                else if ("score" == col) hasScore = true
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error probing scored dictionary shape", e)
            }
            return hasBase && hasScore
        }

        /**
         * Remove a legacy fts3 dictionary and its shadow tables. Tries a normal DROP first; if
         * SQLite rejects it because the saved DDL references an unavailable fts module (the #88
         * crash family), falls back to a narrow writable_schema cleanup of the dictionary object
         * and its shadow tables. Then bumps the schema version so the connection re-reads it.
         */
        private fun dropLegacyFtsDictionary(targetDb: SQLiteDatabase) {
            try {
                targetDb.execSQL("DROP TABLE IF EXISTS " + DICTIONARY_TABLE)
            } catch (dropEx: Exception) {
                Log.w(
                    TAG,
                    "Normal DROP of legacy fts dictionary failed; using writable_schema cleanup",
                    dropEx
                )
                try {
                    targetDb.execSQL("PRAGMA writable_schema=ON")
                    targetDb.execSQL(
                        "DELETE FROM sqlite_master WHERE name='" + DICTIONARY_TABLE +
                                "' OR name LIKE 'dictionary\\_%' ESCAPE '\\'"
                    )
                    targetDb.execSQL("PRAGMA writable_schema=OFF")
                    targetDb.execSQL("PRAGMA schema_version=" + (getSchemaVersion(targetDb) + 1))
                } catch (cleanupEx: Exception) {
                    Log.e(TAG, "writable_schema cleanup of legacy fts dictionary failed", cleanupEx)
                }
            }
            // Android's fts3 DROP TABLE does not always cascade-drop the shadow tables, so drop
            // every remaining 'dictionary_%' SHADOW TABLE discovered from sqlite_master. Restrict
            // to type='table' so we never touch the scored table's own indexes (dictionary_word_idx
            // / dictionary_rank_idx also match 'dictionary_%').
            for (shadow in remainingDictionaryShadowTables(targetDb)) {
                try {
                    targetDb.execSQL("DROP TABLE IF EXISTS " + shadow)
                } catch (ignored: Exception) {
                }
            }
        }

        /** Names of remaining 'dictionary_%' shadow TABLES (excludes indexes).  */
        private fun remainingDictionaryShadowTables(targetDb: SQLiteDatabase): MutableList<String?> {
            val names: MutableList<String?> = ArrayList<String?>()
            try {
                targetDb.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' "
                            + "AND name LIKE 'dictionary\\_%' ESCAPE '\\'", null
                ).use { cursor ->
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            names.add(cursor.getString(0))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error listing dictionary shadow tables", e)
            }
            return names
        }

        private fun getSchemaVersion(targetDb: SQLiteDatabase): Int {
            try {
                targetDb.rawQuery("PRAGMA schema_version", null).use { cursor ->
                    return if (cursor != null && cursor.moveToFirst()) cursor.getInt(0) else 0
                }
            } catch (e: Exception) {
                return 0
            }
        }

        private fun hasDictionaryRows(targetDb: SQLiteDatabase): Boolean {
            try {
                targetDb.rawQuery("SELECT COUNT(*) FROM " + DICTIONARY_TABLE, null).use { cursor ->
                    return cursor != null && cursor.moveToFirst() && cursor.getInt(0) > 0
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking dictionary row count", e)
                return false
            }
        }

        private fun createEmojiTables(targetDb: SQLiteDatabase, forceRecreate: Boolean) {
            if (forceRecreate) {
                dropEmojiFtsTable(targetDb)
                targetDb.execSQL("DROP TABLE IF EXISTS " + EMOJI_TABLE_USER)
                targetDb.execSQL("DROP TABLE IF EXISTS " + EMOJI_TABLE_DATA)
            }
            targetDb.execSQL(
                "CREATE TABLE IF NOT EXISTS " + EMOJI_TABLE_DATA + " (" +
                        "value TEXT PRIMARY KEY, " +
                        "cp TEXT NOT NULL, " +
                        "group_name TEXT NOT NULL, " +
                        "subgroup TEXT NOT NULL, " +
                        "sort_order INTEGER NOT NULL, " +
                        "name_en TEXT, " +
                        "name_tw TEXT, " +
                        "tags_en TEXT, " +
                        "tags_tw TEXT, " +
                        "version REAL NOT NULL)"
            )
            targetDb.execSQL("CREATE INDEX IF NOT EXISTS idx_emoji_group ON " + EMOJI_TABLE_DATA + "(group_name, sort_order)")
            var recreatedEmojiFts = false
            if (!tableExists(targetDb, EMOJI_TABLE_FTS) || !isEmojiFtsTableUsable(targetDb)) {
                dropEmojiFtsTable(targetDb)
                createEmojiFtsTable(targetDb)
                recreatedEmojiFts = true
            }
            targetDb.execSQL(
                "CREATE TABLE IF NOT EXISTS " + EMOJI_TABLE_USER + " (" +
                        "value TEXT PRIMARY KEY REFERENCES " + EMOJI_TABLE_DATA + "(value), " +
                        "last_used INTEGER, " +
                        "use_count INTEGER NOT NULL DEFAULT 0)"
            )
            if (recreatedEmojiFts && hasEmojiDataRows(targetDb)) {
                rebuildEmojiFts(targetDb)
            }
        }

        private fun ensureCj4Schema(targetDb: SQLiteDatabase) {
            targetDb.execSQL(
                "CREATE TABLE IF NOT EXISTS " + LIME.DB_TABLE_CJ4 + " (" +
                        "_id INTEGER primary key autoincrement, " +
                        "code text, " +
                        "code3r text, " +
                        "word text, " +
                        "related text, " +
                        "score integer, " +
                        "'basescore' type integer)"
            )
            targetDb.execSQL("CREATE INDEX IF NOT EXISTS cj4_idx_code ON " + LIME.DB_TABLE_CJ4 + " (code)")
            targetDb.delete(
                LIME.DB_TABLE_KEYBOARD,
                LIME.DB_KEYBOARD_COLUMN_CODE + " = ?",
                arrayOf<String>(LIME.DB_TABLE_CJ4)
            )
        }

        private fun tableExists(targetDb: SQLiteDatabase, tableName: String?): Boolean {
            targetDb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type IN ('table','virtual table') AND name=?",
                arrayOf<String?>(tableName)
            ).use { cursor ->
                return cursor != null && cursor.moveToFirst()
            }
        }

        private fun tableExists(databasePath: String, tableName: String?): Boolean {
            var sourceDb: SQLiteDatabase? = null
            try {
                sourceDb =
                    SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READONLY)
                return tableExists(sourceDb, tableName)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking attached emoji database table", e)
                return false
            } finally {
                if (sourceDb != null) {
                    sourceDb.close()
                }
            }
        }

        private fun isEmojiFtsTableUsable(targetDb: SQLiteDatabase): Boolean {
            try {
                targetDb.rawQuery("SELECT rowid FROM " + EMOJI_TABLE_FTS + " LIMIT 0", null)
                    .use { cursor ->
                        return cursor != null
                    }
            } catch (e: SQLiteException) {
                Log.w(TAG, "Existing emoji FTS table is unusable; recreating", e)
                return false
            }
        }

        private fun createEmojiFtsTable(targetDb: SQLiteDatabase) {
            createEmojiFts4Table(targetDb)
        }

        private fun createEmojiFts4Table(targetDb: SQLiteDatabase) {
            try {
                targetDb.execSQL(
                    "CREATE VIRTUAL TABLE " + EMOJI_TABLE_FTS + " USING fts4(" +
                            "name_en, name_tw, tags_en, tags_tw, " +
                            "tokenize=unicode61 \"remove_diacritics=1\", " +
                            "content=" + EMOJI_TABLE_DATA + ")"
                )
            } catch (fts4Error: SQLiteException) {
                val message = fts4Error.message
                if (message == null || !message.contains("already exists")) {
                    throw fts4Error
                }
                Log.w(
                    TAG,
                    "Removing residual emoji FTS schema before retrying FTS4 fallback",
                    fts4Error
                )
                dropEmojiFtsSchemaRows(targetDb)
                targetDb.execSQL(
                    "CREATE VIRTUAL TABLE " + EMOJI_TABLE_FTS + " USING fts4(" +
                            "name_en, name_tw, tags_en, tags_tw, " +
                            "tokenize=unicode61 \"remove_diacritics=1\", " +
                            "content=" + EMOJI_TABLE_DATA + ")"
                )
            }
        }

        private fun dropEmojiFtsTable(targetDb: SQLiteDatabase) {
            var dropError: SQLiteException? = null
            try {
                targetDb.execSQL("DROP TABLE IF EXISTS " + EMOJI_TABLE_FTS)
            } catch (e: SQLiteException) {
                dropError = e
                Log.w(TAG, "DROP TABLE failed while cleaning emoji FTS", e)
            }
            if (!emojiFtsSchemaRowsExist(targetDb)) {
                if (dropError != null) {
                    throw dropError
                }
                return
            }
            Log.w(TAG, "Removing residual emoji FTS schema before recreating FTS4 table")
            dropEmojiFtsSchemaRows(targetDb)
        }

        private fun emojiFtsSchemaRowsExist(targetDb: SQLiteDatabase): Boolean {
            targetDb.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE name = ? OR tbl_name = ? OR name LIKE ? LIMIT 1",
                arrayOf<String>(EMOJI_TABLE_FTS, EMOJI_TABLE_FTS, EMOJI_TABLE_FTS + "_%")
            ).use { cursor ->
                return cursor != null && cursor.moveToFirst()
            }
        }

        private fun dropEmojiFtsSchemaRows(targetDb: SQLiteDatabase) {
            targetDb.execSQL("PRAGMA writable_schema=ON")
            try {
                targetDb.delete(
                    "sqlite_master",
                    "name = ? OR tbl_name = ? OR name LIKE ?",
                    arrayOf<String>(EMOJI_TABLE_FTS, EMOJI_TABLE_FTS, EMOJI_TABLE_FTS + "_%")
                )
                bumpSchemaVersion(targetDb)
            } finally {
                targetDb.execSQL("PRAGMA writable_schema=OFF")
            }
        }

        private fun bumpSchemaVersion(targetDb: SQLiteDatabase) {
            var schemaVersion = 0
            targetDb.rawQuery("PRAGMA schema_version", null).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    schemaVersion = cursor.getInt(0)
                }
            }
            targetDb.execSQL("PRAGMA schema_version = " + (schemaVersion + 1))
        }

        private fun clearEmojiBaseData(targetDb: SQLiteDatabase) {
            targetDb.delete(EMOJI_TABLE_DATA, null, null)
            targetDb.delete(
                LIME.DB_TABLE_IM,
                LIME.DB_IM_COLUMN_CODE + "=?",
                arrayOf<String>("emoji")
            )
        }

        private fun insertEmojiRows(targetDb: SQLiteDatabase, rows: MutableList<EmojiDataRow>) {
            for (row in rows) {
                val cv = ContentValues()
                cv.put("value", row.value)
                cv.put("cp", row.cp)
                cv.put("group_name", row.groupName)
                cv.put("subgroup", row.subgroup)
                cv.put("sort_order", row.sortOrder)
                cv.put("name_en", row.nameEn)
                cv.put("name_tw", row.nameTw)
                cv.put("tags_en", row.tagsEn)
                cv.put("tags_tw", row.tagsTw)
                cv.put("version", row.version)
                targetDb.insertWithOnConflict(
                    EMOJI_TABLE_DATA,
                    null,
                    cv,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }

        private fun rebuildEmojiFts(targetDb: SQLiteDatabase) {
            targetDb.execSQL("INSERT INTO " + EMOJI_TABLE_FTS + "(" + EMOJI_TABLE_FTS + ") VALUES('rebuild')")
        }

        private fun insertEmojiMetadata(
            targetDb: SQLiteDatabase,
            emojiVersion: String?,
            amount: Int
        ) {
            insertEmojiMetadataRow(targetDb, "version", emojiVersion)
            insertEmojiMetadataRow(targetDb, "name", "Emoji " + emojiVersion + " Dataset")
            insertEmojiMetadataRow(targetDb, "source", "emoji.db")
            insertEmojiMetadataRow(targetDb, "amount", amount.toString())
            insertEmojiMetadataRow(
                targetDb,
                "import",
                (System.currentTimeMillis() / 1000L).toString()
            )
        }

        private fun insertEmojiMetadataRow(
            targetDb: SQLiteDatabase,
            title: String?,
            desc: String?
        ) {
            val cv = ContentValues()
            cv.put("code", "emoji")
            cv.put("title", title)
            cv.put("desc", desc)
            cv.put("keyboard", "")
            cv.put("disable", "")
            cv.put("selkey", "")
            cv.put("endkey", "")
            cv.put("spacestyle", "")
            targetDb.insert(LIME.DB_TABLE_IM, null, cv)
        }

        private fun buildEmojiPanelSearchQuery(input: String?): String {
            val tokens: MutableList<String> = sanitizedEmojiTokens(input)
            val builder = StringBuilder()
            for (token in tokens) {
                if (builder.length > 0) {
                    builder.append(' ')
                }
                builder.append(token).append('*')
            }
            return builder.toString()
        }

        private fun buildEmojiCandidateQuery(input: String?): String {
            val tokens: MutableList<String> = sanitizedEmojiTokens(input)
            val builder = StringBuilder()
            for (token in tokens) {
                if (isSingleAsciiAlphabeticToken(token)) {
                    continue
                }
                appendEmojiQueryTerm(builder, token + "*")
                val firstCjk: String? = firstCjkCharacter(token)
                if (firstCjk != null && firstCjk != token) {
                    appendEmojiQueryTerm(builder, firstCjk + "*")
                }
            }
            return builder.toString()
        }

        private fun sanitizedEmojiTokens(input: String?): MutableList<String> {
            val tokens: MutableList<String> = ArrayList<String>()
            val trimmed = if (input == null) "" else input.trim { it <= ' ' }
            if (trimmed.isEmpty()) {
                return tokens
            }
            for (rawPart in trimmed.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()) {
                val token = rawPart.replace("[^\\p{L}\\p{N}_]+".toRegex(), "")
                if (token.isEmpty()) {
                    continue
                }
                tokens.add(token)
            }
            return tokens
        }

        private fun appendEmojiQueryTerm(builder: StringBuilder, term: String?) {
            if (builder.length > 0) {
                builder.append(" OR ")
            }
            builder.append(term)
        }

        @JvmStatic
        fun buildEmojiPanelSearchQueryForTest(input: String?): String {
            return buildEmojiPanelSearchQuery(input)
        }

        @JvmStatic
        fun buildEmojiCandidateQueryForTest(input: String?): String {
            return buildEmojiCandidateQuery(input)
        }

        private fun isSingleAsciiAlphabeticToken(token: String): Boolean {
            if (token.length != 1) {
                return false
            }
            val value = token.get(0)
            return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z')
        }

        private fun firstCjkCharacter(token: String?): String? {
            if (token == null || token.isEmpty()) {
                return null
            }
            val codePoint = token.codePointAt(0)
            if (!isCjkCodePoint(codePoint)) {
                return null
            }
            return String(Character.toChars(codePoint))
        }

        private fun isCjkCodePoint(codePoint: Int): Boolean {
            return (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                    || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                    || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                    || (codePoint >= 0x20000 && codePoint <= 0x2A6DF)
                    || (codePoint >= 0x2A700 && codePoint <= 0x2B73F)
                    || (codePoint >= 0x2B740 && codePoint <= 0x2B81F)
                    || (codePoint >= 0x2B820 && codePoint <= 0x2CEAF)
                    || (codePoint >= 0x2CEB0 && codePoint <= 0x2EBEF)
                    || (codePoint >= 0x30000 && codePoint <= 0x3134F)
        }

        private fun quoteSqlString(value: String): String {
            return "'" + value.replace("'", "''") + "'"
        }
    }
}
