package net.toload.main.hd.ui

import android.util.Log
import androidx.fragment.app.FragmentManager
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.R
import net.toload.main.hd.ui.view.SetupFragment
import net.toload.main.hd.ui.view.TwoPaneHostFragment

/**
 * Manages navigation between fragments in LIMESettings.
 */
class NavigationManager(private val activity: LIMESettings) {
    private var imConfigFullNameList: List<ImConfig>? = null
    private var currentTitle: CharSequence? = null
    var selectedPosition: Int = -1

    fun setImConfigFullNameList(imConfigList: List<ImConfig>?) {
        imConfigFullNameList = imConfigList
    }

    fun navigateToFragment(position: Int) {
        val fragmentManager = activity.supportFragmentManager

        if (position == 0) {
            fragmentManager.beginTransaction()
                .replace(R.id.main_fragment_container, SetupFragment.newInstance(), "SetupFragment")
                .addToBackStack("SetupFragment")
                .commit()
            updateTitle(position)
        } else {
            fragmentManager.beginTransaction()
                .replace(R.id.main_fragment_container, TwoPaneHostFragment.newInstance(), "TwoPaneHostFragment")
                .addToBackStack("TwoPaneHostFragment")
                .commit()
            updateTitle(position)
        }
    }

    fun updateTitle(position: Int) {
        currentTitle = if (position == 0) {
            activity.resources.getString(R.string.default_menu_initial)
        } else if (position == 1) {
            activity.resources.getString(R.string.default_menu_related)
        } else {
            val imIndex = position - 2
            val list = imConfigFullNameList
            if (!list.isNullOrEmpty() && imIndex >= 0 && imIndex < list.size) {
                list[imIndex].desc
            } else {
                Log.w(TAG, "Cannot update title - invalid IM index: $imIndex")
                ""
            }
        }
    }

    fun getCurrentTitle(): CharSequence? = currentTitle

    companion object {
        private const val TAG = "NavigationManager"
    }
}
