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

import android.app.Dialog
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Shared keyboard-inset handling for manage add/edit bottom sheets.
 */
object ImeAwareBottomSheet {
    @JvmStatic
    fun applyInsets(contentView: View) {
        val initialLeft = contentView.paddingLeft
        val initialTop = contentView.paddingTop
        val initialRight = contentView.paddingRight
        val initialBottom = contentView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(contentView) { view, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottomInset = maxOf(ime.bottom, bars.bottom)
            view.setPadding(initialLeft, initialTop, initialRight, initialBottom + bottomInset)
            insets
        }
        ViewCompat.requestApplyInsets(contentView)
    }

    @JvmStatic
    fun expandForIme(fragment: BottomSheetDialogFragment) {
        val dialog = fragment.dialog ?: return

        val window = dialog.window
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
            } else {
                setLegacyAdjustResize(window)
            }
        }

        if (dialog !is BottomSheetDialog) {
            return
        }

        val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return

        val params = bottomSheet.layoutParams
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        bottomSheet.layoutParams = params

        val behavior: BottomSheetBehavior<FrameLayout> = dialog.behavior
        behavior.isFitToContents = false
        behavior.expandedOffset = 0
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    @Suppress("DEPRECATION")
    private fun setLegacyAdjustResize(window: Window) {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
}
