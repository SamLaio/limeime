package net.toload.main.hd.voice


object LIMEVoiceInputRouter {
    @JvmStatic
    fun chooseRoute(
        inlineFeatureEnabled: Boolean,
        selectedMode: VoiceInputMode?,
        permissionState: VoicePermissionState?,
        inlineRecognizerAvailable: Boolean,
        voiceImeAvailable: Boolean,
        recognizerFallbackAvailable: Boolean
    ): VoiceInputRoute {
        val mode = selectedMode ?: VoiceInputMode.AUTO
        val permission = permissionState ?: VoicePermissionState.NOT_REQUESTED

        return when (mode) {
            VoiceInputMode.LIME_INLINE -> {
                if (canUseInline(inlineFeatureEnabled, permission, inlineRecognizerAvailable)) {
                    VoiceInputRoute.INLINE_DICTATION
                } else {
                    chooseDelegatedRoute(voiceImeAvailable, recognizerFallbackAvailable)
                }
            }
            VoiceInputMode.VOICE_IME -> {
                if (voiceImeAvailable) {
                    VoiceInputRoute.VOICE_IME
                } else if (recognizerFallbackAvailable) {
                    VoiceInputRoute.RECOGNIZER_INTENT
                } else {
                    VoiceInputRoute.UNAVAILABLE
                }
            }
            VoiceInputMode.RECOGNIZER_INTENT -> {
                if (recognizerFallbackAvailable) {
                    VoiceInputRoute.RECOGNIZER_INTENT
                } else {
                    VoiceInputRoute.UNAVAILABLE
                }
            }
            VoiceInputMode.AUTO -> {
                if (canUseInline(inlineFeatureEnabled, permission, inlineRecognizerAvailable)) {
                    VoiceInputRoute.INLINE_DICTATION
                } else {
                    chooseDelegatedRoute(voiceImeAvailable, recognizerFallbackAvailable)
                }
            }
        }
    }

    private fun canUseInline(
        inlineFeatureEnabled: Boolean,
        permissionState: VoicePermissionState,
        inlineRecognizerAvailable: Boolean
    ): Boolean {
        return inlineFeatureEnabled &&
            permissionState == VoicePermissionState.GRANTED &&
            inlineRecognizerAvailable
    }

    private fun chooseDelegatedRoute(
        voiceImeAvailable: Boolean,
        recognizerFallbackAvailable: Boolean
    ): VoiceInputRoute {
        if (voiceImeAvailable) {
            return VoiceInputRoute.VOICE_IME
        }
        if (recognizerFallbackAvailable) {
            return VoiceInputRoute.RECOGNIZER_INTENT
        }
        return VoiceInputRoute.UNAVAILABLE
    }
}
