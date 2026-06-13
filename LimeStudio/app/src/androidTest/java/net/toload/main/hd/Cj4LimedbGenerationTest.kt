@file:Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST", "TYPE_INTERSECTION_AS_REIFIED")

package net.toload.main.hd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.toload.main.hd.global.LIME
import net.toload.main.hd.global.LIMEProgressListener
import net.toload.main.hd.ui.controller.ManageImController
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
open class Cj4LimedbGenerationTest {
    companion object {
        private val INPUT_FILENAME: String = "cj4_haha_20260523_162540.lime"
        private val OUTPUT_FILENAME: String = "cj4.limedb"
        private val EXPECTED_RECORD_COUNT: Int = 33021
    }
    @Test
    fun generateCj4LimedbFromPreparedLimeFile() {
        var context: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var externalDir: File = context.getExternalFilesDir(null)!!
        assertNotNull("External files directory should be available", externalDir)
        var inputFile: File = File(externalDir, INPUT_FILENAME)
        assumeTrue((((("Push " + INPUT_FILENAME) + " to ") + externalDir.getAbsolutePath()) + " before running this local generation test"), inputFile.exists())
        var searchServer: SearchServer = SearchServer(context)
        var dbServer: DBServer = DBServer.getInstance(context)!!
        var manageController: ManageImController = ManageImController(searchServer)
        assertTrue("cj4 should be a valid import/export table", searchServer.isValidTableName(LIME.DB_TABLE_CJ4))
        var latch: CountDownLatch = CountDownLatch(1)
        var error: AtomicReference<String> = AtomicReference("")
        dbServer.importTxtTable(inputFile.getAbsolutePath(), LIME.DB_TABLE_CJ4, object : LIMEProgressListener() {
    override fun onProgress(percentageDone: Long, estimatedRemainingTime: Long, status: String?) {

    }
    override fun onStatusUpdate(status: String?) {

    }
    override fun onError(code: Int, source: String?) {
        error.set((if ((source != null)) source else ("Import failed with code " + code)))
        latch.countDown()
    }
    override fun onPostExecute(success: Boolean, status: String?, code: Int) {
        if (!success) {
            error.set((if ((status != null)) status else ("Import failed with code " + code)))
        }
        latch.countDown()
    }
})
        assertTrue("cj4 import should complete within five minutes", latch.await(5, TimeUnit.MINUTES))
        assertEquals("cj4 import should not report an error", "", error.get())
        var recordCount: Int = manageController.countRecords(LIME.DB_TABLE_CJ4)
        assertEquals("cj4 imported record count", EXPECTED_RECORD_COUNT, recordCount)
        assertEquals("cj4 should reuse the existing Cangjie keyboard", LIME.DATABASE_CLOUD_IM_CJ4_KEYBOARD, getImKeyboardCode(context, LIME.DB_TABLE_CJ4))
        var exportFile: File = File(externalDir, OUTPUT_FILENAME)
        var result: File = dbServer.exportZippedDb(LIME.DB_TABLE_CJ4, exportFile, null)!!
        assertNotNull("cj4 limedb export should return a file", result)
        assertTrue("cj4 limedb export should exist", exportFile.exists())
        assertTrue("cj4 limedb export should not be empty", (exportFile.length > 1000))
        dbServer.importZippedDb(exportFile, LIME.DB_TABLE_CJ4)
        var roundTripCount: Int = manageController.countRecords(LIME.DB_TABLE_CJ4)
        assertEquals("cj4 limedb round-trip record count", EXPECTED_RECORD_COUNT, roundTripCount)
        assertEquals("cj4 limedb should preserve the Cangjie keyboard assignment", LIME.DATABASE_CLOUD_IM_CJ4_KEYBOARD, getImKeyboardCode(context, LIME.DB_TABLE_CJ4))
    }
    private fun getImKeyboardCode(context: Context, tableName: String): String {
        var dbFile: File = context.getDatabasePath(LIME.DATABASE_NAME)
        SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT keyboard FROM im WHERE code=? AND title='keyboard' LIMIT 1", arrayOf(tableName)).use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getString(0)
                    }
            }
        }
        return ""
    }
}
