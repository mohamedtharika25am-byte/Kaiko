# Changelog

All notable changes to the Kaiko application are documented in this file.

## [v1.8.2] — 2026-09-10 23:55 IST
> **Build Status:** ✅ PASSED (`testDebugUnitTest`, `assembleRelease`)  
> **Release Artifact:** `release/v1.8.2/Kaiko-v1.8.2.apk`  
> **Target SDK:** 34 (Android 14) | **Min SDK:** 26 (Android 8.0+) | **Version Code:** 18  

### Dedicated Voice Trigger Screen Fix & Top Bar Navigation
- **Configure Voice Trigger Navigation Fix**:
  - Resolved an auto-close loop in `VoiceTriggerActivity` where setting `selectedItemId = R.id.nav_settings` in `onResume()` unintentionally triggered the bottom navigation listener's `finish()` action.
  - Corrected listener attachment sequence to ensure programmatic tab selection does not trigger screen exit.
  - Tapping **CONFIGURE VOICE TRIGGER** on the Settings card now smoothly and reliably opens the dedicated Voice Trigger configuration screen.
- **Top App Bar Navigation**:
  - Wired top bar back button (🔙) in `VoiceTriggerActivity` to smoothly return to Settings.
  - Preserved direct bottom navigation to Emergency and Guardians screens from Voice Trigger screen.

## [v1.8.1] — 2026-09-10 23:30 IST
> **Build Status:** ✅ PASSED (`testDebugUnitTest`, `assembleRelease`)  
> **Release Artifact:** `release/v1.8.1/Kaiko-v1.8.1.apk`  
> **Target SDK:** 34 (Android 14) | **Min SDK:** 26 (Android 8.0+) | **Version Code:** 17  

### Centered SOS, Primary Call Action, 3-Tab Bottom Nav, Escalation Timeouts & Compact Guardian Actions
- **Home Page**:
  - Main SOS button is now exactly centered vertically and horizontally within the viewport content area.
  - Added dedicated "CALL PRIMARY GUARDIAN" compact pill-shaped button (48dp height, 220dp width) directly below the SOS button.
  - Dynamic display of Guardian 1's saved name (e.g. "Call Amma", falling back to "Call Primary Guardian").
  - Direct 1-tap phone dial action via Android system dialer without triggering or modifying the SOS pipeline.
- **Bottom Navigation**:
  - Removed "Voice" tab completely from the persistent bottom navigation bar.
  - Simplified to 3 essential tabs: Emergency, Guardians, Settings.
  - Retained full access to Voice Trigger from Settings.
- **Settings Page**:
  - Elevated "Voice Trigger" to the FIRST/topmost settings card.
  - Real-time status badge (ON/OFF) and custom phrase count (e.g. 0/5) with direct navigation to the dedicated Voice Trigger configuration screen.
- **Guardians Page**:
  - Fixed setup status banner padding and layout to eliminate text cut-off ("X emergency guardians configured. Setup completed").
  - Moved the SOS Delivery box to the TOP of the page above the guardian cards.
  - Added full Escalation Mode options: "Escalation Mode (Sequential)" vs "Send to all at once".
  - In Sequential mode, added interactive timeout chips: 30s | 60s (Default) | 120s connected to existing escalation logic.
  - Replaced oversized text buttons [ CALL ] and [ MESSAGE ] with sleek, compact modern Material icon actions (📞 and 💬).
- **Quality & Preservation**:
  - Maintained clean white/light theme and color hierarchy.
  - Preserved all existing triggers, permissions, widgets, background services, accessibility, and emergency flows.

## [v1.8.0] — 2026-09-10 16:30 IST
> **Build Status:** ✅ PASSED (`testDebugUnitTest`, `assembleRelease`)  
> **Release Artifact:** `release/v1.8.0/Kaiko-v1.8.0.apk`  
> **Target SDK:** 34 (Android 14) | **Min SDK:** 26 (Android 8.0+) | **Version Code:** 16  

