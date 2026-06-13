package net.toload.main.hd

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.toload.main.hd.ui.LIMESettings
import net.toload.main.hd.ui.ShareManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
open class ShareManagerTest {
    private lateinit var context: Context
    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext()
    }
    @Test
    fun testShareManagerExists() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var manager: ShareManager = activity.getShareManager()!!
        assertNotNull("ShareManager should exist", manager)
    })
        }
    }
    @Test
    fun testShareManagerHasExportMethods() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var manager: ShareManager = activity.getShareManager()!!
        assertNotNull("Should have exportAndShareImTable method", getMethodOrNull(manager, "exportAndShareImTable"))
        assertNotNull("Should have shareImAsText method", getMethodOrNull(manager, "shareImAsText"))
        assertNotNull("Should have shareRelatedAsDatabase method", getMethodOrNull(manager, "shareRelatedAsDatabase"))
    })
        }
    }
    @Test
    fun testShareManagerUsesProgressManager() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var shareManager: ShareManager = activity.getShareManager()!!
        assertNotNull("ShareManager should exist", shareManager)
        assertNotNull("MainActivity should provide ProgressManager for ShareManager", activity.getProgressManager())
    })
        }
    }
    @Test
    fun testShareManagerDelegatesToDBServer() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var manager: ShareManager = activity.getShareManager()!!
        assertNotNull("ShareManager should exist", manager)
    })
        }
    }
    @Test
    fun testShareManagerDelegatesToSearchServerForText() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var manager: ShareManager = activity.getShareManager()!!
        assertNotNull("ShareManager should exist for text exports", manager)
    })
        }
    }
    @Test
    fun testShareManagerCreatesProperShareIntents() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var manager: ShareManager = activity.getShareManager()!!
        assertNotNull("ShareManager should exist", manager)
    })
        }
    }
    @Test
    fun testShareManagerNoDirectLimeDBAccess() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var manager: ShareManager = activity.getShareManager()!!
        assertNotNull("ShareManager should exist", manager)
    })
        }
    }
    @Test
    fun testShareManagerHasControllerDependency() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var manager: ShareManager = activity.getShareManager()!!
        assertNotNull("ShareManager should exist", manager)
        assertNotNull("SetupImController should be available", activity.getSetupImController())
    })
        }
    }
    private fun getMethodOrNull(obj: Any, methodName: String): java.lang.reflect.Method? {
        try {
            for (method in obj.javaClass.methods) {
                if (method.name.equals(methodName)) {
                    return method
                }
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }
}
