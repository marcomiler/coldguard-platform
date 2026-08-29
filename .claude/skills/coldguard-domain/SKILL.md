---
name: coldguard-domain
description: Applies domain terminology, business rules, requirements and use cases.
---

Use canonical terms from `docs/product` and `docs/domain`. Do not invent capabilities outside the
MVP.

## Architecture context (by ID — don't repeat full content here)

- Business rules: `docs/product/business-rules.md` (RN-001 to RN-019). Functional requirements:
  `docs/domain/functional-requirements.md` (RF-001 onward). Use cases:
  `docs/domain/use-cases.md` (CU-001 onward).
- Aggregates and bounded contexts (structure derived from the above, not a new source of truth):
  `docs/domain/aggregates.md`, `docs/domain/bounded-contexts.md`.
- The 7-step target process and its RF/RN/CU/event traceability table: `docs/domain/domain-model.md`.
- Full traceability matrix (RF → CU → RN → evento → prueba futura): `docs/domain/traceability-matrix.md`.

## Key rules to respect

- Priority (P1–P4) always comes from the impacto/urgencia matrix (RN-012), never a direct
  severity→priority mapping (RN-013).
- Asset criticality (RN-009), impacto (RN-010) and urgencia (RN-011) are distinct concepts; don't
  conflate them.
- Incident deduplication/update follows the equivalence key in RN-004/RN-005 (same activo + sensor
  + tipo de anomalía).
- SLA clock: `IncidentAcknowledged` stops the reconocimiento SLA; `IncidentClosed` stops MTTR
  (RN-006). `IncidentClosed` is the only technical-closure event — `IncidentResolved` does not
  exist in this project's event catalog.

## Reference cases (already decided — do not reopen)

- **Sensor lifecycle is a 4-state machine (ACTIVO, EN_MANTENIMIENTO, INACTIVO, RETIRADO, RN-017)
  distinct from connectivity loss (RN-020)** — a sensor can be ACTIVO and disconnected at the same
  time; these are two separate, non-overlapping attributes. The reference case for "don't collapse
  two distinct domain concepts into one field just because they're related."
- **Identity & Access, Audit Log, and operational queries are internal modules of Incident
  Service** (RF-013/CU-014, RF-018/CU-009, RF-009/CU-008; DEC-004, DEC-005, DEC-006, DEC-008) —
  domain capabilities that live inside the Incident Service's aggregate boundary, not a separate
  bounded context of their own.
- **`AssetRegistered` has no confirmed event consumer** (`docs/domain/commands-events.md`) — the
  reference case for "a known gap gets documented as a gap, not silently filled with an invented
  consumer."

## New HU/RF vs. clarifying an existing one

Not every change to a story is a new requirement. Use `docs/planning/definition-of-ready.md` to
tell them apart, rather than guessing case by case:

- **It's a clarification, not a new HU/RF**, when the change only fills in or tightens a field the
  story already has room for — acceptance criteria written as Given/When/Then instead of a vague
  sentence, a missing "Dependencias"/"Prioridad"/"Estimación" field, splitting an over-scoped story
  per the DoR §3 template — and the RF/CU it traces to doesn't change. Edit the existing HU/RF in
  place; no new ID.
- **It's a new HU/RF**, when the change introduces a capability not covered by any existing RF/CU.
  Per DoR §4: register the gap in `docs/domain/traceability-matrix.md` first — never detail a story
  for an untraced capability — then it gets the next free RF/CU/HU number
  (`.claude/rules/documentation.md`: never reuse or renumber).
- **Either way**, a change that would contradict a decision already closed in
  `docs/architecture/architecture-consistency-report.md` (CERRADO) or `decisions-log.md` gets
  flagged as "No lista" per DoR §4, not resolved by editing the story — that's
  `architecture-auditor` / `new-adr` territory, not this skill's.

## Verification checklist

Basic ID-linking and MVP-scope rules already live in `.claude/rules/documentation.md` — not
restated here. This checklist covers domain-specific judgment calls that rule doesn't make:

- [ ] Priority, impact, urgency, and asset criticality are never conflated or shortcut into a
      single derived value not backed by RN-009 to RN-014.
- [ ] Any new domain capability is checked against `docs/domain/bounded-contexts.md` before being
      described as its own context — most turn out to be an existing module (Identity & Access,
      Audit Log, operational queries) rather than a new one.
- [ ] A story-level change is classified as clarification vs. new HU/RF using the criterion above,
      not assumed either way by default.

**This skill does not reopen closed architecture decisions.** For an audit of current consistency
between docs/architecture, docs/domain and the ADRs — without proposing changes — use the
`architecture-auditor` agent instead.
