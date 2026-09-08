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
import com.yourname.kaiko.databinding.DialogCustomVoicePhraseBinding

/**
 * Main Activity for Kaiko (v1.6.0).
 * Features:
 * 1. Top large circular SOS trigger button (RED).
 * 2. Active SOS controls with 4 dedicated actions:
 *    - 🟢 I'M SAFE (Green)
 *    - 🚨 EMERGENCY (Red)
 *    - ⚠️ MISTOUCHED (Amber/Warning)
 *    - 🧪 TEST (Grey, stop escalation test only, no SMS)
 * 3. Compact System Status (Location, GPS, Accessibility, Guardians, Voice Trigger).
 * 4. Voice Trigger safety feature:
 *    - ON/OFF control
 *    - Default emergency phrases ("I need help", "I am in danger", "Help me", "This is an emergency", "Send SOS")
 *    - Custom voice phrase (entered via text input, stored locally, enabled/disabled/removed)
 *    - Triggers existing central SOS flow without modifying SOS/SMS logic
 *    - Respects Android speech recognition APIs and reports background limitations
 * 5. First-time accessibility onboarding.
 * 6. Direct Android Accessibility settings management.
 * 7. Top-right Info icon launching dedicated Kaiko User Guide.
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
                    updateVoiceTriggerUi()
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

    // Microphone permission request launcher for Voice Trigger
    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Microphone permission granted for Voice Trigger", Toast.LENGTH_SHORT).show()
            TriggerManager.setVoiceTriggerEnabled(this, true)
            binding.switchVoiceTrigger.isChecked = true
            startVoiceListening()
        } else {
            Toast.makeText(
                this,
                "Microphone permission is required to use Voice Trigger.",
                Toast.LENGTH_LONG
            ).show()
            TriggerManager.setVoiceTriggerEnabled(this, false)
            binding.switchVoiceTrigger.isChecked = false
            binding.tvVoiceTriggerLiveStatus.text = "Permission denied: Voice Trigger is OFF"
            binding.tvVoiceTriggerLiveStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
        updateSystemStatus()
        updateVoiceTriggerUi()
    }

    // Voice Trigger Listener Callback
    private val voiceTriggerListener = object : VoiceTriggerManager.VoiceTriggerListener {
        override fun onStateChanged(isListening: Boolean, statusMessage: String) {
            runOnUiThread {
                binding.tvVoiceTriggerLiveStatus.text = statusMessage
                if (isListening) {
                    binding.tvVoiceTriggerLiveStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.status_enabled))
                } else {
                    binding.tvVoiceTriggerLiveStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                }
            }
        }

        override fun onPhraseDetected(phrase: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Voice Trigger: \"$phrase\" detected! SOS triggered.", Toast.LENGTH_LONG).show()
                updateActiveSosBanner()
                updateSystemStatus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences(TriggerManager.PREFS_NAME, Context.MODE_PRIVATE)

        setupListeners()
        updateActiveSosBanner()
        updateSystemStatus()
        updateVoiceTriggerUi()
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
        updateVoiceTriggerUi()

        // If Voice Trigger is enabled and permission is granted, start in-app listening
        if (TriggerManager.isVoiceTriggerEnabled(this)) {
            if (VoiceTriggerManager.hasRecordAudioPermission(this)) {
                startVoiceListening()
            } else {
                binding.tvVoiceTriggerLiveStatus.text = "Microphone permission required"
                binding.tvVoiceTriggerLiveStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
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
        // Respect Android microphone privacy by pausing recognizer while activity is paused
        VoiceTriggerManager.stopListening()

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

        // 5. System Status: Accessibility item
        binding.itemAccessibilityStatus.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // 6. System Status: Guardians item -> Open Manage Guardians screen
        binding.itemGuardiansStatus.setOnClickListener {
            startActivity(Intent(this, ManageGuardiansActivity::class.java))
        }

        // 7. System Status: Voice Trigger item -> Scroll to Voice Trigger card
        binding.itemVoiceTriggerStatus.setOnClickListener {
            binding.scrollView.smoothScrollTo(0, binding.cardVoiceTrigger.top)
        }

        // 8. Accessibility Management Button
        binding.btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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

        // 13. Home Button: 👥 MANAGE GUARDIANS (v1.5.1)
        binding.btnManageGuardiansHome.setOnClickListener {
            startActivity(Intent(this, ManageGuardiansActivity::class.java))
        }

        // 14. Voice Trigger ON/OFF Switch
        binding.switchVoiceTrigger.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!VoiceTriggerManager.hasRecordAudioPermission(this)) {
                    requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else if (!VoiceTriggerManager.isSpeechRecognitionAvailable(this)) {
                    Toast.makeText(
                        this,
                        "Speech recognition is not available on this device.",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.switchVoiceTrigger.isChecked = false
                    TriggerManager.setVoiceTriggerEnabled(this, false)
                } else {
                    TriggerManager.setVoiceTriggerEnabled(this, true)
                    startVoiceListening()
                }
            } else {
                TriggerManager.setVoiceTriggerEnabled(this, false)
                VoiceTriggerManager.stopListening()
                binding.tvVoiceTriggerLiveStatus.text = "Voice Trigger is OFF"
                binding.tvVoiceTriggerLiveStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
            updateSystemStatus()
        }

        // 15. Custom Phrase: Add Button
        binding.btnAddCustomPhrase.setOnClickListener {
            showCustomPhraseDialog()
        }

        // 16. Custom Phrase: Edit Button
        binding.btnEditCustomPhrase.setOnClickListener {
            showCustomPhraseDialog(TriggerManager.getCustomVoicePhrase(this))
        }

        // 17. Custom Phrase: Switch (Enable / Disable)
        binding.switchCustomPhrase.setOnCheckedChangeListener { _, isChecked ->
            TriggerManager.setCustomVoicePhraseEnabled(this, isChecked)
            val msg = if (isChecked) "Custom phrase enabled" else "Custom phrase disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // 18. Custom Phrase: Remove Button
        binding.btnRemoveCustomPhrase.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Remove Custom Phrase?")
                .setMessage("Are you sure you want to remove the custom voice phrase?")
                .setPositiveButton("REMOVE") { _, _ ->
                    TriggerManager.removeCustomVoicePhrase(this)
                    updateVoiceTriggerUi()
                    Toast.makeText(this, "Custom phrase removed.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }
    }

    private fun startVoiceListening() {
        if (!TriggerManager.isVoiceTriggerEnabled(this)) return
        VoiceTriggerManager.startListening(this, voiceTriggerListener)
    }

    /**
     * Dialog to add or edit custom emergency voice phrase using TEXT INPUT.
     */
    private fun showCustomPhraseDialog(existingPhrase: String = "") {
        val dialogBinding = DialogCustomVoicePhraseBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        if (existingPhrase.isNotBlank()) {
            dialogBinding.etCustomPhraseInput.setText(existingPhrase)
            dialogBinding.etCustomPhraseInput.setSelection(existingPhrase.length)
        }

        dialogBinding.btnCancelCustomPhrase.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveCustomPhrase.setOnClickListener {
            val inputPhrase = dialogBinding.etCustomPhraseInput.text.toString().trim()
            if (inputPhrase.isBlank()) {
                Toast.makeText(this, "Please enter an emergency phrase", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (inputPhrase.length < 3) {
                Toast.makeText(this, "Phrase is too short. Please use at least 2 words.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            TriggerManager.setCustomVoicePhrase(this, inputPhrase)
            dialog.dismiss()
            updateVoiceTriggerUi()
            Toast.makeText(this, "Custom emergency phrase saved!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    /**
     * Updates Voice Trigger UI elements to reflect stored preferences.
     */
    private fun updateVoiceTriggerUi() {
        val voiceEnabled = TriggerManager.isVoiceTriggerEnabled(this)
        if (binding.switchVoiceTrigger.isChecked != voiceEnabled) {
            binding.switchVoiceTrigger.isChecked = voiceEnabled
        }

        if (voiceEnabled) {
            if (VoiceTriggerManager.isCurrentlyListening()) {
                binding.tvVoiceTriggerLiveStatus.text = "🎤 Active: Listening for emergency phrases..."
                binding.tvVoiceTriggerLiveStatus.setTextColor(ContextCompat.getColor(this, R.color.status_enabled))
            } else {
                binding.tvVoiceTriggerLiveStatus.text = "Voice Trigger is ON"
                binding.tvVoiceTriggerLiveStatus.setTextColor(ContextCompat.getColor(this, R.color.status_enabled))
            }
        } else {
            binding.tvVoiceTriggerLiveStatus.text = "Voice Trigger is OFF"
            binding.tvVoiceTriggerLiveStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }

        val customPhrase = TriggerManager.getCustomVoicePhrase(this)
        val customEnabled = TriggerManager.isCustomVoicePhraseEnabled(this)

        if (customPhrase.isNotBlank()) {
            binding.layoutCustomPhraseEmpty.visibility = View.GONE
            binding.layoutCustomPhraseConfigured.visibility = View.VISIBLE
            binding.tvCustomPhraseDisplay.text = "🗣️ \"$customPhrase\""
            if (binding.switchCustomPhrase.isChecked != customEnabled) {
                binding.switchCustomPhrase.isChecked = customEnabled
            }
        } else {
            binding.layoutCustomPhraseEmpty.visibility = View.VISIBLE
            binding.layoutCustomPhraseConfigured.visibility = View.GONE
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
     * Updates the System Status indicators:
     * 1. Location Permission: ON / OFF
     * 2. Location Toggle: ON / OFF
     * 3. Accessibility: ON / OFF
     * 4. Guardians: X guardians configured
     * 5. Voice Trigger: ON / OFF
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
        val accOn = isAccessibilityServiceEnabled(this, KaikoAccessibilityService::class.java)
        if (accOn) {
            binding.tvAccessibilityStatusValue.text = "ON"
            binding.tvAccessibilityStatusValue.setTextColor(ContextCompat.getColor(this, R.color.status_enabled))
        } else {
            binding.tvAccessibilityStatusValue.text = "OFF"
            binding.tvAccessibilityStatusValue.setTextColor(ContextCompat.getColor(this, R.color.status_disabled))
        }

        // 4. Guardians Count Status
        val guardianCount = TriggerManager.getConfiguredGuardiansCount(this)
        val countText = "$guardianCount ${if (guardianCount == 1) "guardian" else "guardians"} configured"
        binding.tvGuardiansStatusValue.text = countText

        // 5. Voice Trigger Status
        val voiceOn = TriggerManager.isVoiceTriggerEnabled(this)
        if (voiceOn) {
            binding.tvVoiceTriggerStatusValue.text = "ON"
            binding.tvVoiceTriggerStatusValue.setTextColor(ContextCompat.getColor(this, R.color.status_enabled))
        } else {
            binding.tvVoiceTriggerStatusValue.text = "OFF"
            binding.tvVoiceTriggerStatusValue.setTextColor(ContextCompat.getColor(this, R.color.status_disabled))
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
