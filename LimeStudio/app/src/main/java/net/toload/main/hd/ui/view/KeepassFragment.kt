@file:Suppress("DEPRECATION")

package net.toload.main.hd.ui.view

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import net.toload.main.hd.R
import net.toload.main.hd.keepass.KeepassAutofillLock
import net.toload.main.hd.keepass.KeepassEntry
import net.toload.main.hd.keepass.KeepassEntryInput
import net.toload.main.hd.keepass.KeepassRepository
import net.toload.main.hd.keepass.KeepassStorageClient
import net.toload.main.hd.ui.view.ScrollableTabHelper.applyToNestedScrollView

class KeepassFragment : Fragment() {
    private lateinit var enableSwitch: SwitchCompat
    private lateinit var btnDatabaseSelector: MaterialButton
    private lateinit var textDatabasePath: TextView
    private lateinit var btnKeySelector: MaterialButton
    private lateinit var textKeyPath: TextView
    private lateinit var inputDatabasePassword: TextInputEditText
    private lateinit var btnSyncDatabase: MaterialButton
    private lateinit var btnBrowseDatabase: MaterialButton
    private lateinit var btnAddPassword: MaterialButton
    private lateinit var switchFingerprint: SwitchCompat
    private lateinit var inputAutoLock: TextInputEditText
    private lateinit var sectionDatabaseSettings: ViewGroup
    private lateinit var sectionSync: ViewGroup
    private lateinit var sectionSecurity: ViewGroup

    private lateinit var databasePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var keyFilePickerLauncher: ActivityResultLauncher<Intent>

    private val legacyPrefsName = "keepass_settings"
    private val keyEnabled = "enabled"
    private val keyDatabasePath = "database_path"
    private val keyDatabaseKey = "database_key"
    private val keyDatabasePassword = "database_password"
    private val keyFingerprint = "fingerprint_enabled"
    private val keyAutoLockMinutes = "auto_lock_minutes"
    private val keyExtraPasswords = "extra_passwords"
    private val keyRecentDatabasePaths = "recent_database_paths"
    private val keyWebDavUrl = "webdav_url"
    private val keyWebDavUsername = "webdav_username"
    private val keyWebDavPassword = "webdav_password"
    private val keyFtpUrl = "ftp_url"
    private val keyFtpHost = "ftp_host"
    private val keyFtpPort = "ftp_port"
    private val keyFtpInitialDir = "ftp_initial_dir"
    private val keyFtpUsername = "ftp_username"
    private val keyFtpPassword = "ftp_password"
    private val minimumAutoLockMinutes = 5
    private val recentDatabaseSeparator = "\n"
    private var selectedDatabasePath: String = ""
    private var selectedKeyPath: String = ""
    private var updatingFingerprintSwitch = false
    private var syncPromptShowing = false
    private var lastPromptedPendingSyncKey: String? = null
    private val storageOptions = listOf(
        StorageOption(R.string.keepass_storage_local_file, android.R.drawable.sym_def_app_icon, StorageKind.SystemPicker),
        StorageOption(R.string.keepass_storage_webdav, android.R.drawable.ic_menu_upload, StorageKind.WebDav),
        StorageOption(R.string.keepass_storage_ftp, android.R.drawable.ic_menu_upload, StorageKind.Ftp),
    )

    private val migrationStringKeys = mapOf(
        "database_path" to keyDatabasePath,
        "db" to keyDatabasePath,
        "database_key" to keyDatabaseKey,
        "keyfile" to keyDatabaseKey,
        "database_password" to keyDatabasePassword,
        "master_pwd" to keyDatabasePassword,
        "master_pwd_key" to keyDatabasePassword,
    )
    private val migrationIntKeys = mapOf(
        "auto_lock_minutes" to keyAutoLockMinutes,
        "app_timeout_key" to keyAutoLockMinutes,
        "clipboard_timeout_key" to keyAutoLockMinutes,
    )
    private val migrationBoolKeys = listOf(keyEnabled, keyFingerprint)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        migrateLegacyPreferences()

        databasePickerLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
                ActivityResultCallback { result: ActivityResult ->
                    if (result.resultCode != Activity.RESULT_OK) {
                        return@ActivityResultCallback
                    }
                    val uri = result.data?.data ?: return@ActivityResultCallback
                    persistUriPermission(result.data, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    setDatabasePath(uri.toString())
                    updateSectionStates()
                },
            )
        keyFilePickerLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
                ActivityResultCallback { result: ActivityResult ->
                    if (result.resultCode != Activity.RESULT_OK) {
                        return@ActivityResultCallback
                    }
                    val uri = result.data?.data ?: return@ActivityResultCallback
                    persistUriPermission(result.data, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setKeyPath(uri.toString())
                    updateSectionStates()
                },
            )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_keepass, container, false)
        val scrollView = root.findViewById<NestedScrollView>(R.id.keepass_scroll)
        if (scrollView != null) {
            applyToNestedScrollView(requireActivity(), scrollView)
        }

        enableSwitch = root.findViewById(R.id.keepass_enable_switch)
        btnDatabaseSelector = root.findViewById(R.id.btn_keepass_database_selector)
        textDatabasePath = root.findViewById(R.id.text_keepass_database_path)
        btnKeySelector = root.findViewById(R.id.btn_keepass_key_selector)
        textKeyPath = root.findViewById(R.id.text_keepass_key_path)
        inputDatabasePassword = root.findViewById(R.id.input_keepass_password)
        btnSyncDatabase = root.findViewById(R.id.btn_keepass_sync_db)
        btnBrowseDatabase = root.findViewById(R.id.btn_keepass_browse_db)
        btnAddPassword = root.findViewById(R.id.btn_keepass_add_password)
        switchFingerprint = root.findViewById(R.id.switch_keepass_fingerprint)
        inputAutoLock = root.findViewById(R.id.input_keepass_auto_lock)
        sectionDatabaseSettings = root.findViewById(R.id.section_database_settings)
        sectionSync = root.findViewById(R.id.section_database_sync)
        sectionSecurity = root.findViewById(R.id.section_fingerprint_lock)

        loadFromPreference()
        setViewListeners()
        updateSectionStates()
        return root
    }

    override fun onResume() {
        super.onResume()
        updateSectionStates()
        promptSyncIfDatabaseChanged()
    }

    private fun getPrefs(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    private fun migrateLegacyPreferences() {
        val legacy = requireContext().getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
        val current = getPrefs()
        val editor = current.edit()
        var changed = false

        migrationStringKeys.forEach { (legacyKey, currentKey) ->
            if (!current.contains(currentKey)) {
                val legacyValue = legacy.getString(legacyKey, null)
                if (!legacyValue.isNullOrBlank()) {
                    editor.putString(currentKey, legacyValue)
                    changed = true
                }
            }
        }

        migrationIntKeys.forEach { (legacyKey, currentKey) ->
            if (!current.contains(currentKey)) {
                val legacyValue = legacy.getInt(legacyKey, -1)
                val migratedValue =
                    when {
                        legacyKey == "app_timeout_key" && legacyValue > 60 -> legacyValue / 60_000
                        legacyKey == "clipboard_timeout_key" && legacyValue > 60 -> legacyValue / 60_000
                        else -> legacyValue
                    }

                if (migratedValue >= minimumAutoLockMinutes) {
                    editor.putInt(currentKey, migratedValue)
                    changed = true
                }
            }
        }

        migrationBoolKeys.forEach { key ->
            if (!current.contains(key) && legacy.contains(key)) {
                editor.putBoolean(key, legacy.getBoolean(key, false))
                changed = true
            }
        }

        if (!current.contains(keyExtraPasswords) && legacy.contains(keyExtraPasswords)) {
            val legacyExtraPasswords = legacy.getStringSet(keyExtraPasswords, null)
            if (legacyExtraPasswords != null && legacyExtraPasswords.isNotEmpty()) {
                editor.putStringSet(keyExtraPasswords, HashSet(legacyExtraPasswords))
                changed = true
            }
        }

        if (changed) {
            editor.apply()
        }
    }

    private fun loadFromPreference() {
        val prefs = getPrefs()
        enableSwitch.isChecked = prefs.getBoolean(keyEnabled, false)
        selectedDatabasePath = prefs.getString(keyDatabasePath, "").orEmpty()
        updateDatabasePathLabel()
        selectedKeyPath = prefs.getString(keyDatabaseKey, "").orEmpty()
        updateKeyPathLabel()
        inputDatabasePassword.setText(prefs.getString(keyDatabasePassword, ""))
        switchFingerprint.isChecked = prefs.getBoolean(keyFingerprint, false)
        val timeoutMinutes = prefs.getInt(keyAutoLockMinutes, minimumAutoLockMinutes)
        inputAutoLock.setText(timeoutMinutes.toString())
    }

    private fun setViewListeners() {
        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            getPrefs().edit().putBoolean(keyEnabled, isChecked).apply()
            updateSectionStates()
            if (isChecked) {
                requestSystemAutofillServiceIfNeeded()
            }
        }
        switchFingerprint.setOnCheckedChangeListener { _, isChecked ->
            if (updatingFingerprintSwitch) {
                return@setOnCheckedChangeListener
            }
            if (!isChecked) {
                getPrefs().edit().putBoolean(keyFingerprint, false).apply()
                return@setOnCheckedChangeListener
            }
            if (!canUseSystemFingerprint()) {
                setFingerprintSwitchChecked(false)
                Toast.makeText(requireContext(), R.string.keepass_fingerprint_unavailable, Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            setFingerprintSwitchChecked(false)
            showSystemFingerprintPrompt()
        }

        btnDatabaseSelector.setOnClickListener {
            showDatabaseStartDialog()
        }
        btnKeySelector.setOnClickListener {
            launchKeyFilePicker()
        }

        val databaseTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                saveDatabaseFields()
            }
        }
        inputDatabasePassword.addTextChangedListener(databaseTextWatcher)

        btnSyncDatabase.setOnClickListener {
            synchronizeDatabase()
        }

        btnBrowseDatabase.setOnClickListener {
            browseDatabase()
        }

        btnAddPassword.setOnClickListener {
            showAddPasswordDialog()
        }

        inputAutoLock.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                normalizeAndSaveAutoLockMinutes()
            }
        }
        inputAutoLock.setOnEditorActionListener { _, _, _ ->
            normalizeAndSaveAutoLockMinutes()
            false
        }
    }

    private fun saveDatabaseFields() {
        val password = inputDatabasePassword.text?.toString() ?: ""
        getPrefs().edit()
            .putBoolean(keyEnabled, enableSwitch.isChecked)
            .putString(keyDatabasePath, selectedDatabasePath)
            .putString(keyDatabaseKey, selectedKeyPath)
            .putString(keyDatabasePassword, password)
            .apply()
        updateSectionStates()
    }

    private fun setKeyPath(path: String) {
        selectedKeyPath = path.trim()
        updateKeyPathLabel()
        savePrefs { putString(keyDatabaseKey, selectedKeyPath) }
    }

    private fun requestSystemAutofillServiceIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || isLimeAutofillServiceSelected()) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && openCredentialProviderSettings()) {
            return
        }
        val context = requireContext()
        val requestIntent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        try {
            startActivity(requestIntent)
        } catch (e: ActivityNotFoundException) {
            openFallbackAutofillSettings()
        }
    }

    private fun openCredentialProviderSettings(): Boolean {
        return try {
            val credentialManagerClass = Class.forName("androidx.credentials.CredentialManager")
            val createMethod = credentialManagerClass.getMethod("create", Context::class.java)
            val credentialManager = createMethod.invoke(null, requireContext())
            val pendingIntent = credentialManagerClass
                .getMethod("createSettingsPendingIntent")
                .invoke(credentialManager) as android.app.PendingIntent
            pendingIntent.send()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isLimeAutofillServiceSelected(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }
        val enabledService = Settings.Secure.getString(
            requireContext().contentResolver,
            secureSettingAutofillService,
        ).orEmpty()
        return enabledService.contains(requireContext().packageName)
    }

    private fun openFallbackAutofillSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
            Toast.makeText(requireContext(), R.string.keepass_autofill_settings_hint, Toast.LENGTH_LONG).show()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.keepass_autofill_settings_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateKeyPathLabel() {
        val path = selectedKeyPath.ifBlank {
            getString(R.string.keepass_key_not_selected)
        }
        textKeyPath.text = path
    }

    private fun launchKeyFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        keyFilePickerLauncher.launch(intent)
    }

    private fun setDatabasePath(path: String) {
        val newPath = path.trim()
        val databaseChanged = newPath != selectedDatabasePath
        if (databaseChanged) {
            clearDatabaseCredentials()
        }
        selectedDatabasePath = newPath
        updateDatabasePathLabel()
        addRecentDatabasePath(selectedDatabasePath)
        savePrefs { putString(keyDatabasePath, selectedDatabasePath) }
    }

    private fun clearDatabaseCredentials() {
        selectedKeyPath = ""
        updateKeyPathLabel()
        inputDatabasePassword.setText("")
        KeepassAutofillLock.lock(requireContext())
        KeepassRepository.clearEntryCache(requireContext())
        savePrefs {
            remove(keyDatabaseKey)
            remove(keyDatabasePassword)
        }
    }

    private fun updateDatabasePathLabel() {
        val path = selectedDatabasePath.ifBlank {
            getString(R.string.keepass_database_not_selected)
        }
        textDatabasePath.text = path
    }

    private fun showDatabaseStartDialog() {
        val context = requireContext()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }

        ImageView(context).apply {
            setImageResource(R.drawable.logo)
            adjustViewBounds = true
            alpha = 0.9f
            contentDescription = null
            content.addView(
                this,
                LinearLayout.LayoutParams(dp(96), dp(96)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(10)
                },
            )
        }

        TextView(context).apply {
            text = getString(R.string.title_keepass)
            gravity = Gravity.CENTER
            textSize = 28f
            content.addView(
                this,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = dp(18)
                },
            )
        }

        val dialog = AlertDialog.Builder(context)
            .setView(ScrollView(context).apply { addView(content) })
            .create()

        content.addView(
            createActionButton(R.string.keepass_open_file, android.R.drawable.ic_menu_upload) {
                dialog.dismiss()
                showStorageTypeDialog(StorageAction.OpenExisting)
            },
        )
        content.addView(
            createActionButton(R.string.keepass_create_database, android.R.drawable.ic_menu_add) {
                dialog.dismiss()
                launchSystemFilePicker(StorageAction.CreateNew)
            },
        )

        TextView(context).apply {
            text = getString(R.string.keepass_recent_database_title)
            textSize = 18f
            setPadding(0, dp(18), 0, dp(6))
            content.addView(this)
        }

        val recents = getRecentDatabasePaths()
        if (recents.isEmpty()) {
            TextView(context).apply {
                text = getString(R.string.keepass_recent_database_empty)
                setTextColor(0xFF777777.toInt())
                content.addView(this)
            }
        } else {
            recents.forEach { path ->
                content.addView(createRecentDatabaseRow(path) {
                    setDatabasePath(path)
                    dialog.dismiss()
                })
            }
        }

        dialog.show()
    }

    private fun showStorageTypeDialog(action: StorageAction) {
        val context = requireContext()
        val grid = GridLayout(context).apply {
            columnCount = 3
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.keepass_storage_type_title)
            .setView(ScrollView(context).apply { addView(grid) })
            .create()

        storageOptions.forEach { option ->
            val item = createStorageOptionView(option) {
                dialog.dismiss()
                when (option.kind) {
                    StorageKind.SystemPicker -> launchSystemFilePicker(action)
                    StorageKind.WebDav ->
                        showWebDavLoginDialog(
                            titleRes = R.string.keepass_webdav_login_title,
                            urlHintRes = R.string.keepass_webdav_url_hint,
                            usernameHintRes = R.string.keepass_webdav_username,
                            passwordHintRes = R.string.keepass_webdav_password,
                            urlRequiredRes = R.string.keepass_webdav_url_required,
                        )
                    StorageKind.Ftp ->
                        showFtpLoginDialog()
                }
            }
            grid.addView(item)
        }

        dialog.show()
    }

    private fun showWebDavLoginDialog(
        titleRes: Int,
        urlHintRes: Int,
        usernameHintRes: Int,
        passwordHintRes: Int,
        urlRequiredRes: Int,
    ) {
        val context = requireContext()
        val prefs = getPrefs()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val urlInput = createDialogInput(
            hint = getString(urlHintRes),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            value = prefs.getString(keyWebDavUrl, "").orEmpty(),
        )
        val usernameInput = createDialogInput(
            hint = getString(usernameHintRes),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL,
            value = prefs.getString(keyWebDavUsername, "").orEmpty(),
        )
        val passwordInput = createDialogInput(
            hint = getString(passwordHintRes),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            value = prefs.getString(keyWebDavPassword, "").orEmpty(),
        )
        content.addView(urlInput)
        content.addView(usernameInput)
        content.addView(passwordInput)

        AlertDialog.Builder(context)
            .setTitle(titleRes)
            .setView(content)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val url = urlInput.text?.toString()?.trim().orEmpty()
                val username = usernameInput.text?.toString()?.trim().orEmpty()
                val password = passwordInput.text?.toString().orEmpty()
                if (url.isBlank()) {
                    Toast.makeText(context, urlRequiredRes, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val normalizedUrl = normalizeWebDavUrl(url)
                savePrefs {
                    putString(keyWebDavUrl, normalizedUrl)
                    putString(keyWebDavUsername, username)
                    putString(keyWebDavPassword, password)
                }
                selectRemoteDatabasePath(normalizedUrl)
            }
            .show()
    }

    private fun showFtpLoginDialog() {
        val context = requireContext()
        val prefs = getPrefs()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val hostInput = createDialogInput(
            hint = getString(R.string.keepass_ftp_host),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            value = prefs.getString(keyFtpHost, "").orEmpty(),
        )
        val portInput = createDialogInput(
            hint = getString(R.string.keepass_ftp_port),
            inputType = InputType.TYPE_CLASS_NUMBER,
            value = prefs.getString(keyFtpPort, "").orEmpty(),
        )
        val usernameInput = createDialogInput(
            hint = getString(R.string.keepass_ftp_username),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL,
            value = prefs.getString(keyFtpUsername, "").orEmpty(),
        )
        val passwordInput = createDialogInput(
            hint = getString(R.string.keepass_ftp_password),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            value = prefs.getString(keyFtpPassword, "").orEmpty(),
        )
        val initialDirInput = createDialogInput(
            hint = getString(R.string.keepass_ftp_initial_dir),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            value = prefs.getString(keyFtpInitialDir, "/").orEmpty(),
        )
        content.addView(hostInput)
        content.addView(portInput)
        content.addView(usernameInput)
        content.addView(passwordInput)
        content.addView(initialDirInput)

        AlertDialog.Builder(context)
            .setTitle(R.string.keepass_ftp_login_title)
            .setView(content)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val host = hostInput.text?.toString()?.trim().orEmpty()
                if (host.isBlank()) {
                    Toast.makeText(context, R.string.keepass_ftp_url_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val port = portInput.text?.toString()?.trim().orEmpty()
                val username = usernameInput.text?.toString()?.trim().orEmpty()
                val password = passwordInput.text?.toString().orEmpty()
                val initialDir = initialDirInput.text?.toString()?.trim().orEmpty().ifBlank { "/" }
                val ftpPath = buildFtpPath(host, port, initialDir)
                savePrefs {
                    putString(keyFtpUrl, ftpPath)
                    putString(keyFtpHost, stripScheme(host))
                    putString(keyFtpPort, port)
                    putString(keyFtpInitialDir, initialDir)
                    putString(keyFtpUsername, username)
                    putString(keyFtpPassword, password)
                }
                selectRemoteDatabasePath(ftpPath)
            }
            .show()
    }

    private fun selectRemoteDatabasePath(path: String) {
        if (looksLikeFolder(path)) {
            showRemoteFilenameDialog(path)
        } else {
            setDatabasePath(path)
        }
    }

    private fun showRemoteFilenameDialog(basePath: String) {
        val context = requireContext()
        val filenameInput = createDialogInput(
            hint = getString(R.string.keepass_remote_filename_hint),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            value = "",
        )
        AlertDialog.Builder(context)
            .setTitle(R.string.keepass_remote_filename_title)
            .setView(filenameInput)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val filename = filenameInput.text?.toString()?.trim().orEmpty()
                if (filename.isBlank()) {
                    Toast.makeText(context, R.string.keepass_remote_filename_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                setDatabasePath(joinRemotePath(basePath, filename))
            }
            .show()
    }

    private fun browseDatabase() {
        if (selectedDatabasePath.isBlank()) {
            Toast.makeText(requireContext(), R.string.keepass_database_not_selected, Toast.LENGTH_SHORT).show()
            return
        }
        saveDatabaseFields()
        val repository = createKeepassRepository()
        Toast.makeText(requireContext(), R.string.keepass_opening_database, Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val entries = repository.openEntries()
                runOnUiThreadIfAdded {
                    showDatabaseEntriesDialog(entries)
                }
            } catch (e: Exception) {
                showKeepassError(R.string.keepass_open_database_failed, e)
            }
        }.start()
    }

    private fun synchronizeDatabase() {
        if (selectedDatabasePath.isBlank()) {
            Toast.makeText(requireContext(), R.string.keepass_database_not_selected, Toast.LENGTH_SHORT).show()
            return
        }
        saveDatabaseFields()
        val repository = createKeepassRepository()
        Toast.makeText(requireContext(), R.string.keepass_opening_database, Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val result = repository.synchronizeDatabase()
                runOnUiThreadIfAdded {
                    lastPromptedPendingSyncKey = null
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                showKeepassError(R.string.keepass_sync_failed, e)
            }
        }.start()
    }

    private fun promptSyncIfDatabaseChanged() {
        if (!isAdded || syncPromptShowing || selectedDatabasePath.isBlank() || !isRemotePath(selectedDatabasePath)) {
            return
        }
        saveDatabaseFields()
        val repository = createKeepassRepository()
        Thread {
            val pendingKey = runCatching { repository.pendingLocalChangeKey() }.getOrNull()
            if (pendingKey.isNullOrBlank() || pendingKey == lastPromptedPendingSyncKey) {
                return@Thread
            }
            runOnUiThreadIfAdded {
                if (syncPromptShowing || pendingKey == lastPromptedPendingSyncKey) {
                    return@runOnUiThreadIfAdded
                }
                syncPromptShowing = true
                lastPromptedPendingSyncKey = pendingKey
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.keepass_sync_prompt_title)
                    .setMessage(R.string.keepass_sync_prompt_message)
                    .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                        syncPromptShowing = false
                        synchronizeDatabase()
                    }
                    .setNegativeButton(R.string.dialog_cancel) { _, _ ->
                        syncPromptShowing = false
                    }
                    .setOnCancelListener {
                        syncPromptShowing = false
                    }
                    .show()
            }
        }.start()
    }

    private fun showDatabaseEntriesDialog(entries: List<KeepassEntry>) {
        val context = requireContext()
        val visibleEntries = entries.toMutableList()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        TextView(context).apply {
            text = selectedDatabasePath
            maxLines = 2
            textSize = 13f
            setTextColor(0xFF777777.toInt())
            setPadding(0, 0, 0, dp(10))
            content.addView(this)
        }

        val searchInput = createDialogInput(
            hint = getString(R.string.keepass_search_hint),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL,
            value = "",
        )
        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(searchInput)
        content.addView(listContainer)

        fun refreshEntries() {
            renderDatabaseEntries(
                container = listContainer,
                entries = visibleEntries,
                query = searchInput.text?.toString().orEmpty(),
                onDelete = { entry ->
                    confirmDeleteEntry(entry) {
                        visibleEntries.removeAll { item -> item.id == entry.id }
                        refreshEntries()
                    }
                },
            )
        }

        refreshEntries()
        searchInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    refreshEntries()
                }
                override fun afterTextChanged(s: Editable?) {}
            }
        )

        AlertDialog.Builder(context)
            .setTitle(R.string.keepass_browse_database_title)
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton(R.string.dialog_confirm, null)
            .show()
    }

    private fun renderDatabaseEntries(
        container: LinearLayout,
        entries: List<KeepassEntry>,
        query: String,
        onDelete: (KeepassEntry) -> Unit,
    ) {
        container.removeAllViews()
        val filteredEntries = entries.filter { entry -> entry.matchesQuery(query) }
        if (filteredEntries.isEmpty()) {
            TextView(requireContext()).apply {
                text = getString(R.string.keepass_browse_database_empty)
                setTextColor(0xFF777777.toInt())
                setPadding(0, dp(8), 0, dp(8))
                container.addView(this)
            }
            return
        }
        filteredEntries.forEach { entry ->
            container.addView(createEntryRow(entry, onDelete))
        }
    }

    private fun confirmDeleteEntry(entry: KeepassEntry, onDeleted: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.keepass_delete_entry_title)
            .setMessage(getString(R.string.keepass_delete_entry_message, entry.title.ifBlank { getString(R.string.keepass_entry_default_title) }))
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                deleteEntryFromDatabase(entry) {
                    onDeleted()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun deleteEntryFromDatabase(entry: KeepassEntry, onDeleted: () -> Unit) {
        val repository = createKeepassRepository()
        Toast.makeText(requireContext(), R.string.keepass_deleting_entry, Toast.LENGTH_SHORT).show()
        Thread {
            try {
                repository.deleteEntry(entry.id)
                runOnUiThreadIfAdded {
                    Toast.makeText(requireContext(), R.string.keepass_entry_deleted, Toast.LENGTH_SHORT).show()
                    onDeleted()
                    promptSyncIfDatabaseChanged()
                }
            } catch (e: Exception) {
                showKeepassError(R.string.keepass_delete_entry_failed, e)
            }
        }.start()
    }

    private fun KeepassEntry.matchesQuery(query: String): Boolean {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return true
        }
        return (listOf(title, username, url, notes) + additionalUrls + extraSearchValues)
            .any { value -> value.contains(normalizedQuery, ignoreCase = true) }
    }

    private fun createEntryRow(
        entry: KeepassEntry,
        onDelete: (KeepassEntry) -> Unit,
    ): View {
        val context = requireContext()
        val title = entry.title.ifBlank { getString(R.string.keepass_entry_default_title) }
        val username = entry.username
        val url = entry.url
        val notes = entry.notes
        val summary = listOf(
            username.takeIf { it.isNotBlank() }?.let { getString(R.string.keepass_entry_username_display, it) },
            url.takeIf { it.isNotBlank() },
            notes.takeIf { it.isNotBlank() },
        ).filterNotNull().joinToString("\n")

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { showEntryContentMenu(entry) }
            setOnLongClickListener {
                onDelete(entry)
                true
            }

            TextView(context).apply {
                text = title
                textSize = 17f
                setTextColor(0xFF2196F3.toInt())
                contentDescription = title
                addView(this)
            }
            if (summary.isNotBlank()) {
                TextView(context).apply {
                    text = summary
                    textSize = 14f
                    setTextColor(0xFF777777.toInt())
                    setPadding(0, dp(4), 0, 0)
                    addView(this)
                }
            }
        }
    }

    private fun showEntryContentMenu(entry: KeepassEntry) {
        val unlockedEntry = createKeepassRepository().unlockEntry(entry)
        val fields = unlockedEntry.copyableFields()
        if (fields.isEmpty()) {
            Toast.makeText(requireContext(), R.string.keepass_entry_no_content, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = fields.map { (label, value) -> "$label：$value" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(unlockedEntry.title.ifBlank { getString(R.string.keepass_entry_default_title) })
            .setItems(labels) { _, index ->
                val (label, value) = fields[index]
                copyKeepassField(label, value)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun KeepassEntry.copyableFields(): List<Pair<String, String>> {
        return listOf(
            getString(R.string.keepass_entry_title) to title,
            getString(R.string.keepass_entry_username) to username,
            getString(R.string.keepass_entry_password) to password,
            getString(R.string.keepass_entry_url) to url,
            getString(R.string.keepass_entry_notes) to notes,
        ).filter { (_, value) -> value.isNotBlank() }
    }

    private fun copyKeepassField(label: String, value: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(requireContext(), getString(R.string.keepass_entry_copied, label), Toast.LENGTH_SHORT).show()
    }

    private fun createKeepassRepository(): KeepassRepository {
        val prefs = getPrefs()
        return KeepassRepository(
            storageClient = KeepassStorageClient(
                context = requireContext().applicationContext,
                webDavUsername = prefs.getString(keyWebDavUsername, "").orEmpty(),
                webDavPassword = prefs.getString(keyWebDavPassword, "").orEmpty(),
                ftpUsername = prefs.getString(keyFtpUsername, "").orEmpty(),
                ftpPassword = prefs.getString(keyFtpPassword, "").orEmpty(),
            ),
            databasePath = selectedDatabasePath,
            keyFilePath = selectedKeyPath,
            password = inputDatabasePassword.text?.toString().orEmpty(),
        )
    }

    private fun showKeepassError(messageRes: Int, error: Exception) {
        runOnUiThreadIfAdded {
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            Toast.makeText(requireContext(), getString(messageRes, detail), Toast.LENGTH_LONG).show()
        }
    }

    private fun runOnUiThreadIfAdded(block: () -> Unit) {
        activity?.runOnUiThread {
            if (isAdded) {
                block()
            }
        }
    }

    private fun createDialogInput(
        hint: String,
        inputType: Int,
        value: String,
    ): EditText {
        return EditText(requireContext()).apply {
            this.hint = hint
            this.inputType = inputType
            setSingleLine(true)
            setText(value)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(8)
            }
        }
    }

    private fun normalizeWebDavUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.contains("://")) {
            return trimmed
        }
        return "https://$trimmed"
    }

    private fun buildFtpPath(hostInput: String, portInput: String, pathInput: String): String {
        val host = stripScheme(hostInput)
        val port = portInput.toIntOrNull()
        val cleanPath = if (pathInput.startsWith("/")) pathInput else "/$pathInput"
        val portPart = if (port != null && port != 21) ":$port" else ""
        return "ftp://$host$portPart$cleanPath"
    }

    private fun stripScheme(value: String): String {
        return value.substringAfter("://", value).trim().trimEnd('/')
    }

    private fun looksLikeFolder(path: String): Boolean {
        val cleanPath = path.substringBefore('?').trimEnd('/')
        val lastSlash = cleanPath.lastIndexOf('/')
        val lastDot = cleanPath.lastIndexOf('.')
        return lastSlash < 0 || lastSlash >= lastDot
    }

    private fun joinRemotePath(basePath: String, filename: String): String {
        val cleanBase = basePath.trimEnd('/')
        val cleanFilename = filename.trimStart('/')
        return "$cleanBase/$cleanFilename"
    }

    private fun isRemotePath(path: String): Boolean {
        return path.startsWith("http://") || path.startsWith("https://") || path.startsWith("ftp://")
    }

    private fun launchSystemFilePicker(action: StorageAction) {
        val intent =
            when (action) {
                StorageAction.OpenExisting ->
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    }
                StorageAction.CreateNew ->
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_TITLE, "passwords.kdbx")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    }
            }
        databasePickerLauncher.launch(intent)
    }

    private fun createActionButton(
        textRes: Int,
        iconRes: Int,
        onClick: () -> Unit,
    ): MaterialButton {
        return MaterialButton(requireContext()).apply {
            text = getString(textRes)
            isAllCaps = false
            setIconResource(iconRes)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48),
            ).apply {
                bottomMargin = dp(8)
            }
        }
    }

    private fun createRecentDatabaseRow(path: String, onClick: () -> Unit): View {
        return TextView(requireContext()).apply {
            text = path
            textSize = 16f
            setTextColor(0xFF2196F3.toInt())
            setPadding(dp(6), dp(10), dp(6), dp(10))
            maxLines = 3
            setOnClickListener { onClick() }
        }
    }

    private fun createStorageOptionView(option: StorageOption, onClick: () -> Unit): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(8), dp(6), dp(8))
            setBackgroundColor(0xFFECECEC.toInt())
            setOnClickListener { onClick() }

            ImageView(context).apply {
                setImageResource(option.iconRes)
                alpha = 0.55f
                addView(this, LinearLayout.LayoutParams(dp(36), dp(36)))
            }
            TextView(context).apply {
                text = getString(option.labelRes)
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(0xFF777777.toInt())
                setPadding(0, dp(8), 0, 0)
                addView(
                    this,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            layoutParams = GridLayout.LayoutParams().apply {
                width = dp(96)
                height = dp(112)
                setMargins(dp(6), dp(6), dp(6), dp(6))
            }
        }
    }

    private fun addRecentDatabasePath(path: String) {
        if (path.isBlank()) return
        val recents = mutableListOf(path)
        getRecentDatabasePaths()
            .filterNot { it == path }
            .take(4)
            .forEach { recents.add(it) }
        savePrefs { putString(keyRecentDatabasePaths, recents.joinToString(recentDatabaseSeparator)) }
    }

    private fun getRecentDatabasePaths(): List<String> {
        val currentPath = selectedDatabasePath.takeIf { it.isNotBlank() }
        val savedPaths = getPrefs()
            .getString(keyRecentDatabasePaths, "")
            .orEmpty()
            .split(recentDatabaseSeparator)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return (listOfNotNull(currentPath) + savedPaths).distinct().take(5)
    }

    private fun persistUriPermission(intent: Intent?, modeFlags: Int) {
        val uri = intent?.data ?: return
        val grantedFlags = intent.flags and modeFlags
        if (grantedFlags == 0) return
        try {
            requireContext().contentResolver.takePersistableUriPermission(uri, grantedFlags)
        } catch (e: SecurityException) {
            // Some providers return one-shot permissions only.
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun updateSectionStates() {
        val isKeepassEnabled = enableSwitch.isChecked
        setViewEnabled(sectionDatabaseSettings, true)
        setViewEnabled(sectionSync, true)
        setViewEnabled(sectionSecurity, true)
        sectionSync.alpha = 1f
        sectionSecurity.alpha = 1f

        val canUseFingerprint = canUseSystemFingerprint()
        switchFingerprint.isEnabled = canUseFingerprint
        if (!canUseFingerprint && switchFingerprint.isChecked) {
            setFingerprintSwitchChecked(false)
            getPrefs().edit().putBoolean(keyFingerprint, false).apply()
        }

        getPrefs().edit().putBoolean(keyEnabled, isKeepassEnabled).apply()
    }

    private fun canUseSystemFingerprint(): Boolean {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        return BiometricManager.from(requireContext()).canAuthenticate(authenticators) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showSystemFingerprintPrompt() {
        val executor = ContextCompat.getMainExecutor(requireContext())
        val prompt =
            BiometricPrompt(
                this,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        setFingerprintSwitchChecked(true)
                        getPrefs().edit().putBoolean(keyFingerprint, true).apply()
                        Toast.makeText(requireContext(), R.string.keepass_fingerprint_enabled, Toast.LENGTH_SHORT).show()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        setFingerprintSwitchChecked(false)
                        getPrefs().edit().putBoolean(keyFingerprint, false).apply()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Toast.makeText(requireContext(), R.string.keepass_fingerprint_auth_failed, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        val promptInfo =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.keepass_fingerprint_prompt_title))
                .setSubtitle(getString(R.string.keepass_fingerprint_prompt_subtitle))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText(getString(R.string.dialog_cancel))
                .build()
        prompt.authenticate(promptInfo)
    }

    private fun setFingerprintSwitchChecked(checked: Boolean) {
        updatingFingerprintSwitch = true
        switchFingerprint.isChecked = checked
        updatingFingerprintSwitch = false
    }

    private fun savePrefs(block: SharedPreferences.Editor.() -> Unit) {
        val editor = getPrefs().edit()
        block(editor)
        editor.apply()
    }

    private fun normalizeAndSaveAutoLockMinutes() {
        if (!isAdded) return
        val rawValue = inputAutoLock.text?.toString()?.trim().orEmpty()
        val parsed = rawValue.toIntOrNull()
        val safeValue = if (parsed != null) parsed else minimumAutoLockMinutes
        val clamped = safeValue.coerceAtLeast(minimumAutoLockMinutes)
        if (inputAutoLock.text?.toString() != clamped.toString()) {
            inputAutoLock.setText(clamped.toString())
            inputAutoLock.setSelection(inputAutoLock.text?.length ?: 0)
        }
        getPrefs().edit().putInt(keyAutoLockMinutes, clamped).apply()
    }

    private fun setViewEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) {
            val group = view
            for (i in 0 until group.childCount) {
                setViewEnabled(group.getChildAt(i), enabled)
            }
        }
    }

    private fun showAddPasswordDialog() {
        val context = requireContext()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val titleInput = createDialogInput(
            hint = getString(R.string.keepass_entry_title),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL,
            value = "",
        )
        val usernameInput = createDialogInput(
            hint = getString(R.string.keepass_entry_username),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL,
            value = "",
        )
        val passwordInput = createDialogInput(
            hint = getString(R.string.keepass_entry_password),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            value = "",
        )
        val urlInput = createDialogInput(
            hint = getString(R.string.keepass_entry_url),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            value = "",
        )
        val notesInput = createDialogInput(
            hint = getString(R.string.keepass_entry_notes),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            value = "",
        ).apply {
            setSingleLine(false)
            minLines = 2
        }
        content.addView(titleInput)
        content.addView(usernameInput)
        content.addView(passwordInput)
        content.addView(urlInput)
        content.addView(notesInput)

        AlertDialog.Builder(context)
            .setTitle(R.string.keepass_add_password)
            .setMessage(R.string.keepass_add_password_hint)
            .setView(content)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                if (selectedDatabasePath.isBlank()) {
                    Toast.makeText(context, R.string.keepass_database_not_selected, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val title = titleInput.text?.toString()?.trim().orEmpty()
                val username = usernameInput.text?.toString()?.trim().orEmpty()
                val password = passwordInput.text?.toString().orEmpty()
                val url = urlInput.text?.toString()?.trim().orEmpty()
                val notes = notesInput.text?.toString()?.trim().orEmpty()
                if (password.isBlank()) {
                    Toast.makeText(context, R.string.keepass_password_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                saveDatabaseFields()
                addEntryToDatabase(
                    KeepassEntryInput(
                        title = title.ifBlank { getString(R.string.keepass_entry_default_title) },
                        username = username,
                        password = password,
                        url = url,
                        notes = notes,
                    ),
                )
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun addEntryToDatabase(entry: KeepassEntryInput) {
        val repository = createKeepassRepository()
        Toast.makeText(requireContext(), R.string.keepass_saving_password, Toast.LENGTH_SHORT).show()
        Thread {
            try {
                repository.addEntry(entry)
                runOnUiThreadIfAdded {
                    Toast.makeText(requireContext(), R.string.keepass_password_saved, Toast.LENGTH_SHORT).show()
                    promptSyncIfDatabaseChanged()
                }
            } catch (e: Exception) {
                showKeepassError(R.string.keepass_save_password_failed, e)
            }
        }.start()
    }

    companion object {
        private const val secureSettingAutofillService = "autofill_service"

        @JvmStatic
        fun newInstance(): KeepassFragment = KeepassFragment()
    }

    private enum class StorageAction {
        OpenExisting,
        CreateNew,
    }

    private enum class StorageKind {
        SystemPicker,
        WebDav,
        Ftp,
    }

    private data class StorageOption(
        val labelRes: Int,
        val iconRes: Int,
        val kind: StorageKind,
    )
}
