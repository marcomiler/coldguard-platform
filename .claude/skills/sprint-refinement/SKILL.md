---
name: sprint-refinement
description: Run backlog refinement (docs/planning/refinement-process.md, sprint N+1 only) and sprint-content planning (docs/planning/roadmap.md). Use before Sprint Planning, or when the user asks to refine, detail, ready, plan, or update a sprint.
---

Execute the refinement process already fixed in `docs/planning/refinement-process.md`. That
document is the source of truth; this skill operationalizes it and does not redefine its rules.
This skill also absorbs sprint-content planning (formerly the standalone `/plan-sprint` command,
removed to avoid duplicating this same flow — see "Sprint planning" below).

## Core rule

**Only sprint N+1 — the next one to start — gets detailed, never more than one sprint ahead.**
Epics for sprints beyond N+1 stay at epic level in `docs/product/product-backlog.md`, with their
RF/CU and target sprint, with no detailed user stories.

## Steps

1. Identify the current sprint N (from `docs/planning/roadmap.md` / the latest `sprint-N.md`) and
   confirm N+1 is the sprint being refined.
2. For each epic targeted at N+1: draft user stories in `Como/quiero/para` format, with every
   mandatory field from `docs/planning/definition-of-ready.md`.
3. Run the INVEST audit from `docs/planning/definition-of-ready.md` on every drafted story. Any
   "No lista" story is corrected in this same refinement or explicitly left out of the sprint.
4. If a story spans more than one CU or RN, evaluate splitting it per
   `docs/planning/definition-of-ready.md` §3 — split now, not later.
5. Add RF/RN/CU/ADR/DEC traceability and cross-check against
   `docs/domain/traceability-matrix.md`.
6. Before finalizing, verify:
   - Consistency with closed architecture (`docs/architecture/architecture-consistency-report.md`,
     state CERRADO) — no story may contradict a closed decision.
   - Consistency with `docs/planning/decisions-log.md` for any point decision after the
     architecture batch closed.
   - Every story meets `docs/planning/definition-of-ready.md` before leaving refinement.

## Never do

- Never detail stories for sprints beyond N+1, even if their epic already exists.
- Never renumber or delete existing IDs (RF, RN, RNF, CU, ADR, DEC, risk, epic, story).
- Never mark a story as completed during refinement — that only happens per
  `docs/planning/definition-of-done.md`, after the work is actually done.
- Never reopen a closed architecture decision; document it as a finding and escalate to
  `docs/planning/decisions-log.md` if a point adjustment is warranted.

## Output (refinement)

Show the drafted/updated stories and their INVEST audit result before writing to
`docs/product/product-backlog.md`; wait for approval before editing when the refinement surfaces a
finding that would require a new architecture or decisions-log entry.

## Sprint planning

Use this when the user asks to plan or update the *content* of one sprint in
`docs/planning/roadmap.md` — a step that typically precedes detailed refinement of that sprint's
stories (above), rather than the refinement itself.

1. Read the current sprint list in `docs/planning/roadmap.md` and the risk register
   (`docs/quality/risk-register.md`), especially R-001 (alcance excesivo).
2. Read `docs/domain/functional-requirements.md` and `docs/domain/use-cases.md` to ground the
   sprint scope in already-defined RF/CU — never invent a new RF/CU to fill a sprint.
3. Check whether the proposed sprint content depends on an architectural decision that still lacks
   an ADR; if so, flag it instead of assuming a resolution (escalate via the `new-adr` skill or
   `docs/planning/decisions-log.md`, don't decide it here).
4. Keep proposed sprint content a **vertical slice** (a working end-to-end path), not a
   layer-only slice, consistent with R-001's mitigation.
5. Do not add scope beyond the MVP (`docs/product/scope-mvp.md`).

### Output (sprint planning)

Show the proposed sprint content and wait for approval before editing `docs/planning/roadmap.md`
or the corresponding `sprint-N.md`.
