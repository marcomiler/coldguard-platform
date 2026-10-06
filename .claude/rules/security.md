- Target design: JWT + Spring Security, RBAC by actor role (ADR-007). **Current state: Gateway
  implemented, user administration pending.** `apps/gateway` issues RS256 JWTs at
  `POST /api/v1/auth/login` (credentials verified by Identity & Access in `incident-service` over
  gRPC/mTLS), validates them at the edge, and applies deny-by-default RBAC: every route is declared
  in `SecurityConfig`, anything else is rejected. The role→endpoint table lives in
  `docs/security/authn-authz.md`; `RbacPolicyTest` walks it, so change both together. User
  administration (`/users`) is implemented and requires `PLATFORM_ADMIN` both at the Gateway and
  in Identity. Still pending: persisting the audit of identity changes (`AuditRecorder` keeps
  nothing until SPEC-007). Most RBAC routes are
  declared but their controllers do not exist yet; do not write or review code as if they did.
- The Gateway is the sole component that validates the JWT at the edge (ADR-008). Propagation is
  **implemented** (ADR-007, third update): the Gateway sends `x-actor-id` and `x-actor-roles` as
  gRPC metadata over mTLS, and `incident-service` (`ActorServerInterceptor`) accepts it only when
  the peer certificate CN is `gateway`; from any other client it is ignored. Services without a
  gRPC server (asset, telemetry) must reuse that interceptor when they get one. Internal services
  must never parse or trust a raw `Authorization` header themselves — that re-implements edge auth
  regardless of which mechanism is eventually picked.
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
