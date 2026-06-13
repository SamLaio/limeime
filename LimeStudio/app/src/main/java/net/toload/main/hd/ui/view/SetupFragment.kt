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
package net.toload.main.hd.ui.view

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import net.toload.main.hd.global.LIMEUtilities
import net.toload.main.hd.global.LIMEUtilities.Companion.isLIMEActive
import net.toload.main.hd.global.LIMEUtilities.Companion.isLIMEEnabled
import net.toload.main.hd.global.LIMEUtilities.Companion.showInputMethodPicker
import net.toload.main.hd.global.LIMEUtilities.Companion.showInputMethodSettingsPage
import net.toload.main.hd.R
import net.toload.main.hd.ui.view.ScrollableTabHelper.applyToNestedScrollView
import net.toload.main.hd.voice.VoicePermissionHelper
import net.toload.main.hd.voice.VoicePermissionHelper.getRecordAudioPermissionState
import net.toload.main.hd.voice.VoicePermissionHelper.markRecordAudioPermissionPrompted
import net.toload.main.hd.voice.VoicePermissionHelper.openAppSettings
import net.toload.main.hd.voice.VoicePermissionState

/**
 * Activation-guide and About card fragment for the 設定 (Setup) tab.
 * 
 * 
 * Displays current IME activation status, step-by-step setup instructions,
 * buttons to open system IME settings / picker, and an About card with version,
 * license, and source-code link.
 */
class SetupFragment : Fragment() {
    private var activity: Activity? = null
    private var imeChangeReceiver: BroadcastReceiver? = null
    private val recordAudioPermissionLauncher = registerForActivityResult<String?, Boolean?>(
        RequestPermission(),
        ActivityResultCallback { isGranted: Boolean? -> refreshVoicePermissionStatus() })

    private var statusCard: MaterialCardView? = null
    private var statusText: TextView? = null
    private var statusIcon: ImageView? = null
    private var setupHeading: TextView? = null
    private var setupStep1Description: TextView? = null
    private var setupStep2Description: TextView? = null
    private var btnSystemSettings: MaterialButton? = null
    private var btnImePicker: MaterialButton? = null
    private var voicePermissionCard: MaterialCardView? = null
    private var voicePermissionIcon: ImageView? = null
    private var voicePermissionTitle: TextView? = null
    private var voicePermissionDetail: TextView? = null
    private var voicePermissionButton: MaterialButton? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        activity = getActivity()
        val rootView = inflater.inflate(R.layout.fragment_setup, container, false)
        val scrollView = rootView.findViewById<NestedScrollView?>(R.id.setup_scroll)
        if (scrollView != null) {
            applyToNestedScrollView(activity, scrollView)
        }

        statusCard = rootView.findViewById<MaterialCardView>(R.id.statusCard)
        statusText = rootView.findViewById<TextView>(R.id.statusText)
        statusIcon = rootView.findViewById<ImageView>(R.id.statusIcon)
        setupHeading = rootView.findViewById<TextView>(R.id.setupHeading)
        setupStep1Description = rootView.findViewById<TextView>(R.id.setupStep1Description)
        setupStep2Description = rootView.findViewById<TextView>(R.id.setupStep2Description)
        btnSystemSettings = rootView.findViewById<MaterialButton>(R.id.btnSetupImSystemSetting)
        btnImePicker = rootView.findViewById<MaterialButton>(R.id.btnSetupImSystemIMPicker)
        voicePermissionCard = rootView.findViewById<MaterialCardView?>(R.id.voicePermissionCard)
        voicePermissionIcon = rootView.findViewById<ImageView>(R.id.voicePermissionIcon)
        voicePermissionTitle = rootView.findViewById<TextView>(R.id.voicePermissionTitle)
        voicePermissionDetail = rootView.findViewById<TextView>(R.id.voicePermissionDetail)
        voicePermissionButton = rootView.findViewById<MaterialButton?>(R.id.voicePermissionButton)

        btnSystemSettings!!.setOnClickListener(View.OnClickListener { v: View? ->
            showInputMethodSettingsPage(
                requireActivity().getApplicationContext()
            )
        })
        btnImePicker!!.setOnClickListener(View.OnClickListener { v: View? ->
            showInputMethodPicker(
                requireActivity().getApplicationContext()
            )
        })
        if (voicePermissionButton != null) {
            voicePermissionButton!!.setOnClickListener(View.OnClickListener { v: View? -> openVoicePermissionSettings() })
        }

