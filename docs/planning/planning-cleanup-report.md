# Reporte de saneamiento — documentos de planificación

Saneamiento de la documentación de planificación de ColdGuard: archivo de reportes históricos de
migración, limpieza de metadatos de migración en los documentos vivos, y actualización de
`decisions-log.md`, `release-plan.md`, `roadmap.md`, `sprint-0.md`, `sprint-1.md` y `sprint-2.md`.
No se reorganizaron carpetas fuera de lo indicado, no se eliminó trazabilidad funcional (RF, RN,
CU, ADR) ni evidencia de Git, y no se modificaron reglas de negocio ni actores funcionales salvo
las correcciones explícitas de esta tarea.

## 1. Archivos archivados (estado histórico — ver nota posterior)

> **Nota agregada en una ronda posterior**: los dos archivos archivados de esta tabla, y la copia
> `business-analysis.md` dentro de `_legacy/`, **ya no existen en el repositorio**: se perdieron
> después de este saneamiento, sin copia recuperable. Lo que sigue describe el estado en el
> momento en que se ejecutó esta tarea, no el estado actual. Ver "Historial de saneamiento" en
> `docs/architecture/architecture-consistency-report.md` para el hallazgo consolidado.

| Archivo original | Destino (en su momento) | Nota |
|---|---|---|
| `migration-report.md` | `_legacy/migration-report.md` | Movido sin alterar contenido |
| `enrichment-report.md` | `_legacy/enrichment-report.md` | Movido sin alterar contenido |

**Redirección creada** (histórico): `migration-report.md` (ruta original) contenía en su momento
un archivo mínimo de redirección hacia `_legacy/migration-report.md`, porque existían enlaces
internos que lo requerían (`docs/operations/backup-recovery-plan.md`, `capacity-plan.md`,
`incident-model.md`; `docs/security/security-controls.md`, `authn-authz.md`, `threat-model.md`;
y la copia histórica `business-analysis.md` dentro de `_legacy/`, que no podía editarse por ser
copia congelada). Esos archivos estaban fuera de las carpetas indicadas para esta tarea
(`planning/`, `product/`, `domain/`, `architecture/`, `quality/`), por lo que no se editaron
directamente.

**Sin redirección** (histórico): `enrichment-report.md` no la necesitó — solo `docs/README.md` lo
referenciaba, y se actualizó directamente al destino de entonces.

## 2. Archivos modificados (limpieza de metadatos de migración)

| Archivo | Qué se quitó / simplificó |
|---|---|
| `docs/architecture/container-diagram.md` | Encabezado "Migrado desde..." |
| `docs/architecture/deployment-view.md` | Encabezado "Migrado desde..." |
| `docs/architecture/system-context.md` | Encabezado "Migrado desde..." |
| `docs/domain/domain-model.md` | Encabezado "Migrado desde...", frases "no se infiere/inventa", pointer a change-log |
| `docs/domain/aggregates.md` | Frase "No se inventan agregados..." |
| `docs/domain/commands-events.md` | Frase "no se inventa un nombre de evento", pointer a change-log |
| `docs/domain/functional-requirements.md` | Párrafo final sobre incorporación de RF por decisiones posteriores |
| `docs/domain/traceability-matrix.md` | Intro "No se inventan RF, CU...", notas "(antes X)" y pointers a change-log en 6 filas |
| `docs/domain/use-cases.md` | 9 bloques "**Actualización (decisión...)**" y pointers a change-log |
| `docs/product/business-rules.md` | Intro de renumeración/migración, glosario "no se agregan...", 5 etiquetas "**Configurable, no fijo**"/"**Confirmado**" |
| `docs/product/stakeholders.md` | Encabezado "Migrado desde...", 3 bloques "**Actualización (decisión...)**", nota de interpretación ya resuelta |
| `docs/product/product-backlog.md` | 4 bloques "Actualización posterior", pointer a change-log en intro y en EPIC-09/12 |
| `docs/product/scope-mvp.md` | Intro "No se inventa alcance...", 2 bloques "**Resuelto**", 4 pointers a change-log |
| `docs/product/problem-statement.md` | Referencia a `migration-report.md` (archivo hoy inexistente) en la nota de fuente |
| `docs/product/vision.md` | Referencia a `migration-report.md` (archivo hoy inexistente) en la nota de fuente |
| `docs/quality/observability-requirements.md` | Encabezado "Migrado desde..." |

No se tocó `docs/product/core-documents-change-log.md`: es el registro histórico de decisiones en
sí mismo (igual que `decisions-log.md`), no un documento "defensivo de migración" — archivarlo o
vaciarlo habría eliminado trazabilidad real. Sigue existiendo como referencia; solo dejó de ser
citado repetidamente desde los documentos vivos.

## 3. `docs/planning/decisions-log.md`

- `DEC-001` ya existía con contenido completo (estrategia de repositorios y fuente de verdad); no
  se creó ninguna decisión adicional.
- No había una nota de ambigüedad sobre si el documento debía vivir en `operations/`; no se
  encontró ninguna que eliminar.
- Se agregó la sección "Referencias a decisiones registradas en otros documentos", con pointers
  (no DEC formales) a las decisiones confirmadas posteriores a Sprint 1, movidas aquí desde la
  sección "Decisiones posteriores a este cierre" que tenía `sprint-1.md`.

## 4. `docs/planning/release-plan.md`

Reemplazado el `TODO` por el plan incremental de 4 versiones (v0.1 a v1.0) exactamente como se
especificó, cada una con propósito, alcance resumido, evidencia de aceptación y estado
"Planificada". Ninguna versión se presenta como liberada.

