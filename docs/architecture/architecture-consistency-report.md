# Reporte de consistencia arquitectónica

**Estado: Lote de arquitectura CERRADO (Rondas 1-5).** Este documento se congela como bitácora
histórica. Cambios puntuales posteriores se registran en `docs/planning/decisions-log.md`. Este
reporte solo se reabre al cerrar un lote nuevo (planificación, frontend/APF1 u operación) que
requiera una auditoría de consistencia propia.

Cierre del lote de arquitectura, alineado con el dominio ya documentado (RF-001 a RF-018, RN-001
a RN-020, CU-001 a CU-022, catálogo de eventos vigente). No se generó código, no se ejecutó
Docker/Terraform/LocalStack/cloud, no se crearon recursos en ningún proveedor, no se eliminó
ningún ADR ni se alteró su numeración.

**Este documento consolida también el histórico de `documentation-consistency-report.md`
(retirado en la Ronda 5, ver más abajo).**

## Ronda 2 — decisión de proveedor cloud y estrategia de observabilidad

**Resuelve el pendiente #1 de la Ronda 1** (ver tabla B, fila 1, marcada ahora como resuelta):
Azure queda confirmado como proveedor cloud objetivo para el despliegue planificado y la
presentación final; el aprovisionamiento y despliegue permanecen pendientes de ejecución. Se
actualizaron las 9 secciones "Evolución futura a Azure" de los ADR para reflejar esta
confirmación (sin reescribir decisión, alcance ni consecuencias de ningún ADR, y sin nombrar un
servicio Azure concreto de cómputo/base de datos/mensajería que no estuviera ya mencionado como
candidato ilustrativo). Se creó `docs/operations/observability-strategy.md` como estrategia de
observabilidad por entorno (local vs. Azure planificado), con OpenTelemetry como instrumentación
vendor-neutral y Application Insights/Azure Monitor/Log Analytics como destino cloud planificado.
No se ejecutó Docker/Terraform/Azure CLI/GitHub Actions, no se creó ni configuró ningún recurso
Azure, y no se afirma suscripción Azure, Application Insights, Log Analytics, Azure Monitor,
Prometheus/Grafana administrados, Key Vault, pipeline operativo, métricas reales, dashboards
activos, alertas activas, costos ni resultados de pruebas.

Documentos modificados en esta ronda: los 9 ADR (solo su sección "Evolución futura a Azure"),
`system-context.md`, `deployment-view.md`, `tech-stack.md`, `data-flow.md`, `event-flow.md`,
`docs/quality/non-functional-requirements.md` (RNF-002/RNF-004 precisadas, RNF-008 nueva),
`docs/quality/risk-register.md` (R-010 nuevo, R-011 nuevo), `docs/quality/sla-kpi.md`,
`docs/planning/roadmap.md`, `release-plan.md`, `docs/product/product-backlog.md` (EPIC-13,
HU-021, HU-022), y este mismo reporte. `container-diagram.md` y `component-diagram.md` no
requirieron cambios: ya eran agnósticos de proveedor cloud.

## Ronda 3 — decisiones A-F (plataforma Azure, ingesta, identidad/accesos, auditoría, métricas, notificaciones)

Cierra los pendientes #2, #3, #4 y #7 de la tabla B (abajo, ya actualizada) mediante seis
decisiones registradas en `docs/planning/decisions-log.md` (DEC-002 a DEC-007): A (plataforma
Azure planificada: Azure Container Apps, Azure Database for PostgreSQL Flexible Server, RabbitMQ
conservado), B (protocolo de ingesta de telemetría: gRPC desde el simulador, REST interno
protegido para CU-015), C (Identity & Access como módulo/servicio lógico dueño de RF-013/CU-014),
D (Audit Log como módulo/servicio lógico dueño de la nueva RF-018/CU-009), E (RF-009/CU-008
atendido por un módulo de consultas operativas dentro de Incident Service) y F (Azure
Communication Services Email como proveedor productivo planificado de notificaciones, Sprint 6;
Mailpit permanece solo para pruebas locales).

No se generó código, no se ejecutó Docker/Terraform/Azure CLI/GitHub Actions/despliegue, no se
creó ni configuró ningún recurso Azure, no se alteró ningún ID existente de ADR/RF/RN/CU/evento, y
no se reescribió la decisión, contexto, consecuencias ni riesgos de ningún ADR (solo se agregó una
sección "Actualización posterior", fechada "por confirmar", en ADR-003, ADR-004, ADR-006, ADR-007
y ADR-008).

**Único ID nuevo creado**: RF-018 (siguiente libre), trazado a CU-009 y RN-008 (Decisión D). No se
crearon eventos ni CU nuevos: Identity & Access, Audit Log y el módulo de consultas operativas de
Incident Service son ownership sobre RF/CU ya existentes (RF-013/CU-014, RF-018/CU-009 nueva,
RF-009/CU-008), no funcionalidad nueva fuera del alcance del MVP.

