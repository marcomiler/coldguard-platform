# Plan de release

Plan incremental por versión, alineado a las fases académicas del proyecto (APF1, APF2, APF3,
proyecto final). No se afirman fechas ni resultados de una versión que aún no se ha liberado;
cada versión se marca explícitamente con su estado.

## v0.1 — APF1

- **Propósito**: fijar los fundamentos del producto y validar el flujo funcional mediante
  prototipos navegables, sin implementación de backend real.
- **Alcance resumido**: propuesta de proyecto, análisis de negocio, requisitos funcionales y no
  funcionales, reglas de negocio, registro de riesgos, SLA/KPI iniciales, arquitectura (C4, ADRs)
  y prototipos navegables de frontend (sin lógica de negocio real).
- **Evidencia de aceptación**: `docs/product/product-backlog.md` (HU-001 a HU-018),
  `docs/planning/sprint-1.md`, `docs/planning/sprint-2.md`, `docs/academic/apf1-mapping.md`.
- **Estado**: Planificada.

## v0.2 — APF2

- **Propósito**: primer backend funcional con persistencia y seguridad reales.
- **Alcance resumido**: backend inicial (vertical slice), persistencia (PostgreSQL), seguridad
  real de backend (Spring Security, RBAC en endpoints, hash de contraseñas — RF-015, CU-016),
  validación del flujo end-to-end y despliegue de una versión inicial en el entorno local.
- **Evidencia de aceptación**: por definir junto con el backlog de Sprint 3 y Sprint 4
  (`docs/planning/roadmap.md`).
- **Estado**: Planificada.

## v0.3 — APF3

- **Propósito**: robustecer la operación del backend antes de la versión final.
- **Alcance resumido**: gestión de solicitudes de servicio, gestión de cambios, integración
  continua (CI), pruebas de capacidad/rendimiento, e integración planificada del adaptador de
  notificaciones con Azure Communication Services Email como proveedor productivo (DEC-007,
  `docs/planning/decisions-log.md`), sin afirmar recurso creado ni correos reales enviados.
- **Evidencia de aceptación**: por definir junto con el backlog de Sprint 5 y Sprint 6
  (`docs/planning/roadmap.md`).
- **Estado**: Planificada.

## v1.0 — Proyecto final

- **Propósito**: cerrar el alcance completo del MVP con operación sostenible.
- **Alcance resumido**: monitoreo end-to-end (instrumentación OpenTelemetry, stack local
  Prometheus/Grafana/Loki, exportación planificada hacia Application Insights/Azure Monitor y
  Log Analytics — `docs/operations/observability-strategy.md`), eventos de dominio, gestión
  completa de incidentes, continuidad y backups, y evidencia operativa (runbooks, capturas,
  escenarios).
- **Evidencia de aceptación**: por definir junto con el backlog de Sprint 7
  (`docs/planning/roadmap.md`).
- **Estado**: Planificada. Ninguna capacidad de observabilidad cloud está implementada ni
  desplegada.

Ninguna versión de este plan está liberada. El detalle sprint a sprint de cada versión está en
`docs/planning/roadmap.md`.
