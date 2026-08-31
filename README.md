# Kaiko — Android Emergency Alert & Hardware Trigger Prototype

**Kaiko** is a minimal, production-structured Android demo application in Kotlin designed for real-device testing of emergency distress triggers.

It features two trigger methods dispatching to a shared trigger manager:
1. **Hardware Button Triple-Press Trigger**: Monitored via a background `AccessibilityService` (`KEYCODE_VOLUME_DOWN` pressed 3 times within 2000ms with a 5-second anti-spam cooldown).
2. **1x1 Home Screen SOS Widget**: Instant single-tap emergency trigger via `AppWidgetProvider`.

When activated, the shared `TriggerManager` fetches high-accuracy GPS coordinates (with a 3-second timeout), formats an emergency message with raw coordinates and a Google Maps link, sends an SMS to the guardian's phone number, posts a confirmation notification, and outputs detailed structured logs to Logcat.

---

## 📁 Project Structure

```
Kaiko/
├── build.gradle.kts                      # Root Gradle build script
├── settings.gradle.kts                   # Project module definitions
├── gradle.properties                     # JVM and AndroidX configuration
└── app/
    ├── build.gradle.kts                  # App build configuration & dependencies
    ├── proguard-rules.pro                # Proguard rules
    └── src/
        └── main/
            ├── AndroidManifest.xml       # Manifest with components and permission declarations
            ├── java/com/yourname/kaiko/
            │   ├── MainActivity.kt               # Onboarding UI, permission requests, & status check
            │   ├── KaikoAccessibilityService.kt  # Background listener for 3x Volume-Down presses
            │   ├── KaikoWidgetProvider.kt        # 1x1 Home Screen SOS Widget provider
            │   └── TriggerManager.kt             # Shared trigger action, GPS, SMS, & Notification
            └── res/
                ├── drawable/
                │   ├── bg_button_primary.xml     # Gradient primary button
                │   ├── bg_button_secondary.xml   # Secondary action button
                │   ├── bg_card.xml               # Modern slate card background
                │   ├── bg_input.xml              # Text input container background
                │   ├── bg_widget.xml             # Radial glowing SOS widget circle
                │   ├── ic_shield.xml             # Shield icon
                │   └── ic_sos.xml                # SOS vector icon
                ├── layout/
                │   ├── activity_main.xml         # Clean single-screen onboarding layout
                │   └── widget_layout.xml         # 1x1 SOS Widget layout
                ├── values/
                │   ├── colors.xml                # Dark modern palette
                │   ├── strings.xml               # UI text and accessibility descriptions
                │   └── themes.xml                # Material theme definition
                └── xml/
                    ├── accessibility_service_config.xml # Key event filtering service config
                    └── widget_info.xml                  # 1x1 AppWidgetProvider metadata
```

---

## 🛡️ Permissions & Rationale

| Permission | Reason Needed |
| :--- | :--- |
| `android.permission.SEND_SMS` | Dispatches the emergency SMS message containing coordinates directly to the guardian's phone number. |
| `android.permission.ACCESS_FINE_LOCATION` | Queries high-accuracy GPS coordinates (`latitude`, `longitude`) via `FusedLocationProviderClient`. |
| `android.permission.ACCESS_COARSE_LOCATION` | Provides fallback cell/Wi-Fi approximate location if GPS signal is weak indoors. |
| `android.permission.POST_NOTIFICATIONS` | Allows displaying confirmation notifications on Android 13+ (API level 33+). |
| `android.permission.BIND_ACCESSIBILITY_SERVICE` | Required system permission to register `KaikoAccessibilityService` as a hardware key listener. |

---

## ⚙️ Debug Mode & Testing Safely

In `TriggerManager.kt`, you will find:
```kotlin
const val DEBUG_MODE = true
```

- **When `DEBUG_MODE = true` (Default):**
  - **No carrier SMS is sent** (prevents carrier fees or accidental real alerts during testing).
  - The exact message and coordinates that *would* have been sent are logged to Logcat.
  - The confirmation notification **is still shown**, allowing full visual verification on your phone.
- **When `DEBUG_MODE = false`:**
  - Sends a real SMS to the configured guardian number via `SmsManager`.

---

## 📱 How to Test on a Physical Android Device

### Step 1: Open in Android Studio & Deploy
1. Open Android Studio -> **File** -> **Open** -> Select `d:\Kaiko`.
2. Connect your physical Android phone with **USB Debugging** enabled.
3. Click **Run 'app'** (`Shift + F10`).

### Step 2: Configure Onboarding
1. Enter your guardian's phone number (e.g. `+15551234567`).
2. Tap **"Save & Enable"** -> Grant the requested runtime permissions (**Location**, **SMS**, and **Notifications**).
3. Tap **"Enable Accessibility Service"** -> In Android Settings, find **Kaiko Emergency Trigger Listener** and toggle it **ON**.
4. Return to the Kaiko app and verify the status text reads `● Accessibility Service: Enabled` in green.

### Step 3: Test Hardware Trigger (Triple Volume-Down)
1. Even with the screen locked or inside any other app, press the physical **Volume Down** button **3 times within 2 seconds**.
2. Notice the notification appears: **"Help alert sent"** displaying your current GPS coordinates.
3. Try pressing Volume Down again immediately — observe the 5-second cooldown preventing duplicate triggers.

### Step 4: Test Home Screen Widget
1. Long-press on your phone's Home Screen -> Select **Widgets**.
2. Find **Kaiko** and drag the 1x1 **SOS** widget onto your home screen.
3. Tap the **SOS** widget once.
4. The same trigger fires instantly, acquiring location and showing the confirmation notification.

---

## 🔍 Logcat Debugging

Filter your Logcat in Android Studio using the tag:
```
KAIKO_DEBUG
```

**Example Log Output:**
```
D/KAIKO_DEBUG: Volume Down pressed: count = 1 / 3 in sliding window
D/KAIKO_DEBUG: Volume Down pressed: count = 2 / 3 in sliding window
D/KAIKO_DEBUG: Volume Down pressed: count = 3 / 3 in sliding window
D/KAIKO_DEBUG: --> TRIGGER CONDITION MET: 3 Volume Down presses in < 2000ms. Dispatching alert!
D/KAIKO_DEBUG: --------------------------------------------------
D/KAIKO_DEBUG: [2026-08-31 18:00:00] EMERGENCY TRIGGER ACTIVATED from source: 'volume_button'
D/KAIKO_DEBUG: Location acquired successfully: 37.4220, -122.0841 (accuracy: 12.0m)
I/KAIKO_DEBUG: [DEBUG MODE ACTIVE] Would have sent SMS to +15551234567: "I need help. My location: 37.4220, -122.0841 https://maps.google.com/?q=37.4220,-122.0841"
D/KAIKO_DEBUG: System notification posted successfully.
D/KAIKO_DEBUG: Trigger Event Summary: timestamp=1788180000000 (2026-08-31 18:00:00), source=volume_button, locationSuccess=true, coords=37.4220, -122.0841, smsSuccess=SKIPPED_DEBUG_MODE
D/KAIKO_DEBUG: --------------------------------------------------
```
