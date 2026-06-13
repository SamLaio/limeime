@file:Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST", "TYPE_INTERSECTION_AS_REIFIED")

package net.toload.main.hd

import android.content.Context
import androidx.fragment.app.DialogFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.toload.main.hd.ui.LIMESettings
import net.toload.main.hd.ui.dialog.HelpDialog
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
open class HelpDialogTest {
    companion object {
        private var sharedDbServer: DBServer? = null
    }
    @Before
    fun setUp() {
        var appContext: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        if ((sharedDbServer == null)) {
            sharedDbServer = DBServer.getInstance(appContext)!!
        }
        var stillOnHold: Boolean = false
        run {
            var i: Int = 0
            while ((i < 100)) {
                if (sharedDbServer!!.isDatabseOnHold()) {
                    break
                }
                stillOnHold = true
                if ((i < 99)) {
                    try {
                        Thread.sleep(100)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
                i++
            }
        }
        if ((stillOnHold && sharedDbServer!!.isDatabseOnHold())) {
            try {
                var datasourceField: java.lang.reflect.Field = DBServer::class.java.getDeclaredField("datasource")
                datasourceField.setAccessible(true)
                var datasource: net.toload.main.hd.limedb.LimeDB = (datasourceField.get(sharedDbServer) as net.toload.main.hd.limedb.LimeDB)
                if ((datasource != null)) {
                    datasource.openDBConnection(true)
                }
            } catch (e: Exception) {

            }
        }
    }
    @After
    fun tearDown() {
        if (((sharedDbServer != null) && sharedDbServer!!.isDatabseOnHold())) {
            try {
                var datasourceField: java.lang.reflect.Field = DBServer::class.java.getDeclaredField("datasource")
                datasourceField.setAccessible(true)
                var datasource: net.toload.main.hd.limedb.LimeDB = (datasourceField.get(sharedDbServer) as net.toload.main.hd.limedb.LimeDB)
                if ((datasource != null)) {
                    datasource.openDBConnection(true)
                }
            } catch (e: Exception) {

            }
        }
    }
    @Test
    fun testHelpDialogClassExists() {
        try {
            var cls: Class<*> = Class.forName("net.toload.main.hd.ui.dialog.HelpDialog")
            assertNotNull(cls)
        } catch (e: ClassNotFoundException) {
            fail("HelpDialog class not found")
        }
    }
    @Test
    fun testHasLinkOrButtonHandlers() {
        var cls: Class<*> = Class.forName("net.toload.main.hd.ui.dialog.HelpDialog")
        var hasClick: Boolean = false
        for (m in cls.declaredMethods) {
            var n: String = m.name.lowercase()
            if (((n.contains("click") || n.contains("button")) || n.contains("link"))) {
                hasClick = true
                break
            }
        }
        assertTrue("HelpDialog should define link/button handlers", hasClick)
    }
    @org.junit.Ignore("Deprecated: first-launch help splash disabled in commit 6f36521a and LIMESettings startup lifecycle changed (docs/LIME_SETTINGS_BACKPORT.md, docs/DEPCECATED_UI_TESTS.md).")
    @Test(timeout = 60000)
    fun testHelpDialogSurvivesRecreation() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var dialog: DialogFragment = HelpDialog.newInstance()
        dialog.show(activity.getSupportFragmentManager(), "helpdialog-smoke")
    })
                Thread.sleep(500)
                scenario.onActivity({ activity ->
        var dialog: DialogFragment = (activity.getSupportFragmentManager().findFragmentByTag("helpdialog-smoke") as DialogFragment)
        assertNotNull("Dialog should be shown before recreation", dialog)
    })
                scenario.recreate()
                Thread.sleep(500)
                scenario.onActivity({ activity ->
        var dialog: DialogFragment = (activity.getSupportFragmentManager().findFragmentByTag("helpdialog-smoke") as DialogFragment)
        assertNotNull("Dialog should survive recreation", dialog)
        if ((dialog != null)) {
            dialog.dismissAllowingStateLoss()
        }
    })
                Thread.sleep(200)
        }
    }
}
