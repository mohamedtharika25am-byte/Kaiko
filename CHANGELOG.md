# Changelog

All notable changes to the Kaiko application are documented in this file.

## [v1.3.0] - 2026-09-06
- Upgraded SOS location decision flow
- Added Android Location Services / GPS Toggle handling
- Added 5-second location setup timeout
- Added network-aware location fallback
- Added Google Maps coordinate fallback
- Improved Guardian 2/3 fresh-location attempts
- Added live-location integration attempt/fallback handling
- Improved SOS session/location lifecycle
- Safe Now stops escalation and active location session
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
