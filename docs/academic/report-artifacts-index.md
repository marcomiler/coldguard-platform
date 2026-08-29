# Índice de artefactos del informe APF1

| Artefacto | Documento fuente | Ruta | Estado | Uso esperado en APF1 |
|---|---|---|---|---|
| Propuesta de proyecto | `01-propuesta-proyecto.md` | `docs/academic/01-propuesta-proyecto.md` | Completo | Ficha ejecutiva del proyecto |
| Análisis de negocio (actores, proceso) | `stakeholders.md`, `domain-model.md` | `docs/product/stakeholders.md`, `docs/domain/domain-model.md` | Completo | Fundamento de dominio |
| Reglas de negocio | `business-rules.md` | `docs/product/business-rules.md` | Completo (RN-001 a RN-020) | Lógica de dominio trazable |
| Requerimientos funcionales y no funcionales | `functional-requirements.md`, `non-functional-requirements.md` | `docs/domain/functional-requirements.md`, `docs/quality/non-functional-requirements.md` | Completo (RF-001 a RF-018; RF-018 resuelve por decisión el vacío de CU-009) | Alcance funcional verificable |
| Casos de uso | `use-cases.md` | `docs/domain/use-cases.md` | Completo (CU-001 a CU-022; CU-010 no definido en el MVP por decisión explícita) | Comportamiento esperado del sistema |
| Catálogo de eventos | `commands-events.md` | `docs/domain/commands-events.md` | Completo, `AssetRegistered` ya asociado a CU-001 (Resuelto por decisión), sigue sin consumidor confirmado | Contrato de mensajería entre servicios |
| Arquitectura (contexto, contenedores, componentes, despliegue, flujo de datos y eventos, stack) | `system-context.md`, `container-diagram.md`, `component-diagram.md`, `deployment-view.md`, `data-flow.md`, `event-flow.md`, `tech-stack.md` | `docs/architecture/` | Completo | Vista técnica del sistema |
| Decisiones arquitectónicas (ADR) | ADR-001 a ADR-009 | `docs/architecture/adr/` | Completo | Justificación de decisiones técnicas |
| Mapa y plan de riesgos | `risk-register.md` | `docs/quality/risk-register.md` | Completo (R-001 a R-014, con estado revaluado) | Gestión de riesgos del proyecto |
| SLA y KPI | `sla-kpi.md` | `docs/quality/sla-kpi.md` | Completo, con valores agregados pendientes de validación de negocio | Medición de desempeño esperado |
| Matriz de trazabilidad | `traceability-matrix.md` | `docs/domain/traceability-matrix.md` | Completo, con huérfanos reportados sin resolver | Verificación de cobertura RF↔CU↔RN↔evento |
| Backlog de producto | `product-backlog.md` | `docs/product/product-backlog.md` | Completo (HU-001 a HU-020) | Plan de trabajo por historia |
| Plan de sprints | `roadmap.md`, `sprint-0.md`, `sprint-1.md`, `sprint-2.md` | `docs/planning/` | Completo (Sprint 0 a Sprint 7 con objetivo/alcance/evidencia esperada; Sprint 3 a 7 sin ejecutar) | Planificación de la implementación |
| Plan de release | `release-plan.md` | `docs/planning/release-plan.md` | Completo (v0.1 a v1.0, todas "Planificada") | Hoja de ruta de versiones |
| Registro de decisiones | `decisions-log.md` | `docs/planning/decisions-log.md` | Completo (DEC-001) | Trazabilidad de decisiones de alcance |
| Prototipos y frontend inicial | Repositorio `coldguard-frontend` (ADR-002) | Fuera de este repositorio | **Pendiente** — no verificable desde `coldguard-platform` | No se afirma como construido sin enlace, commit o evidencia real en `coldguard-frontend` |

Verificación de estado y observaciones detalladas en `docs/academic/apf1-mapping.md`.
