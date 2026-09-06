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
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Central Orchestrator & State Machine for Kaiko Emergency Operations (v1.2.1).
 * Features:
 * 1. Single central SOS pipeline for all triggers (App, 3x Volume, Normal Widget, Discreet Widget).
 * 2. Strict location handling with 3-5s timeout and resilient fallbacks.
 * 3. Discreet widget silent execution (no permission popups, no obvious local emergency notification).
 * 4. Fresh location attempt for each guardian escalation step (G1, G2, G3) with explicit "Last known location" labeling.
 * 5. Tracking of alerted guardians per SOS event with targeted "I'm Safe Now" and "Test ACK" updates.
 * 6. Clean event state lifecycle and separation.
 * 7. Safe DEBUG_MODE testing switch.
 */
object TriggerManager {

    // =========================================================================
    // DEBUG MODE TOGGLE
    // =========================================================================
    // Set to true for safe testing (no real SMS sent).
    // Set to false to send actual SMS alerts.
    const val DEBUG_MODE = false

    // =========================================================================
    // CONSTANTS & KEYS
    // =========================================================================
    const val TAG = "KAIKO_DEBUG"
    const val PREFS_NAME = "kaiko_preferences"

    // Standard User-Friendly Trigger Method Names
    const val TRIGGER_MANUAL_APP = "Manual SOS Trigger"
    const val TRIGGER_HARDWARE_BUTTON = "Hardware Button Trigger"
    const val TRIGGER_QUICK_ACCESS = "Quick Access Trigger"
    const val TRIGGER_DISCREET_SAFETY = "Discreet Safety Trigger"

    // Guardian Contact Keys (Preserving existing keys for backward compatibility)
    const val KEY_GUARDIAN_PHONE = "guardian_phone_number"
    const val KEY_GUARDIAN_2_PHONE = "guardian_2_phone_number"
    const val KEY_GUARDIAN_3_PHONE = "guardian_3_phone_number"
    const val KEY_FINAL_HELPLINE_PHONE = "final_helpline_phone_number"

    // Configuration Keys
    const val KEY_ESCALATION_DELAY_SECONDS = "escalation_delay_seconds"
    const val DEFAULT_ESCALATION_DELAY_SECONDS = 60L

    // State Persistence Keys
    const val KEY_CURRENT_SOS_STATE = "current_sos_state"
    const val KEY_ACTIVE_EVENT_ID = "active_event_id"
    const val KEY_ACTIVE_TRIGGER_MODE = "active_trigger_mode"
    const val KEY_ACTIVE_GUARDIAN_INDEX = "active_guardian_index"
    const val KEY_LAST_COORDS = "last_known_coords"
    const val KEY_HAS_PREVIOUS_LOCATION = "has_previous_location"
    const val KEY_ALERTED_GUARDIANS = "alerted_guardian_phones"

    // Broadcast Actions
    const val ACTION_STATE_CHANGED = "com.yourname.kaiko.ACTION_STATE_CHANGED"
    const val ACTION_REQUEST_LOCATION_PERMISSION = "com.yourname.kaiko.ACTION_REQUEST_LOCATION_PERMISSION"
    const val ACTION_REQUEST_LOCATION_SETTINGS = "com.yourname.kaiko.ACTION_REQUEST_LOCATION_SETTINGS"

    private const val NOTIFICATION_CHANNEL_ID = "kaiko_emergency_alerts"
    private const val NOTIFICATION_ID = 1001
    private const val LOCATION_TIMEOUT_MS = 4000L // 4.0s for individual location fetches
    private const val LOCATION_SAFETY_TIMEOUT_MS = 5000L // 5.0s maximum waiting time for permission/setup/acquisition flow

    // PendingIntent Request Codes
    private const val REQ_ESCALATION_ALARM = 2001
    private const val REQ_SMS_SENT_BASE = 3000
    private const val REQ_ACTION_SAFE = 4001
    private const val REQ_ACTION_SIMULATE_ACK = 4002

    enum class LocationStatus {
        CURRENT,
        LAST_KNOWN,
        UNAVAILABLE
    }

