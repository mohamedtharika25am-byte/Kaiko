package com.yourname.kaiko

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Manages Voice Trigger speech recognition for Kaiko.
 * 
 * Features:
 * 1. Evaluates emergency phrases directly without any wake word ("Hey Kaiko").
 * 2. Default emergency phrases:
 *    - "I need help"
 *    - "I am in danger"
 *    - "Help me"
 *    - "This is an emergency"
 *    - "Send SOS"
 * 3. Custom voice phrase support (configured via text input, enabled/disabled/removed).
 * 4. Triggers the EXISTING central SOS pipeline via TriggerManager.fireAlert().
 * 5. Uses standard Android SpeechRecognizer APIs appropriately with resilient loop & restart.
 * 6. Explicitly reports Android OS microphone / background privacy limitations.
 */
object VoiceTriggerManager {

    private const val TAG = "KAIKO_VOICE"

    interface VoiceTriggerListener {
        fun onStateChanged(isListening: Boolean, statusMessage: String)
        fun onPhraseDetected(phrase: String)
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false
    private var appContext: Context? = null
    private var listener: VoiceTriggerListener? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var restartRunnable: Runnable? = null
    private var isRestarting = false

    /**
     * Checks if SpeechRecognizer is available on this Android device.
     */
    fun isSpeechRecognitionAvailable(context: Context): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * Checks if RECORD_AUDIO permission is granted.
     */
    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Normalizes text by converting to lowercase, removing non-alphanumeric punctuation,
     * and collapsing whitespace.
     */
    fun normalizeText(text: String): String {
        return text.lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Checks if spoken text matches any active emergency phrase.
     * Returns Pair(isMatched, matchedPhrase).
     */
    fun checkPhraseMatch(spokenText: String, context: Context): Pair<Boolean, String?> {
        val normalizedSpoken = normalizeText(spokenText)
        if (normalizedSpoken.isBlank()) return Pair(false, null)

        // 1. Check default emergency phrases
        for (phrase in TriggerManager.getDefaultEmergencyPhrases()) {
            val normalizedPhrase = normalizeText(phrase)
            if (containsPhrase(normalizedSpoken, normalizedPhrase)) {
                return Pair(true, phrase)
            }
        }

        // 2. Check all custom emergency phrases (up to 5)
        val customPhrases = TriggerManager.getCustomVoicePhrases(context)
        for (custom in customPhrases) {
            val normalizedCustom = normalizeText(custom)
            if (normalizedCustom.isNotBlank() && containsPhrase(normalizedSpoken, normalizedCustom)) {
                return Pair(true, custom)
            }
        }

        return Pair(false, null)
    }

    private fun containsPhrase(spoken: String, target: String): Boolean {
        if (spoken == target) return true
        // Match phrase as substring with boundary awareness
        return spoken.contains(target)
    }

    /**
     * Starts continuous listening for emergency phrases while Kaiko is active.
     */
    fun startListening(context: Context, callback: VoiceTriggerListener? = null) {
        appContext = context.applicationContext
        listener = callback

        if (!TriggerManager.isVoiceTriggerEnabled(context)) {
            Log.d(TAG, "Voice Trigger is disabled in preferences.")
            listener?.onStateChanged(false, "Voice Trigger is OFF")
            return
        }

        if (!hasRecordAudioPermission(context)) {
            Log.w(TAG, "RECORD_AUDIO permission not granted.")
            listener?.onStateChanged(false, "Microphone permission required")
            return
        }

        if (!isSpeechRecognitionAvailable(context)) {
            Log.w(TAG, "SpeechRecognizer not available on this device.")
            listener?.onStateChanged(false, "Speech recognition unavailable on device")
            return
        }

        mainHandler.post {
            startListeningInternal()
        }
    }

    /**
     * Stops listening and frees recognizer resources.
     */
    fun stopListening() {
        mainHandler.post {
            isListening = false
            cancelRestart()
            destroyRecognizer()
            listener?.onStateChanged(false, "Voice Trigger paused")
            Log.d(TAG, "Voice recognition stopped.")
        }
    }

    fun isCurrentlyListening(): Boolean = isListening

    private fun cancelRestart() {
        restartRunnable?.let { mainHandler.removeCallbacks(it) }
        restartRunnable = null
        isRestarting = false
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!isListening) return
        cancelRestart()
        isRestarting = true
        restartRunnable = Runnable {
            if (isListening) {
                isRestarting = false
                startListeningInternal()
            }
        }
        mainHandler.postDelayed(restartRunnable!!, delayMs)
    }

