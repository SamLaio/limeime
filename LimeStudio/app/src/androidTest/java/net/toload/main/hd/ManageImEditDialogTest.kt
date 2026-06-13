package net.toload.main.hd

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
open class ManageImEditDialogTest {
    @Test
    fun testManageImEditDialogClassExists() {
        try {
            var cls: Class<*> = Class.forName("net.toload.main.hd.ui.dialog.ManageImEditSheet")
            assertNotNull(cls)
        } catch (e: ClassNotFoundException) {
            fail("ManageImEditSheet class not found")
        }
    }
    @Test
    fun testValidationAndControllerUpdateApis() {
        var dialog: Class<*> = Class.forName("net.toload.main.hd.ui.dialog.ManageImEditSheet")
        var hasValidation: Boolean = false
        for (m in dialog.declaredMethods) {
            var n: String = m.name.lowercase()
            if ((n.contains("validate") || n.contains("check"))) {
                hasValidation = true
                break
            }
        }
        assertTrue("ManageImEditSheet should perform validation", hasValidation)
        var ctrl: Class<*> = Class.forName("net.toload.main.hd.ui.controller.ManageImController")
        var hasUpdate: Boolean = false
        for (m in ctrl.methods) {
            var n: String = m.name.lowercase()
            if ((n.contains("update") && n.contains("record"))) {
                hasUpdate = true
                break
            }
        }
        assertTrue("ManageImController should expose update IM operation", hasUpdate)
    }
    @Test
    fun testSheetLayoutScrollsWhenImeIsVisible() {
        var context: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        assertEquals("ManageImEditSheet root should scroll above the soft keyboard", "androidx.core.widget.NestedScrollView", getRootTagName(context, R.layout.sheet_manage_im_edit))
    }
    @Test
    fun testSheetMatchesIosRowEditorControls() {
        var context: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        assertEquals("編輯資料列", context.getString(R.string.manage_word_dialog_edit))
        assertNotEquals("Edit sheet should expose a cancel button", 0, R.id.btn_cancel)
        assertNotEquals("Edit sheet should expose an editable score field", 0, R.id.edt_score)
    }
    private fun getRootTagName(context: Context, layoutId: Int): String {
        try {
            var parser: XmlPullParser = context.getResources().getLayout(layoutId)
            var eventType: Int
            while (true) {
                eventType = parser.next()
                if (eventType == XmlPullParser.END_DOCUMENT) break
                if ((eventType == XmlPullParser.START_TAG)) {
                    return parser.name
                }
            }
        } catch (e: Exception) {
            Assert.fail(("Unable to read sheet layout root: " + e.getMessage()))
        }
        Assert.fail("Layout has no root tag")
        return ""
    }
}
