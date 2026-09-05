# Problip Android — Master Full Roadmap

This roadmap is intentionally sequential. Each wave must finish with build/test evidence before the next begins.

---

## WAVE 0 — Repository/bootstrap

Goal: create a healthy Android repository, not product functionality yet.

Create:

```text
problip-android/
    app/
    docs/
    reference/
    README.md
```

Suggested identity:

- Brand: Problip
- Play title: Problip: Random Beep Timer
- applicationId: com.vacster.problip

Stack:

- Kotlin
- Jetpack Compose
- Gradle Kotlin DSL
- compileSdk 36
- targetSdk 36
- minSdk 26
- single `app` module

Reference assets:

```text
reference/windows/
    Problip.cs
    problip.ico
    blip01.wav
```

Write `docs/behavior-contract.md` from the original product behaviour.

Gate:

```text
./gradlew assembleDebug
./gradlew test
```

must pass.

---

## WAVE 1 — Pure scheduling core

Goal: implement deterministic product timing without Android UI/service/audio dependencies.

Implement:

- `BlipScheduler`
- `IntervalMode`
- `IntervalConfig`
- `ProblipState`
- injectable time/delay boundary
- injectable random source
- minimal playback callback/interface if needed

Required behaviour:

- initial playback ~500 ms after START
- random delay 4000..7000 ms inclusive
- fixed delays 5/10/15/20/30 seconds
- START creates exactly one loop
- repeated START is idempotent
- STOP cancels future playback
- repeated STOP is safe
- START after STOP works

Tests:

1. random delay within range
2. every fixed interval exact
3. initial delay
4. START begins scheduling
5. duplicate START does not duplicate loop
6. STOP prevents future playback
7. repeated STOP safe
8. START after STOP works

Gate:
- all unit tests pass
- Android project builds

---

## WAVE 2 — Audio

Goal: connect real short-sound playback.

Implement:

- `AudioPlayer`
- native implementation, with SoundPool as first candidate
- `SoundCatalog`
- `sound_original`

Volume:
- 0..100 UI
- 0.0..1.0 engine gain

Requirements:

- changing volume while running must not create another scheduler
- audio load failure -> ERROR
- playback failure -> ERROR or clearly handled degraded state
- START must not report RUNNING until audio is genuinely usable

Manual checks:

- 0%
- 1%
- 5%
- 50%
- 100%
- speaker
- Bluetooth
- headphones
- 200+ sequential blips
- rapid START/STOP

Gate:
- no leaks/duplicate audio engines
- build + tests pass

---

## WAVE 3 — Settings + minimal UI

Goal: make the first usable app.

Implement:

- `MainActivity`
- Compose main screen
- `ProblipViewModel`
- `SettingsRepository` using DataStore

Persist:
- volume
- interval
- selected sound(s)
- selected theme

Do not auto-persist active running state for reboot resurrection.

Main screen:

```text
PROBLIP                   OFF

VOLUME
[ slider ]                35%

INTERVAL
[4-7] [5] [10] [15]
[20] [30]

SOUND
Original Blip

[ START ]
```

Running:

```text
RUNNING
[ STOP ]
```

Error:

```text
ERROR
Sound could not be loaded
```

Classic theme:
- dark/brown/amber/pixel-retro flavour
- compact
- sharp
- no unnecessary Material visual noise

Gate:
- settings survive process restart
- RUNNING is never falsely shown
- UI remains one simple utility screen

---

## WAVE 4 — Foreground/background reliability

Goal: make Problip remain Problip when the activity is hidden or screen is off.

Implement:
- user-started foreground service
- persistent notification while active
- STOP action in notification
- tap notification opens app

Do not:
- use exact AlarmManager alarms for 4–30 second scheduling
- implement boot autostart
- secretly restart after Force Stop

Expected lifecycle:
- reboot -> STOPPED
- force stop -> STOPPED
- explicit START -> service active
- explicit STOP -> scheduler/audio/service/resources all stop

Notification example:

```text
Problip running
Random 4–7 sec
Original Blip • 5%

[ STOP ]
```

### Screen-off measurement gate

First test without a wake lock.

Test:
- foreground, screen ON: 30 min
- background activity: 60 min
- locked/screen OFF: 60 min
- Battery Saver
- Doze
- Bluetooth
- headphones

If timing stays acceptable:
- do not add a WakeLock

