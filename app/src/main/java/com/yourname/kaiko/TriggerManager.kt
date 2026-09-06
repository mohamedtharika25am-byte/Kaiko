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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import kotlinx.coroutines.Job
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
 * Central Orchestrator & State Machine for Kaiko Emergency Operations (v1.3.0).
 * Features:
 * 1. Single central SOS pipeline for all triggers (App, 3x Volume, Normal Widget, Discreet Widget).
 * 2. Strict location hierarchy: Permission -> Android Location Services / GPS Toggle -> Network -> Live/Coordinate Fallback.
 * 3. 5-Second safety limit: SOS send is high priority, never blocked by location setup.
 * 4. Discreet widget silent execution (no popups, zero delay if permission/services off).
 * 5. Fresh location attempt for each guardian escalation step (G1, G2, G3) with explicit "Last known location" labeling.
 * 6. Active location session management stopped immediately upon "I'm Safe Now" or Test ACK.
 * 7. Duplicate SOS session protection across all triggers.
 * 8. Targeted resolution updates sent only to guardians contacted during the current session.
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
    private const val LOCATION_TIMEOUT_MS = 4000L // 4.0s for fresh fix attempt
    private const val LOCATION_SAFETY_TIMEOUT_MS = 5000L // 5.0s hard safety limit for entire location flow

    // Active Location Session Tracking
    private var activeLocationJob: Job? = null
    private var activeLocationTokenSource: CancellationTokenSource? = null


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
        // Section 14: Duplicate SOS Protection
        val currentState = getCurrentState(context)
        if (currentState.isActive()) {
            Log.w(TAG, "Duplicate SOS trigger blocked! An active SOS session ($currentState) is already in progress.")
            return
        }

        val triggerMode = normalizeTriggerSource(source)
        val triggerTimestamp = System.currentTimeMillis()
        val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(triggerTimestamp))
        val eventId = "KAIKO-" + UUID.randomUUID().toString().substring(0, 6).uppercase(Locale.getDefault())

        Log.d(TAG, "==================================================")
        Log.d(TAG, "[$formattedTime] EMERGENCY SOS TRIGGERED (Event ID: $eventId, Mode: '$triggerMode')")
        Log.d(TAG, "DEBUG_MODE: $DEBUG_MODE")

        // 1. Cancel any existing escalation timer, stop previous location session & clean up previous event data
        cancelEscalationTimer(context)
        stopActiveLocationSession()
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
        activeLocationJob = CoroutineScope(Dispatchers.IO).launch {
            val location = resolveInitialSosLocation(context, triggerMode, LOCATION_SAFETY_TIMEOUT_MS)

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
        val job = CoroutineScope(Dispatchers.IO).launch {
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
                // Section 8 & 9: Guardian 2 & 3 Location Flow
                // ALWAYS attempt to fetch a NEW current location (Max 3-5s)
                Log.d(TAG, "Guardian $guardianIndex Escalation: ALWAYS attempting fresh current location (timeout: ${LOCATION_TIMEOUT_MS}ms)...")
                val freshLocation: Location? = if (hasLocationPermission(context) && isLocationServiceEnabled(context)) {
                    fetchFreshLocationOnly(context, LOCATION_TIMEOUT_MS)
                } else {
                    null
                }

                if (freshLocation != null) {
                    // Priority 1: New location available -> Send NEW CURRENT location
                    coords = "${freshLocation.latitude},${freshLocation.longitude}"
                    prefs.edit()
                        .putString(KEY_LAST_COORDS, coords)
                        .putBoolean(KEY_HAS_PREVIOUS_LOCATION, true)
                        .apply()
                    locationStatus = LocationStatus.CURRENT
                    Log.i(TAG, "Guardian $guardianIndex: NEW Current location obtained: $coords")
                } else {
                    // Priority 2: New location unavailable, but previous location exists -> Send LAST KNOWN location
                    val hasPrev = prefs.getBoolean(KEY_HAS_PREVIOUS_LOCATION, false)
                    val prevCoords = prefs.getString(KEY_LAST_COORDS, null)

                    if (hasPrev && !prevCoords.isNullOrBlank()) {
                        coords = prevCoords
                        locationStatus = LocationStatus.LAST_KNOWN
                        Log.i(TAG, "Guardian $guardianIndex: Fresh location unavailable. Using LAST KNOWN location: $coords")
                    } else {
                        // Priority 3: No location has ever been obtained -> Send without location
                        coords = null
                        locationStatus = LocationStatus.UNAVAILABLE
                        Log.i(TAG, "Guardian $guardianIndex: No location has ever been obtained. Sending without location.")
                    }
                }
            }

            dispatchToGuardianInternal(context, guardianIndex, locationStatus, coords, isRetry)
        }
        activeLocationJob = job
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
     * Resolution message for Section 12: "I'M SAFE NOW".
     */
    fun buildSafeResolutionMessage(): String {
        return "🟢 KAIKO UPDATE: The user is safe. Escalation stopped."
    }

    /**
     * Test Update message for Section 13: Simulated Guardian ACK.
     */
    fun buildTestAckResolutionMessage(): String {
        return "🧪 KAIKO TEST UPDATE: Emergency test acknowledged. Escalation stopped."
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
        stopActiveLocationSession()
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

        // Clean up event state so next SOS starts fresh
        clearAlertedGuardians(context)
    }

    /**
     * Section 13: Simulated Guardian Acknowledgement [TEST ONLY]:
     * 1. Stop pending escalation
     * 2. Stop active test location session
     * 3. Resolve current test event
     * 4. Find guardians already alerted during THIS event
     * 5. Send TEST update ONLY to those guardians
     * 6. Clean up event state
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
        stopActiveLocationSession()
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

        // Clean up event state
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
     * Checks if system location services (GPS or Network provider) are enabled.
     * Terminology: ANDROID LOCATION SERVICES / GPS TOGGLE
     */
    fun isLocationServiceEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    /**
     * Checks if cellular or Wi-Fi network connectivity is currently available.
     */
    fun isNetworkConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNet = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNet) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Attempts to obtain a Google Maps real-time Location Sharing link.
     * Per Section 5 of specification:
     * Evaluates programmatic availability; as Google Maps has no public headless/silent API
     * on Android without user GUI interaction, this safely returns null to trigger immediate coordinate fallback.
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun attemptGoogleMapsLiveLocationLink(context: Context): String? {
        Log.d(TAG, "Attempting Google Maps real-time Location Sharing link...")
        Log.i(TAG, "Live location headless API unsupported on Android platform without user GUI intervention. Cleanly falling back to coordinate location.")
        return null
    }

    /**
     * Immediately stops any active live-location fetching session or coroutine job.
     */
    fun stopActiveLocationSession() {
        try {
            activeLocationTokenSource?.cancel()
            activeLocationJob?.cancel()
            activeLocationJob = null
            activeLocationTokenSource = null
            Log.d(TAG, "Active location session stopped.")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping active location session: ${e.message}")
        }
    }

    private fun broadcastLocationPermissionRequired(context: Context) {
        val intent = Intent(ACTION_REQUEST_LOCATION_PERMISSION).setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    private fun broadcastLocationSettingsRequired(context: Context) {
        val intent = Intent(ACTION_REQUEST_LOCATION_SETTINGS).setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    /**
     * Executes the strict SOS Location Decision Hierarchy (v1.3.0):
     *
     * KAIKO LOCATION PERMISSION?
     * ├── ON
     * │   ANDROID LOCATION SERVICES / GPS TOGGLE?
     * │   ├── YES
     * │   │   NETWORK ON?
     * │   │   ├── YES -> TRY GOOGLE MAPS LIVE LOCATION LINK (Headless API unavailable -> Fallback to Current Coords)
     * │   │   └── NO  -> GET CURRENT LOCATION -> SEND SOS + COORDINATE GOOGLE MAPS URL
     * │   └── NO  -> REQUEST ANDROID LOCATION SERVICES / GPS TOGGLE (Wait max 5s)
     * └── OFF -> REQUEST KAIKO LOCATION PERMISSION (Wait max 5s)
     *
     * Strict 5-Second Safety Limit: SOS is never blocked indefinitely.
     */
    suspend fun resolveInitialSosLocation(
        context: Context,
        triggerMode: String,
        safetyTimeoutMs: Long = LOCATION_SAFETY_TIMEOUT_MS
    ): Location? {
        val startTime = System.currentTimeMillis()

        fun remainingTime(): Long {
            val elapsed = System.currentTimeMillis() - startTime
            return (safetyTimeoutMs - elapsed).coerceAtLeast(0L)
        }

        Log.d(TAG, "Starting SOS location decision flow (Max safety timeout: ${safetyTimeoutMs}ms)...")

        // 1. KAIKO LOCATION PERMISSION?
        var permissionGranted = hasLocationPermission(context)
        if (!permissionGranted) {
            Log.w(TAG, "KAIKO LOCATION PERMISSION: OFF")
            if (triggerMode == TRIGGER_DISCREET_SAFETY) {
                // Discreet Widget: Strict Silent Behaviour -> proceed without location immediately
                Log.d(TAG, "Discreet Safety Trigger: Permission OFF -> proceeding without location immediately.")
                return null
            }

            // Prompt permission request via broadcast
            broadcastLocationPermissionRequired(context)

            // Wait up to remaining time (max 5s safety limit)
            while (remainingTime() > 0L) {
                delay(250)
                if (hasLocationPermission(context)) {
                    permissionGranted = true
                    Log.i(TAG, "User granted Kaiko Location Permission!")
                    break
                }
            }

            if (!permissionGranted) {
                Log.w(TAG, "User ignored or denied Kaiko location permission (5s safety limit reached). SEND SOS WITHOUT LOCATION.")
                return null
            }
        } else {
            Log.i(TAG, "KAIKO LOCATION PERMISSION: ON")
        }

        // CRITICAL SPEC RULE: Permission = ON must NEVER skip directly to NETWORK ON?
        // Must ALWAYS check ANDROID LOCATION SERVICES / GPS TOGGLE first!

        // 2. ANDROID LOCATION SERVICES / GPS TOGGLE?
        var locationServicesEnabled = isLocationServiceEnabled(context)
        if (!locationServicesEnabled) {
            Log.w(TAG, "ANDROID LOCATION SERVICES / GPS TOGGLE: OFF")
            if (triggerMode == TRIGGER_DISCREET_SAFETY) {
                // Discreet Widget: Strict Silent Behaviour -> proceed without location immediately
                Log.d(TAG, "Discreet Safety Trigger: GPS Toggle OFF -> proceeding without location immediately.")
                return null
            }

            // Prompt Android Location Services / GPS Toggle via broadcast
            broadcastLocationSettingsRequired(context)

            // Wait up to remaining time (max 5s safety limit)
            while (remainingTime() > 0L) {
                delay(250)
                if (isLocationServiceEnabled(context)) {
                    locationServicesEnabled = true
                    Log.i(TAG, "User enabled Android Location Services / GPS Toggle!")
                    break
                }
            }

            if (!locationServicesEnabled) {
                Log.w(TAG, "User ignored/declined ANDROID LOCATION SERVICES / GPS TOGGLE (5s safety limit reached). SEND SOS WITHOUT LOCATION.")
                return null
            }
        } else {
            Log.i(TAG, "ANDROID LOCATION SERVICES / GPS TOGGLE: ON")
        }

        // 3. NETWORK ON?
        val networkOn = isNetworkConnected(context)
        Log.i(TAG, "NETWORK: " + (if (networkOn) "ON" else "OFF"))

        val fetchBudget = remainingTime().coerceAtLeast(1500L)

        if (networkOn) {
            // Section 5: Attempt Google Maps real-time Location Sharing link
            val liveLink = attemptGoogleMapsLiveLocationLink(context)
            if (liveLink != null) {
                Log.i(TAG, "Live location link acquired: $liveLink")
            } else {
                Log.i(TAG, "LIVE LOCATION ATTEMPT FAILS -> FALL BACK TO CURRENT COORDINATES -> SEND NORMAL GOOGLE MAPS COORDINATE URL")
            }
            return fetchFreshLocationWithFallback(context, fetchBudget)
        } else {
            // Section 7: Network OFF does NOT automatically mean GPS/location is unavailable!
            Log.i(TAG, "Network is OFF: Attempting to obtain coordinates from device GPS provider...")
            return fetchFreshLocationWithFallback(context, fetchBudget)
        }
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
                    activeLocationTokenSource = cancellationTokenSource

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
        } finally {
            activeLocationTokenSource = null
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