    /**
     * Normalizes trigger string input to the official user-friendly labels.
     */
    fun normalizeTriggerSource(source: String): String {
        return when (source.trim()) {
            TRIGGER_MANUAL_APP, "manual_app_test", "app", "manual" -> TRIGGER_MANUAL_APP
            TRIGGER_HARDWARE_BUTTON, "volume_button", "volume_3x", "hardware" -> TRIGGER_HARDWARE_BUTTON
            TRIGGER_QUICK_ACCESS, "widget", "quick_settings_tile", "quick_access" -> TRIGGER_QUICK_ACCESS
            TRIGGER_DISCREET_SAFETY, "disguised_widget", "discreet" -> TRIGGER_DISCREET_SAFETY
            else -> TRIGGER_MANUAL_APP
        }
    }

    /**
     * Central SOS entry point for ALL triggers:
     * 1. App SOS / Test Trigger
     * 2. 3x Volume Press
     * 3. Normal SOS Widget
     * 4. Discreet Widget
     */
    fun fireAlert(context: Context, source: String = "unknown") {
        val triggerMode = normalizeTriggerSource(source)
        val triggerTimestamp = System.currentTimeMillis()
        val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(triggerTimestamp))
        val eventId = "KAIKO-" + UUID.randomUUID().toString().substring(0, 6).uppercase(Locale.getDefault())

        Log.d(TAG, "==================================================")
        Log.d(TAG, "[$formattedTime] EMERGENCY SOS TRIGGERED (Event ID: $eventId, Mode: '$triggerMode')")
        Log.d(TAG, "DEBUG_MODE: $DEBUG_MODE")

        // 1. Cancel any existing escalation timer & clean up previous event data
        cancelEscalationTimer(context)
        clearAlertedGuardians(context)

