# Main no-scroll correction — 2026-09-05

Follow-up to test candidate `e71ef34`. No playback, billing, trial, widget, or
Developer Access gesture semantics changed.

## Layout

- One Main screen, with no `verticalScroll`, `LazyColumn`, or `LazyVerticalGrid`.
- The header and primary-action footer are measured before the weighted body.
  MINIMIZE is 48 dp high; the wider START/STOP button is 52 dp high.
- `BoxWithConstraints` uses the usable viewport. Short, wide viewports place the
  same interval controls beside volume/sound/navigation; no controls are duplicated.
- Compact mode uses 6 dp outer padding, 3 dp section gaps, two rows of four
  36 dp presets, and one 34 dp FROM/TO detail row. The detail row is reserved in
  every interval mode. Trial badges and errors are limited to one line.
- Explicit text line heights replace inherited Material line heights. System
  font scaling remains enabled. Particularly narrow landscape layouts shorten
  secondary labels, with full navigation descriptions retained for accessibility.
- The effective sound pool is summarized on Main. Its selection toggles moved
  to the existing Sounds screen; existing purchase and trial actions remain there.
- The Activity uses edge-to-edge consistently; the root applies Compose
  `WindowInsets.safeDrawing` once, including system bars, cutouts, and IME.

## Verification performed

`gradlew.bat test assembleDebug assembleRelease lintVitalRelease --no-daemon`

**PASS** (38 seconds): 175 tests per build variant, zero failures, errors, or
skips; debug and release APK assembly and release vital lint passed. Existing
tests, including DeveloperGesture tests, were preserved. No Robolectric or
additional test dependencies were added. `git diff --check` passed for the change.

Static inspection confirmed the Main screen and its root contain no vertical
scroll container. Store/settings scrolling remains on those secondary screens.

## Sizing estimates — not device measurements

These are arithmetic estimates from the specified control/line heights,
including padding, reserved manual details, and the reserved error line. They
exclude system insets and allow no extra space for device-specific font metrics
or pixel rounding. They do **not** establish visual acceptance.

| Arrangement | Font scale | Estimated content height |
| --- | --- | --- |
| Regular portrait | 1.0 | 458 dp |
| Compact portrait | 1.0 | 390 dp |
| Compact portrait | 1.3 | 399 dp |
| Compact landscape | 1.0 | 258 dp |
| Compact landscape | 1.3 | 263 dp |

For example, a 320 × 480 dp portrait display with 24 dp status and 48 dp
navigation bars leaves 408 dp for the compact panel. A 480 × 320 dp landscape
display with a 48 dp side navigation bar and 24 dp top status bar leaves
432 × 296 dp, selecting the narrow landscape arrangement.

## Device acceptance still pending

No physical device was connected (`adb devices -l` returned an empty list).
An API 36 emulator was provisioned, but the host has no emulator hypervisor
driver, and the software-emulation attempt failed to boot. Its processes were
stopped. No screenshots or actual Compose bounds measurements are available.
Runtime visual testing at **both 1.0× and 1.3×** remains unverified.

On a physical device, verify cold open, STOPPED, RUNNING, ERROR with a long
message, MANUAL with FROM/TO, PULSE, active/expired trials, and Developer Access.
In each state, confirm every core control is visible together without scrolling.
Repeat portrait/landscape and 1.0×/1.3× font sizes, including gesture navigation
and three-button navigation. Confirm the title hold for 20 seconds plus a second
finger tapping START/STOP still unlocks access without toggling playback. Verify
sound selection from Sounds, returning to Main, and MINIMIZE during playback.

The reserved action area remains protected if an unusually small multi-window
viewport or an open keyboard leaves less room than the body requires. In that
case the body's drawing is bounded and secondary controls may be cut off; there
is no scroll fallback. Full-panel fit below the estimates above, or at extreme
font scales, is not claimed. Keyboard-open and extreme-size behavior also need
device verification; the numeric editor uses the existing IME Done/focus-loss
commit behavior.

Built APKs: `app/build/outputs/apk/debug/app-debug.apk` and
`app/build/outputs/apk/release/app-release-unsigned.apk`. The release APK remains
unsigned under the existing build configuration.
