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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import net.toload.main.hd.data.Related
import net.toload.main.hd.R
import net.toload.main.hd.ui.view.ManageRelatedFragment

/**
 * Bottom sheet dialog for editing an existing related phrase entry.
 */
class ManageRelatedEditSheet : BottomSheetDialogFragment() {
    private var hostFragment: ManageRelatedFragment? = null
    private var related: Related? = null
    private var score = 0

    fun setFragment(fragment: ManageRelatedFragment?, related: Related?) {
        hostFragment = fragment
        this.related = related
        score = related?.getBasescore() ?: 0
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.sheet_manage_related_edit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ImeAwareBottomSheet.applyInsets(view)

        val edtWord = view.findViewById<TextInputEditText>(R.id.edt_word)
        val edtRelated = view.findViewById<TextInputEditText>(R.id.edt_related)
        val edtScore = view.findViewById<TextInputEditText>(R.id.edt_score)

        val currentRelated = related
        if (currentRelated != null) {
            edtWord.setText(currentRelated.getPword())
            edtRelated.setText(currentRelated.getCword())
            ManageSheetScoreInput.setScore(edtScore, score)
        }

        view.findViewById<View>(R.id.btn_minus).setOnClickListener {
            score = ManageSheetScoreInput.decrement(edtScore, score)
        }
        view.findViewById<View>(R.id.btn_plus).setOnClickListener {
            score = ManageSheetScoreInput.increment(edtScore, score)
        }
        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dismiss()
        }

        view.findViewById<View>(R.id.btn_delete).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_delete_title)
                .setMessage(R.string.dialog_delete_message)
                .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                    val item = related
                    if (hostFragment != null && item != null) {
                        hostFragment?.removeRelated(item.getIdAsInt())
                    }
                    dismiss()
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }

        view.findViewById<View>(R.id.btn_save).setOnClickListener {
            val pword = edtWord.text?.toString()?.trim() ?: ""
            val cword = edtRelated.text?.toString()?.trim() ?: ""
            if (!validateInput(pword, cword)) {
                Toast.makeText(requireContext(), R.string.update_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            score = ManageSheetScoreInput.readScore(edtScore, score)
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_update_title)
                .setMessage(R.string.dialog_update_message)
                .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                    val item = related
                    if (hostFragment != null && item != null) {
                        hostFragment?.updateRelated(item.getIdAsInt(), pword, cword, score)
                    }
                    dismiss()
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }
    }

    override fun onStart() {
        super.onStart()
        ImeAwareBottomSheet.expandForIme(this)
    }

    private fun validateInput(pword: String, cword: String): Boolean {
        return pword.isNotEmpty() && cword.isNotEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hostFragment = null
        related = null
    }

    companion object {
        @JvmStatic
        fun newInstance(): ManageRelatedEditSheet = ManageRelatedEditSheet()
    }
}
