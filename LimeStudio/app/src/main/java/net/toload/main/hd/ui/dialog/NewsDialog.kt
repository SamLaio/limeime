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

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import androidx.fragment.app.DialogFragment
import net.toload.main.hd.global.LIME
import net.toload.main.hd.global.LIMEPreferenceManager
import net.toload.main.hd.R

/**
 * Dialog fragment that displays news or HTML content stored in preferences.
 */
class NewsDialog : DialogFragment() {
    var dialogView: View? = null
    var btnHelpDialog: Button? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
    }

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

    fun cancelDialog() {
        dismiss()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, icicle: Bundle?): View? {
        requireDialog().window?.setTitle(resources.getString(R.string.action_news))

        val limePref = LIMEPreferenceManager(requireActivity())
        val root = inflater.inflate(R.layout.fragment_dialog_news, container, false)
        dialogView = root

        val htmlValue = limePref.getParameterString(LIME.LIME_NEWS_CONTENT, "")
        if (htmlValue.isNotEmpty()) {
            val newsContentArea = root.findViewById<WebView>(R.id.newsContentArea)
            newsContentArea.settings.javaScriptEnabled = true
            newsContentArea.loadData(htmlValue, "text/html", "UTF-8")
        }

        btnHelpDialog = root.findViewById(R.id.btnNewsDialog)
        btnHelpDialog?.setOnClickListener { onNewsButtonClick() }
        return root
    }

    private fun onNewsButtonClick() {
        dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        @JvmStatic
        fun newInstance(): NewsDialog {
            val dialog = NewsDialog()
            dialog.isCancelable = true
            return dialog
        }
    }
}
