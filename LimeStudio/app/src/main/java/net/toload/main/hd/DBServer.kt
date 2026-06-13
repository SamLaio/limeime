/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
 *  **
 *  **    Project Url: https://github.com/SamLaio/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

package net.toload.main.hd

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.RemoteException
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InvalidClassException
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass
import java.io.OutputStream
import java.util.ArrayList
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import net.lingala.zip4j.model.FileHeader
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.global.LIME
import net.toload.main.hd.global.LIMEPreferenceManager
import net.toload.main.hd.global.LIMEProgressListener
import net.toload.main.hd.global.LIMEUtilities
import net.toload.main.hd.global.PreferenceBackupAdapter
import net.toload.main.hd.limedb.LimeDB
import net.toload.main.hd.ui.LIMESettings
import org.json.JSONException

// Jeremy '12,5,1 renamed from DBServer and change from service to ordinary class.
class DBServer internal constructor(context: Context) {
    private val appContext: Context = context.applicationContext

    @JvmField
    protected var ctx: Context? = null

    @JvmField
    protected var datasource: LimeDB? = null

    @JvmField
    protected var mLIMEPref: LIMEPreferenceManager? = null
    private var loadingTablename = ""

    init {
        if (ctx == null) {
            ctx = appContext
            mLIMEPref = LIMEPreferenceManager(appContext)
            if (datasource == null) {
                datasource = LimeDB(appContext)
            }
        }
    }

    @Throws(RemoteException::class)
    fun importTxtTable(filename: String?, tablename: String?, progressListener: LIMEProgressListener?) {
        importTxtTable(File(filename!!), tablename, progressListener)
    }

    fun importTxtTable(sourcefile: File?, tablename: String?, progressListener: LIMEProgressListener?) {
        if (DEBUG) {
            Log.i(TAG, "importTxtTable() on $loadingTablename")
        }

        loadingTablename = tablename.orEmpty()
        val db = datasource ?: return
        db.setFinish(false)
        db.setFilename(sourcefile)
        db.importTxtTable(tablename.orEmpty(), progressListener)
        db.resetCache()
    }

    fun exportTxtTable(
        table: String?,
        targetFile: File?,
        imConfigList: List<ImConfig>?,
        progressListener: LIMEProgressListener?
    ): Boolean {
        val db = datasource
        if (db == null) {
            Log.e(TAG, "exportTxtTable(): datasource is null")
            return false
        }
        return db.exportTxtTable(table.orEmpty(), targetFile, imConfigList?.toMutableList(), progressListener)
    }

    fun exportTxtTable(table: String?, targetFile: File?, imConfigList: List<ImConfig>?): Boolean {
        return exportTxtTable(table, targetFile, imConfigList, null)
    }

    fun getImConfig(imCode: String?, field: String?): String {
        val db = datasource
        if (db == null) {
            Log.e(TAG, "getImConfig(): datasource is null")
            return ""
        }
        return db.getImConfig(imCode.orEmpty(), field)
    }

    fun setImConfig(imCode: String?, field: String?, value: String?) {
        val db = datasource
        if (db == null) {
            Log.e(TAG, "setImConfig(): datasource is null")
            return
        }
        db.setImConfig(imCode, field, value)
    }

    fun importDbRelated(sourcedb: File?) {
        datasource?.importDbRelated(sourcedb)
    }

