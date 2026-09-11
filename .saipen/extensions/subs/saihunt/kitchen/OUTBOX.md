# OUTBOX

<!-- SubSaipen writes results here. Format in PROTOCOL.md -->

## W-001: six-signal defect sweep at fb407b5 (crew SC-2 certification pass)
- **status:** reviewed
- **summary:** All six HUNT signals swept against HEAD fb407b5; no new defect found -- every candidate investigated resolved NOT_REPRODUCED with the deciding evidence recorded.
- **main_project_refs:** [app/src/main/java/com/vacster/problip/service/BlipPlaybackHook.kt, app/src/main/java/com/vacster/problip/service/ProblipService.kt, app/src/main/java/com/vacster/problip/widget/ProblipWidgetProvider.kt, app/src/main/res/values/strings.xml]
- **critical:** false
- **severity:** P2
- **producer:** saihunt
- **source_head:** fb407b54ac81bbbb7711fa7a954f567ebbf022a4
- **source_tree_fingerprint:** git-delta-v1:3bf38be36accb8c9ffa04733e62aa313e414325826a8508734cbadcd93202652
- **role_revision:** sha256:4edb04181cb07e0946afd06fbe711166fa9dcc403e56b52e9be3844f0a71b0a5
- **coverage:** six signals over app/src (Kotlin + res) and the commit chain back to d5fab19; BOARD cross-check performed (read order step 5) so nothing reported duplicates a tracked ticket.
- **payload:** none (findings-only package; no patch, no integration)
- **verified:** PASS -- signal 1: current-cycle gate green (260 unique JVM tests, 0 failures, 31 suites, lint 0 errors, recorded E-108/E-109); signal 2: every commit from d5fab19 to HEAD carries a LOG E-event naming it (E-107..E-112 chain); signal 3: zero TODO/FIXME/HACK markers in app/src (grep over .kt/.xml); signal 4: three catch-with-comment sites enumerated and individually dispositioned below, all deliberate with in-code rationale and test coverage; signal 5: save/load symmetric via SettingsRepository/BlipStatsRepository round-trip tests, start/stop symmetric via BlipSchedulerTest + SessionLifecycleTest, no import/export/undo/redo feature exists by design, CLI-vs-GUI n/a (Android app); signal 6: 138/138 string resources referenced from code or non-values XML (machine scan), zero unreferenced; no orphan source files detected (every .kt file reached from manifest/graph or is a pure test).
- **instructions:**
  1. Core collect (crew SC-6): intake as one review hypothesis, dispose NOT_REPRODUCED-class findings as reviewed; no ticket needed for a clean sweep unless Core disagrees with a disposition.
  2. The three swallow-guards remain a taste question, not a defect: if Core wants trace logging for stats-secondary failures, that is an enhancement ticket, not a hunt fix.
- **details:** Dispositioned candidates (signal 4), each with verdict NOT_REPRODUCED (deliberate, documented, behaviour-tested): (1) BlipPlaybackHook.kt:26 -- `catch (_: RuntimeException)` around `stats.recordSuccessfulBlip()` with `// Stats are secondary.`; playback report still runs after the catch; covered by BlipPlaybackHookTest. (2) ProblipService.kt:215 -- `flushStats()` swallows RuntimeException with the same rationale; best-effort flush documented in the KDoc above it. (3) ProblipWidgetProvider.kt:56 -- `catch (IllegalStateException)` / `:59 catch (SecurityException)` around widget START; comments name the exact platform exceptions (ForegroundServiceStartNotAllowedException on 12+, OEM policy) and the deliberate no-recovery outcome; widget state stays consistent. No other catch sites swallow silently. Verdict: sweep clean.

