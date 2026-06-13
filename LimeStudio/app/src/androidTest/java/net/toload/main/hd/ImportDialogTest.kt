package net.toload.main.hd

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
open class ImportDialogTest {
    @Test
    fun testImportDialogClassExists() {
        try {
            var cls: Class<*> = Class.forName("net.toload.main.hd.ui.dialog.ImportDialog")
            assertNotNull(cls)
        } catch (e: ClassNotFoundException) {
            fail("ImportDialog class not found")
        }
    }
    @Test
    fun testHasNewInstanceForFileFactory() {
        try {
            var cls: Class<*> = Class.forName("net.toload.main.hd.ui.dialog.ImportDialog")
            var hasFactory: Boolean = false
            for (m in cls.methods) {
                if ((m.name.contains("newInstance") && (m.getParameterCount() > 0))) {
                    hasFactory = true
                    break
                }
            }
            assertTrue("ImportDialog should have a factory method (newInstance...)", hasFactory)
        } catch (e: ClassNotFoundException) {
            fail("ImportDialog class not found")
        }
    }
    @Test
    fun testDelegationPatternIndicators() {
        try {
            var cls: Class<*> = Class.forName("net.toload.main.hd.ui.dialog.ImportDialog")
            var mentionsListener: Boolean = false
            for (inner in cls.getDeclaredClasses()) {
                if (inner.simpleName.lowercase().contains("listener")) {
                    mentionsListener = true
                    break
                }
            }
            assertTrue("ImportDialog should use a listener/delegation pattern", mentionsListener)
        } catch (e: ClassNotFoundException) {
            fail("ImportDialog class not found")
        }
    }
    @Test
    fun testSetupIMControllerProvidesListAndCounts() {
        var ss: Class<*> = Class.forName("net.toload.main.hd.ui.controller.SetupImController")
        var hasGetIm: Boolean = false
        var hasCountMapping: Boolean = false
        for (m in ss.methods) {
            if (m.name.equals("getImConfigList")) {
                hasGetIm = true
            }
            if (m.name.equals("countRecords")) {
                hasCountMapping = true
            }
        }
        assertTrue("SearchServer.getImList() present", hasGetIm)
        assertTrue("SearchServer.countRecords(...) present", hasCountMapping)
    }
    @Test
    fun testControllerHandlesSelectionAndImport() {
        var ctrl: Class<*> = Class.forName("net.toload.main.hd.ui.controller.SetupImController")
        var hasSelectionCallback: Boolean = false
        var hasImportText: Boolean = false
        var hasImportZip: Boolean = false
        for (m in ctrl.methods) {
            var n: String = m.name.lowercase()
            if ((n.contains("onimport") || n.contains("importdialog"))) {
                hasSelectionCallback = true
            }
            if (m.name.equals("importTxtTable")) {
                hasImportText = true
            }
            if (m.name.equals("importZippedDb")) {
                hasImportZip = true
            }
        }
        assertTrue("SetupImController should have an import dialog selection handler", hasSelectionCallback)
        assertTrue("SetupImController.importTxtTable present", hasImportText)
        assertTrue("SetupImController.importZippedDb present", hasImportZip)
    }
    @Test
    fun testDbServerProvidesImportOperations() {
        var db: Class<*> = Class.forName("net.toload.main.hd.DBServer")
        var hasImportTxt: Boolean = false
        var hasImportZip: Boolean = false
        for (m in db.methods) {
            if (m.name.equals("importTxtTable")) {
                hasImportTxt = true
            }
            if (m.name.equals("importZippedDb")) {
                hasImportZip = true
            }
        }
        assertTrue("DBServer.importTxtTable present", hasImportTxt)
        assertTrue("DBServer.importZippedDb present", hasImportZip)
    }
}