### Separate Pages, Bottom Navigation, Custom Phrases & Professional UI
- **Dedicated Voice Trigger Page (`VoiceTriggerActivity`)**:
  - Moved detailed Voice Trigger configuration from Home into its own dedicated page.
  - Kept Voice Trigger ON/OFF control with real-time status indication and microphone permission handling.
  - Preserved all 5 built-in emergency phrases ("I need help", "I am in danger", "Help me", "This is an emergency", "Send SOS").
  - Added support for up to 5 text-input based CUSTOM emergency phrases.
  - User can add, edit, and remove custom emergency phrases dynamically.
  - Real-time custom phrase counter (0/5, 1/5 ... 5/5) preventing more than 5 custom phrases.
  - Strict validation preventing blank and duplicate phrases (case-insensitive check against custom & default phrases).
  - Stored locally via existing `SharedPreferences` architecture (`kaiko_preferences`).
  - Custom and default phrases route directly to the existing central SOS flow (`TriggerManager.fireAlert(context, TRIGGER_VOICE)`).
  - Unwanted recognition feedback addressed using Android-supported `SpeechRecognizer` lifecycle reuse, on-device recognition where supported (API 33+), dictation mode hints, extended silence windows, and eliminating aggressive teardown/re-creation loops without altering system audio streams.
- **Compact Home Page with Expandable System Status**:
  - Kept main SOS button centered, prominent, and uncluttered.
  - Compact Home removing detailed Voice Trigger and Guardian lists.
  - Expandable/collapsible System Status section (Google-style question/details UI):
    - Collapsed state displays only the "SYSTEM STATUS" heading and expand chevron (`ic_expand_more`).
    - Expanded state displays existing status items (Location Permission, Location Toggle, Accessibility Service, Guardians Configured, Voice Trigger, Power Button SOS).
    - Preserved all existing live indicators and tap actions.
- **Persistent Bottom Navigation Bar**:
  - Added persistent bottom navigation across 4 primary sections:
    - 🛡️ **Emergency** (`MainActivity`)
    - 🎙️ **Voice** (`VoiceTriggerActivity`)
    - 👥 **Guardians** (`ManageGuardiansActivity`)
    - ⚙️ **Settings** (`SettingsActivity`)
  - Clean Material vector icons (`ic_nav_emergency`, `ic_nav_voice`, `ic_nav_guardians`, `ic_nav_settings`), no emojis.
  - Highlighted active page with seamless transitions.
- **Dedicated Settings Page (`SettingsActivity`)**:
  - Power Button SOS configuration with "MANAGE DEFAULT ASSISTANT" button.
  - Accessibility Setup card with step-by-step instructions.
  - User Guide access link.
  - Version info and status display.
- **Guardians Capacity Expansion (Up to 10)**:
  - Increased maximum guardians from 3 to 10.
  - Minimum 3 guardians remains compulsory.
  - Prevented adding an 11th guardian (action disabled at 10/10 capacity).
  - Responsive setup-complete banner layout preventing text truncation across screen sizes:
    `X emergency guardians configured.`
    `Setup completed`
- **Consistent Edit / Remove Icon Styling (4th Professional Style)**:
  - Unified icon style applied consistently across Guardian management and Custom Voice Phrases:
    - **Edit**: Small neutral grey pencil icon (`ic_edit`).
    - **Remove**: Small red delete/trash icon (`ic_delete`).
    - Standardized 36×36dp touch target with 20dp vector icon and consistent spacing everywhere.
    - Eliminated large "Remove Guardian" text buttons and emoji action icons.
- **Power Button SOS Confirmation Dialog**:
  - Compact, properly sized confirmation dialog card.
  - Clear header: "Send Emergency SOS?"
  - Subtitle: "Power button long press detected."
  - Compact horizontal action buttons: `CANCEL` | `SEND SOS`.
  - "MANAGE DEFAULT ASSISTANT" directly opens the actual Android system Default Assistant settings (`Settings.ACTION_VOICE_INPUT_SETTINGS` with supported fallbacks).
- **UI Consistency & Architecture Preservation**:
  - Preserved existing clean White/Light design system and color palette.
  - Zero modifications to core SOS pipeline or escalation timing.
  - Maintained all prior release APKs.

---

## [v1.7.0] — 2026-09-09 18:30 IST
> **Build Status:** ✅ PASSED (`testDebugUnitTest`, `assembleRelease`)  
> **Release Artifact:** `release/v1.7.0/Kaiko-v1.7.0.apk`  
> **Target SDK:** 34 (Android 14) | **Min SDK:** 26 (Android 8.0+) | **Version Code:** 15  

### Power Button Long Press SOS & Digital Assistant Integration
- **Power Button Trigger Integration**:
  - Registered Kaiko as an Android Digital Assistant service (`KaikoVoiceInteractionService`).
  - Added standalone lock-screen-capable `PowerButtonSosActivity`.
  - Added Power Button SOS status item in System Status.

