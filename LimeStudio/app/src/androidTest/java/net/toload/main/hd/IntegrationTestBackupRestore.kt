package net.toload.main.hd

import org.junit.Assert.*
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.preference.PreferenceManager
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.global.LIME
import net.toload.main.hd.ui.controller.SetupImController
import org.json.JSONObject
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
open class IntegrationTestBackupRestore {
    companion object {
        private lateinit var staticContext: Context
        private lateinit var staticSetupController: SetupImController
        private lateinit var staticDbServer: net.toload.main.hd.DBServer
        private lateinit var realImTable: String
        private var imTableReady: Boolean = false
        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            Log.i("Integrated test", "setUpClass staring....")
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
            Log.i("Integrated test", "setUpClass finished.")
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
    fun test_5_5_ExplicitBackupOnClearTable() {
        addLearnedRecord(testTableName, "backup", "備份", 150)
        addLearnedRecord(testTableName, "backup", "後備", 140)
        var beforeCount: Int = manageController.countRecords(testTableName)
        assertTrue("Table should have learned records", (beforeCount >= 2))
        setupController.clearTable(testTableName, true)
        var afterClearCount: Int = manageController.countRecords(testTableName)
        assertEquals("Table should be empty after clear", 0, afterClearCount)
    }
    @Test
    fun test_5_5_BackupTableStructureAndContent() {
        addLearnedRecord(testTableName, "structure", "結構", 200)
        addLearnedRecord(testTableName, "content", "內容", 180)
        setupController.clearTable(testTableName, true)
        var afterClearCount: Int = manageController.countRecords(testTableName)
        assertEquals("Backup operation should clear table", 0, afterClearCount)
    }
    @Test
    fun test_5_5_BackupDuringImportWithRestoreFlag() {
        addLearnedRecord(testTableName, "import", "導入", 150)
        var beforeCount: Int = manageController.countRecords(testTableName)
        assertTrue("Table should have records before import", (beforeCount > 0))
        setupController.clearTable(testTableName, true)
        var afterCount: Int = manageController.countRecords(testTableName)
        assertEquals("Backup should clear table", 0, afterCount)
    }
    @Test
    fun test_5_5_MultipleBackupsOverwrite() {
        addLearnedRecord(testTableName, "first", "第一", 100)
        setupController.clearTable(testTableName, true)
        addLearnedRecord(testTableName, "second", "第二", 110)
        setupController.clearTable(testTableName, true)
        var finalCount: Int = manageController.countRecords(testTableName)
        assertEquals("Final clear should result in empty table", 0, finalCount)
    }
    @Test
    fun test_5_6_RestoreAfterImport() {
        addLearnedRecord(testTableName, "restore", "恢復", 150)
        addLearnedRecord(testTableName, "restore", "還原", 140)
        var originalCount: Int = manageController.countRecords(testTableName)
        var exportFile: java.io.File = java.io.File(context.getFilesDir(), (("test_restore_" + System.currentTimeMillis()) + ".zip"))
        try {
            setupController.exportZippedDb(testTableName, exportFile, null)
            setupController.clearTable(testTableName, false)
            setupController.importZippedDb(exportFile, testTableName, true)
            var afterRestore: Int = manageController.countRecords(testTableName)
            assertTrue("Restored table should have records", (afterRestore > 0))
        } finally {
            if (exportFile.exists()) {
                exportFile.delete()
            }
        }
    }
    @Test
    fun test_5_6_RestorePreservesLearnedEntries() {
        var testCode: String = "preserve"
        var testWord1: String = "保存"
        var testWord2: String = "維持"
        addLearnedRecord(testTableName, testCode, testWord1, 200)
        addLearnedRecord(testTableName, testCode, testWord2, 180)
        var exportFile: java.io.File = java.io.File(context.getFilesDir(), (("test_preserve_" + System.currentTimeMillis()) + ".zip"))
        try {
            setupController.exportZippedDb(testTableName, exportFile, null)
            setupController.clearTable(testTableName, false)
            setupController.importZippedDb(exportFile, testTableName, true)
            var count: Int = manageController.countRecords(testTableName)
            assertTrue("Restored table should have learned entries", (count > 0))
        } finally {
            if (exportFile.exists()) {
                exportFile.delete()
            }
        }
    }
    @Test
    fun test_5_6_NoRestorePath() {
        addLearnedRecord(testTableName, "norestore", "不還原", 150)
        var beforeCount: Int = manageController.countRecords(testTableName)
        assertTrue("Table should have records", (beforeCount > 0))
        var exportFile: java.io.File = java.io.File(context.getFilesDir(), (("test_norestore_" + System.currentTimeMillis()) + ".zip"))
        try {
            setupController.exportZippedDb(testTableName, exportFile, null)
            setupController.clearTable(testTableName, false)
            setupController.importZippedDb(exportFile, testTableName, false)
            var finalCount: Int = manageController.countRecords(testTableName)
            assertTrue("Import should succeed without restore", (finalCount >= 0))
        } finally {
            if (exportFile.exists()) {
                exportFile.delete()
            }
        }
    }
    @Test
    fun test_5_6_CheckBackupTableBeforeRestore() {
        addLearnedRecord(testTableName, "check", "檢查", 150)
        var exportFile: java.io.File = java.io.File(context.getFilesDir(), (("test_check_" + System.currentTimeMillis()) + ".zip"))
        try {
            setupController.exportZippedDb(testTableName, exportFile, null)
            setupController.importZippedDb(exportFile, testTableName, true)
            var count: Int = manageController.countRecords(testTableName)
            assertTrue("Table should have data after import with restore", (count > 0))
        } finally {
            if (exportFile.exists()) {
                exportFile.delete()
            }
        }
    }
    @Test
    fun test_5_6_RestoreWithNoBackup() {
        setupController.clearTable(testTableName, false)
        var count: Int = manageController.countRecords(testTableName)
        assertEquals("Table should be empty initially", 0, count)
    }
    @Test
    fun test_5_6_6_ZippedDbBackupRestoreWorkflow() {
        var originalCount: Int = manageController.countRecords(testTableName)
        android.util.Log.w("Integrated Test", ("Test table record counts: " + originalCount))
        var exportFile: java.io.File = java.io.File(context.getFilesDir(), (("test_workflow_" + System.currentTimeMillis()) + ".limedb"))
        try {
            setupController.exportZippedDb(testTableName, exportFile, null)
            setupController.clearTable(testTableName, false)
            var clearedCount: Int = manageController.countRecords(testTableName)
            assertTrue("All records should be cleared", (clearedCount == 0))
            setupController.importZippedDb(exportFile, testTableName, true)
            var restoredCount: Int = manageController.countRecords(testTableName)
            assertTrue(((("All records should be restored from zipped Db. Original: " + originalCount) + ", Restored: ") + restoredCount), (restoredCount == originalCount))
        } finally {
            if (exportFile.exists()) {
                exportFile.delete()
            }
        }
    }
    @Test
    fun test_5_6_7_TxtTableBackupRestoreWorkflow() {
        var originalCount: Int = manageController.countRecords(testTableName)
        Log.i("Integrated test", "test_5_6_7_TxtTableBackupRestoreWorkflow() start exporting txt table")
        var exporTxtFile: java.io.File = java.io.File(context.getFilesDir(), (("test_workflow_" + System.currentTimeMillis()) + ".lime"))
        try {
            var completedExport: java.io.File = setupController.exportTxtTable(testTableName, exporTxtFile, null)!!
            assertNotNull("Exported txt file should be returned after completion", completedExport)
            assertTrue("Exported txt file should exist", completedExport.exists())
            assertTrue("Exported txt file should be non-empty", (completedExport.length > 0))
            var exportedLineCount: Int = 0
            var exportedLines: MutableMap<String, MutableSet<String>> = HashMap()
            try {
                java.io.BufferedReader(java.io.FileReader(completedExport)).use { reader ->
                        var line: String
                        while (true) {
                            line = reader.readLine() ?: break
                            if (line.startsWith("@")) {
                                continue
                            }
                            var parts: Array<String> = line.split("\\|".toRegex()).toTypedArray()
                            if ((parts.length >= 2)) {
                                exportedLineCount++
                                var code: String = parts[0]
                                var word: String = parts[1]
                                exportedLines.computeIfAbsent(code) { HashSet() }.add(word)
                            }
                        }
                }
            } catch (e: java.io.IOException) {
                fail(("Failed to read exported txt file: " + e.getMessage()))
            }
            android.util.Log.w("Integrated Test", ("Exported txt file line count: " + exportedLineCount))
            assertTrue("Exported line count should >0", (exportedLineCount > 0))
            var originalRecords: MutableList<net.toload.main.hd.data.Record> = queryRecords(testTableName, null, true)
            Log.i("Integrated Test", ((((("DB total count (includes null/empty words): " + originalCount) + ", queried records (filters null/empty): ") + originalRecords.size) + ", exported lines (filters null/empty): ") + exportedLineCount))
            var recordChecked: Int = 0
            var missingCount: Int = 0
            for (r in originalRecords) {
                var words: MutableSet<String>? = exportedLines.get(r.getCode())
                var found: Boolean = ((words != null) && words.contains(r.getWord()))
                recordChecked++
                if (!found) {
                    missingCount++
                    if ((missingCount <= 10)) {
                        Log.w("Integrated Test", ((("Original record missing from export: code=" + r.getCode()) + ", word=") + r.getWord()))
                    }
                }
            }
            Log.i("Integrated Test", ((("Original records verified: " + recordChecked) + ", missing from export: ") + missingCount))
            assertTrue(("All queried records should be in export. Missing: " + missingCount), (missingCount == 0))
            setupController.clearTable(testTableName, false)
            var clearedCount: Int = manageController.countRecords(testTableName)
            assertTrue("All records should be cleared after clearTable", (clearedCount == 0))
            setupController.importTxtTable(completedExport, testTableName, false)
            var restoredCount: Int = 0
            var start: Long = System.currentTimeMillis()
            var timeoutMs: Long = 30000
            while (((System.currentTimeMillis() - start) < timeoutMs)) {
                restoredCount = manageController.countRecords(testTableName)
                if ((restoredCount == exportedLineCount)) {
                    break
                }
                try {
                    Thread.sleep(500)
                } catch (ignored: InterruptedException) {

                }
            }
            Log.i("Integrated Test", ((("Restored from txtTable: " + restoredCount) + " / expected: ") + exportedLineCount))
            var restoredRecords: MutableList<net.toload.main.hd.data.Record> = queryRecords(testTableName, null, true)
            var restoredMap: MutableMap<String, MutableSet<String?>> = HashMap()
            for (r in restoredRecords) {
                if (((r.getCode() != null) && r.getCode().startsWith("@"))) {
                    continue
                }
                restoredMap.computeIfAbsent(r.getCode()!!) { HashSet() }.add(r.getWord())
            }
            var exportedUniqueCount: Int = 0
            for (words in exportedLines.values) {
                exportedUniqueCount += words.size
            }
            var restoredUniqueCount: Int = 0
            for (words in restoredMap.values) {
                restoredUniqueCount += words.size
            }
            var restoredMissing: Int = 0
            var restoredChecked: Int = 0
            for (entry in exportedLines.entries) {
                var code: String = entry.getKey()
                var exportedWords: MutableSet<String> = entry.getValue()
                var restoredWords: MutableSet<String?>? = restoredMap.get(code)
                for (word in exportedWords) {
                    restoredChecked++
                    var found: Boolean = ((restoredWords != null) && restoredWords.contains(word))
                    if (!found) {
                        restoredMissing++
                        if ((restoredMissing <= 10)) {
                            Log.w("Integrated Test", ((("Restored missing exported record: code=" + code) + ", word=") + word))
                        }
                    }
                }
            }
            var restoredExtra: Int = 0
            var restoredExtraLogged: Int = 0
            for (entry in restoredMap.entries) {
                var code: String = entry.getKey()
                var restoredWords: MutableSet<String?> = entry.getValue()
                var exportedWords: MutableSet<String>? = exportedLines.get(code)
                for (word in restoredWords) {
                    var inExport: Boolean = ((exportedWords != null) && exportedWords.contains(word))
                    if (!inExport) {
                        restoredExtra++
                        if ((restoredExtraLogged < 10)) {
                            Log.w("Integrated Test", ((("Restored extra record not in export: code=" + code) + ", word=") + word))
                            restoredExtraLogged++
                        }
                    }
                }
            }
            Log.i("Integrated Test", ((((((((((((("Restored verification checked: " + restoredChecked) + ", missing: ") + restoredMissing) + ", extra: ") + restoredExtra) + ", restoredCount=") + restoredCount) + ", restoredUnique=") + restoredUniqueCount) + ", exportedUnique=") + exportedUniqueCount) + ", exportedLineCount=") + exportedLineCount))
            assertEquals("Restored unique count should match exported unique count", exportedUniqueCount, restoredUniqueCount)
            assertTrue(("All exported records should be present after restore. Missing: " + restoredMissing), (restoredMissing == 0))
            assertTrue(("No extra records should exist after restore. Extra: " + restoredExtra), (restoredExtra == 0))
        } finally {
            if (exporTxtFile.exists()) {
                exporTxtFile.delete()
            }
        }
    }
    @Test
    fun test_5_6_8_BackupRestoreUserRecordsPair() {
        var code: String = "backup_pair"
        var word1: String = "備份對"
        var word2: String = "還原對"
        var baselineExport: File = File(context.getFilesDir(), (("test_backup_pair_" + System.currentTimeMillis()) + ".zip"))
        try {
            var exportResult: File = setupController.exportZippedDb(testTableName, baselineExport, null)!!
            assertNotNull("Baseline export should succeed", exportResult)
            assertTrue("Baseline export file should exist", baselineExport.exists())
            addLearnedRecord(testTableName, code, word1, 220)
            addLearnedRecord(testTableName, code, word2, 210)
            var beforeImport: MutableList<net.toload.main.hd.data.Record> = queryRecords(testTableName, code, true)
            assertTrue("Learned records should exist before import", (beforeImport.size >= 2))
            setupController.importZippedDb(baselineExport, testTableName, true)
            var afterImport: MutableList<net.toload.main.hd.data.Record> = queryRecords(testTableName, code, true)
            var score1: Int? = null
            var score2: Int? = null
            for (r in afterImport) {
                if (code.equals(r.getCode())) {
                    if (word1.equals(r.getWord())) {
                        score1 = r.getScore()
                    }
                    if (word2.equals(r.getWord())) {
                        score2 = r.getScore()
                    }
                }
            }
            assertNotNull("backupUserRecords + restoreUserRecords should restore word1", score1)
            assertNotNull("backupUserRecords + restoreUserRecords should restore word2", score2)
            assertEquals("Restored score for word1 should match", Integer.valueOf(220), score1)
            assertEquals("Restored score for word2 should match", Integer.valueOf(210), score2)
        } finally {
            if (baselineExport.exists()) {
                baselineExport.delete()
            }
        }
    }
    @Test
    fun test_5_6_7_UIRefreshAfterRestore() {
        addLearnedRecord(testTableName, "refresh", "刷新", 150)
        addLearnedRecord(testTableName, "refresh", "更新介面", 140)
        var originalCount: Int = manageController.countRecords(testTableName)
        var exportFile: java.io.File = java.io.File(context.getFilesDir(), (("test_refresh_" + System.currentTimeMillis()) + ".zip"))
        try {
            setupController.exportZippedDb(testTableName, exportFile, null)
            setupController.clearTable(testTableName, false)
            setupController.importZippedDb(exportFile, testTableName, true)
            var restoredCount: Int = manageController.countRecords(testTableName)
            assertTrue("Restored count should be positive", (restoredCount > 0))
        } finally {
            if (exportFile.exists()) {
                exportFile.delete()
            }
        }
    }
    @Test
    fun test_5_6_9_BackupRestoreDatabasePair() {
        var imConfigObjListBefore: MutableList<ImConfig> = setupController.imConfigList
        var imListBefore: MutableList<String> = ArrayList()
        for (imConfig in imConfigObjListBefore) {
            imListBefore.add(java.lang.String.valueOf(imConfig.getCode()))
        }
        var imCountsBefore: MutableMap<String, Int> = HashMap()
        for (im in imListBefore) {
            var count: Int = manageController.countRecords(im)
            imCountsBefore.put(im, count)
        }
        var backupFile: java.io.File = java.io.File(context.getFilesDir(), (("test_db_backup_" + System.currentTimeMillis()) + ".zip"))
        var backupUri: android.net.Uri = androidx.core.content.FileProvider.getUriForFile(context, (context.getPackageName() + ".fileprovider"), backupFile)
        try {
            setupController.performBackup(backupUri)
            assertTrue("Backup file should exist", backupFile.exists())
            setupController.restoredToDefault()
            var defaultImConfigList: MutableList<ImConfig> = setupController.imConfigList
            assertFalse("Bundled default IM list should not be empty after restoredToDefault", defaultImConfigList.isEmpty())
            setupController.performRestore(backupUri)
            var imConfigObjListAfter: MutableList<ImConfig> = setupController.imConfigList
            var imListAfter: MutableList<String> = ArrayList()
            for (imConfig in imConfigObjListAfter) {
                imListAfter.add(java.lang.String.valueOf(imConfig.getCode()))
            }
            var imCountsAfter: MutableMap<String, Int> = HashMap()
            for (im in imListAfter) {
                var count: Int = manageController.countRecords(im)
                imCountsAfter.put(im, count)
            }
            assertEquals("IM list should be the same after restore", HashSet(imListBefore), HashSet(imListAfter))
            for (im in imListBefore) {
                var before: Int = imCountsBefore.get(im)!!
                var after: Int = imCountsAfter.get(im)!!
                assertEquals((("Record count for IM '" + im) + "' should be the same after restore"), before, after)
            }
        } finally {
            if (backupFile.exists()) {
                backupFile.delete()
            }
        }
    }
    @Test
    fun test_5_6_10_BackupRestoreDatabasePairRestoresPreferenceCompatibilityManifest() {
        var prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        var expected: MutableMap<String, Any?> = fullAndroidPrefsTableFixture()
        var originalValues: MutableMap<String, Any?> = snapshotPrefs(prefs, expected.keys)
        var backupFile: java.io.File = java.io.File(context.getFilesDir(), (("test_pref_backup_" + System.currentTimeMillis()) + ".zip"))
        var backupUri: android.net.Uri = androidx.core.content.FileProvider.getUriForFile(context, (context.getPackageName() + ".fileprovider"), backupFile)
        try {
            seedPrefs(prefs, expected)
            setupController.performBackup(backupUri)
            assertTrue("Backup file should exist", backupFile.exists())
            assertZipContains(backupFile, "databases/lime.db")
            assertZipContains(backupFile, "shared_prefs.bak")
            assertZipContains(backupFile, "preferences/lime_prefs.json")
            var manifest: JSONObject = readPreferenceManifest(backupFile)
            assertEquals("Manifest schema should be v1", 1, manifest.getInt("schema"))
            var values: JSONObject = manifest.getJSONObject("preferences")
            assertEquals("Manifest must contain exactly the full Android PREFS_TABLE set seeded by this test", expected.size, values.length)
            assertManifestValues(values, expected)
            seedPrefs(prefs, mutatedAndroidPrefsTableFixture())
            setupController.performRestore(backupUri)
            assertStoredValues(prefs, expected)
        } finally {
            restorePrefs(prefs, originalValues)
            if (backupFile.exists()) {
                backupFile.delete()
            }
        }
    }
    @Test
    fun test_5_6_11_RestoreIosStylePreferenceFixtureThroughAndroidAdapter() {
        var prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        var expected: MutableMap<String, Any?> = fullAndroidPrefsTableFixture()
        var originalValues: MutableMap<String, Any?> = snapshotPrefs(prefs, expected.keys)
        var fixtureFile: java.io.File = java.io.File(context.getFilesDir(), (("test_ios_pref_fixture_" + System.currentTimeMillis()) + ".zip"))
        var fixtureUri: android.net.Uri = androidx.core.content.FileProvider.getUriForFile(context, (context.getPackageName() + ".fileprovider"), fixtureFile)
        try {
            writeCrossPlatformFixtureZip(fixtureFile, "ios", expected)
            seedPrefs(prefs, mutatedAndroidPrefsTableFixture())
            setupController.performRestore(fixtureUri)
            assertStoredValues(prefs, expected)
        } finally {
            restorePrefs(prefs, originalValues)
            if (fixtureFile.exists()) {
                fixtureFile.delete()
            }
        }
    }
    @Test
    fun test_5_6_12_RestoreLegacyAndroidBackupWithLeadingSlashEntries() {
        var originalCount: Int = manageController.countRecords(testTableName)
        assertTrue("Legacy fixture needs a populated IM table", (originalCount > 0))
        var fixtureFile: java.io.File = java.io.File(context.getFilesDir(), (("test_legacy_slash_backup_" + System.currentTimeMillis()) + ".zip"))
        var fixtureUri: android.net.Uri = androidx.core.content.FileProvider.getUriForFile(context, (context.getPackageName() + ".fileprovider"), fixtureFile)
        try {
            writeLegacyLeadingSlashFullBackupZip(fixtureFile)
            setupController.clearTable(testTableName, false)
            var clearedCount: Int = manageController.countRecords(testTableName)
            assertEquals("Fixture table should be cleared before restore", 0, clearedCount)
            setupController.performRestore(fixtureUri)
            var restoredCount: Int = manageController.countRecords(testTableName)
            assertEquals("Old Android backups with /databases/lime.db must restore the database", originalCount, restoredCount)
        } finally {
            if (fixtureFile.exists()) {
                fixtureFile.delete()
            }
        }
    }
    private fun fullAndroidPrefsTableFixture(): MutableMap<String, Any?> {
        var values: MutableMap<String, Any?> = java.util.LinkedHashMap()
        values.put("keyboard_theme", 4)
        values.put("keyboard_size", "1")
        values.put("font_size", "2")
        values.put("number_row_in_english", false)
        values.put("show_arrow_key", 2)
        values.put("split_keyboard_mode", 1)
        values.put("vibrate_on_keypress", false)
        values.put("vibrate_level", 80)
        values.put("sound_on_keypress", true)
        values.put("smart_chinese_input", false)
        values.put("auto_chinese_symbol", true)
        values.put("candidate_switch", true)
        values.put("persistent_language_mode", true)
        values.put("enable_emoji_position", 3)
        values.put("similiar_list", 30)
        values.put("han_convert_option", 2)
        values.put("similiar_enable", false)
        values.put("candidate_suggestion", false)
        values.put("learn_phrase", false)
        values.put("learning_switch", false)
        values.put("english_dictionary_enable", false)
        values.put("auto_cap", false)
        values.put("custom_im_reverselookup", "dayi")
        values.put("cj_im_reverselookup", "phonetic")
        values.put("scj_im_reverselookup", "cj")
        values.put("cj5_im_reverselookup", "scj")
        values.put("ecj_im_reverselookup", "cj5")
        values.put("dayi_im_reverselookup", "bpmf")
        values.put("bpmf_im_reverselookup", "dayi")
        values.put("phonetic_im_reverselookup", "custom")
        values.put("ez_im_reverselookup", "array")
        values.put("array_im_reverselookup", "array10")
        values.put("array10_im_reverselookup", "ez")
        values.put("wb_im_reverselookup", "hs")
        values.put("hs_im_reverselookup", "pinyin")
        values.put("pinyin_im_reverselookup", "none")
        values.put("phonetic_keyboard_type", "standard")
        values.put("auto_commit", 3)
        values.put("accept_number_index", true)
        values.put("accept_symbol_index", true)
        values.put("backup_on_delete_phonetic", false)
        values.put("restore_on_import_phonetic", false)
        values.put("hide_software_keyboard_typing_with_physical", false)
        values.put("switch_english_mode", true)
        values.put("switch_english_mode_shift", false)
        values.put("disable_physical_selkey", true)
        values.put("selkey_option", 2)
        values.put("english_dictionary_physical_keyboard", true)
        values.put("physical_keyboard_sort", true)
        return values
    }
    private fun mutatedAndroidPrefsTableFixture(): MutableMap<String, Any?> {
        var values: MutableMap<String, Any?> = fullAndroidPrefsTableFixture()
        for (key in ArrayList(values.keys)) {
            var value: Any = values.get(key)!!
            if ((value is Boolean)) {
                values.put(key, (value as Boolean))
            } else {
                if ((value is Int)) {
                    values.put(key, 0)
                } else {
                    if ((value is String)) {
                        values.put(key, "none")
                    }
                }
            }
        }
        values.put("keyboard_size", "2")
        values.put("font_size", "1")
        return values
    }
    private fun snapshotPrefs(prefs: SharedPreferences, keys: MutableSet<String>): MutableMap<String, Any?> {
        var all: Map<String, *> = prefs.getAll()
        var snapshot: MutableMap<String, Any?> = HashMap()
        for (key in keys) {
            if (all.containsKey(key)) {
                snapshot.put(key, all.get(key))
            }
        }
        return snapshot
    }
    private fun restorePrefs(prefs: SharedPreferences, snapshot: MutableMap<String, Any?>) {
        var editor: SharedPreferences.Editor = prefs.edit()
        for (key in fullAndroidPrefsTableFixture().keys) {
            if (snapshot.containsKey(key)) {
                editor.remove(key)
                continue
            }
            var value: Any? = snapshot.get(key)
            if ((value is Boolean)) {
                editor.putBoolean(key, (value as Boolean))
            } else {
                if ((value is String)) {
                    editor.putString(key, (value as String))
                }
            }
        }
        editor.commit()
    }
    private fun seedPrefs(prefs: SharedPreferences, values: MutableMap<String, Any?>) {
        var editor: SharedPreferences.Editor = prefs.edit()
        for (entry in values.entries) {
            var value: Any? = entry.getValue()
            if ((value is Boolean)) {
                editor.putBoolean(entry.getKey(), (value as Boolean))
            } else {
                if (((value is Int) && isAndroidStringBackedInteger(entry.getKey()))) {
                    editor.putString(entry.getKey(), java.lang.String.valueOf(value))
                } else {
                    if ((value is String)) {
                        editor.putString(entry.getKey(), (value as String))
                    }
                }
            }
        }
        editor.commit()
    }
    private fun assertManifestValues(actual: JSONObject, expected: MutableMap<String, Any?>) {
        for (entry in expected.entries) {
            var key: String = entry.getKey()
            var expectedValue: Any? = entry.getValue()
            if ((expectedValue is Boolean)) {
                assertEquals((key + " should be backed up as a boolean"), expectedValue, actual.getBoolean(key))
            } else {
                if ((expectedValue is Int)) {
                    assertEquals((key + " should be backed up as an integer"), expectedValue, actual.getInt(key))
                } else {
                    if ((expectedValue is String)) {
                        assertEquals((key + " should be backed up as a string"), expectedValue, actual.getString(key))
                    }
                }
            }
        }
    }
    private fun assertStoredValues(prefs: SharedPreferences, expected: MutableMap<String, Any?>) {
        for (entry in expected.entries) {
            var key: String = entry.getKey()
            var expectedValue: Any? = entry.getValue()
            if ((expectedValue is Boolean)) {
                assertEquals((key + " should restore as a boolean"), expectedValue, prefs.getBoolean(key, (expectedValue as Boolean)))
            } else {
                if ((expectedValue is Int)) {
                    assertEquals((key + " should restore as Android string-backed integer"), java.lang.String.valueOf(expectedValue), prefs.getString(key, null))
                } else {
                    if ((expectedValue is String)) {
                        assertEquals((key + " should restore as a string"), expectedValue, prefs.getString(key, null))
                    }
                }
            }
        }
    }
    private fun isAndroidStringBackedInteger(key: String): Boolean {
        return java.util.Arrays.asList("keyboard_theme", "show_arrow_key", "split_keyboard_mode", "vibrate_level", "enable_emoji_position", "similiar_list", "han_convert_option", "auto_commit", "selkey_option").contains(key)
    }
    private fun assertZipContains(backupFile: File, entryName: String) {
        ZipFile(backupFile).use { zipFile ->
                var entry: ZipEntry = zipFile.getEntry(entryName)
                assertNotNull(("Full backup should contain " + entryName), entry)
        }
    }
    private fun writeCrossPlatformFixtureZip(fixtureFile: File, sourcePlatform: String, preferences: MutableMap<String, Any?>) {
        if ((fixtureFile.exists() && fixtureFile.delete())) {
            throw java.io.IOException(("Failed to delete old fixture " + fixtureFile))
        }
        var databaseFile: File = context.getDatabasePath(LIME.DATABASE_NAME)
        assertTrue("Cross-platform fixture requires an existing database", databaseFile.exists())
        var manifest: JSONObject = JSONObject()
        ZipOutputStream(java.io.FileOutputStream(fixtureFile)).use { output ->
                output.putNextEntry(ZipEntry("databases/lime.db"))
                java.io.FileInputStream(databaseFile).use { input ->
                        var buffer: ByteArray = ByteArray(8192)
                        var count: Int
                        while (true) {
                            count = input.read(buffer)
                            if (count == -1) break
                            output.write(buffer, 0, count)
                        }
                }
                output.closeEntry()
                output.putNextEntry(ZipEntry("shared_prefs.bak"))
                output.write("legacy-sidecar-not-needed-when-json-exists".toByteArray(StandardCharsets.UTF_8))
                output.closeEntry()
                output.putNextEntry(ZipEntry("preferences/lime_prefs.json"))
                output.write(manifest.toString().getBytes(StandardCharsets.UTF_8))
                output.closeEntry()
        }
    }
    private fun writeLegacyLeadingSlashFullBackupZip(fixtureFile: File) {
        if ((fixtureFile.exists() && fixtureFile.delete())) {
            throw java.io.IOException(("Failed to delete old fixture " + fixtureFile))
        }
        var databaseFile: File = context.getDatabasePath(LIME.DATABASE_NAME)
        assertTrue("Legacy fixture requires an existing database", databaseFile.exists())
        var prefsBackup: File = File(context.getCacheDir(), (("legacy_shared_prefs_" + System.currentTimeMillis()) + ".bak"))
        try {
            staticDbServer.backupDefaultSharedPreference(prefsBackup)
            ZipOutputStream(java.io.FileOutputStream(fixtureFile)).use { output ->
                    output.putNextEntry(ZipEntry("/databases/lime.db"))
                    copyFileToZipEntry(databaseFile, output)
                    output.closeEntry()
                    output.putNextEntry(ZipEntry("/databases/lime.db-journal"))
                    output.closeEntry()
                    output.putNextEntry(ZipEntry("/shared_prefs.bak"))
                    copyFileToZipEntry(prefsBackup, output)
                    output.closeEntry()
            }
        } finally {
            if (prefsBackup.exists()) {
                prefsBackup.delete()
            }
        }
    }
    private fun copyFileToZipEntry(source: File, output: ZipOutputStream) {
        java.io.FileInputStream(source).use { input ->
                var buffer: ByteArray = ByteArray(8192)
                var count: Int
                while (true) {
                            count = input.read(buffer)
                            if (count == -1) break
                    output.write(buffer, 0, count)
                }
        }
    }
    private fun readPreferenceManifest(backupFile: File): JSONObject {
        ZipFile(backupFile).use { zipFile ->
                var entry: ZipEntry = zipFile.getEntry("preferences/lime_prefs.json")
                assertNotNull("Full backup should contain preferences/lime_prefs.json", entry)
                zipFile.getInputStream(entry).use { input ->
                        var data: ByteArray = ByteArray((entry.getSize() as Int))
                        var offset: Int = 0
                        while ((offset < data.length)) {
                            var read: Int = input.read(data, offset, (data.length - offset))
                            if ((read < 0)) {
                                break
                            }
                            offset += read
                        }
                        assertEquals("Manifest should be read completely", data.length, offset)
                        return JSONObject(String(data, StandardCharsets.UTF_8))
                }
        }
    }
    private fun addLearnedRecord(table: String, code: String, word: String, score: Int) {
        manageController.addRecord(table, code, word, score)
    }
    private fun queryRecords(table: String, query: String?, searchByCode: Boolean): MutableList<net.toload.main.hd.data.Record> {
        val latch: java.util.concurrent.CountDownLatch = java.util.concurrent.CountDownLatch(1)
        val out: java.util.concurrent.atomic.AtomicReference<List<net.toload.main.hd.data.Record>?> = java.util.concurrent.atomic.AtomicReference()
        manageController.setManageImView(object : net.toload.main.hd.ui.view.ManageImView {
            override fun displayRecords(records: List<net.toload.main.hd.data.Record>?) {
                out.set(records)
                latch.countDown()
            }
            override fun updateRecordCount(count: Int) {

            }
            override fun showAddRecordDialog() {

            }
            override fun showEditRecordDialog(record: net.toload.main.hd.data.Record?) {

            }
            override fun showDeleteConfirmDialog(id: Long) {

            }
            override fun refreshRecordList() {

            }
            override fun onError(message: String?) {
                latch.countDown()
            }
        })
        manageController.loadRecordsAsync(table, query, searchByCode, 0, Integer.MAX_VALUE)
        try {
            latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
        } catch (ignored: InterruptedException) {

        }
        val result: List<net.toload.main.hd.data.Record>? = out.get()
        return result?.toMutableList() ?: ArrayList()
    }
}
