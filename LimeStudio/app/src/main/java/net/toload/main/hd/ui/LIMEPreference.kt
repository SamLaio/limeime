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
package net.toload.main.hd.ui

import android.app.backup.BackupManager
import android.content.Context
import android.content.res.Configuration
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentManager
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import java.util.ArrayList
import java.util.Objects
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.data.Keyboard
import net.toload.main.hd.global.DiagnosticLog
import net.toload.main.hd.global.LIME
import net.toload.main.hd.global.LIMEPreferenceManager
import net.toload.main.hd.global.LIMEPreferenceManager.ReverseLookupOption
import net.toload.main.hd.R
import net.toload.main.hd.SearchServer
import net.toload.main.hd.ui.view.LimePreferenceFragment
import net.toload.main.hd.ui.view.ScrollableTabHelper

class LIMEPreference : AppCompatActivity() {
    private var SearchSrv: SearchServer? = null

    override fun onPause() {
        super.onPause()

        this.SearchSrv?.initialCache()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DiagnosticLog.record(this, "LIMEPreference", "onCreate() start")
        super.onCreate(savedInstanceState)
        DiagnosticLog.record(this, "LIMEPreference", "onCreate() after super")

        // Enable edge-to-edge display for API 35+ (Android 15+)
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false)
        DiagnosticLog.record(this, "LIMEPreference", "onCreate() decorFitsSystemWindows=false")

        this.SearchSrv = SearchServer(this)
        DiagnosticLog.record(this, "LIMEPreference", "onCreate() SearchServer created")

        // Display the fragment as the main content.
        getSupportFragmentManager().beginTransaction().replace(
            android.R.id.content,
            PrefsFragment()
        ).commit()
        DiagnosticLog.record(this, "LIMEPreference", "onCreate() PrefsFragment committed")

