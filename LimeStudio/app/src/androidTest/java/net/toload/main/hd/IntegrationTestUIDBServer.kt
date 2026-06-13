@file:Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST", "TYPE_INTERSECTION_AS_REIFIED")

package net.toload.main.hd

import org.junit.Assert.*
import android.content.ContentValues
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.toload.main.hd.global.LIME
import net.toload.main.hd.global.LIMEProgressListener
import net.toload.main.hd.limedb.LimeDB
import net.toload.main.hd.ui.controller.SetupImController
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
open class IntegrationTestUIDBServer {
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
        if (((setupController != null) && (testTableName != null))) {
            setupController.clearTable(testTableName, false)
        }
    }
    @Test
    fun test_5_4_CompleteFileOperationFlow_Export() {
        addTestRecord(testTableName, "file", "檔案", 100)
        addTestRecord(testTableName, "file", "文件", 50)
        var exportFile: File = File(context.getFilesDir(), (("test_ui_export_" + System.currentTimeMillis()) + ".zip"))
        try {
            var result: File = setupController.exportZippedDb(testTableName, exportFile, null)!!
            assertNotNull("Export should succeed", result)
            assertTrue("Export file should be created", exportFile.exists())
            assertTrue("Export file should have content", (exportFile.length > 0))
        } finally {
            if (exportFile.exists()) {
                exportFile.delete()
            }
        }
    }
    @Test
    fun test_5_4_CompleteFileOperationFlow_Import() {
        addTestRecord(testTableName, "import", "匯入", 100)
        var exportFile: File = File(context.getFilesDir(), (("test_ui_import_" + System.currentTimeMillis()) + ".zip"))
        try {
            setupController.exportZippedDb(testTableName, exportFile, null)
            assertTrue("Export file should exist", exportFile.exists())
            setupController.clearTable(testTableName, false)
            setupController.importZippedDb(exportFile, testTableName, false)
            var count: Int = manageController.countRecords(testTableName)
            assertTrue("Imported table should have records", (count > 0))
        } finally {
            if (exportFile.exists()) {
                exportFile.delete()
            }
        }
    }
    @Test
    fun test_5_4_FileOperationsAndDatabaseUpdates() {
        addTestRecord(testTableName, "update", "更新", 100)
        var beforeCount: Int = manageController.countRecords(testTableName)
        var exportFile: File = File(context.getFilesDir(), (("test_db_update_" + System.currentTimeMillis()) + ".zip"))
        try {
            setupController.exportZippedDb(testTableName, exportFile, null)
            addTestRecord(testTableName, "update", "修改", 50)
            var afterAddCount: Int = manageController.countRecords(testTableName)
            assertTrue("Count should increase after add", (afterAddCount > beforeCount))
            setupController.importZippedDb(exportFile, testTableName, false)
            var finalCount: Int = manageController.countRecords(testTableName)
            assertTrue("Database should be updated", (finalCount > 0))
        } finally {
            if (exportFile.exists()) {
                exportFile.delete()
            }
        }
    }
    @Test
    fun test_5_4_ProgressCallbacks() {
        run {
            var i: Int = 0
            while ((i < 50)) {
                addTestRecord(testTableName, ("progress" + i), ("進度" + i), (100 - i))
                i++
            }
        }
        var exportFile: File = File(context.getFilesDir(), (("test_progress_" + System.currentTimeMillis()) + ".zip"))
        try {
            var result: File = setupController.exportZippedDb(testTableName, exportFile, null)!!
            assertNotNull("Export should succeed", result)
        } finally {
            if (exportFile.exists()) {
                exportFile.delete()
            }
        }
    }
    @Test
    fun test_5_4_MultipleFileOperationsSequence() {
        addTestRecord(testTableName, "multi", "多重", 100)
        var exportFile1: File = File(context.getFilesDir(), (("test_multi_1_" + System.currentTimeMillis()) + ".zip"))
        var exportFile2: File = File(context.getFilesDir(), (("test_multi_2_" + System.currentTimeMillis()) + ".zip"))
        try {
            var result1: File = setupController.exportZippedDb(testTableName, exportFile1, null)!!
            assertNotNull("First export should succeed", result1)
            addTestRecord(testTableName, "multi", "序列", 50)
            var result2: File = setupController.exportZippedDb(testTableName, exportFile2, null)!!
            assertNotNull("Second export should succeed", result2)
            assertTrue("First export file should exist", exportFile1.exists())
            assertTrue("Second export file should exist", exportFile2.exists())
            assertTrue("First file should have content", (exportFile1.length > 0))
            assertTrue("Second file should have content", (exportFile2.length > 0))
        } finally {
            if (exportFile1.exists()) {
                exportFile1.delete()
            }
            if (exportFile2.exists()) {
                exportFile2.delete()
            }
        }
    }
    @Test
    fun test_5_4_ErrorHandlingInFileOperations() {
        var invalidFile: File = File("/invalid/path/cannot/write/here.zip")
        try {
            var result: File = setupController.exportZippedDb(testTableName, invalidFile, null)!!
            assertNull("Export to invalid path should return null", result)
        } catch (e: Exception) {
            assertNotNull("Exception should be handled", e)
        }
    }
    @Test
    fun test_5_4_RelatedTableFileOperations() {
        manageController.addRelatedPhrase("UI測試", "測試UI", 100)
        var exportFile: File = File(context.getFilesDir(), (("test_ui_related_" + System.currentTimeMillis()) + ".zip"))
        try {
            var exportResult: File = setupController.exportZippedDbRelated(exportFile, null)!!
            assertNotNull("Export related should succeed", exportResult)
            assertTrue("Export file should exist", exportFile.exists())
            setupController.clearTable(LIME.DB_TABLE_RELATED, false)
            setupController.importZippedDbRelated(exportFile)
            var count: Int = manageController.countRecords(LIME.DB_TABLE_RELATED)
            assertTrue("Related records should be restored", (count > 0))
        } finally {
            if (exportFile.exists()) {
                exportFile.delete()
            }
        }
    }
    private fun addTestRecord(table: String, code: String, word: String, score: Int) {
        manageController.addRecord(table, code, word, score)
    }
}