        // License and GitHub link taps
        val txtLicense = rootView.findViewById<TextView?>(R.id.txtLicenseUrl)
        if (txtLicense != null) {
            txtLicense.setOnClickListener(View.OnClickListener { v: View? ->
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.url_license_limeime))
                )
                startActivity(intent)
            })
        }

        val txtGithub = rootView.findViewById<TextView?>(R.id.txtGithubUrl)
        if (txtGithub != null) {
            txtGithub.setOnClickListener(View.OnClickListener { v: View? ->
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.url_github_limeime))
                )
                startActivity(intent)
            })
        }

        return rootView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity = null
    }

    override fun onResume() {
        super.onResume()
        registerImeReceiver()
        refreshStatus()
    }

    override fun onPause() {
        super.onPause()
        unregisterImeReceiver()
    }

    private fun refreshStatus() {
        if (!isAdded() || activity == null) return
        val ctx = activity!!.getApplicationContext()
        val enabled = isLIMEEnabled(ctx)
        val active = isLIMEActive(ctx)
        refreshVoicePermissionStatus()

        // Neutral subtle background; the state color is carried by icon + text (iOS parity)
        statusCard!!.setCardBackgroundColor(
            ContextCompat.getColor(
                activity!!,
                R.color.setup_status_bg
            )
        )

        if (enabled && active) {
            val fg = ContextCompat.getColor(activity!!, R.color.setup_status_fg_green)
            statusIcon!!.setImageResource(R.drawable.ic_status_check)
            statusIcon!!.setColorFilter(fg)
            statusText!!.setTextColor(fg)
            statusText!!.setText(R.string.setup_status_active)
            setupHeading!!.setVisibility(View.GONE)
            setupStep1Description!!.setVisibility(View.GONE)
            setupStep2Description!!.setVisibility(View.GONE)
            btnSystemSettings!!.setVisibility(View.GONE)
            btnImePicker!!.setVisibility(View.GONE)
        } else if (enabled) {
            val fg = ContextCompat.getColor(activity!!, R.color.setup_status_fg_yellow)
            statusIcon!!.setImageResource(R.drawable.ic_status_warning)
            statusIcon!!.setColorFilter(fg)
            statusText!!.setTextColor(fg)
            statusText!!.setText(R.string.setup_status_enabled_not_active)
            setupHeading!!.setVisibility(View.VISIBLE)
            setupStep1Description!!.setVisibility(View.GONE)
            setupStep2Description!!.setVisibility(View.VISIBLE)
            btnSystemSettings!!.setVisibility(View.GONE)
            btnImePicker!!.setVisibility(View.VISIBLE)
        } else {
            val fg = ContextCompat.getColor(activity!!, R.color.setup_status_fg_red)
            statusIcon!!.setImageResource(R.drawable.ic_status_error)
            statusIcon!!.setColorFilter(fg)
            statusText!!.setTextColor(fg)
            statusText!!.setText(R.string.setup_status_not_enabled)
            setupHeading!!.setVisibility(View.VISIBLE)
            setupStep1Description!!.setVisibility(View.VISIBLE)
            setupStep2Description!!.setVisibility(View.GONE)
            btnSystemSettings!!.setVisibility(View.VISIBLE)
            btnImePicker!!.setVisibility(View.GONE)
        }
    }

    private fun refreshVoicePermissionStatus() {
        if (voicePermissionCard == null || activity == null || !isAdded()) {
            return
        }
        if (!getResources().getBoolean(R.bool.inline_dictation_feature_enabled)) {
            voicePermissionCard!!.setVisibility(View.GONE)
            return
        }

        voicePermissionCard!!.setVisibility(View.VISIBLE)
        val state = getRecordAudioPermissionState(this)
        val fg: Int
        when (state) {
            VoicePermissionState.GRANTED -> {
                fg = ContextCompat.getColor(activity!!, R.color.setup_status_fg_green)
                voicePermissionIcon!!.setImageResource(R.drawable.ic_status_check)
                voicePermissionTitle!!.setText(R.string.setup_voice_permission_title_granted)
                voicePermissionDetail!!.setText(R.string.setup_voice_permission_granted)
                voicePermissionDetail!!.setVisibility(View.VISIBLE)
                voicePermissionButton!!.setVisibility(View.GONE)
            }

            VoicePermissionState.DENIED_DO_NOT_ASK_AGAIN -> {
                fg = ContextCompat.getColor(activity!!, R.color.setup_status_fg_yellow)
                voicePermissionIcon!!.setImageResource(R.drawable.ic_status_warning)
                voicePermissionTitle!!.setText(R.string.setup_voice_permission_title_settings)
                voicePermissionDetail!!.setText(R.string.setup_voice_permission_denied_permanently)
                voicePermissionDetail!!.setVisibility(View.VISIBLE)
                voicePermissionButton!!.setText(R.string.setup_voice_permission_open_settings)
                voicePermissionButton!!.setVisibility(View.VISIBLE)
            }

            VoicePermissionState.DENIED_CAN_ASK -> {
                fg = ContextCompat.getColor(activity!!, R.color.setup_status_fg_red)
                voicePermissionIcon!!.setImageResource(R.drawable.ic_status_error)
                voicePermissionTitle!!.setText(R.string.setup_voice_permission_title_request)
                voicePermissionDetail!!.setText(R.string.setup_voice_permission_denied_once)
                voicePermissionDetail!!.setVisibility(View.VISIBLE)
                voicePermissionButton!!.setText(R.string.setup_voice_permission_request)
                voicePermissionButton!!.setVisibility(View.VISIBLE)
            }

            VoicePermissionState.NOT_REQUESTED -> {
                fg = ContextCompat.getColor(activity!!, R.color.setup_status_fg_red)
                voicePermissionIcon!!.setImageResource(R.drawable.ic_status_error)
                voicePermissionTitle!!.setText(R.string.setup_voice_permission_title_request)
                voicePermissionDetail!!.setText(R.string.setup_voice_permission_not_granted)
                voicePermissionDetail!!.setVisibility(View.VISIBLE)
                voicePermissionButton!!.setText(R.string.setup_voice_permission_request)
                voicePermissionButton!!.setVisibility(View.VISIBLE)
            }

            else -> {
                fg = ContextCompat.getColor(activity!!, R.color.setup_status_fg_red)
                voicePermissionIcon!!.setImageResource(R.drawable.ic_status_error)
                voicePermissionTitle!!.setText(R.string.setup_voice_permission_title_request)
                voicePermissionDetail!!.setText(R.string.setup_voice_permission_not_granted)
                voicePermissionDetail!!.setVisibility(View.VISIBLE)
                voicePermissionButton!!.setText(R.string.setup_voice_permission_request)
                voicePermissionButton!!.setVisibility(View.VISIBLE)
            }
        }
        voicePermissionIcon!!.setColorFilter(fg)
        voicePermissionTitle!!.setTextColor(fg)
    }

    private fun openVoicePermissionSettings() {
        if (!isAdded() || activity == null) {
            return
        }
        val state = getRecordAudioPermissionState(this)
        markRecordAudioPermissionPrompted(activity)
        if (state == VoicePermissionState.NOT_REQUESTED
            || state == VoicePermissionState.DENIED_CAN_ASK
        ) {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        Toast.makeText(
            activity,
            R.string.setup_voice_permission_settings_hint,
            Toast.LENGTH_LONG
        ).show()
        openAppSettings(activity)
    }

    private fun registerImeReceiver() {
        if (imeChangeReceiver != null || activity == null) return
        imeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refreshStatus()
            }
        }
        val filter = IntentFilter("android.intent.action.INPUT_METHOD_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity!!.registerReceiver(imeChangeReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            activity!!.registerReceiver(imeChangeReceiver, filter)
        }
    }

    private fun unregisterImeReceiver() {
        if (imeChangeReceiver != null && activity != null) {
            try {
                activity!!.unregisterReceiver(imeChangeReceiver)
            } catch (ignored: IllegalArgumentException) {
            }
            imeChangeReceiver = null
        }
    }

    companion object {
        private const val TAG = "SetupFragment"

        @JvmStatic
        fun newInstance(): SetupFragment {
            return SetupFragment()
        }
    }
}