---

## [v1.6.0] — 2026-09-08 19:15 IST
> **Build Status:** ✅ PASSED (`testDebugUnitTest`, `assembleRelease`)  
> **Release Artifact:** `release/v1.6.0/Kaiko-v1.6.0.apk` (5.25 MB)  
> **Target SDK:** 34 (Android 14) | **Min SDK:** 26 (Android 8.0+) | **Version Code:** 14  

### Voice Trigger Safety Feature & White/Light Design System
- **Voice Trigger Safety Feature**:
  - Added Voice Trigger ON/OFF control with real-time status indication.
  - Emergency phrases trigger directly without any wake word ("Hey Kaiko").
  - Evaluates default emergency phrases:
    - "I need help"
    - "I am in danger"
    - "Help me"
    - "This is an emergency"
    - "Send SOS"
  - Added Custom Voice Phrase support:
    - Entered via text input field (dialog) instead of voice recording.
    - User can enable/disable, add/change, and remove custom phrase at any time.
    - Stored locally using existing local SharedPreferences storage (`kaiko_preferences`).
  - Integrates directly into existing central SOS pipeline (`TriggerManager.fireAlert(context, TRIGGER_VOICE)`) without modifying existing SOS, SMS, location, notification, or escalation logic.
  - Built using standard Android `SpeechRecognizer` and `RecognitionListener` APIs with graceful error handling and retry loops.
  - Clearly reports Android OS background microphone privacy limitations (while-in-use policy; active while Kaiko is open on screen).
- **Clean White/Light Theme & Consistent Color System**:
  - **White**: Main app background, surfaces, cards, and normal content areas.
  - **Blue**: Brand UI, headings, navigation back buttons, icons, and regular action buttons (`👥 MANAGE GUARDIANS`, `MANAGE ACCESSIBILITY`, `+ ADD GUARDIAN`, etc.).
  - **Red**: Emergency elements ONLY (Main circular SOS button, `🚨 EMERGENCY` active SOS action, active emergency status indicators, removal actions).
  - **Green**: Safe/success states ONLY (`🟢 I'M SAFE` active SOS action, successful guardian configuration confirmation).
  - **Grey**: Secondary descriptions, hints, dividers, borders, disabled status items, and neutral/secondary actions (`🧪 TEST`, `CANCEL`).
  - **Light Blue Cards/Boxes**: Normal settings, features, and info (Voice Trigger card, System Status, Accessibility setup, SOS Delivery Mode, and User Guide sections).
  - **Very Light Red Cards/Boxes**: Emergency/SOS-related sections ONLY (`cardActiveSos` active emergency controls).
- **Preserved Existing Features**:
  - Top Circular SOS button, Volume Down 3× trigger, Normal widget, Discreet safety widget, GPS/Location handling, Guardian management, SOS delivery modes, sequential escalation, active SOS notifications, and Accessibility Service completely intact.
- **Release Artifact**:
  - Created `release/v1.6.0/Kaiko-v1.6.0.apk` while preserving all prior APK releases.

---

## [v1.5.1] — 2026-09-07 22:45 IST
> **Build Status:** ✅ PASSED (`testDebugUnitTest`, `assembleRelease`)  
> **Release Artifact:** `release/v1.5.1/Kaiko-v1.5.1.apk`  
> **Target SDK:** 34 (Android 14) | **Min SDK:** 26 (Android 8.0+) | **Version Code:** 13  

### Home Manage Guardians Button & 👤+ Contact Picker Action
- **Home Screen Manage Guardians Button**:
  - Added a prominent `👥 MANAGE GUARDIANS` button directly below the existing `MANAGE ACCESSIBILITY` section.
  - Tapping this button opens the dedicated Manage Guardians page.
- **Edit Guardian via 👤+ Action**:
  - Replaced the previous `Edit` button on Guardian Cards with a green `👤+` contact action icon positioned directly on the contact row matching the reference design.
  - Tapping `👤+` directly opens the native Android Contacts picker to choose/change the emergency guardian.
- **Strict 10-Digit Phone Number Requirement**:
  - Enforced that emergency guardian phone numbers must contain exactly 10 digits across manual entry, contact selection, and edit.
