---
name: coldguard-testing
description: Defines unit, integration, contract and resilience tests.
---

Test domain rules without infrastructure and event consumers with idempotency and retries.

## Architecture context (by ID — don't repeat full content here)

- Mandatory testing rules: `.claude/rules/testing.md`. Level breakdown, tools, and coverage
  approach (no invented numeric target): `docs/quality/test-strategy.md`.
- Contract tests cover the gRPC contracts in `contracts/grpc/` (ADR-003) — currently empty
  (`.gitkeep` only, no `.proto` files yet), so there's nothing to contract-test until the gRPC
  contracts and library choice exist.
- Resilience tests cover consumer idempotency against at-least-once delivery from RabbitMQ +
  Transactional Outbox (ADR-004, ADR-009).
- Test framework baseline: JUnit 5 (Jupiter) — the only supported option per
  `docs/quality/test-strategy.md`, and confirmed compatible with **Spring Boot 4.1.1** (DEC-011):
  DEC-011's own breaking-changes review found all five service modules already used
  `org.junit.jupiter.api.Test`, so the JUnit-4-removed-in-Boot-4 change required no action. There
  is no open question about which Spring Boot line this project targets — 4.1.1 is the confirmed,
  current version.
- **Tooling not yet confirmed** (`docs/quality/test-strategy.md`): Testcontainers for integration
  tests, and the gRPC contract-testing tool itself, are both listed as **candidates, not adopted**.
  Don't write review feedback or code that assumes either is already the chosen tool — check
  `test-strategy.md`'s "Pendiente" section first; if it still lists them as pending, so should any
  review comment.

## Reference cases (already decided — do not reopen)

- **No SLA/KPI numeric target gets asserted unless confirmed by the business**
  (`docs/quality/sla-kpi.md`, `.claude/rules/testing.md`) — a test must not encode an invented
  number as if it were a confirmed requirement. Same principle applies to test *coverage* targets:
  `test-strategy.md` fixes no numeric coverage percentage either, for the same reason.

## Verification checklist

The mandatory rules (domain tests isolated from infrastructure, idempotency/redelivery tests for
consumers, full 16-combination priority matrix, gRPC contract tests) already live in
`.claude/rules/testing.md` — not restated here. This checklist covers what that rule list doesn't:

- [ ] Any gRPC contract test is written against the actual `.proto` contracts in `contracts/grpc/`
      once they exist — not against an assumed/invented contract shape (still empty, `.gitkeep`
      only, as of this writing).
- [ ] No test asserts a numeric SLA/KPI value that isn't confirmed in `docs/quality/sla-kpi.md`.
- [ ] A test or its setup doesn't silently assume Testcontainers, or a specific gRPC
      contract-testing library, is the adopted tool — both are still candidates per
      `docs/quality/test-strategy.md`.

**This skill does not reopen closed architecture decisions.** For an audit of current consistency
between docs/architecture, docs/domain and the ADRs — without proposing changes — use the
`architecture-auditor` agent instead.
