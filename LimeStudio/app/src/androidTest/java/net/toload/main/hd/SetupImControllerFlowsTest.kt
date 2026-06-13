package net.toload.main.hd

import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
open class SetupImControllerFlowsTest {
    @Test
    fun textImportFlow_ApisExist() {
        var controller: Class<*> = Class.forName("net.toload.main.hd.ui.controller.SetupImController")
        var searchServer: Class<*> = Class.forName("net.toload.main.hd.SearchServer")
        var dbServer: Class<*> = Class.forName("net.toload.main.hd.DBServer")
        var hasImportText: Boolean = false
        for (m in controller.methods) {
            if (m.name.equals("importTxtTable")) {
                hasImportText = true
                break
            }
        }
        assertTrue("SetupImController.importTxtTable(...) should exist", hasImportText)
        var hasValidate: Boolean = false
        var hasResetCache: Boolean = false
        var hasBackupUsers: Boolean = false
        var hasRestoreUsers: Boolean = false
        var hasCheckBackupTable: Boolean = false
        for (m in searchServer.methods) {
            if (m.name.equals("isValidTableName")) {
                hasValidate = true
            }
            if (m.name.equals("resetCache")) {
                hasResetCache = true
            }
            if (m.name.equals("backupUserRecords")) {
                hasBackupUsers = true
            }
            if (m.name.equals("restoreUserRecords")) {
                hasRestoreUsers = true
            }
            if (m.name.equals("checkBackupTable")) {
                hasCheckBackupTable = true
            }
        }
        assertTrue("SearchServer.isValidTableName(...) present", hasValidate)
        assertTrue("SearchServer.resetCache() present", hasResetCache)
        assertTrue("SearchServer.backupUserRecords(...) present", hasBackupUsers)
        assertTrue("SearchServer.restoreUserRecords(...) present", hasRestoreUsers)
        assertTrue("SearchServer.checkBackupTable() present", hasCheckBackupTable)
        var hasImportTxt: Boolean = false
        for (m in dbServer.methods) {
            if (m.name.equals("importTxtTable")) {
                hasImportTxt = true
                break
            }
        }
        assertTrue("DBServer.importTxtTable(...) present", hasImportTxt)
    }
    @Test
    fun zippedDbImportFlow_ApisExist() {
        var controller: Class<*> = Class.forName("net.toload.main.hd.ui.controller.SetupImController")
        var searchServer: Class<*> = Class.forName("net.toload.main.hd.SearchServer")
        var dbServer: Class<*> = Class.forName("net.toload.main.hd.DBServer")
        var hasImportZip: Boolean = false
        for (m in controller.methods) {
            if (m.name.equals("importZippedDb")) {
                hasImportZip = true
                break
            }
        }
        assertTrue("SetupImController.importZippedDb(...) should exist", hasImportZip)
        var hasValidate: Boolean = false
        var hasResetCache: Boolean = false
        for (m in searchServer.methods) {
            if (m.name.equals("isValidTableName")) {
                hasValidate = true
            }
            if (m.name.equals("resetCache")) {
                hasResetCache = true
            }
        }
        assertTrue("SearchServer.isValidTableName(...) present", hasValidate)
        assertTrue("SearchServer.resetCache() present", hasResetCache)
        var hasImportZippedDb: Boolean = false
        var hasImportZippedDbRelated: Boolean = false
        for (m in dbServer.methods) {
            if (m.name.equals("importZippedDb")) {
                hasImportZippedDb = true
            }
            if (m.name.equals("importZippedDbRelated")) {
                hasImportZippedDbRelated = true
            }
        }
        assertTrue("DBServer.importZippedDb(...) present", hasImportZippedDb)
        assertTrue("DBServer.importZippedDbRelated(...) present", hasImportZippedDbRelated)
    }
    @Test
    fun downloadAndImportFlow_ApisExist() {
        var controller: Class<*> = Class.forName("net.toload.main.hd.ui.controller.SetupImController")
        var utilities: Class<*> = Class.forName("net.toload.main.hd.global.LIMEUtilities")
        var searchServer: Class<*> = Class.forName("net.toload.main.hd.SearchServer")
        var hasDownloadAndImport: Boolean = false
        for (m in controller.methods) {
            if (m.name.equals("downloadAndImportZippedDb")) {
                hasDownloadAndImport = true
                break
            }
        }
        assertTrue("downloadAndImportZippedDb(...) present", hasDownloadAndImport)
        var hasDownloadRemoteFile: Boolean = false
        for (m in utilities.methods) {
            if (m.name.equals("downloadRemoteFile")) {
                hasDownloadRemoteFile = true
                break
            }
        }
        assertTrue("LIMEUtilities.downloadRemoteFile(...) present", hasDownloadRemoteFile)
        var hasResetCache: Boolean = false
        for (m in searchServer.methods) {
            if (m.name.equals("resetCache")) {
                hasResetCache = true
                break
            }
        }
        assertTrue("SearchServer.resetCache() present", hasResetCache)
        var hasHandler: Boolean = false
        for (f in controller.declaredFields) {
            if ((f.getType().name.equals("android.os.Handler") || f.name.lowercase().contains("handler"))) {
                hasHandler = true
                break
            }
        }
        if (!hasHandler) {
            lateinit var base: Class<*>
            try {
                base = Class.forName("net.toload.main.hd.ui.controller.BaseController")
                for (f in base.declaredFields) {
                    if ((f.getType().name.equals("android.os.Handler") || f.name.lowercase().contains("handler"))) {
                        hasHandler = true
                        break
                    }
                }
            } catch (ignore: ClassNotFoundException) {

            }
        }
        assertTrue("Controller/BaseController should have a main thread Handler", hasHandler)
    }
    @Test
    fun backupRestoreExportFlows_ApisExist() {
        var controller: Class<*> = Class.forName("net.toload.main.hd.ui.controller.SetupImController")
        var dbServer: Class<*> = Class.forName("net.toload.main.hd.DBServer")
        var utilities: Class<*> = Class.forName("net.toload.main.hd.global.LIMEUtilities")
        var hasBackup: Boolean = false
        var hasRestore: Boolean = false
        var hasExport: Boolean = false
        var hasExportRelated: Boolean = false
        for (m in controller.methods) {
            var n: String = m.name
            if (n.equals("performBackup")) {
                hasBackup = true
            }
            if (n.equals("performRestore")) {
                hasRestore = true
            }
            if (n.equals("exportZippedDb")) {
                hasExport = true
            }
            if (n.equals("exportZippedDbRelated")) {
                hasExportRelated = true
            }
        }
        assertTrue("performBackup(Uri) present", hasBackup)
        assertTrue("performRestore(Uri) present", hasRestore)
        assertTrue("exportZippedDb(...) present", hasExport)
        assertTrue("exportZippedDbRelated(...) present", hasExportRelated)
        var hasDbBackup: Boolean = false
        var hasDbRestore: Boolean = false
        var hasDbExport: Boolean = false
        var hasDbExportRelated: Boolean = false
        for (m in dbServer.methods) {
            var n: String = m.name
            if (n.equals("backupDatabase")) {
                hasDbBackup = true
            }
            if (n.equals("restoreDatabase")) {
                hasDbRestore = true
            }
            if (n.equals("exportZippedDb")) {
                hasDbExport = true
            }
            if (n.equals("exportZippedDbRelated")) {
                hasDbExportRelated = true
            }
        }
        assertTrue("DBServer.backupDatabase(...) present", hasDbBackup)
        assertTrue("DBServer.restoreDatabase(...) present", hasDbRestore)
        assertTrue("DBServer.exportZippedDb(...) present", hasDbExport)
        assertTrue("DBServer.exportZippedDbRelated(...) present", hasDbExportRelated)
        var hasZip: Boolean = false
        var hasUnzip: Boolean = false
        for (m in utilities.methods) {
            var n: String = m.name
            if (n.equals("zip")) {
                hasZip = true
            }
            if (n.equals("unzip")) {
                hasUnzip = true
            }
        }
        assertTrue("LIMEUtilities.zip(...) present", hasZip)
        assertTrue("LIMEUtilities.unzip(...) present", hasUnzip)
    }
}