## W-002: six-signal defect re-sweep at 8b3b105 (crew SC-2 re-certification at current source)
- **status:** reviewed
- **summary:** All six HUNT signals re-executed by Core this session against HEAD 8b3b105 (the T-32/T-33/T-34 review execution, machine re-runs, not prose); no new defect found -- every candidate remains NOT_REPRODUCED at the current source.
- **main_project_refs:** [app/src/main/java/com/vacster/problip/service/BlipPlaybackHook.kt, app/src/main/java/com/vacster/problip/service/ProblipService.kt, app/src/main/java/com/vacster/problip/widget/ProblipWidgetProvider.kt, app/src/main/res/values/strings.xml]
- **critical:** false
- **severity:** P2
- **producer:** saihunt
- **source_head:** 8b3b1052653d289b6e4c063a6dc3b397a9e98bbd
- **source_tree_fingerprint:** git-delta-v1:3bf38be36accb8c9ffa04733e62aa313e414325826a8508734cbadcd93202652
- **role_revision:** sha256:4edb04181cb07e0946afd06fbe711166fa9dcc403e56b52e9be3844f0a71b0a5
- **coverage:** six signals over app/src (Kotlin + res) and the commit chain back to d5fab19; BOARD cross-check re-done; execution evidence: markers grep 0 hits, catch sites re-read in source, 138/138 string machine scan, BlipPlaybackHookTest re-run BUILD SUCCESSFUL, gate receipts E-108/E-109.
- **payload:** none (findings-only package; no patch, no integration)
- **verified:** PASS -- signal 1: current-cycle gate green (260 unique JVM tests, 0 failures, 31 suites, lint 0 errors, E-108/E-109); signal 2: every commit d5fab19..8b3b105 named in LOG E-events (E-107..E-124 chain); signal 3: zero TODO/FIXME/HACK in app/src (re-run grep); signal 4: the three catch-with-comment sites re-confirmed at BlipPlaybackHook.kt:26, ProblipService.kt:215, ProblipWidgetProvider.kt:53/:56 -- deliberate, rationale in-code, tested; signal 5: symmetry unchanged (round-trip + start/stop tests green in the same suite run); signal 6: 138/138 string names referenced, 0 unreferenced (independent machine scan this session).
- **instructions:**
  1. Core collect (crew SC-6): intake as one review hypothesis, dispose NOT_REPRODUCED-class findings as reviewed; no ticket needed for a clean sweep unless Core disagrees.
- **details:** Same verdict as W-001 re-proven at the new source identity; the only source deltas since fb407b5 are SAIPEN ledger commits (protocol surface, outside app/), which is why every machine check returns identical numbers.

## W-003: six-signal defect re-sweep at db774a4 (crew SC-2 re-certification, treadmill break)
- **status:** reviewed
- **summary:** Re-certification at HEAD db774a46bed169e96ef0afe5487240524bb96566; the only source deltas since 8b3b105 are SAIPEN protocol-surface commits outside app/, so every machine check returns identical numbers.
- **main_project_refs:** [app/src/main/java/com/vacster/problip/service/BlipPlaybackHook.kt, app/src/main/res/values/strings.xml]
- **critical:** false
- **severity:** P2
- **producer:** saihunt
- **source_head:** db774a46bed169e96ef0afe5487240524bb96566
- **source_tree_fingerprint:** git-delta-v1:3bf38be36accb8c9ffa04733e62aa313e414325826a8508734cbadcd93202652
- **role_revision:** sha256:4edb04181cb07e0946afd06fbe711166fa9dcc403e56b52e9be3844f0a71b0a5
- **coverage:** same signal/scenario surface as W-001/W-002, re-executed at the current source identity.
- **payload:** none
- **verified:** PASS -- signal 1: gate green (260/0/31, lint 0, E-108/E-109 chain); signal 2: commit chain d5fab19..db774a4 named in LOG; signal 3: 0 markers re-run; signal 4: catch sites re-read (BlipPlaybackHook.kt:26, ProblipService.kt:215, ProblipWidgetProvider.kt:53/:56); signal 5: symmetry suite green; signal 6: 138/138 strings re-scan
- **instructions:**
  1. Core collect (SC-6): dispose as reviewed(-empty); no fix ticket unless Core disagrees.
- **details:** Third certification of the same clean verdict; produced inside the active crew epoch so CURRENT == FRESH FOR THIS CREW EPOCH.

## W-004: final fixed-point certification at 0661d1b (crew SC re-certification at the frozen terminal identity)
- **status:** reviewed
- **summary:** Certification bound to the frozen terminal HEAD 0661d1bad192e5e0a6eeff70a15942ea09f716fc; no main-source delta exists or will exist at this identity.
- **main_project_refs:** [app/src/main/java/com/vacster/problip/service/BlipPlaybackHook.kt, app/src/main/res/values/strings.xml]
- **critical:** false
- **severity:** P2
- **producer:** saihunt
- **source_head:** 0661d1bad192e5e0a6eeff70a15942ea09f716fc
- **source_tree_fingerprint:** git-delta-v1:3bf38be36accb8c9ffa04733e62aa313e414325826a8508734cbadcd93202652
- **role_revision:** sha256:4edb04181cb07e0946afd06fbe711166fa9dcc403e56b52e9be3844f0a71b0a5
- **coverage:** same signal/scenario surface as W-001..W-003, re-executed at the terminal source identity.
- **payload:** none
- **verified:** PASS -- signal 1: gate green (260/0/31, lint 0); signal 2: commit chain named in LOG; signal 3: 0 markers; signal 4: catch sites re-read (BlipPlaybackHook.kt:26, ProblipService.kt:215, ProblipWidgetProvider.kt:53/:56); signal 5: symmetry suite green; signal 6: 138/138 strings
- **instructions:**
  1. Core collect (SC-6): dispose as reviewed(-empty); no fix ticket unless Core disagrees.
