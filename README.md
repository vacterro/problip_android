<p align="center">
  <img src="docs/images/problip-mark.png" width="140" alt="Problip mark" />
</p>

<h1 align="center">Problip</h1>

<p align="center"><em>Native Android random-beep meditation timer built with Kotlin and Jetpack Compose.</em></p>

<p align="center">
  <a href="https://www.android.com/about/versions/oreo/"><img alt="Android" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" /></a>
  <a href="https://kotlinlang.org/"><img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white" /></a>
  <a href="https://developer.android.com/compose"><img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-BOM%202025.06-4285F4?logo=jetpackcompose&logoColor=white" /></a>
  <a href="https://github.com/vacterro/problip_android/actions/workflows/android-ci.yml"><img alt="CI" src="https://github.com/vacterro/problip_android/actions/workflows/android-ci.yml/badge.svg" /></a>
</p>

## Overview

Problip is a small, deliberately quiet meditation timer: press START and it emits a short beep at the selected interval until you stop it. The signature mode blips at a fresh random 4–7 second interval, so your attention is never allowed to settle into a rhythm — the original Windows program's core idea, rebuilt natively for Android.

It is fully offline: no account, no analytics, no network permission. Sessions run in a foreground service with a partial wake lock, so blips keep their timing with the screen off or the app in the background. Premium content is generous — every premium sound, theme, and interval preset has a reusable five-minute trial, and Premium unlocks permanently on the device after 100,000 lifetime successful blips, independent of any purchase.

## Highlights

- **Random or fixed intervals** — random 4–7 s, fixed 5/10/15/20/30 s, plus premium **MANUAL** (1–3600 s) and **PULSE** (5 s / 10–20 s alternating) presets
- **Sound pool** — six sounds, one free, selectable for the random draw
- **Themes** — 15 vintage palettes (1 free + 14 premium), instant switch, one light
- **Five-minute trials** — reusable, per item, local wall-clock expiry
- **Statistics** — Today / Week / Month / Total, only successful blips counted
- **100,000-blip earned Premium** — permanent local entitlement, never revoked
- **Home-screen widget** — 1x1-first resizable START/STOP, follows real session state
- **Screen-off / background operation** — foreground service + session wake lock
- **Localization** — English, Русский, Eesti, 日本語

## Privacy and product model

- No account is required; nothing identifies you or your device to a server.
- Trial state and application preferences are stored locally.
- Google Play Billing is used for **one-time** premium products only (no subscriptions): a premium sound pack and a Customization Pack.
- Earned Premium (100,000 blips) is a separate local entitlement that never touches Play ownership.
- Developer Access is a temporary internal unlock and is not purchase ownership.

Details: [privacy policy](docs/privacy-policy.md) · [data safety](docs/data-safety.md). (The privacy policy still awaits a public contact email before Play Console publication.)

## Build from source

Requires **JDK 17+** and an Android SDK with **platform 36** (`sdk.dir` in an untracked `local.properties`).

| Property | Value |
|---|---|
| applicationId | `com.vacster.problip` |
| minSdk | 26 (Android 8.0) |
| targetSdk / compileSdk | 36 |

```bash
./gradlew test          # deterministic JVM regression tests
./gradlew assembleDebug # debug APK, no signing material needed
./gradlew lint
```

Release signing material is intentionally untracked; see [release signing](docs/release-signing.md).

## Development status

Pre-release. The current tree:

- Core application functionality is implemented and self-consistent.
- Deterministic JVM regression coverage exists (300+ tests; latest local full gate at HEAD: 311 unique JVM tests, 0 failures, 0 lint errors).
- Has passed the latest local full verification gate.

Still open before any stable release:

- **Physical-device acceptance** — screen-off/background/locked timing runs, Battery Saver, Doze, Bluetooth/headphones.
- **Live Google Play Billing acceptance** — real purchase, PENDING, restore, refund flows need Play Console products.
- **Remaining correctness/performance audit work** and final curated premium audio assets.

Treat this as pre-release software: core gates are green, but nothing here is a published, device-accepted product yet.

## Documentation

- [Behavior contract](docs/behavior-contract.md) — product behavior, inherited from the Windows original
- [Release checklist](docs/release-checklist.md) — authoritative gate list (CODE / CONTENT / PHYSICAL / PLAY / POLICY / FINAL)
- [Release signing](docs/release-signing.md) — Play App Signing strategy, untracked secrets
- [Privacy policy](docs/privacy-policy.md) · [Data safety](docs/data-safety.md)
- [Foreground-service declaration](docs/play-fgs-declaration.md) — Play FGS use-case declaration + WakeLock evidence
- [QA audit](docs/qa-audit-w12.md) — W12/W12.1 verdicts + device checklist
- [Development history](docs/development-history.md) — former implementation-focused overview and wave record

## Project structure

```text
app/            application module (Kotlin, single module)
    core/       pure scheduling core, no Android dependencies
    trial/      five-minute trials + Developer Access (pure)
    billing/    Play Billing wrapper + entitlement rules
    service/    foreground service, notification, wake lock, cold start
    widget/     home-screen START/STOP widget
    ui/         Compose screens + 15 palettes
docs/           behavior contract, release/checklist/signing, privacy, QA
reference/      original Windows source + palette sources (reference only)
scripts/        placeholder-sound and brand-asset generation scripts
plan/           roadmap pack the project was built from
```

`.saipen/` is development-state and audit infrastructure, not a product feature.

---

Problip for Android is a native port of the original [Windows Problip](reference/windows/Problip.cs), which remains a behavioral reference only.
