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

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.fragment.app.DialogFragment
import net.toload.main.hd.data.Keyboard
import net.toload.main.hd.R
import net.toload.main.hd.ui.view.ManageImFragment

/**
 * Dialog fragment for selecting a keyboard for an IM table.
 */
class ManageImKeyboardDialog : DialogFragment(), AdapterView.OnItemClickListener {
    private var keyboardlist: List<Keyboard> = emptyList()
    private var listSelectKeyboard: ListView? = null
    private var code: String? = null
    private var fragment: ManageImFragment? = null

    fun setCode(code: String?) {
        this.code = code
    }

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
        requireDialog().window?.setTitle(resources.getString(R.string.manage_select_keyboard))

        val view = inflater.inflate(R.layout.fragment_dialog_keyboard, container, false)
        listSelectKeyboard = view.findViewById(R.id.listSelectKeyboard)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val host = fragment ?: return
        keyboardlist = host.keyboardList.filterNotNull()
        val listitems = Array(keyboardlist.size) { i -> keyboardlist[i].desc }

        val adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_single_choice, listitems)
        val listView = listSelectKeyboard ?: return
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        listView.onItemClickListener = this

        val current = host.currentKeyboard
        if (current != null) {
            for (i in keyboardlist.indices) {
                if (keyboardlist[i].code == current.code) {
                    listView.setItemChecked(i, true)
                    break
                }
            }
        }
    }

    override fun onSaveInstanceState(icicle: Bundle) {
        super.onSaveInstanceState(icicle)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val keyboard = keyboardlist[position]
        val host = fragment ?: return
        host.setIMKeyboard(code, keyboard.code)
        host.updateKeyboard(keyboard.code)
        dismiss()
    }

    fun setFragment(fragment: ManageImFragment?, code: String?) {
        this.code = code
        this.fragment = fragment
    }

    companion object {
        @JvmStatic
        fun newInstance(): ManageImKeyboardDialog {
            val dialog = ManageImKeyboardDialog()
            dialog.isCancelable = true
            return dialog
        }
    }
}
