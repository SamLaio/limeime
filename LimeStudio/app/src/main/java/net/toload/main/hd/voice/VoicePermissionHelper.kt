package net.toload.main.hd.voice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.Manifest
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager

object VoicePermissionHelper {
    const val PREF_RECORD_AUDIO_PERMISSION_PROMPTED = "voice_inline_permission_prompted"

    @JvmStatic
    fun hasRecordAudioPermission(context: Context?): Boolean {
        return context != null &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun getRecordAudioPermissionState(activity: Activity?): VoicePermissionState {
        if (activity == null) {
            return VoicePermissionState.NOT_REQUESTED
        }
        if (hasRecordAudioPermission(activity)) {
            return VoicePermissionState.GRANTED
        }
        val prompted = wasRecordAudioPermissionPrompted(activity)
        if (!prompted) {
            return VoicePermissionState.NOT_REQUESTED
        }
        return if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)) {
            VoicePermissionState.DENIED_CAN_ASK
        } else {
            VoicePermissionState.DENIED_DO_NOT_ASK_AGAIN
        }
    }

    @JvmStatic
    fun getRecordAudioPermissionState(fragment: Fragment?): VoicePermissionState {
        if (fragment?.context == null) {
            return VoicePermissionState.NOT_REQUESTED
        }
        val context = fragment.requireContext()
        if (hasRecordAudioPermission(context)) {
            return VoicePermissionState.GRANTED
        }
        val prompted = wasRecordAudioPermissionPrompted(context)
        if (!prompted) {
            return VoicePermissionState.NOT_REQUESTED
        }
        return if (fragment.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            VoicePermissionState.DENIED_CAN_ASK
        } else {
            VoicePermissionState.DENIED_DO_NOT_ASK_AGAIN
        }
    }

    @JvmStatic
    fun markRecordAudioPermissionPrompted(context: Context?) {
        if (context == null) {
            return
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(PREF_RECORD_AUDIO_PERMISSION_PROMPTED, true)
            .apply()
    }

    @JvmStatic
    fun wasRecordAudioPermissionPrompted(context: Context?): Boolean {
        if (context == null) {
            return false
        }
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(PREF_RECORD_AUDIO_PERMISSION_PROMPTED, false)
    }

    @JvmStatic
    fun createAppSettingsIntent(context: Context?): Intent {
        val packageName = context?.packageName ?: ""
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:$packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    @JvmStatic
    fun openAppSettings(context: Context?) {
        if (context == null) {
            return
        }
        context.startActivity(createAppSettingsIntent(context))
    }
}
