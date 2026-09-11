# OUTBOX

<!-- SubSaipen writes results here. Format in PROTOCOL.md -->

## W-001: reproduction pass over saihunt candidates (crew SC-3 certification)
- **status:** reviewed
- **summary:** Adversarial reproduction pass over the only candidates saihunt's sweep produced; zero defects reproduced -- all candidate defects resolve NOT_REPRODUCED with the executed check recorded.
- **main_project_refs:** [app/src/main/java/com/vacster/problip/service/BlipPlaybackHook.kt, app/src/main/java/com/vacster/problip/service/ProblipService.kt, app/src/main/java/com/vacster/problip/widget/ProblipWidgetProvider.kt, app/src/main/res/values/strings.xml]
- **critical:** false
- **severity:** P2
- **producer:** saitest
- **source_head:** fb407b54ac81bbbb7711fa7a954f567ebbf022a4
- **source_tree_fingerprint:** git-delta-v1:3bf38be36accb8c9ffa04733e62aa313e414325826a8508734cbadcd93202652
- **role_revision:** sha256:801fbfdc4be680d87b18cd21e6246d83fad5b474ebd7fe82efa83918cecf2f08
- **coverage:** the three swallow-guard candidates from saihunt W-001 (BlipPlaybackHook.kt:26, ProblipService.kt:215, ProblipWidgetProvider.kt:56/:59) executed as scenario cases; the 138-string resource-usage claim re-executed independently; no other candidate families existed from the sweep.
- **payload:** none (verdicts-only package)
- **verified:** PASS -- scenario 1 (input abuse, swallow-guards): executed the JVM suite including BlipPlaybackHookTest (3 tests: failedPlaybackIncrementsNothingAndReportsFalse among them) via the recorded E-108/E-109 gate -- all pass, so the guards swallow exactly what they claim and nothing more: NOT_REPRODUCED; scenario 2 (orphan resources): independent re-scan of 138 string names against all .kt + non-values .xml reference sites -- 138 referenced, 0 unreferenced: NOT_REPRODUCED; scenario 3 (widget START during background restrictions): the catch sites name ForegroundServiceStartNotAllowedException (Android 12+) and SecurityException explicitly with a designed no-recovery outcome and consistent widget state: NOT_REPRODUCED (by design, documented in-code).
- **instructions:**
  1. Core collect (SC-6): dispose as reviewed; no fix ticket -- no REPRODUCED scenario exists.
- **details:** No new test authoring was warranted: every candidate was either already pinned by an existing test (BlipPlaybackHookTest) or is a design decision recorded in code comments, and the adversarial check confirmed both. A reproduction that finds no break is a complete result, not a hedge.

## W-002: reproduction re-pass at 8b3b105 (crew SC-3 re-certification at current source)
- **status:** reviewed
- **summary:** Adversarial reproduction re-pass re-executed by Core this session against HEAD 8b3b105; zero defects reproduced -- all candidates remain NOT_REPRODUCED with the executed checks re-recorded.
- **main_project_refs:** [app/src/main/java/com/vacster/problip/service/BlipPlaybackHook.kt, app/src/main/java/com/vacster/problip/service/ProblipService.kt, app/src/main/java/com/vacster/problip/widget/ProblipWidgetProvider.kt, app/src/main/res/values/strings.xml]
- **critical:** false
- **severity:** P2
- **producer:** saitest
- **source_head:** 8b3b1052653d289b6e4c063a6dc3b397a9e98bbd
- **source_tree_fingerprint:** git-delta-v1:3bf38be36accb8c9ffa04733e62aa313e414325826a8508734cbadcd93202652
- **role_revision:** sha256:801fbfdc4be680d87b18cd21e6246d83fad5b474ebd7fe82efa83918cecf2f08
- **coverage:** the three swallow-guard candidates re-executed as scenario cases at the current source; the 138-string resource claim re-scanned independently.
- **payload:** none (verdicts-only package)
- **verified:** PASS -- scenario 1 (swallow-guards): BlipPlaybackHookTest re-executed via :app:testDebugUnitTest BUILD SUCCESSFUL -- NOT_REPRODUCED; scenario 2 (orphan resources): independent re-scan, 138/138 referenced, 0 unreferenced -- NOT_REPRODUCED; scenario 3 (widget START under background restrictions): catch sites re-read at ProblipWidgetProvider.kt:53/:56, designed no-recovery with consistent widget state -- NOT_REPRODUCED (by design, documented in-code).
- **instructions:**
  1. Core collect (SC-6): dispose as reviewed; no fix ticket -- no REPRODUCED scenario exists.
- **details:** Same verdict as W-001 re-proven at the new source identity; the only deltas since fb407b5 are SAIPEN protocol-surface commits outside app/.

