# Product Scope and Constitution

## Free product

The free version must remain completely useful:

- Original Blip
- Volume 0–100%
- Random interval 4–7 seconds
- Fixed 5 seconds
- Fixed 10 seconds
- Fixed 15 seconds
- Fixed 20 seconds
- Fixed 30 seconds
- START / STOP
- settings persistence
- operation while activity is backgrounded
- operation while screen is locked/off, within Android platform constraints
- Classic theme

No time limit, no ads, no artificial cripple mode.

## Paid content

Initial monetization:

- several individual premium sound unlocks
- suggested local pricing intention around €0.49 each
- one Themes Pack around €1.99
- random sound pool may use every owned sound

Prices shown in the app must come from Google Play localized pricing, never hardcoded.

## Explicit non-goals for v1.0

Do NOT add unless separately approved after release:

- subscriptions
- accounts
- backend
- Firebase
- analytics
- ads
- cloud sync
- social features
- widgets
- Wear OS
- achievements
- streaks
- profiles
- statistics
- custom uploaded sounds
- custom schedules
- AI features
- referral systems
- multi-module architecture
- speculative enterprise abstractions

## Project invariants

1. Problip must remain a small utility.
2. Free mode must remain fully useful.
3. No ads.
4. No subscriptions.
5. No account.
6. No analytics unless explicitly approved later.
7. No backend unless there is a proven need.
8. No feature may compromise reliable background blipping.
9. RUNNING must mean the audio engine is genuinely operational.
10. START must never create multiple scheduler loops.
11. STOP must release every active resource.
12. Screen-off/background behaviour is release-critical.
13. Purchases unlock cosmetic/content features, never basic functionality.
14. Do not add speculative architecture.
15. Every wave must build and test before the next wave starts.

## Reference behaviour from Windows Problip

Expected defaults and presets:

- default volume: 5%
- default interval mode: random 4–7 seconds
- presets: random 4–7, 5, 10, 15, 20, 30 seconds
- first playback: approximately 500 ms after START
- repeated START: idempotent, no duplicate loop
- repeated STOP: harmless
- STOP cancels future scheduling
- changing settings must not spawn another scheduler
- audio load/play failure must result in ERROR, not false RUNNING

Important source-asset note:
the original `blip01.wav` should be preserved or recreated from an asset whose rights are fully controlled before the first complete Android audio build.