        // Ensure ActionBar title is displayed
        val actionBar = getSupportActionBar()
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true)
            actionBar.setTitle(R.string.title_lime_preference)
            actionBar.setDisplayHomeAsUpEnabled(false)
            actionBar.setHomeButtonEnabled(false)
        }
        getSupportFragmentManager().addOnBackStackChangedListener(FragmentManager.OnBackStackChangedListener { this.syncActionBarToBackStack() })

        // Handle window insets for edge-to-edge display
        setupEdgeToEdge()
        DiagnosticLog.record(this, "LIMEPreference", "onCreate() setupEdgeToEdge complete")
        DiagnosticLog.exportToDownloadsAsync(this, "lime-preference-onCreate")
    }

    override fun onSupportNavigateUp(): Boolean {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack()
            return true
        }
        finish()
        return true
    }

    private fun syncActionBarToBackStack() {
        val actionBar = getSupportActionBar()
        if (actionBar == null) return
        val canGoBack = getSupportFragmentManager().getBackStackEntryCount() > 0
        actionBar.setDisplayHomeAsUpEnabled(canGoBack)
        actionBar.setHomeButtonEnabled(canGoBack)

        val top =
            getSupportFragmentManager().findFragmentById(android.R.id.content)
        if (top is PreferenceFragmentCompat) {
            val pf: PreferenceFragmentCompat = top as PreferenceFragmentCompat
            if (pf.getPreferenceScreen() != null && pf.getPreferenceScreen().getTitle() != null) {
                actionBar.setTitle(pf.getPreferenceScreen().getTitle())
            }
        }
    }

    /**
     * Setup edge-to-edge display with proper window insets handling.
     * This ensures UI elements are not obscured by system bars on API 35+.
     */
    @Suppress("deprecation")
    private fun setupEdgeToEdge() {
        // Apply window insets to the content view (where PreferenceFragment is displayed)
        val contentView = findViewById<View?>(android.R.id.content)
        if (contentView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(
                contentView,
                OnApplyWindowInsetsListener { v: View, insets: WindowInsetsCompat ->
                    val systemBarsType: Int = WindowInsetsCompat.Type.systemBars()
                    val topInset: Int = insets.getInsets(systemBarsType).top
                    val bottomInset: Int = insets.getInsets(systemBarsType).bottom
                    val leftInset: Int = insets.getInsets(systemBarsType).left
                    val rightInset: Int = insets.getInsets(systemBarsType).right

                    // Apply padding: top = status bar only (ActionBar handles its own space),
                    // left/right/bottom = system bars
                    v.setPadding(leftInset, topInset, rightInset, bottomInset)
                    insets
                })
        }

        // Set status bar and navigation bar to transparent for edge-to-edge effect
        // Note: setStatusBarColor and setNavigationBarColor are deprecated in API 35+,
        // but we use them with suppression for backward compatibility
        val window = getWindow()
        window.setStatusBarColor(Color.TRANSPARENT)
        window.setNavigationBarColor(Color.TRANSPARENT)


        // Set status bar icon appearance to dark (black icons) for better visibility
        // Since status bar is transparent and content behind may be light, use dark icons
        val decorView = getWindow().getDecorView()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // API 23+ (Marshmallow+): Use WindowInsetsControllerCompat
            // Note: getWindowInsetsController() is deprecated in API 35+, but necessary for API 23-34
            @Suppress("deprecation") val windowInsetsController: WindowInsetsControllerCompat? =
                ViewCompat.getWindowInsetsController(decorView)
            if (windowInsetsController != null) {
                val uiMode = (getResources().getConfiguration().uiMode
                        and Configuration.UI_MODE_NIGHT_MASK)
                val isLight = (uiMode != Configuration.UI_MODE_NIGHT_YES)
                windowInsetsController.setAppearanceLightStatusBars(isLight)
                windowInsetsController.setAppearanceLightNavigationBars(isLight)
            }
        } else {
            // API 21-22: SYSTEM_UI_FLAG_LIGHT_STATUS_BAR is not available (introduced in API 23)
            // On API 21-22, we cannot change icon color programmatically
            // Set a dark status bar so white icons are visible (compromise for API 21-22)
            //@SuppressWarnings("deprecation")
            //android.view.Window window = getWindow();
            // Use a dark color so white icons are visible
            // This maintains some edge-to-edge while ensuring icons are visible
            window.setStatusBarColor(-0x1000000) // Solid black
        }
    }

    class PrefsFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {
        private val DEBUG = false
        private val TAG = "LIMEPreferenceHC"
        private var ctx: Context? = null
        private var SearchSrv: SearchServer? = null
        private var mLIMEPref: LIMEPreferenceManager? = null

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            ScrollableTabHelper.applyToRecyclerView(getActivity(), getListView())
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // Load the preferences from an XML resource (scoped to rootKey for nested PreferenceScreen drill-down)
            setPreferencesFromResource(R.xml.preference, rootKey)

            // Remove the reserved icon space so rows aren't indented (iconSpaceReserved on
            // the XML root doesn't cascade — apply it recursively to every Preference).
            disableIconSpaceReserved(getPreferenceScreen())

            // Sync the host fragment's toolbar (title + back chevron) to this screen
            // — the OnBackStackChangedListener fires before the new fragment loads
            // its preferences, so we need a follow-up nudge once the screen is ready.
            // Defer via view.post(...) so the sync runs after layout — calling it
            // mid-transaction can leave the toolbar nav button in a state where the
            // first tap is eaten.
            val parent = getParentFragment()
            if (parent is LimePreferenceFragment) {
                val host: LimePreferenceFragment =
                    parent as LimePreferenceFragment
                val hostView: View? = host.getView()
                if (hostView != null) {
                    hostView.post(Runnable { host.syncToolbarToBackStack() })
                } else {
                    host.syncToolbarToBackStack()
                }
            }

            if (ctx == null) {
                ctx = requireActivity().getApplicationContext()
            }
            mLIMEPref = LIMEPreferenceManager(ctx!!)
            SearchSrv = SearchServer(ctx!!)
            configureReverseLookupPreferenceEntries()

            // On API 31+, vibration intensity is controlled by the system via performHapticFeedback.
            // The vibrate_level duration preference has no effect, so hide it to avoid confusion.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibrateLevelPref = findPreference<Preference?>("vibrate_level")
                if (vibrateLevelPref != null) {
                    vibrateLevelPref.setVisible(false)
                }
            }
        }

        override fun onResume() {
            super.onResume()

            // Set up a listener whenever a key changes
            Objects.requireNonNull<SharedPreferences?>(getPreferenceScreen().getSharedPreferences())
                .registerOnSharedPreferenceChangeListener(this)
        }

        override fun onPause() {
            super.onPause()

            // Unregister the listener whenever a key changes
            Objects.requireNonNull<SharedPreferences?>(getPreferenceScreen().getSharedPreferences())
                .unregisterOnSharedPreferenceChangeListener(this)
        }


        // Nested PreferenceScreen navigation: handle ONLY via onNavigateToScreen.
        // PreferenceScreen also bubbles a tap up through onPreferenceTreeClick, so
        // overriding both pushes the same fragment transaction twice — the visible
        // symptom is the back chevron requiring two taps to return to the parent.
        override fun onNavigateToScreen(preferenceScreen: PreferenceScreen) {
            Log.d(TAG, "onNavigateToScreen: " + preferenceScreen.getKey())
            navigateToNested(preferenceScreen.getKey())
        }

        private fun disableIconSpaceReserved(group: PreferenceGroup?) {
            if (group == null) return
            group.setIconSpaceReserved(false)
            for (i in 0..<group.getPreferenceCount()) {
                val p = group.getPreference(i)
                p.setIconSpaceReserved(false)
                if (p is PreferenceGroup) {
                    disableIconSpaceReserved(p)
                }
            }
        }

        private fun navigateToNested(rootKey: String?) {
            val newFragment = PrefsFragment()
            val args: Bundle = Bundle()
            args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, rootKey)
            newFragment.setArguments(args)
            var containerId = android.R.id.content
            val parent = requireView().getParent() as View?
            if (parent != null && parent.getId() != View.NO_ID) {
                containerId = parent.getId()
            }
            val fm = getParentFragmentManager()
            fm.beginTransaction()
                .replace(containerId, newFragment)
                .addToBackStack(null)
                .commit()
        }

        private fun configureReverseLookupPreferenceEntries() {
            val options: MutableList<ReverseLookupOption?> = loadReverseLookupOptions()
            val noneLabel = getString(R.string.reverse_lookup_none)
            val labels: Array<CharSequence?>? =
                LIMEPreferenceManager.reverseLookupLabels(options, noneLabel).map { it as CharSequence? }.toTypedArray()
            val values: Array<CharSequence?>? =
                LIMEPreferenceManager.reverseLookupValues(options, noneLabel).map { it as CharSequence? }.toTypedArray()
            val root: PreferenceGroup? = getPreferenceScreen()
            applyReverseLookupEntries(root, labels, values)
        }

        private fun loadReverseLookupOptions(): MutableList<ReverseLookupOption?> {
            try {
                val searchSrv = SearchSrv
                if (searchSrv != null) {
                    val all: MutableList<ImConfig?> =
                        searchSrv.getImConfigList(null, LIME.IM_FULL_NAME)
                    val active: MutableList<ImConfig?> = ArrayList<ImConfig?>()
                    for (im in all) {
                        if (im != null && ("emoji" != im.code) && !im.isDisable) {
                            active.add(im)
                        }
                    }
                    return LIMEPreferenceManager.buildReverseLookupOptions(
                        active,
                        getString(R.string.reverse_lookup_none)
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "loadReverseLookupOptions(): fallback to saved active IM state", e)
            }
            return if (mLIMEPref != null)
                mLIMEPref!!.getReverseLookupOptions(getString(R.string.reverse_lookup_none))
            else
                LIMEPreferenceManager.buildReverseLookupOptions(
                    null as String?,
                    getString(R.string.reverse_lookup_none)
                )
        }

        private fun applyReverseLookupEntries(
            group: PreferenceGroup?,
            labels: Array<CharSequence?>?, values: Array<CharSequence?>?
        ) {
            if (group == null) return
            for (i in 0..<group.getPreferenceCount()) {
                val pref = group.getPreference(i)
                if (pref is ListPreference && isReverseLookupPreference(pref.getKey())) {
                    val listPreference = pref
                    listPreference.setEntries(labels)
                    listPreference.setEntryValues(values)
                }
                if (pref is PreferenceGroup) {
                    applyReverseLookupEntries(pref, labels, values)
                }
            }
        }

        private fun isReverseLookupPreference(key: String?): Boolean {
            return key != null && key.endsWith("_im_reverselookup")
        }

        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) {
            if (DEBUG) Log.i(TAG, "onSharedPreferenceChanged(), key:" + key)

            val limePref = mLIMEPref
            limePref?.resetStartupConfigVersionIfStartupPreferenceChanged(key)

            if ("phonetic_keyboard_type" == key) {
                val selectedPhoneticKeyboardType = limePref?.phoneticKeyboardType
                //PreferenceManager.getDefaultSharedPreferences(ctx).getString("phonetic_keyboard_type", "");
                try {
                    // Ensure SearchServer instance is initialized
                    if (SearchSrv == null) {
                        if (ctx == null) {
                            ctx = requireActivity().getApplicationContext()
                        }
                        SearchSrv = SearchServer(ctx!!)
                    }
                    val searchSrv = SearchSrv ?: return
                    val pref = limePref ?: return

                    var keyboardConfig = searchSrv.getKeyboardConfig(LIME.DB_TABLE_PHONETIC)

                    when (selectedPhoneticKeyboardType) {
                        LIME.IM_PHONETIC_STANDARD -> keyboardConfig =
                            searchSrv.getKeyboardConfig("phonetic")

                        LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN -> keyboardConfig =
                            searchSrv.getKeyboardConfig("phoneticet41")

                        LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26 -> if (pref.getParameterBoolean(
                                "number_row_in_english",
                                false
                            )
                        ) {
                            keyboardConfig = searchSrv.getKeyboardConfig("limenum")
                        } else {
                            keyboardConfig = searchSrv.getKeyboardConfig("lime")
                        }

                        "eten26_symbol" -> keyboardConfig = searchSrv.getKeyboardConfig("et26")
                        LIME.IM_PHONETIC_KEYBOARD_HSU -> if (pref.getParameterBoolean(
                                "number_row_in_english",
                                false
                            )
                        ) {
                            keyboardConfig = searchSrv.getKeyboardConfig("limenum")
                        } else {
                            keyboardConfig = searchSrv.getKeyboardConfig("lime")
                        }

                        "hsu_symbol" -> keyboardConfig =
                            searchSrv.getKeyboardConfig(LIME.IM_PHONETIC_KEYBOARD_HSU)
                    }
                    searchSrv.setIMKeyboard(
                        "phonetic",
                        keyboardConfig!!.getDescription(),
                        keyboardConfig.code
                    )
                    if (DEBUG) Log.i(
                        TAG, "onSharedPreferenceChanged() PhoneticIMInfo.kyeboard:" +
                                searchSrv.getImConfig("phonetic", "keyboard")
                    )
                } catch (e: Exception) {
                    Log.i(
                        TAG,
                        "onSharedPreferenceChanged(), WriteIMinfo for selected phonetic keyboard failed!!"
                    )
                    Log.e(TAG, "Error in operation", e)
                }
            }
            val backupManager: BackupManager = BackupManager(ctx!!)
            backupManager.dataChanged() //Jeremy '12,4,29 call backup manager to backup the changes.
        } //		private ServiceConnection serConn = new ServiceConnection() {
        //			public void onServiceConnected(ComponentName name, IBinder service) {
        //				if(DBSrv == null){
        //					DBSrv = IDBService.Stub.asInterface(service);
        //				}
        //			}
        //			public void onServiceDisconnected(ComponentName name) {}
        //
        //		};
    }


    companion object {
        init {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
