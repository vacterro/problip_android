# Astra product polish wave

Base: `869d1eb` (Main no-scroll fix). Decisions recorded before production edits.
Scope: four small improvements; no new feature domain or runtime subsystem.

| Candidate | User benefit | Complexity | Risk | Decision |
| --- | --- | --- | --- | --- |
| Readable palette-token application | Fix almost-white text on Vintage Classic and weak running/ownership labels; keep all Wintage constants | Small contrast fallback in existing theme code and local token usage | Accent may yield to primary text on a low-contrast surface | IMPLEMENT |
| Clear, touchable sound/theme selection and purchase | Remove duplicate sound names; separate selection, temporary access, and explicit BUY; make rows/Back easier to tap | Local store composables, existing callbacks | Secondary lists become taller with 48 dp touch targets | IMPLEMENT |
| Restrained tactile and pressed/selected feedback | Make START/STOP, MINIMIZE and selections feel deliberate; strengthen the primary visual anchor | Platform haptics, local interaction sources, borders; no framework | Haptics vary by device/system setting | IMPLEMENT |
| Actual-playback status lamp | Show that an actual SoundPool play succeeded; distinguish STARTING from RUNNING | One process-local, non-replayed signal; one foreground-only UI collector | Reports accepted playback, not proof of audible output at the speaker | IMPLEMENT |
| Widget/notification presentation pass | Potentially improve quick control | Small, but current title already opens Main and notification already has clear STOP | Low incremental benefit; unnecessary churn around working contracts | SKIP |
| Animated transitions, palette previews or a reusable feedback system | Decorative continuity | Broader component/state machinery and more rendering work | Costs more complexity than the product gains | SKIP |

Inspection covered Main, Sounds, Themes, Settings, all palette definitions,
trial/Developer Access UI and policy, widget rendering/actions, audio playback,
scheduler, cold-start barrier, service/WakeLock lifecycle, notification and Billing.
Scheduler, audio engine, ownership, trial timing and widget authority stay intact.

Initial contrast measurements (foreground/background): Vintage Classic accent on
Raised **1.43:1**; Dracula accent on Raised **3.18:1**; running green on Bg ranges
from **2.73:1 to 4.10:1** across the five requested palettes. These are token
application defects, not reasons to recolor authoritative palette constants.

Main keeps its existing constraints, control heights and no-scroll structure.
No attempt will be made to enlarge every Main preset to 48 dp by sacrificing fit.
Device validation availability: no attached physical device at inspection time;
the prior wave also found the local software emulator unable to boot.

## Results

| IMPLEMENTED | WHY | FILES | OBSERVED TRADEOFF |
| --- | --- | --- | --- |
| Palette-aware text/edge contrast and system-bar icons | Vintage Classic needs dark text/icons; running status and ownership must be legible without relying on dark green | `ui/theme/Theme.kt`, `ui/ProblipScreen.kt`, `MainActivity.kt`, `ui/SettingsScreen.kt` | Dracula action text uses its existing primary-text token where purple lacks contrast. Palette constants are untouched. The long Settings reset label uses smaller type so it fits. |
| Clear selection/access/purchase rows with 48 dp minimum targets | One sound name, a checkbox/radio selection, a static INCLUDED/TRIAL/DEV/OWNED badge, and separate TRY/BUY actions; pack purchase is visible before the theme list | `ui/StoreScreens.kt` | Unowned sound rows and theme rows take more vertical space on their existing scrollable secondary screens. Main is unaffected. Active-trial/DEV labels are status badges, not purchase buttons. |
| Tactile clicks and explicit pressed/selected states | START/STOP remains the strongest outline; selected presets use a heavier border and bold text; clicks provide short system haptics | `ui/ProblipScreen.kt`, `ui/StoreScreens.kt` | Main's compact presets retain their existing 36/44 dp height to protect no-scroll fit. Haptics depend on hardware and the user's system setting; slider motion produces none. |
| Successful-playback lamp and truthful STARTING label | The service forwards the real `audio.play()` result; Main briefly lights its status lamp only after success | `service/ProblipSession.kt`, `service/ProblipService.kt`, `ui/ProblipScreen.kt` | SoundPool success means a stream was accepted, not proof that the user heard sound through a muted or disconnected output. The nonblocking UI signal may drop a flash if the UI is busy; it never delays audio. |

