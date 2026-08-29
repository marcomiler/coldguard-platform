- Never reuse, renumber or delete an existing ID (RF-, RN-, CU-, R-, ADR-, evento). New items get
  the next free number.
- Every new functional requirement (RF) must reference the use case(s) (CU) and business rule(s)
  (RN) it supports.
- Every new use case (CU) must reference its RF, related RN, and the event(s) it emits or
  consumes, when applicable.
- Do not add functionality outside the MVP scope declared in
  `docs/academic/01-propuesta-proyecto.md` and `docs/product/scope-mvp.md`.
- Every ADR follows the same 8 sections, in this order: Contexto, Problema, Opciones consideradas,
  Decisión, Consecuencias, Riesgos, Related ADRs, Evolución futura a Azure.
- A change to `docs/architecture/system-context.md`, `docs/architecture/container-diagram.md` or
  `docs/domain/commands-events.md` that reflects an unresolved architectural ambiguity must be
  backed by an ADR, not a silent diagram edit.
- Numeric SLA/KPI targets that are not confirmed by the business must be marked explicitly as
  pending/academic placeholder, never invented — `.claude/rules/testing.md` follows this same
  policy for test assertions instead of repeating it.
- **Operational convention (repository process, not an ADR)**: a structural change to
  architectural or domain documentation — a new section type, a new ID scheme, restructuring how a
  whole document category is organized — goes through audit/proposal/approval before it's written.
  A content update within the existing structure (a new RF, a new ADR, a corrected reference)
  doesn't need this.
