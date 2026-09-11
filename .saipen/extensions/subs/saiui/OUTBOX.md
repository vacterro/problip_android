# SAIUI OUTBOX — T-56.1 canonical UI review

Date: 2026-09-10
Scope: OFF/TOTAL/STATS counter selector, Main four-metric stats strip (TODAY/WEEK/MONTH/TOTAL), Glow entitlement row (PREMIUM+TRY 5 MIN / TRIAL mm:ss / OWNED / EARNED / DEV), full-Main Glow visual (drawContent() + accent overlay, 60/70/210 ms, max alpha 0.25), Main no-scroll contract, four-locale (EN/RU/ET/JA) + font-scale 1.0/1.3 robustness.
Contract: saipen UI.md (Golden Default). No redesigns proposed; implementable decisions only.

---

## STRIP GEOMETRY  (Q1)

**Verdict: FAIL (one-line fix) — cells must be equal width.**

Current `MainStatsStrip.kt:68` uses `Row(horizontalArrangement = Arrangement.SpaceBetween)` with intrinsic-width `StatCell`s. The four cells are NOT equal width: each `Column` takes only its own content width, so "TODAY" (wider) and "TOTAL" (wider) push the tight ones left/right unevenly. UI.md iron law: *"Use aligned edges. Misalignment reads as bug, not style."* and *"Keep the number of simultaneous visual layers low"* — an uneven 4-cell strip reads as a layout bug.

**Decision: `Modifier.weight(1f)` on every cell, `Arrangement.spacedBy(4.dp)` for the gap, content centered per cell.**

UI.md tables rule: *"Use numeric alignment for numbers and dates."* This applies to multi-row table columns where decimals must align on a shared edge. Here each cell holds exactly ONE label + ONE number, stacked — there is no column of mixed-width decimals to align, so per-cell **center** alignment is the correct "aligned edges" reading. Center both label and value under each other.

Exact change (`MainStatsStrip.kt`):
```kotlin
Row(
    modifier = modifier.fillMaxWidth().semantics { contentDescription = a11y },
    horizontalArrangement = Arrangement.spacedBy(4.dp),
) {
    values.forEach { (labelRes, count) ->
        StatCell(
            label = stringResource(labelRes),
            value = formatBlipCountForStrip(count),
            modifier = Modifier.weight(1f).clearAndSetSemantics {},
        )
    }
}
```
`StatCell` Column gains `horizontalAlignment = Alignment.CenterHorizontally`. Add imports: `androidx.compose.ui.Alignment`, `androidx.compose.foundation.layout.Arrangement` (already present).

---

## TEXT FIT  (Q2)

**Verdict: FAIL (missing guards) — must pin one line + deterministic overflow.**

Cell width: quarter of main area. Worst case compact phone 360dp: padding 6dp, gap 3dp → ~81dp per cell at font-scale 1.3.

Measured content (Monospace, char ≈ 0.6×fontsize):
- Labels (9sp → at 1.3 ≈ 11.7sp ≈ 7px/char). Longest: RU `НЕДЕЛЯ`=6 → ~42px+track; ET `KOKKU`=5; JA `今週`=2 CJK. All < 81dp. ✅
- Values (12sp → 1.3 ≈ 15.6sp ≈ 9.4px/char). `<100K` grouped e.g. `99,999`=6 → ~56px; `≥100K` compact `99.9K`/`1.2M`=5 → ~47px. All < 81dp. ✅
  (`formatBlipCountForStrip` already caps width at 100K — see `CountFormat.kt:31`.)

So content fits at both scales, but the texts have **no `maxLines`/`softWrap`/`overflow`**, so an unexpected string (future translation, a longer compact form) could wrap and break the no-scroll contract silently. UI.md demands *"Predictable behavior"* and *"The UI remains readable as a screenshot."*

**Decision: every strip Text gets `maxLines = 1`, `softWrap = false`, `overflow = TextOverflow.Clip`.** Clip (not Ellipsis) because values are formatter-guaranteed to fit and labels are ≤6 chars in all four locales — a `…` artifact is never wanted. Defensive, deterministic, no reflow.

```kotlin
Text(
    text = label,
    color = P.Muted,
    fontFamily = FontFamily.Monospace,
    fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 1.sp,
    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
)
Text(
    text = value,
    color = P.TextDim,
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp, lineHeight = 15.sp,
    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
)
```

---

## GLOW  (Q3)

**Verdict: PASS — keep `GLOW_MAX_ALPHA = 0.25f`. No adjustment required (smallest adjustment = none).**

UI.md iron law 2 lists *"zero transparency"*, but rule 9 carves the only sanctioned exception: *"The one sanctioned movement is `button:active`'s 1px shift. It is instant, it is physical feedback for a press the user themselves caused, and it is the entire motion budget."* The Blip Glow is instant feedback for a **user-caused event** (the blip sound the user is playing) — the same class as `button:active`. It is not decorative (UI.md: *"No visual decoration without function"* / *"do not decorate alerts with extra visual drama"*); it is event feedback.

