# Play Data Safety — Problip

Wave: W9. Derived from the shipped code, not from intent. Re-verify before every
Data Safety submission; Play's form and policy wording change.

Evidence base (2026-09-04, re-verified 2026-09-05 against Play Billing 9.1.0):

- App-declared permissions: `app/src/main/AndroidManifest.xml`
- Effective permissions after manifest merge:
  `app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`
- Dependency inventory: `gradlew :app:dependencies --configuration releaseRuntimeClasspath`
- Source audit: no match in `app/src` for `HttpURLConnection|OkHttp|Retrofit|java.net|Socket|URL(|WebView|firebase|Analytics|AdvertisingId`

## Form answers

| Question | Answer | Basis |
|----------|--------|-------|
| Does your app collect or share any of the required user data types? | **No** | App has no network code, no accounts, no identifiers, no analytics/ads SDK. All settings stay in app-private DataStore. |
| Is all of the user data collected by your app encrypted in transit? | N/A (nothing collected) | No app-originated traffic. Billing traffic is Google Play's own TLS channel. |
| Do you provide a way for users to request that their data is deleted? | N/A (nothing collected) | Uninstall removes the local DataStore file; there is no server-side copy. |
| Data types: location, personal info, financial info, health, messages, photos, audio recordings, files, calendar, contacts, app activity, web browsing, app info and performance, device or other IDs | **None** | No matching permission, no matching API use. Problip plays audio; it never records it. |
| Purchase history | **Not declared as app collection** | One-time purchases are processed by Google Play. Play Billing returns ownership state to the app; the app caches it locally and transmits nothing. Google Play's own processing is disclosed by Google, not by this app. **Re-check this line against current Play guidance at submission time.** |
| Committed to follow the Play Families policy | Only if the store listing targets children | Not planned for v1.0. |

Privacy policy URL: publish `docs/privacy-policy.md` at a stable public URL and
paste that URL into the Play Console listing. The policy file still contains a
`<ADD CONTACT EMAIL BEFORE PUBLISHING>` placeholder.

## Permission inventory

Declared by Problip:

- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — user-started beep
  session, `foregroundServiceType="mediaPlayback"`, visible notification,
  user-stoppable (declaration text belongs to W10).
- `POST_NOTIFICATIONS` — the session notification.

Added by the Google Play Billing library through manifest merge:

- `INTERNET`, `ACCESS_NETWORK_STATE`, `com.android.vending.BILLING`
- `com.vacster.problip.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (AndroidX
  local-broadcast plumbing; not a user-facing permission)

No location, storage, contacts, camera, or microphone permission exists in the
merged manifest.

## Third-party SDK inventory

`com.android.billingclient:billing-ktx:9.1.0` is the only non-AndroidX,
non-Kotlin dependency. It pulls transitively:

- `com.google.android.gms:play-services-base` / `-basement` / `-tasks`
- `com.google.android.datatransport:transport-api` / `-runtime` /
  `-backend-cct`, and with them `com.google.firebase:firebase-encoders`
  (serialization only)

Everything else on the release runtime classpath is `androidx.*`,
`org.jetbrains.kotlin*`, `com.squareup.okio` (a DataStore dependency),
`com.google.guava:listenablefuture`, `org.jspecify:jspecify`.

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
   permission set is unchanged. A live purchase still has to pass on a device
   before release (tracked with the live purchase gate).
2. `androidx.fragment:fragment` is constrained to 1.8.6. `play-services-base`
   resolved it to 1.1.0, which is below the 1.3.0 that
   `registerForActivityResult` requires; `lintVitalRelease` failed the release
   build over it.

One residual caveat: `transport-backend-cct` is Google's telemetry transport,
shipped inside the Billing library and used by Google's own client code. Problip
code never calls it. If Play's SDK-index review asks about it, the answer is
"vendored by Play Billing", not "used by Problip".
