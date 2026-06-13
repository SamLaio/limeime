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
import java.util.LinkedList
import net.toload.main.hd.data.Mapping
import net.toload.main.hd.global.LIME

/**
 * Emoji conversion helper backed by a small SQLite database (emoji.db).
 *
 * @author Art Hung
 */
class EmojiConverter(context: Context?) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase?) {
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    }

    fun convert(tag: String?, emoji: Int?): List<Mapping> {
        val output: MutableList<Mapping> = LinkedList()

        if (!tag.isNullOrEmpty()) {
            var tablename = ""
            if (emoji == LIME.EMOJI_CN) {
                tablename = "cn"
            } else if (emoji == LIME.EMOJI_EN) {
                tablename = "en"
            } else if (emoji == LIME.EMOJI_TW) {
                tablename = "tw"
            }

            try {
                val db = readableDatabase
                val cursor = db.query(
                    tablename,
                    null,
                    LIME.EMOJI_FIELD_TAG + " = '" + tag + "' ",
                    null,
                    null,
                    null,
                    null,
                    null
                )

                if (cursor.moveToFirst()) {
                    val wordColumn = cursor.getColumnIndex(LIME.EMOJI_FIELD_VALUE)
                    if (wordColumn >= 0) {
                        while (!cursor.isAfterLast) {
                            val word = cursor.getString(wordColumn)
                            if (!word.isNullOrEmpty() && word != " ") {
                                val mapping = Mapping()
                                mapping.setCode("")
                                mapping.setWord(word)
                                mapping.setEmojiRecord()
                                output.add(mapping)
                            }
                            cursor.moveToNext()
                        }
                    }
                }

                cursor.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error in emoji conversion", e)
            }
        }
        return output
    }

    companion object {
        private const val TAG = "EmojiConverter"
        private const val DATABASE_NAME = "emoji.db"
        private const val DATABASE_VERSION = 1
    }
}
