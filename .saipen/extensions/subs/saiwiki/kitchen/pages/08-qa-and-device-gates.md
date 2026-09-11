# 08 — QA and the open gates

Mirrors: `docs/qa-audit-w12.md`, `README.md` (wave status), `.saipen/BOARD.md`.

## Build evidence (current)

`gradlew test assembleDebug assembleRelease bundleRelease lint lintVitalRelease
--no-daemon` → BUILD SUCCESSFUL:

- **260 unique JVM tests, 0 failures, 0 errors, 31 suites** (distinct
  classname.testcase pairs over both variants — `test` runs debug and release
  variants of the same suite, so each test counts once).
- Lint: 0 errors; remaining warnings reviewed (typography/dependency-age
  noise, the deliberate session wake lock, localization advisories).
- `aapt2` badging: `versionCode='1' versionName='1.0.0'`; exactly the expected
  8 permissions — no location, recording, camera, contacts, storage,
  exact-alarm or battery-optimization permission anywhere.

## QA hardening history (W12 + W12.1)

Fixed defects with regression tests include: the START/STOP race (a dropped
STOP returned success while the loop kept blipping), SoundPool load leaks on
every pool change, restore-purchases never re-querying during a connection,
the service-lifecycle race killing a fresh session, the orphan notification,
and the audio-ERROR state erased by its own teardown. Hardened: cross-thread
audio state (one lock), billing reconnect, overlapping store queries (FIFO
mutex per query kind). The full defect→evidence table lives in
`docs/qa-audit-w12.md`.

Negative controls are part of the method: proposed fixes are proven to bite
(mutation → named tests FAIL → revert → green) before they are accepted.

## Device session checklist (T-014)

The torture sequence (≥10 runs): START → background → foreground → lock →
unlock → change volume → change interval → STOP → START → kill activity →
open activity → STOP. Severity order: any blip after STOP; notification
without session; second overlapping stream after fast STOP+START; memory
growth across pool changes; timing drift/silence locked, in Doze, in Battery
Saver.

Additional physical rows: four-locale no-scroll/fit pass at 1.0 and ~1.3 font
scale, Glow visual acceptance per palette, widget sizes, the 20 s Developer
Access chord, a locked MANUAL session, the PULSE screen-on pattern, 30-minute
locked timing runs. Partial recorded evidence (~3 h user testing, worst 5 s
interval ≈ 6.5 s, rarely) is noted on page 03; nothing beyond it is claimed.

## Human-blocked gates (nothing the repo can supply)

| Gate | Ticket | Missing authority |
|------|--------|-------------------|
| Final curated premium WAVs + audio QA | T-29 (HUMAN_ASSET_BLOCKED) | user's final five WAVs |
| Privacy contact email / policy publication | T-015 (HUMAN_CONTACT_REQUIRED) | real address + hosting |
| Play Console account, real products, licensed tester | T-009/T-010 | human-owned account |
| Upload key creation + keystore.properties | `docs/release-signing.md` | human-recorded credentials |
| Physical device acceptance | T-014 | physical device session |
| Live purchase gate | T-010 | internal test track |

Until T-29 closes with the audio QA passed, the resulting artifact is never
called FINAL / RC / PRODUCTION CANDIDATE / v1.0.0 RELEASE.
