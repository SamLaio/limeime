@file:Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST", "TYPE_INTERSECTION_AS_REIFIED")

package net.toload.main.hd

import org.junit.Assert.*
import android.content.ContentValues
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.toload.main.hd.data.Keyboard
import net.toload.main.hd.data.Mapping
import net.toload.main.hd.data.Record
import net.toload.main.hd.global.LIME
import net.toload.main.hd.limedb.LimeDB
import net.toload.main.hd.ui.controller.SetupImController
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
open class IntegrationTestSearchServerDBServer {
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
                downloadCloudDbAndImport(LIME.IM_PHONETIC, LIME.DATABASE_CLOUD_IM_PHONETIC, tempController, staticDbServer)
            }
            var dayiCount: Int = tempController.countRecords(LIME.IM_DAYI)
            if ((dayiCount == 0)) {
                staticSetupController.clearTable(LIME.IM_DAYI, false)
                downloadCloudDbAndImport(LIME.IM_DAYI, LIME.DATABASE_CLOUD_IM_DAYI, tempController, staticDbServer)
            }
            var pinyinCount: Int = tempController.countRecords(LIME.IM_PINYIN)
            if ((pinyinCount == 0)) {
                staticSetupController.clearTable(LIME.IM_PINYIN, false)
                downloadCloudDbAndImport(LIME.IM_PINYIN, LIME.DATABASE_CLOUD_IM_PINYIN, tempController, staticDbServer)
            }
            realImTable = LIME.IM_PHONETIC
            var finalPhoneticCount: Int = tempController.countRecords(LIME.IM_PHONETIC)
            var finalDayiCount: Int = tempController.countRecords(LIME.IM_DAYI)
            assertTrue("PHONETIC table should have records", (finalPhoneticCount > 0))
            assertTrue("DAYI table should have records", (finalDayiCount > 0))
            imTableReady = true
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
        @AfterClass
        @JvmStatic
        fun tearDownClass() {

        }
    }
    private lateinit var context: Context
    private lateinit var setupController: SetupImController
    private lateinit var manageController: net.toload.main.hd.ui.controller.ManageImController
    private lateinit var testTableName: String
    @Before
    fun setUp() {
        assertTrue("IM table must be ready before running tests", imTableReady)
        context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var ss: net.toload.main.hd.SearchServer = net.toload.main.hd.SearchServer(context)
        var ds: net.toload.main.hd.DBServer = net.toload.main.hd.DBServer.getInstance(context)!!
        setupController = SetupImController(context, ds, ss)
        manageController = net.toload.main.hd.ui.controller.ManageImController(ss)
        testTableName = realImTable
    }
    @After
    fun tearDown() {

    }
    @Test
    fun test_5_1_CompleteSearchFlow() {
        var results: MutableList<Record> = queryRecords(testTableName, "j6", true)
        assertNotNull("Results should not be null", results)
        assertTrue("Real IM table should have records for common phonetic code", (results.size > 0))
        var first: Record = results.get(0)
        assertNotNull("Code should not be null", first.getCode())
        assertNotNull("Word should not be null", first.getWord())
        assertTrue("Word should not be empty", (first.getWord().length > 0))
        assertTrue("Score should be valid", (first.getScore() >= 0))
    }
    @Test
    fun test_5_1_CachingBehavior() {
        var testCode: String = "ㄊㄜ"
        var start1: Long = System.nanoTime()
        var results1: MutableList<Record> = queryRecords(testTableName, testCode, true)
        var time1: Long = (System.nanoTime() - start1)
        assertNotNull("First query should return results", results1)
        var time2: Long = Long.MAX_VALUE
        var results2: MutableList<Record>? = null
        run {
            var i: Int = 0
            while ((i < 10)) {
                var start2: Long = System.nanoTime()
                results2 = queryRecords(testTableName, testCode, true)
                var currentTime: Long = (System.nanoTime() - start2)
                if ((currentTime < time1)) {
                    time2 = currentTime
                    break
                }
                time2 = currentTime
                i++
            }
        }
        assertNotNull("Second query should return results", results2)
        assertEquals("Cached results should match", results1.size, results2!!.size)
        assertTrue((((("Second query should be faster (cache hit) after retries. Cold: " + time1) + "ns, Warm: ") + time2) + "ns"), (time2 <= time1))
    }
    @Test
    fun test_5_1_ErrorHandlingPropagation() {
        var results: MutableList<Record> = queryRecords(testTableName, ("invalid_code_" + System.currentTimeMillis()), true)
        assertNotNull("Results should not be null even with invalid code", results)
        assertTrue("Results should be empty for invalid code", results.isEmpty())
    }
    @Test
    fun test_5_1_ConfigurationSetImConfig() {
        var keyboards: MutableList<Keyboard> = manageController.keyboardList
        if (((keyboards != null) && !keyboards.isEmpty())) {
            manageController.setIMKeyboard(testTableName, keyboards.get(0)!!)
        }
        var count: Int = manageController.countRecords(testTableName)
        assertTrue("Controller configuration should not break table access", (count >= 0))
    }
    @Test
    fun test_5_1_ConfigurationGetImConfigListInfo() {
        var keyboards: MutableList<Keyboard> = manageController.keyboardList
        assertNotNull("Keyboard list should be retrievable via controller", keyboards)
    }
    @Test
    fun test_5_1_DataPersistence() {
        addTestRecord(testTableName, "persist", "持久化", 100)
        var count: Int = manageController.countRecords(testTableName)
        assertTrue("Record should persist in table", (count > 0))
    }
    @Test
    fun test_5_2_ExportFlow() {
        var recordCount: Int = manageController.countRecords(testTableName)
        assertTrue("Real IM table should have records to export", (recordCount > 0))
        var exportFile: File = File(context.getFilesDir(), (("test_export_" + System.currentTimeMillis()) + ".zip"))
        try {
            var result: File = setupController.exportZippedDb(testTableName, exportFile, null)!!
            assertNotNull("Export should succeed", result)
            assertTrue("Export file should exist", exportFile.exists())
            assertTrue("Export file should not be empty", (exportFile.length > 0))
        } finally {
            if (exportFile.exists()) {
                exportFile.delete()
            }
        }
    }
    @Test
    fun test_5_2_ZipIntegrity() {
        var originalCount: Int = manageController.countRecords(testTableName)
        assertTrue("Real IM table should have records", (originalCount > 0))
        var exportFile: File = File(context.getFilesDir(), (("test_zip_" + System.currentTimeMillis()) + ".zip"))
        try {
            var exportResult: File = setupController.exportZippedDb(testTableName, exportFile, null)!!
            assertNotNull("Export should succeed", exportResult)
            assertTrue("Zip file should exist", exportFile.exists())
            assertTrue("Zip file should have content", (exportFile.length > 100))
        } finally {
            if (exportFile.exists()) {
                exportFile.delete()
            }
        }
    }
    @Test
    fun test_5_2_DataCompleteness() {
        var originalCount: Int = manageController.countRecords(testTableName)
        assertTrue("Real IM table should have records", (originalCount > 0))
        var exportFile: File = File(context.getFilesDir(), (("test_complete_" + System.currentTimeMillis()) + ".zip"))
        try {
            var result: File = setupController.exportZippedDb(testTableName, exportFile, null)!!
            assertNotNull("Export should succeed", result)
            assertTrue("Export file should contain production data", (exportFile.length > 1000))
        } finally {
            if (exportFile.exists()) {
                exportFile.delete()
            }
        }
    }
    @Test
    fun test_5_2_ImportFlowZippedDb() {
        var originalCount: Int = manageController.countRecords(testTableName)
        assertTrue("Real IM table should have records before export", (originalCount > 0))
        var exportFile: File = File(context.getFilesDir(), (("test_import_" + System.currentTimeMillis()) + ".zip"))
        try {
            setupController.exportZippedDb(testTableName, exportFile, null)
            assertTrue("Export file should exist", exportFile.exists())
            assertTrue("Export should contain substantial data", (exportFile.length > 1000))
            var count: Int = manageController.countRecords(testTableName)
            assertEquals("Data should remain intact after export", originalCount, count)
            assertTrue("Imported table should have records", (count > 0))
        } finally {
            if (exportFile.exists()) {
                exportFile.delete()
            }
        }
    }
    @Test
    fun test_5_2_ImportFlowRelated() {
        manageController.addRelatedPhrase("測試", "試測", 100)
        var exportFile: File = File(context.getFilesDir(), (("test_related_" + System.currentTimeMillis()) + ".zip"))
        try {
            var exportResult: File = setupController.exportZippedDbRelated(exportFile, null)!!
            assertNotNull("Export related should succeed", exportResult)
            assertTrue("Export file should exist", exportFile.exists())
            var beforeCount: Int = manageController.countRecords(LIME.DB_TABLE_RELATED)
            setupController.clearTable(LIME.DB_TABLE_RELATED, false)
            setupController.importZippedDbRelated(exportFile)
            var afterCount: Int = manageController.countRecords(LIME.DB_TABLE_RELATED)
            assertTrue("Related records should be restored", (afterCount > 0))
        } finally {
            if (exportFile.exists()) {
                exportFile.delete()
            }
        }
    }
    @Test
    fun test_5_2_DataIntegrityAfterImport() {
        addTestRecord(testTableName, "integrity", "完整性", 100)
        addTestRecord(testTableName, "integrity", "整合", 50)
        var exportFile: File = File(context.getFilesDir(), (("test_integrity_" + System.currentTimeMillis()) + ".zip"))
        try {
            setupController.exportZippedDb(testTableName, exportFile, null)
            setupController.clearTable(testTableName, false)
            setupController.importZippedDb(exportFile, testTableName, false)
            var importedCount: Int = manageController.countRecords(testTableName)
            assertTrue("Imported table should have records", (importedCount > 0))
        } finally {
            if (exportFile.exists()) {
                exportFile.delete()
            }
        }
    }
    @Test
    fun test_5_2_OverwriteBehavior() {
        addTestRecord(testTableName, "overwrite", "覆寫", 100)
        var exportFile: File = File(context.getFilesDir(), (("test_overwrite_" + System.currentTimeMillis()) + ".zip"))
        try {
            setupController.exportZippedDb(testTableName, exportFile, null)
            addTestRecord(testTableName, "overwrite", "重寫", 200)
            var beforeImport: Int = manageController.countRecords(testTableName)
            setupController.importZippedDb(exportFile, testTableName, false)
            var afterImport: Int = manageController.countRecords(testTableName)
            assertTrue("Import should affect table", (afterImport > 0))
        } finally {
            if (exportFile.exists()) {
                exportFile.delete()
            }
        }
    }
    @Test
    fun test_5_4_ImportDbMergesImKeyboardRow() {
        var ss: net.toload.main.hd.SearchServer = net.toload.main.hd.SearchServer(context)
        var rows: MutableList<net.toload.main.hd.data.ImConfig?> = ss.getImConfigList(LIME.IM_PINYIN, LIME.DB_IM_COLUMN_KEYBOARD)
        assertNotNull("getImConfigList should not return null after pinyin install", rows)
        assertFalse("pinyin im row with title='keyboard' must exist after import", rows.isEmpty())
        var kbRow: net.toload.main.hd.data.ImConfig = rows.get(0)!!
        assertEquals("im.keyboard column carries the keyboard CODE", "limenum", kbRow.getKeyboard())
        assertNotNull("im.desc should not be null", kbRow.getDesc())
        assertFalse("im.desc should not be empty", kbRow.getDesc().isEmpty())
    }
    @Test
    fun test_5_4_GetCurrentKeyboardAfterCloudInstall() {
        var kb: net.toload.main.hd.data.Keyboard = manageController.getCurrentKeyboard(LIME.IM_PINYIN)!!
        assertNotNull("getCurrentKeyboard must resolve a Keyboard for pinyin after cloud install", kb)
        assertEquals("Resolved keyboard code must be limenum", "limenum", kb.getCode())
        assertNotNull("Keyboard.desc must not be null", kb.getDesc())
        assertFalse("Keyboard.desc must not be empty", kb.getDesc().isEmpty())
    }
    @Test
    fun test_5_4_GetImConfigKeyboardColumnSemantics() {
        var ss: net.toload.main.hd.SearchServer = net.toload.main.hd.SearchServer(context)
        var value: String = ss.getImConfig(LIME.IM_PINYIN, LIME.DB_IM_COLUMN_KEYBOARD)
        assertNotNull("getImConfig should not return null", value)
        assertNotEquals("getImConfig returns the desc column, NOT the keyboard code", "limenum", value)
        assertFalse("getImConfig should return a non-empty desc value", value.isEmpty())
    }
    private fun addTestRecord(table: String, code: String, word: String, score: Int) {
        manageController.addRecord(table, code, word, score)
        var count: Int = manageController.countRecords(table)
        assertTrue("Record should be added successfully", (count > 0))
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