    fun importZippedDbRelated(compressedSourceDB: File?) {
        val unzipFilePaths = try {
            val unzipTargetDir = File(appContext.cacheDir, "limehd")
            LIMEUtilities.unzip(compressedSourceDB!!.absolutePath, unzipTargetDir.absolutePath, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error unzipping compressed related database", e)
            return
        }

        if (unzipFilePaths.size == 1) {
            importDbRelated(File(unzipFilePaths[0]))
            SearchServer.resetCache(true)
        } else {
            Log.e(TAG, "importZippedDbRelated(): Expected 1 file in zip, found " + unzipFilePaths.size)
        }
    }

    fun importDb(sourceDbFile: File?, tableName: String?) {
        val tableNames = ArrayList<String?>()
        tableNames.add(tableName)
        datasource?.importDb(sourceDbFile, tableNames, false, true)
    }

    fun importZippedDb(sourceDbFile: File?, tableName: String?) {
        val unzipFilePaths = try {
            val unzipTargetDir = File(appContext.cacheDir, "limehd")
            LIMEUtilities.unzip(sourceDbFile!!.absolutePath, unzipTargetDir.absolutePath, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error unzipping compressed database", e)
            return
        }

        if (unzipFilePaths.size == 1) {
            val tableNames = ArrayList<String?>()
            tableNames.add(tableName)
            datasource?.importDb(File(unzipFilePaths[0]), tableNames, false, true)
            SearchServer.resetCache(true)
        } else {
            Log.e(TAG, "importZippedDb(): Expected 1 file in zip, found " + unzipFilePaths.size)
        }
    }

    private fun getDataDirPath(): String {
        val dataDir = ContextCompat.getDataDir(appContext)
        return dataDir?.absolutePath ?: appContext.filesDir.parent.orEmpty()
    }

    @Throws(RemoteException::class)
    fun backupDatabase(uri: Uri?) {
        if (DEBUG) {
            Log.i(TAG, "backupDatabase()")
        }

        val dataDir = getDataDirPath()
        val fileSharedPrefsBackup = File(dataDir, LIME.SHARED_PREFS_BACKUP_NAME)
        if (fileSharedPrefsBackup.exists() && !fileSharedPrefsBackup.delete()) {
            Log.w(TAG, "Failed to delete existing shared preferences backup file")
        }
        backupDefaultSharedPreference(fileSharedPrefsBackup)

        val filePreferenceManifest = File(dataDir, PreferenceBackupAdapter.MANIFEST_PATH)
        backupPreferenceCompatibilityManifest(filePreferenceManifest)

        var limeDBPath = ctx!!.getDatabasePath(LIME.DATABASE_NAME).absolutePath
        val limeDBJournalFile = ctx!!.getDatabasePath(LIME.DATABASE_JOURNAL)
        var limeDBJournalPath = limeDBJournalFile.absolutePath
        if (limeDBPath.startsWith(dataDir)) {
            limeDBPath = limeDBPath.substring(dataDir.length)
        }
        if (limeDBJournalPath.startsWith(dataDir)) {
            limeDBJournalPath = limeDBJournalPath.substring(dataDir.length)
        }

        val backupFileList = ArrayList<String>()
        backupFileList.add(limeDBPath)
        if (limeDBJournalFile.exists()) {
            backupFileList.add(limeDBJournalPath)
        }
        backupFileList.add(LIME.SHARED_PREFS_BACKUP_NAME)
        backupFileList.add(PreferenceBackupAdapter.MANIFEST_PATH)

        datasource?.holdDBConnection()
        closeDatabase()

        val tempZip = File(appContext.cacheDir, LIME.DATABASE_BACKUP_NAME)
        if (tempZip.exists() && !tempZip.delete()) {
            Log.w(TAG, "Failed to delete existing temp zip file")
        }

        var outputStream: OutputStream? = null
        var inputStream: FileInputStream? = null
        var backupSucceeded = false

        try {
            LIMEUtilities.zip(tempZip.absolutePath, backupFileList, dataDir, true)
            if (!tempZip.exists() || tempZip.length() == 0L) {
                throw IOException("Backup archive was not created or is empty")
            }

            inputStream = FileInputStream(tempZip)
            outputStream = appContext.contentResolver.openOutputStream(uri!!)
            if (outputStream == null) {
                throw FileNotFoundException("Could not open backup output stream for URI: $uri")
            }

            val buffer = ByteArray(LIME.BUFFER_SIZE_4KB)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
            backupSucceeded = true
        } catch (e: Exception) {
            Log.e(TAG, "Error backing up database", e)
            showNotificationMessage(appContext.getText(R.string.l3_initial_backup_error).toString())
            throw RemoteException("Error backing up database: " + e.message)
        } finally {
            try {
                outputStream?.close()
                inputStream?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing streams during backup", e)
            }

            datasource?.let {
                it.unHoldDBConnection()
                it.openDBConnection(true)
            }
            if (fileSharedPrefsBackup.exists() && !fileSharedPrefsBackup.delete()) {
                Log.w(TAG, "Failed to delete shared preferences backup file in finally")
            }
            if (filePreferenceManifest.exists() && !filePreferenceManifest.delete()) {
                Log.w(TAG, "Failed to delete preference manifest in finally")
            }
            if (tempZip.exists() && !tempZip.delete()) {
                Log.w(TAG, "Failed to delete temp zip file in finally")
            }

            if (backupSucceeded) {
                showNotificationMessage(appContext.getText(R.string.l3_initial_backup_end).toString())
            }
        }
    }

    @Throws(IOException::class)
    fun restoreDatabase(uri: Uri?) {
        if (DEBUG) {
            Log.i(TAG, "restoreDatabase(Uri) Starting....")
        }

        val tempZip = File(appContext.cacheDir, LIME.DATABASE_BACKUP_NAME)
        if (tempZip.exists() && tempZip.delete()) {
            Log.w(TAG, "Failed to delete shared preferences backup file after restore")
        }

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            inputStream = appContext.contentResolver.openInputStream(uri!!)
            if (inputStream == null) {
                throw FileNotFoundException("Could not open input stream for URI: $uri")
            }
            outputStream = FileOutputStream(tempZip)

            val buffer = ByteArray(LIME.BUFFER_SIZE_4KB)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
            outputStream.close()
            outputStream = null

            if (tempZip.length() == 0L) {
                throw IOException("Restore failed: copied backup archive is empty (0 bytes)")
            }

            Log.i(TAG, "restoreDatabase(Uri) temp file created: " + tempZip.absolutePath)
            restoreDatabase(tempZip.absolutePath)
        } catch (e: IOException) {
            Log.e(TAG, "Error restoring database", e)
            showNotificationMessage(appContext.getText(R.string.l3_initial_restore_error).toString())
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring database", e)
            showNotificationMessage(appContext.getText(R.string.l3_initial_restore_error).toString())
            throw IOException("Restore failed: " + (e.message ?: e.javaClass.simpleName), e)
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing streams during restore", e)
            }
            if (tempZip.exists() && tempZip.delete()) {
                Log.w(TAG, "Failed to delete shared preferences backup file after restore")
            }
        }
    }

