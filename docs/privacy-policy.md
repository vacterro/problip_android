# Problip Privacy Policy

Last updated: 2026-09-08

> **RELEASE BLOCKER — HUMAN_CONTACT_REQUIRED.** The contact section below still
> contains the `<ADD CONTACT EMAIL BEFORE PUBLISHING>` placeholder. A real
> support/privacy contact must be supplied by a human before this policy can be
> published; the placeholder is never filled by automation.

Problip is a random beep timer for Android. It is built to work without knowing
anything about you.

## What Problip does not do

Problip does not create an account, does not ask you to sign in, and does not
identify you. Problip does not collect, transmit, sell, or share personal
information. Problip contains no advertising SDK, no analytics SDK, and no
crash-reporting SDK, and it does not read or use an advertising identifier.
Problip makes no network requests of its own — there is no Problip backend and
no Problip-originated telemetry of any kind.

Problip does not access your location, contacts, calendar, photos, files,
microphone, camera, call log, SMS, or installed-app list. It requests none of
those permissions.

## What Problip stores

Problip stores your preferences and progress locally on your device, in the
app's private storage:

- volume
- interval / mode (including custom Manual Interval values)
- selected sounds
- selected theme
- language / app-level preferences
- blip counter visibility
- Blip Glow on/off preference
- temporary five-minute trial expiry times
- Developer Access expiry
- successful-blip statistics: TODAY / WEEK / MONTH / TOTAL aggregates
- the permanent local "earned Premium" flag reached at 100,000 successful blips
- cached Google Play ownership state (which products this Google account owns),
  so your unlocks still show up while offline

Nothing in that data identifies you, and nothing about it leaves the device
through Problip.

## Blip statistics stay local

The TODAY / WEEK / MONTH / TOTAL statistics are aggregate counts of successful
blips stored in a local app database on your device. **No blip history, no blip
timestamps, and no statistics are uploaded** — Problip has no server to send
them to. Clearing the app's data or uninstalling Problip deletes them; they are
not restored afterwards and Problip does not promise their survival across
"Clear App Data" or a cross-device restore.

## The 100K earned Premium flag

Reaching 100,000 successful blips permanently unlocks Problip's premium content
on that device. This earned unlock is **local app data**. It is distinct from
Google Play ownership: Play purchases are recorded by Google, while the earned
flag exists only in Problip's local storage. Like all local data, it is not
guaranteed to survive clearing app data, uninstalling, or moving to a new
device.

Uninstalling Problip deletes all of the data listed above. Android may include
app data in a device backup if you have enabled backups; that backup is
controlled by your Android/Google account settings, not by Problip.

## Permissions Problip requests

| Permission | Why |
|------------|-----|
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playing your beeps after you press START while the app is in the background or the screen is off. The session only starts when you start it, and it is always visible as a notification you can stop. |
| `POST_NOTIFICATIONS` | Show that notification. If you deny it, sessions still run, they are just not shown as a notification. |
| `WAKE_LOCK` | While a session runs, keep the CPU awake so short intervals stay accurate with the screen off. It never turns the display on. |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `com.android.vending.BILLING` | Added by the Google Play Billing library so purchases can reach Google Play. Problip itself makes no network requests of its own. |

## Purchases

Optional sounds and the themes pack are one-time purchases processed by Google
Play. Problip never sees or stores your payment details. Google Play tells
Problip only which products this Google account owns, and Problip caches that
list locally so your unlocks still show up while offline. Google's handling of
payment data is described in Google's own privacy policy.

## Children

Problip is a general-purpose utility. It collects no personal information from
anyone, including children.

## Changes

If this policy changes, the "Last updated" date above changes with it.

## Contact

Questions about this policy: <ADD CONTACT EMAIL BEFORE PUBLISHING>
