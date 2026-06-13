package net.toload.main.hd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.toload.main.hd.global.PreferenceBackupAdapter
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.ObjectOutputStream
import java.io.FileOutputStream
import java.util.Arrays
import java.util.HashMap
import java.util.LinkedHashMap

@RunWith(AndroidJUnit4::class)
open class PreferenceBackupAdapterTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().commit()
    }
    @After
    fun tearDown() {
        prefs.edit().clear().commit()
    }
    @Test
    fun exportManifestBacksUpFullPrefsTableSetWithCanonicalTypes() {
        var expected: MutableMap<String, Any> = fullAndroidPrefsTableFixture()
        seedPrefs(expected)
        prefs.edit().putString("PAYMENT_FLAG", "do-not-export").commit()
        var manifest: JSONObject = PreferenceBackupAdapter.exportManifest(context)
        var values: JSONObject = manifest.getJSONObject("preferences")
        assertEquals(1, manifest.getInt("schema"))
        assertEquals("android", manifest.getString("sourcePlatform"))
        assertEquals("Manifest must contain exactly the full Android PREFS_TABLE set seeded by this test", expected.size, values.length)
        assertManifestValues(values, expected)
        assertFalse(values.has("PAYMENT_FLAG"))
    }
    @Test
    fun restoreManifestConvertsCanonicalTypesToAndroidStorage() {
        var values: JSONObject = JSONObject()
        var manifest: JSONObject = JSONObject()
        assertTrue(PreferenceBackupAdapter.restoreManifest(context, manifest))
        assertEquals("4", prefs.getString("keyboard_theme", null))
        assertEquals("1", prefs.getString("keyboard_size", null))
        assertEquals("2", prefs.getString("show_arrow_key", null))
        assertEquals("80", prefs.getString("vibrate_level", null))
        assertEquals("2", prefs.getString("han_convert_option", null))
        assertEquals("dayi", prefs.getString("custom_im_reverselookup", null))
        assertEquals("3", prefs.getString("auto_commit", null))
        assertFalse(prefs.getBoolean("smart_chinese_input", true))
        assertTrue(prefs.getBoolean("physical_keyboard_sort", false))
        assertFalse(prefs.contains("unknown_pref"))
    }
    @Test
    fun restoreIosStyleManifestOnAndroid() {
        var values: JSONObject = JSONObject()
        var manifest: JSONObject = JSONObject()
        assertTrue(PreferenceBackupAdapter.restoreManifest(context, manifest))
        assertEquals("4", prefs.getString("keyboard_theme", null))
        assertEquals("1", prefs.getString("keyboard_size", null))
        assertFalse(prefs.getBoolean("smart_chinese_input", true))
        assertFalse(prefs.contains("ios_only_future_key"))
    }
    @Test
    fun restoreManifestRestoresEveryAndroidPrefsTableValue() {
        var expected: MutableMap<String, Any> = fullAndroidPrefsTableFixture()
        var values: JSONObject = JSONObject()
        for (entry in expected.entries) {
            values.put(entry.getKey(), entry.getValue())
        }
        values.put("unknown_pref", "ignored")
        var manifest: JSONObject = JSONObject()
        assertTrue(PreferenceBackupAdapter.restoreManifest(context, manifest))
        for (entry in expected.entries) {
            assertStoredValue(entry.getKey(), entry.getValue())
        }
        assertFalse(prefs.contains("unknown_pref"))
    }
    @Test
    fun restoreManifestIgnoresWrongTypesAndInvalidSchema() {
        var values: JSONObject = JSONObject()
        var manifest: JSONObject = JSONObject()
        assertTrue(PreferenceBackupAdapter.restoreManifest(context, manifest))
        assertFalse(prefs.contains("keyboard_theme"))
        assertFalse(prefs.contains("smart_chinese_input"))
        var invalidSchema: JSONObject = JSONObject()
        assertFalse(PreferenceBackupAdapter.restoreManifest(context, invalidSchema))
        assertFalse(prefs.contains("keyboard_theme"))
    }
    @Test
    fun restoreManifestRejectsOversizedInput() {
        var oversized: ByteArray = ByteArray(((1024 * 1024) + 1))
        assertFalse(PreferenceBackupAdapter.restoreManifest(context, ByteArrayInputStream(oversized)))
    }
    @Test
    fun legacyPreferenceRestoreSkipsPaymentFlagByKey() {
        var backup: File = File(context.getCacheDir(), (("legacy_payment_flag_" + System.currentTimeMillis()) + ".bak"))
        var legacyValues: MutableMap<String, Any> = HashMap()
        legacyValues.put("keyboard_theme", "4")
        legacyValues.put("safe_string", "PAYMENT_FLAG")
        legacyValues.put("PAYMENT_FLAG", true)
        ObjectOutputStream(FileOutputStream(backup)).use { output ->
                output.writeObject(legacyValues)
        }
        DBServer.getInstance(context)!!.restoreDefaultSharedPreference(backup)
        assertEquals("4", prefs.getString("keyboard_theme", null))
        assertEquals("PAYMENT_FLAG", prefs.getString("safe_string", null))
        assertFalse(prefs.contains("PAYMENT_FLAG"))
        if (backup.exists()) {
            backup.delete()
        }
    }
    private fun fullAndroidPrefsTableFixture(): MutableMap<String, Any> {
        var values: MutableMap<String, Any> = LinkedHashMap()
        values.put("keyboard_theme", 4)
        values.put("keyboard_size", "1")
        values.put("font_size", "2")
        values.put("number_row_in_english", false)
        values.put("show_arrow_key", 2)
        values.put("split_keyboard_mode", 1)
        values.put("vibrate_on_keypress", false)
        values.put("vibrate_level", 80)
        values.put("sound_on_keypress", true)
        values.put("smart_chinese_input", false)
        values.put("auto_chinese_symbol", true)
        values.put("candidate_switch", true)
        values.put("persistent_language_mode", true)
        values.put("enable_emoji_position", 3)
        values.put("similiar_list", 30)
        values.put("han_convert_option", 2)
        values.put("similiar_enable", false)
        values.put("candidate_suggestion", false)
        values.put("learn_phrase", false)
        values.put("learning_switch", false)
        values.put("english_dictionary_enable", false)
        values.put("auto_cap", false)
        values.put("custom_im_reverselookup", "dayi")
        values.put("cj_im_reverselookup", "phonetic")
        values.put("scj_im_reverselookup", "cj")
        values.put("cj5_im_reverselookup", "scj")
        values.put("ecj_im_reverselookup", "cj5")
        values.put("dayi_im_reverselookup", "bpmf")
        values.put("bpmf_im_reverselookup", "dayi")
        values.put("phonetic_im_reverselookup", "custom")
        values.put("ez_im_reverselookup", "array")
        values.put("array_im_reverselookup", "array10")
        values.put("array10_im_reverselookup", "ez")
        values.put("wb_im_reverselookup", "hs")
        values.put("hs_im_reverselookup", "pinyin")
        values.put("pinyin_im_reverselookup", "none")
        values.put("phonetic_keyboard_type", "standard")
        values.put("auto_commit", 3)
        values.put("accept_number_index", true)
        values.put("accept_symbol_index", true)
        values.put("backup_on_delete_phonetic", false)
        values.put("restore_on_import_phonetic", false)
        values.put("hide_software_keyboard_typing_with_physical", false)
        values.put("switch_english_mode", true)
        values.put("switch_english_mode_shift", false)
        values.put("disable_physical_selkey", true)
        values.put("selkey_option", 2)
        values.put("english_dictionary_physical_keyboard", true)
        values.put("physical_keyboard_sort", true)
        return values
    }
    private fun seedPrefs(values: MutableMap<String, Any>) {
        var editor: SharedPreferences.Editor = prefs.edit()
        for (entry in values.entries) {
            var value: Any = entry.getValue()
            if ((value is Boolean)) {
                editor.putBoolean(entry.getKey(), (value as Boolean))
            } else {
                if (((value is Int) && isAndroidStringBackedInteger(entry.getKey()))) {
                    editor.putString(entry.getKey(), java.lang.String.valueOf(value))
                } else {
                    if ((value is String)) {
                        editor.putString(entry.getKey(), (value as String))
                    }
                }
            }
        }
        editor.commit()
    }
    private fun assertManifestValues(actual: JSONObject, expected: MutableMap<String, Any>) {
        for (entry in expected.entries) {
            var key: String = entry.getKey()
            var expectedValue: Any = entry.getValue()
            if ((expectedValue is Boolean)) {
                assertEquals((key + " should be backed up as a boolean"), expectedValue, actual.getBoolean(key))
            } else {
                if ((expectedValue is Int)) {
                    assertEquals((key + " should be backed up as an integer"), expectedValue, actual.getInt(key))
                } else {
                    if ((expectedValue is String)) {
                        assertEquals((key + " should be backed up as a string"), expectedValue, actual.getString(key))
                    }
                }
            }
        }
    }
    private fun assertStoredValue(key: String, expectedValue: Any) {
        if ((expectedValue is Boolean)) {
            assertEquals((key + " should restore as a boolean"), expectedValue, prefs.getBoolean(key, (expectedValue as Boolean)))
        } else {
            if ((expectedValue is Int)) {
                assertEquals((key + " should restore as Android string-backed integer"), java.lang.String.valueOf(expectedValue), prefs.getString(key, null))
            } else {
                if ((expectedValue is String)) {
                    assertEquals((key + " should restore as a string"), expectedValue, prefs.getString(key, null))
                }
            }
        }
    }
    private fun isAndroidStringBackedInteger(key: String): Boolean {
        return Arrays.asList("keyboard_theme", "show_arrow_key", "split_keyboard_mode", "vibrate_level", "enable_emoji_position", "similiar_list", "han_convert_option", "auto_commit", "selkey_option").contains(key)
    }
}
