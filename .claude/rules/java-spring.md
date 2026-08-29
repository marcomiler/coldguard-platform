# Java / Spring conventions

## Confirmed version

**Spring Boot 4.1.1** is the confirmed, pinned version — root `pom.xml` (`spring-boot.version`)
and all five `apps/*/pom.xml` (`<parent><version>`), per DEC-011
(`docs/planning/decisions-log.md`), consistent with `docs/architecture/tech-stack.md`. Do not
assume, propose, or review code against any other Spring Boot version. If this file, another
rule, or a skill ever states a different version as current, that's stale — check `pom.xml` and
`tech-stack.md` first.

## Project boundaries (ColdGuard-specific, already decided)

- Java 25 and Spring Boot are the confirmed backend runtime/framework (`CLAUDE.md`,
  `docs/architecture/tech-stack.md`); do not introduce another JVM language or web framework
  without a new ADR.
- One Maven module per service, consistent with the monorepo decision (ADR-001); do not collapse
  services into a shared module or split a service into more modules without an ADR.
- Domain logic must not depend on RabbitMQ clients or Azure SDKs (ports and adapters,
  `.claude/rules/architecture.md`); infrastructure concerns stay in adapters at the module's edge.
- REST only at the Gateway edge; gRPC for internal synchronous calls (ADR-003); no service calls
  another service's REST endpoint directly (ADR-008). The gRPC contracts themselves live in
  `contracts/grpc/` — currently empty (`.gitkeep` only); no `.proto` files or gRPC client library
  choice have been made yet, so don't assume a specific gRPC integration (Spring gRPC project vs.
  a community starter vs. raw `grpc-java`) is already selected.
- Event consumers must be idempotent against at-least-once delivery (RabbitMQ + Transactional
  Outbox, ADR-009); do not assume exactly-once delivery in code.
- Never hardcode secrets, credentials, or connection strings; read them from environment variables
  provided by `.env` locally (not versioned) and, when Azure is provisioned, from Key Vault via
  Managed Identity — never re-implement secret storage in application code.
- Never log secrets, tokens, or credentials (`.claude/rules/security.md`).
- Propagate correlation/trace IDs across service boundaries (RNF-002); do not drop them when
  crossing gRPC or AMQP.
- Every relevant state transition that RN-008 requires to be auditable must be recorded by the
  owning module (Audit Log, DEC-005), not scattered across ad hoc logging.
- `spring-boot-starter-security` is deliberately present **only** in `gateway` (DEC-011): the JWT
  edge validation described in ADR-007/ADR-008 belongs there exclusively; internal services trust
  the propagated identity and must not add their own security starter to reimplement edge auth.
- Match package/module boundaries to the bounded contexts already documented in
  `docs/domain/bounded-contexts.md`; do not create cross-context imports between service modules.
  The current scaffold's per-module packages (`config`, `api`, `application`, `domain`,
  `infrastructure`, DEC-011) are empty placeholders — filling them in still must respect this
  layering, not just the folder names.

## Java 25 language features

- **`record` vs. mutable `class`**: use `record` for API DTOs, domain events
  (`docs/domain/commands-events.md`), commands, immutable configuration, and small value objects
  (e.g. a money amount, a threshold range). Use a mutable `class` for JPA entities and any model
  whose lifecycle is managed by a framework (persistence context, proxying) — records are final and
  have no no-arg constructor, which conflicts with typical JPA entity requirements.
- **Sealed classes + pattern matching**: use a `sealed interface`/`sealed class` hierarchy to model
  a closed, finite set of states or outcomes — e.g. the `Incident` state machine
  (`docs/domain/state-machines.md`) or an exhaustive validation result (`Valid` /
  `Invalid(reasons)`). Pair with `switch` pattern matching over the sealed type so the compiler
  flags a missing branch when a new state is added, instead of a silent default case.
