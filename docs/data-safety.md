# Play Data Safety — Problip

Waves: W9 + T-27/T-28/T-30 refresh. Derived from the shipped code, not from
intent. Re-verify before every Data Safety submission; Play's form and policy
wording change (live Console guidance = REVERIFY_AT_SUBMISSION).

Evidence base (2026-09-08, re-verified at HEAD dd742e3 + T-30 against Play
Billing 9.1.0):

- App-declared permissions: `app/src/main/AndroidManifest.xml`
- Effective permissions after manifest merge:
  `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`
  (and on the shipped artifact: `aapt2 dump badging` over the release APK)
- Dependency inventory: `gradlew :app:dependencies --configuration releaseRuntimeClasspath`
- Source audit (2026-09-08): grep over `app/src/main/java` for
  `HttpURLConnection|OkHttp|Retrofit|java\.net|Socket|WebView|firebase|Analytics|AdvertisingId`
  — the only hit is a HelpDialog comment stating there is **no WebView**.
  Zero analytics/backend/ad-id code.
- Manifest backup declaration: `android:allowBackup="true"` set explicitly in
  `app/src/main/AndroidManifest.xml` (T-30.1). This documents the platform
  default — it adds no backup agent, no custom backup rules and no sync;
  Android system backup remains controlled by the user's platform settings.

## Local data stores (current product)

Two separate Preferences DataStore files in app-private storage:

| Store | File | Contents |
|-------|------|----------|
| Settings | `problip` (`SettingsRepository.kt:25`) | volume, interval/mode, custom MANUAL from/to, selected sounds, theme, per-item five-minute trial expiries, Developer Access expiry, showBlipCounter, blipGlowEnabled, cached Google Play ownership state |
| Statistics | `problip_stats` (`BlipStatsRepository.kt:22-23`) | local aggregate counts: today / ISO-week / month / total successful blips, plus the `earnedPremium` flag latched at 100,000 successful blips |

The stats store is local only. **No counter, aggregate, timestamp or blip
history is transmitted anywhere** — Problip does not transmit these statistics
to a Problip server or third-party analytics service, and the app has no
networking code of its own (source audit above). Android system
backup/device-transfer may independently back up app-private data according to
the user's platform settings. The earned flag is local app data, distinct from
Google Play ownership; clearing app data removes the current local copy (no
Problip survival promise — Android backup may restore it depending on platform
settings, which Problip neither operates nor guarantees).

## Form answers

| Question | Answer | Basis |
|----------|--------|-------|
| Does your app collect or share any of the required user data types? | **No** | App has no network code, no accounts, no identifiers, no analytics/ads SDK. Settings AND the `problip_stats` aggregates stay in app-private local DataStore files; nothing is transmitted. Android system backup/device-transfer may independently back up app-private data according to the user's platform settings. A local counter that never leaves the device through the app is not collection under the form's definitions — **REVERIFY_AT_SUBMISSION** against current Console guidance before relying on this. |
| Is all of the user data collected by your app encrypted in transit? | N/A (nothing collected) | No app-originated traffic. Billing traffic is Google Play's own TLS channel. |
| Do you provide a way for users to request that their data is deleted? | N/A (nothing collected) | Uninstall removes both local DataStore files; there is no server-side copy. |
| Data types: location, personal info, financial info, health, messages, photos, audio recordings, files, calendar, contacts, app activity, web browsing, app info and performance, device or other IDs | **None** | No matching permission, no matching API use. Problip plays audio; it never records it. The stats aggregates are counts of in-app events, not user-identifying data. |
| Purchase history | **Not declared as app collection** | One-time purchases are processed by Google Play. Play Billing returns ownership state to the app; the app caches it locally and transmits nothing. Google Play's own processing is disclosed by Google, not by this app. **Re-check this line against current Play guidance at submission time.** |
| Committed to follow the Play Families policy | Only if the store listing targets children | Not planned for v1.0. |

Do not automatically flip any final Play Console answer merely because a local
counter now exists — the deciding fact is transmission, and the current code
transmits nothing (see the source audit above). Keep every Play-form
interpretation as REVERIFY_AT_SUBMISSION; do not fabricate current Console
questions from memory.

