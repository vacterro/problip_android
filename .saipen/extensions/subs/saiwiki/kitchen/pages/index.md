# Problip Wiki — Index

Mirrors: the `docs/` surface, `README.md`, and the `.saipen/` project memory.
Package: saiwiki W-001 (see `kitchen/OUTBOX.md` for freshness bindings).

Problip is a random-beep meditation timer for Android (port of the Windows
Problip v3). Brand: **Problip**. Play title: *Problip: Random Beep Timer*.
Application ID: `com.vacster.problip`. Release identity: **versionName 1.0.0,
versionCode 1**.

## Pages

| Page | Topic | Canonical source |
|------|-------|------------------|
| [01-overview.md](01-overview.md) | Product overview, stack, philosophy | `README.md` |
| [02-architecture.md](02-architecture.md) | Module layout, access rule, dependencies | `README.md`, `app/src/main/java/com/vacster/problip/` |
| [03-session-and-scheduling.md](03-session-and-scheduling.md) | Session, intervals, states, wake lock, background | `docs/behavior-contract.md`, `docs/play-fgs-declaration.md` |
| [04-sounds-themes-trials.md](04-sounds-themes-trials.md) | Sounds, themes, trials, Developer Access, earned Premium, Glow | `README.md`, `app/src/main/java/com/vacster/problip/{trial,ui}/` |
| [05-storage-and-privacy.md](05-storage-and-privacy.md) | DataStore stores, backup semantics, privacy posture | `docs/privacy-policy.md`, `docs/data-safety.md` |
| [06-billing.md](06-billing.md) | Play Billing, products, entitlement, Data Safety notes | `docs/data-safety.md`, `app/src/main/java/com/vacster/problip/billing/` |
| [07-release-engineering.md](07-release-engineering.md) | Version policy, builds, signing, artifact hygiene | `docs/release-checklist.md`, `docs/release-signing.md` |
| [08-qa-and-device-gates.md](08-qa-and-device-gates.md) | Test evidence, QA history, open human/device gates | `docs/qa-audit-w12.md`, `.saipen/BOARD.md` |
| [09-project-protocol.md](09-project-protocol.md) | SAIPEN project memory, ledger, receipts | `.saipen/`, SAIPEN CONFORMANCE.md |

## Status snapshot (at packaging)

- Full gate green: **260 unique JVM tests / 0 failures / 31 suites, lint 0 errors**.
- T-30 (pre-Play release engineering) and T-30.1 (release-truth hygiene) DONE.
- Blocking release: T-29 final curated WAVs (HUMAN_ASSET_BLOCKED), privacy
  contact email (HUMAN_CONTACT_REQUIRED), Play Console setup, signing
  credentials, physical-device acceptance.
