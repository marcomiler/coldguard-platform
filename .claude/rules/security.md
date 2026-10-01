- Target design: JWT + Spring Security, RBAC by actor role (ADR-007). **Current state: partial.**
  `apps/gateway` has Spring Security with a resource-server JWT converter, but only
  `POST /api/v1/incidents/*/close` is protected and the decoder fails closed because no issuer is
  configured; there is no login, no user store and no RBAC for the other routes. The full
  implementation is specified in `docs/specs/SPEC-004-identidad-seguridad.md` (deny-by-default
  RBAC table). Do not write or review code as if edge JWT validation already covers the API.
- The Gateway is the sole component that validates the JWT at the edge (ADR-008). The propagation
  mechanism is **decided** (ADR-007, third update): the Gateway sends `x-actor-id` and
  `x-actor-roles` as gRPC metadata over mTLS, and internal services accept it only from the
  `gateway` client identity (target design; today only the interim `x-actor-role` metadata for
  the close endpoint exists). Internal services must never parse or trust a raw `Authorization`
  header themselves — that
  re-implements edge auth regardless of which mechanism is eventually picked.
- Never log secrets, tokens, or credentials — in code, telemetry, traces, or business events
  (RNF-008).
- Every relevant state transition (RN-008) must be auditable, including who performed it.
- RBAC roles map to the actors already defined in `docs/product/stakeholders.md`; do not invent
  new roles outside that list.
- Local secret management: `.env` (not versioned, excluded by `.gitignore`) + `.env.example`
  documenting expected keys with no real values (DEC-010) — already implemented
  (`deploy/local/.env.example` exists).
- Azure secret management: Key Vault + Managed Identity — **planned, not provisioned**; creating a
  real Key Vault resource or identity requires explicit human approval per
  `.claude/rules/infra.md`'s Azure-resource rule.
- Terraform artifacts (state, tfvars, plans) that might carry secret values follow
  `.claude/rules/terraform.md` for handling — this file governs the secret values themselves, not
  the IaC files.
