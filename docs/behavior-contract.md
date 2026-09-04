# Problip behavior contract

Source of truth: the Windows Problip v3 (`reference/windows/Problip.cs`).
The Android port preserves the **product behaviour**, never the WinForms implementation
(no registry, no INI files, no GDI, no System.Media, no tray icon).

## Session

- START begins a session; the **first blip plays ~500 ms after START**.
- After each blip the next wait is chosen:
  - random **4000–7000 ms inclusive**, or
  - fixed **5 / 10 / 15 / 20 / 30 seconds**.
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

## Windows-only behaviour intentionally NOT ported

- Registry autostart (`HKCU\...\Run`) — Android v1 has no boot autostart at all.
- Tray icon / hide-to-tray.
- Temp-file WAV caching for volume scaling — Android plays through an audio
  engine with real-time gain.
- WinForms pixel UI — reimplemented later as the Compose classic theme
  (dark/brown/amber, Golden Default palette).