- **Release Artifact**:
  - Generated `release/v1.5.1/Kaiko-v1.5.1.apk` while preserving all previous releases.

---

## [v1.5.0] — 2026-09-07 19:50 IST
> **Build Status:** ✅ PASSED (`testDebugUnitTest`, `assembleRelease`)  
> **Release Artifact:** `release/v1.5.0/Kaiko-v1.5.0.apk`  
> **Target SDK:** 34 (Android 14) | **Min SDK:** 26 (Android 8.0+) | **Version Code:** 12  

### Dedicated Manage Guardians Page & SOS Delivery Mode
- **Manage Guardians Second Page**:
  - Created dedicated `ManageGuardiansActivity` with minimum 3 guardians setup requirement.
  - Guardian Cards displaying optional Name and Relation (no "Unknown" label), phone number, `📞 Call`, and `💬 Message` actions.
  - Native Android Contacts picker integration with permission rationale.
  - Guardian removal restriction enforcing minimum 3 guardians.
- **SOS Delivery Mode (Simultaneous vs Escalation)**:
  - Added toggle inside Manage Guardians to send SOS alert to all guardians simultaneously (ON) or follow existing sequential timeout escalation (OFF).
- **Release Artifact**:
  - Generated `release/v1.5.0/Kaiko-v1.5.0.apk` preserving all prior releases.

---

## [v1.4.2] — 2026-09-07 10:21 IST
> **Build Status:** ✅ PASSED (`testDebugUnitTest`, `testReleaseUnitTest`, `assembleRelease`)  
> **Release Artifact:** `release/v1.4.2/Kaiko-v1.4.2.apk` (5.2 MB)  
> **Target SDK:** 34 (Android 14) | **Min SDK:** 26 (Android 8.0+) | **Version Code:** 11  

### Dynamic Active SOS Notification & Content Tap Navigation
- **Android Notification 3-Action Button Limit Fix**:
  - Android OS strictly caps visible notification actions to a maximum of 3 buttons.
  - Dynamically resolved notification action slot 2:
    - **If next guardian exists** (next configured guardian or final helpline): Shows `🚨 EMERGENCY` (immediately advances escalation chain).
    - **If NO next guardian exists** (all guardians exhausted or no next guardian configured): Shows `🧪 TEST` instead (stops escalation test safely without sending SMS).
  - **Button 1**: Always `🟢 I'M SAFE` (stops escalation and notifies alerted guardians).
  - **Button 3**: Always `⚠️ MISTOUCHED` (cancels false alarm and sends dedicated mistouched update).
- **Notification Body Tap Navigation (`contentIntent`)**:
  - Tapping the notification body opens Kaiko `MainActivity` directly with `FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP`.
  - Gives instant on-screen access to all 4 Active SOS control buttons (`🟢 I'M SAFE`, `🚨 EMERGENCY`, `⚠️ MISTOUCHED`, `🧪 TEST`).
- **Release Artifact**: Generated `release/v1.4.2/Kaiko-v1.4.2.apk` while preserving all prior APK releases.

---

## [v1.4.1] — 2026-09-07 09:11 IST
> **Build Status:** ✅ PASSED (`testDebugUnitTest`, `testReleaseUnitTest`)  
> **Release Artifact:** `release/v1.4.1/Kaiko-v1.4.1.apk` (5.2 MB)  
> **Target SDK:** 34 (Android 14) | **Min SDK:** 26 (Android 8.0+) | **Version Code:** 10  

### Location + SOS Notification + System Status + Accessibility UI Fixes
- **Location Permission + GPS Flow Fix**:
  - When both Permission and GPS Toggle are OFF, Kaiko Location Permission is requested FIRST.
  - If granted, directly prompts the native Google/Android GPS toggle resolution dialog.
  - If user turns GPS ON, re-checks and sends SOS with Google Maps coordinate link.
  - If user denies/ignores permission, waits maximum 5s and sends SOS without location.
  - If permission is already ON, skips permission request and directly checks GPS toggle.
  - If GPS is already ON, immediately acquires current location and dispatches SOS with Maps link.
  - Fixed transparent host activity by removing `windowIsFloating` and `noHistory` so permission dialogs are never blocked.
- **Active SOS Notification Update (4 Buttons)**:
  - Added dedicated Mistouched message: *"⚠️ KAIKO UPDATE: SOS was triggered by mistake. No emergency. Escalation stopped."* sent only to alerted guardians.
