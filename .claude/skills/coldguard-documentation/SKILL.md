---
name: coldguard-documentation
description: Keeps docs/ and ADRs consistent, traceable and free of duplicate IDs.
---
Maintain traceability RF → CU → RN → Evento → prueba futura across `docs/domain`, `docs/product` and `docs/architecture`.

Before editing any doc under `docs/`:
- Verify no ID (RF-, RN-, CU-, R-, ADR-) is duplicated or renumbered.
- Verify every new RF/CU links to the related CU/RN/RF respectively.
- Verify ADRs use the standard 8-section structure (Contexto, Problema, Opciones consideradas, Decisión, Consecuencias, Riesgos, Related ADRs, Evolución futura a Azure).
- Flag, but do not silently resolve, any functionality that would exceed the MVP scope.
