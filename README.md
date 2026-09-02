# Kaiko — Android Emergency Safety Prototype

**Kaiko** is an Android emergency distress and safety prototype built in Kotlin. It provides immediate, multi-channel distress triggers, multi-guardian escalation, and state machine management entirely on-device.

* **Current Version:** `v0.0.2`
* **Development Status:** Prototype / Testing Stage
* **Architecture:** 100% Local / On-Device Android implementation (Zero external backend, No Firebase/Firestore, No REST API, No cloud database, No web services).

---

> [!WARNING]
> **Prototype & Testing Notice:** Kaiko is currently an experimental prototype in active development for testing emergency workflow logic. Features such as "Guardian Acknowledgement" are provided via on-device simulation for testing the SOS state machine. It is not yet a certified production emergency response system.

---

## 🚀 Core Features

### 1. Emergency SOS Trigger
* **Hardware Button Trigger:** Background `AccessibilityService` listens for 3 consecutive presses of the physical `Volume Down` button within a 2000ms sliding window (with a 5-second anti-spam cooldown).
* Works across any application or while the device is on.

### 2. Home Screen SOS Widget
* **Direct Emergency Widget:** A 1x1 home screen widget (`KaikoWidgetProvider`) with high-visibility emergency styling for instant 1-tap activation.

### 3. Disguised / Neutral Widget
* **Daily Memo Widget:** An optional 1x1 neutral widget (`KaikoDisguisedWidgetProvider`) designed for discreet placement.
* Visually styled as a subtle productivity note ("Daily Memo") without visible emergency or SOS wording, while executing the identical SOS trigger workflow when tapped.

### 4. Multiple Guardians
* **Guardian 1 (Mandatory):** Primary emergency contact.
* **Guardian 2 (Optional):** Secondary emergency contact.
* **Guardian 3 (Optional):** Tertiary emergency contact.
* **Backup Helpline (Optional):** Configurable final contact field.
* Includes in-app phone number validation and duplicate number prevention.

### 5. Local SOS State Machine
Every emergency event is tracked through a structured, persistent state machine (`SosState`):
* `IDLE` — System ready and monitoring.
* `SOS_TRIGGERED` — Emergency alert initiated.
* `INITIAL_ALERT_SENT` — Location acquired and first dispatch prepared.
* `WAITING_FOR_GUARDIAN_1` — Alert sent to Guardian 1; awaiting response.
* `GUARDIAN_1_ACKNOWLEDGED` — Guardian 1 acknowledged (escalation halted).
* `ESCALATE_TO_GUARDIAN_2` / `WAITING_FOR_GUARDIAN_2` — Escalating to Guardian 2.
* `GUARDIAN_2_ACKNOWLEDGED` — Guardian 2 acknowledged (escalation halted).
* `ESCALATE_TO_GUARDIAN_3` / `WAITING_FOR_GUARDIAN_3` — Escalating to Guardian 3.
* `GUARDIAN_3_ACKNOWLEDGED` — Guardian 3 acknowledged (escalation halted).
* `FINAL_ESCALATION_REQUIRED` — All guardians exhausted without response.
* `USER_MARKED_SAFE` — User resolved the situation.

### 6. Escalation Chain
* When an SOS is triggered, Kaiko alerts Guardian 1 and initiates a local escalation countdown.
* If Guardian 1 does not acknowledge within the configured interval (configurable to 30s, 60s, or 120s for testing), Kaiko automatically escalates to Guardian 2, and then Guardian 3.
* If a guardian is not configured, the escalation chain intelligently skips to the next step.

```
SOS Trigger
   ↓
Alert Guardian 1
   ↓
Wait (30s / 60s / 120s)
   ↓ (No ack / Send failed)
Alert Guardian 2 (if configured)
   ↓
Wait (30s / 60s / 120s)
   ↓ (No ack / Send failed)
Alert Guardian 3 (if configured)
   ↓
Final Escalation State
```

### 7. "I'm Safe Now" (Cancel / Stop Action)
* When an emergency is active, an **"I'm Safe Now"** action is accessible from both the in-app banner and the ongoing system notification.
* Tapping this immediately cancels pending `AlarmManager` escalation timers and transitions the state to `USER_MARKED_SAFE`.
* Existing event history and previously dispatched logs are preserved for auditing.

### 8. SMS Status & Failure Handling with Retry
* SMS transmissions are monitored through carrier dispatch confirmation (`SmsSentReceiver`).
* If carrier dispatch fails, the system automatically retries **once** after a 3-second delay.
* If the retry also fails, Kaiko **immediately advances** to the next guardian in the escalation chain rather than waiting out the full timeout.
* *Note: Transmission status indicates that the message was accepted by the cellular carrier network; it does not guarantee recipient read delivery.*

