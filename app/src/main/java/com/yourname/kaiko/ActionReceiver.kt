package com.yourname.kaiko

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for interactive notification action buttons:
 * 1. "I'm Safe Now" action to cancel escalation
 * 2. "Simulate Guardian Ack [TEST ONLY]" action to test state resolution
 */
class ActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_SAFE = "com.yourname.kaiko.ACTION_MARK_SAFE"
        const val ACTION_SIMULATE_ACK = "com.yourname.kaiko.ACTION_SIMULATE_ACK"
        private const val TAG = "KAIKO_DEBUG"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MARK_SAFE -> {
                Log.d(TAG, "Notification action clicked: 'I\'m Safe Now'")
                TriggerManager.markUserSafe(context)
            }
            ACTION_SIMULATE_ACK -> {
                Log.d(TAG, "Notification action clicked: 'Simulate Guardian Ack [TEST]'")
                TriggerManager.simulateGuardianAck(context)
            }
        }
    }
}
