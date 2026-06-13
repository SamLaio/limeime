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

package net.toload.main.hd.ui.dialog

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.global.LIME
import net.toload.main.hd.R
import net.toload.main.hd.ui.controller.ManageImController
import net.toload.main.hd.ui.LIMESettings

/**
 * Dialog fragment that exposes share/export options for IM tables and related data.
 */
class ShareDialog : DialogFragment() {
    private var manageImController: ManageImController? = null
    private var hostActivity: Activity? = null

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        isCancelable = false
    }

    override fun onStart() {
        super.onStart()
        val dialog: Dialog? = dialog
        if (dialog != null) {
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onResume() {
        super.onResume()
        requireDialog().setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                dismiss()
                true
            } else {
                false
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, icicle: Bundle?): View? {
        requireDialog().window?.setTitle(resources.getString(R.string.share_dialog_title))
        hostActivity = activity
        val currentActivity = hostActivity
        if (currentActivity is LIMESettings) {
            manageImController = currentActivity.manageImController
        }
        val view = inflater.inflate(R.layout.fragment_dialog_share, container, false)

        val btnShareCancel = view.findViewById<Button>(R.id.btnShareCancel)
        btnShareCancel.setOnClickListener { dismiss() }

        val btnShareRelated = view.findViewById<Button>(R.id.btnShareRelated)
        val imConfigList = manageImController?.imConfigFullNameList ?: emptyList()

        for (config in SHARE_BUTTON_CONFIGS) {
            val button = view.findViewById<Button>(config.buttonId)
            setupShareButton(button, imConfigList, config.tableName, config.imName)
        }

        btnShareRelated.setOnClickListener { confirmShareDialog(LIME.DB_TABLE_RELATED) }
        return view
    }

    private fun setupShareButton(button: Button, imConfigList: List<ImConfig>, tableName: String, im: String) {
        var exists = false
        for (imConfig in imConfigList) {
            if (imConfig.code == tableName) {
                exists = true
                break
            }
        }
        if (!exists) {
            button.alpha = LIME.HALF_ALPHA_VALUE
            button.setTypeface(null, Typeface.ITALIC)
            button.isEnabled = false
        } else {
            button.alpha = LIME.NORMAL_ALPHA_VALUE
            button.setTypeface(null, Typeface.BOLD)
            button.setOnClickListener { confirmShareDialog(im) }
        }
    }

    fun confirmShareDialog(tableName: String) {
        val currentActivity = hostActivity ?: return
        val alertDialog = AlertDialog.Builder(currentActivity).create()
        val isRelated = tableName.equals(LIME.DB_TABLE_RELATED, ignoreCase = true)

        if (isRelated) {
            alertDialog.setTitle(currentActivity.resources.getString(R.string.share_dialog_related_title))
            alertDialog.setMessage(currentActivity.resources.getString(R.string.share_dialog_related_title_message))
        } else {
            alertDialog.setTitle(currentActivity.resources.getString(R.string.share_dialog_title))
            alertDialog.setMessage(currentActivity.resources.getString(R.string.share_dialog_title_message))
        }

        if (!isRelated) {
            alertDialog.setButton(
                DialogInterface.BUTTON_POSITIVE,
                currentActivity.resources.getString(R.string.share_lime_cin)
            ) { _, _ ->
                val mainActivity = currentActivity as LIMESettings
                mainActivity.shareManager?.shareImAsText(tableName)
                dismiss()
            }
        }

        alertDialog.setButton(
            DialogInterface.BUTTON_NEUTRAL,
            currentActivity.resources.getString(R.string.share_lime_db)
        ) { _, _ ->
            val mainActivity = currentActivity as LIMESettings
            val shareManager = mainActivity.shareManager
            if (shareManager != null) {
                if (isRelated) {
                    shareManager.shareRelatedAsDatabase()
                } else {
                    shareManager.exportAndShareImTable(tableName)
                }
            }
            dismiss()
        }
        alertDialog.setButton(
            DialogInterface.BUTTON_NEGATIVE,
            currentActivity.resources.getString(R.string.dialog_cancel)
        ) { dialog, _ -> dialog.dismiss() }
        alertDialog.show()
    }

    private class ShareButtonConfig(
        val buttonId: Int,
        val tableName: String,
        val imName: String
    )

    companion object {
        private val SHARE_BUTTON_CONFIGS = arrayOf(
            ShareButtonConfig(R.id.btnShareCustom, LIME.DB_TABLE_CUSTOM, LIME.IM_CUSTOM),
            ShareButtonConfig(R.id.btnSharePhonetic, LIME.DB_TABLE_PHONETIC, LIME.IM_PHONETIC),
            ShareButtonConfig(R.id.btnShareCj, LIME.DB_TABLE_CJ, LIME.IM_CJ),
            ShareButtonConfig(R.id.btnShareCj5, LIME.DB_TABLE_CJ5, LIME.IM_CJ5),
            ShareButtonConfig(R.id.btnShareScj, LIME.DB_TABLE_SCJ, LIME.IM_SCJ),
            ShareButtonConfig(R.id.btnShareEcj, LIME.DB_TABLE_ECJ, LIME.IM_ECJ),
            ShareButtonConfig(R.id.btnShareDayi, LIME.DB_TABLE_DAYI, LIME.IM_DAYI),
            ShareButtonConfig(R.id.btnShareEz, LIME.DB_TABLE_EZ, LIME.IM_EZ),
            ShareButtonConfig(R.id.btnShareArray, LIME.DB_TABLE_ARRAY, LIME.IM_ARRAY),
            ShareButtonConfig(R.id.btnShareArray10, LIME.DB_TABLE_ARRAY10, LIME.IM_ARRAY10),
            ShareButtonConfig(R.id.btnShareHs, LIME.DB_TABLE_HS, LIME.IM_HS),
            ShareButtonConfig(R.id.btnShareWb, LIME.DB_TABLE_WB, LIME.IM_WB),
            ShareButtonConfig(R.id.btnSharePinyin, LIME.DB_TABLE_PINYIN, LIME.IM_PINYIN)
        )

        @JvmStatic
        fun newInstance(): ShareDialog {
            val dialog = ShareDialog()
            dialog.isCancelable = true
            return dialog
        }
    }
}