Documentos modificados en esta ronda: `docs/planning/decisions-log.md` (DEC-002 a DEC-007); ADR-003,
ADR-004, ADR-006, ADR-007, ADR-008 (solo sección "Actualización posterior"); `system-context.md`,
`container-diagram.md`, `component-diagram.md`, `deployment-view.md`, `data-flow.md`,
`event-flow.md`, `tech-stack.md`; `docs/domain/functional-requirements.md` (RF-018),
`use-cases.md` (CU-001, CU-009), `commands-events.md` (`AssetRegistered`),
`domain-model.md`, `traceability-matrix.md`; `docs/product/vision.md`, `scope-mvp.md`,
`product-backlog.md` (EPIC-04, EPIC-05, EPIC-06, EPIC-09); `docs/quality/non-functional-requirements.md`
(RNF-007 precisada), `risk-register.md` (R-012, R-013, R-014 nuevos; R-010 precisado);
`docs/planning/roadmap.md` (Sprint 3, Sprint 4, Sprint 5, Sprint 6), `release-plan.md` (v0.3);
`docs/academic/apf1-mapping.md`; este mismo reporte (entonces también en el reporte de
consistencia documental, retirado en la Ronda 5).

### Tabla de tecnologías de observabilidad

| Señal | Local previsto | Azure planificado | Decisión |
|---|---|---|---|
| Trazas | OpenTelemetry (SDK de trazas), consulta local compatible | Application Insights / Azure Monitor | Confirmada la instrumentación vendor-neutral (RNF-002); destino Azure planificado, sin suscripción operativa |
| Métricas | Prometheus, visualizado en Grafana; OpenTelemetry + Micrometer | Application Insights / Azure Monitor | Confirmada para local; planificada para Azure (RNF-004, RNF-008) |
| Logs | Loki, visualizado en Grafana; formato JSON estructurado | Azure Monitor Logs / Log Analytics | Confirmada para local; planificada para Azure |
| Eventos de negocio | Registrados junto con logs/trazas, correlacionados (`traceId`/`correlationId`) | Mismo destino que logs/trazas en Azure | Confirmado el catálogo mínimo (`docs/operations/observability-strategy.md`); sin cambio por Ronda 3 |
| Métricas/paneles administrados (alternativa) | No aplica | Azure Managed Prometheus, Azure Managed Grafana | **Opción futura**, no confirmada como parte del MVP |

## Ronda 4 — arquitectura interna, RabbitMQ en Azure y gestión de secretos

Cierra los pendientes #5, #6 (parcial: mapeo rol→endpoint permanece pendiente de implementación) y
#8 de la tabla B mediante tres decisiones registradas en `docs/planning/decisions-log.md`
(DEC-008 a DEC-010): A (no se adopta arquitectura hexagonal formal; Identity & Access, Audit Log y
el módulo de consultas operativas son **módulos internos de Incident Service**, no microservicios
separados), B (para la presentación final, RabbitMQ se despliega como contenedor planificado en
Azure Container Apps; sin Azure Service Bus ni alternativa gestionada), C (secretos: `.env` no
versionado + `.env.example` en local; Azure Key Vault + Managed Identity planificado en Azure). La
Decisión D (parámetros operativos configurables, sin valores globales) ya estaba documentada con
claridad en `docs/product/business-rules.md` (RN-011, RN-018, RN-020); no se registró un DEC nuevo
para ella, solo una nota de confirmación en `docs/planning/decisions-log.md`.

No se generó código, no se ejecutó Docker/Terraform/Azure CLI/GitHub Actions/despliegue, no se
creó ni configuró ningún recurso Azure (incluidos Key Vault, Managed Identity o RabbitMQ en Azure
Container Apps), y no se renumeró ni eliminó ningún ID existente. No se reintrodujo ni reconstruyó
contenido de `docs/academic/_legacy/`, `migration-report.md` ni `enrichment-report.md`.

**Cambio de representación arquitectónica**: `container-diagram.md`, `component-diagram.md` y
`deployment-view.md` dejan de mostrar Identity & Access y Audit Log como cajas C4 separadas; ahora
se representan como módulos internos dentro de la caja de Incident Service, con esquemas lógicos
de persistencia propios (`identity`, `auditlog`) pero sin proceso ni despliegue independiente.

