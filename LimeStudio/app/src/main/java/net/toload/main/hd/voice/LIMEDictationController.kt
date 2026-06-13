package net.toload.main.hd.voice

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.ArrayList

class LIMEDictationController(
    private val recognizer: SpeechRecognizerAdapter?,
    private val listener: DictationResultListener?
) : RecognitionListener {
    var state: DictationState = DictationState.IDLE
        private set

    var isActive: Boolean = false
        private set

    private var finalDelivered = false

    fun isRecognitionAvailable(): Boolean {
        return recognizer?.isRecognitionAvailable() == true
    }

    fun start(languageTag: String?) {
        if (!isRecognitionAvailable()) {
            emitError(SpeechRecognizer.ERROR_CLIENT, true)
            return
        }

        finalDelivered = false
        isActive = true
        recognizer?.setRecognitionListener(this)
        emitState(DictationState.LISTENING)
        recognizer?.startListening(createRecognizerIntent(languageTag))
    }

    fun stopAndCommit() {
        if (!isActive || recognizer == null) {
            return
        }
        recognizer.stopListening()
    }

    fun cancel() {
        recognizer?.cancel()
        isActive = false
        emitState(DictationState.CANCELLED)
        listener?.onDictationCancelled()
    }

    fun destroy() {
        recognizer?.destroy()
        isActive = false
        finalDelivered = false
        state = DictationState.IDLE
    }

    fun createRecognizerIntent(languageTag: String?): Intent {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        if (!languageTag.isNullOrEmpty()) {
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        }
        return intent
    }

    override fun onReadyForSpeech(params: Bundle?) {
        emitState(DictationState.LISTENING)
    }

    override fun onBeginningOfSpeech() {
        emitState(DictationState.LISTENING)
    }

    override fun onRmsChanged(rmsdB: Float) {
    }

    override fun onBufferReceived(buffer: ByteArray?) {
    }

    override fun onEndOfSpeech() {
        emitState(DictationState.FINALIZING)
    }

    override fun onError(error: Int) {
        isActive = false
        recognizer?.cancel()
        if (finalDelivered) {
            return
        }
        emitError(error, false)
    }

    override fun onResults(results: Bundle?) {
        val text = firstRecognitionText(results)
        isActive = false
        if (text.isNullOrEmpty() || finalDelivered) {
            return
        }
        finalDelivered = true
        emitState(DictationState.FINALIZING)
        recognizer?.stopListening()
        listener?.onDictationFinalText(text)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = firstRecognitionText(partialResults)
        if (text.isNullOrEmpty()) {
            return
        }
        emitState(DictationState.PARTIAL)
        listener?.onDictationPartialText(text)
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
    }

    private fun firstRecognitionText(results: Bundle?): String? {
        if (results == null) {
            return null
        }
        val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (matches == null || matches.isEmpty()) {
            return null
        }
        return matches[0]
    }

    private fun emitState(nextState: DictationState) {
        state = nextState
        listener?.onDictationStateChanged(nextState)
    }

    private fun emitError(errorCode: Int, shouldFallback: Boolean) {
        emitState(DictationState.ERROR)
        listener?.onDictationError(errorCode, shouldFallback)
    }
}
