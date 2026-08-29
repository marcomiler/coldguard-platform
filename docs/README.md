# Documentación de ColdGuard

Índice maestro de `/docs`. Esta carpeta es la fuente de verdad técnica y operativa del producto
(arquitectura, dominio, calidad, seguridad, infraestructura y operación). El informe académico
final vive fuera de este repositorio; aquí solo se conserva lo necesario para desarrollar,
verificar y trazar el sistema, más los artefactos académicos puntuales en `academic/`.

> El historial de reorganización y enriquecimiento documental de esta estructura (reportes
> `migration-report.md` y `enrichment-report.md`, y la copia histórica de `business-analysis.md`)
> ya no está disponible en el repositorio: se perdió por no haber sido confirmado en Git antes de
> un reinicio del entorno de trabajo, y no se reconstruye. Ver "Historial de saneamiento" en
> `docs/architecture/architecture-consistency-report.md` para el hallazgo consolidado.

## product/ — Producto

- [`vision.md`](product/vision.md) — nombre, solución y diferenciador de ColdGuard.
- [`problem-statement.md`](product/problem-statement.md) — el problema que resuelve.
- [`scope-mvp.md`](product/scope-mvp.md) — qué está dentro y fuera del MVP, y qué queda pendiente de decisión.
- [`stakeholders.md`](product/stakeholders.md) — actores y trazabilidad actor → caso de uso.
- [`business-rules.md`](product/business-rules.md) — reglas de negocio RN-001 a RN-019 (incluye la matriz impacto/urgencia RN-012).
- [`glossary.md`](product/glossary.md) — glosario de términos (`TODO`, vacío en el origen); diferido, incremental sin sprint fijo.
- [`product-backlog.md`](product/product-backlog.md) — épicas e historias de usuario (HU-001 a HU-020).
- [`core-documents-change-log.md`](product/core-documents-change-log.md) — registro histórico de decisiones aplicadas a los documentos núcleo de producto.

## planning/ — Planificación

- [`roadmap.md`](planning/roadmap.md) — Sprint 0 a Sprint 7, con objetivo, entregables, evidencia y relación con cada versión (APF1 a proyecto final).
- [`sprint-0.md`](planning/sprint-0.md) — incepción técnica, con evidencia de Git (commit `4628e40`).
- [`sprint-1.md`](planning/sprint-1.md), [`sprint-2.md`](planning/sprint-2.md) — detalle de sprint (objetivo, historias, DoD, riesgos, retro).
- [`release-plan.md`](planning/release-plan.md) — plan incremental v0.1 a v1.0, todas en estado "Planificada".
- [`decisions-log.md`](planning/decisions-log.md) — registro de decisiones (`DEC-001` en adelante) y referencias a otras decisiones confirmadas.
- [`planning-cleanup-report.md`](planning/planning-cleanup-report.md) — reporte del saneamiento de la documentación de planificación.
- [`definition-of-ready.md`](planning/definition-of-ready.md) — criterios INVEST y campos obligatorios que debe cumplir toda historia antes de Sprint Planning.
- [`definition-of-done.md`](planning/definition-of-done.md) — criterios generales para marcar cualquier historia como completada.
- [`refinement-process.md`](planning/refinement-process.md) — regla de refinamiento: solo se detalla el sprint N+1, nunca más de uno por adelantado.

## architecture/ — Arquitectura

- [`system-context.md`](architecture/system-context.md) — C4 nivel 1 (contexto).
- [`container-diagram.md`](architecture/container-diagram.md) — C4 nivel 2 (contenedores); incluye una desincronización conocida con ADR-005, marcada explícitamente.
- [`component-diagram.md`](architecture/component-diagram.md) — `TODO`.
- [`deployment-view.md`](architecture/deployment-view.md) — `TODO`.
- [`data-flow.md`](architecture/data-flow.md), [`event-flow.md`](architecture/event-flow.md) — `TODO`.
- [`tech-stack.md`](architecture/tech-stack.md) — puntero a `CLAUDE.md` (no duplica el stack).
- [`adr/`](architecture/adr/) — ADR-001 a ADR-009, formato estándar de 8 secciones.
- [`diagrams/`](architecture/diagrams/) — `context.mmd` y `containers.mmd` completos (derivados de `system-context.md`/`container-diagram.md`); `components-incident-service.mmd`, `components-asset-service.mmd`, `deployment-local.mmd` diferidos a Sprint 2-3.
- [`architecture-consistency-report.md`](architecture/architecture-consistency-report.md) — bitácora histórica del lote de arquitectura (Rondas 1 a 5). **Estado: CERRADO** — documento congelado, no se edita en cada ronda menor. Decisiones puntuales posteriores van en [`decisions-log.md`](planning/decisions-log.md); este reporte solo se reabre al cerrar un lote completo nuevo.

## domain/ — Dominio

