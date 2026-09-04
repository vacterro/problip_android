# W12 QA hardening — audit results

Every core risk the roadmap lists for Wave 12, with the verdict and the evidence
behind it. Build evidence: `gradlew test assembleDebug assembleRelease` PASS,
72 unit tests, 0 failures, 13 suites.

Verdicts: **fixed** (defect found and repaired, regression test where the code is
JVM-testable), **hardened** (no reproduction, but the code had an unsafe window
that is now closed), **audited** (checked, no change needed), **device** (cannot
be answered from a build seat; belongs to the T-014 device session).

| Risk | Verdict | Evidence |
|------|---------|----------|
| START/STOP race | **fixed** | `Mutex.tryLock()` silently dropped whichever call lost the race, so a STOP arriving while START held the lock returned as success, published `STOPPED` and left the loop blipping. Now one plain monitor, no dropped operation: `BlipScheduler.kt:45`, `:59`, `:86`. Regression test `BlipSchedulerTest.stopIsNeverDroppedWhenItRacesWithStart` fails against the old implementation and passes against the new one. |
| SoundPool/resource leaks | **fixed** | `prepare()` called `SoundPool.load()` for every sound on every pool change and never unloaded, so each settings change or purchase leaked one sample per already-loaded sound. Now a load/unload delta: `PoolDelta.kt`, `SoundPoolAudioPlayer.kt:61`, `:69-73`. Covered by `PoolDeltaTest` (4 tests). |
| restore purchases | **fixed** | `onResume()` called `connect()`, which returns immediately while already connected — ownership changes made elsewhere (second device, refund, revocation) were never re-queried for the life of the connection. New `BillingRepository.refresh()` (`BillingRepository.kt:113`) queries when connected and connects otherwise; wired at `MainActivity.kt:58` and `ProblipViewModel.kt:50`. |
| service lifecycle race | **fixed** | `stopSession()` used `stopSelf()`, which destroys the service even when a newer START is already queued — the fresh session died silently. Now the newest `startId` is tracked and passed: `ProblipService.kt:61`, `:71`, `:169`. |
| orphan notification/service | **fixed** | The notification was only removed when the process actually died, so a STOP button could outlive its session. Teardown now removes it explicitly: `ProblipService.kt:168`. `onDestroy()` releases scheduler, audio and scope (`ProblipService.kt:195-205`). |
| duplicate scheduler | **audited** | Two independent guards, both single-writer: `startSession()` returns early when a session exists (`ProblipService.kt:84`) and `start()` returns early on a live loop (`BlipScheduler.kt:60`). `BlipSchedulerTest.repeatedStartDoesNotDuplicateTheLoop` and `AudioWiringTest.volumeChangeWhileRunningKeepsSingleLoop` assert exactly one initial delay. |
| audio load failure | **fixed (W12.1)** | The W12 verdict was wrong. A failed `prepare()` published `ERROR` and then called the teardown, which published `STOPPED` over it — the user saw a session that simply refused to start, with no reason. The state machine moved into `SessionLifecycle` and teardown publishes nothing: `SessionLifecycle.kt`, `ProblipService.kt:106`, `:117`, `:147-149`. `SessionLifecycleTest.initialPrepareFailureSurvivesTeardownAndDestroy` fails against the old ordering (`expected:<ERROR> but was:<STOPPED>`). Still true: an unusable engine can never reach `RUNNING` (`AudioWiringTest.unpreparedEngineTurnsStartIntoErrorNeverRunning`). |
| DataStore default/corruption | **audited** | Read errors fall back to empty preferences and every field is range-checked on read (`SettingsRepository.kt`), proven by `SettingsRepositoryTest` (defaults, persistence, invalid-value fallback). No running-state flag is persisted, so nothing can resurrect a session. |
| Billing reconnect | **hardened (W12.1)** | `onBillingServiceDisconnected` returns the state to `DISCONNECTED` and the next resume reconnects (`BillingRepository.kt:120-123`, `:133`), and a failed setup surfaces as a store error instead of a silent dead client. Play Billing 9.1.0 adds `enableAutoServiceReconnection()` (`BillingRepository.kt:52`), so a connection dropped between two user actions is retried by the client itself. |
| pending purchase | **audited** | `PENDING` never grants access and has its own UI state; `EntitlementsTest` and `StoreItemStateTest` cover the precedence (owned > pending > price). |
| missing acknowledgement | **audited** | Every `PURCHASED` and not yet acknowledged purchase is acknowledged after each purchase update and each refresh (`BillingRepository.kt:163-172`); acknowledgement never decides ownership (`EntitlementsTest`). |
| audio state across threads | **hardened** | `prepare()` (service coroutine), `play()` (scheduler loop) and `release()` (main thread) shared a `HashMap` and the SoundPool handle with no synchronization: a pool switch mid-session raced with playback, and `release()` could land between the pick and the native `play()`. All three now hold one lock and a released engine returns `false` instead of touching native state (`SoundPoolAudioPlayer.kt:103-124`). |
| notification STOP | **device** | Unit tests cover the notification content and action label only (`ProblipNotificationTest`). Tapping STOP from the shade, with the activity dead and with the screen locked, needs a device. |
| WakeLock release | **device** | No WakeLock is taken yet; whether one is needed is the open question of T-014. Nothing to release today. |
| lifecycle torture sequence | **device** | The roadmap sequence below is not reproducible from a build seat. |

