# Handoff Rules for AI Implementation Agents

Use this file together with `01_MASTER_ROADMAP.md`.

## Operating mode

Implement one wave at a time.

Never accept a vague target such as:
- improve everything
- make architecture production-grade
- add whatever is missing

Every wave must have:
1. explicit scope
2. explicit non-goals
3. build/test gate
4. evidence
5. known limitations

## Mandatory discipline

Before coding:
- read README
- read PRODUCT_SCOPE_AND_CONSTITUTION
- read ARCHITECTURE
- read only the current wave from MASTER_ROADMAP

During implementation:
- do not implement future waves
- do not add infrastructure for hypothetical features
- keep dependencies minimal
- preserve stable IDs
- preserve free-product usefulness

After implementation:
- run tests
- build
- report exact failures if any
- fix failures within current wave
- summarize changed files
- mention deviations explicitly

## When to audit

Recommended checkpoints:
- after W1
- after W4
- after W7
- before W14
- before W17

Audit focus:
- correctness
- liveness/background reliability
- race/resource leaks
- billing lifecycle
- Play policy alignment

Do not refactor for aesthetic reasons during an audit unless a verified defect requires it.