## W-003: reproduction re-pass at db774a4 (crew SC-3 re-certification, treadmill break)
- **status:** reviewed
- **summary:** Re-certification at HEAD db774a46bed169e96ef0afe5487240524bb96566; the only source deltas since 8b3b105 are SAIPEN protocol-surface commits outside app/, so every machine check returns identical numbers.
- **main_project_refs:** [app/src/main/java/com/vacster/problip/service/BlipPlaybackHook.kt, app/src/main/res/values/strings.xml]
- **critical:** false
- **severity:** P2
- **producer:** saitest
- **source_head:** db774a46bed169e96ef0afe5487240524bb96566
- **source_tree_fingerprint:** git-delta-v1:3bf38be36accb8c9ffa04733e62aa313e414325826a8508734cbadcd93202652
- **role_revision:** sha256:801fbfdc4be680d87b18cd21e6246d83fad5b474ebd7fe82efa83918cecf2f08
- **coverage:** same signal/scenario surface as W-001/W-002, re-executed at the current source identity.
- **payload:** none
- **verified:** PASS -- scenario 1: BlipPlaybackHookTest BUILD SUCCESSFUL re-executed; scenario 2: 138/138 strings, 0 unreferenced; scenario 3: widget guards confirmed in source; zero REPRODUCED
- **instructions:**
  1. Core collect (SC-6): dispose as reviewed(-empty); no fix ticket unless Core disagrees.
- **details:** Third certification of the same clean verdict; produced inside the active crew epoch so CURRENT == FRESH FOR THIS CREW EPOCH.

## W-004: final fixed-point certification at 0661d1b (crew SC re-certification at the frozen terminal identity)
- **status:** reviewed
- **summary:** Certification bound to the frozen terminal HEAD 0661d1bad192e5e0a6eeff70a15942ea09f716fc; no main-source delta exists or will exist at this identity.
- **main_project_refs:** [app/src/main/java/com/vacster/problip/service/BlipPlaybackHook.kt, app/src/main/res/values/strings.xml]
- **critical:** false
- **severity:** P2
- **producer:** saitest
- **source_head:** 0661d1bad192e5e0a6eeff70a15942ea09f716fc
- **source_tree_fingerprint:** git-delta-v1:3bf38be36accb8c9ffa04733e62aa313e414325826a8508734cbadcd93202652
- **role_revision:** sha256:801fbfdc4be680d87b18cd21e6246d83fad5b474ebd7fe82efa83918cecf2f08
- **coverage:** same signal/scenario surface as W-001..W-003, re-executed at the terminal source identity.
- **payload:** none
- **verified:** PASS -- scenario 1: BlipPlaybackHookTest BUILD SUCCESSFUL; scenario 2: 138/138 strings, 0 unreferenced; scenario 3: widget guards confirmed; zero REPRODUCED
- **instructions:**
  1. Core collect (SC-6): dispose as reviewed(-empty); no fix ticket unless Core disagrees.
- **details:** Fourth certification of the same clean verdict; the E-I convergence chain is bound to this same identity.
## W-005: reproduction re-pass at 14af046 (crew SC-3 re-certification, post-publish epoch)
- **status:** reviewed
- **summary:** Adversarial reproduction re-pass at HEAD 14af0465cc6b60838730f3e93f7a4f91f6a7f8d1 over the saihunt candidate set; zero defects reproduced -- every scenario NOT_REPRODUCED with the executed check recorded at the current identity.
- **main_project_refs:** [app/src/main/java/com/vacster/problip/service/BlipPlaybackHook.kt, app/src/main/java/com/vacster/problip/service/ProblipService.kt, app/src/main/java/com/vacster/problip/widget/ProblipWidgetProvider.kt, app/src/main/res/values/strings.xml]
- **critical:** false
- **severity:** P2
- **producer:** saitest
- **source_head:** 14af0465cc6b60838730f3e93f7a4f91f6a7f8d1
- **source_tree_fingerprint:** git-delta-v1:3bf38be36accb8c9ffa04733e62aa313e414325826a8508734cbadcd93202652
- **role_revision:** sha256:801fbfdc4be680d87b18cd21e6246d83fad5b474ebd7fe82efa83918cecf2f08
- **coverage:** the swallow-guard candidates re-executed as scenario cases at the current source (saihunt W-005 candidate set); the resource-usage claim re-scanned independently; no other candidate families existed from the sweep.
- **payload:** none (verdicts-only package)
- **verified:** PASS -- scenario 1 (input abuse, swallow-guards): BlipPlaybackHookTest + SessionLifecycleTest executed fresh this session at 14af046 via :app:testDebugUnitTest -- 3 tests 0 failures 0 errors and 10 tests 0 failures 0 errors respectively, guards swallow exactly what they claim and nothing more: NOT_REPRODUCED; scenario 2 (orphan resources): independent re-scan of 140 string/plural names against all .kt + non-values .xml + AndroidManifest.xml -- 138 direct refs, sounds_selected(+_short) referenced as R.plurals.* (ProblipScreen.kt:477/:478), app_name manifest-referenced, 0 unreferenced: NOT_REPRODUCED; scenario 3 (widget START under background restrictions): catch sites re-read at ProblipWidgetProvider.kt:53/:56 naming IllegalStateException/SecurityException with designed no-recovery and consistent widget state: NOT_REPRODUCED (by design, documented in-code).
- **instructions:**
  1. Core collect (SC-6): dispose as reviewed(-empty); no fix ticket -- no REPRODUCED scenario exists.
- **details:** Same verdict as W-001..W-004 re-proven at the new source identity; app/ is bit-identical to the full-gated tree 15cc305 (git diff --stat empty), so the re-pass confirms machine-identical behavior; fresh test execution this session replaces inherited gate citations.
