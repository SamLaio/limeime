@file:Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST", "TYPE_INTERSECTION_AS_REIFIED")

package net.toload.main.hd

import android.content.ContentValues
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.data.Keyboard
import net.toload.main.hd.data.Mapping
import net.toload.main.hd.data.Record
import net.toload.main.hd.limedb.LimeDB
import net.toload.main.hd.ui.LIMESettings
import net.toload.main.hd.ui.ProgressManager
import net.toload.main.hd.ui.ShareManager
import net.toload.main.hd.voice.LIMEDictationController
import net.toload.main.hd.voice.DictationState
import org.json.JSONObject
import java.io.File
import java.lang.reflect.AccessibleObject
import java.nio.charset.Charset

fun Throwable.getMessage(): String? = message

fun Throwable.getCause(): Throwable? = cause

fun Number?.intValue(): Int = this!!.toInt()

val File.length: Long
    get() = length()

val JSONObject.length: Int
    get() = length()

fun String.matches(regex: String): Boolean = matches(regex.toRegex())

fun String.getBytes(charset: Charset): ByteArray = toByteArray(charset)

val String?.length: Int
    get() = this!!.length

fun String?.contains(other: CharSequence): Boolean = this!!.contains(other)

fun String?.startsWith(prefix: String): Boolean = this!!.startsWith(prefix)

fun String?.endsWith(suffix: String): Boolean = this!!.endsWith(suffix)

fun String?.trim(): String = this!!.trim()

fun String?.isEmpty(): Boolean = this!!.isEmpty()

fun <T> Collection<T>.size(): Int = size

@JvmName("nullableCollectionSize")
fun <T> Collection<T>?.size(): Int = this!!.size

fun <T> Collection<T>?.isEmpty(): Boolean = this!!.isEmpty()

fun <K, V> Map<K, V>.size(): Int = size

@JvmName("nullableMapSize")
fun <K, V> Map<K, V>?.size(): Int = this!!.size

fun <K, V> Map<K, V>.keySet(): Set<K> = keys

@JvmName("nullableMapKeySet")
fun <K, V> Map<K, V>?.keySet(): Set<K> = this!!.keys

fun <K, V> Map<K, V>.entrySet(): Set<Map.Entry<K, V>> = entries

@JvmName("nullableMapEntrySet")
fun <K, V> Map<K, V>?.entrySet(): Set<Map.Entry<K, V>> = this!!.entries

fun <K, V> Map<K, V>.values(): Collection<V> = values

@JvmName("nullableMapValues")
fun <K, V> Map<K, V>?.values(): Collection<V> = this!!.values

fun <K, V> Map.Entry<K, V>.getKey(): K = key

fun <K, V> Map.Entry<K, V>.getValue(): V = value

operator fun <T> MutableList<T?>?.get(index: Int): T = this!![index]!!

fun <T> MutableList<T?>?.add(element: T): Boolean = this!!.add(element)

fun AccessibleObject.setAccessible(flag: Boolean) {
    isAccessible = flag
}

val <T> Array<T>.length: Int
    get() = size

@get:JvmName("nullableArrayLength")
val <T> Array<T>?.length: Int
    get() = this!!.size

val IntArray.length: Int
    get() = size

val LongArray.length: Int
    get() = size

val ByteArray.length: Int
    get() = size

val CharArray.length: Int
    get() = size

val BooleanArray.length: Int
    get() = size

@get:JvmName("nullableBooleanArrayLength")
val BooleanArray?.length: Int
    get() = this!!.size

fun ImConfig.setCode(value: String?) {
    code = value
}

fun ImConfig?.getCode(): String? = this!!.code

fun ImConfig.setTitle(value: String?) {
    title = value
}

fun ImConfig?.getTitle(): String? = this!!.title

fun ImConfig.setDesc(value: String?) {
    desc = value
}

fun ImConfig?.getDesc(): String? = this!!.desc

fun ImConfig.setDisable(value: Boolean) {
    isDisable = value
}

fun ImConfig?.getDisable(): Boolean = this!!.isDisable

fun ImConfig.setKeyboard(value: String?) {
    keyboard = value
}

fun ImConfig?.getKeyboard(): String? = this!!.keyboard

fun Keyboard.setCode(value: String?) {
    code = value
}

fun Keyboard?.getCode(): String? = this!!.code

fun Keyboard.setName(value: String?) {
    name = value
}

fun Keyboard?.getName(): String? = this!!.name

fun Keyboard.setDesc(value: String?) {
    desc = value
}

fun Keyboard?.getDesc(): String? = this!!.desc

