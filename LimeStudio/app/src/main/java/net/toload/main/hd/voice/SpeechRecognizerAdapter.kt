package net.toload.main.hd.voice

import android.content.Intent
import android.speech.RecognitionListener

interface SpeechRecognizerAdapter {
    fun setRecognitionListener(listener: RecognitionListener?)
    fun startListening(intent: Intent?)
    fun stopListening()
    fun cancel()
    fun destroy()
    fun isRecognitionAvailable(): Boolean
}
