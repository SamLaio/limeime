package net.toload.main.hd.ui.view

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.ArrayList
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.global.LIME
import net.toload.main.hd.R
import net.toload.main.hd.ui.controller.ManageImController
import net.toload.main.hd.ui.LIMESettings
import net.toload.main.hd.ui.viewmodel.ImNavigationViewModel
import android.content.DialogInterface
import android.view.ViewGroup.MarginLayoutParams
import android.widget.CompoundButton
import androidx.recyclerview.widget.DividerItemDecoration
import net.toload.main.hd.global.LIMEPreferenceManager

/**
 * Fragment showing the list of available IMs with enable/disable toggles.
 * Hosted inside TwoPaneHostFragment's list pane.
 */
class ImListFragment : Fragment() {
    private var activity: Activity? = null
    private var manageImController: ManageImController? = null
    private var vm: ImNavigationViewModel? = null
    private var adapter: ImRowAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        activity = getActivity()

        if (activity is LIMESettings) {
            manageImController = (activity as LIMESettings).getManageImController()
        } else {
            Log.w(TAG, "Activity is not LIMESettings; ManageImController unavailable")
        }

        // ViewModel is scoped to TwoPaneHostFragment (parent)
        vm = ViewModelProvider(requireParentFragment())[ImNavigationViewModel::class.java]

        val rootView: View = inflater.inflate(R.layout.fragment_im_list, container, false)

        val fab: FloatingActionButton =
            rootView.findViewById<FloatingActionButton>(R.id.fab_install)
        fab.setOnClickListener(View.OnClickListener { v: View? ->
            vm?.showInstall?.setValue(true)
        })

        // Push FAB above the activity's BottomNavigationView (fragment container fills full screen)
        val bottomNav = requireActivity().findViewById<View?>(R.id.main_bottom_nav)
        if (bottomNav != null) {
            bottomNav.post(Runnable {
                val navHeight = bottomNav.getHeight()
                if (navHeight > 0 && fab.getLayoutParams() is MarginLayoutParams) {
                    val lp: MarginLayoutParams = fab.getLayoutParams() as MarginLayoutParams
                    lp.bottomMargin =
                        navHeight + (16 * getResources().getDisplayMetrics().density).toInt()
                    fab.setLayoutParams(lp)
                }
            })
        }

        val recyclerView: RecyclerView = rootView.findViewById<RecyclerView>(R.id.im_list_recycler)
        recyclerView.setLayoutManager(LinearLayoutManager(requireContext()))
        ScrollableTabHelper.applyToRecyclerView(activity, recyclerView)

        adapter = ImRowAdapter(ArrayList<ImConfig?>())
        recyclerView.setAdapter(adapter)

        loadImList()

