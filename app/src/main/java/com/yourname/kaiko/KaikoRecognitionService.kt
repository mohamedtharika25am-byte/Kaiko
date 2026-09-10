package com.yourname.kaiko

import android.content.Intent
import android.speech.RecognitionService
import android.util.Log

/**
 * Lightweight RecognitionService implementation for Kaiko.
 * Fulfills Android's VoiceInteractionService recognition service requirement.
 */
class KaikoRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        Log.d("KAIKO_RECOGNITION", "Recognition service start listening invoked.")
    }

    override fun onCancel(listener: Callback?) {
        Log.d("KAIKO_RECOGNITION", "Recognition service cancel invoked.")
    }

    override fun onStopListening(listener: Callback?) {
        Log.d("KAIKO_RECOGNITION", "Recognition service stop listening invoked.")
    }
}
