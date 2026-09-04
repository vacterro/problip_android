PROBLIP ANDROID — WAVE 0 + WAVE 1

Create a new native Android project for Problip.

PRODUCT

Brand: Problip
Google Play name: Problip: Random Beep Timer
Application ID: com.vacster.problip

TECH

- Kotlin
- Jetpack Compose
- Gradle Kotlin DSL
- compileSdk 36
- targetSdk 36
- minSdk 26
- single app module
- Kotlin coroutines
- no Flutter
- no React Native
- no MAUI
- no WebView

IMPORTANT SCOPE LIMIT

This is intentionally a tiny application.

Do not add:
- Hilt
- Koin
- Room
- Firebase
- analytics
- ads
- accounts
- networking
- backend
- Billing
- foreground service
- notifications
- premium features
- multi-module architecture
- speculative abstractions

The supplied Windows Problip source is a BEHAVIOURAL REFERENCE ONLY.
Do not port WinForms/Registry/INI/GDI/System.Media implementation techniques.

WAVE 0

Create a clean buildable Android repository.

Add:
- app module
- README
- docs/behavior-contract.md
- reference/windows directory for the supplied original source/assets

The project must build successfully.

WAVE 1 — PURE CORE

Implement a platform-independent Problip scheduling core.

Behaviour:

- states:
  STOPPED
  STARTING
  RUNNING
  ERROR

- initial playback delay approximately 500 ms

- interval modes:
  random 4–7 seconds
  fixed 5 seconds
  fixed 10 seconds
  fixed 15 seconds
  fixed 20 seconds
  fixed 30 seconds

- random delay must be within 4000..7000 ms inclusive

- Start creates exactly one scheduling loop

- repeated Start must not create duplicate loops

- Stop cancels future scheduling

- repeated Stop must be harmless

- Start after Stop must work

Make clock/time and random generation injectable enough for deterministic unit tests.

The scheduling core must NOT depend on:
- Activity
- Compose
- Service
- SoundPool
- Billing
- Android UI classes

Do not implement real audio yet.
Use a minimal playback callback/interface only where required by the core.

TESTS

Add deterministic unit tests for:

1. random delays remain in 4000..7000 ms
2. every fixed interval is exact
3. initial delay behaviour
4. Start begins scheduling
5. repeated Start does not duplicate scheduling
6. Stop prevents future playback
7. repeated Stop is safe
8. Start after Stop works

BUILD GATE

Run all unit tests.
Run the Android build.

Do not continue into audio, UI, services or Billing.

Return:
- concise implementation summary
- file tree
- test evidence
- build evidence
- any deviations from the requested scope

Do not stop at scaffolding.
Finish Wave 0 and Wave 1 completely.
