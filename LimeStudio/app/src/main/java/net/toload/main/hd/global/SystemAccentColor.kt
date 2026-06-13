package net.toload.main.hd.global

import android.content.Context
import android.graphics.Color
import android.provider.Settings
import android.text.TextUtils
import com.google.android.material.color.DynamicColorsOptions

/**
 * Resolves Android's user-selected Material You palette for LIME's follow-system theme.
 */
object SystemAccentColor {
    private const val THEME_CUSTOMIZATION = "theme_customization_overlay_packages"
    private const val SYSTEM_PALETTE = "system_palette"
    private const val ACCENT_COLOR = "accent_color"

    @JvmStatic
    fun dynamicColorOptions(context: Context?): DynamicColorsOptions {
        val builder = DynamicColorsOptions.Builder()
        val seedColor = resolveSeedColor(context, 0)
        if (isUsableColor(seedColor)) {
            builder.setContentBasedSource(seedColor)
        }
        return builder.build()
    }

    @JvmStatic
    fun resolveSeedColor(context: Context?, fallbackColor: Int): Int {
        if (context == null) return fallbackColor
        return try {
            val value = Settings.Secure.getString(
                context.contentResolver,
                THEME_CUSTOMIZATION
            )
            val parsed = parseThemeCustomizationColor(value)
            if (isUsableColor(parsed)) parsed else fallbackColor
        } catch (e: RuntimeException) {
            fallbackColor
        }
    }

    @JvmStatic
    fun parseThemeCustomizationColor(value: String?): Int {
        if (TextUtils.isEmpty(value)) return 0

        val parsed = parseColorAfterKey(value!!, SYSTEM_PALETTE)
        if (isUsableColor(parsed)) return parsed

        return parseColorAfterKey(value, ACCENT_COLOR)
    }

    private fun parseColorAfterKey(value: String, key: String): Int {
        val keyIndex = value.indexOf(key)
        if (keyIndex < 0) return 0

        var index = keyIndex + key.length
        val length = value.length
        while (index < length) {
            val c = value[index]
            if (c == ':' || c == '=' || c == '"' || c == '\'' || Character.isWhitespace(c)) {
                index++
                continue
            }
            break
        }

        val start = index
        while (index < length) {
            val c = value[index]
            if (c == '#' || isHexDigit(c)) {
                index++
                continue
            }
            break
        }
        if (index <= start) return 0
        return parseColorToken(value.substring(start, index))
    }

    private fun parseColorToken(token: String?): Int {
        if (TextUtils.isEmpty(token)) return 0

        var normalized = token!!.trim()
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1)
        }
        if (normalized.length == 6) {
            normalized = "FF$normalized"
        }
        if (normalized.length != 8) return 0

        return try {
            normalized.toLong(16).toInt()
        } catch (e: NumberFormatException) {
            0
        }
    }

    private fun isHexDigit(c: Char): Boolean {
        return c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
    }

    private fun isUsableColor(color: Int): Boolean {
        return Color.alpha(color) != 0
    }
}
