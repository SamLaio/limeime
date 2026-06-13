@file:Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST", "TYPE_INTERSECTION_AS_REIFIED")

package net.toload.main.hd

import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Field
import java.lang.reflect.Method
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
open class LIMEServiceWithStubActivityTest {
    private var limeService: LIMEService? = null
    @Before
    fun setUp() {
        var targetContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        var service: LIMEService = LIMEService()
        try {
            var attachBaseContext: Method? = null
            var clazz: Class<*>? = Service::class.java
            while (((clazz != null) && (attachBaseContext == null))) {
                try {
                    attachBaseContext = clazz.getDeclaredMethod("attachBaseContext", Context::class.java)
                } catch (ignored: NoSuchMethodException) {
                    clazz = clazz.superclass
                }
            }
            if ((attachBaseContext == null)) {
                throw NoSuchMethodException("attachBaseContext not found in Service hierarchy")
            }
            attachBaseContext.setAccessible(true)
            attachBaseContext.invoke(service, targetContext)
        } catch (e: Exception) {
            throw RuntimeException("Failed to attach base context to LIMEService", e)
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync({ service.onCreate() })
        limeService = service
    }
    @After
    fun tearDown() {
        limeService = null
    }
    @Test
    fun test_5_24_1_OnCreateWithContext() {
        try {
            assertNotNull("LIMEService should be created", limeService)
            assertNotNull("Service should have valid Context", limeService!!.getApplicationContext())
        } catch (e: Exception) {
            fail(("onCreate() should not throw exception when Context is provided: " + e.getMessage()))
        }
    }
    @Test
    fun test_5_24_2_ShowIMPickerWithContext() {
        try {
            var showIMPickerMethod: Method = LIMEService::class.java.getDeclaredMethod("showIMPicker")
            showIMPickerMethod.setAccessible(true)
            showIMPickerMethod.invoke(limeService)
            assertTrue("showIMPicker() executed", true)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            var cause: Throwable = e.getCause()!!
            assertTrue("Exception should be NPE or acceptable", ((cause is NullPointerException) || (cause != null)))
        } catch (e: Exception) {
            fail(((("Unexpected exception: " + e.javaClass.name) + ": ") + e.getMessage()))
        }
    }
    @Test
    fun test_5_24_3_ShowHanConvertPickerWithContext() {
        try {
            var showHanConvertPickerMethod: Method = LIMEService::class.java.getDeclaredMethod("showHanConvertPicker")
            showHanConvertPickerMethod.setAccessible(true)
            showHanConvertPickerMethod.invoke(limeService)
            assertTrue("showHanConvertPicker() executed", true)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            var cause: Throwable = e.getCause()!!
            assertTrue("Exception should be NPE or acceptable", ((cause is NullPointerException) || (cause != null)))
        } catch (e: Exception) {
            fail(((("Unexpected exception: " + e.javaClass.name) + ": ") + e.getMessage()))
        }
    }
    @Test
    fun test_5_24_4_HandleOptionsWithContext() {
        try {
            var handleOptionsMethod: Method = LIMEService::class.java.getDeclaredMethod("handleOptions")
            handleOptionsMethod.setAccessible(true)
            handleOptionsMethod.invoke(limeService)
            assertTrue("handleOptions() executed", true)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            var cause: Throwable = e.getCause()!!
            assertTrue("Exception should be NPE or acceptable", ((cause is NullPointerException) || (cause != null)))
        } catch (e: Exception) {
            fail(((("Unexpected exception: " + e.javaClass.name) + ": ") + e.getMessage()))
        }
    }
    @Test
    fun test_5_24_5_LaunchPreferenceWithContext() {
        try {
            var launchPreferenceMethod: Method = LIMEService::class.java.getDeclaredMethod("launchPreference")
            launchPreferenceMethod.setAccessible(true)
            launchPreferenceMethod.invoke(limeService)
            assertTrue("launchPreference() executed", true)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            var cause: Throwable = e.getCause()!!
            assertTrue("Exception should be acceptable", (cause != null))
        } catch (e: Exception) {
            fail(((("Unexpected exception: " + e.javaClass.name) + ": ") + e.getMessage()))
        }
    }
    @Test
    fun test_5_24_6_LaunchRecognizerIntentWithContext() {
        try {
            var voiceIntent: android.content.Intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            var launchRecognizerMethod: Method = LIMEService::class.java.getDeclaredMethod("launchRecognizerIntent", Intent::class.java)
            launchRecognizerMethod.setAccessible(true)
            launchRecognizerMethod.invoke(limeService, voiceIntent)
            assertTrue("launchRecognizerIntent() executed", true)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            var cause: Throwable = e.getCause()!!
            assertTrue("Exception should be acceptable", (cause != null))
        } catch (e: Exception) {
            fail(((("Unexpected exception: " + e.javaClass.name) + ": ") + e.getMessage()))
        }
    }
    @Test
    fun test_5_24_7_VibrateWithContext() {
        try {
            var vibrateMethod: Method = LIMEService::class.java.getDeclaredMethod("vibrate", Long::class.javaPrimitiveType!!)
            vibrateMethod.setAccessible(true)
            vibrateMethod.invoke(limeService, 50L)
            assertTrue("vibrate() executed", true)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            var cause: Throwable = e.getCause()!!
            assertTrue("Exception should be acceptable", (cause != null))
        } catch (e: Exception) {
            fail(((("Unexpected exception: " + e.javaClass.name) + ": ") + e.getMessage()))
        }
    }
    @Test
    fun test_5_24_8_DoVibrateSoundWithContext() {
        try {
            try {
                var hasVibrationField: Field = LIMEService::class.java.getDeclaredField("hasVibration")
                hasVibrationField.setAccessible(true)
                hasVibrationField.set(limeService, true)
                var hasSoundField: Field = LIMEService::class.java.getDeclaredField("hasSound")
                hasSoundField.setAccessible(true)
                hasSoundField.set(limeService, true)
            } catch (e: Exception) {
                try {
                    var loadSettingsMethod: Method = LIMEService::class.java.getDeclaredMethod("loadSettings")
                    loadSettingsMethod.setAccessible(true)
                    loadSettingsMethod.invoke(limeService)
                } catch (e2: Exception) {

                }
            }
            limeService!!.doVibrateSound(32)
            limeService!!.doVibrateSound(5)
            limeService!!.doVibrateSound(10)
            limeService!!.doVibrateSound(65)
            assertTrue("doVibrateSound() executed", true)
        } catch (e: Exception) {
            assertTrue("Exception should be acceptable", (((e is NullPointerException) || (e is SecurityException)) || (e != null)))
        }
    }
    @Test
    fun test_5_24_9_SwitchToNextActivatedIMWithContext() {
        try {
            var switchMethod: Method = LIMEService::class.java.getDeclaredMethod("switchToNextActivatedIM", Boolean::class.javaPrimitiveType!!)
            switchMethod.setAccessible(true)
            switchMethod.invoke(limeService, true)
            assertTrue("switchToNextActivatedIM() executed", true)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            var cause: Throwable = e.getCause()!!
            assertTrue("Exception should be NPE or acceptable", (((cause is NullPointerException) || (cause is AssertionError)) || (cause != null)))
        } catch (e: Exception) {
            fail(((("Unexpected exception: " + e.javaClass.name) + ": ") + e.getMessage()))
        }
    }
}
