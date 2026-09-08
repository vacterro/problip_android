# Problip Android

Native Android port of [Problip](reference/windows/Problip.cs) — a tiny random-beep
meditation timer. The Windows version is a behavioural reference only.

- Brand: **Problip**
- Google Play title: *Problip: Random Beep Timer*
- Application ID: `com.vacster.problip`

## Stack

- Kotlin, Jetpack Compose, coroutines, DataStore, AndroidX AppCompat (per-app locales)
- Play Billing 9.1.0 (one-time products only — no subscriptions)
- Gradle Kotlin DSL, version catalog
- compileSdk 36, targetSdk 36, minSdk 26
- single `app` module

## What it does

- **Session**: START plays the first blip after ~500 ms, then keeps blipping until STOP.
  One foreground service, one scheduler, one audio engine — never two.
- **Intervals**: random 4–7 s (free), fixed 5/10/15/20/30 s (free), plus two premium
  presets — **MANUAL** (own FROM/TO seconds, clamped 1–3600 and reordered) and
  **PULSE** (5 s, then a fresh random 10–20 s, alternating).
- **Sounds**: six sounds, one free; the random pool draws only from what may play.
- **Themes**: **16 Wintage palettes** (1 free + 15 premium), instant switch, one of
  them light (Vintage Classic).
- **Trials**: any unowned premium sound, theme or interval preset can run a
  **five-minute trial**, reusable, per item, persisted as a wall-clock expiry.
  No cooldowns, no accounts, no device IDs, no server checks.
- **Statistics & reward**: only real successful blips are counted (Today / This
  week / This month / Total, in Settings; a `BLIPS n` line on Main, hideable).
  At **100,000** lifetime successful blips Premium unlocks locally, permanently —
  a separate `EARNED` access source that never mutates Play ownership and is
  never revoked by a reset, refund or Billing refresh.
- **Blip Glow**: a premium soft accent pulse on the Main background on every
  successful blip (max 25% alpha). Rides the Customization Pack, its own
  five-minute `feature_blip_glow` trial, Developer Access, or the earned reward;
  the preference default is ON and hides nothing but the effect.
- **Developer Access**: hidden seven-day global unlock behind a chord on the PROBLIP
  title (held 20 s, then START/STOP while still held). Labels read `DEV`, never
  `OWNED` — it expires, a purchase does not.
- **Widget**: compact home-screen START/STOP widget following real session state,
  no polling and no second scheduler.
- **WakeLock**: one service-owned `PARTIAL_WAKE_LOCK` (`Problip:ActiveSession`) held
  only while RUNNING — without it the CPU suspended between blips with the screen
  off and a 5 s interval measured ~20 s.
- **MINIMIZE**: explicit button that backgrounds the app without stopping the session.
- **EXIT**: explicit button at the same utility level — a live session (STARTING/
  RUNNING) is stopped through the normal service path first, then the task closes
  (`finishAndRemoveTask`). STOPPED/ERROR exit without touching the service. No
  `System.exit`, no killProcess.
- **Help**: `?` in the Main header opens one Wintage dialog (scrollable inside,
  Main itself never scrolls) — quick start, intervals, premium/trial, background,
  tips, FAQ. The hidden Developer Access gesture is deliberately not documented.
- **Languages**: English (default), Русский, Eesti, 日本語, plus System Default,
  picked from a LANGUAGE row in Settings via `AppCompatDelegate.setApplicationLocales`
  (persisted by AppCompat; `generateLocaleConfig` on, packaged locales pinned to
  en/ru/et/ja). The service, notification and widget resolve their strings through
  the same override.

Everything premium rides two Play products (the premium sounds and one Customization
Pack); the access rule is one place:
`free || owned || activeTrial || developerAccess || earnedPremium`.

## Layout

```text
app/                    application module
    src/main/java/com/vacster/problip/
        core/           pure scheduling core (no Android dependencies)
        trial/          five-minute trials + Developer Access (pure)
        billing/        Play Billing wrapper + entitlement rules
        service/        foreground service, notification, wake lock, cold start
        widget/         home-screen START/STOP widget
        ui/             Compose screens + 16 palettes
    src/test/java/      deterministic JVM unit tests (no Robolectric)
docs/behavior-contract.md   product behaviour taken from the Windows original
docs/privacy-policy.md      public privacy policy text (W9)
docs/data-safety.md         Play Data Safety answers + permission/SDK inventory (W9)
docs/play-fgs-declaration.md Play foreground-service declaration + demo script (W10)
docs/qa-audit-w12.md        QA audit verdicts + device checklist (W12/W12.1)
reference/windows/      original Windows source + assets (behavioural reference)
reference/wintage/      the 16 source palette JSONs Palettes.kt was imported from
plan/                   roadmap pack the project is built from
```

`reference/wintage/` is documentation, not a dependency: the palettes are compiled
into `ui/theme/Palettes.kt` as literal colours and nothing reads the JSON at runtime.

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
| W6 | themes | done — 16 Wintage palettes, 1 free + 15 in one pack |
| W7 | Google Play Billing | code done; live purchase gate pending Play Console |
| W9 | privacy policy + Data Safety | drafted from shipped code (`docs/privacy-policy.md`, `docs/data-safety.md`); publication pending |
| W10 | Play FGS declaration | declaration text + demo script done (`docs/play-fgs-declaration.md`); video needs a device |
| W11 | billing UI | done — utility main screen, Sounds/Themes/Settings as secondary screens |
| W12 | QA hardening | done — 5 defects fixed (`docs/qa-audit-w12.md`); 3 rows stay device-only |
| W12.1 | runtime correctness + billing baseline | done — session state machine, Play Billing 9.1.0, one git baseline |
| — | trials + widget | done — reusable five-minute trials, START/STOP widget |
| — | screen-off reliability | done — session WakeLock, MINIMIZE, debug drift log; device acceptance pending |
| — | Developer Access + MANUAL | done — hidden seven-day unlock, premium manual interval |
| — | PULSE | done — alternating 5 s / 10–20 s premium preset |
| — | interaction polish | done — playback lamp, STARTING status, haptics, pressed/selected states |
| — | localization + help + exit | done — en/ru/et/ja per-app locales, `?` Help dialog, state-aware EXIT |
| — | statistics + 100K earned Premium + Blip Glow | done — Today/Week/Month/Total on one own DataStore (memory authoritative, one-shot load), saturating counters, threshold self-heal, atomic earned cold start, separate Glow TRY action |

Evidence for the table above: `gradlew test assembleDebug assembleRelease lint lintVitalRelease`
— **248 unique JVM tests, 0 failures, 30 suites**, lint clean (0 errors; remaining
warnings reviewed — typography/dependency-age noise, the deliberate session WakeLock
and two localization advisories), debug and release APKs built. `test` runs the
debug and release variants of the same suite, so that number counts each test once.

Open gates that need something this repository cannot provide:

- W4 device gate: 30/60/60 min screen-on/background/locked runs, Battery Saver, Doze,
  BT/headphones on a physical device (see `plan/01_MASTER_ROADMAP.md` W4), plus the
  20 s Developer Access chord, a locked MANUAL session, the PULSE screen-on pattern
  and the per-palette visual pass.
- W7 live gate: buy, PENDING, acknowledgement, restore, reinstall, refund — needs
  Play Console products and an internal test track (W8 developer setup first).
- W9 publication: a public URL for the privacy policy and a contact email.

Next code wave: localization/help/exit is complete; what remains needs a device
(physical acceptance, T-014) or a Play Console seat (T-009/T-010) or a hosting
decision (T-015).

Project memory (waves, tickets, evidence) lives in `.saipen/`.
