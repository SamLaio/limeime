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
package net.toload.main.hd.limedb

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * Helper for Han character conversion and base-frequency lookup.
 * 
 * 
 * This class wraps a lightweight read-only SQLite database ("hanconvertv2.db")
 * that provides mappings and base frequency scores used when loading mapping
 * tables and computing default base scores for characters/phrases.
 * 
 * 
 * Primary responsibilities:
 * 
 *  * Lookup of base frequency scores via [.getBaseScore]
 *  * Conversion between Traditional and Simplified Chinese via [.convert]
 * 
 * 
 * @author Art Hung
 */
class LimeHanConverter(context: Context?) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    /**
     * Count total amount of specific table
     * 
     * 
     * public int countMapping(String table) {
     * if(DEBUG)
     * Log.i(TAG,"countMapping() on table:" + table);
     * 
     * try {
     * SQLiteDatabase db = this.getReadableDatabase();
     * 
     * Cursor cursor = db.rawQuery("SELECT * FROM " + table, null);
     * if(cursor ==null) return 0;
     * int total = cursor.getCount();
     * cursor.close();
     * if(DEBUG)
     * Log.i(TAG, "countMapping" + "Table," + table + ": " + total);
     * return total;
     * } catch (Exception e) {
     * Log.e(TAG, "Error in Han conversion", e);
     * }
     * return 0;
     * }
     * / **
     * Create SQLite Database and create related tables
     */
    override fun onCreate(db: SQLiteDatabase?) {
        // ignore error when create tables
    }

    /**
     * Upgrade current database
     */
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    }

    fun getBaseScore(input: String?): Int {
        if (DEBUG) Log.i(TAG, "getBaseScore()")
        var score = 0
        if (input != null && !input.isEmpty()) {
            var cursor: Cursor? = null

            try {
                val db = this.getReadableDatabase()

                // Use parameterized query to prevent SQL injection
                cursor = db.query(
                    "TCSC", null, FIELD_CODE + " = ?",
                    arrayOf<String>(input), null, null, null, null
                )
                if (cursor != null && cursor.moveToFirst()) {
                    val scoreColumn = cursor.getColumnIndex(FIELD_SCORE)
                    score = cursor.getInt(scoreColumn)
                } else if (input.length > 1) score = 1 //phase has default score = 1
            } catch (e: Exception) {
                Log.e(TAG, "Error in Han conversion", e)
            } finally {
                // Ensure cursor is closed even if exception occurs
                if (cursor != null) {
                    cursor.close()
                }
            }
        }
        return score
    }

    fun convert(input: String, hanConvertOption: Int?): String {
        val option = hanConvertOption ?: 0
        var output = StringBuilder(input)
        //Log.i("LimeHanConverter.convert()","hanConvertOption:"+hanConvertOption);
        if (!input.isEmpty() && option != 0) {
            var tablename = ""
            var cursor: Cursor? = null
            if (option == 1) { //TC to SC
                tablename = "TCSC"
            } else if (option == 2) { // SC to TC
                tablename = "SCTC"
            }
            try {
                val db = this.getReadableDatabase()

                output = StringBuilder()
                for (i in 0..<input.length) {
                    // Validate table name to prevent SQL injection
                    if (tablename != "TCSC" && tablename != "SCTC") {
                        Log.e(TAG, "convert(): Invalid table name: " + tablename)
                        break
                    }
                    // Use parameterized query to prevent SQL injection
                    val charStr = input.get(i).toString()
                    cursor = db.query(
                        tablename, null, FIELD_CODE + " = ?",
                        arrayOf<String>(charStr), null, null, null, null
                    )

                    if (cursor != null && cursor.moveToFirst()) {
                        //int codeColumn = cursor.getColumnIndex(FIELD_CODE);
                        val wordColumn = cursor.getColumnIndex(FIELD_WORD)
                        val word = cursor.getString(wordColumn)
                        output.append(word)
                    } else {
                        output.append(input.get(i))
                    }


                    // Close cursor after each iteration to prevent resource leak
                    if (cursor != null) {
                        cursor.close()
                        cursor = null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Han conversion", e)
            } finally {
                // Ensure cursor is closed even if exception occurs
                if (cursor != null) {
                    cursor.close()
                }
            }
        }
        return output.toString()
    }

    companion object {
        private const val DEBUG = false
        private const val TAG = "LimeHanConverter"


        private const val DATABASE_NAME = "hanconvertv2.db"
        private const val DATABASE_VERSION = 59


        private const val FIELD_CODE = "code"
        private const val FIELD_WORD = "word"
        private const val FIELD_SCORE = "score"
    }
}
