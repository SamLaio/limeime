package net.toload.main.hd.ui.view

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.content.FileProvider
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.File
import net.toload.main.hd.R
import net.toload.main.hd.ui.controller.SetupImController
import net.toload.main.hd.ui.LIMESettings
import net.toload.main.hd.ui.view.ScrollableTabHelper.applyToNestedScrollView

class DbManagerFragment : Fragment() {
    private enum class BackupRestoreType {
        BACKUP,
        RESTORE,
        BACKUP_TO_DOWNLOADS
    }

    private var setupImController: SetupImController? = null
    private var activity: Activity? = null

    private var dbStatusCard: MaterialCardView? = null
    private var dbStatusText: TextView? = null

    private var backupLauncher: ActivityResultLauncher<Intent?>? = null
    private var restoreLauncher: ActivityResultLauncher<Intent?>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        backupLauncher = registerForActivityResult<Intent?, ActivityResult?>(
            StartActivityForResult(),
            ActivityResultCallback { result: ActivityResult? ->
                if (result!!.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    val uri = result.getData()!!.getData()
                    if (uri != null) performBackup(uri)
                }
            })

        restoreLauncher = registerForActivityResult<Intent?, ActivityResult?>(
            StartActivityForResult(),
            ActivityResultCallback { result: ActivityResult? ->
                if (result!!.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    val uri = result.getData()!!.getData()
                    if (uri != null) performRestore(uri)
                }
            })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        activity = getActivity()
        if (activity is LIMESettings) {
            setupImController = (activity as LIMESettings).getSetupImController()
        }

        val root = inflater.inflate(R.layout.fragment_db_manager, container, false)
        val scrollView = root.findViewById<NestedScrollView?>(R.id.db_manager_scroll)
        if (scrollView != null) {
            applyToNestedScrollView(activity, scrollView)
        }

        dbStatusCard = root.findViewById<MaterialCardView?>(R.id.dbStatusCard)
        dbStatusText = root.findViewById<TextView?>(R.id.dbStatusText)

        val btnBackup = root.findViewById<MaterialButton>(R.id.btnDbBackup)
        val btnRestore = root.findViewById<MaterialButton>(R.id.btnDbRestore)
        val btnRestoreDefault = root.findViewById<MaterialButton>(R.id.btnDbRestoreDefault)

        btnBackup.setOnClickListener(View.OnClickListener { v: View? -> backupLocalDrive() })
        btnRestore.setOnClickListener(View.OnClickListener { v: View? -> restoreLocalDrive() })
        btnRestoreDefault.setOnClickListener(View.OnClickListener { v: View? -> confirmRestoreDefault() })

