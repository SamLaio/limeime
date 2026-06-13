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

package net.toload.main.hd.data


/**
 * Unified data model for IME mapping records.
 *
 * This class represents IME candidates, database records, and related phrases.
 * It intentionally keeps Java-style accessors so the remaining Java sources can
 * continue to call the same API during the Kotlin migration.
 *
 * @author Art Hung
 * @author LimeIME Team
 */
open class Mapping {
    private var id: String? = null
    private var code: String? = null
    private var codeorig: String? = null
    private var code3r: String? = null
    private var word: String? = null
    private var pword: String? = null
    private var related: String? = null
    private var score = 0
    private var basescore = 0
    private var highLighted: Boolean? = true
    private var recordType = 0

    constructor()

    constructor(mapping: Mapping) {
        setId(mapping.id)
        setCode(mapping.code)
        setCodeorig(mapping.codeorig)
        setCode3r(mapping.code3r)
        setWord(mapping.word)
        setPword(mapping.pword)
        setRelated(mapping.related)
        setScore(mapping.score)
        setBasescore(mapping.basescore)
        setHighLighted(mapping.isHighLighted())
        setRecordType(mapping.recordType)
    }

    fun getId(): String? = id

    fun setId(id: String?) {
        this.id = id
    }

    fun getIdAsInt(): Int {
        if (!id.isNullOrEmpty()) {
            return try {
                id!!.toInt()
            } catch (e: NumberFormatException) {
                0
            }
        }
        return 0
    }

    fun setId(id: Int) {
        this.id = id.toString()
    }

    fun getCode(): String? = code?.lowercase()

    fun setCode(code: String?) {
        this.code = code
    }

    fun getCodeorig(): String? = codeorig

    fun setCodeorig(codeorig: String?) {
        this.codeorig = codeorig
    }

    fun getCode3r(): String? = code3r

    fun setCode3r(code3r: String?) {
        this.code3r = code3r
    }

    fun getWord(): String? = word

    fun setWord(word: String?) {
        this.word = word
    }

    fun getCword(): String? = word

    fun setCword(cword: String?) {
        word = cword
    }

    fun getPword(): String? = pword

    fun setPword(pword: String?) {
        this.pword = pword
    }

    fun getRelated(): String? = related

    fun setRelated(related: String?) {
        this.related = related
    }

    fun getScore(): Int = score

    fun setScore(score: Int) {
        this.score = score
    }

    fun getUserscore(): Int = score

    fun setUserscore(userscore: Int) {
        score = userscore
    }

    fun getBasescore(): Int = basescore

    fun setBasescore(score: Int) {
        basescore = score
    }

    fun isHighLighted(): Boolean? = highLighted

    fun setHighLighted(related: Boolean?) {
        highLighted = related
    }

    private fun setRecordType(recordType: Int) {
        this.recordType = recordType
    }

    fun getRecordType(): Int = recordType

    fun isComposingCodeRecord(): Boolean = recordType == RECORD_COMPOSING_CODE

    fun isExactMatchToCodeRecord(): Boolean = recordType == RECORD_EXACT_MATCH_TO_CODE

    fun isPartialMatchToCodeRecord(): Boolean = recordType == RECORD_PARTIAL_MATCH_TO_CODE

    fun isRelatedPhraseRecord(): Boolean = recordType == RECORD_RELATED_PHRASE

    fun isEnglishSuggestionRecord(): Boolean = recordType == RECORD_ENGLISH_SUGGESTION

    fun isChinesePunctuationSymbolRecord(): Boolean = recordType == RECORD_CHINESE_PUNCTUATION_SYMBOL

    fun isHasMoreRecordsMarkRecord(): Boolean = recordType == RECORD_HAS_MORE_RECORDS_MARK

    fun isRuntimeBuiltPhraseRecord(): Boolean = recordType == RECORD_RUNTIME_BUILT_PHRASE

    fun isEmojiRecord(): Boolean = recordType == RECORD_EMOJI_WORD

    fun isExactMatchToWordRecord(): Boolean = recordType == RECORD_EXACT_MATCH_TO_WORD

    fun isPartialMatchToWordRecord(): Boolean = recordType == RECORD_PARTIAL_MATCH_TO_WORD

    fun isCompletionSuggestionRecord(): Boolean = recordType == RECORD_COMPLETION_SUGGESTION_WORD

    fun setComposingCodeRecord() {
        recordType = RECORD_COMPOSING_CODE
    }

    fun setExactMatchToCodeRecord() {
        recordType = RECORD_EXACT_MATCH_TO_CODE
    }

    fun setPartialMatchToCodeRecord() {
        recordType = RECORD_PARTIAL_MATCH_TO_CODE
    }

    fun setRelatedPhraseRecord() {
        recordType = RECORD_RELATED_PHRASE
    }

    fun setEnglishSuggestionRecord() {
        recordType = RECORD_ENGLISH_SUGGESTION
    }

    fun setChinesePunctuationSymbolRecord() {
        recordType = RECORD_CHINESE_PUNCTUATION_SYMBOL
    }

    fun setHasMoreRecordsMarkRecord() {
        recordType = RECORD_HAS_MORE_RECORDS_MARK
    }

    fun setRuntimeBuiltPhraseRecord() {
        recordType = RECORD_RUNTIME_BUILT_PHRASE
    }

    fun setExactMatchToWordRecord() {
        recordType = RECORD_EXACT_MATCH_TO_WORD
    }

    fun setPartialMatchToWordRecord() {
        recordType = RECORD_PARTIAL_MATCH_TO_WORD
    }

    fun setCompletionSuggestionRecord() {
        recordType = RECORD_COMPLETION_SUGGESTION_WORD
    }

    fun setEmojiRecord() {
        recordType = RECORD_EMOJI_WORD
    }

    companion object {
        const val RECORD_COMPOSING_CODE = 1
        const val RECORD_EXACT_MATCH_TO_CODE = 2
        const val RECORD_PARTIAL_MATCH_TO_CODE = 3
        const val RECORD_RELATED_PHRASE = 4
        const val RECORD_ENGLISH_SUGGESTION = 5
        const val RECORD_RUNTIME_BUILT_PHRASE = 6
        const val RECORD_CHINESE_PUNCTUATION_SYMBOL = 7
        const val RECORD_HAS_MORE_RECORDS_MARK = 8
        const val RECORD_EXACT_MATCH_TO_WORD = 9
        const val RECORD_PARTIAL_MATCH_TO_WORD = 10
        const val RECORD_COMPLETION_SUGGESTION_WORD = 11
        const val RECORD_EMOJI_WORD = 12
    }
}
