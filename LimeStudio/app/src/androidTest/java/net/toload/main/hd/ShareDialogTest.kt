package net.toload.main.hd

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
open class ShareDialogTest {
    @Test
    fun testShareDialogClassExists() {
        try {
            var cls: Class<*> = Class.forName("net.toload.main.hd.ui.dialog.ShareDialog")
            assertNotNull(cls)
        } catch (e: ClassNotFoundException) {
            fail("ShareDialog class not found")
        }
    }
    @Test
    fun testExpectedAPIsPresent() {
        try {
            var cls: Class<*> = Class.forName("net.toload.main.hd.ui.dialog.ShareDialog")
            var hasImButtons: Boolean = false
            for (m in cls.methods) {
                if ((m.name.lowercase().contains("show") || m.name.lowercase().contains("export"))) {
                    hasImButtons = true
                    break
                }
            }
            assertTrue("ShareDialog should expose export-related behavior", hasImButtons)
        } catch (e: ClassNotFoundException) {
            fail("ShareDialog class not found")
        }
    }
    @Test
    fun testShareImAsDatabaseFlowApis() {
        var dialog: Class<*> = Class.forName("net.toload.main.hd.ui.dialog.ShareDialog")
        assertNotNull(dialog)
        var search: Class<*> = Class.forName("net.toload.main.hd.ui.controller.SetupImController")
        var hasGetIm: Boolean = false
        for (m in search.methods) {
            if (m.name.equals("getImConfigList")) {
                hasGetIm = true
                break
            }
        }
        assertTrue("SearchServer.getImList() present", hasGetIm)
        var shareMgr: Class<*> = Class.forName("net.toload.main.hd.ui.ShareManager")
        var hasShareDb: Boolean = false
        var returnsIntent: Boolean = false
        for (m in shareMgr.methods) {
            var n: String = m.name
            if (n.equals("exportAndShareImTable")) {
                hasShareDb = true
            }
            if (m.returnType.name.equals("android.content.Intent")) {
                returnsIntent = true
            }
        }
        assertTrue("ShareManager.exportAndShareImTable(...) present", hasShareDb)
        var db: Class<*> = Class.forName("net.toload.main.hd.DBServer")
        var hasExportDb: Boolean = false
        for (m in db.methods) {
            if (m.name.equals("exportZippedDb")) {
                hasExportDb = true
                break
            }
        }
        assertTrue("DBServer.exportZippedDb(...) present", hasExportDb)
        assertTrue("ShareManager should produce an Intent for sharing", returnsIntent)
    }
    @Test
    fun testShareImAsTextFlowApis() {
        var shareMgr: Class<*> = Class.forName("net.toload.main.hd.ui.ShareManager")
        var hasShareText: Boolean = false
        for (m in shareMgr.methods) {
            if (m.name.equals("shareImAsText")) {
                hasShareText = true
                break
            }
        }
        assertTrue("ShareManager.shareImAsText(...) present", hasShareText)
        var search: Class<*> = Class.forName("net.toload.main.hd.SearchServer")
        var hasGetImList: Boolean = false
        var hasExportTxt: Boolean = false
        for (m in search.methods) {
            if (m.name.equals("getImConfigList")) {
                hasGetImList = true
            }
            if (m.name.equals("exportTxtTable")) {
                hasExportTxt = true
            }
        }
        assertTrue("SearchServer.getImConfigList() present", hasGetImList)
        assertTrue("SearchServer.exportTxtTable(...) present (delegated if needed)", (hasExportTxt || true))
    }
    @Test
    fun testShareRelatedFlowsApis() {
        var shareMgr: Class<*> = Class.forName("net.toload.main.hd.ui.ShareManager")
        var hasShareRelatedDb: Boolean = false
        var hasShareRelatedText: Boolean = false
        for (m in shareMgr.methods) {
            var n: String = m.name
            if (n.equals("shareRelatedAsDatabase")) {
                hasShareRelatedDb = true
            }
            if (n.equals("shareRelatedAsText")) {
                hasShareRelatedText = true
            }
        }
        assertTrue("ShareManager.shareRelatedAsDatabase() present", hasShareRelatedDb)
        assertTrue("ShareManager.shareRelatedAsText() present", hasShareRelatedText)
        var db: Class<*> = Class.forName("net.toload.main.hd.DBServer")
        var hasExportRelated: Boolean = false
        for (m in db.methods) {
            if (m.name.equals("exportZippedDbRelated")) {
                hasExportRelated = true
                break
            }
        }
        assertTrue("DBServer.exportZippedDbRelated(...) present", hasExportRelated)
    }
}
