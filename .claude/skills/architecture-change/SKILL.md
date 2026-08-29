---
name: architecture-change
description: Orchestration workflow for changes touching architecture — protocol changes, service ownership, new cross-service flows, Gateway boundaries, Outbox/events/gRPC/REST, or structural changes to architecture diagrams/docs. Decides whether the change needs a new ADR, a new DEC, an update to an existing decision, or a plain documentation edit; delegates ADR authoring to new-adr and domain criteria to coldguard-architecture.
---

# architecture-change

## Purpose

Orchestrate an architecture-affecting change end to end: diagnose which existing decisions it
touches, decide the right kind of record (ADR, DEC, update, or plain doc edit), get explicit
approval, apply only what was approved, then validate traceability and consistency.

## When to use

- A protocol change (e.g. REST vs. gRPC vs. events for some interaction).
- A change to service ownership or a bounded context's boundary.
- A new cross-service flow.
- A change to the Gateway's responsibilities or edge boundary.
- A change to the Transactional Outbox, event catalog, gRPC contracts, or REST surface.
- A structural change to architecture diagrams or architecture documentation.

## Inputs

- A description of the proposed change and which service(s) it touches.
- Any known driver: a new requirement (RF/RN/RNF), a discovered gap, or a correction.

## Ownership and related skills

- Does not author the ADR document itself — once this skill decides a new ADR is needed, it hands
  the actual 8-section drafting and numbering to `.claude/skills/new-adr/SKILL.md`.
- Does not re-derive architecture domain knowledge already captured in
  `.claude/skills/coldguard-architecture/SKILL.md` (the ADR/DEC index, the C4 consistency
  checklist, the event-vs-gRPC criterion, the "before proposing an architectural change" 3-step
  check) — this skill reuses that knowledge instead of restating it.
- Does not perform the repo-wide, non-architecture-specific checks owned by
  `.claude/skills/audit-project/SKILL.md` (empty files, unrelated broken references, generic
  duplication) — only architecture-relevant findings are in scope here.
- Does not implement application/business logic for the change — that is a separate,
  code-level task (e.g. a vertical-slice implementation), out of this skill's scope.

## Allowed actions

- Read, Grep, Glob to diagnose which ADRs/DECs/RF/RN/RNF/docs are affected.
- `Edit`/`Write`, but only after explicit approval, and only on the specific files named in the
  approved proposal (an architecture doc, `docs/planning/decisions-log.md` for a new DEC entry, or
  handing ADR authoring to `new-adr`).
- Invoking `.claude/skills/new-adr/SKILL.md` for ADR creation/update.
- Invoking `.claude/skills/consistency-check/SKILL.md` after a change, to validate links/IDs/
  traceability.

## Forbidden actions

- No Terraform, Docker, Azure CLI, or deployment commands, at any step.
- No implementation of application/business logic.
- No editing `.claude/rules/architecture.md` unless the user explicitly authorizes it in that
  request — this skill's normal output is an ADR/DEC/doc change, not a rule change.
- No editing any ADR's content beyond what `new-adr`'s own rules already allow (a cross-reference
  in "Related ADRs"; never rewriting another ADR's decision).
- No modifying architecture documents before the user has approved the diagnosis.

## Workflow

1. **Identify** whether the proposed change touches an existing decision — check
   `docs/architecture/adr/` (ADR-001 onward) and `docs/planning/decisions-log.md` (DEC-001
   onward), reusing the check already described in `coldguard-architecture`'s "Before proposing an
   architectural change" section.
2. **Locate** the affected ADRs, DECs, RN/RF/RNF, and documents — candidates include
   `docs/architecture/system-context.md`, `container-diagram.md`, `component-diagram.md`,
   `docs/domain/bounded-contexts.md`, `docs/domain/commands-events.md`,
   `docs/architecture/tech-stack.md`, `docs/architecture/deployment-view.md`.
3. **Decide** the record type:
   - Contradicts or extends a closed ADR → propose a **new ADR**.
   - Fills a previously documented gap or pins an operational/planning detail within already-
     decided architecture → propose a **new DEC** entry (matching the existing DEC-001..DEC-011
     format in `docs/planning/decisions-log.md`).
   - Content update inside an existing, still-valid decision (e.g. correcting a stale fact) →
     propose an **update to that decision's own record**, not a new one.
   - Diagram/doc sync with no new ambiguity and full existing backing → propose a **plain
     documentation edit**, citing the ADR/DEC that already covers it.
   - None of the above resolves cleanly → **stop** (see Stop conditions).
4. **Present the diagnosis**: impacted files, the decision-type classification from step 3, and a
   draft outline (ADR skeleton, DEC entry draft, or the exact doc edit).
5. **Wait for explicit approval** before writing anything.
6. **Apply only the approved changes**: delegate ADR authoring to `new-adr`; write the DEC entry
   directly following the existing DEC template; edit docs only within their existing structure
   (a structural reorganization of a document category needs its own audit/proposal pass first,
   per `.claude/rules/documentation.md`).
7. **Validate**: run `consistency-check` against the touched docs, and re-check the relevant items
   of `coldguard-architecture`'s C4 consistency checklist.

## Stop conditions

- No ADR/DEC/RN/RF/RNF backs the premise of the proposed change.
- The change contradicts a closed ADR or DEC and the user hasn't confirmed they want to reopen it.
- The user has not approved the diagnosis from step 4.
- The classification in step 3 (update vs. new ADR vs. new DEC vs. plain doc edit) stays
  ambiguous after checking the available documents — stop and ask, don't guess.

## Expected output

- A diagnosis: which existing decisions are touched.
- A list of impacted files.
- A proposed ADR draft (via `new-adr`) or DEC entry draft, or the exact plain-doc edit proposed.
- A final diff, only after approval.
- A one-line note distinguishing the case handled: architectural change vs. content update within
  an already-resolved decision vs. structural documentation change vs. a pending decision that
  only needed a record, not new architecture.

## Example invocations

- "We're considering moving telemetry ingestion from gRPC to REST — run architecture-change on
  this."
- "Asset Service needs to own a new cross-service flow with Notification Service — diagnose what
  this affects before we touch anything."
- "The Gateway's responsibilities doc looks out of sync with ADR-008 — is this a new ADR or just
  a doc fix?"
