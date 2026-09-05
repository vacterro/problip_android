# Problip behavior contract

Source of truth: the Windows Problip v3 (`reference/windows/Problip.cs`).
The Android port preserves the **product behaviour**, never the WinForms implementation
(no registry, no INI files, no GDI, no System.Media, no tray icon).

## Session

- START begins a session; the **first blip plays ~500 ms after START**.
- After each blip the next wait is chosen:
  - random **4000–7000 ms inclusive**, or
  - fixed **5 / 10 / 15 / 20 / 30 seconds**, or
  - premium **PULSE**: 5000 ms, then a fresh random **10000–20000 ms inclusive**,
    alternating for as long as the session lives, or
  - premium **MANUAL**: random inside the stored FROM/TO seconds (clamped 1–3600,
    reordered so FROM ≤ TO).
- A premium interval without purchase, active trial or Developer Access resolves
  to the free random 4–7 s. Losing access mid-session swaps the interval of the
  running loop; it never stops the session or creates a second scheduler.
- PULSE long waits are values the scheduler deliberately generates, never Doze,
  suspend or a released wake lock — the fixed 5 s preset stays a real 5 s.
- The PULSE short/long phase belongs to the session, is never persisted, and
  resets to the 5 s slot on START, on STOP and whenever the resolved interval
  changes (entering PULSE, leaving it, losing its entitlement).
- **STOP cancels all future blips.**
- Repeated START is a no-op: exactly one scheduling loop ever exists.
- Repeated STOP is a no-op.
- START after STOP begins a fresh session (the ~500 ms initial delay applies again).
- Changing interval / volume / sound during a session takes effect on the next wait
  and must never spawn a second scheduler loop.

## States

| State     | Meaning                                                       |
|-----------|---------------------------------------------------------------|
| STOPPED   | No session.                                                   |
| STARTING  | START received; initial delay not yet elapsed.                |
| RUNNING   | Audio is genuinely playing on schedule.                       |
| ERROR     | Sound could not be loaded or played.                          |

- RUNNING must mean the audio engine is genuinely operational.
- A failed audio load/play must surface as ERROR, never a silent or false RUNNING.

## Defaults (from the Windows product)

- volume: **5%** (UI range 0–100; engine gain 0.0–1.0)
- interval: **random 4–7 s**

## Persistence

Windows keeps `problip.ini` next to the exe; Android uses DataStore for:

- volume
- interval mode
- selected sound(s) (later waves)
- selected theme (later waves)

The running session is **never** persisted as an auto-start instruction:
after reboot or force stop Problip must come up STOPPED.

## Background timing (screen off)

A physical device proved that coroutine delays alone do not survive CPU suspend:
with the screen locked, a fixed 5 s interval stretched to roughly 20 s.

- The foreground service holds one `PARTIAL_WAKE_LOCK`
  (tag `Problip:ActiveSession`, not reference counted) for as long as the session
  is **RUNNING**, and for nothing else.
- STARTING never holds it (audio may never load); STOPPED and ERROR release it.
- The lock follows published session state, so a stale generation cannot release
  the lock of a session the user just restarted.
- No `AlarmManager`, no exact alarms, no `WorkManager`, no silent looping audio,
  no battery-optimisation exemption prompt (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
  is deliberately absent).
- The only added permission is `android.permission.WAKE_LOCK`.
- MINIMIZE (`moveTaskToBack(true)`) backgrounds the Activity and leaves the
  service, the notification and the wake lock untouched.

This is not free: a partial wake lock keeps the CPU awake for the whole session.
It is justified because the session is user-started, foreground-service-visible
and short-interval timing is the product. **Play Android Vitals must be monitored
for excessive partial wake-lock behaviour after real user testing and release** —
timing correctness is not to be weakened pre-emptively to flatter the metric.

## Windows-only behaviour intentionally NOT ported

- Registry autostart (`HKCU\...\Run`) — Android v1 has no boot autostart at all.
- Tray icon / hide-to-tray.
- Temp-file WAV caching for volume scaling — Android plays through an audio
  engine with real-time gain.
- WinForms pixel UI — reimplemented later as the Compose classic theme
  (dark/brown/amber, Golden Default palette).
