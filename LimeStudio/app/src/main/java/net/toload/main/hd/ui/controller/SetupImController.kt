package net.toload.main.hd.ui.controller

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.HashMap
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.DBServer
import net.toload.main.hd.global.LIME
import net.toload.main.hd.global.LIMEUtilities
import net.toload.main.hd.global.LIMEUtilities.DownloadProgressCallback
import net.toload.main.hd.global.LIMEProgressListener
import net.toload.main.hd.R
import net.toload.main.hd.SearchServer
import net.toload.main.hd.ui.dialog.ImportDialog
import net.toload.main.hd.ui.NavigationManager
import net.toload.main.hd.ui.view.LIMESettingsView

/**
 * Controller responsible for IM setup and import/export operations.
 * 
 * 
 * Handles initialization of IM-related menu items, button state management,
 * import/export, backup/restore flows, and provides view callback wiring for
 * the setup UI.
 */
class SetupImController(context: Context, dbServer: DBServer, searchServer: SearchServer?) :
    BaseController(), ImportDialog.OnImportIMSelectedListener {
    private val context: Context
    private val dbServer: DBServer
    private val searchServer: SearchServer

    private var settingsView: LIMESettingsView? = null
    private var navigationManager: NavigationManager? = null

    private var fileToImport: File? = null

    init {
        this.context = context.getApplicationContext()
        this.dbServer = dbServer
        this.searchServer = searchServer ?: SearchServer(context)
    }

    fun setSettingsView(view: LIMESettingsView?) {
        this.settingsView = view
    }

    fun setNavigationManager(manager: NavigationManager?) {
        this.navigationManager = manager
    }

    /**
     * Sets the file to import (called by IntentHandler or LIMESettings).
     * @param file The file to import
     */
    fun setFileToImport(file: File?) {
        this.fileToImport = file
    }


    /**
     * Callback method called when user selects a IM table for import in ImportDialog.
     * 
     * 
     * This method is called after the user selects which IM table to import the
     * file into. It directly calls importTxtTable with the selected parameters.
     * 
     * @param tableName The IM table selected for import
     * @param restoreUserRecords If true, restores user-learned records from backup table after import
     */
    override fun onImportDialogImSelected(tableName: String?, restoreUserRecords: Boolean) {
        if (fileToImport == null) {
            Log.e(TAG, "No file set for import")
            handleError(settingsView, "No file selected for import", null)
            return
        }
        importTxtTable(fileToImport!!, tableName.orEmpty(), restoreUserRecords)
    }

    fun loadNavigationMenu() {
        val navigationManager = navigationManager ?: return
        try {
            navigationManager.setImConfigFullNameList(
                searchServer.getImConfigList(
                    null,
                    LIME.IM_FULL_NAME
                ).filterNotNull()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh IM navigation list", e)
        }
    }

    fun loadButtonStates(): MutableMap<String?, Boolean?> {
        val buttonStates: MutableMap<String?, Boolean?> = HashMap<String?, Boolean?>()
        // Skip loading if server is not initialized (e.g., in test mode)
        try {
            val imConfigList: MutableList<ImConfig> =
                searchServer.getImConfigList(null, LIME.IM_FULL_NAME).filterNotNull().toMutableList()
            for (imConfig in imConfigList) {
                val imName: String? = imConfig.code
                if (imName != null) {
                    val amount = searchServer.getImConfig(imName, "amount")
                    val isAvailable = amount != null && !amount.isEmpty() && (amount != "0")
                    buttonStates.put(imName, isAvailable)
                }
            }

            try {
                refreshSetupImButtonStates()
            } catch (updateException: Exception) {
                Log.e(TAG, "Failed to update button states", updateException)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load button states", e)
        }
        return buttonStates
    }

    val imConfigList: MutableList<ImConfig>
        get() {
            val result: MutableList<ImConfig> =
                searchServer.getImConfigList(null, LIME.IM_FULL_NAME).filterNotNull().toMutableList()
            return result
        }

    fun onNavigationItemSelected(position: Int) {
        navigationManager?.navigateToFragment(position)
    }


    fun isValidTableName(tableName: String?): Boolean {
        try {
            return searchServer.isValidTableName(tableName)
        } catch (e: Exception) {
            handleError(settingsView, "Table name validation failed", e)
            return false
        }
    }

    fun countRecords(tableName: String?): Int {
        try {
            return searchServer.countRecords(tableName)
        } catch (e: Exception) {
            handleError(settingsView, "Failed to count records", e)
            return 0
        }
    }

    fun clearTable(tableName: String, backupUserRecords: Boolean) {
        if (backupUserRecords) {
            searchServer.backupUserRecords(tableName)
        }
        searchServer.clearTable(tableName)
        refreshSetupImButtonStates()
    }

    /**
     * Performs a complete backup of the database to the specified URI.
     * This includes backing up custom user records and the entire database.
     * 
     * @param uri The URI where the backup should be saved
     */
    @Throws(Exception::class)
    fun performBackup(uri: Uri?) {
        try {
            showProgress(settingsView, "Backing up database...")
            dbServer.backupDatabase(uri)
            hideProgress(settingsView)
        } catch (e: Exception) {
            handleError(settingsView, "Failed to backup database", e)
            throw e
        }
    }

    /**
     * Performs a complete restore of the database from the specified URI.
     * This includes restoring all IM tables, related phrases, and shared preferences.
     * 
     * @param uri The URI of the backup file to restore from
     */
    @Throws(Exception::class)
    fun performRestore(uri: Uri?) {
        try {
            showProgress(settingsView, "Restoring database...")
            dbServer.restoreDatabase(uri)
            hideProgress(settingsView)

            // Refresh the menu and UI after restore
            refreshNavigationMenu()
            refreshSetupImButtonStates()
        } catch (e: Exception) {
            handleError(settingsView, "Failed to restore database", e)
            hideProgress(settingsView)
            throw e
        }
    }

    /**
     * Imports the default related database from raw resources.
     * 
     * 
     * This method imports the default related database file from raw resources.
     * The default related database is always zipped, so this method handles the
     * unzip operation before importing.
     * 
     * 
     * This method:
     * 
     *  * Copies the zipped database file from raw resources to a temporary location
     *  * Delegates to DBServer.importDbRelated() 
     * 
     */
    fun importDbDefaultRelated() {
        try {
            showProgress(
                settingsView,
                context!!.getString(R.string.setup_im_import_related_default)
            )

            val limeDbPath = File(context.getCacheDir(), LIME.DATABASE_NAME)
            // Copy zipped database file from raw resources
            LIMEUtilities.copyRAWFile(
                context.getResources().openRawResource(R.raw.lime),
                limeDbPath
            )
            if (limeDbPath.exists()) {
                dbServer.importDbRelated(limeDbPath)
                limeDbPath.deleteOnExit()
            } else {
                handleError(settingsView, context.getString(R.string.error_import_db), null)
            }
            refreshNavigationMenu()
            hideProgress(settingsView)
        } catch (e: Exception) {
            hideProgress(settingsView)
            handleError(settingsView, context!!.getString(R.string.error_import_db), e)
        }
    }

    /**
     * Imports a compressed related database file (.limedb).
     * 
     * 
     * This method delegates to DBServer.importZippedDbRelated() which handles
     * the unzip operation and database import. File operations are centralized
     * in DBServer to maintain architecture compliance.
     * 
     * @param unit The compressed related database file (.limedb) to import
     */
    fun importZippedDbRelated(unit: File?) {
        try {
            showProgress(settingsView, context!!.getString(R.string.setup_im_import_related))

            dbServer.importZippedDbRelated(unit)

            refreshNavigationMenu()

            hideProgress(settingsView)
            showToast(
                settingsView,
                context.getString(R.string.setup_im_import_complete),
                Toast.LENGTH_LONG
            )
        } catch (e: Exception) {
            hideProgress(settingsView)
            handleError(settingsView, context!!.getString(R.string.error_import_db), e)
        }
    }


    fun importZippedDb(fileToImport: File?, tableName: String, restoreUserRecords: Boolean) {
        if ("related" == tableName) {
            try {
                showProgress(settingsView, context!!.getString(R.string.setup_im_import_related))
                dbServer.importZippedDbRelated(fileToImport)
                searchServer.resetCache()
                showToast(
                    settingsView,
                    context.getString(R.string.setup_im_import_complete),
                    Toast.LENGTH_SHORT
                )
            } catch (e: Exception) {
                handleError(settingsView, context!!.getString(R.string.error_import_db), e)
            } finally {
                hideProgress(settingsView)
            }
        } else {
            if (!isValidTableName(tableName)) {
                hideProgress(settingsView)
                handleError(
                    settingsView,
                    context!!.getString(R.string.error_table_name) + ": " + tableName,
                    null
                )
                return
            }
            try {
                showProgress(
                    settingsView,
                    context!!.getString(R.string.setup_im_dialog_import_confirm_title)
                )
                if (restoreUserRecords && countRecords(tableName) > 0) {
                    try {
                        searchServer.backupUserRecords(tableName)
                    } catch (e: Exception) {
                        handleError(
                            settingsView,
                            context.getString(R.string.error_backup_user_records),
                            e
                        )
                    }
                }
                dbServer.importZippedDb(fileToImport, tableName)
                searchServer.resetCache()
                if (restoreUserRecords) {
                    try {
                        if (searchServer.checkBackupTable(tableName)) {
                            searchServer.restoreUserRecords(tableName)
                        }
                    } catch (e: Exception) {
                        handleError(
                            settingsView,
                            context.getString(R.string.error_backup_user_records),
                            e
                        )
                    }
                }
            } catch (e: Exception) {
                handleError(
                    settingsView,
                    context!!.getString(R.string.error_import_db) + e.message,
                    e
                )
            } finally {
                hideProgress(settingsView)
                showToast(
                    settingsView,
                    context!!.getString(R.string.setup_im_import_complete),
                    Toast.LENGTH_SHORT
                )
                searchServer.resetCache()
                refreshNavigationMenu()
                refreshSetupImButtonStates()
            }
        }
    }

    /**
     * Downloads an IM database from the cloud and imports it.
     * @param onSuccess Optional Runnable posted to the main thread after a successful import.
     */
    /**
     * Downloads an IM database from the cloud and imports it.
     */
    @JvmOverloads
    fun downloadAndImportZippedDb(
        tableName: String, url: String?, restoreLearning: Boolean,
        onSuccess: Runnable? = null
    ) {
        if (context == null) {
            handleError(settingsView, "Context unavailable for download", null)
            return
        }

        if (!this.isNetworkAvailable || url == null || url.isEmpty()) {
            handleError(settingsView, context.getString(R.string.l3_tab_initial_error), null)
            return
        }

        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit(Runnable {
                try {
                    showProgress(settingsView, context.getString(R.string.setup_load_download))

                    val tempfile: File? = LIMEUtilities.downloadRemoteFile(
                        url,
                        null,
                        context.getCacheDir(),
                        object : DownloadProgressCallback {
                            override fun onProgress(percent: Int) {
                                updateProgress(
                                    settingsView,
                                    percent,
                                    context.getString(R.string.setup_load_download)
                                )
                            }
                        },
                        null
                    )

                    val minFileSizeBytes = 100000
                    if (tempfile == null || tempfile.length() < minFileSizeBytes) {
                        hideProgress(settingsView)
                        handleError(settingsView, context.getString(R.string.error_import_db), null)
                        return@Runnable
                    }

                    importZippedDb(tempfile, tableName, restoreLearning)
                    if (onSuccess != null) mainHandler.post(onSuccess)
                } catch (e: Exception) {
                    hideProgress(settingsView)
                    handleError(settingsView, context.getString(R.string.error_import_db), e)
                }
            })
        } finally {
            executor.shutdown()
        }
    }

    fun restoredToDefault() {
        try {
            searchServer.restoredToDefault()
            showToast(settingsView, "Settings reset to defaults", Toast.LENGTH_SHORT)
            refreshNavigationMenu()
        } catch (e: Exception) {
            handleError(settingsView, "Failed to reset settings", e)
        }
    }

    private fun refreshNavigationMenu() {
        mainHandler.post(Runnable { this.loadNavigationMenu() })
    }

    private fun refreshSetupImButtonStates() {
        loadNavigationMenu()
    }


    fun exportTxtTable(tableName: String?, targetFile: File?, onProgress: Runnable?): File? {
        try {
            showProgress(settingsView, context!!.getString(R.string.setup_load_migrate_export))

            val imConfigList = searchServer.getImAllConfigList(tableName)?.filterNotNull()?.toMutableList()
            if (onProgress != null) {
                onProgress.run()
            }

            val resultFile = arrayOf<File?>(null)


            // Use progress listener to get real-time export progress
            dbServer.exportTxtTable(
                tableName,
                targetFile,
                imConfigList,
                object : LIMEProgressListener() {
                    public override fun onProgress(
                        percentageDone: Long,
                        var2: Long,
                        status: String?
                    ) {
                        updateProgress(
                            settingsView,
                            percentageDone.toInt(),
                            if (status != null) status else context.getString(R.string.setup_load_migrate_export)
                        )
                    }

                    public override fun onError(code: Int, source: String?) {
                        hideProgress(settingsView)
                        if (source != null && !source.isEmpty()) settingsView?.onError(source)
                    }

                    public override fun onPostExecute(
                        success: Boolean,
                        status: String?,
                        code: Int
                    ) {
                        if (success) {
                            resultFile[0] = targetFile
                        }
                        hideProgress(settingsView)
                        if (success) showToast(
                            settingsView,
                            context.getString(R.string.setup_load_export_finish),
                            Toast.LENGTH_SHORT
                        )
                    }
                })

            return resultFile[0]
        } catch (e: Exception) {
            handleError(settingsView, context!!.getString(R.string.error_export_table), e)
            hideProgress(settingsView)

            return null
        }
    }

    fun exportZippedDb(tableName: String?, targetFile: File?, onProgress: Runnable?): File? {
        try {
            showProgress(settingsView, context!!.getString(R.string.setup_load_migrate_export))

            if (targetFile != null && targetFile.exists() && !targetFile.delete()) {
                Log.w(TAG, "exportZippedDb: failed to delete existing target file")
            }

            val result = dbServer.exportZippedDb(tableName, targetFile, onProgress)

            hideProgress(settingsView)
            return result
        } catch (e: Exception) {
            hideProgress(settingsView)
            handleError(settingsView, context!!.getString(R.string.error_export_table), e)
            return null
        }
    }

    fun exportTxtTableRelated(targetFile: File?, onProgress: Runnable?): File? {
        try {
            showProgress(settingsView, context!!.getString(R.string.setup_load_migrate_export))

            val imConfigInfo: MutableList<ImConfig?>? =
                searchServer.getImAllConfigList(LIME.DB_TABLE_RELATED)
            val exportImConfigInfo = imConfigInfo?.filterNotNull()?.toMutableList()
            if (onProgress != null) {
                onProgress.run()
            }

            val resultFile = arrayOf<File?>(null)


            // Use progress listener to get real-time export progress
            dbServer.exportTxtTable(
                LIME.DB_TABLE_RELATED,
                targetFile,
                exportImConfigInfo,
                object : LIMEProgressListener() {
                    public override fun onProgress(
                        percentageDone: Long,
                        var2: Long,
                        status: String?
                    ) {
                        updateProgress(
                            settingsView,
                            percentageDone.toInt(),
                            if (status != null) status else "Exporting..."
                        )
                    }

                    public override fun onStatusUpdate(status: String?) {
                        if (status != null && !status.isEmpty()) {
                            updateProgress(settingsView, 0, status)
                        }
                    }

                    public override fun onError(code: Int, source: String?) {
                        hideProgress(settingsView)
                        if (source != null && !source.isEmpty()) settingsView?.onError(source)
                    }

                    public override fun onPostExecute(
                        success: Boolean,
                        status: String?,
                        code: Int
                    ) {
                        if (success) {
                            resultFile[0] = targetFile
                        }

                        hideProgress(settingsView)
                        if (success) showToast(settingsView, "Export complete", Toast.LENGTH_SHORT)
                    }
                })

            return resultFile[0]
        } catch (e: Exception) {
            handleError(settingsView, context!!.getString(R.string.error_export_table), e)
            hideProgress(settingsView)

            return null
        }
    }

    fun exportZippedDbRelated(targetFile: File?, onProgress: Runnable?): File? {
        try {
            showProgress(settingsView, context!!.getString(R.string.setup_load_migrate_export))

            if (targetFile != null && targetFile.exists() && !targetFile.delete()) {
                Log.w(TAG, "exportRelatedZippedDb: failed to delete existing target file")
            }


            val result = dbServer.exportZippedDbRelated(targetFile, onProgress)

            hideProgress(settingsView)

            return result
        } catch (e: Exception) {
            handleError(settingsView, context!!.getString(R.string.error_export_table), e)
            hideProgress(settingsView)
            return null
        }
    }


    /**
     * Imports a text file into an IM table with optional user record restore.
     * @param onSuccess Optional Runnable posted to the main thread after a successful import.
     */
    /**
     * Imports a text file into an IM table with optional user record restore.
     */
    @JvmOverloads
    fun importTxtTable(
        file: File, tableName: String, restoreUserRecords: Boolean,
        onSuccess: Runnable? = null
    ) {
        if (!searchServer.isValidTableName(tableName)) {
            handleError(
                settingsView,
                context!!.getString(R.string.error_table_name) + ": " + tableName,
                null
            )
            return
        }

        try {
            showProgress(settingsView, context!!.getString(R.string.setup_im_import_standard))
            try {
                searchServer.backupUserRecords(tableName)
            } catch (e: Exception) {
                handleError(
                    settingsView,
                    context.getString(R.string.error_backup_user_records),
                    e
                )
            }

            dbServer.importTxtTable(
                file.getAbsolutePath(),
                tableName,
                object : LIMEProgressListener() {
                    public override fun onProgress(
                        percentageDone: Long,
                        var2: Long,
                        status: String?
                    ) {
                        updateProgress(
                            settingsView,
                            percentageDone.toInt(),
                            if (status != null) status else ""
                        )
                    }

                    public override fun onStatusUpdate(status: String?) {
                        if (status != null && !status.isEmpty()) {
                            updateProgress(settingsView, 0, status)
                        }
                    }

                    public override fun onError(code: Int, source: String?) {
                        hideProgress(settingsView)
                        if (source != null && !source.isEmpty()) {
                            handleError(settingsView, source, null)
                        }
                    }

                    public override fun onPostExecute(
                        success: Boolean,
                        status: String?,
                        code: Int
                    ) {
                        if (success) searchServer.resetCache()

                        updateProgress(
                            settingsView,
                            100,
                            context.getString(R.string.setup_im_import_complete)
                        )
                        hideProgress(settingsView)

                        if (restoreUserRecords && success) {
                            if (searchServer.checkBackupTable(tableName)) {
                                showProgress(
                                    settingsView,
                                    context.getString(R.string.setup_im_restore_learning_data)
                                )
                                searchServer.restoreUserRecords(tableName)
                                hideProgress(settingsView)
                            }
                        }
                        refreshSetupImButtonStates()
                        refreshNavigationMenu()
                        if (success && onSuccess != null) mainHandler.post(onSuccess)
                    }
                })
        } catch (e: Exception) {
            handleError(settingsView, context!!.getString(R.string.error_import_db), e)
        }
    }

    private val isNetworkAvailable: Boolean
        get() {
            val connectivityManager: ConnectivityManager? =
                context!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            if (connectivityManager == null) {
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network: Network? = connectivityManager.getActiveNetwork()
                if (network == null) return false
                val capabilities: NetworkCapabilities? =
                    connectivityManager.getNetworkCapabilities(network)
                return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                        )
            } else {
                @Suppress("deprecation") val activeNetworkInfo: NetworkInfo? =
                    connectivityManager.getActiveNetworkInfo()
                if (activeNetworkInfo != null) {
                    @Suppress("deprecation") val isConnected: Boolean =
                        activeNetworkInfo.isConnected()
                    return isConnected
                }
                return false
            }
        }

    companion object {
        private const val TAG = "SetupImController"
    }
}

