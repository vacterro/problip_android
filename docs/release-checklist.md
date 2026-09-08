# Problip release checklist — v1.0.0

One authoritative gate list. Nothing is "done" until the evidence exists in the
repository or is linked to it. Do not call any artifact FINAL RC / PRODUCTION
CANDIDATE / v1.0.0 RELEASE while T-29 is open.

## Version identity + versionCode policy

- Current identity: `versionCode = 1`, `versionName = "1.0.0"` (`app/build.gradle.kts`).
- versionCode 1 is acceptable because no production Play release exists yet.
- Do not increment versionCode for local builds.
- **Play upload rule:** once ANY build is uploaded to Play, every later upload
  must use a strictly greater versionCode. From that point the number only
  moves forward.
- No `v1.0.0` Git tag until every FINAL gate below passes on the exact tagged
  commit.

## CODE

- [ ] tests green (`gradlew test` — distinct classname.testcase pairs over both variants)
- [ ] lint green (`lint` + `lintVitalRelease`, 0 errors)
- [ ] release APK builds (`assembleRelease`)
- [ ] release AAB builds (`bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`)
- [ ] exact commit known and recorded
- [ ] git status understood (only expected untracked protocol/tooling files)

## CONTENT

- [ ] T-29 final curated WAVs COMPLETE (five user-selected premium sounds)
- [ ] audio ledger complete (`reference/audio/SOURCES.md`, no PENDING USER ASSET rows)
- [ ] no synthesized placeholder premium audio (`scripts/make_placeholder_sounds.py` removed only after replacement)

## PHYSICAL

- [ ] launcher adaptive icon
- [ ] themed monochrome icon
- [ ] notification icon
- [ ] 1x1 / 2x1 / 2x2 widget
- [ ] Main no-scroll (EN/RU/ET/JA, 1.0 and ~1.3 font scale)
- [ ] Glow visual acceptance per palette
- [ ] counter/statistics visible and correct
- [ ] MINIMIZE/EXIT behaviour
- [ ] long locked-screen timing (30 min locked, fixed + random modes)

## PLAY

- [ ] signed AAB (upload key; see `docs/release-signing.md`)
- [ ] Play App Signing enrolled
- [ ] real products configured in Play Console (stable ids: `sound_glass`, `sound_wood`, `sound_soft_bell`, `sound_bonk`, `sound_space`, `theme_pack` — never renamed)
- [ ] licensed test account
- [ ] restore ownership verified (reinstall re-grants)
- [ ] purchase/cancel flow passes (live purchase gate)
- [ ] Internal Testing install passes

## POLICY

- [ ] privacy policy at stable public URL
- [ ] real contact email (resolves HUMAN_CONTACT_REQUIRED)
- [ ] Data Safety form submitted per `docs/data-safety.md` (REVERIFY_AT_SUBMISSION items re-checked against live Console)
- [ ] FGS declaration submitted per `docs/play-fgs-declaration.md`
- [ ] store listing complete (EN/RU/ET/JA)

## FINAL

- [ ] exact AAB hash recorded (SHA-256)
- [ ] exact versionCode/versionName confirmed on the artifact
- [ ] release commit recorded
- [ ] final audit pass
- [ ] `v1.0.0` tag on the exact tested production commit
- [ ] production promotion
