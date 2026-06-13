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
import android.content.Context
import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.global.LIME
import net.toload.main.hd.R
import net.toload.main.hd.ui.controller.ManageImController
import net.toload.main.hd.ui.LIMESettings

/**
 * Dialog fragment to import IM data from text or .limedb files.
 * 
 * 
 * Supports two modes: importing raw text (suggest IM table mapping) and
 * importing a .limedb file (restore into a specific IM table). Hosts UI to
 * select target table and restore options.
 */
class ImportDialog : DialogFragment() {
    var manageImController: ManageImController? = null
    var activity: Activity? = null
    var dialogView: View? = null

    // ImportDialog cancel button
    var btnImportCancel: Button? = null

    // ImportDialog import related button
    var btnImportRelated: Button? = null

    // ImportDialog checkbox to restore user records
    var chkImportRestoreUserRecords: CheckBox? = null

    // ImportDialog importing text send from other apps
    var importText: String? = null

    // ImportDialog file path send from other apps
    var filePath: String? = null
    var importMode: Int = 0

    // Callback interface for file import mode
    interface OnImportIMSelectedListener {
        fun onImportDialogImSelected(tableName: String?, restoreUserRecords: Boolean)
    }

    private var listener: OnImportIMSelectedListener? = null

    fun setOnImportTypeSelectedListener(listener: OnImportIMSelectedListener?) {
        this.listener = listener
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)
        checkNotNull(getArguments())
        importMode = getArguments()!!.getInt(IMPORT_MODE, IMPORT_MODE_TEXT)
        if (importMode == IMPORT_MODE_TEXT) {
            importText = getArguments()!!.getString(IMPORT_TEXT)
        } else {
            filePath = getArguments()!!.getString(FILE_PATH)
        }
        // Set listener if activity implements the interface
        if (context is OnImportIMSelectedListener) {
            listener = context as OnImportIMSelectedListener
        }
    }


    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        this.setCancelable(false)
    }


    override fun onResume() {
        super.onResume()

        checkNotNull(getDialog())
        getDialog()!!.setOnKeyListener { _: DialogInterface?, keyCode: Int, _: KeyEvent? ->
            if ((keyCode == KeyEvent.KEYCODE_BACK)) {
                // To dismiss the fragment when the back-button is pressed.
                dismiss()
                true
            } else false
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        icicle: Bundle?
    ): View {
        checkNotNull(getDialog())
        activity = getActivity()
        if (activity is LIMESettings) {
            manageImController = (activity as LIMESettings).getManageImController()
        }
        getDialog()!!.getWindow()!!.setTitle(getResources().getString(R.string.import_dialog_title))

        dialogView = inflater.inflate(R.layout.fragment_dialog_import, container, false)

        btnImportCancel = dialogView!!.findViewById<Button>(R.id.btnImportCancel)
        btnImportCancel!!.setOnClickListener(View.OnClickListener { v: View? -> dismiss() })
        btnImportRelated = dialogView!!.findViewById<Button>(R.id.btnImportRelated)
        chkImportRestoreUserRecords = dialogView!!.findViewById<CheckBox>(R.id.chkImportRestoreLearning)


        // Show checkbox only in IMPORT_MODE_FILE
        if (importMode == IMPORT_MODE_FILE) {
            chkImportRestoreUserRecords!!.setVisibility(View.VISIBLE)
            chkImportRestoreUserRecords!!.setChecked(true) // Default to checked
            btnImportRelated!!.setVisibility(View.GONE)
        } else {
            chkImportRestoreUserRecords!!.setVisibility(View.GONE)
            btnImportRelated!!.setVisibility(View.VISIBLE)
        }

        val imConfigFullNameList: MutableList<ImConfig> =
            manageImController!!.imConfigFullNameList

        // Setup all IM type buttons using imList
        setupImportDialogButton(
            dialogView!!,
            R.id.btnImportCustom,
            LIME.DB_TABLE_CUSTOM,
            LIME.IM_CUSTOM,
            imConfigFullNameList
        )
        setupImportDialogButton(
            dialogView!!,
            R.id.btnImportArray,
            LIME.DB_TABLE_ARRAY,
            LIME.IM_ARRAY,
            imConfigFullNameList
        )
        setupImportDialogButton(
            dialogView!!,
            R.id.btnImportArray10,
            LIME.DB_TABLE_ARRAY10,
            LIME.IM_ARRAY10,
            imConfigFullNameList
        )
        setupImportDialogButton(
            dialogView!!,
            R.id.btnImportCj,
            LIME.DB_TABLE_CJ,
            LIME.IM_CJ,
            imConfigFullNameList
        )
        setupImportDialogButton(
            dialogView!!,
            R.id.btnImportCj5,
            LIME.DB_TABLE_CJ5,
            LIME.IM_CJ5,
            imConfigFullNameList
        )
        setupImportDialogButton(
            dialogView!!,
            R.id.btnImportDayi,
            LIME.DB_TABLE_DAYI,
            LIME.IM_DAYI,
            imConfigFullNameList
        )
        setupImportDialogButton(
            dialogView!!,
            R.id.btnImportEcj,
            LIME.DB_TABLE_ECJ,
            LIME.IM_ECJ,
            imConfigFullNameList
        )
        setupImportDialogButton(
            dialogView!!,
            R.id.btnImportEz,
            LIME.DB_TABLE_EZ,
            LIME.IM_EZ,
            imConfigFullNameList
        )
        setupImportDialogButton(
            dialogView!!,
            R.id.btnImportPhonetic,
            LIME.DB_TABLE_PHONETIC,
            LIME.IM_PHONETIC,
            imConfigFullNameList
        )
        setupImportDialogButton(
            dialogView!!,
            R.id.btnImportPinyin,
            LIME.DB_TABLE_PINYIN,
            LIME.IM_PINYIN,
            imConfigFullNameList
        )
        setupImportDialogButton(
            dialogView!!,
            R.id.btnImportScj,
            LIME.DB_TABLE_SCJ,
            LIME.IM_SCJ,
            imConfigFullNameList
        )
        setupImportDialogButton(
            dialogView!!,
            R.id.btnImportWb,
            LIME.DB_TABLE_WB,
            LIME.IM_WB,
            imConfigFullNameList
        )
        setupImportDialogButton(
            dialogView!!,
            R.id.btnImportHs,
            LIME.DB_TABLE_HS,
            LIME.IM_HS,
            imConfigFullNameList
        )

        // Setup related table button
        if (importMode == IMPORT_MODE_TEXT && importText != null && importText!!.length > 1) {
            setupImportDialogButton(
                dialogView!!,
                R.id.btnImportRelated,
                LIME.DB_TABLE_RELATED,
                LIME.DB_TABLE_RELATED,
                imConfigFullNameList
            )
        } else {
            setupButtonDisabled(btnImportRelated!!)
        }

        return dialogView!!
    }

    /**
     * / **
     * Sets up a button based on import mode and table state.
     * @param view The parent view
     * @param buttonId The button resource ID
     * @param tableName The database table name
     * @param imCode The IM name
     * @param imConfigList List of existing Im objects
     */
    private fun setupImportDialogButton(
        view: View,
        buttonId: Int,
        tableName: String?,
        imCode: String,
        imConfigList: MutableList<ImConfig>
    ) {
        val button = view.findViewById<Button>(buttonId)
        var shouldShow: Boolean
        if (importMode == IMPORT_MODE_TEXT) {
            // Enable if any Im in imList has code matching imCode
            shouldShow = false
            for (imConfig in imConfigList) {
                if (imConfig.code == imCode) {
                    shouldShow = true
                    break
                }
            }
        } else if (importMode == IMPORT_MODE_FILE) {
            // Only show empty tables in IMPORT_MODE_FILE
            shouldShow = (manageImController!!.countRecords(tableName) == 0)
        } else {
            shouldShow = false
        }

        if (shouldShow) {
            button.setAlpha(LIME.NORMAL_ALPHA_VALUE)
            button.setTypeface(null, Typeface.BOLD)
            button.setEnabled(true)
            button.setOnClickListener(View.OnClickListener { v: View? -> confirmImportDialog(imCode) })
        } else {
            setupButtonDisabled(button)
        }
    }

    /**
     * Disables a button with visual feedback.
     * @param button The button to disable
     */
    private fun setupButtonDisabled(button: Button) {
        button.setAlpha(LIME.HALF_ALPHA_VALUE)
        button.setTypeface(null, Typeface.ITALIC)
        button.setEnabled(false)
    }

    fun confirmImportDialog(imName: String) {
        // For file mode, directly call listener and dismiss
        if (importMode == IMPORT_MODE_FILE && listener != null) {
            val restoreUserRecords = chkImportRestoreUserRecords!!.isChecked()
            listener!!.onImportDialogImSelected(imName, restoreUserRecords)
            dismiss()
            return
        }

        // For text mode, show confirmation dialog
        val act = activity ?: return
        val builder = AlertDialog.Builder(act)
        val input: EditText = EditText(act)
        val isRelated = imName.equals(LIME.DB_TABLE_RELATED, ignoreCase = true)

        if (isRelated) {
            builder.setTitle(
                act.getResources().getString(R.string.import_dialog_related_title)
            )
                .setMessage(importText)
        } else {
            builder.setTitle(act.getResources().getString(R.string.import_dialog_title))
                .setMessage(importText + getResources().getString(R.string.import_code_hint))
            val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            input.setLayoutParams(lp)
            builder.setView(input)
        }

        builder.setPositiveButton(
            act.getResources().getString(R.string.dialog_confirm),
            DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                if (isRelated) {
                    importToRelatedTable()
                    dismiss()
                } else {
                    val code = if (input.getText() != null) input.getText().toString() else ""
                    if (!code.isEmpty()) {
                        importToImTable(imName, code)
                        dismiss()
                    } else {
                        Toast.makeText(
                            act,
                            getResources().getString(R.string.import_code_empty),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            })
            .setNegativeButton(
                act.getResources().getString(R.string.dialog_cancel),
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> dialog?.dismiss() })
            .show()
    }


    private fun importToRelatedTable() {
        val relatedWords: Array<String?> = splitLeadingCodePoint(importText)
        val pWord = relatedWords[0]
        val cWord = relatedWords[1]

        // Use parameterized query to prevent SQL injection
        manageImController!!.addRelatedPhrase(pWord, cWord, 1)
        Toast.makeText(
            activity,
            getResources().getString(R.string.import_related_success),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun importToImTable(tableName: String?, addCode: String?) {
        // Use parameterized query to prevent SQL injection
        // Add using ManageImController API
        //String code3r = tableName.equals(LIME.DB_TABLE_PHONETIC) ? addCode.replaceAll("[ 3467]", "") : "";

        manageImController!!.addRecord(tableName.orEmpty(), addCode.orEmpty(), importText.orEmpty(), 1)

        Toast.makeText(
            activity,
            getResources().getString(R.string.import_word_success),
            Toast.LENGTH_SHORT
        ).show()
    }

    companion object {
        private fun splitLeadingCodePoint(text: String?): Array<String?> {
            if (text == null || text.isEmpty()) {
                return arrayOf<String?>("", "")
            }
            val end = text.offsetByCodePoints(0, 1)
            return arrayOf<String?>(text.substring(0, end), text.substring(end))
        }

        // Import mode constants
        const val IMPORT_MODE_TEXT: Int = 0 // For handleSendText: show non-empty tables + related
        const val IMPORT_MODE_FILE: Int = 1 // For handleImportTxt: show empty tables only

        private const val IMPORT_TEXT = "import_text" // Bundle key for import text
        private const val FILE_PATH = "file_path" // Bundle key for file path
        private const val IMPORT_MODE = "import_mode" // Bundle key for import mode

        @JvmStatic
        fun newInstance(importText: String?): ImportDialog {
            val btd = ImportDialog()
            val args: Bundle = Bundle()
            args.putString(IMPORT_TEXT, importText)
            args.putInt(IMPORT_MODE, IMPORT_MODE_TEXT)
            btd.setArguments(args)
            btd.setCancelable(true)
            return btd
        }

        @JvmStatic
        fun newInstanceForFile(filePath: String?): ImportDialog {
            val btd = ImportDialog()
            val args: Bundle = Bundle()
            args.putString(FILE_PATH, filePath)
            args.putInt(IMPORT_MODE, IMPORT_MODE_FILE)
            btd.setArguments(args)
            btd.setCancelable(true)
            return btd
        }
    }
}
