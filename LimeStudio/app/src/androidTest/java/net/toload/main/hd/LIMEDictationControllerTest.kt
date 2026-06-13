package net.toload.main.hd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.toload.main.hd.voice.DictationResultListener
import net.toload.main.hd.voice.DictationState
import net.toload.main.hd.voice.LIMEDictationController
import net.toload.main.hd.voice.SpeechRecognizerAdapter
import org.junit.Test
import org.junit.runner.RunWith
import java.util.ArrayList

@RunWith(AndroidJUnit4::class)
open class LIMEDictationControllerTest {
    @Test
    fun startCreatesRecognizerIntentAndMovesToListening() {
        var adapter: FakeRecognizerAdapter = FakeRecognizerAdapter()
        var listener: RecordingListener = RecordingListener()
        var controller: LIMEDictationController = LIMEDictationController(adapter, listener)
        controller.start("zh-TW")
        assertTrue(controller.isActive)
        assertEquals(DictationState.LISTENING, controller.getState())
        assertNotNull(adapter.listener)
        assertNotNull(adapter.lastIntent)
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, adapter.lastIntent.getAction())
        assertEquals("zh-TW", adapter.lastIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertTrue(adapter.lastIntent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))
        assertEquals(DictationState.LISTENING, listener.states.get(0))
    }
    @Test
    fun partialResultPublishesPartialText() {
        var adapter: FakeRecognizerAdapter = FakeRecognizerAdapter()
        var listener: RecordingListener = RecordingListener()
        var controller: LIMEDictationController = LIMEDictationController(adapter, listener)
        controller.start("zh-TW")
        adapter.listener.onPartialResults(bundleWithText("這是測試"))
        assertEquals(DictationState.PARTIAL, controller.getState())
        assertEquals("這是測試", listener.partials.get(0))
    }
    @Test
    fun finalResultPublishesFinalTextOnceAndStopsActiveSession() {
        var adapter: FakeRecognizerAdapter = FakeRecognizerAdapter()
        var listener: RecordingListener = RecordingListener()
        var controller: LIMEDictationController = LIMEDictationController(adapter, listener)
        controller.start("zh-TW")
        adapter.listener.onResults(bundleWithText("繁體中文"))
        adapter.listener.onResults(bundleWithText("第二次"))
        assertTrue(adapter.stopped)
        assertFalse(controller.isActive)
        assertEquals(DictationState.FINALIZING, controller.getState())
        assertEquals(1, listener.finals.size)
        assertEquals("繁體中文", listener.finals.get(0))
    }
    @Test
    fun cancelCancelsAdapterAndPublishesCancelledState() {
        var adapter: FakeRecognizerAdapter = FakeRecognizerAdapter()
        var listener: RecordingListener = RecordingListener()
        var controller: LIMEDictationController = LIMEDictationController(adapter, listener)
        controller.start("zh-TW")
        controller.cancel()
        assertTrue(adapter.cancelled)
        assertFalse(controller.isActive)
        assertEquals(DictationState.CANCELLED, controller.getState())
        assertTrue(listener.cancelled)
    }
    @Test
    fun recognizerErrorDoesNotRequestFallback() {
        var adapter: FakeRecognizerAdapter = FakeRecognizerAdapter()
        var listener: RecordingListener = RecordingListener()
        var controller: LIMEDictationController = LIMEDictationController(adapter, listener)
        controller.start("zh-TW")
        adapter.listener.onError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        assertTrue(adapter.cancelled)
        assertFalse(controller.isActive)
        assertEquals(DictationState.ERROR, controller.getState())
        assertEquals(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, listener.errorCode)
        assertFalse(listener.shouldFallback)
    }
    @Test
    fun lateRecognizerErrorAfterFinalResultIsIgnored() {
        var adapter: FakeRecognizerAdapter = FakeRecognizerAdapter()
        var listener: RecordingListener = RecordingListener()
        var controller: LIMEDictationController = LIMEDictationController(adapter, listener)
        controller.start("zh-TW")
        adapter.listener.onResults(bundleWithText("繁體中文"))
        adapter.listener.onError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        assertEquals(1, listener.finals.size)
        assertEquals("繁體中文", listener.finals.get(0))
        assertEquals(0, listener.errorCode)
        assertFalse(listener.shouldFallback)
    }
    @Test
    fun unavailableRecognizerErrorsWithoutStartingAdapter() {
        var adapter: FakeRecognizerAdapter = FakeRecognizerAdapter()
        adapter.available = false
        var listener: RecordingListener = RecordingListener()
        var controller: LIMEDictationController = LIMEDictationController(adapter, listener)
        controller.start("zh-TW")
        assertFalse(controller.isActive)
        assertEquals(DictationState.ERROR, controller.getState())
        assertEquals(SpeechRecognizer.ERROR_CLIENT, listener.errorCode)
        assertTrue(listener.shouldFallback)
        assertFalse(adapter.started)
    }
    private fun bundleWithText(text: String): Bundle {
        var bundle: Bundle = Bundle()
        var matches: ArrayList<String> = ArrayList()
        matches.add(text)
        bundle.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, matches)
        return bundle
    }
    private open class FakeRecognizerAdapter : SpeechRecognizerAdapter {
        lateinit var listener: RecognitionListener
        lateinit var lastIntent: Intent
        var available: Boolean = true
        var started: Boolean = false
        var stopped: Boolean = false
        var cancelled: Boolean = false
        override fun setRecognitionListener(listener: RecognitionListener?) {
            this.listener = listener!!
        }
        override fun startListening(intent: Intent?) {
            this.lastIntent = intent!!
            this.started = true
        }
        override fun stopListening() {
            this.stopped = true
        }
        override fun cancel() {
            this.cancelled = true
        }
        override fun destroy() {

        }
        override fun isRecognitionAvailable(): Boolean {
            return available
        }
    }
    private open class RecordingListener : DictationResultListener {
        val states: MutableList<DictationState?> = ArrayList()
        val partials: MutableList<String?> = ArrayList()
        val finals: MutableList<String?> = ArrayList()
        var errorCode: Int = 0
        var shouldFallback: Boolean = false
        var cancelled: Boolean = false
        override fun onDictationStateChanged(state: DictationState?) {
            states.add(state)
        }
        override fun onDictationPartialText(text: String?) {
            partials.add(text)
        }
        override fun onDictationFinalText(text: String?) {
            finals.add(text)
        }
        override fun onDictationError(errorCode: Int, shouldFallback: Boolean) {
            this.errorCode = errorCode
            this.shouldFallback = shouldFallback
        }
        override fun onDictationCancelled() {
            this.cancelled = true
        }
    }
}
