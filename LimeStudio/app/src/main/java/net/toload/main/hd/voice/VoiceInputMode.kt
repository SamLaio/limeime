package net.toload.main.hd.voice


enum class VoiceInputMode(
    @JvmField val preferenceValue: String
) {
    AUTO("auto"),
    LIME_INLINE("lime_inline"),
    VOICE_IME("voice_ime"),
    RECOGNIZER_INTENT("recognizer_intent");

    companion object {
        @JvmStatic
        fun fromPreferenceValue(value: String?): VoiceInputMode {
            if (value == null) {
                return AUTO
            }
            for (mode in entries) {
                if (mode.preferenceValue == value) {
                    return mode
                }
            }
            return AUTO
        }
    }
}
