package net.toload.main.hd.voice

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer

class AndroidSpeechRecognizerAdapter(context: Context?) : SpeechRecognizerAdapter {
    private val context: Context? = context?.applicationContext
    private var speechRecognizer: SpeechRecognizer? = null

    override fun setRecognitionListener(listener: RecognitionListener?) {
        val recognizer = getOrCreateRecognizer()
        recognizer?.setRecognitionListener(listener)
    }

    override fun startListening(intent: Intent?) {
        val recognizer = getOrCreateRecognizer()
        recognizer?.startListening(intent)
    }

    override fun stopListening() {
        speechRecognizer?.stopListening()
    }

    override fun cancel() {
        speechRecognizer?.cancel()
    }

    override fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    override fun isRecognitionAvailable(): Boolean {
        return context != null && SpeechRecognizer.isRecognitionAvailable(context)
    }

    private fun getOrCreateRecognizer(): SpeechRecognizer? {
        if (context == null || !isRecognitionAvailable()) {
            return null
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }
        return speechRecognizer
    }
}
