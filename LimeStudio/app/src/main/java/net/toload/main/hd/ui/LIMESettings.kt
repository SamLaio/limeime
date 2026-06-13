package net.toload.main.hd.ui

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.OnBackPressedCallback
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.DynamicColors
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigationrail.NavigationRailView
import net.toload.main.hd.DBServer
import net.toload.main.hd.global.LIME
import net.toload.main.hd.global.LIMEPreferenceManager
import net.toload.main.hd.global.SystemAccentColor
import net.toload.main.hd.R
import net.toload.main.hd.SearchServer
import net.toload.main.hd.ui.controller.ManageImController
import net.toload.main.hd.ui.controller.SetupImController
import net.toload.main.hd.ui.dialog.HelpDialog
import net.toload.main.hd.ui.dialog.NewsDialog
import net.toload.main.hd.ui.view.DbManagerFragment
import net.toload.main.hd.ui.view.KeepassFragment
import net.toload.main.hd.ui.view.LimePreferenceFragment
import net.toload.main.hd.ui.view.LIMESettingsView
import net.toload.main.hd.ui.view.SetupFragment
import net.toload.main.hd.ui.view.TwoPaneHostFragment

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
*/
/**
 * Main activity for the LimeIME application.
 * 
 * 
 * LIMESettings serves as the primary container and coordinator for the IME management UI.
 * It manages the lifecycle of all major controllers, managers, and UI fragments, ensuring
 * they are properly initialized before fragments are instantiated.
 * 
 * <h2>Architecture</h2>
 * 
 * The activity follows a clean architecture pattern with clear separation of concerns:
 * 
 *  * **Controllers**: [SetupImController], [ManageImController] - handle business logic
 *  * **Managers**: [NavigationManager], [ProgressManager], [ShareManager] - manage UI concerns
 *  * **Handlers**: [IntentHandler] - process incoming intents
 *  * **Fragments**: SetupFragment, TwoPaneHostFragment, LimePreferenceFragment, DbManagerFragment - provide UI
 * 
 * 
 * <h2>Initialization Sequence</h2>
 * 
 * Controllers are initialized in [.onCreate] **BEFORE** `setContentView()`
 * to prevent race conditions when fragments are instantiated during layout inflation. This ensures
 * fragments can safely access controllers via getter methods.
 * 
 * <h2>Fragment Navigation</h2>
 * 
 * Fragment navigation is delegated to [NavigationManager], which orchestrates:
 * 
 *  * Fragment transaction management
 *  * Tab selection and legacy position mapping
 *  * ActionBar title updates
 * 
 * 
 * <h2>UI Updates</h2>
 * 
 * This activity implements [LIMESettingsView] to provide UI update callbacks:
 * 
 *  * [.navigateToFragment] - navigate to fragment by position
 *  * [.showProgress] - show progress overlay
 *  * [.hideProgress] - hide progress overlay
 *  * [.showToast] - show toast message
 *  * [.onError] - handle errors
 *  * [.onProgress] - update progress status
 * 
 * 
 * <h2>Edge-to-Edge Display</h2>
 * 
 * The activity supports edge-to-edge display on modern Android devices (API 21+) while
 * maintaining backward compatibility. Window insets are properly handled to avoid obscuring
 * UI elements behind system bars.
 * 
 * @see LIMESettingsView
 * 
 * @see NavigationManager
 * 
 * @see SetupImController
 * 
 * @see ManageImController
 * 
 * @see ProgressManager
 */
class LIMESettings : AppCompatActivity(), LIMESettingsView {
    // Controllers
    var setupImController: SetupImController? = null
    var manageImController: ManageImController? = null


    // Handlers/Managers
    private var intentHandler: IntentHandler? = null

    /**
     * Gets the ProgressManager instance.
     * 
     * 
     * This allows fragments and dialogs to show or hide activity-level progress
     * overlays through the coordinator (LIMESettings) without needing to manage
     * their own progress UI.
     * 
     * @return The ProgressManager instance
     */
    var progressManager: ProgressManager? = null
        private set