The signal has no replay or persistence. Its collector exists only on Main while
the Activity is RESUMED; its 140 ms clearing delay is cancelled on pause/stop.
There is no repeating animation timer, new scheduler, widget refresh per blip,
or background UI collector. STARTING-to-RUNNING does not restart the collector.

The consciously rejected feedback/animation framework would introduce reusable
state machinery for a few call sites. A six-line local platform-click helper
and existing Compose interaction sources deliver the useful part. Widget and
notification were also left unchanged: title-to-Main and an obvious STOP already
work, so their redesign would add churn with little user benefit.

## Complexity and regression evidence

- Production delta (`git diff --numstat -- app/src/main`): **+418 / -261 = +157
  net lines**, including formatting/comments. Zero new production Kotlin files.
- New files: this decision/evidence document and
  `app/src/test/java/com/vacster/problip/service/PlaybackSignalTest.kt`.
- No new permissions, dependencies, services, screens, settings, products or
  architecture layers. Manifest and Gradle dependency declarations are unchanged.
- Git comparison confirmed no edits in core scheduler/intervals, audio engine,
  trials, Billing, settings persistence, widget, cold-start barrier, notification,
  SessionLifecycle, WakeLock policy, DeveloperGesture or palette constants.
- The service change wraps its existing playback callback and returns the same
  Boolean. Its START/STOP, foreground and resource-release paths are unchanged.
- Main and its root have no `verticalScroll`, `LazyColumn`, or `LazyVerticalGrid`.
  Main's adaptive layout, control heights and reserved primary-action area remain.

## Build and tests

Final gate on 2026-09-05:

```text
gradlew.bat test assembleDebug assembleRelease lintVitalRelease --no-daemon
BUILD SUCCESSFUL in 37s
98 actionable tasks: 20 executed, 78 up-to-date
```

**179 tests per variant, zero failures/errors/skips.** All 175 existing tests
remain; four new tests cover only new logic: contrast preference/fallback and
five-palette coverage, playback-success gating, and no replay on returning to
Main. No Robolectric or test dependency was added. `git diff --check` passed for
the wave files.

## Five-palette quality evidence

Computed from the real palette constants and the new token-selection rule,
also covered by unit tests. Action/edge values below are the minimum across
Bg, Surface, Raised and Compare; secondary text is checked on Surface.

| Palette | Action text | Secondary text | Control edge |
| --- | --- | --- | --- |
| Golden Default | 7.81:1 | 8.06:1 | 3.84:1 |
| Vintage Classic | 11.54:1 | 6.25:1 | 6.25:1 |
| OLED | 18.42:1 | 7.57:1 | 4.11:1 |
| Dracula | 7.20:1 | 4.61:1 | 3.87:1 |
| Nord | 4.66:1 | 7.45:1 | 3.94:1 |

Error text on Bg also meets 4.5:1 in all five palettes. Selected rows/presets add
border weight and selection semantics, so selection is not conveyed by color
alone. Material onSurface/onBackground/onSurfaceVariant and primary text tokens
are explicitly mapped to the current palette.

These are color measurements and source/test evidence, **not screenshots or
physical-device acceptance**. No physical device was connected. Visual checks
at 1.0×/1.3×, pressed-state appearance, haptic strength, lamp visibility and system
bar rendering remain unverified on a device. The prior no-scroll wave's
exceptionally-small-window/keyboard limitation remains documented in
`main-no-scroll-qa.md`; this wave does not increase Main's vertical footprint.

Manual follow-up: check cold Main and START/STOP/MINIMIZE in stopped/running/error,
MANUAL/PULSE, trial/DEV states and both orientations at 1.0×/1.3×. Verify the lamp
fires on playback and stays quiet after leaving Main; check TRY versus BUY and
selected/access badges in all five palettes; exercise the unchanged 20-second
two-finger Developer Access chord. No physical pass is claimed here.

The repository had pre-existing staged/unstaged `.saipen/` changes and untracked
`audit/` and `reference/wintage/` content before this wave. They are excluded from
the polish commit and preserved; a globally clean status cannot be claimed.