Do not treat "no custom networking code" as proof that third-party SDKs are
irrelevant to the Data Safety form: at live submission, re-verify the current
Play guidance for the Google Play Billing SDK, purchase history / ownership
state, and third-party SDK handling generally (the Billing-vendored
`transport-backend-cct` below is the concrete example).

## Android system backup (explicit declaration)

`android:allowBackup="true"` is set explicitly in the manifest (T-30.1). It
documents the existing platform default; it does not change the product
contract and it does not create any Problip backup, sync or account mechanism.
Android system backup / device-transfer may preserve and restore app-private
data (including the local DataStore files) depending on the user's device,
account and platform settings; Problip does not operate that service, does not
guarantee that backup happens, and does not guarantee cross-device
restoration. This is not app-operated data transfer and is not declared as
collection on the form above — **REVERIFY_AT_SUBMISSION**.

Privacy policy URL: publish `docs/privacy-policy.md` at a stable public URL
after a real contact email is supplied (blocker: HUMAN_CONTACT_REQUIRED) and
paste that URL into the Play Console listing. The in-app link points there
(T-015 owns publication).

## Permission inventory

Declared by Problip:

- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — user-started beep
  session, `foregroundServiceType="mediaPlayback"`, visible notification,
  user-stoppable (declaration text: `docs/play-fgs-declaration.md`).
- `POST_NOTIFICATIONS` — the session notification.
- `WAKE_LOCK` — one service-owned `PARTIAL_WAKE_LOCK` held only while a session
  is RUNNING so screen-off intervals stay accurate (see
  `docs/play-fgs-declaration.md` for the implementation evidence).

Added by the Google Play Billing library through manifest merge:

- `INTERNET`, `ACCESS_NETWORK_STATE`, `com.android.vending.BILLING`
- `com.vacster.problip.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (AndroidX
  local-broadcast plumbing; not a user-facing permission)

No location, storage, contacts, camera, microphone, exact-alarm or
battery-optimization permission exists in the merged manifest.

## Third-party SDK inventory

`com.android.billingclient:billing-ktx:9.1.0` is the only non-AndroidX,
non-Kotlin dependency. It pulls transitively:

- `com.google.android.gms:play-services-base` / `-basement` / `-tasks`
- `com.google.android.datatransport:transport-api` / `-runtime` /
  `-backend-cct`, and with them `com.google.firebase:firebase-encoders`
  (serialization only)

Everything else on the release runtime classpath is `androidx.*`,
`org.jetbrains.kotlin*`, `com.squareup.okio` (a DataStore dependency),
`com.google.guava:listenablefuture`, `org.jspecify:jspecify`. No analytics SDK,
no ad SDK, no crash reporter, no location SDK, no networking client of Problip's
own.

Two deliberate dependency decisions (`app/build.gradle.kts`):

1. `play-services-location` is **excluded** from the Billing dependency. Billing
   pulls it in via POM only — re-verified on 9.1.0: its 607 classes contain no
   `gms/location`, `gms/places` or `placereport` reference — and shipping a
   location SDK in a beep timer that requests no location permission is a
   liability in review and in the Play SDK index. Verified after the exclusion:
   neither `play-services-location` nor `play-services-places-placereport`
   appears in `releaseRuntimeClasspath` (placereport reaches the graph only
   through location, so one exclude drops both), the release APK dex contains no
   `gms/location` or `placereport` strings, and the merged release manifest
   permission set is unchanged. The exclusion is re-confirmed for this wave in
   the T-30 dependency sweep. A live purchase still has to pass on a device
   before release (tracked with the live purchase gate).
2. `androidx.fragment:fragment` is constrained to 1.8.6. `play-services-base`
   resolved it to 1.1.0, which is below the 1.3.0 that
   `registerForActivityResult` requires; `lintVitalRelease` failed the release
   build over it.

One residual caveat: `transport-backend-cct` is Google's telemetry transport,
shipped inside the Billing library and used by Google's own client code. Problip
code never calls it. If Play's SDK-index review asks about it, the answer is
"vendored by Play Billing", not "used by Problip".
