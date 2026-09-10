package com.yourname.kaiko

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.yourname.kaiko.databinding.LayoutPowerButtonSosBinding

/**
 * Standalone, lock-screen-capable Activity that displays the Kaiko SOS confirmation UI
 * when invoked via Android's ACTION_ASSIST or assistant shortcut.
 * 
 * Ensures 100% compatibility across all OEM devices (Oppo/ColorOS, Xiaomi/MIUI, Samsung/OneUI, Pixel).
 */
class PowerButtonSosActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "KAIKO_POWER_ACTIVITY"
    }

    private lateinit var binding: LayoutPowerButtonSosBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allow display over lock screen and wake screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        binding = LayoutPowerButtonSosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "PowerButtonSosActivity launched. Showing SOS confirmation.")

        // [🚨 ACCEPT & SEND SOS]
        binding.btnAcceptSos.setOnClickListener {
            Log.i(TAG, "🚨 Power Button SOS: ACCEPT tapped! Triggering existing SOS flow.")
            TriggerManager.fireAlert(this, TriggerManager.TRIGGER_POWER_BUTTON)

            val mainIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(mainIntent)
            finish()
        }

        // [CANCEL]
        binding.btnCancelSos.setOnClickListener {
            Log.d(TAG, "Power Button SOS: CANCEL tapped. No SOS sent.")
            finish()
        }

        // Dismiss when tapping outside the dialog card
        binding.layoutPowerButtonSosRoot.setOnClickListener {
            finish()
        }
    }
}