Documentos modificados en esta ronda: `docs/planning/decisions-log.md` (DEC-008 a DEC-010, más
notas de actualización en DEC-004, DEC-005, DEC-006); ADR-007, ADR-008 (segunda "Actualización
posterior"); `docs/architecture/container-diagram.md`, `component-diagram.md`,
`deployment-view.md`, `tech-stack.md` (tabla rehecha), `data-flow.md`; `docs/product/scope-mvp.md`,
`vision.md`, `product-backlog.md` (EPIC-06, EPIC-09); `docs/domain/domain-model.md`,
`use-cases.md`, `traceability-matrix.md`; `docs/planning/roadmap.md` (Sprint 3, Sprint 4, Sprint 5,
Sprint 7); `docs/academic/apf1-mapping.md`; este mismo reporte (entonces también en el reporte de
consistencia documental, retirado en la Ronda 5).

### Tabla de tecnologías por entorno

| Capa | Local previsto | Azure planificado | Estado |
|---|---|---|---|
| Cómputo | Docker Compose | Azure Container Apps (DEC-002) | Planificada; ningún recurso creado |
| Persistencia | PostgreSQL, esquemas lógicos `asset`/`telemetry`/`incident`/`identity`/`auditlog` (ADR-006, DEC-008) | Azure Database for PostgreSQL Flexible Server (DEC-002) | Local confirmada; Azure planificada |
| Mensajería | RabbitMQ (ADR-004) | Contenedor planificado en Azure Container Apps (DEC-009); sin Service Bus, sin alternativa gestionada | Local confirmada; Azure planificada |
| Identity & Access / Audit Log / métricas | Módulos internos de Incident Service (DEC-004, DEC-005, DEC-006, DEC-008); sin arquitectura hexagonal formal | Sin cambio de ownership ni granularidad | Confirmada |
| Gestión de secretos | `.env` no versionado + `.env.example` (DEC-010) | Azure Key Vault + Managed Identity (DEC-010) | Planificada; sin secretos ni identidades creados |
| Observabilidad — trazas/métricas/logs | OpenTelemetry, Prometheus, Grafana, Loki | Application Insights/Azure Monitor, Azure Monitor Logs/Log Analytics | Local confirmada; Azure planificada |
| Notificaciones | Mailpit, solo pruebas locales | Azure Communication Services Email, Sprint 6 (DEC-007) | Local planificada; Azure planificada, sin correos reales |

## Ronda 5 — retiro del reporte de consistencia documental y corrección de enlaces rotos

`documentation-consistency-report.md` se retira en esta ronda: su alcance quedó redundante
con este reporte tras las Rondas 2 a 4 (mismas decisiones, mismos documentos modificados, mismo
historial de saneamiento). Todo su contenido único —hallazgos, IDs creados/eliminados y pendientes
que no estaban ya aquí— se migra a la sección "Historial consolidado" de abajo. El archivo se
elimina del repositorio; ninguna referencia activa lo menciona ya (`docs/README.md`,
`docs/planning/decisions-log.md` y este mismo reporte se corrigieron para apuntar aquí en su
lugar).

Adicionalmente, esta ronda corrige los 12 enlaces rotos reportados en la validación anterior (ver
tabla más abajo) y dos referencias activas al reporte retirado. No se generó código, no se
ejecutaron Docker/Terraform/Azure CLI/GitHub Actions, no se creó ningún recurso Azure, no se
renumeró ni eliminó ningún ID (RF, RN, RNF, CU, ADR, DEC, riesgo, épica, historia), y no se
reconstruyó ni restauró contenido de `docs/academic/_legacy/`, `migration-report.md` ni
`enrichment-report.md`.

### Historial consolidado — reporte de consistencia documental (retirado)

Contenido único migrado desde `documentation-consistency-report.md`, no duplicado en el resto
de este reporte.

**Alcance original de ese reporte**: auditoría de consistencia transversal y aplicación de 5
decisiones de dominio confirmadas —distintas de las decisiones A-F de arquitectura de este
reporte— sobre escalamiento de incidentes, inyección de telemetría, pérdida de conectividad,
eventos del ciclo de vida del sensor y eliminación de `IncidentResolved`. No se reorganizaron
carpetas, no se eliminó ningún archivo, no se agregó arquitectura fuera de lo indicado y no se
inventaron métricas, integraciones físicas, regulaciones, fechas, sprints cerrados ni despliegues.

**Documentos revisados en esa auditoría**: `docs/academic/apf1-mapping.md`,
`01-propuesta-proyecto.md`, `report-artifacts-index.md`; `docs/domain/functional-requirements.md`,
`use-cases.md`, `commands-events.md`, `domain-model.md`, `state-machines.md`,
`traceability-matrix.md`; `docs/product/business-rules.md`, `scope-mvp.md`, `stakeholders.md`;
`docs/quality/risk-register.md`; `docs/operations/incident-model.md` (más los documentos de
`docs/architecture/` ya cubiertos en el alcance de este reporte).

**Inconsistencias de dominio/producto resueltas** (tabla original):

| # | Inconsistencia | Resolución |
|---|---|---|
| 1 | RF-007 describía "escalar por incumplimiento de SLA" sin precisar actor, lo que podía leerse como escalamiento automático | Reescrito: "Permitir al Supervisor de operaciones escalar un incidente y al sistema registrar la transición y notificar al Técnico de mantenimiento" |
| 2 | CU-015 tenía actor `TODO` sin confirmar | Actor principal confirmado: Administrador de plataforma |
| 3 | CU-002 marcaba "pérdida de conectividad" como TODO sin evento ni mecanismo de detección | Se separó en CU-022 (actor Sistema, no representable dentro de CU-002 por diferencia de actor); CU-002 remite a CU-022 |
| 4 | `commands-events.md` era una lista plana sin productor/consumidor/trazabilidad | Reestructurado en tabla: evento, productor, consumidor/propósito, CU/RF/RN, payload conceptual |
| 5 | `IncidentResolved` catalogado sin transición ni estado distinto de `IncidentClosed` | Eliminado del catálogo y de toda referencia viva; `IncidentClosed` documentado como único evento de cierre técnico |
| 6 | `container-diagram.md` mostraba `IS → NS` como comunicación directa, contradiciendo ADR-005 | Corregido a `IS → MQ → NS` |
| 7 | R-005 a R-009 seguían "abiertos" pese a tener ADR resolutorio desde hace varias rondas | Estado cambiado a "Mitigado por decisión; pendiente validación en implementación" |
| 8 | `component-diagram.md`, `deployment-view.md`, `data-flow.md`, `event-flow.md`, `tech-stack.md` estaban vacíos o con solo `TODO` | Completados con contenido derivado de decisiones ya confirmadas |
| 9 | `docs/academic/report-artifacts-index.md` era una lista sin evidencia ni estado | Convertido a tabla: artefacto, documento fuente, ruta, estado, uso esperado en APF1 |
| 10 | `docs/academic/01-propuesta-proyecto.md` tenía un diferenciador centrado en tecnología | Reemplazado por texto de negocio; arquitectura técnica movida a enlace |

**IDs creados en esa auditoría** (ya reflejados en `docs/domain/` vigente; ninguno nuevo respecto
de este reporte):

| ID | Descripción | Trazabilidad |
|---|---|---|
| RF-017 | Detectar y registrar pérdida de conectividad de un sensor | CU-022, RN-020 |
| RN-020 | Ausencia de telemetría esperada genera evento operativo de conectividad, sin crear incidente | CU-022, RF-017 |
| CU-022 | Detectar y registrar pérdida de conectividad de sensor (actor Sistema) | RF-017, RN-020, evento `SensorConnectivityLost` |
| `SensorStatusChanged`, `SensorReassigned`, `SensorCalibrationRecorded`, `SensorRetired`, `SensorCalibrationExpired`, `SensorConnectivityLost` | Eventos del ciclo de vida del sensor y de conectividad | RF-016/RF-017, CU-017 a CU-022 según el evento |

**IDs eliminados**: `IncidentResolved` (evento) — no existe una transición o estado del incidente
distinto de `IncidentClosed` que lo justifique.

**Referencias actualizadas en esa auditoría**: `docs/domain/traceability-matrix.md` (filas RF-007,
RF-014, RF-016, RF-017), `docs/product/scope-mvp.md`, `docs/product/stakeholders.md` (CU-015,
CU-022), `docs/planning/roadmap.md` (Sprint 3 y 5), `docs/operations/incident-model.md`
(`IncidentResolved` retirado), `docs/domain/state-machines.md`.

**Pendientes de esa auditoría ya resueltos por decisiones posteriores** (Rondas 2-4 de este
reporte): CU-009 sin RF asociado (RF-018, DEC-005); servicio dueño de RF-013/CU-014, RF-009/CU-008
y RF-018/CU-009 (DEC-004/005/006); proveedor productivo de notificaciones (DEC-007); protocolo de
ingesta de telemetría (DEC-003); servicios Azure de cómputo/persistencia (DEC-002); arquitectura
hexagonal (DEC-008); granularidad de Identity & Access/Audit Log (DEC-008); RabbitMQ en Azure
(DEC-009); gestión de secretos (DEC-010).

**Pendientes de esa auditoría que siguen abiertos** (ya incorporados a "Deudas / documentación
pendiente" de este reporte, más abajo, para no mantener dos listas): RN-010/RN-011 sin columna RN
propia en la matriz de trazabilidad; RF-009 sin evento ni criterio de prueba propio; evento
`AssetRegistered` sin consumidor confirmado; prototipos de `coldguard-frontend` sin evidencia
verificable; valores de demostración configurables sin valor numérico fijado.

### Tabla de corrección de los 12 enlaces rotos

Columna "Ruta rota original" en texto plano, sin backticks de ruta activa, precisamente porque
esas rutas no existen — para no reintroducir el mismo patrón que la validación de enlaces marca
como roto.

| # | Ruta rota original (histórica, no existe) | Archivo que la citaba | Acción aplicada | Ruta final |
|---|---|---|---|---|
| 1 | docs/academic/_legacy/business-analysis.md | `docs/planning/planning-cleanup-report.md` | Reescrito como referencia histórica en pasado, sin ruta activa | Sin ruta (mención de nombre de archivo) |
| 2 | docs/academic/_legacy/enrichment-report.md | `docs/planning/planning-cleanup-report.md` | Ídem | Sin ruta |
| 3 | docs/academic/_legacy/migration-report.md | `docs/planning/planning-cleanup-report.md` | Ídem | Sin ruta |
| 4 | docs/migration-report.md | `docs/planning/planning-cleanup-report.md` (y ambos reportes de consistencia) | Ídem | Sin ruta |
| 5 | docs/enrichment-report.md | `docs/planning/planning-cleanup-report.md` (y ambos reportes de consistencia) | Ídem | Sin ruta |
| 6 | docs/architecture/c4.md | `docs/product/core-documents-change-log.md` | Corregido: la intención original correspondía al C4 ya dividido | `docs/architecture/system-context.md`, `container-diagram.md`, `component-diagram.md`, `deployment-view.md` |
| 7 | docs/architecture/data-model.md | `docs/product/core-documents-change-log.md` | Aclarado como artefacto pendiente (modelo de datos físico no existe todavía), sin enlace activo | Sin ruta; referenciado como pendiente en "Deudas" de este reporte |
| 8 | docs/architecture/security.md | `docs/security/threat-model.md` | Corregido: el contenido de seguridad ya vive en ADR-007/ADR-008 | `docs/architecture/adr/ADR-007-jwt-rbac.md`, `ADR-008-gateway-responsibilities.md` |
| 9 | docs/business/business-analysis.md | `docs/product/core-documents-change-log.md` | Aclarado como referencia histórica; el contenido real vive en stakeholders/domain-model | `docs/product/stakeholders.md`, `docs/domain/domain-model.md` |
| 10 | docs/operations/backup-restore.md | `docs/operations/backup-recovery-plan.md` | Eliminada la referencia duplicada; el contenido ya está consolidado en el único archivo | `docs/operations/backup-recovery-plan.md` (mismo archivo, sin fragmentar) |
| 11 | docs/operations/disaster-recovery.md | `docs/operations/backup-recovery-plan.md` | Ídem | `docs/operations/backup-recovery-plan.md` |
| 12 | docs/operations/incident-management.md | `docs/operations/incident-model.md` | Corregido: `incident-model.md` ya es el documento operativo de incidentes; la referencia a un archivo distinto se retiró | `docs/operations/incident-model.md` (mismo archivo) |

## Alcance revisado

`docs/architecture/system-context.md`, `container-diagram.md`, `component-diagram.md`,
`deployment-view.md`, `data-flow.md`, `event-flow.md`, `tech-stack.md`, y los 9 ADR
(`docs/architecture/adr/`), contrastados contra `docs/domain/functional-requirements.md`,
`use-cases.md`, `commands-events.md`, `domain-model.md`, `traceability-matrix.md`,
`docs/product/business-rules.md`, `docs/quality/non-functional-requirements.md`,
`risk-register.md`, `sla-kpi.md`.

## A. Tabla de cambios aplicados

| Documento | Cambio |
|---|---|
| `system-context.md` | Se individualizaron los 5 actores humanos (antes un nodo genérico "Operador/Supervisor"); `coldguard-frontend` explícito como sistema cliente externo; correo representado como "adaptador de notificaciones" abstracto, no un proveedor productivo; agregada sección "Alcance y supuestos" |
| `container-diagram.md` | Agregados protocolo por interacción (REST, gRPC, AMQP, SQL) y columna "Datos que maneja"; aclarado que Gateway no es BFF (ADR-008); aclarado que Docker Compose no es un contenedor C4 |
| `component-diagram.md` | Reescrito para profundizar solo Incident Service: 8 componentes conceptuales (API/Command Handler, Motor de evaluación, Servicio de aplicación, Repositorio, Publicador de eventos vía Outbox, Auditoría/correlación, adaptadores de persistencia y mensajería) y su recorrido por CU-003 a CU-006 |
| `data-flow.md` | Documentados los dos recorridos (A: sensor-simulator; B: endpoint de pruebas, actor Administrador de plataforma); agregada tabla de datos sensibles y de controles previstos (no afirmados como implementados) |
| `event-flow.md` | Reestructurado por categorías (telemetría, conectividad, incidentes, notificación, ciclo de vida del sensor); aclarado que `SensorConnectivityLost` no crea incidente; confirmada la ausencia de `IncidentResolved`; nombres verificados contra `commands-events.md` |
| `deployment-view.md` | Separado en vista A (local, con `coldguard-frontend` explícito como proceso separado) y vista B (cloud planificado, agnóstica de proveedor); agregados límites de seguridad/red lógicos; alta disponibilidad/balanceo/DR movidos a "consideraciones futuras", no como diseño actual |
| `tech-stack.md` | Reescrito como tabla capa/tecnología/propósito/estado/evidencia con los estados exactos indicados (Confirmada, Preferida, Planificada, Por decidir, Condicionada) |
| ADR-001 a ADR-009 | Sin cambios de contenido ni numeración (ver auditoría abajo) |

## B. Tabla de pendientes que requieren decisión humana

| # | Pendiente | Por qué requiere decisión humana |
|---|---|---|
| 1 | ~~Proveedor cloud (Azure/AWS) no decidido~~ | **Resuelto en la Ronda 2**: Azure confirmado como proveedor cloud objetivo (decisión humana). Los 9 ADR y `tech-stack.md` ya reflejan esta confirmación; `.claude/rules/documentation.md` no requirió cambio porque su exigencia de la sección "Evolución futura a Azure" ya era consistente con esta decisión. |
| 2 | ~~Protocolo de ingesta de telemetría (Sensor Simulator/endpoint de pruebas → Telemetry Service): gRPC, REST interno u otro~~ | **Resuelto por decisión en la Ronda 3** (DEC-003, `docs/planning/decisions-log.md`): gRPC desde Sensor Simulator; REST interno protegido para CU-015. Aplicado en `container-diagram.md`, `data-flow.md`, ADR-003 |
| 3 | ~~Servicio dueño de RF-013/CU-014 (accesos), RF-009/CU-008 (métricas) y RN-008/CU-009 (auditoría general)~~ | **Resuelto por decisión en la Ronda 3** (DEC-004, DEC-005, DEC-006): Identity & Access, módulo de consultas operativas de Incident Service, y Audit Log, respectivamente. Aplicado en `container-diagram.md`, `component-diagram.md`, `docs/product/scope-mvp.md` |
| 4 | ~~Proveedor productivo del adaptador de notificaciones (Mailpit es solo para pruebas locales)~~ | **Resuelto por decisión en la Ronda 3** (DEC-007): Azure Communication Services Email, planificado para Sprint 6; sin recurso creado. Aplicado en `tech-stack.md`, `deployment-view.md`, `docs/planning/roadmap.md` |
| 5 | ~~Adopción de arquitectura hexagonal formal para Incident Service (o los demás servicios)~~ | **Resuelto por decisión en la Ronda 4** (DEC-008): no se adopta arquitectura hexagonal formal como patrón obligatorio del MVP; se mantiene separación por módulos/capas, con puertos solo en los límites externos |
| 6 | Mapeo explícito rol → endpoint (RBAC operacionalizado) | **Sigue sin resolver** — es un riesgo de implementación (R-014, `docs/quality/risk-register.md`), no una decisión documental pendiente; se resuelve construyendo el Gateway y los endpoints protegidos en Sprint 4 |
| 7 | ~~Si corresponde reescribir la sección "Evolución futura a Azure"~~ | **Resuelto en la Ronda 2**: no se necesita cambio de plantilla — Azure es ahora el proveedor confirmado, exactamente lo que la sección y la regla de `.claude/rules/documentation.md` ya nombraban. Solo se actualizó el contenido de cada sección, no la plantilla. |
| 8 | ~~Granularidad final de Identity & Access y Audit Log (microservicio separado vs. módulo dentro de otro servicio)~~ | **Resuelto por decisión en la Ronda 4** (DEC-008): ambos son módulos internos de Incident Service, junto con el módulo de consultas operativas; ninguno es microservicio separado |
| 9 | ~~Elección operativa concreta de RabbitMQ en Azure (contenedor en Azure Container Apps vs. alternativa gestionada)~~ | **Resuelto por decisión en la Ronda 4** (DEC-009): contenedor planificado en Azure Container Apps; sin alternativa gestionada en el MVP |
| 10 | ~~Gestión de secretos en Azure sin servicio concreto seleccionado~~ | **Resuelto por decisión en la Ronda 4** (DEC-010): Azure Key Vault + Managed Identity, planificado; local con `.env` no versionado + `.env.example` |

## C. Riesgos de arquitectura que deben pasar a planificación o risk-register

| Riesgo | Motivo | Destino sugerido |
|---|---|---|
| ~~Protocolo de ingesta de telemetría no decidido puede bloquear el diseño de Telemetry Service en Sprint 5~~ | Resuelto en la Ronda 3 (R-012, `docs/quality/risk-register.md`, "Mitigado por decisión: DEC-003") | — |
| ~~Ambigüedad Azure/proveedor por decidir~~ | Resuelto en la Ronda 2 (R-010, `docs/quality/risk-register.md`), precisado en la Ronda 3 con Azure Container Apps y PostgreSQL Flexible Server (DEC-002) | — |
| Configuración incorrecta de telemetría o exposición de datos sensibles en logs/trazas al instrumentar con OpenTelemetry | Riesgo nuevo de la Ronda 2 (R-011), sigue abierto | `docs/quality/risk-register.md` (ya registrado) |
| ~~Adaptador de notificaciones sin proveedor productivo decidido puede retrasar Sprint 6~~ | Resuelto en la Ronda 3 (R-013, `docs/quality/risk-register.md`, "Mitigado por decisión: DEC-007") | — |
| RBAC declarado (ADR-007) pero no operacionalizado (sin mapeo rol→endpoint) puede llegar a Sprint 4 sin criterio de implementación | Riesgo nuevo de la Ronda 3 (R-014), sigue abierto — depende de implementación, no de decisión documental | `docs/quality/risk-register.md` (ya registrado) |

## Auditoría de ADR — tabla de alineamiento

| ADR | Decisión | Estado | Documentos/diagramas que la reflejan | Discrepancias |
|---|---|---|---|---|
| ADR-001 | Monorepo backend (`coldguard-platform`) | Vigente | `container-diagram.md` (todos los servicios listados), `tech-stack.md` | Ninguna. Su sección "Evolución futura a Azure" se actualizó en la Ronda 2 para confirmar el proveedor, sin cambiar la decisión del ADR |
| ADR-002 | Frontend en repositorio separado (`coldguard-frontend`) | Vigente | `system-context.md` (`coldguard-frontend` como sistema externo), `container-diagram.md` | Ninguna |
| ADR-003 | gRPC para comunicación síncrona interna; REST solo en el borde | Vigente | `container-diagram.md` (protocolos anotados en esta revisión) | Ninguna. Sección "Actualización posterior" agregada en la Ronda 3 (DEC-002, DEC-003) |
| ADR-004 | RabbitMQ como broker local del MVP | Vigente | `container-diagram.md`, `deployment-view.md`, `event-flow.md` | Ninguna. Sección "Actualización posterior" agregada en la Ronda 3 (DEC-002: RabbitMQ conservado, no sustituido por Azure Service Bus) |
| ADR-005 | Incident Service → Notification Service vía evento (no gRPC directo) | Vigente | `container-diagram.md` (`IS → MQ → NS`), `event-flow.md`, `data-flow.md` | Ninguna — el desync histórico (`IS → NS` directo) ya se había corregido en una ronda anterior a esta |
| ADR-006 | PostgreSQL única, ownership lógico de esquema por servicio | Vigente | `container-diagram.md` (esquemas mencionados por servicio), `component-diagram.md` (adaptador de persistencia) | Ninguna. Sección "Actualización posterior" agregada en la Ronda 3 (DEC-002: Azure Database for PostgreSQL Flexible Server planificado) |
| ADR-007 | JWT + Spring Security + RBAC | Vigente | `container-diagram.md`, `data-flow.md` (controles previstos), `tech-stack.md` | Ninguna nueva; riesgo de mapeo rol→CU sin operacionalizar sigue abierto, ver pendiente #6. Segunda "Actualización posterior" agregada en la Ronda 4 (DEC-008: Identity & Access es módulo interno de Incident Service, no cambia el ownership fijado en la Ronda 3) |
| ADR-008 | Gateway delgado: auth, routing, propagación de contexto; sin lógica de negocio | Vigente | `container-diagram.md` (explícito "no es BFF") | Ninguna. Segunda "Actualización posterior" agregada en la Ronda 4 (DEC-008: Identity & Access sigue sin ser responsabilidad del Gateway, ahora precisado como módulo interno de Incident Service) |
| ADR-009 | Transactional Outbox para publicación confiable de eventos | Vigente | `component-diagram.md` (Publicador de eventos, componente dedicado) | Ninguna. Antes de esta revisión no tenía representación visual — corregido |

**Ningún ADR se reescribió en su decisión, contexto, consecuencias o riesgos.** En la Ronda 2, las
9 secciones "Evolución futura a Azure" se actualizaron únicamente para confirmar el proveedor
cloud ya decidido por el equipo, preservando cada mención de servicio Azure como candidato
ilustrativo (no como selección) donde ya lo era.

## Documentos modificados

**Ronda 1**: `docs/architecture/system-context.md`, `container-diagram.md`, `component-diagram.md`,
`deployment-view.md`, `data-flow.md`, `event-flow.md`, `tech-stack.md`. Ningún ADR ni documento de
`docs/domain/`, `docs/product/` o `docs/quality/` modificado.

**Ronda 2**: los 9 ADR (solo sección "Evolución futura a Azure"), `system-context.md`,
`deployment-view.md`, `tech-stack.md`, `data-flow.md`, `event-flow.md`,
`docs/quality/non-functional-requirements.md`, `risk-register.md`, `sla-kpi.md`,
`docs/planning/roadmap.md`, `release-plan.md`, `docs/product/product-backlog.md`, y creación de
`docs/operations/observability-strategy.md`. `container-diagram.md` y `component-diagram.md` se
revisaron y no requirieron cambios.

**Ronda 3**: ver tabla completa al inicio de esta sección ("Ronda 3 — decisiones A-F"). En
`docs/architecture/`: ADR-003, ADR-004, ADR-006, ADR-007, ADR-008 (solo sección "Actualización
posterior"), `system-context.md`, `container-diagram.md`, `component-diagram.md`,
`deployment-view.md`, `data-flow.md`, `event-flow.md`, `tech-stack.md`, y este mismo reporte.

**Ronda 4**: ver tabla completa al inicio de esta sección ("Ronda 4 — arquitectura interna,
RabbitMQ en Azure y gestión de secretos"). En `docs/architecture/`: ADR-007, ADR-008 (segunda
"Actualización posterior"), `container-diagram.md`, `component-diagram.md`, `deployment-view.md`,
`tech-stack.md` (tabla por entorno rehecha), `data-flow.md`, y este mismo reporte.

**Ronda 5**: `documentation-consistency-report.md` eliminado (retirado, contenido migrado a
este reporte); `docs/README.md`, `docs/planning/decisions-log.md`,
`docs/planning/planning-cleanup-report.md`, `docs/product/core-documents-change-log.md`,
`docs/security/threat-model.md`, `docs/operations/backup-recovery-plan.md`,
`docs/operations/incident-model.md`, y este mismo reporte.

## Inconsistencias corregidas

1. Protocolos de interacción no estaban anotados en `container-diagram.md` (ADR-003 ya los
   definía) — corregido.
2. `component-diagram.md` no representaba el patrón Transactional Outbox (ADR-009) como
   componente — corregido.
3. `system-context.md` colapsaba 5 actores distintos en un nodo genérico — corregido.
4. `deployment-view.md` no distinguía entre vista local (real) y vista cloud (planificada,
   agnóstica) — corregido.
5. `tech-stack.md` no tenía estados diferenciados (Confirmada/Preferida/Planificada/Por
   decidir/Condicionada) — corregido.
6. **Ronda 3**: `container-diagram.md` no representaba Identity & Access ni Audit Log como
   módulos/servicios lógicos con ownership propio — corregido (DEC-004, DEC-005).
7. **Ronda 3**: `container-diagram.md` dejaba el protocolo de ingesta de telemetría como TODO —
   corregido (DEC-003): gRPC desde el simulador, REST interno protegido para CU-015.
8. **Ronda 3**: `deployment-view.md` (Vista B) representaba cómputo y persistencia como
   completamente agnósticos — corregido (DEC-002): Azure Container Apps y Azure Database for
   PostgreSQL Flexible Server, como planificados, no implementados.
9. **Ronda 4**: `container-diagram.md`, `component-diagram.md` y `deployment-view.md`
   representaban Identity & Access y Audit Log como cajas C4 separadas — corregido (DEC-008): son
   módulos internos de Incident Service, no microservicios independientes.
10. **Ronda 4**: `deployment-view.md` (Vista B) dejaba la operación de RabbitMQ en Azure y la
    gestión de secretos como ambiguas — corregido (DEC-009, DEC-010): RabbitMQ como contenedor
    planificado en Azure Container Apps; secretos vía Azure Key Vault + Managed Identity.
11. **Ronda 4**: `tech-stack.md` mezclaba tecnología/propósito/estado/evidencia en una sola tabla
    genérica — corregido: reescrita como tabla por entorno (Capa | Local previsto | Azure
    planificado | Estado), sin perder ninguna afirmación de estado ya vigente.

## Decisiones que requieren aprobación humana

Ver tabla B arriba. Ninguna se tomó por cuenta propia.

## Deudas / documentación pendiente

- Componentes internos de Asset Service, Telemetry Service y Notification Service (solo Incident
  Service, incluidos sus tres módulos internos, se profundizó a nivel de componente).
- Modelo de datos físico (tablas, claves, índices): no definido en ningún documento de
  arquitectura todavía.
- Estrategia de reintentos/dead-letter en RabbitMQ: no definida.
- Formato serializado de mensajes (JSON/Protobuf) y versionado de contrato de eventos: no
  definido.
- Clases y paquetes técnicos concretos de Identity & Access, Audit Log y consultas operativas:
  diseño técnico posterior (el ownership y la granularidad ya están fijados, DEC-008).
- Mapeo explícito rol→endpoint (RBAC operacionalizado): pendiente real de implementación, no de
  decisión (R-014, `docs/quality/risk-register.md`), a resolver en Sprint 4.
- Modelo de datos físico (equivalente de un `data-model.md`): no existe todavía; no se referencia
  con un enlace activo hasta que se cree.
- RN-010 y RN-011 sin columna RN propia en `docs/domain/traceability-matrix.md`: documentadas y
  usadas (alimentan RN-012, RN-013), pero no ligadas directamente a un RF en esa tabla —
  observación de formato, no vacío funcional.
- RF-009 sin evento ni criterio de prueba propio en `docs/domain/traceability-matrix.md` (su
  servicio dueño sí quedó resuelto por decisión, DEC-006).
- Evento `AssetRegistered` sin consumidor confirmado: ya asociado a CU-001; falta definir si algún
  componente lo consume.
- Prototipos de `coldguard-frontend`: siguen en estado "Pendiente" — ningún documento afirma que
  estén construidos sin evidencia verificable (enlace, commit).
- Valores de demostración configurables (frecuencia esperada de conectividad RN-020, periodicidad
  de vencimiento de calibración RN-018, umbrales de alertas): sin valor numérico fijado, por
  diseño; se definirán antes de la presentación final como supuestos académicos.

## Enlaces rotos o no verificables

- **Rutas hacia adelante, ahora resueltas**: `system-context.md`, `deployment-view.md` y
  `tech-stack.md` citaban `docs/architecture/architecture-consistency-report.md` antes de que
  este archivo existiera; queda resuelto al crearse este documento.
- **Historial de saneamiento**: los archivos `_legacy`, `migration-report.md` y
  `enrichment-report.md` fueron eliminados deliberadamente (ya no existen en el repositorio, sin
  copia recuperable ni intención de reconstruirlos); sus referencias funcionales ya se limpiaron.
  Detalle histórico completo en "Historial consolidado" (Ronda 5, más abajo).
- **Ronda 5 — corrección de los 12 enlaces rotos preexistentes**: ver tabla dedicada en la sección
  "Ronda 5" más abajo. Tras esa corrección, la validación de enlaces sobre todo `/docs` no reporta
  rutas rotas.
- **Sin enlaces rotos nuevos** introducidos por esta revisión en `docs/architecture/` (incluida la
  Ronda 3: todas las rutas citadas en las secciones nuevas — `docs/planning/decisions-log.md`,
  ADR-003/004/006/007/008 — existen y se verificaron).

## Afirmaciones explícitamente no realizadas

- No se afirma que exista código de ningún servicio.
- No se afirma que Docker Compose, Terraform o LocalStack se hayan ejecutado.
- No se afirma que exista ningún recurso creado en Azure, GitHub Actions en ejecución real, ni
  ningún otro proveedor. Azure está confirmado como proveedor objetivo, no como entorno operativo.
- No se afirma suscripción Azure, Application Insights, Log Analytics, Azure Monitor, Prometheus
  administrado, Grafana administrado ni Key Vault como existentes u operativos.
- No se afirma que Azure Container Apps, Azure Database for PostgreSQL Flexible Server, RabbitMQ
  en Azure o Azure Communication Services Email estén configurados, desplegados o generando costo
  alguno: los cuatro son planificados, no implementados (DEC-002, DEC-007).
- No se afirma observabilidad activa, métricas reales, trazas correlacionadas en producción,
  dashboards activos, alertas activas, ni resultados de pruebas de rendimiento.
- No se afirma un SLA/KPI alcanzado; los valores en `docs/quality/sla-kpi.md` siguen siendo
  objetivos, no mediciones.
- No se afirma que los prototipos de `coldguard-frontend` estén construidos (ver
  `docs/academic/apf1-mapping.md`).
- No se afirma ningún costo cloud, cuenta cloud ni configuración ya aplicada.
- No se afirma que Identity & Access, Audit Log o el módulo de consultas operativas estén
  implementados como código; son ownership documental confirmado (DEC-004, DEC-005, DEC-006), no
  un módulo en ejecución.
- No se afirma que RabbitMQ esté desplegado en Azure Container Apps (DEC-009), ni que Azure Key
  Vault, Managed Identity, permisos o secretos existan (DEC-010): son diseño planificado.

## Nota de cierre del lote

**Fecha:** Por confirmar

El lote de arquitectura queda cerrado con las Rondas 1 a 5 documentadas arriba, sin cambios de
contenido sobre lo ya escrito. A partir de esta nota, cualquier decisión de arquitectura puntual
(nueva plataforma, cambio de ownership, precisión de un ADR, etc.) se registra como entrada nueva
en `docs/planning/decisions-log.md`, no como una ronda adicional de este reporte. Este documento
vuelve a editarse únicamente cuando se cierre un lote completo nuevo que requiera su propia
auditoría de consistencia (por ejemplo, el lote de planificación, el lote de frontend/APF1, o un
lote de operación), y esa reapertura se documentará como una nueva ronda numerada, igual que las
anteriores.
