package net.toload.main.hd.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import net.toload.main.hd.global.LIME
import net.toload.main.hd.R
import net.toload.main.hd.ui.controller.SetupImController

/**
 * Manages share operations for IM tables and related phrases.
 * 
 * 
 * This class encapsulates all share-related functionality, including:
 * 
 *  * Sharing IM tables as text files (.lime)
 *  * Sharing IM tables as compressed database files (.limedb)
 *  * Sharing related phrases as text files
 *  * Sharing related phrases as compressed database files
 *  * Android share intent creation and file provider URI handling
 * 
 * 
 * 
 * This extraction reduces LIMESettings's complexity and provides a reusable
 * component for share operations across the application.
 */
class ShareManager(
    private val activity: LIMESettings,
    setupImController: SetupImController,
    progressManager: ProgressManager?
) {
    private val setupImController: SetupImController
    private val progressManager: ProgressManager?

    private var shareThread: Thread? = null

    /**
     * Creates a new ShareManager.
     * 
     * @param activity The activity context for UI operations
     * @param setupImController The controller for export operations
     * @param progressManager The progress manager for showing export progress
     */
    init {
        this.setupImController = setupImController
        this.progressManager = progressManager
    }

    /**
     * Initiates sharing of an IM table as a text file.
     * 
     * 
     * This method starts a background thread that exports the specified IM table
     * to a text file and then shares it using the Android share intent.
     * 
     * @param tableName The IM type (table name) to share
     */
    fun shareImAsText(tableName: String?) {
        shareThread = Thread(Runnable {
            if (progressManager != null) progressManager.show()
            if (progressManager != null) progressManager.updateProgress(
                activity.getResources().getString(R.string.share_step_initial)
            )

            var cacheDir: File? = activity.getExternalCacheDir()
            if (cacheDir == null) {
                cacheDir = activity.getCacheDir()
            }
            val target = File(cacheDir, tableName + ".lime")

            val exported = setupImController.exportTxtTable(
                tableName, target,
                Runnable {
                    if (progressManager != null) {
                        progressManager.updateProgress(
                            activity.getResources().getString(R.string.share_step_write)
                        )
                    }
                })

            if (progressManager != null) progressManager.dismiss()
            if (exported != null) {
                shareFile(exported.getAbsolutePath(), "text/plain")
            } else {
                Log.e(TAG, "Failed to export table: " + tableName)
            }
        })
        shareThread!!.start()
    }

    /**
     * Initiates sharing of an IM table as a compressed database file.
     * 
     * 
     * This method starts a background thread that exports the specified IM table
     * to a compressed .limedb file and then shares it using the Android share intent.
     * 
     * @param tableName The IM type (table name) to share
     */
    fun exportAndShareImTable(tableName: String?) {
        shareThread = Thread(Runnable {
            if (progressManager != null) progressManager.show()
            var cacheDir: File? = activity.getExternalCacheDir()
            if (cacheDir == null) {
                cacheDir = activity.getCacheDir()
            }

            val targetFileZip = File(cacheDir, tableName + ".limedb")

            if (progressManager != null) progressManager.updateProgress(
                activity.getResources().getString(R.string.share_step_initial)
            )
            val exportedFile = setupImController.exportZippedDb(
                tableName, targetFileZip,
                Runnable {
                    if (progressManager != null) {
                        progressManager.updateProgress(
                            activity.getResources().getString(R.string.share_step_write)
                        )
                    }
                })

            if (progressManager != null) progressManager.dismiss()
            if (exportedFile != null) {
                shareFile(exportedFile.getAbsolutePath(), "application/zip")
            } else {
                Log.e(TAG, "Failed to export database: " + tableName)
            }
        })
        shareThread!!.start()
    }

    /**
     * Initiates sharing of the related phrases table as a text file.
     * 
     * 
     * This method starts a background thread that exports the related phrases
     * table to a text file and then shares it using the Android share intent.
     */
    fun shareRelatedAsText() {
        shareThread = Thread(Runnable {
            if (progressManager != null) progressManager.show()
            if (progressManager != null) progressManager.updateProgress(
                activity.getResources().getString(R.string.share_step_initial)
            )

            var cacheDir: File? = activity.getExternalCacheDir()
            if (cacheDir == null) {
                cacheDir = activity.getCacheDir()
            }
            val target = File(cacheDir, "lime.related")

            val exported = setupImController.exportTxtTableRelated(
                target,
                Runnable {
                    if (progressManager != null) {
                        progressManager.updateProgress(
                            activity.getResources().getString(R.string.share_step_write)
                        )
                    }
                })

            if (progressManager != null) progressManager.dismiss()
            if (exported != null) {
                shareFile(exported.getAbsolutePath(), "text/plain")
            } else {
                Log.e(TAG, "Failed to export related table")
            }
        })
        shareThread!!.start()
    }

    /**
     * Initiates sharing of the related phrases table as a compressed database file.
     * 
     * 
     * This method starts a background thread that exports the related phrases
     * table to a compressed .limedb file and then shares it using the Android share intent.
     */
    fun shareRelatedAsDatabase() {
        shareThread = Thread(Runnable {
            if (progressManager != null) progressManager.show()
            var cacheDir: File? = activity.getExternalCacheDir()
            if (cacheDir == null) {
                cacheDir = activity.getCacheDir()
            }

            val targetFileZip = File(cacheDir, LIME.DB_TABLE_RELATED + ".limedb")

            if (progressManager != null) progressManager.updateProgress(
                activity.getResources().getString(R.string.share_step_initial)
            )
            val exportedFile = setupImController.exportZippedDbRelated(
                targetFileZip,
                Runnable {
                    if (progressManager != null) {
                        progressManager.updateProgress(
                            activity.getResources().getString(R.string.share_step_write)
                        )
                    }
                })

            if (progressManager != null) progressManager.dismiss()
            if (exportedFile != null) {
                shareFile(exportedFile.getAbsolutePath(), "application/zip")
            } else {
                Log.e(TAG, "Failed to export database")
            }
        })
        shareThread!!.start()
    }

    /**
     * Shares a file using Android's share intent.
     * 
     * 
     * This method creates a share intent for the specified file and launches
     * the Android share chooser. The file is shared using FileProvider to ensure
     * proper URI permissions.
     * 
     * 
     * The share intent includes:
     * 
     *  * The file URI with read permission granted
     *  * The file name as extra text
     * 
     * 
     * @param filePath The path to the file to share
     * @param mimeType The MIME type of the file (e.g., "text/plain" or "application/zip")
     */
    fun createShareIntent(filePath: String, mimeType: String?): Intent? {
        try {
            val sharingIntent: Intent = Intent(Intent.ACTION_SEND)
            sharingIntent.setType(mimeType)

            val target = File(filePath)

            val targetUri = FileProvider.getUriForFile(
                activity,
                activity.getApplicationContext().getPackageName() + ".fileprovider",
                target
            )

            sharingIntent.putExtra(Intent.EXTRA_STREAM, targetUri)
            sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            sharingIntent.putExtra(Intent.EXTRA_TEXT, target.getName())

            return Intent.createChooser(sharingIntent, target.getName())
        } catch (e: Exception) {
            Log.e(TAG, "Error creating share intent", e)
            return null
        }
    }

    fun shareFile(filePath: String, mimeType: String?) {
        val intent: Intent? = createShareIntent(filePath, mimeType)
        if (intent != null) {
            activity.startActivity(intent)
        }
    }

    companion object {
        private const val TAG = "ShareManager"
    }
}
