# Changelog

All notable changes to the Kaiko application are documented in this file.

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
