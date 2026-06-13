/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
 *  **
 *  **    Project Url: https://github.com/SamLaio/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

package net.toload.main.hd

import android.app.backup.BackupAgentHelper
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.SharedPreferencesBackupHelper
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.IOException

/**
 * Backs up the LIME shared preferences.
 */
class LIMEBackupAgent : BackupAgentHelper() {
    override fun onCreate() {
        if (DEBUG) {
            Log.i(TAG, "onCreate(), backup default share preferences for :" + packageName + "_preferences")
        }
        val helper = SharedPreferencesBackupHelper(this, packageName + "_preferences")
        addHelper(PREFS_BACKUP_KEY, helper)
    }

    @Throws(IOException::class)
    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?
    ) {
        super.onBackup(oldState, data, newState)
        if (DEBUG) Log.i(TAG, "onBackup()")
    }

    @Throws(IOException::class)
    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?
    ) {
        super.onRestore(data, appVersionCode, newState)
        if (DEBUG) Log.i(TAG, "onRestore()")
    }

    companion object {
        private const val TAG = "LIMEBackupAgent"
        private const val DEBUG = false
        private const val PREFS_BACKUP_KEY = "defaultPrefs"
    }
}
