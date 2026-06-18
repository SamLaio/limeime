@file:Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST", "TYPE_INTERSECTION_AS_REIFIED")

package net.toload.main.hd

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.toload.main.hd.data.ImConfig
import net.toload.main.hd.global.LIMEPreferenceManager
import net.toload.main.hd.ui.LIMESettings
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Arrays
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
open class ApplicationTest {
    @Test
    fun testApplicationContext() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        assertNotNull("Application context should not be null", appContext)
        var application: Application = (appContext.getApplicationContext() as Application)
        assertNotNull("Application instance should not be null", application)
    }
    @Test
    fun testPackageName() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var packageName: String = appContext.getPackageName()
        assertEquals("tw.idv.sam.lime", packageName)
    }
    @Test
    fun testApplicationInfo() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var pm: PackageManager = appContext.getPackageManager()
        var appInfo: ApplicationInfo = pm.getApplicationInfo(appContext.getPackageName(), 0)
        assertNotNull("Application info should not be null", appInfo)
        assertNotNull("Application label should not be null", pm.getApplicationLabel(appInfo))
    }
    @Test
    fun testApplicationResources() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        assertNotNull("Resources should not be null", appContext.getResources())
        assertNotNull("String resources should be available", appContext.getResources().getString(R.string.app_name))
    }
    @Test
    fun testPackageInfoCompat() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var pm: PackageManager = appContext.getPackageManager()
        var pInfo: PackageInfo = pm.getPackageInfo(appContext.getPackageName(), 0)
        var versionCode: Long = PackageInfoCompat.getLongVersionCode(pInfo)
        assertTrue("Version code should be positive", (versionCode > 0))
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)) {
            assertEquals("Version code should match", versionCode, pInfo.getLongVersionCode())
        } else {
            @Suppress("deprecation")
            var deprecatedVersionCode: Int = pInfo.versionCode
            assertEquals("Version code should match", versionCode, deprecatedVersionCode)
        }
    }
    @Test
    fun testContextCompatGetDataDir() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var dataDir: File = ContextCompat.getDataDir(appContext)!!
        assertNotNull("Data directory should not be null", dataDir)
        assertTrue("Data directory should exist", dataDir.exists())
        assertTrue("Data directory should be a directory", dataDir.isDirectory())
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)) {
            assertEquals("Data dir should match Context.getDataDir()", appContext.getDataDir().getAbsolutePath(), dataDir.getAbsolutePath())
        } else {
            var expectedPath: String? = appContext.getFilesDir().getParent()
            assertEquals("Data dir should match fallback path", expectedPath, dataDir.getAbsolutePath())
        }
    }
    @Test
    fun testLIMEPreferenceManager() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var prefManager: LIMEPreferenceManager = LIMEPreferenceManager(appContext)
        assertNotNull("PreferenceManager should not be null", prefManager)
        var testKey: String = ("test_key_" + System.currentTimeMillis())
        var testValue: String = "test_value"
        prefManager.setParameter(testKey, testValue)
        var retrievedValue: String = prefManager.getParameterString(testKey, "")
        assertEquals("Retrieved value should match set value", testValue, retrievedValue)
        var prefs: android.content.SharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(appContext)
        prefs.edit().remove(testKey).apply()
    }
    @Test
    fun testLIMEPreferenceManagerReverseLookupRoundTrip() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var prefManager: LIMEPreferenceManager = LIMEPreferenceManager(appContext)
        prefManager.setReverseLookupTable("phonetic", "cj")
        prefManager.setReverseLookupTable("dayi", "array")
        assertEquals("Phonetic should use the legacy bpmf reverse lookup key", "cj", prefManager.getReverseLookupTable("phonetic"))
        assertEquals("Non-phonetic IMs should use table_im_reverselookup", "array", prefManager.getReverseLookupTable("dayi"))
        var prefs: android.content.SharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(appContext)
        prefs.edit().remove("bpmf_im_reverselookup").remove("dayi_im_reverselookup").apply()
    }
    @Test
    fun testReverseLookupOptionsUseEnabledIMLabelsButKeepTableCodes() {
        var cj: ImConfig = ImConfig()
        cj.setCode("cj")
        cj.setDesc("倉頡輸入法")
        cj.setDisable(false)
        var dayi: ImConfig = ImConfig()
        dayi.setCode("dayi")
        dayi.setDesc("大易輸入法")
        dayi.setDisable(false)
        var array: ImConfig = ImConfig()
        array.setCode("array")
        array.setDesc("行列輸入法")
        array.setDisable(true)
        var options: MutableList<LIMEPreferenceManager.ReverseLookupOption> = LIMEPreferenceManager.buildReverseLookupOptions(Arrays.asList(cj, dayi, array), "無").filterNotNull().toMutableList()
        assertEquals(Arrays.asList("無", "倉頡輸入法", "大易輸入法"), Arrays.asList(LIMEPreferenceManager.reverseLookupLabels(options)))
        assertEquals(Arrays.asList("none", "cj", "dayi"), Arrays.asList(LIMEPreferenceManager.reverseLookupValues(options)))
    }
    @Test
    fun testAPILevelCompatibility() {
        var currentApiLevel: Int = Build.VERSION.SDK_INT
        var minSdkVersion: Int = 21
        var targetSdkVersion: Int = 36
        assertTrue("Current API level should be >= minSdkVersion", (currentApiLevel >= minSdkVersion))
        assertTrue("Current API level should be <= targetSdkVersion", (currentApiLevel <= targetSdkVersion))
    }
    @Test
    fun testEdgeToEdgeSupport() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        try {
            var windowCompatClass: Class<*> = Class.forName("androidx.core.view.WindowCompat")
            assertNotNull("WindowCompat class should be available", windowCompatClass)
        } catch (e: ClassNotFoundException) {
            fail("WindowCompat should be available for edge-to-edge support")
        }
        try {
            var viewCompatClass: Class<*> = Class.forName("androidx.core.view.ViewCompat")
            assertNotNull("ViewCompat class should be available", viewCompatClass)
        } catch (e: ClassNotFoundException) {
            fail("ViewCompat should be available for edge-to-edge support")
        }
        try {
            var windowInsetsCompatClass: Class<*> = Class.forName("androidx.core.view.WindowInsetsCompat")
            assertNotNull("WindowInsetsCompat class should be available", windowInsetsCompatClass)
        } catch (e: ClassNotFoundException) {
            fail("WindowInsetsCompat should be available for edge-to-edge support")
        }
    }
    @Test
    fun testDatabasePathAccess() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var filesDir: File = appContext.getFilesDir()
        assertNotNull("Files directory should not be null", filesDir)
        assertTrue("Files directory should exist", filesDir.exists())
        var cacheDir: File = appContext.getCacheDir()
        assertNotNull("Cache directory should not be null", cacheDir)
        assertTrue("Cache directory should exist", cacheDir.exists())
    }
    @Test
    fun testSharedPreferencesAccess() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var prefs: android.content.SharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(appContext)
        assertNotNull("SharedPreferences should not be null", prefs)
        var testKey: String = ("test_pref_" + System.currentTimeMillis())
        var testValue: String = "test_value"
        prefs.edit().putString(testKey, testValue).apply()
        var retrievedValue: String = prefs.getString(testKey, null)!!
        assertEquals("Retrieved preference value should match", testValue, retrievedValue)
        prefs.edit().remove(testKey).apply()
    }
    @Test
    fun testConnectivityManagerCompatibility() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var cm: android.net.ConnectivityManager = (appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager)
        assertNotNull("ConnectivityManager should not be null", cm)
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            try {
                var network: android.net.Network = cm.getActiveNetwork()!!
            } catch (e: Exception) {
                fail("getActiveNetwork() should be available on API 23+")
            }
        }
    }
    @Test
    fun testActivityIntents() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var mainIntent: android.content.Intent = android.content.Intent(appContext, LIMESettings::class.java)
        mainIntent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        var pm: android.content.pm.PackageManager = appContext.getPackageManager()
        var resolveInfo: android.content.pm.ResolveInfo = pm.resolveActivity(mainIntent, 0)!!
        assertNotNull("LIMESettings should be resolvable", resolveInfo)
        assertNotNull("LIMESettings component should not be null", resolveInfo.activityInfo)
    }
}
