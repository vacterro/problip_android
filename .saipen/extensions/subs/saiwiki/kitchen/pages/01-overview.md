# 01 — Overview

Mirrors: `README.md`.

## What Problip is

Problip is a random-beep meditation timer for Android: press START, get short
audio cues at a chosen rhythm until you press STOP. It is a native port of the
Windows Problip v3 (`reference/windows/Problip.cs`), which is a **behavioural**
reference only — the WinForms implementation (registry, INI files, GDI,
`System.Media`, tray icon) is not ported.

- Brand: **Problip**
- Google Play title: *Problip: Random Beep Timer*
- Application ID: `com.vacster.problip`

## Stack

- Kotlin, Jetpack Compose, coroutines, DataStore, AndroidX AppCompat (per-app locales)
- Play Billing 9.1.0 (one-time products only — no subscriptions)
- Gradle Kotlin DSL, version catalog
- compileSdk 36, targetSdk 36, minSdk 26
- Single `app` module

## Product philosophy

- **Local-first**: no account, no backend, no analytics, no ads SDK, no
  Problip-originated network requests. See page 05 (storage & privacy).
- **One of everything**: one foreground service, one scheduler, one audio
  engine, one wake lock — never two. Duplicated loops are treated as defects.
- **Access is one rule**: everything premium resolves through
  `free || owned || activeTrial || developerAccess || earnedPremium`.
  See page 04 (access sources) and page 06 (billing).
- **Honest states**: `RUNNING` means audio is genuinely playing; a failed
  engine surfaces `ERROR`, never a fake RUNNING.

## Languages

English (default), Русский, Eesti, 日本語 — chosen from a LANGUAGE row in
Settings via `AppCompatDelegate.setApplicationLocales`; packaged locales are
pinned to `en/ru/et/ja`. The service, notification and widget resolve strings
through the same per-app locale override.
