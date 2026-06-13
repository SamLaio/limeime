/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
 *  **
 *  **    Project Url: https://github.com/SamLaio/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

package net.toload.main.hd

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.util.Log
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.ConfigurationCompat
import java.util.ArrayList
import java.util.Locale

/**
 * Helper Activity to launch RecognizerIntent and return results.
 */
class VoiceInputActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val flagFullscreen = WindowManager.LayoutParams.FLAG_FULLSCREEN
        window.setFlags(flagFullscreen, flagFullscreen)
        window.setBackgroundDrawableResource(android.R.color.transparent)

        Log.i(TAG, "onCreate(): Starting voice input on API level " + Build.VERSION.SDK_INT)

        val voiceInputLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            ::handleVoiceInputResult
        )

        var voiceIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_VOICE_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_VOICE_INTENT)
        }

        if (voiceIntent == null) {
            Log.w(TAG, "onCreate(): voiceIntent is NULL, using fallback with system locale")
            voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            voiceIntent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            val systemLocale = ConfigurationCompat.getLocales(resources.configuration)[0]
            val languageTag = LIMEService.resolveVoiceRecognitionLanguageTag(systemLocale)
            voiceIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            voiceIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        } else {
            val language = voiceIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)
            Log.i(TAG, "onCreate(): Received voiceIntent from LIMEService with language: $language")
        }

        try {
            voiceInputLauncher.launch(voiceIntent)
            Log.i(TAG, "onCreate(): Launched RecognizerIntent")
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "onCreate(): ActivityNotFoundException launching RecognizerIntent: " + e.message, e)
            Toast.makeText(
                this,
                getString(R.string.voice_recognition_activity_not_found),
                Toast.LENGTH_SHORT
            ).show()
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "onCreate(): Failed to launch RecognizerIntent: " + e.message, e)
            Toast.makeText(
                this,
                getString(R.string.voice_recognition_start_failed, e.message),
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    private fun handleVoiceInputResult(result: ActivityResult) {
        var recognizedText: String? = null
        if (result.resultCode == RESULT_OK && result.data != null) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                recognizedText = results[0]
                Log.i(TAG, "handleVoiceInputResult(): Recognized text: '$recognizedText'")
            } else {
                Log.w(TAG, "handleVoiceInputResult(): No results in data")
            }
        } else {
            Log.w(
                TAG,
                "handleVoiceInputResult(): Voice recognition cancelled or failed, resultCode: " +
                    result.resultCode
            )
        }

        if (recognizedText != null) {
            sPendingVoiceText = recognizedText
            Log.i(TAG, "handleVoiceInputResult(): Stored pending voice text in static field")
        }

        finish()

        if (recognizedText != null) {
            val textToCommit = recognizedText
            Handler(Looper.getMainLooper()).postDelayed(
                { sendVoiceResultBroadcast(textToCommit) },
                300
            )
        }
    }

    private fun sendVoiceResultBroadcast(recognizedText: String) {
        try {
            val resultIntent = Intent(ACTION_VOICE_RESULT)
            resultIntent.setPackage(packageName)
            resultIntent.putExtra(EXTRA_RECOGNIZED_TEXT, recognizedText)

            applicationContext.sendBroadcast(resultIntent)
            Log.i(TAG, "sendVoiceResultBroadcast(): Sent broadcast via ApplicationContext")
        } catch (e: Exception) {
            Log.w(
                TAG,
                "sendVoiceResultBroadcast(): Broadcast failed (static field is primary): " + e.message
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy()")
    }

    companion object {
        private const val TAG = "VoiceInputActivity"
        const val ACTION_VOICE_RESULT = "net.toload.main.hd.VOICE_INPUT_RESULT"
        const val EXTRA_RECOGNIZED_TEXT = "recognized_text"
        const val EXTRA_VOICE_INTENT = "voice_intent"

        @Volatile
        private var sPendingVoiceText: String? = null

        @JvmStatic
        fun consumePendingVoiceText(): String? {
            val text = sPendingVoiceText
            sPendingVoiceText = null
            return text
        }
    }
}
