# 03 — Session and scheduling

Mirrors: `docs/behavior-contract.md`, `docs/play-fgs-declaration.md`.

## Session flow

- START begins a session; the **first blip plays ~500 ms after START**.
- After each blip the next wait is chosen by the interval mode (below).
- **STOP cancels all future blips.** Repeated START is a no-op; repeated STOP
  is a no-op; START after STOP begins a fresh session (initial delay again).
- Changing interval / volume / sound mid-session applies on the next wait and
  must never spawn a second scheduler loop.
- Losing access to the current interval mid-session swaps the running loop's
  interval; it never stops the session.

## Interval modes

| Mode | Wait | Access |
|------|------|--------|
| Random | 4000–7000 ms inclusive | free |
| Fixed | 5 / 10 / 15 / 20 / 30 s | free |
| MANUAL (premium) | random inside the stored FROM/TO seconds, clamped 1–3600 and reordered so FROM ≤ TO | pack |
| PULSE (premium) | 5 s, then a fresh random 10–20 s, alternating while the session lives | pack |

PULSE long waits are values the scheduler deliberately generates — never Doze,
suspend or a released wake lock. The fixed 5 s preset stays a real 5 s. The
PULSE short/long phase belongs to the session, is never persisted, and resets
to the 5 s slot on START, on STOP, and whenever the resolved interval changes.

## States

| State | Meaning |
|-------|---------|
| STOPPED | No session. |
| STARTING | START received; initial delay not yet elapsed. |
| RUNNING | Audio is genuinely playing on schedule. |
| ERROR | Sound could not be loaded or played. |

`RUNNING` must mean the audio engine is genuinely operational; a failed
load/play surfaces `ERROR`, never a silent or false RUNNING. Terminal `ERROR`
runs the same teardown as a user STOP, so the next START opens a fresh
session. A failure report from a stale session generation is rejected — it
can never tear down the session the user just restarted.

## Defaults (inherited from the Windows product)

- volume: **5%** (UI range 0–100; engine gain 0.0–1.0)
- interval: **random 4–7 s**

## Persistence rule

Windows kept `problip.ini` beside the exe; Android keeps settings in DataStore.
The running session is **never** persisted as an auto-start instruction:
after reboot or force stop Problip must come up STOPPED.

## Background timing and the wake lock

A physical device proved coroutine delays alone do not survive CPU suspend:
with the screen locked, a fixed 5 s interval stretched to roughly 20 s. The fix:

- One `PARTIAL_WAKE_LOCK` (tag `Problip:ActiveSession`, not reference counted),
  owned by the service, held **only while the session is RUNNING** — nothing
  else. STARTING never holds it (audio may never load); STOPPED and ERROR
  release it.
- The hold decision is pure (`WakeLockPolicy.requiredFor`) and the lock follows
  published session state, so a stale generation cannot release the lock of a
  session the user just restarted. Release is idempotent (STOP, ERROR, failed
  startup, teardown, `onDestroy`).
- It is PARTIAL: it never wakes the display.
- No `AlarmManager`, no exact alarms, no `WorkManager`, no silent looping
  audio, no battery-optimisation exemption prompt, no boot autostart, no
  `START_STICKY`.
- MINIMIZE (`moveTaskToBack(true)`) backgrounds the Activity leaving service,
  notification and wake lock untouched.

Measured reality (user evidence, ~3 h): fixed 5 s mode generally correct;
worst observed ≈ 6.5 s, rarely (~once per 30 min), then normal. The old
recurring 15–20 s stretch was no longer observed. Full lab-style acceptance
runs stay on the device gate (page 08).

Play Android Vitals must be watched for excessive partial wake-lock behaviour
after release — timing correctness is not weakened pre-emptively to flatter
the metric.

## Foreground service (Play declaration summary)

One service, one type: `mediaPlayback`. The session exists only because the
user pressed START; the ongoing notification (channel `problip_active`,
importance LOW) carries a STOP action the whole time. The full Console
declaration text and the no-autostart evidence sweep live in
`docs/play-fgs-declaration.md`.
