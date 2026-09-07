package com.yourname.kaiko

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for interactive notification action buttons (v1.4.1):
 * 1. 🟢 I'M SAFE
 * 2. 🚨 EMERGENCY
 * 3. ⚠️ MISTOUCHED
 * 4. 🧪 TEST
 */
class ActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_SAFE = "com.yourname.kaiko.ACTION_MARK_SAFE"
        const val ACTION_EMERGENCY = "com.yourname.kaiko.ACTION_EMERGENCY"
        const val ACTION_MISTOUCHED = "com.yourname.kaiko.ACTION_MISTOUCHED"
        const val ACTION_TEST = "com.yourname.kaiko.ACTION_TEST"
        private const val TAG = "KAIKO_DEBUG"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MARK_SAFE -> {
                Log.d(TAG, "Notification action clicked: '🟢 I\'M SAFE'")
                TriggerManager.markUserSafe(context)
            }
            ACTION_EMERGENCY -> {
                Log.d(TAG, "Notification action clicked: '🚨 EMERGENCY'")
                TriggerManager.escalateImmediately(context)
            }
            ACTION_MISTOUCHED -> {
                Log.d(TAG, "Notification action clicked: '⚠️ MISTOUCHED'")
                TriggerManager.handleMistouched(context)
            }
            ACTION_TEST -> {
                Log.d(TAG, "Notification action clicked: '🧪 TEST'")
                TriggerManager.stopEscalationTestOnly(context)
            }
        }
    }
}
