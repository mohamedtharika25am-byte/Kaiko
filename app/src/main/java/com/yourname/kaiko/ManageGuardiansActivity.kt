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
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.yourname.kaiko.databinding.ActivityManageGuardiansBinding
import com.yourname.kaiko.databinding.DialogAddEditGuardianBinding
import com.yourname.kaiko.databinding.DialogChooseAddMethodBinding
import com.yourname.kaiko.databinding.ItemGuardianCardBinding
import com.yourname.kaiko.databinding.ItemGuardianEmptyBinding

/**
 * Dedicated Manage Guardians Screen (Kaiko v1.5.0).
 * Features:
 * 1. Minimum 3 guardians required.
 * 2. Guardian cards showing Name, Relation, Phone Number (no priority, no unknown placeholders).
 * 3. 📞 Call & 💬 Message standard native actions on each card.
 * 4. Add Guardian via native phone Contacts picker or Manual entry.
 * 5. Edit and Remove guardian (enforcing minimum 3 guardians restriction).
 * 6. SOS Delivery Mode setting:
 *    - OFF: Guardian 1 → Guardian 2 → Guardian 3
 *    - ON: All guardians receive SOS simultaneously
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
        setupDeliveryModeSetting()
        setupAddGuardianButton()
        renderGuardiansList()
    }

    private fun setupTopBar() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    /**
     * Requirement 9 & 10: SOS Delivery Mode toggle.
     */
    private fun setupDeliveryModeSetting() {
        val isAllAtOnce = TriggerManager.isSosDeliveryAllAtOnce(this)
        binding.switchDeliveryMode.isChecked = isAllAtOnce
        updateDeliveryModeExplanation(isAllAtOnce)

        binding.switchDeliveryMode.setOnCheckedChangeListener { _, isChecked ->
            TriggerManager.setSosDeliveryAllAtOnce(this, isChecked)
            updateDeliveryModeExplanation(isChecked)
        }
    }

    private fun updateDeliveryModeExplanation(isAllAtOnce: Boolean) {
        if (isAllAtOnce) {
            binding.tvDeliveryExplanation.text = getString(R.string.sos_delivery_on_desc)
        } else {
            binding.tvDeliveryExplanation.text = getString(R.string.sos_delivery_off_desc)
        }
    }

    private fun setupAddGuardianButton() {
        binding.btnAddGuardian.setOnClickListener {
            val currentGuardians = TriggerManager.getAllGuardians(this)
            // If there's an empty slot among 1..3, target that slot, else append
            val targetSlot = if (currentGuardians.size < 3) currentGuardians.size else null
            showAddMethodDialog(targetSlot)
        }
    }

    /**
     * Requirement 2 & 3: Renders Guardian cards.
     * Ensures Guardian 1, Guardian 2, Guardian 3 are always displayed.
     * All 3 must be configured before guardian setup is considered complete.
     */
    private fun renderGuardiansList() {
        binding.llGuardiansContainer.removeAllViews()

        val guardians = TriggerManager.getAllGuardians(this).toMutableList()
        val configuredCount = guardians.count { it.phone.isNotBlank() }

        // Update top status banner
        if (configuredCount < 3) {
            binding.layoutStatusBanner.setBackgroundResource(R.drawable.bg_status_warning)
            binding.tvStatusIcon.text = "⚠️"
            binding.tvStatusText.text = "Kaiko requires at least 3 guardians. Configured: $configuredCount / 3.\nAll 3 must be configured before guardian setup is considered complete."
        } else {
            binding.layoutStatusBanner.setBackgroundResource(R.drawable.bg_status_success)
            binding.tvStatusIcon.text = "✅"
            binding.tvStatusText.text = "$configuredCount emergency guardians configured. Setup complete."
        }

        // We display at least 3 slots (Guardian 1, Guardian 2, Guardian 3)
        val totalSlots = maxOf(3, guardians.size)

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

                // Name: Optional, do not show "Unknown"
                if (guardian.name.isNotBlank()) {
                    cardBinding.tvGuardianName.visibility = View.VISIBLE
                    cardBinding.tvGuardianName.text = "👤 ${guardian.name.trim()}"
                } else {
                    cardBinding.tvGuardianName.visibility = View.GONE
                }

                // Relation: Optional, do not show "Unknown"
                if (guardian.relation.isNotBlank() && !guardian.relation.startsWith("Select Relation", ignoreCase = true)) {
                    cardBinding.tvGuardianRelation.visibility = View.VISIBLE
                    cardBinding.tvGuardianRelation.text = guardian.relation.trim()
                } else {
                    cardBinding.tvGuardianRelation.visibility = View.GONE
                }

                // Phone: Required
                cardBinding.tvGuardianPhone.text = guardian.phone

                // 📞 Call: Normal Android phone call action
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

                // 💬 Message: Normal Android SMS / Messages action
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

                // 👤+ Action: Open Contacts picker to change this guardian contact
                cardBinding.btnChangeContact.setOnClickListener {
                    pendingSlotIndex = slotIndex
                    handleChooseFromContacts()
                }

                // Remove: Enforce minimum 3 guardians restriction
                cardBinding.btnRemove.setOnClickListener {
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
     * Requirement 8: Edit / Remove Guardian.
     * Do NOT allow removal if it would leave fewer than 3 guardians.
     * Show: "Kaiko requires at least 3 guardians."
     */
    private fun handleRemoveGuardian(indexToRemove: Int) {
        val currentGuardians = TriggerManager.getAllGuardians(this).toMutableList()
        val configuredCount = currentGuardians.count { it.phone.isNotBlank() }

        if (configuredCount <= 3) {
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

    /**
     * Dialog to choose between choosing from contacts or manual add.
     */
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

    /**
     * Requirement 4: Choose from Phone Contacts.
     * Checks and requests permission with rationale if needed.
     */
    private fun handleChooseFromContacts() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            openNativeContactPicker()
        } else {
            // Explain briefly: "Allow Kaiko to access your contacts to select an emergency guardian."
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

    /**
     * Launches native Android Contacts picker.
     */
    private fun openNativeContactPicker() {
        try {
            val pickIntent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            pickContactLauncher.launch(pickIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open contacts: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Extracts name and phone number from contact URI, then opens Review/Edit dialog.
     */
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

        // Open Review/Edit dialog with prefilled data
        showAddEditGuardianDialog(
            targetSlotIndex = pendingSlotIndex,
            initialName = name,
            initialRelation = initialRelation,
            initialPhone = digits
        )
    }

    /**
     * Requirement 3, 5, 6: Add / Edit Guardian Form Dialog.
     * Name: Optional
     * Relation: Optional
     * Phone Number: Required
     */
    private fun showAddEditGuardianDialog(
        targetSlotIndex: Int?,
        initialName: String,
        initialRelation: String,
        initialPhone: String
    ) {
        val dialogBinding = DialogAddEditGuardianBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val isEditMode = targetSlotIndex != null &&
                targetSlotIndex < TriggerManager.getAllGuardians(this).size &&
                TriggerManager.getAllGuardians(this)[targetSlotIndex].phone.isNotBlank()

        dialogBinding.tvDialogTitle.text = if (isEditMode) {
            "Edit Guardian ${(targetSlotIndex ?: 0) + 1}"
        } else {
            val slotNum = if (targetSlotIndex != null) "${targetSlotIndex + 1}" else "${TriggerManager.getAllGuardians(this).size + 1}"
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

        // Select initial relation if present
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
            val currentGuardians = TriggerManager.getAllGuardians(this).toMutableList()
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
                currentGuardians.add(newGuardian)
            }

            // Filter out empty placeholder entries if any
            val cleanedList = currentGuardians.filter { it.phone.isNotBlank() }
            TriggerManager.saveGuardians(this, cleanedList)

            Toast.makeText(this, "Guardian saved successfully!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            renderGuardiansList()
        }

        dialog.show()
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        val digits = phone.replace(Regex("[^0-9]"), "")
        return digits.length == 10
    }
}
