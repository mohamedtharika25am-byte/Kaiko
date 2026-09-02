package com.yourname.kaiko

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log

/**
 * BroadcastReceiver to track SMS delivery to the carrier network.
 * Handles single-retry on dispatch failure.
 */
class SmsSentReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SMS_SENT = "com.yourname.kaiko.ACTION_SMS_SENT"
        const val EXTRA_GUARDIAN_INDEX = "extra_guardian_index"
        const val EXTRA_IS_RETRY = "extra_is_retry"
        private const val TAG = "KAIKO_DEBUG"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SMS_SENT) return

        val guardianIndex = intent.getIntExtra(EXTRA_GUARDIAN_INDEX, 1)
        val isRetry = intent.getBooleanExtra(EXTRA_IS_RETRY, false)
        val resultCode = resultCode

        val statusDescription = when (resultCode) {
            Activity.RESULT_OK -> "SENT_TO_CARRIER (Dispatched to carrier network)"
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "FAILED (Generic carrier failure)"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "FAILED (No cellular service)"
            SmsManager.RESULT_ERROR_NULL_PDU -> "FAILED (Null PDU)"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "FAILED (Cellular radio turned off / Airplane mode)"
            else -> "FAILED (Result code: $resultCode)"
        }

        Log.d(TAG, "SMS Sent Status for Guardian $guardianIndex: $statusDescription (isRetry=$isRetry)")

        TriggerManager.onSmsSentResult(context, guardianIndex, resultCode == Activity.RESULT_OK, isRetry)
    }
}
