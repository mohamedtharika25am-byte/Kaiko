package com.yourname.kaiko

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.yourname.kaiko.databinding.ActivityMainBinding

/**
 * Onboarding and Configuration Activity for Kaiko (v0.0.2).
 * Supports:
 * 1. Multi-Guardian Configuration (Guardian 1 mandatory, Guardians 2 & 3 optional, Final contact optional).
 * 2. Escalation Delay Configuration (30s, 60s, 120s for testing).
 * 3. Real-time Active Emergency banner with "I'm Safe Now" and "Simulate Ack [TEST]" actions.
 * 4. Runtime permission requests and Accessibility Service status check.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences

    // Receiver to update UI live when SOS state changes
    private val stateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateActiveSosBanner()
        }
    }

    // Permission request launcher
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val smsGranted = permissions[Manifest.permission.SEND_SMS] == true

        if (locationGranted && smsGranted) {
            Toast.makeText(this, "Permissions granted successfully!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "Note: SMS and Location permissions are required for full emergency functionality.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences(TriggerManager.PREFS_NAME, Context.MODE_PRIVATE)

        loadSavedConfiguration()
        setupListeners()
        updateActiveSosBanner()
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
        updateActiveSosBanner()

        // Register state change receiver
        val filter = IntentFilter(TriggerManager.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stateChangeReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(stateChangeReceiver)
        } catch (e: Exception) {
            // Ignored if not registered
        }
    }

    private fun loadSavedConfiguration() {
        // Guardian 1 (Preserves existing data from KEY_GUARDIAN_PHONE)
        val g1 = sharedPreferences.getString(TriggerManager.KEY_GUARDIAN_PHONE, "")
        binding.etGuardian1.setText(g1)

        // Guardian 2
        val g2 = sharedPreferences.getString(TriggerManager.KEY_GUARDIAN_2_PHONE, "")
        binding.etGuardian2.setText(g2)

        // Guardian 3
        val g3 = sharedPreferences.getString(TriggerManager.KEY_GUARDIAN_3_PHONE, "")
        binding.etGuardian3.setText(g3)

        // Final Helpline
        val helpline = sharedPreferences.getString(TriggerManager.KEY_FINAL_HELPLINE_PHONE, "")
        binding.etHelpline.setText(helpline)

        // Escalation Delay
        val delay = sharedPreferences.getLong(
            TriggerManager.KEY_ESCALATION_DELAY_SECONDS,
            TriggerManager.DEFAULT_ESCALATION_DELAY_SECONDS
        )
        when (delay) {
            30L -> binding.rbDelay30.isChecked = true
            120L -> binding.rbDelay120.isChecked = true
            else -> binding.rbDelay60.isChecked = true
        }
    }

    private fun setupListeners() {
        // Save & Enable Button
        binding.btnSaveAndEnable.setOnClickListener {
            val g1 = binding.etGuardian1.text.toString().trim()
            val g2 = binding.etGuardian2.text.toString().trim()
            val g3 = binding.etGuardian3.text.toString().trim()
            val helpline = binding.etHelpline.text.toString().trim()

            // 1. Validate Guardian 1 (Mandatory)
            if (g1.isEmpty()) {
                binding.etGuardian1.error = "Guardian 1 phone number is required"
                binding.etGuardian1.requestFocus()
                return@setOnClickListener
            }
            if (!isValidPhoneNumber(g1)) {
                binding.etGuardian1.error = "Please enter a valid phone number"
                binding.etGuardian1.requestFocus()
                return@setOnClickListener
            }

            // 2. Validate optional guardians
            if (g2.isNotEmpty() && !isValidPhoneNumber(g2)) {
                binding.etGuardian2.error = "Please enter a valid phone number"
                binding.etGuardian2.requestFocus()
                return@setOnClickListener
            }
            if (g3.isNotEmpty() && !isValidPhoneNumber(g3)) {
                binding.etGuardian3.error = "Please enter a valid phone number"
                binding.etGuardian3.requestFocus()
                return@setOnClickListener
            }

            // 3. Prevent duplicates
            val contactList = listOfNotNull(
                g1.ifEmpty { null },
                g2.ifEmpty { null },
                g3.ifEmpty { null }
            )
            if (contactList.size != contactList.distinct().size) {
                Toast.makeText(this, "Error: Duplicate guardian phone numbers detected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 4. Save Escalation Delay
            val delay = when {
                binding.rbDelay30.isChecked -> 30L
                binding.rbDelay120.isChecked -> 120L
                else -> 60L
            }

            sharedPreferences.edit()
                .putString(TriggerManager.KEY_GUARDIAN_PHONE, g1)
                .putString(TriggerManager.KEY_GUARDIAN_2_PHONE, g2)
                .putString(TriggerManager.KEY_GUARDIAN_3_PHONE, g3)
                .putString(TriggerManager.KEY_FINAL_HELPLINE_PHONE, helpline)
                .putLong(TriggerManager.KEY_ESCALATION_DELAY_SECONDS, delay)
                .apply()

            Toast.makeText(this, "Configuration saved successfully!", Toast.LENGTH_SHORT).show()
            requestAppPermissions()
        }

        // Accessibility Settings Button
        binding.btnOpenAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        // "I'm Safe Now" Button
        binding.btnSafeNow.setOnClickListener {
            TriggerManager.markUserSafe(this)
            updateActiveSosBanner()
            Toast.makeText(this, "Marked safe. Escalation stopped.", Toast.LENGTH_SHORT).show()
        }

        // "Simulate Guardian Ack [TEST ONLY]" Button
        binding.btnSimulateAck.setOnClickListener {
            TriggerManager.simulateGuardianAck(this)
            updateActiveSosBanner()
            Toast.makeText(this, "Simulated Guardian Acknowledgement recorded.", Toast.LENGTH_SHORT).show()
        }

        // Manual Test SOS Trigger
        binding.btnManualTestTrigger.setOnClickListener {
            TriggerManager.fireAlert(this, "manual_app_test")
            updateActiveSosBanner()
        }
    }

    private fun updateActiveSosBanner() {
        val currentState = TriggerManager.getCurrentState(this)
        if (currentState.isActive()) {
            binding.cardActiveSos.visibility = View.VISIBLE
            binding.tvActiveSosStatus.text = "Status: ${currentState.displayName}"
        } else {
            binding.cardActiveSos.visibility = View.GONE
        }
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        val cleaned = phone.replace(Regex("[^0-9+]"), "")
        return cleaned.length >= 7 && (phone.startsWith("+") || phone.all { it.isDigit() || it.isWhitespace() || it == '-' || it == '(' || it == ')' })
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }

    private fun updateAccessibilityStatus() {
        val isEnabled = isAccessibilityServiceEnabled(this, KaikoAccessibilityService::class.java)
        if (isEnabled) {
            binding.tvAccessibilityStatus.text = getString(R.string.status_accessibility_enabled)
            binding.tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.status_enabled))
        } else {
            binding.tvAccessibilityStatus.text = getString(R.string.status_accessibility_disabled)
            binding.tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.status_disabled))
        }
    }

    private fun isAccessibilityServiceEnabled(
        context: Context,
        serviceClass: Class<out AccessibilityService>
    ): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val runningServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        if (runningServices != null) {
            for (service in runningServices) {
                val serviceInfo = service.resolveInfo?.serviceInfo
                if (serviceInfo != null &&
                    serviceInfo.packageName == context.packageName &&
                    serviceInfo.name == serviceClass.name
                ) {
                    return true
                }
            }
        }
        return false
    }
}
