---
name: coldguard-architecture
description: Reviews service boundaries, gRPC, events and ADR compliance.
---

Use REST at the edge, gRPC internally and events for cross-service workflows. Keep service
boundaries explicit.

## Architecture context (by ID — don't repeat full content here)

- ADR-001 (monorepo), ADR-002 (frontend in a separate repo), ADR-003 (gRPC internal), ADR-004
  (RabbitMQ for the MVP), ADR-005 (Incident→Notification is async), ADR-006 (shared PostgreSQL
  instance, schema-per-service), ADR-007 (JWT/RBAC), ADR-008 (Gateway responsibilities), ADR-009
  (Transactional Outbox) — the full text lives in `docs/architecture/adr/`.
- Point decisions after the architecture batch closed live in `docs/planning/decisions-log.md`
  (DEC-001 onward), not in a new/edited ADR unless they cross the ADR threshold themselves.
- Container/context diagrams: `docs/architecture/system-context.md`,
  `docs/architecture/container-diagram.md`, `docs/architecture/component-diagram.md` (Incident
  Service only, for now — Asset/Telemetry/Notification stay at container level until the team
  decides to deepen them; that asymmetry is deliberate, not a gap). Bounded contexts:
  `docs/domain/bounded-contexts.md`.
- Backend framework version: **Spring Boot 4.1.1** (DEC-011, corrected 2026-08-29 — a same-day
  correction of an initial, verified-wrong "4.x not yet published" entry; see
  `docs/architecture/tech-stack.md`). If a review or proposal assumes any Spring Boot version
  other than 4.1.1, that's stale — check `pom.xml`/`tech-stack.md` before trusting either this
  file or an older draft.

## Reference cases (already decided — do not reopen)

- **RabbitMQ as a planned Azure Container Apps container** (DEC-009): the MVP broker isn't
  replaced by Azure Service Bus or any managed alternative; it's the same RabbitMQ, deployed as a
  container. Use this as the pattern for "what stays the same across local vs. Azure planned."
- **Identity & Access, Audit Log, and operational queries as internal modules of Incident
  Service, not separate microservices** (DEC-004, DEC-005, DEC-006, DEC-008): the reference case
  for "when a capability gets its own module vs. its own service" in this project — the answer so
  far has consistently been "internal module," not a new deployable.
- **Incident Service never calls Notification Service directly** (ADR-005, ADR-009): publishes to
  RabbitMQ via the Transactional Outbox; Notification Service consumes from there. The reference
  case for "cross-service workflow" always being event-driven, never a direct call, even when a
  synchronous-looking flow would be simpler to write.
- **The Gateway is the sole REST-to-gRPC edge and the sole JWT validation point** (ADR-007,
  ADR-008) — reflected in the scaffold itself (`spring-boot-starter-security` only in
  `apps/gateway/pom.xml`, DEC-011). The reference case for "which container owns edge concerns";
  for the security-control depth of this (RBAC, secrets, audit), see `coldguard-security`, not
  this skill.

## C4 consistency checklist (context → container → component → deployment)

For `architecture-auditor` (or anyone auditing manually) to check the four C4 levels against each
other quickly — only checks actually supported by what exists in `docs/architecture/` today:

- [ ] **Context ↔ Container**: every actor in `system-context.md` reaches the system only through
      `coldguard-frontend` → Gateway (REST); no actor or external system in `system-context.md` is
      drawn talking directly to an internal container in `container-diagram.md`.
- [ ] **Container ↔ Container**: every arrow in `container-diagram.md` matches a decision already
      on record — gRPC internal (ADR-003), REST only at the Gateway (ADR-008), async
      Incident→Notification via RabbitMQ/Outbox (ADR-005, ADR-009), schema-per-service in
      PostgreSQL with no cross-schema access (ADR-006).
- [ ] **Container ↔ Component**: `component-diagram.md` currently only exists for Incident
      Service; a review should not expect (or silently invent) component-level detail for Asset,
      Telemetry, or Notification Service — that's a known, deliberate scope limit, not a
      documentation gap to fill without the team deciding to.
- [ ] **Container ↔ Deployment**: every container named in `container-diagram.md` appears as a
      service in `deployment-view.md`'s local view (A) and, for the Azure-planned view (B), maps
      to a planned Azure Container Apps workload (DEC-002) — never to a resource presented as
      already provisioned.