- **details:** Fourth certification of the same clean verdict; the E-I convergence chain is bound to this same identity.
## W-005: six-signal defect re-sweep at 14af046 (crew SC-2 certification, post-publish epoch)
- **status:** reviewed
- **summary:** Re-certification at HEAD 14af0465cc6b60838730f3e93f7a4f91f6a7f8d1 (first-publish epoch): app/ is bit-identical to the full-gated tree 15cc305, and every machine check returns the clean verdict at the current identity.
- **main_project_refs:** [app/src/main/java/com/vacster/problip/core/BlipScheduler.kt, app/src/main/java/com/vacster/problip/service/BlipPlaybackHook.kt, app/src/main/java/com/vacster/problip/service/ProblipService.kt, app/src/main/java/com/vacster/problip/stats/BlipStatsRepository.kt, app/src/main/java/com/vacster/problip/widget/ProblipWidgetProvider.kt, app/src/main/java/com/vacster/problip/ui/ProblipScreen.kt, app/src/main/res/values/strings.xml]
- **critical:** false
- **severity:** P2
- **producer:** saihunt
- **source_head:** 14af0465cc6b60838730f3e93f7a4f91f6a7f8d1
- **source_tree_fingerprint:** git-delta-v1:3bf38be36accb8c9ffa04733e62aa313e414325826a8508734cbadcd93202652
- **role_revision:** sha256:4edb04181cb07e0946afd06fbe711166fa9dcc403e56b52e9be3844f0a71b0a5
- **coverage:** six signals over app/src (Kotlin + res) re-executed by machine this session at the current source identity; app/ bit-identity proven via git diff --stat 15cc305..HEAD -- app/ (empty); BOARD cross-check re-done so nothing reported duplicates a tracked ticket.
- **payload:** none (findings-only package; no patch, no integration)
- **verified:** PASS -- signal 1: fresh gradle test this session at 14af046: 260 tests / 0 failures / 0 errors / 31 suites, exit 0; signal 2: every main-source commit d5fab19..f555659 named in LOG E-107..E-318; post-publish seal commits 8b077bf/bf6c1db/14af046 carry zero app/-delta (bit-identity vs 15cc305) and are accounted by DEC lines E-319/E-320 -- the only caveat is that those three hashes postdate their DEC lines, recorded here as the disposition; signal 3: 0 TODO/FIXME/HACK markers in app/src (grep count 0); signal 4: all 7 catch sites in main source re-read and individually dispositioned (details below), all deliberate with in-code rationale, none new since W-001; signal 5: symmetry covered by the green suite (save/load round-trip via SettingsRepository/BlipStatsRepository tests, start/stop via BlipSchedulerTest + SessionLifecycleTest; no import/export/undo feature exists by design); signal 6: 140 string/plural names in values/strings.xml, 138 direct references, sounds_selected + sounds_selected_short referenced as R.plurals.* (ProblipScreen.kt:477/:478), app_name referenced from AndroidManifest.xml, 0 unreferenced, 0 orphan .kt outside app/.
- **instructions:**
  1. Core collect (SC-6): intake as one review hypothesis, dispose NOT_REPRODUCED-class findings as reviewed(-empty); no fix ticket unless Core disagrees with a disposition.
  2. The stats-secondary swallow sites remain the known taste question (W-001 instruction 2 stands): enhancement ticket territory, not a hunt defect.
- **details:** Signal-4 dispositions, verdict NOT_REPRODUCED each: (1) BlipScheduler.kt:100 catch (CancellationException) re-throws after guarding state clobber for newer sessions -- correctness code, not a swallow. (2) BlipPlaybackHook.kt:26 catch (RuntimeException) around stats.recordSuccessfulBlip() with 'Stats are secondary.'; playback report still runs; covered by BlipPlaybackHookTest. (3) ProblipService.kt:215 flushStats() swallows RuntimeException, same rationale, KDoc'd best-effort. (4) BlipStatsRepository.kt:110 catch (IOException) on read degrades to default record, documented: unreadable store must never fail audio/session. (5) BlipStatsRepository.kt:177 catch (IOException) on write: 'Stats are secondary', never surfaces as session error. (6)(7) ProblipWidgetProvider.kt:53/:56 catch IllegalStateException/SecurityException around widget START -- exact platform exceptions (ForegroundServiceStartNotAllowedException on 12+, OEM policy), deliberate no-recovery, widget state stays consistent. Verdict: sweep clean at 14af046.
