package net.toload.main.hd

import org.junit.Assert.assertEquals
import net.toload.main.hd.voice.LIMEVoiceInputRouter
import net.toload.main.hd.voice.VoiceInputMode
import net.toload.main.hd.voice.VoiceInputRoute
import net.toload.main.hd.voice.VoicePermissionState
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
open class LIMEVoiceInputRouterTest {
    @Test
    fun autoGrantedUsesInlineDictation() {
        assertEquals(VoiceInputRoute.INLINE_DICTATION, LIMEVoiceInputRouter.chooseRoute(true, VoiceInputMode.AUTO, VoicePermissionState.GRANTED, true, true, true))
    }
    @Test
    fun deniedPermissionFallsBackToVoiceImeFirst() {
        assertEquals(VoiceInputRoute.VOICE_IME, LIMEVoiceInputRouter.chooseRoute(true, VoiceInputMode.AUTO, VoicePermissionState.DENIED_CAN_ASK, true, true, true))
    }
    @Test
    fun missingPermissionFallsBackToVoiceImeFirst() {
        assertEquals(VoiceInputRoute.VOICE_IME, LIMEVoiceInputRouter.chooseRoute(true, VoiceInputMode.AUTO, VoicePermissionState.NOT_REQUESTED, true, true, true))
    }
    @Test
    fun missingPermissionFallsBackToRecognizerWhenVoiceImeMissing() {
        assertEquals(VoiceInputRoute.RECOGNIZER_INTENT, LIMEVoiceInputRouter.chooseRoute(true, VoiceInputMode.AUTO, VoicePermissionState.NOT_REQUESTED, true, false, true))
    }
    @Test
    fun deniedPermissionFallsBackToRecognizerWhenVoiceImeMissing() {
        assertEquals(VoiceInputRoute.RECOGNIZER_INTENT, LIMEVoiceInputRouter.chooseRoute(true, VoiceInputMode.AUTO, VoicePermissionState.DENIED_DO_NOT_ASK_AGAIN, true, false, true))
    }
    @Test
    fun voiceImeModeIgnoresInlineEvenWhenPermissionGranted() {
        assertEquals(VoiceInputRoute.VOICE_IME, LIMEVoiceInputRouter.chooseRoute(true, VoiceInputMode.VOICE_IME, VoicePermissionState.GRANTED, true, true, true))
    }
    @Test
    fun recognizerIntentModeSkipsInlineAndVoiceIme() {
        assertEquals(VoiceInputRoute.RECOGNIZER_INTENT, LIMEVoiceInputRouter.chooseRoute(true, VoiceInputMode.RECOGNIZER_INTENT, VoicePermissionState.GRANTED, true, true, true))
    }
    @Test
    fun noAvailableRoutesReturnsUnavailable() {
        assertEquals(VoiceInputRoute.UNAVAILABLE, LIMEVoiceInputRouter.chooseRoute(true, VoiceInputMode.AUTO, VoicePermissionState.DENIED_CAN_ASK, false, false, false))
    }
    @Test
    fun modePreferenceValueFallsBackToAuto() {
        assertEquals(VoiceInputMode.AUTO, VoiceInputMode.fromPreferenceValue(null))
        assertEquals(VoiceInputMode.AUTO, VoiceInputMode.fromPreferenceValue("unknown"))
        assertEquals(VoiceInputMode.VOICE_IME, VoiceInputMode.fromPreferenceValue("voice_ime"))
    }
}
