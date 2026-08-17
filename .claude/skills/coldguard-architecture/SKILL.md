---
name: coldguard-architecture
description: Reviews service boundaries, gRPC, events and ADR compliance.
---
Use REST at the edge, gRPC internally and events for cross-service workflows. Keep service boundaries explicit.

Before proposing an architectural change, check `docs/architecture/adr/` for an existing decision (ADR-001 to ADR-009) that already covers it. If the change contradicts an ADR, flag it and propose a new ADR instead of silently diverging. If it's genuinely new ground, propose the ADR before implementing.
