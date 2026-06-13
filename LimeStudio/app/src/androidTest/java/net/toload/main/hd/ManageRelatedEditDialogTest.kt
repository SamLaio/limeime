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
open class ManageRelatedEditDialogTest {
    @Test
    fun testManageRelatedEditDialogClassExists() {
        try {
            var cls: Class<*> = Class.forName("net.toload.main.hd.ui.dialog.ManageRelatedEditSheet")
            assertNotNull(cls)
        } catch (e: ClassNotFoundException) {
            fail("ManageRelatedEditSheet class not found")
        }
    }
    @Test
    fun testValidationAndControllerUpdateRelatedApis() {
        var dialog: Class<*> = Class.forName("net.toload.main.hd.ui.dialog.ManageRelatedEditSheet")
        var hasValidation: Boolean = false
        for (m in dialog.declaredMethods) {
            var n: String = m.name.lowercase()
            if ((n.contains("validate") || n.contains("check"))) {
                hasValidation = true
                break
            }
        }
        assertTrue("ManageRelatedEditSheet should perform validation", hasValidation)
        var ctrl: Class<*> = Class.forName("net.toload.main.hd.ui.controller.ManageImController")
        var hasUpdateRelated: Boolean = false
        for (m in ctrl.methods) {
            var n: String = m.name.lowercase()
            if ((n.contains("update") && n.contains("related"))) {
                hasUpdateRelated = true
                break
            }
        }
        assertTrue("ManageImController should expose update related operation", hasUpdateRelated)
    }
    @Test
    fun testSheetLayoutScrollsWhenImeIsVisible() {
        var context: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        assertEquals("ManageRelatedEditSheet root should scroll above the soft keyboard", "androidx.core.widget.NestedScrollView", getRootTagName(context, R.layout.sheet_manage_related_edit))
    }
    @Test
    fun testSheetMatchesIosRowEditorControls() {
        var context: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        assertEquals("編輯資料列", context.getString(R.string.manage_related_dialog_edit))
        assertNotEquals("Related edit sheet should expose a cancel button", 0, R.id.btn_cancel)
        assertNotEquals("Related edit sheet should expose an editable score field", 0, R.id.edt_score)
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
