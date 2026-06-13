package net.toload.main.hd

import androidx.fragment.app.DialogFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.toload.main.hd.ui.LIMESettings
import net.toload.main.hd.ui.dialog.NewsDialog
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
open class NewsDialogTest {
    @Test
    fun testNewsDialogClassExists() {
        try {
            var cls: Class<*> = Class.forName("net.toload.main.hd.ui.dialog.NewsDialog")
            assertNotNull(cls)
        } catch (e: ClassNotFoundException) {
            fail("NewsDialog class not found")
        }
    }
    @Test
    fun testHasLinkOrButtonHandlers() {
        var cls: Class<*> = Class.forName("net.toload.main.hd.ui.dialog.NewsDialog")
        var hasClick: Boolean = false
        for (m in cls.declaredMethods) {
            var n: String = m.name.lowercase()
            if (((n.contains("click") || n.contains("button")) || n.contains("link"))) {
                hasClick = true
                break
            }
        }
        assertTrue("NewsDialog should define link/button handlers", hasClick)
    }
    @org.junit.Ignore("Deprecated: first-launch news/help splash flow disabled in commit 6f36521a; see docs/DEPCECATED_UI_TESTS.md.")
    @Test
    fun testNewsDialogSurvivesRecreation() {
        ActivityScenario.launch(LIMESettings::class.java).use { scenario ->
                scenario.onActivity({ activity ->
        var dialog: DialogFragment = NewsDialog.newInstance()
        dialog.show(activity.getSupportFragmentManager(), "newsdialog-smoke")
    })
                scenario.recreate()
        }
    }
}