        // 2. Persist fresh event state
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ACTIVE_EVENT_ID, eventId)
            .putString(KEY_ACTIVE_TRIGGER_MODE, triggerMode)
            .putString(KEY_CURRENT_SOS_STATE, SosState.SOS_TRIGGERED.name)
            .putInt(KEY_ACTIVE_GUARDIAN_INDEX, 1)
            .remove(KEY_LAST_COORDS)
            .putBoolean(KEY_HAS_PREVIOUS_LOCATION, false)
            .apply()

        broadcastStateChange(context)

        // 3. Central location resolution & Guardian 1 dispatch
        CoroutineScope(Dispatchers.IO).launch {
            val location: Location? = if (triggerMode == TRIGGER_DISCREET_SAFETY) {
                // Section 8: Discreet Safety Trigger — Completely Untouched
                // Strictly silent: no popups, no UI, no extra delay.
                if (hasLocationPermission(context) && isLocationServiceEnabled(context)) {
                    Log.d(TAG, "Discreet Safety Trigger: Permission & Services available. Silently attempting location fetch...")
                    fetchFreshLocationWithFallback(context, LOCATION_TIMEOUT_MS)
                } else {
                    Log.d(TAG, "Discreet Safety Trigger: Permission or services not available. Silently proceeding without location.")
                    null
                }
            } else {
                // Section 1-6: Normal SOS Triggers (3x Press, Normal Widget, App SOS Button)
                resolveNormalSosLocation(context)
            }

            val locationStatus: LocationStatus
            val coords: String?

            if (location != null) {
                coords = "${location.latitude},${location.longitude}"
                prefs.edit()
                    .putString(KEY_LAST_COORDS, coords)
                    .putBoolean(KEY_HAS_PREVIOUS_LOCATION, true)
                    .apply()
                locationStatus = LocationStatus.CURRENT
                Log.d(TAG, "Initial Location acquired: $coords")
            } else {
                coords = null
                prefs.edit().putBoolean(KEY_HAS_PREVIOUS_LOCATION, false).apply()
                locationStatus = LocationStatus.UNAVAILABLE
                Log.d(TAG, "Initial Location unavailable for Guardian 1.")
            }

            // Dispatch to Guardian 1
            dispatchToGuardianInternal(
                context = context,
                guardianIndex = 1,
                locationStatus = locationStatus,
                coords = coords,
                isRetry = false
            )
        }
    }

    /**
     * Dispatches emergency alert to Guardian at the specified index (1, 2, or 3).
     * For Guardian 2 & 3, ALWAYS attempts to fetch a fresh current location (max 3-5s).
     */
    fun dispatchToGuardian(context: Context, guardianIndex: Int, isRetry: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            val currentState = getCurrentState(context)
            if (currentState == SosState.USER_MARKED_SAFE || (!currentState.isActive() && currentState != SosState.SOS_TRIGGERED)) {
                Log.d(TAG, "Dispatch halted. Current state is inactive: $currentState")
                return@launch
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            val locationStatus: LocationStatus
            val coords: String?

            if (guardianIndex == 1) {
                val hasPrev = prefs.getBoolean(KEY_HAS_PREVIOUS_LOCATION, false)
                val storedCoords = prefs.getString(KEY_LAST_COORDS, null)
                if (hasPrev && !storedCoords.isNullOrBlank()) {
                    locationStatus = LocationStatus.CURRENT
                    coords = storedCoords
                } else {
                    locationStatus = LocationStatus.UNAVAILABLE
                    coords = null
                }
            } else {
                // Section 7 & 8: Guardian 2 & 3 Location Flow
                // ALWAYS attempt to fetch a NEW current location (Max 3-5s)
                Log.d(TAG, "Guardian $guardianIndex Escalation: ALWAYS attempting fresh current location (timeout: ${LOCATION_TIMEOUT_MS}ms)...")
                val freshLocation: Location? = if (hasLocationPermission(context) && isLocationServiceEnabled(context)) {
                    fetchFreshLocationOnly(context, LOCATION_TIMEOUT_MS)
                } else {
                    null
                }

                if (freshLocation != null) {
                    // Case A: New location available -> Send NEW CURRENT location
                    coords = "${freshLocation.latitude},${freshLocation.longitude}"
                    prefs.edit()
                        .putString(KEY_LAST_COORDS, coords)
                        .putBoolean(KEY_HAS_PREVIOUS_LOCATION, true)
                        .apply()
                    locationStatus = LocationStatus.CURRENT
                    Log.i(TAG, "Guardian $guardianIndex: NEW Current location obtained: $coords")
                } else {
                    // Case B: New location unavailable, but previous location exists -> Send LAST KNOWN location
                    val hasPrev = prefs.getBoolean(KEY_HAS_PREVIOUS_LOCATION, false)
                    val prevCoords = prefs.getString(KEY_LAST_COORDS, null)

                    if (hasPrev && !prevCoords.isNullOrBlank()) {
                        coords = prevCoords
                        locationStatus = LocationStatus.LAST_KNOWN
                        Log.i(TAG, "Guardian $guardianIndex: Fresh location unavailable. Using LAST KNOWN location: $coords")
                    } else {
                        // Case C: No location has ever been obtained -> Send without location
                        coords = null
                        locationStatus = LocationStatus.UNAVAILABLE
                        Log.i(TAG, "Guardian $guardianIndex: No location has ever been obtained. Sending without location.")
                    }
                }
            }

            dispatchToGuardianInternal(context, guardianIndex, locationStatus, coords, isRetry)
        }
    }

    /**
     * Internal execution of SMS transmission, escalation timer scheduling, and notification posting.
     */
    private fun dispatchToGuardianInternal(
        context: Context,
        guardianIndex: Int,
        locationStatus: LocationStatus,
        coords: String?,
        isRetry: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentState = getCurrentState(context)

        if (currentState == SosState.USER_MARKED_SAFE || (!currentState.isActive() && currentState != SosState.SOS_TRIGGERED)) {
            Log.d(TAG, "Dispatch halted in internal step. Current state: $currentState")
            return
        }

        val phone = getGuardianPhone(context, guardianIndex)
        val eventId = prefs.getString(KEY_ACTIVE_EVENT_ID, "KAIKO-SOS") ?: "KAIKO-SOS"
        val triggerMode = prefs.getString(KEY_ACTIVE_TRIGGER_MODE, TRIGGER_MANUAL_APP) ?: TRIGGER_MANUAL_APP

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

        // Section 6, 7, 8, 9: Construct formatted message with trigger mode
        val smsText = buildEmergencyMessage(triggerMode, locationStatus, coords)
        Log.i(TAG, "Alerting Guardian $guardianIndex ($phone). isRetry=$isRetry. Status=$locationStatus")

        // Track guardian as alerted for this SOS event (Section 13)
        addAlertedGuardian(context, phone)

        // Perform SMS dispatch
        sendSms(context, phone, smsText, guardianIndex, isRetry)

        // Schedule escalation timer for next guardian
        scheduleEscalationTimer(context, guardianIndex)

        // Section 5: Discreet Widget Notification Rules
        // Do NOT show loud or obvious emergency notifications for Discreet Widget!
        if (triggerMode != TRIGGER_DISCREET_SAFETY) {
            val coordsDisplay = when (locationStatus) {
                LocationStatus.CURRENT -> "Current: $coords"
                LocationStatus.LAST_KNOWN -> "Last Known: $coords"
                LocationStatus.UNAVAILABLE -> "Location unavailable"
            }
            postEmergencyNotification(context, nextState, phone, coordsDisplay, eventId)
        } else {
            Log.d(TAG, "Discreet Safety Trigger: Suppressing local emergency notifications.")
        }
    }

    /**
     * Builds standard emergency message adhering strictly to Sections 6, 7, 8, 9.
     */
    fun buildEmergencyMessage(
        triggerMethod: String,
        locationStatus: LocationStatus,
        coords: String?
    ): String {
        val sb = StringBuilder()
        sb.append("🚨 KAIKO SOS ALERT\n\n")
        sb.append("An emergency alert has been triggered.\n\n")
        sb.append("Trigger method:\n").append(triggerMethod).append("\n\n")

        when (locationStatus) {
            LocationStatus.CURRENT -> {
                sb.append("📍 Current Location:\n")
                sb.append("https://maps.google.com/?q=").append(coords).append("\n\n")
            }
            LocationStatus.LAST_KNOWN -> {
                sb.append("📍 Last known location:\n")
                sb.append("https://maps.google.com/?q=").append(coords).append("\n\n")
            }
            LocationStatus.UNAVAILABLE -> {
                sb.append("⚠️ Current location is unavailable.\n\n")
            }
        }

        sb.append("Please contact or check on the user immediately.\n\n")
        sb.append("— Sent via Kaiko")
        return sb.toString()
    }

    /**
     * Resolution message for Section 14: "I'M SAFE NOW".
     */
    fun buildSafeResolutionMessage(): String {
        return "🟢 KAIKO SOS UPDATE\n\n" +
                "The user has marked themselves as safe.\n\n" +
                "Emergency escalation has been stopped.\n\n" +
                "No further guardian escalation is currently scheduled."
    }

    /**
     * Test Update message for Section 15: Simulated Guardian ACK.
     */
    fun buildTestAckResolutionMessage(): String {
        return "🧪 KAIKO TEST UPDATE\n\n" +
                "This SOS event was part of a test.\n\n" +
                "The emergency test has been acknowledged and the escalation has been stopped.\n\n" +
                "No action is required."
    }

    /**
     * Tracks that a guardian has been alerted in the current SOS event.
     */
    fun addAlertedGuardian(context: Context, phoneNumber: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_ALERTED_GUARDIANS, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(phoneNumber.trim())
        prefs.edit().putStringSet(KEY_ALERTED_GUARDIANS, set).apply()
        Log.d(TAG, "Recorded alerted guardian: $phoneNumber. Total alerted in this event: ${set.size}")
    }

    /**
     * Retrieves guardians alerted during this specific event.
     */
    fun getAlertedGuardians(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_ALERTED_GUARDIANS, emptySet()) ?: emptySet()
    }

    /**
     * Clears alerted guardian list for fresh SOS event state.
     */
    fun clearAlertedGuardians(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_ALERTED_GUARDIANS).apply()
    }

    /**
     * Sends the SMS or simulates in DEBUG_MODE.
     * Uses sendMultipartTextMessage to safely support multi-part messages with Unicode emojis.
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

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>().apply {
                    repeat(parts.size) { add(pendingSentIntent) }
                }
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, pendingSentIntent, null)
            }
            Log.i(TAG, "Dispatched SMS request to carrier for Guardian $guardianIndex ($phoneNumber)")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during SMS send: ${e.message}", e)
            onSmsSentResult(context, guardianIndex, isSuccess = false, isRetry = isRetry)
        }
    }

    /**
     * Sends resolution/test SMS update to alerted guardians.
     */
    private fun sendResolutionSms(context: Context, phoneNumber: String, message: String) {
        if (DEBUG_MODE) {
            Log.i(TAG, "[DEBUG MODE] Simulated Resolution SMS to ($phoneNumber): \"$message\"")
            return
        }

        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasSmsPermission) {
            Log.e(TAG, "SEND_SMS permission not granted for resolution SMS to $phoneNumber")
            return
        }

        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            Log.i(TAG, "Dispatched resolution SMS to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Exception dispatching resolution SMS: ${e.message}", e)
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
                cancelEscalationTimer(context)
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
            val coords = prefs.getString(KEY_LAST_COORDS, null)
            val triggerMode = prefs.getString(KEY_ACTIVE_TRIGGER_MODE, TRIGGER_MANUAL_APP) ?: TRIGGER_MANUAL_APP
            val status = if (coords != null) LocationStatus.LAST_KNOWN else LocationStatus.UNAVAILABLE
            val msg = buildEmergencyMessage(triggerMode, status, coords)
            Log.i(TAG, "Alerting backup helpline ($helpline)")
            addAlertedGuardian(context, helpline)
            sendSms(context, helpline, msg, guardianIndex = 4, isRetry = false)
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val triggerMode = prefs.getString(KEY_ACTIVE_TRIGGER_MODE, TRIGGER_MANUAL_APP) ?: TRIGGER_MANUAL_APP
        if (triggerMode != TRIGGER_DISCREET_SAFETY) {
            postEmergencyNotification(context, SosState.FINAL_ESCALATION_REQUIRED, helpline ?: "None", "All guardians alerted", "")
        }
    }

    /**
     * Section 14: "I'M SAFE NOW" Action:
     * 1. Stop pending escalation
     * 2. Resolve current SOS event
     * 3. Find guardians already alerted during THIS event
     * 4. Send resolution update ONLY to those guardians
     * 5. Clean up event state
     */
    fun markUserSafe(context: Context) {
        Log.i(TAG, "Action triggered: 'I\'m Safe Now'. Halting pending escalation...")
        cancelEscalationTimer(context)
        updateState(context, SosState.USER_MARKED_SAFE)

        // Dismiss ongoing emergency notification
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(NOTIFICATION_ID)

        // Find guardians already alerted during THIS event
        val alertedGuardians = getAlertedGuardians(context)
        Log.i(TAG, "Sending 'I\'M SAFE NOW' resolution update to ${alertedGuardians.size} alerted guardian(s): $alertedGuardians")

        val safeMessage = buildSafeResolutionMessage()
        for (phone in alertedGuardians) {
            sendResolutionSms(context, phone, safeMessage)
        }

        // Section 16: Clean up event state so next SOS starts fresh
        clearAlertedGuardians(context)
    }

    /**
     * Section 15: Simulated Guardian Acknowledgement [TEST ONLY]:
     * 1. Stop pending escalation
     * 2. Resolve current test event
     * 3. Find guardians already alerted during THIS event
     * 4. Send TEST update ONLY to those guardians
     * 5. Clean up event state
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

        Log.i(TAG, "[TEST ONLY] Simulated acknowledgement received for Guardian $activeIndex. Stopping escalation.")
        cancelEscalationTimer(context)
        updateState(context, ackState)

        // Dismiss ongoing emergency notification
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(NOTIFICATION_ID)

        // Find guardians already alerted during THIS event
        val alertedGuardians = getAlertedGuardians(context)
        Log.i(TAG, "Sending Simulated TEST update to ${alertedGuardians.size} alerted guardian(s): $alertedGuardians")

        val testMessage = buildTestAckResolutionMessage()
        for (phone in alertedGuardians) {
            sendResolutionSms(context, phone, testMessage)
        }

        // Section 16: Clean up event state
        clearAlertedGuardians(context)
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
        val content = "Alerting: $guardianPhone | $coordsText"
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
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    /**
     * Checks whether Android Location Services / GPS Toggle is ON.
     */
    fun isLocationServiceEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(lm) ||
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Requests Android location permission by launching MainActivity or broadcasting.
     * Skips request if permission is already ON.
     */
    fun requestLocationPermission(context: Context) {
        if (hasLocationPermission(context)) {
            Log.d(TAG, "requestLocationPermission: Permission is already ON. Skipping.")
            return
        }
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_REQUEST_LOCATION_PERMISSION
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MainActivity for permission request: ${e.message}")
        }
        try {
            val broadcast = Intent(ACTION_REQUEST_LOCATION_PERMISSION).setPackage(context.packageName)
            context.sendBroadcast(broadcast)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast permission request: ${e.message}")
        }
    }

    /**
     * Requests user to turn ON Android Location Services / GPS Toggle via transparent LocationPromptActivity.
     * Uses the default native Android/Google location request dialog directly over current screen.
     * Skips request if GPS Toggle is already ON. Does NOT open MainActivity.
     */
    fun requestLocationSettings(context: Context) {
        if (isLocationServiceEnabled(context)) {
            Log.d(TAG, "requestLocationSettings: GPS Toggle is already ON. Skipping.")
            return
        }
        try {
            val intent = Intent(context, LocationPromptActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start LocationPromptActivity for location settings: ${e.message}")
        }
    }

    /**
     * Executes the strict Location Flow for Normal SOS Triggers (v1.3.4):
     * 1. 3x Press Trigger
     * 2. Normal Widget Trigger
     * 3. App SOS Button
     *
     * Flow:
     * 1. KAIKO LOCATION PERMISSION
     *    - If already ON: SKIP permission request -> Go directly to GPS Toggle check.
     *    - If OFF: Request location permission -> Wait FULL 5s.
     *      - User ACCEPTS -> Continue to GPS Toggle check.
     *      - User DENIES / IGNORES -> After FULL 5s, send SOS WITHOUT location.
     *
     * 2. ANDROID LOCATION SERVICES / GPS TOGGLE
     *    - If already ON: SKIP toggle request -> Immediately get CURRENT location.
     *    - If OFF: Default native Android/Google location request directly over current screen.
     *      - User ACCEPTS / turns GPS ON -> Re-check that GPS is actually ON -> Get CURRENT location -> SOS + Maps link.
     *      - User DENIES / CLOSES / IGNORES -> Wait FULL 5 seconds -> Send SOS WITHOUT location.
     *
     * 3. CURRENT LOCATION ACQUISITION
     *    - Never treat location as unavailable immediately after accepting GPS toggle.
     *    - Re-check GPS state and obtain current location with full acquisition window.
     */
    suspend fun resolveNormalSosLocation(context: Context): Location? {
        Log.d(TAG, "Starting Normal SOS location flow (v1.3.4)...")

        // 1. KAIKO LOCATION PERMISSION
        if (hasLocationPermission(context)) {
            Log.i(TAG, "STEP 1: Kaiko location permission is already ON. SKIPPING permission request.")
        } else {
            Log.w(TAG, "STEP 1: Kaiko location permission is OFF. Requesting permission (max 5s)...")
            requestLocationPermission(context)

            val permStartTime = System.currentTimeMillis()
            var granted = false
            while (System.currentTimeMillis() - permStartTime < 5000L) {
                delay(200)
                if (hasLocationPermission(context)) {
                    granted = true
                    Log.i(TAG, "STEP 1: User ACCEPTED Kaiko location permission!")
                    break
                }
            }

            if (!granted) {
                val elapsedPerm = System.currentTimeMillis() - permStartTime
                if (elapsedPerm < 5000L) {
                    delay(5000L - elapsedPerm)
                }
                Log.w(TAG, "STEP 1: User DENIED or IGNORED location permission after FULL 5s. SEND SOS WITHOUT LOCATION.")
                return null
            }
        }

        // 2. ANDROID LOCATION SERVICES / GPS TOGGLE
        if (isLocationServiceEnabled(context)) {
            Log.i(TAG, "STEP 2: Android Location Services / GPS Toggle is already ON. SKIPPING toggle request.")
        } else {
            Log.w(TAG, "STEP 2: Android Location Services / GPS Toggle is OFF. Launching default native Google dialog over current screen (max 5s)...")
            requestLocationSettings(context)

            val gpsStartTime = System.currentTimeMillis()
            var enabled = false
            while (System.currentTimeMillis() - gpsStartTime < 5000L) {
                delay(200)
                if (isLocationServiceEnabled(context)) {
                    enabled = true
                    Log.i(TAG, "STEP 2: User ACCEPTED / turned GPS ON!")
                    break
                }
            }

            if (!enabled) {
                // Short wait to re-check in case settings change takes a moment to propagate
                delay(300)
                if (isLocationServiceEnabled(context)) {
                    enabled = true
                    Log.i(TAG, "STEP 2: User turned GPS ON (confirmed on re-check)!")
                }
            }

            if (!enabled) {
                // Wait FULL 5 seconds if loop or re-check ended earlier
                val elapsedGps = System.currentTimeMillis() - gpsStartTime
                if (elapsedGps < 5000L) {
                    delay(5000L - elapsedGps)
                }
                Log.w(TAG, "STEP 2: User DENIED, CLOSED, or IGNORED GPS toggle request after FULL 5s. SEND SOS WITHOUT LOCATION.")
                return null
            }
        }

        // 3. CURRENT LOCATION ACQUISITION
        // After user accepts GPS Toggle request, NEVER treat location as unavailable immediately.
        // Re-check that Location Services / GPS is actually ON
        var gpsConfirmed = isLocationServiceEnabled(context)
        if (!gpsConfirmed) {
            for (i in 1..5) {
                delay(200)
                if (isLocationServiceEnabled(context)) {
                    gpsConfirmed = true
                    break
                }
            }
        }

        if (!gpsConfirmed) {
            Log.w(TAG, "STEP 3: Location Services / GPS re-check failed. Location Services is OFF. SEND SOS WITHOUT LOCATION.")
            return null
        }

        Log.i(TAG, "STEP 3: Location Services / GPS confirmed ON. Acquiring CURRENT GPS location...")
        val location = fetchFreshLocationWithFallback(context, 5000L)
        if (location != null) {
            Log.i(TAG, "STEP 3: CURRENT GPS coordinates obtained: ${location.latitude},${location.longitude}")
        } else {
            Log.w(TAG, "STEP 3: GPS coordinates unavailable within timeout. Proceeding WITHOUT location.")
        }
        return location
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Attempts to acquire a fresh current location from FusedLocationProviderClient within the specified timeout.
     * Does NOT return stale cached location.
     */
    private suspend fun fetchFreshLocationOnly(context: Context, timeoutMs: Long): Location? {
        if (!hasLocationPermission(context) || !isLocationServiceEnabled(context)) return null

        return try {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    val fusedClient: FusedLocationProviderClient =
                        LocationServices.getFusedLocationProviderClient(context)
                    val cancellationTokenSource = CancellationTokenSource()

                    try {
                        fusedClient.getCurrentLocation(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            cancellationTokenSource.token
                        ).addOnSuccessListener { location: Location? ->
                            if (continuation.isActive) continuation.resume(location)
                        }.addOnFailureListener {
                            if (continuation.isActive) continuation.resume(null)
                        }.addOnCanceledListener {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    } catch (e: SecurityException) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    continuation.invokeOnCancellation {
                        cancellationTokenSource.cancel()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchFreshLocationOnly failed: ${e.message}")
            null
        }
    }

    /**
     * Initial location retrieval for Guardian 1: tries fresh current location first;
     * falls back to lastLocation / LocationManager if fresh fix times out within the budget.
     */
    private suspend fun fetchFreshLocationWithFallback(context: Context, timeoutMs: Long): Location? {
        if (!hasLocationPermission(context) || !isLocationServiceEnabled(context)) return null

        // 1. Try high-accuracy fresh location
        val fresh = fetchFreshLocationOnly(context, timeoutMs)
        if (fresh != null) return fresh

        // 2. Resilient fallback to last known location (fused client or LocationManager)
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        try {
            val lastFused = suspendCancellableCoroutine<Location?> { cont ->
                try {
                    fusedClient.lastLocation
                        .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                } catch (e: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
            if (lastFused != null) {
                Log.d(TAG, "Fallback lastFused acquired: ${lastFused.latitude}, ${lastFused.longitude}")
                return lastFused
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fused lastLocation fallback error: ${e.message}")
        }

        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val gps = try { lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (e: SecurityException) { null }
            val net = try { lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (e: SecurityException) { null }
            val best = listOfNotNull(gps, net).maxByOrNull { it.time }
            if (best != null) {
                Log.d(TAG, "Fallback LocationManager acquired: ${best.latitude}, ${best.longitude}")
                return best
            }
        } catch (e: Exception) {
            Log.w(TAG, "LocationManager fallback error: ${e.message}")
        }

        return null
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
