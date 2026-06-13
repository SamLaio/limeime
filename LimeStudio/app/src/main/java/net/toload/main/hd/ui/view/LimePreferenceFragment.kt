package net.toload.main.hd.ui.view

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import net.toload.main.hd.R
import net.toload.main.hd.SearchServer
import net.toload.main.hd.ui.LIMEPreference
import net.toload.main.hd.ui.LIMESettings

class LimePreferenceFragment : Fragment() {
    private var toolbar: MaterialToolbar? = null
    private var rootTitle: CharSequence? = null
    private var backCallback: OnBackPressedCallback? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_lime_preference_host, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val bar = view.findViewById<MaterialToolbar>(R.id.lime_preference_toolbar)
        toolbar = bar
        rootTitle = bar.title

        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                childFragmentManager.popBackStackImmediate()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback!!)

        bar.navigationContentDescription = "Back"
        bar.setNavigationOnClickListener { clickedView ->
            Log.d(
                "LimePrefBack",
                "nav-icon click: depth=" + childFragmentManager.backStackEntryCount +
                    " icon=" + (bar.navigationIcon != null) +
                    " viewAttached=" + bar.isAttachedToWindow +
                    " enabled=" + bar.isEnabled
            )
            clickedView.post {
                val depth = childFragmentManager.backStackEntryCount
                Log.d("LimePrefBack", "posted pop attempt: depth=$depth")
                if (depth > 0) {
                    val popped = childFragmentManager.popBackStackImmediate()
                    Log.d("LimePrefBack", "popBackStackImmediate returned=$popped")
                }
            }
        }

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.lime_preference_host, LIMEPreference.PrefsFragment())
                .commit()
        }

        childFragmentManager.addOnBackStackChangedListener {
            val currentView = getView()
            if (currentView != null) {
                currentView.post { syncToolbarToBackStack() }
            } else {
                syncToolbarToBackStack()
            }
        }
        syncToolbarToBackStack()
    }

    fun syncToolbarToBackStack() {
        val bar = toolbar ?: return
        val depth = childFragmentManager.backStackEntryCount
        backCallback?.isEnabled = depth > 0
        if (depth == 0) {
            bar.title = rootTitle
            bar.navigationIcon = null
            return
        }
        bar.setNavigationIcon(R.drawable.ic_arrow_back)
        val top = childFragmentManager.findFragmentById(R.id.lime_preference_host)
        if (top is PreferenceFragmentCompat) {
            val screen = top.preferenceScreen
            if (screen?.title != null) {
                bar.title = screen.title
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val act: Activity? = activity
        if (act is LIMESettings) {
            val ctrl = act.manageImController
            if (ctrl != null) {
                val ss: SearchServer? = ctrl.getSearchServer()
                ss?.initialCache()
            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(): LimePreferenceFragment = LimePreferenceFragment()
    }
}