- **System Status Location Split**:
  - Split into 2 independently clickable items:
    - 📍 **Location Permission** (ON/OFF, tap to manage permission)
    - 📡 **Location Toggle** (ON/OFF, tap to manage Android Location Services / GPS)
- **Accessibility Setup Path 2-Line Layout**:
  - Formatted into exactly 2 readable lines:
    - Line 1: `Settings → Accessibility → Downloaded Apps`
    - Line 2: `→ Kaiko Emergency Trigger Listener → ON`
- **Release Artifact**: Preserved all prior releases and generated `release/v1.4.1/Kaiko-v1.4.1.apk`.

---

## [v1.4.0] — 2026-09-06 23:03 IST
> **Build Status:** ✅ PASSED (`testDebugUnitTest`, `testReleaseUnitTest`)  
> **Release Artifact:** `release/v1.4.0/Kaiko-v1.4.0.apk` (5.2 MB)  
> **Target SDK:** 34 (Android 14) | **Min SDK:** 26 (Android 8.0+) | **Version Code:** 9  

### Home UI + Accessibility + Active SOS Controls + User Guide
- **Top Main SOS Button**: Centered large circular SOS button ("SOS", "TAP TO TRIGGER") clearly visible at the top of the home screen.
- **System Status Card**: Compact status indicators for Location (ON/OFF, tap to manage), Accessibility (ON/OFF, tap to manage), and Guardians ("X guardians configured", tap to open manage guardians).
- **Active SOS Controls (4 Buttons)**: Appears only after an actual SOS trigger starts:
  1. 🟢 I'M SAFE — Stops escalation and dispatches safe update only to alerted guardians.
  2. 🚨 EMERGENCY — Immediately advances to next guardian without waiting for escalation timeout.
  3. ⚠️ MISTOUCHED — Prompts confirmation dialog before stopping escalation.
  4. 🧪 TEST — Stops current escalation for testing without sending any SMS or notifications.
- **First-Time Accessibility Onboarding**: Non-forcing dialog explaining requirement with [ENABLE ACCESSIBILITY] and [NOT NOW] options.
- **Accessibility Management**: Direct navigation to Android Accessibility Settings with clear step-by-step instructions and live re-checking.
- **Dedicated User Guide**: Top-right ℹ️ icon opens a clean, bulleted Kaiko User Guide explaining app purpose, all trigger methods, 3x press setup, location, guardians, and active SOS controls.
- **Release Artifact**: Preserved all prior releases and generated `release/v1.4.0/Kaiko-v1.4.0.apk`.

---

## [v1.3.4] — 2026-09-06 21:30 IST
> **Build Status:** ✅ PASSED  
> **Release Artifact:** `release/v1.3.4/Kaiko-v1.3.4.apk` (5.2 MB)  
> **Target SDK:** 34 | **Min SDK:** 26 | **Version Code:** 8  

### Location Toggle Fix
- 3x Press & Normal Widget: Uses DEFAULT native Android/Google location request dialog (`ResolvableApiException.startResolutionForResult`) directly over whatever screen the user is currently on via transparent `LocationPromptActivity`.
- Never opens `MainActivity` or shows Kaiko main app UI on 3x press or normal widget trigger.
- No custom dialog created; strictly uses the default native Android/Google system resolution prompt.
- GPS ON: Skips request, acquires current location, and sends SOS SMS with Maps coordinate link (`https://maps.google.com/?q=LATITUDE,LONGITUDE`).
- User turns GPS ON: Re-checks GPS is ON, acquires current location, and sends SOS SMS with Maps coordinate link.
- User DENY / CLOSE / IGNORE: Waits FULL 5 seconds before sending SOS SMS without location.
- Preserved all previous releases and generated `release/v1.3.4/Kaiko-v1.3.4.apk`.

---

## [v1.3.3] — 2026-09-06 19:54 IST
> **Build Status:** ✅ PASSED  
> **Release Artifact:** `release/v1.3.3/Kaiko-v1.3.3.apk` (5.2 MB)  
> **Target SDK:** 34 | **Min SDK:** 26 | **Version Code:** 7  