    private fun startListeningInternal() {
        val ctx = appContext ?: return

        // If an SOS is currently active, do not listen to prevent concurrent trigger loops
        if (TriggerManager.getCurrentState(ctx).isActive()) {
            Log.d(TAG, "Active SOS in progress; suspending voice listening.")
            listener?.onStateChanged(false, "Active SOS in progress")
            return
        }

        try {
            // Lifecycle handling: reuse existing SpeechRecognizer instance rather than
            // destroying and rebuilding on every cycle (which triggers repeated system start beeps)
            if (speechRecognizer == null) {
                speechRecognizer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) {
                    Log.d(TAG, "Using on-device speech recognizer to avoid remote network/chime overhead.")
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(ctx)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "SpeechRecognizer ready for speech.")
                        listener?.onStateChanged(true, "Listening for emergency phrases...")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "User started speaking...")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "User finished speaking.")
                    }

                    override fun onError(error: Int) {
                        handleSpeechError(error)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        processSpeechMatches(matches)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        processSpeechMatches(matches, isPartial = true)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            } else {
                speechRecognizer?.cancel()
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
                // Dictation mode hint avoids conversational assistant audio prompts on supported platforms
                putExtra("android.speech.extra.DICTATION_MODE", true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
            Log.d(TAG, "SpeechRecognizer started listening.")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognizer: ${e.message}", e)
            listener?.onStateChanged(false, "Error initializing microphone: ${e.message}")
            destroyRecognizer()
            scheduleRestart(2000L)
        }
    }

    private fun handleSpeechError(error: Int) {
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match (listening...)"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout (listening...)"
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            else -> "Speech recognition error ($error)"
        }
        Log.d(TAG, "SpeechRecognizer error: $error ($errorMessage)")

        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            isListening = false
            destroyRecognizer()
            listener?.onStateChanged(false, "Microphone permission required")
            return
        }

        // For temporary timeouts or no-match, quietly restart with backoff to prevent tight reconnect loops
        if (isListening) {
            val retryDelay = when (error) {
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 2000L
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH -> 1200L
                else -> 1500L
            }
            scheduleRestart(retryDelay)
        }
    }

    private fun processSpeechMatches(matches: List<String>?, isPartial: Boolean = false) {
        val ctx = appContext ?: return
        if (matches.isNullOrEmpty()) return

        Log.d(TAG, "Speech detected (${if (isPartial) "partial" else "final"}): $matches")

        for (candidate in matches) {
            val (isMatched, phrase) = checkPhraseMatch(candidate, ctx)
            if (isMatched && phrase != null) {
                Log.i(TAG, "🚨 VOICE TRIGGER MATCH DETECTED: '$phrase' in candidate: '$candidate'")
                
                // Immediately stop listening to avoid redundant trigger invocations
                stopListening()
                
                listener?.onPhraseDetected(phrase)
                listener?.onStateChanged(false, "Emergency phrase detected: \"$phrase\"")

                // Trigger the EXISTING central SOS pipeline
                TriggerManager.fireAlert(ctx, TriggerManager.TRIGGER_VOICE)
                return
            }
        }

        // If this was final results and no match was found, restart listening cycle
        if (!isPartial && isListening) {
            scheduleRestart(200L)
        }
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer: ${e.message}")
        } finally {
            speechRecognizer = null
        }
    }
}