- [ ] **Any diagram change**: an edit to `system-context.md`, `container-diagram.md`, or
      `docs/domain/commands-events.md` that reflects an unresolved architectural ambiguity is
      backed by an ADR first, never a silent diagram edit (`.claude/rules/documentation.md`).

## Rule elaboration (moved here from rules/architecture.md to keep that file short)

- **REST-at-the-edge and Actuator/health endpoints**: ADR-008 doesn't mention operational
  endpoints at all — it's silent on them, not permissive. Per
  `docs/infrastructure/docker-strategy.md` (a candidate design doc, not an ADR), each service
  would expose `/actuator/health` for Docker Compose's own healthcheck ordering — that's
  container-internal traffic Compose uses to sequence startup, never routed through the Gateway or
  reachable from `coldguard-frontend`. It's a different category of endpoint, not a carved-out
  exception to "REST only at the edge." No ADR authorizes a service to expose additional
  business REST (e.g. an inbound webhook) outside the Gateway; that needs a new ADR, not an
  implicit exception.
- **Event vs. gRPC criterion**: taken directly from ADR-003's Decisión ("comunicación asíncrona
  cross-service... no usa gRPC") and ADR-005's Problema/Opciones (Incident Service must not block
  or degrade if Notification Service is down). The rule of thumb: **event** when the producer's
  own transaction must succeed independently of the consumer's availability; **gRPC** when the
  caller needs an immediate answer to continue its own operation. No ADR defines a third criterion
  (e.g. by volume or latency) — don't invent one in a review.
- **Transactional Outbox mechanism — how firm is "poller"**: ADR-009's "Decisión" section only
  fixes the Outbox *pattern*, not the publisher mechanism. The poller appears in "Consecuencias"
  as *"opción más simple para el MVP"*, and "Evolución futura a Azure" calls CDC *"candidato
  ilustrativo... si se justifica"* — i.e. CDC was never evaluated and discarded with the same
  weight as the poller, it's simply out of scope unless a future decision revisits it. Treat the
  MVP mechanism as **decided (poller)**, not "pending," but don't cite ADR-009 as if it forbade
  CDC forever — it just doesn't currently choose it.
- **`domain`/`application` isolation — literal vs. inferred scope**: DEC-008 literally says ports
  belong "únicamente en los límites externos: persistencia, mensajería, correo, observabilidad" —
  it does not name Java packages. DEC-011 is what actually defines the five-folder convention
  (`config`, `api`, `application`, `domain`, `infrastructure`), citing DEC-008 as its rationale.
  So the accurate citation for "which packages must stay agnostic" is **DEC-011, reasoning from
  DEC-008** — not DEC-008 alone (an earlier draft of this file cited DEC-008 by itself for the
  package convention, which overstated what DEC-008 itself defines). Extending the boundary to
  `api/`/`config/` (not just `domain/`/`application/`) is this skill's inference from the same
  layer split, not a line either decision states — flagged as such in the rule.

## Before proposing an architectural change

1. Check `docs/architecture/adr/` (ADR-001 to ADR-009) and `docs/planning/decisions-log.md`
   (DEC-001 onward) for an existing decision that already covers it.
2. If the change contradicts a closed decision, flag it and propose a new ADR/DEC entry instead of
   silently diverging — don't implement around a closed decision.
3. If it's genuinely new ground, propose the ADR before implementing (`new-adr` skill).

## Verification checklist

The short rules (REST-at-edge/gRPC-internal, no cross-schema access, Transactional Outbox for
reliable publishing, no bypassing the Gateway) already live in `.claude/rules/architecture.md` —
not restated here. This checklist covers what that short rule list doesn't:

- [ ] Any new capability that could plausibly become "its own service" is checked against DEC-004/
      DEC-005/DEC-006/DEC-008 before assuming it needs one — the answer has consistently been
      "internal module of Incident Service," not a new deployable.
- [ ] The four C4-consistency checks above pass, or a mismatch is flagged rather than silently
      resolved.
- [ ] Any code or proposal that assumes a Spring Boot version other than 4.1.1 (DEC-011) is
      flagged, not silently trusted.

**This skill does not reopen closed architecture decisions.** For an audit of current consistency
between docs/architecture, docs/domain and the ADRs — without proposing changes — use the
`architecture-auditor` agent instead.