- [`functional-requirements.md`](domain/functional-requirements.md) — RF-001 a RF-015.
- [`use-cases.md`](domain/use-cases.md) — CU-001 a CU-016.
- [`commands-events.md`](domain/commands-events.md) — catálogo de eventos de dominio.
- [`traceability-matrix.md`](domain/traceability-matrix.md) — matriz RF → CU → RN → evento → prueba futura.
- [`domain-model.md`](domain/domain-model.md) — proceso objetivo (7 pasos); modelo de datos pendiente (`TODO`).
- [`bounded-contexts.md`](domain/bounded-contexts.md), [`aggregates.md`](domain/aggregates.md) — completos, derivados de `domain-model.md` y `container-diagram.md`.
- [`state-machines.md`](domain/state-machines.md) — `TODO` (fuera del alcance bloqueante de Sprint 1).

## quality/ — Calidad

- [`non-functional-requirements.md`](quality/non-functional-requirements.md) — RNF-001 a RNF-007.
- [`sla-kpi.md`](quality/sla-kpi.md) — SLA por prioridad (P1–P4) y KPIs candidatos.
- [`risk-register.md`](quality/risk-register.md) — R-001 a R-009.
- [`observability-requirements.md`](quality/observability-requirements.md) — diferido a Sprint 5.
- [`acceptance-criteria.md`](quality/acceptance-criteria.md) — `TODO`.
- [`test-strategy.md`](quality/test-strategy.md) — niveles, herramientas y enfoque de cobertura, además del resumen de `.claude/rules/testing.md`.

## security/ — Seguridad

- [`authn-authz.md`](security/authn-authz.md) — diferido a Sprint 3-4.
- [`security-controls.md`](security/security-controls.md) — diferido a Sprint 3-4.
- [`threat-model.md`](security/threat-model.md) — diferido a Sprint 3-4.

## infrastructure/ — Infraestructura

- [`environments.md`](infrastructure/environments.md), [`docker-strategy.md`](infrastructure/docker-strategy.md) — completos: perfiles local/test/azure-planned y estrategia de Compose (servicios, redes, volúmenes, variables), sin implementar.
- [`terraform-plan.md`](infrastructure/terraform-plan.md), [`localstack-usage.md`](infrastructure/localstack-usage.md), [`deployment-checklist.md`](infrastructure/deployment-checklist.md) — diferidos a Sprint 7; ningún recurso Azure se crea sin aprobación explícita.

## operations/ — Operación

- [`runbooks.md`](operations/runbooks.md) — arranque local vía Docker Compose.
- [`backup-recovery-plan.md`](operations/backup-recovery-plan.md) — diferido a Sprint 5-7 (consolida backup + disaster recovery).
- [`incident-model.md`](operations/incident-model.md) — `TODO` (perspectiva operativa, distinta del dominio en `domain/`).
- [`capacity-plan.md`](operations/capacity-plan.md) — diferido a Sprint 5-7, sin cifras de dimensionamiento confirmadas.
- [`event-severity-policy.md`](operations/event-severity-policy.md), [`service-requests.md`](operations/service-requests.md) — diferidos a Sprint 5-7.

## research/ — Investigación

- [`references.md`](research/references.md), [`apa-sources.md`](research/apa-sources.md) — diferidos al cierre del informe académico, sin contenido fuente.

## academic/ — Entregables académicos (APF1)

- [`01-propuesta-proyecto.md`](academic/01-propuesta-proyecto.md) — propuesta original del proyecto.
- [`report-artifacts-index.md`](academic/report-artifacts-index.md) — índice de artefactos esperados del informe APF1.
- [`apf1-mapping.md`](academic/apf1-mapping.md) — mapeo de cada entregable a su evidencia real en el repo, con vacíos detectados.

## Cómo navegar si buscas...

| Si buscas... | Ve a |
|---|---|
| Por qué existe ColdGuard | `product/vision.md`, `product/problem-statement.md` |
| Qué entra y qué no en el MVP | `product/scope-mvp.md` |
| Un requisito (RF) o caso de uso (CU) | `domain/functional-requirements.md`, `domain/use-cases.md` |
| Una regla de negocio (RN) | `product/business-rules.md` |
| Una decisión arquitectónica (ADR) | `architecture/adr/` |
| Cómo se calcula la prioridad de un incidente | `product/business-rules.md` (RN-012) |
| Qué eventos existen | `domain/commands-events.md` |
| Cómo levantar el entorno local | `operations/runbooks.md` |
| El estado de cierre de APF1 | `academic/apf1-mapping.md` |

## Reglas que rigen esta documentación

- `.claude/rules/documentation.md` — IDs, estructura de ADR, alcance MVP.
- `.claude/rules/architecture.md`, `.claude/rules/security.md`, `.claude/rules/testing.md`, `.claude/rules/infra.md`.
- `CLAUDE.md` (raíz del repositorio) — alcance, stack, flujo de trabajo y puertas de calidad.
