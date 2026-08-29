- Target design: JWT + Spring Security, RBAC by actor role (ADR-007). **Current state: not
  implemented** — `apps/gateway/src` has no JWT filter or Spring Security configuration yet, only
  empty package placeholders; real implementation is scoped to Sprint 4
  (`docs/planning/roadmap.md`). Do not write or review code as if edge JWT validation already
  runs.
- The Gateway is the sole component that validates the JWT at the edge (ADR-008). The propagation
  mechanism to internal services is **not yet decided** — ADR-007 leaves it open ("vía metadata
  gRPC... o revalidan el token según se defina en implementación"). Until a mechanism is chosen:
  internal services must never parse or trust a raw `Authorization` header themselves — that
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
