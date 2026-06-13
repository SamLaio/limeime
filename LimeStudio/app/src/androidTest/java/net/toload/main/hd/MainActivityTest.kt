package net.toload.main.hd

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.toload.main.hd.ui.LIMESettings
import net.toload.main.hd.ui.NavigationManager
import net.toload.main.hd.ui.ProgressManager
import net.toload.main.hd.ui.ShareManager
import net.toload.main.hd.ui.controller.ManageImController
import net.toload.main.hd.ui.controller.SetupImController
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
open class MainActivityTest {
    private lateinit var context: Context
    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext()
    }
    @Test
    fun testMainActivityCreatesSingletonInstances() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var setupCtrl1: SetupImController = activity.getSetupImController()
        var setupCtrl2: SetupImController = activity.getSetupImController()
        var manageCtrl1: ManageImController = activity.getManageImController()
        var manageCtrl2: ManageImController = activity.getManageImController()
        var progressMgr1: ProgressManager = activity.getProgressManager()!!
        var progressMgr2: ProgressManager = activity.getProgressManager()!!
        var shareMgr1: ShareManager = activity.getShareManager()!!
        var shareMgr2: ShareManager = activity.getShareManager()!!
        var navMgr1: NavigationManager = activity.getNavigationManager()
        var navMgr2: NavigationManager = activity.getNavigationManager()
        assertSame("SetupImController should be singleton", setupCtrl1, setupCtrl2)
        assertSame("ManageImController should be singleton", manageCtrl1, manageCtrl2)
        assertSame("ProgressManager should be singleton", progressMgr1, progressMgr2)
        assertSame("ShareManager should be singleton", shareMgr1, shareMgr2)
        assertSame("NavigationManager should be singleton", navMgr1, navMgr2)
    })
        }
    }
    @Test
    fun testInitialHelpDialogGateIsAlwaysDisabled() {
        assertFalse(LIMESettings.shouldShowInitialHelpDialog("", "v6.1.0"))
        assertFalse(LIMESettings.shouldShowInitialHelpDialog("old-version", "v6.1.0"))
        assertFalse(LIMESettings.shouldShowInitialHelpDialog("v6.1.0", "v6.1.0"))
    }
    @Test
    fun testGetterMethodsReturnNonNullInstances() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        assertNotNull("SetupImController should not be null", activity.getSetupImController())
        assertNotNull("ManageImController should not be null", activity.getManageImController())
        assertNotNull("ProgressManager should not be null", activity.getProgressManager())
        assertNotNull("ShareManager should not be null", activity.getShareManager())
        assertNotNull("NavigationManager should not be null", activity.getNavigationManager())
    })
        }
    }
    @Test
    fun testMainActivityDoesNotImplementNavigationDrawerCallbacks() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var interfaces: Array<Class<*>> = activity.javaClass.getInterfaces()
        var implementsCallbacks: Boolean = false
        for (iface in interfaces) {
            if (iface.simpleName.equals("NavigationDrawerCallbacks")) {
                implementsCallbacks = true
                break
            }
        }
        assertFalse(("MainActivity should NOT implement NavigationDrawerCallbacks " + "(moved to NavigationManager)"), implementsCallbacks)
    })
        }
    }
    @Test
    fun testNavigationManagerOwnsNavigationBehavior() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var navManager: NavigationManager = activity.getNavigationManager()
        assertNotNull("NavigationManager should exist", navManager)
        try {
            assertNotNull("NavigationManager should expose navigation selection handling", navManager.javaClass.getMethod("navigateToFragment", Int::class.javaPrimitiveType!!))
        } catch (e: NoSuchMethodException) {
            fail("NavigationManager should expose navigation selection handling")
        }
    })
        }
    }
    @Test
    fun testMainActivityExposesManagersControllersOnly() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        assertNotNull("Should have getSetupImController()", getMethodOrNull(activity, "getSetupImController"))
        assertNotNull("Should have getManageImController()", getMethodOrNull(activity, "getManageImController"))
        assertNotNull("Should have getProgressManager()", getMethodOrNull(activity, "getProgressManager"))
        assertNotNull("Should have getShareManager()", getMethodOrNull(activity, "getShareManager"))
        assertNotNull("Should have getNavigationManager()", getMethodOrNull(activity, "getNavigationManager"))
        assertNull("Should NOT have getSearchServer() - use controllers", getMethodOrNull(activity, "getSearchServer"))
        assertNull("Should NOT have getLimeDB() - use controllers", getMethodOrNull(activity, "getLimeDB"))
    })
        }
    }
    @org.junit.Ignore("Deprecated: LIMESettings startup (post commit 6f36521a + LIME_SETTINGS_BACKPORT MVC refactor) no longer keeps the activity in RESUMED long enough for ActivityScenario.recreate() to round-trip. See docs/DEPCECATED_UI_TESTS.md.")
    @Test
    fun testActivityLifecycleMaintainsSingletons() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var ctrl: SetupImController = activity.getSetupImController()
        assertNotNull("Controller should be created", ctrl)
    })
                scenario.recreate()
                scenario.onActivity({ activity ->
        var ctrl: SetupImController = activity.getSetupImController()
        assertNotNull("Controller should be recreated after config change", ctrl)
    })
        }
    }
    private fun getMethodOrNull(obj: Any, methodName: String): java.lang.reflect.Method? {
        try {
            return obj.javaClass.getMethod(methodName)
        } catch (e: NoSuchMethodException) {
            return null
        }
    }
}