    @Throws(IOException::class)
    fun restoreDatabase(srcFilePath: String?) {
        val check = File(srcFilePath!!)
        val dataDir = getDataDirPath()

        if (!check.exists()) {
            showNotificationMessage(appContext.getText(R.string.error_restore_not_found).toString())
            throw FileNotFoundException("Restore source file not found: $srcFilePath")
        }

        if (!zipContainsLimeDbEntry(check)) {
            showNotificationMessage(appContext.getText(R.string.l3_initial_restore_error).toString())
            throw IOException("Restore failed: backup archive does not contain a lime.db entry")
        }

        datasource?.holdDBConnection()
        closeDatabase()

        val sharedPref = File(dataDir, LIME.SHARED_PREFS_BACKUP_NAME)
        val preferenceManifest = File(dataDir, PreferenceBackupAdapter.MANIFEST_PATH)

        var restoreSucceeded = false
        var restoreError: IOException? = null
        try {
            restoreFullBackupEntries(check, sharedPref, preferenceManifest)
            restoreSucceeded = true
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting restore file", e)
            showNotificationMessage(appContext.getText(R.string.l3_initial_restore_error).toString())
            restoreError = IOException("Restore failed: unable to extract backup archive", e)
        }

        val db = datasource
        db?.unHoldDBConnection()
        db?.openDBConnection(true)
        db?.ensureCurrentDatabase()

        if (!restoreSucceeded) {
            throw restoreError!!
        }

        showNotificationMessage(appContext.getText(R.string.l3_initial_restore_end).toString())

        if (!restorePreferenceCompatibilityManifest(preferenceManifest)) {
            restoreDefaultSharedPreference(sharedPref)
        }
        if (sharedPref.exists() && !sharedPref.delete()) {
            Log.w(TAG, "Failed to delete shared preferences backup file after restore")
        }
        if (preferenceManifest.exists() && !preferenceManifest.delete()) {
            Log.w(TAG, "Failed to delete preference manifest after restore")
        }
        SearchServer.resetCache(true)
        db?.checkAndUpdateRelatedTable()
        db?.ensureCurrentDatabase()
    }

