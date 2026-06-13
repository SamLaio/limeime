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
import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.MaterialColors
import java.util.ArrayList
import java.util.Locale
import java.util.Objects
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.data.Keyboard
import net.toload.main.hd.data.Record
import net.toload.main.hd.global.LIME
import net.toload.main.hd.R
import net.toload.main.hd.ui.controller.ManageImController
import net.toload.main.hd.ui.dialog.ManageImAddSheet
import net.toload.main.hd.ui.dialog.ManageImEditSheet
import net.toload.main.hd.ui.dialog.ManageImKeyboardDialog
import net.toload.main.hd.ui.LIMESettings
import android.content.DialogInterface
import android.view.ViewGroup.MarginLayoutParams
import android.widget.CompoundButton
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import net.toload.main.hd.global.LIMEPreferenceManager

/**
 * Fragment that displays and manages IM records for a specific IM table.
 * 
 * 
 * Provides UI for searching, paging, adding, editing and deleting mapping
 * records. Delegates data operations to `ManageImController` and implements
 * the `ManageImView` contract for controller-driven updates.
 */
class ManageImFragment : Fragment(), ManageImView {
    private val TAG = "ManageImFragment"

    private var manageImController: ManageImController? = null
    private var gridManageIm: RecyclerView? = null

    private var btnManageImPrevious: Button? = null
    private var btnManageImNext: Button? = null

    private var txtNavigationInfo: TextView? = null

    private var wordlist: MutableList<Record?>? = null
    private var keyboardlist: MutableList<Keyboard>? = null

    private var page = 0
    private var total = 0
    private var searchroot = true
    private val searchreset = false

    private var prequery: String? = ""

    private var table: String? = null
    private var activity: Activity? = null
    private var adapter: ManageImAdapter? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView: View = inflater.inflate(R.layout.fragment_manage_im, container, false)

        // Back navigation toolbar
        val toolbar: MaterialToolbar? =
            rootView.findViewById<MaterialToolbar?>(R.id.manage_im_toolbar)
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(View.OnClickListener { v: View? ->
                val parent = getParentFragment()
                if (parent != null) {
                    parent.getChildFragmentManager().popBackStack()
                }
            })

            toolbar.inflateMenu(R.menu.menu_manage_im)
            tintToolbarMenuIcons(toolbar)