### 9. Guardian Acknowledgement Simulation [TEST / PROTOTYPE ONLY]
* In the current local prototype, guardian acknowledgement is simulated locally via the **"Simulate Guardian Ack [TEST ONLY]"** button in the app and ongoing notification.
* This allows developers and testers to verify that acknowledgement successfully halts escalation without requiring a secondary cellular device or cloud server.

### 10. Lock-Screen Quick Access
* **Quick Settings Tile:** Supports Android's Quick Settings shade (`KaikoTileService`). Users can pull down the quick settings shade directly from the lock screen and tap "Quick Alert" for 1-tap triggering.
* **Public Ongoing Notification:** An ongoing notification with `VISIBILITY_PUBLIC` remains active during an SOS event, exposing "I'm Safe Now" and test actions on the secure lock screen.
* *Platform Note: Android 5.0+ restricts arbitrary third-party interactive home-screen widgets directly on system lock screens. The Quick Settings Tile and Public Notification represent the closest officially supported Android mechanisms.*

### 11. 100% Local-Only Architecture
* Runs completely on-device using Android native components (`SharedPreferences`, `AlarmManager`, `BroadcastReceiver`, `FusedLocationProviderClient`, and `SmsManager`).
* No network server, cloud database, or third-party authentication required.

---

## 📦 APK Versioning

Kaiko follows sequential application-level versioning. Each generated APK is versioned sequentially and represents the overall application update:

* `v0.0.1` — Initial prototype.
* `v0.0.2` — Multi-guardian escalation, state machine, and disguised widget update.
* `v0.0.3` — Sequential future update.

### Release APK Artifact Naming
Release builds automatically generate version-named APK artifacts:
```
Kaiko-v<versionName>.apk
```
* Current output artifact: `app/build/outputs/apk/release/Kaiko-v0.0.2.apk`

---

## 📁 Project Structure

```
Kaiko/
├── build.gradle.kts                            # Root Gradle build script
├── settings.gradle.kts                         # Project module definitions
├── gradle.properties                           # JVM and AndroidX settings
└── app/
    ├── build.gradle.kts                        # App build configuration, versionCode 2, versionName "0.0.2"
    ├── proguard-rules.pro                      # Proguard configuration
    └── src/
        └── main/
            ├── AndroidManifest.xml             # Components, permissions, and receiver declarations
            ├── java/com/yourname/kaiko/
            │   ├── MainActivity.kt             # Multi-guardian UI, delay selector, and active SOS banner
            │   ├── TriggerManager.kt           # Central orchestrator, escalation chain, & SMS dispatcher
            │   ├── SosState.kt                 # 13-state emergency lifecycle enum and data models
            │   ├── KaikoAccessibilityService.kt# Background listener for 3x Volume-Down key presses
            │   ├── KaikoWidgetProvider.kt      # Direct 1x1 Home Screen SOS Widget provider
            │   ├── KaikoDisguisedWidgetProvider.kt # Neutral Disguised "Daily Memo" widget provider
            │   ├── KaikoTileService.kt         # Quick Settings Tile for lock-screen pull-down access
            │   ├── EscalationReceiver.kt       # AlarmManager receiver for escalation timeouts
            │   ├── SmsSentReceiver.kt          # Carrier SMS result handler with single-retry logic
            │   └── ActionReceiver.kt           # Notification action handler ("I'm Safe Now" & Test Ack)
            └── res/
                ├── drawable/
                │   ├── bg_button_primary.xml   # Primary action button background
                │   ├── bg_button_secondary.xml # Secondary action button background
                │   ├── bg_card.xml             # Slate card container background
                │   ├── bg_input.xml            # Phone input box background
                │   ├── ic_sos.xml              # SOS icon
                │   ├── ic_memo.xml             # Neutral memo icon for disguised widget
                │   ├── widget_background.xml   # 16dp rounded corner SOS widget background
                │   └── widget_disguised_background.xml # Neutral disguised widget background
                ├── layout/
                │   ├── activity_main.xml       # Scrollable configuration and emergency dashboard
                │   ├── widget_layout.xml       # Direct SOS widget layout
                │   └── widget_disguised_layout.xml # Disguised "Daily Memo" widget layout
                ├── values/
                │   ├── colors.xml              # Dark modern UI palette
                │   ├── strings.xml             # UI labels, widget titles, and descriptions
                │   └── themes.xml              # App theme definitions
                └── xml/
                    ├── accessibility_service_config.xml # Key event filtering service configuration
                    ├── widget_info.xml                  # Direct SOS widget metadata
                    └── widget_disguised_info.xml        # Disguised widget metadata
```

