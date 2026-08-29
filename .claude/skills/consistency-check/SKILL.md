---
name: consistency-check
description: Validate Markdown links and ID traceability (RF/RN/CU/R/ADR/events) across docs/ without touching closed architecture or domain content. Use when the user asks to check, audit, or validate documentation consistency, or after any batch of documentation edits.
---

Read-only audit. This skill reports findings; it never edits `docs/architecture`, `docs/domain`,
or any other content directly — that is a separate, explicitly-approved step.

## Checks

1. **Markdown links**: every `[text](relative/path.md)` link across the touched (or, if asked,
   the whole `docs/` tree) resolves to a file that exists, and every inline reference of the form
   `` `docs/.../file.md` `` points to a real file.
2. **ID integrity**: no `RF-`, `RN-`, `RNF-`, `CU-`, `R-`, `ADR-`, `DEC-`, `HU-`/story, or event ID
   is duplicated or reused for two different items across `docs/`.
3. **Traceability**: every RF references the CU(s)/RN(s) it supports; every CU references its RF,
   related RN, and the event(s) it emits/consumes when applicable — per
   `.claude/rules/documentation.md`. Flag gaps against
   `docs/domain/traceability-matrix.md`, don't silently fill them in.
4. **Architecture backing**: any content in `docs/architecture/system-context.md`,
   `docs/architecture/container-diagram.md`, or `docs/domain/commands-events.md` that reflects an
   unresolved architectural ambiguity is backed by an ADR (`.claude/rules/documentation.md`) — flag
   any that isn't.
5. **ADR structure**: every ADR in `docs/architecture/adr/` uses the standard 8 sections, in order
   (Contexto, Problema, Opciones consideradas, Decisión, Consecuencias, Riesgos, Related ADRs,
   Evolución futura a Azure).
6. **Scope**: nothing described exceeds the MVP scope declared in
   `docs/academic/01-propuesta-proyecto.md` / `docs/product/scope-mvp.md`.
7. **Numeric placeholders**: no SLA/KPI numeric target is stated as confirmed when it's actually a
   pending/academic placeholder (`.claude/rules/testing.md`, `.claude/rules/documentation.md`).

## Rules

- Never edit `docs/architecture` or `docs/domain` content to "fix" a finding — this skill reports,
  it does not correct architecture or domain documentation on its own
  (`architecture-auditor` agent has the same constraint; this skill is the tool it and others use).
- Never renumber or delete an ID to resolve a duplicate — report the duplicate and let the user
  decide which one is authoritative.
- Do not run any infrastructure command (no `docker`, `terraform`, `az`, `git push`) as part of the
  check.

## Output

A prioritized findings list (critical / high / medium / low), same format as prior documentation
reviews in this project (see `.claude/commands/review-docs.md`). Propose fixes; wait for approval
before editing anything.
