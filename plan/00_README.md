# Problip Android — Full Roadmap Pack

Purpose: build a small native Android version of Problip without turning it into a monster.

## Product identity

- Brand: Problip
- Google Play title: Problip: Random Beep Timer
- Launcher label: Problip
- Repository: problip-android
- Suggested application ID: com.vacster.problip

## Core rule

Preserve the product behaviour of the Windows Problip, not its WinForms implementation.

Android implementation:
- Kotlin
- Jetpack Compose
- Gradle Kotlin DSL
- compileSdk 36
- targetSdk 36
- minSdk 26
- single app module
- coroutines
- DataStore
- native short-audio playback
- user-started foreground service for active sessions
- Google Play Billing only after the core/background behaviour is proven

## Recommended implementation order

1. W0 — repository/bootstrap
2. W1 — pure scheduling core
3. W2 — audio
4. W3 — settings + minimal UI
5. W4 — foreground/background reliability
6. W5 — sound catalog / random pool
7. W6 — themes
8. W7 — billing
9. W8 — developer/store setup
10. W9 — privacy/data safety
11. W10 — foreground-service Play declaration
12. W11 — billing UI
13. W12 — QA hardening
14. W13 — device matrix
15. W14 — internal testing
16. W15 — closed test
17. W16 — store assets
18. W17 — production v1.0

Do not implement the whole roadmap in one agent run.
Finish, build and test one wave before moving to the next.
