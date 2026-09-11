# 07 — Release engineering

Mirrors: `docs/release-checklist.md`, `docs/release-signing.md`, README
(Building).

## Version identity and the versionCode rule

- Current identity: `versionCode = 1`, `versionName = "1.0.0"`
  (`app/build.gradle.kts`).
- versionCode 1 is acceptable because no production Play release exists yet.
- Do not increment versionCode for local builds.
- **Play upload rule:** once ANY build is uploaded to Play, every later upload
  must use a strictly greater versionCode. From that point the number only
  moves forward.
- No `v1.0.0` Git tag until every FINAL gate passes on the exact tagged commit.

## Builds

```bash
./gradlew test assembleDebug assembleRelease bundleRelease lint lintVitalRelease --no-daemon
```

- `bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
- Release artifacts build **unsigned** unless an untracked
  `keystore.properties` provides signing secrets — fine for local
  verification, never usable for a production upload.
- `keystore.properties` present but incomplete → the release build **fails
  explicitly** naming the missing field (never a silently unsigned artifact
  when a signed one was requested).
- Minification stays off for v1.0.0: enabling R8 right before release would
  add a behaviour delta to revalidate, not a release step.

## Signing strategy (no key exists yet)

- Google Play App Signing: Google holds the app-signing key; local builds use
  an **upload key**; Play re-signs before distribution.
- Losing the upload key is recoverable (Play support reset after identity
  verification); losing an un-enrolled app-signing key is effectively not.
  That is the distinction the strategy exists around.
- The keystore lives OUTSIDE the repository (e.g.
  `V:\___VAC\__K\__CODE\_ANDROID\keys\problip-upload.jks`); `.gitignore`
  blocks `keystore.properties`, `*.jks`, `*.keystore` as a second line of
  defence.
- Back up immediately after creation: keystore file + store/key passwords +
  alias, stored with the same care as the key itself.
- No passwords, paths or aliases as literals anywhere in Gradle files.
  Key creation happens only with explicit human credentials/storage
  instructions.

## Root export surface (T-30.1)

Fresh verification exports live at the repository root, all gitignored,
filenames stating exactly what they are:

| File | Bytes (at packaging) | SHA-256 (at packaging) |
|------|----------------------|------------------------|
| `Problip-1.0.0-debug.apk` | 12,159,353 | `b4b20ccaa57b447c270775e2c8f0447e1c086b659d5281265d0a51615ea9ee9b` |
| `Problip-1.0.0-release-UNSIGNED-not-installable.apk` | 9,041,897 | `57c348fcdecbdfa6f9a2a147cc4e06dbda0fbbdb92c8d351fc926a32947d36f4` |
| `Problip-1.0.0-release-UNSIGNED-not-uploadable.aab` | 8,809,885 | `c7b51adcf5ff8dae576bdcd3b8a33f37f510eebb81502da277dced8e32e44606` |

- Exported only from artifacts of the exact verified gate run; SHA-256
  computed **after** export and byte-identical to the same-run `app/build`
  artifacts.
- Nothing is named FINAL / RC / PRODUCTION while T-29 is open.
- The unsigned AAB is a repository-verification bundle only — never an upload
  artifact; Play gets the signed AAB built at release time.

## The authoritative gate list

`docs/release-checklist.md` is the single gate list:
CODE / CONTENT / PHYSICAL / PLAY / POLICY / FINAL. Nothing is "done" until
the evidence exists in the repository or is linked to it.
