@file:Suppress("SENSELESS_COMPARISON", "USELESS_IS_CHECK", "PARAMETER_NAME_CHANGED_ON_OVERRIDE", "UNCHECKED_CAST", "TYPE_INTERSECTION_AS_REIFIED")

package net.toload.main.hd

import android.content.Context
import android.util.AttributeSet
import android.util.Xml
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import java.util.ArrayList
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
open class KeyboardLayoutResourceTest {
    companion object {
        private val LIME_ATTR_NS: String = "http://schemas.android.com/apk/res-auto"
        private val ANDROID_ATTR_NS: String = "http://schemas.android.com/apk/res/android"
    }
    @Test
    fun hsLayoutsUseLowercaseUnshiftedAndUppercaseShiftedLetterCodesAndLabels() {
        var context: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        assertLetterKeyCodes(context, R.xml.lime_hs, false)
        assertLetterKeyCodes(context, R.xml.lime_hs_shift, true)
    }
    @Test
    fun customThemeCandidateEmojiIconsUseThemeTintInNormalState() {
        var context: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        assertVectorPaintUsesOnlyColor(context, R.drawable.sym_candidate_emoji_pink, R.color.second_background_pink)
        assertVectorPaintUsesOnlyColor(context, R.drawable.sym_candidate_emoji_tech_blue, R.color.second_background_tech_blue)
        assertVectorPaintUsesOnlyColor(context, R.drawable.sym_candidate_emoji_fashion_purple, R.color.second_background_fashion_purple)
        assertVectorPaintUsesOnlyColor(context, R.drawable.sym_candidate_emoji_relax_green, R.color.second_background_relax_green)
    }
    @Test
    fun candidateEmojiButtonsDoNotUseStickyFocusedTint() {
        var context: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        assertSelectorDoesNotContainFocusedState(context, R.drawable.btn_emoji_light)
        assertSelectorDoesNotContainFocusedState(context, R.drawable.btn_emoji_dark)
        assertSelectorDoesNotContainFocusedState(context, R.drawable.btn_emoji_pink)
        assertSelectorDoesNotContainFocusedState(context, R.drawable.btn_emoji_tech_blue)
        assertSelectorDoesNotContainFocusedState(context, R.drawable.btn_emoji_fashion_purple)
        assertSelectorDoesNotContainFocusedState(context, R.drawable.btn_emoji_relax_green)
    }
    @Test
    fun shiftedSymbolKeysDoNotShowChineseRootSubLabels() {
        var context: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        assertNoSubLabelsOnShiftedSymbolKeys(context, R.xml.lime_phonetic_shift)
        assertNoSubLabelsOnShiftedSymbolKeys(context, R.xml.lime_ez_shift)
        assertNoSubLabelsOnShiftedSymbolKeys(context, R.xml.lime_et_41_shift)
        assertNoSubLabelsOnShiftedSymbolKeys(context, R.xml.lime_dayi_sym_shift)
    }
    @Test
    fun array10AutoCommitRowHasTitleAndSummary() {
        var context: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        assertLayoutContainsTextResource(context, R.layout.fragment_im_detail, R.string.auto_commit)
        assertLayoutContainsTextResource(context, R.layout.fragment_im_detail, R.string.auto_commit_summary)
    }
    @Test
    fun settingsActionLayoutsUseThemeAccentInsteadOfFixedBlue() {
        var context: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        assertLayoutDoesNotReferenceColor(context, R.layout.fragment_db_manager, R.color.material_blue)
        assertLayoutDoesNotReferenceColor(context, R.layout.fragment_im_list, R.color.material_blue)
        assertLayoutDoesNotReferenceColor(context, R.layout.fragment_manage_im, R.color.material_blue)
        assertLayoutDoesNotReferenceColor(context, R.layout.fragment_manage_related, R.color.material_blue)
        assertLayoutDoesNotReferenceColor(context, R.layout.fragment_im_detail, R.color.material_blue)
        assertLayoutDoesNotReferenceColor(context, R.layout.fragment_setup, R.color.material_blue)
        assertLayoutDoesNotReferenceColor(context, R.layout.sheet_manage_im_add, R.color.material_blue)
        assertLayoutDoesNotReferenceColor(context, R.layout.sheet_manage_im_edit, R.color.material_blue)
        assertLayoutDoesNotReferenceColor(context, R.layout.sheet_manage_related_add, R.color.material_blue)
        assertLayoutDoesNotReferenceColor(context, R.layout.sheet_manage_related_edit, R.color.material_blue)
    }
    private fun assertLayoutContainsTextResource(context: Context, layoutId: Int, textResId: Int) {
        var parser: XmlPullParser = context.getResources().getLayout(layoutId)
        while ((parser.next() != XmlPullParser.END_DOCUMENT)) {
            if ((parser.getEventType() != XmlPullParser.START_TAG)) {
                continue
            }
            var attrs: AttributeSet = Xml.asAttributeSet(parser)
            var value: Int = attrs.getAttributeResourceValue(ANDROID_ATTR_NS, "text", 0)
            if ((value == textResId)) {
                return
            }
        }
        fail(((("Layout " + context.getResources().getResourceEntryName(layoutId)) + " should contain text resource ") + context.getResources().getResourceEntryName(textResId)))
    }
    private fun assertLayoutDoesNotReferenceColor(context: Context, layoutId: Int, colorId: Int) {
        var parser: XmlPullParser = context.getResources().getLayout(layoutId)
        while ((parser.next() != XmlPullParser.END_DOCUMENT)) {
            if ((parser.getEventType() != XmlPullParser.START_TAG)) {
                continue
            }
            var attrs: AttributeSet = Xml.asAttributeSet(parser)
            run {
                var i: Int = 0
                while ((i < attrs.getAttributeCount())) {
                    var value: Int = attrs.getAttributeResourceValue(i, 0)
                    assertNotEquals(((((((("Settings layout " + context.getResources().getResourceEntryName(layoutId)) + " should use theme accent instead of fixed ") + context.getResources().getResourceEntryName(colorId)) + " on <") + parser.name) + "> attribute ") + attrs.getAttributeName(i)), colorId, value)
                    i++
                }
            }
        }
    }
    private fun assertLetterKeyCodes(context: Context, layoutId: Int, shouldBeUppercase: Boolean) {
        var letterKeys: MutableList<KeyDefinition> = readLetterKeys(context, layoutId)
        assertFalse("HS layout should contain Latin letter keys", letterKeys.isEmpty())
        for (key in letterKeys) {
            if (shouldBeUppercase) {
                assertTrue(("Shifted HS letter should emit uppercase code: " + key.code), ((key.code >= 'A'.code) && (key.code <= 'Z'.code)))
                assertEquals("Shifted HS letter should show uppercase label", key.label.uppercase(), key.label)
            } else {
                assertTrue(("Unshifted HS letter should emit lowercase code: " + key.code), ((key.code >= 'a'.code) && (key.code <= 'z'.code)))
                assertEquals("Unshifted HS letter should show lowercase label", key.label.lowercase(), key.label)
            }
        }
    }
    private fun readLetterKeys(context: Context, layoutId: Int): MutableList<KeyDefinition> {
        var keys: MutableList<KeyDefinition> = ArrayList()
        try {
            var parser: XmlPullParser = context.getResources().getXml(layoutId)
            var eventType: Int
            while (true) {
                eventType = parser.next()
                if (eventType == XmlPullParser.END_DOCUMENT) break
                if (((eventType != XmlPullParser.START_TAG) || !"Key".equals(parser.getName()))) {
                    continue
                }
                var value: String = parser.getAttributeValue(LIME_ATTR_NS, "codes")
                if ((((value == null) || value.isEmpty()) || value.contains(","))) {
                    continue
                }
                var code: Int = Integer.parseInt(value)
                var label: String = parser.getAttributeValue(LIME_ATTR_NS, "keyLabel")
                if ((((label != null) && (label.length == 1)) && (((code >= 'A'.code) && (code <= 'Z'.code)) || ((code >= 'a'.code) && (code <= 'z'.code))))) {
                    keys.add(KeyDefinition(code, label))
                }
            }
        } catch (e: Exception) {
            fail(((("Unable to read keyboard XML resource " + layoutId) + ": ") + e.getMessage()))
        }
        return keys
    }
    private fun assertNoSubLabelsOnShiftedSymbolKeys(context: Context, layoutId: Int) {
        var sawSymbolKey: Boolean = false
        var sawAlphabetSubLabel: Boolean = false
        try {
            var parser: XmlPullParser = context.getResources().getXml(layoutId)
            var eventType: Int
            while (true) {
                eventType = parser.next()
                if (eventType == XmlPullParser.END_DOCUMENT) break
                if (((eventType != XmlPullParser.START_TAG) || !"Key".equals(parser.getName()))) {
                    continue
                }
                var value: String = parser.getAttributeValue(LIME_ATTR_NS, "codes")
                if ((((value == null) || value.isEmpty()) || value.contains(","))) {
                    continue
                }
                var code: Int = Integer.parseInt(value)
                var label: String = parser.getAttributeValue(LIME_ATTR_NS, "keyLabel")
                if ((label == null)) {
                    continue
                }
                var normalizedLabel: String = label.replace("\\n", "\n")
                if (isUppercaseAsciiLetter(code)) {
                    if (normalizedLabel.contains("\n")) {
                        sawAlphabetSubLabel = true
                    }
                    continue
                }
                if (isPrintableNonAlphabetSymbol(code)) {
                    sawSymbolKey = true
                    assertFalse(((((("Shifted symbol key should not show root sub-label in layout " + layoutId) + ": code=") + code) + " label=") + label), normalizedLabel.contains("\n"))
                }
            }
        } catch (e: Exception) {
            fail(((("Unable to read keyboard XML resource " + layoutId) + ": ") + e.getMessage()))
        }
        assertTrue(("Shifted layout should contain printable symbol keys: " + layoutId), sawSymbolKey)
        assertTrue(("Shifted alphabet roots should remain in layout: " + layoutId), sawAlphabetSubLabel)
    }
    private fun isPrintableNonAlphabetSymbol(code: Int): Boolean {
        return ((((code >= 33) && (code <= 126)) && !isUppercaseAsciiLetter(code)) && !isLowercaseAsciiLetter(code))
    }
    private fun isUppercaseAsciiLetter(code: Int): Boolean {
        return ((code >= 'A'.code) && (code <= 'Z'.code))
    }
    private fun isLowercaseAsciiLetter(code: Int): Boolean {
        return ((code >= 'a'.code) && (code <= 'z'.code))
    }
    private fun assertVectorPaintUsesOnlyColor(context: Context, drawableId: Int, expectedColorId: Int) {
        try {
            var parser: XmlPullParser = context.getResources().getXml(drawableId)
            var paintedPathCount: Int = 0
            var eventType: Int
            while (true) {
                eventType = parser.next()
                if (eventType == XmlPullParser.END_DOCUMENT) break
                if (((eventType != XmlPullParser.START_TAG) || !"path".equals(parser.getName()))) {
                    continue
                }
                paintedPathCount += assertPaintAttributeUsesOnlyColor(parser, drawableId, "fillColor", expectedColorId)
                paintedPathCount += assertPaintAttributeUsesOnlyColor(parser, drawableId, "strokeColor", expectedColorId)
            }
            assertTrue(("Vector should contain painted paths: " + drawableId), (paintedPathCount > 0))
        } catch (e: Exception) {
            fail(((("Unable to read vector drawable " + drawableId) + ": ") + e.getMessage()))
        }
    }
    private fun assertPaintAttributeUsesOnlyColor(parser: XmlPullParser, drawableId: Int, attrName: String, expectedColorId: Int): Int {
        var value: String = parser.getAttributeValue(ANDROID_ATTR_NS, attrName)
        if (((value == null) || "@android:color/transparent".equals(value))) {
            return 0
        }
        var attributes: AttributeSet = Xml.asAttributeSet(parser)
        var colorId: Int = attributes.getAttributeResourceValue(ANDROID_ATTR_NS, attrName, 0)
        if ((colorId == android.R.color.transparent)) {
            return 0
        }
        assertEquals((((("Drawable " + drawableId) + " ") + attrName) + " should use theme color"), expectedColorId, colorId)
        return 1
    }
    private fun assertSelectorDoesNotContainFocusedState(context: Context, drawableId: Int) {
        try {
            var parser: XmlPullParser = context.getResources().getXml(drawableId)
            var eventType: Int
            while (true) {
                eventType = parser.next()
                if (eventType == XmlPullParser.END_DOCUMENT) break
                if (((eventType != XmlPullParser.START_TAG) || !"item".equals(parser.getName()))) {
                    continue
                }
                var attrs: AttributeSet = Xml.asAttributeSet(parser)
                var focused: Boolean = attrs.getAttributeBooleanValue(ANDROID_ATTR_NS, "state_focused", false)
                assertFalse(("Emoji button selector should not keep highlight tint on focus: " + drawableId), focused)
            }
        } catch (e: Exception) {
            fail(((("Unable to read selector drawable " + drawableId) + ": ") + e.getMessage()))
        }
    }
    private open class KeyDefinition {
        var code: Int = 0
        lateinit var label: String
        constructor(code: Int, label: String) {
            this.code = code
            this.label = label
        }
    }
}
