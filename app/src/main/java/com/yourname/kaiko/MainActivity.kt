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
 * Main Activity (Home / Emergency Page) for Kaiko (v1.8.0).
 * 
 * Features:
 * 1. Prominent centered circular SOS button.
 * 2. Active SOS banner and controls (🟢 I'M SAFE, 🚨 EMERGENCY, ⚠️ MISTOUCHED, 🧪 TEST).
 * 3. Compact Home layout with Expandable/Collapsible System Status section.
 * 4. Preserves all status indicators and tap actions (Location, GPS, Accessibility, Guardians, Voice, Power Button).
 * 5. Persistent bottom navigation bar across Emergency, Voice, Guardians, and Settings.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences
    private var isSystemStatusExpanded = false

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

    // App permissions request launcher (SMS & Location)
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

        setupBottomNavigation()
        setupExpandableSystemStatus()
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
        binding.bottomNavigationView.selectedItemId = R.id.nav_emergency
        updateSystemStatus()
        updateActiveSosBanner()

        // If Voice Trigger is enabled and permission is granted, start continuous recognition
        if (TriggerManager.isVoiceTriggerEnabled(this) && VoiceTriggerManager.hasRecordAudioPermission(this)) {
            VoiceTriggerManager.startListening(this)
        }

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

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.selectedItemId = R.id.nav_emergency
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_emergency -> true
                R.id.nav_voice -> {
                    val intent = Intent(this, VoiceTriggerActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.nav_guardians -> {
                    val intent = Intent(this, ManageGuardiansActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.nav_settings -> {
                    val intent = Intent(this, SettingsActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Requirement 2: Collapsible / Expandable System Status UI.
     * Collapsed state shows only the heading and expand arrow.
     * Expanded state shows the existing status items.
     */
    private fun setupExpandableSystemStatus() {
        binding.containerSystemStatusItems.visibility = if (isSystemStatusExpanded) View.VISIBLE else View.GONE
        binding.ivSystemStatusExpand.setImageResource(
            if (isSystemStatusExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
        )

        binding.headerSystemStatus.setOnClickListener {
            isSystemStatusExpanded = !isSystemStatusExpanded
            binding.containerSystemStatusItems.visibility = if (isSystemStatusExpanded) View.VISIBLE else View.GONE
            binding.ivSystemStatusExpand.setImageResource(
                if (isSystemStatusExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
            )
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

        // 5. System Status: Accessibility item
        binding.itemAccessibilityStatus.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // 6. System Status: Guardians item -> Open Manage Guardians tab/screen
        binding.itemGuardiansStatus.setOnClickListener {
            val intent = Intent(this, ManageGuardiansActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        // 7. System Status: Voice Trigger item -> Open Voice Trigger tab/screen
        binding.itemVoiceTriggerStatus.setOnClickListener {
            val intent = Intent(this, VoiceTriggerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        // 8. System Status: Power Button SOS item -> Open Settings tab/screen
        binding.itemPowerButtonStatus.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        // 9. Active SOS Button 1: 🟢 I'M SAFE
        binding.btnSafeNow.setOnClickListener {
            TriggerManager.markUserSafe(this)
            updateActiveSosBanner()
            Toast.makeText(this, "Marked safe. Escalation stopped.", Toast.LENGTH_SHORT).show()
        }

        // 10. Active SOS Button 2: 🚨 EMERGENCY (Immediate Escalation)
        binding.btnEscalateEmergency.setOnClickListener {
            TriggerManager.escalateImmediately(this)
            updateActiveSosBanner()
            Toast.makeText(this, "Escalating to next guardian immediately.", Toast.LENGTH_SHORT).show()
        }

        // 11. Active SOS Button 3: ⚠️ MISTOUCHED (Confirmation Dialog)
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

        // 12. Active SOS Button 4: 🧪 TEST (Stop Escalation Test Only, No SMS)
        binding.btnTestOnly.setOnClickListener {
            TriggerManager.stopEscalationTestOnly(this)
            updateActiveSosBanner()
            Toast.makeText(this, "Test stopped. No SMS sent.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Section 4: First-time Accessibility Onboarding.
     * Non-forcing dialog explaining requirement with [ENABLE ACCESSIBILITY] and [NOT NOW].
     */
    private fun checkAccessibilityOnboarding() {
        val hasShown = sharedPreferences.getBoolean("KEY_ACCESSIBILITY_ONBOARDING_SHOWN", false)
        val isEnabled = KaikoAccessibilityService.isAccessibilityServiceEnabled(this)
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
     * Updates the System Status indicators:
     * 1. Location Permission: ON / OFF
     * 2. Location Toggle: ON / OFF
     * 3. Accessibility: ON / OFF
     * 4. Guardians: X / 3 configured
     * 5. Voice Trigger: ON / OFF
     * 6. Power Button Assistant: ACTIVE / OFF
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

        // 3. Accessibility Status
        val accOn = KaikoAccessibilityService.isAccessibilityServiceEnabled(this)
        if (accOn) {
            binding.tvAccessibilityStatusValue.text = "ON"
            binding.tvAccessibilityStatusValue.setTextColor(ContextCompat.getColor(this, R.color.status_enabled))
        } else {
            binding.tvAccessibilityStatusValue.text = "OFF"
            binding.tvAccessibilityStatusValue.setTextColor(ContextCompat.getColor(this, R.color.status_disabled))
        }

        // 4. Guardians Count Status
        val guardianCount = TriggerManager.getConfiguredGuardiansCount(this)
        binding.tvGuardiansConfiguredCount.text = "$guardianCount / ${TriggerManager.MIN_GUARDIANS} configured (${TriggerManager.MIN_GUARDIANS} required)"
        if (guardianCount >= TriggerManager.MIN_GUARDIANS) {
            binding.tvGuardiansStatusValue.text = "COMPLETE"
            binding.tvGuardiansStatusValue.setTextColor(ContextCompat.getColor(this, R.color.status_enabled))
        } else {
            binding.tvGuardiansStatusValue.text = "INCOMPLETE"
            binding.tvGuardiansStatusValue.setTextColor(ContextCompat.getColor(this, R.color.emergency_red))
        }

        // 5. Voice Trigger Status
        val voiceOn = TriggerManager.isVoiceTriggerEnabled(this)
        if (voiceOn) {
            binding.tvVoiceTriggerStatusValue.text = "ON"
            binding.tvVoiceTriggerStatusValue.setTextColor(ContextCompat.getColor(this, R.color.status_enabled))
        } else {
            binding.tvVoiceTriggerStatusValue.text = "OFF"
            binding.tvVoiceTriggerStatusValue.setTextColor(ContextCompat.getColor(this, R.color.status_disabled))
        }

        // 6. Power Button Assistant Status
        val isAssistant = TriggerManager.isDefaultAssistant(this)
        if (isAssistant) {
            binding.tvPowerButtonStatusValue.text = "ACTIVE"
            binding.tvPowerButtonStatusValue.setTextColor(ContextCompat.getColor(this, R.color.status_enabled))
        } else {
            binding.tvPowerButtonStatusValue.text = "OFF"
            binding.tvPowerButtonStatusValue.setTextColor(ContextCompat.getColor(this, R.color.status_disabled))
        }
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
}
