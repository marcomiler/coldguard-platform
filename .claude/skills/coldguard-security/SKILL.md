---
name: coldguard-security
description: Reviews auth, authorization, secrets and audit controls.
---

The short rules (never log secrets, RBAC by actor role, audit every relevant transition, Gateway is
the sole JWT validator, `.env`/Key Vault split) already live in `.claude/rules/security.md` — not
restated here. This skill adds the current implementation status, the concrete risk tracking it,
and a review checklist.

## Current status: designed, not implemented

**RBAC/JWT is a closed design decision (ADR-007, ADR-008), not running code yet.** Confirmed
against the actual scaffold: `apps/gateway/src` has only empty `package-info.java` placeholders per
layer (`config`, `api`, `application`, `domain`, `infrastructure`) — no JWT filter, no Spring
Security configuration class exists yet. Per `docs/planning/roadmap.md`, real authentication/RBAC
lands in **Sprint 4** ("Seguridad y gRPC", RF-015/CU-016, tagged APF2 v0.2); Sprint 5 completes the
Identity & Access internal module (RF-013/CU-014, DEC-004/DEC-008) alongside Incident Service.
- In APF1, the Gateway performs no real JWT validation — the frontend login is a demonstration
  without a security backend (RN-016). That's a documented, deliberate phase boundary, not a bug to
  flag in an APF1-phase review.
- **Open risk R-014** (`docs/quality/risk-register.md`): ADR-007 fixes the RBAC *mechanism* but not
  the concrete rol→endpoint mapping; that mapping must be defined before Sprint 4 builds the
  protected endpoints, or security review at that point has nothing concrete to check against.
  Treat "RBAC is declared" and "RBAC is operationalized" as different claims — don't conflate them
  in a review.

## Architecture context (by ID — don't repeat full content here)

- Secret management: `.env`/`.env.example` locally (now real: `deploy/local/.env.example` exists),
  Key Vault + Managed Identity planned for Azure, not provisioned — DEC-010,
  `docs/architecture/deployment-view.md`.
- Auditable state transitions: RN-008; owning module is Audit Log — DEC-005, RF-018/CU-009.
- RBAC roles are exactly the actors already defined in `docs/product/stakeholders.md`:
  Administrador de plataforma, Supervisor de operaciones, Operador, Técnico de mantenimiento,
  Auditor — do not invent a role outside that list.

## Reference cases (already decided — do not reopen)

- **The Gateway is the only JWT validator; internal services never re-implement edge auth**
  (ADR-008) — currently reflected in the scaffold itself: `spring-boot-starter-security` is present
  only in `apps/gateway/pom.xml` (DEC-011), not in the other four service modules. The reference
  case for "which module gets the security starter."
- **Secrets never live in code, `.env` (versioned), Terraform state, or logs**
  (`.claude/rules/security.md`) — the reference case for where credential material is and isn't
  allowed to appear.
- **Audit Log is a read-only, restricted-query internal module of Incident Service** (RF-018/CU-009,
  DEC-005), not a separate service and not a general-purpose logging sink — the reference case for
  "audit trail" scope: it records the transitions RN-008 requires, not arbitrary application logs.

## Verification checklist

- [ ] No secret, token, or credential appears in code, logs, commit history, or a Claude Code
      settings file (`settings.local.json` included).
- [ ] Any new JWT/auth-adjacent code lives in the Gateway module, not in an internal service
      (ADR-008); an internal service should only ever consume a propagated identity.
- [ ] Any new role name maps to one of the five actors in `docs/product/stakeholders.md` — flag,
      don't invent, if a review surfaces a need for a role outside that list.
- [ ] Any state transition RN-008 requires to be auditable is actually recorded (actor, timestamp,
      motive, previous/new value) through the Audit Log module, not through ad hoc logging.
- [ ] Nothing in scope creates an Azure Key Vault resource, identity, or permission — DEC-010's
      Azure column is planned, not provisioned (`.claude/rules/infra.md`).
- [ ] Before Sprint 4 builds protected endpoints, the rol→endpoint mapping (R-014) is actually
      defined somewhere concrete — not assumed to exist because ADR-007 declared RBAC in general.

**This skill does not reopen closed architecture decisions.** For an audit of current consistency
between docs/architecture, docs/domain and the ADRs — without proposing changes — use the
`architecture-auditor` agent instead.