fun Keyboard.setEngkb(value: String?) {
    engkb = value
}

fun Keyboard.setEngshiftkb(value: String?) {
    engshiftkb = value
}

fun LIMESettings.getProgressManager(): ProgressManager? = progressManager

fun LIMESettings.getShareManager(): ShareManager? = shareManager

fun LIMEDictationController.getState(): DictationState = state

fun LIMEKeyboardSwitcher.getKeyboardMode(): Int = keyboardMode

fun SearchServer.getKeyboardList(): MutableList<Keyboard?>? = keyboard

fun LimeDB.getKeyboardConfigList(): MutableList<Keyboard?>? = keyboardConfigList

fun LimeDB.getCountImported(): Int = countImported

fun LimeDB.getProgressPercentageDone(): Int = progressPercentageDone

fun LimeDB.getMappingByCode(code: String?, softKeyboard: Boolean, getAllRecords: Boolean): MutableList<Mapping?>? =
    getMappingByCode(code.orEmpty(), softKeyboard, getAllRecords)

fun LimeDB.addOrUpdateMappingRecord(code: String?, word: String?) {
    if (code != null && word != null) addOrUpdateMappingRecord(code, word)
}

fun LimeDB.addOrUpdateMappingRecord(table: String?, code: String?, word: String?, score: Int) {
    if (table != null && code != null && word != null) addOrUpdateMappingRecord(table, code, word, score)
}

fun LimeDB.addRecord(table: String?, values: ContentValues?): Long =
    if (table == null) -1 else addRecord(table, values)

fun LimeDB.deleteRecord(table: String?, whereClause: String?, whereArgs: Array<String?>?): Int =
    if (table == null) -1 else deleteRecord(table, whereClause, whereArgs)

fun LimeDB.updateRecord(table: String?, values: ContentValues?, whereClause: String?, whereArgs: Array<String?>?): Int =
    if (table == null) -1 else updateRecord(table, values, whereClause, whereArgs)

fun LimeDB.clearTable(tableName: String?) {
    if (tableName != null) clearTable(tableName)
}

fun LimeDB.getRecordList(code: String?, query: String?, searchByCode: Boolean, maximum: Int, offset: Int): MutableList<Record> =
    if (code == null) ArrayList() else getRecordList(code, query, searchByCode, maximum, offset)

fun LimeDB.setImConfigKeyboard(imCode: String?, keyboard: Keyboard?) {
    if (keyboard != null) setImConfigKeyboard(imCode, keyboard)
}

fun LimeDB.keyToKeyName(code: String?, table: String, preferUserDef: Boolean): String =
    keyToKeyName(code ?: throw NullPointerException("code"), table, preferUserDef)

fun LimeDB.getImConfig(imCode: String?, field: String?): String? =
    if (imCode == null || field == null) null else getImConfig(imCode, field)

fun LimeDB.getMappingByWord(keyword: String?, table: String?): MutableList<Mapping?>? =
    if (keyword == null || table == null) ArrayList() else getMappingByWord(keyword, table)

fun LimeDB.exportTxtTable(table: String?, file: File?, imConfigInfo: MutableList<ImConfig?>?): Boolean =
    if (table == null || file == null) false else exportTxtTable(table, file, imConfigInfo?.filterNotNull()?.toMutableList())

fun SearchServer.setTableName(table: String?, numberMapping: Boolean, symbolMapping: Boolean) {
    setTableName(table ?: throw IllegalArgumentException("Invalid table name"), numberMapping, symbolMapping)
}

fun LIMEService.getEmojiKeyboardViewForTesting(): android.view.View? = emojiKeyboardViewForTesting

fun LIMEService.getEmojiPageViewCountForTesting(): Int = emojiPageViewCountForTesting

fun LIMEService.getEmojiCategoryTabCountForTesting(): Int = emojiCategoryTabCountForTesting

fun LIMEService.getInputViewGenerationForTesting(): Int = inputViewGenerationForTesting

fun Mapping?.getWord(): String? = this!!.getWord()

fun Mapping?.setWord(value: String?) = this!!.setWord(value)

fun Mapping?.getCode(): String? = this!!.getCode()

fun Mapping?.setCode(value: String?) = this!!.setCode(value)

fun Mapping?.getScore(): Int = this!!.getScore()

fun Mapping?.setScore(value: Int) = this!!.setScore(value)

fun Mapping?.getPword(): String? = this!!.getPword()

fun Mapping?.setPword(value: String?) = this!!.setPword(value)

fun Boolean.Companion.valueOf(value: Boolean): Boolean = value

fun Long.Companion.parseLong(value: String): Long = value.toLong()
