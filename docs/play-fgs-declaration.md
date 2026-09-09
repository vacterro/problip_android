# Play foreground-service declaration — Problip

Waves: W10 + T-30 refresh; citations re-anchored in the T-48/T-50 housekeeping
pass (2026-09-09). Source of truth for every claim below is the shipped code,
cited by stable symbol anchors (`File.kt :: symbol`) with a line range kept only
where the range itself is the evidence, so harmless source-line movement does
not rot this document. Re-read Play's current FGS policy before
submitting; the form wording changes.

## Declared type

One service, one type:

- `app/src/main/AndroidManifest.xml` — the `<service android:name=".service.ProblipService">`
  element: `com.vacster.problip.service.ProblipService`,
  `android:exported="false"`, `android:foregroundServiceType="mediaPlayback"`
- `app/src/main/java/com/vacster/problip/service/ProblipService.kt :: goForeground()` —
  `ServiceCompat.startForeground` with
  `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`
- Permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`,
  `POST_NOTIFICATIONS`, `WAKE_LOCK` (`AndroidManifest.xml` `<uses-permission>` block)

No other foreground-service type is declared or used.

## Console declaration text

> Problip plays short audio cues (beeps) at a user-chosen interval — a random
> 4–7 second interval, a fixed 5/10/15/20/30 second interval, a custom fixed
> interval set by the user (Manual Interval), or a PULSE pattern alternating a
> 5-second cue with a deliberate 10–20 second pause. The session exists only
> because the user pressed START, and playing that audio while the phone is in
> the user's pocket with the screen off is the entire purpose of the app. The
> foreground service owns the audio engine and the timing loop for as long as
> the session runs, and it shows an ongoing notification with a STOP action the
> whole time. The session ends when the user stops it, from the app or from the
> notification.
>
> Alternative APIs do not work for this feature. WorkManager and JobScheduler
> are deferrable and their minimum periodic interval is 15 minutes, while
> Problip must play a cue every few seconds continuously. Exact alarms would
> mean thousands of alarms per hour for what is a single continuous audio
> session, which is exactly the abuse pattern the platform asks developers to
> avoid. A media-playback foreground service is the sanctioned way to keep
> user-initiated audio running.

## User-visible controls (what the reviewer will see)

- Ongoing notification, channel `problip_active`, importance LOW
  (`ProblipNotification.kt :: CHANNEL_ID`, `:: ensureChannel()`,
  `setOngoing(true)` inside `:: build()`)
- Title "Problip running", second line built from live interval/sound/volume
  via `describe()` (`ProblipNotification.kt :: describe()`, called from `:: build()`)
- STOP action wired to `ProblipService.ACTION_STOP` (`ProblipNotification.kt :: build()`)
- Tapping the notification opens `MainActivity`

## WakeLock (previously an open item — now implemented and shipped)

The older revision of this document listed the partial WakeLock as a future
item. That is no longer true; the current implementation uses it, and
`WAKE_LOCK` is declared (`AndroidManifest.xml`, `WAKE_LOCK` `<uses-permission>`):

- One `PowerManager.PARTIAL_WAKE_LOCK` per service lifecycle, created in
  `service/SessionWakeLock.kt :: SessionWakeLock.<init>`, tag `Problip:ActiveSession`
  (`SessionWakeLock.kt :: TAG`), `setReferenceCounted(false)`.
- The hold decision is pure (`SessionWakeLock.kt :: WakeLockPolicy.requiredFor`): only a
  RUNNING session may hold the lock. STARTING waits for audio without the lock;
  STOPPED/ERROR are terminal and never keep the CPU awake.
- The lock is applied from published session state
  (`ProblipService.kt :: applyWakeLock()`) — held only during the active RUNNING
  session — and released idempotently on STOP, ERROR, failed startup, teardown
  and `onDestroy` (`ProblipService.kt :: stopSession()`, `:: failSession()`,
  `:: releaseSession()`, `:: onDestroy()`; `SessionWakeLock.kt :: apply()`).
  Stale session generations are rejected before they can touch a newer session's
  lock.
- Purpose: it prevents CPU suspend from stretching the short audio intervals.
  This exists because of a measured defect — with the screen off the device
  suspended between blips and stretched a 5 s interval to roughly 20 s
  (recorded on `SessionWakeLock.kt :: SessionWakeLock`). Coroutine delays cannot
  survive CPU suspend on their own.
- It is a PARTIAL wake lock: it never wakes the display and does not brighten
  or unlock the screen.

### Known physical behaviour (recorded user evidence only — no invented lab data)

Approximately 3 hours of physical testing by the user across interval modes:
fixed 5-second mode generally behaved correctly; the worst observed interval
was approximately 6.5 seconds, occurring rarely (roughly once per ~30 minutes),
after which timing returned to normal. The old recurring 15–20 second suspend
behaviour was no longer observed. Exact-device physical acceptance runs
(30-minute locked runs, Battery Saver, Doze, Bluetooth/headphones) remain open
on the device gate ticket and are not claimed here.

## Demo video script

Record on a physical device, one continuous take, no cuts. Audio on.

1. Fresh app launch. Show the main screen: volume, interval, sound, START.
2. Press START. Beeps begin. Point the camera at the notification shade so the
   "Problip running" notification with the STOP action is visible.
3. Press Home. Stay on the launcher for ~30 seconds. Beeps keep playing.
4. Lock the screen. Wait ~30 seconds with the screen off. Beeps keep playing.
5. Unlock, pull down the shade, tap the notification. The app reopens.
6. Change the interval to "Every 5 sec". Show the notification line updating.
7. Pull down the shade and press STOP in the notification. Beeps stop, the
   notification disappears, and the app shows the stopped state.
8. Press START again, then press the in-app STOP button to show both stop paths.

Total runtime is roughly 2–3 minutes. Steps 3 and 4 are the ones Play cares
about; do not shorten them.

## No-autostart sweep

Claims a reviewer may test, and the evidence for each:

| Claim | Evidence |
|-------|----------|
| The service starts only from an explicit user action | START flows from the UI `onStartRequested` (`MainActivity.kt :: onStartRequested` -> `ProblipViewModel.kt :: start()`, the only `ProblipService.start()` caller, defined at `ProblipService.kt :: start(context)`) |
| Nothing restarts a session after reboot | No `BOOT_COMPLETED` receiver anywhere in `app/src/main` (manifest declares no `<receiver>`; source sweep for `BOOT_COMPLETED`/`RECEIVE_BOOT` returns nothing) |
| Nothing restarts a session after force-stop or a system kill | `onStartCommand` returns `START_NOT_STICKY` (`ProblipService.kt :: onStartCommand`) |
| No deferrable/alarm scheduling backdoor | Source sweep for `AlarmManager`, `WorkManager`, `JobScheduler` in `app/src/main` matches only explanatory comments |
| Running state is never resurrected from storage | `SettingsRepository` persists settings/access only — no running flag (`SettingsRepository.kt`) |
| Stop actually tears everything down | `stopSession()` runs the single main-confined teardown (`ProblipService.kt :: stopSession()`); `onDestroy()` repeats the teardown for a system-initiated kill (`ProblipService.kt :: onDestroy()`) |

The sweep was run with `grep` over `app/src/main` for
`BOOT_COMPLETED|START_STICKY|START_NOT_STICKY|AlarmManager|WorkManager|JobScheduler|setAlarm|RECEIVE_BOOT|schedule\(`.
