# 06 — Billing

Mirrors: `docs/data-safety.md`, `app/src/main/java/com/vacster/problip/billing/`.

## Products

One-time purchases only — no subscriptions. Two products carry everything
premium:

- The five premium sounds (stable ids `sound_glass`, `sound_wood`,
  `sound_soft_bell`, `sound_bonk`, `sound_space`).
- `theme_pack` — the Customization Pack (14 premium palettes + Blip Glow).

Product IDs are **never renamed** (see `docs/release-checklist.md`).

## Entitlement rule

```text
free || owned || activeTrial || developerAccess || earnedPremium
```

- `owned` outranks everything transient; a `DEV` unlock is displayed as `DEV`,
  never `OWNED`.
- `PENDING` never grants access and has its own UI state (owned > pending >
  price).
- The 100K earned reward is a separate source that never mutates Play
  ownership and is never revoked by a reset, refund or Billing refresh.

## Client behaviour (BillingRepository)

- Play Billing 9.1.0 (`billing-ktx`); connection state machine with explicit
  reconnect on `onBillingServiceDisconnected` and `enableAutoServiceReconnection()`.
- `refresh()` queries when connected, connects otherwise — wired at
  `onResume()`, so ownership changes made elsewhere (second device, refund,
  revocation) are re-queried for the connection's life.
- Every `PURCHASED` and not-yet-acknowledged purchase is acknowledged after
  each purchase update and each refresh; acknowledgement never decides
  ownership.
- One FIFO mutex per query kind: the newest refresh publishes last, so a slow
  response cannot restore ownership Play already superseded.
- Product details are an immutable map published through `@Volatile` — no
  torn reads between the IO query and `purchase()`.
- Query failures surface as store errors (resource ids, localized), never a
  silent dead client.
- Ownership is cached locally (page 05) so unlocks show while offline.

## Data Safety / review notes

- Problip never receives card/payment credentials; Google Play processes the
  purchase and discloses its own processing.
- Problip receives only ownership/purchase state needed for entitlement;
  cached ownership stays local.
- Purchase history is **not declared as app collection**; re-check against
  live Play guidance at submission (REVERIFY_AT_SUBMISSION).
- `play-services-location` is excluded from the Billing dependency (declared
  by POM, unused by its 607 classes; no `gms/location` or placereport strings
  in the release dex). `androidx.fragment` is constrained to 1.8.6 —
  load-bearing for `registerForActivityResult`.
- The live purchase gate (buy, PENDING handling, acknowledgement, restart,
  reinstall/restore, offline cache, refund/revocation) needs real Play
  Console products, an internal test track and a licensed test account —
  ticket T-010, human-blocked.
