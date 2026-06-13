package net.toload.main.hd.ui.view

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import net.toload.main.hd.R
import net.toload.main.hd.ui.view.ScrollableTabHelper.applyToNestedScrollView

class KeepassFragment : Fragment() {
    private lateinit var enableSwitch: SwitchCompat
    private lateinit var inputDatabasePath: TextInputEditText
    private lateinit var inputDatabaseKey: TextInputEditText
    private lateinit var inputDatabasePassword: TextInputEditText
    private lateinit var btnBrowseDatabase: MaterialButton
    private lateinit var btnAddPassword: MaterialButton
    private lateinit var switchFingerprint: SwitchCompat
    private lateinit var inputAutoLock: TextInputEditText
    private lateinit var sectionDatabaseSettings: ViewGroup
    private lateinit var sectionSync: ViewGroup
    private lateinit var sectionSecurity: ViewGroup

    private lateinit var databasePickerLauncher: ActivityResultLauncher<Intent>

    private val legacyPrefsName = "keepass_settings"
    private val keyEnabled = "enabled"
    private val keyDatabasePath = "database_path"
    private val keyDatabaseKey = "database_key"
    private val keyDatabasePassword = "database_password"
    private val keyFingerprint = "fingerprint_enabled"
    private val keyAutoLockMinutes = "auto_lock_minutes"
    private val keyExtraPasswords = "extra_passwords"
    private val minimumAutoLockMinutes = 5

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
                    inputDatabasePath.setText(uri.toString())
                    savePrefs { putString(keyDatabasePath, uri.toString()) }
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
        inputDatabasePath = root.findViewById(R.id.input_keepass_database)
        inputDatabaseKey = root.findViewById(R.id.input_keepass_key)
        inputDatabasePassword = root.findViewById(R.id.input_keepass_password)
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
        inputDatabasePath.setText(prefs.getString(keyDatabasePath, ""))
        inputDatabaseKey.setText(prefs.getString(keyDatabaseKey, ""))
        inputDatabasePassword.setText(prefs.getString(keyDatabasePassword, ""))
        switchFingerprint.isChecked = prefs.getBoolean(keyFingerprint, false)
        val timeoutMinutes = prefs.getInt(keyAutoLockMinutes, minimumAutoLockMinutes)
        inputAutoLock.setText(timeoutMinutes.toString())
    }

    private fun setViewListeners() {
        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            getPrefs().edit().putBoolean(keyEnabled, isChecked).apply()
            updateSectionStates()
        }
        switchFingerprint.setOnCheckedChangeListener { _, isChecked ->
            if (!isDatabaseConfigured()) {
                switchFingerprint.isChecked = false
                Toast.makeText(requireContext(), R.string.keepass_setup_required, Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            if (isChecked && !canUseFingerprint()) {
                switchFingerprint.isChecked = false
                Toast.makeText(requireContext(), R.string.keepass_fingerprint_unavailable, Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            getPrefs().edit().putBoolean(keyFingerprint, isChecked).apply()
        }

        val databaseTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                saveDatabaseFields()
            }
        }
        inputDatabasePath.addTextChangedListener(databaseTextWatcher)
        inputDatabaseKey.addTextChangedListener(databaseTextWatcher)
        inputDatabasePassword.addTextChangedListener(databaseTextWatcher)

        btnBrowseDatabase.setOnClickListener {
            if (!isFeatureEnabled()) {
                Toast.makeText(requireContext(), R.string.keepass_setup_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            databasePickerLauncher.launch(intent)
        }

        btnAddPassword.setOnClickListener {
            if (!isFeatureEnabled()) {
                Toast.makeText(requireContext(), R.string.keepass_setup_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
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
        val path = inputDatabasePath.text?.toString()?.trim() ?: ""
        val key = inputDatabaseKey.text?.toString() ?: ""
        val password = inputDatabasePassword.text?.toString() ?: ""
        getPrefs().edit()
            .putBoolean(keyEnabled, enableSwitch.isChecked)
            .putString(keyDatabasePath, path)
            .putString(keyDatabaseKey, key)
            .putString(keyDatabasePassword, password)
            .apply()
        updateSectionStates()
    }

    private fun isFeatureEnabled(): Boolean {
        return enableSwitch.isChecked && isDatabaseConfigured()
    }

    private fun isDatabaseConfigured(): Boolean {
        val path = inputDatabasePath.text?.toString()?.trim().orEmpty()
        val key = inputDatabaseKey.text?.toString()?.trim().orEmpty()
        val password = inputDatabasePassword.text?.toString()?.trim().orEmpty()
        return path.isNotEmpty() && key.isNotEmpty() && password.isNotEmpty()
    }

    private fun updateSectionStates() {
        val isKeepassEnabled = enableSwitch.isChecked
        setViewEnabled(sectionDatabaseSettings, isKeepassEnabled)
        val allowSyncAndSecurity = isKeepassEnabled && isDatabaseConfigured()
        setViewEnabled(sectionSync, allowSyncAndSecurity)
        setViewEnabled(sectionSecurity, allowSyncAndSecurity)
        sectionSync.alpha = if (allowSyncAndSecurity) 1f else 0.5f
        sectionSecurity.alpha = if (allowSyncAndSecurity) 1f else 0.5f

        val canUseFingerprint = allowSyncAndSecurity && canUseFingerprint()
        switchFingerprint.isEnabled = canUseFingerprint
        if (!canUseFingerprint && switchFingerprint.isChecked) {
            switchFingerprint.isChecked = false
            getPrefs().edit().putBoolean(keyFingerprint, false).apply()
        }

        getPrefs().edit().putBoolean(keyEnabled, isKeepassEnabled).apply()
    }

    @Suppress("DEPRECATION")
    private fun canUseFingerprint(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }
        val context = requireContext()
        val fingerprintManager =
            context.getSystemService(FingerprintManager::class.java) ?: return false
        val isDeviceSecure = context.getSystemService(KeyguardManager::class.java)?.isKeyguardSecure == true

        return try {
            fingerprintManager.isHardwareDetected &&
                fingerprintManager.hasEnrolledFingerprints() &&
                isDeviceSecure
        } catch (e: Exception) {
            false
        }
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
        val passwordInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.keepass_add_password)
            .setMessage(R.string.keepass_add_password_hint)
            .setView(passwordInput)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val value = passwordInput.text?.toString()?.trim().orEmpty()
                if (value.isBlank()) {
                    Toast.makeText(context, R.string.keepass_password_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val existing =
                    HashSet(getPrefs().getStringSet(keyExtraPasswords, emptySet()) ?: emptySet())
                existing.add(value)
                getPrefs().edit().putStringSet(keyExtraPasswords, existing).apply()
                Toast.makeText(context, R.string.keepass_password_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    companion object {
        @JvmStatic
        fun newInstance(): KeepassFragment = KeepassFragment()
    }
}
