package net.toload.main.hd

import org.junit.Assert.assertTrue
import net.toload.main.hd.voice.AndroidSpeechRecognizerAdapter
import net.toload.main.hd.voice.DictationResultListener
import net.toload.main.hd.voice.DictationState
import net.toload.main.hd.voice.SpeechRecognizerAdapter
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
open class SpeechRecognizerAdapterContractTest {
    @Test
    fun adapterAndDictationContractsExist() {
        assertTrue(SpeechRecognizerAdapter::class.java.isInterface())
        assertTrue(DictationResultListener::class.java.isInterface())
        assertTrue(AndroidSpeechRecognizerAdapter::class.java.name.contains("AndroidSpeechRecognizerAdapter"))
        assertTrue((DictationState.IDLE.ordinal >= 0))
    }
}
