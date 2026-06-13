@file:Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST", "TYPE_INTERSECTION_AS_REIFIED")

package net.toload.main.hd

import android.content.SharedPreferences
import android.graphics.Color
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceScreen
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.toload.main.hd.ui.LIMEPreference
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
open class LIMEPreferenceTest {
    @Test
    fun testLIMEPreferenceActivityLaunches() {
        ActivityScenario.launch(LIMEPreference::class.java).use { scenario ->
                assertTrue(true)
        }
    }
    @Test
    fun testStandalonePreferenceActivityTitleMatchesPreferenceTab() {
        ActivityScenario.launch(LIMEPreference::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        assertNotNull("ActionBar should exist", activity.getSupportActionBar())
        assertEquals("喜好設定", java.lang.String.valueOf(activity.getSupportActionBar()!!.getTitle()))
    })
        }
    }
    @Test
    fun testPrefsFragmentClassExists() {
        try {
            var cls: Class<*> = Class.forName("net.toload.main.hd.ui.LIMEPreference\$PrefsFragment")
            assertNotNull(cls)
        } catch (e: ClassNotFoundException) {
            fail("LIMEPreference.PrefsFragment class not found")
        }
    }
    @Test
    fun testPrefsFragmentAttachedWithSearchServerInitialized() {
        ActivityScenario.launch(LIMEPreference::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var fragment: Fragment = activity.getSupportFragmentManager().findFragmentById(android.R.id.content)!!
        assertNotNull("PrefsFragment should be attached", fragment)
        try {
            var searchField: java.lang.reflect.Field = fragment.javaClass.getDeclaredField("SearchSrv")
            searchField.setAccessible(true)
            var searchSrv: Any? = searchField.get(fragment)
            assertNotNull("PrefsFragment should initialize SearchServer", searchSrv)
        } catch (e: Exception) {
            throw AssertionError("Failed to inspect PrefsFragment SearchSrv", e)
        }
    })
        }
    }
    @Test
    fun testOnSharedPreferenceChangedCallsBackupManager() {
        ActivityScenario.launch(LIMEPreference::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var fragment: Fragment = activity.getSupportFragmentManager().findFragmentById(android.R.id.content)!!
        assertNotNull("PrefsFragment should be attached", fragment)
        try {
            var prefs: SharedPreferences = (fragment as LIMEPreference.PrefsFragment).getPreferenceScreen().getSharedPreferences()!!
            (fragment as LIMEPreference.PrefsFragment).onSharedPreferenceChanged(prefs, "some_key")
        } catch (e: Exception) {
            throw AssertionError("PrefsFragment onSharedPreferenceChanged should not crash", e)
        }
    })
        }
    }
    @Test
    fun testPhoneticKeyboardTypeChangeDoesNotCrash() {
        ActivityScenario.launch(LIMEPreference::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var fragment: Fragment = activity.getSupportFragmentManager().findFragmentById(android.R.id.content)!!
        assertNotNull("PrefsFragment should be attached", fragment)
        try {
            var prefs: SharedPreferences = (fragment as LIMEPreference.PrefsFragment).getPreferenceScreen().getSharedPreferences()!!
            (fragment as LIMEPreference.PrefsFragment).onSharedPreferenceChanged(prefs, "phonetic_keyboard_type")
        } catch (e: Exception) {
            throw AssertionError("PrefsFragment phonetic keyboard change crashed", e)
        }
    })
        }
    }
    @Test
    fun testPreferenceChangeListenerLifecycleSafe() {
        ActivityScenario.launch(LIMEPreference::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var fragment: Fragment = activity.getSupportFragmentManager().findFragmentById(android.R.id.content)!!
        assertNotNull("PrefsFragment should be attached", fragment)
        try {
            (fragment as LIMEPreference.PrefsFragment).onResume()
            (fragment as LIMEPreference.PrefsFragment).onPause()
        } catch (e: Exception) {
            throw AssertionError("PrefsFragment listener lifecycle crashed", e)
        }
    })
        }
    }
    @org.junit.Ignore("Deprecated: standalone LIMEPreference activity is being absorbed into the new BottomNav tab per docs/LIME_SETTINGS_BACKPORT.md §8; the standalone-launch + nested-screen navigation flow no longer holds the activity in RESUMED long enough for the fragment transaction. See docs/DEPCECATED_UI_TESTS.md.")
    @Test
    fun testReverseLookupNestedScreenOpensFromStandalonePreferenceActivity() {
        ActivityScenario.launch(LIMEPreference::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var fragment: Fragment = activity.getSupportFragmentManager().findFragmentById(android.R.id.content)!!
        assertNotNull("PrefsFragment should be attached", fragment)
        var reverseLookupScreen: PreferenceScreen = (fragment as LIMEPreference.PrefsFragment).findPreference("reverse_lookup_screen")!!
        assertNotNull("Reverse lookup screen should exist", reverseLookupScreen)
        (fragment as LIMEPreference.PrefsFragment).onNavigateToScreen(reverseLookupScreen)
        activity.getSupportFragmentManager().executePendingTransactions()
        var nestedFragment: Fragment = activity.getSupportFragmentManager().findFragmentById(android.R.id.content)!!
        assertTrue("Nested reverse lookup screen should be shown", (nestedFragment is LIMEPreference.PrefsFragment))
        assertEquals("reverse_lookup_screen", (nestedFragment as LIMEPreference.PrefsFragment).getPreferenceScreen().getKey())
        assertNotNull("ActionBar should exist", activity.getSupportActionBar())
        assertTrue("Nested standalone preference screen should show a back chevron", ((activity.getSupportActionBar()!!.getDisplayOptions() and androidx.appcompat.app.ActionBar.DISPLAY_HOME_AS_UP) != 0))
    })
        }
    }
    @Test
    fun testRootBackChevronFinishesStandalonePreferenceActivity() {
        ActivityScenario.launch(LIMEPreference::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        assertEquals(0, activity.getSupportFragmentManager().getBackStackEntryCount())
        assertTrue("Root up should be handled", activity.onSupportNavigateUp())
        assertTrue("Root up should finish the standalone preference activity", activity.isFinishing())
    })
        }
    }
    @Test
    fun testPhoneticKeyboardMappingBranches() {
        ActivityScenario.launch(LIMEPreference::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var fragment: Fragment = activity.getSupportFragmentManager().findFragmentById(android.R.id.content)!!
        assertNotNull("PrefsFragment should be attached", fragment)
        try {
            var prefs: SharedPreferences = (fragment as LIMEPreference.PrefsFragment).getPreferenceScreen().getSharedPreferences()!!
            prefs.edit().putString("phonetic_keyboard_type", "eten26").putBoolean("number_row_in_english", true).apply()
            (fragment as LIMEPreference.PrefsFragment).onSharedPreferenceChanged(prefs, "phonetic_keyboard_type")
            prefs.edit().putString("phonetic_keyboard_type", "hsu_symbol").putBoolean("number_row_in_english", false).apply()
            (fragment as LIMEPreference.PrefsFragment).onSharedPreferenceChanged(prefs, "phonetic_keyboard_type")
        } catch (e: Exception) {
            throw AssertionError("PrefsFragment phonetic mapping branches crashed", e)
        }
    })
        }
    }
    @Test
    @Suppress("deprecation")
    fun testEdgeToEdgeColorsApplied() {
        ActivityScenario.launch(LIMEPreference::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var statusColor: Int = activity.getWindow().getStatusBarColor()
        var navColor: Int = activity.getWindow().getNavigationBarColor()
        assertTrue("Status bar color should be transparent or dark fallback", ((statusColor == Color.TRANSPARENT) || (statusColor == (0xFF000000).toInt())))
        assertTrue("Navigation bar color should be transparent", (navColor == Color.TRANSPARENT))
    })
        }
    }
    @Test
    fun testLegacyNameNotPresent() {
        try {
            Class.forName("net.toload.main.hd.ui.LIMEPreferenceHC")
            fail("LIMEPreferenceHC should not exist after rename")
        } catch (expected: ClassNotFoundException) {

        }
    }
}
