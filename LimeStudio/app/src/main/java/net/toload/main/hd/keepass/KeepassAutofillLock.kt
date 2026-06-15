package net.toload.main.hd.keepass

import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager

object KeepassAutofillLock {
    private const val keyAutoLockMinutes = "auto_lock_minutes"
    private const val keyUnlockedAt = "keepass_autofill_unlocked_at"
    private const val minimumAutoLockMinutes = 5
    const val actionLocked = "net.toload.main.hd.keepass.action.LOCKED"
    const val extraLockReason = "net.toload.main.hd.keepass.extra.LOCK_REASON"
    const val lockReasonManual = "manual"
    const val lockReasonAuto = "auto"

    fun isUnlocked(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val unlockedAt = prefs.getLong(keyUnlockedAt, 0L)
        if (unlockedAt <= 0L) {
            return false
        }
        if (remainingUnlockedMillis(context, unlockedAt) > 0L) {
            return true
        }
        lock(context, lockReasonAuto)
        return false
    }

    fun markUnlocked(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putLong(keyUnlockedAt, System.currentTimeMillis())
            .apply()
    }

    fun lock(context: Context, reason: String = lockReasonManual) {
        val appContext = context.applicationContext
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .remove(keyUnlockedAt)
            .commit()
        runCatching { KeepassRepository.clearEntryCache(appContext) }
        appContext.sendBroadcast(
            Intent(actionLocked)
                .setPackage(appContext.packageName)
                .putExtra(extraLockReason, reason),
        )
    }

    fun remainingUnlockedMillis(context: Context): Long {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val unlockedAt = prefs.getLong(keyUnlockedAt, 0L)
        if (unlockedAt <= 0L) {
            return 0L
        }
        return remainingUnlockedMillis(context, unlockedAt).coerceAtLeast(0L)
    }

    private fun remainingUnlockedMillis(context: Context, unlockedAt: Long): Long {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val timeoutMinutes = prefs.getInt(keyAutoLockMinutes, minimumAutoLockMinutes)
            .coerceAtLeast(minimumAutoLockMinutes)
        val timeoutMillis = timeoutMinutes * 60_000L
        val elapsed = System.currentTimeMillis() - unlockedAt
        return timeoutMillis - elapsed
    }
}
