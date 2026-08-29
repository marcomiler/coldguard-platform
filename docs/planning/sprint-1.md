# Sprint 1

Sprint de definición del producto, análisis, arquitectura y planificación. Resumido en
`docs/planning/roadmap.md`; precedido por `docs/planning/sprint-0.md` (incepción técnica). El
alcance de este sprint es exclusivamente documental, sin implementación de código.

## Objetivo del sprint

Fijar los fundamentos del proyecto — propuesta, análisis de negocio, requisitos, casos de uso,
reglas de negocio, arquitectura C4, ADRs de decisiones abiertas, riesgos, SLA/KPI, trazabilidad
y backlog — como base verificable para planificar la implementación (Sprint 3 en adelante) y
los prototipos de frontend (Sprint 2).

## Historias comprometidas

Ver detalle completo (criterios de aceptación, prioridad, dependencias) en `docs/product/product-backlog.md`.

| Historia | Resumen | Estado |
|---|---|---|
| HU-001 | Propuesta de proyecto | Hecho |
| HU-002 | Análisis de negocio | Hecho |
| HU-003 | Requisitos funcionales y no funcionales | Hecho, con vacío detectado |
| HU-004 | Casos de uso | Hecho, con nota pendiente |
| HU-005 | Reglas de negocio | Hecho |
| HU-006 | Arquitectura C4 y ADRs | Hecho, con nota pendiente |
| HU-007 | Registro de riesgos | Hecho, con nota pendiente |
| HU-008 | SLA y KPI inicial | Hecho |
| HU-009 | Matriz de trazabilidad | Hecho (este cierre) |
| HU-010 | Backlog de producto | Hecho (este cierre) |
| HU-011 | Documentos de Sprint 1 y Sprint 2 | Hecho (este cierre) |
| HU-012 | Checklist de entregables APF1 | Hecho (este cierre) |

## Definición de terminado (DoD)

- El documento existe en la ruta declarada en `docs/academic/report-artifacts-index.md` o en `product-backlog.md`.
- No reutiliza, renumera ni elimina un ID existente (RF-, RN-, CU-, R-, ADR-, evento) — regla de `.claude/rules/documentation.md`.
- Toda referencia cruzada (RF↔CU, CU↔RN, CU↔evento) apunta a un ID que existe realmente en el documento fuente.
- Ningún valor numérico de SLA/KPI se presenta como confirmado si no lo está (deben quedar marcados como placeholder académico).
- No se agrega alcance fuera del MVP declarado en `01-propuesta-proyecto.md` y `docs/product/scope-mvp.md`.
- No se ejecuta código, no se crean recursos cloud, no se corre `terraform apply`.

## Riesgos

Riesgos relevantes para este sprint (ver `docs/quality/risk-register.md` para el registro completo):

| ID | Riesgo | Relevancia en Sprint 1 |
|---|---|---|
| R-001 | Alcance excesivo | Mitigado por mantener el backlog y las historias acotadas al MVP declarado |
| R-004 | Falta de evidencia operativa | Mitigado parcialmente por este cierre documental (trazabilidad, backlog, checklist) |
| R-005 a R-009 | Decisiones arquitectónicas no tomadas | Todas ya cuentan con ADR (003 a 009); pendiente confirmar si deben marcarse como mitigadas en `risk-register.md` |

## Entregables

- `docs/academic/01-propuesta-proyecto.md`
- `docs/product/stakeholders.md`, `docs/domain/domain-model.md`, `docs/product/business-rules.md`
- `docs/domain/functional-requirements.md`, `non-functional-requirements.md`, `use-cases.md`
- `docs/architecture/system-context.md`, `docs/architecture/container-diagram.md`, `docs/architecture/adr/ADR-001` a `ADR-009`
- `docs/quality/risk-register.md`, `sla-kpi.md`
- `docs/domain/traceability-matrix.md`
- `docs/product/product-backlog.md`
- `docs/planning/sprint-1.md`, `sprint-2.md`
- `docs/academic/apf1-mapping.md`

## Retrospectiva

Pendiente de completar por el equipo al cerrar el sprint.

- **Qué funcionó bien:** _pendiente_.
- **Qué se puede mejorar:** _pendiente_.
- **Acciones para el próximo sprint:** _pendiente_ (candidato: resolver HU-017 antes de iniciar Sprint 3).
