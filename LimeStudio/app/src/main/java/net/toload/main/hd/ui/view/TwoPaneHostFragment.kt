package net.toload.main.hd.ui.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.R
import net.toload.main.hd.ui.viewmodel.ImNavigationViewModel

/**
 * Host fragment for the two-pane IM management UI.
 */
class TwoPaneHostFragment : Fragment() {
    private var slidingPaneLayout: SlidingPaneLayout? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_two_pane_im_host, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val paneLayout = view.findViewById<SlidingPaneLayout>(R.id.sliding_pane_layout)
        slidingPaneLayout = paneLayout
        paneLayout.lockMode = SlidingPaneLayout.LOCK_MODE_LOCKED

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.im_list_pane, ImListFragment.newInstance())
                .commit()
        }

        val vm = ViewModelProvider(this)[ImNavigationViewModel::class.java]

        vm.selectedIm.observe(viewLifecycleOwner) { im ->
            if (im != null) {
                childFragmentManager.beginTransaction()
                    .replace(R.id.im_detail_pane, ImDetailFragment.newInstance(im))
                    .addToBackStack(null)
                    .commit()
                if (!paneLayout.isOpen) {
                    paneLayout.open()
                }
                vm.selectedIm.value = null
            }
        }

        vm.showInstall.observe(viewLifecycleOwner) { show ->
            if (show == true) {
                childFragmentManager.beginTransaction()
                    .replace(R.id.im_detail_pane, ImInstallFragment.newInstance())
                    .addToBackStack(null)
                    .commit()
                if (!paneLayout.isOpen) {
                    paneLayout.open()
                }
                vm.showInstall.value = false
            }
        }

        childFragmentManager.addOnBackStackChangedListener {
            if (childFragmentManager.backStackEntryCount == 0) {
                paneLayout.close()
                val listFragment = childFragmentManager.findFragmentById(R.id.im_list_pane)
                if (listFragment is ImListFragment) {
                    listFragment.refreshList()
                }
            }
        }
    }

    fun navigateToDetail(fragment: Fragment?) {
        childFragmentManager.beginTransaction()
            .replace(R.id.im_detail_pane, fragment!!)
            .addToBackStack(null)
            .commit()
        val paneLayout = slidingPaneLayout
        if (paneLayout != null && !paneLayout.isOpen) {
            paneLayout.open()
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(): TwoPaneHostFragment = TwoPaneHostFragment()
    }
}
