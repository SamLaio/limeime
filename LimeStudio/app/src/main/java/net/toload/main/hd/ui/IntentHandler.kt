package net.toload.main.hd.ui

import android.app.AlertDialog
import android.content.ContentResolver
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentTransaction
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import net.toload.main.hd.R
import net.toload.main.hd.ui.controller.SetupImController
import net.toload.main.hd.ui.dialog.ImportDialog

/**
 * Handles intent processing for LIMESettings.
 * 
 * 
 * This class encapsulates all intent-related logic, including:
 * 
 *  * ACTION_SEND: text/plain file imports (.lime, .cin files)
 *  * ACTION_VIEW: file imports with URI scheme handling
 *  * File validation and type checking
 *  * Input stream to file conversion
 * 
 * 
 * 
 * This extraction reduces LIMESettings's complexity and centralizes intent
 * handling logic for better maintainability.
 */
class IntentHandler(
    private val activity: LIMESettings,
    private val setupImController: SetupImController?
) {
    /**
     * Processes intent and handles file imports.
     * 
     * 
     * This method extracts intent data and routes to appropriate handlers:
     * 
     *  * ACTION_SEND + text/plain: [.handleSendText]
     *  * ACTION_VIEW + content/file scheme: [.processFileImport]
     * 
     * 
     * @param intent The intent to process
     */
    fun processIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.getAction()
        val type = intent.getType()


        // 1. For ACTION_SEND, use handleSendText() to process
        if (Intent.ACTION_SEND == action && type != null) {
            if ("text/plain" == type) {
                handleSendText(intent)
            }
        } else if (Intent.ACTION_VIEW == action && type != null) {
            processFileImport(intent)
        }
    }

    /**
     * Handles ACTION_SEND intent with text/plain type.
     * 
     * 
     * Extracts the shared text and initiates file import process
     * by delegating to [.handleImportTxt].
     * 
     * @param intent The ACTION_SEND intent
     */
    private fun handleSendText(intent: Intent) {
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (sharedText != null) {
            handleImportTxt(sharedText)
        }
    }

    /**
     * Processes ACTION_VIEW intent with file/content schemes.
     * 
     * 
     * Validates URI, extracts filename, checks file type, and routes
     * to appropriate import handler based on file type.
     * 
     * @param intent The ACTION_VIEW intent
     */
    private fun processFileImport(intent: Intent) {
        val type = intent.getType()
        val scheme = intent.getScheme()
        val uri = intent.getData()

        if (uri == null) {
            Log.e(TAG, "Intent data URI is null")
            return
        }


        // Validate scheme
        val resolver: ContentResolver = activity.getContentResolver()
        if (!isValidScheme(scheme)) {
            Log.e(TAG, "Invalid URI scheme: " + scheme)
            showToast(activity.getResources().getString(R.string.error_file_format))
            return
        }


        // Extract filename
        var fileName = getContentName(resolver, uri)
        if (fileName == null) {
            fileName = uri.getLastPathSegment()
        }
        if (fileName == null) {
            val errorMessage: String =
                activity.getResources().getString(R.string.error_no_file_name)
            Log.e(TAG, errorMessage)
            showToast(errorMessage)
            return
        }

        val extension = getFileExtension(fileName)
        if (extension.isEmpty()) {
            showToast(activity.getResources().getString(R.string.error_file_format))
            return
        }


        // 3. Check if type matches extension
        if (!isFileTypeValid(type, extension)) {
            val errorMessage: String = activity.getResources().getString(R.string.error_file_format)
            Log.w(TAG, errorMessage)
            showToast(errorMessage)
            return
        }


        // Read file from URI
        val input: InputStream?
        try {
            input = resolver.openInputStream(uri)
        } catch (e: FileNotFoundException) {
            val errorMessage: String? =
                activity.getResources().getString(R.string.error_file_opening_error)
            Log.e(TAG, errorMessage, e)
            showToast(errorMessage)
            return
        }

        if (input == null) {
            Log.e(TAG, "Input stream is null")
            showToast(activity.getResources().getString(R.string.error_file_opening_error))
            return
        }


        // Prepare import directory and file
        val importDir: File = File(activity.getCacheDir(), "imports")
        if (!importDir.exists() && !importDir.mkdirs()) {
            Log.w(TAG, "Failed to create import dir: " + importDir.getAbsolutePath())
        }
        val fileToImport = File(importDir, fileName)
        val importFilepath = fileToImport.getAbsolutePath()


        // Convert input stream to file
        InputStreamToFile(input, importFilepath)


        // Handle based on file type
        if ("text/plain" == type && ("lime" == extension || "cin" == extension)) {
            // 4. For text/plain with .lime or .cin, call handleImportTxt
            handleImportTxt(importFilepath)
        } else if ("application/zip" == type && "limedb" == extension) {
            // 5. For .limedb file, handle import
            val tableName = getFileNameWithoutExtension(fileName)
            handleLimedbImport(fileToImport, tableName)
        }
    }

    /**
     * Handles text file import (.lime or .cin).
     * 
     * 
     * Shows import dialog for user to select target IM table.
     * 
     * @param importFilepath The path to the text file to import
     */
    private fun handleImportTxt(importFilepath: String) {
        try {
            val fileToImport = File(importFilepath)
            setupImController!!.setFileToImport(fileToImport) // Store for onImportTypeSelected callback

            val ft: FragmentTransaction = activity.getSupportFragmentManager().beginTransaction()
            val dialog = ImportDialog.newInstanceForFile(importFilepath)
            dialog.setOnImportTypeSelectedListener(setupImController)
            dialog.show(ft, "ImportDialog")
        } catch (e: Exception) {
            val errorMessage: String? = activity.getResources().getString(R.string.error_import_db)
            Log.e(TAG, errorMessage, e)
            showToast(errorMessage + ": " + e.message)
        }
    }

    /**
     * Handles .limedb (compressed database) import.
     * 
     * 
     * Checks if table is empty before importing. If not empty, shows confirmation dialog.
     * If empty, proceeds with import. Delegates actual import to controller.
     * 
     * @param fileToImport The .limedb file to import
     * @param tableName The target table name
     */
    private fun handleLimedbImport(fileToImport: File?, tableName: String?) {
        try {
            // Check if table is empty before importing
            val recordCount = setupImController!!.countRecords(tableName)

            if (recordCount > 0) {
                // Table is not empty: show single three-choice dialog (cancel/restore/don't restore)
                showImportConfirmationDialog(fileToImport, tableName)
            } else {
                // Table is empty: proceed with import (default: don't restore)
                performLimedbImport(fileToImport, tableName, false)
            }
        } catch (e: Exception) {
            val errorMessage: String? = activity.getResources().getString(R.string.error_import_db)
            Log.e(TAG, errorMessage, e)
            showToast(errorMessage + ": " + e.message)
        }
    }

    /**
     * Shows confirmation dialog before importing to non-empty table.
     * 
     * @param fileToImport The .limedb file
     * @param tableName The target table name
     */
    private fun showImportConfirmationDialog(fileToImport: File?, tableName: String?) {
        val builder = AlertDialog.Builder(activity)
        // Use the same strings as 1a alert dialog
        val title: String? =
            activity.getResources().getString(R.string.setup_im_restore_learning_data)
        val message: String? =
            activity.getResources().getString(R.string.setup_im_restore_learning_data_message)
        builder.setTitle(title)
        builder.setMessage(message)
        // Cancel
        builder.setNeutralButton(
            activity.getResources().getString(R.string.dialog_cancel),
            DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> dialog!!.dismiss() })
        // Restore
        builder.setPositiveButton(
            activity.getResources().getString(R.string.dialog_yes),
            DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                performLimedbImport(
                    fileToImport,
                    tableName,
                    true
                )
            })
        // Don't restore
        builder.setNegativeButton(
            activity.getResources().getString(R.string.dialog_no),
            DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                performLimedbImport(
                    fileToImport,
                    tableName,
                    false
                )
            })
        builder.show()
    }

    /**
     * Performs the actual .limedb import.
     * 
     * @param fileToImport The .limedb file
     * @param tableName The target table name
     */
    private fun performLimedbImport(
        fileToImport: File?,
        tableName: String?,
        restoreUserRecords: Boolean
    ) {
        if (setupImController != null) {
            setupImController.importZippedDb(fileToImport, tableName.orEmpty(), restoreUserRecords)
        }
    }

    /**
     * Converts input stream to file.
     * 
     * @param inputStream The input stream to read from
     * @param filePath The target file path to write to
     */
    private fun InputStreamToFile(inputStream: InputStream, filePath: String?) {
        try {
            val outputStream: OutputStream = FileOutputStream(filePath)
            val buffer = ByteArray(1024)
            var length: Int
            while ((inputStream.read(buffer).also { length = it }) > 0) {
                outputStream.write(buffer, 0, length)
            }
            outputStream.close()
            inputStream.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error converting input stream to file", e)
            showToast(activity.getResources().getString(R.string.error_file_opening_error))
        }
    }

    // ========== Helper Methods ==========
    /**
     * Extracts file extension from filename.
     * 
     * @param fileName The filename
     * @return The extension (without dot), or empty string if no extension
     */
    private fun getFileExtension(fileName: String?): String {
        if (fileName == null || fileName.isEmpty()) {
            return ""
        }
        val lastDot = fileName.lastIndexOf('.')
        if (lastDot > 0 && lastDot < fileName.length - 1) {
            return fileName.substring(lastDot + 1).lowercase(Locale.getDefault())
        }
        return ""
    }

    /**
     * Gets filename without extension.
     * 
     * @param fileName The filename
     * @return The filename without extension
     */
    private fun getFileNameWithoutExtension(fileName: String?): String {
        if (fileName == null || fileName.isEmpty()) {
            return ""
        }
        val lastDot = fileName.lastIndexOf('.')
        if (lastDot > 0) {
            return fileName.substring(0, lastDot)
        }
        return fileName
    }

    /**
     * Checks if file type matches expected extension.
     * 
     * @param mimeType The MIME type
     * @param extension The file extension
     * @return true if type matches extension
     */
    private fun isFileTypeValid(mimeType: String?, extension: String?): Boolean {
        if (mimeType == null || extension == null) {
            return false
        }

        if ("text/plain" == mimeType) {
            return "lime" == extension || "cin" == extension
        } else if ("application/zip" == mimeType) {
            return "limedb" == extension
        }

        return false
    }

    /**
     * Validates URI scheme.
     * 
     * @param scheme The URI scheme to validate
     * @return true if scheme is valid for file operations
     */
    private fun isValidScheme(scheme: String?): Boolean {
        if (scheme == null) return false
        return ContentResolver.SCHEME_CONTENT == scheme
                || ContentResolver.SCHEME_FILE == scheme
                || "http" == scheme || "https" == scheme || "ftp" == scheme
    }

    /**
     * Gets content name from URI.
     * 
     * @param resolver The ContentResolver
     * @param uri The URI
     * @return The content name, or null if not found
     */
    private fun getContentName(resolver: ContentResolver, uri: Uri): String? {
        try {
            val cursor = resolver.query(uri, null, null, null, null)
            if (cursor == null) return null

            val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            val name = cursor.getString(nameIndex)
            cursor.close()
            return name
        } catch (e: Exception) {
            Log.e(TAG, "Error getting content name", e)
            return null
        }
    }

    /**
     * Shows a toast message.
     * 
     * @param message The message to show
     */
    private fun showToast(message: String?) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "IntentHandler"
    }
}
