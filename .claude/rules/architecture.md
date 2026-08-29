- Use REST only at the external edge; the Gateway is the only component that exposes business
  REST and the only one that translates REST→gRPC (ADR-008, ADR-003). Internal Actuator/health
  endpoints are a separate category, not an exception to this rule — see `coldguard-architecture`.
- Use gRPC for synchronous communication between internal backend services (ADR-003); calls to
  external providers use declarative HTTP clients instead (`.claude/rules/java-spring.md`), not
  gRPC — ADR-003 doesn't cover external providers.
- Use asynchronous events over RabbitMQ for cross-service workflows (ADR-004, ADR-005): event when
  the producer must not depend on the consumer's availability, gRPC when the caller needs an
  immediate answer — see `coldguard-architecture` for the full criterion.
- Keep `domain/` and `application/` free of RabbitMQ clients and Azure SDKs; that coupling is
  allowed only in `infrastructure/` (DEC-008, DEC-011 package convention). Note de implementación
  (no ADR/DEC): extending this same boundary to `api/`/`config/` is inference from the layer
  split, not a literal DEC-008/DEC-011 line.
- Do not bypass the Gateway from the frontend, in any environment — local Docker Compose included,
  not only a hypothetical production deployment (ADR-002, ADR-008).
- Each service owns its logical schema inside one shared PostgreSQL instance — never a separate
  physical database per service, that option was explicitly discarded (ADR-006).
- Reliable event publishing goes through the Transactional Outbox (ADR-009), not direct broker
  calls after commit; the MVP mechanism is a periodic poller, not CDC/Debezium — see
  `coldguard-architecture` for how firm ADR-009 actually is on this point.
- Incompatible gRPC contract changes require explicit versioning (e.g. a `v2` package), never a
  silent breaking change (ADR-003).
- The Gateway contains no business logic — RN-001 to RN-014 stay exclusively in the domain
  services (ADR-008).
- Any new cross-service architectural decision requires a new ADR before implementation, not a
  silent choice in code.