        return root
    }

    // -----------------------------------------------------------------------
    // Backup
    // -----------------------------------------------------------------------
    fun backupLocalDrive() {
        if (backupLauncher == null) {
            showToastMessage(getString(R.string.l3_initial_backup_error), Toast.LENGTH_SHORT)
            return
        }
        try {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.setType("application/zip")
            intent.putExtra(Intent.EXTRA_TITLE, "limeBackup.zip")

            val pm = requireActivity().getPackageManager()
            val activities = pm.queryIntentActivities(intent, 0)
            if (intent.resolveActivity(pm) == null || activities == null || activities.isEmpty()) {
                showAlertDialog(BackupRestoreType.BACKUP_TO_DOWNLOADS)
                return
            }
            showAlertDialog(BackupRestoreType.BACKUP)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking backup options", e)
            showToastMessage(getString(R.string.l3_initial_backup_error), Toast.LENGTH_SHORT)
        }
    }

    private fun launchBackupFilePicker() {
        try {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.setType("application/zip")
            intent.putExtra(Intent.EXTRA_TITLE, "limeBackup.zip")

            val pm = requireActivity().getPackageManager()
            val activities = pm.queryIntentActivities(intent, 0)
            if (intent.resolveActivity(pm) == null || activities == null || activities.isEmpty()) {
                saveBackupToDownloads()
                return
            }
            backupLauncher!!.launch(Intent.createChooser(intent, "Save Backup"))
        } catch (e: Exception) {
            Log.e(TAG, "Error launching backup file picker", e)
            saveBackupToDownloads()
        }
    }

    private fun saveBackupToDownloads() {
        val act: Activity? = activity
        if (act == null) return
        val resolver = act.getContentResolver()
        val errorMsg = getString(R.string.l3_initial_backup_error)
        val pkgName = act.getApplicationContext().getPackageName()

        Thread(Runnable {
            try {
                val backupUri: Uri?
                val fileName = "limeBackup.zip"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues()
                    values.put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    values.put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    backupUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (backupUri == null) {
                        runOnUi(Runnable { showToastMessage(errorMsg, Toast.LENGTH_SHORT) })
                        return@Runnable
                    }
                } else {
                    @Suppress("deprecation") val downloadsDir =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (downloadsDir == null) {
                        runOnUi(Runnable { showToastMessage(errorMsg, Toast.LENGTH_SHORT) })
                        return@Runnable
                    }
                    if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                        runOnUi(Runnable { showToastMessage(errorMsg, Toast.LENGTH_SHORT) })
                        return@Runnable
                    }
                    var backupFile = File(downloadsDir, fileName)
                    var counter = 1
                    while (backupFile.exists()) {
                        backupFile = File(downloadsDir, "limeBackup(" + counter + ").zip")
                        counter++
                    }
                    backupUri =
                        FileProvider.getUriForFile(act, pkgName + ".fileprovider", backupFile)
                }
                val finalUri = backupUri
                runOnUi(Runnable { performBackup(finalUri) })
            } catch (e: Exception) {
                Log.e(TAG, "Error saving backup to Downloads", e)
                runOnUi(Runnable { showToastMessage(errorMsg, Toast.LENGTH_SHORT) })
            }
        }).start()
    }

    private fun performBackup(uri: Uri?) {
        try {
            if (setupImController != null) setupImController!!.performBackup(uri)
            runOnUi(Runnable { setStatus(getString(R.string.db_status_backup_ok)) })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to backup database", e)
            showToastMessage(getString(R.string.l3_initial_backup_error), Toast.LENGTH_LONG)
            runOnUi(Runnable {
                setStatus(
                    getString(
                        R.string.db_status_backup_fail,
                        if (e.message != null) e.message else "unknown"
                    )
                )
            })
        }
    }

    // -----------------------------------------------------------------------
    // Restore
    // -----------------------------------------------------------------------
    fun restoreLocalDrive() {
        if (restoreLauncher == null) {
            showToastMessage(getString(R.string.l3_initial_restore_error), Toast.LENGTH_SHORT)
            return
        }
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.setType("application/zip")
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            val chooserIntent = Intent.createChooser(intent, "Select Backup")
            if (chooserIntent.resolveActivity(requireActivity().getPackageManager()) == null) {
                showToastMessage(getString(R.string.l3_initial_restore_error), Toast.LENGTH_SHORT)
                return
            }
            showAlertDialog(BackupRestoreType.RESTORE)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking restore options", e)
            showToastMessage(getString(R.string.l3_initial_restore_error), Toast.LENGTH_SHORT)
        }
    }

    private fun launchRestoreFilePicker() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.setType("application/zip")
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            val chooserIntent = Intent.createChooser(intent, "Select Backup")
            if (chooserIntent.resolveActivity(requireActivity().getPackageManager()) == null) {
                showToastMessage(getString(R.string.l3_initial_restore_error), Toast.LENGTH_SHORT)
                return
            }
            restoreLauncher!!.launch(chooserIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching restore file picker", e)
            showToastMessage(getString(R.string.l3_initial_restore_error), Toast.LENGTH_SHORT)
        }
    }

    private fun performRestore(uri: Uri?) {
        if (setupImController == null) {
            showToastMessage(getString(R.string.l3_initial_restore_error), Toast.LENGTH_LONG)
            runOnUi(Runnable {
                setStatus(
                    getString(
                        R.string.db_status_restore_fail,
                        "controller unavailable"
                    )
                )
            })
            return
        }
        try {
            setupImController!!.performRestore(uri)
            // Only reached when no exception propagated from the controller/DBServer chain.
            runOnUi(Runnable { setStatus(getString(R.string.db_status_restore_ok)) })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore database", e)
            showToastMessage(getString(R.string.l3_initial_restore_error), Toast.LENGTH_LONG)
            runOnUi(Runnable {
                setStatus(
                    getString(
                        R.string.db_status_restore_fail,
                        if (e.message != null) e.message else "unknown"
                    )
                )
            })
        }
    }

    // -----------------------------------------------------------------------
    // Restore default
    // -----------------------------------------------------------------------
    private fun confirmRestoreDefault() {
        AlertDialog.Builder(requireActivity())
            .setMessage(R.string.l3_restore_default_confirm)
            .setCancelable(false)
            .setPositiveButton(
                R.string.dialog_confirm,
                DialogInterface.OnClickListener { d: DialogInterface?, w: Int ->
                    if (setupImController != null) {
                        setupImController!!.restoredToDefault()
                        setStatus(getString(R.string.db_status_default_ok))
                    }
                })
            .setNegativeButton(
                R.string.dialog_cancel,
                DialogInterface.OnClickListener { d: DialogInterface?, w: Int -> })
            .show()
    }

    // -----------------------------------------------------------------------
    // Shared confirm dialog (backup / restore)
    // -----------------------------------------------------------------------
    private fun showAlertDialog(type: BackupRestoreType) {
        val messageResId: Int
        val onConfirm: Runnable?
        when (type) {
            BackupRestoreType.RESTORE -> {
                messageResId = R.string.l3_initial_restore_confirm
                onConfirm = Runnable { this.launchRestoreFilePicker() }
            }

            BackupRestoreType.BACKUP -> {
                messageResId = R.string.l3_initial_backup_confirm
                onConfirm = Runnable { this.launchBackupFilePicker() }
            }

            BackupRestoreType.BACKUP_TO_DOWNLOADS -> {
                messageResId = R.string.l3_initial_backup_confirm_downloads
                onConfirm = Runnable { this.saveBackupToDownloads() }
            }

            else -> return
        }
        AlertDialog.Builder(requireActivity())
            .setMessage(getString(messageResId))
            .setCancelable(false)
            .setPositiveButton(
                R.string.dialog_confirm,
                DialogInterface.OnClickListener { d: DialogInterface?, w: Int -> onConfirm.run() })
            .setNegativeButton(
                R.string.dialog_cancel,
                DialogInterface.OnClickListener { d: DialogInterface?, w: Int -> })
            .show()
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------
    override fun onDestroyView() {
        super.onDestroyView()
        activity = null
        setupImController = null
    }

    private fun setStatus(message: String?) {
        if (dbStatusCard != null) dbStatusCard!!.setVisibility(View.VISIBLE)
        if (dbStatusText != null) dbStatusText!!.setText(message)
    }

    private fun showToastMessage(msg: String?, length: Int) {
        runOnUi(Runnable {
            val ctx = getContext()
            if (ctx != null) Toast.makeText(ctx, msg, length).show()
        })
    }

    private fun runOnUi(r: Runnable?) {
        if (activity == null || r == null) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run()
        } else {
            requireActivity().runOnUiThread(r)
        }
    }

    companion object {
        private const val TAG = "DbManagerFragment"

        @JvmStatic
        fun newInstance(): DbManagerFragment {
            return DbManagerFragment()
        }
    }
}