## W12.1 — runtime correctness repairs

Findings that the W12 pass missed, all reachable from a build seat.

| Defect | Symptom | Fix | Evidence |
|--------|---------|-----|----------|
| ERROR erased by its own teardown | Any `prepare()` failure showed as an instant return to `OFF`: `ERROR` was published, then the teardown it triggered published `STOPPED` over it. | Terminal state is published by the failure path only; teardown publishes nothing. | `SessionLifecycle.kt`, `ProblipService.kt:147-170`; `SessionLifecycleTest.initialPrepareFailureSurvivesTeardownAndDestroy` |
| dead session could not be restarted | A runtime playback failure left the scheduler installed, the service alive and the notification up. `startSession()` returns early on `scheduler != null`, so START was a permanent no-op — the app had to be force-stopped. | The runtime ERROR now runs the same teardown as a user STOP, so the next START builds a fresh session. | `ProblipService.kt:125-133`, `:147-170`; `SessionLifecycleTest.startAfterErrorOpensAFreshSession` |
| stale teardown could kill a live session | A failure detected on `Dispatchers.Default` could land after the user had already stopped and restarted, tearing down the new session. | Generation token per session; a stale token is rejected before anything is released. | `SessionLifecycle.kt:24-56`; `SessionLifecycleTest.staleFailureNeverTouchesTheSessionTheUserJustStarted`, `.staleReportCannotResurrectAStoppedSession` |
| replayed `STOPPED` flickered a starting session | The scheduler collector subscribes before `start()`, so the StateFlow replays its initial `STOPPED` on top of `STARTING`. | `report()` accepts live states only; terminal states have dedicated entry points. | `SessionLifecycle.kt:37-43`; `SessionLifecycleTest.replayedSchedulerStoppedDoesNotIdleAStartingSession` |
| session fields mutated from two threads | START/STOP arrive on the main thread, failures on `Dispatchers.Default`; both wrote `scheduler`, `audio` and the job list. | All session bookkeeping is main-confined through one `Handler`. | `ProblipService.kt:43`, `:177-179` |
| `ProductDetails` map cleared under a concurrent read | `detailsById` was a `HashMap` written by the `Dispatchers.IO` query and read by `purchase()` on the main thread, with a `clear()` in between — a torn read, not merely a stale one. | Immutable map replaced whole, published through `@Volatile`. | `BillingRepository.kt:77-83`, `:190` |
| overlapping store queries | Resume, retry and the purchase callback could run three refreshes at once; the slowest response published last and could restore ownership Play had already superseded. | One FIFO mutex per query kind, so the newest request publishes last. | `BillingRepository.kt:85-92`, `:145`, `:176` |
| volume slider wrote on every drag frame | A drag emitted ~60 `setVolume()` calls per second, each a DataStore write. | Thumb drags on local state, gain persists once on release. | `ProblipScreen.kt:119-127` |

Dependency guards re-verified against Play Billing 9.1.0: the location SDK is
still declared by its POM and still unused by its 607 classes, so the exclude
stays (0 `play-services-location` entries in `releaseRuntimeClasspath`); the
`androidx.fragment` constraint is still load-bearing, raising the 1.0.0/1.1.0 that
`play-services-base` requests to 1.8.6. The
`play-services-places-placereport` exclude was dropped as redundant — it reaches
the graph only through `play-services-location`.

## Device session checklist (T-014)

Run the roadmap torture sequence at least 10 times, then answer the three
device-only rows above:

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

Watch for, in this order of severity:

1. Any blip after a STOP — a dropped STOP would look exactly like this.
2. A notification with no session behind it, or a session with no notification.
3. A second, overlapping blip stream after `START` following a fast `STOP`
   (the `stopSelf(startId)` fix is what should prevent this).
4. Growing memory across repeated sound/interval changes — the pool delta fix
   should keep the SoundPool sample count flat.
5. Timing drift or silence with the screen locked, in Doze and in Battery
   Saver, which is the input for the WakeLock decision.