Palette check at 0.25 over the three named palettes:
- **Golden Default (CLASSIC):** Gold `F0D060` over dark `1A1810`, bright text `D4C89A` → 25% gold tint softens but never hides. ✅
- **OLED:** Gold = `FFFFFFFF`; 25% white over black bg + gray text `A0A0A0` briefly brightens, recovers in ~330ms. ✅
- **VINTAGECLASSIC (the one light palette):** Gold = `F6F6F6` (near-white) over black text `000000` on `C0C0C0` — most contrast-risky. But the pulse is **60+70+210 = 340 ms** and only fires on a blip the user triggered; text contrast is fully restored immediately after. UI.md's transparency ban targets *persistent* state loss (e.g. faded disabled controls: *"quieter via --textMuted … never via opacity"*). A transient 340ms event flash is outside that concern.

Decision: keep `0.25f`. The `drawContent()`-then-rect order (`BlipGlow.kt:67`) is correct — drawing the accent ABOVE opaque Surface/Raised panels is what makes "the whole Main lights up" real; moving it below text would hide it behind panels and break the physical expectation. No change.

Optional (NOT required): a palette-aware cap could clamp the light palette lower, but that is extra complexity against UI.md *"the SMALLEST adjustment"* — default to none.

---

## TOKENS  (Q4)

**Verdict: PASS — no token violations.**

- All strip colors trace to palette tokens via `P.*`: `P.Muted` (label), `P.TextDim` (value). `P` maps 1:1 to `ProblipColors` slots (`ProblipScreen.kt:91`), which are the 15 verbatim Wintage palettes (`Palettes.kt`) — closed set, no ad-hoc hex anywhere in the strip.
- Glow accent uses `LocalProblipColors.current.Gold` — a palette token, not a literal. ✅
- No `BorderRadius` / rounded corners added (none exist in the strip). ✅
- No Material `Card` added — the strip is `Row`/`Column` of `Text`, exactly as its doc comment states (*"not four Material cards"*). ✅
- No strip-height increase: `StatCell` Column ≈ 26dp (label 12 + value 15 lineHeights), bounded and fixed. ✅
- Main no-scroll contract holds: the strip is a fixed-height row inside the top `Column(fillMaxSize())`; the action body is `Modifier.weight(1f)` (`ProblipScreen.kt:370`) and absorbs remaining space. STATS mode adds one bounded row, OFF renders nothing, TOTAL renders one `Text` line — none introduce a scroll container. ✅

Known out-of-contract deviation (NOT in scope, noted for the record): the app uses `FontFamily.Monospace`, not Verdana non-AA (UI.md iron law 1). This is a pre-existing, app-wide pragmatic Android choice and outside the 5-question scope; flag, do not fix here.

---

## ALIGNMENT  (Q5)

**Verdict: FAIL (size floor) — bump label 9sp → 10sp; keep letterSpacing 1.sp.**

- **Size:** `StatCell` label is `9.sp`. UI.md iron law 1: *"Sizes only 10/11/12/14/16px."* 9sp is below the contract floor. The rest of the app already uses 10sp for secondary metadata (`IntervalTrialLabel`, `developer_label`, `earned_label` all 10sp). Bump label to **10.sp** to satisfy the iron law and match the app's own scale. Width impact: RU `НЕДЕЛЯ` at 10sp×1.3 ≈ 53px < 81dp cell — still fits. ✅
- **letterSpacing 1.sp:** at 10sp this is ~10% tracking — within the normal 5–15% uppercase tracking band and it aids the *"readable as screenshot"* / *"readable when stripped of color"* goals for small uppercase labels. JA `今日`/`今週` have no case; 1sp tracking is harmless there. **Keep 1.sp.** ✅
- Value stays **12.sp** (valid per the size list).

Applying the 9→10sp change to the `StatCell` snippet in TEXT FIT above (label `fontSize = 10.sp, lineHeight = 12.sp`).

---

## VERDICT SUMMARY (implementable decisions only)

1. STRIP GEOMETRY — FAIL → `Modifier.weight(1f)` per cell + `Arrangement.spacedBy(4.dp)`, `Column` `Alignment.CenterHorizontally`. Equal columns, centered label/value.
2. TEXT FIT — FAIL → all strip `Text`: `maxLines = 1`, `softWrap = false`, `overflow = TextOverflow.Clip`. Content already fits EN/RU/ET/JA at scale 1.0 & 1.3.
3. GLOW — PASS → keep `GLOW_MAX_ALPHA = 0.25f`; keep `drawContent()`-then-rect order. Transient user-caused pulse = `button:active` class (UI.md rule 9). No change.
4. TOKENS — PASS → `P.*` only, no hex, no cards, no corners, no height bump, no scroll. (Monospace-vs-Verdana noted, out of scope.)
5. ALIGNMENT — FAIL → label `9.sp → 10.sp` (UI.md size floor); keep `letterSpacing = 1.sp`; value `12.sp` unchanged.

Net code touch: `MainStatsStrip.kt` only (Row arrangement + `weight(1f)`, `StatCell` Column alignment + label 10.sp + text-fit params; add `Alignment` import).
