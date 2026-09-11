# 05 — Storage, backup and privacy

Mirrors: `docs/privacy-policy.md`, `docs/data-safety.md`.

## Local data stores

Two separate Preferences DataStore files in app-private storage (not a
database — a DataStore):

| Store | File | Contents |
|-------|------|----------|
| Settings | `problip` (`SettingsRepository.kt`) | volume, interval/mode, custom MANUAL from/to, selected sounds, theme, per-item five-minute trial expiries, Developer Access expiry, showBlipCounter, blipGlowEnabled, cached Google Play ownership state |
| Statistics | `problip_stats` (`BlipStatsRepository.kt`) | local aggregate counts: today / ISO-week / month / total successful blips, plus the `earnedPremium` flag latched at 100,000 |

The stats store is local only. **No counter, aggregate, timestamp or blip
history is transmitted anywhere** — the app has no networking code of its own.
A local counter that never leaves the device through the app is not
"collection" under Play's Data Safety definitions.

## Android backup — the accurate semantics (T-30.1 wording)

`android:allowBackup="true"` is set explicitly in
`app/src/main/AndroidManifest.xml`. It **documents the platform default**; it
does not create any Problip backup, sync or account mechanism — no backup
agent, no backup rules, no Problip cloud.

What that means, stated without contradiction:

- Problip itself has no account/backend/cloud-sync mechanism; nothing
  re-downloads or restores Problip data from Problip.
- Clear App Data removes Problip's current local copy; uninstall removes the
  current installation's local copy. Problip does not promise survival across
  either.
- **Separately**, Android system backup / device-transfer MAY preserve and
  restore some app-private data depending on the user's device, account and
  platform settings. Problip does not operate that service, does not control
  it, does not guarantee that backup happens, and does not guarantee
  cross-device restoration.
- Consequently the 100K earned Premium flag (and the statistics) **may
  reappear** after a reinstall or device transfer if Android restores the
  relevant app data — but Problip does not promise it, and never claimed the
  opposite ("never restored" would have been false; the docs were corrected
  in T-30.1 rather than backup being disabled to make the old sentence true).

## Privacy posture (published policy summary)

- No account, no sign-in, no identification, no personal information
  collected, transmitted, sold or shared.
- No advertising SDK, no analytics SDK, no crash-reporting SDK, no
  advertising identifier.
- No location, contacts, calendar, photos, files, microphone, camera, call
  log, SMS or installed-app-list access.
- Cached Google Play ownership state (which products this Google account
  owns) lives locally so unlocks show while offline.
- Purchases are processed by Google Play; Problip never sees or stores
  payment credentials, and receives only ownership/purchase state.

## Data Safety form notes

- Answers and their evidence live in `docs/data-safety.md`; every
  interpretation carries **REVERIFY_AT_SUBMISSION** against live Console
  guidance.
- "No custom networking code" is NOT proof third-party SDKs are irrelevant to
  the form: the Billing SDK and purchase-history/ownership state must be
  re-checked at submission. Billing vendors `transport-backend-cct`
  (Google's own telemetry transport, invoked by Google's client code, never
  by Problip code) — the answer there is "vendored by Play Billing",
  not "used by Problip".
- The privacy policy still carries a contact-email placeholder —
  HUMAN_CONTACT_REQUIRED, never filled by automation.
