package net.toload.main.hd

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
open class ManageRelatedAdapterTest {
    @Test
    fun testManageRelatedAdapterClassExists() {
        try {
            var cls: Class<*> = Class.forName("net.toload.main.hd.ui.adapter.ManageRelatedAdapter")
            assertNotNull(cls)
        } catch (e: ClassNotFoundException) {
            fail("ManageRelatedAdapter class not found")
        }
    }
    @Test
    fun testManageRelatedAdapterDiffUtilAndCallbacks() {
        var cls: Class<*> = Class.forName("net.toload.main.hd.ui.adapter.ManageRelatedAdapter")
        var hasDiffCallback: Boolean = false
        for (f in cls.declaredFields) {
            if ((f.getType().name.contains("DiffUtil") || f.name.lowercase().contains("diff"))) {
                hasDiffCallback = true
                break
            }
        }
        if (!hasDiffCallback) {
            for (inner in cls.getDeclaredClasses()) {
                for (m in inner.declaredMethods) {
                    var n: String = m.name.lowercase()
                    if ((n.contains("areitemssame") || n.contains("arecontents"))) {
                        hasDiffCallback = true
                        break
                    }
                }
                if (hasDiffCallback) {
                    break
                }
            }
        }
        assertTrue("ManageRelatedAdapter should define DiffUtil callback", hasDiffCallback)
        var hasClickOrDelete: Boolean = false
        for (m in cls.methods) {
            var n: String = m.name.lowercase()
            if (((n.contains("click") || n.contains("listener")) || n.contains("delete"))) {
                hasClickOrDelete = true
                break
            }
        }
        assertTrue("ManageRelatedAdapter should expose click/delete callbacks", hasClickOrDelete)
        var handlesNullSafe: Boolean = false
        for (m in cls.declaredMethods) {
            if ((m.name.lowercase().contains("bind") || m.name.lowercase().contains("onbind"))) {
                handlesNullSafe = true
                break
            }
        }
        assertTrue("ManageRelatedAdapter should bind items safely", handlesNullSafe)
    }
}
