package net.toload.main.hd.keepass

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import net.toload.main.hd.R
import java.util.UUID

class LimeAutofillAuthActivity : FragmentActivity() {
    private var usernameId: AutofillId? = null
    private var passwordId: AutofillId? = null
    private var entryId: UUID? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usernameId = intent.getParcelableExtraCompat(extraUsernameId)
        passwordId = intent.getParcelableExtraCompat(extraPasswordId)
        entryId = intent.getStringExtra(extraEntryId)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        if (KeepassAutofillLock.isUnlocked(this)) {
            continueAfterUnlock()
        } else if (isFingerprintEnabled()) {
            showBiometricPrompt()
        } else {
            KeepassAutofillLock.markUnlocked(this)
            continueAfterUnlock()
        }
    }

    private fun isFingerprintEnabled(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(keyFingerprint, false)
    }

    private fun showBiometricPrompt() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, R.string.keepass_fingerprint_unavailable, Toast.LENGTH_SHORT).show()
            finishCanceled()
            return
        }

        val prompt =
            BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        KeepassAutofillLock.markUnlocked(this@LimeAutofillAuthActivity)
                        continueAfterUnlock()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        finishCanceled()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Toast.makeText(
                            this@LimeAutofillAuthActivity,
                            R.string.keepass_fingerprint_auth_failed,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        val promptInfo =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.keepass_fingerprint_prompt_title))
                .setSubtitle(getString(R.string.keepass_autofill_unlock_prompt))
                .setAllowedAuthenticators(authenticators)
                .setNegativeButtonText(getString(R.string.dialog_cancel))
                .build()
        prompt.authenticate(promptInfo)
    }

    private fun continueAfterUnlock() {
        Thread {
            try {
                val entries = loadEntries()
                val selectedEntry = entryId?.let { id -> entries.firstOrNull { entry -> entry.id == id } }
                runOnUiThread {
                    if (selectedEntry != null) {
                        finishWithEntry(selectedEntry)
                    } else {
                        showEntrySelection(entries)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, R.string.keepass_open_database_failed, Toast.LENGTH_SHORT).show()
                    finishCanceled()
                }
            }
        }.start()
    }

    private fun showEntrySelection(entries: List<KeepassEntry>) {
        val fillableEntries = entries.filter { entry -> entry.username.isNotBlank() || entry.hasPassword }
        if (fillableEntries.isEmpty()) {
            Toast.makeText(this, R.string.keepass_browse_database_empty, Toast.LENGTH_SHORT).show()
            finishCanceled()
            return
        }
        val labels = fillableEntries
            .map { entry ->
                val title = entry.title.ifBlank { getString(R.string.keepass_entry_default_title) }
                val subtitle = entry.username.ifBlank { entry.url }
                if (subtitle.isBlank()) title else "$title\n$subtitle"
            }
            .toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.keepass_autofill_select_entry)
            .setItems(labels) { _, which ->
                finishWithEntry(fillableEntries[which])
            }
            .setNegativeButton(R.string.dialog_cancel) { _, _ -> finishCanceled() }
            .setOnCancelListener { finishCanceled() }
            .show()
    }

    private fun finishWithEntry(entry: KeepassEntry) {
        val dataset = buildDataset(createRepository().unlockEntry(entry))
        if (dataset == null) {
            finishCanceled()
            return
        }
        val result = android.content.Intent()
            .putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    private fun buildDataset(entry: KeepassEntry): Dataset? {
        val presentation = createPresentation(entry)
        val builder = Dataset.Builder()
        var hasValue = false
        usernameId?.let { id ->
            if (entry.username.isNotBlank()) {
                builder.setAutofillValue(id, entry.username, presentation)
                hasValue = true
            }
        }
        passwordId?.let { id ->
            if (entry.password.isNotBlank()) {
                builder.setAutofillValue(id, entry.password, presentation)
                hasValue = true
            }
        }
        return if (hasValue) builder.build() else null
    }

    private fun createPresentation(entry: KeepassEntry): RemoteViews {
        return RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
            setTextViewText(android.R.id.text1, entry.title.ifBlank { getString(R.string.keepass_entry_default_title) })
            setTextViewText(android.R.id.text2, getString(R.string.keepass_autofill_unlocked))
        }
    }

    private fun loadEntries(): List<KeepassEntry> {
        return createRepository().openEntries()
    }

    private fun createRepository(): KeepassRepository {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val databasePath = prefs.getString(keyDatabasePath, "").orEmpty()
        if (databasePath.isBlank()) {
            throw IllegalArgumentException("Database path is empty")
        }
        return KeepassRepository(
            storageClient = KeepassStorageClient(
                context = applicationContext,
                webDavUsername = prefs.getString(keyWebDavUsername, "").orEmpty(),
                webDavPassword = prefs.getString(keyWebDavPassword, "").orEmpty(),
                ftpUsername = prefs.getString(keyFtpUsername, "").orEmpty(),
                ftpPassword = prefs.getString(keyFtpPassword, "").orEmpty(),
            ),
            databasePath = databasePath,
            keyFilePath = prefs.getString(keyDatabaseKey, "").orEmpty(),
            password = prefs.getString(keyDatabasePassword, "").orEmpty(),
        )
    }

    private fun finishCanceled() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    @Suppress("DEPRECATION")
    private fun Dataset.Builder.setAutofillValue(
        id: AutofillId,
        value: String,
        presentation: RemoteViews,
    ) {
        setValue(id, AutofillValue.forText(value), presentation)
    }

    private inline fun <reified T> android.content.Intent.getParcelableExtraCompat(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key)
        }
    }

    companion object {
        const val extraUsernameId = "net.toload.main.hd.keepass.extra.USERNAME_ID"
        const val extraPasswordId = "net.toload.main.hd.keepass.extra.PASSWORD_ID"
        const val extraEntryId = "net.toload.main.hd.keepass.extra.ENTRY_ID"

        private const val keyDatabasePath = "database_path"
        private const val keyDatabaseKey = "database_key"
        private const val keyDatabasePassword = "database_password"
        private const val keyFingerprint = "fingerprint_enabled"
        private const val keyWebDavUsername = "webdav_username"
        private const val keyWebDavPassword = "webdav_password"
        private const val keyFtpUsername = "ftp_username"
        private const val keyFtpPassword = "ftp_password"
    }
}
