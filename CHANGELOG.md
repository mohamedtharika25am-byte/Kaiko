# Changelog

All notable changes to the Kaiko application are documented in this file.

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
