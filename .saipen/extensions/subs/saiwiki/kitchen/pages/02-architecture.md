# 02 — Architecture

Mirrors: `README.md` (Layout section), the source tree
`app/src/main/java/com/vacster/problip/`.

## Module layout

```text
app/                        application module
    src/main/java/com/vacster/problip/
        core/               pure scheduling core (no Android dependencies)
        trial/              five-minute trials + Developer Access (pure)
        billing/            Play Billing wrapper + entitlement rules
        service/            foreground service, notification, wake lock, cold start
        widget/             home-screen START/STOP widget
        ui/                 Compose screens + palettes
    src/test/java/          deterministic JVM unit tests (no Robolectric)
```

`core/` and `trial/` are pure Kotlin: injectable clock/delay/random sources,
JVM-testable, no Android framework types. Android lives at the edges
(service, UI, widget, billing).

## Reference assets (documentation, not dependencies)

- `reference/windows/` — original Windows source + assets (behavioural reference).
- `reference/wintage/` — 16 source palette JSONs. The palettes are **compiled
  into** `ui/theme/Palettes.kt` as literal colours; nothing reads the JSON at
  runtime. `custom.json` is included for history; its duplicate preset was
  removed for release (see page 04).

## The access rule (one place)

Every premium surface — sounds, theme pack, MANUAL, PULSE, Blip Glow —
resolves through a single precedence chain:

```text
free || owned || activeTrial || developerAccess || earnedPremium
```

- `owned` (Google Play) outranks everything transient.
- `developerAccess` (hidden 7-day unlock) is shown as `DEV`, never `OWNED`,
  because it expires and a purchase does not.
- `earnedPremium` (local 100K reward) is a separate source that never mutates
  Play ownership. See page 05.
- Trial labels rank below developer access: a DEV-granted row never advertises
  a `TRY 5 MIN` countdown.

## Runtime invariants

- Exactly one scheduling loop, one foreground service, one audio engine.
- The running session is **never** persisted: after reboot or force-stop the
  app comes up STOPPED (no boot receiver, `START_NOT_STICKY`).
- Cold start is barriered (`service/ColdStart.kt`): the first plan waits for
  persisted settings AND access readiness (Billing ownership + earned flag),
  with a 5-second valve that degrades to free content instead of a broken
  notification.

## Key dependencies

- `androidx.datastore:datastore-preferences` (two stores — see page 05)
- `com.android.billingclient:billing-ktx:9.1.0`, with
  `play-services-location` **excluded** (declared by its POM, unused by its
  607 classes — no location SDK in a beep timer).
- `androidx.fragment` constrained to 1.8.6 (Play services would resolve it to
  1.1.0, below what `registerForActivityResult` needs).

Full dependency/permission inventory: `docs/data-safety.md`.
