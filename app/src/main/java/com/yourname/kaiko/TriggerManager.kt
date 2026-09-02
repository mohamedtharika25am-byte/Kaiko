package com.yourname.kaiko

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Central Orchestrator & State Machine for Kaiko Emergency Operations (v0.0.2).
 * Handles:
 * 1. High-accuracy location retrieval via FusedLocationProviderClient (with 3s timeout).
 * 2. Multi-Guardian configuration & escalation chain (G1 -> G2 -> G3 -> Final Escalation).
 * 3. SMS dispatch with carrier confirmation and single-retry on failure.
 * 4. "I'm Safe Now" cancel action to safely halt pending escalation.
 * 5. Simulated Guardian Acknowledgement for local prototype testing.
 * 6. Ongoing Lock-Screen Notification with interactive controls.
 * 7. Safe DEBUG_MODE testing switch.
 */
object TriggerManager {

    // =========================================================================
    // 6. DEBUG MODE TOGGLE
    // =========================================================================
    // Set to true for safe testing (no real SMS sent).
    // Set to false to send actual SMS alerts.
    const val DEBUG_MODE = false

    // =========================================================================
    // CONSTANTS & KEYS
    // =========================================================================
    const val TAG = "KAIKO_DEBUG"
    const val PREFS_NAME = "kaiko_preferences"

    // Guardian Contact Keys (Preserving existing KEY_GUARDIAN_PHONE for backward compatibility)
    const val KEY_GUARDIAN_PHONE = "guardian_phone_number"
    const val KEY_GUARDIAN_2_PHONE = "guardian_2_phone_number"
    const val KEY_GUARDIAN_3_PHONE = "guardian_3_phone_number"
    const val KEY_FINAL_HELPLINE_PHONE = "final_helpline_phone_number"

    // Configuration Keys
    const val KEY_ESCALATION_DELAY_SECONDS = "escalation_delay_seconds"
    const val DEFAULT_ESCALATION_DELAY_SECONDS = 60L // Testing default

    // State Persistence Keys
    const val KEY_CURRENT_SOS_STATE = "current_sos_state"
    const val KEY_ACTIVE_EVENT_ID = "active_event_id"
    const val KEY_ACTIVE_GUARDIAN_INDEX = "active_guardian_index"
    const val KEY_LAST_COORDS = "last_known_coords"

    // Broadcast Actions
    const val ACTION_STATE_CHANGED = "com.yourname.kaiko.ACTION_STATE_CHANGED"

    private const val NOTIFICATION_CHANNEL_ID = "kaiko_emergency_alerts"
    private const val NOTIFICATION_ID = 1001
    private const val LOCATION_TIMEOUT_MS = 3000L

    // PendingIntent Request Codes
    private const val REQ_ESCALATION_ALARM = 2001
    private const val REQ_SMS_SENT_BASE = 3000
    private const val REQ_ACTION_SAFE = 4001
    private const val REQ_ACTION_SIMULATE_ACK = 4002

    /**
     * Shared trigger execution method called by hardware button (KaikoAccessibilityService),
     * direct SOS widget (KaikoWidgetProvider), disguised widget (KaikoDisguisedWidgetProvider),
     * or Quick Settings tile (KaikoTileService).
     */
    fun fireAlert(context: Context, source: String = "unknown") {
        val triggerTimestamp = System.currentTimeMillis()
        val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(triggerTimestamp))
        val eventId = "KAIKO-" + UUID.randomUUID().toString().substring(0, 6).uppercase(Locale.getDefault())

        Log.d(TAG, "==================================================")
        Log.d(TAG, "[$formattedTime] EMERGENCY SOS TRIGGERED (Event ID: $eventId, Source: '$source')")
        Log.d(TAG, "DEBUG_MODE: $DEBUG_MODE")

