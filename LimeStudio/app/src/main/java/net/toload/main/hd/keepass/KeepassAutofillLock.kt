package net.toload.main.hd.keepass

import android.content.Context
import androidx.preference.PreferenceManager

object KeepassAutofillLock {
    private const val keyAutoLockMinutes = "auto_lock_minutes"
    private const val keyUnlockedAt = "keepass_autofill_unlocked_at"
    private const val minimumAutoLockMinutes = 5

    fun isUnlocked(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val unlockedAt = prefs.getLong(keyUnlockedAt, 0L)
        if (unlockedAt <= 0L) {
            return false
        }
        val timeoutMinutes = prefs.getInt(keyAutoLockMinutes, minimumAutoLockMinutes)
            .coerceAtLeast(minimumAutoLockMinutes)
        val elapsed = System.currentTimeMillis() - unlockedAt
        return elapsed < timeoutMinutes * 60_000L
    }

    fun markUnlocked(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putLong(keyUnlockedAt, System.currentTimeMillis())
            .apply()
    }

    fun lock(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .remove(keyUnlockedAt)
            .apply()
    }
}
