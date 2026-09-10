package com.yourname.kaiko

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button

/**
 * Voice Interaction Session invoked upon Power Button long-press or assistant gesture.
 * 
 * Requirements:
 * - Show Kaiko SOS confirmation UI
 * - [🚨 ACCEPT & SEND SOS]
 * - [CANCEL]
 * - Only when ACCEPT is pressed:
 *   -> use the existing TriggerManager
 *   -> start the existing SOS flow.
 * - Do NOT make Power Button Long Press automatically send SOS.
 * - Do NOT add chatbot, conversational assistant, or Assistant Session UI.
 */
class KaikoVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    companion object {
        private const val TAG = "KAIKO_VOICE_SESSION"
    }

    private var rootView: View? = null

    override fun onCreateContentView(): View {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.layout_power_button_sos, null)
        rootView = view

        val btnAccept = view.findViewById<Button>(R.id.btnAcceptSos)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelSos)

        btnAccept.setOnClickListener {
            Log.i(TAG, "🚨 Power Button Long Press: ACCEPT pressed by user! Triggering existing SOS flow.")
            
            // Only when ACCEPT is pressed: use the existing TriggerManager to start existing SOS flow
            TriggerManager.fireAlert(context, TriggerManager.TRIGGER_POWER_BUTTON)

            // Bring MainActivity to front to display active SOS controls
            try {
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                context.startActivity(mainIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch MainActivity: ${e.message}")
            }

            // Dismiss the assistant window
            finish()
        }

        btnCancel.setOnClickListener {
            Log.d(TAG, "Power Button Long Press: CANCEL pressed by user. No SOS sent.")
            finish()
        }

        return view
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.d(TAG, "KaikoVoiceInteractionSession shown. Displaying SOS confirmation UI.")
    }
}
