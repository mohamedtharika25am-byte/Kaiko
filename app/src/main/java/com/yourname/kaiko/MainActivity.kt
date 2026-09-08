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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.yourname.kaiko.databinding.ActivityMainBinding

/**
 * Main Activity for Kaiko (v1.4.0).
 * Features:
 * 1. Top large circular SOS trigger button.
 * 2. Active SOS controls with 4 dedicated actions:
 *    - 🟢 I'M SAFE
 *    - 🚨 EMERGENCY
 *    - ⚠️ MISTOUCHED (with confirmation)
 *    - 🧪 TEST (stop escalation test only, no SMS)
 * 3. Compact System Status (Location, Accessibility, Guardians).
 * 4. First-time accessibility onboarding.
 * 5. Direct Android Accessibility settings management.
 * 6. Top-right Info icon launching dedicated Kaiko User Guide.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences

    // Receiver to update UI live when SOS state changes or location setup is requested
    private val stateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                TriggerManager.ACTION_STATE_CHANGED -> {
                    updateActiveSosBanner()
                    updateSystemStatus()
                }
                TriggerManager.ACTION_REQUEST_LOCATION_PERMISSION -> requestAppPermissions()
                TriggerManager.ACTION_REQUEST_LOCATION_SETTINGS -> promptLocationServices()
            }
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
        updateSystemStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences(TriggerManager.PREFS_NAME, Context.MODE_PRIVATE)

        setupListeners()
        updateActiveSosBanner()
        updateSystemStatus()
        checkAccessibilityOnboarding()
        handleIncomingAction(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingAction(intent)
    }

    private fun handleIncomingAction(intent: Intent?) {
        when (intent?.action) {
            TriggerManager.ACTION_REQUEST_LOCATION_PERMISSION -> requestAppPermissions()
            TriggerManager.ACTION_REQUEST_LOCATION_SETTINGS -> promptLocationServices()
        }
    }

    override fun onResume() {
        super.onResume()
        updateSystemStatus()
        updateActiveSosBanner()

        // Register state change receiver and location setup actions
        val filter = IntentFilter().apply {
            addAction(TriggerManager.ACTION_STATE_CHANGED)
            addAction(TriggerManager.ACTION_REQUEST_LOCATION_PERMISSION)
            addAction(TriggerManager.ACTION_REQUEST_LOCATION_SETTINGS)
        }
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


    private fun setupListeners() {
        // 1. Top Large Circular SOS Button
        binding.btnMainSos.setOnClickListener {
            TriggerManager.fireAlert(this, TriggerManager.TRIGGER_MANUAL_APP)
            updateActiveSosBanner()
        }

        // 2. Info Icon -> User Guide Screen
        binding.btnUserGuide.setOnClickListener {
            startActivity(Intent(this, UserGuideActivity::class.java))
        }

        // 3. System Status: Location Permission item
        binding.itemLocationPermission.setOnClickListener {
            if (!TriggerManager.hasLocationPermission(this)) {
                requestAppPermissions()
            } else {
                Toast.makeText(this, "Location Permission is already ON", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. System Status: Location Toggle item
        binding.itemLocationToggle.setOnClickListener {
            if (!TriggerManager.isLocationServiceEnabled(this)) {
                promptLocationServices()
            } else {
                Toast.makeText(this, "Location Services / GPS Toggle is ON", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. System Status: Accessibility item
        binding.itemAccessibilityStatus.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // 5. System Status: Guardians item -> Open Manage Guardians screen
        binding.itemGuardiansStatus.setOnClickListener {
            startActivity(Intent(this, ManageGuardiansActivity::class.java))
        }

        // 6. Accessibility Management Button
        binding.btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // 7. Active SOS Button 1: 🟢 I'M SAFE
        binding.btnSafeNow.setOnClickListener {
            TriggerManager.markUserSafe(this)
            updateActiveSosBanner()
            Toast.makeText(this, "Marked safe. Escalation stopped.", Toast.LENGTH_SHORT).show()
        }

        // 8. Active SOS Button 2: 🚨 EMERGENCY (Immediate Escalation)
        binding.btnEscalateEmergency.setOnClickListener {
            TriggerManager.escalateImmediately(this)
            updateActiveSosBanner()
            Toast.makeText(this, "Escalating to next guardian immediately.", Toast.LENGTH_SHORT).show()
        }

        // 9. Active SOS Button 3: ⚠️ MISTOUCHED (Confirmation Dialog)
        binding.btnMistouched.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Mistouched?")
                .setMessage("This will stop the current SOS escalation.")
                .setPositiveButton("YES, STOP") { _, _ ->
                    TriggerManager.handleMistouched(this)
                    updateActiveSosBanner()
                    Toast.makeText(this, "SOS stopped: Mistouched update sent.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("KEEP SOS ACTIVE") { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(false)
                .show()
        }

        // 10. Active SOS Button 4: 🧪 TEST (Stop Escalation Test Only, No SMS)
        binding.btnTestOnly.setOnClickListener {
            TriggerManager.stopEscalationTestOnly(this)
            updateActiveSosBanner()
            Toast.makeText(this, "Test stopped. No SMS sent.", Toast.LENGTH_SHORT).show()
        }

        // 11. Home Button: 👥 MANAGE GUARDIANS (v1.5.1)
        binding.btnManageGuardiansHome.setOnClickListener {
            startActivity(Intent(this, ManageGuardiansActivity::class.java))
        }
    }

    /**
     * Section 4: First-time Accessibility Onboarding.
     * Non-forcing dialog explaining requirement with [ENABLE ACCESSIBILITY] and [NOT NOW].
     */
    private fun checkAccessibilityOnboarding() {
        val hasShown = sharedPreferences.getBoolean("KEY_ACCESSIBILITY_ONBOARDING_SHOWN", false)
        val isEnabled = isAccessibilityServiceEnabled(this, KaikoAccessibilityService::class.java)
        if (!hasShown && !isEnabled) {
            sharedPreferences.edit().putBoolean("KEY_ACCESSIBILITY_ONBOARDING_SHOWN", true).apply()
            AlertDialog.Builder(this)
                .setTitle("Accessibility Setup")
                .setMessage("Accessibility is required for the 3× Press emergency trigger.")
                .setPositiveButton("ENABLE ACCESSIBILITY") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("NOT NOW") { dialog, _ ->
                    dialog.dismiss()
                    updateSystemStatus()
                }
                .setCancelable(true)
                .show()
        }
    }

    /**
     * Updates the 3 System Status indicators:
     * 1. Location: ON / OFF
     * 2. Accessibility: ON / OFF
     * 3. Guardians: X guardians configured
     */
    private fun updateSystemStatus() {
        // 1. Location Permission
        val locPerm = TriggerManager.hasLocationPermission(this)
        if (locPerm) {
            binding.tvLocationPermissionValue.text = "ON"
            binding.tvLocationPermissionValue.setTextColor(ContextCompat.getColor(this, R.color.status_enabled))
        } else {
            binding.tvLocationPermissionValue.text = "OFF"
            binding.tvLocationPermissionValue.setTextColor(ContextCompat.getColor(this, R.color.status_disabled))
        }

        // 2. Location Toggle (GPS)
        val gpsEnabled = TriggerManager.isLocationServiceEnabled(this)
        if (gpsEnabled) {
            binding.tvLocationToggleValue.text = "ON"
            binding.tvLocationToggleValue.setTextColor(ContextCompat.getColor(this, R.color.status_enabled))
        } else {
            binding.tvLocationToggleValue.text = "OFF"
            binding.tvLocationToggleValue.setTextColor(ContextCompat.getColor(this, R.color.status_disabled))
        }

        // 2. Accessibility Status
        val accOn = isAccessibilityServiceEnabled(this, KaikoAccessibilityService::class.java)
        if (accOn) {
            binding.tvAccessibilityStatusValue.text = "ON"
            binding.tvAccessibilityStatusValue.setTextColor(ContextCompat.getColor(this, R.color.status_enabled))
        } else {
            binding.tvAccessibilityStatusValue.text = "OFF"
            binding.tvAccessibilityStatusValue.setTextColor(ContextCompat.getColor(this, R.color.status_disabled))
        }

        // 3. Guardians Count Status
        val guardianCount = TriggerManager.getConfiguredGuardiansCount(this)
        val countText = "$guardianCount ${if (guardianCount == 1) "guardian" else "guardians"} configured"
        binding.tvGuardiansStatusValue.text = countText
    }

    /**
     * Shows 4 Active SOS buttons ONLY when an actual SOS trigger action is active.
     */
    private fun updateActiveSosBanner() {
        val currentState = TriggerManager.getCurrentState(this)
        if (currentState.isActive()) {
            binding.cardActiveSos.visibility = View.VISIBLE
            binding.tvActiveSosStatus.text = "Status: ${currentState.displayName}"
        } else {
            binding.cardActiveSos.visibility = View.GONE
        }
    }

    /**
     * Prompts the user to enable system location services (GPS toggle).
     */
    fun promptLocationServices() {
        if (TriggerManager.isLocationServiceEnabled(this)) {
            return
        }
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5000L
        ).build()
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
        val client = LocationServices.getSettingsClient(this)
        client.checkLocationSettings(builder.build())
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        exception.startResolutionForResult(this, 1002)
                    } catch (e: Exception) {
                        try {
                            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        } catch (ign: Exception) {}
                    }
                } else {
                    try {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    } catch (ign: Exception) {}
                }
            }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1002 && resultCode == RESULT_OK) {
            Toast.makeText(this, "Location services enabled", Toast.LENGTH_SHORT).show()
            updateSystemStatus()
        }
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        val cleaned = phone.replace(Regex("[^0-9+]"), "")
        return cleaned.length >= 7 && (phone.startsWith("+") || phone.all { it.isDigit() || it.isWhitespace() || it == '-' || it == '(' || it == ')' })
    }

    private fun requestAppPermissions() {
        if (TriggerManager.hasLocationPermission(this)) {
            return
        }
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
