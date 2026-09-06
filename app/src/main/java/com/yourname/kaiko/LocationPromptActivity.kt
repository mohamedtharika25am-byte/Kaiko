package com.yourname.kaiko

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

/**
 * Lightweight transparent activity to request Android Location Services / GPS Toggle
 * directly from the current screen (e.g. Home screen widget, 3x hardware press)
 * using the default native Google/Android location request dialog without opening MainActivity.
 */
class LocationPromptActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_CHECK_SETTINGS = 2002
        private const val TAG = "KAIKO_DEBUG"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable {
        if (!isFinishing && !isDestroyed) {
            Log.d(TAG, "LocationPromptActivity: Auto-dismissing after timeout.")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        // Auto-dismiss activity after 5.5 seconds as a safety limit
        handler.postDelayed(autoDismissRunnable, 5500L)
        promptLocationSettings()
    }

    private fun promptLocationSettings() {
        if (TriggerManager.isLocationServiceEnabled(this)) {
            Log.d(TAG, "LocationPromptActivity: GPS is already ON. Finishing.")
            finish()
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5000L
        ).build()
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)

        val client = LocationServices.getSettingsClient(this)
        client.checkLocationSettings(builder.build())
            .addOnSuccessListener {
                Log.d(TAG, "LocationPromptActivity: Location settings already satisfied.")
                finish()
            }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        Log.d(TAG, "LocationPromptActivity: Launching Google Play Services GPS resolution dialog...")
                        exception.startResolutionForResult(this, REQUEST_CHECK_SETTINGS)
                    } catch (e: Exception) {
                        Log.e(TAG, "LocationPromptActivity: Failed to start resolution: ${e.message}")
                        finish()
                    }
                } else {
                    Log.w(TAG, "LocationPromptActivity: ResolvableApiException not available. Opening location settings.")
                    try {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    } catch (ign: Exception) {}
                    finish()
                }
            }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            Log.d(TAG, "LocationPromptActivity: Resolution dialog returned resultCode=$resultCode")
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(autoDismissRunnable)
    }
}
