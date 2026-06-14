package net.toload.main.hd.keepass

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import net.toload.main.hd.R

class LimeKeepassImeUnlockActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (KeepassAutofillLock.isUnlocked(this)) {
            finishWithResult(true)
        } else if (isFingerprintEnabled()) {
            showBiometricPrompt()
        } else {
            KeepassAutofillLock.markUnlocked(this)
            finishWithResult(true)
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
            finishWithResult(false)
            return
        }

        val prompt =
            BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        KeepassAutofillLock.markUnlocked(this@LimeKeepassImeUnlockActivity)
                        finishWithResult(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        finishWithResult(false)
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Toast.makeText(
                            this@LimeKeepassImeUnlockActivity,
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

    private fun finishWithResult(unlocked: Boolean) {
        sendBroadcast(
            Intent(actionUnlockResult)
                .setPackage(packageName)
                .putExtra(extraUnlocked, unlocked),
        )
        finish()
    }

    companion object {
        const val actionUnlockResult = "net.toload.main.hd.keepass.action.IME_UNLOCK_RESULT"
        const val extraUnlocked = "net.toload.main.hd.keepass.extra.IME_UNLOCKED"
        private const val keyFingerprint = "fingerprint_enabled"
    }
}
