package com.yourname.kaiko

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * BroadcastReceiver for incoming Guardian SMS Acknowledgements (Kaiko v1.8.4).
 * 
 * Listens for system Telephony.Sms.Intents.SMS_RECEIVED_ACTION broadcasts.
 * Extracts sender and message body, and delegates validation to TriggerManager.handleIncomingSms().
 */
class GuardianAckReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "KAIKO_ACK_RCV"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) {
                Log.d(TAG, "SMS_RECEIVED intent contained no messages.")
                return
            }

            // Group parts by originating address in case of multi-part SMS
            val messagesBySender = messages.groupBy { it.displayOriginatingAddress ?: it.originatingAddress ?: "" }
            for ((sender, parts) in messagesBySender) {
                if (sender.isBlank()) continue
                val fullBody = parts.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }
                Log.d(TAG, "Incoming SMS received from $sender: \"$fullBody\"")
                TriggerManager.handleIncomingSms(context, sender, fullBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing incoming SMS broadcast: ${e.message}", e)
        }
    }
}
