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
package net.toload.main.hd.global

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLConnection
import java.util.ArrayList
import java.util.Objects
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.ZipFile
import net.toload.main.hd.LIMEService
import net.toload.main.hd.R

//
/**
 * @author jrywu
 */
class LIMEUtilities {
    fun getDbFolder(ctx: Context): File? {
        return ctx.getDatabasePath(LIME.DATABASE_NAME).getParentFile()
    }

    /**
     * Progress callback interface for download operations
     */
    interface DownloadProgressCallback {
        /**
         * Called when download progress updates
         * @param percent Progress percentage (0-100)
         */
        fun onProgress(percent: Int)
    }

    /**
     * Abort flag supplier interface for download operations (API 21+ compatible)
     * Replaces java.util.function.Supplier&lt;Boolean&gt; which requires API 24+
     */
    interface AbortFlagSupplier {
        /**
         * Gets the current abort flag state
         * @return true if download should be aborted, false otherwise
         */
        fun get(): Boolean
    }

    companion object {
        const val TAG: String = "LIMEUtilities"
        const val DEBUG: Boolean = false

        @JvmStatic
		fun isUnicodeSurrogate(word: String?): Boolean {  // emoji icons are within these surrogate areas
            if (word != null && word.length == 2) {
                val chArray = word.toCharArray()
                return Character.isSurrogatePair(chArray[0], chArray[1])
            }
            return false
        }

        /**
         * Return the filepath if the file not exist in the target path
         */
        @JvmStatic
        fun isFileNotExist(filepath: String): File? {
            val mfile = File(filepath)
            if (mfile.exists()) return null
            else return mfile
        }

        @JvmStatic
        fun isFileExist(filepath: String): File? {
            val mfile = File(filepath)
            if (mfile.exists()) return mfile
            else return null
        }

        /*
*   Zip single file into single zip.
*   sourceFile should be assigned with absolute path.
*
 */
        @JvmStatic
		@Throws(Exception::class)
        fun zip(zipFilePath: String, sourceFile: String?, OverWrite: Boolean?) {
            zip(zipFilePath, sourceFile, "", OverWrite)
        }

        /*
	*   Zip singl file into single zip.
	*   sourceFile is specify relative to the baseFolderPath.
	*   sourceFile should be assigned with absolute path if baseFolderPath is null of empty
	*
 	*/
        @JvmStatic
		@Throws(Exception::class)
        fun zip(
            zipFilePath: String,
            sourceFile: String?,
            baseFolderPath: String?,
            OverWrite: Boolean?
        ) {
            val sourceFileList: MutableList<String> = ArrayList<String>()
            sourceFileList.add(sourceFile!!)
            zip(zipFilePath, sourceFileList, baseFolderPath, OverWrite)
        }

        /*
	*   Zip multile files into single zip.
	*   sourceFiles should be assigned with absolute path.
	*
	 */
        @JvmStatic
		@Throws(Exception::class)
        fun zip(zipFilePath: String, sourceFiles: MutableList<String>, OverWrite: Boolean?) {
            zip(zipFilePath, sourceFiles, "", OverWrite)
        }

        /*
	*   Zip multiple files into single zip.
	*   sourceFiles is specify relative to the baseFolderPath.
	*   sourceFiles should be assigned with absolute path if baseFolderPath is null of empty
	*
	 */
        @JvmStatic
		@Throws(Exception::class)
        fun zip(
            zipFilePath: String,
            sourceFiles: MutableList<String>,
            baseFolderPath: String?,
            OverWrite: Boolean?
        ) {
            var baseFolderPath = baseFolderPath
            val zipFile = File(zipFilePath)
            if (zipFile.exists() && OverWrite != true) return
            else if (zipFile.exists() && OverWrite == true && !zipFile.delete()) Log.w(
                TAG,
                "Failed to delete existing zip file"
            )

            val zos: ZipOutputStream?
            val outStream: FileOutputStream?
            outStream = FileOutputStream(zipFile)
            zos = ZipOutputStream(outStream)

            if (baseFolderPath == null) baseFolderPath = ""

            for (item in sourceFiles) {
                val itemName =
                    if (item.startsWith(File.separator) || baseFolderPath.endsWith(File.separator)) item else (File.separator + item)

                if (baseFolderPath.isEmpty())  //absolute path
                    addFileToZip(baseFolderPath + itemName, zos)
                else  //relative path
                    addFileToZip(baseFolderPath + itemName, baseFolderPath, zos)
            }
            zos.flush()
            zos.close()
        }

        @Throws(Exception::class)
        private fun addFileToZip(
            sourceFilePath: String,
            baseFolderPath: String?,
            zos: ZipOutputStream
        ) {
            addFileToZip("", sourceFilePath, baseFolderPath, zos)
        }

        @Throws(Exception::class)
        private fun addFileToZip(sourceFilePath: String, zos: ZipOutputStream) {
            addFileToZip("", sourceFilePath, "", zos)
        }

        @Throws(Exception::class)
        private fun addFileToZip(
            sourceFolderPath: String?,
            sourceFilePath: String,
            baseFolderPath: String?,
            zos: ZipOutputStream
        ) {
            var baseFolderPath = baseFolderPath
            val item = File(sourceFilePath)
            //if( item==null || !item.exists()) return; //skip if the file is not exist
            if (isSymLink(item)) return  // do nothing to symbolic links.


            if (baseFolderPath == null) baseFolderPath = ""

            if (item.isDirectory()) {
                for (subItem in Objects.requireNonNull<Array<String?>?>(item.list())) {
                    addFileToZip(
                        sourceFolderPath + File.separator + item.getName(),
                        sourceFilePath + File.separator + subItem,
                        baseFolderPath,
                        zos
                    )
                }
            } else {
                val buf = ByteArray(LIME.BUFFER_SIZE_64KB)
                var len: Int
                FileInputStream(sourceFilePath).use { inStream ->
                    var entryPath: String?
                    if (baseFolderPath.isEmpty()) {
                        entryPath = item.getName()
                    } else {
                        entryPath = sourceFilePath.substring(baseFolderPath.length)
                    }

                    if (entryPath.startsWith(File.separator)) {
                        entryPath = entryPath.substring(1)
                    }

                    zos.putNextEntry(ZipEntry(entryPath))
                    while ((inStream.read(buf).also { len = it }) > 0) {
                        zos.write(buf, 0, len)
                    }
                }
            }
        }

        @JvmStatic
		@Throws(IOException::class)
        fun isSymLink(filePath: File): Boolean {
            if (filePath == null) throw NullPointerException("filePath cannot be null")
            val canonical: File?
            if (filePath.getParent() == null) {
                canonical = filePath
            } else {
                val canonDir =
                    Objects.requireNonNull<File?>(filePath.getParentFile()).getCanonicalFile()
                canonical = File(canonDir, filePath.getName())
            }
            return canonical.getCanonicalFile() != canonical.getAbsoluteFile()
        }

        @JvmStatic
		@Throws(IOException::class)
        fun unzip(
            zipFilePath: String,
            targetFolder: String,
            OverWrite: Boolean?
        ): MutableList<String?> {
            return unzip(File(zipFilePath), File(targetFolder), OverWrite == true)
        }

        @Throws(IOException::class)
        @JvmStatic
        fun unzip(
            zipFile: File,
            targetDirectory: File,
            overWrite: Boolean
        ): MutableList<String?> {
            val returnFilePaths: MutableList<String?> = ArrayList<String?>()

            if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
                throw IOException("Failed to create target dir: " + targetDirectory.getAbsolutePath())
            }

            ZipFile(zipFile).use { zip4jFile ->
                val fileHeaders = zip4jFile.getFileHeaders()
                for (fileHeader in fileHeaders) {
                    val itemName = fileHeader.getFileName()

                    if (itemName == null || itemName.isEmpty()) {
                        continue
                    }

                    val targetFile: File?

                    // Handle absolute /data/ paths: only restore into our package root
                    if (itemName.startsWith("/data/")
                        || itemName.startsWith(
                            Environment.getDataDirectory().toString() + File.separator
                        )
                    ) {
                        val packageRoot: String =
                            LIME.limeDataRootFolder // e.g. /data/user/0/your.pkg
                        val abs = File(itemName).getCanonicalFile()
                        val root = File(packageRoot).getCanonicalPath() + File.separator

                        // Skip if not under our package root
                        if (!abs.getPath().startsWith(root)) {
                            continue
                        }

                        targetFile = abs
                    } else {
                        // Normal relative entry under targetDirectory
                        val out = File(targetDirectory, itemName).getCanonicalFile()
                        val destRoot = targetDirectory.getCanonicalPath() + File.separator

                        // Zip‑Slip guard: keep inside targetDirectory
                        if (!out.getPath().startsWith(destRoot)) {
                            continue
                        }

                        targetFile = out
                    }

                    // Create parent directories if they don't exist
                    val parentDir = targetFile.getParentFile()
                    if (parentDir != null && !parentDir.isDirectory() && !parentDir.mkdirs()) {
                        throw IOException("Failed to ensure parent directory: " + parentDir.getAbsolutePath())
                    }

                    if (fileHeader.isDirectory()) {
                        if (!targetFile.isDirectory() && !targetFile.mkdirs()) {
                            throw IOException("Failed to create directory: " + targetFile.getAbsolutePath())
                        }
                        returnFilePaths.add(targetFile.getAbsolutePath())
                        continue
                    }

                    if (targetFile.exists()) {
                        if (!overWrite) {
                            returnFilePaths.add(targetFile.getAbsolutePath())
                            continue
                        }
                        if (!targetFile.delete()) {
                            throw IOException("Failed to delete existing file: " + targetFile)
                        }
                    }

                    // Extract the file using Zip4j
                    zip4jFile.extractFile(fileHeader, targetDirectory.getAbsolutePath())
                    returnFilePaths.add(targetFile.getAbsolutePath())
                }
            }
            return returnFilePaths
        }


