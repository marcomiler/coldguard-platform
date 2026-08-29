---
name: new-adr
description: Create a new ADR in docs/architecture/adr/ with the next free ADR number, or update an existing ADR — never renumbering or overwriting an unrelated one. Use when the user asks to record, document, or propose an architectural decision.
---

Create or update exactly **one** ADR in `docs/architecture/adr/`, following
`ADR-0NN-short-slug.md`.

## Before writing

1. List `docs/architecture/adr/` and find the highest existing `ADR-0NN`. The new ADR uses the
   next free number — never reuse, renumber, or overwrite an existing one, even if a gap exists
   (`.claude/rules/documentation.md`).
2. Check `docs/architecture/architecture-consistency-report.md` (state: CERRADO) and
   `docs/planning/decisions-log.md` for whether this decision is already closed. If it is, do not
   silently reopen it — flag that to the user and ask whether this is a genuinely new decision, a
   refinement, or a duplicate.
3. Check whether the decision affects `docs/architecture/system-context.md`,
   `docs/architecture/container-diagram.md`, or `docs/domain/commands-events.md` — if so, the ADR
   must exist before (or alongside) any edit to those files (`.claude/rules/documentation.md`).

## Required structure (exactly these 8 sections, in this order)

1. Contexto
2. Problema
3. Opciones consideradas
4. Decisión
5. Consecuencias
6. Riesgos
7. Related ADRs
8. Evolución futura a Azure

Cross-reference other ADRs by ID in "Related ADRs" whenever the decision depends on, or is
depended on by, them. "Evolución futura a Azure" describes the future path without creating any
cloud resource now.

## Rules

- Do not edit code.
- Do not create cloud resources, do not execute `terraform apply`, `az`, or `docker compose up`.
- Do not edit any other ADR's content beyond adding a cross-reference in "Related ADRs".
- Show the file to create/modify and its justification before writing it; wait for approval before
  writing when the decision affects already-closed architecture.
