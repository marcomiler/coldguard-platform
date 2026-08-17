# ColdGuard Platform — Claude Code Instructions

## Scope
This repository contains backend services, contracts, simulator, infrastructure and project documentation.
Frontend work belongs to the `coldguard-frontend` repository.

## Stack
Java 25, Spring Boot, Maven, gRPC/Protobuf, RabbitMQ locally, PostgreSQL, Docker Compose, Terraform, GitHub Actions, OpenTelemetry, Micrometer, Prometheus, Grafana, Loki and Mailpit.

## Rules
- REST at the edge, gRPC internally.
- Events for cross-service workflows.
- Domain logic must not depend on RabbitMQ or Azure SDKs.
- Use ports and adapters.
- No Azure apply without explicit approval.
- Propose file changes before editing.
- Keep ADRs updated.