        @JvmStatic
        fun copyFile(
            sourceFilePath: String?,
            targetFilePath: String?,
            overWrite: Boolean?
        ): Boolean {
            val sourceFile: File? = Companion.isFileExist(sourceFilePath!!)
            if (sourceFilePath == null || sourceFile == null || targetFilePath == null) return false
            var targetFile: File? = isFileExist(targetFilePath)
            if (targetFile != null && overWrite != true) return false
            if (targetFile == null) targetFile = File(targetFilePath)
            try {
                val inStream = FileInputStream(sourceFile)
                val outSteram = FileOutputStream(targetFile)
                copyRAWFile(inStream, outSteram)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Error copying file", e)
                return false
            }
        }

        @JvmStatic
		fun copyRAWFile(inStream: InputStream, newfile: File?) {
            try {
                val fs = FileOutputStream(newfile)
                copyRAWFile(inStream, fs)
                fs.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error copying raw file to new file", e)
            }
        }

        @JvmStatic
        fun copyRAWFile(inStream: InputStream, outStream: FileOutputStream) {
            try {
                var bytesum = 0
                var byteread: Int

                val buffer = ByteArray(LIME.BUFFER_SIZE_64KB)
                while ((inStream.read(buffer).also { byteread = it }) != -1) {
                    bytesum += byteread
                    if (DEBUG) Log.d(TAG, "bytesum: " + bytesum)
                    outStream.write(buffer, 0, byteread)
                }
                inStream.close()
                outStream.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error copying raw file", e)
            }
        }


