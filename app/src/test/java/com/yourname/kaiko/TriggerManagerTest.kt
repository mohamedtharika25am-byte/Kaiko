package com.yourname.kaiko

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerManagerTest {

    @Test
    fun testNormalizeTriggerSource() {
        assertEquals(
            TriggerManager.TRIGGER_MANUAL_APP,
            TriggerManager.normalizeTriggerSource("manual_app_test")
        )
        assertEquals(
            TriggerManager.TRIGGER_MANUAL_APP,
            TriggerManager.normalizeTriggerSource("Manual SOS Trigger")
        )
        assertEquals(
            TriggerManager.TRIGGER_HARDWARE_BUTTON,
            TriggerManager.normalizeTriggerSource("volume_button")
        )
        assertEquals(
            TriggerManager.TRIGGER_HARDWARE_BUTTON,
            TriggerManager.normalizeTriggerSource("Hardware Button Trigger")
        )
        assertEquals(
            TriggerManager.TRIGGER_QUICK_ACCESS,
            TriggerManager.normalizeTriggerSource("widget")
        )
        assertEquals(
            TriggerManager.TRIGGER_QUICK_ACCESS,
            TriggerManager.normalizeTriggerSource("quick_settings_tile")
        )
        assertEquals(
            TriggerManager.TRIGGER_QUICK_ACCESS,
            TriggerManager.normalizeTriggerSource("Quick Access Trigger")
        )
        assertEquals(
            TriggerManager.TRIGGER_DISCREET_SAFETY,
            TriggerManager.normalizeTriggerSource("disguised_widget")
        )
        assertEquals(
            TriggerManager.TRIGGER_DISCREET_SAFETY,
            TriggerManager.normalizeTriggerSource("Discreet Safety Trigger")
        )
    }

    @Test
    fun testEmergencyMessageCurrentLocation() {
        val msg = TriggerManager.buildEmergencyMessage(
            TriggerManager.TRIGGER_MANUAL_APP,
            TriggerManager.LocationStatus.CURRENT,
            "11.0168,76.9558"
        )
        assertTrue(msg.contains("🚨 KAIKO SOS ALERT"))
        assertTrue(msg.contains("Manual SOS Trigger"))
        assertTrue(msg.contains("📍 Current Location:"))
        assertTrue(msg.contains("https://maps.google.com/?q=11.0168,76.9558"))
        assertTrue(msg.contains("— Sent via Kaiko"))
    }

    @Test
    fun testEmergencyMessageLastKnownLocation() {
        val msg = TriggerManager.buildEmergencyMessage(
            TriggerManager.TRIGGER_HARDWARE_BUTTON,
            TriggerManager.LocationStatus.LAST_KNOWN,
            "11.0168,76.9558"
        )
        assertTrue(msg.contains("Hardware Button Trigger"))
        assertTrue(msg.contains("📍 Last known location:"))
        assertTrue(msg.contains("https://maps.google.com/?q=11.0168,76.9558"))
    }

    @Test
    fun testEmergencyMessageLocationUnavailable() {
        val msg = TriggerManager.buildEmergencyMessage(
            TriggerManager.TRIGGER_DISCREET_SAFETY,
            TriggerManager.LocationStatus.UNAVAILABLE,
            null
        )
        assertTrue(msg.contains("Discreet Safety Trigger"))
        assertTrue(msg.contains("⚠️ Current location is unavailable."))
        assertFalse(msg.contains("https://maps.google.com"))
    }

    @Test
    fun testSafeResolutionMessage() {
        val msg = TriggerManager.buildSafeResolutionMessage()
        assertEquals("🟢 KAIKO UPDATE: The user is safe. Escalation stopped.", msg)
    }

    @Test
    fun testTestAckResolutionMessage() {
        val msg = TriggerManager.buildTestAckResolutionMessage()
        assertEquals("🧪 KAIKO TEST UPDATE: Emergency test acknowledged. Escalation stopped.", msg)
    }

    @Test
    fun testSosStateIsActive() {
        assertFalse(SosState.IDLE.isActive())
        assertFalse(SosState.USER_MARKED_SAFE.isActive())
        assertFalse(SosState.GUARDIAN_1_ACKNOWLEDGED.isActive())
        assertFalse(SosState.GUARDIAN_2_ACKNOWLEDGED.isActive())
        assertFalse(SosState.GUARDIAN_3_ACKNOWLEDGED.isActive())

        assertTrue(SosState.SOS_TRIGGERED.isActive())
        assertTrue(SosState.INITIAL_ALERT_SENT.isActive())
        assertTrue(SosState.WAITING_FOR_GUARDIAN_1.isActive())
        assertTrue(SosState.ESCALATE_TO_GUARDIAN_2.isActive())
        assertTrue(SosState.WAITING_FOR_GUARDIAN_2.isActive())
        assertTrue(SosState.ESCALATE_TO_GUARDIAN_3.isActive())
        assertTrue(SosState.WAITING_FOR_GUARDIAN_3.isActive())
        assertTrue(SosState.FINAL_ESCALATION_REQUIRED.isActive())
    }
}
