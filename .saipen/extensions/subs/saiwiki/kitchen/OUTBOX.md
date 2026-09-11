# OUTBOX

<!-- SubSaipen writes results here. Format in PROTOCOL.md -->

## W-003: maintained wiki surface for Problip (index + 9 pages) — fresh re-issue
- **status:** ready
- **summary:** Force-fresh re-preparation of the complete documentation page set mirroring the current docs/ surface and README; product tree byte-identical to the W-001 draft, package rebound to the current source identity after the SAIPEN ledger advanced.
- **main_project_refs:** [README.md, docs/behavior-contract.md, docs/data-safety.md, docs/privacy-policy.md, docs/play-fgs-declaration.md, docs/qa-audit-w12.md, docs/release-checklist.md, docs/release-signing.md, project .saipen memory (board/log/state surfaces)]
- **critical:** false
- **severity:** P2
- **producer:** saiwiki
- **source_head:** fb406727ea5d28b0a2be1c443367982b12628fce
- **source_tree_fingerprint:** git-delta-v1:3bf38be36accb8c9ffa04733e62aa313e414325826a8508734cbadcd93202652
- **role_revision:** sha256:54a42475a124ab0f27e83d600a284a9cc54d9668029c4828cfc48512b031df13
- **coverage:** kitchen/pages/index.md plus 01-overview.md, 02-architecture.md, 03-session-and-scheduling.md, 04-sounds-themes-trials.md, 05-storage-and-privacy.md, 06-billing.md, 07-release-engineering.md, 08-qa-and-device-gates.md, 09-project-protocol.md -- one page per stable topic cluster of the project, each mirroring its canonical source (named in the page header) by content and structure, not by line position.
- **payload:** .saipen/extensions/subs/saiwiki/kitchen/pages/ (the whole directory, 10 files)
- **verified:** PASS -- engine tools/freshness.py compute_source_identity at packaging time returned source_head fb406727ea5d28b0a2be1c443367982b12628fce, source_tree_fingerprint git-delta-v1:3bf38be36accb8c9ffa04733e62aa313e414325826a8508734cbadcd93202652, stable across drafting; git diff 638ccd39..HEAD -- README.md docs/ empty (pages byte-identical to the W-001 read, binding re-derived); every page re-read same-session against its canonical source at the current head (adopt-time read list in sub LOG E-004); charter digest recompute equals the STATE-recorded role_revision sha256:54a42475a124ab0f27e83d600a284a9cc54d9668029c4828cfc48512b031df13; write-scope check found no file outside the saiwiki home written at any point
- **instructions:**
  1. Run `saipen collect saiwiki` (or handle the package manually on the next doc wave). This is an `explicit`-policy producer: only this named command consumes it.
  2. Decide the integration target: recommended `docs/wiki/` in the main tree, committed verbatim (the pages are written as plain Markdown, no sub-internal references).
  3. If integrated under a different path, fix only the relative `kitchen/pages/` links inside index.md's page table (they are the only path-dependent lines).
  4. Re-verify freshness at collect time: source_head must equal fb406727ea5d28b0a2be1c443367982b12628fce and the fingerprint must match; any source mutation after packaging (including the W-002 doc fix, if applied first) invalidates the triple -- re-run `saipen prepare saiwiki` instead of editing the binding.
  5. Do not push, tag, or fold anything onto the main BOARD beyond the single collect ticket the integration creates.
- **details:** Why a fresh package exists: the prior ready package W-001 was bound to source_head 638ccd39, and the repository head has since moved to fb40672 through SAIPEN ledger commits (pre-audio state seal). Head mismatch is staleness class 1 (PROTOCOL § 6) regardless of payload identity, so W-001/W-002 are invalidated below and this package re-binds the identical page set to the current triple. Pages deliberately use stable references (symbol names, file paths, byte/hash values) instead of line numbers so ordinary doc waves do not rot the mirror. Page 09 documents the SAIPEN memory layout for cold contributors, including the T-30.1 precedent that regenerated GENERATED index files are landed, not hidden. Page 05 carries the corrected Android-backup semantics so the wiki never reintroduces the contradiction the T-30.1 pass removed.

