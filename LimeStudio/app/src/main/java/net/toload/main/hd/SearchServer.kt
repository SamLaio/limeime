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

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.RemoteException
import android.util.Log
import android.util.Pair
import android.widget.Toast
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.HashMap
import java.util.Iterator
import java.util.LinkedList
import java.util.Locale
import java.util.Stack
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.data.Keyboard
import net.toload.main.hd.data.Mapping
import net.toload.main.hd.data.Record
import net.toload.main.hd.data.Related
import net.toload.main.hd.global.LIME
import net.toload.main.hd.global.LIMEPreferenceManager
import net.toload.main.hd.global.LIMEUtilities
import net.toload.main.hd.limedb.LimeDB
import net.toload.main.hd.limedb.LimeDB.EmojiLocale
import net.toload.main.hd.SearchServer.Companion.resetCache
import net.toload.main.hd.ui.LIMESettings

/**
 * SearchServer is the central engine for handling input method queries and candidate suggestions.
 * 
 * 
 * It acts as an intermediary between the IME service and the database [LimeDB], providing:
 * 
 *  * Efficient database querying for character mapping and phrase retrieval.
 *  * Multi-level caching mechanics (code, English words, emojis) to optimize performance.
 *  * Runtime suggestion generation for dynamic phrase building.
 *  * Handling of different keyboard types (phonetic, physical) and their mapping logic.
 *  * Support for related phrase lookups and Han character conversion.
 * 
 */
class SearchServer(private val mContext: Context?) {
    private var mLIMEPref: LIMEPreferenceManager? = null

    //Jeremy '11,6,6
    private val selKeyMap = HashMap<String?, String?>()

    /**
     * Converts a string using the Han character conversion settings (e.g., Traditional to Simplified).
     * 
     * @param input The input string to convert.
     * @return The converted string.
     */
    fun hanConvert(input: String?): String? {
        return dbadapter!!.hanConvert(input, mLIMEPref!!.getHanCovertOption())
    }

    /**
     * Gets the current database table name.
     *
     * Kept as a JVM field for Java tests and callers that used the original Java API.
     */
    @JvmField
    var tablename: String = Companion.tablename

    /**
     * Sets the current active database table (IME method).
     * 
     * 
     * This updates the database adapter and optionally triggers a cache prefetch for better performance.
     * 
     * @param table         The name of the table to switch to (e.g., Phonetic, CJ).
     * @param numberMapping Whether the table supports number mapping.
     * @param symbolMapping Whether the table supports symbol mapping.
     */
    fun setTableName(table: String, numberMapping: Boolean, symbolMapping: Boolean) {
        if (DEBUG) Log.i(TAG, "SearchService.setTablename()")
        // Validate table name before setting
        if (!isValidTableName(table)) {
            Log.e(TAG, "setTableName(): Invalid table name: " + table)
            throw IllegalArgumentException("Invalid table name: " + table)
        }
        dbadapter!!.setTableName(table)
        Companion.tablename = table
        this.tablename = table
        hasNumberMapping = numberMapping
        hasSymbolMapping = symbolMapping

        //run prefetch on first keys thread to feed the data into cache first for better response on large table.  Jeremy '15, 6,7
        if (cache!!.get(cacheKey("a")) == null) {  // no cache records present. do prefetch now.  '15,6,7
            prefetchCache(numberMapping, symbolMapping)
        }

        //Jeremy '15,6,21 set max code length
        if (Companion.tablename.startsWith(LIME.DB_TABLE_CJ)) {
            maxCodeLength = 5
        }
    }

    fun getTablename(): String {
        return tablename
    }

    /**
     * Prefetches common mappings into the cache to improve initial response time.
     * 
     * 
     * Runs in a background thread.
     * 
     * @param numberMapping Whether to prefetch number mappings.
     * @param symbolMapping Whether to prefetch symbol mappings.
     */
    private fun prefetchCache(numberMapping: Boolean, symbolMapping: Boolean) {
        if (DEBUG) Log.i(TAG, "prefetchCache() on table :" + Companion.tablename)

        val keysBuilder = StringBuilder("abcdefghijklmnopqrstuvwxyz")
        if (numberMapping) keysBuilder.append("01234567890")
        if (symbolMapping) keysBuilder.append(",./;")
        val finalKeys = keysBuilder.toString()

        if (prefetchThread != null && prefetchThread!!.isAlive()) return

        prefetchThread = object : Thread() {
            override fun run() {
                val startime = System.currentTimeMillis()
                for (i in 0..<finalKeys.length) {
                    val key = finalKeys.substring(i, i + 1)
                    try {
                        //bypass run-time suggestion for prefetch queries
                        getMappingByCode(key, true, false, true)
                    } catch (e: RemoteException) {
                        Log.e(TAG, "Error in search operation", e)
                    }
                }
                Log.i(
                    TAG,
                    ("prefetchCache() on table :" + Companion.tablename + " finished.  Elapsed time = "
                            + (System.currentTimeMillis() - startime) + " ms.")
                )
            }
        }
        prefetchThread!!.start()
    }


    //TODO: Should cache related phrase 15,6,8 Jeremy
    /**
     * Gets related phrase suggestions for a parent word.
     * 
     * 
     * This method delegates to LimeDB.getRelatedPhrase() to retrieve related phrase
     * candidates that can follow the given parent word.
     * 
     * @param word The parent word to get related phrases for
     * @param getAllRecords If true, returns up to FINAL_RESULT_LIMIT; if false, returns up to INITIAL_RESULT_LIMIT
     * @return List of Mapping objects containing related phrase suggestions
     * @throws RemoteException if database error occurs
     */
    @Throws(RemoteException::class)
    fun getRelatedByWord(word: String?, getAllRecords: Boolean): MutableList<Mapping?> {
        return dbadapter!!.getRelatedPhrase(word, getAllRecords)
    }

