package net.toload.main.hd.keepass

import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.provider.AuthenticationAction
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.PasswordCredentialEntry
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import androidx.preference.PreferenceManager
import net.toload.main.hd.R

@TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class LimeCredentialProviderService : CredentialProviderService() {
    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, androidx.credentials.exceptions.GetCredentialException>,
    ) {
        Thread {
            try {
                if (cancellationSignal.isCanceled) return@Thread
                val passwordOptions = request.beginGetCredentialOptions.filterIsInstance<BeginGetPasswordOption>()
                val responseBuilder = BeginGetCredentialResponse.Builder()
                if (passwordOptions.isNotEmpty()) {
                    val entries = loadKeepassEntries()
                        .filter { entry -> entry.username.isNotBlank() && entry.hasPassword }
                        .take(maxCredentialEntries)
                    passwordOptions.forEach { option ->
                        entries
                            .filter { entry -> option.allowedUserIds.isEmpty() || option.allowedUserIds.contains(entry.username) }
                            .forEach { entry ->
                                responseBuilder.addCredentialEntry(
                                    PasswordCredentialEntry.Builder(
                                        applicationContext,
                                        entry.username,
                                        createCredentialIntent(entry.id.toString()),
                                        option,
                                    )
                                        .setDisplayName(entry.title.ifBlank { getString(R.string.keepass_entry_default_title) })
                                        .setAffiliatedDomain(entry.url.ifBlank { entry.additionalUrls.firstOrNull() })
                                        .build(),
                                )
                            }
                    }
                    if (entries.isNotEmpty()) {
                        responseBuilder.addAuthenticationAction(
                            AuthenticationAction.Builder(
                                getString(R.string.keepass_autofill_select_entry),
                                createCredentialIntent(null),
                            ).build(),
                        )
                    }
                }
                callback.onResult(responseBuilder.build())
            } catch (e: Exception) {
                callback.onResult(BeginGetCredentialResponse())
            }
        }.start()
    }

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        callback.onResult(BeginCreateCredentialResponse())
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) {
        callback.onResult(null)
    }

    private fun createCredentialIntent(entryId: String?): PendingIntent {
        val intent = Intent(this, LimeCredentialAuthActivity::class.java).apply {
            entryId?.let { putExtra(LimeCredentialAuthActivity.extraEntryId, it) }
        }
        val mutableFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        return PendingIntent.getActivity(
            this,
            (entryId ?: "credential_selection").hashCode(),
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or mutableFlag,
        )
    }

    private fun loadKeepassEntries(): List<KeepassEntry> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean(keyEnabled, false)) {
            return emptyList()
        }
        val databasePath = prefs.getString(keyDatabasePath, "").orEmpty()
        if (databasePath.isBlank()) {
            return emptyList()
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
        ).openEntries()
    }

    private companion object {
        private const val maxCredentialEntries = 10
        private const val keyEnabled = "enabled"
        private const val keyDatabasePath = "database_path"
        private const val keyDatabaseKey = "database_key"
        private const val keyDatabasePassword = "database_password"
        private const val keyWebDavUsername = "webdav_username"
        private const val keyWebDavPassword = "webdav_password"
        private const val keyFtpUsername = "ftp_username"
        private const val keyFtpPassword = "ftp_password"
    }
}
