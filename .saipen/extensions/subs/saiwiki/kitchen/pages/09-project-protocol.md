# 09 — Project protocol (SAIPEN memory)

Mirrors: the `.saipen/` surface; SAIPEN CONFORMANCE.md (cold-continuation
principle). This page describes the project's own memory layout so a cold
contributor can navigate it.

## What lives where

```text
.saipen/
    STATE.md      current phase, next action, blockers, event counter
    BOARD.md      ticket board (T-### checkboxes: DOING/TODO/DONE/BLOCKED)
    LOG.md        active event journal (E-###, ≤ one line each)
    logs/         sealed LOG segments (verbatim, never edited)
    KNOWLEDGE/    project knowledge cards
    recovery/     conformance receipts + regenerated index (GENERATED)
    extensions/   bounded-improvement markers, subSaipen homes
```

- Files outrank model memory: an agent continuing the work reads STATE →
  BOARD → LOG tail and executes `next_action`, never a chat memory.
- Sealed log segments are immutable history; the active LOG continues the
  same event numbering.
- The conformance `recovery/` index is engine-generated: after gate receipts
  accumulate, the index is regenerated engine-side and the regenerated files
  are landed as a normal commit (done in T-30.1: receipt_count 21 → 69).

## Ledger conventions

- One event = one line: date, `[E-###]`, optional `[parent:]`/`[T-###]`/
  `[agent:]`, then a taxonomy tag (RUN/DEC/H) and the evidence. Claims carry
  hashes, byte counts, test counts — not adjectives.
- Tickets are `[P0-P2]` with a `| verify:` clause naming the evidence that
  closes them. A ticket is closed only when that evidence exists.
- Negative controls: a fix is accepted only when the mutated code demonstrably
  fails the named tests, then reverts green.

## Tickets that shape release (current)

- **T-29** — final curated WAVs (HUMAN_ASSET_BLOCKED; the only TODO on the
  board). Placeholders + `scripts/make_placeholder_sounds.py` stay until real
  replacement; then a separate `feat: replace placeholder sounds with final
  audio` commit.
- **T-30.1** (DONE) — release-truth/artifact-hygiene pass: dirty state
  resolved (two GENERATED conformance index files landed), backup wording
  corrected, explicit `allowBackup` declared, fresh 1.0.0 root exports with
  verified hashes, stale 0.1.0 exports removed.
- **T-014 / T-009 / T-010 / T-015** — device, Play setup, live purchase,
  publication (all human/physical authority; see page 08).

## Non-negotiables observed by every wave

- No history rewrite, no squash, no push without the user.
- Signing material never enters Git; keys are created only by a human with
  recorded credentials.
- No placeholder is renamed, no stock sound fetched, no audio gate claimed
  while T-29 is open; physical results are never invented.
- Post-close correctness amendments land as small numbered passes (e.g.
  T-27.1, T-30.1) instead of reopening closed work.