            toolbar.setOnMenuItemClickListener { item: MenuItem? ->
                if (item!!.getItemId() == R.id.action_manage_im_add) {
                    val sheet: ManageImAddSheet = ManageImAddSheet.newInstance()
                    sheet.setFragment(this)
                    sheet.show(getParentFragmentManager(), "addsheet")
                    true
                } else {
                    false
                }
            }
        }

        // Handle system back gesture and hardware back button.
        // The toolbar ← alone is unreliable near the left-edge gesture zone on Android 10+;
        // OnBackPressedCallback intercepts both the swipe-back gesture and the KEYCODE_BACK key.
        requireActivity().onBackPressedDispatcher.addCallback(
            getViewLifecycleOwner(),
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val parent = getParentFragment()
                    if (parent != null) {
                        parent.getChildFragmentManager().popBackStack()
                    }
                }
            })

        this.activity = this.getActivity()

        if (activity is LIMESettings) {
            val controller = (activity as LIMESettings).getManageImController()
            this.manageImController = controller
            if (controller != null) {
                controller.setManageImView(this)
            } else {
                Log.w(TAG, "ManageImController is null; UI operations may fail")
            }
        } else {
            Log.w(TAG, "Activity is not LIMESettings; ManageImController unavailable")
        }

        // Push pagination bar above the activity's BottomNavigationView so it isn't clipped
        val paginationBar = rootView.findViewById<View?>(R.id.pagination_bar)
        val bottomNav = requireActivity().findViewById<View?>(R.id.main_bottom_nav)
        if (paginationBar != null && bottomNav != null) {
            bottomNav.post(Runnable {
                val navHeight = bottomNav.getHeight()
                if (navHeight > 0 && paginationBar.getLayoutParams() is MarginLayoutParams) {
                    val lp: MarginLayoutParams =
                        paginationBar.getLayoutParams() as MarginLayoutParams
                    lp.bottomMargin = navHeight
                    paginationBar.setLayoutParams(lp)
                }
            })
        }

        val grid = rootView.findViewById<RecyclerView>(R.id.gridManageIm)
        this.gridManageIm = grid
        // TODO: add ItemTouchHelper for swipe-to-edit / swipe-to-delete (future pass)
        val lm: LinearLayoutManager =
            LinearLayoutManager(activity)
        grid.layoutManager = lm
        grid.addItemDecoration(
            DividerItemDecoration(activity, lm.getOrientation())
        )
        this.adapter = ManageImAdapter()
        this.adapter!!.setOnItemClickListener(ManageImAdapter.OnItemClickListener { record: Record?, position: Int ->
            val sheet: ManageImEditSheet = ManageImEditSheet.newInstance()
            sheet.setFragment(this, record)
            sheet.show(getParentFragmentManager(), "editsheet")
        })
        grid.adapter = this.adapter

        // Large heading below toolbar
        val tvImLabelHeading: TextView? = rootView.findViewById<TextView?>(R.id.tv_im_label_heading)

        // Segmented control: 字根 / 文字
        val toggleGroupManageIm: MaterialButtonToggleGroup =
            rootView.findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupManageIm)
        toggleGroupManageIm.addOnButtonCheckedListener(MaterialButtonToggleGroup.OnButtonCheckedListener { group: MaterialButtonToggleGroup?, checkedId: Int, isChecked: Boolean ->
            if (isChecked) {
                searchroot = (checkedId == R.id.btnFilterCode)
                total = 0
                prequery = ""
                searchword(null)
            }
        })

        // Inline search bar
        val edtManageImSearch: EditText = rootView.findViewById<EditText>(R.id.edtManageImSearch)
        edtManageImSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = if (s != null) s.toString().trim { it <= ' ' } else ""
                page = 0
                searchword(if (q.isEmpty()) null else q)
            }
        })

        this.btnManageImNext = rootView.findViewById<Button>(R.id.btnManageImNext)
        this.btnManageImNext!!.setEnabled(false)
        this.btnManageImNext!!.setOnClickListener(View.OnClickListener { v: View? ->
            val checkrecord = LIME.IM_MANAGE_DISPLAY_AMOUNT * (page + 1)
            if (checkrecord < total) {
                page++
            }
            searchword()
        })
        this.btnManageImPrevious = rootView.findViewById<Button>(R.id.btnManageImPrevious)
        this.btnManageImPrevious!!.setEnabled(false)
        this.btnManageImPrevious!!.setOnClickListener(View.OnClickListener { v: View? ->
            if (page > 0) {
                page--
            }
            searchword()
        })

        this.txtNavigationInfo = rootView.findViewById<TextView?>(R.id.txtNavigationInfo)

        // initial imConfigFullNamelist via controller
        val imConfigFullNamelist: MutableList<ImConfig> =
            manageImController?.imConfigFullNameList ?: ArrayList<ImConfig>()

        // Set large heading to the IM's display name (toolbar title stays empty)
        if (tvImLabelHeading != null && table != null) {
            for (imConfig in imConfigFullNamelist) {
                if (imConfig.code == table) {
                    tvImLabelHeading.setText(imConfig.desc)
                    break
                }
            }
        }

        // Diagnostic: ensure table is set before attempting to load records
        Log.i(
            TAG,
            "onCreateView: table=" + table + ", imController=" + (manageImController != null)
        )
        if (table == null || table!!.isEmpty()) {
            Log.e(TAG, "IM table is not set; aborting record load")
            if (activity != null) {
                Toast.makeText(activity, R.string.manage_im_error_no_table, Toast.LENGTH_LONG)
                    .show()
            }
        } else if (manageImController == null) {
            Log.e(TAG, "ImController is null; cannot load records")
            if (activity != null) {
                Toast.makeText(activity, R.string.manage_im_error_no_controller, Toast.LENGTH_LONG)
                    .show()
            }
        } else {
            searchword(null)
        }

        return rootView
    }

    private fun tintToolbarMenuIcons(toolbar: MaterialToolbar) {
        val tint: Int = MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface)
        for (i in 0..<toolbar.getMenu().size()) {
            val icon: Drawable? = toolbar.getMenu().getItem(i).getIcon()
            if (icon != null) {
                icon.mutate().setTint(tint)
            }
        }
    }

    @JvmOverloads
    fun searchword(curquery: String? = prequery) {
        val offset = LIME.IM_MANAGE_DISPLAY_AMOUNT * page
        val limit = LIME.IM_MANAGE_DISPLAY_AMOUNT

        if (curquery != prequery) {
            page = 0
        }

        manageImController?.loadRecordsAsync(table ?: return, curquery, searchroot, offset, limit)
        prequery = curquery
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val activity: Activity = context as Activity
        checkNotNull(getArguments())
        // Set the table early so subsequent lifecycle methods have access to it
        this.table = getArguments()!!.getString(ARG_SECTION_CODE)
        (activity as LIMESettings).onSectionAttached(
            getArguments()!!.getInt(ARG_SECTION_NUMBER)
        )
    }

    override fun onDestroy() {
        super.onDestroy()


        this.wordlist = null

        val controller = manageImController
        if (controller != null) {
            controller.setManageImView(null)
            manageImController = null
        }
    }


    fun updateGridView(wordlist: MutableList<Record?>?) {
        // Ensure UI updates happen on the main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            val act = activity
            if (act != null) {
                act.runOnUiThread(Runnable { updateGridView(wordlist) })
                return
            }
        }

        this.wordlist = if (wordlist != null) wordlist else ArrayList<Record?>()

        val startrecord = LIME.IM_MANAGE_DISPLAY_AMOUNT * page
        val endrecord = Math.min(LIME.IM_MANAGE_DISPLAY_AMOUNT * (page + 1), total)

        this.btnManageImPrevious!!.setEnabled(page > 0)
        this.btnManageImNext!!.setEnabled(LIME.IM_MANAGE_DISPLAY_AMOUNT * (page + 1) < total)

        if (adapter != null) {
            adapter!!.submitList(this.wordlist)
            gridManageIm?.scrollToPosition(0)
        }

        var totalPages = (total + LIME.IM_MANAGE_DISPLAY_AMOUNT - 1) / LIME.IM_MANAGE_DISPLAY_AMOUNT
        if (totalPages < 1) totalPages = 1
        val formattedTotal = String.format(Locale.US, "%,d", total)
        val nav = getString(R.string.manage_page_info, page + 1, totalPages, formattedTotal)

        Log.i(
            TAG,
            "updateGridView(): total=" + total + ", page=" + page + ", start=" + startrecord + ", end=" + endrecord + ", wordlistSize=" + (if (this.wordlist == null) 0 else this.wordlist!!.size)
        )
        this.txtNavigationInfo?.setText(nav)
    }

    fun removeRecord(id: Int) {
        manageImController?.deleteRecord(this.table ?: return, id.toLong())
    }

    fun addRecord(code: String, score: Int, word: String?) {
        var word = word
        if (word != null) {
            word = word.trim { it <= ' ' }
        }

        manageImController?.addRecord(this.table ?: return, code, word.orEmpty(), score)
    }

    fun updateRecord(id: Int, code: String?, score: Int, word: String?) {
        var word = word
        if (word != null) {
            word = word.trim { it <= ' ' }
        }

        manageImController?.updateRecord(this.table ?: return, id.toLong(), code, word, score)
    }

    fun updateKeyboard(keyboard: String?) {
        // Use controller for keyboard operations
        val controller = manageImController
        if (keyboardlist == null && controller != null) {
            keyboardlist = controller.keyboardList
        }
        checkNotNull(keyboardlist)
        for (k in keyboardlist) {
            if (k.code == keyboard) {
                controller?.setIMKeyboard(table ?: return, k)
            }
        }
    }

    val keyboardList: MutableList<Keyboard?>
        /**
         * Expose keyboard list to handlers/dialogs
         */
        get() = manageImController?.keyboardList?.toMutableList() ?: ArrayList<Keyboard?>()

    val currentKeyboard: Keyboard?
        /**
         * Returns the IM's currently configured keyboard, or null if none is set.
         * Exposed for dialogs that need to highlight the current selection.
         */
        get() = manageImController?.getCurrentKeyboard(table)

    /**
     * Helper to set IM keyboard via controller
     */
    fun setIMKeyboard(table: String?, keyboardCode: String?) {
        val controller = manageImController
        if (controller != null) {
            // Find the keyboard object and set it
            val list: MutableList<Keyboard> = controller.keyboardList
            for (k in list) {
                if (k.code == keyboardCode) {
                    controller.setIMKeyboard(table ?: return, k)
                    return
                }
            }
        }
    }

    // ========== ManageImView Interface Implementation ==========
    override fun displayRecords(records: List<Record>?) {
        Log.i(TAG, "displayRecords(): records=" + (if (records == null) "null" else records.size))
        this.wordlist = records?.toMutableList()
        updateGridView(this.wordlist)
    }

    override fun updateRecordCount(count: Int) {
        this.total = count
        if (txtNavigationInfo != null) {
            var totalPages =
                (total + LIME.IM_MANAGE_DISPLAY_AMOUNT - 1) / LIME.IM_MANAGE_DISPLAY_AMOUNT
            if (totalPages < 1) totalPages = 1
            val formattedTotal = String.format(Locale.US, "%,d", total)
            txtNavigationInfo?.setText(
                getString(
                    R.string.manage_page_info,
                    page + 1, totalPages, formattedTotal
                )
            )
        }
    }

    override fun showAddRecordDialog() {
        val sheet: ManageImAddSheet = ManageImAddSheet.newInstance()
        sheet.setFragment(this)
        sheet.show(getParentFragmentManager(), "addsheet")
    }

    override fun showEditRecordDialog(record: Record?) {
        val sheet: ManageImEditSheet = ManageImEditSheet.newInstance()
        sheet.setFragment(this, record)
        sheet.show(getParentFragmentManager(), "editsheet")
    }

    override fun showDeleteConfirmDialog(id: Long) {
        // Show confirmation dialog before deleting
        AlertDialog.Builder(activity)
            .setTitle(getResources().getString(R.string.dialog_delete_title))
            .setMessage(getResources().getString(R.string.dialog_delete_message))
            .setPositiveButton(
                getResources().getString(R.string.dialog_confirm),
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                    removeRecord(id.toInt())
                })
            .setNegativeButton(getResources().getString(R.string.dialog_cancel), null)
            .show()
    }

    override fun refreshRecordList() {
        searchword()
    }

    override fun onError(message: String?) {
        if (message == null) return
        Log.e(TAG, message)
        // Ensure the loading spinner is hidden on error to avoid a stuck UI
        if (activity != null) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        }
    }


    companion object {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private const val ARG_SECTION_NUMBER = "section_number"
        private const val ARG_SECTION_CODE = "section_code"

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        @JvmStatic
        fun newInstance(sectionNumber: Int, code: String?): ManageImFragment {
            val fragment = ManageImFragment()
            val args: Bundle = Bundle()
            args.putInt(ARG_SECTION_NUMBER, sectionNumber)
            args.putString(ARG_SECTION_CODE, code)
            fragment.setArguments(args)
            return fragment
        }
    }
}
