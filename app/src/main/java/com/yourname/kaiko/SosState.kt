package com.yourname.kaiko

/**
 * Lifecycle states of an emergency SOS event.
 */
enum class SosState(val displayName: String) {
    IDLE("Ready (Idle)"),
    SOS_TRIGGERED("SOS Triggered"),
    INITIAL_ALERT_SENT("Initial Alert Sent"),
    WAITING_FOR_GUARDIAN_1("Waiting for Guardian 1"),
    GUARDIAN_1_ACKNOWLEDGED("Guardian 1 Acknowledged"),
    ESCALATE_TO_GUARDIAN_2("Escalating to Guardian 2"),
    WAITING_FOR_GUARDIAN_2("Waiting for Guardian 2"),
    GUARDIAN_2_ACKNOWLEDGED("Guardian 2 Acknowledged"),
    ESCALATE_TO_GUARDIAN_3("Escalating to Guardian 3"),
    WAITING_FOR_GUARDIAN_3("Waiting for Guardian 3"),
    GUARDIAN_3_ACKNOWLEDGED("Guardian 3 Acknowledged"),
    FINAL_ESCALATION_REQUIRED("Final Escalation Required"),
    USER_MARKED_SAFE("User Marked Safe");

    fun isActive(): Boolean {
        return this != IDLE && this != USER_MARKED_SAFE &&
                this != GUARDIAN_1_ACKNOWLEDGED &&
                this != GUARDIAN_2_ACKNOWLEDGED &&
                this != GUARDIAN_3_ACKNOWLEDGED
    }
}

/**
 * Configured Guardian Contact.
 */
data class GuardianContact(
    val priority: Int, // 1, 2, or 3
    val name: String,
    val phoneNumber: String,
    val isMandatory: Boolean = false
)

/**
 * Status of an SMS transmission.
 */
enum class SmsSendStatus {
    NOT_ATTEMPTED,
    SENDING_ATTEMPTED,
    SENT_TO_CARRIER,
    FAILED,
    RETRY_ATTEMPTED,
    FAILED_AFTER_RETRY
}
