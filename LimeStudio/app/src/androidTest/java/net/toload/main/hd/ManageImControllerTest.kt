package net.toload.main.hd

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.toload.main.hd.SearchServer
import net.toload.main.hd.data.Record
import net.toload.main.hd.ui.controller.ManageImController
import net.toload.main.hd.ui.view.ManageImView
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
open class ManageImControllerTest {
    private open class StubManageImView : ManageImView {
        private lateinit var errorRef: AtomicReference<String>
        constructor(errorRef: AtomicReference<String>) {
            this.errorRef = errorRef
        }
        override fun displayRecords(records: List<Record>?) {

        }
        override fun updateRecordCount(count: Int) {

        }
        override fun refreshRecordList() {

        }
        override fun showDeleteConfirmDialog(id: Long) {

        }
        override fun showEditRecordDialog(record: Record?) {

        }
        override fun showAddRecordDialog() {

        }
        override fun onError(message: String?) {
            errorRef.set(message)
        }
    }
    @Test
    fun loadRecordsAsync_invalidTable_reportsError() {
        var searchServer: SearchServer = SearchServer(ApplicationProvider.getApplicationContext())
        var controller: ManageImController = ManageImController(searchServer)
        var errorRef: AtomicReference<String> = AtomicReference()
        controller.setManageImView(StubManageImView(errorRef))
        controller.loadRecordsAsync("nonexistent_table", "", false, 0, 10)
        assertNotNull("Invalid table should report error", errorRef.get())
    }
    @Test
    fun updateIMMetadata_persistsNameAndVersion() {
        var context: android.content.Context = ApplicationProvider.getApplicationContext()
        var dbServer: DBServer = DBServer.getInstance(context)!!
        var searchServer: SearchServer = SearchServer(context)
        var controller: ManageImController = ManageImController(searchServer)
        var suffix: String = java.lang.String.valueOf(System.currentTimeMillis())
        var editedName: String = ("Edited Custom " + suffix)
        var editedVersion: String = ("Version " + suffix)
        assertTrue(controller.updateIMMetadata("custom", editedName, editedVersion))
        assertEquals(editedName, dbServer.getImConfig("custom", "name"))
        assertEquals(editedVersion, dbServer.getImConfig("custom", "version"))
    }
    @Test
    fun updateIMMetadata_rejectsEmptyName() {
        var searchServer: SearchServer = SearchServer(ApplicationProvider.getApplicationContext())
        var controller: ManageImController = ManageImController(searchServer)
        assertFalse(controller.updateIMMetadata("custom", "   ", "Version 2026.05"))
    }
    @Test
    fun updateIMMetadataField_persistsIndependentVersion() {
        var context: android.content.Context = ApplicationProvider.getApplicationContext()
        var dbServer: DBServer = DBServer.getInstance(context)!!
        var searchServer: SearchServer = SearchServer(context)
        var controller: ManageImController = ManageImController(searchServer)
        var suffix: String = java.lang.String.valueOf(System.currentTimeMillis())
        var editedVersion: String = ("Independent Version " + suffix)
        assertTrue(controller.updateIMMetadataField("custom", "version", editedVersion))
        assertEquals(editedVersion, dbServer.getImConfig("custom", "version"))
    }
    @Test
    fun updateIMMetadataField_allowsLimeEndkey() {
        var context: android.content.Context = ApplicationProvider.getApplicationContext()
        var dbServer: DBServer = DBServer.getInstance(context)!!
        var searchServer: SearchServer = SearchServer(context)
        var controller: ManageImController = ManageImController(searchServer)
        assertTrue(controller.updateIMMetadataField("custom", "limeendkey", " ;/ "))
        assertEquals(";/", dbServer.getImConfig("custom", "limeendkey"))
        assertTrue(controller.updateIMMetadataField("custom", "limeendkey", " "))
        assertEquals("", dbServer.getImConfig("custom", "limeendkey"))
    }
}