        // Persist initial state
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ACTIVE_EVENT_ID, eventId)
            .putString(KEY_CURRENT_SOS_STATE, SosState.SOS_TRIGGERED.name)
            .putInt(KEY_ACTIVE_GUARDIAN_INDEX, 1)
            .apply()
        broadcastStateChange(context)

        // Asynchronously acquire location, then initiate guardian escalation chain
        CoroutineScope(Dispatchers.IO).launch {
            val location: Location? = fetchCurrentLocation(context)
            val coordsText = if (location != null) {
                "${location.latitude}, ${location.longitude}"
            } else {
                "Location unavailable"
            }

            prefs.edit().putString(KEY_LAST_COORDS, coordsText).apply()
            Log.d(TAG, "Location determined: $coordsText")

            // Begin escalation with Guardian 1
            dispatchToGuardian(context, guardianIndex = 1, isRetry = false)
        }
    }

    /**
     * Dispatches emergency alert to Guardian at the specified index (1, 2, or 3).
     * If guardian is not configured, automatically skips to the next guardian or final state.
     */
    fun dispatchToGuardian(context: Context, guardianIndex: Int, isRetry: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentState = getCurrentState(context)

        // If user already marked safe or active alert was acknowledged, halt dispatch
        if (currentState == SosState.USER_MARKED_SAFE || !currentState.isActive() && currentState != SosState.SOS_TRIGGERED) {
            Log.d(TAG, "Dispatch halted. Current state is inactive: $currentState")
            return
        }

        val phone = getGuardianPhone(context, guardianIndex)
        val coords = prefs.getString(KEY_LAST_COORDS, "Location unavailable") ?: "Location unavailable"
        val eventId = prefs.getString(KEY_ACTIVE_EVENT_ID, "KAIKO-SOS") ?: "KAIKO-SOS"

        if (phone.isNullOrBlank()) {
            Log.w(TAG, "Guardian $guardianIndex not configured. Skipping to next escalation step...")
            if (guardianIndex < 3) {
                dispatchToGuardian(context, guardianIndex + 1, isRetry = false)
            } else {
                transitionToFinalEscalation(context)
            }
            return
        }

        // Update state to waiting for this guardian
        val nextState = when (guardianIndex) {
            1 -> SosState.WAITING_FOR_GUARDIAN_1
            2 -> SosState.WAITING_FOR_GUARDIAN_2
            3 -> SosState.WAITING_FOR_GUARDIAN_3
            else -> SosState.FINAL_ESCALATION_REQUIRED
        }
        updateState(context, nextState)
        prefs.edit().putInt(KEY_ACTIVE_GUARDIAN_INDEX, guardianIndex).apply()

        val smsText = buildEmergencyMessage(coords, eventId, guardianIndex)
        Log.i(TAG, "Alerting Guardian $guardianIndex ($phone). isRetry=$isRetry")

        // Perform SMS dispatch
        sendSms(context, phone, smsText, guardianIndex, isRetry)

        // Schedule escalation timer for the next guardian (if not already final)
        scheduleEscalationTimer(context, guardianIndex)

        // Post / Update high-priority persistent lock-screen notification
        postEmergencyNotification(context, nextState, phone, coords, eventId)
    }

    /**
     * Sends the SMS or simulates in DEBUG_MODE.
     */
    private fun sendSms(
        context: Context,
        phoneNumber: String,
        message: String,
        guardianIndex: Int,
        isRetry: Boolean
    ) {
        if (DEBUG_MODE) {
            Log.i(TAG, "[DEBUG MODE] Simulated SMS to Guardian $guardianIndex ($phoneNumber): \"$message\"")
            // Simulate carrier dispatch callback on main thread
            Handler(Looper.getMainLooper()).postDelayed({
                onSmsSentResult(context, guardianIndex, isSuccess = true, isRetry = isRetry)
            }, 800)
            return
        }

        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasSmsPermission) {
            Log.e(TAG, "SEND_SMS runtime permission not granted! Cannot dispatch SMS.")
            onSmsSentResult(context, guardianIndex, isSuccess = false, isRetry = isRetry)
            return
        }

        try {
            val sentIntent = Intent(context, SmsSentReceiver::class.java).apply {
                action = SmsSentReceiver.ACTION_SMS_SENT
                putExtra(SmsSentReceiver.EXTRA_GUARDIAN_INDEX, guardianIndex)
                putExtra(SmsSentReceiver.EXTRA_IS_RETRY, isRetry)
            }
            val pendingSentIntent = PendingIntent.getBroadcast(
                context,
                REQ_SMS_SENT_BASE + guardianIndex * 10 + (if (isRetry) 1 else 0),
                sentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            smsManager.sendTextMessage(phoneNumber, null, message, pendingSentIntent, null)
            Log.i(TAG, "Dispatched SMS request to carrier for Guardian $guardianIndex ($phoneNumber)")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during SMS send: ${e.message}", e)
            onSmsSentResult(context, guardianIndex, isSuccess = false, isRetry = isRetry)
        }
    }

    /**
     * Callback from SmsSentReceiver or DEBUG_MODE simulation.
     */
    fun onSmsSentResult(context: Context, guardianIndex: Int, isSuccess: Boolean, isRetry: Boolean) {
        if (isSuccess) {
            Log.i(TAG, "SMS transmission confirmed delivered to carrier for Guardian $guardianIndex.")
        } else {
            Log.w(TAG, "SMS dispatch failed for Guardian $guardianIndex. isRetry=$isRetry")
            if (!isRetry) {
                Log.d(TAG, "Scheduling one-time SMS retry in 3 seconds for Guardian $guardianIndex...")
                Handler(Looper.getMainLooper()).postDelayed({
                    val currentState = getCurrentState(context)
                    if (currentState.isActive()) {
                        dispatchToGuardian(context, guardianIndex, isRetry = true)
                    }
                }, 3000)
            } else {
                Log.e(TAG, "SMS retry also failed for Guardian $guardianIndex. Immediately advancing to next guardian...")
                // Cancel pending escalation timer for this failed guardian
                cancelEscalationTimer(context)
                // Immediately proceed to the next guardian without waiting for the full timeout
                handleEscalationTimeout(context)
            }
        }
    }

    /**
     * Advances to the next guardian in the escalation chain when timer expires.
     */
    fun handleEscalationTimeout(context: Context) {
        val currentState = getCurrentState(context)
        if (!currentState.isActive()) {
            Log.d(TAG, "Escalation timeout ignored because state is inactive ($currentState).")
            return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentIndex = prefs.getInt(KEY_ACTIVE_GUARDIAN_INDEX, 1)
        Log.w(TAG, "Escalation timeout elapsed for Guardian $currentIndex without acknowledgement.")

        if (currentIndex < 3) {
            val nextIndex = currentIndex + 1
            Log.i(TAG, "Escalating from Guardian $currentIndex -> Guardian $nextIndex")
            dispatchToGuardian(context, nextIndex, isRetry = false)
        } else {
            transitionToFinalEscalation(context)
        }
    }

    /**
     * Transitions state to FINAL_ESCALATION_REQUIRED when all configured guardians have been alerted.
     */
    private fun transitionToFinalEscalation(context: Context) {
        Log.w(TAG, "All guardians exhausted without acknowledgement. Transitioning to FINAL_ESCALATION_REQUIRED.")
        cancelEscalationTimer(context)
        updateState(context, SosState.FINAL_ESCALATION_REQUIRED)

        val helpline = getFinalHelpline(context)
        if (!helpline.isNullOrBlank()) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val coords = prefs.getString(KEY_LAST_COORDS, "Location unavailable") ?: "Location unavailable"
            val eventId = prefs.getString(KEY_ACTIVE_EVENT_ID, "KAIKO-SOS") ?: "KAIKO-SOS"
            val msg = "EMERGENCY: All emergency contacts unacknowledged. User location: $coords ($eventId)"
            Log.i(TAG, "Alerting backup helpline ($helpline): $msg")
            sendSms(context, helpline, msg, guardianIndex = 4, isRetry = false)
        }

        postEmergencyNotification(context, SosState.FINAL_ESCALATION_REQUIRED, helpline ?: "None", "See logs", "")
    }

    /**
     * "I'm Safe Now" Action:
     * Immediately stops pending escalation timers and updates state to USER_MARKED_SAFE.
     */
    fun markUserSafe(context: Context) {
        Log.i(TAG, "Action triggered: 'I\'m Safe Now'. Cancelling pending escalation...")
        cancelEscalationTimer(context)
        updateState(context, SosState.USER_MARKED_SAFE)

        // Post resolution notification
        val notificationManager = NotificationManagerCompat.from(context)
        createNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Kaiko SOS Resolved")
            .setContentText("Emergency cancelled. You marked yourself safe.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

        try {
            if (hasNotificationPermission(context)) {
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not post resolution notification: ${e.message}")
        }
    }

    /**
     * Simulated Guardian Acknowledgement [FOR LOCAL PROTOTYPE / TESTING ONLY].
     * Stops pending escalation and records test acknowledgement.
     */
    fun simulateGuardianAck(context: Context, guardianIndex: Int? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeIndex = guardianIndex ?: prefs.getInt(KEY_ACTIVE_GUARDIAN_INDEX, 1)

        val ackState = when (activeIndex) {
            1 -> SosState.GUARDIAN_1_ACKNOWLEDGED
            2 -> SosState.GUARDIAN_2_ACKNOWLEDGED
            3 -> SosState.GUARDIAN_3_ACKNOWLEDGED
            else -> SosState.GUARDIAN_1_ACKNOWLEDGED
        }

        Log.i(TAG, "[TEST / SIMULATION ONLY] Simulated acknowledgement received for Guardian $activeIndex. Stopping escalation.")
        cancelEscalationTimer(context)
        updateState(context, ackState)

        // Post acknowledgement confirmation notification
        val notificationManager = NotificationManagerCompat.from(context)
        createNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Kaiko Alert Acknowledged [TEST ONLY]")
            .setContentText("Guardian $activeIndex acknowledged the alert. Escalation stopped.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

        try {
            if (hasNotificationPermission(context)) {
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not post ack notification: ${e.message}")
        }
    }

    /**
     * Schedules the next escalation alarm via AlarmManager.
     */
    private fun scheduleEscalationTimer(context: Context, currentGuardianIndex: Int) {
        val delaySec = getEscalationDelaySeconds(context)
        val triggerAtMillis = System.currentTimeMillis() + (delaySec * 1000L)

        val intent = Intent(context, EscalationReceiver::class.java).apply {
            action = EscalationReceiver.ACTION_ESCALATE_TIMER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQ_ESCALATION_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager?.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            Log.d(TAG, "Escalation timer scheduled: will escalate in ${delaySec}s if Guardian $currentGuardianIndex does not acknowledge.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact AlarmManager: ${e.message}")
            alarmManager?.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    /**
     * Cancels any pending escalation alarm.
     */
    private fun cancelEscalationTimer(context: Context) {
        val intent = Intent(context, EscalationReceiver::class.java).apply {
            action = EscalationReceiver.ACTION_ESCALATE_TIMER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQ_ESCALATION_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        alarmManager?.cancel(pendingIntent)
        Log.d(TAG, "Pending escalation timer cancelled.")
    }

    /**
     * Posts high-priority persistent lock-screen notification with action buttons.
     */
    private fun postEmergencyNotification(
        context: Context,
        state: SosState,
        guardianPhone: String,
        coordsText: String,
        eventId: String
    ) {
        createNotificationChannel(context)

        // Action: "I'm Safe Now"
        val safeIntent = Intent(context, ActionReceiver::class.java).apply {
            action = ActionReceiver.ACTION_MARK_SAFE
        }
        val safePendingIntent = PendingIntent.getBroadcast(
            context,
            REQ_ACTION_SAFE,
            safeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: "Simulate Ack [TEST ONLY]"
        val ackIntent = Intent(context, ActionReceiver::class.java).apply {
            action = ActionReceiver.ACTION_SIMULATE_ACK
        }
        val ackPendingIntent = PendingIntent.getBroadcast(
            context,
            REQ_ACTION_SIMULATE_ACK,
            ackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "⚠️ KAIKO SOS ACTIVE: ${state.displayName}"
        val content = "Alerting: $guardianPhone | Location: $coordsText"
        val bigText = StringBuilder()
            .append("Status: ").append(state.displayName).append("\n")
            .append("Alerting Guardian: ").append(guardianPhone).append("\n")
            .append("Location: ").append(coordsText).append("\n")
            .append("Event Token: ").append(eventId).append("\n")
            .append(if (DEBUG_MODE) "[DEBUG MODE: SMS Simulated]" else "[REAL SMS MODE]")
            .toString()

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible on secure lock screen!
            .setOngoing(state.isActive())
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "I'm Safe Now", safePendingIntent)
            .addAction(android.R.drawable.ic_menu_info_details, "Simulate Ack [TEST]", ackPendingIntent)
            .build()

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            if (hasNotificationPermission(context)) {
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error posting notification: ${e.message}")
        }
    }

    private fun buildEmergencyMessage(coords: String, eventId: String, guardianIndex: Int): String {
        return "I need help. My location: $coords https://maps.google.com/?q=$coords [Alert: $eventId (G$guardianIndex)]"
    }

    private fun updateState(context: Context, newState: SosState) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CURRENT_SOS_STATE, newState.name).apply()
        Log.d(TAG, "SOS State Transitioned to: ${newState.name} (${newState.displayName})")
        broadcastStateChange(context)
    }

    fun getCurrentState(context: Context): SosState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stateName = prefs.getString(KEY_CURRENT_SOS_STATE, SosState.IDLE.name) ?: SosState.IDLE.name
        return try {
            SosState.valueOf(stateName)
        } catch (e: Exception) {
            SosState.IDLE
        }
    }

    fun getGuardianPhone(context: Context, index: Int): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return when (index) {
            1 -> prefs.getString(KEY_GUARDIAN_PHONE, null)
            2 -> prefs.getString(KEY_GUARDIAN_2_PHONE, null)
            3 -> prefs.getString(KEY_GUARDIAN_3_PHONE, null)
            else -> null
        }
    }

    fun getFinalHelpline(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FINAL_HELPLINE_PHONE, null)
    }

    fun getEscalationDelaySeconds(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_ESCALATION_DELAY_SECONDS, DEFAULT_ESCALATION_DELAY_SECONDS)
    }

    private fun broadcastStateChange(context: Context) {
        val intent = Intent(ACTION_STATE_CHANGED)
        context.sendBroadcast(intent)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun fetchCurrentLocation(context: Context): Location? {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation && !hasCoarseLocation) {
            Log.e(TAG, "Location permission NOT granted.")
            return null
        }

        return try {
            withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val fusedLocationClient: FusedLocationProviderClient =
                        LocationServices.getFusedLocationProviderClient(context)
                    val cancellationTokenSource = CancellationTokenSource()

                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cancellationTokenSource.token
                    ).addOnSuccessListener { location: Location? ->
                        if (continuation.isActive) continuation.resume(location)
                    }.addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }.addOnCanceledListener {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    continuation.invokeOnCancellation {
                        cancellationTokenSource.cancel()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during location retrieval: ${e.message}")
            null
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Emergency Alerts"
            val descriptionText = "Shows status notifications when Kaiko emergency triggers fire."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
