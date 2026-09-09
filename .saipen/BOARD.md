# Board

## DOING

## TODO

## DONE








## BLOCKED
- [ ] T-29 [P1] T-28B [P1] Final curated audio assets: replace the five synthesized placeholder premium WAVs with the user's final set, preserving stable ids sound_glass/sound_wood/sound_soft_bell/sound_bonk/sound_space (settings persistence, trials, Billing products), fill reference/audio/SOURCES.md ledger rows, run the T-28 audio QA list, remove scripts/make_placeholder_sounds.py only after real replacement, then a separate feat: replace placeholder sounds with final audio commit | verify: Final WAVs supplied by user; audio QA list passes; SOURCES.md rows filled; SoundCatalog/ProductCatalog ids unchanged; gradlew test assembleDebug assembleRelease lint lintVitalRelease PASS; separate commit created | blocker: WAIT_USER_DECISION -- HUMAN_ASSET_BLOCKED -- final curated premium WAVs must be supplied by the user (five sounds, stable ids sound_glass/sound_wood/sound_soft_bell/sound_bonk/sound_space); no agent seat can produce the audio; scripts/make_placeholder_sounds.py + reference/audio/SOURCES.md stay until real replacement
- [ ] T-25 [P1] Execute external audit inbox layer audit/1.md (SRC-001); source_receipt=SRC-001 | verify: every actionable clause of SRC-001 is terminal with evidence; linked Work DONE; source closure succeeds; audit/1.md consumed by the journaled audit inbox cleanup; source_receipt=SRC-001 | source_receipts: SRC-001 | owner: opencode | claim_time: 2026-09-05T15:14:33Z | blocker: WAIT_USER_DECISION -- SRC-001 cannot close: 44 of 45 clauses are terminal (38 IMPLEMENTED, 5 VERIFIED, 1 SUPERSEDED) but R42 -- manual acceptance on a physical device: the trial sound run, the trial theme run, the purchase-during-trial run and the fifteen widget rows -- is BLOCKED and linked to T-014. Closure requires every actionable clause terminal AND linked Work DONE, and BLOCKED is not terminal, so the receipt stays ACTIVE and audit/1.md may not be consumed. Missing authority is a physical Android device; no repository evidence can substitute for it, and inventing a device result would be the one unrecoverable lie in this wave. R44 and R45 are now IMPLEMENTED (commit e71ef34, fourteen-item report delivered in E-72), so this ticket is waiting on hardware alone.

- [ ] T-009 [P2] W8 Google Play developer/merchant setup: account, legal identity, payments profile, payout details, Estonia/EEA tax data | blocker: WAIT_USER_DECISION -- Play Console account work is human-only; no agent seat can create or verify a merchant profile
- [ ] T-010 [P1] W7 live purchase gate: buy, PENDING handling, acknowledgement, app restart, reinstall/restore, offline cached UX, refund/revocation review, plus confirming Billing still works with play-services-location excluded (T-016) | needs: T-009 | blocker: WAIT_USER_DECISION -- needs real Play Console products plus an internal test track; nothing about a live purchase is verifiable from this seat
- [ ] T-014 [P1] W4 device/screen-off measurement gate: 30 min foreground, 60 min background, 60 min locked, Battery Saver, Doze, Bluetooth, headphones; now also the audit/3.md acceptance run A-F (5 s foreground, 5 s minimized via the new MINIMIZE button, 5 s locked 30 min, random 4-7 locked 30 min, STOP from the shade, forced audio ERROR) with ProblipTiming drift samples, the audit/4.md PULSE acceptance (5 min screen-on showing the ~5 / ~10-20 alternation, 30 min locked where SHORT stays ~5 s and LONG stays near its chosen value, drift samples separating intentional pauses from suspend), plus the three device-only rows and the 10x torture-sequence checklist from docs/qa-audit-w12.md | blocker: WAIT_USER_DECISION -- requires a physical device session; the agent seat has no device automation, and both the roadmap and audit/3.md demand physical-device evidence
- [ ] T-015 [P2] W9 publication: host docs/privacy-policy.md at a stable public URL, fill the contact-email placeholder, paste the URL into the Play Console listing, and link it from the app | blocker: WAIT_USER_DECISION -- needs a human-owned domain/hosting choice and a contact address; the in-app link lands with the W11 secondary screens (T-013)
