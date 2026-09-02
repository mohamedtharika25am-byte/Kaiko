package com.yourname.kaiko

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
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
import kotlin.coroutines.resume

/**
 * Shared Trigger Action Manager for Kaiko.
 * Handles:
 * 1. High-accuracy location retrieval via FusedLocationProviderClient (with 3-second timeout).
 * 2. Composing and dispatching emergency SMS to the configured guardian.
 * 3. Posting a high-priority system confirmation notification (with coordinates).
 * 4. Comprehensive logging to Logcat under tag "KAIKO_DEBUG".
 * 5. Debug Mode toggle for safe device testing without incurring SMS charges.
 */
object TriggerManager {

    // =========================================================================
    // 6. DEBUG MODE TOGGLE
    // =========================================================================
    // Set to true for safe testing (no real SMS sent).
    // Set to false to send actual SMS alerts.
    const val DEBUG_MODE = false

    // =========================================================================
    // CONSTANTS
    // =========================================================================
    const val TAG = "KAIKO_DEBUG"
    const val PREFS_NAME = "kaiko_preferences"
    const val KEY_GUARDIAN_PHONE = "guardian_phone_number"
    private const val NOTIFICATION_CHANNEL_ID = "kaiko_emergency_alerts"
    private const val NOTIFICATION_ID = 1001
    private const val LOCATION_TIMEOUT_MS = 3000L

    /**
     * Shared trigger execution method called by both KaikoAccessibilityService (hardware button)
     * and KaikoWidgetProvider (home screen widget).
     *
     * @param context Application or component Context
     * @param source Trigger origin ("volume_button" or "widget")
     */
    fun fireAlert(context: Context, source: String = "unknown") {
        val triggerTimestamp = System.currentTimeMillis()
        val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(triggerTimestamp))

        Log.d(TAG, "--------------------------------------------------")
        Log.d(TAG, "[$formattedTime] EMERGENCY TRIGGER ACTIVATED from source: '$source'")
        Log.d(TAG, "DEBUG_MODE is currently: $DEBUG_MODE")

        // Execute asynchronous location retrieval, SMS dispatch, and notification posting on IO dispatcher
        CoroutineScope(Dispatchers.IO).launch {
            // 1. Fetch location with 3-second timeout
            val location: Location? = fetchCurrentLocation(context)
            val locationSuccess = location != null
            val coordsText: String
            val smsText: String

            if (location != null) {
                val lat = location.latitude
                val lng = location.longitude
                coordsText = "$lat, $lng"
                // Compose SMS text with coordinates and Google Maps link
                smsText = "I need help. My location: $lat, $lng https://maps.google.com/?q=$lat,$lng"
                Log.d(TAG, "Location acquired successfully: $coordsText (accuracy: ${location.accuracy}m)")
            } else {
                coordsText = "Location unavailable"
                smsText = "I need help. Location unavailable."
                Log.w(TAG, "Location acquisition failed or timed out (${LOCATION_TIMEOUT_MS}ms limit).")
            }

            // 2. Retrieve guardian phone number from SharedPreferences
            val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val guardianPhone: String? = prefs.getString(KEY_GUARDIAN_PHONE, null)

            // 3. Send SMS (or simulate in Debug Mode)
            val smsResultStatus: String = handleSmsDispatch(context, guardianPhone, smsText)

            // 4. Post System Notification for visual confirmation
            postNotification(
                context = context,
                source = source,
                coordsText = coordsText,
                smsStatus = smsResultStatus,
                formattedTime = formattedTime
            )

            // 5. Structured Debug Logging
            Log.d(
                TAG,
                "Trigger Event Summary: timestamp=$triggerTimestamp ($formattedTime), " +
                        "source=$source, locationSuccess=$locationSuccess, " +
                        "coords=$coordsText, smsSuccess=$smsResultStatus"
            )
            Log.d(TAG, "--------------------------------------------------")
        }
    }

    /**
     * Fetches the current high-accuracy device location using FusedLocationProviderClient
     * wrapped with a 3-second timeout.
     */
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
            Log.e(TAG, "Location permission NOT granted. Cannot query FusedLocationProviderClient.")
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
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }.addOnFailureListener { exception ->
                        Log.e(TAG, "getCurrentLocation failed: ${exception.message}", exception)
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }.addOnCanceledListener {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }

                    continuation.invokeOnCancellation {
                        cancellationTokenSource.cancel()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during location retrieval: ${e.message}", e)
            null
        }
    }

    /**
     * Handles SMS dispatch based on DEBUG_MODE and permission state.
     */
    private fun handleSmsDispatch(context: Context, guardianPhone: String?, smsText: String): String {
        if (guardianPhone.isNullOrBlank()) {
            val err = "FAILED (No guardian phone number configured in SharedPreferences)"
            Log.e(TAG, err)
            return err
        }

        if (DEBUG_MODE) {
            val debugMsg = "SKIPPED_DEBUG_MODE (Simulated SMS to '$guardianPhone': \"$smsText\")"
            Log.i(TAG, "[DEBUG MODE ACTIVE] Would have sent SMS to $guardianPhone: \"$smsText\"")
            return debugMsg
        }

        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasSmsPermission) {
            val err = "FAILED (SEND_SMS runtime permission not granted)"
            Log.e(TAG, err)
            return err
        }

        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            smsManager.sendTextMessage(guardianPhone, null, smsText, null, null)
            Log.i(TAG, "REAL SMS successfully dispatched to $guardianPhone: \"$smsText\"")
            "SUCCESS (SMS sent to $guardianPhone)"
        } catch (e: Exception) {
            val err = "FAILED (Exception: ${e.localizedMessage})"
            Log.e(TAG, "Error sending SMS: ${e.message}", e)
            err
        }
    }

    /**
     * Creates notification channel (API 26+) and posts confirmation notification.
     */
    private fun postNotification(
        context: Context,
        source: String,
        coordsText: String,
        smsStatus: String,
        formattedTime: String
    ) {
        createNotificationChannel(context)

        val title = "Help alert sent"
        val shortContent = "Coordinates: $coordsText"
        val bigText = StringBuilder()
            .append("Emergency trigger fired via: ").append(source.replace("_", " ").uppercase(Locale.getDefault())).append("\n")
            .append("Time: ").append(formattedTime).append("\n")
            .append("Location: ").append(coordsText).append("\n")
            .append("SMS Status: ").append(if (DEBUG_MODE) "Simulated (DEBUG_MODE=true)" else smsStatus)
            .toString()

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(shortContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(NOTIFICATION_ID, notification)
                Log.d(TAG, "System notification posted successfully.")
            } else {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Notification omitted.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not post notification: ${e.message}", e)
        }
    }

    /**
     * Initializes the notification channel on Android 8.0+ (API 26+).
     */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Emergency Alerts"
            val descriptionText = "Shows status notifications when Kaiko emergency triggers fire."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
