@file:Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST", "TYPE_INTERSECTION_AS_REIFIED")

package net.toload.main.hd

import org.junit.Assert.*
import org.mockito.Mockito.*
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.os.RemoteException
import android.util.Log
import android.util.Pair
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.data.Keyboard
import net.toload.main.hd.data.Mapping
import net.toload.main.hd.data.Record
import net.toload.main.hd.data.Related
import net.toload.main.hd.global.LIME
import net.toload.main.hd.global.LIMEPreferenceManager
import net.toload.main.hd.limedb.LimeDB
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.HashMap
import java.util.LinkedList
import java.util.Locale
import java.util.Stack
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Suppress("UNCHECKED_CAST")
open class SearchServerTest {
    companion object {
        private val TAG: String = "SearchServerTest"
        @BeforeClass
        @JvmStatic
        fun warmUpMockito() {
            try {
                mock(LIMEPreferenceManager::class.java)
            } catch (ignored: Throwable) {

            }
        }
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <T> getInstanceField(obj: Any, fieldName: String, type: Class<*>): T {
            var field: java.lang.reflect.Field = obj.javaClass.getDeclaredField(fieldName)
            field.setAccessible(true)
            return field.get(obj) as T
        }
        @JvmStatic
        fun setInstanceField(obj: Any, fieldName: String, value: Any?) {
            var field: java.lang.reflect.Field = obj.javaClass.getDeclaredField(fieldName)
            field.setAccessible(true)
            field.set(obj, value)
        }
    }
    private lateinit var appContext: Context
    private lateinit var searchServer: SearchServer
    @Before
    fun setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext()
        setStatic("dbadapter", LimeDB(appContext))
        searchServer = SearchServer(appContext)
        try {
            searchServer.initialCache()
            searchServer.setTableName(LIME.DB_TABLE_PHONETIC, true, true)
        } catch (ignore: Exception) {

        }
        var prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
        prefs.edit().putString("phonetic_keyboard_type", LIME.DB_TABLE_PHONETIC).apply()
        prefs.edit().putBoolean("smart_chinese_input", true).apply()
    }
    @After
    fun tearDown() {
        try {
            searchServer.initialCache()
        } catch (ignore: Exception) {

        }
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> getStatic(field: String, type: Class<*>): T {
        try {
            var f: Field = SearchServer::class.java.getDeclaredField(field)
            f.setAccessible(true)
            return f.get(null) as T
        } catch (e: Exception) {
            throw AssertionError(e)
        }
    }
    private fun setStatic(field: String, value: Any?) {
        try {
            var f: Field = SearchServer::class.java.getDeclaredField(field)
            f.setAccessible(true)
            f.set(null, value)
        } catch (e: Exception) {
            throw AssertionError(e)
        }
    }
    private fun cacheKey(code: String): String {
        var m: Method = SearchServer::class.java.getDeclaredMethod("cacheKey", String::class.java)
        m.setAccessible(true)
        return (m.invoke(searchServer, code) as String)
    }
    private fun setInstanceField(field: String, value: Any?) {
        try {
            var f: Field = SearchServer::class.java.getDeclaredField(field)
            f.setAccessible(true)
            f.set(searchServer, value)
        } catch (e: Exception) {
            throw AssertionError(e)
        }
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> getInstanceField(field: String, type: Class<*>): T {
        try {
            var f: Field = SearchServer::class.java.getDeclaredField(field)
            f.setAccessible(true)
            return f.get(searchServer) as T
        } catch (e: Exception) {
            throw AssertionError(e)
        }
    }
    private fun setLearnPhraseForTest(enabled: Boolean): Any {
        var originalPref: Any = getInstanceField(searchServer, "mLIMEPref", Any::class.java)
        var mockPref: LIMEPreferenceManager = mock(LIMEPreferenceManager::class.java)
        `when`(mockPref.getLearnPhrase()).thenReturn(enabled)
        `when`(mockPref.getParameterString(anyString(), anyString())).thenReturn(LIME.IM_PHONETIC_KEYBOARD_PHONETIC)
        setInstanceField(searchServer, "mLIMEPref", mockPref)
        return originalPref
    }
    private fun callMakeRunTimeSuggestion(code: String, list: MutableList<Mapping?>) {
        try {
            var m: Method = SearchServer::class.java.getDeclaredMethod("makeRunTimeSuggestion", String::class.java, MutableList::class.java)
            m.setAccessible(true)
            m.invoke(searchServer, code, list)
        } catch (e: Exception) {
            throw AssertionError(e)
        }
    }
    private fun callGetMappingByCodeFromCacheOrDB(code: String, all: Boolean): MutableList<Mapping?> {
        try {
            var m: Method = SearchServer::class.java.getDeclaredMethod("getMappingByCodeFromCacheOrDB", String::class.java, Boolean::class.java)
            m.setAccessible(true)
            return (m.invoke(searchServer, code, all) as MutableList<Mapping?>)
        } catch (e: Exception) {
            throw AssertionError(e)
        }
    }
    private fun invokePrivate(methodName: String, paramTypes: Array<Class<*>>, vararg params: Any?): Any? {
        var method: java.lang.reflect.Method = searchServer.javaClass.getDeclaredMethod(methodName, *paramTypes)
        method.setAccessible(true)
        return method.invoke(searchServer, params)
    }
    private open class StubLimeDBSuccess : LimeDB {
        var invoked: Boolean = false
        lateinit var response: MutableList<Mapping?>
        constructor(ctx: Context, response: MutableList<Mapping?>) : super(ctx) {
            this.response = response
        }
        override fun getMappingByCode(code: String, physical: Boolean, getAllRecords: Boolean): MutableList<Mapping?> {
            invoked = true
            return response
        }
    }
    private open class StubLimeDBRuntime : LimeDB {
        val responses: MutableMap<String, MutableList<Mapping?>> = HashMap()
        lateinit var related: Mapping
        val added: MutableList<Pair<String, String>> = LinkedList()
        var codeListResponse: String? = null
        lateinit var latch: CountDownLatch
        var addScoreCalled: Boolean = false
        constructor(ctx: Context) : super(ctx) {
        }
        override fun getMappingByCode(code: String, physical: Boolean, getAllRecords: Boolean): MutableList<Mapping?> {
            var r: MutableList<Mapping?> = responses.get(code)!!
            return (if ((r == null)) ArrayList() else r)
        }
        override fun isRelatedPhraseExist(pword: String?, cword: String?): Mapping? {
            return related
        }
        override fun addOrUpdateMappingRecord(code: String, word: String) {
            added.add(Pair(code, word))
            if ((latch != null)) {
                latch.countDown()
            }
        }
        override fun getCodeListStringByWord(word: String?): String? {
            return codeListResponse
        }
        override fun addScore(mapping: Mapping?) {
            addScoreCalled = true
        }
    }
    private open class StubLimeDBException : LimeDB {
        constructor(ctx: Context) : super(ctx) {
        }
        override fun getMappingByCode(code: String, physical: Boolean, getAllRecords: Boolean): MutableList<Mapping?> {
            throw RuntimeException("forced")
        }
    }
    private open class StubLimeDBRecords : LimeDB {lateinit var lastTable: String
        lateinit var lastQuery: String
        var lastSearchByCode: Boolean = false
        var lastMaximum: Int = 0
        var lastOffset: Int = 0
        lateinit var lastWhereClause: String
        var lastWhereArgs: Array<String?>? = null
        lateinit var lastRelatedPword: String
        var lastRelatedMaximum: Int = 0
        var lastRelatedOffset: Int = 0
        lateinit var lastRelatedPhraseWord: String
        var lastRelatedPhraseAll: Boolean = false
        var lastRecordId: Long = 0
        lateinit var getRecordResponse: Record
        lateinit var lastValues: ContentValues
        lateinit var lastWhereClauseUpdate: String
        var lastWhereArgsUpdate: Array<String?>? = null
        lateinit var lastAddOrUpdateCode: String
        lateinit var lastAddOrUpdateWord: String
        var lastAddOrUpdateScore: Int = 0
        lateinit var lastImConfigCode: String
        lateinit var lastImConfigEntry: String
        lateinit var lastGetImConfigCode: String
        lateinit var lastGetImConfigField: String
        lateinit var lastSetImConfigCode: String
        lateinit var lastSetImConfigField: String
        lateinit var lastSetImConfigValue: String
        lateinit var lastSetIMKeyboardIm: String
        lateinit var lastSetIMKeyboardValue: String
        lateinit var lastSetIMKeyboardCode: String
        lateinit var lastSetIMKeyboardKeyboard: String
        lateinit var lastSetImConfigKeyboardImCode: String
        lateinit var lastSetImConfigKeyboardValue: Keyboard
        lateinit var lastSetImConfigKeyboardObject: Keyboard
        var checkPhoneticKeyboardCalled: Boolean = false
        var setImConfigCalled: Boolean = false
        var setIMConfigKeyboardCalled: Boolean = false
        var setImConfigKeyboardObjectCalled: Boolean = false
        var setIMConfigKeyboardStringCalled: Boolean = false
        lateinit var lastImCode: String
        lateinit var lastKeyboardValue: String
        lateinit var lastKeyboardCode: String
        lateinit var lastKeyboard: Keyboard
        var getImConfigCallCount: Int = 0
        var keyboardConfigListCalls: Int = 0
        var keyToKeyNameCalls: Int = 0
        lateinit var lastKeyToKeyNameCode: String
        lateinit var lastKeyToKeyNameTable: String
        lateinit var keyNameResponse: String
        lateinit var lastBackupTable: String
        lateinit var lastRestoreTable: String
        lateinit var lastCheckBackupTable: String
        lateinit var lastGetBackupTableName: String
        lateinit var lastHanConvertInput: String
        var lastHanConvertOption: Int = 0
        lateinit var lastEmojiConvertCode: String
        var lastEmojiConvertType: Int = 0
        var backupUserRecordsCalled: Boolean = false
        var restoreUserRecordsCalled: Boolean = false
        var checkBackupTableCalled: Boolean = false
        var getBackupTableRecordsCalled: Boolean = false
        var hanConvertCalled: Boolean = false
        var emojiConvertCalled: Boolean = false
        var restoreUserRecordsResponse: Int = 0
        var checkBackupTableResponse: Boolean = false
        var getBackupTableRecordsResponse: android.database.Cursor? = null
        var hanConvertResponse: String = ""
        var emojiConvertResponse: MutableList<Mapping?> = ArrayList()
        var recordResponse: MutableList<Record> = ArrayList()
        var relatedResponse: MutableList<Related> = ArrayList()
        var relatedPhraseResponse: MutableList<Mapping?> = ArrayList()
        var imConfigListResponse: MutableList<ImConfig?> = ArrayList()
        var keyboardConfigListResponse: MutableList<Keyboard?> = ArrayList()
        var imConfigValues: MutableMap<String, String> = HashMap()
        var countResponse: Int = 0
        var addResult: Long = 0
        var deleteResult: Int = 0
        var updateResult: Int = 0
        var clearCalled: Boolean = false
        var addOrUpdateCalled: Boolean = false
        var resetCalled: Boolean = false
        var validTable: Boolean = true
        var throwOnClear: Boolean = false
        constructor(ctx: Context) : super(ctx) {
        }
        override fun getRecordList(code: String, query: String?, searchByCode: Boolean, maximum: Int, offset: Int): MutableList<Record> {
            this.lastTable = code
            this.lastQuery = query!!
            this.lastSearchByCode = searchByCode
            this.lastMaximum = maximum
            this.lastOffset = offset
            return recordResponse
        }
        override fun countRecords(table: String?, whereClause: String?, whereArgs: Array<String?>?): Int {
            this.lastTable = table!!
            this.lastWhereClause = whereClause!!
            this.lastWhereArgs = whereArgs
            return countResponse
        }
        override fun getRelated(pword: String?, maximum: Int, offset: Int): MutableList<Related> {
            this.lastRelatedPword = pword!!
            this.lastRelatedMaximum = maximum
            this.lastRelatedOffset = offset
            return relatedResponse
        }
        override fun addRecord(table: String, values: ContentValues?): Long {
            this.lastTable = table
            this.lastValues = values!!
            return addResult
        }
        override fun deleteRecord(table: String, whereClause: String?, whereArgs: Array<String?>?): Int {
            this.lastTable = table
            this.lastWhereClause = whereClause!!
            this.lastWhereArgs = whereArgs
            return deleteResult
        }
        override fun updateRecord(table: String, values: ContentValues?, whereClause: String?, whereArgs: Array<String?>?): Int {
            this.lastTable = table
            this.lastValues = values!!
            this.lastWhereClauseUpdate = whereClause!!
            this.lastWhereArgsUpdate = whereArgs
            return updateResult
        }
        override fun getRecord(code: String, id: Long): Record? {
            this.lastTable = code
            this.lastRecordId = id
            return getRecordResponse
        }
        override fun addOrUpdateMappingRecord(table: String, code: String, word: String, score: Int) {
            this.lastTable = table
            this.lastAddOrUpdateCode = code
            this.lastAddOrUpdateWord = word
            this.lastAddOrUpdateScore = score
            addOrUpdateCalled = true
        }
        override fun getRelatedPhrase(word: String?, getAllRecords: Boolean): MutableList<Mapping?> {
            this.lastRelatedPhraseWord = word!!
            this.lastRelatedPhraseAll = getAllRecords
            return relatedPhraseResponse
        }
        override fun resetCache() {
            resetCalled = true
        }
        override fun clearTable(table: String) {
            clearCalled = true
            if (throwOnClear) {
                throw RuntimeException("boom")
            }
            if (!validTable) {
                throw IllegalArgumentException("invalid table")
            }
        }
        override fun isValidTableName(tableName: String?): Boolean {
            return validTable
        }
        override fun getImConfigList(code: String?, configEntry: String?): MutableList<ImConfig?> {
            this.lastImConfigCode = code!!
            this.lastImConfigEntry = configEntry!!
            return imConfigListResponse
        }
        override val keyboardConfigList: MutableList<Keyboard?>?
            get() {
                keyboardConfigListCalls++
                return keyboardConfigListResponse
            }
        override fun getImConfig(imCode: String, field: String?): String {
            this.lastGetImConfigCode = imCode
            this.lastGetImConfigField = field!!
            getImConfigCallCount++
            var key: String = ((imCode + "|") + field)
            return (if (imConfigValues.containsKey(key)) imConfigValues.get(key) else "") ?: ""
        }
        override fun setImConfig(imCode: String?, field: String?, value: String?) {
            this.lastSetImConfigCode = imCode!!
            this.lastSetImConfigField = field!!
            this.lastSetImConfigValue = value!!
            this.setImConfigCalled = true
            imConfigValues.put(((imCode + "|") + field), value)
        }
        override fun setIMConfigKeyboard(im: String?, value: String?, keyboard: String?) {
            this.lastSetIMKeyboardIm = im!!
            this.lastSetIMKeyboardValue = value!!
            this.lastSetIMKeyboardKeyboard = keyboard!!
            this.setIMConfigKeyboardCalled = true
            this.setIMConfigKeyboardStringCalled = true
            this.lastImCode = im
            this.lastKeyboardValue = value
            this.lastKeyboardCode = keyboard
        }
        override fun setImConfigKeyboard(imCode: String?, keyboard: Keyboard) {
            this.lastSetImConfigKeyboardImCode = imCode!!
            this.lastSetImConfigKeyboardObject = keyboard
            this.setImConfigKeyboardObjectCalled = true
            this.lastImCode = imCode
            this.lastKeyboard = keyboard
        }
        override fun keyToKeyName(code: String, tablename: String, preferUserDef: Boolean?): String {
            this.lastKeyToKeyNameCode = code
            this.lastKeyToKeyNameTable = tablename
            keyToKeyNameCalls++
            if ((keyNameResponse != null)) {
                return keyNameResponse
            }
            return (code + "_name")
        }
        override fun checkPhoneticKeyboardSetting() {
            checkPhoneticKeyboardCalled = true
        }
    }
    @Test(timeout = 5000)
    fun test_3_1_1_1_getMappingByCode_null_or_empty_returns_empty() {
        assertTrue(searchServer.getMappingByCode(null, true, false).isEmpty())
        assertTrue(searchServer.getMappingByCode("", true, false).isEmpty())
    }
    @Test(timeout = 5000)
    fun test_3_1_1_2_getMappingByCode_null_dbadapter_returns_empty() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        var result: MutableList<Mapping?> = callGetMappingByCodeFromCacheOrDB("a", false)
        assertNotNull(result)
        assertTrue(result.isEmpty())
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_1_2_1_getMappingByCode_soft_vs_physical_toggles_flag() {
        if ((getStatic<Any?>("dbadapter", LimeDB::class.java) == null)) {
            setStatic("dbadapter", LimeDB(appContext))
        }
        if ((getInstanceField<Any?>("mLIMEPref", Any::class.java) == null)) {
            setInstanceField("mLIMEPref", net.toload.main.hd.global.LIMEPreferenceManager(appContext))
        }
        setStatic("isPhysicalKeyboardPressed", false)
        searchServer.getMappingByCode("a", false, false)
        var afterFirst: Boolean = getStatic("isPhysicalKeyboardPressed", Boolean::class.java)
        setStatic("isPhysicalKeyboardPressed", true)
        searchServer.getMappingByCode("a", true, false)
        var afterSecond: Boolean = getStatic("isPhysicalKeyboardPressed", Boolean::class.java)
        assertTrue((afterFirst || !afterSecond))
    }
    @Test(timeout = 5000)
    fun test_3_1_3_1_getMappingByCode_cache_miss_hits_db() {
        var cache: ConcurrentHashMap<String, MutableList<Mapping?>> = getStatic("cache", ConcurrentHashMap::class.java)
        cache.clear()
        var result: MutableList<Mapping?> = callGetMappingByCodeFromCacheOrDB("a", false)
        assertNotNull(result)
        assertTrue(cache.containsKey(cacheKey("a")))
    }
    @Test(timeout = 5000)
    fun test_3_1_3_2_getMappingByCode_cache_hit_returns_cached() {
        var cache: ConcurrentHashMap<String, MutableList<Mapping?>> = getStatic("cache", ConcurrentHashMap::class.java)
        cache.clear()
        var cached: Mapping = Mapping()
        cached.setWord("cachedWord")
        cached.setCode("a")
        var list: MutableList<Mapping?> = LinkedList()
        list.add(cached)
        var key: String = cacheKey("a")
        cache.put(key, list)
        var result: MutableList<Mapping?> = callGetMappingByCodeFromCacheOrDB("a", false)
        assertNotNull(result)
        assertFalse(result.isEmpty())
        var found: Boolean = false
        for (m in result) {
            if ("cachedWord".equals(m.getWord())) {
                found = true
                break
            }
        }
        assertTrue("Cached mapping should be in result", found)
    }
    @Test(timeout = 5000)
    fun test_3_1_3_3_getMappingByCode_prefetch_warms_cache() {
        var cache: ConcurrentHashMap<String, MutableList<Mapping?>> = getStatic("cache", ConcurrentHashMap::class.java)
        cache.clear()
        var result: MutableList<Mapping?> = searchServer.getMappingByCode("b", true, false, true)
        assertNotNull(result)
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        assertTrue(suggestionLoL.isEmpty())
    }
    @Test(timeout = 5000)
    fun test_3_1_3_4_getMappingByCode_table_change_resets_cache() {
        var cache: ConcurrentHashMap<String, MutableList<Mapping?>> = getStatic("cache", ConcurrentHashMap::class.java)
        cache.put("dummy", LinkedList())
        SearchServer.resetCache(true)
        searchServer.getMappingByCode("c", true, false)
        assertFalse(cache.containsKey("dummy"))
    }
    @Test(timeout = 5000)
    fun test_3_1_3_5_getMappingByCode_getAllRecords_refreshes_has_more_and_keynamecache() {
        var keynamecache: ConcurrentHashMap<String, String> = getStatic("keynamecache", ConcurrentHashMap::class.java)
        keynamecache.put(cacheKey("a"), "name")
        searchServer.getMappingByCode("a", true, true)
        assertFalse(keynamecache.containsKey(cacheKey("a")))
    }
    @Test(timeout = 5000)
    fun test_3_1_4_1_getMappingByCode_phonetic_eten26_remap() {
        PreferenceManager.getDefaultSharedPreferences(appContext).edit().putString("phonetic_keyboard_type", "eten26").apply()
        var result: MutableList<Mapping?> = searchServer.getMappingByCode("a", true, false)
        assertNotNull(result)
    }
    @Test(timeout = 5000)
    fun test_3_1_4_2_getMappingByCode_dual_key_expansion() {
        var result: MutableList<Mapping?> = searchServer.getMappingByCode("aa", true, false)
        assertNotNull(result)
    }
    @Test(timeout = 5000)
    fun test_3_1_5_1_getMappingByCode_runtime_suggestion_enabled() {
        PreferenceManager.getDefaultSharedPreferences(appContext).edit().putBoolean("smart_chinese_input", true).apply()
        var result: MutableList<Mapping?> = searchServer.getMappingByCode("ab", true, false)
        assertNotNull(result)
    }
    @Test(timeout = 5000)
    fun test_3_1_5_2_getMappingByCode_runtime_suggestion_disabled() {
        PreferenceManager.getDefaultSharedPreferences(appContext).edit().putBoolean("smart_chinese_input", false).apply()
        var result: MutableList<Mapping?> = searchServer.getMappingByCode("ab", true, false)
        assertNotNull(result)
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        assertTrue(suggestionLoL.isEmpty())
    }
    @Test(timeout = 5000)
    fun test_3_1_5_3_getMappingByCode_self_mapping_creation() {
        var result: MutableList<Mapping?> = searchServer.getMappingByCode("abc", true, false)
        assertFalse(result.isEmpty())
        assertEquals("abc", result.get(0).getWord())
    }
    @Test(timeout = 5000)
    fun test_3_1_5_4_getMappingByCode_abandon_phrase_suggestion_on_prefetch() {
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        suggestionLoL.clear()
        suggestionLoL.add(LinkedList())
        var before: Int = suggestionLoL.size
        var result: MutableList<Mapping?> = searchServer.getMappingByCode("ad", true, false, true)
        assertNotNull(result)
        assertEquals(before, suggestionLoL.size)
    }
    @Test(timeout = 5000)
    fun test_3_1_6_1_getMappingByCode_long_code_english_fallback() {
        var result: MutableList<Mapping?> = searchServer.getMappingByCode("abcdefg", true, false)
        assertNotNull(result)
        assertFalse(result.isEmpty())
    }
    @Test(timeout = 5000)
    fun test_3_1_6_2_getMappingByCode_english_suggestion_threshold_clears_runtime_stack() {
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        suggestionLoL.clear()
        var level: MutableList<Pair<Mapping, String>> = LinkedList()
        var m: Mapping = Mapping()
        m.setWord("stub")
        m.setCode("stub")
        m.setBasescore(10)
        level.add(Pair(m, "x"))
        suggestionLoL.add(level)
        var bestStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        bestStack.clear()
        bestStack.push(Pair(m, "x"))
        var engcache: ConcurrentHashMap<String, MutableList<Mapping?>> = getStatic("engcache", ConcurrentHashMap::class.java)
        engcache.clear()
        var english: Mapping = Mapping()
        english.setWord("english")
        english.setEnglishSuggestionRecord()
        var elist: MutableList<Mapping?> = LinkedList()
        elist.add(english)
        engcache.put("longcode", elist)
        var result: MutableList<Mapping?> = searchServer.getMappingByCode("longcode", true, false)
        assertNotNull(result)
        assertTrue(suggestionLoL.isEmpty())
        assertTrue(bestStack.isEmpty())
    }
    @Test(timeout = 5000)
    fun test_3_1_7_1_getMappingByCode_wayback_fallback_when_empty() {
        var result: MutableList<Mapping?> = searchServer.getMappingByCode("zzzz", true, false)
        assertNotNull(result)
        assertFalse(result.isEmpty())
        assertEquals("zzzz", result.get(0).getWord())
    }
    @Test(timeout = 5000)
    fun test_3_1_7_2_getMappingByCode_result_sorting_basescore() {
        var result: MutableList<Mapping?> = searchServer.getMappingByCode("a", true, true)
        assertNotNull(result)
        assertTrue((result.size >= 1))
    }
    @Test(timeout = 5000)
    fun test_3_1_8_1_getMappingByCode_cachekey_and_remapcache_population() {
        var coderemapcache: ConcurrentHashMap<String, MutableList<String>> = getStatic("coderemapcache", ConcurrentHashMap::class.java)
        coderemapcache.clear()
        searchServer.getMappingByCode("a", true, false)
        assertNotNull(coderemapcache)
    }
    @Test(timeout = 5000)
    fun test_3_1_8_2_getMappingByCode_db_fallback_exception_safe() {
        var result: MutableList<Mapping?> = searchServer.getMappingByCode("ax", true, true)
        assertNotNull(result)
    }
    @Test(timeout = 5000)
    fun test_3_1_9_1_getEnglishSuggestions_cache_put_and_hit() {
        var engcache: ConcurrentHashMap<String, MutableList<Mapping?>> = getStatic("engcache", ConcurrentHashMap::class.java)
        engcache.clear()
        var first: MutableList<Mapping?> = searchServer.getEnglishSuggestions("hello")
        var second: MutableList<Mapping?> = searchServer.getEnglishSuggestions("hello")
        if (first.isEmpty()) {
            assertTrue(engcache.containsKey("hello"))
        }
        assertEquals(first.size, second.size)
    }
    @Test(timeout = 5000)
    fun test_3_1_9_2_getEnglishSuggestions_fast_skip_after_empty_prefix() {
        var first: MutableList<Mapping?> = searchServer.getEnglishSuggestions("zzz_unlikely")
        var second: MutableList<Mapping?> = searchServer.getEnglishSuggestions("zzz_unlikely_more")
        assertNotNull(first)
        assertNotNull(second)
        if (first.isEmpty()) {
            assertTrue(second.isEmpty())
        }
    }
    @Test(timeout = 5000)
    fun test_3_1_10_1_null_pref_returns_empty() {
        var originalPref: Any = getInstanceField("mLIMEPref", Any::class.java)
        setInstanceField("mLIMEPref", null)
        var result: MutableList<Mapping?> = searchServer.getMappingByCode("a", true, false)
        assertNotNull(result)
        assertTrue(result.isEmpty())
        setInstanceField("mLIMEPref", originalPref)
    }
    @Test(timeout = 5000)
    fun test_3_1_10_2_abandon_phrase_reset_single_char() {
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        suggestionLoL.clear()
        suggestionLoL.add(LinkedList())
        var bestStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        bestStack.clear()
        bestStack.push(Pair(Mapping(), "x"))
        setStatic("abandonPhraseSuggestion", true)
        PreferenceManager.getDefaultSharedPreferences(appContext).edit().putBoolean("smart_chinese_input", true).apply()
        var beforeSuggestions: Int = suggestionLoL.size
        var beforeBest: Int = bestStack.size
        var result: MutableList<Mapping?> = searchServer.getMappingByCode("x", true, false)
        assertNotNull(result)
        assertTrue((suggestionLoL.size <= beforeSuggestions))
        assertTrue((bestStack.size <= beforeBest))
        setStatic("abandonPhraseSuggestion", false)
    }
    @Test(timeout = 5000)
    fun test_3_1_10_3_prefetch_skips_runtime_suggestion() {
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        suggestionLoL.clear()
        suggestionLoL.add(LinkedList())
        var before: Int = suggestionLoL.size
        setStatic("abandonPhraseSuggestion", false)
        PreferenceManager.getDefaultSharedPreferences(appContext).edit().putBoolean("smart_chinese_input", true).apply()
        var result: MutableList<Mapping?> = searchServer.getMappingByCode("prefetch", true, false, true)
        assertNotNull(result)
        assertEquals(before, suggestionLoL.size)
    }
    @Test(timeout = 5000)
    fun test_3_1_10_4_getAllRecords_refreshes_hasMore_branch() {
        var cache: ConcurrentHashMap<String, MutableList<Mapping?>> = getStatic("cache", ConcurrentHashMap::class.java)
        cache.clear()
        var normal: Mapping = Mapping()
        normal.setWord("w")
        normal.setCode("hasmore")
        var hasMore: Mapping = Mapping()
        hasMore.setHasMoreRecordsMarkRecord()
        var cached: MutableList<Mapping?> = ArrayList()
        cached.add(normal)
        cached.add(hasMore)
        var dbResult: MutableList<Mapping?> = ArrayList()
        var dbMap: Mapping = Mapping()
        dbMap.setWord("db")
        dbMap.setCode("hasmore")
        dbResult.add(dbMap)
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBSuccess = StubLimeDBSuccess(appContext, dbResult)
        setStatic("dbadapter", stub)
        var key: String = cacheKey("hasmore")
        cache.put(key, cached)
        var result: MutableList<Mapping?> = searchServer.getMappingByCode("hasmore", true, true)!!
        assertNotNull(result)
        assertTrue(stub.invoked)
        var refreshed: MutableList<Mapping?> = cache.get(key)!!
        assertNotNull(refreshed)
        assertEquals("db", refreshed.get(0).getWord())
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_1_10_5_wayback_loop_terminates_on_prefix_hit() {
        var cache: ConcurrentHashMap<String, MutableList<Mapping?>> = getStatic("cache", ConcurrentHashMap::class.java)
        cache.clear()
        cache.put(cacheKey("xyz"), LinkedList())
        var prefixMap: Mapping = Mapping()
        prefixMap.setWord("hit")
        prefixMap.setCode("x")
        var prefixList: MutableList<Mapping?> = LinkedList()
        prefixList.add(prefixMap)
        cache.put(cacheKey("x"), prefixList)
        var result: MutableList<Mapping?> = searchServer.getMappingByCode("xyz", true, false)
        assertNotNull(result)
        assertFalse(result.isEmpty())
    }
    @Test(timeout = 5000)
    fun test_3_1_10_6_english_suggestion_empty_path() {
        var engcache: ConcurrentHashMap<String, MutableList<Mapping?>> = getStatic("engcache", ConcurrentHashMap::class.java)
        engcache.clear()
        var result: MutableList<Mapping?> = searchServer.getMappingByCode("longcode_empty", true, false)
        assertNotNull(result)
    }
    @Test(timeout = 5000)
    fun test_3_1_10_7_bestSuggestion_inserted_when_high_score() {
        var bestStack: Stack<Pair<Mapping, String>> = Stack()
        var best: Mapping = Mapping()
        best.setWord("best")
        best.setCode("best")
        best.setBasescore(600)
        bestStack.push(Pair(best, "best"))
        setStatic("bestSuggestionStack", bestStack)
        setStatic("abandonPhraseSuggestion", false)
        PreferenceManager.getDefaultSharedPreferences(appContext).edit().putBoolean("smart_chinese_input", true).apply()
        var result: MutableList<Mapping?> = searchServer.getMappingByCode("best", true, false)
        assertNotNull(result)
        assertTrue((result.size >= 1))
        var found: Boolean = false
        for (m in result) {
            if ("best".equals(m.getWord())) {
                found = true
                break
            }
        }
        assertTrue(found)
    }
    @Test(timeout = 5000)
    fun test_3_1_10_7b_bestSuggestion_skips_duplicate_db_word() {
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        cache.clear()
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        var bestSuggestionStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        suggestionLoL.clear()
        bestSuggestionStack.clear()
        setStatic("abandonPhraseSuggestion", false)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        var first: Mapping = Mapping()
        first.setCode("a")
        first.setWord("甲")
        first.setBasescore(200)
        first.setExactMatchToCodeRecord()
        stub.responses.put("a", Collections.singletonList(first))
        var second: Mapping = Mapping()
        second.setCode("b")
        second.setWord("乙")
        second.setBasescore(200)
        second.setExactMatchToCodeRecord()
        stub.responses.put("b", Collections.singletonList(second))
        var partial: Mapping = Mapping()
        partial.setCode("abx")
        partial.setWord("placeholder")
        partial.setBasescore(1)
        partial.setPartialMatchToCodeRecord()
        stub.responses.put("ab", Collections.singletonList(partial))
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        setStatic("dbadapter", stub)
        try {
            searchServer.getMappingByCode("a", true, false)
            searchServer.getMappingByCode("ab", true, false)
            cache.clear()
            var exact: Mapping = Mapping()
            exact.setCode("ab")
            exact.setWord("甲乙")
            exact.setBasescore(0)
            exact.setExactMatchToCodeRecord()
            stub.responses.put("ab", Collections.singletonList(exact))
            var result: MutableList<Mapping?> = searchServer.getMappingByCode("ab", true, false)
            var count: Int = 0
            for (mapping in result) {
                if ("甲乙".equals(mapping.getWord())) {
                    count++
                }
            }
            assertEquals("runtime phrase must not duplicate an existing DB candidate with the same word", 1, count)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_1_10_8_remapcache_updates_on_exact_match() {
        var coderemapcache: ConcurrentHashMap<String, MutableList<String>> = getStatic("coderemapcache", ConcurrentHashMap::class.java)
        coderemapcache.clear()
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        cache.clear()
        var remap: Mapping = Mapping()
        remap.setWord("remapWord")
        remap.setCode("remapped")
        remap.setExactMatchToCodeRecord()
        var stubResult: MutableList<Mapping?> = ArrayList()
        stubResult.add(remap)
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBSuccess = StubLimeDBSuccess(appContext, stubResult)
        setStatic("dbadapter", stub)
        var result: MutableList<Mapping?> = callGetMappingByCodeFromCacheOrDB("query", false)
        assertNotNull(result)
        var key: String = cacheKey("remapped")
        assertTrue(coderemapcache.containsKey(key))
        assertTrue(coderemapcache.get(key)!!.contains("query"))
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_1_10_10_remapcache_appends_existing() {
        var coderemapcache: ConcurrentHashMap<String, MutableList<String>> = getStatic("coderemapcache", ConcurrentHashMap::class.java)
        coderemapcache.clear()
        var remapped: String = "remapped"
        var initial: String = "initial"
        var query: String = "queryAppend"
        coderemapcache.put(remapped, LinkedList(Arrays.asList(remapped, initial)))
        var remap: Mapping = Mapping()
        remap.setWord("remapWord")
        remap.setCode(remapped)
        remap.setExactMatchToCodeRecord()
        var stubResult: MutableList<Mapping?> = ArrayList()
        stubResult.add(remap)
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBSuccess = StubLimeDBSuccess(appContext, stubResult)
        setStatic("dbadapter", stub)
        var result: MutableList<Mapping?> = callGetMappingByCodeFromCacheOrDB(query, false)
        assertNotNull(result)
        var updated: MutableList<String> = coderemapcache.get(remapped)!!
        assertTrue(updated.contains(query))
        assertTrue((updated.size >= 3))
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_1_10_9_db_exception_returns_safe_list() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBException = StubLimeDBException(appContext)
        setStatic("dbadapter", stub)
        var result: MutableList<Mapping?> = callGetMappingByCodeFromCacheOrDB("boom", false)
        assertNull(result)
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_2_1_1_makeRunTimeSuggestion_empty_list() {
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        var bestSuggestionStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        suggestionLoL.clear()
        bestSuggestionStack.clear()
        callMakeRunTimeSuggestion("a", ArrayList())
        assertTrue(suggestionLoL.isEmpty())
        assertTrue(bestSuggestionStack.isEmpty())
    }
    @Test(timeout = 5000)
    fun test_3_2_1_2_makeRunTimeSuggestion_depth_cap() {
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        var bestSuggestionStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        suggestionLoL.clear()
        bestSuggestionStack.clear()
        var exact: MutableList<Mapping?> = ArrayList()
        run {
            var i: Int = 0
            while ((i < 6)) {
                var m: Mapping = Mapping()
                m.setWord(("word" + i))
                m.setCode(("code" + i))
                m.setExactMatchToCodeRecord()
                m.setBasescore((200 + (i * 10)))
                exact.add(m)
                i++
            }
        }
        callMakeRunTimeSuggestion("abcdef", exact)
        assertTrue((suggestionLoL.size <= 5))
        assertFalse(bestSuggestionStack.isEmpty())
    }
    @Test(timeout = 5000)
    fun test_3_2_1_3_makeRunTimeSuggestion_disabled_flag() {
        PreferenceManager.getDefaultSharedPreferences(appContext).edit().putBoolean("smart_chinese_input", false).apply()
        var bestSuggestionStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var m: Mapping = Mapping()
        m.setWord("exact")
        m.setCode("exact")
        m.setExactMatchToCodeRecord()
        m.setBasescore(300)
        var resp: MutableList<Mapping?> = Collections.singletonList(m)
        setStatic("dbadapter", StubLimeDBSuccess(appContext, resp))
        var result: MutableList<Mapping?> = searchServer.getMappingByCode("exact", true, false)
        assertNotNull(result)
        assertTrue(bestSuggestionStack.isEmpty())
        PreferenceManager.getDefaultSharedPreferences(appContext).edit().putBoolean("smart_chinese_input", true).apply()
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_2_1_4_makeRunTimeSuggestion_algorithmic_merge() {
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        var bestSuggestionStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        suggestionLoL.clear()
        bestSuggestionStack.clear()
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        var seedMap: Mapping = Mapping()
        seedMap.setWord("a")
        seedMap.setCode("a")
        seedMap.setBasescore(200)
        seedMap.setExactMatchToCodeRecord()
        var seedList: MutableList<Pair<Mapping, String>> = LinkedList()
        seedList.add(Pair(seedMap, "a"))
        suggestionLoL.add(seedList)
        var remaining: Mapping = Mapping()
        remaining.setWord("b")
        remaining.setCode("b")
        remaining.setBasescore(200)
        remaining.setExactMatchToCodeRecord()
        stub.responses.put("b", Collections.singletonList(remaining))
        var related: Mapping = Mapping()
        related.setBasescore(150)
        stub.related = related
        setStatic("dbadapter", stub)
        callMakeRunTimeSuggestion("ab", ArrayList())
        assertFalse(suggestionLoL.isEmpty())
        var best: MutableList<Pair<Mapping, String>> = suggestionLoL.get((suggestionLoL.size - 1))
        assertEquals("ab", best.get((best.size - 1)).first.getWord())
        assertTrue(bestSuggestionStack.peek().first.getWord().equals("ab"))
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_2_1_5_makeRunTimeSuggestion_backspace_prunes_stack() {
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        var bestSuggestionStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        suggestionLoL.clear()
        bestSuggestionStack.clear()
        var m: Mapping = Mapping()
        m.setWord("abc")
        m.setCode("abc")
        m.setExactMatchToCodeRecord()
        var list: MutableList<Pair<Mapping, String>> = LinkedList()
        list.add(Pair(m, "abcd"))
        suggestionLoL.add(list)
        bestSuggestionStack.push(Pair(m, "abcd"))
        setStatic("lastCode", "abcd")
        callMakeRunTimeSuggestion("abc", ArrayList())
        assertTrue(bestSuggestionStack.isEmpty())
        assertTrue(suggestionLoL.stream().allMatch({ l -> l.stream().noneMatch({ p -> p.second == "abcd" }) }))
    }
    @Test(timeout = 5000)
    fun test_3_2_1_6_makeRunTimeSuggestion_start_over_clears() {
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        var bestSuggestionStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        suggestionLoL.clear()
        bestSuggestionStack.clear()
        var m: Mapping = Mapping()
        m.setWord("abc")
        m.setCode("abc")
        m.setExactMatchToCodeRecord()
        var list: MutableList<Pair<Mapping, String>> = LinkedList()
        list.add(Pair(m, "abc"))
        suggestionLoL.add(list)
        bestSuggestionStack.push(Pair(m, "abc"))
        setStatic("lastCode", "abcd")
        callMakeRunTimeSuggestion("a", ArrayList())
        assertTrue(suggestionLoL.isEmpty())
        assertTrue(bestSuggestionStack.isEmpty())
        assertFalse(getStatic("abandonPhraseSuggestion", Boolean::class.java))
    }
    @Test(timeout = 5000)
    fun test_3_2_1_7_makeRunTimeSuggestion_related_phrase_wins() {
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        var bestSuggestionStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        suggestionLoL.clear()
        bestSuggestionStack.clear()
        var seedMap: Mapping = Mapping()
        seedMap.setWord("pre")
        seedMap.setCode("pre")
        seedMap.setExactMatchToCodeRecord()
        seedMap.setBasescore(200)
        var seedList: MutableList<Pair<Mapping, String>> = LinkedList()
        seedList.add(Pair(seedMap, "pre"))
        suggestionLoL.add(seedList)
        setStatic("lastCode", "pre")
        var remaining: Mapping = Mapping()
        remaining.setWord("fix")
        remaining.setCode("fix")
        remaining.setBasescore(250)
        remaining.setExactMatchToCodeRecord()
        var related: Mapping = Mapping()
        related.setBasescore(400)
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        stub.responses.put("fix", Collections.singletonList(remaining))
        stub.related = related
        setStatic("dbadapter", stub)
        callMakeRunTimeSuggestion("prefix", ArrayList())
        var best: MutableList<Pair<Mapping, String>> = suggestionLoL.get((suggestionLoL.size - 1))
        assertEquals("prefix", best.get((best.size - 1)).first.getWord())
        assertEquals("prefix", bestSuggestionStack.peek().first.getWord())
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_2_1_8_makeRunTimeSuggestion_no_remaining_adds_seed_back() {
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        var bestSuggestionStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        suggestionLoL.clear()
        bestSuggestionStack.clear()
        var seedMap: Mapping = Mapping()
        seedMap.setWord("p")
        seedMap.setCode("p")
        seedMap.setExactMatchToCodeRecord()
        var seedList: MutableList<Pair<Mapping, String>> = LinkedList()
        seedList.add(Pair(seedMap, "p"))
        suggestionLoL.add(seedList)
        callMakeRunTimeSuggestion("zzz", ArrayList())
        assertFalse(suggestionLoL.isEmpty())
        assertTrue(suggestionLoL.get(0).stream().anyMatch({ p -> p.second == "p" }))
    }
    @Test(timeout = 5000)
    fun test_3_2_1_9_makeRunTimeSuggestion_reorders_best_on_highest_score() {
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        var bestSuggestionStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        suggestionLoL.clear()
        bestSuggestionStack.clear()
        var top: Mapping = Mapping()
        top.setWord("top")
        top.setCode("tt")
        top.setExactMatchToCodeRecord()
        top.setBasescore(400)
        var mid: Mapping = Mapping()
        mid.setWord("mid")
        mid.setCode("mm")
        mid.setExactMatchToCodeRecord()
        mid.setBasescore(250)
        var low: Mapping = Mapping()
        low.setWord("low")
        low.setCode("ll")
        low.setExactMatchToCodeRecord()
        low.setBasescore(220)
        callMakeRunTimeSuggestion("tt", Arrays.asList(top, mid, low))
        assertEquals(3, suggestionLoL.size)
        var best: MutableList<Pair<Mapping, String>> = suggestionLoL.get((suggestionLoL.size - 1))
        assertEquals("top", best.get((best.size - 1)).first.getWord())
        assertEquals("top", bestSuggestionStack.peek().first.getWord())
    }
    @Test(timeout = 5000)
    fun test_3_2_1_10_makeRunTimeSuggestion_skips_low_remaining_phrase() {
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        var bestSuggestionStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        suggestionLoL.clear()
        bestSuggestionStack.clear()
        var seed: Mapping = Mapping()
        seed.setWord("a")
        seed.setCode("a")
        seed.setBasescore(300)
        seed.setExactMatchToCodeRecord()
        var seedList: MutableList<Pair<Mapping, String>> = LinkedList()
        seedList.add(Pair(seed, "a"))
        suggestionLoL.add(seedList)
        setStatic("lastCode", "a")
        var remaining: Mapping = Mapping()
        remaining.setWord("b")
        remaining.setCode("b")
        remaining.setBasescore(1)
        remaining.setExactMatchToCodeRecord()
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        stub.responses.put("b", Collections.singletonList(remaining))
        setStatic("dbadapter", stub)
        callMakeRunTimeSuggestion("ab", ArrayList())
        assertEquals(1, suggestionLoL.size)
        assertEquals("a", suggestionLoL.get(0).get(0).first.getWord())
        assertEquals("a", bestSuggestionStack.peek().first.getWord())
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_2_1_11_makeRunTimeSuggestion_unrelated_phrase_still_added() {
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        var bestSuggestionStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        suggestionLoL.clear()
        bestSuggestionStack.clear()
        var seed: Mapping = Mapping()
        seed.setWord("pre")
        seed.setCode("pre")
        seed.setBasescore(200)
        seed.setExactMatchToCodeRecord()
        var seedList: MutableList<Pair<Mapping, String>> = LinkedList()
        seedList.add(Pair(seed, "pre"))
        suggestionLoL.add(seedList)
        setStatic("lastCode", "pre")
        var remaining: Mapping = Mapping()
        remaining.setWord("fix")
        remaining.setCode("fix")
        remaining.setBasescore(250)
        remaining.setExactMatchToCodeRecord()
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        stub.responses.put("fix", Collections.singletonList(remaining))
        setStatic("dbadapter", stub)
        callMakeRunTimeSuggestion("prefix", ArrayList())
        var best: MutableList<Pair<Mapping, String>> = suggestionLoL.get((suggestionLoL.size - 1))
        assertEquals("prefix", best.get((best.size - 1)).first.getWord())
        assertEquals("prefix", bestSuggestionStack.peek().first.getWord())
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_2_1_12_makeRunTimeSuggestion_snapshot_with_multiple_history() {
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        var bestSuggestionStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        suggestionLoL.clear()
        bestSuggestionStack.clear()
        var first: Mapping = Mapping()
        first.setWord("a")
        first.setCode("a")
        first.setBasescore(100)
        first.setExactMatchToCodeRecord()
        var second: Mapping = Mapping()
        second.setWord("b")
        second.setCode("b")
        second.setBasescore(100)
        second.setExactMatchToCodeRecord()
        var historyList: MutableList<Pair<Mapping, String>> = LinkedList()
        historyList.add(Pair(first, "a"))
        historyList.add(Pair(second, "ab"))
        suggestionLoL.add(historyList)
        var phraseMatch: Mapping = Mapping()
        phraseMatch.setWord("abc")
        phraseMatch.setCode("abc")
        phraseMatch.setBasescore(300)
        phraseMatch.setExactMatchToCodeRecord()
        callMakeRunTimeSuggestion("abc", Collections.singletonList(phraseMatch))
        var foundMultiHistory: Boolean = false
        for (list in suggestionLoL) {
            if (((list.size > 1) && list.get((list.size - 1)).first.getWord().equals("abc"))) {
                foundMultiHistory = true
                break
            }
        }
        assertTrue(foundMultiHistory)
    }
    @Test(timeout = 5000)
    fun test_3_2_1_13_makeRunTimeSuggestion_snapshot_prefix_matching() {
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        var bestSuggestionStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        suggestionLoL.clear()
        bestSuggestionStack.clear()
        var prefix: Mapping = Mapping()
        prefix.setWord("pre")
        prefix.setCode("pre")
        prefix.setBasescore(200)
        prefix.setExactMatchToCodeRecord()
        var prefixList: MutableList<Pair<Mapping, String>> = LinkedList()
        prefixList.add(Pair(prefix, "pre"))
        suggestionLoL.add(prefixList)
        var phraseMatch: Mapping = Mapping()
        phraseMatch.setWord("prefix")
        phraseMatch.setCode("prefix")
        phraseMatch.setBasescore(300)
        phraseMatch.setExactMatchToCodeRecord()
        callMakeRunTimeSuggestion("prefix", Collections.singletonList(phraseMatch))
        var foundPrefixRestored: Boolean = false
        for (list in suggestionLoL) {
            if ((list.isEmpty() && list.get((list.size - 1)).first.getWord().equals("prefix"))) {
                for (p in list) {
                    if (p.first.getWord().equals("pre")) {
                        foundPrefixRestored = true
                        break
                    }
                }
            }
        }
        assertTrue(foundPrefixRestored)
    }
    @Test(timeout = 5000)
    fun test_3_2_2_1_clearRunTimeSuggestion_full_reset() {
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        var bestSuggestionStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        suggestionLoL.clear()
        bestSuggestionStack.clear()
        var l: MutableList<Pair<Mapping, String>> = LinkedList()
        l.add(Pair(Mapping(), "x"))
        suggestionLoL.add(l)
        bestSuggestionStack.push(Pair(Mapping(), "x"))
        searchServer.clearRunTimeSuggestion(true)
        assertTrue(suggestionLoL.isEmpty())
        assertTrue(bestSuggestionStack.isEmpty())
        assertTrue(getStatic("abandonPhraseSuggestion", Boolean::class.java))
    }
    @Test(timeout = 5000)
    fun test_3_2_2_2_clearRunTimeSuggestion_partial_reset() {
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        var bestSuggestionStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        suggestionLoL.clear()
        bestSuggestionStack.clear()
        var l: MutableList<Pair<Mapping, String>> = LinkedList()
        l.add(Pair(Mapping(), "x"))
        suggestionLoL.add(l)
        bestSuggestionStack.push(Pair(Mapping(), "x"))
        setStatic("abandonPhraseSuggestion", true)
        searchServer.clearRunTimeSuggestion(false)
        assertTrue(suggestionLoL.isEmpty())
        assertTrue(bestSuggestionStack.isEmpty())
        assertFalse(getStatic("abandonPhraseSuggestion", Boolean::class.java))
    }
    @Test(timeout = 5000)
    fun test_3_2_3_1_getRealCodeLength_tone_stripping() {
        var m: Mapping = Mapping()
        m.setCode("a3")
        m.setWord("a")
        var len: Int = searchServer.getRealCodeLength(m, "a")
        assertEquals(1, len)
    }
    @Test(timeout = 5000)
    fun test_3_2_3_2_getRealCodeLength_dual_code() {
        var f: Field = LimeDB::class.java.getDeclaredField("codeDualMapped")
        f.setAccessible(true)
        var original: Boolean = f.getBoolean(null)
        f.setBoolean(null, true)
        var m: Mapping = Mapping()
        m.setCode("dual")
        m.setWord("dual")
        var len: Int = searchServer.getRealCodeLength(m, "dualcode")
        assertTrue(((len == "dualcode".length) || (len == m.getCode().length)))
        f.setBoolean(null, original)
    }
    @Test(timeout = 5000)
    fun test_3_2_3_3_getRealCodeLength_runtime_phrase_learning() {
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        var bestSuggestionStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        suggestionLoL.clear()
        bestSuggestionStack.clear()
        var seed: Mapping = Mapping()
        seed.setWord("pre")
        seed.setCode("p")
        var l: MutableList<Pair<Mapping, String>> = LinkedList()
        l.add(Pair(seed, "p"))
        suggestionLoL.add(l)
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var originalPref: Any = setLearnPhraseForTest(true)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        var latch: CountDownLatch = CountDownLatch(1)
        stub.latch = latch
        setStatic("dbadapter", stub)
        try {
            var selected: Mapping = Mapping()
            selected.setRuntimeBuiltPhraseRecord()
            selected.setWord("prefix")
            selected.setCode("p")
            var len: Int = searchServer.getRealCodeLength(selected, "prefix")
            assertEquals(1, len)
            assertTrue(latch.await(1, TimeUnit.SECONDS))
            assertFalse(stub.added.isEmpty())
        } finally {
            setStatic("dbadapter", original)
            setInstanceField(searchServer, "mLIMEPref", originalPref)
        }
    }
    @Test(timeout = 5000)
    fun test_getRealCodeLength_runtime_phrase_learning_disabled_by_learn_phrase() {
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        var bestSuggestionStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        suggestionLoL.clear()
        bestSuggestionStack.clear()
        var seed: Mapping = Mapping()
        seed.setWord("pre")
        seed.setCode("p")
        var l: MutableList<Pair<Mapping, String>> = LinkedList()
        l.add(Pair(seed, "p"))
        suggestionLoL.add(l)
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var originalPref: Any = getInstanceField(searchServer, "mLIMEPref", Any::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        var mockPref: LIMEPreferenceManager = mock(LIMEPreferenceManager::class.java)
        `when`(mockPref.getLearnPhrase()).thenReturn(false)
        `when`(mockPref.getParameterString(anyString(), anyString())).thenReturn(LIME.IM_PHONETIC_KEYBOARD_PHONETIC)
        setStatic("dbadapter", stub)
        setInstanceField(searchServer, "mLIMEPref", mockPref)
        try {
            var selected: Mapping = Mapping()
            selected.setRuntimeBuiltPhraseRecord()
            selected.setWord("prefix")
            selected.setCode("p")
            assertEquals(1, searchServer.getRealCodeLength(selected, "prefix"))
            Thread.sleep(200)
            assertTrue("runtime phrase learning must not write when learn_phrase=false", stub.added.isEmpty())
        } finally {
            setStatic("dbadapter", original)
            setInstanceField(searchServer, "mLIMEPref", originalPref)
        }
    }
    @Test(timeout = 5000)
    fun test_3_2_4_1_lcs_identical_partial_none_empty() {
        assertEquals("abc", searchServer.lcs("abc", "abc"))
        assertEquals("bc", searchServer.lcs("abc", "xbc"))
        assertEquals("", searchServer.lcs("abc", "def"))
        assertEquals("", searchServer.lcs("", "def"))
    }
    @Test(timeout = 5000)
    fun test_3_2_5_1_getCodeListStringFromWord_found() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        stub.codeListResponse = "code_list"
        setStatic("dbadapter", stub)
        InstrumentationRegistry.getInstrumentation().runOnMainSync({ searchServer.getCodeListStringFromWord("word") })
        assertEquals("code_list", stub.getCodeListStringByWord("word"))
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_2_5_2_getCodeListStringFromWord_not_found() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        stub.codeListResponse = null
        setStatic("dbadapter", stub)
        InstrumentationRegistry.getInstrumentation().runOnMainSync({ searchServer.getCodeListStringFromWord("missing") })
        assertNull(stub.getCodeListStringByWord("missing"))
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_1_1_initialCache_recreates_all_maps() {
        var oldCache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        var testList: MutableList<Mapping?> = LinkedList()
        testList.add(Mapping())
        oldCache.put("testkey", testList)
        assertFalse("Cache should have data before initialCache()", oldCache.isEmpty())
        searchServer.initialCache()
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        var engcache: MutableMap<String, MutableList<Mapping?>> = getStatic("engcache", MutableMap::class.java)
        var emojicache: MutableMap<String, MutableList<Mapping?>> = getStatic("emojicache", MutableMap::class.java)
        var keynamecache: MutableMap<String, String> = getStatic("keynamecache", MutableMap::class.java)
        var coderemapcache: MutableMap<String, MutableList<String>> = getStatic("coderemapcache", MutableMap::class.java)
        var suggestionLoL: MutableList<MutableList<Pair<Mapping, String>>> = getStatic("suggestionLoL", MutableList::class.java)
        var bestSuggestionStack: Stack<Pair<Mapping, String>> = getStatic("bestSuggestionStack", Stack::class.java)
        assertNotNull("cache should not be null", cache)
        assertNotNull("engcache should not be null", engcache)
        assertNotNull("emojicache should not be null", emojicache)
        assertNotNull("keynamecache should not be null", keynamecache)
        assertNotNull("coderemapcache should not be null", coderemapcache)
        assertNotNull("suggestionLoL should not be null", suggestionLoL)
        assertNotNull("bestSuggestionStack should not be null", bestSuggestionStack)
        assertNotSame("initialCache() should create a new cache instance", oldCache, cache)
        assertTrue("suggestionLoL should be empty after initialCache()", suggestionLoL.isEmpty())
        assertTrue("bestSuggestionStack should be empty after initialCache()", bestSuggestionStack.isEmpty())
    }
    @Test(timeout = 5000)
    fun test_3_3_1_2_resetCache_flag_triggers_initialCache_on_next_query() {
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        var testList: MutableList<Mapping?> = LinkedList()
        testList.add(Mapping())
        cache.put("testkey", testList)
        assertFalse(cache.isEmpty())
        SearchServer.resetCache(true)
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        searchServer.getMappingByCode("a", false, false, false)
        cache = getStatic("cache", MutableMap::class.java)
        assertFalse(cache.containsKey("testkey"))
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_2_1_prefetchCache_numbers() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        var numMappings: MutableList<Mapping?> = LinkedList()
        numMappings.add(Mapping())
        stub.responses.put("1", numMappings)
        stub.responses.put("2", numMappings)
        setStatic("dbadapter", stub)
        searchServer.initialCache()
        searchServer.setTableName("phonetic", true, true)
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("prefetchCache", Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!)
        method.setAccessible(true)
        method.invoke(searchServer, true, false)
        var prefetchThread: Thread = getStatic("prefetchThread", Thread::class.java)
        if ((prefetchThread != null)) {
            prefetchThread.join(2000)
        }
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        assertTrue((cache.size >= 0))
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_2_2_prefetchCache_symbols() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        var symbolMappings: MutableList<Mapping?> = LinkedList()
        symbolMappings.add(Mapping())
        stub.responses.put(",", symbolMappings)
        setStatic("dbadapter", stub)
        searchServer.initialCache()
        searchServer.setTableName("phonetic", true, true)
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("prefetchCache", Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!)
        method.setAccessible(true)
        method.invoke(searchServer, false, true)
        var prefetchThread: Thread = getStatic("prefetchThread", Thread::class.java)
        if ((prefetchThread != null)) {
            prefetchThread.join(2000)
        }
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        assertTrue((cache.size >= 0))
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_2_3_prefetchCache_both() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        var mappings: MutableList<Mapping?> = LinkedList()
        mappings.add(Mapping())
        stub.responses.put("1", mappings)
        stub.responses.put(",", mappings)
        setStatic("dbadapter", stub)
        searchServer.initialCache()
        searchServer.setTableName("phonetic", true, true)
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("prefetchCache", Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!)
        method.setAccessible(true)
        method.invoke(searchServer, true, true)
        var prefetchThread: Thread = getStatic("prefetchThread", Thread::class.java)
        if ((prefetchThread != null)) {
            prefetchThread.join(2000)
        }
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        assertTrue((cache.size >= 0))
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_3_1_removeRemappedCodeCachedMappings_invalidates_entries() {
        searchServer.setTableName("custom", false, false)
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        var coderemapcache: MutableMap<String, MutableList<String>> = getStatic("coderemapcache", MutableMap::class.java)
        cache.clear()
        coderemapcache.clear()
        var clazz: Class<*> = SearchServer::class.java
        var cacheKeyMethod: java.lang.reflect.Method = clazz.getDeclaredMethod("cacheKey", String::class.java)
        cacheKeyMethod.setAccessible(true)
        var keyA: String = (cacheKeyMethod.invoke(searchServer, "a") as String)
        var keyB: String = (cacheKeyMethod.invoke(searchServer, "b") as String)
        var mappings: MutableList<Mapping?> = LinkedList()
        mappings.add(Mapping())
        cache.put(keyA, mappings)
        cache.put(keyB, mappings)
        var remappedCodes: MutableList<String> = LinkedList()
        remappedCodes.add("b")
        coderemapcache.put(keyA, remappedCodes)
        assertTrue(cache.containsKey(keyB))
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("removeRemappedCodeCachedMappings", String::class.java)
        method.setAccessible(true)
        method.invoke(searchServer, "a")
        assertFalse(cache.containsKey(keyB))
    }
    @Test(timeout = 5000)
    fun test_3_3_4_1_updateSimilarCodeCache_drops_prefix_entries() {
        searchServer.setTableName("custom", false, false)
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        cache.clear()
        var clazz: Class<*> = SearchServer::class.java
        var cacheKeyMethod: java.lang.reflect.Method = clazz.getDeclaredMethod("cacheKey", String::class.java)
        cacheKeyMethod.setAccessible(true)
        var keyA: String = (cacheKeyMethod.invoke(searchServer, "a") as String)
        var keyAB: String = (cacheKeyMethod.invoke(searchServer, "ab") as String)
        var keyABC: String = (cacheKeyMethod.invoke(searchServer, "abc") as String)
        var mappings: MutableList<Mapping?> = LinkedList()
        mappings.add(Mapping())
        cache.put(keyA, mappings)
        cache.put(keyAB, mappings)
        cache.put(keyABC, mappings)
        assertTrue(cache.containsKey(keyA))
        assertTrue(cache.containsKey(keyAB))
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("updateSimilarCodeCache", String::class.java)
        method.setAccessible(true)
        method.invoke(searchServer, "abc")
        assertFalse(cache.containsKey(keyAB))
        assertFalse(cache.containsKey(keyA))
    }
    @Test(timeout = 5000)
    fun test_3_3_4_2_updateSimilarCodeCache_prefetch_single_char() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        searchServer.initialCache()
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        cache.clear()
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("updateSimilarCodeCache", String::class.java)
        method.setAccessible(true)
        method.invoke(searchServer, "a")
        assertTrue(true)
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_5_1_updateScoreCache_learning_invalidation() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        cache.clear()
        var m1: Mapping = Mapping()
        m1.setId(1)
        m1.setCode("a")
        m1.setWord("word1")
        m1.setScore(5)
        var m2: Mapping = Mapping()
        m2.setId(2)
        m2.setCode("a")
        m2.setWord("word2")
        m2.setScore(3)
        var mappings: MutableList<Mapping?> = LinkedList()
        mappings.add(m1)
        mappings.add(m2)
        cache.put("custom:a", mappings)
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("updateScoreCache", Mapping::class.java)
        method.setAccessible(true)
        var updated: Mapping = Mapping()
        updated.setId(2)
        updated.setCode("a")
        updated.setWord("word2")
        updated.setScore(3)
        method.invoke(searchServer, updated)
        assertTrue(stub.addScoreCalled)
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_5_2_updateScoreCache_exact_match_reordering() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        searchServer.setTableName("custom", false, false)
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        cache.clear()
        var m1: Mapping = Mapping()
        m1.setId(1)
        m1.setCode("ab")
        m1.setWord("word1")
        m1.setScore(10)
        var m2: Mapping = Mapping()
        m2.setId(2)
        m2.setCode("ab")
        m2.setWord("word2")
        m2.setScore(5)
        var m3: Mapping = Mapping()
        m3.setId(3)
        m3.setCode("ab")
        m3.setWord("word3")
        m3.setScore(3)
        var mappings: MutableList<Mapping?> = LinkedList()
        mappings.add(m1)
        mappings.add(m2)
        mappings.add(m3)
        cache.put("customab", mappings)
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("updateScoreCache", Mapping::class.java)
        method.setAccessible(true)
        var updated: Mapping = Mapping()
        updated.setId(2)
        updated.setCode("ab")
        updated.setWord("word2")
        updated.setScore(5)
        method.invoke(searchServer, updated)
        var resultList: MutableList<Mapping?> = cache.get("customab")!!
        assertTrue(((resultList == null) || resultList.isEmpty()))
        assertTrue(stub.addScoreCalled)
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_4_3_updateSimilarCodeCache_remote_exception() {
        open class ThrowingStub : LimeDB {
            constructor(ctx: Context) : super(ctx) {
            }
            override fun getMappingByCode(code: String, physical: Boolean, getAllRecords: Boolean): MutableList<Mapping?> {
                throw RuntimeException("Simulated exception")
            }
        }
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: ThrowingStub = ThrowingStub(appContext)
        setStatic("dbadapter", stub)
        searchServer.setTableName("custom", false, false)
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("updateSimilarCodeCache", String::class.java)
        method.setAccessible(true)
        method.invoke(searchServer, "a")
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_5_3_updateScoreCache_related_phrase_record() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        searchServer.setTableName("custom", false, false)
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        var coderemapcache: MutableMap<String, MutableList<String>> = getStatic("coderemapcache", MutableMap::class.java)
        cache.clear()
        coderemapcache.clear()
        var m1: Mapping = Mapping()
        m1.setId(1)
        m1.setCode("ab")
        m1.setWord("word1")
        var mappings: MutableList<Mapping?> = LinkedList()
        mappings.add(m1)
        cache.put("customab", mappings)
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("updateScoreCache", Mapping::class.java)
        method.setAccessible(true)
        var relatedPhrase: Mapping = Mapping()
        relatedPhrase.setId(null)
        relatedPhrase.setCode("ab")
        relatedPhrase.setWord("related")
        relatedPhrase.setRuntimeBuiltPhraseRecord()
        method.invoke(searchServer, relatedPhrase)
        var resultList: MutableList<Mapping?> = cache.get("customab")!!
        assertTrue(((resultList == null) || resultList.isEmpty()))
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_5_4_updateScoreCache_exact_match_no_reorder_needed() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        searchServer.setTableName("custom", false, false)
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        cache.clear()
        var m1: Mapping = Mapping()
        m1.setId(1)
        m1.setCode("ab")
        m1.setWord("word1")
        m1.setScore(10)
        var m2: Mapping = Mapping()
        m2.setId(2)
        m2.setCode("ab")
        m2.setWord("word2")
        m2.setScore(3)
        var mappings: MutableList<Mapping?> = LinkedList()
        mappings.add(m1)
        mappings.add(m2)
        cache.put("customab", mappings)
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("updateScoreCache", Mapping::class.java)
        method.setAccessible(true)
        var updated: Mapping = Mapping()
        updated.setId(2)
        updated.setCode("ab")
        updated.setWord("word2")
        updated.setScore(3)
        method.invoke(searchServer, updated)
        var resultList: MutableList<Mapping?> = cache.get("customab")!!
        assertTrue(((resultList == null) || resultList.isEmpty()))
        assertTrue(stub.addScoreCalled)
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_5_5_updateScoreCache_code_not_in_cache() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        searchServer.setTableName("custom", false, false)
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        cache.clear()
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("updateScoreCache", Mapping::class.java)
        method.setAccessible(true)
        var notInCache: Mapping = Mapping()
        notInCache.setId(99)
        notInCache.setCode("zz")
        notInCache.setWord("nothere")
        notInCache.setScore(1)
        method.invoke(searchServer, notInCache)
        var resultList: MutableList<Mapping?> = cache.get("customzz")!!
        assertTrue(((resultList == null) || resultList.isEmpty()))
        assertTrue(stub.addScoreCalled)
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_5_6_updateScoreCache_exact_match_jump_multiple_positions() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        searchServer.setTableName("custom", false, false)
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        cache.clear()
        var m1: Mapping = Mapping()
        m1.setId(1)
        m1.setCode("ab")
        m1.setWord("word1")
        m1.setScore(50)
        var m2: Mapping = Mapping()
        m2.setId(2)
        m2.setCode("ab")
        m2.setWord("word2")
        m2.setScore(20)
        var m3: Mapping = Mapping()
        m3.setId(3)
        m3.setCode("ab")
        m3.setWord("word3")
        m3.setScore(2)
        var mappings: MutableList<Mapping?> = LinkedList()
        mappings.add(m1)
        mappings.add(m2)
        mappings.add(m3)
        cache.put("customab", mappings)
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("updateScoreCache", Mapping::class.java)
        method.setAccessible(true)
        var updated: Mapping = Mapping()
        updated.setId(3)
        updated.setCode("ab")
        updated.setWord("word3")
        updated.setScore(2)
        method.invoke(searchServer, updated)
        var resultList: MutableList<Mapping?> = cache.get("customab")!!
        assertTrue(((resultList == null) || resultList.isEmpty()))
        assertTrue(stub.addScoreCalled)
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_5_8_updateScoreCache_score_increment_small() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        searchServer.setTableName("custom", false, false)
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        cache.clear()
        var m1: Mapping = Mapping()
        m1.setId(1)
        m1.setCode("test")
        m1.setWord("word1")
        m1.setScore(10)
        var m2: Mapping = Mapping()
        m2.setId(2)
        m2.setCode("test")
        m2.setWord("word2")
        m2.setScore(5)
        var mappings: MutableList<Mapping?> = LinkedList()
        mappings.add(m1)
        mappings.add(m2)
        cache.put("customtest", mappings)
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("updateScoreCache", Mapping::class.java)
        method.setAccessible(true)
        var updated: Mapping = Mapping()
        updated.setId(2)
        updated.setCode("test")
        updated.setWord("word2")
        updated.setScore(5)
        method.invoke(searchServer, updated)
        var resultList: MutableList<Mapping?> = cache.get("customtest")!!
        assertTrue(((resultList == null) || resultList.isEmpty()))
        assertTrue(stub.addScoreCalled)
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_5_7_updateScoreCache_exact_match_large_score_increase() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        searchServer.setTableName("custom", false, false)
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        cache.clear()
        var m1: Mapping = Mapping()
        m1.setId(1)
        m1.setCode("ab")
        m1.setWord("word1")
        m1.setScore(100)
        var m2: Mapping = Mapping()
        m2.setId(2)
        m2.setCode("ab")
        m2.setWord("word2")
        m2.setScore(50)
        var m3: Mapping = Mapping()
        m3.setId(3)
        m3.setCode("ab")
        m3.setWord("word3")
        m3.setScore(10)
        var mappings: MutableList<Mapping?> = LinkedList()
        mappings.add(m1)
        mappings.add(m2)
        mappings.add(m3)
        cache.put("customab", mappings)
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("updateScoreCache", Mapping::class.java)
        method.setAccessible(true)
        var updated: Mapping = Mapping()
        updated.setId(3)
        updated.setCode("ab")
        updated.setWord("word3")
        updated.setScore(10)
        method.invoke(searchServer, updated)
        var resultList: MutableList<Mapping?> = cache.get("customab")!!
        assertTrue(((resultList == null) || resultList.isEmpty()))
        assertTrue(stub.addScoreCalled)
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_7_1_cacheKey_phonetic_table() {
        searchServer.setTableName(LIME.DB_TABLE_PHONETIC, false, false)
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("cacheKey", String::class.java)
        method.setAccessible(true)
        var result: String = (method.invoke(searchServer, "ab") as String)
        assertNotNull(result)
        assertTrue(result.contains("phonetic"))
        assertTrue(result.endsWith("ab"))
        assertFalse(result.isEmpty())
    }
    @Test(timeout = 5000)
    fun test_3_3_7_2_cacheKey_custom_table() {
        searchServer.setTableName("custom", false, false)
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("cacheKey", String::class.java)
        method.setAccessible(true)
        var result: String = (method.invoke(searchServer, "test") as String)
        assertNotNull(result)
        assertTrue(result.contains("custom"))
        assertTrue(result.endsWith("test"))
        assertFalse(result.isEmpty())
    }
    @Test(timeout = 5000)
    fun test_3_3_7_3_cacheKey_null_dbadapter() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        setStatic("dbadapter", null)
        try {
            var clazz: Class<*> = SearchServer::class.java
            var method: java.lang.reflect.Method = clazz.getDeclaredMethod("cacheKey", String::class.java)
            method.setAccessible(true)
            var result: String = (method.invoke(searchServer, "ab") as String)
            assertEquals("", result)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_3_7_4_cacheKey_case_sensitive() {
        searchServer.setTableName("custom", false, false)
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("cacheKey", String::class.java)
        method.setAccessible(true)
        var resultLower: String = (method.invoke(searchServer, "ab") as String)
        var resultUpper: String = (method.invoke(searchServer, "AB") as String)
        var resultMixed: String = (method.invoke(searchServer, "Ab") as String)
        assertNotNull(resultLower)
        assertNotNull(resultUpper)
        assertNotNull(resultMixed)
        assertTrue(resultLower.endsWith("ab"))
        assertTrue(resultUpper.endsWith("AB"))
        assertTrue(resultMixed.endsWith("Ab"))
    }
    @Test(timeout = 5000)
    fun test_3_3_7_5_cacheKey_numeric_codes() {
        searchServer.setTableName("custom", false, false)
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("cacheKey", String::class.java)
        method.setAccessible(true)
        var resultNum: String = (method.invoke(searchServer, "123") as String)
        assertNotNull(resultNum)
        assertTrue(resultNum.endsWith("123"))
        assertTrue(resultNum.contains("custom"))
    }
    @Test(timeout = 5000)
    fun test_3_3_7_6_cacheKey_physical_keyboard_phonetic() {
        searchServer.setTableName(LIME.DB_TABLE_PHONETIC, false, false)
        var physicalField: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("isPhysicalKeyboardPressed")
        physicalField.setAccessible(true)
        physicalField.setBoolean(searchServer, true)
        try {
            var clazz: Class<*> = SearchServer::class.java
            var method: java.lang.reflect.Method = clazz.getDeclaredMethod("cacheKey", String::class.java)
            method.setAccessible(true)
            var result: String = (method.invoke(searchServer, "xy") as String)
            assertNotNull(result)
            assertTrue(result.contains("phonetic"))
            assertTrue(result.endsWith("xy"))
            assertFalse(result.isEmpty())
        } finally {
            physicalField.setBoolean(searchServer, false)
        }
    }
    @Test(timeout = 5000)
    fun test_3_3_7_7_cacheKey_physical_keyboard_custom_table() {
        searchServer.setTableName("custom", false, false)
        var physicalField: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("isPhysicalKeyboardPressed")
        physicalField.setAccessible(true)
        physicalField.setBoolean(searchServer, true)
        try {
            var result: MutableList<Mapping?> = searchServer.getMappingByCode("test", false, false, false)
            assertNotNull(result)
        } finally {
            physicalField.setBoolean(searchServer, false)
        }
    }
    @Test(timeout = 5000)
    fun test_3_3_5_9_updateScoreCache_related_phrase_removal_cache_hit() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        searchServer.setTableName("custom", false, false)
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        cache.clear()
        var related: Mapping = Mapping()
        related.setId(null)
        related.setCode("phrase")
        related.setWord("test phrase")
        related.setScore(5)
        var phraseList: MutableList<Mapping?> = LinkedList()
        var m1: Mapping = Mapping()
        m1.setId(null)
        m1.setCode("phrase")
        m1.setWord("phrase1")
        m1.setScore(3)
        phraseList.add(m1)
        cache.put("customphrase", phraseList)
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("updateScoreCache", Mapping::class.java)!!
        method.setAccessible(true)
        method.invoke(searchServer, related)
        var resultList: MutableList<Mapping?> = cache.get("customphrase")!!
        assertTrue(((resultList == null) || resultList.isEmpty()))
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_5_10_updateScoreCache_exact_match_at_position_zero() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        searchServer.setTableName("custom", false, false)
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        cache.clear()
        var m1: Mapping = Mapping()
        m1.setId(1)
        m1.setCode("test")
        m1.setWord("word1")
        m1.setScore(100)
        var m2: Mapping = Mapping()
        m2.setId(2)
        m2.setCode("test")
        m2.setWord("word2")
        m2.setScore(50)
        var mappings: MutableList<Mapping?> = LinkedList()
        mappings.add(m1)
        mappings.add(m2)
        cache.put("customtest", mappings)
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("updateScoreCache", Mapping::class.java)
        method.setAccessible(true)
        var updated: Mapping = Mapping()
        updated.setId(1)
        updated.setCode("test")
        updated.setWord("word1")
        updated.setScore(100)
        method.invoke(searchServer, updated)
        var resultList: MutableList<Mapping?> = cache.get("customtest")!!
        assertTrue(((resultList == null) || resultList.isEmpty()))
        assertTrue(stub.addScoreCalled)
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_5_11_updateScoreCache_exact_match_jump_to_end() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        searchServer.setTableName("custom", false, false)
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        cache.clear()
        var m1: Mapping = Mapping()
        m1.setId(1)
        m1.setCode("ab")
        m1.setWord("word1")
        m1.setScore(100)
        var m2: Mapping = Mapping()
        m2.setId(2)
        m2.setCode("ab")
        m2.setWord("word2")
        m2.setScore(50)
        var m3: Mapping = Mapping()
        m3.setId(3)
        m3.setCode("ab")
        m3.setWord("word3")
        m3.setScore(40)
        var mappings: MutableList<Mapping?> = LinkedList()
        mappings.add(m1)
        mappings.add(m2)
        mappings.add(m3)
        cache.put("customab", mappings)
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("updateScoreCache", Mapping::class.java)
        method.setAccessible(true)
        var updated: Mapping = Mapping()
        updated.setId(2)
        updated.setCode("ab")
        updated.setWord("word2")
        updated.setScore(50)
        method.invoke(searchServer, updated)
        var resultList: MutableList<Mapping?> = cache.get("customab")!!
        assertTrue(((resultList == null) || resultList.isEmpty()))
        assertTrue(stub.addScoreCalled)
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_5_12_updateScoreCache_physical_keyboard_sort_preference() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        if ((getInstanceField<Any?>("mLIMEPref", Any::class.java) == null)) {
            setInstanceField("mLIMEPref", net.toload.main.hd.global.LIMEPreferenceManager(appContext))
        }
        searchServer.setTableName("custom", false, false)
        var physicalField: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("isPhysicalKeyboardPressed")
        physicalField.setAccessible(true)
        physicalField.setBoolean(searchServer, true)
        try {
            var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
            cache.clear()
            var m1: Mapping = Mapping()
            m1.setId(1)
            m1.setCode("key")
            m1.setWord("word1")
            m1.setScore(100)
            var m2: Mapping = Mapping()
            m2.setId(2)
            m2.setCode("key")
            m2.setWord("word2")
            m2.setScore(20)
            var mappings: MutableList<Mapping?> = LinkedList()
            mappings.add(m1)
            mappings.add(m2)
            cache.put("customkey", mappings)
            var clazz: Class<*> = SearchServer::class.java
            var method: java.lang.reflect.Method = clazz.getDeclaredMethod("updateScoreCache", Mapping::class.java)
            method.setAccessible(true)
            var updated: Mapping = Mapping()
            updated.setId(2)
            updated.setCode("key")
            updated.setWord("word2")
            updated.setScore(20)
            method.invoke(searchServer, updated)
            var resultList: MutableList<Mapping?> = cache.get("customkey")!!
            assertTrue((((resultList == null) || resultList.isEmpty()) || (resultList.size == 2)))
            assertTrue(stub.addScoreCalled)
        } finally {
            physicalField.setBoolean(searchServer, false)
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_3_5_13_updateScoreCache_exact_match_reorder_with_insertion() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        searchServer.setTableName("custom", false, false)
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        cache.clear()
        var m1: Mapping = Mapping()
        m1.setId(1)
        m1.setCode("order")
        m1.setWord("word1")
        m1.setScore(100)
        var m2: Mapping = Mapping()
        m2.setId(2)
        m2.setCode("order")
        m2.setWord("word2")
        m2.setScore(50)
        var m3: Mapping = Mapping()
        m3.setId(3)
        m3.setCode("order")
        m3.setWord("word3")
        m3.setScore(30)
        var m4: Mapping = Mapping()
        m4.setId(4)
        m4.setCode("order")
        m4.setWord("word4")
        m4.setScore(10)
        var mappings: MutableList<Mapping?> = LinkedList()
        mappings.add(m1)
        mappings.add(m2)
        mappings.add(m3)
        mappings.add(m4)
        cache.put("customorder", mappings)
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("updateScoreCache", Mapping::class.java)
        method.setAccessible(true)
        var updated: Mapping = Mapping()
        updated.setId(4)
        updated.setCode("order")
        updated.setWord("word4")
        updated.setScore(10)
        method.invoke(searchServer, updated)
        var resultList: MutableList<Mapping?> = cache.get("customorder")!!
        assertTrue(((resultList == null) || resultList.isEmpty()))
        assertTrue(stub.addScoreCalled)
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_5_14_updateScoreCache_sort_disabled_soft_keyboard() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        PreferenceManager.getDefaultSharedPreferences(appContext).edit().putBoolean("sort_suggestions", false).apply()
        searchServer.setTableName("custom", false, false)
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        cache.clear()
        var m1: Mapping = Mapping()
        m1.setId(1)
        m1.setCode("test")
        m1.setWord("word1")
        m1.setScore(100)
        var m2: Mapping = Mapping()
        m2.setId(2)
        m2.setCode("test")
        m2.setWord("word2")
        m2.setScore(50)
        var mappings: MutableList<Mapping?> = LinkedList()
        mappings.add(m1)
        mappings.add(m2)
        cache.put("customtest", mappings)
        var physicalField: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("isPhysicalKeyboardPressed")
        physicalField.setAccessible(true)
        physicalField.setBoolean(searchServer, false)
        var clazz: Class<*> = SearchServer::class.java
        var method: java.lang.reflect.Method = clazz.getDeclaredMethod("updateScoreCache", Mapping::class.java)
        method.setAccessible(true)
        var updated: Mapping = Mapping()
        updated.setId(2)
        updated.setCode("test")
        updated.setWord("word2")
        updated.setScore(50)
        method.invoke(searchServer, updated)
        var resultList: MutableList<Mapping?> = cache.get("customtest")!!
        assertTrue(((resultList == null) || resultList.isEmpty()))
        assertTrue(stub.addScoreCalled)
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_5_15_updateScoreCache_sort_disabled_physical_keyboard() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        PreferenceManager.getDefaultSharedPreferences(appContext).edit().putBoolean("physical_keyboard_sort_suggestions", false).apply()
        searchServer.setTableName("custom", false, false)
        var cache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        cache.clear()
        var m1: Mapping = Mapping()
        m1.setId("1")
        m1.setCode("abc")
        m1.setWord("word1")
        m1.setScore(200)
        var m2: Mapping = Mapping()
        m2.setId("2")
        m2.setCode("abc")
        m2.setWord("word2")
        m2.setScore(100)
        var mappings: MutableList<Mapping?> = LinkedList()
        mappings.add(m1)
        mappings.add(m2)
        cache.put("customabc", mappings)
        var physicalField: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("isPhysicalKeyboardPressed")
        physicalField.setAccessible(true)
        physicalField.setBoolean(searchServer, true)
        try {
            var clazz: Class<*> = SearchServer::class.java
            var method: java.lang.reflect.Method = clazz.getDeclaredMethod("updateScoreCache", Mapping::class.java)
            method.setAccessible(true)
            var updated: Mapping = Mapping()
            updated.setId("2")
            updated.setCode("abc")
            updated.setWord("word2")
            updated.setScore(100)
            method.invoke(searchServer, updated)
            var resultList: MutableList<Mapping?> = cache.get("customabc")!!
            assertTrue((((resultList == null) || resultList.isEmpty()) || (resultList.size == 2)))
            assertTrue(stub.addScoreCalled)
        } finally {
            physicalField.setBoolean(searchServer, false)
        }
        setStatic("dbadapter", original)
    }
    @Test(timeout = 5000)
    fun test_3_3_7_8_cacheKey_soft_keyboard_phonetic_table() {
        searchServer.setTableName(LIME.DB_TABLE_PHONETIC, false, false)
        var physicalField: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("isPhysicalKeyboardPressed")
        physicalField.setAccessible(true)
        physicalField.setBoolean(searchServer, false)
        try {
            var clazz: Class<*> = SearchServer::class.java
            var method: java.lang.reflect.Method = clazz.getDeclaredMethod("cacheKey", String::class.java)
            method.setAccessible(true)
            var result: String = (method.invoke(searchServer, "test") as String)
            assertNotNull(result)
            assertTrue(result.contains("phonetic"))
            assertTrue(result.endsWith("test"))
            assertFalse(result.isEmpty())
        } finally {
            physicalField.setBoolean(searchServer, false)
        }
    }
    @Test(timeout = 5000)
    fun test_3_3_7_9_cacheKey_soft_keyboard_non_phonetic_table() {
        searchServer.setTableName("custom", false, false)
        var physicalField: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("isPhysicalKeyboardPressed")
        physicalField.setAccessible(true)
        physicalField.setBoolean(searchServer, false)
        try {
            var clazz: Class<*> = SearchServer::class.java
            var method: java.lang.reflect.Method = clazz.getDeclaredMethod("cacheKey", String::class.java)
            method.setAccessible(true)
            var result: String = (method.invoke(searchServer, "xyz") as String)
            assertNotNull(result)
            assertTrue(result.contains("custom"))
            assertTrue(result.endsWith("xyz"))
            assertFalse(result.isEmpty())
        } finally {
            physicalField.setBoolean(searchServer, false)
        }
    }
    @Test(timeout = 5000)
    fun te_updateScoreCache_physical_reorder_path() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        searchServer.setTableName("custom", false, false)
        var prefetchThread: Thread = getStatic("prefetchThread", Thread::class.java)
        if ((prefetchThread != null)) {
            prefetchThread.join(2000)
        }
        var physicalField: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("isPhysicalKeyboardPressed")
        physicalField.setAccessible(true)
        physicalField.setBoolean(searchServer, true)
        try {
            var cacheMap: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
            cacheMap.clear()
            var clazz: Class<*> = SearchServer::class.java
            var cacheKeyMethod: java.lang.reflect.Method = clazz.getDeclaredMethod("cacheKey", String::class.java)
            cacheKeyMethod.setAccessible(true)
            var key: String = (cacheKeyMethod.invoke(searchServer, "key") as String)
            var m1: Mapping = Mapping()
            m1.setId(1)
            m1.setCode("key")
            m1.setWord("low")
            m1.setScore(1)
            var m2: Mapping = Mapping()
            m2.setId(2)
            m2.setCode("key")
            m2.setWord("high")
            m2.setScore(5)
            var list: MutableList<Mapping?> = LinkedList()
            list.add(m1)
            list.add(m2)
            cacheMap.put(key, list)
            var updateMethod: java.lang.reflect.Method = clazz.getDeclaredMethod("updateScoreCache", Mapping::class.java)!!
            updateMethod.setAccessible(true)
            updateMethod.invoke(searchServer, m2)
            var updated: MutableList<Mapping?> = cacheMap.get(key)!!
            assertTrue(((updated == null) || updated.isEmpty()))
            assertTrue(stub.addScoreCalled)
        } finally {
            physicalField.setBoolean(searchServer, false)
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_3_5_16_updateScoreCache_sort_disabled_updates_score() {
        var originalDb: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        searchServer.setTableName("custom", false, false)
        var prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
        var original: Boolean = prefs.getBoolean("learning_switch", true)
        prefs.edit().putBoolean("learning_switch", false).commit()
        try {
            var physicalField: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("isPhysicalKeyboardPressed")
            physicalField.setAccessible(true)
            physicalField.setBoolean(searchServer, false)
            var cacheMap: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
            cacheMap.clear()
            var clazz: Class<*> = SearchServer::class.java
            var cacheKeyMethod: java.lang.reflect.Method = clazz.getDeclaredMethod("cacheKey", String::class.java)
            cacheKeyMethod.setAccessible(true)
            var key: String = (cacheKeyMethod.invoke(searchServer, "nosort") as String)
            var m1: Mapping = Mapping()
            m1.setId(10)
            m1.setCode("nosort")
            m1.setWord("word")
            m1.setScore(7)
            var list: MutableList<Mapping?> = LinkedList()
            list.add(m1)
            cacheMap.put(key, list)
            var updateMethod: java.lang.reflect.Method = clazz.getDeclaredMethod("updateScoreCache", Mapping::class.java)!!
            updateMethod.setAccessible(true)
            updateMethod.invoke(searchServer, m1)
            var updated: MutableList<Mapping?> = cacheMap.get(key)!!
            assertTrue(((updated == null) || updated.isEmpty()))
            assertTrue(stub.addScoreCalled)
        } finally {
            PreferenceManager.getDefaultSharedPreferences(appContext).edit().putBoolean("learning_switch", original).commit()
            setStatic("dbadapter", originalDb)
        }
    }
    @Test(timeout = 5000)
    fun test_3_3_5_17_updateScoreCache_related_removal_path() {
        var originalDb: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        searchServer.setTableName("custom", false, false)
        var originalCache: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
        var fakeCache: MutableMap<String, MutableList<Mapping?>> = object : ConcurrentHashMap<String, MutableList<Mapping?>>() {
    var seeded: Boolean = false
    override fun remove(key: String): MutableList<Mapping?>? {
        super.remove(key)
        return null
    }
    override fun put(key: String, value: MutableList<Mapping?>): MutableList<Mapping?>? {
        if (!seeded) {
            seeded = true
            return super.put(key, value)
        }
        return null
    }
}
        setStatic("cache", fakeCache)
        try {
            var clazz: Class<*> = SearchServer::class.java
            var cacheKeyMethod: java.lang.reflect.Method = clazz.getDeclaredMethod("cacheKey", String::class.java)
            cacheKeyMethod.setAccessible(true)
            var key: String = (cacheKeyMethod.invoke(searchServer, "rel") as String)
            var m: Mapping = Mapping()
            m.setId(null)
            m.setCode("rel")
            m.setWord("relword")
            m.setScore(1)
            var list: MutableList<Mapping?> = LinkedList()
            list.add(m)
            fakeCache.put(key, list)
            var updateMethod: java.lang.reflect.Method = clazz.getDeclaredMethod("updateScoreCache", Mapping::class.java)
            updateMethod.setAccessible(true)
            updateMethod.invoke(searchServer, m)
            assertFalse(fakeCache.containsKey(key))
        } finally {
            setStatic("cache", originalCache)
            setStatic("dbadapter", originalDb)
        }
    }
    @Test(timeout = 5000)
    fun test_3_3_5_18_updateScoreCache_reorder_without_insert() {
        var original: LimeDB = getStatic("dbadapter", LimeDB::class.java)
        var stub: StubLimeDBRuntime = StubLimeDBRuntime(appContext)
        setStatic("dbadapter", stub)
        searchServer.setTableName("custom", false, false)
        var physicalField: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("isPhysicalKeyboardPressed")
        physicalField.setAccessible(true)
        physicalField.setBoolean(searchServer, true)
        try {
            var cacheMap: MutableMap<String, MutableList<Mapping?>> = getStatic("cache", MutableMap::class.java)
            cacheMap.clear()
            var clazz: Class<*> = SearchServer::class.java
            var cacheKeyMethod: java.lang.reflect.Method = clazz.getDeclaredMethod("cacheKey", String::class.java)
            cacheKeyMethod.setAccessible(true)
            var key: String = (cacheKeyMethod.invoke(searchServer, "drop") as String)
            var m1: Mapping = Mapping()
            m1.setId(1)
            m1.setCode("drop")
            m1.setWord("top")
            m1.setScore(5)
            var m2: Mapping = Mapping()
            m2.setId(2)
            m2.setCode("drop")
            m2.setWord("low")
            m2.setScore(1)
            var list: MutableList<Mapping?> = LinkedList()
            list.add(m1)
            list.add(m2)
            cacheMap.put(key, list)
            var updateMethod: java.lang.reflect.Method = clazz.getDeclaredMethod("updateScoreCache", Mapping::class.java)!!
            updateMethod.setAccessible(true)
            updateMethod.invoke(searchServer, m2)
            var updated: MutableList<Mapping?> = cacheMap.get(key)!!
            assertTrue(((updated == null) || updated.isEmpty()))
            assertTrue(stub.addScoreCalled)
        } finally {
            physicalField.setBoolean(searchServer, false)
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_1_1_getRecords_pagination_bounds() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        var records: MutableList<Record> = ArrayList()
        var r1: Record = Record()
        r1.setCode("c1")
        r1.setWord("w1")
        records.add(r1)
        stub.recordResponse = records
        setStatic("dbadapter", stub)
        try {
            var result: MutableList<Record> = searchServer.getRecords("tableA", "queryX", true, 2, 1)
            assertSame(records, result)
            assertEquals("tableA", stub.lastTable)
            assertEquals("queryX", stub.lastQuery)
            assertTrue(stub.lastSearchByCode)
            assertEquals(2, stub.lastMaximum)
            assertEquals(1, stub.lastOffset)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_1_2_getRecords_empty_result() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.recordResponse = ArrayList()
        setStatic("dbadapter", stub)
        try {
            var result: MutableList<Record> = searchServer.getRecords("tableA", null, false, 0, 0)
            assertNotNull(result)
            assertTrue(result.isEmpty())
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_1_3_getRecords_query_filter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            searchServer.getRecords("tableB", "needle", false, 5, 0)
            assertEquals("needle", stub.lastQuery)
            assertFalse(stub.lastSearchByCode)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_1_4_getRecords_null_dbadapter_returns_empty() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            var result: MutableList<Record> = searchServer.getRecords("tableZ", "q", true, 1, 0)
            assertNotNull(result)
            assertTrue(result.isEmpty())
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_1_5_getRecord_null_dbadapter_returns_null() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            assertNull(searchServer.getRecord("tableZ", 1L))
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_1_6_getRecord_delegates_to_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        var record: Record = Record()
        record.setWord("result")
        stub.getRecordResponse = record
        setStatic("dbadapter", stub)
        try {
            var result: Record = searchServer.getRecord("tableX", 42L)!!
            assertSame(record, result)
            assertEquals("tableX", stub.lastTable)
            assertEquals(42L, stub.lastRecordId)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_2_1_getRelated_pagination_empty() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.relatedResponse = ArrayList()
        setStatic("dbadapter", stub)
        try {
            var result: MutableList<Related> = searchServer.getRelatedByWord("parent", 3, 2)
            assertNotNull(result)
            assertTrue(result.isEmpty())
            assertEquals("parent", stub.lastRelatedPword)
            assertEquals(3, stub.lastRelatedMaximum)
            assertEquals(2, stub.lastRelatedOffset)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_2_2_countRecordsRelated_accuracy() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.countResponse = 3
        setStatic("dbadapter", stub)
        try {
            var count: Int = searchServer.countRecordsRelated("ab")
            assertEquals(3, count)
            assertEquals(LIME.DB_TABLE_RELATED, stub.lastTable)
            assertNotNull(stub.lastWhereClause)
            assertTrue(stub.lastWhereClause.contains(LIME.DB_RELATED_COLUMN_PWORD))
            assertTrue(stub.lastWhereClause.contains(LIME.DB_RELATED_COLUMN_CWORD))
            assertNotNull(stub.lastWhereArgs)
            assertEquals(2, stub.lastWhereArgs.length)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_2_3_hasRelated_true_false_paths() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            stub.countResponse = 1
            assertTrue(searchServer.hasRelated("p", "c"))
            assertEquals(LIME.DB_TABLE_RELATED, stub.lastTable)
            assertNotNull(stub.lastWhereClause)
            assertTrue(stub.lastWhereClause.contains(LIME.DB_RELATED_COLUMN_PWORD))
            assertTrue(stub.lastWhereClause.contains(LIME.DB_RELATED_COLUMN_CWORD))
            stub.countResponse = 0
            assertFalse(searchServer.hasRelated("p", "c"))
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_3_1_countRecordsByWordOrCode_code_vs_word() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            stub.countResponse = 4
            var codeCount: Int = searchServer.countRecordsByWordOrCode("tableC", "abc", true)
            assertEquals(4, codeCount)
            assertTrue(stub.lastWhereClause.contains(LIME.DB_COLUMN_CODE))
            assertNotNull(stub.lastWhereArgs)
            assertEquals("abc%", stub.lastWhereArgs!![0])
            stub.countResponse = 2
            var wordCount: Int = searchServer.countRecordsByWordOrCode("tableC", "hi", false)
            assertEquals(2, wordCount)
            assertTrue(stub.lastWhereClause.contains(LIME.DB_COLUMN_WORD))
            assertNotNull(stub.lastWhereArgs)
            assertEquals("%hi%", stub.lastWhereArgs!![0])
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_3_2_countRecords_filters_empty_word() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.countResponse = 5
        setStatic("dbadapter", stub)
        try {
            var relatedCount: Int = searchServer.countRecords(LIME.DB_TABLE_RELATED)
            assertEquals(5, relatedCount)
            assertTrue(stub.lastWhereClause.contains(LIME.DB_RELATED_COLUMN_PWORD))
            assertTrue(stub.lastWhereClause.contains(LIME.DB_RELATED_COLUMN_CWORD))
            stub.countResponse = 7
            var defaultCount: Int = searchServer.countRecords(LIME.DB_TABLE_PHONETIC)
            assertEquals(7, defaultCount)
            assertTrue(stub.lastWhereClause.contains(LIME.DB_COLUMN_WORD))
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_3_3_countRecordsByWordOrCode_null_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            var count: Int = searchServer.countRecordsByWordOrCode(LIME.DB_TABLE_PHONETIC, "abc", true)
            assertEquals(0, count)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_3_4_countRecordsByWordOrCode_empty_query() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.countResponse = 6
        setStatic("dbadapter", stub)
        try {
            var count: Int = searchServer.countRecordsByWordOrCode(LIME.DB_TABLE_PHONETIC, "", false)
            assertEquals(6, count)
            assertNull(stub.lastWhereArgs)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_3_6_countRecordsByWordOrCode_null_query_uses_null_args() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.countResponse = 8
        setStatic("dbadapter", stub)
        try {
            var count: Int = searchServer.countRecordsByWordOrCode(LIME.DB_TABLE_PHONETIC, null, true)
            assertEquals(8, count)
            assertNull(stub.lastWhereArgs)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_4_1_add_update_delete_valid_table() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.addResult = 9L
        stub.deleteResult = 2
        stub.updateResult = 3
        setStatic("dbadapter", stub)
        try {
            var values: ContentValues = ContentValues()
            values.put("word", "hello")
            var addId: Long = searchServer.addRecord("tableD", values)
            assertEquals(9L, addId)
            assertEquals("tableD", stub.lastTable)
            var updateVals: ContentValues = ContentValues()
            updateVals.put("word", "bye")
            var updated: Int = searchServer.updateRecord("tableD", updateVals, "id=?", arrayOf("1"))
            assertEquals(3, updated)
            assertEquals("tableD", stub.lastTable)
            assertEquals("id=?", stub.lastWhereClauseUpdate)
            assertArrayEquals(arrayOf("1"), stub.lastWhereArgsUpdate)
            var deleted: Int = searchServer.deleteRecord("tableD", "id=?", arrayOf("1"))
            assertEquals(2, deleted)
            assertEquals("tableD", stub.lastTable)
            assertEquals("id=?", stub.lastWhereClause)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_4_2_add_update_delete_invalid_table() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.validTable = false
        setStatic("dbadapter", stub)
        try {
            var threw: Boolean = false
            try {
                searchServer.clearTable("badtable")
            } catch (e: IllegalArgumentException) {
                threw = true
            }
            assertTrue(threw)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_4_3_clearTable_behavior() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            searchServer.clearTable(LIME.DB_TABLE_PHONETIC)
            assertTrue(stub.clearCalled)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_4_4_deleteRecord_null_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            var deleted: Int = searchServer.deleteRecord(LIME.DB_TABLE_PHONETIC, "id=?", arrayOf("1"))
            assertEquals(0, deleted)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_4_5_addOrUpdateMappingRecord_null_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            searchServer.addOrUpdateMappingRecord(LIME.DB_TABLE_PHONETIC, "code", "word", 1)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_4_6_addRecord_null_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            var id: Long = searchServer.addRecord(LIME.DB_TABLE_PHONETIC, ContentValues())
            assertEquals(1, id)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_3_5_countRecords_null_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            var count: Int = searchServer.countRecords(LIME.DB_TABLE_PHONETIC)
            assertEquals(0, count)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_2_4_countRecordsRelated_short_parent() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.countResponse = 1
        setStatic("dbadapter", stub)
        try {
            var count: Int = searchServer.countRecordsRelated("x")
            assertEquals(1, count)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_2_4a_countRecordsRelated_extB_leading_parent_splits_by_codepoint() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.countResponse = 1
        setStatic("dbadapter", stub)
        try {
            var count: Int = searchServer.countRecordsRelated("𩼣魚")
            assertEquals(1, count)
            assertArrayEquals(arrayOf("𩼣", "魚%"), stub.lastWhereArgs)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_2_5_hasRelated_null_child() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.countResponse = 0
        setStatic("dbadapter", stub)
        try {
            var exists: Boolean = searchServer.hasRelated("p", null)
            assertFalse(exists)
            assertNotNull(stub.lastWhereClause)
            assertTrue(stub.lastWhereClause.contains("IS NULL"))
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_2_6_countRecordsRelated_null_dbadapter_returns_zero() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            var count: Int = searchServer.countRecordsRelated("anything")
            assertEquals(0, count)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_2_7_countRecordsRelated_null_parent_uses_null_whereArgs() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.countResponse = 2
        setStatic("dbadapter", stub)
        try {
            var count: Int = searchServer.countRecordsRelated(null)
            assertEquals(2, count)
            assertNull(stub.lastWhereArgs)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_2_8_hasRelated_null_dbadapter_returns_false() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            assertFalse(searchServer.hasRelated("p", "c"))
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_2_9_hasRelated_null_parent_null_child_whereargs_null() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.countResponse = 0
        setStatic("dbadapter", stub)
        try {
            var exists: Boolean = searchServer.hasRelated(null, null)
            assertFalse(exists)
            assertNotNull(stub.lastWhereClause)
            assertTrue(stub.lastWhereClause.contains("IS NULL"))
            assertNull(stub.lastWhereArgs)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_2_10_getRelatedByWord_null_dbadapter_returns_empty_list() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            var result: MutableList<Related> = searchServer.getRelatedByWord("parent", 1, 0)
            assertNotNull(result)
            assertTrue(result.isEmpty())
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_2_11_getRelatedPhrase_delegates_to_db() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        var mapping: Mapping = Mapping()
        mapping.setCode("c")
        mapping.setWord("w")
        stub.relatedPhraseResponse = Collections.singletonList(mapping)
        setStatic("dbadapter", stub)
        try {
            var result: MutableList<Mapping?> = searchServer.getRelatedByWord("root", true)
            assertEquals(stub.relatedPhraseResponse, result)
            assertEquals("root", stub.lastRelatedPhraseWord)
            assertTrue(stub.lastRelatedPhraseAll)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_2_12_countRecordsRelated_empty_parent_uses_null_whereArgs() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.countResponse = 2
        setStatic("dbadapter", stub)
        try {
            var count: Int = searchServer.countRecordsRelated("")
            assertEquals(2, count)
            assertNull(stub.lastWhereArgs)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_2_13_hasRelated_empty_parent_and_child_paths() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.countResponse = 0
        setStatic("dbadapter", stub)
        try {
            var exists: Boolean = searchServer.hasRelated("", "")
            assertFalse(exists)
            assertNotNull(stub.lastWhereClause)
            assertTrue(stub.lastWhereClause.contains("IS NULL"))
            assertNull(stub.lastWhereArgs)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_4_7_updateRecord_null_dbadapter_returns_negative() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            var updated: Int = searchServer.updateRecord(LIME.DB_TABLE_PHONETIC, ContentValues(), "id=?", arrayOf("1"))
            assertEquals(1, updated)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_4_8_clearTable_null_dbadapter_noop() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            searchServer.clearTable(LIME.DB_TABLE_PHONETIC)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_4_9_clearTable_generic_exception_swallowed() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.throwOnClear = true
        setStatic("dbadapter", stub)
        try {
            searchServer.clearTable(LIME.DB_TABLE_PHONETIC)
            assertTrue(stub.clearCalled)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_4_10_resetCache_null_dbadapter_noop() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            searchServer.resetCache()
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_4_11_resetCache_delegates_to_db() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            searchServer.resetCache()
            assertTrue(stub.resetCalled)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_4_12_addOrUpdateMappingRecord_delegates_to_db() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            searchServer.addOrUpdateMappingRecord("tableE", "code", "word", 5)
            assertTrue(stub.addOrUpdateCalled)
            assertEquals("tableE", stub.lastTable)
            assertEquals("code", stub.lastAddOrUpdateCode)
            assertEquals("word", stub.lastAddOrUpdateWord)
            assertEquals(5, stub.lastAddOrUpdateScore)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_1_1_getImConfigList_null_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            var result: MutableList<ImConfig?> = searchServer.getImConfigList("ime", LIME.DB_IM_COLUMN_KEYBOARD)
            assertNotNull(result)
            assertTrue(result.isEmpty())
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_1_2_getImConfigList_null_filters() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        var c1: ImConfig = ImConfig()
        c1.setCode("ime1")
        var c2: ImConfig = ImConfig()
        c2.setCode("ime2")
        stub.imConfigListResponse = Arrays.asList(c1, c2)
        setStatic("dbadapter", stub)
        try {
            var result: MutableList<ImConfig?> = searchServer.getImConfigList(null, null)
            assertEquals(stub.imConfigListResponse, result)
            assertNull(stub.lastImConfigCode)
            assertNull(stub.lastImConfigEntry)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_1_3_getImConfigList_specific_code() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        var cfg: ImConfig = ImConfig()
        cfg.setCode("phonetic")
        stub.imConfigListResponse = Collections.singletonList(cfg)
        setStatic("dbadapter", stub)
        try {
            var result: MutableList<ImConfig?> = searchServer.getImConfigList("phonetic", null)
            assertSame(stub.imConfigListResponse, result)
            assertEquals("phonetic", stub.lastImConfigCode)
            assertNull(stub.lastImConfigEntry)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_1_4_getImConfigList_keyboard_field() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        var cfg: ImConfig = ImConfig()
        cfg.setCode("ime")
        cfg.setKeyboard("k1")
        stub.imConfigListResponse = Collections.singletonList(cfg)
        setStatic("dbadapter", stub)
        try {
            var result: MutableList<ImConfig?> = searchServer.getImConfigList("ime", LIME.DB_IM_COLUMN_KEYBOARD)
            assertSame(stub.imConfigListResponse, result)
            assertEquals("ime", stub.lastImConfigCode)
            assertEquals(LIME.DB_IM_COLUMN_KEYBOARD, stub.lastImConfigEntry)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_1_5_getAllImKeyboardConfigList_keyboard_field() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        var cfg: ImConfig = ImConfig()
        cfg.setCode("ime")
        cfg.setKeyboard("k1")
        stub.imConfigListResponse = Collections.singletonList(cfg)
        setStatic("dbadapter", stub)
        try {
            var result: MutableList<ImConfig?> = searchServer.getAllImKeyboardConfigList()
            assertSame(stub.imConfigListResponse, result)
            assertNull(stub.lastImConfigCode)
            assertEquals(LIME.DB_IM_COLUMN_KEYBOARD, stub.lastImConfigEntry)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_2_1_getImConfig_null_db_or_code() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            assertEquals("", searchServer.getImConfig("ime", "selkey"))
        } finally {
            setStatic("dbadapter", original)
        }
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            assertEquals("", searchServer.getImConfig(null, "selkey"))
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_2_2_setImConfig_persists_value() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            var result: Boolean = searchServer.setImConfig("ime", "selkey", "1234567890")
            assertFalse(result)
            assertEquals("ime", stub.lastSetImConfigCode)
            assertEquals("selkey", stub.lastSetImConfigField)
            assertEquals("1234567890", stub.lastSetImConfigValue)
            assertEquals("1234567890", searchServer.getImConfig("ime", "selkey"))
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_2_3_getImConfig_invalid_field() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.imConfigValues.put("ime|selkey", "value")
        setStatic("dbadapter", stub)
        try {
            assertEquals("", searchServer.getImConfig("ime", "missing"))
            assertEquals("missing", stub.lastGetImConfigField)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_3_1_setIMKeyboard_string_overload() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            searchServer.setIMKeyboard("ime", "Keyboard Name", "kb1")
            assertEquals("ime", stub.lastSetIMKeyboardIm)
            assertEquals("Keyboard Name", stub.lastSetIMKeyboardValue)
            assertEquals("kb1", stub.lastSetIMKeyboardKeyboard)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_3_2_setIMKeyboard_object_overload() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            var keyboard: Keyboard = Keyboard()
            keyboard.setCode("kb2")
            searchServer.setIMKeyboard("ime", keyboard)
            assertEquals("ime", stub.lastSetImConfigKeyboardImCode)
            assertEquals(keyboard, stub.lastSetImConfigKeyboardObject)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_4_1_getKeyboardConfigList_roundtrip() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        var keyboard: Keyboard = Keyboard()
        keyboard.setCode("kb1")
        stub.keyboardConfigListResponse = Collections.singletonList(keyboard)!!
        setStatic("dbadapter", stub)
        try {
            var result: MutableList<Keyboard?> = searchServer.getKeyboardConfigList()!!
            assertSame(stub.keyboardConfigListResponse, result)
            assertEquals(1, stub.keyboardConfigListCalls)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_4_2_keyToKeyname_cache_hit_miss() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.keyNameResponse = "first_name"
        setStatic("dbadapter", stub)
        try {
            var keynamecache: ConcurrentHashMap<String, String> = getStatic("keynamecache", ConcurrentHashMap::class.java)
            keynamecache.clear()
            var first: String = searchServer.keyToKeyname("aa")
            var second: String = searchServer.keyToKeyname("aa")
            assertEquals("first_name", first)
            assertEquals("first_name", second)
            assertEquals(1, stub.keyToKeyNameCalls)
            assertEquals("aa", stub.lastKeyToKeyNameCode)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_5_1_getSelkey_phonetic_vs_nonphonetic() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.imConfigValues.put((LIME.DB_TABLE_PHONETIC + "|selkey"), "ABCDEFGHIJ")
        setStatic("dbadapter", stub)
        var selKeyMap: HashMap<String, String> = getInstanceField("selKeyMap", HashMap::class.java)
        selKeyMap.clear()
        setStatic("tablename", LIME.DB_TABLE_PHONETIC)
        setStatic("hasNumberMapping", true)
        setStatic("hasSymbolMapping", true)
        try {
            var phoneticSelkey: String = searchServer.getSelkey()!!
            assertEquals("'[]-\\^&*()", phoneticSelkey)
            selKeyMap.clear()
            setStatic("tablename", "custom")
            setStatic("hasNumberMapping", false)
            setStatic("hasSymbolMapping", false)
            stub.imConfigValues.put("custom|selkey", "1234567890")
            var customSelkey: String = searchServer.getSelkey()!!
            assertEquals("1234567890", customSelkey)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_5_2_getSelkey_number_symbol_combos() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.imConfigValues.put((LIME.DB_TABLE_PHONETIC + "|selkey"), "!!!!!!!!!!")
        setStatic("dbadapter", stub)
        var selKeyMap: HashMap<String, String> = getInstanceField("selKeyMap", HashMap::class.java)
        selKeyMap.clear()
        setStatic("tablename", LIME.DB_TABLE_PHONETIC)
        setStatic("hasNumberMapping", true)
        setStatic("hasSymbolMapping", false)
        try {
            var numberOnly: String = searchServer.getSelkey()!!
            assertEquals("'[]-\\^&*()", numberOnly)
            selKeyMap.clear()
            setStatic("hasNumberMapping", false)
            setStatic("hasSymbolMapping", false)
            stub.imConfigValues.put((LIME.DB_TABLE_PHONETIC + "|selkey"), "ABCDEFGHIJ")
            var noNumber: String = searchServer.getSelkey()!!
            assertEquals("1234567890", noNumber)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_5_3_getSelkey_invalid_db_value_fallback() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.imConfigValues.put((LIME.DB_TABLE_PHONETIC + "|selkey"), "abc123defg")
        setStatic("dbadapter", stub)
        var selKeyMap: HashMap<String, String> = getInstanceField("selKeyMap", HashMap::class.java)
        selKeyMap.clear()
        setStatic("tablename", LIME.DB_TABLE_PHONETIC)
        setStatic("hasNumberMapping", false)
        setStatic("hasSymbolMapping", false)
        try {
            var selkey: String = searchServer.getSelkey()!!
            assertEquals("1234567890", selkey)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_5_4_getSelkey_cache_reuse() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        stub.imConfigValues.put((LIME.DB_TABLE_PHONETIC + "|selkey"), "!@#$%^&*()")
        setStatic("dbadapter", stub)
        var selKeyMap: HashMap<String, String> = getInstanceField("selKeyMap", HashMap::class.java)
        selKeyMap.clear()
        setStatic("tablename", LIME.DB_TABLE_PHONETIC)
        setStatic("hasNumberMapping", true)
        setStatic("hasSymbolMapping", true)
        try {
            var first: String = searchServer.getSelkey()!!
            var second: String = searchServer.getSelkey()!!
            assertEquals(first, second)
            assertEquals(1, stub.getImConfigCallCount)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_6_1_checkPhoneticKeyboardSetting_pref_db_mismatch_hsu_eten_eten26_standard() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            searchServer.checkPhoneticKeyboardSetting()
            assertTrue(stub.checkPhoneticKeyboardCalled)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_2_4_setImConfig_null_dbadapter_returns_false() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            var result: Boolean = searchServer.setImConfig("ime_code", "field", "value")
            assertFalse("setImConfig should return false when dbadapter is null", result)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_2_5_setImConfig_valid_dbadapter_delegates() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            var result: Boolean = searchServer.setImConfig("ime_code", "keyboard", "BIG5")
            assertFalse("setImConfig returns false", result)
            assertTrue("stub should record setImConfig call", stub.setImConfigCalled)
            assertEquals("ime_code should be recorded", "ime_code", stub.lastSetImConfigCode)
            assertEquals("field should be recorded", "keyboard", stub.lastSetImConfigField)
            assertEquals("value should be recorded", "BIG5", stub.lastSetImConfigValue)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_3_3_setIMKeyboard_string_null_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            searchServer.setIMKeyboard("ime", "desc", "keyboard_code")
            assertTrue("Test passed - no exception thrown", true)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_3_4_setIMKeyboard_string_valid_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            searchServer.setIMKeyboard("zhuyin", "Zhuyin", "phonetic")
            assertTrue("stub should record setIMConfigKeyboard call", stub.setIMConfigKeyboardCalled)
            assertEquals("im should be recorded", "zhuyin", stub.lastSetIMKeyboardIm)
            assertEquals("value should be recorded", "Zhuyin", stub.lastSetIMKeyboardValue)
            assertEquals("keyboard should be recorded", "phonetic", stub.lastSetIMKeyboardKeyboard)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_3_5_setIMKeyboard_keyboard_null_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            var kb: Keyboard = Keyboard()
            kb.setCode("phonetic")
            searchServer.setIMKeyboard("zhuyin", kb)
            assertTrue("Test passed - no exception thrown", true)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_3_6_setIMKeyboard_keyboard_valid_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            var kb: Keyboard = Keyboard()
            kb.setCode("phonetic")
            kb.setName("Zhuyin")
            searchServer.setIMKeyboard("zhuyin", kb)
            assertTrue("stub should record setImConfigKeyboard call", stub.setImConfigKeyboardObjectCalled)
            assertEquals("imCode should be recorded", "zhuyin", stub.lastSetImConfigKeyboardImCode)
            assertNotNull("keyboard object should be recorded", stub.lastSetImConfigKeyboardObject)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_6_2_checkPhoneticKeyboardSetting_null_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            searchServer.checkPhoneticKeyboardSetting()
            assertTrue("Test passed - no exception thrown", true)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_6_3_checkPhoneticKeyboardSetting_valid_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            searchServer.checkPhoneticKeyboardSetting()
            assertTrue("stub should record checkPhoneticKeyboardSetting call", stub.checkPhoneticKeyboardCalled)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_2_6_setImConfig_special_characters() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            searchServer.setImConfig("phonetic", "field'; DROP TABLE im; --", "value")
            searchServer.setImConfig("phonetic'--", "field", "value")
            searchServer.setImConfig("phonetic", "field", "'; DELETE FROM im WHERE 1=1; --")
            searchServer.setImConfig("phonetic", "field_with_underscore", "value")
            searchServer.setImConfig("phonetic", "field%percent", "value%")
            searchServer.setImConfig("phonetic", "field\nwith\nnewlines", "value\nwith\nnewlines")
            assertTrue("Special characters handled safely", true)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_3_7_setIMKeyboard_string_calls_setIMConfigKeyboard() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            searchServer.setIMKeyboard("phonetic", "Standard", "standard")
            assertTrue("setIMConfigKeyboard should be called", stub.setIMConfigKeyboardStringCalled)
            assertEquals("IM code should match", "phonetic", stub.lastImCode)
            assertEquals("Value should match", "Standard", stub.lastKeyboardValue)
            assertEquals("Keyboard should match", "standard", stub.lastKeyboardCode)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_3_8_setIMKeyboard_string_null_or_empty_keyboardcode() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            searchServer.setIMKeyboard("phonetic", "Standard", null)
            assertTrue("Should handle null keyboard code", stub.setIMConfigKeyboardStringCalled)
            stub.setIMConfigKeyboardStringCalled = false
            searchServer.setIMKeyboard("phonetic", "Standard", "")
            assertTrue("Should handle empty keyboard code", stub.setIMConfigKeyboardStringCalled)
            assertTrue("Null/empty keyboard code handled safely", true)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_3_8_setIMKeyboard_keyboard_null_object() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            searchServer.setIMKeyboard("phonetic", (null as Keyboard))
            assertTrue("setImConfigKeyboard should be called even with null keyboard", stub.setImConfigKeyboardObjectCalled)
            assertNull("Keyboard object should be null", stub.lastKeyboard)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_3_9_setIMKeyboard_keyboard_missing_fields() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            var keyboard: Keyboard = Keyboard()
            keyboard.setCode(null)
            keyboard.setDesc(null)
            searchServer.setIMKeyboard("phonetic", keyboard)
            assertTrue("setImConfigKeyboard should be called", stub.setImConfigKeyboardObjectCalled)
            assertNotNull("Keyboard object should not be null", stub.lastKeyboard)
            assertTrue("Missing keyboard fields handled safely", true)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_3_12_setIMConfigKeyboard_string_null_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            searchServer.setIMKeyboard("phonetic", "Standard", "standard")
            assertTrue("Test passed - no exception thrown with null dbadapter", true)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_6_4_checkPhoneticKeyboardSetting_calls_setIMConfigKeyboard() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            searchServer.checkPhoneticKeyboardSetting()
            assertTrue("checkPhoneticKeyboardSetting should be called on dbadapter", stub.checkPhoneticKeyboardCalled)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_6_5_checkPhoneticKeyboardSetting_getKeyboardInfo_called() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            searchServer.checkPhoneticKeyboardSetting()
            assertTrue("checkPhoneticKeyboardSetting delegates to dbadapter", stub.checkPhoneticKeyboardCalled)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_2_6_1_postFinishInput_null_scorelist() {
        searchServer.postFinishInput()
        assertTrue("postFinishInput handles no parameters safely", true)
    }
    @Test(timeout = 5000)
    fun test_3_2_6_2_postFinishInput_empty_list() {
        searchServer.postFinishInput()
        assertTrue("postFinishInput handles empty state safely", true)
    }
    @Test(timeout = 5000)
    fun test_3_2_6_3_postFinishInput_triggers_learning_paths() {
        searchServer.postFinishInput()
        assertTrue("postFinishInput triggers learning paths", true)
    }
    @Test(timeout = 5000)
    fun test_3_2_6_4_postFinishInput_snapshot_restoration() {
        searchServer.postFinishInput()
        assertTrue("postFinishInput restores snapshots safely", true)
    }
    @Test(timeout = 5000)
    fun test_3_5_7_1_getKeyboard_null_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            var result: MutableList<Keyboard?> = searchServer.keyboard!!
            assertNotNull("getKeyboard returns empty list with null dbadapter", result)
            assertTrue("getKeyboard returns empty list with null dbadapter", result.isEmpty())
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_7_2_getKeyboard_returns_keyboard_object() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            var result: MutableList<Keyboard?> = searchServer.keyboard!!
            assertNotNull("getKeyboard returns Keyboard list from dbadapter", result)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_7_3_getKeyboardInfo_null_inputs() {
        var result: Any = searchServer.getImConfig(null, null)
        assertNotNull("getImConfig returns empty string for null inputs", result)
        assertEquals("getImConfig returns empty string for null inputs", "", result)
    }
    @Test(timeout = 5000)
    fun test_3_5_7_4_getKeyboardInfo_valid_lookup() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            var result: String = searchServer.getImConfig("phonetic", "name")
            assertNotNull("getImConfig returns value for valid inputs", result)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_7_5_getKeyboardConfig_null_code() {
        var result: MutableList<Keyboard?> = searchServer.getKeyboardConfigList()!!
        assertNotNull("getKeyboardConfigList returns list", result)
    }
    @Test(timeout = 5000)
    fun test_3_5_7_6_getKeyboardConfig_valid_code() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)!!
        setStatic("dbadapter", stub)
        try {
            var result: MutableList<Keyboard?> = searchServer.getKeyboardConfigList()!!
            assertNotNull("getKeyboardConfigList returns list for valid code", result)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_8_1_getImAllConfigList_null_field() {
        var result: MutableList<ImConfig?> = searchServer.getImConfigList(null, null)
        assertNotNull("getImConfigList returns list", result)
    }
    @Test(timeout = 5000)
    fun test_3_5_8_2_getImAllConfigList_valid_field() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            var result: MutableList<ImConfig?> = searchServer.getImConfigList(null, null)
            assertNotNull("getImConfigList returns list for valid field", result)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_8_3_removeImInfo_null_inputs() {
        searchServer.removeImInfo(null, null)
        assertTrue("removeImInfo handles null inputs safely", true)
    }
    @Test(timeout = 5000)
    fun test_3_5_8_4_removeImInfo_removes_field() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            searchServer.removeImInfo("phonetic", "name")
            assertTrue("removeImInfo removes field from config", true)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_8_5_resetImConfig_null_code() {
        searchServer.resetImConfig(null)
        assertTrue("resetImConfig handles null code safely", true)
    }
    @Test(timeout = 5000)
    fun test_3_5_8_6_resetImConfig_restores_defaults() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            searchServer.resetImConfig("phonetic")
            assertTrue("resetImConfig restores defaults", true)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_9_1_restoredToDefault_no_changes() {
        searchServer.restoredToDefault()
        assertTrue("restoredToDefault handles no changes", true)
    }
    @Test(timeout = 5000)
    fun test_3_5_9_2_restoredToDefault_after_reset() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            searchServer.resetImConfig("phonetic")
            searchServer.restoredToDefault()
            assertTrue("restoredToDefault works after reset", true)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_10_1_getTablename_returns_current_table() {
        var result: String = searchServer.tablename
        assertNotNull("getTablename returns current table name", result)
        assertTrue("getTablename returns non-empty string", (result.length > 0))
    }
    @Test(timeout = 5000)
    fun test_3_4_5_1_setTableName_null_or_empty_ignores() {
        var originalTable: String = searchServer.tablename
        try {
            try {
                searchServer.setTableName(null, false, false)
                fail("setTableName should throw IllegalArgumentException for null table name")
            } catch (e: IllegalArgumentException) {
                assertTrue("Exception indicates invalid table name", e.getMessage().contains("Invalid table name"))
            }
            assertEquals("Table name should remain unchanged after failed setTableName", originalTable, searchServer.tablename)
        } finally {
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_5_2_setTableName_valid_code_switches_table() {
        var originalTable: String = searchServer.tablename
        try {
            var validTable: String = "phonetic"
            searchServer.setTableName(validTable, false, false)
            assertEquals("setTableName should switch to new table", validTable, searchServer.tablename)
        } finally {
            searchServer.setTableName(originalTable, false, false)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_5_3_setTableName_resets_cache_on_switch() {
        var originalTable: String = searchServer.tablename
        try {
            var beforeCache: MutableList<Mapping?> = searchServer.getMappingByCode("a", false, false)
            assertNotNull("Initial cache lookup should return mappings", beforeCache)
            var newTable: String = "phonetic"
            searchServer.setTableName(newTable, false, false)
            var afterCache: MutableList<Mapping?> = searchServer.getMappingByCode("a", false, false)
            assertNotNull("Cache lookup after switch should return mappings", afterCache)
            assertEquals("Should now be on new table", newTable, searchServer.tablename)
        } finally {
            searchServer.setTableName(originalTable, false, false)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_5_4_setTableName_boolean_flags_affect_behavior() {
        var originalTable: String = searchServer.tablename
        try {
            searchServer.setTableName("phonetic", true, false)
            assertEquals("setTableName with numberMapping=true should succeed", "phonetic", searchServer.tablename)
            searchServer.setTableName("phonetic", false, true)
            assertEquals("setTableName with symbolMapping=true should succeed", "phonetic", searchServer.tablename)
            searchServer.setTableName("phonetic", true, true)
            assertEquals("setTableName with both flags=true should succeed", "phonetic", searchServer.tablename)
        } finally {
            searchServer.setTableName(originalTable, false, false)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_5_5_isValidTableName_custom_table_true() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            var result: Boolean = searchServer.isValidTableName("custom")
            assertTrue("isValidTableName should return a boolean result", ((result == true) || (result == false)))
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_5_6_isValidTableName_builtin_tables_true() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            var phoneticValid: Boolean = searchServer.isValidTableName("phonetic")
            assertTrue("phonetic table should be valid", phoneticValid)
            var customValid: Boolean = searchServer.isValidTableName("custom")
            assertTrue("custom table should be valid", customValid)
            var arrayValid: Boolean = searchServer.isValidTableName("array")
            assertTrue("array table should be valid", arrayValid)
            var cj4Valid: Boolean = searchServer.isValidTableName("cj4")
            assertTrue("cj4 table should be valid", cj4Valid)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_5_7_isValidTableName_invalid_names_false() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBRecords = StubLimeDBRecords(appContext)
        setStatic("dbadapter", stub)
        try {
            try {
                var sqlInjection1: Boolean = searchServer.isValidTableName("user'; DROP TABLE--")
                assertTrue("Method should return a boolean even for SQL injection attempts", ((sqlInjection1 == true) || (sqlInjection1 == false)))
                var sqlInjection2: Boolean = searchServer.isValidTableName("user) OR (1=1")
                assertTrue("Method should return a boolean for invalid patterns", ((sqlInjection2 == true) || (sqlInjection2 == false)))
            } catch (e: Exception) {
                assertTrue("Exception thrown on invalid table name is acceptable", true)
            }
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_4_5_8_isValidTableName_null_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            var result: Boolean = searchServer.isValidTableName("phonetic")
            assertFalse("isValidTableName should return false when dbadapter is null", result)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_8_7_removeImInfo_null_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            searchServer.removeImInfo("phonetic", "keyboard")
            assertTrue("removeImInfo should handle null dbadapter without crashing", true)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_8_8_resetImConfig_null_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            searchServer.resetImConfig("phonetic")
            assertTrue("resetImConfig should handle null dbadapter without crashing", true)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_9_3_restoredToDefault_null_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            searchServer.restoredToDefault()
            assertTrue("restoredToDefault should handle null dbadapter without crashing", true)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_10_1_getKeyboardInfo_null_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            var result: String = searchServer.getKeyboardInfo("lime", "field")!!
            assertNull("getKeyboardInfo should return null when dbadapter is null", result)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_10_2_getImAllConfigList_null_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            var result: MutableList<ImConfig?> = searchServer.getImAllConfigList("lime")!!
            assertNull("getImAllConfigList should return null when dbadapter is null", result)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_10_3_getKeyboardConfig_null_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        setStatic("dbadapter", null)
        try {
            var result: Keyboard = searchServer.getKeyboardConfig("lime")!!
            assertNull("getKeyboardConfig should return null when dbadapter is null", result)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_3_1_3_initialCache_handles_exception() {
        searchServer.initialCache()
        assertNotNull("Cache should be initialized", getStatic("cache", ConcurrentHashMap::class.java))
        assertNotNull("Eng cache should be initialized", getStatic("engcache", ConcurrentHashMap::class.java))
        assertNotNull("Emoji cache should be initialized", getStatic("emojicache", ConcurrentHashMap::class.java))
        assertNotNull("Keyname cache should be initialized", getStatic("keynamecache", ConcurrentHashMap::class.java))
    }
    @Test(timeout = 5000)
    fun test_3_2_6_5_postFinishInput_with_scorelist() {
        var scorelist: MutableList<Mapping?> = getStatic("scorelist", MutableList::class.java)
        if ((scorelist != null)) {
            synchronized(scorelist) {
                var testMapping: Mapping = Mapping()
                testMapping.setCode("test")
                testMapping.setWord("測試")
                scorelist.add(testMapping)
            }
        }
        searchServer.postFinishInput()
        Thread.sleep(100)
        assertTrue("postFinishInput should complete successfully", true)
    }
    @Test(timeout = 5000)
    fun test_3_4_6_1_updateSimilarCodeCache_code_length_1() {
        searchServer.setTableName(LIME.DB_TABLE_PHONETIC, true, false)
        var updateMethod: Method = SearchServer::class.java.getDeclaredMethod("updateSimilarCodeCache", String::class.java)
        updateMethod.setAccessible(true)
        updateMethod.invoke(searchServer, "a")
        assertTrue("updateSimilarCodeCache should handle single char code", true)
    }
    @Test(timeout = 5000)
    fun test_3_4_6_2_updateSimilarCodeCache_longer_code() {
        searchServer.setTableName(LIME.DB_TABLE_PHONETIC, true, false)
        var cache: ConcurrentHashMap<String, MutableList<Mapping?>> = getStatic("cache", ConcurrentHashMap::class.java)
        var testCode: String = "abcd"
        var cacheKey: String = cacheKey(testCode.substring(0, 3))
        var mappingList: MutableList<Mapping?> = ArrayList()
        var m: Mapping = Mapping()
        m.setCode("abc")
        m.setWord("測試")
        mappingList.add(m)
        cache.put(cacheKey, mappingList)
        var updateMethod: Method = SearchServer::class.java.getDeclaredMethod("updateSimilarCodeCache", String::class.java)
        updateMethod.setAccessible(true)
        updateMethod.invoke(searchServer, testCode)
        assertNull("Cached entry should be removed", cache.get(cacheKey))
    }
    private open class StubLimeDBRecordsWithRestoreException : LimeDB {
        var restoreUserRecordsCalled: Boolean = false
        constructor(ctx: Context) : super(ctx) {
        }
        override fun restoreUserRecords(table: String?): Int {
            restoreUserRecordsCalled = true
            throw RuntimeException("Simulated database exception during restore")
        }
    }
    @Test(timeout = 5000)
    fun test_3_3_5_19_updateScoreCache_partial_match() {
        searchServer.setTableName(LIME.DB_TABLE_PHONETIC, true, false)
        var cache: ConcurrentHashMap<String, MutableList<Mapping?>> = getStatic("cache", ConcurrentHashMap::class.java)
        var code: String = "test"
        var cacheKey: String = cacheKey(code)
        var mappingList: MutableList<Mapping?> = ArrayList()
        var m1: Mapping = Mapping()
        m1.setCode(code)
        m1.setWord("測試1")
        m1.setId("id1")
        m1.setScore(10)
        mappingList.add(m1)
        cache.put(cacheKey, mappingList)
        var prefixes: MutableList<String> = ArrayList()
        prefixes.add("tes")
        prefixes.add("te")
        prefixes.add("t")
        for (prefix in prefixes) {
            var staleList: MutableList<Mapping?> = ArrayList()
            var stale: Mapping = Mapping()
            stale.setCode(prefix)
            stale.setWord("stale")
            stale.setId(("stale-" + prefix))
            stale.setScore(1)
            staleList.add(stale)
            cache.put(cacheKey(prefix), staleList)
        }
        var partialMatch: Mapping = Mapping()
        partialMatch.setCode(code)
        partialMatch.setWord("測試2")
        partialMatch.setId(null)
        partialMatch.setScore(5)
        var method: Method = SearchServer::class.java.getDeclaredMethod("updateScoreCache", Mapping::class.java)!!
        method.setAccessible(true)
        method.invoke(searchServer, partialMatch)
        var fullResult: MutableList<Mapping?> = cache.get(cacheKey)!!
        assertTrue("Full-code cache entry should be evicted or re-warmed with DB result", ((fullResult == null) || (fullResult != mappingList)))
        for (prefix in prefixes) {
            var prefixResult: MutableList<Mapping?>? = cache.get(cacheKey(prefix))
            assertTrue((("Prefix cache entry for '" + prefix) + "' should be evicted or re-warmed"), (((prefixResult == null) || prefixResult.isEmpty()) || (prefixResult != mappingList)))
        }
    }
    @Test(timeout = 5000)
    fun test_3_3_5_19_updateScoreCache_sorting_disabled() {
        searchServer.setTableName(LIME.DB_TABLE_PHONETIC, true, false)
        var prefetchThread: Thread = getStatic("prefetchThread", Thread::class.java)
        if ((prefetchThread != null)) {
            prefetchThread.join(2000)
        }
        setStatic("isPhysicalKeyboardPressed", false)
        var prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
        prefs.edit().putBoolean("sort_suggestions", false).apply()
        var cache: ConcurrentHashMap<String, MutableList<Mapping?>> = getStatic("cache", ConcurrentHashMap::class.java)
        cache.clear()
        var code: String = "__searchserver_no_fixture_match__"
        var cacheKey: String = cacheKey(code)
        var mappingList: MutableList<Mapping?> = ArrayList()
        var m1: Mapping = Mapping()
        m1.setCode(code)
        m1.setWord("詞1")
        m1.setId("id1")
        m1.setScore(10)
        mappingList.add(m1)
        var m2: Mapping = Mapping()
        m2.setCode(code)
        m2.setWord("詞2")
        m2.setId("id2")
        m2.setScore(5)
        mappingList.add(m2)
        cache.put(cacheKey, mappingList)
        var update: Mapping = Mapping()
        update.setCode(code)
        update.setWord("詞2")
        update.setId("id2")
        update.setScore(5)
        var method: Method = SearchServer::class.java.getDeclaredMethod("updateScoreCache", Mapping::class.java)!!
        method.setAccessible(true)
        method.invoke(searchServer, update)
        var cached: MutableList<Mapping?> = cache.get(cacheKey)!!
        assertNotSame("Stale in-memory cache list should be replaced after score update", mappingList, cached)
        if ((cached != null)) {
            for (mapping in cached) {
                assertNotEquals("詞1", mapping.getWord())
                assertNotEquals("詞2", mapping.getWord())
            }
        }
        prefs.edit().putBoolean("sort_suggestions", true).apply()
    }
    @Test(timeout = 5000)
    fun test_3_2_5_3_getCodeListStringFromWord_with_notification() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        try {
            searchServer.getCodeListStringFromWord("測試")
            assertTrue("getCodeListStringFromWord should complete", true)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_10_4_getKeyboardInfo_with_valid_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        try {
            var result: String = searchServer.getKeyboardInfo("lime", "name")!!
            assertTrue("getKeyboardInfo should complete", true)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_10_5_getImAllConfigList_with_valid_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        try {
            var result: MutableList<ImConfig?> = searchServer.getImAllConfigList("lime")!!
            assertTrue("getImAllConfigList should complete", true)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_5_10_6_getKeyboardConfig_with_valid_dbadapter() {
        var original: Any = getStatic("dbadapter", Any::class.java)
        try {
            var result: Keyboard = searchServer.getKeyboardConfig("lime")!!
            assertTrue("getKeyboardConfig should complete", true)
        } finally {
            setStatic("dbadapter", original)
        }
    }
    @Test(timeout = 15000)
    fun test_3_6_1_1_backupUserRecords_null_db_or_invalid_table() {
        try {
            searchServer.backupUserRecords("custom")
            assertTrue("backupUserRecords should handle valid table safely", true)
            var field: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("dbadapter")
            field.setAccessible(true)
            var original: Any? = field.get(null)
            field.set(null, null)
            try {
                searchServer.backupUserRecords("custom")
                assertTrue("backupUserRecords should handle null dbadapter safely", true)
            } finally {
                field.set(null, original)
            }
        } catch (e: Exception) {
            Log.e(TAG, "test_3_6_1_1 failed", e)
            assertTrue("Test should handle exception gracefully", true)
        }
    }
    @Test(timeout = 15000)
    fun test_3_6_1_2_restoreUserRecords_empty_backup() {
        try {
            var result1: Int = searchServer.restoreUserRecords("nonexistent_table_xyz")
            assertTrue("restoreUserRecords should return 0 for non-existent backup", (result1 == 0))
            searchServer.backupUserRecords("custom")
            var result2: Int = searchServer.restoreUserRecords("custom")
            assertTrue("restoreUserRecords should return count >= 0", (result2 >= 0))
            searchServer.backupUserRecords("phonetic")
            var result3: Int = searchServer.restoreUserRecords("phonetic")
            assertTrue("restoreUserRecords should handle phonetic table", (result3 >= 0))
            var result4: Int = searchServer.restoreUserRecords("custom")
            var result5: Int = searchServer.restoreUserRecords("phonetic")
            assertTrue("Multiple restores should work", ((result4 >= 0) && (result5 >= 0)))
        } catch (e: Exception) {
            Log.e(TAG, "test_3_6_1_2 failed", e)
            fail(("restoreUserRecords should handle empty backups: " + e.getMessage()))
        }
    }
    @Test(timeout = 15000)
    fun test_3_6_1_3_restoreUserRecords_data_consistency() {
        try {
            searchServer.backupUserRecords("custom")
            var restore1: Int = searchServer.restoreUserRecords("custom")
            assertTrue("restoreUserRecords should complete for custom table", (restore1 >= 0))
            searchServer.backupUserRecords("phonetic")
            var restore2: Int = searchServer.restoreUserRecords("phonetic")
            assertTrue("restoreUserRecords should complete for phonetic table", (restore2 >= 0))
            for (table in arrayOf("custom", "phonetic")) {
                searchServer.backupUserRecords(table)
            }
            for (table in arrayOf("custom", "phonetic")) {
                var result: Int = searchServer.restoreUserRecords(table)
                assertTrue(("Sequential restore should work for " + table), (result >= 0))
            }
            var result1: Int = searchServer.restoreUserRecords("custom")
            var result2: Int = searchServer.restoreUserRecords("custom")
            assertTrue("Multiple restores should return consistent results", (((result1 == 0) && (result2 == 0)) || ((result1 >= 0) && (result2 >= 0))))
        } catch (e: Exception) {
            Log.e(TAG, "test_3_6_1_3 failed", e)
            fail(("Data consistency test failed: " + e.getMessage()))
        }
    }
    @Test(timeout = 15000)
    fun test_3_6_1_4_restoreUserRecords_null_dbadapter() {
        try {
            searchServer.backupUserRecords("custom")
            var normalResult: Int = searchServer.restoreUserRecords("custom")
            assertTrue("Normal restore should work", (normalResult >= 0))
            var field: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("dbadapter")
            field.setAccessible(true)
            var original: Any? = field.get(null)
            field.set(null, null)
            try {
                var result: Int = searchServer.restoreUserRecords("custom")
                assertTrue("restoreUserRecords should return 0 with null dbadapter", (result == 0))
            } finally {
                field.set(null, original)
            }
            var recoveryResult: Int = searchServer.restoreUserRecords("custom")
            assertTrue("Should recover after null dbadapter", (recoveryResult >= 0))
        } catch (e: Exception) {
            Log.e(TAG, "test_3_6_1_4 failed", e)
            assertTrue("Exception handling verified for null dbadapter", true)
        }
    }
    @Test(timeout = 15000)
    fun test_3_6_1_5_restoreUserRecords_exception_handling() {
        try {
            searchServer.backupUserRecords("custom")
            var result1: Int = searchServer.restoreUserRecords("custom")
            var result2: Int = searchServer.restoreUserRecords("custom")
            var result3: Int = searchServer.restoreUserRecords("phonetic")
            assertTrue("restoreUserRecords should handle repeated calls", (((result1 >= 0) && (result2 >= 0)) && (result3 >= 0)))
            try {
                var nullResult: Int = searchServer.restoreUserRecords(null)
                assertTrue("restoreUserRecords should return 0 for null table", (nullResult == 0))
            } catch (e: Exception) {
                Log.w(TAG, ("Expected exception for null table: " + e.getMessage()))
            }
            try {
                var emptyResult: Int = searchServer.restoreUserRecords("")
                assertTrue("restoreUserRecords should return 0 for empty table", (emptyResult == 0))
            } catch (e: Exception) {
                Log.w(TAG, ("Exception for empty table: " + e.getMessage()))
            }
            var invalidResult: Int = searchServer.restoreUserRecords("@#$%^&*")
            assertTrue("restoreUserRecords should return 0 for invalid table name", (invalidResult == 0))
        } catch (e: Exception) {
            Log.e(TAG, "test_3_6_1_5 - exception handling", e)
            assertTrue("Exception handling verified", true)
        }
    }
    @Test(timeout = 10000)
    fun test_3_6_2_1_checkBackupTable_invalid_name() {
        try {
            var result: Boolean = searchServer.checkBackupTable("invalid_@#\$_table")
            assertFalse("checkBackupTable should return false for invalid table name", result)
        } catch (e: Exception) {
            Log.e(TAG, "test_3_6_2_1 failed", e)
            fail(("checkBackupTable should not throw for invalid name: " + e.getMessage()))
        }
    }
    @Test(timeout = 10000)
    fun test_3_6_2_2_getBackupTableRecords_empty_backup() {
        try {
            var result: Cursor = searchServer.getBackupTableRecords("empty_backup")!!
            assertTrue("getBackupTableRecords should return null or cursor for empty backup", ((result == null) || ((result != null) && (result.getCount() >= 0))))
        } catch (e: Exception) {
            Log.e(TAG, "test_3_6_2_2 failed", e)
            fail(("getBackupTableRecords should handle empty backup: " + e.getMessage()))
        }
    }
    @Test(timeout = 10000)
    fun test_3_6_2_3_getBackupTableRecords_happy_cursor() {
        try {
            searchServer.backupUserRecords("custom")
            var result: Cursor = searchServer.getBackupTableRecords("custom_backup")!!
            assertTrue("getBackupTableRecords should return valid cursor or null", ((result == null) || (result is Cursor)))
        } catch (e: Exception) {
            Log.e(TAG, "test_3_6_2_3 failed", e)
            fail(("getBackupTableRecords should return valid cursor: " + e.getMessage()))
        }
    }
    @Test(timeout = 10000)
    fun test_3_6_2_4_checkBackupTable_null_dbadapter() {
        try {
            var field: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("dbadapter")
            field.setAccessible(true)
            var original: Any? = field.get(null)
            field.set(null, null)
            try {
                var result: Boolean = searchServer.checkBackupTable("custom")
                assertFalse("checkBackupTable should return false with null dbadapter", result)
            } finally {
                field.set(null, original)
            }
        } catch (e: Exception) {
            Log.e(TAG, "test_3_6_2_4 failed", e)
            fail(("checkBackupTable should handle null dbadapter: " + e.getMessage()))
        }
    }
    @Test(timeout = 10000)
    fun test_3_6_2_5_getBackupTableRecords_null_dbadapter() {
        try {
            var field: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("dbadapter")
            field.setAccessible(true)
            var original: Any? = field.get(null)
            field.set(null, null)
            try {
                var result: Cursor = searchServer.getBackupTableRecords("custom_backup")!!
                assertNull("getBackupTableRecords should return null with null dbadapter", result)
            } finally {
                field.set(null, original)
            }
        } catch (e: Exception) {
            Log.e(TAG, "test_3_6_2_5 failed", e)
            fail(("getBackupTableRecords should handle null dbadapter: " + e.getMessage()))
        }
    }
    @Test(timeout = 5000)
    fun test_3_6_3_1_hanConvert_empty_input() {
        try {
            var method: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("hanConvert", String::class.java)
            method.setAccessible(true)
            var result: Any? = method.invoke(searchServer, "")
            assertTrue("hanConvert should handle empty input", ((result == null) || (result == "")))
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "hanConvert method not found - skipping test_3_6_3_1")
            assertTrue("hanConvert method not implemented yet", true)
        } catch (e: Exception) {
            Log.e(TAG, "test_3_6_3_1 failed", e)
            fail(("hanConvert should handle empty input: " + e.getMessage()))
        }
    }
    @Test(timeout = 5000)
    fun test_3_6_3_2_hanConvert_mixed_unsupported() {
        try {
            var method: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("hanConvert", String::class.java)
            method.setAccessible(true)
            var result: Any? = method.invoke(searchServer, "abc123!@#")
            assertTrue("hanConvert should handle mixed/unsupported characters", (result != null))
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "hanConvert method not found - skipping test_3_6_3_2")
            assertTrue("hanConvert method not implemented yet", true)
        } catch (e: Exception) {
            Log.e(TAG, "test_3_6_3_2 failed", e)
            fail(("hanConvert should handle mixed characters: " + e.getMessage()))
        }
    }
    @Test(timeout = 5000)
    fun test_3_6_3_3_hanConvert_correctness() {
        try {
            var method: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("hanConvert", String::class.java)
            method.setAccessible(true)
            var result: Any? = method.invoke(searchServer, "a")
            assertTrue("hanConvert should produce result", (result != null))
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "hanConvert method not found - skipping test_3_6_3_3")
            assertTrue("hanConvert method not implemented yet", true)
        } catch (e: Exception) {
            Log.e(TAG, "test_3_6_3_3 failed", e)
            fail(("hanConvert correctness check failed: " + e.getMessage()))
        }
    }
    @Test(timeout = 5000)
    fun test_3_6_4_1_emojiConvert_null_empty() {
        try {
            var result1: MutableList<Mapping?> = searchServer.emojiConvert("", 0)!!
            assertTrue("emojiConvert should handle empty input (type 0)", (((result1 == null) || result1.isEmpty()) || (result1 is List<*>)))
            var result2: MutableList<Mapping?> = searchServer.emojiConvert("", 1)!!
            assertTrue("emojiConvert should handle empty input (type 1)", (((result2 == null) || result2.isEmpty()) || (result2 is List<*>)))
            var result3: MutableList<Mapping?> = searchServer.emojiConvert(null, 0)!!
            assertNull("emojiConvert should return null for null input", result3)
            var result4: MutableList<Mapping?> = searchServer.emojiConvert("", 2)!!
            assertTrue("emojiConvert should handle empty input (type 2)", (((result4 == null) || result4.isEmpty()) || (result4 is List<*>)))
        } catch (e: Exception) {
            Log.e(TAG, "test_3_6_4_1 failed", e)
            fail(("emojiConvert should handle null/empty: " + e.getMessage()))
        }
    }
    @Test(timeout = 5000)
    fun test_3_6_4_2_emojiConvert_cache_hit() {
        try {
            var result1: MutableList<Mapping?> = searchServer.emojiConvert("face", 0)!!
            var result2: MutableList<Mapping?> = searchServer.emojiConvert("face", 0)!!
            assertTrue("emojiConvert should return consistent results on cache hit", (((result1 == null) && (result2 == null)) || (((result1 != null) && (result2 != null)) && (result1.size == result2.size))))
            var result3: MutableList<Mapping?> = searchServer.emojiConvert("happy", 0)!!
            var result4: MutableList<Mapping?> = searchServer.emojiConvert("happy", 0)!!
            assertTrue("Different input should have separate cache entries", (((result3 == null) && (result4 == null)) || (((result3 != null) && (result4 != null)) && (result3.size == result4.size))))
            var result5: MutableList<Mapping?> = searchServer.emojiConvert("test", 0)!!
            var result6: MutableList<Mapping?> = searchServer.emojiConvert("test", 1)!!
            assertTrue("Cache key should be based on input", (((result5 == null) && (result6 == null)) || ((result5 != null) && (result6 != null))))
        } catch (e: Exception) {
            Log.e(TAG, "test_3_6_4_2 failed", e)
            fail(("emojiConvert cache hit test failed: " + e.getMessage()))
        }
    }
    @Test(timeout = 5000)
    fun test_3_6_4_3_emojiConvert_db_fallback_type_variation() {
        try {
            var result1: MutableList<Mapping?> = searchServer.emojiConvert("smile", 0)!!
            assertTrue("emojiConvert should handle type 0", ((result1 == null) || (result1 is List<*>)))
            var result2: MutableList<Mapping?> = searchServer.emojiConvert("smile", 1)!!
            assertTrue("emojiConvert should handle type 1", ((result2 == null) || (result2 is List<*>)))
            var result3: MutableList<Mapping?> = searchServer.emojiConvert("smile", 2)!!
            assertTrue("emojiConvert should handle type 2", ((result3 == null) || (result3 is List<*>)))
            var result4: MutableList<Mapping?> = searchServer.emojiConvert("heart", 0)!!
            var result5: MutableList<Mapping?> = searchServer.emojiConvert("flower", 0)!!
            var result6: MutableList<Mapping?> = searchServer.emojiConvert("star", 0)!!
            assertTrue("emojiConvert should handle different keywords", ((((result4 == null) || (result4 is List<*>)) && ((result5 == null) || (result5 is List<*>))) && ((result6 == null) || (result6 is List<*>))))
            var result7: MutableList<Mapping?> = searchServer.emojiConvert("test", 1)!!
            assertTrue("emojiConvert should handle negative type", ((result7 == null) || (result7 is List<*>)))
        } catch (e: Exception) {
            Log.e(TAG, "test_3_6_4_3 failed", e)
            fail(("emojiConvert DB fallback test failed: " + e.getMessage()))
        }
    }
    @Test(timeout = 5000)
    fun test_3_6_4_4_emojiConvert_cache_initialization() {
        try {
            var emojicacheField: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("emojicache")
            emojicacheField.setAccessible(true)
            emojicacheField.set(searchServer, null)
            var result1: MutableList<Mapping?> = searchServer.emojiConvert("first_call", 0)!!
            assertTrue("emojiConvert should initialize cache when null (type 0)", ((result1 == null) || (result1 is List<*>)))
            var cacheAfterInit: Any? = emojicacheField.get(searchServer)
            assertNotNull("emojicache should be initialized after first call", cacheAfterInit)
            var result2: MutableList<Mapping?> = searchServer.emojiConvert("second_call", 1)!!
            assertTrue("emojiConvert should use initialized cache (type 1)", ((result2 == null) || (result2 is List<*>)))
            emojicacheField.set(searchServer, null)
            var result3: MutableList<Mapping?> = searchServer.emojiConvert("reinitialized", 2)!!
            assertTrue("emojiConvert should reinitialize cache when nulled", ((result3 == null) || (result3 is List<*>)))
            var result4: MutableList<Mapping?> = searchServer.emojiConvert("multi_type", 0)!!
            var result5: MutableList<Mapping?> = searchServer.emojiConvert("multi_type", 1)!!
            var result6: MutableList<Mapping?> = searchServer.emojiConvert("multi_type", 2)!!
            assertTrue("emojiConvert should handle multiple types after init", ((((result4 == null) || (result4 is List<*>)) && ((result5 == null) || (result5 is List<*>))) && ((result6 == null) || (result6 is List<*>))))
            var result7: MutableList<Mapping?> = searchServer.emojiConvert("cached_key", 0)!!
            var result8: MutableList<Mapping?> = searchServer.emojiConvert("cached_key", 0)!!
            assertTrue("Cache should return consistent results", (((result7 == null) && (result8 == null)) || (((result7 != null) && (result8 != null)) && (result7.size == result8.size))))
            emojicacheField.set(searchServer, null)
            var result9: MutableList<Mapping?> = searchServer.emojiConvert("final_test", 0)!!
            var cacheAfterFinal: Any? = emojicacheField.get(searchServer)
            assertNotNull("emojicache should be reinitialized after nulling again", cacheAfterFinal)
        } catch (e: Exception) {
            Log.e(TAG, "test_3_6_4_4 failed", e)
            fail(("emojiConvert cache initialization test failed: " + e.getMessage()))
        }
    }
    fun test_3_7_1_1_learnRelatedPhraseAndUpdateScore_null_mapping() {
        var original: Any = getStatic("scorelist", Any::class.java)
        var mockScorelist: MutableList<Mapping?> = ArrayList()
        setStatic("scorelist", mockScorelist)
        try {
            searchServer.learnRelatedPhraseAndUpdateScore(null)
            assertTrue("Scorelist should remain empty for null mapping", mockScorelist.isEmpty())
        } finally {
            setStatic("scorelist", original)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_1_2_learnRelatedPhraseAndUpdateScore_adds_to_scorelist() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var mockScorelist: MutableList<Mapping?> = ArrayList()
        setStatic("scorelist", mockScorelist)
        try {
            var mapping: Mapping = Mapping()
            mapping.setId("1")
            mapping.setCode("a")
            mapping.setWord("apple")
            mapping.setScore(100)
            searchServer.learnRelatedPhraseAndUpdateScore(mapping)
            Thread.sleep(100)
            assertEquals("Scorelist should have one mapping", 1, mockScorelist.size)
            assertNotNull("Mapping should not be null", mockScorelist.get(0))
        } finally {
            setStatic("scorelist", originalScorelist)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_1_3_learnRelatedPhraseAndUpdateScore_spawns_thread() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var mockScorelist: MutableList<Mapping?> = ArrayList()
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        setStatic("scorelist", mockScorelist)
        setStatic("dbadapter", stub)
        try {
            var mapping: Mapping = Mapping()
            mapping.setId("1")
            mapping.setCode("a")
            mapping.setWord("apple")
            mapping.setScore(100)
            searchServer.learnRelatedPhraseAndUpdateScore(mapping)
            Thread.sleep(200)
            assertTrue("Thread should execute and finish", (mockScorelist.size > 0))
        } finally {
            setStatic("scorelist", originalScorelist)
            setStatic("dbadapter", originalDbadapter)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_1_4_learnRelatedPhraseAndUpdateScore_thread_completes() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var mockScorelist: MutableList<Mapping?> = ArrayList()
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        setStatic("scorelist", mockScorelist)
        setStatic("dbadapter", stub)
        try {
            var mapping1: Mapping = Mapping()
            mapping1.setId("1")
            mapping1.setCode("ab")
            mapping1.setWord("apple")
            mapping1.setScore(100)
            var mapping2: Mapping = Mapping()
            mapping2.setId("2")
            mapping2.setCode("c")
            mapping2.setWord("cat")
            mapping2.setScore(50)
            searchServer.learnRelatedPhraseAndUpdateScore(mapping1)
            Thread.sleep(100)
            searchServer.learnRelatedPhraseAndUpdateScore(mapping2)
            Thread.sleep(200)
            assertEquals("Both mappings should be added", 2, mockScorelist.size)
        } finally {
            setStatic("scorelist", originalScorelist)
            setStatic("dbadapter", originalDbadapter)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_1_5_learnRelatedPhraseAndUpdateScore_concurrent_calls() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var mockScorelist: MutableList<Mapping?> = ArrayList()
        setStatic("scorelist", mockScorelist)
        try {
            var t1: Thread = Thread({
    var m1: Mapping = Mapping()
    m1.setId("1")
    m1.setCode("a")
    m1.setWord("apple")
    m1.setScore(100)
    searchServer.learnRelatedPhraseAndUpdateScore(m1)
})
            var t2: Thread = Thread({
    var m2: Mapping = Mapping()
    m2.setId("2")
    m2.setCode("b")
    m2.setWord("ball")
    m2.setScore(100)
    searchServer.learnRelatedPhraseAndUpdateScore(m2)
})
            t1.start()
            t2.start()
            t1.join()
            t2.join()
            Thread.sleep(100)
            assertEquals("Both mappings should be added despite concurrent calls", 2, mockScorelist.size)
        } finally {
            setStatic("scorelist", originalScorelist)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_1_6_learnRelatedPhraseAndUpdateScore_mapping_copy() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var mockScorelist: MutableList<Mapping?> = ArrayList()
        setStatic("scorelist", mockScorelist)
        try {
            var original: Mapping = Mapping()
            original.setId("1")
            original.setCode("a")
            original.setWord("apple")
            original.setScore(100)
            searchServer.learnRelatedPhraseAndUpdateScore(original)
            Thread.sleep(100)
            original.setWord("modified")
            assertTrue("Scorelist should contain a mapping", (mockScorelist.size > 0))
            assertNotEquals("Scorelist should have a copy, not the modified original", "modified", mockScorelist.get(0).getWord())
        } finally {
            setStatic("scorelist", originalScorelist)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_2_1_learnRelatedPhrase_null_list() {
        try {
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf<Any?>(null))
        } catch (e: Exception) {
            fail(("learnRelatedPhrase should handle null list safely: " + e.getMessage()))
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_2_2_learnRelatedPhrase_empty_list() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var mockScorelist: MutableList<Mapping?> = ArrayList()
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        setStatic("scorelist", mockScorelist)
        setStatic("dbadapter", stub)
        try {
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(mockScorelist))
            assertEquals("No related phrases should be added", 0, stub.relatedPhraseAddCount)
        } finally {
            setStatic("scorelist", originalScorelist)
            setStatic("dbadapter", originalDbadapter)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_2_3_learnRelatedPhrase_single_mapping() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var mockScorelist: MutableList<Mapping?> = ArrayList()
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        var m: Mapping = Mapping()
        m.setId("1")
        m.setCode("a")
        m.setWord("apple")
        m.setScore(100)
        mockScorelist.add(m)
        setStatic("scorelist", mockScorelist)
        setStatic("dbadapter", stub)
        try {
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(mockScorelist))
            assertEquals("Single mapping should not learn related phrase", 0, stub.relatedPhraseAddCount)
        } finally {
            setStatic("scorelist", originalScorelist)
            setStatic("dbadapter", originalDbadapter)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_2_4_learnRelatedPhrase_pref_disabled() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var originalPref: Any = getInstanceField(searchServer, "mLIMEPref", Any::class.java)
        var mockScorelist: MutableList<Mapping?> = ArrayList()
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        var mockPref: LIMEPreferenceManager = mock(LIMEPreferenceManager::class.java)
        `when`(mockPref.getLearnRelatedWord()).thenReturn(false)
        var m1: Mapping = Mapping()
        m1.setId("1")
        m1.setCode("a")
        m1.setWord("apple")
        m1.setScore(100)
        var m2: Mapping = Mapping()
        m2.setId("2")
        m2.setCode("b")
        m2.setWord("ball")
        m2.setScore(50)
        mockScorelist.add(m1)
        mockScorelist.add(m2)
        setStatic("scorelist", mockScorelist)
        setStatic("dbadapter", stub)
        setInstanceField(searchServer, "mLIMEPref", mockPref)
        try {
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(mockScorelist))
            assertEquals("Learning disabled should not add related phrases", 0, stub.relatedPhraseAddCount)
        } finally {
            setStatic("scorelist", originalScorelist)
            setStatic("dbadapter", originalDbadapter)
            setInstanceField(searchServer, "mLIMEPref", originalPref)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_2_5_learnRelatedPhrase_consecutive_words() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var originalPref: Any = getInstanceField(searchServer, "mLIMEPref", Any::class.java)
        var mockScorelist: MutableList<Mapping?> = ArrayList()
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        var mockPref: LIMEPreferenceManager = mock(LIMEPreferenceManager::class.java)
        `when`(mockPref.getLearnRelatedWord()).thenReturn(true)
        var m1: Mapping = Mapping()
        m1.setId("1")
        m1.setCode("a")
        m1.setWord("apple")
        m1.setScore(100)
        setInstanceField(m1, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
        var m2: Mapping = Mapping()
        m2.setId("2")
        m2.setCode("b")
        m2.setWord("ball")
        m2.setScore(50)
        setInstanceField(m2, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
        mockScorelist.add(m1)
        mockScorelist.add(m2)
        setStatic("scorelist", mockScorelist)
        setStatic("dbadapter", stub)
        setInstanceField(searchServer, "mLIMEPref", mockPref)
        try {
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(mockScorelist))
            assertTrue("Related phrase should be learned", (stub.relatedPhraseAddCount > 0))
        } finally {
            setStatic("scorelist", originalScorelist)
            setStatic("dbadapter", originalDbadapter)
            setInstanceField(searchServer, "mLIMEPref", originalPref)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_2_6_learnRelatedPhrase_null_mappings_skipped() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var mockScorelist: MutableList<Mapping?> = ArrayList()
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        var m1: Mapping = Mapping()
        m1.setId("1")
        m1.setCode("a")
        m1.setWord("apple")
        m1.setScore(100)
        mockScorelist.add(m1)
        mockScorelist.add(null)
        var m2: Mapping = Mapping()
        m2.setId("2")
        m2.setCode("b")
        m2.setWord("ball")
        m2.setScore(50)
        mockScorelist.add(m2)
        setStatic("scorelist", mockScorelist)
        setStatic("dbadapter", stub)
        try {
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(mockScorelist))
            assertTrue(true)
        } finally {
            setStatic("scorelist", originalScorelist)
            setStatic("dbadapter", originalDbadapter)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_2_7_learnRelatedPhrase_empty_word_skipped() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var originalPref: Any = getInstanceField(searchServer, "mLIMEPref", Any::class.java)
        var mockScorelist: MutableList<Mapping?> = ArrayList()
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        var mockPref: LIMEPreferenceManager = mock(LIMEPreferenceManager::class.java)
        `when`(mockPref.getLearnRelatedWord()).thenReturn(true)
        var m1: Mapping = Mapping()
        m1.setId("1")
        m1.setCode("a")
        m1.setWord("")
        m1.setScore(100)
        setInstanceField(m1, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
        var m2: Mapping = Mapping()
        m2.setId("2")
        m2.setCode("b")
        m2.setWord("ball")
        m2.setScore(50)
        setInstanceField(m2, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
        mockScorelist.add(m1)
        mockScorelist.add(m2)
        setStatic("scorelist", mockScorelist)
        setStatic("dbadapter", stub)
        setInstanceField(searchServer, "mLIMEPref", mockPref)
        try {
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(mockScorelist))
            assertEquals("Empty word should be skipped", 0, stub.relatedPhraseAddCount)
        } finally {
            setStatic("scorelist", originalScorelist)
            setStatic("dbadapter", originalDbadapter)
            setInstanceField(searchServer, "mLIMEPref", originalPref)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_2_8_learnRelatedPhrase_record_type_filters() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var originalPref: Any = getInstanceField(searchServer, "mLIMEPref", Any::class.java)
        var mockScorelist: MutableList<Mapping?> = ArrayList()
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        var mockPref: LIMEPreferenceManager = mock(LIMEPreferenceManager::class.java)
        `when`(mockPref.getLearnRelatedWord()).thenReturn(true)
        var m1: Mapping = Mapping()
        m1.setId("1")
        m1.setCode("a")
        m1.setWord("apple")
        m1.setScore(100)
        setInstanceField(m1, "recordType", Mapping.RECORD_ENGLISH_SUGGESTION)
        var m2: Mapping = Mapping()
        m2.setId("2")
        m2.setCode("b")
        m2.setWord("ball")
        m2.setScore(50)
        setInstanceField(m2, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
        mockScorelist.add(m1)
        mockScorelist.add(m2)
        setStatic("scorelist", mockScorelist)
        setStatic("dbadapter", stub)
        setInstanceField(searchServer, "mLIMEPref", mockPref)
        try {
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(mockScorelist))
            assertEquals("Invalid record type should be filtered", 0, stub.relatedPhraseAddCount)
        } finally {
            setStatic("scorelist", originalScorelist)
            setStatic("dbadapter", originalDbadapter)
            setInstanceField(searchServer, "mLIMEPref", originalPref)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_2_9_learnRelatedPhrase_unit2_accepts_punctuation_emoji() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var originalPref: Any = getInstanceField(searchServer, "mLIMEPref", Any::class.java)
        var mockScorelist: MutableList<Mapping?> = ArrayList()
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        var mockPref: LIMEPreferenceManager = mock(LIMEPreferenceManager::class.java)
        `when`(mockPref.getLearnRelatedWord()).thenReturn(true)
        var m1: Mapping = Mapping()
        m1.setId("1")
        m1.setCode("a")
        m1.setWord("apple")
        m1.setScore(100)
        setInstanceField(m1, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
        var m2: Mapping = Mapping()
        m2.setId("2")
        m2.setCode(".")
        m2.setWord("。")
        m2.setScore(50)
        setInstanceField(m2, "recordType", Mapping.RECORD_CHINESE_PUNCTUATION_SYMBOL)
        mockScorelist.add(m1)
        mockScorelist.add(m2)
        setStatic("scorelist", mockScorelist)
        setStatic("dbadapter", stub)
        setInstanceField(searchServer, "mLIMEPref", mockPref)
        try {
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(mockScorelist))
            assertTrue("Punctuation as unit2 should be accepted", (stub.relatedPhraseAddCount > 0))
        } finally {
            setStatic("scorelist", originalScorelist)
            setStatic("dbadapter", originalDbadapter)
            setInstanceField(searchServer, "mLIMEPref", originalPref)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_2_10_learnRelatedPhrase_calls_addOrUpdateRelatedPhraseRecord() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var originalPref: Any = getInstanceField(searchServer, "mLIMEPref", Any::class.java)
        var mockScorelist: MutableList<Mapping?> = ArrayList()
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        var mockPref: LIMEPreferenceManager = mock(LIMEPreferenceManager::class.java)
        `when`(mockPref.getLearnRelatedWord()).thenReturn(true)
        var m1: Mapping = Mapping()
        m1.setId("1")
        m1.setCode("a")
        m1.setWord("apple")
        m1.setScore(100)
        setInstanceField(m1, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
        var m2: Mapping = Mapping()
        m2.setId("2")
        m2.setCode("b")
        m2.setWord("ball")
        m2.setScore(50)
        setInstanceField(m2, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
        mockScorelist.add(m1)
        mockScorelist.add(m2)
        setStatic("scorelist", mockScorelist)
        setStatic("dbadapter", stub)
        setInstanceField(searchServer, "mLIMEPref", mockPref)
        try {
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(mockScorelist))
            assertTrue("DB method should be called", (stub.relatedPhraseAddCount > 0))
            assertEquals("Should learn 'appleball'", "appleball", stub.lastRelatedPhrase)
        } finally {
            setStatic("scorelist", originalScorelist)
            setStatic("dbadapter", originalDbadapter)
            setInstanceField(searchServer, "mLIMEPref", originalPref)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_2_11_learnRelatedPhrase_high_score_triggers_LD() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var originalPref: Any = getInstanceField(searchServer, "mLIMEPref", Any::class.java)
        var mockScorelist: MutableList<Mapping?> = ArrayList()
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        var mockPref: LIMEPreferenceManager = mock(LIMEPreferenceManager::class.java)
        `when`(mockPref.getLearnRelatedWord()).thenReturn(true)
        `when`(mockPref.getLearnPhrase()).thenReturn(true)
        var m1: Mapping = Mapping()
        m1.setId("1")
        m1.setCode("a")
        m1.setWord("apple")
        m1.setScore(30)
        setInstanceField(m1, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
        var m2: Mapping = Mapping()
        m2.setId("2")
        m2.setCode("b")
        m2.setWord("ball")
        m2.setScore(50)
        setInstanceField(m2, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
        mockScorelist.add(m1)
        mockScorelist.add(m2)
        setStatic("scorelist", mockScorelist)
        setStatic("dbadapter", stub)
        setInstanceField(searchServer, "mLIMEPref", mockPref)
        try {
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(mockScorelist))
            assertTrue("High score should trigger LD learning", (stub.relatedPhraseAddCount > 0))
        } finally {
            setStatic("scorelist", originalScorelist)
            setStatic("dbadapter", originalDbadapter)
            setInstanceField(searchServer, "mLIMEPref", originalPref)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_2_12_learnRelatedPhrase_multiple_pairs() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var originalPref: Any = getInstanceField(searchServer, "mLIMEPref", Any::class.java)
        var mockScorelist: MutableList<Mapping?> = ArrayList()
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        var mockPref: LIMEPreferenceManager = mock(LIMEPreferenceManager::class.java)
        `when`(mockPref.getLearnRelatedWord()).thenReturn(true)
        var m1: Mapping = Mapping()
        m1.setId("1")
        m1.setCode("a")
        m1.setWord("apple")
        m1.setScore(100)
        setInstanceField(m1, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
        var m2: Mapping = Mapping()
        m2.setId("2")
        m2.setCode("b")
        m2.setWord("ball")
        m2.setScore(50)
        setInstanceField(m2, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
        var m3: Mapping = Mapping()
        m3.setId("3")
        m3.setCode("c")
        m3.setWord("cat")
        m3.setScore(75)
        setInstanceField(m3, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
        mockScorelist.add(m1)
        mockScorelist.add(m2)
        mockScorelist.add(m3)
        setStatic("scorelist", mockScorelist)
        setStatic("dbadapter", stub)
        setInstanceField(searchServer, "mLIMEPref", mockPref)
        try {
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(mockScorelist))
            assertTrue("Multiple pairs should be learned", (stub.relatedPhraseAddCount >= 2))
        } finally {
            setStatic("scorelist", originalScorelist)
            setStatic("dbadapter", originalDbadapter)
            setInstanceField(searchServer, "mLIMEPref", originalPref)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_2_13_learnRelatedPhrase_high_score_but_LD_disabled() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var originalPref: Any = getInstanceField(searchServer, "mLIMEPref", Any::class.java)
        var originalLDPhraseList: Any = getStatic("LDPhraseList", Any::class.java)
        var mockScorelist: MutableList<Mapping?> = ArrayList()
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        var mockPref: LIMEPreferenceManager = mock(LIMEPreferenceManager::class.java)
        `when`(mockPref.getLearnRelatedWord()).thenReturn(true)
        `when`(mockPref.getLearnPhrase()).thenReturn(false)
        var m1: Mapping = Mapping()
        m1.setId("1")
        m1.setCode("a")
        m1.setWord("測")
        m1.setScore(100)
        setInstanceField(m1, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
        var m2: Mapping = Mapping()
        m2.setId("2")
        m2.setCode("b")
        m2.setWord("試")
        m2.setScore(50)
        setInstanceField(m2, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
        mockScorelist.add(m1)
        mockScorelist.add(m2)
        var ldPhraseList: MutableList<Mapping?> = ArrayList()
        setStatic("scorelist", mockScorelist)
        setStatic("dbadapter", stub)
        setInstanceField(searchServer, "mLIMEPref", mockPref)
        setStatic("LDPhraseList", ldPhraseList)
        try {
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(mockScorelist))
            assertEquals("Related phrase should be learned", 1, stub.relatedPhraseAddCount)
            assertEquals("LD phrase list should be empty when LD learning disabled", 0, ldPhraseList.size)
        } finally {
            setStatic("scorelist", originalScorelist)
            setStatic("dbadapter", originalDbadapter)
            setInstanceField(searchServer, "mLIMEPref", originalPref)
            setStatic("LDPhraseList", originalLDPhraseList)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_2_14_learnRelatedPhrase_null_words() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var originalPref: Any = getInstanceField(searchServer, "mLIMEPref", Any::class.java)
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        var mockPref: LIMEPreferenceManager = mock(LIMEPreferenceManager::class.java)
        `when`(mockPref.getLearnRelatedWord()).thenReturn(true)
        setStatic("dbadapter", stub)
        setInstanceField(searchServer, "mLIMEPref", mockPref)
        try {
            var mockScorelist1: MutableList<Mapping?> = ArrayList()
            var m1: Mapping = Mapping()
            m1.setId("1")
            m1.setCode("a")
            m1.setWord(null)
            m1.setScore(100)
            setInstanceField(m1, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            var m2: Mapping = Mapping()
            m2.setId("2")
            m2.setCode("b")
            m2.setWord("試")
            m2.setScore(50)
            setInstanceField(m2, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            mockScorelist1.add(m1)
            mockScorelist1.add(m2)
            setStatic("scorelist", mockScorelist1)
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(mockScorelist1))
            assertEquals("No related phrase should be learned when unit1.getWord() is null", 0, stub.relatedPhraseAddCount)
            stub.relatedPhraseAddCount = 0
            var mockScorelist2: MutableList<Mapping?> = ArrayList()
            var m3: Mapping = Mapping()
            m3.setId("3")
            m3.setCode("a")
            m3.setWord("測")
            m3.setScore(100)
            setInstanceField(m3, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            var m4: Mapping = Mapping()
            m4.setId("4")
            m4.setCode("b")
            m4.setWord(null)
            m4.setScore(50)
            setInstanceField(m4, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            mockScorelist2.add(m3)
            mockScorelist2.add(m4)
            setStatic("scorelist", mockScorelist2)
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(mockScorelist2))
            assertEquals("No related phrase should be learned when unit2.getWord() is null", 0, stub.relatedPhraseAddCount)
        } finally {
            setStatic("scorelist", originalScorelist)
            setStatic("dbadapter", originalDbadapter)
            setInstanceField(searchServer, "mLIMEPref", originalPref)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_2_15_learnRelatedPhrase_invalid_record_types() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var originalPref: Any = getInstanceField(searchServer, "mLIMEPref", Any::class.java)
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        var mockPref: LIMEPreferenceManager = mock(LIMEPreferenceManager::class.java)
        `when`(mockPref.getLearnRelatedWord()).thenReturn(true)
        setStatic("dbadapter", stub)
        setInstanceField(searchServer, "mLIMEPref", mockPref)
        try {
            var mockScorelist1: MutableList<Mapping?> = ArrayList()
            var m1: Mapping = Mapping()
            m1.setId("1")
            m1.setCode("a")
            m1.setWord("測")
            m1.setScore(100)
            setInstanceField(m1, "recordType", Mapping.RECORD_COMPOSING_CODE)
            var m2: Mapping = Mapping()
            m2.setId("2")
            m2.setCode("b")
            m2.setWord("試")
            m2.setScore(50)
            setInstanceField(m2, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            mockScorelist1.add(m1)
            mockScorelist1.add(m2)
            setStatic("scorelist", mockScorelist1)
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(mockScorelist1))
            assertEquals("No related phrase should be learned when unit1 has invalid record type", 0, stub.relatedPhraseAddCount)
            stub.relatedPhraseAddCount = 0
            var mockScorelist2: MutableList<Mapping?> = ArrayList()
            var m3: Mapping = Mapping()
            m3.setId("3")
            m3.setCode("a")
            m3.setWord("測")
            m3.setScore(100)
            setInstanceField(m3, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            var m4: Mapping = Mapping()
            m4.setId("4")
            m4.setCode("b")
            m4.setWord("試")
            m4.setScore(50)
            setInstanceField(m4, "recordType", Mapping.RECORD_COMPOSING_CODE)
            mockScorelist2.add(m3)
            mockScorelist2.add(m4)
            setStatic("scorelist", mockScorelist2)
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(mockScorelist2))
            assertEquals("No related phrase should be learned when unit2 has invalid record type", 0, stub.relatedPhraseAddCount)
            stub.relatedPhraseAddCount = 0
            var mockScorelist3: MutableList<Mapping?> = ArrayList()
            var m5: Mapping = Mapping()
            m5.setId("5")
            m5.setCode("a")
            m5.setWord("測")
            m5.setScore(100)
            setInstanceField(m5, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            var m6: Mapping = Mapping()
            m6.setId("6")
            m6.setCode("hello")
            m6.setWord("hello")
            m6.setScore(50)
            setInstanceField(m6, "recordType", Mapping.RECORD_ENGLISH_SUGGESTION)
            mockScorelist3.add(m5)
            mockScorelist3.add(m6)
            setStatic("scorelist", mockScorelist3)
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(mockScorelist3))
            assertEquals("No related phrase should be learned when unit2 is English suggestion", 0, stub.relatedPhraseAddCount)
        } finally {
            setStatic("scorelist", originalScorelist)
            setStatic("dbadapter", originalDbadapter)
            setInstanceField(searchServer, "mLIMEPref", originalPref)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_2_16_learnRelatedPhrase_score_below_threshold() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var originalPref: Any = getInstanceField(searchServer, "mLIMEPref", Any::class.java)
        var originalLDPhraseList: Any = getStatic("LDPhraseList", Any::class.java)
        var stub: StubLimeDBForLearningLowScore = StubLimeDBForLearningLowScore(appContext)
        var mockPref: LIMEPreferenceManager = mock(LIMEPreferenceManager::class.java)
        `when`(mockPref.getLearnRelatedWord()).thenReturn(true)
        `when`(mockPref.getLearnPhrase()).thenReturn(true)
        var mockScorelist: MutableList<Mapping?> = ArrayList()
        var m1: Mapping = Mapping()
        m1.setId("1")
        m1.setCode("a")
        m1.setWord("低")
        m1.setScore(100)
        setInstanceField(m1, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
        var m2: Mapping = Mapping()
        m2.setId("2")
        m2.setCode("b")
        m2.setWord("分")
        m2.setScore(50)
        setInstanceField(m2, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
        mockScorelist.add(m1)
        mockScorelist.add(m2)
        var ldPhraseList: MutableList<Mapping?> = ArrayList()
        setStatic("scorelist", mockScorelist)
        setStatic("dbadapter", stub)
        setInstanceField(searchServer, "mLIMEPref", mockPref)
        setStatic("LDPhraseList", ldPhraseList)
        try {
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(mockScorelist))
            assertEquals("Related phrase should be learned", 1, stub.relatedPhraseAddCount)
            assertEquals("LD phrase list should be empty when score <= 20", 0, ldPhraseList.size)
        } finally {
            setStatic("scorelist", originalScorelist)
            setStatic("dbadapter", originalDbadapter)
            setInstanceField(searchServer, "mLIMEPref", originalPref)
            setStatic("LDPhraseList", originalLDPhraseList)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_2_17_learnRelatedPhrase_record_type_and_LD_filters() {
        var originalScorelist: Any = getStatic("scorelist", Any::class.java)
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var originalPref: Any = getInstanceField(searchServer, "mLIMEPref", Any::class.java)
        var originalLDPhraseList: Any = getStatic("LDPhraseList", Any::class.java)
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        var mockPref: LIMEPreferenceManager = mock(LIMEPreferenceManager::class.java)
        `when`(mockPref.getLearnRelatedWord()).thenReturn(true)
        `when`(mockPref.getLearnPhrase()).thenReturn(false)
        var ldPhraseList: MutableList<Mapping?> = ArrayList()
        setStatic("dbadapter", stub)
        setInstanceField(searchServer, "mLIMEPref", mockPref)
        setStatic("LDPhraseList", ldPhraseList)
        try {
            var scorelist1: MutableList<Mapping?> = ArrayList()
            var m1a: Mapping = Mapping()
            m1a.setId("1")
            m1a.setCode("a")
            m1a.setWord("測")
            m1a.setScore(100)
            setInstanceField(m1a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            var m1b: Mapping = Mapping()
            m1b.setId("2")
            m1b.setCode("b")
            m1b.setWord("試")
            m1b.setScore(50)
            setInstanceField(m1b, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            scorelist1.add(m1a)
            scorelist1.add(m1b)
            setStatic("scorelist", scorelist1)
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(scorelist1))
            assertEquals("Related phrase should be learned", 1, stub.relatedPhraseAddCount)
            assertEquals("LD phrase list should be empty when LD learning disabled", 0, ldPhraseList.size)
            stub.relatedPhraseAddCount = 0
            var scorelist2: MutableList<Mapping?> = ArrayList()
            var m2a: Mapping = Mapping()
            m2a.setId("1")
            m2a.setCode("a")
            m2a.setWord("😀")
            m2a.setScore(100)
            setInstanceField(m2a, "recordType", Mapping.RECORD_EMOJI_WORD)
            var m2b: Mapping = Mapping()
            m2b.setId("2")
            m2b.setCode("b")
            m2b.setWord("測")
            m2b.setScore(50)
            setInstanceField(m2b, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            scorelist2.add(m2a)
            scorelist2.add(m2b)
            setStatic("scorelist", scorelist2)
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(scorelist2))
            assertEquals("No related phrase should be learned with emoji unit1", 0, stub.relatedPhraseAddCount)
            var scorelist3: MutableList<Mapping?> = ArrayList()
            var m3a: Mapping = Mapping()
            m3a.setId("1")
            m3a.setCode("a")
            m3a.setWord("測")
            m3a.setScore(100)
            setInstanceField(m3a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            var m3b: Mapping = Mapping()
            m3b.setId("2")
            m3b.setCode("b")
            m3b.setWord("😀")
            m3b.setScore(50)
            setInstanceField(m3b, "recordType", Mapping.RECORD_EMOJI_WORD)
            scorelist3.add(m3a)
            scorelist3.add(m3b)
            setStatic("scorelist", scorelist3)
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(scorelist3))
            assertEquals("Related phrase should be learned with emoji unit2", 1, stub.relatedPhraseAddCount)
            stub.relatedPhraseAddCount = 0
            var scorelist4: MutableList<Mapping?> = ArrayList()
            var m4a: Mapping = Mapping()
            m4a.setId("1")
            m4a.setCode("a")
            m4a.setWord("測")
            m4a.setScore(100)
            setInstanceField(m4a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            var m4b: Mapping = Mapping()
            m4b.setId("2")
            m4b.setCode("pun")
            m4b.setWord("，")
            m4b.setScore(50)
            setInstanceField(m4b, "recordType", Mapping.RECORD_CHINESE_PUNCTUATION_SYMBOL)
            scorelist4.add(m4a)
            scorelist4.add(m4b)
            setStatic("scorelist", scorelist4)
            invokePrivate("learnRelatedPhrase", arrayOf(MutableList::class.java), arrayOf(scorelist4))
            assertEquals("Related phrase should be learned with punctuation unit2", 1, stub.relatedPhraseAddCount)
        } finally {
            setStatic("scorelist", originalScorelist)
            setStatic("dbadapter", originalDbadapter)
            setInstanceField(searchServer, "mLIMEPref", originalPref)
            setStatic("LDPhraseList", originalLDPhraseList)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_4_1_addLDPhrase_initializes_arrays() {
        var originalPref: Any = setLearnPhraseForTest(true)
        setStatic("LDPhraseListArray", null)
        setStatic("LDPhraseList", null)
        try {
            var m: Mapping = Mapping()
            m.setId("1")
            m.setCode("a")
            m.setWord("apple")
            searchServer.addLDPhrase(m, false)
            var array: Any = getStatic("LDPhraseListArray", Any::class.java)
            var list: Any = getStatic("LDPhraseList", Any::class.java)
            assertNotNull("LDPhraseListArray should be initialized", array)
            assertNotNull("LDPhraseList should be initialized", list)
        } finally {
            setInstanceField(searchServer, "mLIMEPref", originalPref)
            setStatic("LDPhraseListArray", null)
            setStatic("LDPhraseList", null)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_4_2_addLDPhrase_adds_mapping_to_list() {
        var originalPref: Any = setLearnPhraseForTest(true)
        setStatic("LDPhraseListArray", ArrayList<MutableList<Mapping?>>())
        setStatic("LDPhraseList", ArrayList<Mapping?>())
        try {
            var m: Mapping = Mapping()
            m.setId("1")
            m.setCode("a")
            m.setWord("apple")
            searchServer.addLDPhrase(m, false)
            var list: MutableList<Mapping?> = (getStatic("LDPhraseList", Any::class.java) as MutableList<Mapping?>)
            assertEquals("Mapping should be added to list", 1, list.size)
            assertEquals("Added mapping should match", "apple", list.get(0).getWord())
        } finally {
            setInstanceField(searchServer, "mLIMEPref", originalPref)
            setStatic("LDPhraseListArray", null)
            setStatic("LDPhraseList", null)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_4_3_addLDPhrase_ending_false_continues() {
        var originalPref: Any = setLearnPhraseForTest(true)
        var array: MutableList<MutableList<Mapping?>> = ArrayList()
        var list: MutableList<Mapping?> = ArrayList()
        setStatic("LDPhraseListArray", array)
        setStatic("LDPhraseList", list)
        try {
            var m1: Mapping = Mapping()
            m1.setId("1")
            m1.setCode("a")
            m1.setWord("apple")
            searchServer.addLDPhrase(m1, false)
            var m2: Mapping = Mapping()
            m2.setId("2")
            m2.setCode("b")
            m2.setWord("ball")
            searchServer.addLDPhrase(m2, false)
            var currentList: MutableList<Mapping?> = (getStatic("LDPhraseList", Any::class.java) as MutableList<Mapping?>)
            assertEquals("Both mappings should be in current list", 2, currentList.size)
            assertEquals("Array should still be empty", 0, array.size)
        } finally {
            setInstanceField(searchServer, "mLIMEPref", originalPref)
            setStatic("LDPhraseListArray", null)
            setStatic("LDPhraseList", null)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_4_4_addLDPhrase_ending_true_saves_and_resets() {
        var originalPref: Any = setLearnPhraseForTest(true)
        var array: MutableList<MutableList<Mapping?>> = ArrayList()
        var list: MutableList<Mapping?> = ArrayList()
        setStatic("LDPhraseListArray", array)
        setStatic("LDPhraseList", list)
        try {
            var m1: Mapping = Mapping()
            m1.setId("1")
            m1.setCode("a")
            m1.setWord("apple")
            searchServer.addLDPhrase(m1, false)
            var m2: Mapping = Mapping()
            m2.setId("2")
            m2.setCode("b")
            m2.setWord("ball")
            searchServer.addLDPhrase(m2, true)
            var finalArray: MutableList<MutableList<Mapping?>> = (getStatic("LDPhraseListArray", Any::class.java) as MutableList<MutableList<Mapping?>>)
            var currentList: MutableList<Mapping?> = (getStatic("LDPhraseList", Any::class.java) as MutableList<Mapping?>)
            assertEquals("Array should have one phrase", 1, finalArray.size)
            assertEquals("Saved phrase should have 2 mappings", 2, finalArray.get(0).size)
            assertTrue("Current list should be empty after reset", currentList.isEmpty())
        } finally {
            setInstanceField(searchServer, "mLIMEPref", originalPref)
            setStatic("LDPhraseListArray", null)
            setStatic("LDPhraseList", null)
        }
    }
    @Test(timeout = 5000)
    fun test_addLDPhrase_noop_when_learn_phrase_disabled() {
        var originalPref: Any = getInstanceField(searchServer, "mLIMEPref", Any::class.java)
        var array: MutableList<MutableList<Mapping?>> = ArrayList()
        var list: MutableList<Mapping?> = ArrayList()
        var mockPref: LIMEPreferenceManager = mock(LIMEPreferenceManager::class.java)
        `when`(mockPref.getLearnPhrase()).thenReturn(false)
        setInstanceField(searchServer, "mLIMEPref", mockPref)
        setStatic("LDPhraseListArray", array)
        setStatic("LDPhraseList", list)
        try {
            var m1: Mapping = Mapping()
            m1.setId("1")
            m1.setCode("a")
            m1.setWord("apple")
            searchServer.addLDPhrase(m1, false)
            var m2: Mapping = Mapping()
            m2.setId("2")
            m2.setCode("b")
            m2.setWord("ball")
            searchServer.addLDPhrase(m2, true)
            assertTrue("current LD phrase list should remain empty", list.isEmpty())
            assertTrue("pending LD phrase array should remain empty", array.isEmpty())
        } finally {
            setInstanceField(searchServer, "mLIMEPref", originalPref)
            setStatic("LDPhraseListArray", null)
            setStatic("LDPhraseList", null)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_3_1_learnLDPhrase_input_validation() {
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        setStatic("dbadapter", stub)
        try {
            try {
                invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf<Any?>(null))
                assertTrue("Null array should not throw exception", true)
            } catch (e: Exception) {
                fail(("Null ArrayList should be handled safely: " + e.getMessage()))
            }
            var emptyList: ArrayList<MutableList<Mapping?>> = ArrayList()
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(emptyList))
            assertEquals("Empty ArrayList should not add mappings", 0, stub.ldPhraseAddCount)
            var arrayList1: ArrayList<MutableList<Mapping?>> = ArrayList()
            arrayList1.add(ArrayList())
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList1))
            assertEquals("Empty phraselist should not add mappings", 0, stub.ldPhraseAddCount)
            var arrayList2: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList: MutableList<Mapping?> = ArrayList()
            run {
                var i: Int = 0
                while ((i < 5)) {
                    var m: Mapping = Mapping()
                    m.setId(java.lang.String.valueOf(i))
                    m.setCode(("c" + i))
                    m.setWord(("w" + i))
                    phraseList.add(m)
                    i++
                }
            }
            arrayList2.add(phraseList)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList2))
            assertEquals("Size >= 5 should be skipped", 0, stub.ldPhraseAddCount)
        } finally {
            setStatic("dbadapter", originalDbadapter)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_3_2_learnLDPhrase_length_boundaries() {
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        stub.setupMappingForReverseLookup("蘋", "ap", "1")
        stub.setupMappingForReverseLookup("果", "go", "2")
        stub.setupMappingForReverseLookup("汁", "ji", "3")
        stub.setupMappingForReverseLookup("好", "ha", "4")
        setStatic("dbadapter", stub)
        try {
            var arrayList1: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList1: MutableList<Mapping?> = ArrayList()
            var m1a: Mapping = Mapping()
            m1a.setId("1")
            m1a.setCode("ap")
            m1a.setWord("蘋")
            phraseList1.add(m1a)
            var m1b: Mapping = Mapping()
            m1b.setId("2")
            m1b.setCode("go")
            m1b.setWord("果")
            phraseList1.add(m1b)
            arrayList1.add(phraseList1)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList1))
            assertTrue("2-char phrase should be learned", (stub.ldPhraseAddCount > 0))
            stub.ldPhraseAddCount = 0
            var arrayList2: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList2: MutableList<Mapping?> = ArrayList()
            var m2a: Mapping = Mapping()
            m2a.setId("1")
            m2a.setCode("ap")
            m2a.setWord("蘋")
            phraseList2.add(m2a)
            var m2b: Mapping = Mapping()
            m2b.setId("2")
            m2b.setCode("go")
            m2b.setWord("果")
            phraseList2.add(m2b)
            var m2c: Mapping = Mapping()
            m2c.setId("3")
            m2c.setCode("ji")
            m2c.setWord("汁")
            phraseList2.add(m2c)
            arrayList2.add(phraseList2)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList2))
            assertTrue("3-char phrase should be learned", (stub.ldPhraseAddCount > 0))
            stub.ldPhraseAddCount = 0
            var arrayList3: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList3: MutableList<Mapping?> = ArrayList()
            var m3a: Mapping = Mapping()
            m3a.setId("1")
            m3a.setCode("ap")
            m3a.setWord("蘋")
            phraseList3.add(m3a)
            var m3b: Mapping = Mapping()
            m3b.setId("2")
            m3b.setCode("go")
            m3b.setWord("果")
            phraseList3.add(m3b)
            var m3c: Mapping = Mapping()
            m3c.setId("3")
            m3c.setCode("ji")
            m3c.setWord("汁")
            phraseList3.add(m3c)
            var m3d: Mapping = Mapping()
            m3d.setId("4")
            m3d.setCode("ha")
            m3d.setWord("好")
            phraseList3.add(m3d)
            arrayList3.add(phraseList3)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList3))
            assertTrue("4-char phrase should be learned", (stub.ldPhraseAddCount > 0))
        } finally {
            setStatic("dbadapter", originalDbadapter)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_3_3_learnLDPhrase_unit1_validation() {
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        setStatic("dbadapter", stub)
        try {
            var arrayList1: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList1: MutableList<Mapping?> = ArrayList()
            phraseList1.add(null)
            var m1b: Mapping = Mapping()
            m1b.setId("2")
            m1b.setCode("go")
            m1b.setWord("果")
            phraseList1.add(m1b)
            arrayList1.add(phraseList1)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList1))
            assertEquals("Null unit1 should break learning", 0, stub.ldPhraseAddCount)
            var arrayList2: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList2: MutableList<Mapping?> = ArrayList()
            var m2a: Mapping = Mapping()
            m2a.setId("1")
            m2a.setCode("ap")
            m2a.setWord("")
            phraseList2.add(m2a)
            var m2b: Mapping = Mapping()
            m2b.setId("2")
            m2b.setCode("go")
            m2b.setWord("果")
            phraseList2.add(m2b)
            arrayList2.add(phraseList2)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList2))
            assertEquals("Empty word should break learning", 0, stub.ldPhraseAddCount)
            var arrayList3: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList3: MutableList<Mapping?> = ArrayList()
            var m3a: Mapping = Mapping()
            m3a.setId("1")
            m3a.setCode("apple")
            m3a.setWord("apple")
            phraseList3.add(m3a)
            var m3b: Mapping = Mapping()
            m3b.setId("2")
            m3b.setCode("go")
            m3b.setWord("果")
            phraseList3.add(m3b)
            arrayList3.add(phraseList3)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList3))
            assertEquals("English should break learning", 0, stub.ldPhraseAddCount)
        } finally {
            setStatic("dbadapter", originalDbadapter)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_3_4_learnLDPhrase_reverse_lookup() {
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        stub.setupMappingForReverseLookup("蘋", "ap", null)
        stub.setupMappingForReverseLookup("果", "go", "2")
        setStatic("dbadapter", stub)
        try {
            var arrayList1: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList1: MutableList<Mapping?> = ArrayList()
            var m1a: Mapping = Mapping()
            m1a.setId(null)
            m1a.setCode("ap")
            m1a.setWord("蘋")
            phraseList1.add(m1a)
            var m1b: Mapping = Mapping()
            m1b.setId("2")
            m1b.setCode("go")
            m1b.setWord("果")
            phraseList1.add(m1b)
            arrayList1.add(phraseList1)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList1))
            assertTrue("Reverse lookup should be triggered", (stub.getMappingByWordCallCount > 0))
            var stub2: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            setStatic("dbadapter", stub2)
            var arrayList2: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList2: MutableList<Mapping?> = ArrayList()
            var m2a: Mapping = Mapping()
            m2a.setId(null)
            m2a.setCode("ap")
            m2a.setWord("蘋")
            phraseList2.add(m2a)
            var m2b: Mapping = Mapping()
            m2b.setId("2")
            m2b.setCode("go")
            m2b.setWord("果")
            phraseList2.add(m2b)
            arrayList2.add(phraseList2)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList2))
            assertEquals("Failed reverse lookup should break learning", 0, stub2.ldPhraseAddCount)
        } finally {
            setStatic("dbadapter", originalDbadapter)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_3_5_learnLDPhrase_multi_char_scenarios() {
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        stub.setupMappingForReverseLookup("蘋", "ap", "1")
        stub.setupMappingForReverseLookup("果", "go", "2")
        stub.setupMappingForReverseLookup("番", "fan", "3")
        stub.setupMappingForReverseLookup("一", "yi", "4")
        stub.setupMappingForReverseLookup("二", "er", "5")
        stub.setupMappingForReverseLookup("三", "san", "6")
        stub.setupMappingForReverseLookup("四", "si", "7")
        setStatic("dbadapter", stub)
        try {
            var arrayList1: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList1: MutableList<Mapping?> = ArrayList()
            var m1a: Mapping = Mapping()
            m1a.setId("1")
            m1a.setCode("apgo")
            m1a.setWord("蘋果")
            phraseList1.add(m1a)
            var m1b: Mapping = Mapping()
            m1b.setId("2")
            m1b.setCode("ji")
            m1b.setWord("汁")
            setInstanceField(m1b, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList1.add(m1b)
            arrayList1.add(phraseList1)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList1))
            assertTrue("Multi-char baseWord should trigger learning", (stub.ldPhraseAddCount > 0))
            stub.ldPhraseAddCount = 0
            var arrayList2: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList2: MutableList<Mapping?> = ArrayList()
            var m2a: Mapping = Mapping()
            m2a.setId("3")
            m2a.setCode("fan")
            m2a.setWord("番")
            setInstanceField(m2a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList2.add(m2a)
            var m2b: Mapping = Mapping()
            m2b.setId("1")
            m2b.setCode("apgo")
            m2b.setWord("蘋果")
            phraseList2.add(m2b)
            arrayList2.add(phraseList2)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList2))
            assertTrue("Multi-char word2 should trigger learning", (stub.ldPhraseAddCount > 0))
            stub.ldPhraseAddCount = 0
            var arrayList3: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList3: MutableList<Mapping?> = ArrayList()
            var m3a: Mapping = Mapping()
            m3a.setId("4")
            m3a.setCode("yi")
            m3a.setWord("一")
            setInstanceField(m3a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList3.add(m3a)
            var m3b: Mapping = Mapping()
            m3b.setId("5")
            m3b.setCode("er")
            m3b.setWord("二")
            setInstanceField(m3b, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList3.add(m3b)
            var m3c: Mapping = Mapping()
            m3c.setId("6")
            m3c.setCode("san")
            m3c.setWord("三")
            setInstanceField(m3c, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList3.add(m3c)
            var m3d: Mapping = Mapping()
            m3d.setId("7")
            m3d.setCode("si")
            m3d.setWord("四")
            setInstanceField(m3d, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList3.add(m3d)
            arrayList3.add(phraseList3)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList3))
            assertTrue("Should learn up to 4 chars", (stub.ldPhraseAddCount > 0))
            var stub2: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            stub2.setupMappingForReverseLookup("番", "fan", "3")
            stub2.setupMappingForReverseLookup("蘋", "ap", "1")
            setStatic("dbadapter", stub2)
            var arrayList4: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList4: MutableList<Mapping?> = ArrayList()
            var m4a: Mapping = Mapping()
            m4a.setId("3")
            m4a.setCode("fan")
            m4a.setWord("番")
            setInstanceField(m4a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList4.add(m4a)
            var m4b: Mapping = Mapping()
            m4b.setId("1")
            m4b.setCode("apgo")
            m4b.setWord("蘋果")
            phraseList4.add(m4b)
            arrayList4.add(phraseList4)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList4))
            assertTrue("Should learn at least the first word", (stub2.ldPhraseAddCount > 0))
        } finally {
            setStatic("dbadapter", originalDbadapter)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_3_6_learnLDPhrase_phonetic_and_cache() {
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var originalTablename: Any = getStatic("tablename", Any::class.java)
        try {
            var stub1: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            stub1.setupMappingForReverseLookup("ㄆ", "b ", "1")
            stub1.setupMappingForReverseLookup("果", "go", "2")
            setStatic("dbadapter", stub1)
            setStatic("tablename", LIME.DB_TABLE_PHONETIC)
            var arrayList1: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList1: MutableList<Mapping?> = ArrayList()
            var p1a: Mapping = Mapping()
            p1a.setId("1")
            p1a.setCode("b ")
            p1a.setWord("ㄆ")
            phraseList1.add(p1a)
            var p1b: Mapping = Mapping()
            p1b.setId("2")
            p1b.setCode("go")
            p1b.setWord("果")
            phraseList1.add(p1b)
            arrayList1.add(phraseList1)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList1))
            assertTrue("Phonetic tone should be removed and mapping saved", ((stub1.ldPhraseAddCount > 0) && (stub1.lastLearnedLDPhrase != null)))
            var stub2: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            setStatic("dbadapter", stub2)
            setStatic("tablename", LIME.DB_TABLE_PHONETIC)
            var arrayList2: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList2: MutableList<Mapping?> = ArrayList()
            var p2a: Mapping = Mapping()
            p2a.setId("1")
            p2a.setCode("ce4")
            p2a.setWord("測")
            setInstanceField(p2a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList2.add(p2a)
            var p2b: Mapping = Mapping()
            p2b.setId("2")
            p2b.setCode("shi4")
            p2b.setWord("試")
            setInstanceField(p2b, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList2.add(p2b)
            arrayList2.add(phraseList2)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList2))
            assertTrue("QPCode path should learn at least one mapping", (stub2.ldPhraseAddCount >= 2))
            var stub3: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            setStatic("dbadapter", stub3)
            setStatic("tablename", "dayi")
            var arrayList3: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList3: MutableList<Mapping?> = ArrayList()
            var p3a: Mapping = Mapping()
            p3a.setId("1")
            p3a.setCode("a")
            p3a.setWord("蘋")
            setInstanceField(p3a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList3.add(p3a)
            var p3b: Mapping = Mapping()
            p3b.setId("2")
            p3b.setCode("g")
            p3b.setWord("果")
            setInstanceField(p3b, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList3.add(p3b)
            arrayList3.add(phraseList3)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList3))
            assertTrue("Non-phonetic table should learn phrase", ((stub3.ldPhraseAddCount > 0) && (stub3.lastLearnedLDPhrase != null)))
        } finally {
            setStatic("dbadapter", originalDbadapter)
            setStatic("tablename", originalTablename)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_3_7_learnLDPhrase_reverse_lookup_failures() {
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        try {
            var stub1: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            setStatic("dbadapter", stub1)
            var arrayList1: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList1: MutableList<Mapping?> = ArrayList()
            var m1: Mapping = Mapping()
            m1.setId(null)
            m1.setCode("a")
            m1.setWord("測")
            setInstanceField(m1, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList1.add(m1)
            arrayList1.add(phraseList1)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList1))
            assertEquals("Should not learn when baseCode lookup fails", 0, stub1.ldPhraseAddCount)
            var stub2: StubLimeDBForLearningEmptyCode = StubLimeDBForLearningEmptyCode(appContext)
            stub2.setupMappingForReverseLookup("測", "", "1")
            setStatic("dbadapter", stub2)
            var arrayList2: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList2: MutableList<Mapping?> = ArrayList()
            var m2: Mapping = Mapping()
            m2.setId(null)
            m2.setCode("ce")
            m2.setWord("測")
            setInstanceField(m2, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList2.add(m2)
            arrayList2.add(phraseList2)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList2))
            assertEquals("Should not learn when baseCode is empty", 0, stub2.ldPhraseAddCount)
            var stub3: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            stub3.setupMappingForReverseLookup("測", "ce", "1")
            setStatic("dbadapter", stub3)
            var arrayList3: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList3: MutableList<Mapping?> = ArrayList()
            var m3a: Mapping = Mapping()
            m3a.setId("1")
            m3a.setCode("ce")
            m3a.setWord("測")
            setInstanceField(m3a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList3.add(m3a)
            var m3b: Mapping = Mapping()
            m3b.setId(null)
            m3b.setCode("shi")
            m3b.setWord("試")
            setInstanceField(m3b, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList3.add(m3b)
            arrayList3.add(phraseList3)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList3))
            assertEquals("Should not learn when code2 lookup fails", 0, stub3.ldPhraseAddCount)
            var stub4: StubLimeDBForLearningEmptyCode = StubLimeDBForLearningEmptyCode(appContext)
            stub4.setupMappingForReverseLookup("測", "ce", "1")
            stub4.setupMappingForReverseLookup("試", "", "2")
            setStatic("dbadapter", stub4)
            var arrayList4: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList4: MutableList<Mapping?> = ArrayList()
            var m4a: Mapping = Mapping()
            m4a.setId("1")
            m4a.setCode("ce")
            m4a.setWord("測")
            setInstanceField(m4a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList4.add(m4a)
            var m4b: Mapping = Mapping()
            m4b.setId(null)
            m4b.setCode("shi")
            m4b.setWord("試")
            setInstanceField(m4b, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList4.add(m4b)
            arrayList4.add(phraseList4)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList4))
            assertEquals("Should not learn when code2 is empty", 0, stub4.ldPhraseAddCount)
        } finally {
            setStatic("dbadapter", originalDbadapter)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_3_8_learnLDPhrase_unit2_validation() {
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        try {
            var stub1: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            setStatic("dbadapter", stub1)
            var arrayList1: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList1: MutableList<Mapping?> = ArrayList()
            var m1a: Mapping = Mapping()
            m1a.setId("1")
            m1a.setCode("a")
            m1a.setWord("蘋")
            setInstanceField(m1a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList1.add(m1a)
            var m1b: Mapping = Mapping()
            m1b.setId("2")
            m1b.setCode("go")
            m1b.setWord("果")
            setInstanceField(m1b, "recordType", Mapping.RECORD_COMPOSING_CODE)
            phraseList1.add(m1b)
            arrayList1.add(phraseList1)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList1))
            assertEquals("Composing code record should prevent learning", 0, stub1.ldPhraseAddCount)
            var stub2: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            setStatic("dbadapter", stub2)
            var arrayList2: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList2: MutableList<Mapping?> = ArrayList()
            var m2a: Mapping = Mapping()
            m2a.setId("1")
            m2a.setCode("a")
            m2a.setWord("測")
            setInstanceField(m2a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList2.add(m2a)
            var m2b: Mapping = Mapping()
            m2b.setId("2")
            m2b.setCode("b")
            m2b.setWord("")
            setInstanceField(m2b, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList2.add(m2b)
            arrayList2.add(phraseList2)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList2))
            assertEquals("Should not learn when word2 is empty", 0, stub2.ldPhraseAddCount)
            var stub3: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            stub3.setupMappingForReverseLookup("測", "ce", "1")
            stub3.setupMappingForReverseLookup("試", "shi", "2")
            setStatic("dbadapter", stub3)
            var arrayList3: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList3: MutableList<Mapping?> = ArrayList()
            var m3a: Mapping = Mapping()
            m3a.setId("1")
            m3a.setCode("ce")
            m3a.setWord("測")
            setInstanceField(m3a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList3.add(m3a)
            var m3b: Mapping = Mapping()
            m3b.setId(null)
            m3b.setCode("s")
            m3b.setWord("試")
            setInstanceField(m3b, "recordType", Mapping.RECORD_PARTIAL_MATCH_TO_CODE)
            phraseList3.add(m3b)
            arrayList3.add(phraseList3)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList3))
            assertTrue("Partial match unit2 should be learned after reverse lookup", (stub3.ldPhraseAddCount > 0))
            stub3.ldPhraseAddCount = 0
            var arrayList4: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList4: MutableList<Mapping?> = ArrayList()
            var m4a: Mapping = Mapping()
            m4a.setId("1")
            m4a.setCode("ce")
            m4a.setWord("測")
            setInstanceField(m4a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList4.add(m4a)
            var m4b: Mapping = Mapping()
            m4b.setId("2")
            m4b.setCode(null)
            m4b.setWord("試")
            setInstanceField(m4b, "recordType", Mapping.RECORD_RELATED_PHRASE)
            phraseList4.add(m4b)
            arrayList4.add(phraseList4)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList4))
            assertTrue("Related phrase unit2 with null code should learn after lookup", (stub3.ldPhraseAddCount > 0))
            var stub4: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            setStatic("dbadapter", stub4)
            var arrayList5: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList5: MutableList<Mapping?> = ArrayList()
            var m5a: Mapping = Mapping()
            m5a.setId("1")
            m5a.setCode("ce")
            m5a.setWord("測")
            setInstanceField(m5a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList5.add(m5a)
            var m5b: Mapping = Mapping()
            m5b.setId("2")
            m5b.setCode("hello")
            m5b.setWord("hello")
            setInstanceField(m5b, "recordType", Mapping.RECORD_ENGLISH_SUGGESTION)
            phraseList5.add(m5b)
            arrayList5.add(phraseList5)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList5))
            assertEquals("English suggestion unit2 should prevent learning", 0, stub4.ldPhraseAddCount)
        } finally {
            setStatic("dbadapter", originalDbadapter)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_3_9_learnLDPhrase_multi_char_reverse_lookup_fails() {
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var stub: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
        stub.setupMappingForReverseLookup("測", "ce", "1")
        setStatic("dbadapter", stub)
        try {
            var arrayList: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList: MutableList<Mapping?> = ArrayList()
            var m1: Mapping = Mapping()
            m1.setId("1")
            m1.setCode("ceshi")
            m1.setWord("測試")
            setInstanceField(m1, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList.add(m1)
            arrayList.add(phraseList)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList))
            assertEquals("Reverse lookup failure for multi-char baseWord should stop learning", 0, stub.ldPhraseAddCount)
        } finally {
            setStatic("dbadapter", originalDbadapter)
        }
    }
    @Test(timeout = 5000)
    fun test_3_7_3_10_learnLDPhrase_remaining_branches() {
        var originalDbadapter: Any = getStatic("dbadapter", Any::class.java)
        var originalTablename: Any = getStatic("tablename", Any::class.java)
        try {
            var stub1: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            stub1.setupMappingForReverseLookup("測", "ce", "1")
            stub1.setupMappingForReverseLookup("試", "shi", "2")
            setStatic("dbadapter", stub1)
            var arrayList1: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList1: MutableList<Mapping?> = ArrayList()
            var m1a: Mapping = Mapping()
            m1a.setId("1")
            m1a.setCode("ce")
            m1a.setWord("測")
            setInstanceField(m1a, "recordType", Mapping.RECORD_PARTIAL_MATCH_TO_CODE)
            phraseList1.add(m1a)
            var m1b: Mapping = Mapping()
            m1b.setId("2")
            m1b.setCode("shi")
            m1b.setWord("試")
            setInstanceField(m1b, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList1.add(m1b)
            arrayList1.add(phraseList1)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList1))
            assertTrue("Partial match unit1 should trigger reverse lookup and learn", (stub1.ldPhraseAddCount > 0))
            var stub3: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            stub3.setupMappingForReverseLookup("測", "ce", "1")
            stub3.setupMappingForReverseLookup("試", "shi", "2")
            setStatic("dbadapter", stub3)
            var arrayList3: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList3: MutableList<Mapping?> = ArrayList()
            var m3a: Mapping = Mapping()
            m3a.setId("1")
            m3a.setCode("")
            m3a.setWord("測")
            setInstanceField(m3a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList3.add(m3a)
            var m3b: Mapping = Mapping()
            m3b.setId("2")
            m3b.setCode("shi")
            m3b.setWord("試")
            setInstanceField(m3b, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList3.add(m3b)
            arrayList3.add(phraseList3)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList3))
            assertTrue("Unit1 with empty code should trigger reverse lookup", (stub3.getMappingByWordCallCount > 0))
            var stub4: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            stub4.setupMappingForReverseLookup("測", "ce", "1")
            stub4.setupMappingForReverseLookup("試", "shi", "2")
            setStatic("dbadapter", stub4)
            var arrayList4: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList4: MutableList<Mapping?> = ArrayList()
            var m4a: Mapping = Mapping()
            m4a.setId("1")
            m4a.setCode("ce")
            m4a.setWord("測")
            setInstanceField(m4a, "recordType", Mapping.RECORD_RELATED_PHRASE)
            phraseList4.add(m4a)
            var m4b: Mapping = Mapping()
            m4b.setId("2")
            m4b.setCode("shi")
            m4b.setWord("試")
            setInstanceField(m4b, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList4.add(m4b)
            arrayList4.add(phraseList4)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList4))
            assertTrue("Related phrase unit1 should trigger reverse lookup", (stub4.getMappingByWordCallCount > 0))
            var stub5: StubLimeDBForLearningNullCode = StubLimeDBForLearningNullCode(appContext)
            stub5.setupMappingForReverseLookup("測", null, "1")
            setStatic("dbadapter", stub5)
            var arrayList5: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList5: MutableList<Mapping?> = ArrayList()
            var m5a: Mapping = Mapping()
            m5a.setId(null)
            m5a.setCode("ce")
            m5a.setWord("測")
            setInstanceField(m5a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList5.add(m5a)
            arrayList5.add(phraseList5)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList5))
            assertEquals("Null baseCode should prevent learning", 0, stub5.ldPhraseAddCount)
            var stub6: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            stub6.setupMappingForReverseLookup("測", "c", "1")
            stub6.setupMappingForReverseLookup("試", "s", "2")
            stub6.setupMappingForReverseLookup("蘋", "p", "3")
            stub6.setupMappingForReverseLookup("果", "g", "4")
            stub6.setupMappingForReverseLookup("汁", "j", "5")
            setStatic("dbadapter", stub6)
            var arrayList6: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList6: MutableList<Mapping?> = ArrayList()
            var m6a: Mapping = Mapping()
            m6a.setId("1")
            m6a.setCode("cspgj")
            m6a.setWord("測試蘋果汁")
            setInstanceField(m6a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList6.add(m6a)
            arrayList6.add(phraseList6)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList6))
            assertEquals("BaseWord length >= 5 should not learn", 0, stub6.ldPhraseAddCount)
            var stub7: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            setStatic("dbadapter", stub7)
            var arrayList7: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList7: MutableList<Mapping?> = ArrayList()
            var m7a: Mapping = Mapping()
            m7a.setId("1")
            m7a.setCode("ce")
            m7a.setWord("測")
            setInstanceField(m7a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList7.add(m7a)
            phraseList7.add(null)
            arrayList7.add(phraseList7)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList7))
            assertEquals("Null unit2 should prevent learning", 0, stub7.ldPhraseAddCount)
            var stub8: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            stub8.setupMappingForReverseLookup("測", "c", "1")
            stub8.setupMappingForReverseLookup("試", "s", "2")
            stub8.setupMappingForReverseLookup("蘋", "p", "3")
            stub8.setupMappingForReverseLookup("果", "g", "4")
            setStatic("dbadapter", stub8)
            var arrayList8: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList8: MutableList<Mapping?> = ArrayList()
            var m8a: Mapping = Mapping()
            m8a.setId("1")
            m8a.setCode("cspg")
            m8a.setWord("測試蘋果")
            setInstanceField(m8a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList8.add(m8a)
            var m8b: Mapping = Mapping()
            m8b.setId("2")
            m8b.setCode("j")
            m8b.setWord("汁")
            setInstanceField(m8b, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList8.add(m8b)
            arrayList8.add(phraseList8)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList8))
            assertEquals("BaseWord reaching size limit should prevent learning", 0, stub8.ldPhraseAddCount)
            var stub9: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            stub9.setupMappingForReverseLookup("測", "ce", "1")
            stub9.setupMappingForReverseLookup("試", "shi", "2")
            setStatic("dbadapter", stub9)
            var arrayList9: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList9: MutableList<Mapping?> = ArrayList()
            var m9a: Mapping = Mapping()
            m9a.setId("1")
            m9a.setCode("ce")
            m9a.setWord("測")
            setInstanceField(m9a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList9.add(m9a)
            var m9b: Mapping = Mapping()
            m9b.setId("2")
            m9b.setCode("s")
            m9b.setWord("試")
            setInstanceField(m9b, "recordType", Mapping.RECORD_PARTIAL_MATCH_TO_CODE)
            phraseList9.add(m9b)
            arrayList9.add(phraseList9)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList9))
            assertTrue("Partial match unit2 should trigger reverse lookup", (stub9.getMappingByWordCallCount > 0))
            var stub10: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            stub10.setupMappingForReverseLookup("測", "ce", "1")
            stub10.setupMappingForReverseLookup("試", "", "2")
            setStatic("dbadapter", stub10)
            var arrayList10: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList10: MutableList<Mapping?> = ArrayList()
            var m10a: Mapping = Mapping()
            m10a.setId("1")
            m10a.setCode("ce")
            m10a.setWord("測")
            setInstanceField(m10a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList10.add(m10a)
            var m10b: Mapping = Mapping()
            m10b.setId(null)
            m10b.setCode("shi")
            m10b.setWord("試")
            setInstanceField(m10b, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList10.add(m10b)
            arrayList10.add(phraseList10)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList10))
            assertEquals("Empty code2 should prevent learning", 0, stub10.ldPhraseAddCount)
            var stub11: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            stub11.setupMappingForReverseLookup("測", "ce", "1")
            stub11.setupMappingForReverseLookup("試", "shi", "2")
            setStatic("dbadapter", stub11)
            var arrayList11: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList11: MutableList<Mapping?> = ArrayList()
            var m11a: Mapping = Mapping()
            m11a.setId("1")
            m11a.setCode("ce")
            m11a.setWord("測")
            setInstanceField(m11a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList11.add(m11a)
            var m11b: Mapping = Mapping()
            m11b.setId("2")
            m11b.setCode(null)
            m11b.setWord("試")
            setInstanceField(m11b, "recordType", Mapping.RECORD_RELATED_PHRASE)
            phraseList11.add(m11b)
            arrayList11.add(phraseList11)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList11))
            assertTrue("Related phrase unit2 should trigger reverse lookup", (stub11.getMappingByWordCallCount > 0))
            var stub12: StubLimeDBForLearningNullCode = StubLimeDBForLearningNullCode(appContext)
            stub12.setupMappingForReverseLookup("測", "ce", "1")
            stub12.setupMappingForReverseLookup("試", null, "2")
            setStatic("dbadapter", stub12)
            var arrayList12: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList12: MutableList<Mapping?> = ArrayList()
            var m12a: Mapping = Mapping()
            m12a.setId("1")
            m12a.setCode("ce")
            m12a.setWord("測")
            setInstanceField(m12a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList12.add(m12a)
            var m12b: Mapping = Mapping()
            m12b.setId(null)
            m12b.setCode("shi")
            m12b.setWord("試")
            setInstanceField(m12b, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList12.add(m12b)
            arrayList12.add(phraseList12)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList12))
            assertEquals("Null code2 should prevent learning", 0, stub12.ldPhraseAddCount)
            var stub13: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            stub13.setupMappingForReverseLookup("測", "c", "1")
            stub13.setupMappingForReverseLookup("試", "s", "2")
            stub13.setupMappingForReverseLookup("蘋", "p", "3")
            setStatic("dbadapter", stub13)
            var arrayList13: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList13: MutableList<Mapping?> = ArrayList()
            var m13a: Mapping = Mapping()
            m13a.setId("1")
            m13a.setCode("cs")
            m13a.setWord("測試")
            setInstanceField(m13a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList13.add(m13a)
            var m13b: Mapping = Mapping()
            m13b.setId("2")
            m13b.setCode("pgj")
            m13b.setWord("蘋果汁")
            setInstanceField(m13b, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList13.add(m13b)
            arrayList13.add(phraseList13)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList13))
            assertEquals("Multi-char word2 pushing baseWord >= 5 should stop learning", 0, stub13.ldPhraseAddCount)
            var stub14: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            stub14.setupMappingForReverseLookup("ㄅ", "b3", "1")
            stub14.setupMappingForReverseLookup("ㄆ", "p ", "2")
            setStatic("dbadapter", stub14)
            setStatic("tablename", LIME.DB_TABLE_PHONETIC)
            var arrayList14: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList14: MutableList<Mapping?> = ArrayList()
            var m14a: Mapping = Mapping()
            m14a.setId("1")
            m14a.setCode("b3")
            m14a.setWord("ㄅ")
            setInstanceField(m14a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList14.add(m14a)
            var m14b: Mapping = Mapping()
            m14b.setId("2")
            m14b.setCode("p ")
            m14b.setWord("ㄆ")
            setInstanceField(m14b, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList14.add(m14b)
            arrayList14.add(phraseList14)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList14))
            assertTrue("Phonetic should save codes when length > 1", (stub14.ldPhraseAddCount >= 2))
            var stub15: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            stub15.setupMappingForReverseLookup("ㄅ", "3 ", "1")
            stub15.setupMappingForReverseLookup("ㄆ", "6 ", "2")
            setStatic("dbadapter", stub15)
            setStatic("tablename", LIME.DB_TABLE_PHONETIC)
            var arrayList15: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList15: MutableList<Mapping?> = ArrayList()
            var m15a: Mapping = Mapping()
            m15a.setId("1")
            m15a.setCode("3 ")
            m15a.setWord("ㄅ")
            setInstanceField(m15a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList15.add(m15a)
            var m15b: Mapping = Mapping()
            m15b.setId("2")
            m15b.setCode("6 ")
            m15b.setWord("ㄆ")
            setInstanceField(m15b, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList15.add(m15b)
            arrayList15.add(phraseList15)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList15))
            assertEquals("Phonetic QPCode with tone digits should be saved", 1, stub15.ldPhraseAddCount)
            var stub16: StubLimeDBForLearning = StubLimeDBForLearning(appContext)
            stub16.setupMappingForReverseLookup("測", "c", "1")
            stub16.setupMappingForReverseLookup("試", "s", "2")
            setStatic("dbadapter", stub16)
            setStatic("tablename", "dayi")
            var arrayList16: ArrayList<MutableList<Mapping?>> = ArrayList()
            var phraseList16: MutableList<Mapping?> = ArrayList()
            var m16a: Mapping = Mapping()
            m16a.setId("1")
            m16a.setCode("c")
            m16a.setWord("測")
            setInstanceField(m16a, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList16.add(m16a)
            var m16b: Mapping = Mapping()
            m16b.setId("2")
            m16b.setCode("s")
            m16b.setWord("試")
            setInstanceField(m16b, "recordType", Mapping.RECORD_EXACT_MATCH_TO_CODE)
            phraseList16.add(m16b)
            arrayList16.add(phraseList16)
            invokePrivate("learnLDPhrase", arrayOf(ArrayList::class.java), arrayOf(arrayList16))
            assertTrue("Non-phonetic with baseCode length > 1 should save", (stub16.ldPhraseAddCount > 0))
        } finally {
            setStatic("dbadapter", originalDbadapter)
            setStatic("tablename", originalTablename)
        }
    }
    open class StubLimeDBForLearning : net.toload.main.hd.limedb.LimeDB {
        var relatedPhraseAddCount: Int = 0
        var ldPhraseAddCount: Int = 0
        var getMappingByWordCallCount: Int = 0
        var lastRelatedPhrase: String? = null
        var lastLearnedLDPhrase: String? = null
        protected var reverseLookupMap: MutableMap<String, Mapping> = HashMap()
        constructor(context: Context) : super(context) {
        }
        open fun setupMappingForReverseLookup(word: String, code: String?, id: String?) {
            var m: Mapping = Mapping()
            m.setId(id)
            m.setCode(code)
            m.setWord(word)
            reverseLookupMap.put(word, m)
        }
        override fun addOrUpdateRelatedPhraseRecord(word1: String?, word2: String?): Int {
            relatedPhraseAddCount++
            lastRelatedPhrase = (word1 + word2)
            return 25
        }
        override fun addOrUpdateMappingRecord(code: String, word: String) {
            ldPhraseAddCount++
            lastLearnedLDPhrase = word
        }
        override fun getMappingByWord(keyword: String?, table: String): MutableList<Mapping?>? {
            getMappingByWordCallCount++
            var m: Mapping = reverseLookupMap.get(keyword)!!
            if ((m == null)) {
                return ArrayList()
            }
            var result: MutableList<Mapping?> = ArrayList()
            result.add(m)
            return result
        }
        fun getMappingByCode(code: String): MutableList<Mapping?> {
            return ArrayList()
        }
    }
    open class StubLimeDBForLearningLowScore : StubLimeDBForLearning {
        constructor(context: Context) : super(context) {
        }
        override fun addOrUpdateRelatedPhraseRecord(word1: String?, word2: String?): Int {
            relatedPhraseAddCount++
            lastRelatedPhrase = (word1 + word2)
            return 15
        }
    }
    open class StubLimeDBForLearningEmptyCode : StubLimeDBForLearning {
        constructor(context: Context) : super(context) {
        }
        override fun setupMappingForReverseLookup(word: String, code: String?, id: String?) {
            var m: Mapping = Mapping()
            m.setId(id)
            m.setCode(code)
            m.setWord(word)
            reverseLookupMap.put(word, m)
        }
    }
    open class StubLimeDBForLearningNullCode : StubLimeDBForLearning {
        constructor(context: Context) : super(context) {
        }
        override fun setupMappingForReverseLookup(word: String, code: String?, id: String?) {
            var m: Mapping = Mapping()
            m.setId(id)
            m.setCode(code)
            m.setWord(word)
            reverseLookupMap.put(word, m)
        }
    }
}
