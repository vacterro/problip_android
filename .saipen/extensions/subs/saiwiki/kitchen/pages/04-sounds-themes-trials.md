# 04 — Sounds, themes, trials, earned Premium, Glow

Mirrors: `README.md` (What it does), `app/src/main/java/com/vacster/problip/trial/`,
`app/src/main/java/com/vacster/problip/ui/`.

## Sounds

Six sounds: one free (Original Blip) + five premium. The random pool draws
only from sounds that may actually play. Stable product IDs (never renamed):
`sound_glass`, `sound_wood`, `sound_soft_bell`, `sound_bonk`, `sound_space`,
plus the `theme_pack` for themes/Glow. The five premium WAVs are still
**synthesized placeholders** (`scripts/make_placeholder_sounds.py`, ledger
`reference/audio/SOURCES.md`) until the user's final curated set lands — that
is ticket T-29, HUMAN_ASSET_BLOCKED.

## Themes

15 Wintage palettes: 1 free + 14 premium (one light: Vintage Classic).
Instant switch via CompositionLocal, selection persisted, a locked pick falls
back to Classic. History: the archive's `Custom` preset duplicated Golden
Default exactly and was removed for release; a stored `theme_wintage_custom`
pick resolves back to Golden Default (it appears exactly once in the dex, as
the normalize key).

## Five-minute trials

Any unowned premium sound, theme or interval preset can run a reusable
five-minute trial:

- One wall-clock expiry per item id, persisted in DataStore
  (`trial/TrialAccess.kt`, `TrialClock`, `TrialCoordinator`).
- No extension on repeat selection; immediate restart after expiry;
  independent per-item expiries; ownership overrides expiry.
- Trials are re-usable without cooldowns, accounts, device IDs or server
  checks.
- The Glow toggle **never starts a trial by accident**: toggling OFF keeps the
  timer and suppresses only; `TRY 5 MIN` is its own separate row, offered only
  without access.

## Developer Access

Hidden seven-day global unlock behind a chord on the PROBLIP title: hold the
title ~20 s, then START/STOP while still held. Labels read `DEV`, never
`OWNED` — it expires, a purchase does not. Deliberately not documented in the
in-app Help.

## Statistics & the 100K earned Premium

- Only real successful blips are counted (playback-success hook, never a bare
  scheduler tick). Aggregates: Today / ISO-week / Month / Total, saturating
  counters that can never wrap or go negative.
- Visible as TODAY/WEEK/MONTH/TOTAL in Settings, plus a `BLIPS n` line on
  Main (hideable). Counts render with the app's locale, not the system's.
- At **100,000** lifetime successful blips Premium unlocks locally,
  permanently (`earnedPremium`). It is a separate access source: it never
  mutates Play ownership and is never revoked by a reset, refund or Billing
  refresh. A `sanitize()` pass self-heals the flag from the threshold reality.
- Earned readiness is atomic at cold start: `TrialCoordinator` awaits the
  earned flag before its first recompute, so access never completes before
  the reward is known. Crossing the threshold publishes `earned=true`
  inline with the in-memory transition — never via an async derived flow.
- Storage and backup semantics: page 05.

## Blip Glow

A premium soft accent pulse on the Main background on every successful blip:

- Max 25% alpha, Gold accent; ~60 ms rise / 200 ms decay.
- Reuses the session's blip stream — no second event bus.
- RESUMED-only, no replay, no widget/notification/screen wake.
- Access: rides the theme pack, its own `feature_blip_glow` five-minute
  trial, Developer Access, or the earned reward. Preference default ON;
  toggling OFF hides only the effect.