    //Add by jeremy '10, 4,1
    /**
     * Retrieves the list of input codes for a given word and displays it.
     * 
     * 
     * Used for reverse lookup features.
     * 
     * @param word The word to look up.
     */
    fun getCodeListStringFromWord(word: String?) {
        val result: String? = dbadapter!!.getCodeListStringByWord(word)
        if (isReverseLookupResult(result)) {
            val context = mContext ?: return
            LIMEUtilities.showNotification(
                context,
                true,
                context.getText(R.string.ime_setting),
                result,
                Intent(context, LIMESettings::class.java)
            )

            if (context is LIMEService) {
                context.showReverseLookup(result)
            } else {
                Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Generates a unique cache key for a given code.
     * 
     * 
     * The key depends on the keyboard type (physical/virtual) and table name to avoid collisions.
     * 
     * @param code The input code.
     * @return A unique string key for the cache.
     */
    private fun cacheKey(code: String): String {
        // Snapshot non-volatile fields — teardown/reinit on other threads can null
        // them between the check below and later reads, causing NPE in orphan
        // prefetch threads during test process shutdown.
        val pref = mLIMEPref
        val db: LimeDB? = dbadapter
        if (pref == null || db == null) {
            Log.e(TAG, "cacheKey() mLIMEPref or dbadapter is null")
            return ""
        }
        val key: String
        //Jeremy '11,6,17 Separate physical keyboard cache with keyboardtype
        if (isPhysicalKeyboardPressed) {
            if (Companion.tablename == LIME.DB_TABLE_PHONETIC) {
                key = (pref.getPhysicalKeyboardType() + db.getTableName()
                        + pref.getPhoneticKeyboardType() + code)
            } else {
                key = pref.getPhysicalKeyboardType() + db.getTableName() + code
            }
        } else {
            if (Companion.tablename == LIME.DB_TABLE_PHONETIC) key =
                db.getTableName() + pref.getPhoneticKeyboardType() + code
            else key = db.getTableName() + code
        }
        return key
    }


    /**
     * Clears the runtime suggestion history.
     * 
     * @param abandonSuggestion true if the suggestion process should be abandoned.
     */
    fun clearRunTimeSuggestion(abandonSuggestion: Boolean) {
        for (suggestList in suggestionLoL!!) {
            suggestList.clear()
        }
        suggestionLoL!!.clear()
        if (bestSuggestionStack != null) bestSuggestionStack!!.clear()
        val lastConfirmedBestSuggestion: String? = null
        abandonPhraseSuggestion = abandonSuggestion
    }

    /**
     * Generates runtime phrase suggestions based on the current input code.
     * 
     * 
     * This method analyzes the input code and the list of exact matches to dynamically construct
     * phrase suggestions. It handles incremental updates, maintaining a history of suggestions
     * in `suggestionLoL` to support efficient updates when new characters are added or removed
     * (backspace).
     * 
     * 
     * It attempts to combine previous best suggestions with new exact matches for the remaining
     * code segment to form longer phrases, validating them against the related words table and
     * calculating scores to prioritize the most likely candidates.
     * 
     * @param code                   The current full input code sequence.
     * @param completeCodeResultList A list of mappings that exactly match the current code (or parts of it).
     */
    @Synchronized
    fun makeRunTimeSuggestion(code: String, completeCodeResultList: MutableList<Mapping?>?) {
        var startTime: Long = 0
        if (DEBUG || dumpRunTimeSuggestion) {
            Log.i(TAG, "makeRunTimeSuggestion() code = " + code)
            startTime = System.currentTimeMillis()
        }
        //check if the composing is start over or user pressed backspace
        if (suggestionLoL != null && !suggestionLoL!!.isEmpty()) {
            // code is start over, clear the stack.  The composition is start over.   Jeremy'15,6,4.
            if (code.length == 1) {
                clearRunTimeSuggestion(false)
            } else if (code.length == lastCode!!.length - 1) {  //user press backspace.
                for (suggestList in suggestionLoL) {
                    //check the last element in each list
                    if (!suggestList.isEmpty() && suggestList.get(suggestList.size - 1).second == lastCode) {
                        suggestList.removeAt(suggestList.size - 1)
                    }
                }
                //remove best suggestion stack last element if last element is with lastCode
                if (bestSuggestionStack != null && !bestSuggestionStack!!.isEmpty() && bestSuggestionStack!!.lastElement()!!.second == lastCode) {
                    bestSuggestionStack!!.pop()
                }
                // If nothing remains at the current depth, clear runtime state to avoid stale suggestions
                if (suggestionLoL!!.all { it.isEmpty() } && bestSuggestionStack != null
                ) {
                    bestSuggestionStack!!.clear()
                }
            }
        }
        lastCode = code


        if (DEBUG || dumpRunTimeSuggestion) Log.i(
            TAG,
            "makeRunTimeSuggestion(): Finish checking for the composing is start over or user pressed backspace. Time elapsed  = " + (System.currentTimeMillis() - startTime)
        )


        //15,6,8  Jeremy. Check exact match records first.
        if (completeCodeResultList != null && !completeCodeResultList.isEmpty() && completeCodeResultList.get(
                0
            )!!.isExactMatchToCodeRecord()
        ) {
            var exactMatchMapping: Mapping
            var k = 0
            var highestScore = 0
            var initialSize: Int = suggestionLoL!!.size
            var highestScoreIndex = initialSize
            var suggestLoLSnapshot: MutableList<MutableList<Pair<Mapping?, String?>>>? = null
            do {
                exactMatchMapping = completeCodeResultList.get(k)!!
                var score = exactMatchMapping.getBasescore()
                if (score < MIN_SCORE_THRESHOLD) {
                    score = MIN_SCORE_THRESHOLD
                } else if (score > MAX_SCORE_THRESHOLD) {
                    score = MAX_SCORE_THRESHOLD
                }
                val codeLenBonus: Int =
                    exactMatchMapping.getCode()!!.length / exactMatchMapping.getWord()!!.length * CODE_LENGTH_BONUS_MULTIPLIER
                val newScore = score + codeLenBonus

                exactMatchMapping.setBasescore(newScore * exactMatchMapping.getWord()!!.length)

                if (DEBUG || dumpRunTimeSuggestion) Log.i(
                    TAG, ("makeRunTimeSuggestion() complete code = " + code +
                            ", got exact match  = " + exactMatchMapping.getWord()
                            + " score =" + exactMatchMapping.getScore() + ", bases core=" + exactMatchMapping.getBasescore()
                            + ", time elapsed  =" + (System.currentTimeMillis() - startTime))
                )


                //push the exact match mapping with current code into exact match stack. '15,6,2 Jeremy
                if (exactMatchMapping.getBasescore() > 0) {
                    if (k == 0 && exactMatchMapping.getWord()!!.length > 1) { //clear all previous traces if exact match phrase found
                        suggestLoLSnapshot = LinkedList<MutableList<Pair<Mapping?, String?>>>()
                        for (lpm in suggestionLoL!!) {
                            suggestLoLSnapshot.add(LinkedList<Pair<Mapping?, String?>>(lpm))
                            lpm.clear()
                        }
                        suggestionLoL!!.clear()
                        initialSize = 0
                    }

                    if (newScore > highestScore) {
                        highestScore = newScore
                        highestScoreIndex = k + initialSize
                    }
                    val suggestionList: MutableList<Pair<Mapping?, String?>> =
                        LinkedList<Pair<Mapping?, String?>>()

                    //trace back to mappings in snapshot if the exact matching word is start with it.
                    if (suggestLoLSnapshot != null) {
                        for (i in suggestLoLSnapshot.indices) {
                            if (!suggestLoLSnapshot.get(i)
                                    .isEmpty() && exactMatchMapping.getWord()!!.startsWith(
                                    suggestLoLSnapshot.get(i).get(0)!!.first!!.getWord()!!
                                )
                            ) {
                                suggestionList.add(suggestLoLSnapshot.get(i).get(0))
                                if (suggestLoLSnapshot.get(i).size > 1) {
                                    for (j in 1..<suggestLoLSnapshot.get(i).size) {
                                        if (exactMatchMapping.getWord()!!.startsWith(
                                                suggestLoLSnapshot.get(i)
                                                    .get(j)!!.first!!.getWord()!!
                                            )
                                        ) suggestionList.add(suggestLoLSnapshot.get(i).get(j))
                                    }
                                }
                            }
                        }
                    }

                    suggestionList.add(Pair<Mapping?, String?>(exactMatchMapping, code))
                    suggestionLoL!!.add(suggestionList)
                }
                k++
                if (DEBUG || dumpRunTimeSuggestion) Log.i(
                    TAG,
                    "makeRunTimeSuggestion(): Check  " + k + "th exact match records. Time elapsed  = " + (System.currentTimeMillis() - startTime)
                )
            } while (completeCodeResultList.size > k && completeCodeResultList.get(k)!!
                    .isExactMatchToCodeRecord() && k < 5
            ) //process at most 5 exact match items.


            // clear suggestLoLSnapshot if it's not empty
            if (suggestLoLSnapshot != null) {
                for (lpm in suggestLoLSnapshot) {
                    lpm.clear()
                }
                suggestLoLSnapshot.clear()
            }
            if (!suggestionLoL!!.isEmpty() && highestScoreIndex != suggestionLoL!!.size - 1) { //move bestSuggestionList to the last element
                val bestSuggestionList: MutableList<Pair<Mapping?, String?>> =
                    suggestionLoL!!.removeAt(highestScoreIndex)
                suggestionLoL!!.add(bestSuggestionList)
            }
        } else {
            checkNotNull(suggestionLoL)
            if (!suggestionLoL!!.isEmpty()) {  // no exact match recoreds found. search remaining code

                if (DEBUG || dumpRunTimeSuggestion) Log.i(
                    TAG,
                    "makeRunTimeSuggestion() no exact match on complete code = " + code + ", time elapsed = " + (System.currentTimeMillis() - startTime)
                )

                var highestScore = 0
                var highestRelatedScore = 0
                var i = 0
                var highestScoreIndex = 0
                //iterate all previous exact match mapping and check for exact match on remaining code.
                val suggestionLoLSnapShot: MutableList<MutableList<Pair<Mapping?, String?>>> =
                    LinkedList<MutableList<Pair<Mapping?, String?>>>(
                        suggestionLoL!!
                    )
                for (suggestionList in suggestionLoLSnapShot) {
                    val seedSuggestionList: MutableList<Pair<Mapping?, String?>> =
                        suggestionLoL!!.removeAt(0)
                    if (highestScoreIndex > 0) highestScoreIndex--
                    val lolSize: Int = suggestionLoL!!.size

                    for (p in suggestionList) {
                        val pCode: String = p!!.second!!
                        if (pCode.length < code.length && code.startsWith(pCode) && code.length - pCode.length <= maxCodeLength) {
                            val remainingCode = code.substring(pCode.length)
                            if (DEBUG || dumpRunTimeSuggestion) Log.i(
                                TAG,
                                "makeRunTimeSuggestion() working on previous exact match item = " + p.first!!.getWord() +
                                        " with base score = " + p.first!!.getBasescore() + ", average score = " + p.first!!.getBasescore() / p.first!!.getWord()!!.length +
                                        ", remainingCode =" + remainingCode + " , highestScoreIndex = " + highestScoreIndex + ", time elapsed =" + (System.currentTimeMillis() - startTime)
                            )


                            val resultList =  //do remaining code query
                                getMappingByCodeFromCacheOrDB(remainingCode, false)
                            if (resultList == null) continue

                            if (DEBUG || dumpRunTimeSuggestion) Log.i(
                                TAG,
                                "makeRunTimeSuggestion() finish query on previous exact match item = " + p.first!!.getWord() +
                                        " , time elapsed =" + (System.currentTimeMillis() - startTime)
                            )

                            if (!resultList.isEmpty()
                                && resultList.get(0)!!.isExactMatchToCodeRecord()
                            ) {  //remaining code search got exact match
                                val remainingCodeExactMatchMapping: Mapping = resultList.get(0)!!
                                val previousMapping: Mapping = p.first!!
                                val phrase =
                                    previousMapping.getWord() + remainingCodeExactMatchMapping.getWord()
                                val phraseLen: Int = codePointLength(phrase)
                                if (phraseLen < 2 || remainingCodeExactMatchMapping.getBasescore() < 2) continue
                                var remainingScore = remainingCodeExactMatchMapping.getBasescore()
                                val codeLenBonus: Int =
                                    remainingCodeExactMatchMapping.getCode()!!.length /
                                            remainingCodeExactMatchMapping.getWord()!!.length * CODE_LENGTH_BONUS_MULTIPLIER
                                if (remainingScore > MIN_SCORE_THRESHOLD) remainingScore =
                                    MIN_SCORE_THRESHOLD
                                remainingScore =
                                    remainingScore / remainingCodeExactMatchMapping.getWord()!!.length + codeLenBonus

                                val previousScore =
                                    previousMapping.getBasescore() / previousMapping.getWord()!!.length
                                val averageScore = (previousScore + remainingScore) / 2

                                if (DEBUG || dumpRunTimeSuggestion) Log.i(
                                    TAG,
                                    ("makeRunTimeSuggestion() remaining code = " + remainingCode +
                                            ", got exact match  = " + remainingCodeExactMatchMapping.getWord() + " with base score = "
                                            + remainingScore + " average score =" + averageScore + " , highestScoreIndex = " + highestScoreIndex + ", time elapsed =" + (System.currentTimeMillis() - startTime))
                                )

                                //verify if the new phrase is in related table.
                                // check up to four characters phrase 1-3, 1-2 , 1-1
                                var relatedMapping: Mapping? = null
                                for (k in (if (phraseLen < 4) phraseLen - 1 else 3) downTo 1) {
                                    val relatedWords: Array<String> =
                                        splitRelatedPhraseTail(phrase, k)
                                    val pword = relatedWords[0]
                                    val cword = relatedWords[1]
                                    relatedMapping = dbadapter!!.isRelatedPhraseExist(pword, cword)
                                    if (relatedMapping != null) break
                                }
                                if (relatedMapping != null && relatedMapping.getBasescore() >= highestRelatedScore && (averageScore + SCORE_ADJUSTMENT_INCREMENT) > highestScore
                                ) {
                                    val suggestMapping = Mapping()
                                    suggestMapping.setRuntimeBuiltPhraseRecord()
                                    suggestMapping.setCode(code)
                                    suggestMapping.setWord(phrase)
                                    highestRelatedScore = relatedMapping.getBasescore()
                                    suggestMapping.setScore(highestRelatedScore)
                                    highestScore = (averageScore + SCORE_ADJUSTMENT_INCREMENT)
                                    suggestMapping.setBasescore(highestScore * phraseLen)
                                    val newSuggestionList: MutableList<Pair<Mapping?, String?>> =
                                        LinkedList<Pair<Mapping?, String?>>(seedSuggestionList)
                                    newSuggestionList.add(
                                        Pair<Mapping?, String?>(
                                            suggestMapping,
                                            code
                                        )
                                    )
                                    suggestionLoL!!.add(newSuggestionList)
                                    highestScoreIndex = suggestionLoL!!.size - 1
                                    if (DEBUG || dumpRunTimeSuggestion) Log.i(
                                        TAG,
                                        ("makeRunTimeSuggestion()  run-time suggest phrase verified from related table ="
                                                + phrase + ", basescore from related table = " + highestRelatedScore + " " +
                                                ", new average score = " + highestScore + " , highestScoreIndex = " + highestScoreIndex + ", time elapsed =" + (System.currentTimeMillis() - startTime))
                                    )
                                } else if ( //highestRelatedScore == 0 &&// no mapping is verified from related table
                                    averageScore > highestScore) {
                                    val suggestMapping = Mapping()
                                    suggestMapping.setRuntimeBuiltPhraseRecord()
                                    suggestMapping.setCode(code)
                                    suggestMapping.setWord(phrase)
                                    highestScore = averageScore
                                    suggestMapping.setBasescore(highestScore * phraseLen)

                                    val newSuggestionList: MutableList<Pair<Mapping?, String?>> =
                                        LinkedList<Pair<Mapping?, String?>>(seedSuggestionList)
                                    newSuggestionList.add(
                                        Pair<Mapping?, String?>(
                                            suggestMapping,
                                            code
                                        )
                                    )
                                    suggestionLoL!!.add(newSuggestionList)
                                    highestScoreIndex = suggestionLoL!!.size - 1

                                    if (DEBUG || dumpRunTimeSuggestion) Log.i(
                                        TAG,
                                        ("makeRunTimeSuggestion()  run-time suggest phrase =" + phrase
                                                + ", new average score = " + highestScore + " , highestScoreIndex = " + highestScoreIndex + ", time elapsed =" + (System.currentTimeMillis() - startTime))
                                    )
                                }
                            }
                        }
                    }
                    if (lolSize == suggestionLoL!!.size) {
                        suggestionLoL!!.add(seedSuggestionList)
                        if (DEBUG || dumpRunTimeSuggestion) Log.i(
                            TAG,
                            "makeRunTimeSuggestion()  no new suggestion list. add back the seed suggestion list to location 0 because of last run."
                        )
                    }
                    i++
                    if (DEBUG || dumpRunTimeSuggestion) Log.i(
                        TAG,
                        "makeRunTimeSuggestion() : remaing cod search +" + i + "th run.  time elapsed = " + (System.currentTimeMillis() - startTime)
                    )
                }
                if (!suggestionLoL!!.isEmpty() && highestScoreIndex != suggestionLoL!!.size - 1) { //move bestSuggestionList to the last element
                    val bestSuggestionList: MutableList<Pair<Mapping?, String?>> =
                        suggestionLoL!!.removeAt(highestScoreIndex)
                    suggestionLoL!!.add(bestSuggestionList)
                }
            }
        }

        //push best suggestion to stack
        val bestSuggestionList: MutableList<Pair<Mapping?, String?>>?
        if (!suggestionLoL!!.isEmpty()) {
            bestSuggestionList = suggestionLoL!!.get(suggestionLoL!!.size - 1)
            if (!bestSuggestionList.isEmpty()) {
                bestSuggestionStack!!.push(bestSuggestionList.get(bestSuggestionList.size - 1))
            }
        }

        /*
        //find confirmed best suggestion with longest common string
        if (bestSuggestionStack != null && !bestSuggestionStack.isEmpty() && bestSuggestionStack.size() > 1) {
            for (int i = bestSuggestionStack.size() - 1; i > 0; i--) {
                if (code.length() - bestSuggestionStack.get(i).first.getCode().length() > maxCodeLength) {
                    String lastBestSuggestion = bestSuggestionStack.get(i - 1).first.getWord(), bestSuggestion = bestSuggestionStack.get(i).first.getWord();
                    if (lastBestSuggestion != null &&
                            lastBestSuggestion.length() > 1 && bestSuggestion.length() >= lastBestSuggestion.length()) {
                        String tempBestSuggestion = lcs(lastBestSuggestion, bestSuggestion);
                        if (confirmedBestSuggestion == null) {
                            confirmedBestSuggestion = tempBestSuggestion;
                        } else if (lastConfirmedBestSuggestion == null
                                || tempBestSuggestion.length() > lastConfirmedBestSuggestion.length()) {
                            lastConfirmedBestSuggestion = confirmedBestSuggestion;
                            confirmedBestSuggestion = tempBestSuggestion;
                        }
                    }
                    break;
                }
            }
            if ((DEBUG || dumpRunTimeSuggestion)) {
                if (lastConfirmedBestSuggestion != null)
                    Log.i(TAG, "makeRunTimeSuggestion() last confirmed best suggestion = " + lastConfirmedBestSuggestion);
                if (confirmedBestSuggestion != null)
                    Log.i(TAG, "makeRunTimeSuggestion() confirmed best suggestion = " + confirmedBestSuggestion);
                if (!bestSuggestionStack.isEmpty()) {
                    int i = 0;
                    for (Pair<Mapping, String> it : bestSuggestionStack) {
                        Log.i(TAG, "makeRunTimeSuggestion() best suggestion stack (" + (i) + ")= " + bestSuggestionStack.get(i).first.getWord());
                        i++;
                    }
                }
            }
        }
        */

        // dump suggestion list of list
        //if ((DEBUG || dumpRunTimeSuggestion) &&
        //    suggestionLoL != null && !suggestionLoL.isEmpty()) {
        //for (int i = 0; i < suggestionLoL.size(); i++) {
        //if (suggestionLoL.get(i) != null && !suggestionLoL.get(i).isEmpty()) {
        //for (int j = 0; j < suggestionLoL.get(i).size(); j++) {
        //Log.i(TAG, "makeRunTimeSuggestion() suggestionLoL(" + i + ")(" + j + "): word="
        //        + suggestionLoL.get(i).get(j).first.getWord() + ", code=" + suggestionLoL.get(i).get(j).second
        //        + ", base score=" + suggestionLoL.get(i).get(j).first.getBasescore()
        //        + ", average base score=" + suggestionLoL.get(i).get(j).first.getBasescore() / suggestionLoL.get(i).get(j).first.getWord().length()
        //        + ", score=" + suggestionLoL.get(i).get(j).first.getScore());
        //}
        //}
        // }

        //Log.i(TAG,"makeRunTimeSuggestion() time elapsed = " +  (System.currentTimeMillis()- startTime ) );
        //}
    }

    /*
    *   return longest common substring with recursive method.
     */
    /**
     * Computes the Longest Common Substring (LCS) of two strings.
     * 
     * @param a First string.
     * @param b Second string.
     * @return The longest common substring.
     */
    fun lcs(a: String, b: String): String {
        val aLen = a.length
        val bLen = b.length
        if (aLen == 0 || bLen == 0) {
            return ""
        } else if (a.get(aLen - 1) == b.get(bLen - 1)) {
            return (lcs(a.substring(0, aLen - 1), b.substring(0, bLen - 1))
                    + a.get(aLen - 1))
        } else {
            val x = lcs(a, b.substring(0, bLen - 1))
            val y = lcs(a.substring(0, aLen - 1), b)
            return if (x.length > y.length) x else y
        }
    }

    /*
    * Jeremy '15,7,12 synchronized the method called from LIMEService only
    */
    /**
     * Retrieves a list of candidate mappings for a given input code.
     * 
     * 
     * This is an overload for [.getMappingByCode].
     * 
     * @param code          The input code to search for.
     * @param softkeyboard  True if input is from software keyboard, false for hardware.
     * @param getAllRecords True to retrieve all matching records, false for a limited set.
     * @return A list of matching [Mapping] objects.
     * @throws RemoteException If a database error occurs.
     */
    @Synchronized
    @Throws(RemoteException::class)
    fun getMappingByCode(
        code: String?,
        softkeyboard: Boolean,
        getAllRecords: Boolean
    ): MutableList<Mapping?> {
        return getMappingByCode(code, softkeyboard, getAllRecords, false)
    }

    /**
     * Converts a code to its corresponding Emoji mappings.
     * 
     * 
     * Results are cached in `emojicache` to optimize repeated lookups.
     * 
     * @param code The code to convert.
     * @param type The type of conversion (internal DB parameter).
     * @return A list of Emoji mappings.
     */
    fun emojiConvert(code: String?, type: Int): MutableList<Mapping?>? {
        if (code != null) {
            if (emojicache == null) {
                emojicache =
                    ConcurrentHashMap<String?, MutableList<Mapping?>?>(LIME.SEARCHSRV_RESET_CACHE_SIZE)
            }
            val cacheKey = type.toString() + ":" + code
            var results: MutableList<Mapping?>? = emojicache!!.get(cacheKey)
            if (results == null) {
                results = dbadapter!!.emojiConvert(code, type)
                emojicache!!.put(cacheKey, results)
            }
            return results
        }
        return null
    }

    fun findEmojiForCandidate(
        code: String?,
        locale: EmojiLocale,
        limit: Int
    ): MutableList<Mapping?>? {
        if (code != null) {
            if (emojicache == null) {
                emojicache =
                    ConcurrentHashMap<String?, MutableList<Mapping?>?>(LIME.SEARCHSRV_RESET_CACHE_SIZE)
            }
            val cacheKey = "v2:" + locale.name + ":" + code + ":" + limit
            var results: MutableList<Mapping?>? = emojicache!!.get(cacheKey)
            if (results == null) {
                results = dbadapter!!.findEmojiForCandidate(code, locale, limit)
                emojicache!!.put(cacheKey, results)
            }
            return results
        }
        return null
    }

    fun searchEmoji(query: String?, locale: EmojiLocale, limit: Int): MutableList<Mapping?>? {
        if (query != null) {
            if (emojicache == null) {
                emojicache =
                    ConcurrentHashMap<String?, MutableList<Mapping?>?>(LIME.SEARCHSRV_RESET_CACHE_SIZE)
            }
            val cacheKey = "search:" + locale.name + ":" + query + ":" + limit
            var results: MutableList<Mapping?>? = emojicache!!.get(cacheKey)
            if (results == null) {
                results = dbadapter!!.searchEmoji(query, locale, limit)
                emojicache!!.put(cacheKey, results)
            }
            return results
        }
        return null
    }

    fun loadRecentEmoji(limit: Int): MutableList<Mapping?> {
        if (emojicache == null) {
            emojicache =
                ConcurrentHashMap<String?, MutableList<Mapping?>?>(LIME.SEARCHSRV_RESET_CACHE_SIZE)
        }
        val cacheKey = "recent:" + limit
        var results: MutableList<Mapping?>? = emojicache!!.get(cacheKey)
        if (results == null) {
            results = dbadapter!!.loadRecentEmoji(limit)
            emojicache!!.put(cacheKey, results)
        }
        return results
    }

    @Synchronized
    fun loadEmojiCategoryPages(): MutableList<MutableList<String?>?> {
        if (emojiCategoryPagesCache != null) {
            return copyEmojiCategoryPages(emojiCategoryPagesCache)
        }

        val pages: MutableList<MutableList<String?>?> = ArrayList<MutableList<String?>?>()
        val db: LimeDB? = dbadapter
        if (db == null) {
            return pages
        }
        val recent = loadRecentEmoji(32)
        pages.add(mappingWords(recent))
        pages.addAll(db.loadEmojiCategoryPages())
        emojiCategoryPagesCache = copyEmojiCategoryPages(pages)
        return pages
    }

    fun hasEmojiCategoryPagesCache(): Boolean {
        return emojiCategoryPagesCache != null
    }

    val isEmojiCategoryPreloadRunning: Boolean
        get() = emojiPreloadThread != null && emojiPreloadThread!!.isAlive()

    fun preloadEmojiCategoryPages() {
        if (this.isEmojiCategoryPreloadRunning) return
        emojiPreloadThread = Thread(Runnable {
            try {
                loadEmojiCategoryPages()
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading emoji category pages", e)
            }
        }, "emoji-category-preload")
        emojiPreloadThread!!.start()
    }

    fun recordEmojiUsage(value: String?) {
        dbadapter!!.recordEmojiUsage(value, System.currentTimeMillis() / 1000L)
        if (emojicache != null) {
            emojicache!!.clear()
        }
        emojiCategoryPagesCache = null
    }

    /**
     * Learn a picked English suggestion by incrementing its score in the scored dictionary.
     * Delegates to LimeDB (UPDATE-only, +1). See docs/ENG_AUTO_COMPLETION.md "Learning".
     */
    fun recordEnglishUsage(word: String?) {
        if (dbadapter != null) {
            dbadapter!!.recordEnglishUsage(word)
        }
    }

    /**
     * Core method to retrieve mappings for a code from cache or database.
     * 
     * 
     * Handles complex logic including:
     * 
     *  * Cache lookup and management.
     *  * Runtime phrase suggestion generation.
     *  * English word suggestion fallback.
     *  * Result sorting and filtering.
     * 
     * 
     * @param code           The input code.
     * @param softkeyboard   True if soft keyboard.
     * @param getAllRecords  True to fetch all records.
     * @param prefetchCache  True if this request is a background prefetch.
     * @return List of mappings.
     * @throws RemoteException If database fails.
     */
    @Throws(RemoteException::class)
    fun getMappingByCode(
        code: String?,
        softkeyboard: Boolean,
        getAllRecords: Boolean,
        prefetchCache: Boolean
    ): MutableList<Mapping?> {
        var code = code
        if (DEBUG || dumpRunTimeSuggestion) Log.i(TAG, "getMappingByCode(): code=" + code)

        // Handle null or empty code gracefully
        if (code == null || code.isEmpty()) {
            if (DEBUG) Log.w(TAG, "getMappingByCode(): code is null or empty, returning empty list")
            return LinkedList<Mapping?>()
        }

        // Handle null dbadapter gracefully (e.g., when SearchServer created with null context)
        if (mLIMEPref == null || dbadapter == null) {
            if (DEBUG) Log.w(
                TAG,
                "getMappingByCode(): mLIMEPref or dbadapter is null, returning empty list"
            )
            return LinkedList<Mapping?>()
        }

        // Check if system need to reset cache

        //check reset cache with local variable instead of reading from shared preference for better performance
        if (mResetCache) {
            initialCache()
            mResetCache = false
        }

        //codeLengthMap.clear();//Jeremy '12,6,2 reset the codeLengthMap
        val result: MutableList<Mapping?> = LinkedList<Mapping?>()
        // clear mappingidx when user switching between softkeyboard and hard keyboard. Jeremy '11,6,11
        if (isPhysicalKeyboardPressed == softkeyboard) isPhysicalKeyboardPressed = !softkeyboard

        // Jeremy '11,9, 3 remove cached keyname when request full records
        if (getAllRecords && keynamecache!!.get(cacheKey(code)) != null) keynamecache!!.remove(
            cacheKey(code)
        )

        val size = code.length


        //boolean hasMore = false;


        // 12,6,4 Jeremy. Ascending a ab abc... looking up db if the cache is not exist
        //'15,6,4 Jeremy. Do exact search only in between search mode (1 time only).
        val resultList = getMappingByCodeFromCacheOrDB(code, getAllRecords)


        //Jeremy '15,7,16 reset abandonPhraseSuggestion if code length ==1
        // Skip for prefetch queries — they are background cache warming, not real user input,
        // and must not disturb the phrase suggestion state.
        if (!prefetchCache && mLIMEPref!!.getSmartChineseInput() && abandonPhraseSuggestion && code.length == 1) {
            clearRunTimeSuggestion(false)
        }
        // make run-time suggestion '15, 6, 9 Jeremy.
        if (!abandonPhraseSuggestion && !prefetchCache && mLIMEPref!!.getSmartChineseInput()) {
            makeRunTimeSuggestion(code, resultList)
        }

        // 12,6,4 Jeremy. Descending  abc ab a... Build the result candidate list.
        //'15,6,4 Jeremy. Do exact search only in between search mode.
        //for (int i = 0; i < ((LimeDB.getBetweenSearch()) ? 1 : size); i++) {
        val cacheKey = cacheKey(code)
        var cacheTemp: MutableList<Mapping?>? = cache!!.get(cacheKey)


        if (cacheTemp != null) {
            val resultlist: MutableList<Mapping?>? = cacheTemp

            //if getAllRecords is true and result list or related list has has more mark in the end
            // recall LimeDB.GetMappingByCode with getAllRecords true.
            if (getAllRecords && resultlist!!.size > 1 && resultlist.get(resultlist.size - 1)!!
                    .isHasMoreRecordsMarkRecord()
            ) {
                try {
                    cacheTemp = dbadapter!!.getMappingByCode(code, !isPhysicalKeyboardPressed, true)
                    cache!!.remove(cacheKey)
                    cache!!.put(cacheKey, cacheTemp)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in search operation", e)
                }
            }
        }

        if (cacheTemp != null) {
            var resultlist: MutableList<Mapping?> = cacheTemp

            //List<Mapping> relatedtlist = cacheTemp.second;
            if (DEBUG || dumpRunTimeSuggestion) Log.i(
                TAG,
                "getMappingByCode() code=" + code + " resultlist.size()=" + resultlist.size + ", abandonPhraseSuggestion:" + abandonPhraseSuggestion
            )


            //if (i == 0) {
            if (resultlist.isEmpty() && code.length > 1) {
                //If the result list is empty we need to go back to last result list with nonzero result list
                var wayBackCode: String? = code
                do {
                    wayBackCode = wayBackCode!!.substring(0, wayBackCode.length - 1)
                    cacheTemp = cache!!.get(cacheKey(wayBackCode))
                    if (cacheTemp != null) resultlist = cacheTemp
                } while (resultlist.isEmpty() && wayBackCode.length > 1)
            }


            val self = Mapping()
            self.setWord(code)
            self.setCode(code)
            self.setComposingCodeRecord()

            // put run-time built suggestion if it's present
            /*List<Pair<Mapping, String>> bestSuggestionList = null;
                    Mapping bestSuggestion = null;
                    if (!suggestionLoL.isEmpty()) {
                        bestSuggestionList = suggestionLoL.get(suggestionLoL.size() - 1);
                    }
                    if (bestSuggestionList != null && !bestSuggestionList.isEmpty()) {
                        bestSuggestion = bestSuggestionList.get(bestSuggestionList.size() - 1).first;
                    }*/

            //Jeremy '15,7,16 check english suggestion if code length > maxCodeLength
            var englishSuggestion: Mapping? = null
            if (code.length > maxCodeLength) {
                val englishSuggestions = getEnglishSuggestions(code)
                if (!englishSuggestions.isEmpty()) {
                    englishSuggestion = englishSuggestions.get(0)
                    englishSuggestion!!.setRuntimeBuiltPhraseRecord()
                    englishSuggestion.setCode(code)
                }
            }


            var bestSuggestion: Mapping? = null
            if (bestSuggestionStack != null && !bestSuggestionStack!!.isEmpty()) {
                bestSuggestion = bestSuggestionStack!!.lastElement()!!.first
            }
            var averageScore = 0
            var bestSuggestionLength = 0
            if (bestSuggestion != null) {
                val bestSuggestionWord = bestSuggestion.getWord()
                bestSuggestionLength =
                    if (bestSuggestionWord == null) 0 else bestSuggestionWord.length
                if (bestSuggestionLength > 0) {
                    averageScore = bestSuggestion.getBasescore() / bestSuggestionLength
                }
            }
            val bestSuggestionDuplicatesResultList =
                bestSuggestion != null && mappingListContainsWord(
                    resultlist,
                    bestSuggestion.getWord()
                )

            if (bestSuggestion != null // the last element is run-time built suggestion from remaining code query
                && !abandonPhraseSuggestion && !bestSuggestion.isExactMatchToCodeRecord() //will be the first item of result list, dont' add duplicated item
                && !bestSuggestionDuplicatesResultList && bestSuggestionLength > 1 && ((englishSuggestion == null && averageScore > MIN_SCORE_THRESHOLD) || (englishSuggestion != null && averageScore > MAX_SCORE_THRESHOLD))
            ) {
                result.add(self)
                result.add(bestSuggestion)
            } else if (englishSuggestion != null && averageScore <= MAX_SCORE_THRESHOLD) {
                clearRunTimeSuggestion(true)
                result.add(self)
                result.add(englishSuggestion)
            } else {
                // put self into the first mapping for mixed input.
                result.add(self)
            }

            // }
            if (!resultlist.isEmpty()) {
                result.addAll(resultlist)
                /*
                int rsize = result.size();
                if (result.get(rsize - 1).isHasMoreRecordsMarkRecord()) {
                    //do not need to touch the has more record in between search mode. Jeremy '15,6,4
                    result.remove(rsize - 1);
                    hasMore = true;

                    }
                    */
                if (DEBUG) Log.i(
                    TAG,
                    ("getMappingByCode() code=" + code + "  result list added resultlist.size()="
                            + resultlist.size)
                )
            }
        }

        //codeLengthMap is deprecated and replace by exact match stack scheme '15,6,3 jeremy
        //codeLengthMap.add(new Pair<>(code.length(), result.size()));  //Jeremy 12,6,2 preserve the code length in each loop.
        //if (DEBUG) 	Log.i(TAG, "getMappingByCode() codeLengthMap  code length = " + code.length() + ", result size = " + result.size());
        code = code.substring(0, code.length - 1)
        if (DEBUG) Log.i(TAG, "getMappingByCode() code=" + code + " result.size()=" + result.size)

        return result
    }

    /**
     * Retrieves the mapping list from cache, or queries the database if not found.
     * 
     * 
     * Separated from `getMappingByCode` to modularize cache/DB logic.
     * 
     * @param queryCode     The code to look up.
     * @param getAllRecords Whether to retrieve all matching records.
     * @return List of mappings.
     */
    private fun getMappingByCodeFromCacheOrDB(
        queryCode: String,
        getAllRecords: Boolean
    ): MutableList<Mapping?>? {
        val db: LimeDB? = dbadapter
        if (db == null) {
            return LinkedList<Mapping?>()
        }

        val cachedKey = checkNotNull(cacheKey(queryCode))
        if (cachedKey.isEmpty()) {
            return LinkedList<Mapping?>()
        }
        var cacheTemp: MutableList<Mapping?>? = cache!!.get(cachedKey)

        if (DEBUG) Log.i(
            TAG,
            " getMappingByCode() check if cached exist on code = '" + queryCode + "'"
        )

        if (cacheTemp == null) {
            // 25/Jul/2011 by Art
            // Just ignore error when something wrong with the result set
            try {
                cacheTemp =
                    db.getMappingByCode(queryCode, !isPhysicalKeyboardPressed, getAllRecords)
                if (cacheTemp != null) cache!!.put(cachedKey, cacheTemp)
                //Jeremy '12,6,5 check if need to update code remap cache
                if (cacheTemp != null && !cacheTemp.isEmpty() && cacheTemp.get(0) != null && cacheTemp.get(
                        0
                    )!!.isExactMatchToCodeRecord()
                ) {
                    val remappedCode = cacheTemp.get(0)!!.getCode()
                    if (queryCode != remappedCode) {
                        val codeList: MutableList<String>? = coderemapcache!!.get(remappedCode)
                        val key = cacheKey(remappedCode!!)
                        if (codeList == null) {
                            val newlist: MutableList<String> = LinkedList<String>()
                            newlist.add(remappedCode) //put self in the list
                            newlist.add(queryCode)
                            coderemapcache!!.put(key, newlist)
                            if (DEBUG) Log.i(
                                TAG, ("getMappingByCode() build new remap code = '"
                                        + remappedCode + "' to code = '" + queryCode + "'"
                                        + " coderemapcache.size()=" + coderemapcache!!.size)
                            )
                        } else {
                            codeList.add(queryCode)
                            coderemapcache!!.remove(key)
                            coderemapcache!!.put(key, codeList)
                            if (DEBUG) Log.i(
                                TAG,
                                "getMappingByCode() remappedCode: add new remap code = '" + remappedCode + "' to code = '" + queryCode + "'"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in search operation", e)
            }
        }
        return cacheTemp
    }

    /**
     * Determines the actual length of the code matched by a selected mapping.
     * 
     * 
     * Useful for separating the matched portion of the input from the remaining buffer.
     * Also triggers learning of runtime-built phrases.
     * 
     * @param selectedMapping The user-selected mapping.
     * @param currentCode     The current input buffer.
     * @return The length of the code corresponding to the selection.
     */
    fun getRealCodeLength(selectedMapping: Mapping, currentCode: String): Int {
        if (DEBUG) Log.i(TAG, "getRealCodeLength()")

        val code = selectedMapping.getCode()
        var realCodeLen = code!!.length
        if (LimeDB.isCodeDualMapped) { //abandon LD support for dual mapped codes. Jeremy '15,6,5
            realCodeLen = currentCode.length
        } else {
            if (Companion.tablename == LIME.DB_TABLE_PHONETIC) {
                val selectedPhoneticKeyboardType =
                    mLIMEPref!!.getParameterString("phonetic_keyboard_type", LIME.DB_TABLE_PHONETIC)
                var lcode = currentCode
                if (selectedPhoneticKeyboardType.startsWith(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN)) {
                    lcode = dbadapter!!.preProcessingRemappingCode(currentCode)
                }
                val noToneCode = code.replace("[3467 ]".toRegex(), "")
                if (code == noToneCode) {
                    realCodeLen = code.length
                } else if (!lcode.startsWith(code) && lcode.startsWith(noToneCode)) {
                    realCodeLen = noToneCode.length
                } else {
                    realCodeLen = currentCode.length //unexpected condition.
                }
            }
        }

        //remove elements in suggestionLoL with code length smaller than current code length - submitted code length
        if (realCodeLen < currentCode.length) {
            val itl: MutableIterator<MutableList<Pair<Mapping?, String?>>> =
                suggestionLoL!!.iterator()
            while (itl.hasNext()) {
                val lpe = itl.next()
                val it: MutableIterator<Pair<Mapping?, String?>> = lpe.iterator()
                while (it.hasNext()) {
                    val pe = it.next()
                    if (pe.second!!.length > currentCode.length - realCodeLen) {
                        it.remove()
                    }
                }
                if (lpe.isEmpty()) itl.remove()
            }
            val it: MutableIterator<Pair<Mapping?, String?>> = bestSuggestionStack!!.iterator()
            while (it.hasNext()) {
                val pe = it.next()
                if (pe.second!!.length > currentCode.length - realCodeLen) {
                    it.remove()
                }
            }
        }

        // learn ld phrase if the select mapping is run-time suggestion
        if (mLIMEPref != null && mLIMEPref!!.getLearnPhrase()
            && selectedMapping.isRuntimeBuiltPhraseRecord()
            && suggestionLoL != null && !suggestionLoL!!.isEmpty()
        ) {
            val bestSuggestionList: MutableList<Pair<Mapping?, String?>> =
                LinkedList<Pair<Mapping?, String?>>(
                    suggestionLoL!!.get(suggestionLoL!!.size - 1)
                )
            val selectedWord: String = selectedMapping.getWord()!!

            val learnLDPhraseThread: Thread = object : Thread() {
                override fun run() {
                    if (!bestSuggestionList.isEmpty()) {
                        for (j in bestSuggestionList.indices) {
                            //TODO:should learn QP code for phonetic table
                            if (selectedWord.startsWith(bestSuggestionList.get(j)!!.first!!.getWord()!!)) {
                                if (bestSuggestionList.get(j)!!.first!!.getWord()!!.length > 8) break //stop learning if word length > 8

                                dbadapter!!.addOrUpdateMappingRecord(
                                    bestSuggestionList.get(j)!!.second!!,
                                    bestSuggestionList.get(j)!!.first!!.getWord()!!
                                )
                                removeRemappedCodeCachedMappings(bestSuggestionList.get(j)!!.second!!)
                            }

                            if ((DEBUG || dumpRunTimeSuggestion))  // dump best suggestion list
                                Log.i(
                                    TAG,
                                    ("getRealCodeLength() best suggestion list(" + j + "): word="
                                            + bestSuggestionList.get(j)!!.first!!.getWord() + ", code=" + bestSuggestionList.get(
                                        j
                                    )!!.second)
                                )
                        }
                    }
                }
            }
            learnLDPhraseThread.start()
        }

        return realCodeLen
    }


    /**
     * Initializes or resets all internal caches (mappings, English words, emojis, etc.).
     * 
     * 
     * Clears existing caches and allocates new concurrent hashmaps.
     */
    fun initialCache() {
        try {
            clear()
        } catch (e: RemoteException) {
            Log.e(TAG, "Error in search operation", e)
        }
        cache = ConcurrentHashMap<String?, MutableList<Mapping?>?>(LIME.SEARCHSRV_RESET_CACHE_SIZE)
        engcache =
            ConcurrentHashMap<String?, MutableList<Mapping?>?>(LIME.SEARCHSRV_RESET_CACHE_SIZE)
        emojicache =
            ConcurrentHashMap<String?, MutableList<Mapping?>?>(LIME.SEARCHSRV_RESET_CACHE_SIZE)
        emojiCategoryPagesCache = null
        keynamecache = ConcurrentHashMap<String?, String?>(LIME.SEARCHSRV_RESET_CACHE_SIZE)
        coderemapcache =
            ConcurrentHashMap<String?, MutableList<String>?>(LIME.SEARCHSRV_RESET_CACHE_SIZE)

        //  initial exact match stack here
        suggestionLoL = LinkedList<MutableList<Pair<Mapping?, String?>>>()
        bestSuggestionStack = Stack<Pair<Mapping?, String?>>()
    }


    /**
     * Updates the score of a cached mapping and evicts then re-warms affected
     * cache entries from the database.
     * 
     * 
     * Mirrors the iOS evict-and-re-warm pattern: instead of mutating the in-memory
     * `ArrayList` on a background thread (which caused a race and produced an
     * approximated sort order), this evicts the exact-match entry and its prefixes,
     * then re-queries the DB so the rewarmed cache reflects the true `ORDER BY`.
     * 
     * @param cachedMapping The mapping with the updated score.
     */
    private fun updateScoreCache(cachedMapping: Mapping) {
        if (DEBUG) Log.i(TAG, "updateScoreCache(): code=" + cachedMapping.getCode())

        dbadapter!!.addScore(cachedMapping)
        // Jeremy '11,7,29 update cached here
        if (!cachedMapping.isRelatedPhraseRecord()) {
            val code = cachedMapping.getCode()!!.lowercase()
            val cachekey = cacheKey(code)

            // Always evict the full-code cache entry (harmless if not cached) and the
            // remapped-code mappings for this code. DB score just changed, so any
            // in-memory copy under this key is stale.
            if (cache!!.remove(cachekey) == null) {
                removeRemappedCodeCachedMappings(code)
            }

            // Always evict every prefix cache entry and re-warm each from the DB.
            // This is required even when the full-code cache has no entry — which
            // is the common partial-match case: user typed "g", picked 也
            // (code="gds"). The stale score lives in cache["g"], not cache["gds"].
            // Without this, the next lookup of "g" returns the stale Mapping and
            // addScore() writes back the same stale score + 1, so the DB appears
            // "stuck" at the first bump. Issue #49 follow-up.
            val evictedPrefixes = updateSimilarCodeCache(code)
            try {
                getMappingByCode(code, !isPhysicalKeyboardPressed, false, true)
                for (prefix in evictedPrefixes) {
                    getMappingByCode(prefix, !isPhysicalKeyboardPressed, false, true)
                }
            } catch (e: RemoteException) {
                Log.e(TAG, "updateScoreCache(): re-warm failed", e)
            }
        }
    }

    // '11,8,1 renamed from updateuserdict()
    var scorelistSnapshot: MutableList<Mapping?>? = null

    /**
     * Tasks to perform after an input session finishes.
     * 
     * 
     * Spawns a background thread to:
     * 
     *  * Learn related phrases from the recent score list.
     *  * Update user dictionary/scores.
     *  * Process LD phrases if applicable.
     * 
     * 
     * @throws RemoteException If a database error occurs.
     */
    @Throws(RemoteException::class)
    fun postFinishInput() {
        if (scorelistSnapshot == null) scorelistSnapshot = LinkedList<Mapping?>()
        else scorelistSnapshot!!.clear()


        if (DEBUG) Log.i(TAG, "postFinishInput(), creating offline updating thread")
        // Jeremy '11,7,31 The updating process takes some time. Create a new thread to do this.
        val UpdatingThread: Thread = object : Thread() {
            override fun run() {
                // for thread-safe operation, duplicate local copy of scorelist and LDphraselistarray
                synchronized(scorelist!!) {
                    scorelistSnapshot!!.addAll(scorelist)
                    scorelist.clear()
                }

                //Jeremy '11,7,28 combine to adduserdict and addscore
                //Jeremy '11,6,12 do adduserdict and add score if diclist.size > 0 and only adduserdict if diclist.size >1
                //Jeremy '11,6,11, always learn scores, but sorted according preference options

                // Learn the consecutive two words as a related phrase).
                learnRelatedPhrase(scorelistSnapshot)

                val localLDPhraseListArray = ArrayList<MutableList<Mapping?>>()
                if (LDPhraseListArray != null) {
                    localLDPhraseListArray.addAll(LDPhraseListArray!!.filterNotNull())
                    LDPhraseListArray!!.clear()
                }

                // Learn LD Phrase
                learnLDPhrase(localLDPhraseListArray)
            }
        }
        UpdatingThread.start()
    }

    /**
     * Learns associations between consecutive words in a sentence (related phrases).
     * 
     * @param localScorelist The list of mappings selected during the session.
     */
    private fun learnRelatedPhrase(localScorelist: MutableList<Mapping?>?) {
        if (localScorelist != null) {
            if (DEBUG) Log.i(
                TAG,
                "learnRelatedPhrase(), localScorelist.size=" + localScorelist.size
            )
            if (mLIMEPref!!.getLearnRelatedWord() && localScorelist.size > 1) {
                for (i in localScorelist.indices) {
                    val unit = localScorelist.get(i)
                    if (unit == null) {
                        continue
                    }
                    if (i + 1 < localScorelist.size) {
                        val unit2 = localScorelist.get((i + 1))
                        if (unit2 == null) {
                            continue
                        }
                        if (unit.getWord() != null && !unit.getWord()!!
                                .isEmpty() && unit2.getWord() != null && !unit2.getWord()!!
                                .isEmpty() &&
                            (unit.isExactMatchToCodeRecord() || unit.isPartialMatchToCodeRecord()
                                    || unit.isRelatedPhraseRecord()) // use record type to identify records. Jeremy '15,6,4

                            &&
                            (unit2.isExactMatchToCodeRecord() || unit2.isPartialMatchToCodeRecord()
                                    || unit.isRelatedPhraseRecord() || unit2.isChinesePunctuationSymbolRecord()
                                    || unit.isEmojiRecord() || unit2.isEmojiRecord()) //allow unit2 to be chinese punctuation symbols.
                        //&& !unit.getCode().equals(unit.getWord())//Jeremy '12,6,13 avoid learning mixed mode english
                        //&& !unit2.getCode().equals(unit2.getWord())
                        /**&& unit2.getId() !=null */

                        ) {
                            val score: Int

                            //if (unit.getId() != null && unit2.getId() != null) //Jeremy '12,7,2 eliminate learning english words.
                            score = dbadapter!!.addOrUpdateRelatedPhraseRecord(
                                unit.getWord(),
                                unit2.getWord()
                            )
                            if (DEBUG) Log.i(
                                TAG,
                                "learnRelatedPhrase(), the return score = " + score
                            )
                            //Jeremy '12,6,7 learn LD phrase if the score of userdic is > 20
                            if (score > 20 && mLIMEPref!!.getLearnPhrase()) {
                                addLDPhrase(unit, false)
                                addLDPhrase(unit2, true)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Jeremy '12,6,9 Rewrite to support word with more than 1 characters
     */
    /**
     * Learns new phrases based on user input patterns (Learning Dictionary).
     * 
     * @param localLDPhraseListArray The list of potential phrases to learn.
     */
    private fun learnLDPhrase(localLDPhraseListArray: ArrayList<MutableList<Mapping?>>?) {
        if (DEBUG) Log.i(TAG, "learnLDPhrase()")

        if (localLDPhraseListArray != null && !localLDPhraseListArray.isEmpty()) {
            if (DEBUG) Log.i(
                TAG,
                "learnLDPhrase(): LDPhrase learning, arraysize =" + localLDPhraseListArray.size
            )


            for (phraselist in localLDPhraseListArray) {
                if (DEBUG) Log.i(
                    TAG,
                    "learnLDPhrase(): LDPhrase learning, current list size =" + phraselist.size
                )
                if (!phraselist.isEmpty() && phraselist.size < 5) { //Jeremy '12,6,8 limit the phrase to have 4 chracters


                    var baseCode: String?
                    var LDCode: String? = ""
                    var QPCode: String? = ""
                    var baseWord: String?

                    val unit1 = phraselist.get(0)

                    if (DEBUG) Log.i(
                        TAG, ("learnLDPhrase(): unit1.getId() = " + unit1!!.getId()
                                + ", unit1.getCode() =" + unit1.getCode()
                                + ", unit1.getWord() =" + unit1.getWord())
                    )

                    if (unit1 == null || unit1.getWord()!!.isEmpty()
                        || unit1.getCode() == unit1.getWord()
                    )  //Jeremy '12,6,13 avoid learning mixed mode english
                    {
                        break
                    }

                    baseCode = unit1.getCode()
                    baseWord = unit1.getWord()

                    if (codePointLength(baseWord) == 1) {
                        if (unit1.getId() == null //Jeremy '12,6,7 break if id is null (selected from related list)
                            || unit1.isPartialMatchToCodeRecord() //Jeremy '15,6,3 new record identification
                            || unit1.getCode() == null //Jeremy '12,6,7 break if code is null (selected from related phrase)
                            || unit1.getCode()!!.isEmpty()
                            || unit1.isRelatedPhraseRecord()
                        ) {
                            val rMappingList: MutableList<Mapping?>? =
                                dbadapter!!.getMappingByWord(baseWord, Companion.tablename)
                            if (!rMappingList!!.isEmpty()) baseCode =
                                rMappingList.get(0)!!.getCode()
                            else break //look-up failed, abandon.
                        }
                        if (baseCode != null && !baseCode.isEmpty()) QPCode += baseCode.substring(
                            0,
                            1
                        )
                        else break //abandon the phrase learning process;


                        //if word length >0, lookup all codes and rebuild basecode and QPCode
                    } else if (codePointLength(baseWord) > 1 && codePointLength(baseWord) < 5) {
                        baseCode = ""
                        val baseWordCodePointLength: Int = codePointLength(baseWord)
                        for (i in 0..<baseWordCodePointLength) {
                            val c: String = Companion.codePointSubstring(baseWord!!, i, i + 1)
                            val rMappingList: MutableList<Mapping?>? =
                                dbadapter!!.getMappingByWord(c, Companion.tablename)
                            if (!rMappingList!!.isEmpty()) {
                                baseCode += rMappingList.get(0)!!.getCode()
                                QPCode += rMappingList.get(0)!!.getCode()!!.substring(0, 1)
                            } else {
                                baseCode = "" //r-lookup failed. abandon the phrase learning
                                break
                            }
                        }
                    }


                    for (i in phraselist.indices) {
                        if (i + 1 < phraselist.size) {
                            val unit2 = phraselist.get((i + 1))
                            if (unit2 == null || unit2.getWord()!!
                                    .isEmpty() || unit2.isComposingCodeRecord() || unit2.isEnglishSuggestionRecord()
                            )  //Jeremy 15,6,4 exclude composing code
                            //|| unit2.getCode().equals(unit2.getWord())) //Jeremy '12,6,13 avoid learning mixed mode english
                            {
                                break
                            }

                            val word2: String = unit2.getWord()!!
                            var code2 = unit2.getCode()
                            baseWord += word2

                            if (codePointLength(word2) == 1 && codePointLength(baseWord) < 5) { //limit the phrase size to 4
                                if (unit2.getId() == null //Jeremy '12,6,7 break if id is null (selected from related phrase)
                                    || unit2.isPartialMatchToCodeRecord() //Jeremy '15,6,3 new record identification
                                    || code2 == null //Jeremy '12,6,7 break if code is null (selected from relatedphrase)
                                    || code2.isEmpty()
                                    || unit2.isRelatedPhraseRecord()
                                ) {
                                    val rMappingList: MutableList<Mapping?>? =
                                        dbadapter!!.getMappingByWord(word2, Companion.tablename)
                                    if (!rMappingList!!.isEmpty()) code2 =
                                        rMappingList.get(0)!!.getCode()
                                    else break
                                }
                                if (code2 != null && !code2.isEmpty()) {
                                    baseCode += code2
                                    QPCode += code2.substring(0, 1)
                                } else break //abandon the phrase learning process;


                                //if word length >0, lookup all codes and rebuild basecode and QPCode
                            } else if (codePointLength(word2) > 1 && codePointLength(baseWord) < 5) {
                                val word2CodePointLength: Int = codePointLength(word2)
                                for (j in 0..<word2CodePointLength) {
                                    val c: String = codePointSubstring(word2, j, j + 1)
                                    val rMappingList: MutableList<Mapping?>? =
                                        dbadapter!!.getMappingByWord(c, Companion.tablename)
                                    if (!rMappingList!!.isEmpty()) {
                                        baseCode += rMappingList.get(0)!!.getCode()
                                        QPCode += rMappingList.get(0)!!.getCode()!!
                                            .substring(0, 1)
                                    } else  //r-lookup failed. abandon the phrase learning
                                        break
                                }
                            } else  // abandon the learing process.
                                break


                            if (DEBUG) Log.i(
                                TAG, ("learnLDPhrase(): code1 = " + unit1.getCode()
                                        + ", code2 = '" + code2
                                        + "', word1 = " + unit1.getWord()
                                        + ", word2 = " + word2
                                        + ", basecode = '" + baseCode
                                        + "', baseWord = " + baseWord
                                        + ", QPcode = '" + QPCode
                                        + "'.")
                            )
                            if (i + 1 == phraselist.size - 1) { //only learn at the end of the phrase word '12,6,8
                                if (Companion.tablename == LIME.DB_TABLE_PHONETIC) { // remove tone symbol in phonetic table
                                    LDCode = baseCode!!.replace("[3467 ]".toRegex(), "").lowercase()
                                    QPCode = QPCode!!.lowercase()
                                    if (LDCode.length > 1) {
                                        dbadapter!!.addOrUpdateMappingRecord(LDCode, baseWord)
                                        removeRemappedCodeCachedMappings(LDCode)
                                        updateSimilarCodeCache(LDCode)
                                    }
                                    if (QPCode.length > 1) {
                                        dbadapter!!.addOrUpdateMappingRecord(QPCode, baseWord)
                                        removeRemappedCodeCachedMappings(QPCode)
                                        updateSimilarCodeCache(QPCode)
                                    }
                                } else if (baseCode!!.length > 1) {
                                    baseCode = baseCode.lowercase()
                                    dbadapter!!.addOrUpdateMappingRecord(baseCode, baseWord)
                                    removeRemappedCodeCachedMappings(baseCode)
                                    updateSimilarCodeCache(baseCode)
                                }
                                if (DEBUG) Log.i(
                                    TAG,
                                    ("learnLDPhrase(): LDPhrase learning, baseCode = '" + baseCode
                                            + "', LDCode = '" + LDCode + "', QPCode=" + QPCode + "'."
                                            + ", baseWord" + baseWord)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 
     */
    /**
     * Removes cached mappings for codes that have been remapped.
     * 
     * @param code The original code.
     */
    private fun removeRemappedCodeCachedMappings(code: String) {
        if (DEBUG) Log.i(
            TAG,
            "removeRemappedCodeCachedMappings() on code ='" + code + "' coderemapcache.size=" + coderemapcache!!.size
        )
        val codelist: MutableList<String>? = coderemapcache!!.get(cacheKey(code))
        if (codelist != null) {
            for (entry in codelist) {
                if (DEBUG) Log.i(
                    TAG,
                    "removeRemappedCodeCachedMappings() remove code= '" + entry + "' from cache."
                )
                cache!!.remove(cacheKey(entry))
            }
        } else cache!!.remove(cacheKey(code)) //Jeremy '12,6,6 no remap. remove the code mapping from cache.
    }

    /**
     * Updates the cache for codes similar to the modified code (e.g., prefix matches).
     * 
     * @param code The modified code.
     * @return The list of prefix codes that were actually evicted from the cache so
     * callers can re-warm them from the DB on the current thread.
     */
    private fun updateSimilarCodeCache(code: String): MutableList<String?> {
        if (DEBUG) Log.i(TAG, "updateSimilarCodeCache(): code = '" + code + "'")
        val evictedPrefixes: MutableList<String?> = LinkedList<String?>()
        var cachekey: String?
        var cachedList: MutableList<Mapping?>? // = cache.get(cachekey);
        var len = code.length
        if (len > 5) len = 5 //Jeremy '12,6,7 change max backward level to 5.

        for (k in 1..<len) {
            val key = code.substring(0, code.length - k)
            cachekey = cacheKey(key)
            cachedList = cache!!.get(cachekey)
            if (DEBUG) Log.i(
                TAG,
                "updateSimilarCodeCache(): cachekey = '" + cachekey + "' cachedList == null :" + (cachedList == null)
            )
            if (cachedList != null) {
                cache!!.remove(cachekey)
                evictedPrefixes.add(key)
            } else {
                if (DEBUG) Log.i(
                    TAG,
                    "updateSimilarCodeCache(): code not in cache. update to db only on code = '" + key + "'"
                )
                removeRemappedCodeCachedMappings(key)
            }
        }
        // Prefetch if code length == 1 (moved outside loop since loop doesn't execute when code.length() == 1)
        if (code.length == 1) {
            try {
                getMappingByCode(code, !isPhysicalKeyboardPressed, false, true)
            } catch (e: RemoteException) {
                Log.e(TAG, "Error in search operation", e)
            }
        }
        return evictedPrefixes
    }


    /**
     * Converts an internal key code/string to its display name.
     * 
     * @param code The key code.
     * @return The display name for the key.
     */
    fun keyToKeyname(code: String): String {
        //Jeremy '11,6,21 Build cache according using cachekey

        val cacheKey = cacheKey(code)
        var result: String? = keynamecache!!.get(cacheKey)
        if (result == null) {
            //loadDBAdapter(); openLimeDatabase();
            result = keyToKeyName(code, Companion.tablename, true)
            keynamecache!!.put(cacheKey, result)
        }
        return result
    }

    fun keyToKeyName(code: String, tablename: String, preferUserDef: Boolean?): String {
        return dbadapter!!.keyToKeyName(code, tablename, preferUserDef ?: true)
    }

    /**
     * Renamed from addUserDict and pass parameter with mapping directly Jeremy '12,6,5
     * Renamed to learnRelatedPhraseAndUpdateScore Jeremy '15,6,4
     */
    /**
     * Updates the score of a mapping and learns it as a related phrase.
     * 
     * 
     * This spawns a background thread to update the score cache and persistent storage asynchronously.
     * 
     * @param updateMapping The mapping to update/learn.
     */
    fun learnRelatedPhraseAndUpdateScore(updateMapping: Mapping?) //String id, String code, String word,
    //String pword, int score, boolean isDictionary)
    {
        if (DEBUG) Log.i(TAG, "learnRelatedPhraseAndUpdateScore() ")

        // Temp final Mapping Object For updateMapping thread.
        if (updateMapping != null) {
            val updateMappingTemp = Mapping(updateMapping)
            synchronized(scorelist!!) {
                scorelist.add(updateMappingTemp)
            }
            val UpdatingThread: Thread = object : Thread() {
                override fun run() {
                    updateScoreCache(updateMappingTemp)
                }
            }
            UpdatingThread.start()
        }
    }

    /**
     * Adds a mapping to the Learning Dictionary (LD) phrase buffer.
     * 
     * @param mapping The mapping to add.
     * @param ending  True if this mapping ends the current phrase sequence.
     */
    fun addLDPhrase(
        mapping: Mapping?,  //String id, String code, String word, int score,
        ending: Boolean
    ) {
        if (mLIMEPref != null && !mLIMEPref!!.getLearnPhrase()) {
            return
        }

        if (LDPhraseListArray == null) LDPhraseListArray = ArrayList<MutableList<Mapping?>?>()
        if (LDPhraseList == null) LDPhraseList = LinkedList<Mapping?>()


        if (mapping != null) { // force interruped if mapping=null
            LDPhraseList!!.add(mapping)
        }

        if (ending) {
            if (LDPhraseList!!.size > 1) LDPhraseListArray!!.add(LDPhraseList)
            LDPhraseList = LinkedList<Mapping?>()
        }

        if (DEBUG) Log.i(
            TAG, ("addLDPhrase()" //+mapping.getCode() + ". id=" + mapping.getId()
                    + ". ending:" + ending
                    + ". LDPhraseListArray.size=" + LDPhraseListArray!!.size
                    + ". LDPhraseList.size=" + LDPhraseList!!.size)
        )
    }

    @get:Throws(RemoteException::class)
    val keyboardConfigList: MutableList<Keyboard?>?
        /**
         * Retrieves the list of installed keyboards.
         * 
         * @return A list of [Keyboard] objects.
         * @throws RemoteException If a database error occurs.
         */
        get() =//if(dbadapter == null){dbadapter = new LimeDB(ctx);}
            dbadapter!!.keyboardConfigList

    @JvmName("getKeyboardConfigListCompat")
    @Throws(RemoteException::class)
    fun getKeyboardConfigList(): MutableList<Keyboard?>? = keyboardConfigList

    @get:Throws(RemoteException::class)
    val allImKeyboardConfigList: MutableList<ImConfig?>
        /**
         * Retrieves the list of Input Methods (IMs).
         * 
         * @return A list of [ImConfig] objects with soft keyboard settings of all activated IM.
         * @throws RemoteException If a database error occurs.
         */
        get() = dbadapter!!.getImConfigList(null, LIME.DB_IM_COLUMN_KEYBOARD)

    @JvmName("getAllImKeyboardConfigListCompat")
    @Throws(RemoteException::class)
    fun getAllImKeyboardConfigList(): MutableList<ImConfig?> = allImKeyboardConfigList


    /**
     * Clears all runtime caches (score list, mappings, english, emoji, key names).
     * 
     * @throws RemoteException If a database error occurs.
     */
    @Throws(RemoteException::class)
    fun clear() {
        if (scorelist != null) {
            scorelist.clear()
        }
        if (cache != null) {
            cache!!.clear()
        }
        if (engcache != null) {
            engcache!!.clear()
        }
        if (emojicache != null) {
            emojicache!!.clear()
        }
        if (keynamecache != null) {
            keynamecache!!.clear()
        }

        if (coderemapcache != null) {
            coderemapcache!!.clear()
        }
    }

    // deprecated and using exact match stack to get real code length now. Jerey '15,6,2
    //private static List<Pair<Integer, Integer>> codeLengthMap = new LinkedList<>();
    /**
     * Constructs a new SearchServer instance.
     * 
     * @param mContext The application context, used for database access and preference loading.
     */
    init {
        // Handle null context gracefully (e.g., in tests) by using a dummy context
        // This prevents NullPointerException during object construction
        if (mContext != null) {
            mLIMEPref = LIMEPreferenceManager(mContext.getApplicationContext())
            if (dbadapter == null) {
                dbadapter = LimeDB(mContext)
            } else {
                dbadapter!!.openDBConnection(false)
            }
        } else {
            // For null context (test scenarios), create a minimal preference manager
            // This allows the object to be constructed without crashing
            mLIMEPref = null // Will be handled by null checks in methods that use it
        }
        initialCache()
        if (dbadapter != null) {
            preloadEmojiCategoryPages()
        }
    }

    /**
     * Retrieves English word suggestions for a given prefix.
     * 
     * @param word The prefix or word to search for.
     * @return A list of English word mappings.
     * @throws RemoteException If a database error occurs.
     */
    @Synchronized
    @Throws(RemoteException::class)
    fun getEnglishSuggestions(word: String): MutableList<Mapping?> {
        var startTime: Long = 0
        if (DEBUG || dumpRunTimeSuggestion) {
            startTime = System.currentTimeMillis()
            Log.i(TAG, "getEnglishSuggestions()")
        }

        val result: MutableList<Mapping?> = LinkedList<Mapping?>()

        //Jeremy '15,7,16 return zero result if last query returns no result
        if (!(word.length > 1 && lastEnglishWord != null && word.startsWith(lastEnglishWord!!) && noSuggestionsForLastEnglishWord)) {
            val cacheTemp: MutableList<Mapping?>? = engcache!!.get(word)

            if (cacheTemp != null) {
                result.addAll(cacheTemp)
            } else {
                val tempResult: MutableList<String?>? = dbadapter!!.getEnglishSuggestions(word)
                for (u in tempResult!!) {
                    val temp = Mapping()
                    temp.setWord(u)
                    temp.setEnglishSuggestionRecord()
                    result.add(temp)
                }
                if (!result.isEmpty()) {
                    engcache!!.put(word, result)
                }
            }

            noSuggestionsForLastEnglishWord = result.isEmpty()
            lastEnglishWord = word
        }

        if (DEBUG || dumpRunTimeSuggestion) {
            Log.i(
                TAG,
                "getEnglishSuggestions() time elapsed =" + (System.currentTimeMillis() - startTime)
            )
        }

        return result
    }


    @get:Throws(RemoteException::class)
    val selkey: String?
        /**
         * Retrieves the selection key string for the current keyboard.
         * 
         * 
         * Selection keys are used for selecting candidates (e.g., "1234567890" or "asdfghjkl;").
         * 
         * @return The selection key characters string.
         * @throws RemoteException If a database error occurs.
         */
        get() {
            if (DEBUG) Log.i(
                TAG,
                "getSelkey():hasNumber:" + hasNumberMapping + "hasSymbol:" + hasSymbolMapping
            )
            var selkey: String?
            var table: String? = Companion.tablename
            if (Companion.tablename == LIME.DB_TABLE_PHONETIC) {
                table = Companion.tablename + mLIMEPref!!.getPhoneticKeyboardType()
            }
            if (selKeyMap.get(table) == null || selKeyMap.isEmpty()) {
                //if(dbadapter == null){dbadapter = new LimeDB(ctx);}
                selkey = dbadapter!!.getImConfig(
                    Companion.tablename,
                    "selkey"
                )
                if (DEBUG) Log.i(
                    TAG,
                    "getSelkey():selkey from db:" + selkey
                )
                var validSelkey = true
                if (selkey.length == 10) {
                    for (i in 0..9) {
                        if (Character.isLetter(selkey.get(i)) ||
                            (hasNumberMapping && Character.isDigit(
                                selkey.get(i)
                            ))
                        ) validSelkey = false
                    }
                } else validSelkey = false
                //Jeremy '11,6,19 Rewrite for IM has symbol mapping like ETEN
                if (!validSelkey || Companion.tablename == LIME.DB_TABLE_PHONETIC) {
                    if (hasNumberMapping && hasSymbolMapping) {
                        if (Companion.tablename == LIME.DB_TABLE_DAYI
                            || (Companion.tablename == LIME.DB_TABLE_PHONETIC && mLIMEPref!!.getPhoneticKeyboardType() == LIME.DB_TABLE_PHONETIC)
                        ) {
                            selkey = "'[]-\\^&*()"
                        } else {
                            selkey = "!@#$%^&*()"
                        }
                    } else if (hasNumberMapping) {
                        selkey = "'[]-\\^&*()"
                    } else {
                        selkey = "1234567890"
                    }
                }
                if (DEBUG) Log.i(
                    TAG,
                    "getSelkey():selkey:" + selkey
                )
                selKeyMap.put(table, selkey)
            }
            return selKeyMap.get(table)
        }

    @JvmName("getSelkeyCompat")
    @Throws(RemoteException::class)
    fun getSelkey(): String? = selkey

    /*
    private class runTimeSuggestion {

        private List<List<Pair<Mapping, String>>> suggestionLoL;
        private int level;

        public runTimeSuggestion() {
            suggestionLoL = new LinkedList<>();
        }

        public void addExactMatch(String code, List<Mapping> completeCodeResultList) {
            Mapping exactMatchMapping;
            level++;

            int i = 0;
            do {
                exactMatchMapping = completeCodeResultList.get(i);
                int score = exactMatchMapping.getBasescore();
                if (score < MIN_SCORE_THRESHOLD) {
                    score = MIN_SCORE_THRESHOLD;
                } else if (score > MAX_SCORE_THRESHOLD) {
                    score = MAX_SCORE_THRESHOLD;
                }
                int codeLenBonus = exactMatchMapping.getCode().length() / exactMatchMapping.getWord().length() * CODE_LENGTH_BONUS_MULTIPLIER;
                exactMatchMapping.setBasescore((score + codeLenBonus) * exactMatchMapping.getWord().length());

                if (DEBUG || dumpRunTimeSuggestion)
                    Log.i(TAG, "addExactMatch() complete code = " + code + "" +
                            ", got exact match  = " + exactMatchMapping.getWord()
                            + " score =" + exactMatchMapping.getScore() + ", basescore=" + exactMatchMapping.getBasescore());
                

                //push the exact match mapping with current code into exact match stack. '15,6,2 Jeremy
                if (exactMatchMapping.getBasescore() > 0) {
                    List<Pair<Mapping, String>> suggestionList = new LinkedList<>();
                    suggestionList.add(new Pair<>(exactMatchMapping, code));
                    suggestionLoL.add(suggestionList);
                }
                i++;
            }
            while (completeCodeResultList.size() > i
                    && completeCodeResultList.get(i).isExactMatchToCodeRecord() && i < 5); //process at most 5 exact match items.

        }

        public void checkRemainingCode(String code) {

            int highestScore = 0, highestRelatedScore = 0;
            //iterate all previous exact match mapping and check for exact match on remaining code.
            for (List<Pair<Mapping, String>> suggestionList : suggestionLoL) {
                for (Pair<Mapping, String> p : suggestionList) {
                    String pCode = p.second;
                    if (pCode.length() < code.length() && code.startsWith(pCode) && code.length() - pCode.length() <= 5) {
                        String remainingCode = code.substring(pCode.length(), code.length());
                        Log.i(TAG, "makeRunTimeSuggestion() working on previous exact match item = " + p.first.getWord() +
                                " with base score = " + p.first.getBasescore() + ", average score = " + p.first.getBasescore() / p.first.getWord().length() +
                                ", remainingCode =" + remainingCode);


                        Pair<List<Mapping>, List<Mapping>> resultPair =  //do remaining code query
                                getMappingByCodeFromCacheOrDB(remainingCode, false);
                        if (resultPair == null) continue;

                        List<Mapping> resultList = resultPair.first;
                        if (resultList.size() > 0
                                && resultList.get(0).isExactMatchToCodeRecord()) {  //remaining code search got exact match
                            Mapping remainingCodeExactMatchMapping = resultList.get(0);
                            Mapping previousMapping = p.first;
                            String phrase = previousMapping.getWord() + remainingCodeExactMatchMapping.getWord();
                            int phraseLen = codePointLength(phrase);
                            if (phraseLen < 2 || remainingCodeExactMatchMapping.getBasescore() < 2)
                                continue;
                            int remainingScore = remainingCodeExactMatchMapping.getBasescore();
                            int codeLenBonus = remainingCodeExactMatchMapping.getCode().length() / remainingCodeExactMatchMapping.getWord().length() * 30;
                            if (remainingScore > 120) remainingScore = 120;
                            remainingScore = remainingScore / remainingCodeExactMatchMapping.getWord().length() + codeLenBonus;

                            int previousScore = previousMapping.getBasescore() / previousMapping.getWord().length();
                            int averageScore = (previousScore + remainingScore) / 2;

                            if (DEBUG || dumpRunTimeSuggestion)
                                Log.i(TAG, "makeRunTimeSuggestion() remaining code = " + remainingCode + "" +
                                        ", got exact match  = " + remainingCodeExactMatchMapping.getWord() + " with base score = "
                                        + remainingScore + " average score =" + averageScore);

                            //verify if the new phrase is in related table.
                            // check up to four characters phrase 1-3, 1-2 , 1-1
                            Mapping relatedMapping = null;
                            for (int i = ((phraseLen < 4) ? phraseLen - 1 : 3); i > 0; i--) {
                                String[] relatedWords = splitRelatedPhraseTail(phrase, i);
                                String pword = relatedWords[0];
                                String cword = relatedWords[1];
                                relatedMapping = dbadapter.isRelatedPhraseExist(pword, cword);
                                if (relatedMapping != null) break;
                            }
                            if (relatedMapping != null
                                    && relatedMapping.getScore() >= highestRelatedScore
                                //&& averageScore > highestScore
                                    ) {
                                Mapping suggestMapping = new Mapping();
                                suggestMapping.setRuntimeBuiltPhraseRecord();
                                suggestMapping.setCode(code);
                                suggestMapping.setWord(phrase);
                                highestRelatedScore = relatedMapping.getBasescore();
                                suggestMapping.setScore(highestRelatedScore);

                                suggestMapping.setBasescore((averageScore + SCORE_ADJUSTMENT_INCREMENT) * phraseLen);
                                suggestionList.add(new Pair<>(suggestMapping, code));
                                if (DEBUG || dumpRunTimeSuggestion)
                                    Log.i(TAG, "makeRunTimeSuggestion()  run-time suggest phrase verified from related table ="
                                            + phrase + "score from related table = " + highestRelatedScore + " , new base score = " + suggestMapping.getBasescore());
                            } else if (highestRelatedScore == 0// no mapping is verified from related table
                                    && averageScore > highestScore) {
                                Mapping suggestMapping = new Mapping();
                                suggestMapping.setRuntimeBuiltPhraseRecord();
                                suggestMapping.setCode(code);
                                suggestMapping.setWord(phrase);
                                highestScore = averageScore;
                                suggestMapping.setBasescore(highestScore * phraseLen);
                                suggestionList.add(new Pair<>(suggestMapping, code));
                                if (DEBUG || dumpRunTimeSuggestion)
                                    Log.i(TAG, "makeRunTimeSuggestion()  run-time suggest phrase =" + phrase
                                            + ", new base score = " + highestScore);
                            }
                        }
                    }
                }
            }
        }

        public void clear() {
            level = 0;
            for (List<Pair<Mapping, String>> item : suggestionLoL) {
                if (item != null) item.clear();
            }
            suggestionLoL.clear();

        }

        public Mapping getBestSuggestion() {
            return null;
        }

    }
    */
    // ============================================================================
    // UI-Compatible Methods - Delegates to LimeDB for database operations
    // These methods allow UI components to access database operations through
    // SearchServer instead of directly accessing LimeDB, maintaining architectural
    // separation and enabling centralized caching/logging if needed in the future.
    // ============================================================================
    /**
     * Gets IM records filtered by code and/or configEntry.
     * 
     * 
     * This method delegates to LimeDB.getIm() to retrieve IM information records.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param code The IM code to filter by, or null/empty for all
     * @param configEntry The IM configEntry to filter by, or null/empty for all
     * @return List of Im objects, or empty list if database error
     */
    fun getImConfigList(code: String?, configEntry: String?): MutableList<ImConfig?> {
        if (dbadapter == null) {
            Log.e(TAG, "getIm(): dbadapter is null")
            return ArrayList<ImConfig?>()
        }
        return dbadapter!!.getImConfigList(code, configEntry)
    }

    val keyboard: MutableList<Keyboard?>?
        /**
         * Gets a list of all keyboards from the database.
         * 
         * 
         * This method delegates to LimeDB.getKeyboard() to retrieve keyboard records.
         * UI components should use this method instead of directly accessing LimeDB.
         * 
         * @return List of Keyboard objects, or empty list if database error
         */
        get() {
            if (dbadapter == null) {
                Log.e(TAG, "getKeyboard(): dbadapter is null")
                return ArrayList<Keyboard?>()
            }
            return dbadapter!!.keyboardConfigList
        }


    /**
     * Counts the number of mapping records in a table.
     * 
     * 
     * This method delegates to LimeDB.countRecords() to count mapping records.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param imCode The table name to count records in
     * @return The number of records, or 0 if database error
     */
    fun getImConfig(imCode: String?, field: String?): String {
        if (dbadapter == null || imCode == null) {
            Log.e(TAG, "getImConfig(): dbadapter or imCode is null")
            return ""
        }
        return dbadapter!!.getImConfig(imCode, field)
    }

    /**
     * Sets IM information for a specific field.
     * 
     * 
     * This method delegates to LimeDB.setImInfo() to store or update configuration
     * information in the imCode table. UI components should use this method instead of
     * directly accessing LimeDB.
     * 
     * @param imCode    The IM code (e.g., LIME.DB_TABLE_PHONETIC, LIME.DB_TABLE_DAYI)
     * @param field The field name to set
     * @param value The value to store
     * @return
     */
    fun setImConfig(imCode: String?, field: String?, value: String?): Boolean {
        if (dbadapter == null) {
            Log.e(TAG, "setImInfo(): dbadapter is null")
            return false
        }
        dbadapter!!.setImConfig(imCode, field, value)
        return false
    }

    /**
     * Sets the keyboard assignment for an IM using string parameters.
     * 
     * 
     * This method delegates to LimeDB.setIMKeyboard() to store keyboard
     * configuration in the im table. UI components should use this method instead
     * of directly accessing LimeDB.
     * 
     * @param im The IM code
     * @param value The keyboard description/name
     * @param keyboard The keyboard code
     */
    fun setIMKeyboard(im: String?, value: String?, keyboard: String?) {
        if (dbadapter == null) {
            Log.e(TAG, "setIMKeyboard(): dbadapter is null")
            return
        }
        dbadapter!!.setIMConfigKeyboard(im, value, keyboard)
    }

    /**
     * Sets the keyboard assignment for an IM using a Keyboard object.
     * 
     * 
     * This method delegates to LimeDB.setImKeyboard() to store keyboard
     * configuration in the im table. UI components should use this method instead
     * of directly accessing LimeDB.
     * 
     * @param imCode The IM imCode
     * @param keyboard The Keyboard object containing keyboard information
     */
    fun setIMKeyboard(imCode: String?, keyboard: Keyboard) {
        if (dbadapter == null) {
            Log.e(TAG, "setIMKeyboard(): dbadapter is null")
            return
        }
        dbadapter!!.setImConfigKeyboard(imCode, keyboard)
    }

    /**
     * Backs up user-learned records to a backup table.
     * 
     * 
     * This method delegates to LimeDB.backupUserRecords() to create a backup table
     * containing user-learned records (score > 0). UI components should use this method
     * instead of directly accessing LimeDB.
     * 
     * @param table The table name to backup user records from
     */
    fun backupUserRecords(table: String) {
        if (dbadapter == null) {
            Log.e(TAG, "backupUserRecords(): dbadapter is null")
            return
        }
        dbadapter!!.backupUserRecords(table)
    }

    /**
     * Restores user-learned records from a backup table to the main table.
     * 
     * 
     * This method delegates to LimeDB.restoreUserRecords() to restore user-learned
     * records from a backup table (typically named "{table}_user") back to the main
     * mapping table. UI components should use this method instead of directly accessing LimeDB.
     * 
     * 
     * The method performs the following operations:
     * 
     *  * Validates that the database adapter is available
     *  * Delegates to LimeDB.restoreUserRecords() which validates the table name
     *  * Retrieves all records from the backup table
     *  * Restores each record to the main table using addOrUpdateMappingRecord
     * 
     * 
     * @param table The base table name to restore records to (e.g., "cj", "phonetic")
     * @return The number of records restored, or 0 if no records to restore or error
     */
    fun restoreUserRecords(table: String?): Int {
        if (dbadapter == null) {
            Log.e(TAG, "restoreUserRecords(): dbadapter is null")
            return 0
        }

        try {
            return dbadapter!!.restoreUserRecords(table)
        } catch (e: Exception) {
            Log.e(TAG, "restoreUserRecords(): Error restoring user records for table: " + table, e)
            return 0
        }
    }

    /**
     * Gets a single record by ID.
     * 
     * 
     * This method delegates to LimeDB.getRecord() to retrieve a record.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param code The table name (code)
     * @param id The record ID
     * @return Record object, or null if not found or database error
     */
    fun getRecord(code: String, id: Long): Record? {
        if (dbadapter == null) {
            Log.e(TAG, "getRecord(): dbadapter is null")
            return null
        }
        return dbadapter!!.getRecord(code, id)
    }

    /**
     * Gets the count of records matching a query by word or code.
     * 
     * 
     * This method delegates to LimeDB.countRecords() to get the count of matching records.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param table The table name to query
     * @param curQuery The search query, or null/empty for all records
     * @param searchByCode If true, search by code; if false, search by word
     * @return The count of matching records, or 0 if database error
     */
    fun countRecordsByWordOrCode(table: String?, curQuery: String?, searchByCode: Boolean): Int {
        if (DEBUG) Log.i(TAG, "countRecordsByWordOrCode()")
        if (dbadapter == null) {
            Log.e(TAG, "countRecordsByWordOrCode(): dbadapter is null")
            return 0
        }
        // Build WHERE clause for countRecords() with parameterized queries
        val whereBuilder = StringBuilder()
        val whereArgsList: MutableList<String?> = ArrayList<String?>()

        if (curQuery != null && !curQuery.isEmpty()) {
            if (searchByCode) {
                whereBuilder.append(LIME.DB_COLUMN_CODE).append(" LIKE ? AND ")
                whereArgsList.add(curQuery + "%")
            } else {
                whereBuilder.append(LIME.DB_COLUMN_WORD).append(" LIKE ? AND ")
                whereArgsList.add("%" + curQuery + "%")
            }
        }
        whereBuilder.append("ifnull(").append(LIME.DB_COLUMN_WORD).append(", '') <> ''")

        val whereArgs = if (whereArgsList.isEmpty()) null else whereArgsList.toTypedArray<String?>()
        return dbadapter!!.countRecords(table, whereBuilder.toString(), whereArgs)
    }

    /**
     * Deletes a record from a table using a parameterized query.
     * 
     * 
     * This method delegates to LimeDB.deleteRecord() to safely delete records.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param table The table name
     * @param whereClause WHERE clause with "?" placeholders
     * @param whereArgs Arguments for the WHERE clause
     * @return Number of rows deleted, or 0 if error
     */
    fun deleteRecord(table: String, whereClause: String?, whereArgs: Array<String?>?): Int {
        if (dbadapter == null) {
            Log.e(TAG, "deleteRecord(): dbadapter is null")
            return 0
        }
        return dbadapter!!.deleteRecord(table, whereClause, whereArgs)
    }

    /**
     * Adds or updates a mapping record in the database.
     * 
     * 
     * This method delegates to LimeDB.addOrUpdateMappingRecord() to store or update
     * word mappings. UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param table The table name
     * @param code The code
     * @param word The word
     * @param score The score
     */
    fun addOrUpdateMappingRecord(table: String, code: String, word: String, score: Int) {
        if (dbadapter == null) {
            Log.e(TAG, "addOrUpdateMappingRecord(): dbadapter is null")
            return
        }
        dbadapter!!.addOrUpdateMappingRecord(table, code, word, score)
    }

    /**
     * Adds a record to a table using ContentValues.
     * 
     * 
     * This method delegates to LimeDB.addRecord() to safely insert records.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param table The table name
     * @param values The ContentValues containing column values
     * @return The row ID of the newly inserted row, or -1 if error
     */
    fun addRecord(table: String, values: ContentValues?): Long {
        if (dbadapter == null) {
            Log.e(TAG, "addRecord(): dbadapter is null")
            return -1
        }
        return dbadapter!!.addRecord(table, values)
    }

    /**
     * Counts the total number of records in the specified table.
     * 
     * 
     * This method delegates to LimeDB.countRecords() to get the count of records.
     * Filters out records with null/empty values to match getRecords() behavior.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param table The table name to count records from
     * @return The number of records in the table (excluding null/empty key values), or 0 if error or empty
     */
    fun countRecords(table: String?): Int {
        if (dbadapter == null) {
            Log.e(TAG, "countRecords(): dbadapter is null")
            return 0
        }


        // Apply table-specific filters to match getRecords behavior
        var whereClause: String? = null
        if (LIME.DB_TABLE_RELATED == table) {
            // For related table: exclude null/empty pword or cword
            whereClause =
                "ifnull(" + LIME.DB_RELATED_COLUMN_PWORD + ", '') <> '' AND ifnull(" + LIME.DB_RELATED_COLUMN_CWORD + ", '') <> ''"
        } else {
            // For IM tables: exclude null/empty word
            whereClause = "ifnull(" + LIME.DB_COLUMN_WORD + ", '') <> ''"
        }

        return dbadapter!!.countRecords(table, whereClause, null)
    }

    /**
     * Clears a mapping table by deleting all records and clearing the cache.
     * 
     * 
     * This method delegates to LimeDB.resetMapping() to safely delete all records from
     * the specified table and reset the cache. UI components should use this method
     * instead of directly accessing LimeDB.
     * 
     * 
     * The method performs the following operations:
     * 
     *  * Validates that the database adapter is available
     *  * Delegates to LimeDB.resetMapping() which validates the table name
     *  * Deletes all records from the specified table
     *  * Resets the SearchServer cache to ensure consistency
     * 
     * 
     * 
     * If the database adapter is null or the table name is invalid, the method
     * will log an error and return without performing any operations.
     * 
     * @param table The table name to clear (must be valid according to LimeDB.isValidTableName())
     * @throws IllegalArgumentException if table name is null or empty (propagated from LimeDB)
     */
    fun clearTable(table: String) {
        if (dbadapter == null) {
            Log.e(TAG, "clearTable(): dbadapter is null")
            return
        }

        try {
            dbadapter!!.clearTable(table)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "clearTable(): Invalid table name: " + table, e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "clearTable(): Error clearing table: " + table, e)
        }
    }

    /**
     * Resets the SearchServer cache.
     * 
     * 
     * This method delegates to LimeDB.resetCache() to clear the cache maintained
     * by SearchServer. UI components should use this method instead of directly
     * accessing LimeDB.
     */
    fun resetCache() {
        if (dbadapter == null) {
            Log.e(TAG, "resetCache(): dbadapter is null")
            return
        }
        dbadapter!!.resetCache()
    }

    /**
     * Checks and updates phonetic keyboard settings consistency between preferences and database.
     * 
     * 
     * This method delegates to LimeDB.checkPhoneticKeyboardSetting() to ensure that the
     * keyboard configuration stored in the database matches the user's preference setting.
     * It handles different phonetic keyboard types (hsu, eten26, eten, standard).
     * 
     * 
     * UI components should use this method instead of directly accessing LimeDB.
     */
    fun checkPhoneticKeyboardSetting() {
        if (dbadapter == null) {
            Log.e(TAG, "checkPhoneticKeyboardSetting(): dbadapter is null")
            return
        }
        dbadapter!!.checkPhoneticKeyboardSetting()
    }

    /**
     * Checks if a backup table exists and has records.
     * 
     * 
     * This method delegates to LimeDB.checkBackuptable() to check if user data
     * backup exists. UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param table The base table name to check backup for
     * @return true if backup table exists and has records, false otherwise
     */
    fun checkBackupTable(table: String?): Boolean {
        if (dbadapter == null) {
            Log.e(TAG, "checkBackuptable(): dbadapter is null")
            return false
        }
        return dbadapter!!.checkBackupTable(table)
    }

    /**
     * Gets all records from a backup table.
     * 
     * 
     * This method delegates to LimeDB.getBackupTableRecords() to retrieve backup records.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param backupTableName The backup table name (must end with "_user", e.g., "cj_user")
     * @return Cursor with all records from the backup table, or null if invalid or error
     */
    fun getBackupTableRecords(backupTableName: String?): Cursor? {
        if (dbadapter == null) {
            Log.e(TAG, "getBackupTableRecords(): dbadapter is null")
            return null
        }
        return dbadapter!!.getBackupTableRecords(backupTableName)
    }

    /**
     * Removes IM information for a specific field.
     * 
     * 
     * This method delegates to LimeDB.removeImInfo() to delete configuration information.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param im The IM code
     * @param field The field name to remove
     */
    fun removeImInfo(im: String?, field: String?) {
        if (dbadapter == null) {
            Log.e(TAG, "removeImInfo(): dbadapter is null")
            return
        }
        dbadapter!!.removeImConfig(im, field)
    }

    /**
     * Resets all IM information for a specific IM.
     * 
     * 
     * This method delegates to LimeDB.resetImInfo() to clear all configuration
     * information for an IM. UI components should use this method instead of
     * directly accessing LimeDB.
     * 
     * @param imCode The IM code to reset
     */
    fun resetImConfig(imCode: String?) {
        if (dbadapter == null) {
            Log.e(TAG, "resetImInfo(): dbadapter is null")
            return
        }
        dbadapter!!.resetImConfig(imCode)
    }

    /**
     * Resets all LIME settings to factory defaults.
     * 
     * 
     * This method delegates to LimeDB.resetLimeSetting() to reset all databases
     * (main, emoji, han converter) to factory defaults. This is a destructive operation
     * that will erase all user data including learned mappings and related phrases.
     * 
     * 
     * UI components should use this method instead of directly accessing LimeDB.
     */
    fun restoredToDefault() {
        if (dbadapter == null) {
            Log.e(TAG, "resetLimeSetting(): dbadapter is null")
            return
        }
        dbadapter!!.restoredToDefault()
    }

    /**
     * Gets keyboard information for a specific field.
     * 
     * 
     * This method delegates to LimeDB.getKeyboardInfo() to retrieve keyboard
     * configuration information. UI components should use this method instead of
     * directly accessing LimeDB.
     * 
     * @param keyboardCode The keyboard code (e.g., "lime", "limenum")
     * @param field The field name to retrieve
     * @return The field value, or null if not found or database error
     */
    fun getKeyboardInfo(keyboardCode: String?, field: String?): String? {
        if (dbadapter == null) {
            Log.e(TAG, "getKeyboardInfo(): dbadapter is null")
            return null
        }
        return dbadapter!!.getKeyboardInfo(keyboardCode, field)
    }


    /**
     * Gets keyboard object information for a specific keyboard code.
     * 
     * 
     * This method delegates to LimeDB.getKeyboardConfig() to retrieve keyboard
     * configuration including layout definitions. UI components should use this
     * method instead of directly accessing LimeDB.
     * 
     * @param keyboard The keyboard code (e.g., "lime", "limenum", "wb", "hs")
     * @return KeyboardObj with keyboard information, or null if not found or database error
     */
    fun getKeyboardConfig(keyboard: String?): Keyboard? {
        if (dbadapter == null) {
            Log.e(TAG, "getKeyboardConfig(): dbadapter is null")
            return null
        }
        return dbadapter!!.getKeyboardConfig(keyboard)
    }

    /**
     * Loads records from a table with optional filtering and pagination.
     * 
     * 
     * This method delegates to LimeDB.getRecords() to retrieve records.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param code The table name (code)
     * @param query The search query, or null/empty for all records
     * @param searchByCode If true, search by code; if false, search by word
     * @param maximum Maximum number of records to return (0 for no limit)
     * @param offset Number of records to skip (0 for no offset)
     * @return List of Record objects, or empty list if error
     */
    fun getRecords(
        code: String,
        query: String?,
        searchByCode: Boolean,
        maximum: Int,
        offset: Int
    ): MutableList<Record> {
        if (dbadapter == null) {
            Log.e(TAG, "getRecords(): dbadapter is null")
            return ArrayList<Record>()
        }
        return dbadapter!!.getRecordList(code, query, searchByCode, maximum, offset)
    }


    /**
     * Gets the count of related phrase records for a parent word.
     * 
     * 
     * This method delegates to LimeDB.countRecords() to get the count of related phrases.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param pword The parent word to count related phrases for
     * @return The count of related phrases, or 0 if database error or not found
     */
    fun countRecordsRelated(pword: String?): Int {
        var pword = pword
        if (dbadapter == null) {
            Log.e(TAG, "countRecords(): dbadapter is null")
            return 0
        }
        // Build WHERE clause for related table
        val whereBuilder = StringBuilder()
        val whereArgsList: MutableList<String?> = ArrayList<String?>()

        var cword = ""
        if (pword != null && !pword.isEmpty() && codePointLength(pword) > 1) {
            val relatedWords: Array<String> = splitLeadingCodePoint(pword)
            pword = relatedWords[0]
            cword = relatedWords[1]
        }

        if (pword != null && !pword.isEmpty()) {
            whereBuilder.append(LIME.DB_RELATED_COLUMN_PWORD).append(" = ? AND ")
            whereArgsList.add(pword)
        }
        if (!cword.isEmpty()) {
            whereBuilder.append(LIME.DB_RELATED_COLUMN_CWORD).append(" LIKE ? AND ")
            whereArgsList.add(cword + "%")
        }

        whereBuilder.append("ifnull(").append(LIME.DB_RELATED_COLUMN_CWORD).append(", '') <> ''")

        val whereArgs = if (whereArgsList.isEmpty()) null else whereArgsList.toTypedArray<String?>()
        return dbadapter!!.countRecords(LIME.DB_TABLE_RELATED, whereBuilder.toString(), whereArgs)
    }

    /**
     * Checks if a related phrase exists.
     * 
     * 
     * This method delegates to LimeDB.countRecords() to check for related phrase existence.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param pword The parent word (must not be null or empty)
     * @param cword The child word (must not be null or empty)
     * @return true if related phrase exists, false otherwise
     */
    fun hasRelated(pword: String?, cword: String?): Boolean {
        if (dbadapter == null) {
            Log.e(TAG, "hasRelated(): dbadapter is null")
            return false
        }
        // Build WHERE clause for related table
        val whereBuilder = StringBuilder()
        val whereArgsList: MutableList<String?> = ArrayList<String?>()

        if (pword != null && !pword.isEmpty()) {
            whereBuilder.append(LIME.DB_RELATED_COLUMN_PWORD).append(" = ? AND ")
            whereArgsList.add(pword)
        }
        if (cword != null && !cword.isEmpty()) {
            whereBuilder.append(LIME.DB_RELATED_COLUMN_CWORD).append(" = ?")
            whereArgsList.add(cword)
        } else {
            whereBuilder.append(LIME.DB_RELATED_COLUMN_CWORD).append(" IS NULL")
        }

        val whereArgs = if (whereArgsList.isEmpty()) null else whereArgsList.toTypedArray<String?>()
        val count: Int =
            dbadapter!!.countRecords(LIME.DB_TABLE_RELATED, whereBuilder.toString(), whereArgs)
        return count > 0
    }

    /**
     * Updates records in a table using parameterized queries.
     * 
     * 
     * This method delegates to LimeDB.updateRecord() to safely update records.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param table The table name
     * @param values The values to update
     * @param whereClause WHERE clause with "?" placeholders
     * @param whereArgs Arguments for the WHERE clause
     * @return Number of rows updated, or -1 if error
     */
    fun updateRecord(
        table: String,
        values: ContentValues?,
        whereClause: String?,
        whereArgs: Array<String?>?
    ): Int {
        if (dbadapter == null) {
            Log.e(TAG, "updateRecord(): dbadapter is null")
            return -1
        }
        return dbadapter!!.updateRecord(table, values, whereClause, whereArgs)
    }


    /**
     * Gets related phrase records with optional filtering and pagination.
     * 
     * 
     * This method delegates to LimeDB.getRelated() to retrieve related phrase records.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param pword The parent word to search for, or null/empty for all
     * @param maximum Maximum number of records to return (0 for no limit)
     * @param offset Number of records to skip (0 for no offset)
     * @return List of Related objects, or empty list if error
     */
    fun getRelatedByWord(pword: String?, maximum: Int, offset: Int): MutableList<Related> {
        if (dbadapter == null) {
            Log.e(TAG, "getRelatedByWord(): dbadapter is null")
            return ArrayList<Related>()
        }
        return dbadapter!!.getRelated(pword, maximum, offset)
    }

    /**
     * Gets a list of IM information records for a specific IM code.
     * 
     * 
     * This method delegates to LimeDB.getImList() to retrieve IM information.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param code The IM code to retrieve information for
     * @return List of Im objects, or null if database error
     */
    fun getImAllConfigList(code: String?): MutableList<ImConfig?>? {
        if (dbadapter == null) {
            Log.e(TAG, "getImList(): dbadapter is null")
            return null
        }
        return dbadapter!!.getImConfigList(code, null)
    }

    /**
     * Validates if a table name is valid according to LimeDB whitelist.
     * 
     * 
     * This method delegates to LimeDB.isValidTableName() to validate table names.
     * UI components should use this method instead of directly accessing LimeDB.
     * 
     * @param tableName The table name to validate
     * @return true if the table name is valid, false otherwise
     */
    fun isValidTableName(tableName: String?): Boolean {
        if (dbadapter == null) {
            Log.e(TAG, "isValidTableName(): dbadapter is null")
            return false
        }
        return dbadapter!!.isValidTableName(tableName)
    }

    companion object {
        private const val DEBUG = false
        private const val TAG = "SearchServer"
        private var dbadapter: LimeDB? = null

        // Score Thresholds
        private const val MIN_SCORE_THRESHOLD = 120 // Minimum score threshold for search results
        private const val MAX_SCORE_THRESHOLD = 200 // Maximum score threshold for search results
        private const val SCORE_ADJUSTMENT_INCREMENT = 50 // Score adjustment increment
        private const val CODE_LENGTH_BONUS_MULTIPLIER =
            30 // Multiplier for code length bonus calculation

        private fun codePointLength(text: String?): Int {
            return if (text == null) 0 else text.codePointCount(0, text.length)
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

        private fun splitLeadingCodePoint(text: String?): Array<String> {
            if (text == null || text.isEmpty()) {
                return arrayOf("", "")
            }
            val end = text.offsetByCodePoints(0, 1)
            return arrayOf<String>(text.substring(0, end), text.substring(end))
        }

        private fun splitRelatedPhraseTail(
            phrase: String,
            cwordCodePointCount: Int
        ): Array<String> {
            val phraseCodePointLength: Int = codePointLength(phrase)
            val pwordStart = phraseCodePointLength - cwordCodePointCount - 1
            val cwordStart = phraseCodePointLength - cwordCodePointCount
            return arrayOf<String>(
                codePointSubstring(phrase, pwordStart, cwordStart),
                codePointSubstring(phrase, cwordStart, phraseCodePointLength)
            )
        }

        //Jeremy '12,5,1 shared single LIMEDB object
        //Jeremy '12,4,6 Combine updatedb and quierydb into db,
        //Jeremy '12,4,7 move db open/close back to LimeDB
        //Jeremy '12,6,9 make run-time suggestion phrase
        private const val doRunTimeSuggestion = true

        private val scorelist: MutableList<Mapping?>? = Collections.synchronizedList<Mapping?>(
            ArrayList<Mapping?>()
        )

        //Jeremy '15,6,2 preserve the exact match mapping with the code user typed.
        @JvmField
        protected var suggestionLoL: MutableList<MutableList<Pair<Mapping?, String?>>>? = null
        @JvmField
        protected var bestSuggestionStack: Stack<Pair<Mapping?, String?>>? = null
        @JvmField
        protected var lastCode: String? = null // preserved the last code queried from LIMEService

        //Jeremy '15,6,21
        private var maxCodeLength = 4

        private var mResetCache = false

        private var LDPhraseListArray: MutableList<MutableList<Mapping?>?>? = null
        private var LDPhraseList: MutableList<Mapping?>? = null

        private var tablename = ""

        private var isPhysicalKeyboardPressed = false // Sync to LIMEService and LIMEDB

        //Jeremy '11,6,10
        private var hasNumberMapping = false
        private var hasSymbolMapping = false

        private var cache: ConcurrentHashMap<String?, MutableList<Mapping?>?>? = null
        private var engcache: ConcurrentHashMap<String?, MutableList<Mapping?>?>? = null
        private var emojicache: ConcurrentHashMap<String?, MutableList<Mapping?>?>? = null
        private var emojiCategoryPagesCache: MutableList<MutableList<String?>?>? = null
        private var keynamecache: ConcurrentHashMap<String?, String?>? = null

        /**
         * Store the mapping of typing code and mapped code from getMappingByCode on db  Jeremy '12,6,5
         */
        private var coderemapcache: ConcurrentHashMap<String?, MutableList<String>?>? = null

        /**
         * Signals whether the cache should be reset on the next operation.
         * 
         * @param resetCache true to trigger a cache reset.
         */
        @JvmStatic
        fun resetCache(resetCache: Boolean) {
            mResetCache = resetCache
            if (resetCache) {
                emojiCategoryPagesCache = null
            }
        }

        private var prefetchThread: Thread? = null
        private var emojiPreloadThread: Thread? = null

        private fun isReverseLookupResult(result: String?): Boolean {
            return result != null && !result.trim { it <= ' ' }.isEmpty() && result.contains("=")
        }

        private const val dumpRunTimeSuggestion = false

        private var abandonPhraseSuggestion = false

        private fun mappingWords(mappings: MutableList<Mapping?>?): MutableList<String?> {
            val words: MutableList<String?> = ArrayList<String?>()
            if (mappings == null) {
                return words
            }
            for (mapping in mappings) {
                if (mapping != null && mapping.getWord() != null && !mapping.getWord()!!
                        .isEmpty() && !words.contains(mapping.getWord())
                ) {
                    words.add(mapping.getWord())
                }
            }
            return words
        }

        private fun copyEmojiCategoryPages(source: MutableList<MutableList<String?>?>?): MutableList<MutableList<String?>?> {
            val copy: MutableList<MutableList<String?>?> = ArrayList<MutableList<String?>?>()
            if (source == null) {
                return copy
            }
            for (page in source) {
                copy.add(if (page == null) ArrayList<String?>() else ArrayList<String?>(page))
            }
            return copy
        }

        private fun mappingListContainsWord(
            mappings: MutableList<Mapping?>?,
            word: String?
        ): Boolean {
            if (mappings == null || word == null) return false
            for (mapping in mappings) {
                if (mapping != null && word == mapping.getWord()) {
                    return true
                }
            }
            return false
        }


        private var lastEnglishWord: String? = null
        private var noSuggestionsForLastEnglishWord = false
    }
}
