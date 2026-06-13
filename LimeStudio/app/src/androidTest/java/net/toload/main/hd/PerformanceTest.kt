package net.toload.main.hd

import org.junit.Assert.*
import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.toload.main.hd.data.Mapping
import net.toload.main.hd.data.Record
import net.toload.main.hd.global.LIME
import net.toload.main.hd.limedb.LimeDB
import net.toload.main.hd.ui.controller.ManageImController
import net.toload.main.hd.ui.controller.SetupImController
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
open class PerformanceTest {
    companion object {
        private lateinit var staticContext: Context
        private lateinit var staticSetupController: SetupImController
        private lateinit var staticDbServer: DBServer
        private lateinit var staticSearchServer: SearchServer
        private lateinit var realImTablePhonetic: String
        private lateinit var realImTableDayi: String
        private var imTablesReady: Boolean = false
        private val COUNT_OPERATION_THRESHOLD: Long = 100
        private val SEARCH_OPERATION_THRESHOLD: Long = 50
        private val BACKUP_OPERATION_THRESHOLD: Long = 3000
        private val EXPORT_OPERATION_THRESHOLD: Long = 3000
        private val IMPORT_OPERATION_THRESHOLD: Long = 5000
        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            staticContext = InstrumentationRegistry.getInstrumentation().getTargetContext()
            staticSearchServer = SearchServer(staticContext)
            staticDbServer = DBServer.getInstance(staticContext)!!
            staticSetupController = SetupImController(staticContext, staticDbServer, staticSearchServer)
            var tempController: ManageImController = ManageImController(staticSearchServer)
            var phoneticCount: Int = tempController.countRecords(LIME.IM_PHONETIC)
            if ((phoneticCount == 0)) {
                staticSetupController.clearTable(LIME.IM_PHONETIC, false)
                downloadCloudDbAndImport(LIME.IM_PHONETIC, LIME.DATABASE_CLOUD_IM_PHONETIC)
            }
            var dayiCount: Int = tempController.countRecords(LIME.IM_DAYI)
            if ((dayiCount == 0)) {
                staticSetupController.clearTable(LIME.IM_DAYI, false)
                downloadCloudDbAndImport(LIME.IM_DAYI, LIME.DATABASE_CLOUD_IM_DAYI)
            }
            realImTablePhonetic = LIME.IM_PHONETIC
            realImTableDayi = LIME.IM_DAYI
            var finalPhoneticCount: Int = tempController.countRecords(LIME.IM_PHONETIC)
            var finalDayiCount: Int = tempController.countRecords(LIME.IM_DAYI)
            assertTrue("PHONETIC table should have records", (finalPhoneticCount > 0))
            assertTrue("DAYI table should have records", (finalDayiCount > 0))
            imTablesReady = true
        }
        @JvmStatic
        fun downloadCloudDbAndImport(tableName: String, url: String) {
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
                staticDbServer.importZippedDb(tmpFile, tableName)
                var recordCount: Int = staticSearchServer.countRecords(tableName)
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
    private lateinit var limeDB: LimeDB
    private lateinit var dbServer: DBServer
    private lateinit var searchServer: SearchServer
    private lateinit var testTableName: String
    @Before
    fun setUp() {
        assertTrue("IM tables must be ready", imTablesReady)
        context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        limeDB = LimeDB(context)
        dbServer = DBServer.getInstance(context)!!
        searchServer = SearchServer(context)
        testTableName = ("performance_test_" + System.currentTimeMillis())
    }
    @After
    fun tearDown() {

    }
    @Test
    fun test_9_1_1_benchmarkCountOperations() {
        var tableName: String = realImTablePhonetic
        var expectedCount: Int = searchServer.countRecords(tableName)
        assertTrue("Table should have records", (expectedCount > 0))
        run {
            var i: Int = 0
            while ((i < 5)) {
                searchServer.countRecords(tableName)
                i++
            }
        }
        var startTime: Long = SystemClock.elapsedRealtime()
        var iterations: Int = 100
        run {
            var i: Int = 0
            while ((i < iterations)) {
                var count: Int = searchServer.countRecords(tableName)
                assertEquals("Count should match expected size", expectedCount, count)
                i++
            }
        }
        var elapsedTime: Long = (SystemClock.elapsedRealtime() - startTime)
        var averageTime: Long = (elapsedTime / iterations)
        System.out.println("Count Operations Benchmark (Real-world PHONETIC data):")
        System.out.println(("  Table: " + tableName))
        System.out.println(("  Record count: " + expectedCount))
        System.out.println((("  Total time: " + elapsedTime) + "ms"))
        System.out.println((("  Average time per operation: " + averageTime) + "ms"))
        System.out.println(("  Operations per second: " + (1000.0 / averageTime)))
        assertTrue((((("Count operation should complete within threshold (" + COUNT_OPERATION_THRESHOLD) + "ms), actual: ") + averageTime) + "ms"), (averageTime < COUNT_OPERATION_THRESHOLD))
    }
    @Test
    fun test_9_1_2_benchmarkSearchOperations() {
        var tableName: String = realImTableDayi
        var searchCode: String = "a"
        run {
            var i: Int = 0
            while ((i < 5)) {
                searchServer.getRecords(tableName, searchCode, true, 10, 0)
                i++
            }
        }
        var startTime: Long = SystemClock.elapsedRealtime()
        var iterations: Int = 50
        run {
            var i: Int = 0
            while ((i < iterations)) {
                var results: MutableList<Record> = searchServer.getRecords(tableName, searchCode, true, 10, 0)
                assertNotNull("Search results should not be null", results)
                i++
            }
        }
        var searchServerTime: Long = (SystemClock.elapsedRealtime() - startTime)
        var searchServerAverage: Long = (searchServerTime / iterations)
        startTime = SystemClock.elapsedRealtime()
        run {
            var i: Int = 0
            while ((i < iterations)) {
                var results: MutableList<Record> = limeDB.getRecordList(tableName, searchCode, true, 10, 0)
                assertNotNull("Search results should not be null", results)
                i++
            }
        }
        var limeDBTime: Long = (SystemClock.elapsedRealtime() - startTime)
        var limeDBAverage: Long = (limeDBTime / iterations)
        System.out.println("Search Operations Benchmark (Real-world DAYI data):")
        System.out.println(("  Table: " + tableName))
        System.out.println((("  Search code: '" + searchCode) + "'"))
        System.out.println((("  SearchServer average: " + searchServerAverage) + "ms"))
        System.out.println((("  LimeDB average: " + limeDBAverage) + "ms"))
        System.out.println((("  Overhead: " + (searchServerAverage - limeDBAverage)) + "ms"))
        assertTrue((((("Search operation should complete within threshold (" + SEARCH_OPERATION_THRESHOLD) + "ms), actual: ") + searchServerAverage) + "ms"), (searchServerAverage < SEARCH_OPERATION_THRESHOLD))
        if ((limeDBAverage > 10)) {
            var overhead: Double = ((((searchServerAverage - limeDBAverage) as Double) / limeDBAverage) * 100)
            assertTrue((("SearchServer overhead should be less than 20%, actual: " + overhead) + "%"), (overhead < 20))
        } else {
            System.out.println((("  Note: LimeDB search too fast (" + limeDBAverage) + "ms) to measure overhead accurately"))
        }
    }
    @Test
    fun test_9_1_3_benchmarkBackupImportOperations() {
        var tableName: String = realImTablePhonetic
        var backupFile: File = File(context.getFilesDir(), "benchmark_backup.lime")
        dbServer.exportZippedDb(tableName, backupFile, null)
        if (backupFile.exists()) {
            backupFile.delete()
        }
        var startTime: Long = SystemClock.elapsedRealtime()
        var iterations: Int = 10
        run {
            var i: Int = 0
            while ((i < iterations)) {
                var result: File = dbServer.exportZippedDb(tableName, backupFile, null)!!
                assertNotNull("Backup should succeed", result)
                assertTrue("Backup file should exist", backupFile.exists())
                backupFile.delete()
                i++
            }
        }
        var backupTime: Long = (SystemClock.elapsedRealtime() - startTime)
        var backupAverage: Long = (backupTime / iterations)
        dbServer.exportZippedDb(tableName, backupFile, null)
        var expectedCount: Int = searchServer.countRecords(tableName)
        startTime = SystemClock.elapsedRealtime()
        run {
            var i: Int = 0
            while ((i < iterations)) {
                dbServer.importZippedDb(backupFile, tableName)
                var count: Int = searchServer.countRecords(tableName)
                assertTrue("Imported table should have records", (count > 0))
                assertEquals("Import should restore all records", expectedCount, count)
                i++
            }
        }
        var importTime: Long = (SystemClock.elapsedRealtime() - startTime)
        var importAverage: Long = (importTime / iterations)
        System.out.println("Backup/Import Operations Benchmark (Real-world PHONETIC data):")
        System.out.println(("  Table: " + tableName))
        System.out.println((("  Backup average: " + backupAverage) + "ms"))
        System.out.println((("  Import average: " + importAverage) + "ms"))
        System.out.println((("  Total average: " + (backupAverage + importAverage)) + "ms"))
        assertTrue((((("Backup operation should complete within threshold (" + BACKUP_OPERATION_THRESHOLD) + "ms), actual: ") + backupAverage) + "ms"), (backupAverage < BACKUP_OPERATION_THRESHOLD))
        assertTrue((((("Import operation should complete within threshold (" + BACKUP_OPERATION_THRESHOLD) + "ms), actual: ") + importAverage) + "ms"), (importAverage < BACKUP_OPERATION_THRESHOLD))
        if (backupFile.exists()) {
            backupFile.delete()
        }
    }
    @Test
    fun test_9_2_1_benchmarkExportOperations() {
        var tableName: String = realImTablePhonetic
        var exportFile: File = File(context.getFilesDir(), "benchmark_export.lime")
        if (exportFile.exists()) {
            exportFile.delete()
        }
        dbServer.exportZippedDb(tableName, exportFile, null)
        if (exportFile.exists()) {
            exportFile.delete()
        }
        var startTime: Long = SystemClock.elapsedRealtime()
        var iterations: Int = 5
        run {
            var i: Int = 0
            while ((i < iterations)) {
                var result: File = dbServer.exportZippedDb(tableName, exportFile, null)!!
                assertNotNull("Export should succeed", result)
                assertTrue("Export file should be created", exportFile.exists())
                exportFile.delete()
                i++
            }
        }
        var elapsedTime: Long = (SystemClock.elapsedRealtime() - startTime)
        var averageTime: Long = (elapsedTime / iterations)
        System.out.println("Export Operations Benchmark (Real-world PHONETIC data):")
        System.out.println(("  Table: " + tableName))
        System.out.println((("  Total time: " + elapsedTime) + "ms"))
        System.out.println((("  Average time per operation: " + averageTime) + "ms"))
        assertTrue((((("Export operation should complete within threshold (" + EXPORT_OPERATION_THRESHOLD) + "ms), actual: ") + averageTime) + "ms"), (averageTime < EXPORT_OPERATION_THRESHOLD))
        if (exportFile.exists()) {
            exportFile.delete()
        }
    }
    @Test
    fun test_9_2_2_benchmarkImportOperations() {
        var sourceTable: String = realImTableDayi
        var expectedCount: Int = searchServer.countRecords(sourceTable)
        var importFile: File = File(context.getFilesDir(), "benchmark_import.lime")
        dbServer.exportZippedDb(sourceTable, importFile, null)
        assertTrue("Import file should be created", importFile.exists())
        dbServer.importZippedDb(importFile, sourceTable)
        var startTime: Long = SystemClock.elapsedRealtime()
        var iterations: Int = 5
        run {
            var i: Int = 0
            while ((i < iterations)) {
                dbServer.importZippedDb(importFile, sourceTable)
                var count: Int = searchServer.countRecords(sourceTable)
                assertEquals("Imported table should have correct record count", expectedCount, count)
                i++
            }
        }
        var elapsedTime: Long = (SystemClock.elapsedRealtime() - startTime)
        var averageTime: Long = (elapsedTime / iterations)
        System.out.println("Import Operations Benchmark (Real-world DAYI data):")
        System.out.println(("  Source table: " + sourceTable))
        System.out.println(("  Record count: " + expectedCount))
        System.out.println((("  Total time: " + elapsedTime) + "ms"))
        System.out.println((("  Average time per operation: " + averageTime) + "ms"))
        assertTrue((((("Import operation should complete within threshold (" + IMPORT_OPERATION_THRESHOLD) + "ms), actual: ") + averageTime) + "ms"), (averageTime < IMPORT_OPERATION_THRESHOLD))
        if (importFile.exists()) {
            importFile.delete()
        }
    }
    @Test
    fun test_9_3_1_testMemoryLeaks() {
        var tableName: String = realImTablePhonetic
        var runtime: Runtime = Runtime.getRuntime()
        runtime.gc()
        SystemClock.sleep(100)
        var initialMemory: Long = (runtime.totalMemory() - runtime.freeMemory())
        var iterations: Int = 100
        run {
            var i: Int = 0
            while ((i < iterations)) {
                var results: MutableList<Record> = searchServer.getRecords(tableName, "a", true, 10, 0)
                assertNotNull("Search results should not be null", results)
                var count: Int = searchServer.countRecords(tableName)
                assertTrue("Count should be positive", (count > 0))
                var values: android.content.ContentValues = android.content.ContentValues()
                values.put("code", ("leak_test_" + i))
                values.put("word", ("漏洞測試" + i))
                values.put("score", 100)
                values.put("basescore", 100)
                searchServer.addRecord(tableName, values)
                values.put("score", 200)
                searchServer.addRecord(tableName, values)
                if (((i % 10) == 0)) {
                    runtime.gc()
                    SystemClock.sleep(50)
                }
                i++
            }
        }
        runtime.gc()
        SystemClock.sleep(100)
        var finalMemory: Long = (runtime.totalMemory() - runtime.freeMemory())
        var memoryIncrease: Long = (finalMemory - initialMemory)
        System.out.println("Memory Leak Test (Real-world PHONETIC data):")
        System.out.println(("  Table: " + tableName))
        System.out.println((("  Initial memory: " + (initialMemory / 1024)) + " KB"))
        System.out.println((("  Final memory: " + (finalMemory / 1024)) + " KB"))
        System.out.println((("  Memory increase: " + (memoryIncrease / 1024)) + " KB"))
        System.out.println(("  Iterations: " + iterations))
        var maxMemoryIncrease: Long = ((5 * 1024) * 1024)
        assertTrue((((("Memory increase should be less than " + ((maxMemoryIncrease / 1024) / 1024)) + "MB, actual: ") + ((memoryIncrease / 1024) / 1024)) + "MB"), (memoryIncrease < maxMemoryIncrease))
    }
}
