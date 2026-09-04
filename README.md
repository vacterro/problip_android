# Problip Android

Native Android port of [Problip](reference/windows/Problip.cs) — a tiny random-beep
meditation timer. The Windows version is a behavioural reference only.

- Brand: **Problip**
- Google Play title: *Problip: Random Beep Timer*
- Application ID: `com.vacster.problip`

## Stack

- Kotlin, Jetpack Compose, coroutines, DataStore (settings arrive in Wave 3)
- Gradle Kotlin DSL, version catalog
- compileSdk 36, targetSdk 36, minSdk 26
- single `app` module

## Layout

```text
app/                    application module
    src/main/java/com/vacster/problip/
        core/           pure scheduling core (no Android dependencies)
    src/test/java/      deterministic unit tests for the core
docs/behavior-contract.md   product behaviour taken from the Windows original
docs/privacy-policy.md      public privacy policy text (W9)
docs/data-safety.md         Play Data Safety answers + permission/SDK inventory (W9)
docs/play-fgs-declaration.md Play foreground-service declaration + demo script (W10)
docs/qa-audit-w12.md        QA audit verdicts + device checklist (W12/W12.1)
reference/windows/      original Windows source + assets (behavioural reference)
plan/                   roadmap pack the project is built from
```

## Building

Requires JDK 17+ and an Android SDK with platform 36.
Point `local.properties` at the SDK (`sdk.dir=...`).

```bash
./gradlew assembleDebug
./gradlew test
```

## Wave status

| Wave | Scope | Status |
|------|-------|--------|
| W0 | repository/bootstrap | done |
| W1 | pure scheduling core | done |
| W2 | audio (SoundPool + sound_original) | done |
| W3 | settings (DataStore) + main UI | done |
| W4 | foreground service + notification | code done; screen-off/device measurements pending |
| W5 | sound catalog / random pool | done (premium WAVs are synthesized placeholders — see scripts/) |
| W6 | themes (6 palettes, one pack) | done |
| W7 | Google Play Billing | code done; live purchase gate pending Play Console |
| W9 | privacy policy + Data Safety | drafted from shipped code (`docs/privacy-policy.md`, `docs/data-safety.md`); publication pending |
| W10 | Play FGS declaration | declaration text + demo script done (`docs/play-fgs-declaration.md`); video needs a device |
| W11 | billing UI | done — utility main screen, Sounds/Themes as secondary store screens |
| W12 | QA hardening | done — 5 defects fixed (`docs/qa-audit-w12.md`); 3 rows stay device-only |
| W12.1 | runtime correctness + billing baseline | done — session state machine, Play Billing 9.1.0, one git baseline |

Evidence for the table above: `gradlew test assembleDebug assembleRelease` — 72 unit tests,
0 failures, 13 suites, `lintVitalRelease` clean, debug and release APKs built.

Open gates that need something this repository cannot provide:

- W4 device gate: 30/60/60 min screen-on/background/locked runs, Battery Saver, Doze,
  BT/headphones on a physical device (see `plan/01_MASTER_ROADMAP.md` W4).
- W7 live gate: buy, PENDING, acknowledgement, restore, reinstall, refund — needs
  Play Console products and an internal test track (W8 developer setup first).
- W9 publication: a public URL for the privacy policy and a contact email.

Next code wave: none queued; every remaining item needs a device or a Play Console seat.

Project memory (waves, tickets, evidence) lives in `.saipen/`.
