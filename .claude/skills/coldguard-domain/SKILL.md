---
name: coldguard-domain
description: Applies domain terminology, business rules, requirements and use cases.
---
Use canonical terms from `docs/product` and `docs/domain`. Do not invent capabilities outside the MVP.

Key rules to respect:
- Priority (P1–P4) always comes from the impacto/urgencia matrix (RN-012), never a direct severity→priority mapping (RN-013).
- Asset criticality (RN-009), impacto (RN-010) and urgencia (RN-011) are distinct concepts; don't conflate them.
- Incident deduplication/update follows the equivalence key in RN-004/RN-005 (same activo + sensor + tipo de anomalía).
- SLA clock: `IncidentAcknowledged` stops the reconocimiento SLA; `IncidentClosed` stops MTTR (RN-006).
