package com.yourname.kaiko

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.yourname.kaiko.databinding.ActivityVoiceTriggerBinding
import com.yourname.kaiko.databinding.ItemCustomPhraseBinding

/**
 * Dedicated Voice Trigger Configuration Page (Kaiko v1.8.0).
 * 
 * Features:
 * 1. Voice Trigger ON/OFF switch with real-time status indication.
 * 2. Displays 5 default emergency phrases.
 * 3. Up to 5 custom emergency phrases (text-input based).
 * 4. Add, edit, and remove custom emergency phrases.
 * 5. Visual counter showing 0/5..5/5 phrases configured.
 * 6. Enforces duplicate, empty, and 5-phrase limits.
 * 7. Unified grey pencil edit icon and red trash delete icon.
 * 8. Persistent bottom navigation bar.
 */
class VoiceTriggerActivity : AppCompatActivity(), VoiceTriggerManager.VoiceTriggerListener {

    private lateinit var binding: ActivityVoiceTriggerBinding

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            TriggerManager.setVoiceTriggerEnabled(this, true)
            binding.switchVoiceTrigger.isChecked = true
            VoiceTriggerManager.startListening(this, this)
            updateVoiceTriggerUi()
        } else {
            TriggerManager.setVoiceTriggerEnabled(this, false)
            binding.switchVoiceTrigger.isChecked = false
            updateVoiceTriggerUi()
            Toast.makeText(
                this,
                "Microphone permission is required to enable Voice Trigger.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceTriggerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTopBar()
        setupBottomNavigation()
        setupVoiceTriggerSwitch()
        setupAddCustomPhraseButton()
        renderCustomPhrasesList()
    }

    private fun setupTopBar() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigationView.selectedItemId = R.id.nav_settings
        updateVoiceTriggerUi()
        renderCustomPhrasesList()

        if (TriggerManager.isVoiceTriggerEnabled(this) && VoiceTriggerManager.hasRecordAudioPermission(this)) {
            VoiceTriggerManager.startListening(this, this)
        }
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
                R.id.nav_settings -> {
                    val intent = Intent(this, SettingsActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupVoiceTriggerSwitch() {
        binding.switchVoiceTrigger.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!VoiceTriggerManager.hasRecordAudioPermission(this)) {
                    requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    TriggerManager.setVoiceTriggerEnabled(this, true)
                    VoiceTriggerManager.startListening(this, this)
                    updateVoiceTriggerUi()
                }
            } else {
                TriggerManager.setVoiceTriggerEnabled(this, false)
                VoiceTriggerManager.stopListening()
                updateVoiceTriggerUi()
            }
        }
    }

    private fun updateVoiceTriggerUi() {
        val isEnabled = TriggerManager.isVoiceTriggerEnabled(this)
        val hasPermission = VoiceTriggerManager.hasRecordAudioPermission(this)

        binding.switchVoiceTrigger.setOnCheckedChangeListener(null)
        binding.switchVoiceTrigger.isChecked = isEnabled
        setupVoiceTriggerSwitch()

        if (isEnabled && hasPermission) {
            binding.tvVoiceLiveStatus.text = if (VoiceTriggerManager.isCurrentlyListening()) {
                "Active (Listening for emergency phrases...)"
            } else {
                "Active (Ready to listen)"
            }
            binding.tvVoiceLiveStatus.setTextColor(ContextCompat.getColor(this, R.color.status_enabled))
        } else if (isEnabled && !hasPermission) {
            binding.tvVoiceLiveStatus.text = "Microphone permission required"
            binding.tvVoiceLiveStatus.setTextColor(ContextCompat.getColor(this, R.color.emergency_red))
        } else {
            binding.tvVoiceLiveStatus.text = "Voice Trigger is OFF"
            binding.tvVoiceLiveStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun setupAddCustomPhraseButton() {
        binding.btnAddCustomPhrase.setOnClickListener {
            val count = TriggerManager.getCustomVoicePhrasesCount(this)
            if (count >= TriggerManager.MAX_CUSTOM_VOICE_PHRASES) {
                Toast.makeText(this, "Maximum 5 custom phrases reached.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showAddEditPhraseDialog(phraseIndex = null, currentText = "")
        }
    }

    private fun renderCustomPhrasesList() {
        binding.llCustomPhrasesContainer.removeAllViews()
        val phrases = TriggerManager.getCustomVoicePhrases(this)
        val count = phrases.size

        binding.tvCustomPhrasesCount.text = "$count/${TriggerManager.MAX_CUSTOM_VOICE_PHRASES}"

        if (count == 0) {
            binding.tvCustomPhrasesEmpty.visibility = View.VISIBLE
        } else {
            binding.tvCustomPhrasesEmpty.visibility = View.GONE
        }

        // Prevent adding more than 5 custom phrases
        if (count >= TriggerManager.MAX_CUSTOM_VOICE_PHRASES) {
            binding.btnAddCustomPhrase.isEnabled = false
            binding.btnAddCustomPhrase.alpha = 0.5f
            binding.btnAddCustomPhrase.text = "MAXIMUM 5 PHRASES REACHED (5/5)"
        } else {
            binding.btnAddCustomPhrase.isEnabled = true
            binding.btnAddCustomPhrase.alpha = 1.0f
            binding.btnAddCustomPhrase.text = "+ ADD CUSTOM PHRASE"
        }

        for ((index, phrase) in phrases.withIndex()) {
            val itemBinding = ItemCustomPhraseBinding.inflate(
                LayoutInflater.from(this),
                binding.llCustomPhrasesContainer,
                false
            )

            itemBinding.tvPhraseNumber.text = "${index + 1}."
            itemBinding.tvPhraseText.text = "\"$phrase\""

            // Edit icon
            itemBinding.btnEditPhrase.setOnClickListener {
                showAddEditPhraseDialog(phraseIndex = index, currentText = phrase)
            }

            // Remove icon
            itemBinding.btnRemovePhrase.setOnClickListener {
                confirmRemovePhrase(index, phrase)
            }

            binding.llCustomPhrasesContainer.addView(itemBinding.root)
        }
    }

    private fun showAddEditPhraseDialog(phraseIndex: Int?, currentText: String) {
        val isEdit = phraseIndex != null
        val input = EditText(this).apply {
            hint = "e.g. \"I need immediate help\""
            setText(currentText)
            setSelection(currentText.length)
            setBackgroundResource(R.drawable.bg_input)
            setPadding(32, 24, 32, 24)
            setTextColor(ContextCompat.getColor(this@VoiceTriggerActivity, R.color.text_primary))
        }

        val container = android.widget.FrameLayout(this).apply {
            setPadding(48, 16, 48, 8)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle(if (isEdit) "Edit Custom Phrase" else "Add Custom Phrase")
            .setMessage("Enter an emergency distress phrase. Must be at least 2 words.")
            .setView(container)
            .setPositiveButton(if (isEdit) "SAVE" else "ADD") { _, _ ->
                val enteredText = input.text.toString().trim()
                if (enteredText.isBlank()) {
                    Toast.makeText(this, "Phrase cannot be empty.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (isEdit) {
                    val success = TriggerManager.updateCustomVoicePhrase(this, phraseIndex!!, enteredText)
                    if (success) {
                        renderCustomPhrasesList()
                        Toast.makeText(this, "Custom phrase updated.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Phrase cannot be empty or duplicate.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val success = TriggerManager.addCustomVoicePhrase(this, enteredText)
                    if (success) {
                        renderCustomPhrasesList()
                        Toast.makeText(this, "Custom phrase added.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Could not add phrase. Check for duplicates or 5-phrase limit.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("CANCEL") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun confirmRemovePhrase(index: Int, phrase: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove Custom Phrase?")
            .setMessage("Are you sure you want to remove \"$phrase\"?")
            .setPositiveButton("REMOVE") { _, _ ->
                TriggerManager.removeCustomVoicePhrase(this, index)
                renderCustomPhrasesList()
                Toast.makeText(this, "Phrase removed.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("CANCEL") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onStateChanged(isListening: Boolean, statusMessage: String) {
        runOnUiThread {
            if (TriggerManager.isVoiceTriggerEnabled(this)) {
                binding.tvVoiceLiveStatus.text = statusMessage
            }
        }
    }

    override fun onPhraseDetected(phrase: String) {
        runOnUiThread {
            Toast.makeText(this, "🚨 Phrase detected: \"$phrase\"", Toast.LENGTH_LONG).show()
        }
    }
}