If CPU suspend demonstrably breaks the product:
- add partial WakeLock only for active session
- release on STOP/error/onDestroy

Gate:
- physical-device evidence
- no orphan notification
- no orphan service
- no leaked WakeLock
- no duplicate scheduler

---

## WAVE 5 — Sound catalog / freemium-ready sound pool

Goal: add content architecture without Billing yet.

Catalog example:

```text
sound_original        FREE
sound_glass           PREMIUM
sound_wood            PREMIUM
sound_soft_bell       PREMIUM
sound_bonk            PREMIUM
sound_space           PREMIUM
```

Random pool UI may allow:

```text
[x] Original
[x] Glass
[ ] Wood
[x] Soft Bell
```

At this wave premium sounds may be represented as locked catalog entries without live purchasing.

Scheduler receives only playable/owned sound list.

Do not embed Billing checks inside scheduler/core.

Gate:
- random pool works with available sounds
- no immediate repeat bugs if a no-repeat rule is explicitly chosen
- missing locked resources cannot crash playback

---

## WAVE 6 — Themes

Goal: add small cosmetic monetization surface.

Free:
- `theme_classic` (Wintage Golden Default)

Premium pack, the fifteen other Wintage palettes (`theme_wintage_*`):
- Dark Golden (Win95), Claude Code, Antigravity, K-Lite (MPC-HC), FreeBuff, CodeNomad
- Default, Golden Vintage, Vintage Dark, Vintage Classic, Dark 2 (OLED)
- Dracula, Nord, Solarized Dark, Custom

Sell themes as one pack initially rather than dozens of separate SKUs.

Gate:
- theme switch is instant
- selected theme persists
- locked theme state is clear
- Classic remains fully usable

---

## WAVE 7 — Google Play Billing

Goal: real one-time permanent unlocks.

Use the current supported Google Play Billing version at implementation time.

Initial product model:
- non-consumable one-time products

Stable IDs example:

```text
sound_glass
sound_wood
sound_soft_bell
sound_bonk
sound_space
theme_pack
```

Do not hardcode display prices.
Render localized formatted price returned by Google Play.

Purchase lifecycle:
- do not grant entitlement just because user clicked BUY
- handle PENDING
- grant only when purchase state is PURCHASED
- acknowledge successful purchase
- re-query owned purchases on reconnect/start/resume as appropriate

Local cache is a convenience.
Play ownership is the authority.

v1.0 does not need a custom backend if the threat/business model does not justify it.

Gate:
- buy
- pending purchase handling
- acknowledge
- app restart
- reinstall/restore
- offline cached UX
- refund/revocation behaviour reviewed
- no accidental consumable behaviour

---

## WAVE 8 — Developer / Play Console setup

Do this in parallel with mid-development, not the night before release.

Tasks:
- create/verify Google Play developer account
- configure legal identity carefully
- configure merchant/payments profile for IAP
- configure payout bank details
- confirm Estonia/EEA tax/payment details as required by Google at that time

Avoid changing country/legal profile casually after setup.

---

## WAVE 9 — Privacy + Data Safety

Keep privacy footprint intentionally tiny.

Do not collect:
- accounts
- contacts
- location
- microphone
- camera
- analytics
- advertising identifiers

Create public privacy-policy webpage.

Suggested substance:

```text
Problip does not create accounts.
Problip does not collect personal information.
Problip does not use advertising or analytics SDKs.
Settings are stored locally on the device.
Purchases are processed by Google Play.
```

Before release:
- audit every SDK actually bundled
- complete Google Play Data Safety based on real behaviour
- link Privacy Policy in Play listing
- link Privacy Policy inside app/about/settings

Gate:
- policy matches actual app behaviour exactly

---

## WAVE 10 — Foreground-service Play declaration

Because Problip intentionally uses a foreground service for active background audio, prepare Play Console declaration material.

Suggested explanation:

```text
Problip uses a media playback foreground service only after the user explicitly
starts an active Problip session. The service periodically plays user-configured
short audio cues while the app is in the background or the screen is off.
The active session is visible through a persistent notification and can be
stopped by the user at any time.
```

Record short demo video:
1. open Problip
2. START
3. show persistent notification
4. background/lock phone
5. demonstrate continuing blips
6. STOP from notification/app
7. show service/notification ended

Gate:
- declaration text matches implementation
- no hidden/background-autostart behaviour contradicts it