    /**
     * Gets the ShareManager instance.
     * 
     * 
     * This method is called by dialogs to access the ShareManager,
     * which handles share operations and dialog coordination. The manager
     * is guaranteed to be initialized in [.onCreate].
     * 
     * @return The ShareManager instance
     */
    var shareManager: ShareManager? = null
        private set
    private var navigationManager: NavigationManager? = null

    // Import callback
    /**
     * Called when the activity is first created.
     * 
     * 
     * **IMPORTANT**: Controllers are initialized **BEFORE** `setContentView()` is called.
     * This is critical to prevent race conditions where fragments are instantiated during layout
     * inflation and need to access controllers via getter methods. The initialization order is:
     * 
     *  1. Create [SearchServer], [DBServer] instances
     *  1. Create [ManageImController] and [SetupImController]
     *  1. Call `setContentView(R.layout.activity_main)`
     *  1. Create [ProgressManager], [ShareManager], [NavigationManager]
     *  1. Configure managers and register callbacks
     * 
     * 
     * 
     * The activity also:
     * 
     *  * Sets up edge-to-edge display
     *  * Initializes preference manager and package name
     *  * Registers navigation and intent callbacks
     * 
     * 
     * @param savedInstanceState If the activity is being re-initialized after previously
     * being shut down, this Bundle contains the most recent data
     * supplied. If not provided, this value will be null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this, SystemAccentColor.dynamicColorOptions(this))
        super.onCreate(savedInstanceState)
        // Register back gesture/press callback for AndroidX
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        // Initialize controllers BEFORE setContentView() to prevent race conditions
        // when fragments are instantiated during layout inflation
        // In test mode, use lightweight mock instances to avoid blocking database operations
        val searchServer: SearchServer?
        val dbServer: DBServer
        if (this.isRunningInTestMode) {
            // Use null for servers in test mode - controllers will handle gracefully
            searchServer = null
            dbServer = DBServer.getInstance(this)
        } else {
            searchServer = SearchServer(this)
            dbServer = DBServer.getInstance(this)
        }
        manageImController = ManageImController(searchServer)
        setupImController = SetupImController(this, dbServer, searchServer)

        // NOW inflate layout - fragments will find initialized controllers via getters
        setContentView(R.layout.activity_main)

        // Hide the activity-level ActionBar so each fragment's MaterialToolbar
        // becomes the sole top bar (prevents double-bar stacking).
        val ab = getSupportActionBar()
        if (ab != null) ab.hide()

        // Setup edge-to-edge display
        setupEdgeToEdge()


        //ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        val mLIMEPref: LIMEPreferenceManager = LIMEPreferenceManager(this)

        LIME.PACKAGE_NAME = getApplicationContext().getPackageName()

        setupImController!!.setSettingsView(this)

        // Initialize managers
        progressManager = ProgressManager(this)
        shareManager = ShareManager(this, setupImController!!, progressManager)
        navigationManager = NavigationManager(this)

        // Set navigation callbacks to NavigationManager
        setupImController!!.setNavigationManager(navigationManager)

        // initial imList
        navigationManager!!.setImConfigFullNameList(manageImController!!.imConfigFullNameList)

        // Wire bottom nav (phone) or navigation rail (tablet) — whichever is present in the layout
        val bottomNav: BottomNavigationView? =
            findViewById<BottomNavigationView?>(R.id.main_bottom_nav)
        val navRail: NavigationRailView? = findViewById<NavigationRailView?>(R.id.main_nav_rail)

        if (bottomNav != null) {
            bottomNav.setOnItemSelectedListener(NavigationBarView.OnItemSelectedListener { item: MenuItem? ->
                onTabSelected(item!!.getItemId())
                true
            })
        }
        if (navRail != null) {
            navRail.setOnItemSelectedListener(NavigationBarView.OnItemSelectedListener { item: MenuItem? ->
                onTabSelected(item!!.getItemId())
                true
            })
        }

        // If activity is started fresh (not restoring), show tab 0 (設定)
        // Skip initial navigation in test mode to prevent blocking startActivitySync()
        if (savedInstanceState == null && !this.isRunningInTestMode) {
            onTabSelected(R.id.nav_setup)
        }


        // Delegate intent handling to IntentHandler
        if (intentHandler == null) {
            intentHandler = IntentHandler(this, setupImController)
        }
        // Don't process intent in onCreate during tests to avoid blocking startActivitySync()
        if (!this.isRunningInTestMode) {
            intentHandler!!.processIntent(getIntent())
        }

        var versionStr = ""
        val pInfo: PackageInfo
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0)
            val versionCode: Long = PackageInfoCompat.getLongVersionCode(pInfo)
            versionStr = getString(R.string.version_format, pInfo.versionName, versionCode)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Error getting package info", e)
        }

        val currentVersion: String = mLIMEPref.getParameterString("current_version", "")
        if (shouldShowInitialHelpDialog(currentVersion, versionStr)) {
            // Skip HelpDialog in test environment to prevent blocking startActivitySync()
            val isTest = this.isRunningInTestMode
            Log.d(TAG, "isRunningInTestMode: " + isTest)
            if (!isTest) {
                val ft = getSupportFragmentManager().beginTransaction()
                val dialog: HelpDialog = HelpDialog.newInstance()
                dialog.show(ft, "helpdialog")
            } else {
                Log.d(TAG, "Skipping HelpDialog in test mode")
            }
            mLIMEPref.setParameter("current_version", versionStr)
        }
    }

    /**
     * Navigates to a fragment based on the selected position.
     * 
     * 
     * This method implements [LIMESettingsView] and delegates the actual navigation
     * to [NavigationManager], which handles fragment transactions, back stack management,
     * and title updates.
     * 
     * 
     * This method handles navigation to different fragments based on the selected
     * position:
     * 
     *  * Position 0: Shows the setup tab
     *  * Position 1+: Shows the input-method manager tab
     * 
     * 
     * 
     * All fragment transactions are added to the back stack to allow navigation
     * back through the history.
     * 
     * @param position The legacy navigation position
     * @see NavigationManager.navigateToFragment
     */
    override fun navigateToFragment(position: Int) {
        // Map old drawer positions to tab item IDs:
        // 0 = 設定 tab, 1+ = 輸入法 tab (IM management)
        val itemId: Int
        if (position == 0) {
            itemId = R.id.nav_setup
        } else {
            itemId = R.id.nav_im
        }
        onTabSelected(itemId)
        // Sync the selected tab indicator on whichever nav control is present
        val bottomNav: BottomNavigationView? =
            findViewById<BottomNavigationView?>(R.id.main_bottom_nav)
        if (bottomNav != null) bottomNav.setSelectedItemId(itemId)
        val navRail: NavigationRailView? = findViewById<NavigationRailView?>(R.id.main_nav_rail)
        if (navRail != null) navRail.setSelectedItemId(itemId)
    }

