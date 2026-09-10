package com.yourname.kaiko

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.yourname.kaiko.databinding.ActivitySettingsBinding

/**
 * Dedicated Settings Page (Kaiko v1.8.0).
 * 
 * Hosts:
 * 1. Power Button SOS / Android Default Assistant configuration.
 * 2. Accessibility Setup (3x volume button listener).
 * 3. Kaiko User Guide link.
 * 4. App information & version metadata.
 * 5. Persistent bottom navigation bar.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()
        setupActions()
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigationView.selectedItemId = R.id.nav_settings
        updateStatuses()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.selectedItemId = R.id.nav_settings
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_emergency -> {
                    val intent = Intent(this, MainActivity::class.java).apply {
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
                R.id.nav_settings -> true
                else -> false
            }
        }
    }

    private fun setupActions() {
        // 0. VOICE TRIGGER (Requirement 3: Dedicated Page Navigation)
        val openVoiceTrigger = {
            val intent = Intent(this, VoiceTriggerActivity::class.java)
            startActivity(intent)
        }
        binding.cardVoiceTriggerSettings.setOnClickListener { openVoiceTrigger() }
        binding.btnConfigureVoiceTrigger.setOnClickListener { openVoiceTrigger() }

        // 1. MANAGE DEFAULT ASSISTANT (opens actual Android system Default Assistant settings)
        binding.btnConfigurePowerButton.setOnClickListener {
            TriggerManager.openDefaultAssistantSettings(this)
        }

        // 2. MANAGE ACCESSIBILITY
        binding.btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // 3. USER GUIDE
        binding.cardUserGuide.setOnClickListener {
            startActivity(Intent(this, UserGuideActivity::class.java))
        }
    }

    private fun updateStatuses() {
        // Voice Trigger status & custom phrase count (Requirement 3)
        val isVoiceEnabled = TriggerManager.isVoiceTriggerEnabled(this)
        val customCount = TriggerManager.getCustomVoicePhrasesCount(this)
        val maxCustom = TriggerManager.MAX_CUSTOM_VOICE_PHRASES

        if (isVoiceEnabled) {
            binding.tvVoiceStatusBadge.text = "ON"
            binding.tvVoiceStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_enabled))
            binding.tvVoiceTriggerStatus.text = "Status: ON (Listening)"
            binding.tvVoiceTriggerStatus.setTextColor(ContextCompat.getColor(this, R.color.status_enabled))
        } else {
            binding.tvVoiceStatusBadge.text = "OFF"
            binding.tvVoiceStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_disabled))
            binding.tvVoiceTriggerStatus.text = "Status: OFF"
            binding.tvVoiceTriggerStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
        binding.tvCustomPhrasesCount.text = "Custom phrases: $customCount/$maxCustom (5 default active)"

        // Power Button Assistant status
        val isAssistant = TriggerManager.isDefaultAssistant(this)
        if (isAssistant) {
            binding.tvPowerButtonLiveStatus.text = getString(R.string.power_button_status_active)
            binding.tvPowerButtonLiveStatus.setTextColor(ContextCompat.getColor(this, R.color.status_enabled))
            binding.btnConfigurePowerButton.text = "MANAGE DEFAULT ASSISTANT"
        } else {
            binding.tvPowerButtonLiveStatus.text = getString(R.string.power_button_status_inactive)
            binding.tvPowerButtonLiveStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.btnConfigurePowerButton.text = "MANAGE DEFAULT ASSISTANT"
        }

        // Accessibility status
        val isAccessibilityEnabled = KaikoAccessibilityService.isAccessibilityServiceEnabled(this)
        if (isAccessibilityEnabled) {
            binding.tvAccessibilityStatus.text = "Accessibility Service: ON"
            binding.tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.status_enabled))
        } else {
            binding.tvAccessibilityStatus.text = "Accessibility Service: OFF"
            binding.tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.status_disabled))
        }
    }
}
