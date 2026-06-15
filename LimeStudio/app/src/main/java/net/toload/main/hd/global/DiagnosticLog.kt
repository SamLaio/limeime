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
package net.toload.main.hd.global

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticLog {
    const val ENABLED = false
    private const val TAG = "LIMEDiagnostic"
    private const val LOG_FILE_NAME = "limeime-diagnostic-current.txt"
    private const val MAX_LOG_BYTES = 256 * 1024L
    private val lock = Any()
    @Volatile
    private var installed = false

    val isEnabled: Boolean
        get() = ENABLED

    fun install(context: Context) {
        if (!isEnabled) return
        if (installed) return
        synchronized(lock) {
            if (installed) return
            installed = true
            val appContext = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    recordThrowable(appContext, "Uncaught exception on ${thread.name}", throwable)
                    exportToDownloads(appContext, "crash")
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to export crash diagnostic log", t)
                } finally {
                    if (previous != null) {
                        previous.uncaughtException(thread, throwable)
                    } else {
                        throw throwable
                    }
                }
            }
            record(appContext, "Application", "Diagnostic logger installed")
            recordEnvironment(appContext)
            exportToDownloadsAsync(appContext, "startup")
        }
    }

    fun record(context: Context?, area: String, message: String) {
        if (!isEnabled) return
        Log.i(TAG, "[$area] $message")
        if (context == null) return
        synchronized(lock) {
            try {
                val file = logFile(context)
                rotateIfNeeded(file)
                file.appendText("${timestamp()} [$area] $message\n")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to write diagnostic log", t)
            }
        }
    }

    fun recordThrowable(context: Context?, area: String, throwable: Throwable) {
        if (!isEnabled) return
        val stack = StringWriter()
        throwable.printStackTrace(PrintWriter(stack))
        record(context, area, stack.toString())
    }

    fun exportToDownloadsAsync(context: Context?, reason: String) {
        if (!isEnabled) return
        if (context == null) return
        val appContext = context.applicationContext
        Thread {
            try {
                exportToDownloads(appContext, reason)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to export diagnostic log", t)
            }
        }.start()
    }

    fun exportToDownloads(context: Context, reason: String): Uri? {
        if (!isEnabled) return null
        val appContext = context.applicationContext
        val content = synchronized(lock) {
            val file = logFile(appContext)
            if (file.exists()) file.readText() else ""
        }
        if (content.isEmpty()) return null

        val fileName = "limeime-diagnostic-${safeReason(reason)}-${fileTimestamp()}.txt"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportToMediaStoreDownloads(appContext, fileName, content)
        } else {
            exportToAppDownloads(appContext, fileName, content)
        }
    }

    private fun recordEnvironment(context: Context) {
        val version = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("deprecation")
                info.versionCode.toLong()
            }
            "${info.versionName}($versionCode)"
        } catch (t: Throwable) {
            "unknown"
        }
        record(
            context,
            "Environment",
            "version=$version, " +
                    "sdk=${Build.VERSION.SDK_INT}, release=${Build.VERSION.RELEASE}, " +
                    "brand=${Build.BRAND}, manufacturer=${Build.MANUFACTURER}, " +
                    "model=${Build.MODEL}, device=${Build.DEVICE}"
        )
    }

    private fun exportToMediaStoreDownloads(context: Context, fileName: String, content: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues()
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        values.put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        values.put(MediaStore.MediaColumns.IS_PENDING, 1)
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
            } ?: return uri
            val completeValues = ContentValues()
            completeValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, completeValues, null, null)
            Log.i(TAG, "Exported diagnostic log to Downloads: $fileName")
            return uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    private fun exportToAppDownloads(context: Context, fileName: String, content: String): Uri? {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        Log.i(TAG, "Exported diagnostic log to app downloads: ${file.absolutePath}")
        return Uri.fromFile(file)
    }

    private fun logFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }

    private fun rotateIfNeeded(file: File) {
        if (file.exists() && file.length() > MAX_LOG_BYTES) {
            file.writeText("${timestamp()} [DiagnosticLog] log rotated\n")
        }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
    }

    private fun fileTimestamp(): String {
        return SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    }

    private fun safeReason(reason: String): String {
        return reason.replace(Regex("[^A-Za-z0-9_-]"), "_")
    }
}
