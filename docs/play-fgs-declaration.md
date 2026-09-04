# Play foreground-service declaration — Problip

Wave: W10. Source of truth for every claim below is the shipped code, cited by
file and line. Re-read Play's current FGS policy before submitting; the form
wording changes.

## Declared type

One service, one type:

- `app/src/main/AndroidManifest.xml:23-26` — `com.vacster.problip.service.ProblipService`,
  `android:exported="false"`, `android:foregroundServiceType="mediaPlayback"`
- `app/src/main/java/com/vacster/problip/service/ProblipService.kt:141` —
  `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` passed to
  `ServiceCompat.startForeground`
- Permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`,
  `POST_NOTIFICATIONS` (`AndroidManifest.xml:4-6`)

No other foreground-service type is declared or used.

## Console declaration text

> Problip plays short audio cues (beeps) at a user-chosen interval — either a
> random 4–7 second interval or a fixed 5/10/15/20/30 second interval. The
> session exists only because the user pressed START, and playing that audio
> while the phone is in the user's pocket with the screen off is the entire
> purpose of the app. The foreground service owns the audio engine and the
> timing loop for as long as the session runs, and it shows an ongoing
> notification with a STOP action the whole time. The session ends when the user
> stops it, from the app or from the notification.
>
> Alternative APIs do not work for this feature. WorkManager and JobScheduler
> are deferrable and their minimum periodic interval is 15 minutes, while
> Problip must play a cue every 4 to 30 seconds continuously. Exact alarms would
> mean thousands of alarms per hour for what is a single continuous audio
> session, which is exactly the abuse pattern the platform asks developers to
> avoid. A media-playback foreground service is the sanctioned way to keep
> user-initiated audio running.

## User-visible controls (what the reviewer will see)

- Ongoing notification, channel `problip_active`, importance LOW
  (`ProblipNotification.kt:17-30`, `setOngoing(true)` at line 57)
- Title "Problip running", second line "Random 4–7 sec • Original Blip • 5%"
  built from live settings (`ProblipNotification.kt:47-54`, `describe()` at 75-85)
- STOP action wired to `ProblipService.ACTION_STOP` (`ProblipNotification.kt:33-38, 56`)
- Tapping the notification opens `MainActivity` (`ProblipNotification.kt:39-44, 55`)

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
| The service starts only from an explicit user action | The START button's `onClick` calls `onStartRequested()` (`ProblipScreen.kt:183`), which is `MainActivity.kt:44-46`: request `POST_NOTIFICATIONS` if needed, then `viewModel.start()`. That is the only caller of `ProblipService.start()` (`ProblipViewModel.kt:47`) |
| Nothing restarts a session after reboot | No `BOOT_COMPLETED` receiver anywhere in `app/src/main` (manifest declares no `<receiver>`; source sweep for `BOOT_COMPLETED`/`RECEIVE_BOOT` returns nothing) |
| Nothing restarts a session after force-stop or a system kill | `onStartCommand` returns `START_NOT_STICKY` (`ProblipService.kt:64`) |
| No deferrable/alarm scheduling backdoor | Source sweep for `AlarmManager`, `WorkManager`, `JobScheduler` in `app/src/main` matches only the two explanatory comments at `ProblipService.kt:34-35` |
| Running state is never resurrected from storage | `SettingsRepository.Settings` holds volume, interval, selected sounds, theme and cached owned products only — no running flag (`SettingsRepository.kt:29-36`) |
| Stop actually tears everything down | `stopSession()` cancels session jobs, stops the scheduler, releases audio and calls `stopSelf()` (`ProblipService.kt:124-134`); `onDestroy()` repeats the teardown for a system-initiated kill (`ProblipService.kt:150-159`) |

The sweep was run with `grep` over `app/src/main` for
`BOOT_COMPLETED|START_STICKY|START_NOT_STICKY|AlarmManager|WorkManager|JobScheduler|setAlarm|RECEIVE_BOOT|schedule\(`.

One open item that belongs to the device gate, not to this declaration: whether
long screen-off sessions need a partial WakeLock (`ProblipService.kt:36`). If a
WakeLock is added later, this document and the declaration text change with it.
