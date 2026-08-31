package com.yourname.kaiko

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import java.util.ArrayDeque

/**
 * Background Accessibility Service that listens for hardware button trigger patterns.
 * Specifically monitors triple presses of KEYCODE_VOLUME_DOWN within a 2000ms sliding window.
 */
class KaikoAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "KAIKO_DEBUG"
        private const val SLIDING_WINDOW_MS = 2000L  // 2 seconds window for 3 presses
        private const val REQUIRED_PRESS_COUNT = 3   // 3 presses of Volume Down
        private const val COOLDOWN_PERIOD_MS = 5000L // 5 seconds cooldown after trigger
    }

    // Sliding window of timestamps for Volume Down key-down events
    private val pressTimestamps = ArrayDeque<Long>()

    // Timestamp of the last successful trigger to enforce cooldown
    private var lastTriggerTimestamp: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "KaikoAccessibilityService connected and initialized.")

        // Configure service flags programmatically as a safeguard alongside XML config
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info
    }

    /**
     * Intercepts key events before they reach the system.
     * Note: We return false so we DO NOT consume/block normal system volume down adjustments.
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

        // Listen only to Volume Down key down events (ignore repeat events from holding the button)
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0) {
                handleVolumeDownPress()
            }
        }

        // Return false so normal volume control and other system behaviors are not blocked
        return false
    }

    /**
     * Evaluates volume down key press in the sliding window.
     */
    @Synchronized
    private fun handleVolumeDownPress() {
        val now = System.currentTimeMillis()

        // 1. Check cooldown period
        if (now - lastTriggerTimestamp < COOLDOWN_PERIOD_MS) {
            val remainingCooldown = (COOLDOWN_PERIOD_MS - (now - lastTriggerTimestamp)) / 1000.0
            Log.d(TAG, "Volume Down press ignored during cooldown (%.1fs remaining)".format(remainingCooldown))
            return
        }

        // 2. Evict timestamps outside the 2000ms sliding window
        while (pressTimestamps.isNotEmpty() && (now - pressTimestamps.peekFirst() > SLIDING_WINDOW_MS)) {
            pressTimestamps.pollFirst()
        }

        // 3. Record current press
        pressTimestamps.addLast(now)
        Log.d(TAG, "Volume Down pressed: count = ${pressTimestamps.size} / $REQUIRED_PRESS_COUNT in sliding window")

        // 4. Check if trigger condition met (3 presses within 2000ms)
        if (pressTimestamps.size >= REQUIRED_PRESS_COUNT) {
            Log.d(TAG, "--> TRIGGER CONDITION MET: 3 Volume Down presses in < 2000ms. Dispatching alert!")
            lastTriggerTimestamp = now
            pressTimestamps.clear()

            // Fire shared trigger action
            TriggerManager.fireAlert(applicationContext, "volume_button")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No window/content accessibility processing needed
    }

    override fun onInterrupt() {
        Log.w(TAG, "KaikoAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "KaikoAccessibilityService destroyed.")
    }
}