- **Virtual threads**: prefer virtual threads for I/O-bound, high-concurrency code paths —
  PostgreSQL calls, RabbitMQ publish/consume, outbound HTTP/gRPC calls — where the thread mostly
  waits rather than computes. Avoid virtual threads for CPU-bound work (no concurrency benefit) and
  be deliberate around `synchronized` blocks/native locks held during blocking I/O.
  **Pending verification**: whether Java 25 still pins a virtual thread when it blocks inside
  `synchronized` (a pinning fix for `synchronized` was proposed in earlier JDK releases). Confirm
  the exact behavior against the JDK 25 release notes before relying on `synchronized` inside a
  virtual-thread-heavy path; until confirmed, prefer `ReentrantLock` over `synchronized` in code
  that will run on virtual threads.

## Spring Boot 4.1.1 conventions

These reflect what Spring Boot 4.1.1 actually makes available. **None of them are adopted in code
yet** — verified against all five `apps/*/pom.xml`: no dependency on a gRPC library, JSpecify, or
an OpenTelemetry starter exists in any module today, and `apps/gateway/src` has only empty
`package-info.java` placeholders. Treat the items below as "available to use," not "already used."

- **Declarative HTTP clients**: register `@HttpExchange` interfaces in groups via
  `@ImportHttpServices` on a configuration class (the "consumer-driven" model — you annotate your
  own config, not the interface, and assign it to a group), instead of hand-building a
  `RestTemplate` call chain or manually declaring an `HttpServiceProxyFactory` bean per client.
  **Pending verification**: the annotation name `@HttpServiceClient` (mentioned in an earlier draft
  of this rule) was not confirmed by verification — the real mechanism found in Spring reference
  material is `@HttpExchange` + `@ImportHttpServices`. Confirm the exact annotation name in the
  Spring reference docs before writing code against `@HttpServiceClient`.
- **gRPC**: the Spring gRPC project's auto-configuration, extended in Spring Boot 4.1 with
  `@GrpcAdvice` + `@GrpcExceptionHandler` for centralized exception-to-status mapping, is one
  candidate for this project's gRPC integration. **Not yet selected** — no gRPC library choice has
  been made (see "Project boundaries" above); don't assume Spring gRPC over a community starter or
  raw `grpc-java` without that decision being made explicit.
- **Null-safety**: JSpecify annotations (`org.jspecify.annotations.Nullable`/`NonNull`) are the
  null-safety standard as of Spring Framework 7, superseding Spring's own
  `org.springframework.lang.Nullable`/`@NonNull`. Prefer JSpecify in new domain/application code
  over Spring's own annotations, now that 4.1.1 is the pinned version — there's no earlier-version
  constraint holding this back anymore.
- **Observability**: the `spring-boot-starter-opentelemetry` starter bundles the relevant
  Micrometer dependencies and OTLP export behind one dependency, instead of wiring
  `micrometer-tracing-bridge-otel` and a Micrometer OTLP registry by hand. Not yet added to any
  module's `pom.xml`; DEC-011 also notes `micrometer-registry-prometheus` isn't wired in either.
- **Exception handling** (version-independent — apply now): define a project-specific domain
  exception hierarchy per bounded context (`docs/domain/bounded-contexts.md`); translate
  infrastructure exceptions (JPA/`DataAccessException`, AMQP/`AmqpException`, gRPC
  `StatusRuntimeException` once gRPC is wired) into domain or application exceptions at the adapter
  boundary — never let an infrastructure exception type cross into application/API-layer code or a
  gRPC response.

### Breaking changes already verified compliant (DEC-011)

DEC-011's scaffolding review checked the three known Spring Boot 4 breaking changes against the
codebase and found no action needed — this isn't a future migration concern, it's already closed:

- **Jackson 3 is required**; no module pins its own Jackson version, so each inherits the
  Boot-managed one — compliant by construction.
- **JUnit 4 support is removed**; all five service modules already use
  `org.junit.jupiter.api.Test` (JUnit 5/Jupiter) — compliant.
- **Undertow is no longer a supported embedded server**; no module declares
  `spring-boot-starter-undertow` — all use Tomcat, the default — compliant.

## What NOT to assume

Do not add a convention to this file about a Spring/Java annotation, starter artifact ID, or
behavior that hasn't been checked against official documentation or against this project's actual
`pom.xml`/`docs/architecture/tech-stack.md`. When in doubt, write "Pending verification in official
Spring/Java documentation before adopting" next to the item instead of asserting it as settled —
two items above already carry that flag.
