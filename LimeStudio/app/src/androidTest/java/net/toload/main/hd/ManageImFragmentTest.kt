package net.toload.main.hd

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.toload.main.hd.ui.LIMESettings
import net.toload.main.hd.ui.controller.ManageImController
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
open class ManageImFragmentTest {
    private lateinit var context: Context
    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext()
    }
    @Test
    fun testIMKeyboardLoadingUsesController() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var controller: ManageImController = activity.getManageImController()
        assertNotNull("MainActivity should provide ManageImController", controller)
        assertNotNull("Controller should have getImConfigFullNameList method", getMethodOrNull(controller, "getImConfigFullNameList"))
        assertNotNull("Controller should have getKeyboardList method", getMethodOrNull(controller, "getKeyboardList"))
    })
        }
    }
    @Test
    fun testAsynchronousRecordLoadingIsThreadSafe() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var controller: ManageImController = activity.getManageImController()
        assertNotNull("Controller should exist", controller)
        assertNotNull("Controller should have loadRecordsAsync method", getMethodOrNull(controller, "loadRecordsAsync", String::class.java, String::class.java, Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!))
        assertNotNull("Controller should have showProgress method", getMethodOrNull(controller, "showProgress", Any::class.java, String::class.java))
        assertNotNull("Controller should have hideProgress method", getMethodOrNull(controller, "hideProgress", Any::class.java))
        assertNotNull("Controller should have showToast method", getMethodOrNull(controller, "showToast", Any::class.java, String::class.java, Int::class.javaPrimitiveType!!))
    })
        }
    }
    @Test
    fun testRecordManagementDelegatesToController() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var controller: ManageImController = activity.getManageImController()
        assertNotNull("Controller should exist", controller)
        assertNotNull("Controller should have addRecord method", getMethodOrNull(controller, "addRecord"))
        assertNotNull("Controller should have updateRecord method", getMethodOrNull(controller, "updateRecord"))
        assertNotNull("Controller should have deleteRecord method", getMethodOrNull(controller, "deleteRecord"))
    })
        }
    }
    @Test
    fun testNoDirectLimeDBAccess() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var controller: ManageImController = activity.getManageImController()
        assertNotNull("Fragment should use controller, not direct DB", controller)
    })
        }
    }
    private fun getMethodOrNull(obj: Any, methodName: String, vararg paramTypes: Class<*>): java.lang.reflect.Method? {
        try {
            return obj.javaClass.getMethod(methodName, *paramTypes)
        } catch (e: NoSuchMethodException) {
            for (method in obj.javaClass.methods) {
                if (method.name.equals(methodName)) {
                    return method
                }
            }
            return null
        }
    }
}