## 5. `docs/planning/roadmap.md`

Reescrito con las 8 secciones (Sprint 0 a Sprint 7), cada una con objetivo, capacidades/
entregables esperados, evidencia esperada y relación con APF1/APF2/APF3/proyecto final. Se
conservó el orden y el contenido temático ya definido para cada sprint.

## 6. `docs/planning/sprint-0.md`

Título cambiado a "Sprint 0 — Incepción técnica y preparación del entorno". Reescrito como fase de
preparación con evidencia de Git (commit `4628e40`), sin afirmar una ceremonia o cierre Scrum
histórico. Incluye objetivo, actividades verificables, evidencia, entregables, criterios de salida
hacia Sprint 1, riesgos iniciales y una nota explícita de que la retrospectiva no está disponible
por no haber sido un sprint formal documentado.

## 7. `docs/planning/sprint-1.md`

Mantenido como sprint de definición y planificación. Se quitó el lenguaje de migración/defensa de
la introducción y de la retrospectiva. Se conservaron íntegros los entregables reales, la tabla de
historias, el DoD y los riesgos. La retrospectiva sigue marcada como pendiente (no se afirma
completada). La sección "Decisiones posteriores a este cierre" se movió a
`docs/planning/decisions-log.md` como referencias; la historia de Sprint 1 (qué se hizo y cuándo)
no se alteró.

## 8. `docs/planning/sprint-2.md`

Objetivo actualizado para cubrir explícitamente: configuración de activos/sensores/perfiles,
ciclo de vida del sensor, tablero y detalle de incidentes, reconocimiento/escalamiento/cierre
técnico, métricas operativas, bitácora y login de demostración por roles. Se agregó una tabla de
actores por capacidad (Administrador de plataforma, Supervisor de operaciones, Operador, Técnico
de mantenimiento) según las decisiones confirmadas. Se señaló, sin resolverlo, que no existe
todavía una historia de usuario dedicada al prototipo de ciclo de vida del sensor.

## Enlaces actualizados

- `docs/README.md`: en su momento, las referencias a `migration-report.md`/`enrichment-report.md`
  se actualizaron para apuntar a `_legacy/`; en una ronda posterior, al perderse esos archivos, el
  párrafo se reescribió como nota histórica sin enlace activo (ver
  `docs/architecture/architecture-consistency-report.md`).
- `docs/planning/sprint-1.md`: referencia a `business-analysis.md` (ya archivado) corregida a
  `docs/product/scope-mvp.md`.
- Todas las menciones de actor obsoletas ya corregidas en rondas anteriores se mantuvieron; esta
  tarea no modificó ningún actor funcional adicional.

## Verificación técnica

Se validaron todas las referencias entre backticks (rutas relativas a `docs/` con extensión `.md`) en los 44 archivos de
`docs/planning/`, `docs/product/`, `docs/domain/`, `docs/architecture/` y `docs/quality/`. Las
únicas 3 rutas inexistentes referenciadas están dentro de `docs/product/core-documents-change-log.md`
(documento histórico exento de esta limpieza, citando rutas ya archivadas a propósito). No se
encontraron encabezados `## CU-XXX` duplicados en `docs/domain/use-cases.md` ni reglas `RN-XXX`
duplicadas en `docs/product/business-rules.md`.

## Contenido que se preservó

- Las 19 reglas de negocio (RN-001 a RN-019), sin cambio de numeración ni de significado.
- Los 16 RF, 21 CU (CU-010 sigue reservado/no definido) y las 9 ADR, íntegros.
- Toda la evidencia de Git referenciada (commit `4628e40`).
- El registro de decisiones (`DEC-001`) y el registro de cambios
  (`docs/product/core-documents-change-log.md`), sin alterar.
- El contenido completo de los dos reportes archivados, sin modificar una sola línea.
- El historial "hecho" de las historias de usuario de Sprint 1 y Sprint 2 (no se reescribió como
  si no hubiera ocurrido).

## TODOs funcionales pendientes (no resueltos por este saneamiento)

- Actor de CU-015 (inyección de telemetría de prueba): no confirmado.
- Eventos de dominio no catalogados para CU-013, CU-014, CU-017 a CU-020, y para "pérdida de
  conectividad" de un sensor (CU-002).
- Criterio de vencimiento de calibración/verificación (RN-018): pendiente de definir, debe ser
  configurable.
- Qué constituye una "acción equivalente documentada" cuando la tarea programada de vencimiento
  no ejecuta la transición directamente (RN-018).
- Servicio que atiende RF-013/CU-014 (asignaciones de acceso): sin asignar.
- Valores numéricos agregados de SLA/KPI (MTTA/MTTR globales, throughput, disponibilidad): sin
  confirmar por negocio.
- RF-009 sin evento ni criterio de prueba propio; CU-009 sin RF asociado; eventos
  `AssetRegistered`/`IncidentResolved` no referenciados por ningún CU (vacíos ya conocidos,
  documentados en `docs/academic/apf1-mapping.md`).
- Gap señalado en `docs/planning/sprint-2.md`: no existe una historia de usuario dedicada al
  prototipo de ciclo de vida del sensor ni a la configuración conjunta de activos/sensores/
  perfiles más allá de HU-013.
- Evidencia de aceptación de v0.2, v0.3 y v1.0 (`docs/planning/release-plan.md`): por definir
  junto con el backlog de los sprints correspondientes.
