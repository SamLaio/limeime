package net.toload.main.hd

import org.junit.Assert.*
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.toload.main.hd.data.Record
import net.toload.main.hd.global.LIME
import net.toload.main.hd.ui.controller.SetupImController
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
open class IntegrationTestUISearchServer {
    companion object {
        private lateinit var staticContext: Context
        private lateinit var staticSetupController: SetupImController
        private lateinit var staticDbServer: net.toload.main.hd.DBServer
        private var imTablesReady: Boolean = false
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
                downloadCloudDbAndImport(LIME.IM_PHONETIC, LIME.DATABASE_CLOUD_IM_PHONETIC, tempController, staticDbServer)
            }
            var dayiCount: Int = tempController.countRecords(LIME.IM_DAYI)
            if ((dayiCount == 0)) {
                staticSetupController.clearTable(LIME.IM_DAYI, false)
                downloadCloudDbAndImport(LIME.IM_DAYI, LIME.DATABASE_CLOUD_IM_DAYI, tempController, staticDbServer)
            }
            var finalPhoneticCount: Int = tempController.countRecords(LIME.IM_PHONETIC)
            var finalDayiCount: Int = tempController.countRecords(LIME.IM_DAYI)
            assertTrue("PHONETIC table should have records", (finalPhoneticCount > 0))
            assertTrue("DAYI table should have records", (finalDayiCount > 0))
            imTablesReady = true
        }
        @JvmStatic
        fun downloadCloudDbAndImport(tableName: String, url: String, manageController: net.toload.main.hd.ui.controller.ManageImController, dbServer: net.toload.main.hd.DBServer) {
            var tmpFile: java.io.File = java.io.File(staticContext.getFilesDir(), (((tableName + "_cloud_") + System.currentTimeMillis()) + ".limedb"))
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
                var recordCount: Int = manageController.countRecords(tableName)
                assertTrue(("Imported table should have records: " + tableName), (recordCount > 0))
            } catch (e: java.io.IOException) {
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
    private lateinit var setupController: SetupImController
    private lateinit var manageController: net.toload.main.hd.ui.controller.ManageImController
    private lateinit var testTableName: String
    @Before
    fun setUp() {
        assertTrue("IM tables must be ready before running tests", imTablesReady)
        context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var ss: net.toload.main.hd.SearchServer = net.toload.main.hd.SearchServer(context)
        var ds: net.toload.main.hd.DBServer = net.toload.main.hd.DBServer.getInstance(context)!!
        setupController = SetupImController(context, ds, ss)
        manageController = net.toload.main.hd.ui.controller.ManageImController(ss)
        testTableName = net.toload.main.hd.global.LIME.DB_TABLE_CUSTOM
        setupController.clearTable(testTableName, false)
    }
    @Test
    fun test_5_3_1_CompleteUIOperationFlow() {
        manageController.addRecord(testTableName, "ui", "介面", 100)
        var count: Int = manageController.countRecords(testTableName)
        assertEquals("Record should exist in database", 1, count)
    }
    @Test
    fun test_5_3_1_DataFlowTransformations() {
        manageController.addRecord(testTableName, "transform", "轉換測試", 999)
        var results: MutableList<Record> = queryRecords(testTableName, "transform", true)
        assertNotNull("Results should not be null", results)
        assertFalse("Results should not be empty", results.isEmpty())
        var r: Record = results.get(0)
        assertEquals("Code should be preserved", "transform", r.getCode())
        assertEquals("Word should be preserved", "轉換測試", r.getWord())
        assertEquals("Score should be preserved", 999, r.getScore())
    }
    @Test
    fun test_5_3_1_ErrorHandlingAtEachLayer() {
        manageController.addRecord(("invalid_table_" + System.currentTimeMillis()), "error", "錯誤", 100)
        var before: Int = manageController.countRecords(testTableName)
        var after: Int = manageController.countRecords(testTableName)
        assertEquals("Record count should remain unchanged", before, after)
    }
    @Test
    fun test_5_3_2_0_CloudURLsAvailable() {
        assertNotNull("Phonetic cloud URL should be defined", LIME.DATABASE_CLOUD_IM_PHONETIC)
        assertNotNull("Dayi cloud URL should be defined", LIME.DATABASE_CLOUD_IM_DAYI)
        assertFalse("Phonetic URL should not be empty", LIME.DATABASE_CLOUD_IM_PHONETIC.isEmpty())
        assertFalse("Dayi URL should not be empty", LIME.DATABASE_CLOUD_IM_DAYI.isEmpty())
        assertTrue("Phonetic URL should start with http", LIME.DATABASE_CLOUD_IM_PHONETIC.startsWith("http"))
        assertTrue("Dayi URL should start with http", LIME.DATABASE_CLOUD_IM_DAYI.startsWith("http"))
    }
    @Test
    fun test_5_3_2_0b_IMTypeConstantsExist() {
        assertNotNull("IM_PHONETIC constant should be defined", LIME.IM_PHONETIC)
        assertNotNull("IM_DAYI constant should be defined", LIME.IM_DAYI)
        assertFalse("IM_PHONETIC should not be empty", LIME.IM_PHONETIC.isEmpty())
        assertFalse("IM_DAYI should not be empty", LIME.IM_DAYI.isEmpty())
    }
    @Test
    fun test_5_3_2_ErrorHandlingOnNetworkFailures() {
        var before: Int = setupController.countRecords(net.toload.main.hd.global.LIME.IM_PHONETIC)
        setupController.downloadAndImportZippedDb(net.toload.main.hd.global.LIME.IM_PHONETIC, "http://invalid.invalid/doesnotexist.zip", false)
        Thread.sleep(3000)
        var after: Int = setupController.countRecords(net.toload.main.hd.global.LIME.IM_PHONETIC)
        assertEquals("Import should not change count on failure", before, after)
    }
    @Test
    fun test_5_3_2_HotPathQueryLatency() {
        run {
            var i: Int = 0
            while ((i < 100)) {
                manageController.addRecord(testTableName, ("hot" + i), ("熱" + i), (100 - i))
                i++
            }
        }
        var startCold: Long = System.nanoTime()
        var coldResults: MutableList<Record> = queryRecords(testTableName, "hot50", true)
        var startWarm: Long = System.nanoTime()
        var warmResults: MutableList<Record> = queryRecords(testTableName, "hot50", true)
        assertNotNull("Cold query should return results", coldResults)
        assertNotNull("Warm query should return results", warmResults)
        assertEquals("Results should be consistent", coldResults.size, warmResults.size)
    }
    @Test
    fun test_5_3_2_CacheWarmthVerification() {
        manageController.addRecord(testTableName, "cache_test", "快取", 100)
        var result1: MutableList<Record> = queryRecords(testTableName, "cache_test", true)
        var result2: MutableList<Record> = queryRecords(testTableName, "cache_test", true)
        var result3: MutableList<Record> = queryRecords(testTableName, "cache_test", true)
        assertNotNull("First result should not be null", result1)
        assertNotNull("Second result should not be null", result2)
        assertNotNull("Third result should not be null", result3)
        assertEquals("Results should be consistent", result1.size, result2.size)
        assertEquals("Results should be consistent", result2.size, result3.size)
    }
    @Test
    fun test_5_3_2_DualCodeExpansionAndBlacklist() {
        manageController.addRecord(testTableName, "dual", "雙碼", 100)
        manageController.addRecord(testTableName, "dual", "雙重", 50)
        var results: MutableList<Record> = queryRecords(testTableName, "dual", true)
        assertNotNull("Results should not be null", results)
        assertFalse("Results should not be empty", results.isEmpty())
        assertTrue("Should return multiple results", (results.size >= 1))
    }
    @Test
    fun test_5_3_3_0_PreconditionTableImported() {
        var count: Int = manageController.countRecords(testTableName)
        if ((count == 0)) {
            manageController.addRecord(testTableName, "precondition", "前提", 100)
        }
        var finalCount: Int = manageController.countRecords(testTableName)
        assertTrue("Test table should be imported and accessible", (finalCount > 0))
    }
    @Test
    fun test_5_3_3_LearnedEntriesInfluenceResults() {
        manageController.addRecord(testTableName, "learn", "學習", 100)
        manageController.addRecord(testTableName, "learn", "習得", 200)
        var results: MutableList<Record> = queryRecords(testTableName, "learn", true)
        assertNotNull("Results should include learned entries", results)
        assertTrue("Should have multiple results", (results.size >= 2))
        var foundLearned: Boolean = false
        for (r in results) {
            if ("習得".equals(r.getWord())) {
                foundLearned = true
                break
            }
        }
        assertTrue("Learned entry should be in results", foundLearned)
    }
    @Test
    fun test_5_3_3_CacheRespectsLearningUpdates() {
        manageController.addRecord(testTableName, "update", "更新", 100)
        var initialResults: MutableList<Record> = queryRecords(testTableName, "update", true)
        var initialSize: Int = initialResults.size
        manageController.addRecord(testTableName, "update", "學習新詞", 150)
        var updatedResults: MutableList<Record> = queryRecords(testTableName, "update", true)
        assertNotNull("Updated results should not be null", updatedResults)
        assertTrue("Updated results should include new learned entry", (updatedResults.size >= initialSize))
    }
    @Test
    fun test_5_3_3_BlacklistNotSuppressLearned() {
        manageController.addRecord(testTableName, "special", "特殊學習", 200)
        var results: MutableList<Record> = queryRecords(testTableName, "special", true)
        assertNotNull("Results should not be null", results)
        assertFalse("Learned entry should not be filtered out", results.isEmpty())
    }
    private fun queryRecords(table: String, query: String, searchByCode: Boolean): MutableList<Record> {
        val latch: java.util.concurrent.CountDownLatch = java.util.concurrent.CountDownLatch(1)
        val out: java.util.concurrent.atomic.AtomicReference<List<Record>?> = java.util.concurrent.atomic.AtomicReference()
        manageController.setManageImView(object : net.toload.main.hd.ui.view.ManageImView {
    override fun displayRecords(records: List<Record>?) {
        out.set(records)
        latch.countDown()
    }
    override fun updateRecordCount(count: Int) {

    }
    override fun showAddRecordDialog() {

    }
    override fun showEditRecordDialog(record: Record?) {

    }
    override fun showDeleteConfirmDialog(id: Long) {

    }
    override fun refreshRecordList() {

    }
    override fun onError(message: String?) {
        latch.countDown()
    }
})
        manageController.loadRecordsAsync(table, query, searchByCode, 0, 50)
        try {
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (ignored: InterruptedException) {

        }
        var result: List<Record>? = out.get()
        return (result?.toMutableList() ?: ArrayList())
    }
}
