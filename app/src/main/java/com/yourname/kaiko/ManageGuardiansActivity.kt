package com.yourname.kaiko

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.yourname.kaiko.databinding.ActivityManageGuardiansBinding
import com.yourname.kaiko.databinding.DialogAddEditGuardianBinding
import com.yourname.kaiko.databinding.DialogChooseAddMethodBinding
import com.yourname.kaiko.databinding.ItemGuardianCardBinding
import com.yourname.kaiko.databinding.ItemGuardianEmptyBinding

/**
 * Dedicated Manage Guardians Screen (Kaiko v1.8.0).
 * 
 * Features:
 * 1. Minimum 3 guardians required; up to 10 guardians supported.
 * 2. Responsive setup-complete banner ("X emergency guardians configured.\nSetup completed").
 * 3. Prevents adding an 11th guardian.
 * 4. Unified professional edit/remove icons (grey pencil & red trash).
 * 5. Call & Message native actions on each card.
 * 6. Native Contacts picker and manual text entry.
 * 7. SOS Delivery Mode setting (all-at-once vs sequential).
 * 8. Persistent bottom navigation bar.
 */
class ManageGuardiansActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageGuardiansBinding
    private var pendingSlotIndex: Int? = null

    // Native Contacts Picker Launcher
    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val contactUri = result.data?.data
            if (contactUri != null) {
                retrieveContactDetails(contactUri)
            }
        }
    }

    // Contacts Runtime Permission Launcher
    private val requestContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openNativeContactPicker()
        } else {
            Toast.makeText(
                this,
                "Contacts permission is required to select from contacts.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageGuardiansBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTopBar()
        setupBottomNavigation()
        setupDeliveryModeSetting()
        setupAddGuardianButton()
        renderGuardiansList()
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigationView.selectedItemId = R.id.nav_guardians
        renderGuardiansList()
    }

    private fun setupTopBar() {
        binding.btnBack.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.selectedItemId = R.id.nav_guardians
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
                R.id.nav_guardians -> true
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
     * Requirement: SOS Delivery Mode & Escalation Timeout.
     * Mode: "Escalation Mode (Sequential)" vs "Send to all at once".
     * Sequential Mode allows selecting timeout: 30s | 60s (Default) | 120s.
     */
    private fun setupDeliveryModeSetting() {
        val isAllAtOnce = TriggerManager.isSosDeliveryAllAtOnce(this)
        binding.switchDeliveryMode.isChecked = isAllAtOnce
        updateDeliveryModeUi(isAllAtOnce)

        binding.switchDeliveryMode.setOnCheckedChangeListener { _, isChecked ->
            TriggerManager.setSosDeliveryAllAtOnce(this, isChecked)
            updateDeliveryModeUi(isChecked)
        }

        binding.chipTimeout30.setOnClickListener {
            TriggerManager.setEscalationDelaySeconds(this, 30L)
            updateTimeoutChipsUi(30L)
        }
        binding.chipTimeout60.setOnClickListener {
            TriggerManager.setEscalationDelaySeconds(this, 60L)
            updateTimeoutChipsUi(60L)
        }
        binding.chipTimeout120.setOnClickListener {
            TriggerManager.setEscalationDelaySeconds(this, 120L)
            updateTimeoutChipsUi(120L)
        }
    }

    private fun updateDeliveryModeUi(isAllAtOnce: Boolean) {
        if (isAllAtOnce) {
            binding.tvDeliveryModeSubtitle.text = "Simultaneous Delivery: ACTIVE"
            binding.tvDeliveryExplanation.text = getString(R.string.sos_delivery_on_desc)
            binding.layoutEscalationTimeout.visibility = View.GONE
        } else {
            binding.tvDeliveryModeSubtitle.text = "Escalation Mode (Sequential): ACTIVE"
            binding.tvDeliveryExplanation.text = getString(R.string.sos_delivery_off_desc)
            binding.layoutEscalationTimeout.visibility = View.VISIBLE
            val currentDelay = TriggerManager.getEscalationDelaySeconds(this)
            updateTimeoutChipsUi(currentDelay)
        }
    }

    private fun updateTimeoutChipsUi(selectedDelay: Long) {
        val white = ContextCompat.getColor(this, android.R.color.white)
        val textPrimary = ContextCompat.getColor(this, R.color.text_primary)

        binding.chipTimeout30.setBackgroundResource(if (selectedDelay == 30L) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
        binding.chipTimeout30.setTextColor(if (selectedDelay == 30L) white else textPrimary)

        binding.chipTimeout60.setBackgroundResource(if (selectedDelay == 60L) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
        binding.chipTimeout60.setTextColor(if (selectedDelay == 60L) white else textPrimary)

        binding.chipTimeout120.setBackgroundResource(if (selectedDelay == 120L) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
        binding.chipTimeout120.setTextColor(if (selectedDelay == 120L) white else textPrimary)
    }

    private fun setupAddGuardianButton() {
        binding.btnAddGuardian.setOnClickListener {
            val currentGuardians = TriggerManager.getAllGuardians(this)
            val configuredCount = currentGuardians.count { it.phone.isNotBlank() }

            // Prevent adding an 11th guardian
            if (configuredCount >= TriggerManager.MAX_GUARDIANS) {
                Toast.makeText(this, "Maximum 10 guardians configured. Cannot add more.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // If there's an empty slot among 1..3, target that slot, else append
            val targetSlot = if (currentGuardians.size < 3) currentGuardians.size else null
            showAddMethodDialog(targetSlot)
        }
    }

    /**
     * Requirement 4: Renders Guardian cards up to 10.
     * Enforces minimum 3 guardians compulsory.
     * Responsive setup-complete banner layout without clipping.
     */
    private fun renderGuardiansList() {
        binding.llGuardiansContainer.removeAllViews()

        val guardians = TriggerManager.getAllGuardians(this).toMutableList()
        val configuredCount = guardians.count { it.phone.isNotBlank() }

        // Update top status banner responsively without text cut-off
        if (configuredCount < TriggerManager.MIN_GUARDIANS) {
            binding.layoutStatusBanner.setBackgroundResource(R.drawable.bg_status_warning)
            binding.tvStatusIcon.text = "⚠️"
            binding.tvStatusText.text = "Kaiko requires at least 3 guardians. Configured: $configuredCount / ${TriggerManager.MIN_GUARDIANS}.\nAll 3 must be configured before guardian setup is considered complete."
        } else {
            binding.layoutStatusBanner.setBackgroundResource(R.drawable.bg_status_success)
            binding.tvStatusIcon.text = "✅"
            binding.tvStatusText.text = "$configuredCount emergency guardians configured. Setup completed"
        }

        // Prevent adding an 11th guardian
        if (configuredCount >= TriggerManager.MAX_GUARDIANS) {
            binding.btnAddGuardian.isEnabled = false
            binding.btnAddGuardian.alpha = 0.5f
            binding.btnAddGuardian.text = "MAXIMUM 10 GUARDIANS REACHED (10/10)"
        } else {
            binding.btnAddGuardian.isEnabled = true
            binding.btnAddGuardian.alpha = 1.0f
            binding.btnAddGuardian.text = getString(R.string.add_guardian_btn)
        }

        // Display at least 3 slots (Guardian 1, 2, 3), up to guardians.size (max 10)
        val totalSlots = maxOf(3, guardians.size).coerceAtMost(TriggerManager.MAX_GUARDIANS)

        for (slotIndex in 0 until totalSlots) {
            val guardian = guardians.getOrNull(slotIndex)
            val slotNumber = slotIndex + 1

            if (guardian != null && guardian.phone.isNotBlank()) {
                // Configured Guardian Card
                val cardBinding = ItemGuardianCardBinding.inflate(
                    LayoutInflater.from(this),
                    binding.llGuardiansContainer,
                    false
                )

                cardBinding.tvGuardianSlot.text = "Guardian $slotNumber"

                // Name: Optional, never "Unknown"
                if (guardian.name.isNotBlank()) {
                    cardBinding.tvGuardianName.visibility = View.VISIBLE
                    cardBinding.tvGuardianName.text = guardian.name.trim()
                } else {
                    cardBinding.tvGuardianName.visibility = View.GONE
                }

                // Relation: Optional, never "Unknown"
                if (guardian.relation.isNotBlank() && !guardian.relation.startsWith("Select Relation", ignoreCase = true)) {
                    cardBinding.tvGuardianRelation.visibility = View.VISIBLE
                    cardBinding.tvGuardianRelation.text = guardian.relation.trim()
                } else {
                    cardBinding.tvGuardianRelation.visibility = View.GONE
                }

                // Phone: Required
                cardBinding.tvGuardianPhone.text = guardian.phone

                // 📞 Call: Normal phone dial action
                cardBinding.btnCall.setOnClickListener {
                    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:${guardian.phone.trim()}")
                    }
                    try {
                        startActivity(dialIntent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not open dialer: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                // 💬 Message: Normal SMS / Messages action
                cardBinding.btnMessage.setOnClickListener {
                    val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:${guardian.phone.trim()}")
                    }
                    try {
                        startActivity(smsIntent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not open messaging: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                // Professional Edit Icon: Small neutral/grey pencil icon
                cardBinding.btnEditGuardian.setOnClickListener {
                    pendingSlotIndex = slotIndex
                    showAddEditGuardianDialog(
                        targetSlotIndex = slotIndex,
                        initialName = guardian.name,
                        initialRelation = guardian.relation,
                        initialPhone = guardian.phone
                    )
                }

                // Professional Remove Icon: Small red delete/trash icon
                cardBinding.btnRemoveGuardian.setOnClickListener {
                    handleRemoveGuardian(slotIndex)
                }

                binding.llGuardiansContainer.addView(cardBinding.root)
            } else {
                // Empty required slot (Guardian 1, 2, or 3)
                val emptyBinding = ItemGuardianEmptyBinding.inflate(
                    LayoutInflater.from(this),
                    binding.llGuardiansContainer,
                    false
                )
                emptyBinding.tvEmptySlotTitle.text = "Guardian $slotNumber"
                emptyBinding.btnConfigureSlot.text = "+ CONFIGURE GUARDIAN $slotNumber"
                emptyBinding.btnConfigureSlot.setOnClickListener {
                    showAddMethodDialog(slotIndex)
                }
                binding.llGuardiansContainer.addView(emptyBinding.root)
            }
        }
    }

    /**
     * Requirement 4 & 5: Edit / Remove Guardian.
     * Do NOT allow removal if it would leave fewer than 3 guardians.
     */
    private fun handleRemoveGuardian(indexToRemove: Int) {
        val currentGuardians = TriggerManager.getAllGuardians(this).toMutableList()
        val configuredCount = currentGuardians.count { it.phone.isNotBlank() }

        if (configuredCount <= TriggerManager.MIN_GUARDIANS) {
            AlertDialog.Builder(this)
                .setTitle("Cannot Remove Guardian")
                .setMessage(getString(R.string.min_guardians_warning))
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
            return
        }

        // Ask for confirmation when removal is permitted (> 3 guardians)
        AlertDialog.Builder(this)
            .setTitle("Remove Guardian?")
            .setMessage("Are you sure you want to remove Guardian ${indexToRemove + 1}?")
            .setPositiveButton("REMOVE") { _, _ ->
                if (indexToRemove in currentGuardians.indices) {
                    currentGuardians.removeAt(indexToRemove)
                    TriggerManager.saveGuardians(this, currentGuardians)
                    renderGuardiansList()
                    Toast.makeText(this, "Guardian removed.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("CANCEL") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showAddMethodDialog(slotIndex: Int?) {
        pendingSlotIndex = slotIndex

        val dialogBinding = DialogChooseAddMethodBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.btnChooseContacts.setOnClickListener {
            dialog.dismiss()
            handleChooseFromContacts()
        }

        dialogBinding.btnAddManually.setOnClickListener {
            dialog.dismiss()
            showAddEditGuardianDialog(
                targetSlotIndex = pendingSlotIndex,
                initialName = "",
                initialRelation = "",
                initialPhone = ""
            )
        }

        dialogBinding.btnCancelAddMethod.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun handleChooseFromContacts() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            openNativeContactPicker()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Contacts Permission")
                .setMessage(getString(R.string.contacts_permission_rationale))
                .setPositiveButton("CONTINUE") { _, _ ->
                    requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
                .setNegativeButton("CANCEL") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    private fun openNativeContactPicker() {
        try {
            val pickIntent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            pickContactLauncher.launch(pickIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open contacts: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun retrieveContactDetails(contactUri: Uri) {
        var name = ""
        var phoneNumber = ""
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (nameIdx != -1) name = cursor.getString(nameIdx) ?: ""
                    if (phoneIdx != -1) phoneNumber = cursor.getString(phoneIdx) ?: ""
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error reading contact details: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        var digits = phoneNumber.replace(Regex("[^0-9]"), "")
        if (digits.length > 10 && (digits.startsWith("91") || digits.startsWith("0"))) {
            digits = digits.takeLast(10)
        }
        val currentGuardian = pendingSlotIndex?.let { idx ->
            TriggerManager.getAllGuardians(this).getOrNull(idx)
        }
        val initialRelation = currentGuardian?.relation ?: ""

        showAddEditGuardianDialog(
            targetSlotIndex = pendingSlotIndex,
            initialName = name,
            initialRelation = initialRelation,
            initialPhone = digits
        )
    }

    private fun showAddEditGuardianDialog(
        targetSlotIndex: Int?,
        initialName: String,
        initialRelation: String,
        initialPhone: String
    ) {
        val currentGuardians = TriggerManager.getAllGuardians(this).toMutableList()

        // Prevent adding an 11th guardian
        if (targetSlotIndex == null && currentGuardians.size >= TriggerManager.MAX_GUARDIANS) {
            Toast.makeText(this, "Maximum 10 guardians configured. Cannot add more.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogAddEditGuardianBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val isEditMode = targetSlotIndex != null &&
                targetSlotIndex < currentGuardians.size &&
                currentGuardians[targetSlotIndex].phone.isNotBlank()

        dialogBinding.tvDialogTitle.text = if (isEditMode) {
            "Edit Guardian ${(targetSlotIndex ?: 0) + 1}"
        } else {
            val slotNum = if (targetSlotIndex != null) "${targetSlotIndex + 1}" else "${currentGuardians.size + 1}"
            "Add Guardian $slotNum"
        }

        dialogBinding.etGuardianName.setText(initialName)
        dialogBinding.etGuardianPhone.setText(initialPhone)

        // Setup Relation Spinner
        val relationsArray = resources.getStringArray(R.array.guardian_relations)
        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            relationsArray
        )
        dialogBinding.spinnerRelation.adapter = spinnerAdapter

        if (initialRelation.isNotBlank()) {
            val pos = relationsArray.indexOfFirst { it.equals(initialRelation, ignoreCase = true) }
            if (pos >= 0) {
                dialogBinding.spinnerRelation.setSelection(pos)
            }
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveGuardian.setOnClickListener {
            val name = dialogBinding.etGuardianName.text.toString().trim()
            val phone = dialogBinding.etGuardianPhone.text.toString().trim()
            val digits = phone.replace(Regex("[^0-9]"), "")
            val selectedRelationItem = dialogBinding.spinnerRelation.selectedItem?.toString() ?: ""
            val relation = if (selectedRelationItem.startsWith("Select Relation", ignoreCase = true)) "" else selectedRelationItem

            // 1. Validate Phone Number (Must contain exactly 10 digits)
            if (digits.length != 10) {
                dialogBinding.etGuardianPhone.error = "Phone number must contain exactly 10 digits"
                dialogBinding.etGuardianPhone.requestFocus()
                return@setOnClickListener
            }

            // 2. Prevent duplicate phone numbers
            val isDuplicate = currentGuardians.withIndex().any { (idx, g) ->
                idx != targetSlotIndex && g.phone.replace(Regex("[^0-9]"), "") == digits
            }
            if (isDuplicate) {
                Toast.makeText(this, "Error: Duplicate guardian phone number detected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 3. Save guardian (10-digit phone)
            val newGuardian = Guardian(name = name, relation = relation, phone = digits)

            if (targetSlotIndex != null && targetSlotIndex in currentGuardians.indices) {
                currentGuardians[targetSlotIndex] = newGuardian
            } else if (targetSlotIndex != null && targetSlotIndex >= currentGuardians.size) {
                while (currentGuardians.size < targetSlotIndex) {
                    currentGuardians.add(Guardian())
                }
                currentGuardians.add(newGuardian)
            } else {
                if (currentGuardians.size >= TriggerManager.MAX_GUARDIANS) {
                    Toast.makeText(this, "Maximum 10 guardians configured.", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    return@setOnClickListener
                }
                currentGuardians.add(newGuardian)
            }

            val cleanedList = currentGuardians.filter { it.phone.isNotBlank() }
            TriggerManager.saveGuardians(this, cleanedList)

            Toast.makeText(this, "Guardian saved successfully!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            renderGuardiansList()
        }

        dialog.show()
    }
}
