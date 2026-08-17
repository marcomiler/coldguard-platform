---
name: coldguard-testing
description: Defines unit, integration, contract and resilience tests.
---
Test domain rules without infrastructure and event consumers with idempotency and retries.

Contract tests cover the gRPC contracts in `contracts/` (ADR-003). Resilience tests cover consumer idempotency against at-least-once delivery from RabbitMQ + Transactional Outbox (ADR-004, ADR-009).
