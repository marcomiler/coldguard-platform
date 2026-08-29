# Mapeo de entregables APF1 a evidencia

Matriz de estado vigente: verifica cada entregable listado en
`docs/academic/report-artifacts-index.md` contra su evidencia real en el repositorio. Se
actualiza conforme se confirman decisiones; los vacíos ya resueltos se retiran de esta matriz y
solo quedan los pendientes reales.

| Entregable | Archivo / evidencia | Estado | Observaciones |
|---|---|---|---|
| Propuesta de proyecto | `docs/academic/01-propuesta-proyecto.md` | Completo | — |
| Análisis de negocio | `docs/product/stakeholders.md`, `docs/domain/domain-model.md`, `docs/product/business-rules.md` | Completo | Todos los roles (Supervisor de operaciones, Operador, Técnico de mantenimiento, Auditor, Administrador de plataforma) tienen alcance y casos de uso confirmados |
| Requerimientos funcionales y no funcionales | `docs/domain/functional-requirements.md`, `docs/quality/non-functional-requirements.md` | Completo | RF-018 (consultar bitácora de auditoría, CU-009, RN-008) agregado por decisión (DEC-005, `docs/planning/decisions-log.md`) |
| Casos de uso | `docs/domain/use-cases.md` | Completo | CU-010 explícitamente no se define en el MVP (decisión ya registrada); actor de CU-015 confirmado (Administrador de plataforma) |
| Catálogo de eventos | `docs/domain/commands-events.md` | Completo, con huérfano de consumidor conocido | `AssetRegistered` ya está asociado explícitamente a CU-001; sigue sin consumidor confirmado. `IncidentResolved` fue eliminado del catálogo (Decisión E: `IncidentClosed` es el único evento de cierre técnico) |
| Arquitectura | `docs/architecture/system-context.md`, `container-diagram.md`, `component-diagram.md`, `deployment-view.md`, `data-flow.md`, `event-flow.md`, `tech-stack.md`, `docs/architecture/adr/ADR-001` a `ADR-009` | Completo | El diagrama de contenedores ya refleja `IS → MQ → NS` (antes mostraba comunicación directa) |
| Mapa y plan de riesgos | `docs/quality/risk-register.md` | Completo | R-005 a R-009 reevaluados: "Mitigado por decisión; pendiente validación en implementación" |
| SLA y KPI | `docs/quality/sla-kpi.md` | Completo | Valores agregados (MTTA/MTTR globales, throughput, disponibilidad) marcados como pendientes de validación de negocio; solo los SLA por prioridad individual están definidos |
| Plan de sprints | `docs/planning/roadmap.md`, `docs/planning/sprint-0.md`, `docs/planning/sprint-1.md`, `docs/planning/sprint-2.md` | Completo | Sprint 0 a Sprint 7 con objetivo, alcance, evidencia esperada y relación con APF1/APF2/APF3/proyecto final; Sprint 3 en adelante sin ejecutar todavía |
| Prototipos y frontend inicial en repositorio separado | Repositorio `coldguard-frontend` (ADR-002) | **Pendiente** | No verificable desde `coldguard-platform`; las historias HU-013 a HU-018 en `docs/product/product-backlog.md` describen el alcance esperado, pero no se afirma que el prototipo esté construido sin un enlace, commit o evidencia real en `coldguard-frontend` |

## Vacíos reales pendientes (no resueltos en este cierre)

1. **Prototipos en `coldguard-frontend` sin evidencia verificable desde este repositorio.**
   Permanece "Pendiente" hasta que exista un enlace, commit o referencia real.
2. **RN-010 y RN-011 sin columna RN propia en `docs/domain/traceability-matrix.md`.** Están
   documentadas y usadas (alimentan RN-012 y RN-013), pero no aparecen listadas en ninguna fila RF
   de la matriz — observación de formato, señalada en la propia matriz.
3. **RF-009 sin evento ni criterio de prueba propio** en `docs/domain/traceability-matrix.md`; su
   servicio dueño (Incident Service, módulo de consultas operativas) sí quedó resuelto por
   decisión (DEC-006).
4. **Evento `AssetRegistered` sin consumidor confirmado.** Ya está asociado explícitamente a
   CU-001; falta definir si algún componente lo consume.

## Resuelto por decisión (esta ronda)

- **CU-009 sin RF**: resuelto. `docs/domain/functional-requirements.md` agrega **RF-018**
  (consultar bitácora de auditoría, solo lectura restringida), trazado a CU-009 y RN-008
  (DEC-005, `docs/planning/decisions-log.md`).
- **Evento `AssetRegistered` no referenciado por ningún CU**: resuelto parcialmente.
  `docs/domain/use-cases.md` (CU-001) ya lo referencia explícitamente; el consumidor sigue sin
  confirmar (ver vacío #4 arriba).
- **Servicio dueño de RF-013/CU-014, RF-009/CU-008 y RF-018/CU-009**: resuelto (DEC-004, DEC-005,
  DEC-006). Los tres son módulos internos de Incident Service, no microservicios separados
  (DEC-008): Identity & Access, consultas operativas, y Audit Log, respectivamente.