    private fun onTabSelected(itemId: Int) {
        val fragment: Fragment?
        if (itemId == R.id.nav_setup) {
            fragment = SetupFragment.newInstance()
        } else if (itemId == R.id.nav_im) {
            fragment = TwoPaneHostFragment.newInstance()
        } else if (itemId == R.id.nav_prefs) {
            fragment = LimePreferenceFragment.newInstance()
        } else if (itemId == R.id.nav_keepass) {
            fragment = KeepassFragment.newInstance()
        } else if (itemId == R.id.nav_db) {
            fragment = DbManagerFragment.newInstance()
        } else {
            fragment = SetupFragment.newInstance()
        }
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.main_fragment_container, fragment)
            .commit()
    }

    /**
     * Shows a progress overlay with an optional message.
     * 
     * 
     * This method implements [LIMESettingsView] and delegates to
     * [ProgressManager], which displays a modal progress dialog or
     * an activity-level overlay depending on what's available.
     * 
     * 
     * If a message is provided, it will be displayed in the progress view.
     * 
     * @param message The message to display in the progress view, or null/empty
     * to show the progress view without a message
     */
    override fun showProgress(message: String?) {
        if (progressManager != null) {
            progressManager!!.show()
            if (message != null && !message.isEmpty()) {
                progressManager!!.updateProgress(message)
            }
        }
    }

    /**
     * Hides the progress overlay.
     * 
     * 
     * This method implements [LIMESettingsView] and delegates to
     * [ProgressManager] to dismiss the progress dialog or hide the overlay.
     */
    override fun hideProgress() {
        if (progressManager != null) progressManager!!.dismiss()
    }

    /**
     * Shows a toast message to the user.
     * 
     * 
     * This method implements [LIMESettingsView] and delegates to
     * 
     * @param message The message text to display
     * @param duration The duration to show the message (`Toast.LENGTH_SHORT`
     * or `Toast.LENGTH_LONG`)
     */
    override fun showToast(message: String?, duration: Int) {
        val toast: Toast = Toast.makeText(this, message, duration)
        toast.show()
    }

    /**
     * Finishes this activity.
     * 
     * 
     * This method implements [LIMESettingsView] and provides a way for
     * controllers to request the activity to close itself.
     */
    override fun finishActivity() {
        finish()
    }

    /**
     * Handles an error by logging and displaying a toast message.
     * 
     * 
     * This method implements [LIMESettingsView] and is called when an
     * error occurs in a controller or fragment. The error is logged at ERROR level
     * and displayed to the user as a long-duration toast.
     * 
     * @param message The error message to log and display
     */
    override fun onError(message: String?) {
        Log.e(TAG, message.orEmpty())
        showToast(message, Toast.LENGTH_LONG)
    }

    /**
     * Updates progress information on the progress overlay.
     * 
     * 
     * This method implements [LIMESettingsView] and is called during long-running
     * operations to update the progress percentage and status message. Both parameters
     * are optional and only update their respective views if provided.
     * 
     * 
     * This method only updates the progress if a progress view is currently showing.
     * 
     * @param percentage The progress percentage (0-100), or -1 to skip percentage update
     * @param status The status message to display, or null/empty to skip message update
     */
    override fun onProgress(percentage: Int, status: String?) {
        if (progressManager != null && progressManager!!.isShowing()) {
            if (status != null && !status.isEmpty()) {
                progressManager!!.updateProgress(status)
            }
            if (percentage >= 0) {
                progressManager!!.updateProgress(percentage)
            }
        }
    }

    /**
     * Called when a navigation section is attached to update the ActionBar title.
     * 
     * 
     * No-op stub kept for compatibility with ManageImFragment and ManageRelatedFragment.
     * 
     * @param number The section number (unused)
     */
    /** No-op stub kept for compatibility with ManageImFragment and ManageRelatedFragment.  */
    fun onSectionAttached(number: Int) {
        // no-op — bottom nav / nav rail replaces the old drawer navigation
    }

    /**
     * Shows the news/message board dialog.
     * 
     * 
     * This method displays a [NewsDialog] containing news, announcements, or
     * other information to the user. The dialog is shown using the FragmentManager
     * and added to the fragment transaction queue.
     * 
     * 
     * If an error occurs while showing the dialog (e.g., activity has been destroyed),
     * the error is logged but not thrown. This prevents crashes if the activity is
     * finishing when this method is called.
     */
    fun showMessageBoard() {
        try {
            val ft = getSupportFragmentManager().beginTransaction()
            val dialog: NewsDialog = NewsDialog.newInstance()
            dialog.show(ft, "newsdialog")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing news dialog", e)
        }
    }

    /**
     * Setup edge-to-edge display with proper window insets handling.
     * 
     * 
     * This method enables edge-to-edge display on modern Android devices (API 21+)
     * while maintaining backward compatibility. It handles:
     * 
     *  * Window insets for main content container to avoid system bars
     *  * Transparent status bar and navigation bar for full screen immersion
     *  * Status bar icon color based on API level
     * 
     * 
     * 
     * **API Compatibility:**
     * 
     *  * **API 35+**: Uses modern window insets handling and transparent bars
     *  * **API 23-34**: Uses `setSystemUiVisibility()` for light status bar icons
     *  * **API 21-22**: Uses dark status bar (SYSTEM_UI_FLAG_LIGHT_STATUS_BAR not available)
     * 
     * 
     * 
     * The method ensures UI elements are not obscured by system bars while maintaining
     * visual consistency across API levels.
     */
    @Suppress("deprecation")
    private fun setupEdgeToEdge() {
        // Apply window insets to the main content container (FrameLayout)
        // ActionBar already handles its own space, so we only need to account for status bar
        val container = findViewById<View?>(R.id.main_fragment_container)
        if (container != null) {
            ViewCompat.setOnApplyWindowInsetsListener(
                container,
                OnApplyWindowInsetsListener { v: View, insets: WindowInsetsCompat ->
                    val systemBarsType: Int = WindowInsetsCompat.Type.systemBars()
                    val topInset: Int = insets.getInsets(systemBarsType).top
                    val bottomInset: Int = insets.getInsets(systemBarsType).bottom
                    val leftInset: Int = insets.getInsets(systemBarsType).left
                    val rightInset: Int = insets.getInsets(systemBarsType).right

                    // Apply padding: top = status bar only (ActionBar handles its own space),
                    // left/right/bottom = system bars
                    v.setPadding(leftInset, topInset, rightInset, 0)
                    insets
                })
        }

        // Set status bar and navigation bar to transparent for edge-to-edge effect
        // This works on all API levels, but is required for API 35+
        // Note: setStatusBarColor and setNavigationBarColor are deprecated in API 35+,
        // but we use them with suppression for backward compatibility
        val window = getWindow()
        window.setStatusBarColor(Color.TRANSPARENT)
        window.setNavigationBarColor(Color.TRANSPARENT)

        val uiMode = getResources().getConfiguration().uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isLight = (uiMode != Configuration.UI_MODE_NIGHT_YES)
        val decorView = getWindow().getDecorView()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val controller: WindowInsetsControllerCompat =
                WindowInsetsControllerCompat(getWindow(), decorView)
            controller.setAppearanceLightStatusBars(isLight)
            controller.setAppearanceLightNavigationBars(isLight)
        } else {
            // API 21-22: cannot toggle icon brightness; use solid dark bars so the
            // default white icons remain visible regardless of theme.
            getWindow().setStatusBarColor(-0x1000000)
            getWindow().setNavigationBarColor(-0x1000000)
        }
    }

    /**
     * Called when the activity is becoming visible to the user.
     * 
     * 
     * This method is called after [.onCreate] or after
     * [.onRestart] if the activity was previously stopped. At this point,
     * the activity is visible but may not be in the foreground.
     * 
     * 
     * Currently, this method performs minimal work. Subclasses may override to
     * perform initialization that requires the activity to be visible.
     */
    public override fun onStart() {
        super.onStart()
    }


    /**
     * Gets the SetupImController instance.
     * 
     * 
     * This method is called by fragments to access the SetupImController,
     * which manages import, export, and setup operations. The controller is
     * guaranteed to be initialized in [.onCreate] before
     * fragments are instantiated.
     * 
     * @return The SetupImController instance
     */
    @JvmName("getSetupImControllerCompat")
    fun getSetupImController(): SetupImController {
        return setupImController!!
    }

    /**
     * Gets the ManageImController instance.
     * 
     * 
     * This method is called by fragments to access the ManageImController,
     * which manages IM table operations. The controller is guaranteed to be
     * initialized in [.onCreate] before fragments are instantiated.
     * 
     * @return The ManageImController instance
     */
    @JvmName("getManageImControllerCompat")
    fun getManageImController(): ManageImController {
        return manageImController!!
    }

    /**
     * Gets the NavigationManager instance.
     * 
     * 
     * This method is called by fragments to access the NavigationManager,
     * which handles fragment navigation and title updates. The manager is
     * guaranteed to be initialized in [.onCreate].
     * 
     * @return The NavigationManager instance
     * @see NavigationManager
     */
    fun getNavigationManager(): NavigationManager {
        return navigationManager!!
    }

    private val isRunningInTestMode: Boolean
        /**
         * Checks if the app is running in test mode (instrumentation tests).
         * 
         * 
         * This is used to skip UI dialogs (like HelpDialog) that would block
         * test execution by preventing ActivityScenario.launch() from completing.
         * 
         * @return true if running under instrumentation tests, false otherwise
         */
        get() {
            // Check if test runner class is available in the classpath
            // This is the most reliable way that doesn't depend on process state
            try {
                Class.forName("androidx.test.runner.AndroidJUnitRunner")
                return true
            } catch (e: ClassNotFoundException) {
                return false
            }
        }


    companion object {
        init {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }


        private const val TAG = "LIMESettings"

        @JvmStatic
        fun shouldShowInitialHelpDialog(currentVersion: String?, versionStr: String?): Boolean {
            return false
        }
    }
}
