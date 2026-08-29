- Test domain rules (RN-001 to RN-014) in isolation, without infrastructure (no RabbitMQ, no
  PostgreSQL, no Azure SDKs).
- RabbitMQ delivers **at-least-once, not exactly-once** (ADR-004, ADR-009) — a redelivered or
  duplicated message is expected behavior, not a bug to work around in a test. Event consumers
  must be tested for idempotency and for retry/redelivery scenarios against that guarantee.
- Engineering convention (the mechanism isn't mandated by any ADR, only the at-least-once
  guarantee is): a consumer acknowledges a message only *after* its effect is durably committed,
  never before. Consumer tests should include a case that fails after the ack would otherwise
  fire.
- Engineering convention: where a consumer or outbound call distinguishes retryable failure
  (timeout, connection reset, 5xx) from permanent failure (validation error, 4xx, poison message),
  tests should cover both paths.
- gRPC contracts (`contracts/grpc/`) require contract tests, not only unit tests of the
  implementation (RNF-006, ADR-003) — `contracts/grpc/` is currently empty (`.gitkeep` only, no
  `.proto` files), so this rule takes effect once contracts exist, not before.
- Priority calculation (impacto × urgencia → P1–P4, RN-012) needs a dedicated test matrix covering
  all **4×4 = 16 combinations** (impacto: bajo/medio/alto/crítico, RN-010; urgencia:
  baja/media/alta/inmediata, RN-011) — no sampling, no shortcuts.
- SLA/KPI numeric targets follow the pending/placeholder policy in
  `.claude/rules/documentation.md` — never assert an invented number in a test.
