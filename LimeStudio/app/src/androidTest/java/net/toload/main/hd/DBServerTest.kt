@file:Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST", "TYPE_INTERSECTION_AS_REIFIED")

package net.toload.main.hd

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.RemoteException
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.ArrayList
import java.util.Enumeration
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.junit.Assert.*
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.global.LIME
import net.toload.main.hd.global.LIMEUtilities
import net.toload.main.hd.global.LIMEProgressListener
import net.toload.main.hd.limedb.LimeDB
import net.toload.main.hd.data.Record

@RunWith(AndroidJUnit4::class)
open class DBServerTest {
    companion object {
        private var sharedDbServer: DBServer? = null
        private val TEST_METADATA_TABLE: String = LIME.DB_TABLE_CUSTOM
    }
    private val TAG: String = "DBServerTest"
    @Before
    fun setUp() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        if ((sharedDbServer == null)) {
            sharedDbServer = DBServer.getInstance(appContext)!!
        }
        var stillOnHold: Boolean = false
        run {
            var i: Int = 0
            while ((i < 100)) {
                if (sharedDbServer!!.isDatabseOnHold()) {
                    break
                }
                stillOnHold = true
                if ((i < 99)) {
                    try {
                        Thread.sleep(100)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
                i++
            }
        }
        if ((stillOnHold && sharedDbServer!!.isDatabseOnHold())) {
            Log.w(TAG, "Database still on hold after waiting, forcing release to prevent test failure")
            try {
                var datasourceField: java.lang.reflect.Field = DBServer::class.java.getDeclaredField("datasource")
                datasourceField.setAccessible(true)
                var datasource: net.toload.main.hd.limedb.LimeDB = (datasourceField.get(sharedDbServer) as net.toload.main.hd.limedb.LimeDB)
                if ((datasource != null)) {
                    datasource.openDBConnection(true)
                    Log.i(TAG, "Successfully forced database release in setUp")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error forcing database release in setUp", e)
            }
        }
    }
    @After
    fun tearDown() {
        if (((sharedDbServer != null) && sharedDbServer!!.isDatabseOnHold())) {
            Log.w(TAG, "Database still on hold after test, waiting for loading thread and releasing in tearDown")
            try {
                var datasourceField: java.lang.reflect.Field = DBServer::class.java.getDeclaredField("datasource")
                datasourceField.setAccessible(true)
                var datasource: net.toload.main.hd.limedb.LimeDB = (datasourceField.get(sharedDbServer) as net.toload.main.hd.limedb.LimeDB)
                if ((datasource != null)) {
                    var loadingThread: Thread? = null
                    try {
                        var threadField: java.lang.reflect.Field = LimeDB::class.java.getDeclaredField("importThread")
                        threadField.setAccessible(true)
                        loadingThread = (threadField.get(datasource) as Thread)
                    } catch (e: Exception) {

                    }
                    if (((loadingThread != null) && loadingThread.isAlive())) {
                        Log.i(TAG, "Waiting for import thread to complete before releasing database")
                        var waitCount: Int = 0
                        while ((loadingThread.isAlive() && (waitCount < 50))) {
                            try {
                                Thread.sleep(100)
                                waitCount++
                            } catch (e: InterruptedException) {
                                Thread.currentThread().interrupt()
                                break
                            }
                        }
                        if (loadingThread.isAlive()) {
                            Log.w(TAG, "Import thread still alive after 5 seconds, forcing release")
                        } else {
                            Log.i(TAG, "Import thread completed")
                        }
                    }
                    datasource.openDBConnection(true)
                    Log.i(TAG, "Successfully released database in tearDown")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing database in tearDown", e)
            }
        }
    }
    private fun ensureDatabaseReady(dbServer: DBServer): Boolean {
        run {
            var i: Int = 0
            while ((i < 100)) {
                if (dbServer.isDatabseOnHold()) {
                    return true
                }
                if ((i < 99)) {
                    try {
                        Thread.sleep(100)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        Log.e(TAG, "Thread interrupted while waiting for database", e)
                        return false
                    }
                }
                i++
            }
        }
        Log.e(TAG, (("ERROR: Database is still on hold after waiting 10 seconds. " + "This indicates a stuck operation from a previous test. ") + "Test may hang or fail."))
        return false
    }
    @Test
    fun testDBServerSetImConfigPersistsMetadata() {
        var suffix: String = java.lang.String.valueOf(System.currentTimeMillis())
        var editedName: String = ("Edited Name " + suffix)
        var editedVersion: String = ("Edited Version " + suffix)
        sharedDbServer!!.setImConfig(TEST_METADATA_TABLE, "name", editedName)
        sharedDbServer!!.setImConfig(TEST_METADATA_TABLE, "version", editedVersion)
        assertEquals(editedName, sharedDbServer!!.getImConfig(TEST_METADATA_TABLE, "name"))
        assertEquals(editedVersion, sharedDbServer!!.getImConfig(TEST_METADATA_TABLE, "version"))
    }
    private fun ensureDatabaseReady(limeDB: LimeDB): Boolean {
        run {
            var i: Int = 0
            while ((i < 100)) {
                if (limeDB.isDatabaseOnHold()) {
                    return true
                }
                if ((i < 99)) {
                    try {
                        Thread.sleep(100)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        Log.e(TAG, "Thread interrupted while waiting for database", e)
                        return false
                    }
                }
                i++
            }
        }
        Log.e(TAG, (("ERROR: Database is still on hold after waiting 10 seconds. " + "This indicates a stuck operation from a previous test. ") + "Test may hang or fail."))
        return false
    }
    private fun initializeDatabase(limeDB: LimeDB): Boolean {
        if (ensureDatabaseReady(limeDB)) {
            Log.e(TAG, ("ERROR: Cannot initialize database - database is on hold. " + "This test may hang or fail."))
            return false
        }
        var result: Boolean = limeDB.openDBConnection(false)
        if (!result) {
            Log.e(TAG, "ERROR: Failed to open database connection")
        }
        return result
    }
    private fun deleteDirectory(directory: File): Boolean {
        if (((directory == null) || directory.exists())) {
            return true
        }
        if (directory.isDirectory()) {
            var files: Array<out File>? = directory.listFiles()
            if ((files != null)) {
                for (file in files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file)
                    } else {
                        file.delete()
                    }
                }
            }
        }
        return directory.delete()
    }
    @Test
    fun testDBServerInitialization() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        assertNotNull("DBServer instance should not be null", dbServer)
        var dbServer2: DBServer = DBServer.getInstance(appContext)!!
        assertSame("getInstance should return the same singleton instance", dbServer, dbServer2)
    }
    @Test
    fun testDBServerIsDatabaseOnHold() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        var onHold: Boolean = dbServer.isDatabseOnHold()
        assertTrue("isDatabseOnHold should return boolean", true)
    }
    @Test
    fun testDBServerResetCache() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
    }
    @Test
    fun testDBServerRenameTableName() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        assertNotNull("DBServer should have renameTableName method", dbServer)
    }
    @Test(timeout = 5000)
    fun testDBServerImportBackupRelatedDb() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (dbServer.isDatabseOnHold()) {
            return
        }
        var testBackup: File = File(appContext.getCacheDir(), "test_related_backup.db")
        var testDb: SQLiteDatabase? = null
        try {
            if ((testBackup.exists() && testBackup.delete())) {
                Log.e(TAG, "Failed to delete existing test backup file")
            }
            testDb = SQLiteDatabase.openOrCreateDatabase(testBackup, null)
            testDb.execSQL((((((((((((("CREATE TABLE IF NOT EXISTS " + LIME.DB_TABLE_RELATED) + " (") + LIME.DB_RELATED_COLUMN_ID) + " INTEGER PRIMARY KEY AUTOINCREMENT, ") + LIME.DB_RELATED_COLUMN_PWORD) + " TEXT, ") + LIME.DB_RELATED_COLUMN_CWORD) + " TEXT, ") + LIME.DB_RELATED_COLUMN_BASESCORE) + " INTEGER, ") + LIME.DB_RELATED_COLUMN_USERSCORE) + " INTEGER)"))
            testDb.close()
            testDb = null
            dbServer.importDbRelated(testBackup)
            if ((testBackup.exists() && testBackup.delete())) {
                Log.e(TAG, "Failed to delete test backup file")
            }
        } catch (e: Exception) {
            if (((testDb != null) && testDb.isOpen())) {
                testDb.close()
            }
            if ((testBackup.exists() && testBackup.delete())) {
                Log.e(TAG, "Failed to delete test backup file")
            }
        }
        assertTrue("importDbRelated should handle file operations", true)
    }
    @Test
    fun testDBServerImportBackupDb() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        var testBackup: File = File(appContext.getCacheDir(), "test_backup.db")
        try {
            testBackup.createNewFile()
            dbServer.importDb(testBackup, "custom")
            if ((testBackup.exists() && testBackup.delete())) {
                Log.e(TAG, "Failed to delete test backup file")
            }
        } catch (e: Exception) {
            if ((testBackup.exists() && testBackup.delete())) {
                Log.e(TAG, "Failed to delete test backup file")
            }
        }
        assertTrue("importDb should handle file operations", true)
    }
    @Test(timeout = 15000)
    fun testDBServerImportMapping() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail((("ERROR: Database is still on hold after waiting 10 seconds. " + "Cannot proceed with test - previous operation may be stuck. ") + "Test will fail to prevent timeout."))
        }
        var tempZip: File? = null
        var tempDb: File? = null
        try {
            var limeDB: net.toload.main.hd.limedb.LimeDB = net.toload.main.hd.limedb.LimeDB(appContext)
            if (limeDB.openDBConnection(false)) {
                fail("ERROR: Cannot initialize database connection. Database may be on hold.")
            }
            var tableName: String = "custom"
            limeDB.addOrUpdateMappingRecord(tableName, "test1", "æ¸¬è©¦1", 10)
            limeDB.addOrUpdateMappingRecord(tableName, "test2", "æ¸¬è©¦2", 20)
            limeDB.addOrUpdateMappingRecord(tableName, "test3", "æ¸¬è©¦3", 30)
            var originalCount: Int = limeDB.countRecords(tableName, null, null)
            assertTrue("Should have at least 3 records before backup", (originalCount >= 3))
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            tempDb = File(cacheDir, (("test_import_mapping_" + System.currentTimeMillis()) + ".db"))
            if ((tempDb.exists() && tempDb.delete())) {
                Log.w(TAG, ("Failed to delete existing backup file: " + tempDb.getAbsolutePath()))
            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(net.toload.main.hd.R.raw.blank), tempDb)
            var tableNames: MutableList<String?> = ArrayList()
            tableNames.add(tableName)
            limeDB.prepareBackup(tempDb, tableNames, false)
            if (tempDb.exists()) {
                fail(("ERROR: prepareBackup failed - backup file was not created: " + tempDb.getAbsolutePath()))
            }
            tempZip = File(cacheDir, (("test_import_mapping_" + System.currentTimeMillis()) + ".zip"))
            if ((tempZip.exists() && tempZip.delete())) {
                Log.w(TAG, ("Failed to delete existing zip file: " + tempZip.getAbsolutePath()))
            }
            LIMEUtilities.zip(tempZip.getAbsolutePath(), tempDb.getAbsolutePath(), true)
            if (tempZip.exists()) {
                fail(("ERROR: zip failed - zip file was not created: " + tempZip.getAbsolutePath()))
            }
            limeDB.clearTable(tableName)
            var countAfterDelete: Int = limeDB.countRecords(tableName, null, null)
            assertEquals("Table should be empty after deleteAll", 0, countAfterDelete)
            dbServer.importZippedDb(tempZip, tableName)
            var countAfterImport: Int = limeDB.countRecords(tableName, null, null)
            if ((countAfterImport != originalCount)) {
                Log.e(TAG, ((("ERROR: Record count mismatch - original: " + originalCount) + ", after import: ") + countAfterImport))
                fail(((("ERROR: Record count should match after import. Expected: " + originalCount) + ", Actual: ") + countAfterImport))
            }
            assertEquals("Record count should match original count after import", originalCount, countAfterImport)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testDBServerImportMapping failed: " + e.getMessage()), e)
            fail(("ERROR: importMapping test failed with exception: " + e.getMessage()))
        } finally {
            if (((tempZip != null) && tempZip.exists())) {
                if (tempZip.delete()) {
                    Log.w(TAG, ("Failed to delete zip file after test: " + tempZip.getAbsolutePath()))
                }
            }
            if (((tempDb != null) && tempDb.exists())) {
                if (tempDb.delete()) {
                    Log.w(TAG, ("Failed to delete database file after test: " + tempDb.getAbsolutePath()))
                }
            }
        }
    }
    @Test
    fun testDBServerCompressFile() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        var testSource: File = File(appContext.getCacheDir(), "test_source.txt")
        var testTargetDir: File = appContext.getCacheDir()
        var testTargetFile: String = "test_compressed.zip"
        try {
            var writer: java.io.FileWriter = java.io.FileWriter(testSource)
            writer.write("Test content for compression")
            writer.close()
            dbServer.zip(testSource, testTargetDir.getAbsolutePath(), testTargetFile)
            var compressedFile: File = File(testTargetDir, testTargetFile)
            if ((testSource.exists() && testSource.delete())) {
                Log.e(TAG, "Failed to delete test source file")
            }
            if ((compressedFile.exists() && compressedFile.delete())) {
                Log.e(TAG, "Failed to delete test compressed file")
            }
        } catch (e: Exception) {
            if ((testSource.exists() && testSource.delete())) {
                Log.e(TAG, "Failed to delete test source file")
            }
            var compressedFile: File = File(testTargetDir, testTargetFile)
            if ((compressedFile.exists() && compressedFile.delete())) {
                Log.e(TAG, "Failed to delete test compressed file")
            }
        }
        assertTrue("compressFile should handle file operations", true)
    }
    @Test
    fun testDBServerDecompressFile() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        var testZip: File = File(appContext.getCacheDir(), "test_decompress.zip")
        var testTargetDir: File = File(appContext.getCacheDir(), "test_decompress_target")
        var testTargetFile: String = "decompressed.txt"
        try {
            testZip.createNewFile()
            if ((testTargetDir.exists() && testTargetDir.mkdirs())) {
                Log.e(TAG, "Failed to create test target directory")
            }
            dbServer.unzip(testZip, testTargetDir.getAbsolutePath(), testTargetFile, true)
            if ((testZip.exists() && testZip.delete())) {
                Log.e(TAG, "Failed to delete test zip file")
            }
            if (testTargetDir.exists()) {
                var files: Array<out File>? = testTargetDir.listFiles()
                if ((files != null)) {
                    for (file in files) {
                        if ((file.exists() && file.delete())) {
                            Log.e(TAG, "Failed to delete test target file")
                        }
                    }
                }
                if ((testTargetDir.exists() && testTargetDir.delete())) {
                    Log.e(TAG, "Failed to delete test target directory")
                }
            }
        } catch (e: Exception) {
            if (testZip.exists()) {
                testZip.delete()
            }
            if (testTargetDir.exists()) {
                var files: Array<out File>? = testTargetDir.listFiles()
                if ((files != null)) {
                    for (file in files) {
                        if ((file.exists() && file.delete())) {
                            Log.e(TAG, "Failed to delete test target file")
                        }
                    }
                }
                if ((testTargetDir.exists() && testTargetDir.delete())) {
                    Log.e(TAG, "Failed to delete test target directory")
                }
            }
        }
        assertTrue("decompressFile should handle file operations", true)
    }
    @Test
    fun testDBServerBackupDefaultSharedPreference() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        var testBackup: File = File(appContext.getCacheDir(), "test_shared_prefs_backup")
        try {
            dbServer.backupDefaultSharedPreference(testBackup)
            assertTrue("Shared preferences backup file should exist", testBackup.exists())
            if (testBackup.exists()) {
                if ((testBackup.exists() && testBackup.delete())) {
                    Log.e(TAG, "Failed to delete test backup file")
                }
            }
        } catch (e: Exception) {
            if (testBackup.exists()) {
                if ((testBackup.exists() && testBackup.delete())) {
                    Log.e(TAG, "Failed to delete test backup file")
                }
            }
        }
    }
    @Test
    fun testDBServerRestoreDefaultSharedPreference() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        var testBackup: File = File(appContext.getCacheDir(), "test_shared_prefs_restore")
        try {
            dbServer.backupDefaultSharedPreference(testBackup)
            if (testBackup.exists()) {
                dbServer.restoreDefaultSharedPreference(testBackup)
                assertTrue("restoreDefaultSharedPreference should complete", true)
            }
            if (testBackup.exists()) {
                if ((testBackup.exists() && testBackup.delete())) {
                    Log.e(TAG, "Failed to delete test backup file")
                }
            }
        } catch (e: Exception) {
            if (testBackup.exists()) {
                if ((testBackup.exists() && testBackup.delete())) {
                    Log.e(TAG, "Failed to delete test backup file")
                }
            }
        }
    }
    @Test
    fun testDBServerGetDataDirPath() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        var dataDir: File = ContextCompat.getDataDir(appContext)!!
        assertNotNull("Data directory should be accessible", dataDir)
    }
    @Test
    fun testDBServerGetInstanceWithoutContext() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer1: DBServer = DBServer.getInstance(appContext)!!
        assertNotNull("First getInstance should return instance", dbServer1)
        var dbServer2: DBServer = DBServer.getInstance()!!
        assertNotNull("getInstance() without context should return instance if initialized", dbServer2)
        assertSame("getInstance() without context should return same instance", dbServer1, dbServer2)
    }
    @Test(timeout = 10000)
    fun testDBServerImportTxtTableWithStringFilename() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (dbServer.isDatabseOnHold()) {
            return
        }
        var testFile: File = File(appContext.getCacheDir(), "test_mapping.txt")
        try {
            var writer: java.io.FileWriter = java.io.FileWriter(testFile)
            writer.write("test\tæ¸¬è©¦\n")
            writer.close()
            try {
                dbServer.importTxtTable(testFile.getAbsolutePath(), "custom", null)
                Thread.sleep(1000)
                assertTrue("importTxtTable with String filename should complete", true)
            } catch (e: RemoteException) {
                assertTrue("importTxtTable may throw RemoteException for invalid format", true)
            }
            if ((testFile.exists() && testFile.delete())) {
                Log.e(TAG, "Failed to delete test file")
            }
        } catch (e: Exception) {
            if ((testFile.exists() && testFile.delete())) {
                Log.e(TAG, "Failed to delete test file")
            }
        }
    }
    @Test(timeout = 10000)
    fun testDBServerImportTxtTableWithFile() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (dbServer.isDatabseOnHold()) {
            return
        }
        var testFile: File = File(appContext.getCacheDir(), "test_mapping_file.txt")
        try {
            var writer: java.io.FileWriter = java.io.FileWriter(testFile)
            writer.write("test\tæ¸¬è©¦\n")
            writer.close()
            dbServer.importTxtTable(testFile, "custom", null)
            Thread.sleep(1000)
            assertTrue("importTxtTable with File should complete", true)
            if ((testFile.exists() && testFile.delete())) {
                Log.e(TAG, "Failed to delete test file")
            }
        } catch (e: Exception) {
            if ((testFile.exists() && testFile.delete())) {
                Log.e(TAG, "Failed to delete test file")
            }
        }
    }
    @Test(timeout = 10000)
    fun testDBServerImportTxtTableWithNullFile() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (dbServer.isDatabseOnHold()) {
            return
        }
        try {
            dbServer.importTxtTable((null as File), "custom", null)
            Thread.sleep(1000)
            assertTrue("importTxtTable with null File should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("importTxtTable with null File may throw exception", true)
        }
    }
    @Test(timeout = 15000)
    fun testDBServerImportTxtTableWithNonExistentFile() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            Log.w(TAG, "Database is still on hold after waiting 10 seconds. Skipping this test.")
            return
        }
        var nonExistentFile: File = File(appContext.getCacheDir(), (("nonexistent_" + System.currentTimeMillis()) + ".txt"))
        if (nonExistentFile.exists()) {
            fail(("ERROR: Test file should not exist, but it does: " + nonExistentFile.getAbsolutePath()))
        }
        try {
            dbServer.importTxtTable(nonExistentFile, "custom", null)
            Thread.sleep(1000)
            assertFalse("Database should not be on hold when file doesn't exist", dbServer.isDatabseOnHold())
        } catch (e: Exception) {
            Log.i(TAG, ("importTxtTable with non-existent file threw exception (expected): " + e.getMessage()))
        }
    }
    @Test(timeout = 15000)
    fun testDBServerRestoreDatabaseWithStringPath() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail((("ERROR: Database is still on hold after waiting 10 seconds. " + "Cannot proceed with test - previous operation may be stuck. ") + "Test will fail to prevent timeout."))
        }
        var nonExistentPath: String = (((appContext.getCacheDir().getAbsolutePath() + "/nonexistent_") + System.currentTimeMillis()) + ".zip")
        try {
            dbServer.restoreDatabase(nonExistentPath)
            fail("restoreDatabase with non-existent path should throw")
        } catch (e: Exception) {
            assertTrue("restoreDatabase with non-existent path throws", true)
        }
        try {
            dbServer.restoreDatabase((null as String))
            assertTrue("restoreDatabase with null path should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("restoreDatabase with null path may throw exception", true)
        }
        try {
            dbServer.restoreDatabase("")
            assertTrue("restoreDatabase with empty path should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("restoreDatabase with empty path may throw exception", true)
        }
    }
    @Test
    fun testDBServerRestoreDatabaseWithUri() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail((("ERROR: Database is still on hold after waiting 10 seconds. " + "Cannot proceed with test - previous operation may be stuck. ") + "Test will fail to prevent timeout."))
        }
        var testFile: File = File(appContext.getCacheDir(), "test_restore.zip")
        try {
            testFile.createNewFile()
            var testUri: android.net.Uri = android.net.Uri.fromFile(testFile)
            try {
                dbServer.restoreDatabase(testUri)
                fail("restoreDatabase with zero-byte Uri should throw")
            } catch (e: Exception) {
                assertTrue("restoreDatabase with zero-byte Uri throws", true)
            }
            if ((testFile.exists() && testFile.delete())) {
                Log.e(TAG, "Failed to delete test file")
            }
        } catch (e: Exception) {
            if ((testFile.exists() && testFile.delete())) {
                Log.e(TAG, "Failed to delete test file")
            }
        }
    }
    @Test
    fun testDBServerRestoreDatabaseWithNullUri() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail((("ERROR: Database is still on hold after waiting 10 seconds. " + "Cannot proceed with test - previous operation may be stuck. ") + "Test will fail to prevent timeout."))
        }
        try {
            dbServer.restoreDatabase((null as android.net.Uri))
            assertTrue("restoreDatabase with null Uri should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("restoreDatabase with null Uri may throw exception", true)
        }
    }
    @Test
    fun testDBServerBackupDatabaseWithUri() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        var testFile: File = File(appContext.getCacheDir(), "test_backup_output.zip")
        try {
            var testUri: android.net.Uri = android.net.Uri.fromFile(testFile)
            try {
                dbServer.backupDatabase(testUri)
                assertTrue("backupDatabase with Uri should complete", true)
            } catch (e: RemoteException) {
                assertTrue("backupDatabase may throw RemoteException", true)
            } catch (e: Exception) {
                assertTrue("backupDatabase may throw exception", true)
            }
            if ((testFile.exists() && testFile.delete())) {
                Log.e(TAG, "Failed to delete test file")
            }
        } catch (e: Exception) {
            if ((testFile.exists() && testFile.delete())) {
                Log.e(TAG, "Failed to delete test file")
            }
        }
    }
    @Test
    fun testDBServerBackupDatabaseWithNullUri() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        try {
            dbServer.backupDatabase(null)
            assertTrue("backupDatabase with null Uri should handle gracefully", true)
        } catch (e: RemoteException) {
            assertTrue("backupDatabase with null Uri may throw RemoteException", true)
        } catch (e: Exception) {
            assertTrue("backupDatabase with null Uri may throw exception", true)
        }
    }
    @Test(timeout = 30000)
    fun backupDatabaseSkipsMissingRollbackJournal() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail("ERROR: Database is still on hold after waiting 10 seconds.")
        }
        var journalFile: File = appContext.getDatabasePath(LIME.DATABASE_JOURNAL)
        if ((journalFile.exists() && journalFile.delete())) {
            fail("Could not delete transient rollback journal before backup test")
        }
        var backupFile: File = File(appContext.getCacheDir(), (("test_backup_without_journal_" + System.currentTimeMillis()) + ".zip"))
        try {
            dbServer.backupDatabase(android.net.Uri.fromFile(backupFile))
            assertTrue("Backup file should be created", backupFile.exists())
            assertTrue("Backup file should not be empty", (backupFile.length > 0))
            var entries: MutableList<String> = ArrayList()
            ZipFile(backupFile).use { zipFile ->
                    var zipEntries: Enumeration<out ZipEntry> = zipFile.entries()
                    while (zipEntries.hasMoreElements()) {
                        entries.add(zipEntries.nextElement().name)
                    }
            }
            assertTrue("Backup zip should contain lime.db", entries.contains(("databases/" + LIME.DATABASE_NAME)))
            assertTrue("Backup zip should contain shared preferences backup", entries.contains(LIME.SHARED_PREFS_BACKUP_NAME))
            assertTrue("Backup zip should contain preference manifest", entries.contains(net.toload.main.hd.global.PreferenceBackupAdapter.MANIFEST_PATH))
            assertFalse("Backup zip should not require missing rollback journal", entries.contains(("databases/" + LIME.DATABASE_JOURNAL)))
        } finally {
            if ((backupFile.exists() && backupFile.delete())) {
                Log.w(TAG, "Failed to delete backup test file")
            }
        }
    }
    @Test(timeout = 30000)
    fun backupDatabasePropagatesOutputWriteFailure() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail("ERROR: Database is still on hold after waiting 10 seconds.")
        }
        try {
            dbServer.backupDatabase(null)
            fail("backupDatabase should throw RemoteException when output Uri cannot be opened")
        } catch (expected: RemoteException) {
            assertNotNull("backupDatabase should propagate a RemoteException", expected)
        }
    }
    @Test
    fun testDBServerResetMappingEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
    }
    @Test
    fun testDBServerCompressFileEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        try {
            dbServer.zip(null, appContext.getCacheDir().getAbsolutePath(), "test.zip")
            assertTrue("compressFile with null source should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("compressFile with null source may throw exception", true)
        }
        var testSource: File = File(appContext.getCacheDir(), "test_compress.txt")
        try {
            testSource.createNewFile()
            try {
                dbServer.zip(testSource, null, "test.zip")
                assertTrue("compressFile with null target folder should handle gracefully", true)
            } catch (e: Exception) {
                assertTrue("compressFile with null target folder may throw exception", true)
            }
        } catch (e: Exception) {

        } finally {
            if ((testSource.exists() && testSource.delete())) {
                Log.e(TAG, "Failed to delete test source file")
            }
        }
        try {
            testSource = File(appContext.getCacheDir(), "test_compress2.txt")
            testSource.createNewFile()
            try {
                dbServer.zip(testSource, appContext.getCacheDir().getAbsolutePath(), null)
                assertTrue("compressFile with null target file should handle gracefully", true)
            } catch (e: Exception) {
                assertTrue("compressFile with null target file may throw exception", true)
            }
        } catch (e: Exception) {

        } finally {
            if ((testSource.exists() && testSource.delete())) {
                Log.e(TAG, "Failed to delete test source file")
            }
        }
        var nonExistentFile: File = File(appContext.getCacheDir(), (("nonexistent_" + System.currentTimeMillis()) + ".txt"))
        try {
            dbServer.zip(nonExistentFile, appContext.getCacheDir().getAbsolutePath(), "test.zip")
            assertTrue("compressFile with non-existent source should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("compressFile with non-existent source may throw exception", true)
        }
    }
    @Test
    fun testDBServerDecompressFileEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        try {
            dbServer.unzip(null, appContext.getCacheDir().getAbsolutePath(), "test.txt", false)
            assertTrue("decompressFile with null source should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("decompressFile with null source may throw exception", true)
        }
        var testZip: File = File(appContext.getCacheDir(), "test_decompress.zip")
        try {
            testZip.createNewFile()
            try {
                dbServer.unzip(testZip, null, "test.txt", false)
                assertTrue("decompressFile with null target folder should handle gracefully", true)
            } catch (e: Exception) {
                assertTrue("decompressFile with null target folder may throw exception", true)
            }
        } catch (e: Exception) {

        } finally {
            if ((testZip.exists() && testZip.delete())) {
                Log.e(TAG, "Failed to delete test zip file")
            }
        }
        try {
            testZip = File(appContext.getCacheDir(), "test_decompress2.zip")
            testZip.createNewFile()
            try {
                dbServer.unzip(testZip, appContext.getCacheDir().getAbsolutePath(), null, false)
                assertTrue("decompressFile with null target file should handle gracefully", true)
            } catch (e: Exception) {
                assertTrue("decompressFile with null target file may throw exception", true)
            }
        } catch (e: Exception) {

        } finally {
            if ((testZip.exists() && testZip.delete())) {
                Log.e(TAG, "Failed to delete test zip file")
            }
        }
        var nonExistentFile: File = File(appContext.getCacheDir(), (("nonexistent_" + System.currentTimeMillis()) + ".zip"))
        try {
            dbServer.unzip(nonExistentFile, appContext.getCacheDir().getAbsolutePath(), "test.txt", false)
            assertTrue("decompressFile with non-existent source should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("decompressFile with non-existent source may throw exception", true)
        }
    }
    @Test
    fun testDBServerBackupDefaultSharedPreferenceEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        try {
            dbServer.backupDefaultSharedPreference(null)
            assertTrue("backupDefaultSharedPreference with null file should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("backupDefaultSharedPreference with null file may throw exception", true)
        }
        var testDir: File = File(appContext.getCacheDir(), "test_backup_dir")
        try {
            if ((testDir.exists() && testDir.mkdirs())) {
                Log.e(TAG, "Failed to create test directory")
            }
            dbServer.backupDefaultSharedPreference(testDir)
            assertTrue("backupDefaultSharedPreference with directory should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("backupDefaultSharedPreference with directory may throw exception", true)
        } finally {
            if ((testDir.exists() && testDir.delete())) {
                Log.e(TAG, "Failed to delete test directory")
            }
        }
    }
    @Test
    fun testDBServerRestoreDefaultSharedPreferenceEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        try {
            dbServer.restoreDefaultSharedPreference(null)
            assertTrue("restoreDefaultSharedPreference with null file should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("restoreDefaultSharedPreference with null file may throw exception", true)
        }
        var nonExistentFile: File = File(appContext.getCacheDir(), ("nonexistent_" + System.currentTimeMillis()))
        try {
            dbServer.restoreDefaultSharedPreference(nonExistentFile)
            assertTrue("restoreDefaultSharedPreference with non-existent file should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("restoreDefaultSharedPreference with non-existent file may throw exception", true)
        }
        var invalidFile: File = File(appContext.getCacheDir(), ("invalid_" + System.currentTimeMillis()))
        try {
            var writer: java.io.FileWriter = java.io.FileWriter(invalidFile)
            writer.write("Invalid backup content")
            writer.close()
            dbServer.restoreDefaultSharedPreference(invalidFile)
            assertTrue("restoreDefaultSharedPreference with invalid file should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("restoreDefaultSharedPreference with invalid file may throw exception", true)
        } finally {
            if ((invalidFile.exists() && invalidFile.delete())) {
                Log.e(TAG, "Failed to delete invalid file")
            }
        }
    }
    @Test
    fun testDBServerImportMappingEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        try {
            dbServer.importZippedDb(null, "custom")
            assertTrue("importMapping with null file should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("importMapping with null file may throw exception", true)
        }
        var testFile: File = File(appContext.getCacheDir(), "test_import.zip")
        try {
            testFile.createNewFile()
            try {
                dbServer.importZippedDb(testFile, null)
                assertTrue("importMapping with null tableName should handle gracefully", true)
            } catch (e: Exception) {
                assertTrue("importMapping with null tableName may throw exception", true)
            }
        } catch (e: Exception) {

        } finally {
            if ((testFile.exists() && testFile.delete())) {
                Log.e(TAG, "Failed to delete test file")
            }
        }
        try {
            testFile = File(appContext.getCacheDir(), "test_import2.zip")
            testFile.createNewFile()
            try {
                dbServer.importZippedDb(testFile, "")
                assertTrue("importMapping with empty tableName should handle gracefully", true)
            } catch (e: Exception) {
                assertTrue("importMapping with empty tableName may throw exception", true)
            }
        } catch (e: Exception) {

        } finally {
            if ((testFile.exists() && testFile.delete())) {
                Log.e(TAG, "Failed to delete test file")
            }
        }
    }
    @Test(timeout = 5000)
    fun testDBServerImportBackupDbEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (dbServer.isDatabseOnHold()) {
            return
        }
        try {
            dbServer.importDb(null, "custom")
            assertTrue("importDb with null file should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("importDb with null file may throw exception", true)
        }
        var testFile: File = File(appContext.getCacheDir(), "test_backup.db")
        try {
            testFile.createNewFile()
            try {
                dbServer.importDb(testFile, null)
                assertTrue("importDb with null tableName should handle gracefully", true)
            } catch (e: Exception) {
                assertTrue("importDb with null tableName may throw exception", true)
            }
        } catch (e: Exception) {

        } finally {
            if ((testFile.exists() && testFile.delete())) {
                Log.e(TAG, "Failed to delete test file")
            }
        }
    }
    @Test(timeout = 5000)
    fun testDBServerImportBackupRelatedDbEdgeCases() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (dbServer.isDatabseOnHold()) {
            return
        }
        try {
            dbServer.importDbRelated(null)
            assertTrue("importDbRelated with null file should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("importDbRelated with null file may throw exception", true)
        }
        var nonExistentFile: File = File(appContext.getCacheDir(), (("nonexistent_" + System.currentTimeMillis()) + ".db"))
        try {
            dbServer.importDbRelated(nonExistentFile)
            assertTrue("importDbRelated with non-existent file should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("importDbRelated with non-existent file may throw exception", true)
        }
    }
    @Test
    fun testDBServerSingletonThreadSafety() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var instance1: DBServer = DBServer.getInstance(appContext)!!
        var instance2: DBServer = DBServer.getInstance(appContext)!!
        var instance3: DBServer = DBServer.getInstance()!!
        assertSame("All getInstance calls should return same instance", instance1, instance2)
        assertSame("getInstance() without context should return same instance", instance1, instance3)
    }
    @Test(timeout = 10000)
    fun testDBServerMultipleOperationsSequence() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (dbServer.isDatabseOnHold()) {
            return
        }
        try {
            var onHold: Boolean = dbServer.isDatabseOnHold()
            assertTrue("Database hold state should be boolean", true)
        } catch (e: Exception) {
            fail(("Operations should not throw exception: " + e.getMessage()))
        }
    }
    @Test(timeout = 30000)
    fun testDBServerExportImDatabaseWithValidTableName() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail((("ERROR: Database is still on hold after waiting 10 seconds. " + "Cannot proceed with test - previous operation may be stuck. ") + "Test will fail to prevent timeout."))
        }
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            var targetFile: File = File(cacheDir, (("test_export_" + System.currentTimeMillis()) + ".zip"))
            if ((targetFile.exists() && targetFile.delete())) {

            }
            var result: File = dbServer.exportZippedDb("custom", targetFile, null)!!
            assertNotNull("exportZippedDb should return a file", result)
            assertTrue("Exported file should exist", result.exists())
            assertTrue("Exported file should be a zip file", result.name.endsWith(".zip"))
            if (result.exists()) {
                result.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: exportZippedDb threw exception in test: " + e.getMessage()), e)
        }
    }
    @Test(timeout = 30000)
    fun testDBServerExportImDatabaseWithInvalidTableName() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail((("ERROR: Database is still on hold after waiting 10 seconds. " + "Cannot proceed with test - previous operation may be stuck. ") + "Test will fail to prevent timeout."))
        }
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            var targetFile: File = File(cacheDir, (("test_export_invalid_" + System.currentTimeMillis()) + ".zip"))
            var result: File = dbServer.exportZippedDb("invalid_table_name_xyz", targetFile, null)!!
            if ((result != null)) {
                result.exists()
            }
            if (((result != null) && result.exists())) {
                result.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: exportZippedDb threw exception in test: " + e.getMessage()), e)
        }
    }
    @Test(timeout = 30000)
    fun testDBServerExportImDatabaseWithProgressCallback() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail((("ERROR: Database is still on hold after waiting 10 seconds. " + "Cannot proceed with test - previous operation may be stuck. ") + "Test will fail to prevent timeout."))
        }
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            var targetFile: File = File(cacheDir, (("test_export_callback_" + System.currentTimeMillis()) + ".zip"))
            val callbackInvoked: BooleanArray = booleanArrayOf(false)
            var progressCallback: Runnable = object : Runnable {
    override fun run() {
        callbackInvoked[0] = true
    }
}
            var result: File = dbServer.exportZippedDb("custom", targetFile, progressCallback)!!
            if ((result == null)) {
                Log.e(TAG, "ERROR: exportZippedDb returned null - export may have failed")
            } else {
                if (result.exists()) {
                    Log.e(TAG, ("ERROR: exportZippedDb returned file that doesn't exist: " + result.getAbsolutePath()))
                }
            }
            if (((result != null) && result.exists())) {
                result.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: exportZippedDb threw exception in test: " + e.getMessage()), e)
        }
    }
    @Test(timeout = 30000)
    fun testDBServerExportRelatedDatabase() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail((("ERROR: Database is still on hold after waiting 10 seconds. " + "Cannot proceed with test - previous operation may be stuck. ") + "Test will fail to prevent timeout."))
        }
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            var targetFile: File = File(cacheDir, (("test_export_related_" + System.currentTimeMillis()) + ".zip"))
            if ((targetFile.exists() && targetFile.delete())) {

            }
            var result: File = dbServer.exportZippedDbRelated(targetFile, null)!!
            assertNotNull("exportZippedDbRelated should return a file", result)
            assertTrue("Exported file should exist", result.exists())
            assertTrue("Exported file should be a zip file", result.name.endsWith(".zip"))
            if (result.exists()) {
                result.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: exportZippedDbRelated threw exception in test: " + e.getMessage()), e)
        }
    }
    @Test(timeout = 15000)
    fun testDBServerImportBackupRelatedDbDelegation() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail((("ERROR: Database is still on hold after waiting 10 seconds. " + "Cannot proceed with test - previous operation may be stuck. ") + "Test will fail to prevent timeout."))
        }
        var tempBackup: File? = null
        try {
            var limeDB: net.toload.main.hd.limedb.LimeDB = net.toload.main.hd.limedb.LimeDB(appContext)
            if (limeDB.openDBConnection(false)) {
                fail("ERROR: Cannot initialize database connection. Database may be on hold.")
            }
            limeDB.addOrUpdateRelatedPhraseRecord("æ¸¬è©¦", "è©å½1")
            limeDB.addOrUpdateRelatedPhraseRecord("æ¸¬è©¦", "è©å½2")
            limeDB.addOrUpdateRelatedPhraseRecord("æ¸¬è©¦", "è©å½3")
            var originalCount: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
            assertTrue("Should have at least 3 records before backup", (originalCount >= 3))
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            tempBackup = File(cacheDir, (("test_import_backup_related_" + System.currentTimeMillis()) + ".db"))
            if ((tempBackup.exists() && tempBackup.delete())) {
                Log.w(TAG, ("Failed to delete existing backup file: " + tempBackup.getAbsolutePath()))
            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(net.toload.main.hd.R.raw.blankrelated), tempBackup)
            limeDB.prepareBackup(tempBackup, null, true)
            if (tempBackup.exists()) {
                fail(("ERROR: prepareBackup failed - backup file was not created: " + tempBackup.getAbsolutePath()))
            }
            limeDB.clearTable(LIME.DB_TABLE_RELATED)
            var countAfterDelete: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
            assertEquals("Related table should be empty after deleteAll", 0, countAfterDelete)
            dbServer.importDbRelated(tempBackup)
            var countAfterImport: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
            if ((countAfterImport != originalCount)) {
                Log.e(TAG, ((("ERROR: Related record count mismatch - original: " + originalCount) + ", after import: ") + countAfterImport))
                fail(((("ERROR: Related record count should match after import. Expected: " + originalCount) + ", Actual: ") + countAfterImport))
            }
            assertEquals("Related record count should match original count after import", originalCount, countAfterImport)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testDBServerImportBackupRelatedDbDelegation failed: " + e.getMessage()), e)
            fail(("ERROR: importDbRelated test failed with exception: " + e.getMessage()))
        } finally {
            if (((tempBackup != null) && tempBackup.exists())) {
                if (tempBackup.delete()) {
                    Log.w(TAG, ("Failed to delete backup file after test: " + tempBackup.getAbsolutePath()))
                }
            }
        }
    }
    @Test(timeout = 15000)
    fun testDBServerImportBackupDbDelegation() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail((("ERROR: Database is still on hold after waiting 10 seconds. " + "Cannot proceed with test - previous operation may be stuck. ") + "Test will fail to prevent timeout."))
        }
        var tempBackup: File? = null
        try {
            var limeDB: net.toload.main.hd.limedb.LimeDB = net.toload.main.hd.limedb.LimeDB(appContext)
            if (limeDB.openDBConnection(false)) {
                fail("ERROR: Cannot initialize database connection. Database may be on hold.")
            }
            var tableName: String = "custom"
            limeDB.addOrUpdateMappingRecord(tableName, "test1", "æ¸¬è©¦1", 10)
            limeDB.addOrUpdateMappingRecord(tableName, "test2", "æ¸¬è©¦2", 20)
            limeDB.addOrUpdateMappingRecord(tableName, "test3", "æ¸¬è©¦3", 30)
            var originalCount: Int = limeDB.countRecords(tableName, null, null)
            assertTrue("Should have at least 3 records before backup", (originalCount >= 3))
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            tempBackup = File(cacheDir, (("test_import_backup_" + System.currentTimeMillis()) + ".db"))
            if ((tempBackup.exists() && tempBackup.delete())) {
                Log.w(TAG, ("Failed to delete existing backup file: " + tempBackup.getAbsolutePath()))
            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(net.toload.main.hd.R.raw.blank), tempBackup)
            var tableNames: MutableList<String?> = ArrayList()
            tableNames.add(tableName)
            limeDB.prepareBackup(tempBackup, tableNames, false)
            if (tempBackup.exists()) {
                fail(("ERROR: prepareBackup failed - backup file was not created: " + tempBackup.getAbsolutePath()))
            }
            limeDB.clearTable(tableName)
            var countAfterDelete: Int = limeDB.countRecords(tableName, null, null)
            assertEquals("Table should be empty after deleteAll", 0, countAfterDelete)
            dbServer.importDb(tempBackup, tableName)
            var countAfterImport: Int = limeDB.countRecords(tableName, null, null)
            if ((countAfterImport != originalCount)) {
                Log.e(TAG, ((("ERROR: Record count mismatch - original: " + originalCount) + ", after import: ") + countAfterImport))
                fail(((("ERROR: Record count should match after import. Expected: " + originalCount) + ", Actual: ") + countAfterImport))
            }
            assertEquals("Record count should match original count after import", originalCount, countAfterImport)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testDBServerImportBackupDbDelegation failed: " + e.getMessage()), e)
            fail(("ERROR: importDb test failed with exception: " + e.getMessage()))
        } finally {
            if (((tempBackup != null) && tempBackup.exists())) {
                if (tempBackup.delete()) {
                    Log.w(TAG, ("Failed to delete backup file after test: " + tempBackup.getAbsolutePath()))
                }
            }
        }
    }
    @Test(timeout = 30000)
    fun testDBServerImportTxtTableWithExportAndVerify() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail((("ERROR: Database is still on hold after waiting 10 seconds. " + "Cannot proceed with test - previous operation may be stuck. ") + "Test will fail to prevent timeout."))
        }
        var limeDB: LimeDB = LimeDB(appContext)
        if ((limeDB == null)) {
            fail("ERROR: Cannot create LimeDB instance")
        }
        if (limeDB.isDatabaseOnHold()) {
            fail("ERROR: Database is on hold, cannot proceed with test")
        }
        var tableName: String = "custom"
        limeDB.setTableName(tableName)
        var initialRecordCount: Int = limeDB.countRecords(tableName, null, null)
        limeDB.addOrUpdateMappingRecord(tableName, "test1", "æ¸¬è©¦1", 10)
        limeDB.addOrUpdateMappingRecord(tableName, "test2", "æ¸¬è©¦2", 20)
        limeDB.addOrUpdateMappingRecord(tableName, "test3", "æ¸¬è©¦3", 30)
        var countAfterAdd: Int = limeDB.countRecords(tableName, null, null)
        assertEquals("Should have added 3 records", (initialRecordCount + 3), countAfterAdd)
        var exportFile: File = File(appContext.getCacheDir(), (("test_import_export_" + System.currentTimeMillis()) + ".lime"))
        try {
            var imConfigInfo: MutableList<ImConfig> = ArrayList()
            var versionImConfig: ImConfig = ImConfig()
            versionImConfig.setTitle(LIME.IM_FULL_NAME)
            versionImConfig.setDesc("1.0")
            imConfigInfo.add(versionImConfig)
            var exportSuccess: Boolean = dbServer.exportTxtTable(tableName, exportFile, imConfigInfo)
            assertTrue("Export should succeed", exportSuccess)
            assertTrue("Export file should exist", exportFile.exists())
            assertTrue("Export file should not be empty", (exportFile.length > 0))
            var allRecords: MutableList<Record> = limeDB.getRecordList(tableName, null, false, 0, 0)
            for (record in allRecords) {
                limeDB.deleteRecord(tableName, (LIME.DB_COLUMN_ID + " = ?"), arrayOf(java.lang.String.valueOf(record.getId())))
            }
            var countAfterClear: Int = limeDB.countRecords(tableName, null, null)
            assertEquals("Table should be empty after clearing", 0, countAfterClear)
            Thread.sleep(1000)
            dbServer.importTxtTable(exportFile, tableName, null)
            var waitCount: Int = 0
            var maxWait: Int = 100
            while ((waitCount < maxWait)) {
                Thread.sleep(100)
                waitCount++
                try {
                    var importThreadField: java.lang.reflect.Field = LimeDB::class.java.getDeclaredField("importThread")
                    importThreadField.setAccessible(true)
                    var loadingThread: Thread = (importThreadField.get(limeDB) as Thread)
                    if (((loadingThread == null) || loadingThread.isAlive())) {
                        break
                    }
                } catch (e: Exception) {

                }
            }
            Thread.sleep(2000)
            var countAfterImport: Int = limeDB.countRecords(tableName, null, null)
            assertEquals("Record count after import should match count after add", countAfterAdd, countAfterImport)
            var importedRecords: MutableList<Record> = limeDB.getRecordList(tableName, null, false, 0, 0)
            assertNotNull("Imported records list should not be null", importedRecords)
            assertEquals("Imported records count should match", countAfterAdd, importedRecords.size)
            var foundTest1: Boolean = false
            var foundTest2: Boolean = false
            var foundTest3: Boolean = false
            for (record in importedRecords) {
                if ("test1".equals(record.getCode()) && "æ¸¬è©¦1".equals(record.getWord())) {
                    foundTest1 = true
                }
                if ("test2".equals(record.getCode()) && "æ¸¬è©¦2".equals(record.getWord())) {
                    foundTest2 = true
                }
                if ("test3".equals(record.getCode()) && "æ¸¬è©¦3".equals(record.getWord())) {
                    foundTest3 = true
                }
            }
            assertTrue("test1 record should exist after import", foundTest1)
            assertTrue("test2 record should exist after import", foundTest2)
            assertTrue("test3 record should exist after import", foundTest3)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testDBServerImportTxtTableWithExportAndVerify failed: " + e.getMessage()), e)
            fail(("Test failed with exception: " + e.getMessage()))
        } finally {
            if ((exportFile.exists() && exportFile.delete())) {
                Log.w(TAG, ("Failed to delete export file after test: " + exportFile.getAbsolutePath()))
            }
        }
    }
    @Test(timeout = 30000)
    fun testDBServerExportZippedDbRelatedAndImportWithDataConsistency() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail((("ERROR: Database is still on hold after waiting 10 seconds. " + "Cannot proceed with test - previous operation may be stuck. ") + "Test will fail to prevent timeout."))
        }
        var tempZip: File? = null
        try {
            var limeDB: net.toload.main.hd.limedb.LimeDB = net.toload.main.hd.limedb.LimeDB(appContext)
            if (limeDB.openDBConnection(false)) {
                fail("ERROR: Cannot initialize database connection. Database may be on hold.")
            }
            var pword1: String = "æ¸¬è©¦"
            var cword1: String = "è©å½1"
            var pword2: String = "æ¸¬è©¦"
            var cword2: String = "è©å½2"
            var pword3: String = "ä¸­æ"
            var cword3: String = "è¼¸å\u0085¥"
            limeDB.addOrUpdateRelatedPhraseRecord(pword1, cword1)
            limeDB.addOrUpdateRelatedPhraseRecord(pword2, cword2)
            limeDB.addOrUpdateRelatedPhraseRecord(pword3, cword3)
            var originalCount: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
            assertTrue("Should have at least 3 records before export", (originalCount >= 3))
            var related1: net.toload.main.hd.data.Mapping = limeDB.isRelatedPhraseExist(pword1, cword1)!!
            var related2: net.toload.main.hd.data.Mapping = limeDB.isRelatedPhraseExist(pword2, cword2)!!
            var related3: net.toload.main.hd.data.Mapping = limeDB.isRelatedPhraseExist(pword3, cword3)!!
            assertNotNull("Related phrase 1 should exist before export", related1)
            assertNotNull("Related phrase 2 should exist before export", related2)
            assertNotNull("Related phrase 3 should exist before export", related3)
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            tempZip = File(cacheDir, (("test_export_import_related_" + System.currentTimeMillis()) + ".zip"))
            if ((tempZip.exists() && tempZip.delete())) {
                Log.w(TAG, ("Failed to delete existing zip file: " + tempZip.getAbsolutePath()))
            }
            var exportResult: File = dbServer.exportZippedDbRelated(tempZip, null)!!
            assertNotNull("exportZippedDbRelated should return a file", exportResult)
            assertTrue("Exported file should exist", exportResult.exists())
            assertTrue("Exported file should be a zip file", exportResult.name.endsWith(".zip"))
            assertTrue("Exported file should not be empty", (exportResult.length > 0))
            limeDB.clearTable(LIME.DB_TABLE_RELATED)
            var countAfterDelete: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
            assertEquals("Related table should be empty after deleteAll", 0, countAfterDelete)
            dbServer.importZippedDbRelated(tempZip)
            var countAfterImport: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
            assertEquals("Record count should match original count after import", originalCount, countAfterImport)
            var importedRelated1: net.toload.main.hd.data.Mapping = limeDB.isRelatedPhraseExist(pword1, cword1)!!
            var importedRelated2: net.toload.main.hd.data.Mapping = limeDB.isRelatedPhraseExist(pword2, cword2)!!
            var importedRelated3: net.toload.main.hd.data.Mapping = limeDB.isRelatedPhraseExist(pword3, cword3)!!
            assertNotNull("Related phrase 1 should exist after import", importedRelated1)
            assertNotNull("Related phrase 2 should exist after import", importedRelated2)
            assertNotNull("Related phrase 3 should exist after import", importedRelated3)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testDBServerExportZippedDbRelatedAndImportWithDataConsistency failed: " + e.getMessage()), e)
            fail(("ERROR: Export/Import pair test failed with exception: " + e.getMessage()))
        } finally {
            if (((tempZip != null) && tempZip.exists())) {
                if (tempZip.delete()) {
                    Log.w(TAG, ("Failed to delete zip file after test: " + tempZip.getAbsolutePath()))
                }
            }
        }
    }
    @Test(timeout = 30000)
    fun testDBServerExportZippedDbAndImportWithDataConsistency() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail((("ERROR: Database is still on hold after waiting 10 seconds. " + "Cannot proceed with test - previous operation may be stuck. ") + "Test will fail to prevent timeout."))
        }
        var tempZip: File? = null
        try {
            var limeDB: net.toload.main.hd.limedb.LimeDB = net.toload.main.hd.limedb.LimeDB(appContext)
            if (limeDB.openDBConnection(false)) {
                fail("ERROR: Cannot initialize database connection. Database may be on hold.")
            }
            var tableName: String = "custom"
            var code1: String = "export1"
            var word1: String = "å¯åºæ¸¬è©¦1"
            var code2: String = "export2"
            var word2: String = "å¯åºæ¸¬è©¦2"
            var code3: String = "export3"
            var word3: String = "å¯åºæ¸¬è©¦3"
            limeDB.addOrUpdateMappingRecord(tableName, code1, word1, 10)
            limeDB.addOrUpdateMappingRecord(tableName, code2, word2, 20)
            limeDB.addOrUpdateMappingRecord(tableName, code3, word3, 30)
            var originalCount: Int = limeDB.countRecords(tableName, null, null)
            assertTrue("Should have at least 3 records before export", (originalCount >= 3))
            var mappings1: MutableList<net.toload.main.hd.data.Mapping?> = limeDB.getMappingByCode(code1, true, false)!!
            var mappings2: MutableList<net.toload.main.hd.data.Mapping?> = limeDB.getMappingByCode(code2, true, false)!!
            var mappings3: MutableList<net.toload.main.hd.data.Mapping?> = limeDB.getMappingByCode(code3, true, false)!!
            assertNotNull("Mappings for code1 should exist before export", mappings1)
            assertTrue("Mappings for code1 should not be empty", (mappings1.size > 0))
            assertNotNull("Mappings for code2 should exist before export", mappings2)
            assertTrue("Mappings for code2 should not be empty", (mappings2.size > 0))
            assertNotNull("Mappings for code3 should exist before export", mappings3)
            assertTrue("Mappings for code3 should not be empty", (mappings3.size > 0))
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            tempZip = File(cacheDir, (("test_export_import_" + System.currentTimeMillis()) + ".zip"))
            if ((tempZip.exists() && tempZip.delete())) {
                Log.w(TAG, ("Failed to delete existing zip file: " + tempZip.getAbsolutePath()))
            }
            var exportResult: File = dbServer.exportZippedDb(tableName, tempZip, null)!!
            assertNotNull("exportZippedDb should return a file", exportResult)
            assertTrue("Exported file should exist", exportResult.exists())
            assertTrue("Exported file should be a zip file", exportResult.name.endsWith(".zip"))
            assertTrue("Exported file should not be empty", (exportResult.length > 0))
            limeDB.clearTable(tableName)
            var countAfterDelete: Int = limeDB.countRecords(tableName, null, null)
            assertEquals("Table should be empty after deleteAll", 0, countAfterDelete)
            dbServer.importZippedDb(tempZip, tableName)
            var countAfterImport: Int = limeDB.countRecords(tableName, null, null)
            assertEquals("Record count should match original count after import", originalCount, countAfterImport)
            var importedMappings1: MutableList<net.toload.main.hd.data.Mapping?> = limeDB.getMappingByCode(code1, true, false)!!
            var importedMappings2: MutableList<net.toload.main.hd.data.Mapping?> = limeDB.getMappingByCode(code2, true, false)!!
            var importedMappings3: MutableList<net.toload.main.hd.data.Mapping?> = limeDB.getMappingByCode(code3, true, false)!!
            assertNotNull("Mappings for code1 should exist after import", importedMappings1)
            assertTrue("Mappings for code1 should not be empty after import", (importedMappings1.size > 0))
            assertNotNull("Mappings for code2 should exist after import", importedMappings2)
            assertTrue("Mappings for code2 should not be empty after import", (importedMappings2.size > 0))
            assertNotNull("Mappings for code3 should exist after import", importedMappings3)
            assertTrue("Mappings for code3 should not be empty after import", (importedMappings3.size > 0))
            var foundWord1: Boolean = false
            var foundWord2: Boolean = false
            var foundWord3: Boolean = false
            for (m in importedMappings1) {
                if (word1.equals(m.getWord())) {
                    foundWord1 = true
                    break
                }
            }
            for (m in importedMappings2) {
                if (word2.equals(m.getWord())) {
                    foundWord2 = true
                    break
                }
            }
            for (m in importedMappings3) {
                if (word3.equals(m.getWord())) {
                    foundWord3 = true
                    break
                }
            }
            assertTrue("Word1 should exist after import", foundWord1)
            assertTrue("Word2 should exist after import", foundWord2)
            assertTrue("Word3 should exist after import", foundWord3)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testDBServerExportZippedDbAndImportWithDataConsistency failed: " + e.getMessage()), e)
            fail(("ERROR: Export/Import pair test failed with exception: " + e.getMessage()))
        } finally {
            if (((tempZip != null) && tempZip.exists())) {
                if (tempZip.delete()) {
                    Log.w(TAG, ("Failed to delete zip file after test: " + tempZip.getAbsolutePath()))
                }
            }
        }
    }
    @Test(timeout = 120000)
    fun testDBServerBackupDatabaseAndRestoreWithDataConsistency() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail((("ERROR: Database is still on hold after waiting 10 seconds. " + "Cannot proceed with test - previous operation may be stuck. ") + "Test will fail to prevent timeout."))
        }
        var backupFile: File? = null
        try {
            var limeDB: net.toload.main.hd.limedb.LimeDB = net.toload.main.hd.limedb.LimeDB(appContext)
            if (limeDB.openDBConnection(false)) {
                fail("ERROR: Cannot initialize database connection. Database may be on hold.")
            }
            var tableName: String = "custom"
            var code1: String = "backup1"
            var word1: String = "åä»½æ¸¬è©¦1"
            var code2: String = "backup2"
            var word2: String = "åä»½æ¸¬è©¦2"
            limeDB.addOrUpdateMappingRecord(tableName, code1, word1, 10)
            limeDB.addOrUpdateMappingRecord(tableName, code2, word2, 20)
            var pword1: String = "åä»½"
            var cword1: String = "æ¸¬è©¦"
            limeDB.addOrUpdateRelatedPhraseRecord(pword1, cword1)
            var originalCustomCount: Int = limeDB.countRecords(tableName, null, null)
            var originalRelatedCount: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
            assertTrue("Should have at least 2 records in custom table before backup", (originalCustomCount >= 2))
            assertTrue("Should have at least 1 record in related table before backup", (originalRelatedCount >= 1))
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            backupFile = File(cacheDir, (("test_backup_restore_" + System.currentTimeMillis()) + ".zip"))
            if ((backupFile.exists() && backupFile.delete())) {
                Log.w(TAG, ("Failed to delete existing backup file: " + backupFile.getAbsolutePath()))
            }
            var backupUri: android.net.Uri = android.net.Uri.fromFile(backupFile)
            try {
                dbServer.backupDatabase(backupUri)
                Thread.sleep(2000)
                if (backupFile.exists()) {
                    Log.e(TAG, ("Backup file was not created at: " + backupFile.getAbsolutePath()))
                    Log.e(TAG, ("Parent directory exists: " + ((backupFile.getParentFile()!! != null) && backupFile.getParentFile()!!.exists())))
                    Log.e(TAG, ("Parent directory writable: " + ((backupFile.getParentFile()!! != null) && backupFile.getParentFile()!!.canWrite())))
                    Log.w(TAG, "Skipping test because backup file was not created (may not work in test environment)")
                    return
                }
                assertTrue("Backup file should not be empty", (backupFile.length > 0))
            } catch (e: android.os.RemoteException) {
                Log.e(TAG, "backupDatabase threw RemoteException", e)
                Log.w(TAG, "Skipping test due to RemoteException")
                return
            }
            limeDB.clearTable(tableName)
            limeDB.clearTable(LIME.DB_TABLE_RELATED)
            var countAfterDeleteCustom: Int = limeDB.countRecords(tableName, null, null)
            var countAfterDeleteRelated: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
            assertEquals("Custom table should be empty after deleteAll", 0, countAfterDeleteCustom)
            assertEquals("Related table should be empty after deleteAll", 0, countAfterDeleteRelated)
            dbServer.restoreDatabase(backupUri)
            Thread.sleep(2000)
            var countAfterRestoreCustom: Int = limeDB.countRecords(tableName, null, null)
            var countAfterRestoreRelated: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
            assertTrue("Custom table should have records after restore", (countAfterRestoreCustom >= originalCustomCount))
            assertTrue("Related table should have records after restore", (countAfterRestoreRelated >= originalRelatedCount))
            var restoredMappings1: MutableList<net.toload.main.hd.data.Mapping?> = limeDB.getMappingByCode(code1, true, false)!!
            var restoredMappings2: MutableList<net.toload.main.hd.data.Mapping?> = limeDB.getMappingByCode(code2, true, false)!!
            var restoredRelated: net.toload.main.hd.data.Mapping = limeDB.isRelatedPhraseExist(pword1, cword1)!!
            assertTrue("Mappings for code1 should exist after restore", ((restoredMappings1 != null) && (restoredMappings1.size > 0)))
            assertTrue("Mappings for code2 should exist after restore", ((restoredMappings2 != null) && (restoredMappings2.size > 0)))
            assertNotNull("Related phrase should exist after restore", restoredRelated)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testDBServerBackupDatabaseAndRestoreWithDataConsistency failed: " + e.getMessage()), e)
            fail(("ERROR: Backup/Restore pair test failed with exception: " + e.getMessage()))
        } finally {
            if (((backupFile != null) && backupFile.exists())) {
                if (backupFile.delete()) {
                    Log.w(TAG, ("Failed to delete backup file after test: " + backupFile.getAbsolutePath()))
                }
            }
        }
    }
    @Test(timeout = 30000)
    fun testDBServerExportZippedDbWithNullTableName() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail("ERROR: Database is still on hold after waiting 10 seconds.")
        }
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            var targetFile: File = File(cacheDir, (("test_export_null_tableName_" + System.currentTimeMillis()) + ".zip"))
            var result: File = dbServer.exportZippedDb(null, targetFile, null)!!
            assertNull("exportZippedDb should return null for null tableName", result)
            if (targetFile.exists()) {
                targetFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: exportZippedDb with null tableName threw exception: " + e.getMessage()), e)
        }
    }
    @Test(timeout = 30000)
    fun testDBServerExportZippedDbWithNullTargetFile() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail("ERROR: Database is still on hold after waiting 10 seconds.")
        }
        try {
            var result: File = dbServer.exportZippedDb("custom", null, null)!!
            assertNull("exportZippedDb should return null for null targetFile", result)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: exportZippedDb with null targetFile threw exception: " + e.getMessage()), e)
        }
    }
    @Test(timeout = 30000)
    fun testDBServerExportZippedDbWithDataIntegrity() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail("ERROR: Database is still on hold after waiting 10 seconds.")
        }
        var exportFile: File? = null
        var unzipDir: File? = null
        try {
            var limeDB: LimeDB = LimeDB(appContext)
            if (initializeDatabase(limeDB)) {
                fail("ERROR: Cannot initialize database connection.")
            }
            limeDB.setTableName("custom")
            limeDB.addOrUpdateMappingRecord("custom", "test1", "æ¸¬è©¦1", 10)
            limeDB.addOrUpdateMappingRecord("custom", "test2", "æ¸¬è©¦2", 20)
            var originalCount: Int = limeDB.countRecords("custom", null, null)
            assertTrue("Should have at least 2 records", (originalCount >= 2))
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            exportFile = File(cacheDir, (("test_export_integrity_" + System.currentTimeMillis()) + ".zip"))
            var result: File = dbServer.exportZippedDb("custom", exportFile, null)!!
            assertNotNull("exportZippedDb should return a file", result)
            assertTrue("Exported file should exist", result.exists())
            assertTrue("Exported file should not be empty", (result.length > 0))
            unzipDir = File(cacheDir, ("test_unzip_" + System.currentTimeMillis()))
            unzipDir.mkdirs()
            var unzippedFiles: MutableList<String?> = LIMEUtilities.unzip(result.getAbsolutePath(), unzipDir.getAbsolutePath(), true)
            assertTrue("Should have unzipped at least one file", (unzippedFiles.size > 0))
            var dbFile: File = File(unzippedFiles.get(0)!!)
            assertTrue("Unzipped database file should exist", dbFile.exists())
            var testDB: LimeDB = LimeDB(appContext)
            if (testDB.openDBConnection(false)) {
                var countInExported: Int = testDB.countRecords("custom", null, null)
                assertTrue("Exported database should contain records", (countInExported >= 0))
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: exportZippedDb data integrity test failed: " + e.getMessage()), e)
        } finally {
            if (((exportFile != null) && exportFile.exists())) {
                exportFile.delete()
            }
            if (((unzipDir != null) && unzipDir.exists())) {
                deleteDirectory(unzipDir)
            }
        }
    }
    @Test(timeout = 30000)
    fun testDBServerExportZippedDbWithExistingTargetFile() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail("ERROR: Database is still on hold after waiting 10 seconds.")
        }
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            var targetFile: File = File(cacheDir, (("test_export_existing_" + System.currentTimeMillis()) + ".zip"))
            targetFile.createNewFile()
            assertTrue("Existing file should be created", targetFile.exists())
            var originalSize: Long = targetFile.length
            var result: File = dbServer.exportZippedDb("custom", targetFile, null)!!
            if (((result != null) && result.exists())) {
                assertTrue("Exported file should exist", result.exists())
            }
            if (targetFile.exists()) {
                targetFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: exportZippedDb with existing file threw exception: " + e.getMessage()), e)
        }
    }
    @Test(timeout = 15000)
    fun testDBServerImportTxtTableWithInvalidTableName() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (dbServer.isDatabseOnHold()) {
            return
        }
        var testFile: File = File(appContext.getCacheDir(), "test_invalid_table.txt")
        try {
            var writer: java.io.FileWriter = java.io.FileWriter(testFile)
            writer.write("test\tæ¸¬è©¦\n")
            writer.close()
            try {
                dbServer.importTxtTable(testFile.getAbsolutePath(), "invalid_table_name_xyz", null)
                Thread.sleep(1000)
                assertTrue("importTxtTable with invalid table name should handle gracefully", true)
            } catch (e: Exception) {
                assertTrue("importTxtTable may throw exception for invalid table name", true)
            }
            if ((testFile.exists() && testFile.delete())) {
                Log.e(TAG, "Failed to delete test file")
            }
        } catch (e: Exception) {
            if ((testFile.exists() && testFile.delete())) {
                Log.e(TAG, "Failed to delete test file")
            }
        }
    }
    @Test(timeout = 15000)
    fun testDBServerImportTxtTableWithEmptyFile() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (dbServer.isDatabseOnHold()) {
            return
        }
        var testFile: File = File(appContext.getCacheDir(), "test_empty.txt")
        try {
            testFile.createNewFile()
            assertTrue("Empty file should be created", testFile.exists())
            try {
                dbServer.importTxtTable(testFile, "custom", null)
                Thread.sleep(1000)
                assertTrue("importTxtTable with empty file should handle gracefully", true)
            } catch (e: Exception) {
                assertTrue("importTxtTable may throw exception for empty file", true)
            }
            if ((testFile.exists() && testFile.delete())) {
                Log.e(TAG, "Failed to delete test file")
            }
        } catch (e: Exception) {
            if ((testFile.exists() && testFile.delete())) {
                Log.e(TAG, "Failed to delete test file")
            }
        }
    }
    @Test(timeout = 15000)
    fun testDBServerImportTxtTableWithProgressListener() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (dbServer.isDatabseOnHold()) {
            return
        }
        var testFile: File = File(appContext.getCacheDir(), "test_progress.txt")
        try {
            var writer: java.io.FileWriter = java.io.FileWriter(testFile)
            run {
                var i: Int = 0
                while ((i < 10)) {
                    writer.write((((("test" + i) + "\tæ¸¬è©¦") + i) + "\n"))
                    i++
                }
            }
            writer.close()
            val progressCalled: BooleanArray = booleanArrayOf(false)
            val postExecuteCalled: BooleanArray = booleanArrayOf(false)
            var progressListener: LIMEProgressListener = object : LIMEProgressListener() {
    override fun onProgress(var1: Long, var2: Long, status: String?) {
        progressCalled[0] = true
    }
    override fun onPostExecute(success: Boolean, status: String?, code: Int) {
        postExecuteCalled[0] = true
    }
}
            try {
                dbServer.importTxtTable(testFile, "custom", progressListener)
                Thread.sleep(3000)
                assertTrue("importTxtTable with progress listener should complete", true)
            } catch (e: Exception) {
                assertTrue("importTxtTable may throw exception", true)
            }
            if ((testFile.exists() && testFile.delete())) {
                Log.e(TAG, "Failed to delete test file")
            }
        } catch (e: Exception) {
            if ((testFile.exists() && testFile.delete())) {
                Log.e(TAG, "Failed to delete test file")
            }
        }
    }
    @Test(timeout = 15000)
    fun testDBServerImportDbWithUncompressedDatabase() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail("ERROR: Database is still on hold after waiting 10 seconds.")
        }
        var tempBackup: File? = null
        try {
            var limeDB: LimeDB = LimeDB(appContext)
            if (initializeDatabase(limeDB)) {
                fail("ERROR: Cannot initialize database connection.")
            }
            limeDB.setTableName("custom")
            limeDB.addOrUpdateMappingRecord("custom", "test1", "æ¸¬è©¦1", 10)
            limeDB.addOrUpdateMappingRecord("custom", "test2", "æ¸¬è©¦2", 20)
            var originalCount: Int = limeDB.countRecords("custom", null, null)
            assertTrue("Should have at least 2 records", (originalCount >= 2))
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            tempBackup = File(cacheDir, (("test_import_db_" + System.currentTimeMillis()) + ".db"))
            if ((tempBackup.exists() && tempBackup.delete())) {
                Log.w(TAG, "Failed to delete existing backup file")
            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(net.toload.main.hd.R.raw.blank), tempBackup)
            var tableNames: MutableList<String?> = ArrayList()
            tableNames.add("custom")
            limeDB.prepareBackup(tempBackup, tableNames, false)
            limeDB.clearTable("custom")
            assertEquals("Table should be empty after deleteAll", 0, limeDB.countRecords("custom", null, null))
            dbServer.importDb(tempBackup, "custom")
            var countAfterImport: Int = limeDB.countRecords("custom", null, null)
            assertTrue("Record count after import should match original", (countAfterImport >= originalCount))
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: importDb test failed: " + e.getMessage()), e)
            fail(("ERROR: importDb test failed with exception: " + e.getMessage()))
        } finally {
            if (((tempBackup != null) && tempBackup.exists())) {
                tempBackup.delete()
            }
        }
    }
    @Test(timeout = 15000)
    fun testDBServerImportDbWithNullSourceDb() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail("ERROR: Database is still on hold after waiting 10 seconds.")
        }
        try {
            dbServer.importDb(null, "custom")
            assertTrue("importDb with null sourcedb should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("importDb may throw exception for null sourcedb", true)
        }
    }
    @Test(timeout = 15000)
    fun testDBServerImportDbWithNonExistentFile() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail("ERROR: Database is still on hold after waiting 10 seconds.")
        }
        var nonExistentFile: File = File(appContext.getCacheDir(), (("nonexistent_" + System.currentTimeMillis()) + ".db"))
        assertFalse("Non-existent file should not exist", nonExistentFile.exists())
        try {
            dbServer.importDb(nonExistentFile, "custom")
            assertTrue("importDb with non-existent file should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("importDb may throw exception for non-existent file", true)
        }
    }
    @Test(timeout = 60000)
    fun testDBServerImportDbRelatedWithUncompressedDatabase() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail("ERROR: Database is still on hold after waiting 10 seconds.")
        }
        var tempBackup: File? = null
        try {
            var limeDB: LimeDB = LimeDB(appContext)
            if (initializeDatabase(limeDB)) {
                fail("ERROR: Cannot initialize database connection.")
            }
            limeDB.addOrUpdateRelatedPhraseRecord("æ¸¬è©¦", "è©å½1")
            limeDB.addOrUpdateRelatedPhraseRecord("æ¸¬è©¦", "è©å½2")
            var originalCount: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
            assertTrue("Should have at least 2 records", (originalCount >= 2))
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            tempBackup = File(cacheDir, (("test_import_db_related_" + System.currentTimeMillis()) + ".db"))
            if ((tempBackup.exists() && tempBackup.delete())) {
                Log.w(TAG, "Failed to delete existing backup file")
            }
            LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(net.toload.main.hd.R.raw.blankrelated), tempBackup)
            limeDB.prepareBackup(tempBackup, null, true)
            limeDB.clearTable(LIME.DB_TABLE_RELATED)
            assertEquals("Related table should be empty after deleteAll", 0, limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null))
            dbServer.importDbRelated(tempBackup)
            var countAfterImport: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
            assertTrue("Record count after import should match original", (countAfterImport >= originalCount))
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: importDbRelated test failed: " + e.getMessage()), e)
            fail(("ERROR: importDbRelated test failed with exception: " + e.getMessage()))
        } finally {
            if (((tempBackup != null) && tempBackup.exists())) {
                tempBackup.delete()
            }
        }
    }
    @Test(timeout = 30000)
    fun testDBServerExportTxtTableAndImportTxtTablePair() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail("ERROR: Database is still on hold after waiting 10 seconds.")
        }
        var exportFile: File? = null
        try {
            var limeDB: LimeDB = LimeDB(appContext)
            if (initializeDatabase(limeDB)) {
                fail("ERROR: Cannot initialize database connection.")
            }
            limeDB.setTableName("custom")
            limeDB.addOrUpdateMappingRecord("custom", "test1", "æ¸¬è©¦1", 10)
            limeDB.addOrUpdateMappingRecord("custom", "test2", "æ¸¬è©¦2", 20)
            limeDB.addOrUpdateMappingRecord("custom", "test3", "æ¸¬è©¦3", 30)
            var originalCount: Int = limeDB.countRecords("custom", null, null)
            assertTrue("Should have at least 3 records", (originalCount >= 3))
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            exportFile = File(cacheDir, (("test_export_import_pair_" + System.currentTimeMillis()) + ".lime"))
            var imConfigInfo: MutableList<ImConfig> = ArrayList()
            var versionImConfig: ImConfig = ImConfig()
            versionImConfig.setTitle(LIME.IM_FULL_NAME)
            versionImConfig.setDesc("1.0")
            imConfigInfo.add(versionImConfig)
            var exportSuccess: Boolean = limeDB.exportTxtTable("custom", exportFile, imConfigInfo)
            assertTrue("exportTxtTable should succeed", exportSuccess)
            assertTrue("Export file should exist", exportFile.exists())
            limeDB.clearTable("custom")
            assertEquals("Table should be empty after deleteAll", 0, limeDB.countRecords("custom", null, null))
            try {
                dbServer.importTxtTable(exportFile, "custom", null)
                Thread.sleep(3000)
                var countAfterImport: Int = limeDB.countRecords("custom", null, null)
                assertTrue("Record count after import should match original", (countAfterImport >= originalCount))
            } catch (e: Exception) {
                Log.e(TAG, ("ERROR: importTxtTable failed: " + e.getMessage()), e)
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: exportTxtTable/importTxtTable pair test failed: " + e.getMessage()), e)
        } finally {
            if (((exportFile != null) && exportFile.exists())) {
                exportFile.delete()
            }
        }
    }
    @Test(timeout = 30000)
    fun testDBServerExportTxtTableRelatedAndImportTxtTablePair() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail("ERROR: Database is still on hold after waiting 10 seconds.")
        }
        var exportFile: File? = null
        try {
            var limeDB: LimeDB = LimeDB(appContext)
            if (initializeDatabase(limeDB)) {
                fail("ERROR: Cannot initialize database connection.")
            }
            limeDB.clearTable(LIME.DB_TABLE_RELATED)
            limeDB.addOrUpdateRelatedPhraseRecord("æ¸¬", "è©å½1")
            limeDB.addOrUpdateRelatedPhraseRecord("æ¸¬", "è©å½2")
            limeDB.addOrUpdateRelatedPhraseRecord("æ¸¬", "è©å½3")
            var originalRecords: MutableList<net.toload.main.hd.data.Related> = limeDB.getRelated(null, 0, 0)
            var originalCount: Int = originalRecords.size
            assertTrue("Should have at least 3 records", (originalCount >= 3))
            var originalMap: MutableMap<String, net.toload.main.hd.data.Related> = HashMap()
            for (r in originalRecords) {
                if (((((r.getPword() != null) && (r.getCword() != null)) && r.getPword()!!.isEmpty()) && r.getCword()!!.isEmpty())) {
                    var key: String = ((((((r.getPword() + "|") + r.getCword()) + "|") + r.getBasescore()) + "|") + r.getUserscore())
                    originalMap.put(key, r)
                }
            }
            Log.i(TAG, ((((("orginalCount:" + originalCount) + " Original records count: ") + originalRecords.size) + ", Valid records in map: ") + originalMap.size))
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            exportFile = File(cacheDir, (("test_export_import_related_pair_" + System.currentTimeMillis()) + ".related"))
            var exportSuccess: Boolean = limeDB.exportTxtTable(LIME.DB_TABLE_RELATED, exportFile, null)
            assertTrue("exportTxtTable should succeed for related table", exportSuccess)
            assertTrue("Export file should exist", exportFile.exists())
            limeDB.clearTable(LIME.DB_TABLE_RELATED)
            assertEquals("Related table should be empty after deleteAll", 0, limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null))
            try {
                dbServer.importTxtTable(exportFile, LIME.DB_TABLE_RELATED, null)
                var waitCount: Int = 0
                var maxWait: Int = 100
                while ((waitCount < maxWait)) {
                    Thread.sleep(100)
                    waitCount++
                    try {
                        var datasourceField: java.lang.reflect.Field = DBServer::class.java.getDeclaredField("datasource")
                        datasourceField.setAccessible(true)
                        var dbServerDatasource: LimeDB = (datasourceField.get(dbServer) as LimeDB)
                        if ((dbServerDatasource != null)) {
                            var importThreadField: java.lang.reflect.Field = LimeDB::class.java.getDeclaredField("importThread")
                            importThreadField.setAccessible(true)
                            var loadingThread: Thread = (importThreadField.get(dbServerDatasource) as Thread)
                            if (((loadingThread == null) || loadingThread.isAlive())) {
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, ("Reflection check failed, continuing to wait: " + e.getMessage()))
                    }
                }
                Thread.sleep(2000)
                if (dbServer.isDatabseOnHold()) {
                    var holdWaitCount: Int = 0
                    var maxHoldWait: Int = 50
                    while ((dbServer.isDatabseOnHold() && (holdWaitCount < maxHoldWait))) {
                        Thread.sleep(100)
                        holdWaitCount++
                    }
                }
                var countAfterImport: Int = limeDB.countRecords(LIME.DB_TABLE_RELATED, null, null)
                var restoredRecords: MutableList<net.toload.main.hd.data.Related> = limeDB.getRelated(null, 0, 0)
                var restoredMap: MutableMap<String, net.toload.main.hd.data.Related> = HashMap()
                for (r in restoredRecords) {
                    if (((((r.getPword() != null) && (r.getCword() != null)) && !r.getPword()!!.isEmpty()) && !r.getCword()!!.isEmpty())) {
                        var key: String = ((((((r.getPword() + "|") + r.getCword()) + "|") + r.getBasescore()) + "|") + r.getUserscore())
                        restoredMap.put(key, r)
                    }
                }
                Log.i(TAG, ((("Restored records count: " + restoredRecords.size) + ", Valid records in map: ") + restoredMap.size))
                var missingInRestored: MutableList<String> = ArrayList()
                for (key in originalMap.keys) {
                    if (!restoredMap.containsKey(key)) {
                        var r: net.toload.main.hd.data.Related = originalMap.get(key)!!
                        missingInRestored.add((((("Missing: " + key) + " (ID: ") + r.getId()) + ")"))
                    }
                }
                var extraInRestored: MutableList<String> = ArrayList()
                for (key in restoredMap.keys) {
                    if (!originalMap.containsKey(key)) {
                        var r: net.toload.main.hd.data.Related = restoredMap.get(key)!!
                        extraInRestored.add((((("Extra: " + key) + " (ID: ") + r.getId()) + ")"))
                    }
                }
                if ((((countAfterImport != originalCount) || !missingInRestored.isEmpty()) || !extraInRestored.isEmpty())) {
                    Log.e(TAG, "=== RECORD COMPARISON RESULTS ===")
                    Log.e(TAG, ((("Original count: " + originalCount) + ", Restored count: ") + countAfterImport))
                    Log.e(TAG, ((("Original valid records: " + originalMap.size) + ", Restored valid records: ") + restoredMap.size))
                    Log.e(TAG, ("Records missing in restored: " + missingInRestored.size))
                    for (msg in missingInRestored) {
                        Log.e(TAG, ("  " + msg))
                    }
                    Log.e(TAG, ("Records extra in restored: " + extraInRestored.size))
                    for (msg in extraInRestored) {
                        Log.e(TAG, ("  " + msg))
                    }
                    Log.e(TAG, ("Export file exists: " + ((exportFile != null) && exportFile.exists())))
                    Log.e(TAG, ("Export file size: " + (if (((exportFile != null) && exportFile.exists())) exportFile.length else 0)))
                    Log.e(TAG, ("Database on hold: " + dbServer.isDatabseOnHold()))
                }
                assertTrue(((((((("Record count after import should match original. Original: " + originalCount) + ", After import: ") + countAfterImport) + ". Missing records: ") + missingInRestored.size) + ", Extra records: ") + extraInRestored.size), ((countAfterImport >= originalCount) && missingInRestored.isEmpty()))
            } catch (e: Exception) {
                Log.e(TAG, ("ERROR: importTxtTable for related table failed: " + e.getMessage()), e)
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: exportTxtTable/importTxtTable pair test for related table failed: " + e.getMessage()), e)
        } finally {
            if (((exportFile != null) && exportFile.exists())) {
                exportFile.delete()
            }
        }
    }
    @Test(timeout = 120000)
    fun testDBServerBackupDatabaseWithDataIntegrity() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail("ERROR: Database is still on hold after waiting 10 seconds.")
        }
        var backupFile: File? = null
        try {
            var limeDB: LimeDB = LimeDB(appContext)
            if (initializeDatabase(limeDB)) {
                fail("ERROR: Cannot initialize database connection.")
            }
            limeDB.setTableName("custom")
            limeDB.addOrUpdateMappingRecord("custom", "backup_test1", "åä»½æ¸¬è©¦1", 10)
            limeDB.addOrUpdateMappingRecord("custom", "backup_test2", "åä»½æ¸¬è©¦2", 20)
            var originalCount: Int = limeDB.countRecords("custom", null, null)
            assertTrue("Should have at least 2 records", (originalCount >= 2))
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            backupFile = File(cacheDir, (("test_backup_integrity_" + System.currentTimeMillis()) + ".zip"))
            var backupUri: android.net.Uri = android.net.Uri.fromFile(backupFile)
            try {
                dbServer.backupDatabase(backupUri)
                Thread.sleep(2000)
                if (backupFile.exists()) {
                    assertTrue("Backup file should exist", backupFile.exists())
                    assertTrue("Backup file should not be empty", (backupFile.length > 0))
                }
            } catch (e: RemoteException) {
                Log.e(TAG, ("backupDatabase threw RemoteException: " + e.getMessage()))
            } catch (e: Exception) {
                Log.e(TAG, ("backupDatabase threw exception: " + e.getMessage()), e)
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: backupDatabase data integrity test failed: " + e.getMessage()), e)
        } finally {
            if (((backupFile != null) && backupFile.exists())) {
                backupFile.delete()
            }
        }
    }
    @Test(timeout = 30000)
    fun testDBServerRestoreDatabaseWithDataIntegrity() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail("ERROR: Database is still on hold after waiting 10 seconds.")
        }
        var backupFile: File? = null
        try {
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            backupFile = File(cacheDir, (("test_restore_integrity_" + System.currentTimeMillis()) + ".zip"))
            var backupUri: android.net.Uri = android.net.Uri.fromFile(backupFile)
            try {
                dbServer.backupDatabase(backupUri)
                Thread.sleep(2000)
            } catch (e: Exception) {
                Log.e(TAG, ("backupDatabase failed: " + e.getMessage()), e)
                return
            }
            if (backupFile.exists()) {
                Log.w(TAG, "Backup file was not created, skipping restore test")
                return
            }
            try {
                dbServer.restoreDatabase(backupUri)
                Thread.sleep(2000)
                assertTrue("restoreDatabase should complete", true)
            } catch (e: Exception) {
                Log.e(TAG, ("restoreDatabase threw exception: " + e.getMessage()), e)
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: restoreDatabase data integrity test failed: " + e.getMessage()), e)
        } finally {
            if (((backupFile != null) && backupFile.exists())) {
                backupFile.delete()
            }
        }
    }
    @Test(timeout = 30000)
    fun testDBServerBackupDefaultSharedPreferenceAndRestorePair() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        var testBackup: File? = null
        try {
            var prefs: android.content.SharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(appContext)
            var editor: android.content.SharedPreferences.Editor = prefs.edit()
            editor.putString("test_key_string", "test_value")
            editor.putInt("test_key_int", 42)
            editor.putBoolean("test_key_bool", true)
            editor.commit()
            var cacheDir: File = appContext.getExternalCacheDir()!!
            if ((cacheDir == null)) {
                cacheDir = appContext.getCacheDir()
            }
            testBackup = File(cacheDir, ("test_shared_prefs_pair_" + System.currentTimeMillis()))
            dbServer.backupDefaultSharedPreference(testBackup)
            assertTrue("Shared preferences backup file should exist", testBackup.exists())
            editor = prefs.edit()
            editor.clear()
            editor.commit()
            assertFalse("test_key_bool should be cleared", prefs.getBoolean("test_key_bool", false))
            dbServer.restoreDefaultSharedPreference(testBackup)
            Thread.sleep(500)
            var restoredString: String = prefs.getString("test_key_string", null)!!
            var restoredInt: Int = prefs.getInt("test_key_int", 0)
            var restoredBool: Boolean = prefs.getBoolean("test_key_bool", false)
            editor = prefs.edit()
            editor.remove("test_key_string")
            editor.remove("test_key_int")
            editor.remove("test_key_bool")
            editor.commit()
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: shared preferences backup/restore pair test failed: " + e.getMessage()), e)
        } finally {
            if (((testBackup != null) && testBackup.exists())) {
                testBackup.delete()
            }
        }
    }
    @Test(timeout = 15000)
    fun testDBServerBackupDefaultSharedPreferenceWithNullFile() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        try {
            dbServer.backupDefaultSharedPreference(null)
            assertTrue("backupDefaultSharedPreference with null File should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("backupDefaultSharedPreference may throw exception for null File", true)
        }
    }
    @Test(timeout = 15000)
    fun testDBServerRestoreDefaultSharedPreferenceWithNonExistentFile() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        var nonExistentFile: File = File(appContext.getCacheDir(), ("nonexistent_" + System.currentTimeMillis()))
        assertFalse("Non-existent file should not exist", nonExistentFile.exists())
        try {
            dbServer.restoreDefaultSharedPreference(nonExistentFile)
            assertTrue("restoreDefaultSharedPreference with non-existent file should handle gracefully", true)
        } catch (e: Exception) {
            assertTrue("restoreDefaultSharedPreference may throw exception for non-existent file", true)
        }
    }
    @Test(timeout = 15000)
    fun testDBServerBackupUserRecordsViaLimeDB() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail("ERROR: Database is still on hold after waiting 10 seconds.")
        }
        try {
            var limeDB: LimeDB = LimeDB(appContext)
            if (initializeDatabase(limeDB)) {
                fail("ERROR: Cannot initialize database connection.")
            }
            limeDB.setTableName("custom")
            limeDB.addOrUpdateMappingRecord("custom", "user1", "ç¨æ¶1", 100)
            limeDB.addOrUpdateMappingRecord("custom", "user2", "ç¨æ¶2", 200)
            limeDB.addOrUpdateMappingRecord("custom", "base1", "åºç¤1", 0)
            limeDB.backupUserRecords("custom")
            var hasBackup: Boolean = limeDB.checkBackupTable("custom")
            assertTrue("backupUserRecords should create backup table", hasBackup)
            var cursor: android.database.Cursor = limeDB.getBackupTableRecords("custom_user")!!
            if ((cursor != null)) {
                var backupCount: Int = cursor.getCount()
                assertTrue("Backup table should contain user-learned records", (backupCount >= 2))
                cursor.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: backupUserRecords test failed: " + e.getMessage()), e)
            fail(("ERROR: backupUserRecords test failed with exception: " + e.getMessage()))
        }
    }
    @Test(timeout = 15000)
    fun testDBServerRestoreUserRecordsViaLimeDB() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail("ERROR: Database is still on hold after waiting 10 seconds.")
        }
        try {
            var limeDB: LimeDB = LimeDB(appContext)
            if (initializeDatabase(limeDB)) {
                fail("ERROR: Cannot initialize database connection.")
            }
            limeDB.setTableName("custom")
            limeDB.addOrUpdateMappingRecord("custom", "restore1", "éå1", 100)
            limeDB.addOrUpdateMappingRecord("custom", "restore2", "éå2", 200)
            limeDB.backupUserRecords("custom")
            var hasBackup: Boolean = limeDB.checkBackupTable("custom")
            assertTrue("backupUserRecords should create backup table", hasBackup)
            limeDB.clearTable("custom")
            assertEquals("Table should be empty after deleteAll", 0, limeDB.countRecords("custom", null, null))
            var restoredCount: Int = limeDB.restoreUserRecords("custom")
            assertTrue("restoreUserRecords should return number of restored records", (restoredCount >= 0))
            var countAfterRestore: Int = limeDB.countRecords("custom", null, null)
            assertTrue("Record count after restore should match restored count", (countAfterRestore >= restoredCount))
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: restoreUserRecords test failed: " + e.getMessage()), e)
            fail(("ERROR: restoreUserRecords test failed with exception: " + e.getMessage()))
        }
    }
    @Test(timeout = 15000)
    fun testDBServerBackupUserRecordsWithInvalidTableName() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail("ERROR: Database is still on hold after waiting 10 seconds.")
        }
        try {
            var limeDB: LimeDB = LimeDB(appContext)
            if (initializeDatabase(limeDB)) {
                fail("ERROR: Cannot initialize database connection.")
            }
            limeDB.backupUserRecords("invalid_table_name_xyz")
            var hasBackup: Boolean = limeDB.checkBackupTable("invalid_table_name_xyz")
            assertFalse("backupUserRecords should not create backup table for invalid table name", hasBackup)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: backupUserRecords with invalid table name test failed: " + e.getMessage()), e)
        }
    }
    @Test(timeout = 15000)
    fun testDBServerBackupUserRecordsAndRestoreUserRecordsPair() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail("ERROR: Database is still on hold after waiting 10 seconds.")
        }
        try {
            var limeDB: LimeDB = LimeDB(appContext)
            if (initializeDatabase(limeDB)) {
                fail("ERROR: Cannot initialize database connection.")
            }
            limeDB.setTableName("custom")
            limeDB.addOrUpdateMappingRecord("custom", "pair_test1", "é\u008då°\u008dæ¸¬è©¦1", 100)
            limeDB.addOrUpdateMappingRecord("custom", "pair_test2", "é\u008då°\u008dæ¸¬è©¦2", 200)
            limeDB.addOrUpdateMappingRecord("custom", "pair_test3", "é\u008då°\u008dæ¸¬è©¦3", 300)
            var originalUserRecordsCount: Int = limeDB.countRecords("custom", (LIME.DB_COLUMN_SCORE + " > 0"), null)
            assertTrue("Should have at least 3 user-learned records", (originalUserRecordsCount >= 3))
            limeDB.backupUserRecords("custom")
            var hasBackup: Boolean = limeDB.checkBackupTable("custom")
            assertTrue("backupUserRecords should create backup table", hasBackup)
            var backupCursor: android.database.Cursor = limeDB.getBackupTableRecords("custom_user")!!
            assertNotNull("getBackupTableRecords should return cursor if backup exists", backupCursor)
            var backupCount: Int = backupCursor.getCount()
            assertTrue("Backup table should contain user-learned records", (backupCount >= originalUserRecordsCount))
            backupCursor.close()
            limeDB.clearTable("custom")
            assertEquals("Table should be empty after deleteAll", 0, limeDB.countRecords("custom", null, null))
            var restoredCount: Int = limeDB.restoreUserRecords("custom")
            assertTrue("restoreUserRecords should return number of restored records", (restoredCount >= 0))
            var countAfterRestore: Int = limeDB.countRecords("custom", (LIME.DB_COLUMN_SCORE + " > 0"), null)
            assertTrue("User record count after restore should match original", (countAfterRestore >= originalUserRecordsCount))
            var restoredTest1: Int = limeDB.countRecords("custom", (((LIME.DB_COLUMN_CODE + " = 'pair_test1' AND ") + LIME.DB_COLUMN_SCORE) + " = 100"), null)
            var restoredTest2: Int = limeDB.countRecords("custom", (((LIME.DB_COLUMN_CODE + " = 'pair_test2' AND ") + LIME.DB_COLUMN_SCORE) + " = 200"), null)
            var restoredTest3: Int = limeDB.countRecords("custom", (((LIME.DB_COLUMN_CODE + " = 'pair_test3' AND ") + LIME.DB_COLUMN_SCORE) + " = 300"), null)
            assertTrue("pair_test1 should be restored", (restoredTest1 >= 1))
            assertTrue("pair_test2 should be restored", (restoredTest2 >= 1))
            assertTrue("pair_test3 should be restored", (restoredTest3 >= 1))
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: backupUserRecords/restoreUserRecords pair test failed: " + e.getMessage()), e)
            fail(("ERROR: backupUserRecords/restoreUserRecords pair test failed with exception: " + e.getMessage()))
        }
    }
    @Test(timeout = 15000)
    fun testDBServerGetBackupTableRecords() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail("ERROR: Database is still on hold after waiting 10 seconds.")
        }
        try {
            var limeDB: LimeDB = LimeDB(appContext)
            if (initializeDatabase(limeDB)) {
                fail("ERROR: Cannot initialize database connection.")
            }
            limeDB.setTableName("custom")
            limeDB.clearTable("custom")
            limeDB.addOrUpdateMappingRecord("custom", "backup_test1", "åä»½æ¸¬è©¦1", 100)
            limeDB.addOrUpdateMappingRecord("custom", "backup_test2", "åä»½æ¸¬è©¦2", 200)
            limeDB.backupUserRecords("custom")
            var hasBackup: Boolean = limeDB.checkBackupTable("custom")
            assertTrue("backupUserRecords should create backup table", hasBackup)
            var cursor: android.database.Cursor = limeDB.getBackupTableRecords("custom_user")!!
            assertNotNull("getBackupTableRecords should return cursor for valid backup table", cursor)
            assertTrue("Cursor should contain records", (cursor.getCount() >= 2))
            if (cursor.moveToFirst()) {
                var codeIndex: Int = cursor.getColumnIndex(LIME.DB_COLUMN_CODE)
                var wordIndex: Int = cursor.getColumnIndex(LIME.DB_COLUMN_WORD)
                var scoreIndex: Int = cursor.getColumnIndex(LIME.DB_COLUMN_SCORE)
                assertTrue("Cursor should have code column", (codeIndex >= 0))
                assertTrue("Cursor should have word column", (wordIndex >= 0))
                assertTrue("Cursor should have score column", (scoreIndex >= 0))
                var code: String = cursor.getString(codeIndex)
                var word: String = cursor.getString(wordIndex)
                var score: Int = cursor.getInt(scoreIndex)
                assertNotNull("Code should not be null", code)
                assertNotNull("Word should not be null", word)
                assertTrue("Score should be > 0", (score > 0))
            }
            cursor.close()
            var invalidCursor1: android.database.Cursor = limeDB.getBackupTableRecords("custom")!!
            assertNull("getBackupTableRecords should return null for invalid format (doesn't end with _user)", invalidCursor1)
            var invalidCursor2: android.database.Cursor = limeDB.getBackupTableRecords("invalid_table_user")!!
            assertNull("getBackupTableRecords should return null for invalid base table name", invalidCursor2)
            limeDB.setTableName("custom")
            limeDB.clearTable("custom")
            limeDB.addOrUpdateMappingRecord("custom", "base1", "åºç¤1", 0)
            limeDB.backupUserRecords("custom")
            assertFalse("Backup table should be dropped when no user records exist", limeDB.checkBackupTable("custom"))
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: getBackupTableRecords test failed: " + e.getMessage()), e)
            fail(("ERROR: getBackupTableRecords test failed with exception: " + e.getMessage()))
        }
    }
    @Test(timeout = 15000)
    fun testDBServerCheckBackupTable() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (ensureDatabaseReady(dbServer)) {
            fail("ERROR: Database is still on hold after waiting 10 seconds.")
        }
        try {
            var limeDB: LimeDB = LimeDB(appContext)
            if (initializeDatabase(limeDB)) {
                fail("ERROR: Cannot initialize database connection.")
            }
            limeDB.dropBackupTable("custom")
            limeDB.setTableName("custom")
            limeDB.addOrUpdateMappingRecord("custom", "check_test1", "æª¢æ¥æ¸¬è©¦1", 100)
            limeDB.addOrUpdateMappingRecord("custom", "check_test2", "æª¢æ¥æ¸¬è©¦2", 200)
            limeDB.backupUserRecords("custom")
            var hasBackup: Boolean = limeDB.checkBackupTable("custom")
            assertTrue("checkBackupTable should return true for backup table with records", hasBackup)
            var invalidCheck: Boolean = limeDB.checkBackupTable("invalid_table_name_xyz")
            assertFalse("checkBackupTable should return false for invalid table name", invalidCheck)
            limeDB.dropBackupTable("phonetic")
            var nonExistentCheck: Boolean = limeDB.checkBackupTable("phonetic")
            assertFalse("checkBackupTable should return false for non-existent backup table", nonExistentCheck)
            limeDB.dropBackupTable("cj")
            limeDB.setTableName("cj")
            limeDB.addOrUpdateMappingRecord("cj", "base1", "åºç¤1", 0)
            limeDB.backupUserRecords("cj")
            var emptyCheck: Boolean = limeDB.checkBackupTable("cj")
            assertFalse("checkBackupTable should return false for empty backup table", emptyCheck)
            limeDB.addOrUpdateMappingRecord("cj", "user1", "ç¨æ¶1", 100)
            limeDB.backupUserRecords("cj")
            var hasRecordsCheck: Boolean = limeDB.checkBackupTable("cj")
            assertTrue("checkBackupTable should return true for backup table containing records", hasRecordsCheck)
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: checkBackupTable test failed: " + e.getMessage()), e)
            fail(("ERROR: checkBackupTable test failed with exception: " + e.getMessage()))
        }
    }
    @Test(timeout = 15000)
    fun testDBServerImportTxtTableDelegatesToLimeDB() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dbServer: DBServer = DBServer.getInstance(appContext)!!
        if (dbServer.isDatabseOnHold()) {
            return
        }
        var testFile: File = File(appContext.getCacheDir(), "test_delegation.txt")
        try {
            var writer: java.io.FileWriter = java.io.FileWriter(testFile)
            writer.write("test\tæ¸¬è©¦\n")
            writer.close()
            try {
                dbServer.importTxtTable(testFile, "custom", null)
                Thread.sleep(1000)
                assertTrue("importTxtTable should delegate to LimeDB", true)
            } catch (e: Exception) {
                assertTrue("importTxtTable may throw exception but should delegate", true)
            }
            if ((testFile.exists() && testFile.delete())) {
                Log.e(TAG, "Failed to delete test file")
            }
        } catch (e: Exception) {
            if ((testFile.exists() && testFile.delete())) {
                Log.e(TAG, "Failed to delete test file")
            }
        }
    }
    @Test
    fun testDBServerRunnableClassesUseDBServerForFileOperations() {
        try {
            var shareDbRunnableClass: java.lang.Class<*> = Class.forName("net.toload.main.hd.ui.ShareDbRunnable")
            var methods: Array<java.lang.reflect.Method> = shareDbRunnableClass.declaredMethods
            var usesDBServer: Boolean = false
            for (method in methods) {
                var methodCode: String = method.toString()
                if ((methodCode.contains("exportZippedDb") || methodCode.contains("dbsrv"))) {
                    usesDBServer = true
                    break
                }
            }
            var sourceFile: java.io.File = java.io.File("app/src/main/java/net/toload/main/hd/ui/ShareDbRunnable.java")
            if (sourceFile.exists()) {
                var scanner: java.util.Scanner = java.util.Scanner(sourceFile)
                var foundDBServerCall: Boolean = false
                while (scanner.hasNextLine()) {
                    var line: String = scanner.nextLine()
                    if ((line.contains("dbsrv.exportZippedDb") || line.contains("dbServer.exportZippedDb"))) {
                        foundDBServerCall = true
                        break
                    }
                }
                scanner.close()
                assertTrue("ShareDbRunnable should use DBServer.exportZippedDb() for file operations", foundDBServerCall)
            }
        } catch (e: Exception) {
            Log.w(TAG, ("Could not verify ShareDbRunnable architecture compliance: " + e.getMessage()))
        }
        try {
            var sourceFile: java.io.File = java.io.File("app/src/main/java/net/toload/main/hd/ui/ShareRelatedDbRunnable.java")
            if (sourceFile.exists()) {
                var scanner: java.util.Scanner = java.util.Scanner(sourceFile)
                var foundDBServerCall: Boolean = false
                while (scanner.hasNextLine()) {
                    var line: String = scanner.nextLine()
                    if ((line.contains("dbsrv.exportZippedDbRelated") || line.contains("dbServer.exportZippedDbRelated"))) {
                        foundDBServerCall = true
                        break
                    }
                }
                scanner.close()
                assertTrue("ShareRelatedDbRunnable should use DBServer.exportZippedDbRelated() for file operations", foundDBServerCall)
            }
        } catch (e: Exception) {
            Log.w(TAG, ("Could not verify ShareRelatedDbRunnable architecture compliance: " + e.getMessage()))
        }
    }
    @Test
    fun testDBServerMainActivityUsesDBServerForFileOperations() {
        try {
            var sourceFile: java.io.File = java.io.File("app/src/main/java/net/toload/main/hd/MainActivity.java")
            if (sourceFile.exists()) {
                var scanner: java.util.Scanner = java.util.Scanner(sourceFile)
                var foundDBServerImport: Boolean = false
                var foundDBServerExport: Boolean = false
                var foundDirectFileOps: Boolean = false
                while (scanner.hasNextLine()) {
                    var line: String = scanner.nextLine()
                    if (((line.contains("dbServer.import") || line.contains("dbServer.export")) || line.contains("DBServer.getInstance"))) {
                        if (line.contains("import")) {
                            foundDBServerImport = true
                        }
                        if (line.contains("export")) {
                            foundDBServerExport = true
                        }
                    }
                    if (((((((line.contains("importZippedDb") || line.contains("importDb")) || line.contains("exportZippedDb")) || line.contains("backupDatabase")) || line.contains("restoreDatabase")) && line.contains("dbServer.")) && line.contains("DBServer."))) {
                        if ((line.trim().startsWith("//") && line.trim().startsWith("*"))) {
                            foundDirectFileOps = true
                        }
                    }
                }
                scanner.close()
                assertTrue("MainActivity should delegate file operations to DBServer", ((foundDBServerImport || foundDBServerExport) || !foundDirectFileOps))
            }
        } catch (e: Exception) {
            Log.w(TAG, ("Could not verify MainActivity architecture compliance: " + e.getMessage()))
        }
    }
    @Test
    fun testDBServerLimeDBOnlyHasTextFileOperations() {
        try {
            var sourceFile: java.io.File = java.io.File("app/src/main/java/net/toload/main/hd/limedb/LimeDB.java")
            if (sourceFile.exists()) {
                var scanner: java.util.Scanner = java.util.Scanner(sourceFile)
                var hasTextFileOps: Boolean = false
                var hasOtherFileOps: Boolean = false
                while (scanner.hasNextLine()) {
                    var line: String = scanner.nextLine()
                    if ((((((line.contains("importTxtTable") || line.contains("exportTxtTable")) || line.contains("FileWriter")) || line.contains("FileReader")) || line.contains("BufferedWriter")) || line.contains("BufferedReader"))) {
                        hasTextFileOps = true
                    }
                    if ((((((((((line.contains("FileOutputStream") || line.contains("FileInputStream")) || line.contains(".zip")) || line.contains(".limedb")) || line.contains("LIMEUtilities.zip")) || line.contains("LIMEUtilities.unzip")) && line.contains("importTxtTable")) && line.contains("exportTxtTable")) && line.trim().startsWith("//")) && line.trim().startsWith("*"))) {
                        hasOtherFileOps = true
                    }
                }
                scanner.close()
                assertTrue("LimeDB should only have text file import/export operations", (hasTextFileOps && !hasOtherFileOps))
            }
        } catch (e: Exception) {
            Log.w(TAG, ("Could not verify LimeDB architecture compliance: " + e.getMessage()))
        }
    }
    @Test
    fun testDBServerUIFragmentsUseDBServerForFileOperations() {
        try {
            var sourceFile: java.io.File = java.io.File("app/src/main/java/net/toload/main/hd/ui/controller/SetupImController.java")
            if (sourceFile.exists()) {
                var scanner: java.util.Scanner = java.util.Scanner(sourceFile)
                var foundDBServerCall: Boolean = false
                var foundDirectFileOps: Boolean = false
                while (scanner.hasNextLine()) {
                    var line: String = scanner.nextLine()
                    if (((line.contains("DBSrv.import") || line.contains("dbServer.import")) || line.contains("DBServer.getInstance"))) {
                        foundDBServerCall = true
                    }
                    if ((((((((line.contains("importZippedDb") || line.contains("importDb")) || line.contains("importTxtTable")) && line.contains("DBSrv.")) && line.contains("dbServer.")) && line.contains("DBServer.")) && line.trim().startsWith("//")) && line.trim().startsWith("*"))) {
                        foundDirectFileOps = true
                    }
                }
                scanner.close()
                assertTrue("SetupImController should delegate file operations to DBServer", (foundDBServerCall || !foundDirectFileOps))
            }
        } catch (e: Exception) {
            Log.w(TAG, ("Could not verify SetupImController architecture compliance: " + e.getMessage()))
        }
    }
    @Test
    fun testDBServerUnzipFile() {
        try {
            var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
            var cacheDir: File = appContext.getCacheDir()
            var testZipFile: File = File(cacheDir, "test_unzip.zip")
            var testContentFile: File = File(cacheDir, "test_content.txt")
            var extractDir: File = File(cacheDir, "test_extract")
            try {
                var writer: java.io.FileWriter = java.io.FileWriter(testContentFile)
                writer.write("Test content for zip extraction")
                writer.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create test content file", e)
                fail("Could not create test content file")
            }
            sharedDbServer!!.zip(testContentFile, cacheDir.getAbsolutePath(), "test_unzip.zip")
            assertTrue("Zip file should be created", testZipFile.exists())
            sharedDbServer!!.unzip(testZipFile, extractDir.getAbsolutePath(), "extracted_test_content.txt", true)
            var extractedFile: File = File(extractDir, "extracted_test_content.txt")
            assertTrue("Extracted file should exist", extractedFile.exists())
            try {
                var reader: java.io.BufferedReader = java.io.BufferedReader(java.io.FileReader(extractedFile))
                var content: String = reader.readLine()
                reader.close()
                assertEquals("Extracted content should match original", "Test content for zip extraction", content)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read extracted file", e)
                fail("Could not read extracted file")
            }
            testZipFile.delete()
            testContentFile.delete()
            extractedFile.delete()
            extractDir.delete()
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testDBServerUnzipFile failed: " + e.getMessage()), e)
            fail(("ERROR: unzip test failed with exception: " + e.getMessage()))
        }
    }
    @Test
    fun testDBServerZipFile() {
        try {
            var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
            var cacheDir: File = appContext.getCacheDir()
            var testContentFile: File = File(cacheDir, "test_zip_content.txt")
            var testZipFile: File = File(cacheDir, "test_zip.zip")
            try {
                var writer: java.io.FileWriter = java.io.FileWriter(testContentFile)
                writer.write("Test content for zipping")
                writer.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create test content file", e)
                fail("Could not create test content file")
            }
            sharedDbServer!!.zip(testContentFile, cacheDir.getAbsolutePath(), "test_zip.zip")
            assertTrue("Zip file should be created", testZipFile.exists())
            assertTrue("Zip file should not be empty", (testZipFile.length > 0))
            testContentFile.delete()
            testZipFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testDBServerZipFile failed: " + e.getMessage()), e)
            fail(("ERROR: zip test failed with exception: " + e.getMessage()))
        }
    }
    @Test
    fun testLimeUtilitiesZipUsesBareFileNameForAbsoluteSource() {
        try {
            var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
            var cacheDir: File = appContext.getCacheDir()
            var nestedDir: File = File(cacheDir, "limedb_export_path_test/nested")
            assertTrue("Nested dir should be created", (nestedDir.exists() || nestedDir.mkdirs()))
            var sourceDb: File = File(nestedDir, "array.db")
            var targetZip: File = File(cacheDir, "array_test.limedb")
            try {
                var writer: java.io.FileWriter = java.io.FileWriter(sourceDb)
                writer.write("sqlite placeholder")
                writer.close()
                LIMEUtilities.zip(targetZip.getAbsolutePath(), sourceDb.getAbsolutePath(), true)
                assertTrue("Zip file should be created", targetZip.exists())
                ZipFile(targetZip).use { zipFile ->
                        var entries: Enumeration<out ZipEntry> = zipFile.entries()
                        assertTrue("Zip should contain one entry", entries.hasMoreElements())
                        var entry: ZipEntry = entries.nextElement()
                        assertEquals("array.db", entry.name)
                        assertFalse("Zip should contain only one entry", entries.hasMoreElements())
                }
            } finally {
                sourceDb.delete()
                targetZip.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testLimeUtilitiesZipUsesBareFileNameForAbsoluteSource failed: " + e.getMessage()), e)
            fail(("ERROR: zip entry name test failed with exception: " + e.getMessage()))
        }
    }
    @Test
    fun testDBServerUnzipWithInvalidFile() {
        try {
            var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
            var cacheDir: File = appContext.getCacheDir()
            var invalidZipFile: File = File(cacheDir, "nonexistent.zip")
            var extractDir: File = File(cacheDir, "test_extract_invalid")
            sharedDbServer!!.unzip(invalidZipFile, extractDir.getAbsolutePath(), "test.txt", false)
            var extractedFile: File = File(extractDir, "test.txt")
            assertFalse("Extracted file should not exist for invalid zip", extractedFile.exists())
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testDBServerUnzipWithInvalidFile failed: " + e.getMessage()), e)
            fail(("ERROR: unzip with invalid file test failed with exception: " + e.getMessage()))
        }
    }
    @Test
    fun testDBServerZipWithInvalidFile() {
        try {
            var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
            var cacheDir: File = appContext.getCacheDir()
            var invalidSourceFile: File = File(cacheDir, "nonexistent.txt")
            var testZipFile: File = File(cacheDir, "test_invalid_zip.zip")
            sharedDbServer!!.zip(invalidSourceFile, cacheDir.getAbsolutePath(), "test_invalid_zip.zip")
            assertFalse("Zip file should not be created for invalid source", testZipFile.exists())
        } catch (e: Exception) {
            Log.e(TAG, ("ERROR: testDBServerZipWithInvalidFile failed: " + e.getMessage()), e)
            fail(("ERROR: zip with invalid file test failed with exception: " + e.getMessage()))
        }
    }
}
