package com.yourname.kaiko

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.yourname.kaiko.databinding.ActivityMainBinding

/**
 * Onboarding and Configuration Activity for Kaiko.
 * Provides a minimal, focused UI to:
 * 1. Enter and persist the guardian's emergency phone number in SharedPreferences.
 * 2. Request necessary runtime permissions (SEND_SMS, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, POST_NOTIFICATIONS).
 * 3. Check and launch Accessibility Settings to enable the background hardware button trigger service.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences

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

        // Pre-populate existing guardian phone number if previously saved
        val savedPhone = sharedPreferences.getString(TriggerManager.KEY_GUARDIAN_PHONE, "")
        binding.etGuardianPhone.setText(savedPhone)

        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        // Refresh Accessibility Service status badge when returning to the app
        updateAccessibilityStatus()
    }

    private fun setupListeners() {
        // "Save & Enable" Button
        binding.btnSaveAndEnable.setOnClickListener {
            val phone = binding.etGuardianPhone.text.toString().trim()
            if (phone.isEmpty()) {
                binding.etGuardianPhone.error = getString(R.string.phone_empty_error)
                binding.etGuardianPhone.requestFocus()
                return@setOnClickListener
            }

            // Save phone number locally to SharedPreferences
            sharedPreferences.edit()
                .putString(TriggerManager.KEY_GUARDIAN_PHONE, phone)
                .apply()

            Toast.makeText(this, getString(R.string.saved_success_toast), Toast.LENGTH_SHORT).show()

            // Request runtime permissions
            requestAppPermissions()
        }

        // "Enable Accessibility Service" Button -> Direct intent to Android Accessibility Settings
        binding.btnOpenAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    /**
     * Requests required runtime permissions for SMS, Location, and Notifications.
     */
    private fun requestAppPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // Request POST_NOTIFICATIONS on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }

    /**
     * Checks whether KaikoAccessibilityService is currently active and enabled in system settings.
     */
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

    /**
     * Helper to verify if the given AccessibilityService class is enabled in Android Settings.
     */
    private fun isAccessibilityServiceEnabled(
        context: Context,
        serviceClass: Class<out AccessibilityService>
    ): Boolean {
        // 1. Check via AccessibilityManager
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

        // 2. Check via Settings.Secure as fallback
        val expectedComponentName = ComponentName(context, serviceClass).flattenToString()
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            if (componentNameString.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }

        return false
    }
}
