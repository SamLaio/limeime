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
import android.content.DialogInterface
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
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import java.util.ArrayList
import java.util.Locale
import java.util.Objects
import net.toload.main.hd.data.Related
import net.toload.main.hd.global.LIME
import net.toload.main.hd.global.LIMEPreferenceManager
import net.toload.main.hd.R
import net.toload.main.hd.ui.controller.ManageImController
import net.toload.main.hd.ui.dialog.ManageRelatedAddSheet
import net.toload.main.hd.ui.dialog.ManageRelatedEditSheet
import net.toload.main.hd.ui.LIMESettings

/**
 * Fragment that displays and manages related-phrase entries.
 * 
 * 
 * This fragment hosts the related-phrase grid, provides search and
 * pagination controls, and delegates data operations to
 * `ManageImController` via the `ManageRelatedView` contract.
 */
class ManageRelatedFragment : Fragment(), ManageRelatedView {
    private var manageImController: ManageImController? = null
    private var gridManageRelated: RecyclerView? = null

    private var btnManageRelatedPrevious: Button? = null
    private var btnManageRelatedNext: Button? = null

    private var txtNavigationInfo: TextView? = null

    private var relatedlist: MutableList<Related?>? = null

    private var page = 0
    private var total = 0
    private val searchReset = false

    private var preQuery: String? = ""

    private var activity: Activity? = null
    private var adapter: ManageRelatedAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView: View = inflater.inflate(R.layout.fragment_manage_related, container, false)

        // Back navigation toolbar
        val toolbar: MaterialToolbar? =
            rootView.findViewById<MaterialToolbar?>(R.id.manage_related_toolbar)
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(View.OnClickListener { v: View? ->
                val parent = getParentFragment()
                if (parent != null) {
                    parent.getChildFragmentManager().popBackStack()
                }
            })

            toolbar.inflateMenu(R.menu.menu_manage_related)
            tintToolbarMenuIcons(toolbar)

