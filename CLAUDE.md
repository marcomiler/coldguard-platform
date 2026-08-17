# ColdGuard Platform

## Scope

This repository contains ColdGuard backend services, contracts,
simulator, infrastructure and academic/technical documentation.

The frontend is maintained in the separate repository:
coldguard-frontend.

## Product

ColdGuard converts cold-chain telemetry into prioritized incidents,
notifications, operational actions and auditable evidence.

## Technology

- Java 25
- Spring Boot
- Maven
- gRPC and Protocol Buffers
- RabbitMQ for the local MVP
- PostgreSQL
- Docker Compose
- Terraform
- GitHub Actions
- OpenTelemetry
- Micrometer
- Prometheus
- Grafana
- Loki
- Mailpit

## Architecture

- REST at the external edge.
- gRPC for internal synchronous communication.
- Asynchronous events for cross-service workflows.
- Ports and adapters for brokers, email and cloud providers.
- Domain logic must not depend on RabbitMQ or Azure SDKs.
- Consumers must be idempotent.
- Correlation and trace IDs must be propagated.
- Avoid distributed transactions.
- Prefer eventual consistency where appropriate.

## Cloud policy

- Do not create Azure resources without explicit human approval.
- Do not execute terraform apply without explicit approval.
- Do not commit secrets, Terraform state, credentials, logs or generated PIDs.
- The MVP must run locally with Docker Compose.

## Workflow

Before changing files:

1. Inspect relevant documentation.
2. Identify requirements and acceptance criteria.
3. Propose the files to change.
4. Wait for approval when the change affects architecture or scope.
5. Implement the smallest vertical slice.
6. Add or update tests.
7. Update documentation.
8. Report commands and results.

## Quality gates

- Java 25 compilation succeeds.
- Unit and integration tests pass.
- Formatting and static analysis pass.
- No secrets are present.
- Docker health checks pass.
- Logs are structured.
- Metrics and traces are available.
- Documentation and ADRs are updated.
