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

import java.util.LinkedList

/**
 * Provides Chinese punctuation symbol conversion utilities.
 *
 * @author LimeIME Team
 */
class ChineseSymbol {
    companion object {
        @JvmField
        val chineseSymbols: String = "，|。|、|？|！|：|；|（|）|「|」|『|』|【|】|" +
            "／|＼|－|＿|＊|＆|︿|％|＄|＃|＠|～|｛|｝|［|］|＜|＞|＋|｜|‵|＂"

        private val chineseSymbolMapping: MutableList<Mapping> = LinkedList()

        @JvmStatic
        fun getSymbol(symbol: Char): String? {
            return when (symbol) {
                '.' -> "。"
                ',' -> "，"
                '/' -> "／"
                '\\' -> "＼"
                '=' -> "＝"
                '-' -> "－"
                '_' -> "＿"
                '*' -> "＊"
                '&' -> "＆"
                '^' -> "︿"
                '%' -> "％"
                '$' -> "＄"
                '#' -> "＃"
                '@' -> "＠"
                '~' -> "～"
                '`' -> "‵"
                '"' -> "＂"
                '\'' -> "’"
                '?' -> "？"
                '}' -> "｝"
                '{' -> "｛"
                ']' -> "］"
                '[' -> "［"
                '<' -> "＜"
                '>' -> "＞"
                '+' -> "＋"
                '(' -> "（"
                ')' -> "）"
                '|' -> "｜"
                ':' -> "："
                ';' -> "；"
                '1' -> "１"
                '2' -> "２"
                '3' -> "３"
                '4' -> "４"
                '5' -> "５"
                '6' -> "６"
                '7' -> "７"
                '8' -> "８"
                '9' -> "９"
                '0' -> "０"
                '!' -> "！"
                else -> null
            }
        }

        @JvmStatic
        fun getChineseSymoblList(): List<Mapping> {
            if (chineseSymbolMapping.isEmpty()) {
                val symArray = chineseSymbols.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }

                for (sym in symArray) {
                    val mapping = Mapping()
                    mapping.setCode("")
                    mapping.setWord(sym)
                    mapping.setChinesePunctuationSymbolRecord()
                    chineseSymbolMapping.add(mapping)
                }
            }
            return chineseSymbolMapping
        }
    }
}