---

## WAVE 11 — Billing UI

Goal: expose purchases without turning the main screen into a shop.

Main screen remains:
- Volume
- Interval
- Sound
- START/STOP

Secondary screens:
- Sounds
- Themes

Example Sounds UI:

```text
✓ Original Blip      Included
🔒 Glass             localized price
🔒 Wood              localized price
🔒 Soft Bell         localized price
```

After purchase:

```text
✓ Glass              Owned
```

Themes:

```text
Classic              Included
Themes Pack          localized price
```

Gate:
- store is secondary
- free workflow remains clean
- locked/owned/loading/pending/error states are distinct

---

## WAVE 12 — QA hardening

Run focused audits, not vague "improve everything" prompts.

Core risks:
- duplicate scheduler
- START/STOP race
- service lifecycle race
- audio load failure
- DataStore default/corruption handling
- Billing reconnect
- pending purchase
- missing acknowledgement
- restore purchases
- notification STOP
- WakeLock release
- orphan notification/service
- SoundPool/resource leaks

Lifecycle torture sequence:

```text
START
background
foreground
lock
unlock
change volume
change interval
STOP
START
kill activity
open activity
STOP
```

Repeat many times where automation is practical.

Gate:
- zero known release-blocking races/leaks
- build/test evidence archived

---

## WAVE 13 — Device matrix

Emulators:
- API 26
- API 31
- API 33
- API 34
- API 35
- API 36

Physical:
- primary real phone
- ideally at least one Samsung/OEM device with aggressive battery management

Focus:
- Android 13 notification permission
- Android 14+ FGS rules
- Android 15/16 behaviour
- screen off
- Battery Saver
- Bluetooth/headphones

Gate:
- known OEM limitations documented
- no silent false RUNNING

---

## WAVE 14 — Google Play Internal Testing

Upload AAB to Internal Testing.

Validate the Play-delivered build:
- install
- update
- Billing environment
- purchases
- restore
- notification
- background operation
- screen off
- app relaunch

Do not rely only on sideloaded APKs for commerce testing.

---

## WAVE 15 — Closed test

If the developer account is subject to Google Play's personal-account production-access testing requirement, satisfy the current tester-count/duration requirement shown by Play Console.

Operational advice:
- invite more testers than the exact minimum
- keep them opted in for the full required continuous period
- watch Play Vitals, crashes, ANRs, battery/background complaints

Do not assume policy numbers forever; re-check Play Console/current docs immediately before this wave.

---

## WAVE 16 — Store assets

Play title:
- `Problip: Random Beep Timer`

Launcher:
- `Problip`

Short description example:
- `Random audio cues at intervals you choose, with sounds and custom themes.`

Avoid metadata spam:
- BEST
- #1
- FREE!!!
- fake medical claims
- keyword stuffing

Assets:
- proper adaptive launcher icon
- Play icon
- 4 useful screenshots

Suggested screenshots:
1. Main screen — Random beeps. Your interval.
2. Running — Keeps blipping with the screen off.
3. Sounds — Pick one sound or randomize several.
4. Themes — Customize the look.

Do not upscale a tiny ICO and call it finished artwork.

---

## WAVE 17 — Production v1.0

Required v1.0:

FREE:
- Original sound
- 0–100 volume
- random 4–7 sec
- 5/10/15/20/30 sec
- reliable explicit-session background operation
- screen-off operation within tested platform behaviour
- Classic theme

PREMIUM:
- several individual sound unlocks
- Themes Pack
- random pool from owned sounds

INFRA:
- Play Billing
- purchase restore
- foreground notification
- privacy policy
- Data Safety
- foreground-service declaration
- no account
- no ads
- no analytics

STOP FEATURE DEVELOPMENT HERE AND SHIP.

Do not block v1.0 on:
- custom interval editor
- custom WAV import
- profiles
- statistics
- streaks
- cloud backup
- widgets
- Wear OS
- desktop sync
- AI

---

# After release

First observation period:
- crashes
- ANRs
- ratings
- refunds
- sound sales
- theme-pack sales
- battery complaints
- timing/background complaints

Only then choose v1.1.

Possible v1.1:
- custom min/max interval
- optional no-repeat random
- rare-sound weighting
- All Sounds Pack

Possible v1.2 only if requested:
- user-imported custom sounds

Never grow features merely because an agent can implement them.