    @Throws(IOException::class)
    private fun restoreFullBackupEntries(zipFile: File, sharedPref: File, preferenceManifest: File) {
        val databaseFile = ctx!!.getDatabasePath(LIME.DATABASE_NAME)
        val journalFile = ctx!!.getDatabasePath(LIME.DATABASE_JOURNAL)
        var restoredDatabase = false

        net.lingala.zip4j.ZipFile(zipFile).use { zip4jFile ->
            for (fileHeader: FileHeader in zip4jFile.fileHeaders) {
                if (fileHeader.isDirectory) {
                    continue
                }

                val normalizedName = normalizeBackupEntryName(fileHeader.fileName)
                val lastPathComponent = lastPathComponent(normalizedName)
                var target: File? = null

                if (LIME.DATABASE_NAME == lastPathComponent) {
                    target = databaseFile
                    restoredDatabase = true
                } else if (LIME.DATABASE_JOURNAL == lastPathComponent) {
                    target = journalFile
                } else if (LIME.SHARED_PREFS_BACKUP_NAME == lastPathComponent) {
                    target = sharedPref
                } else if (PreferenceBackupAdapter.MANIFEST_PATH == normalizedName) {
                    target = preferenceManifest
                }

                if (target == null) {
                    continue
                }
                zip4jFile.getInputStream(fileHeader).use { input ->
                    copyZipEntryToFile(input, target)
                }
            }
        }

        if (!restoredDatabase) {
            throw IOException("Restore failed: backup archive does not contain a restorable lime.db entry")
        }
    }