### Location Flow on Current Screen
- Implemented transparent LocationPromptActivity to request Android Location Services / GPS Toggle directly from the current screen/context without opening the full Kaiko app UI for 3x Press and Normal Widget.
- Enforced strict 5-second rule: if user denies/ignores, wait until maximum 5 seconds before sending SOS without location.
- Re-check that Location Services / GPS is actually ON upon user acceptance before getting current location.
- Ensured location is never treated as unavailable immediately after user accepts GPS toggle.
- Preserved all previous releases and created `release/v1.3.3/Kaiko-v1.3.3.apk`.

---

## [v1.3.2] — 2026-09-06 16:39 IST
> **Build Status:** ✅ PASSED  
> **Release Artifact:** `release/v1.3.2/Kaiko-v1.3.2.apk` (5.2 MB)  
> **Target SDK:** 34 | **Min SDK:** 26 | **Version Code:** 6  

### Location Flow Fix Only
- Fixed Location Permission flow: if permission is already ON, skip permission request and directly check GPS Toggle.
- Fixed Android Location Services / GPS Toggle flow: if GPS Toggle is already ON, skip toggle request and immediately get current location.
- Fixed GPS Toggle acceptance: after user turns GPS ON, re-checks GPS state and obtains current location instead of immediately treating as unavailable.
- Dedicated acquisition timeout ensures GPS coordinates are acquired after turning GPS toggle ON.
- Prevented duplicate/premature permission or location prompts from MainActivity SOS button.
- Preserved all previous releases and created `release/v1.3.2/Kaiko-v1.3.2.apk`.

---

## [v1.3.1] — 2026-09-06 15:31 IST
> **Build Status:** ✅ PASSED  
> **Release Artifact:** `release/v1.3.1/Kaiko-v1.3.1.apk` (5.2 MB)  
> **Target SDK:** 34 | **Min SDK:** 26 | **Version Code:** 5  

### Location Flow Only
- Implemented strict location permission check for normal SOS triggers (3x Press, Normal Widget, App SOS Button).
- Implemented Android Location Services / GPS Toggle detection and user prompt.
- Implemented 5-second total safety limit so SOS dispatch is never blocked indefinitely.
- Implemented GPS coordinate acquisition with Google Maps coordinate URL (`https://maps.google.com/?q=LATITUDE,LONGITUDE`).
- Network-independent GPS coordinate acquisition (works offline without requiring network connection).
- Preserved Discreet Safety Trigger completely untouched (no popups, no UI, no extra delay, no notifications).
- Preserved existing escalation timing and logic for Guardian 2 & 3 (fresh location attempt with last-known fallback).
- Preserved all existing releases and generated `release/v1.3.1/Kaiko-v1.3.1.apk`.

---

## [v1.3.0] — 2026-09-06 14:15 IST
> **Build Status:** ✅ PASSED  
> **Release Artifact:** `release/v1.3.0/Kaiko-v1.3.0.apk` (5.2 MB)  
> **Target SDK:** 34 | **Min SDK:** 26 | **Version Code:** 4  

- Upgraded SOS location decision flow.
- Added Android Location Services / GPS Toggle handling.
- Added 5-second location setup timeout.
- Added network-aware location fallback.
- Added Google Maps coordinate fallback.
- Improved Guardian 2/3 fresh-location attempts.
- Preserved previous APK releases.

---

## [v1.2.2] — 2026-09-05 22:04 IST
> **Build Status:** ✅ PASSED  
> **Release Artifact:** `release/v1.2.2/Kaiko-v1.2.2.apk` (5.2 MB)  
> **Target SDK:** 34 | **Min SDK:** 26 | **Version Code:** 3  

- Multi-guardian escalation stability improvements.
- Fixes for background receiver event delivery.
- Build and packaging optimizations.

---

## [v1.2.1] — 2026-09-04 17:45 IST
> **Build Status:** ✅ PASSED  
> **Release Artifact:** `release/v1.2.1/Kaiko-v1.2.1.apk` (5.2 MB)  
> **Target SDK:** 34 | **Min SDK:** 26 | **Version Code:** 2  

- FusedLocationProviderClient integration with high-accuracy GPS fixes.
- Configurable escalation delay interval (30s, 60s, 120s).
- Direct SOS home screen widget and disguised daily memo widget.
- Accessibility service for 3x Volume-Down hardware button distress triggers.

---

## [v0.0.2] — 2026-08-31
- Multi-guardian configuration and emergency contact persistence.
- 13-state on-device emergency lifecycle state machine.
- Emergency lock-screen notification with interactive actions.

---

## [v0.0.1] — 2026-08-25
- Initial emergency alert prototype.