## W-004: stale AndroidManifest line citations in docs/play-fgs-declaration.md (re-verified current)
- **status:** ready
- **summary:** The declaration doc cites AndroidManifest.xml:26-30 for the ProblipService declaration; the T-30.1 allowBackup edit moved that block to lines 29-33. Re-checked at the current head: still unfixed.
- **main_project_refs:** [docs/play-fgs-declaration.md, app/src/main/AndroidManifest.xml]
- **critical:** false
- **severity:** P2
- **producer:** saiwiki
- **source_head:** fb406727ea5d28b0a2be1c443367982b12628fce
- **source_tree_fingerprint:** git-delta-v1:3bf38be36accb8c9ffa04733e62aa313e414325826a8508734cbadcd93202652
- **role_revision:** sha256:54a42475a124ab0f27e83d600a284a9cc54d9668029c4828cfc48512b031df13
- **coverage:** the full citation set of docs/play-fgs-declaration.md checked against the current manifest: the `:4-7` permission-range citation is still correct; the `:26-30` service-range citation (line 11 of the doc) is stale by exactly the 4 lines the manifest comment + allowBackup attribute added (service block now :29-33); all source-file citations (ProblipSessionWakeLock/SessionWakeLock.kt, ProblipNotification.kt, MainActivity.kt, ProblipViewModel.kt, SettingsRepository.kt) were NOT re-verified line-by-line in this pass and are out of W-004 scope.
- **payload:** none (finding only; fix belongs to a main-agent doc wave, one str_replace of `:26-30` -> `:29-33` plus a fresh verify)
- **verified:** PASS -- grep -n over app/src/main/AndroidManifest.xml at fb40672 returned `android:name=".service.ProblipService"` at :31 inside a block starting :29, permissions still at :4-7; sed -n '11p' docs/play-fgs-declaration.md still shows the `:26-30` citation
- **instructions:**
  1. Fold into the next doc-touching wave (or fix immediately with the one-line str_replace above).
  2. Re-run the affected citation check after any future manifest edit above line 26 -- the lesson is that manifest-adjacent line citations rot silently.
- **details:** First recorded as W-002 at 638ccd39 and re-verified current at fb40672; carried forward here because the old entry is invalidated with its package binding. The defect was introduced by the legitimate T-30.1 manifest edit (ae65551), not by any protocol violation. Note for the integrating agent: this page set also mirrors the project's protocol memory (its STATE/BOARD/LOG surfaces are described on page 09 by layout and convention, never by referencing internal file paths in this package).

## W-001: maintained wiki surface for Problip (index + 9 pages) — SUPERSEDED
- **status:** stale
- **summary:** Original complete package, bound to source_head 638ccd39; superseded by W-003 (same page set, current binding). Invalidated per PROTOCOL § 6 class 1 (source_head differs), never silently reused.
- **main_project_refs:** [README.md, docs/]
- **critical:** false
- **severity:** P2
- **producer:** saiwiki
- **source_head:** 638ccd39380dbb3efd4d3eaef5139c95d4ec24b3
- **source_tree_fingerprint:** git-delta-v1:3bf38be36accb8c9ffa04733e62aa313e414325826a8508734cbadcd93202652
- **role_revision:** sha256:54a42475a124ab0f27e83d600a284a9cc54d9668029c4828cfc48512b031df13
- **coverage:** as W-001 (10 pages)
- **payload:** superseded by W-003 payload
- **verified:** BLOCKED -- superseded by W-003; not collectable as history per § 2
- **instructions:** Do not collect; history only.
- **details:** Evidence for invalidation: git diff 638ccd39..HEAD -- README.md docs/ is empty, but the freshness triple binds the full source identity, and head moved (fb40672). Retained as history per § 2 (reviewed/stale history is never deleted).

## W-002: stale AndroidManifest line citations — SUPERSEDED by W-004
- **status:** stale
- **summary:** Original finding entry, bound to source_head 638ccd39; finding itself re-verified current and re-issued as W-004 under the fresh binding.
- **main_project_refs:** [docs/play-fgs-declaration.md, app/src/main/AndroidManifest.xml]
- **critical:** false
- **severity:** P2
- **producer:** saiwiki
- **source_head:** 638ccd39380dbb3efd4d3eaef5139c95d4ec24b3
- **source_tree_fingerprint:** git-delta-v1:3bf38be36accb8c9ffa04733e62aa313e414325826a8508734cbadcd93202652
- **role_revision:** sha256:54a42475a124ab0f27e83d600a284a9cc54d9668029c4828cfc48512b031df13
- **coverage:** as W-002
- **payload:** superseded by W-004 (finding only)
- **verified:** BLOCKED -- superseded by W-004; not collectable as history per § 2
- **instructions:** Do not collect; history only.
- **details:** Retained as history per § 2; the live finding is W-004 in this OUTBOX.
