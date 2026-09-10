package com.yourname.kaiko

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * Official Android VoiceInteractionService for Kaiko.
 * 
 * Capabilities:
 * 1. Invoked when the user long-presses the Power Button (when Kaiko is selected as Default Assistant).
 * 2. Launches the Kaiko SOS confirmation UI with [ACCEPT & SEND SOS] and [CANCEL].
 * 3. Keeps voice recognition active when the phone is sleeping/screen is off where Android officially permits it.
 * 4. Connects directly to the existing central TriggerManager SOS flow upon confirmation or emergency voice phrase.
 */
class KaikoVoiceInteractionService : VoiceInteractionService() {

    companion object {
        private const val TAG = "KAIKO_VOICE_SVC"

        /**
         * Checks whether Kaiko is currently set as the Android device's Default Digital Assistant.
         */
        fun isDefaultAssistant(context: Context): Boolean {
            return try {
                val currentSetting = Settings.Secure.getString(
                    context.contentResolver,
                    "voice_interaction_service"
                )
                if (!currentSetting.isNullOrBlank()) {
                    val componentName = ComponentName.unflattenFromString(currentSetting)
                    componentName?.packageName == context.packageName
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check default assistant setting: ${e.message}")
                false
            }
        }
    }

    override fun onReady() {
        super.onReady()
        Log.d(TAG, "KaikoVoiceInteractionService is ready and bound by Android OS as Digital Assistant.")

        // Broadcast state change so MainActivity updates status live
        try {
            sendBroadcast(Intent(TriggerManager.ACTION_STATE_CHANGED))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to broadcast assistant ready: ${e.message}")
        }

        // Start voice trigger listening if enabled in preferences and microphone permission is granted
        if (TriggerManager.isVoiceTriggerEnabled(this) && VoiceTriggerManager.hasRecordAudioPermission(this)) {
            Log.d(TAG, "Voice Trigger is enabled; starting voice recognition in assistant service.")
            VoiceTriggerManager.startListening(this)
        }
    }

    override fun onShutdown() {
        super.onShutdown()
        Log.d(TAG, "KaikoVoiceInteractionService shutting down.")
        try {
            sendBroadcast(Intent(TriggerManager.ACTION_STATE_CHANGED))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to broadcast assistant shutdown: ${e.message}")
        }
    }
}
