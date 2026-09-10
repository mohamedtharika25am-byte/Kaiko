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
        assertTrue(msg.contains("🟢 KAIKO SOS UPDATE"))
        assertTrue(msg.contains("The user has marked themselves as safe."))
        assertTrue(msg.contains("Emergency escalation has been stopped."))
    }

    @Test
    fun testTestAckResolutionMessage() {
        val msg = TriggerManager.buildTestAckResolutionMessage()
        assertTrue(msg.contains("🧪 KAIKO TEST UPDATE"))
        assertTrue(msg.contains("The emergency test has been acknowledged"))
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

    @Test
    fun testNormalizeTriggerSourceVoice() {
        assertEquals(
            TriggerManager.TRIGGER_VOICE,
            TriggerManager.normalizeTriggerSource("voice")
        )
        assertEquals(
            TriggerManager.TRIGGER_VOICE,
            TriggerManager.normalizeTriggerSource("voice_trigger")
        )
        assertEquals(
            TriggerManager.TRIGGER_VOICE,
            TriggerManager.normalizeTriggerSource("Voice Safety Trigger")
        )
    }

    @Test
    fun testEmergencyMessageVoiceTrigger() {
        val msg = TriggerManager.buildEmergencyMessage(
            TriggerManager.TRIGGER_VOICE,
            TriggerManager.LocationStatus.CURRENT,
            "11.0168,76.9558"
        )
        assertTrue(msg.contains("🚨 KAIKO SOS ALERT"))
        assertTrue(msg.contains("Voice Safety Trigger"))
        assertTrue(msg.contains("📍 Current Location:"))
        assertTrue(msg.contains("https://maps.google.com/?q=11.0168,76.9558"))
    }

    @Test
    fun testVoiceTextNormalization() {
        assertEquals("i need help", VoiceTriggerManager.normalizeText("I need help!"))
        assertEquals("help me", VoiceTriggerManager.normalizeText("...Help ME???"))
        assertEquals("i am in danger", VoiceTriggerManager.normalizeText("  I AM   IN DANGER.  "))
        assertEquals("this is an emergency", VoiceTriggerManager.normalizeText("THIS IS AN EMERGENCY!"))
        assertEquals("send sos", VoiceTriggerManager.normalizeText("Send SOS"))
    }

    @Test
    fun testDefaultEmergencyPhrasesList() {
        val defaults = TriggerManager.getDefaultEmergencyPhrases()
        assertEquals(5, defaults.size)
        assertTrue(defaults.contains("I need help"))
        assertTrue(defaults.contains("I am in danger"))
        assertTrue(defaults.contains("Help me"))
        assertTrue(defaults.contains("This is an emergency"))
        assertTrue(defaults.contains("Send SOS"))
    }

    @Test
    fun testNormalizeTriggerSourcePowerButton() {
        assertEquals(
            TriggerManager.TRIGGER_POWER_BUTTON,
            TriggerManager.normalizeTriggerSource("power_button")
        )
        assertEquals(
            TriggerManager.TRIGGER_POWER_BUTTON,
            TriggerManager.normalizeTriggerSource("power_button_assistant")
        )
        assertEquals(
            TriggerManager.TRIGGER_POWER_BUTTON,
            TriggerManager.normalizeTriggerSource("assistant")
        )
        assertEquals(
            TriggerManager.TRIGGER_POWER_BUTTON,
            TriggerManager.normalizeTriggerSource("Power Button Trigger")
        )
    }

    @Test
    fun testEmergencyMessagePowerButtonTrigger() {
        val msg = TriggerManager.buildEmergencyMessage(
            TriggerManager.TRIGGER_POWER_BUTTON,
            TriggerManager.LocationStatus.CURRENT,
            "11.0168,76.9558"
        )
        assertTrue(msg.contains("🚨 KAIKO SOS ALERT"))
        assertTrue(msg.contains("Power Button Trigger"))
        assertTrue(msg.contains("📍 Current Location:"))
        assertTrue(msg.contains("https://maps.google.com/?q=11.0168,76.9558"))
    }

    @Test
    fun testGuardianConstants() {
        assertEquals(10, TriggerManager.MAX_GUARDIANS)
        assertEquals(3, TriggerManager.MIN_GUARDIANS)
    }

    @Test
    fun testMaxCustomVoicePhrases() {
        assertEquals(5, TriggerManager.MAX_CUSTOM_VOICE_PHRASES)
    }

    @Test
    fun testCustomPhraseNormalization() {
        val customPhrase = "Red Alert Emergency"
        val normalized = VoiceTriggerManager.normalizeText(customPhrase)
        assertEquals("red alert emergency", normalized)
        val spoken = "Please activate red alert emergency right now"
        val normalizedSpoken = VoiceTriggerManager.normalizeText(spoken)
        assertTrue(normalizedSpoken.contains(normalized))
    }
}
