Review `docs/academic`, `docs/product`, `docs/domain`, `docs/architecture` and `docs/operations` for consistency.

Check for:
- Actors or requirements without a corresponding use case.
- Business rules (RN) that reference a concept not defined anywhere (e.g. a field or classification with no owning requirement).
- Requirements (RF) without traceability to a use case (CU) or business rule (RN).
- Events (`docs/domain/commands-events.md`) implied by a rule but missing from the catalog.
- Architectural decisions visible in `system-context.md`, `container-diagram.md` or elsewhere that are not backed by an ADR.
- Duplicate or reused IDs across RF/RN/CU/R/ADR.
- Functionality beyond the MVP scope declared in `docs/academic` and `docs/product/scope-mvp.md`.

Report findings as a prioritized list (critical / high / medium / low), same format as prior documentation reviews in this project.

Do not edit files during the review. Propose changes first and wait for approval before editing.