---

## 🛡️ Permissions & Rationale

| Permission | Purpose |
| :--- | :--- |
| `android.permission.SEND_SMS` | Dispatches distress SMS with location coordinates to configured guardians. |
| `android.permission.ACCESS_FINE_LOCATION` | Queries high-accuracy GPS coordinates via `FusedLocationProviderClient`. |
| `android.permission.ACCESS_COARSE_LOCATION` | Fallback approximate location if GPS signal is weak indoors. |
| `android.permission.POST_NOTIFICATIONS` | Displays persistent lock-screen status and action notifications (Android 13+). |
| `android.permission.VIBRATE` | Provides tactile feedback when emergency triggers are activated. |
| `android.permission.BIND_ACCESSIBILITY_SERVICE` | Required by Android to register the hardware button listener. |
| `android.permission.BIND_QUICK_SETTINGS_TILE` | Enables the lock-screen Quick Settings Tile. |

---

## ⚙️ Safe Testing: DEBUG_MODE

In `TriggerManager.kt`, safe testing is controlled by:
```kotlin
// Set to true for safe testing (no real SMS sent).
// Set to false to send actual SMS alerts.
const val DEBUG_MODE = false
```

* **When `DEBUG_MODE = true`:**
  * No real cellular SMS messages are sent (prevents carrier fees during testing).
  * SMS payloads and coordinates are logged to Logcat under tag `KAIKO_DEBUG`.
  * The state machine, notifications, and escalation timers run normally.
* **When `DEBUG_MODE = false`:**
  * Real SMS messages are dispatched to configured guardians via `SmsManager`.

---

## 📱 Setup and Testing Guide

### 1. Build and Deploy
1. Clone this repository:
   ```bash
   git clone https://github.com/mohamedtharika25am-byte/Kaiko.git
   ```
2. Open the project in **Android Studio**.
3. Connect an Android device with **USB Debugging** enabled.
4. Run the app (`Shift + F10`) or install the release APK:
   ```bash
   adb install app/build/outputs/apk/release/Kaiko-v0.0.2.apk
   ```

### 2. Initial Configuration
1. Enter **Guardian 1** phone number (Mandatory).
2. Optionally enter **Guardian 2**, **Guardian 3**, and a **Backup Helpline**.
3. Select an **Escalation Delay** (choose **30s** or **60s** for testing).
4. Tap **"Save & Enable"** and grant runtime permissions (SMS, Location, Notifications).
5. Tap **"Enable Accessibility Service"** -> In Android Settings, turn **Kaiko Emergency Trigger Listener** ON.

### 3. Verification Steps
* **Hardware Trigger:** Press the physical **Volume Down** button 3 times within 2 seconds. Verify that the SOS banner appears and the countdown starts.
* **Direct Widget:** Add the "Kaiko SOS" widget to the home screen and tap it.
* **Disguised Widget:** Add the "Daily Memo" widget to the home screen and tap it. Verify that it triggers the emergency workflow without displaying emergency branding.
* **Escalation Countdown:** Observe the state machine advance from Guardian 1 to Guardian 2 after the timeout expires.
* **Simulated Ack:** Tap **"Simulate Guardian Ack [TEST ONLY]"** to verify that escalation immediately stops.
* **"I'm Safe Now":** Trigger an alert and tap **"I'm Safe Now"** to confirm that pending escalation is safely cancelled and the status transitions to `USER_MARKED_SAFE`.

---

## 🔍 Logcat Monitoring

Filter Logcat output in Android Studio using the tag:
```
KAIKO_DEBUG
```

---

## 📜 Version History

### `v0.0.1`
* Initial Kaiko emergency safety prototype.
* Triple-press Volume Down hardware trigger via background Accessibility Service.
* High-accuracy Fused Location Provider with 3-second timeout.
* Direct 1x1 Home Screen SOS widget.
* Single guardian SMS dispatch with `DEBUG_MODE` support.

### `v0.0.2`
* Upgraded to sequential versioning (`versionCode = 2`, `versionName = "0.0.2"`).
* Multiple Guardian support: Guardian 1 (mandatory), Guardian 2 (optional), Guardian 3 (optional), and backup helpline.
* Local 13-state emergency SOS state machine (`SosState`).
* Automatic background escalation chain with configurable timeouts (30s, 60s, 120s).
* "I'm Safe Now" cancel action in app and ongoing notification.
* SMS carrier send status tracking with single-retry and immediate escalation on retry failure.
* Simulated Guardian Acknowledgement for on-device state machine testing.
* Disguised / Neutral "Daily Memo" 1x1 home screen widget.
* Android Quick Settings Tile for lock-screen quick access.
* 100% local, self-contained Android architecture.
