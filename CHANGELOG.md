# Changelog

All notable changes to the Kaiko application are documented in this file.

## [v1.4.0] — 2026-09-06 23:33 IST
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

## [v1.3.4] — 2026-09-06 20:34 IST
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
- Preserved all previous releases (v1.2.1, v1.2.2, v1.3.0, v1.3.1, v1.3.2, v1.3.3) and generated `release/v1.3.4/Kaiko-v1.3.4.apk`.

## [v1.3.3] - 2026-09-06
### Location Flow on Current Screen
- Implemented transparent LocationPromptActivity to request Android Location Services / GPS Toggle directly from the current screen/context without opening the full Kaiko app UI for 3x Press and Normal Widget
- Enforced strict 5-second rule: if user denies/ignores, wait until maximum 5 seconds before sending SOS without location
- Re-check that Location Services / GPS is actually ON upon user acceptance before getting current location
- Ensured location is never treated as unavailable immediately after user accepts GPS toggle
- Preserved all previous releases (v1.2.1, v1.2.2, v1.3.0, v1.3.1, v1.3.2) and created release/v1.3.3/Kaiko-v1.3.3.apk

## [v1.3.2] - 2026-09-06
### Location Flow Fix Only
- Fixed Location Permission flow: if permission is already ON, skip permission request and directly check GPS Toggle
- Fixed Android Location Services / GPS Toggle flow: if GPS Toggle is already ON, skip toggle request and immediately get current location
- Fixed GPS Toggle acceptance: after user turns GPS ON, re-checks GPS state and obtains current location instead of immediately treating as unavailable
- Dedicated acquisition timeout ensures GPS coordinates are acquired after turning GPS toggle ON
- Prevented duplicate/premature permission or location prompts from MainActivity SOS button
- Preserved all previous releases (v1.2.1, v1.2.2, v1.3.0, v1.3.1) and created release/v1.3.2/Kaiko-v1.3.2.apk

## [v1.3.1] - 2026-09-06
### Location Flow Only
- Implemented strict location permission check for normal SOS triggers (3x Press, Normal Widget, App SOS Button)
- Implemented Android Location Services / GPS Toggle detection and user prompt
- Implemented 5-second total safety limit so SOS dispatch is never blocked indefinitely
- Implemented GPS coordinate acquisition with Google Maps coordinate URL (`https://maps.google.com/?q=LATITUDE,LONGITUDE`)
- Network-independent GPS coordinate acquisition (works offline without requiring network connection)
- Preserved Discreet Safety Trigger completely untouched (no popups, no UI, no extra delay, no notifications)
- Preserved existing escalation timing and logic for Guardian 2 & 3 (fresh location attempt with last-known fallback)
- Preserved all existing releases (v1.2.1, v1.2.2, v1.3.0) and generated release/v1.3.1/Kaiko-v1.3.1.apk

## [v1.3.0] - 2026-09-06
- Upgraded SOS location decision flow
- Added Android Location Services / GPS Toggle handling
- Added 5-second location setup timeout
- Added network-aware location fallback
- Added Google Maps coordinate fallback
- Improved Guardian 2/3 fresh-location attempts
- Preserved previous APK releases

## [v1.2.2] - 2026-09-03
- Multi-guardian escalation stability improvements
- Fixes for background receiver event delivery
- Build and packaging optimizations

## [v1.2.1] - 2026-09-01
- FusedLocationProviderClient integration with high-accuracy GPS fixes
- Configurable escalation delay interval (30s, 60s, 120s)
- Direct SOS home screen widget and disguised daily memo widget
- Accessibility service for 3x Volume-Down hardware button distress triggers

## [v0.0.2] - 2026-08-31
- Multi-guardian configuration and emergency contact persistence
- 13-state on-device emergency lifecycle state machine
- Emergency lock-screen notification with interactive actions

## [v0.0.1] - 2026-08-25
- Initial emergency alert prototype
