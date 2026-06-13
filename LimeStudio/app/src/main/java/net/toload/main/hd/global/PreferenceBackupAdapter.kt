package net.toload.main.hd.global

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import org.json.JSONException
import org.json.JSONObject

object PreferenceBackupAdapter {
    const val SCHEMA_VERSION: Int = 1
    const val MANIFEST_PATH: String = "preferences/lime_prefs.json"
    private val MAX_MANIFEST_BYTES = 1024 * 1024

    private val SPECS: MutableMap<String?, Spec> = LinkedHashMap<String?, Spec>()

    init {
        add("keyboard_theme", Type.INTEGER_AS_STRING)
        add("keyboard_size", Type.STRING)
        add("font_size", Type.STRING)
        add("number_row_in_english", Type.BOOLEAN)
        add("show_arrow_key", Type.INTEGER_AS_STRING)
        add("split_keyboard_mode", Type.INTEGER_AS_STRING)
        add("vibrate_on_keypress", Type.BOOLEAN)
        add("vibrate_level", Type.INTEGER_AS_STRING)
        add("sound_on_keypress", Type.BOOLEAN)
        add("smart_chinese_input", Type.BOOLEAN)
        add("auto_chinese_symbol", Type.BOOLEAN)
        add("candidate_switch", Type.BOOLEAN)
        add("persistent_language_mode", Type.BOOLEAN)
        add("enable_emoji_position", Type.INTEGER_AS_STRING)
        add("similiar_list", Type.INTEGER_AS_STRING)
        add("han_convert_option", Type.INTEGER_AS_STRING)
        add("similiar_enable", Type.BOOLEAN)
        add("candidate_suggestion", Type.BOOLEAN)
        add("learn_phrase", Type.BOOLEAN)
        add("learning_switch", Type.BOOLEAN)
        add("english_dictionary_enable", Type.BOOLEAN)
        add("auto_cap", Type.BOOLEAN)
        add("custom_im_reverselookup", Type.STRING)
        add("cj_im_reverselookup", Type.STRING)
        add("scj_im_reverselookup", Type.STRING)
        add("cj5_im_reverselookup", Type.STRING)
        add("ecj_im_reverselookup", Type.STRING)
        add("dayi_im_reverselookup", Type.STRING)
        add("bpmf_im_reverselookup", Type.STRING)
        add("phonetic_im_reverselookup", Type.STRING)
        add("ez_im_reverselookup", Type.STRING)
        add("array_im_reverselookup", Type.STRING)
        add("array10_im_reverselookup", Type.STRING)
        add("wb_im_reverselookup", Type.STRING)
        add("hs_im_reverselookup", Type.STRING)
        add("pinyin_im_reverselookup", Type.STRING)
        add("phonetic_keyboard_type", Type.STRING)
        add("auto_commit", Type.INTEGER_AS_STRING)
        add("accept_number_index", Type.BOOLEAN)
        add("accept_symbol_index", Type.BOOLEAN)
        add("hide_software_keyboard_typing_with_physical", Type.BOOLEAN)
        add("switch_english_mode", Type.BOOLEAN)
        add("switch_english_mode_shift", Type.BOOLEAN)
        add("disable_physical_selkey", Type.BOOLEAN)
        add("selkey_option", Type.INTEGER_AS_STRING)
        add("english_dictionary_physical_keyboard", Type.BOOLEAN)
        add("physical_keyboard_sort", Type.BOOLEAN)
    }

    private fun add(key: String, type: Type) {
        SPECS.put(key, Spec(key, type))
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun exportManifest(context: Context): JSONObject {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val storedValues = prefs.getAll()
        val values = JSONObject()

        for (spec in SPECS.values) {
            if (!storedValues.containsKey(spec.key)) continue
            putValue(values, spec, storedValues.get(spec.key))
        }
        for (entry in storedValues.entries) {
            val dynamicSpec = dynamicSpecForKey(entry.key)
            if (dynamicSpec != null) {
                putValue(values, dynamicSpec, entry.value)
            }
        }

        return JSONObject()
            .put("schema", SCHEMA_VERSION)
            .put("sourcePlatform", "android")
            .put("preferences", values)
    }

    @Throws(JSONException::class)
    @JvmStatic
    fun exportManifestBytes(context: Context): ByteArray? {
        return exportManifest(context).toString().toByteArray(StandardCharsets.UTF_8)
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun restoreManifest(context: Context, root: JSONObject?): Boolean {
        if (root == null || root.optInt("schema", -1) != SCHEMA_VERSION) return false

        val values = root.optJSONObject("preferences")
        if (values == null) return false

        val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
        for (spec in SPECS.values) {
            if (!values.has(spec.key)) continue
            restoreValue(editor, spec.key, spec.type, values.get(spec.key))
        }
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val dynamicSpec = dynamicSpecForKey(key)
            if (dynamicSpec != null) {
                restoreValue(editor, key, dynamicSpec.type, values.get(key))
            }
        }
        return editor.commit()
    }

    @JvmStatic
    @Throws(IOException::class, JSONException::class)
    fun restoreManifest(context: Context, input: InputStream?): Boolean {
        if (input == null) return false

        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var count: Int
        while ((input.read(buffer).also { count = it }) != -1) {
            if (output.size() + count > MAX_MANIFEST_BYTES) {
                return false
            }
            output.write(buffer, 0, count)
        }
        return restoreManifest(context, JSONObject(output.toString(StandardCharsets.UTF_8.name())))
    }

    @Throws(JSONException::class)
    private fun putIntegerAsString(values: JSONObject, key: String, value: Any?) {
        if (isIntegralNumber(value)) {
            values.put(key, (value as Number).toInt())
        } else if (value is String) {
            try {
                values.put(key, value.toInt())
            } catch (ignored: NumberFormatException) {
            }
        }
    }

    private fun isIntegralNumber(value: Any?): Boolean {
        return value is Int
                || value is Long
                || value is Short
                || value is Byte
    }

    private fun dynamicSpecForKey(key: String?): Spec? {
        if (key != null && (key.startsWith("backup_on_delete_") || key.startsWith("restore_on_import_"))) {
            return Spec(key, Type.BOOLEAN)
        }
        return null
    }

    @Throws(JSONException::class)
    private fun putValue(values: JSONObject, spec: Spec, value: Any?) {
        when (spec.type) {
            Type.BOOLEAN -> if (value is Boolean) {
                values.put(spec.key, value)
            }

            Type.INTEGER_AS_STRING -> putIntegerAsString(values, spec.key, value)
            Type.STRING -> if (value is String) {
                values.put(spec.key, value)
            }
        }
    }

    private fun restoreValue(
        editor: SharedPreferences.Editor,
        key: String?,
        type: Type,
        value: Any?
    ) {
        when (type) {
            Type.BOOLEAN -> if (value is Boolean) {
                editor.putBoolean(key, value)
            }

            Type.INTEGER_AS_STRING -> if (isIntegralNumber(value)) {
                editor.putString(key, (value as Number).toInt().toString())
            }

            Type.STRING -> if (value is String) {
                editor.putString(key, value)
            }
        }
    }

    private enum class Type {
        BOOLEAN,
        INTEGER_AS_STRING,
        STRING
    }

    private class Spec(val key: String, val type: Type)
}
