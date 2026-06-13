package net.toload.main.hd

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
open class ManageImAdapterTest {
    @Test
    fun testManageImAdapterClassExists() {
        try {
            var cls: Class<*> = Class.forName("net.toload.main.hd.ui.adapter.ManageImAdapter")
            assertNotNull(cls)
        } catch (e: ClassNotFoundException) {
            fail("ManageImAdapter class not found")
        }
    }
    @Test
    fun testManageImAdapterDiffUtilAndClickApis() {
        var cls: Class<*> = Class.forName("net.toload.main.hd.ui.adapter.ManageImAdapter")
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
        assertTrue("ManageImAdapter should define DiffUtil callback", hasDiffCallback)
        var hasOnBindOrTruncate: Boolean = false
        for (m in cls.declaredMethods) {
            var n: String = m.name.lowercase()
            if (((n.contains("bind") || n.contains("truncate")) || n.contains("ellips"))) {
                hasOnBindOrTruncate = true
                break
            }
        }
        assertTrue("ManageImAdapter should have bind/truncate logic", hasOnBindOrTruncate)
        var hasClick: Boolean = false
        for (m in cls.methods) {
            if ((m.name.lowercase().contains("click") || m.name.lowercase().contains("listener"))) {
                hasClick = true
                break
            }
        }
        assertTrue("ManageImAdapter should expose click/listener handling", hasClick)
    }
}
