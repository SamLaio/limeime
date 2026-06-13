@file:Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST", "TYPE_INTERSECTION_AS_REIFIED")

package net.toload.main.hd

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.toload.main.hd.ui.LIMESettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
open class ManageImKeyboardDialogTest {
    @Test
    fun testManageImKeyboardDialogClassExists() {
        try {
            var cls: Class<*> = Class.forName("net.toload.main.hd.ui.dialog.ManageImKeyboardDialog")
            assertNotNull(cls)
        } catch (e: ClassNotFoundException) {
            fail("ManageImKeyboardDialog class not found")
        }
    }
    @Test
    fun testActivityProvidesController() {
        androidx.test.core.app.ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        try {
            var getter: java.lang.reflect.Method = activity.javaClass.getMethod("getManageImController")
            var controller: Any? = getter.invoke(activity)
            assertNotNull("LIMESettings.getManageImController() should return controller", controller)
        } catch (e: Exception) {
            fail(("LIMESettings should expose getManageImController(): " + e.getMessage()))
        }
    })
        }
    }
    @Test
    fun testControllerKeyboardApisExist() {
        var ctrl: Class<*> = Class.forName("net.toload.main.hd.ui.controller.ManageImController")
        var hasGetKeyboardList: Boolean = false
        var hasSetImKeyboard: Boolean = false
        for (m in ctrl.methods) {
            var n: String = m.name
            if (n.equals("getKeyboardList")) {
                hasGetKeyboardList = true
            }
            if (n.equals("setIMKeyboard")) {
                hasSetImKeyboard = true
            }
        }
        assertTrue("ManageImController.getKeyboardList() present", hasGetKeyboardList)
        assertTrue("ManageImController.setIMKeyboard(...) present", hasSetImKeyboard)
    }
}