    private fun normalizeBackupEntryName(name: String?): String {
        var normalized = name?.replace('\\', '/') ?: return ""
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1)
        }
        return normalized
    }

    private fun lastPathComponent(path: String?): String {
        if (path.isNullOrEmpty()) return ""
        val slash = path.lastIndexOf('/')
        return if (slash >= 0) path.substring(slash + 1) else path
    }

    @Throws(IOException::class)
    private fun copyZipEntryToFile(input: InputStream, target: File) {
        val parent = target.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IOException("Failed to create restore target directory: " + parent.absolutePath)
        }

        BufferedOutputStream(FileOutputStream(target)).use { output ->
            val buffer = ByteArray(LIME.BUFFER_SIZE_4KB)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
            }
        }
    }

    @Throws(IOException::class)
    private fun zipContainsLimeDbEntry(zipFile: File): Boolean {
        net.lingala.zip4j.ZipFile(zipFile).use { zip4jFile ->
            for (fileHeader: FileHeader in zip4jFile.fileHeaders) {
                val name = fileHeader.fileName
                if (name != null && !fileHeader.isDirectory && normalizeBackupEntryName(name).endsWith("lime.db")) {
                    return true
                }
            }
        }
        return false
    }

    private fun backupPreferenceCompatibilityManifest(manifestFile: File?) {
        if (manifestFile == null) return
        val parent = manifestFile.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            Log.w(TAG, "Failed to create preference manifest directory")
            return
        }
        if (manifestFile.exists() && !manifestFile.delete()) {
            Log.w(TAG, "Failed to delete existing preference manifest")
        }

        try {
            FileOutputStream(manifestFile).use { output ->
                output.write(PreferenceBackupAdapter.exportManifestBytes(appContext))
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error writing preference manifest", e)
        } catch (e: JSONException) {
            Log.e(TAG, "Error writing preference manifest", e)
        }
    }

    private fun restorePreferenceCompatibilityManifest(manifestFile: File?): Boolean {
        if (manifestFile == null || !manifestFile.exists()) return false
        return try {
            FileInputStream(manifestFile).use { input ->
                PreferenceBackupAdapter.restoreManifest(appContext, input)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error restoring preference manifest", e)
            false
        } catch (e: JSONException) {
            Log.e(TAG, "Error restoring preference manifest", e)
            false
        }
    }

    fun backupDefaultSharedPreference(sharePrefs: File?) {
        if (sharePrefs == null) return
        if (sharePrefs.exists() && !sharePrefs.delete()) {
            Log.w(TAG, "Failed to delete existing shared preferences backup file")
        }

        var output: ObjectOutputStream? = null
        try {
            output = ObjectOutputStream(FileOutputStream(sharePrefs))
            val pref: SharedPreferences =
                appContext.getSharedPreferences(appContext.packageName + "_preferences", Context.MODE_PRIVATE)
            output.writeObject(pref.all)
        } catch (e: IOException) {
            Log.e(TAG, "Error backing up shared preferences", e)
        } finally {
            try {
                output?.flush()
                output?.close()
            } catch (ex: IOException) {
                Log.e(TAG, "Error closing shared preferences backup stream", ex)
            }
        }
    }

    fun restoreDefaultSharedPreference(sharePrefs: File?) {
        if (sharePrefs == null) return
        try {
            LegacyPreferenceObjectInputStream(FileInputStream(sharePrefs)).use { inputStream ->
                try {
                    val prefEdit = appContext
                        .getSharedPreferences(appContext.packageName + "_preferences", Context.MODE_PRIVATE)
                        .edit()
                    prefEdit.clear()
                    @Suppress("UNCHECKED_CAST")
                    val entries = inputStream.readObject() as Map<String, *>
                    for ((key, v) in entries) {
                        if (key == "PAYMENT_FLAG") {
                            continue
                        }

                        when (v) {
                            is Boolean -> prefEdit.putBoolean(key, v)
                            is Float -> prefEdit.putFloat(key, v)
                            is Int -> prefEdit.putInt(key, v)
                            is Long -> prefEdit.putLong(key, v)
                            is String -> prefEdit.putString(key, v)
                        }
                    }
                    prefEdit.apply()
                } catch (e: IOException) {
                    Log.e(TAG, "Error restoring shared preferences", e)
                } catch (e: ClassNotFoundException) {
                    Log.e(TAG, "Error restoring shared preferences", e)
                }
            }
        } catch (ex: IOException) {
            Log.e(TAG, "Error reading shared preferences backup file", ex)
        }
    }

    private fun closeDatabase() {
        Log.i(TAG, "closeDatabase()")
        datasource?.close()
    }

    fun unzip(sourceFile: File?, targetFolder: String?, targetFile: String?, removeOriginal: Boolean) {
        if (DEBUG) {
            Log.i(TAG, "unzip(), souce = $sourceFile, target = $targetFolder/$targetFile")
        }

        try {
            val targetFolderObj = File(targetFolder!!)
            if (!targetFolderObj.exists() && !targetFolderObj.mkdirs()) {
                Log.w(TAG, "Failed to create target folder")
            }

            FileInputStream(sourceFile).use { fis ->
                val zis = getZipInputStream(targetFile, fis, targetFolderObj)
                zis.close()
            }

            if (removeOriginal && !sourceFile!!.delete()) {
                Log.w(TAG, "Failed to delete original source file")
            }
        } catch (e: Exception) {
            showNotificationMessage(appContext.getText(R.string.l3_initial_download_failed).toString())
            Log.e(TAG, "Error compressing file", e)
        }
    }

    @Throws(IOException::class)
    private fun getZipInputStream(targetFile: String?, fis: FileInputStream, targetFolderObj: File): ZipInputStream {
        val zis = ZipInputStream(BufferedInputStream(fis))
        while (zis.nextEntry != null) {
            val buffer = ByteArray(LIME.BUFFER_SIZE_2KB)
            val outputFile = File(targetFolderObj.absolutePath + File.separator + targetFile)
            if (outputFile.exists() && !outputFile.delete()) {
                Log.w(TAG, "Failed to delete existing output file")
            }

            FileOutputStream(outputFile).use { fos ->
                BufferedOutputStream(fos, buffer.size).use { bos ->
                    var size: Int
                    while (zis.read(buffer, 0, buffer.size).also { size = it } != -1) {
                        bos.write(buffer, 0, size)
                    }
                    bos.flush()
                }
            }
        }
        return zis
    }

    fun zip(sourceFile: File?, targetFolder: String?, targetFile: String?) {
        if (DEBUG) {
            Log.i(TAG, "zip(), source = $sourceFile, target = $targetFolder/$targetFile")
        }

        try {
            val bufferSize = LIME.BUFFER_SIZE_2KB
            val targetFolderObj = File(targetFolder!!)
            if (!targetFolderObj.exists() && targetFolderObj.mkdirs()) {
                Log.w(TAG, "Failed to create target folder")
            }

            if (sourceFile == null || !sourceFile.exists() || !sourceFile.isFile) {
                Log.e(TAG, "zip(): source file does not exist: $sourceFile")
                return
            }

            val outputFile = File(targetFolderObj.absolutePath + File.separator + targetFile)
            if (outputFile.exists() && !outputFile.delete()) {
                Log.w(TAG, "Failed to delete existing output file for compression")
            }

            val data = ByteArray(bufferSize)
            FileOutputStream(outputFile).use { dest ->
                ZipOutputStream(BufferedOutputStream(dest)).use { out ->
                    FileInputStream(sourceFile).use { fi ->
                        BufferedInputStream(fi, bufferSize).use { origin ->
                            val entry = ZipEntry(sourceFile.name)
                            out.putNextEntry(entry)
                            var count: Int
                            while (origin.read(data, 0, bufferSize).also { count = it } != -1) {
                                out.write(data, 0, count)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing file", e)
        }
    }

    private fun showNotificationMessage(message: String) {
        val intent = Intent(appContext, LIMESettings::class.java)
        LIMEUtilities.showNotification(appContext, true, appContext.getText(R.string.ime_setting), message, intent)
    }

    fun isDatabseOnHold(): Boolean {
        return LimeDB.isDatabaseOnHold
    }

    fun exportZippedDb(tableName: String?, targetDbFile: File?, progressCallback: Runnable?): File? {
        if (tableName == null || targetDbFile == null) {
            Log.e(TAG, "exportZippedDb(): Invalid parameters")
            return null
        }

        return try {
            val cacheDir = appContext.externalCacheDir ?: appContext.cacheDir
            val dbFile = File(cacheDir, tableName + LIME.DATABASE_EXT)
            if (dbFile.exists() && !dbFile.delete()) {
                Log.e(TAG, "exportZippedDb(): Error deleting existing file")
            }

            if (targetDbFile.exists() && !targetDbFile.delete()) {
                Log.e(TAG, "exportZippedDb(): Error deleting existing zip file")
            }

            progressCallback?.run()

            LIMEUtilities.copyRAWFile(appContext.resources.openRawResource(R.raw.blank), dbFile)
            val tableNames = ArrayList<String?>()
            tableNames.add(tableName)
            datasource?.prepareBackup(dbFile, tableNames, false)
            LIMEUtilities.zip(targetDbFile.absolutePath, dbFile.absolutePath, true)

            if (dbFile.exists() && !dbFile.delete()) {
                Log.e(TAG, "exportZippedDb(): Error deleting temp file")
            }

            targetDbFile
        } catch (e: Exception) {
            Log.e(TAG, "exportZippedDb(): Error exporting database", e)
            null
        }
    }

    fun exportZippedDbRelated(targetFile: File?, progressCallback: Runnable?): File? {
        if (targetFile == null) {
            Log.e(TAG, "exportZippedDbRelated(): Invalid parameters")
            return null
        }

        return try {
            val cacheDir = appContext.externalCacheDir ?: appContext.cacheDir
            val dbFile = File(cacheDir, LIME.DB_TABLE_RELATED + LIME.DATABASE_EXT)
            if (dbFile.exists() && !dbFile.delete()) {
                Log.e(TAG, "exportZippedDbRelated(): Error deleting existing file")
            }

            if (targetFile.exists() && !targetFile.delete()) {
                Log.e(TAG, "exportZippedDbRelated(): Error deleting existing zip file")
            }

            progressCallback?.run()

            LIMEUtilities.copyRAWFile(appContext.resources.openRawResource(R.raw.blankrelated), dbFile)
            datasource?.prepareBackup(dbFile, null, true)
            LIMEUtilities.zip(targetFile.absolutePath, dbFile.absolutePath, true)

            if (dbFile.exists() && !dbFile.delete()) {
                Log.e(TAG, "exportZippedDbRelated(): Error deleting temp file")
            }

            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "exportZippedDbRelated(): Error exporting database", e)
            null
        }
    }

    private class LegacyPreferenceObjectInputStream(input: InputStream) : ObjectInputStream(input) {
        @Throws(IOException::class, ClassNotFoundException::class)
        override fun resolveClass(desc: ObjectStreamClass): Class<*> {
            val resolvedClass = super.resolveClass(desc)
            if (isAllowedLegacyPreferenceClass(resolvedClass)) {
                return resolvedClass
            }
            throw InvalidClassException(desc.name, "Class is not allowed in legacy preference backup")
        }

        companion object {
            private fun isAllowedLegacyPreferenceClass(clazz: Class<*>): Boolean {
                if (clazz.isPrimitive) return true
                if (clazz.isArray) {
                    val componentType = clazz.componentType
                    return componentType == Any::class.java ||
                        (componentType != null && isAllowedLegacyPreferenceClass(componentType))
                }
                if (clazz == String::class.java) return true
                if (Number::class.java.isAssignableFrom(clazz) ||
                    clazz == java.lang.Boolean::class.java ||
                    clazz == Character::class.java
                ) {
                    return true
                }
                if (isAllowedMapInternal(clazz.name)) return true
                return clazz.name.startsWith("java.util.") && Map::class.java.isAssignableFrom(clazz)
            }

            private fun isAllowedMapInternal(className: String): Boolean {
                return className.startsWith("java.util.HashMap$") ||
                    className.startsWith("java.util.LinkedHashMap$") ||
                    className.startsWith("java.util.Hashtable$")
            }
        }
    }

    companion object {
        private const val DEBUG = false
        private const val TAG = "DBServer"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: DBServer? = null

        @JvmStatic
        fun getInstance(context: Context): DBServer {
            var current = instance
            if (current == null) {
                synchronized(DBServer::class.java) {
                    current = instance
                    if (current == null) {
                        current = DBServer(context)
                        instance = current
                    }
                }
            }
            return current!!
        }

        @JvmStatic
        fun getInstance(): DBServer? = instance
    }
}