            toolbar.setOnMenuItemClickListener { item: MenuItem? ->
                if (item!!.getItemId() == R.id.action_manage_related_add) {
                    val sheet: ManageRelatedAddSheet = ManageRelatedAddSheet.newInstance()
                    sheet.setFragment(this)
                    sheet.show(getParentFragmentManager(), "addsheet")
                    true
                } else {
                    false
                }
            }
        }

        // Inline search bar
        val edtManageRelatedSearch: EditText? =
            rootView.findViewById<EditText?>(R.id.edtManageRelatedSearch)
        if (edtManageRelatedSearch != null) {
            edtManageRelatedSearch.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val q = if (s != null) s.toString().trim { it <= ' ' } else ""
                    page = 0
                    searchRelated(if (q.isEmpty()) null else q)
                }
            })
        }

        // Handle system back gesture and hardware back button.
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


        // Get ManageImController from LIMESettings
        if (this.activity is LIMESettings) {
            val controller = (this.activity as LIMESettings).getManageImController()
            this.manageImController = controller
            if (controller != null) {
                controller.setManageRelatedView(this)
            }
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

        val grid = rootView.findViewById<RecyclerView>(R.id.gridManageRelated)
        this.gridManageRelated = grid
        val lm: LinearLayoutManager =
            LinearLayoutManager(activity)
        grid.layoutManager = lm
        grid.addItemDecoration(
            DividerItemDecoration(activity, lm.getOrientation())
        )
        this.adapter = ManageRelatedAdapter(activity)
        this.adapter!!.setOnItemClickListener(ManageRelatedAdapter.OnItemClickListener { related: Related?, position: Int ->
            val sheet: ManageRelatedEditSheet = ManageRelatedEditSheet.newInstance()
            sheet.setFragment(this, related)
            sheet.show(getParentFragmentManager(), "editsheet")
        })
        grid.adapter = this.adapter

        this.btnManageRelatedNext = rootView.findViewById<Button>(R.id.btnManageRelatedNext)
        this.btnManageRelatedNext!!.setEnabled(false)
        this.btnManageRelatedNext!!.setOnClickListener(View.OnClickListener { v: View? ->
            val checkrecord = LIME.IM_MANAGE_DISPLAY_AMOUNT * (page + 1)
            if (checkrecord < total) {
                page++
            }
            searchRelated()
        })
        this.btnManageRelatedPrevious = rootView.findViewById<Button>(R.id.btnManageRelatedPrevious)
        this.btnManageRelatedPrevious!!.setEnabled(false)
        this.btnManageRelatedPrevious!!.setOnClickListener(View.OnClickListener { v: View? ->
            if (page > 0) {
                page--
            }
            searchRelated()
        })

        // TODO: add ItemTouchHelper for swipe-to-edit / swipe-to-delete (future pass)
        this.txtNavigationInfo = rootView.findViewById<TextView?>(R.id.txtNavigationInfo)

        searchRelated(null)

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
    fun searchRelated(curQuery: String? = preQuery) {
        val offset = LIME.IM_MANAGE_DISPLAY_AMOUNT * page
        val limit = LIME.IM_MANAGE_DISPLAY_AMOUNT

        if ((curQuery == null && total == 0) || curQuery != preQuery) {
            page = 0
        }

        manageImController?.loadRelatedPhrases(curQuery, offset, limit)
        preQuery = curQuery
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val activity: Activity = context as Activity
        checkNotNull(getArguments())
        (activity as LIMESettings).onSectionAttached(
            getArguments()!!.getInt(ARG_SECTION_NUMBER)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        this.relatedlist = null
        manageImController?.getSearchServer()?.initialCache()
    }


    fun updateGridView(relatedlist: MutableList<Related?>?) {
        // Ensure UI updates happen on the main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            val act = activity
            if (act != null) {
                act.runOnUiThread(Runnable { updateGridView(relatedlist) })
                return
            }
        }

        this.relatedlist = if (relatedlist != null) relatedlist else ArrayList<Related?>()

        val startRecord = LIME.IM_MANAGE_DISPLAY_AMOUNT * page
        val endRecord = Math.min(LIME.IM_MANAGE_DISPLAY_AMOUNT * (page + 1), total)

        this.btnManageRelatedPrevious!!.setEnabled(page > 0)
        this.btnManageRelatedNext!!.setEnabled(LIME.IM_MANAGE_DISPLAY_AMOUNT * (page + 1) < total)

        if (adapter != null) {
            adapter!!.submitList(this.relatedlist)
            gridManageRelated?.scrollToPosition(0)
        }

        var totalPages = (total + LIME.IM_MANAGE_DISPLAY_AMOUNT - 1) / LIME.IM_MANAGE_DISPLAY_AMOUNT
        if (totalPages < 1) totalPages = 1
        val formattedTotal = String.format(Locale.US, "%,d", total)
        val nav = getString(R.string.manage_page_info, page + 1, totalPages, formattedTotal)

        this.txtNavigationInfo?.setText(nav)
    }

    fun removeRelated(id: Int) {
        if (this.relatedlist != null) {
            for (i in this.relatedlist!!.indices) {
                if (id == this.relatedlist!![i]?.getIdAsInt()) {
                    this.relatedlist!!.removeAt(i)
                    break
                }
            }
        }
        val controller = manageImController
        if (controller != null) {
            controller.deleteRelatedPhrase(id.toLong())
            searchRelated()
        }
    }

    fun addRelated(pword: String?, cword: String?, score: Int) {
        val controller = manageImController
        if (controller != null) {
            controller.addRelatedPhrase(pword, cword, score)
            // Refresh the grid after add
            searchRelated()
        }
    }

    fun updateRelated(id: Int, pword: String?, cword: String?, score: Int) {
        val controller = manageImController
        if (controller != null) {
            controller.updateRelatedPhrase(id.toLong(), pword, cword, score)
            // Refresh the grid after update to show new score
            searchRelated()
        }
    }

    // ========== ManageRelatedView Interface Implementation ==========
    override fun displayRelatedPhrases(phrases: List<Related>?) {
        this.relatedlist = phrases?.toMutableList()
        // Ensure UI updates on main thread
        if (getActivity() != null) {
            requireActivity().runOnUiThread(Runnable { updateGridView(this.relatedlist) })
        } else {
            updateGridView(this.relatedlist)
        }
    }

    override fun updatePhraseCount(count: Int) {
        this.total = count
        updateNavigationInfo()
    }

    override fun showAddPhraseDialog() {
        val sheet: ManageRelatedAddSheet = ManageRelatedAddSheet.newInstance()
        sheet.setFragment(this)
        sheet.show(getParentFragmentManager(), "addsheet")
    }

    override fun showEditPhraseDialog(phrase: Related?) {
        val sheet: ManageRelatedEditSheet = ManageRelatedEditSheet.newInstance()
        sheet.setFragment(this, phrase)
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
                    removeRelated(id.toInt())
                })
            .setNegativeButton(getResources().getString(R.string.dialog_cancel), null)
            .show()
    }

    override fun refreshPhraseList() {
        searchRelated()
    }


    override fun onError(message: String?) {
        if (message == null) return
        Log.e(TAG, message)
        if (activity != null) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        }
    }


    private fun updateNavigationInfo() {
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

    companion object {
        private const val TAG = "ManageRelatedFragment"

        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private const val ARG_SECTION_NUMBER = "section_number"

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        @JvmStatic
        fun newInstance(sectionNumber: Int): ManageRelatedFragment {
            val fragment = ManageRelatedFragment()
            val args: Bundle = Bundle()
            args.putInt(ARG_SECTION_NUMBER, sectionNumber)
            fragment.setArguments(args)
            return fragment
        }
    }
}
