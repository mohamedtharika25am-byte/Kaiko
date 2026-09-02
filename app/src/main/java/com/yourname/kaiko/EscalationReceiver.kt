package com.yourname.kaiko

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver triggered by AlarmManager when an escalation timer expires.
 * Advances the emergency SOS escalation chain to the next configured guardian.
 */
class EscalationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ESCALATE_TIMER = "com.yourname.kaiko.ACTION_ESCALATE_TIMER"
        private const val TAG = "KAIKO_DEBUG"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_ESCALATE_TIMER) {
            Log.d(TAG, "Escalation timer broadcast received. Advancing escalation chain...")
            TriggerManager.handleEscalationTimeout(context)
        }
    }
}
