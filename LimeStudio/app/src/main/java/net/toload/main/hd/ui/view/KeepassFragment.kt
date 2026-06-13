package net.toload.main.hd.ui.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import net.toload.main.hd.R

class KeepassFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_keepass, container, false)
        val scrollView = root.findViewById<NestedScrollView>(R.id.keepass_scroll)
        if (scrollView != null) {
            ScrollableTabHelper.applyToNestedScrollView(activity, scrollView)
        }
        return root
    }

    companion object {
        @JvmStatic
        fun newInstance(): KeepassFragment = KeepassFragment()
    }
}
