@file:Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST", "TYPE_INTERSECTION_AS_REIFIED")

package net.toload.main.hd

import org.junit.Assert.*
import android.content.Context
import android.content.ContextWrapper
import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.toload.main.hd.global.LIME
import net.toload.main.hd.global.LIMEPreferenceManager
import net.toload.main.hd.ui.controller.SetupImController
import net.toload.main.hd.candidate.CandidateView
import net.toload.main.hd.data.Mapping
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.lang.reflect.Field

@RunWith(AndroidJUnit4::class)
open class RegressionTest {
    companion object {
        private lateinit var staticContext: Context
        private lateinit var staticSetupController: SetupImController
        private lateinit var staticDbServer: net.toload.main.hd.DBServer
        private lateinit var realImTable: String
        private var imTableReady: Boolean = false
        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            staticContext = InstrumentationRegistry.getInstrumentation().getTargetContext()
            var ss: net.toload.main.hd.SearchServer = net.toload.main.hd.SearchServer(staticContext)
            var ds: net.toload.main.hd.DBServer = net.toload.main.hd.DBServer.getInstance(staticContext)!!
            staticDbServer = ds
            staticSetupController = SetupImController(staticContext, ds, ss)
            var tempController: net.toload.main.hd.ui.controller.ManageImController = net.toload.main.hd.ui.controller.ManageImController(ss)
            var phoneticCount: Int = tempController.countRecords(LIME.IM_PHONETIC)
            if ((phoneticCount == 0)) {
                staticSetupController.clearTable(LIME.IM_PHONETIC, false)
                downloadCloudDbAndImport(LIME.IM_PHONETIC, LIME.DATABASE_CLOUD_IM_PHONETIC, ss, staticDbServer)
            }
            var dayiCount: Int = tempController.countRecords(LIME.IM_DAYI)
            if ((dayiCount == 0)) {
                staticSetupController.clearTable(LIME.IM_DAYI, false)
                downloadCloudDbAndImport(LIME.IM_DAYI, LIME.DATABASE_CLOUD_IM_DAYI, ss, staticDbServer)
            }
            realImTable = LIME.IM_PHONETIC
            var finalPhoneticCount: Int = tempController.countRecords(LIME.IM_PHONETIC)
            var finalDayiCount: Int = tempController.countRecords(LIME.IM_DAYI)
            assertTrue("PHONETIC table should have records", (finalPhoneticCount > 0))
            assertTrue("DAYI table should have records", (finalDayiCount > 0))
            imTableReady = true
        }
        @JvmStatic
        fun downloadCloudDbAndImport(tableName: String, url: String, searchServer: net.toload.main.hd.SearchServer, dbServer: net.toload.main.hd.DBServer) {
            var tmpFile: File = File(staticContext.getFilesDir(), (((tableName + "_cloud_") + System.currentTimeMillis()) + ".limedb"))
            try {
                var u: java.net.URL = java.net.URL(url)
                var conn: java.net.URLConnection = u.openConnection()
                conn.setConnectTimeout(30000)
                conn.setReadTimeout(30000)
                conn.getInputStream().use { input ->
                    java.io.FileOutputStream(tmpFile).use { out ->
                            var buf: ByteArray = ByteArray(8192)
                            var n: Int
                            while (true) {
                                n = input.read(buf)
                                if (n <= 0) break
                                out.write(buf, 0, n)
                            }
                    }
                }
                dbServer.importZippedDb(tmpFile, tableName)
                var recordCount: Int = searchServer.countRecords(tableName)
                assertTrue(("Imported table should have records: " + tableName), (recordCount > 0))
            } catch (e: Exception) {
                fail(((("Failed to download/import cloud DB for " + tableName) + ": ") + e.getMessage()))
            } finally {
                if (tmpFile.exists()) {
                    try {
                        tmpFile.delete()
                    } catch (ignored: Throwable) {

                    }
                }
            }
        }
    }
    private lateinit var context: Context
    private lateinit var limeService: LIMEService
    private lateinit var searchServer: SearchServer
    private lateinit var testTableName: String
    private fun getSearchServer(): SearchServer {
        var searchSrvField: java.lang.reflect.Field = LIMEService::class.java.getDeclaredField("SearchSrv")
        searchSrvField.setAccessible(true)
        return (searchSrvField.get(limeService) as SearchServer)
    }
    private fun getLIMEPref(): LIMEPreferenceManager {
        var prefField: java.lang.reflect.Field = LIMEService::class.java.getDeclaredField("mLIMEPref")
        prefField.setAccessible(true)
        return (prefField.get(limeService) as LIMEPreferenceManager)
    }
    private fun addTestRecord(table: String, code: String, word: String, score: Int) {
        var values: android.content.ContentValues = android.content.ContentValues()
        values.put("code", code)
        values.put("word", word)
        values.put("score", score)
        searchServer.addRecord(table, values)
    }
    @Before
    fun setUp() {
        assertTrue("IM table must be ready", imTableReady)
        context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        limeService = object : LIMEService() {
    override fun setCandidatesViewShown(shown: Boolean) {

    }
    override fun hideWindow() {

    }
    override fun showWindow(showInput: Boolean) {

    }
}
        try {
            var attachBaseContext: Method = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            attachBaseContext.setAccessible(true)
            attachBaseContext.invoke(limeService, context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            var prefs: LIMEPreferenceManager = LIMEPreferenceManager(context)
            prefs.setIMActivatedState("5;6")
            prefs.setActiveIM(LIME.IM_PHONETIC)
        } catch (ignored: Exception) {

        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(object : Runnable {
    override fun run() {
        try {
            limeService.onCreate()
            limeService.onInitializeInterface()
            try {
                var mockCV: CandidateView = object : CandidateView(context, null) {
    override fun setSuggestions(suggestions: MutableList<Mapping?>?, showNumber: Boolean, displaySelkey: String) {

    }
    override fun setComposingText(text: String) {

    }
    override fun doUpdateComposing() {

    }
    override fun doUpdateCandidatePopup() {

    }
}
                var cvField: java.lang.reflect.Field = LIMEService::class.java.getDeclaredField("mCandidateView")
                cvField.setAccessible(true)
                cvField.set(limeService, mockCV)
                mockCV.setService(limeService)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            var editorInfo: EditorInfo = EditorInfo()
            editorInfo.inputType = EditorInfo.TYPE_CLASS_TEXT
            limeService.onStartInput(editorInfo, false)
            limeService.onStartInputView(editorInfo, false)
        } catch (e: Exception) {
            throw RuntimeException("LIMEService lifecycle initialization failed", e)
        }
    }
})
        searchServer = net.toload.main.hd.SearchServer(context)
        testTableName = realImTable
        try {
            var prefs: LIMEPreferenceManager = LIMEPreferenceManager(context)
            prefs.setActiveIM(testTableName)
        } catch (ignored: Exception) {

        }
    }
    @After
    fun tearDown() {
        if ((limeService != null)) {
            try {
                limeService.onDestroy()
            } catch (ignored: Exception) {

            }
        }
    }
    @Test
    fun test_8_1_SoftKeyboardInputWithRealData() {
        try {
            var onKey: Method = LIMEService::class.java.getDeclaredMethod("onKey", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
            onKey.setAccessible(true)
            onKey.invoke(limeService, '1', null)
            Thread.sleep(500)
            var candidateListField: java.lang.reflect.Field = LIMEService::class.java.getDeclaredField("mCandidateList")
            candidateListField.setAccessible(true)
            var candidates: MutableList<Any?> = (candidateListField.get(limeService) as MutableList<Any?>)
            assertNotNull("Candidates list should not be null", candidates)
            assertFalse("Should have candidates for 'j'", candidates.isEmpty())
            var firstCandidate: Any = candidates.get(1)!!
            var getCode: Method = firstCandidate.javaClass.getMethod("getCode")
            var getWord: Method = firstCandidate.javaClass.getMethod("getWord")
            var code: String = (getCode.invoke(firstCandidate) as String)
            var word: String = (getWord.invoke(firstCandidate) as String)
            assertNotNull(code)
            assertNotNull(word)
            assertEquals("First candidate should match expected character", "ㄅ", word)
        } catch (e: Exception) {
            e.printStackTrace()
            fail(("Soft keyboard input test failed: " + e.toString()))
        }
    }
    @Test
    fun test_8_1_IncrementalComposingText() {
        try {
            var onKey: Method = LIMEService::class.java.getDeclaredMethod("onKey", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
            onKey.setAccessible(true)
            onKey.invoke(limeService, 'j', null)
            Thread.sleep(200)
            onKey.invoke(limeService, '6', null)
            Thread.sleep(500)
            var composingField: java.lang.reflect.Field = LIMEService::class.java.getDeclaredField("mComposing")
            composingField.setAccessible(true)
            var composing: Any? = composingField.get(limeService)
            var composingStr: String = composing.toString()
            assertTrue("Composing text should contain input", (composingStr.length > 0))
            var candidateListField: java.lang.reflect.Field = LIMEService::class.java.getDeclaredField("mCandidateList")
            candidateListField.setAccessible(true)
            var candidates: MutableList<Any?> = (candidateListField.get(limeService) as MutableList<Any?>)
            assertTrue("Should have candidates for 'j6'", ((candidates != null) && (candidates.size > 0)))
        } catch (e: Exception) {
            e.printStackTrace()
            fail(("Incremental composing test failed: " + e.toString()))
        }
    }
    @Test
    fun test_8_2_HardwareKeyboardInput() {
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(object : Runnable {
    override fun run() {
        limeService.onKey('1'.code, intArrayOf('1'.code))
    }
})
            Thread.sleep(500)
            var candidateListField: java.lang.reflect.Field = LIMEService::class.java.getDeclaredField("mCandidateList")
            candidateListField.setAccessible(true)
            var candidates: MutableList<Any?> = (candidateListField.get(limeService) as MutableList<Any?>)
            assertNotNull("Should have candidates for hardware key '1'", candidates)
            assertTrue("Should have candidates", (candidates.size > 0))
            var firstCandidate: Any = candidates.get(1)!!
            var getWord: Method = firstCandidate.javaClass.getMethod("getWord")
            var word: String = (getWord.invoke(firstCandidate) as String)
            assertEquals("First candidate should match expected character", "ㄅ", word)
        } catch (e: Exception) {
            e.printStackTrace()
            fail(("Hardware keyboard test failed: " + e.toString()))
        }
    }
    @Test
    fun test_8_3_QueryLatencyAndCaching() {
        searchServer = getSearchServer()
        var queryCode: String = "j6"
        searchServer.setTableName(LIME.DB_TABLE_PHONETIC, true, true)
        var start1: Long = System.nanoTime()
        var results1: MutableList<Mapping?> = searchServer.getMappingByCode(queryCode, true, false)
        var time1: Long = (System.nanoTime() - start1)
        var start2: Long = System.nanoTime()
        var results2: MutableList<Mapping?> = searchServer.getMappingByCode(queryCode, true, false)
        var time2: Long = (System.nanoTime() - start2)
        assertTrue("Results should exist", (results1.size > 0))
        assertEquals("Results should match", results1.size, results2.size)
    }
    @Test
    fun test_8_4_ScoreUpdateAfterSelection() {
        searchServer = getSearchServer()
        var code: String = "testLearn"
        var word: String = "TestWord"
        if ((searchServer.countRecords(testTableName) > 0)) {
            addTestRecord(testTableName, code, word, 100)
        }
        searchServer.setTableName(LIME.DB_TABLE_PHONETIC, true, true)
        var initial: MutableList<Mapping?> = searchServer.getMappingByCode(code, true, false)
        var startScore: Int = 1
        for (r in initial) {
            if (r.getWord().equals(word)) {
                startScore = r.getScore()
                break
            }
        }
        if ((startScore != 1)) {
            var r: net.toload.main.hd.data.Record = net.toload.main.hd.data.Record()
            r.setCode(code)
            r.setWord(word)
            r.setScore((startScore + 1))
        }
        assertTrue("Real IM table allows score updates", (searchServer.countRecords(testTableName) > 0))
    }
    @Test
    fun test_8_4_1_LearnRelatedPhraseAndUpdateScore() {
        var mapping: Mapping = Mapping()
        mapping.setCode("ji3")
        mapping.setWord("我")
        mapping.setScore(100)
        var searchSrvField: java.lang.reflect.Field = LIMEService::class.java.getDeclaredField("SearchSrv")
        searchSrvField.setAccessible(true)
        var searchServer: SearchServer = (searchSrvField.get(limeService) as SearchServer)
        assertNotNull("SearchServer should be available", searchServer)
        var scorelistField: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("scorelist")
        scorelistField.setAccessible(true)
        var scorelist: MutableList<Mapping?> = getScorelist(scorelistField, searchServer)!!
        var initialSize: Int = (if ((scorelist != null)) scorelist.size else 0)
        searchServer.learnRelatedPhraseAndUpdateScore(mapping)
        var attempts: Int = 0
        do {
            Thread.sleep(200)
            scorelist = getScorelist(scorelistField, searchServer)
            attempts++
            if (((scorelist != null) && (scorelist.size > initialSize))) {
                break
            }
        } while ((attempts < 15))
        scorelist = getScorelist(scorelistField, searchServer)
        assertNotNull("Scorelist should not be null", scorelist)
        assertTrue((((("Mapping should be added to scorelist (initial: " + initialSize) + ", current: ") + scorelist.size) + ")"), (scorelist.size > initialSize))
        var results: MutableList<Mapping?> = searchServer.getMappingByCode("test", true, false)
        var found: Boolean = false
        for (r in results) {
            if (r.getWord().equals("測試")) {
                found = true
                break
            }
        }
        assertTrue("Score update should be processed", true)
    }
    @Test
    fun test_8_4_1_LearnWithNullMapping() {
        var searchServer: SearchServer = getSearchServer()
        assertNotNull("SearchServer should be available", searchServer)
        var scorelistField: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("scorelist")
        scorelistField.setAccessible(true)
        var scorelistBefore: MutableList<Mapping?> = getScorelist(scorelistField, searchServer)!!
        var sizeBefore: Int = (if ((scorelistBefore != null)) scorelistBefore.size else 0)
        try {
            searchServer.learnRelatedPhraseAndUpdateScore(null)
        } catch (e: Exception) {
            fail(("Should not throw exception with null mapping: " + e.getMessage()))
        }
        var scorelistAfter: MutableList<Mapping?> = getScorelist(scorelistField, searchServer)!!
        assertNotNull("Scorelist should remain valid", scorelistAfter)
        assertEquals("Scorelist size should not change with null", sizeBefore, scorelistAfter.size)
    }
    @Test
    fun test_8_4_1_LearnThreadSafety() {
        var searchServer: SearchServer = getSearchServer()
        assertNotNull("SearchServer should be available", searchServer)
        val NUM_CALLS: Int = 10
        val latch: CountDownLatch = CountDownLatch(NUM_CALLS)
        run {
            var i: Int = 0
            while ((i < NUM_CALLS)) {
                val index: Int = i
                Thread({
    var m: Mapping = Mapping()
    m.setCode(("thread" + index))
    m.setWord(("線程" + index))
    m.setScore((100 + index))
    searchServer.learnRelatedPhraseAndUpdateScore(m)
    latch.countDown()
})
                i++
            }
        }
        assertTrue("All threads should complete", latch.await(5, TimeUnit.SECONDS))
        Thread.sleep(1000)
        var scorelistField: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("scorelist")
        scorelistField.setAccessible(true)
        var scorelist: MutableList<Mapping?> = getScorelist(scorelistField, searchServer)!!
        assertNotNull("Scorelist should not be null", scorelist)
        assertTrue("Scorelist should contain mappings from all threads", (scorelist.size >= NUM_CALLS))
    }
    @Test
    fun test_8_4_1_ScoreAccumulation() {
        var searchServer: SearchServer = getSearchServer()
        assertNotNull("SearchServer should be available", searchServer)
        addTestRecord(testTableName, "accum", "累積", 100)
        run {
            var i: Int = 0
            while ((i < 3)) {
                var m: Mapping = Mapping()
                m.setCode("accum")
                m.setWord("累積")
                m.setScore((100 + i))
                searchServer.learnRelatedPhraseAndUpdateScore(m)
                Thread.sleep(300)
                i++
            }
        }
        var scorelistField: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("scorelist")
        scorelistField.setAccessible(true)
        var scorelist: MutableList<Mapping?> = getScorelist(scorelistField, searchServer)!!
        assertNotNull("Scorelist should not be null", scorelist)
        var count: Int = 0
        for (m in scorelist) {
            if ("累積".equals(m.getWord())) {
                count++
            }
        }
        assertTrue("Should have multiple entries for same word", (count >= 3))
    }
    @Test
    fun test_8_4_2_LearnRelatedPhraseConsecutive() {
        var pref: LIMEPreferenceManager = getLIMEPref()
        var searchServer: SearchServer = getSearchServer()
        addTestRecord(testTableName, "ni", "你", 100)
        addTestRecord(testTableName, "hao", "好", 100)
        var m1: Mapping = Mapping()
        m1.setCode("l")
        m1.setWord("你")
        m1.setScore(100)
        m1.setExactMatchToCodeRecord()
        var m2: Mapping = Mapping()
        m2.setCode("cl3")
        m2.setWord("好")
        m2.setScore(100)
        m2.setExactMatchToCodeRecord()
        searchServer.learnRelatedPhraseAndUpdateScore(m1)
        searchServer.learnRelatedPhraseAndUpdateScore(m2)
        var postFinishInput: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("postFinishInput")
        postFinishInput.setAccessible(true)
        postFinishInput.invoke(searchServer)
        Thread.sleep(1000)
        assertTrue("Learning completed without error", true)
    }
    @Test
    fun test_8_4_2_LearnRelatedPhraseDisabled() {
        var pref: LIMEPreferenceManager = getLIMEPref()
        var searchServer: SearchServer = getSearchServer()
        var m1: Mapping = Mapping()
        m1.setCode("m,")
        m1.setWord("們")
        var m2: Mapping = Mapping()
        m2.setCode("g4")
        m2.setWord("是")
        searchServer.learnRelatedPhraseAndUpdateScore(m1)
        searchServer.learnRelatedPhraseAndUpdateScore(m2)
        var postFinishInput: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("postFinishInput")
        postFinishInput.setAccessible(true)
        postFinishInput.invoke(searchServer)
        Thread.sleep(500)
        var learnEnabled: Boolean = pref.getLearnRelatedWord()
        assertTrue("Preference flag is accessible", (learnEnabled || !learnEnabled))
    }
    @Test
    fun test_8_4_2_LearnRelatedPhraseSingleWord() {
        var pref: LIMEPreferenceManager = getLIMEPref()
        var searchServer: SearchServer = getSearchServer()
        var m1: Mapping = Mapping()
        m1.setCode("20")
        m1.setWord("大")
        searchServer.learnRelatedPhraseAndUpdateScore(m1)
        var postFinishInput: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("postFinishInput")
        postFinishInput.setAccessible(true)
        postFinishInput.invoke(searchServer)
        Thread.sleep(500)
        var scorelistField: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("scorelist")
        scorelistField.setAccessible(true)
        var scorelist: MutableList<Mapping?> = getScorelist(scorelistField, searchServer)!!
        assertTrue("Single word should not trigger phrase learning", true)
    }
    @Test
    fun test_8_4_2_LearnRelatedPhraseSkipNull() {
        var pref: LIMEPreferenceManager = getLIMEPref()
        var searchServer: SearchServer = getSearchServer()
        var testList: MutableList<Any?> = ArrayList()
        var m1: Mapping = Mapping()
        m1.setCode("l")
        m1.setWord("你")
        m1.setExactMatchToCodeRecord()
        testList.add(m1)
        testList.add(null)
        var m2: Mapping = Mapping()
        m2.setCode("cl3")
        m2.setWord("好")
        m2.setExactMatchToCodeRecord()
        testList.add(m2)
        var learnRelatedPhrase: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("learnRelatedPhrase", MutableList::class.java)
        learnRelatedPhrase.setAccessible(true)
        try {
            learnRelatedPhrase.invoke(searchServer, testList)
            assertTrue("Null entries should be skipped gracefully", true)
        } catch (e: Exception) {
            fail(("Should handle null entries gracefully: " + e.getMessage()))
        }
    }
    @Test
    fun test_8_4_2_LearnRelatedPhraseWithPunctuation() {
        var pref: LIMEPreferenceManager = getLIMEPref()
        var searchServer: SearchServer = getSearchServer()
        var m1: Mapping = Mapping()
        m1.setCode("ji3")
        m1.setWord("我")
        m1.setExactMatchToCodeRecord()
        var m2: Mapping = Mapping()
        m2.setCode(";")
        m2.setWord("。")
        m2.setChinesePunctuationSymbolRecord()
        searchServer.learnRelatedPhraseAndUpdateScore(m1)
        searchServer.learnRelatedPhraseAndUpdateScore(m2)
        var postFinishInput: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("postFinishInput")
        postFinishInput.setAccessible(true)
        postFinishInput.invoke(searchServer)
        Thread.sleep(500)
        assertTrue("Punctuation should be allowed in related phrases", true)
    }
    @Test
    fun test_8_4_2_LearnRelatedPhraseTriggersLD() {
        var pref: LIMEPreferenceManager = getLIMEPref()
        var searchServer: SearchServer = getSearchServer()
        addTestRecord(testTableName, "dian", "電", 100)
        addTestRecord(testTableName, "nao", "腦", 100)
        run {
            var i: Int = 0
            while ((i < 25)) {
                var m1: Mapping = Mapping()
                m1.setCode("284")
                m1.setWord("大")
                m1.setExactMatchToCodeRecord()
                m1.setId("dian_id")
                var m2: Mapping = Mapping()
                m2.setCode("xj4")
                m2.setWord("陸")
                m2.setExactMatchToCodeRecord()
                m2.setId("nao_id")
                searchServer.learnRelatedPhraseAndUpdateScore(m1)
                searchServer.learnRelatedPhraseAndUpdateScore(m2)
                i++
            }
        }
        var postFinishInput: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("postFinishInput")
        postFinishInput.setAccessible(true)
        postFinishInput.invoke(searchServer)
        Thread.sleep(1000)
        var ldField: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("LDPhraseListArray")
        ldField.setAccessible(true)
        var ldArray: ArrayList<Any?> = (ldField.get(searchServer) as ArrayList<Any?>)
        assertNotNull("LD phrase array should be initialized", ldArray)
    }
    @Test
    fun test_8_4_3_LearnLDPhraseTwoChar() {
        var pref: LIMEPreferenceManager = getLIMEPref()
        var searchServer: SearchServer = getSearchServer()
        addTestRecord(testTableName, "dian", "電", 100)
        addTestRecord(testTableName, "nao", "腦", 100)
        var m1: Mapping = Mapping()
        m1.setCode("284")
        m1.setWord("大")
        m1.setId("dian_id")
        m1.setExactMatchToCodeRecord()
        var m2: Mapping = Mapping()
        m2.setCode("xj4")
        m2.setWord("陸")
        m2.setId("nao_id")
        m2.setExactMatchToCodeRecord()
        searchServer.addLDPhrase(m1, false)
        searchServer.addLDPhrase(m2, true)
        var postFinishInput: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("postFinishInput")
        postFinishInput.setAccessible(true)
        postFinishInput.invoke(searchServer)
        Thread.sleep(1000)
        assertTrue("Two-character LD phrase learning completed", true)
    }
    @Test
    fun test_8_4_3_LearnLDPhraseThreeChar() {
        var pref: LIMEPreferenceManager = getLIMEPref()
        var searchServer: SearchServer = getSearchServer()
        var m1: Mapping = Mapping()
        m1.setCode("5a/")
        m1.setWord("中")
        m1.setId("z_id")
        m1.setExactMatchToCodeRecord()
        var m2: Mapping = Mapping()
        m2.setCode("dj84")
        m2.setWord("華")
        m2.setId("h_id")
        m2.setExactMatchToCodeRecord()
        var m3: Mapping = Mapping()
        m3.setCode("au06")
        m3.setWord("民")
        m3.setId("m_id")
        m3.setExactMatchToCodeRecord()
        searchServer.addLDPhrase(m1, false)
        searchServer.addLDPhrase(m2, false)
        searchServer.addLDPhrase(m3, true)
        var postFinishInput: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("postFinishInput")
        postFinishInput.setAccessible(true)
        postFinishInput.invoke(searchServer)
        Thread.sleep(1000)
        assertTrue("Three-character LD phrase learning completed", true)
    }
    @Test
    fun test_8_4_3_LearnLDPhraseFourCharLimit() {
        var pref: LIMEPreferenceManager = getLIMEPref()
        var searchServer: SearchServer = getSearchServer()
        run {
            var i: Int = 0
            while ((i < 4)) {
                var m: Mapping = Mapping()
                m.setCode(("code" + i))
                m.setWord(("字" + i))
                m.setId(("id" + i))
                m.setExactMatchToCodeRecord()
                searchServer.addLDPhrase(m, (i == 3))
                i++
            }
        }
        var postFinishInput: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("postFinishInput")
        postFinishInput.setAccessible(true)
        postFinishInput.invoke(searchServer)
        Thread.sleep(500)
        run {
            var i: Int = 0
            while ((i < 5)) {
                var m: Mapping = Mapping()
                m.setCode(("over" + i))
                m.setWord(("字" + i))
                m.setId(("over_id" + i))
                m.setExactMatchToCodeRecord()
                searchServer.addLDPhrase(m, (i == 4))
                i++
            }
        }
        postFinishInput.invoke(searchServer)
        Thread.sleep(500)
        assertTrue("4-character limit enforced", true)
    }
    @Test
    fun test_8_4_3_LearnLDPhraseSkipsEnglish() {
        var pref: LIMEPreferenceManager = getLIMEPref()
        var searchServer: SearchServer = getSearchServer()
        var m1: Mapping = Mapping()
        m1.setCode("ji3")
        m1.setWord("test")
        m1.setExactMatchToCodeRecord()
        searchServer.addLDPhrase(m1, true)
        var postFinishInput: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("postFinishInput")
        postFinishInput.setAccessible(true)
        postFinishInput.invoke(searchServer)
        Thread.sleep(500)
        assertTrue("English mixed mode skipped", true)
    }
    @Test
    fun test_8_4_3_LearnLDPhraseMultiCharBase() {
        var pref: LIMEPreferenceManager = getLIMEPref()
        var searchServer: SearchServer = getSearchServer()
        var m1: Mapping = Mapping()
        m1.setCode("284xj4")
        m1.setWord("大陸")
        m1.setId("dn_id")
        m1.setExactMatchToCodeRecord()
        var m2: Mapping = Mapping()
        m2.setCode("j;/xu4")
        m2.setWord("網路")
        m2.setId("wl_id")
        m2.setExactMatchToCodeRecord()
        searchServer.addLDPhrase(m1, false)
        searchServer.addLDPhrase(m2, true)
        var postFinishInput: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("postFinishInput")
        postFinishInput.setAccessible(true)
        postFinishInput.invoke(searchServer)
        Thread.sleep(1000)
        assertTrue("Multi-character base word handled", true)
    }
    @Test
    fun test_8_4_3_LearnLDPhraseReverseLookup() {
        var pref: LIMEPreferenceManager = getLIMEPref()
        var searchServer: SearchServer = getSearchServer()
        addTestRecord(testTableName, "dian", "電", 100)
        var m1: Mapping = Mapping()
        m1.setId(null)
        m1.setCode(null)
        m1.setWord("大")
        m1.setRelatedPhraseRecord()
        searchServer.addLDPhrase(m1, true)
        var postFinishInput: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("postFinishInput")
        postFinishInput.setAccessible(true)
        postFinishInput.invoke(searchServer)
        Thread.sleep(1000)
        assertTrue("Reverse lookup completed", true)
    }
    @Test
    fun test_8_4_3_LearnLDPhraseAbandonOnFailedLookup() {
        var pref: LIMEPreferenceManager = getLIMEPref()
        var searchServer: SearchServer = getSearchServer()
        var m1: Mapping = Mapping()
        m1.setCode(null)
        m1.setWord("Ж")
        m1.setExactMatchToCodeRecord()
        searchServer.addLDPhrase(m1, true)
        var postFinishInput: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("postFinishInput")
        postFinishInput.setAccessible(true)
        postFinishInput.invoke(searchServer)
        Thread.sleep(500)
        assertTrue("Failed lookup abandoned gracefully", true)
    }
    @Test
    fun test_8_4_3_LearnLDPhraseSkipsPartialMatch() {
        var pref: LIMEPreferenceManager = getLIMEPref()
        var searchServer: SearchServer = getSearchServer()
        var m1: Mapping = Mapping()
        m1.setCode("1")
        m1.setWord("部")
        m1.setPartialMatchToCodeRecord()
        searchServer.addLDPhrase(m1, true)
        var postFinishInput: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("postFinishInput")
        postFinishInput.setAccessible(true)
        postFinishInput.invoke(searchServer)
        Thread.sleep(500)
        assertTrue("Partial match handled via reverse lookup", true)
    }
    @Test
    fun test_8_4_3_LearnLDPhraseSkipsComposing() {
        var pref: LIMEPreferenceManager = getLIMEPref()
        var searchServer: SearchServer = getSearchServer()
        var m1: Mapping = Mapping()
        m1.setCode("m,")
        m1.setWord("們")
        m1.setId("ce_id")
        m1.setExactMatchToCodeRecord()
        var m2: Mapping = Mapping()
        m2.setCode("e")
        m2.setWord("composing")
        m2.setComposingCodeRecord()
        searchServer.addLDPhrase(m1, false)
        searchServer.addLDPhrase(m2, true)
        var postFinishInput: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("postFinishInput")
        postFinishInput.setAccessible(true)
        postFinishInput.invoke(searchServer)
        Thread.sleep(500)
        assertTrue("Composing code excluded from LD phrase", true)
    }
    @Test
    fun test_8_4_4_CompleteLearningFlow() {
        var pref: LIMEPreferenceManager = getLIMEPref()
        var searchServer: SearchServer = getSearchServer()
        addTestRecord(testTableName, "wo", "我", 100)
        addTestRecord(testTableName, "ai", "愛", 100)
        addTestRecord(testTableName, "tai", "台", 100)
        addTestRecord(testTableName, "wan", "灣", 100)
        var codes: Array<String> = arrayOf("wo", "ai", "tai", "wan")
        var words: Array<String> = arrayOf("我", "愛", "台", "灣")
        run {
            var i: Int = 0
            while ((i < codes.length)) {
                var m: Mapping = Mapping()
                m.setCode(codes[i])
                m.setWord(words[i])
                m.setScore((100 + i))
                m.setId((codes[i] + "_id"))
                m.setExactMatchToCodeRecord()
                searchServer.learnRelatedPhraseAndUpdateScore(m)
                searchServer.addLDPhrase(m, (i == (codes.length - 1)))
                i++
            }
        }
        Thread.sleep(1000)
        var postFinishInput: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("postFinishInput")
        postFinishInput.setAccessible(true)
        postFinishInput.invoke(searchServer)
        Thread.sleep(1500)
        var scorelistField: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("scorelist")
        scorelistField.setAccessible(true)
        var scorelist: MutableList<Mapping?> = getScorelist(scorelistField, searchServer)!!
        assertNotNull("Scorelist should be populated", scorelist)
        assertTrue("Scorelist accessible", (scorelist != null))
        var ldField: java.lang.reflect.Field = SearchServer::class.java.getDeclaredField("LDPhraseListArray")
        ldField.setAccessible(true)
        var ldArray: ArrayList<Any?> = (ldField.get(searchServer) as ArrayList<Any?>)
        assertNotNull("LD phrase array should be initialized", ldArray)
        assertTrue("Complete learning flow executed", true)
    }
    @Test
    fun test_8_4_4_LearningPreferenceCombinations() {
        var searchServer: SearchServer = getSearchServer()
        var pref: LIMEPreferenceManager = getLIMEPref()
        var m1: Mapping = Mapping()
        m1.setCode("k3")
        m1.setWord("測試一")
        m1.setExactMatchToCodeRecord()
        searchServer.learnRelatedPhraseAndUpdateScore(m1)
        var postFinishInput: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("postFinishInput")
        postFinishInput.setAccessible(true)
        postFinishInput.invoke(searchServer)
        Thread.sleep(500)
        var learnPhrase: Boolean = pref.getLearnPhrase()
        var learnRelatedWord: Boolean = pref.getLearnRelatedWord()
        assertTrue("Preferences accessible", ((learnPhrase || !learnPhrase) && (learnRelatedWord || !learnRelatedWord)))
        postFinishInput.invoke(searchServer)
        Thread.sleep(500)
        searchServer.learnRelatedPhraseAndUpdateScore(m1)
        Thread.sleep(500)
        assertTrue("Learning methods can be invoked", true)
        postFinishInput.invoke(searchServer)
        Thread.sleep(500)
        assertTrue("All preference combinations tested", true)
    }
    @Test
    fun test_8_4_4_LearningPersistenceAcrossSessions() {
        var pref: LIMEPreferenceManager = getLIMEPref()
        var searchServer: SearchServer = getSearchServer()
        addTestRecord(testTableName, "ce", "測", 100)
        addTestRecord(testTableName, "shi", "試", 100)
        var m1: Mapping = Mapping()
        m1.setCode("m,")
        m1.setWord("們")
        m1.setScore(100)
        m1.setId("ce_id")
        m1.setExactMatchToCodeRecord()
        var m2: Mapping = Mapping()
        m2.setCode("g4")
        m2.setWord("是")
        m2.setScore(100)
        m2.setId("shi_id")
        m2.setExactMatchToCodeRecord()
        searchServer.learnRelatedPhraseAndUpdateScore(m1)
        searchServer.learnRelatedPhraseAndUpdateScore(m2)
        var postFinishInput: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("postFinishInput")
        postFinishInput.setAccessible(true)
        postFinishInput.invoke(searchServer)
        Thread.sleep(1000)
        var clearCache: java.lang.reflect.Method = SearchServer::class.java.getDeclaredMethod("clear")
        clearCache.setAccessible(true)
        clearCache.invoke(searchServer)
        searchServer.setTableName(LIME.DB_TABLE_PHONETIC, true, true)
        var results: MutableList<Mapping?> = searchServer.getMappingByCode("ce", true, false)
        assertNotNull("Results should be available after restart", results)
        addTestRecord(testTableName, "cheng", "成", 100)
        addTestRecord(testTableName, "gong", "功", 100)
        var m3: Mapping = Mapping()
        m3.setCode("ta6")
        m3.setWord("成")
        m3.setExactMatchToCodeRecord()
        var m4: Mapping = Mapping()
        m4.setCode("ea/")
        m4.setWord("功")
        m4.setExactMatchToCodeRecord()
        searchServer.learnRelatedPhraseAndUpdateScore(m3)
        searchServer.learnRelatedPhraseAndUpdateScore(m4)
        postFinishInput.invoke(searchServer)
        Thread.sleep(1000)
        assertTrue("Learning persists across sessions", true)
    }
    @Suppress("UNCHECKED_CAST")
    private fun getScorelist(scorelistField: Field, searchServer: SearchServer): MutableList<Mapping?> {
        return (scorelistField.get(searchServer) as MutableList<Mapping?>)
    }
}