        /** Add by Jeremy '12,4,23 Show notification with notification builder in compatibility package replacing the deprecated alert dialog creation
         * 
         */
        @JvmStatic
        fun showNotification(
            context: Context,
            autoCancel: Boolean?,
            title: CharSequence?,
            message: CharSequence?,
            intent: Intent?
        ) {
            val mNotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channelId = "lime_notification_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "LIME Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                mNotificationManager.createNotificationChannel(channel)
            }

            val mBuilder: NotificationCompat.Builder =
                NotificationCompat.Builder(context, channelId) // Pass channel ID here
                    .setLargeIcon(getNotificationIconBitmap(context))
                    .setContentTitle(title)
                    .setAutoCancel(autoCancel == true)
                    .setTicker(message)
                    .setContentText(message)


            mBuilder.setSmallIcon(R.drawable.logobw)


            mNotificationManager.notify(501, mBuilder.build())
        }

        private val notificationIcon: Int
            get() =//boolean whiteIcon = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
                R.drawable.logobw

        private fun getNotificationIconBitmap(context: Context): Bitmap? {
            return BitmapFactory.decodeResource(context.getResources(), R.drawable.logo)
        }


        @JvmStatic
		fun isVoiceSearchServiceExist(context: Context): String? {
            if (DEBUG) Log.i(TAG, "isVoiceSearchServiceExist()")

            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            if (imm == null) {
                return null
            }
            val mInputMethodProperties = imm.getEnabledInputMethodList()

            var subtypeVoiceId: String? = null
            var heuristicVoiceId: String? = null
            var gboardId: String? = null

            for (imi in mInputMethodProperties) {
                val id = imi.getId()
                if (DEBUG) Log.i(TAG, "enabled IM:" + id)

                if (isKnownVoiceInputMethodId(id)) {
                    return id
                }
                if (subtypeVoiceId == null && hasVoiceSubtype(imi)) {
                    subtypeVoiceId = id
                }
                if (heuristicVoiceId == null && isHeuristicVoiceInputMethodId(id)) {
                    heuristicVoiceId = id
                }
                if (gboardId == null && isGboardInputMethodId(id)) {
                    gboardId = id
                }
            }

            if (subtypeVoiceId != null) {
                return subtypeVoiceId
            }
            if (heuristicVoiceId != null) {
                return heuristicVoiceId
            }
            if (gboardId != null) {
                return gboardId
            }
            return null
        }

        @JvmStatic
		fun isVoiceInputMethodId(id: String?): Boolean {
            if (id == null) {
                return false
            }
            return isKnownVoiceInputMethodId(id) ||
                    isHeuristicVoiceInputMethodId(id) ||
                    isGboardInputMethodId(id)
        }

        private fun isKnownVoiceInputMethodId(id: String?): Boolean {
            if (id == null) {
                return false
            }
            return id == "com.google.android.voicesearch/.ime.VoiceInputMethodService" ||
                    id == "com.google.android.googlequicksearchbox/com.google.android.voicesearch.ime.VoiceInputMethodService" ||
                    id == "com.google.android.tts/com.google.android.apps.speech.tts.googletts.settings.asr.voiceime.VoiceInputMethodService"
        }

        private fun isHeuristicVoiceInputMethodId(id: String?): Boolean {
            if (id == null) {
                return false
            }
            val lowerId = id.lowercase()
            return lowerId.contains("voice") || lowerId.contains("speech")
        }

        private fun isGboardInputMethodId(id: String?): Boolean {
            return id != null && id.startsWith("com.google.android.inputmethod.latin/")
        }

        private fun hasVoiceSubtype(imi: InputMethodInfo?): Boolean {
            if (imi == null) {
                return false
            }
            for (i in 0..<imi.getSubtypeCount()) {
                val subtype = imi.getSubtypeAt(i)
                if (subtype != null && "voice".equals(subtype.getMode(), ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        @JvmStatic
		fun isLIMEEnabled(context: Context): Boolean {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val mInputMethodProperties = imm.getEnabledInputMethodList()
            val limeID: String = getLIMEID(context)

            var isLIMEActive = false

            for (i in mInputMethodProperties.indices) {
                val imi = mInputMethodProperties.get(i)
                if (DEBUG) Log.i(TAG, "enabled IM " + i + ":" + imi.getId())
                if (imi.getId() == limeID) {
                    isLIMEActive = true
                    break
                }
            }
            return isLIMEActive
        }

        @JvmStatic
		fun isLIMEActive(context: Context): Boolean {
            val activeIM = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            val limeID: String = getLIMEID(context)

            if (DEBUG) Log.i(TAG, "active IM:" + activeIM + " LIME IM:" + limeID)
            return activeIM == limeID
        }

        @JvmStatic
		fun getLIMEID(context: Context): String {
            val LIMEComponentName = ComponentName(context, LIMEService::class.java)
            return LIMEComponentName.flattenToShortString()
        }

        @JvmStatic
        fun getVoiceSearchIMId(context: Context?): String {
            val voiceInputComponent =
                ComponentName(
                    "com.google.android.voiceSearch",
                    "com.google.android.voicesearch.ime.VoceInputMethdServce"
                )
            if (DEBUG) Log.i(
                TAG, ("getVoiceSearchIMId(), Comment name = "
                        + voiceInputComponent.flattenToString() + ", id = "
                        + voiceInputComponent.flattenToShortString())
            )
            return voiceInputComponent.flattenToShortString()
        }

        @JvmStatic
		fun showInputMethodSettingsPage(context: Context) {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        @JvmStatic
		fun showInputMethodPicker(context: Context) {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        /**
         * Shared method to download a remote file.
         * Supports both temporary file creation (for cache) and specific file creation (for persistent storage).
         * 
         * @param url The URL to download from
         * @param targetFile The target file to write to (if null, creates temp file in cacheDir)
         * @param cacheDir Context cache directory (required if targetFile is null)
         * @param progressCallback Optional progress callback (can be null)
         * @param abortFlagSupplier Optional supplier function that returns abort flag state (can be null)
         * @return The downloaded file, or null if download failed
         */
        @JvmStatic
        fun downloadRemoteFile(
            url: String?, targetFile: File?, cacheDir: File,
            progressCallback: DownloadProgressCallback?, abortFlagSupplier: AbortFlagSupplier?
        ): File? {
            if (DEBUG) Log.i(TAG, "downloadRemoteFile() Starting: " + url)

            try {
                val downloadUrl = URL(url)
                val conn = downloadUrl.openConnection()
                conn.connect()
                val `is` = conn.getInputStream()
                val remoteFileSize = conn.getContentLength().toLong()
                var downloadedSize: Long = 0

                if (DEBUG) Log.i(TAG, "downloadRemoteFile() contentLength: " + remoteFileSize)

                if (`is` == null) {
                    throw RuntimeException("stream is null")
                }

                val downloadedFile: File?
                if (targetFile != null) {
                    // Use specific target file
                    val downloadFolder = targetFile.getParentFile()
                    if (downloadFolder != null && !downloadFolder.exists()) {
                        if (!downloadFolder.mkdirs()) {
                            Log.w(
                                TAG,
                                "Failed to create target folder: " + downloadFolder.getAbsolutePath()
                            )
                        }
                    }
                    downloadedFile = targetFile
                    if (downloadedFile.exists() && !downloadedFile.delete()) {
                        Log.w(TAG, "Failed to delete existing downloadedFile")
                    }
                } else {
                    // Create temp file in cache directory
                    requireNotNull(cacheDir) { "cacheDir cannot be null when targetFile is null" }
                    downloadedFile = File.createTempFile(
                        LIME.DATABASE_IM_TEMP,
                        LIME.DATABASE_IM_TEMP_EXT,
                        cacheDir
                    )
                    downloadedFile.deleteOnExit()
                }

                FileOutputStream(downloadedFile).use { fos ->
                    `is`.use { inputStream ->
                        // Use 128KB buffer for better performance on modern devices
                        val buf = ByteArray(LIME.BUFFER_SIZE_64KB)
                        do {
                            // Check abort flag if provided
                            if (abortFlagSupplier != null && abortFlagSupplier.get()) {
                                if (DEBUG) Log.i(TAG, "downloadRemoteFile() aborted by user")
                                break
                            }

                            // InputStream.read() is already blocking and will wait for data
                            val numread = inputStream.read(buf)
                            if (numread <= 0) {
                                break
                            }

                            fos.write(buf, 0, numread)
                            downloadedSize += numread.toLong() // Track actual bytes downloaded

                            // Update progress if callback provided and size is known
                            if (progressCallback != null && remoteFileSize > 0) {
                                val percent =
                                    (downloadedSize.toFloat() / remoteFileSize.toFloat()) * 100
                                progressCallback.onProgress(percent.toInt())
                            }

                            if (DEBUG) Log.i(
                                TAG, ("downloadRemoteFile(), contentLength: " + remoteFileSize
                                        + ", downloadedSize: " + downloadedSize)
                            )
                        } while (true)
                    }
                }
                return downloadedFile
            } catch (e: FileNotFoundException) {
                Log.e(
                    TAG,
                    "downloadRemoteFile() FileNotFoundException: can't open file for writing.",
                    e
                )
            } catch (e: MalformedURLException) {
                Log.e(TAG, "downloadRemoteFile() MalformedURLException: " + url, e)
            } catch (e: IOException) {
                Log.e(TAG, "downloadRemoteFile() IOException: " + e.message, e)
            } catch (e: Exception) {
                Log.e(TAG, "downloadRemoteFile() Exception: " + e.message, e)
            }
            if (DEBUG) Log.i(TAG, "downloadRemoteFile() failed.")
            return null
        }
    }
}