        return rootView
    }

    /** Re-query the IM config table and refresh the list. Safe to call from any thread.  */
    fun refreshList() {
        loadImList()
    }

    private fun loadImList() {
        val ctrl: ManageImController? = manageImController
        if (ctrl == null) return
        val act: Activity? = activity

        Thread(Runnable {
            val rawList: MutableList<ImConfig> = ctrl.imConfigFullNameList
            // Filter out the internal emoji dataset — it is not a user-facing Chinese IM
            val list: MutableList<ImConfig?> = ArrayList<ImConfig?>()
            for (im in rawList) {
                if ("emoji" != im.code) {
                    list.add(im)
                }
            }
            if (act == null) return@Runnable
            act.runOnUiThread(Runnable {
                if (!isAdded() || activity == null) return@Runnable
                adapter!!.setData(list)
            })
        }).start()
    }

    override fun onResume() {
        super.onResume()
        // Refresh the list when returning from IM Detail (e.g., after Remove-IM)
        if (manageImController != null && adapter != null) {
            loadImList()
        }
    }

    override fun onDestroyView() {
        val root = getView()
        if (root != null) {
            val rv: RecyclerView? = root.findViewById<RecyclerView?>(R.id.im_list_recycler)
            if (rv != null) {
                rv.setAdapter(null)
            }
        }
        super.onDestroyView()
        activity = null
        manageImController = null
        vm = null
        adapter = null
    }

    /**
     * Returns true if `activeImCode` corresponds to an IM that is currently
     * enabled (not disabled) in the in-memory list. Used to decide whether a newly
     * enabled IM should become the active IM. Reads the adapter's in-memory list so
     * it is not subject to the async DB write performed by setImEnabled().
     */
    private fun isActiveImEnabled(activeImCode: String?): Boolean {
        if (activeImCode == null || adapter == null) return false
        val list: MutableList<ImConfig?>? = adapter!!.getImList()
        if (list == null) return false
        for (im in list) {
            if (im == null || im.code == null) continue
            if (activeImCode == im.code) {
                return !im.isDisable
            }
        }
        return false
    }

    // -------- Adapter --------
    private inner class ImRowAdapter(imList: MutableList<ImConfig?>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder?>() {
        private var imList: MutableList<ImConfig?>

        init {
            this.imList = imList
        }

        fun getImList(): MutableList<ImConfig?> {
            return imList
        }

        fun setData(data: MutableList<ImConfig?>?) {
            this.imList = if (data != null) data else ArrayList<ImConfig?>()
            notifyDataSetChanged()
            val root = getView()
            if (root != null) {
                ScrollableTabHelper.refreshRecyclerViewScrollbar(root.findViewById<RecyclerView>(R.id.im_list_recycler))
            }
        }

        override fun getItemCount(): Int =
            // header(installed) + IM rows + header(related) + related row
            1 + imList.size + 1 + 1

        override fun getItemViewType(position: Int): Int {
            if (position == 0) return TYPE_HEADER // installed header

            val imEnd = 1 + imList.size
            if (position < imEnd) return TYPE_IM
            if (position == imEnd) return TYPE_HEADER // related header

            return TYPE_RELATED
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == TYPE_HEADER) {
                val tv: TextView = TextView(parent.getContext())
                tv.setPadding(32, 24, 32, 8)
                tv.setTypeface(null, Typeface.BOLD)
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                tv.setLayoutParams(
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                return HeaderViewHolder(tv)
            }
            val v: View = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_im_row, parent, false)
            if (viewType == TYPE_RELATED) {
                return RelatedViewHolder(v)
            }
            return ImViewHolder(v)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is HeaderViewHolder) {
                val labelRes: Int =
                    if (position == 0) R.string.im_list_header_installed else R.string.im_list_header_related
                holder.bind(labelRes)
            } else if (holder is RelatedViewHolder) {
                holder.bind()
            } else if (holder is ImViewHolder) {
                // position 0 is header, so IM data starts at position 1
                imList.getOrNull(position - 1)?.let { holder.bind(it) }
            }
        }
    }

    private inner class HeaderViewHolder(itemView: TextView) : RecyclerView.ViewHolder(itemView) {
        val tvHeader: TextView

        init {
            tvHeader = itemView
        }

        fun bind(labelRes: Int) {
            tvHeader.setText(labelRes)
            tvHeader.setClickable(false)
        }
    }

    private inner class ImViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvLabel: TextView
        val switchEnabled: SwitchMaterial

        init {
            tvLabel = itemView.findViewById<TextView>(R.id.tv_im_label)
            switchEnabled = itemView.findViewById<SwitchMaterial>(R.id.switch_im_enabled)
        }

        fun bind(im: ImConfig) {
            tvLabel.setText(im.desc)
            itemView.setAlpha(if (im.isDisable) LIME.HALF_ALPHA_VALUE else 1.0f)

            // Clear listener before setting state to avoid spurious callbacks
            switchEnabled.setOnCheckedChangeListener(null)
            switchEnabled.setChecked(!im.isDisable)
            switchEnabled.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { btn: CompoundButton?, checked: Boolean ->
                im.isDisable = !checked
                itemView.setAlpha(if (checked) 1.0f else LIME.HALF_ALPHA_VALUE)
                val ctrl: ManageImController? = manageImController
                if (ctrl != null) {
                    ctrl.setImEnabled(im.id, checked)
                    val pref: LIMEPreferenceManager =
                        LIMEPreferenceManager(requireContext())
                    pref.syncIMActivatedState(ArrayList<ImConfig?>(ctrl.imConfigFullNameList))
                    // When enabling an IM, make it the active IM if the currently
                    // persisted active IM is not (or no longer) an enabled one. This
                    // ensures the first IM installed/enabled on a fresh install becomes
                    // active instead of leaving activeIM pointing at a default IM whose
                    // keyboard config is not loaded (which falls back to the English
                    // keyboard). Uses the adapter's in-memory list to stay race-free
                    // against the async DB write in setImEnabled().
                    if (checked && !isActiveImEnabled(pref.activeIM)) {
                        pref.activeIM = im.code
                    }
                }
            })

            itemView.setOnClickListener(View.OnClickListener { v: View? ->
                val vmRef: ImNavigationViewModel? = vm
                if (vmRef != null) {
                    vmRef.selectedIm.setValue(im)
                }
            })
        }
    }

    private inner class RelatedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView
        val tvLabel: TextView
        val switchEnabled: SwitchMaterial

        init {
            ivIcon = itemView.findViewById<ImageView>(R.id.iv_im_icon)
            tvLabel = itemView.findViewById<TextView>(R.id.tv_im_label)
            switchEnabled = itemView.findViewById<SwitchMaterial>(R.id.switch_im_enabled)
        }

        fun bind() {
            tvLabel.setText(R.string.im_related_label)
            ivIcon.setImageResource(R.drawable.ic_list_bullet)
            switchEnabled.setVisibility(View.GONE)
            itemView.setAlpha(1.0f)

            itemView.setOnClickListener(View.OnClickListener { v: View? ->
                val parent = getParentFragment()
                if (parent is TwoPaneHostFragment) {
                    val synthetic: ImConfig = ImConfig()
                    synthetic.id = -1
                    synthetic.code = "related"
                    synthetic.desc = itemView.getResources().getString(R.string.im_related_heading)
                    parent.navigateToDetail(
                        ImDetailFragment.newInstance(synthetic)
                    )
                }
            })
        }
    }

    companion object {
        private const val TAG = "ImListFragment"
        private const val TYPE_IM = 0
        private const val TYPE_RELATED = 1
        private const val TYPE_HEADER = 2

        @JvmStatic
        fun newInstance(): ImListFragment {
            return ImListFragment()
        }
    }
}
