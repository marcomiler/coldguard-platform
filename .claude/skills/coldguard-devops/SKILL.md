---
name: coldguard-devops
description: Maintains Docker Compose, GitHub Actions and Terraform.
---

The short rules (no Azure apply without approval, never commit secrets/logs/state files, Terraform
modular but unapplied) already live in `.claude/rules/infra.md` — not restated here.

## Architecture context (by ID — don't repeat full content here)

- Local execution: `deploy/local/docker-compose.yml` and `deploy/local/.env.example` now exist
  (11 services: `gateway`, `asset-service`, `telemetry-service`, `incident-service`,
  `notification-service`, `postgres`, `rabbitmq`, `mailpit`, `prometheus`, `grafana`, `loki`), each
  with a multi-stage `Dockerfile` per `apps/*/Dockerfile` — this has moved from "strategy only" to
  "implemented" since `docs/infrastructure/docker-strategy.md` was written; treat that document as
  the design rationale, and the actual compose file/Dockerfiles as the current source of truth for
  what's really there. **Not yet confirmed**: `docker-strategy.md`'s planned services table also
  lists `sensor-simulator`, which isn't a service in the current compose file — flag this as a gap
  to verify with the team, don't assume it's an oversight or that it's intentionally excluded.
- Environment profiles: `docs/infrastructure/environments.md` (`local`, `test`,
  `azure-planned`).
- Azure target platform: DEC-002 (Azure Container Apps for compute, PostgreSQL Flexible Server for
  persistence), DEC-009 (RabbitMQ as a planned container in Azure Container Apps, not Service Bus),
  DEC-010 (secrets: `.env`/`.env.example` locally, Key Vault + Managed Identity planned for Azure)
  — full detail in `docs/planning/decisions-log.md` and
  `docs/architecture/deployment-view.md`. Per `docs/planning/roadmap.md`, the Azure-planned design
  work (exporter config, RabbitMQ-in-ACA design, Key Vault strategy) is scoped to **Sprint 7** —
  still design/planning, not provisioning, even then.
- Current build reality: root `pom.xml` and `apps/*/pom.xml` pin **Spring Boot 4.1.1** — DEC-011's
  original entry fixed an earlier interim version on a verified-wrong premise ("Spring Boot 4.x
  not yet published"); that was corrected the same day (2026-08-29) once verified against Maven
  Central. Any CI/build step this skill touches should reflect `4.1.1`, the only version DEC-011
  confirms as correct.

## Reference cases (already decided — do not reopen)

- **RabbitMQ deployed as a planned container inside Azure Container Apps** (DEC-009), not migrated
  to Azure Service Bus or any other managed broker — the reference case for "don't substitute a
  managed cloud equivalent just because one exists."
- **Secrets: `.env` locally, Key Vault + Managed Identity planned for Azure** (DEC-010) — the
  reference case for where credential handling logic goes (never embedded in code or committed
  files), and that Key Vault provisioning itself is planned, not something to create now.
- **No Azure resource has ever been created, and `terraform apply` has never been executed** in
  this project — every "planned"/"confirmed as target" note in `docs/architecture/tech-stack.md`
  and `docs/architecture/deployment-view.md` is exactly that: planned, not provisioned.

## Verification checklist

- [ ] No command in scope executes `terraform apply`/`terraform destroy`, `az` (any subcommand), or
      brings up a production-targeting `docker compose` file — these are `deny`-listed in
      `.claude/settings.json`.
- [ ] Any Docker Compose change matches the services/networks/volumes/env vars already fixed in
      `docs/infrastructure/docker-strategy.md`; a new service or topology change updates that
      document first, or is flagged as a gap instead of silently diverging.
- [ ] Any CI workflow change doesn't leak `.env` contents, credentials, or Terraform state into
      logs or artifacts.
- [ ] Any Dockerfile change follows the multi-stage build strategy already fixed in
      `docs/infrastructure/docker-strategy.md` (build stage with JDK, runtime stage with JRE only).
- [ ] Terraform files stay modular and prepared, but nothing is applied — no state file is created
      or committed.

**This skill does not reopen closed architecture decisions.** For an audit of current consistency
between docs/architecture, docs/domain and the ADRs — without proposing changes — use the
`architecture-auditor` agent instead.
