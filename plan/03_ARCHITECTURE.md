# Architecture

Keep the architecture deliberately small.

Suggested package layout:

```text
com.vacster.problip

MainActivity.kt

ui/
    ProblipScreen.kt
    SoundsScreen.kt
    ThemesScreen.kt
    ProblipViewModel.kt
    theme/

core/
    BlipScheduler.kt
    IntervalConfig.kt
    ProblipState.kt

audio/
    AudioPlayer.kt
    SoundPoolAudioPlayer.kt
    SoundCatalog.kt

service/
    ProblipService.kt
    ProblipNotification.kt

settings/
    SettingsRepository.kt

billing/
    BillingRepository.kt
    Entitlements.kt
    ProductCatalog.kt
```

A few files may be merged if that keeps the project clearer.

## Do not create fake complexity

Avoid layers such as:

```text
domain/
data/
presentation/
usecases/
interactors/
repositories/interfaces/
repositories/implementations/
mappers/
dto/
entities/
```

unless there is a concrete demonstrated need.

## Core scheduler

Suggested states:

```text
STOPPED
STARTING
RUNNING
ERROR
```

Scheduler sequence:

```text
START
  -> initial delay ~500 ms
  -> PLAY
  -> select next interval
  -> delay
  -> PLAY
  -> repeat
```

Random interval:

```text
4000 <= delayMs <= 7000
```

Fixed intervals:

```text
5000
10000
15000
20000
30000
```

The scheduling core must not depend on:

- Activity
- Compose
- Service
- Notification
- SoundPool
- BillingClient

Inject time/random boundaries enough for deterministic tests.

## Audio

Use a minimal `AudioPlayer` abstraction.

For short sound effects, evaluate SoundPool first.

Volume:
- UI range 0–100
- engine gain range 0.0–1.0
- do not bypass Android media volume

Do not request audio focus by default if the desired product behaviour is to blip over existing music/audio.

Let Android route output normally to:
- speaker
- Bluetooth
- headphones
- USB audio

## Settings

Use DataStore for:
- volume
- interval mode
- selected sounds
- selected theme

Do not persist `isRunning=true` as an instruction to auto-resurrect a session after reboot.

## Background runtime

Use a user-started foreground service for an active Problip session.

Intended service type:
- media playback, if it remains the correct declared type for the final implementation/policy review

Likely permissions:
- FOREGROUND_SERVICE
- FOREGROUND_SERVICE_MEDIA_PLAYBACK
- POST_NOTIFICATIONS where applicable

Do not use exact AlarmManager alarms every 4–30 seconds.

Start with:
- Foreground Service
- coroutine scheduler
- native audio

Only add a partial wake lock if real device testing proves CPU suspend breaks the required interval behaviour. If used:
- acquire only for the active session
- release on STOP
- release on error
- release on service destruction

## Sound catalog

Use stable IDs:

```text
sound_original
sound_glass
sound_wood
sound_soft_bell
sound_bonk
sound_space
```

The scheduler should receive a list of playable/owned sounds and never know Billing logic.

## Themes

Stable IDs:

```text
theme_classic
theme_terminal
theme_phosphor
theme_midnight
theme_amber
theme_pink
```

Start with `theme_classic`.

## Billing

Use a small repository:

```text
BillingRepository
    connect()
    queryProducts()
    queryPurchases()
    purchase(productId)
    acknowledge()
```

Expose simple entitlements:

```text
owns(sound_glass)
owns(sound_wood)
owns(theme_pack)
```

Do not let Compose UI depend directly on BillingClient.

Initial products should be non-consumable one-time products.

Do not add a backend for v1.0 unless a concrete threat/business requirement appears.
