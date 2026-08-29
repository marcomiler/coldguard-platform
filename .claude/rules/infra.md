- The MVP must run locally with Docker Compose (`deploy/local/docker-compose.yml`, RNF-001) —
  already implemented (compose file, 5 `Dockerfile`s, `.env.example` all exist), not just planned.
- `docker compose build` runs locally under the project's default permission mode — not gated by
  `.claude/settings.json`'s deny/ask lists.
- `docker compose up` requires confirmation per `.claude/settings.json`'s ask list; a `docker
  compose` command targeting an Azure/production-named file or project name is blocked outright
  (deny list) — never described as permitted here.
- `az` commands and any real Azure resource are blocked by `.claude/settings.json`'s deny list.
  No Azure deployment is permitted from this file — Azure remains planned, not provisioned
  (DEC-002, DEC-009, DEC-010).
- Terraform operations (`apply`/`destroy`/`import`) require explicit human approval and follow
  `.claude/rules/terraform.md` in full — this file only states that they're not part of local MVP
  execution, it doesn't repeat terraform.md's rules.
- Environment structure: **`local` is mandatory and is the only environment with a real
  deployment today**; **`azure-planned` is a design/config target only**
  (`docs/infrastructure/environments.md`) — never described as provisioned.
- No MVP dependency may require Internet or cloud connectivity to *run* locally — only to *fetch*
  it once (base images, Maven artifacts). After that, the stack must operate fully offline.
- Secret and credential handling follows `.claude/rules/security.md` — this file doesn't repeat
  that policy.
