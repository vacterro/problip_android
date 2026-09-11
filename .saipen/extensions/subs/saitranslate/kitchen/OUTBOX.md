# OUTBOX

<!-- SubSaipen writes results here. Format in PROTOCOL.md -->

## W-002: translation surface scan + producer-owned README mirrors (partial) — fresh re-issue
- **status:** draft
- **summary:** Force-fresh re-preparation: both real surfaces re-scanned against the current source identity, ja/uk mirror drafts restamped to the recomputed README digest, package rebound after the SAIPEN ledger advanced; the remaining 27 of the producer's 29 locales are not drafted -- named gaps, never rounded up to ready.
- **main_project_refs:** [README.md, app/src/main/res/values/strings.xml, app/src/main/res/values-ru/strings.xml, app/src/main/res/values-et/strings.xml, app/src/main/res/values-ja/strings.xml]
- **critical:** false
- **severity:** P2
- **producer:** saitranslate
- **source_head:** fb406727ea5d28b0a2be1c443367982b12628fce
- **source_tree_fingerprint:** git-delta-v1:3bf38be36accb8c9ffa04733e62aa313e414325826a8508734cbadcd93202652
- **role_revision:** sha256:f241e6b83c39e9b46bfa586638efb0374bbb39889646f723b9189bbb4912c0c5
- **coverage:** (1) docs surface: root README.md is the ONLY hand-maintained user document; no README locale siblings exist anywhere in the tree (plan/00_README.md is an internal roadmap doc, not a top-level user doc; other docs/*.md are engineering evidence docs, not user docs) -- the README mirror is the only doc deliverable. (2) UI surface: real strings.xml exist for the product's pinned en/ru/et/ja; machine check proves 138 string elements + 2 plurals (140 keys) in EACH of the four with zero missing and zero extra -- there is no in-app translation work to produce. (3) Producer-owned mirror drafts delivered: kitchen/locales/README.ja.md and kitchen/locales/README.uk.md, each ending with `<!-- source-digest: README.md sha256:31ce45f99116e9fb -->` (recomputed this run; see details on the prior marker).
- **payload:** .saipen/extensions/subs/saitranslate/kitchen/locales/README.ja.md and .saipen/extensions/subs/saitranslate/kitchen/locales/README.uk.md (on a future collect, integration target is the repository root as locale siblings beside README.md, keeping the language switcher convention)
- **verified:** PASS -- engine tools/freshness.py compute_source_identity at packaging time returned source_head fb406727ea5d28b0a2be1c443367982b12628fce, source_tree_fingerprint git-delta-v1:3bf38be36accb8c9ffa04733e62aa313e414325826a8508734cbadcd93202652; grep -c '<string name=' + '<plurals name=' over the four res/values*/strings.xml returned 138+2 in each of en/ru/et/ja, zero missing, zero extra; git diff b4cde5ad..HEAD -- README.md docs/ empty; recomputed README digest sha256:31ce45f99116e9fb matches both restamped markers; charter digest recompute equals STATE role_revision; write-scope check found no file outside the saitranslate home written
- **instructions:**
  1. This package is DRAFT by protocol: only complete 32-language + Дед coverage may go ready. Collecting it now is NOT requested; treat it as an incremental handoff for review of the ja/uk drafts.
  2. If Core wants the two mirrors integrated before the rest: verify each draft against README.md@fb406727 (digest line must recompute to sha256:31ce45f99116e9fb), then commit them as root README.ja.md / README.uk.md siblings in a docs-only commit, and keep their language switcher convention.
  3. Gaps that keep this at draft (named, per producer ownership): de, fr, es, it, pt, nl, pl, sv, da, fi, no, zh, ko, th, vi, ar, he, tr, hi, id, el, cs, ro, hu, bg, sk, hr -- 27 locales, plus the Дед voice which is a STYLE.md voice translation, not a doc mirror, and has no separate doc surface to own here.
  4. Core-owned surfaces (EN/EE/RU/DED) were checked, not touched: EN/RU/ET are current in-repo (README + real UI strings); no DED doc surface exists to own. Drift found: none.
  5. Re-run `saipen prepare saitranslate` after any README or strings.xml change -- this package's digest goes stale the moment the source moves.
- **details:** Why a fresh package exists: the prior draft W-001 was bound to source_head b4cde5ad, and the repository head has since moved to fb40672 through SAIPEN ledger commits. Head mismatch is staleness class 1 (PROTOCOL § 6) regardless of payload identity, so W-001 is invalidated below and this package re-binds. Correction recorded: the digest recorded at draft time (6d221d94fb9c1ba3) was a mis-derived value -- the translate contract's recomputation (every version string `\d+\.\d+\.\d+` normalized to literal VERSION) yields sha256:31ce45f99116e9fb for the byte-identical README; both drafts are restamped to the recomputed value and the derivation lesson is kept as a finding, per the charter's "a stale digest is a finding, never a silent refresh". The project already carries its product locales (en/ru/et/ja) as REAL res/values files at full parity -- so the only honest translation backlog is documentation mirrors for the 29 producer-owned locales, of which 2 (ja, uk) are drafted. The Дед voice is listed in the default six but is a chat voice contract, not a document format; no README.ded.md mirror exists to maintain, and inventing one is out of scope. No main-tree write was performed; integration authority stays with Core collect.

## W-001: translation surface scan + producer-owned README mirrors (partial) — SUPERSEDED
- **status:** stale
- **summary:** Original partial package, bound to source_head b4cde5ad; superseded by W-002 (same deliverables, current binding, restamped digest markers). Invalidated per PROTOCOL § 6 class 1 (source_head differs), never silently reused.
- **main_project_refs:** [README.md, app/src/main/res/values*/strings.xml]
- **critical:** false
- **severity:** P2
- **producer:** saitranslate
- **source_head:** b4cde5ad5ffe11ddaa152158db76f7aea0df6602
- **source_tree_fingerprint:** git-delta-v1:3bf38be36accb8c9ffa04733e62aa313e414325826a8508734cbadcd93202652
- **role_revision:** sha256:f241e6b83c39e9b46bfa586638efb0374bbb39889646f723b9189bbb4912c0c5
- **coverage:** as W-001
- **payload:** superseded by W-002 payload
- **verified:** BLOCKED -- superseded by W-002; not collectable as history per § 2
- **instructions:** Do not collect; history only.
- **details:** Evidence for invalidation: head moved (fb40672), product surface byte-identical, and the recorded source-digest value was corrected on restamp. Retained as history per § 2 (stale history is never deleted).
