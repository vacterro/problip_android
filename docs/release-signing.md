# Problip release signing strategy

Status: strategy and plumbing only. **No production key exists yet and none was
generated in this wave.** Key creation happens only with explicit human
credentials/storage instructions, because a private key with a random password
nobody recorded is not automation — it is a future support ticket.

## Google Play App Signing (required for new apps)

Google Play App Signing means Google holds the true **app-signing key** and
every artifact uploaded to Play is signed with an **upload key**. Play re-signs
the uploaded artifact with the app-signing key before distribution.

- Generate the **upload key** locally, enrol Play App Signing with the Play
  Console (it generates/holds the app-signing key), and upload the public
  certificate of the upload key.
- The app-signing key never lives on this machine and never enters this repo.
- Losing the upload key is recoverable: Play support can reset it after an
  identity/ownership verification with the account owner. That is the crucial
  distinction — losing the **app-signing key** with no Play App Signing enrolment
  is effectively unrecoverable (the app could never be updated again), which is
  exactly what App Signing exists to prevent.

## Where the local keystore lives

- Outside the repository tree, e.g. `V:\___VAC\__K\__CODE\_ANDROID\keys\problip-upload.jks`.
- The repo-side `.gitignore` blocks `keystore.properties`, `*.jks` and
  `*.keystore` as a second line of defence, but the canonical location is
  outside the checkout.
- Back it up (encrypted) immediately after creation: keystore file + store/key
  passwords + key alias, stored with the same care as the key itself. Back up
  again whenever the key changes.

## How Gradle receives secrets

`app/build.gradle.kts` reads an **untracked** `keystore.properties` file at the
project root with exactly four entries:

```properties
storeFile=V:/___VAC/__K/__CODE/_ANDROID/keys/problip-upload.jks
storePassword=...
keyAlias=problip-upload
keyPassword=...
```

Behaviour:

| Condition | Result |
|-----------|--------|
| `keystore.properties` absent | release artifacts build **unsigned** — fine for local verification, not usable for production upload |
| `keystore.properties` present but a field is blank | release build **fails** with an explicit error naming the missing field |
| `storeFile` path does not exist | release build **fails** with the path named |

There are no passwords, paths or aliases as literals anywhere in Gradle files.

## What must NEVER enter Git

- Any `.jks` / `.keystore` file
- `keystore.properties`
- Store/key passwords or the alias in any committed file, doc, log or ticket
  (document the *mechanism*, never the secret)

## Before any signed build

1. Human decides key alias/password (or generates the key and records them in
   the secret store).
2. Create the upload key with a recorded password; back it up.
3. Fill the untracked `keystore.properties`.
4. Verify: `gradlew assembleRelease` produces `app-release.apk` that installs;
   confirm signing with `apksigner verify --print-certs`.
5. Only after a full green gate, upload the **AAB** to Internal Testing.
