# Backlog de producto

Backlog derivado de los requisitos, casos de uso y reglas de negocio documentados en
`docs/domain/` y `docs/product/`, dentro del alcance del MVP declarado en
`docs/academic/01-propuesta-proyecto.md` y `docs/product/scope-mvp.md`.

Este backlog nació como cierre documental de APF1 (Sprint 1 y Sprint 2, HU-001 a HU-018) y se
amplió en una ronda de planificación posterior con las historias de implementación de EPIC-12
(ciclo de vida del sensor, HU-019/HU-020) y EPIC-13 (observabilidad, HU-021/HU-022), ya auditadas
para Sprint Planning. El resto de las épicas de Sprint 3 en adelante (EPIC-02 a EPIC-06, EPIC-09,
EPIC-10, EPIC-14) siguen listadas a nivel de épica únicamente, como referencia de alcance futuro,
sin historias detalladas todavía — su detalle se agrega en los cierres de sprint respectivos.

La cobertura RF/CU de cada épica se verifica contra `docs/domain/traceability-matrix.md`, que es
la fuente de verdad para trazabilidad completa (incluye eventos y pruebas futuras); este backlog
no repite esa trazabilidad, solo referencia los IDs relevantes por historia.

## Épicas

| ID | Épica | RF / CU cubiertos | Sprint objetivo |
|---|---|---|---|
| EPIC-01 | Registro de activos y sensores | RF-001, RF-002, RF-010, RF-011 / CU-001, CU-007, CU-011, CU-012 | Sprint 2 (prototipo), Sprint 3+ (implementación, fuera de alcance APF1) |
| EPIC-02 | Ingesta y evaluación de telemetría | RF-003, RF-004 / CU-002 | Sprint 5 (fuera de alcance APF1) |
| EPIC-03 | Gestión de incidentes | RF-005, RF-006, RF-007 / CU-003 a CU-006 | Sprint 2 (prototipo), Sprint 5 (fuera de alcance APF1) |
| EPIC-04 | Notificaciones (adaptador desacoplado; proveedor productivo planificado: Azure Communication Services Email, DEC-007) | RF-008 | Sprint 6 (fuera de alcance APF1) |
| EPIC-05 | Métricas operativas — módulo de consultas operativas dentro de Incident Service (DEC-006) | RF-009 / CU-008 | Sprint 2 (prototipo), Sprint 5+ (fuera de alcance APF1) |
| EPIC-06 | Auditoría — módulo interno Audit Log, dentro de Incident Service (DEC-005, DEC-008) | RF-018, RN-008 / CU-009 | Sprint 2 (prototipo), Sprint 5 (fuera de alcance APF1) |
| EPIC-07 | Fundamentos del proyecto (documentación, arquitectura, riesgos) | — | Sprint 1 |
| EPIC-08 | Prototipos y frontend inicial | — (repositorio `coldguard-frontend`, ADR-002) | Sprint 2 |
| EPIC-09 | Gestión de activos, sensores y perfiles (Asset Service) y asignaciones de acceso (RF-013/CU-014, módulo interno Identity & Access dentro de Incident Service, DEC-004, DEC-008) | RF-012, RF-013 / CU-013, CU-014 | Sprint 3+ (Asset Service), Sprint 5 (Identity & Access) — implementación, fuera de alcance APF1 |
| EPIC-10 | Telemetría de prueba (endpoint interno protegido) | RF-014 / CU-015 | Sprint 5 (fuera de alcance APF1) |
| EPIC-11 | Autenticación y autorización — login demo (APF1) y backend real (APF2) | RF-015 / CU-016, RN-016 | Sprint 2 (prototipo demo, APF1), Sprint 4+ (backend real, APF2, fuera de alcance APF1) |
| EPIC-12 | Ciclo de vida operativo del sensor (estado, calibración, reasignación, retiro, historial) | RF-016, RF-012 / CU-017, CU-018, CU-019, CU-020, CU-021 | Sprint 3+ (implementación, fuera de alcance APF1) |
| EPIC-13 | Observabilidad: instrumentación local (OpenTelemetry, Prometheus, Grafana, Loki) y exportación planificada a Azure Monitor/Application Insights | RNF-002, RNF-004, RNF-008 | Sprint 5 (instrumentación local), Sprint 7 (exportación planificada a Azure, fuera de alcance APF1) |
| EPIC-14 | Detección de pérdida de conectividad de sensor (sin crear incidente automáticamente) | RF-017 / CU-022 | Sprint 5 (fuera de alcance APF1) |

EPIC-12 excluye explícitamente actualización remota de firmware, aprovisionamiento automático en
IoT Hub, gestión de certificados por dispositivo, telemetría de batería física, geolocalización
del dispositivo y comandos bidireccionales a hardware real — ver
`docs/product/scope-mvp.md` ("Capacidades explícitamente fuera de alcance").

## Historias de usuario — Sprint 1 (fundamentos del proyecto)

### HU-001 — Propuesta de proyecto
**Como** equipo de proyecto, **quiero** una propuesta documentada del problema y la solución,
**para** alinear alcance antes de detallar requisitos.
- Criterios de aceptación: `docs/academic/01-propuesta-proyecto.md` describe problema, solución y diferenciador.
- Prioridad: Must have.
- Estimación: completada (documento ya entregado en el cierre de Sprint 1; sin re-estimar).
- Dependencias: ninguna.
- Estado: hecho (documento ya existe).

### HU-002 — Análisis de negocio
**Como** equipo de proyecto, **quiero** actores y proceso de negocio documentados,
**para** tener una base común de dominio.
- Criterios de aceptación: `docs/product/stakeholders.md` lista actores y trazabilidad actor→CU; `docs/domain/domain-model.md` lista el proceso objetivo.
- Prioridad: Must have.
- Estimación: completada (documento ya entregado en el cierre de Sprint 1; sin re-estimar).
- Dependencias: HU-001.
- Estado: hecho.

### HU-003 — Requisitos funcionales y no funcionales
**Como** equipo de proyecto, **quiero** RF/RNF trazables a CU y RN,
**para** poder verificar cobertura de alcance.
- Criterios de aceptación: `functional-requirements.md` y `non-functional-requirements.md` completos, sin IDs reutilizados.
- Prioridad: Must have.
- Estimación: completada (documento ya entregado en el cierre de Sprint 1; sin re-estimar).
- Dependencias: HU-002.
- Estado: hecho. Vacío histórico (CU-009 sin RF asociado) resuelto por decisión: RF-018
  (`docs/planning/decisions-log.md`, DEC-005).

### HU-004 — Casos de uso
**Como** equipo de proyecto, **quiero** CU con trazabilidad a RF, RN y eventos,
**para** especificar el comportamiento esperado del sistema.
- Criterios de aceptación: `use-cases.md` cubre todos los actores de `business-analysis.md`.
- Prioridad: Must have.
- Estimación: completada (documento ya entregado en el cierre de Sprint 1; sin re-estimar).
- Dependencias: HU-003.
- Estado: hecho. El rol Administrador se confirmó como Administrador de plataforma, con CU-014
  (principal) y CU-013 (secundario) — ver `docs/product/stakeholders.md`.

### HU-005 — Reglas de negocio
**Como** equipo de proyecto, **quiero** las reglas de negocio (RN-001 a RN-014) documentadas,
**para** fijar la lógica de dominio antes de implementar.
- Criterios de aceptación: `business-rules.md` incluye la matriz impacto/urgencia (RN-012) completa.
- Prioridad: Must have.
- Estimación: completada (documento ya entregado en el cierre de Sprint 1; sin re-estimar).
- Dependencias: HU-004.
- Estado: hecho.

### HU-006 — Arquitectura C4 y ADRs
**Como** equipo de proyecto, **quiero** el modelo C4 y las decisiones arquitectónicas abiertas resueltas en ADRs,
**para** que la implementación futura no repita decisiones ambiguas.
- Criterios de aceptación: ADR-001 a ADR-009 con las 8 secciones estándar (`.claude/rules/documentation.md`).
- Prioridad: Must have.
- Estimación: completada (documento ya entregado en el cierre de Sprint 1; sin re-estimar).
- Dependencias: HU-004, HU-005.
- Estado: hecho, con nota pendiente (C4 no refleja aún `IS → MQ → NS` según consecuencias de ADR-005).

### HU-007 — Registro de riesgos
**Como** equipo de proyecto, **quiero** los riesgos del proyecto documentados con mitigación,
**para** hacer seguimiento explícito de las decisiones pendientes.
- Criterios de aceptación: `risk-register.md` cubre alcance, complejidad técnica, costos cloud y decisiones arquitectónicas abiertas.
- Prioridad: Must have.
- Estimación: completada (documento ya entregado en el cierre de Sprint 1; sin re-estimar).
- Dependencias: HU-006.
- Estado: hecho, con nota pendiente (R-005 a R-009 ya tienen ADR asociado; revisar si corresponde marcarlos como mitigados).

### HU-008 — SLA y KPI inicial
**Como** equipo de proyecto, **quiero** SLA por prioridad y KPIs candidatos documentados,
**para** tener un punto de partida medible, sin inventar cifras no validadas por negocio.
- Criterios de aceptación: `sla-kpi.md` marca explícitamente qué valores son placeholders académicos.
- Prioridad: Must have.
- Estimación: completada (documento ya entregado en el cierre de Sprint 1; sin re-estimar).
- Dependencias: HU-005.
- Estado: hecho.

### HU-009 — Matriz de trazabilidad
**Como** equipo de proyecto, **quiero** una matriz RF→CU→RN→evento→prueba futura,
**para** verificar cobertura antes de implementar código.
- Criterios de aceptación: `docs/domain/traceability-matrix.md` cubre todos los RF vigentes; vacíos documentales quedan señalados, no inventados.
- Prioridad: Must have.
- Estimación: completada (documento ya entregado en el cierre de Sprint 1; sin re-estimar).
- Dependencias: HU-003, HU-004, HU-005.
- Estado: hecho (este cierre de APF1).

### HU-010 — Backlog de producto
**Como** equipo de proyecto, **quiero** épicas e historias con criterios, prioridad y dependencias,
**para** poder planificar Sprint 1 y Sprint 2 de forma verificable.
- Criterios de aceptación: este documento (`product-backlog.md`).
- Prioridad: Must have.
- Estimación: completada (documento ya entregado en el cierre de Sprint 1; sin re-estimar).
- Dependencias: HU-009.
- Estado: hecho (este cierre de APF1).

### HU-011 — Documentos de Sprint 1 y Sprint 2
**Como** equipo de proyecto, **quiero** el detalle de objetivo, historias comprometidas, DoD, riesgos y entregables por sprint,
**para** cerrar la evidencia de planificación exigida por APF1.
- Criterios de aceptación: `sprint-1.md` y `sprint-2.md` existen y son consistentes con `sprints.md`.
- Prioridad: Must have.
- Estimación: completada (documento ya entregado en el cierre de Sprint 1; sin re-estimar).
- Dependencias: HU-010.
- Estado: hecho (este cierre de APF1).

### HU-012 — Checklist de entregables APF1
**Como** equipo de proyecto, **quiero** verificar cada entregable de `02-entregables-apf1.md` contra su evidencia real,
**para** identificar vacíos antes de la entrega.
- Criterios de aceptación: `docs/academic/apf1-mapping.md` cubre los 9 entregables listados.
- Prioridad: Must have.
- Estimación: completada (documento ya entregado en el cierre de Sprint 1; sin re-estimar).
- Dependencias: HU-001 a HU-011.
- Estado: hecho (este cierre de APF1).

## Historias de usuario — Sprint 2 (prototipos frontend, requisitos detallados y dominio)

> Los prototipos viven en el repositorio `coldguard-frontend` (ADR-002); las historias aquí
> describen el alcance esperado del prototipo, no su implementación, que no corresponde a este repositorio.

### HU-013 — Prototipo de alta de unidad y sensores
**Como** Administrador de plataforma, **quiero** un prototipo navegable de alta de organización/sede/unidad/sensor,
**para** validar el flujo de CU-001 y CU-007 antes de implementar el backend.
- Criterios de aceptación: prototipo sin lógica de negocio real, sin llamadas a servicios backend reales; referencia RF-001/RF-002.
- Prioridad: Should have.
- Estimación: por estimar en Sprint Planning.
- Dependencias: HU-004 (CU-001, CU-007 definidos).
- Sprint: 2.

### HU-014 — Prototipo de tablero de incidentes
**Como** Supervisor de operaciones, **quiero** un prototipo de tablero que muestre incidentes con su prioridad (P1–P4),
**para** validar la usabilidad del flujo de reconocimiento, escalamiento y cierre (CU-004, CU-005, CU-006) antes de implementar.
- Criterios de aceptación: prototipo usa datos de ejemplo estáticos; refleja la matriz impacto/urgencia (RN-012) solo como maqueta visual, sin calcularla; distingue visualmente que reconocer/escalar corresponde al Supervisor de operaciones y cerrar técnicamente al Técnico de mantenimiento, sin implementar lógica de permisos real.
- Prioridad: Should have.
- Estimación: por estimar en Sprint Planning.
- Dependencias: HU-005 (RN-012 definida), HU-013.
- Sprint: 2.

### HU-015 — Prototipo de métricas operativas
**Como** Supervisor de operaciones, **quiero** un prototipo de vista de métricas (MTTA, MTTR, cumplimiento SLA),
**para** validar qué KPIs de `sla-kpi.md` son útiles en pantalla antes de implementar el backend de métricas.
- Criterios de aceptación: prototipo con datos de ejemplo; no implica implementación de agregación real.
- Prioridad: Could have.
- Estimación: por estimar en Sprint Planning.
- Dependencias: HU-008.
- Sprint: 2.

### HU-016 — Prototipo de bitácora de auditoría
**Como** Auditor, **quiero** un prototipo de consulta de bitácora de transiciones,
**para** validar qué campos de auditoría (RN-008) son visibles y consultables.
- Criterios de aceptación: prototipo con datos de ejemplo; sin backend real de auditoría.
- Prioridad: Could have.
- Estimación: por estimar en Sprint Planning.
- Dependencias: HU-005 (RN-008), HU-014.
- Sprint: 2.

### HU-017 — Cerrar el criterio de prueba y evento propio de RF-009

**Re-alcanzada por decisión de Scrum Master** (ver nota abajo): esta historia cubría originalmente
tres vacíos documentales del cierre de APF1 (rol Administrador sin CU, RF-009 sin regla de dominio
propia, eventos no referenciados por ningún CU). Dos de los tres ya se resolvieron por decisión en
rondas posteriores (DEC-002 a DEC-010) y no requieren trabajo de esta historia. El alcance queda
reducido al único vacío real que sigue abierto.

**Como** equipo de proyecto, **quiero** definir el criterio de prueba y, si corresponde, el evento
de dominio asociado a RF-009 (consultar métricas operativas), **para** que
`docs/domain/traceability-matrix.md` deje de marcar RF-009 como fila con vacío de trazabilidad.
- Criterios de aceptación:
  - `docs/domain/traceability-matrix.md` (fila RF-009) deja de decir "no catalogado" en la columna
    de evento, con una decisión explícita (evento de dominio nuevo con el siguiente ID libre, o
    justificación documentada de por qué RF-009 no requiere uno — p. ej. por ser una consulta, no
    un comando).
  - La columna "Prueba futura" de esa misma fila queda con un criterio de prueba concreto y
    verificable, coherente con que RF-009 es atendido por el módulo de consultas operativas dentro
    de Incident Service (DEC-006).
  - No se afirma ninguna implementación de código como parte de esta historia; es una decisión
    documental, igual que su alcance original.
- Prioridad: Should have (ya no bloquea Sprint 3; los vacíos que sí lo hacían quedaron resueltos).
- Estimación: por estimar en Sprint Planning.
- Dependencias: HU-012.
- Sprint: 2 (documental; puede ejecutarse en el refinamiento de cualquier sprint sin backend
  construido todavía, ver `docs/planning/refinement-process.md`).
- Trazabilidad: RF-009; CU-008; DEC-006.

**Nota de resolución (esta ronda)**: el vacío del rol Administrador se resolvió (Administrador de
plataforma, CU-014). CU-009 ya tiene RF asociado (RF-018, DEC-005) y `AssetRegistered` ya está
asociado a CU-001. Por decisión de Scrum Master, la historia se mantiene con el mismo ID (HU-017,
sin renumerar) y se re-alcanza al único vacío real restante, en vez de retirarla — su ID permanece
en el backlog como trazabilidad del hallazgo original.

### HU-018 — Prototipo de login y navegación protegida por rol (demostración APF1)
**Como** cualquiera de los actores humanos (Supervisor de operaciones, Operador, Técnico de
mantenimiento, Auditor, Administrador de plataforma), **quiero** un login funcional de
demostración con navegación protegida por rol, estados de sesión, acceso denegado y cierre de
sesión, **para** validar el flujo de acceso antes de implementar seguridad real en el backend.
- Criterios de aceptación: prototipo en `coldguard-frontend`; usa datos/roles de ejemplo, sin
  backend real de autenticación; **no se presenta como un control de seguridad productivo**
  (RN-016, `docs/product/business-rules.md`); referencia RF-015/CU-016 solo como el destino futuro
  (APF2), no como algo ya implementado.
- Prioridad: Should have.
- Estimación: por estimar en Sprint Planning.
- Dependencias: `docs/product/stakeholders.md` (roles confirmados, incluyendo Administrador de
  plataforma).
- Sprint: 2.

### HU-019 — Gestión de estado, calibración y reasignación de sensores
**Como** Administrador de plataforma, **quiero** cambiar el estado operativo de un sensor,
registrar sus calibraciones/verificaciones y reasignarlo entre activos, **para** mantener
confiable la elegibilidad de sus lecturas para detección de anomalías.
- Criterios de aceptación:
  - Cambiar el estado de un sensor (CU-017) solo permite transiciones válidas según
    `docs/domain/state-machines.md`; toda transición registra usuario, fecha, motivo y valores
    anterior/posterior (RN-017).
  - Un sensor en EN_MANTENIMIENTO no vuelve a ACTIVO sin una calibración/verificación registrada
    explícitamente después de entrar a ese estado (CU-019) y sin el cambio de estado ejecutado
    explícitamente mediante CU-017; registrar la calibración por sí sola no reactiva el sensor.
  - Un sensor en INACTIVO vuelve a ACTIVO sin exigir una calibración/verificación nueva, siempre
    que la vigente no esté vencida; si está vencida, se rechaza.
  - Reasignar un sensor a otro activo (CU-018) solo se permite si el sensor está en
    EN_MANTENIMIENTO; en cualquier otro estado se rechaza. Conserva el historial de asociaciones
    previas, sin sobrescribirlo.
  - La detección de vencimiento de calibración puede ejecutarse manualmente (Administrador de
    plataforma, CU-017) o mediante una tarea programada (actor Sistema); ningún criterio numérico
    de periodicidad/vencimiento se implementa en este MVP sin confirmación explícita (ver `TODO`
    en `docs/product/business-rules.md`, RN-018).
- Prioridad: Must have (RN-017/RN-018 son reglas de dominio, no un prototipo visual).
- Estimación: por estimar; división aprobada, se ejecuta en el refinamiento previo a Sprint 3, no
  en esta ronda (`docs/planning/refinement-process.md`).
- Dependencias: HU-005 (reglas de negocio), `docs/domain/use-cases.md` (CU-017, CU-018, CU-019).
- Sprint: 3+ (implementación; fuera de alcance de APF1, igual que el resto de EPIC-12).
- **Decisión de Scrum Master (esta ronda)**: se aprueba dividir esta historia en tres al momento
  del refinamiento de Sprint 3 (HU-019a: cambio de estado, CU-017; HU-019b: calibración/
  verificación, CU-019; HU-019c: reasignación, CU-018) — ver plantilla en
  `docs/planning/definition-of-ready.md` §3. No se ejecuta la división ahora, para no detallar
  historias fuera del sprint N+1.

### HU-020 — Retiro lógico y consulta de historial de sensores
**Como** Administrador de plataforma, **quiero** retirar lógicamente un sensor y consultar su
historial técnico y administrativo, **para** dar de baja sensores sin perder trazabilidad.
- Criterios de aceptación:
  - Retirar un sensor (CU-020) lo deja en estado RETIRADO (terminal para el MVP) sin eliminar
    físicamente sus lecturas, eventos, incidentes ni historial de asociaciones (RN-017).
  - Un sensor con historial de lecturas, eventos o incidentes no admite eliminación física bajo
    ninguna operación de este backlog.
  - Consultar el historial de un sensor (CU-021) muestra cambios de estado, calibraciones,
    reasignaciones y retiros, cada uno con usuario, fecha, motivo y valores anterior/posterior.
- Prioridad: Should have.
- Estimación: por estimar; división aprobada, se ejecuta en el refinamiento previo a Sprint 3, no
  en esta ronda (`docs/planning/refinement-process.md`).
- Dependencias: HU-019.
- Sprint: 3+ (implementación; fuera de alcance de APF1).
- **Decisión de Scrum Master (esta ronda)**: se aprueba dividir esta historia en dos al momento
  del refinamiento de Sprint 3 (HU-020a: retiro lógico, CU-020, comando; HU-020b: consulta de
  historial, CU-021, consulta de solo lectura) — ver plantilla en
  `docs/planning/definition-of-ready.md` §3. No se ejecuta la división ahora.

### HU-021 — Instrumentación local de observabilidad
**Como** equipo de proyecto, **quiero** instrumentar los servicios backend con OpenTelemetry y
exponer trazas, métricas y logs estructurados hacia el stack local (Prometheus, Grafana, Loki),
**para** poder diagnosticar el sistema durante el desarrollo sin acoplar el código a un proveedor
cloud.
- Criterios de aceptación:
  - Cada servicio backend emite trazas correlacionables (`traceId`, `spanId`) y logs
    estructurados en JSON con `correlationId`, mediante OpenTelemetry (RNF-002, RNF-004).
  - Métricas de aplicación y de dependencias (PostgreSQL, RabbitMQ) visibles en Prometheus/
    Grafana en el entorno local.
  - Los eventos de negocio mínimos (`IncidentCreated`, `IncidentAcknowledged`,
    `IncidentEscalated`, `IncidentClosed`, `SensorConnectivityLost`) quedan registrados con
    nombre, timestamp e identificadores correlacionables, sin datos sensibles (RNF-008,
    `docs/operations/observability-strategy.md`).
  - Ningún token, credencial, cadena de conexión, secreto o payload completo de telemetría
    aparece en logs, trazas o eventos de negocio.
  - Esta historia no se marca como terminada hasta que exista evidencia verificable (código y
    capturas), no solo diseño documental.
- Prioridad: Should have.
- Estimación: por estimar; división recomendada (por servicio o por señal), **no aprobada ni
  ejecutada en esta ronda** — se decide en el refinamiento previo a Sprint 5, no antes
  (`docs/planning/refinement-process.md`), por ser fuera del sprint N+1 en este cierre.
- Dependencias: Telemetry Service e Incident Service construidos en este mismo Sprint 5 (ver
  `docs/planning/roadmap.md`), no en una historia previa detallada — esta historia no puede
  iniciarse antes de que ambos servicios expongan al menos un endpoint/flujo funcional dentro del
  propio Sprint 5.
- Sprint: 5 (instrumentación local; fuera de alcance de APF1).
- **Decisión de Scrum Master (esta ronda)**: a diferencia de HU-019/HU-020 (Sprint 3), la
  división de esta historia (Sprint 5) queda explícitamente diferida — esta ronda no detalla
  trabajo de Sprint 5, 6 ni 7 por adelantado. La recomendación de dividir por servicio o por señal
  queda registrada para el refinamiento previo a Sprint 5.

## Historias de usuario — EPIC-03 (gestión de incidentes)

### HU-023 — Crear incidentes automáticamente con prioridad calculada
**Como** Sistema, **quiero** crear un incidente automáticamente con impacto/urgencia/prioridad
calculados a partir de la criticidad del activo y la magnitud de la anomalía, **para** priorizar
la atención sin intervención manual.
- Criterios de aceptación:
  - Una solicitud de creación con activo, sensor, tipo de anomalía, criticidad del activo y
    magnitud calcula impacto, urgencia y prioridad (matriz 4×4, RN-012) y crea el incidente con
    estado CREATED.
  - La respuesta incluye identificador, estado, impacto, urgencia, prioridad y fecha de creación.
  - La entrada es un endpoint técnico/interno (DEC-012), no la integración real con Telemetry
    Service, que no existe todavía.
- Prioridad: Must have.
- Estimación: pendiente de normalización retrospectiva; implementación y tests existentes.
- Dependencias: RN-012 (matriz impacto/urgencia, `docs/product/business-rules.md`, documentada
  como parte de HU-005) — dependencia documental, no funcional.
- Sprint: implementación adelantada, sin sprint formal asociado. Se propone Sprint 5 como sprint
  de regularización (registrar retroactivamente su cierre), **pendiente de decisión explícita**
  del Product Owner/Scrum Master — no se asume Sprint 5 como comprometido para esto.
- Estado técnico: implementada y testeada (`CreateIncidentService`, `PriorityCalculator`,
  `IncidentController`; `CreateIncidentServiceTest`, `IncidentGrpcServiceTest`,
  `IncidentControllerTest`, `IncidentGrpcClientTest`).
- Estado académico: no evidenciada (ningún documento de `docs/academic/` la referencia todavía).

### HU-024 — Rechazar incidentes duplicados mientras uno esté abierto
**Como** Sistema, **quiero** rechazar una solicitud de creación si ya existe un incidente abierto
para la misma combinación de activo, sensor y tipo de anomalía, **para** evitar duplicados
(RN-004).
- Criterios de aceptación:
  - Una segunda solicitud equivalente mientras el primer incidente sigue en CREATED es rechazada,
    devolviendo el identificador del incidente existente.
  - La unicidad se garantiza a nivel de base de datos (índice único), incluso ante solicitudes
    concurrentes.
  - Un incidente en estado CLOSED no bloquea una nueva solicitud equivalente.
- Prioridad: Must have.
- Estimación: pendiente de normalización retrospectiva; implementación y tests existentes.
- Dependencias: HU-023.
- Sprint: implementación adelantada, sin sprint formal asociado — mismo tratamiento que HU-023.
- Estado técnico: implementada y testeada (`V1`/`V2` migraciones, `DuplicateIncidentException`,
  `IncidentAlreadyOpenException`; `IncidentRepositoryAdapterTest` incl. concurrencia,
  `CreateIncidentServiceTest`, `IncidentControllerTest.createIncident_alreadyExists_returns409`).
- Estado académico: no evidenciada.

### HU-025 — Modelar el ciclo de vida mínimo del incidente (CREATED → CLOSED)
**Como** Sistema, **quiero** representar que un incidente puede pasar de creado a cerrado, **para**
tener una base de estado sobre la cual ejecutar el cierre técnico real (RN-007, RN-019).
- Criterios de aceptación:
  - El estado de negocio del incidente admite únicamente CREATED y CLOSED.
  - Un incidente CREATED transiciona a CLOSED una sola vez; un segundo intento sobre un incidente
    ya CLOSED es rechazado.
  - Los eventos ya catalogados `IncidentAcknowledged` e `IncidentEscalated`
    (`docs/domain/commands-events.md`) no se traducen en esta historia a valores del enum de
    estado; su eventual representación como estado, si llegara a decidirse, queda fuera de este
    alcance.
  - El valor `INCIDENT_STATUS_UNSPECIFIED = 0` del enum `IncidentStatus` en
    `contracts/grpc/incident_service.proto` es el valor centinela técnico por defecto de proto3
    (todo enum de protobuf requiere un primer valor por defecto/no seteado); no representa un
    tercer estado de negocio del incidente.
- Prioridad: Must have.
- Estimación: pendiente de normalización retrospectiva; implementación y tests existentes.
- Dependencias: HU-023.
- Sprint: implementación adelantada, sin sprint formal asociado — mismo tratamiento que HU-023.
- Estado técnico: implementada y testeada (`IncidentStatus`, `Incident.close()`; `IncidentTest`,
  `IncidentGrpcServiceTest.toGrpcStatus_mapsClosed`).
- Estado académico: no evidenciada.

### HU-026 — Cerrar un incidente (Técnico de mantenimiento) — parcial
**Como** Técnico de mantenimiento, **quiero** cerrar técnicamente un incidente, **para** detener
el MTTR y dejar registrado el diagnóstico, causa y comentario de resolución (RN-007, RN-019).
- Criterios de aceptación, separados por lo que ya existe y lo que falta:
  - **Dominio/aplicación — implementado**: dado un incidente en CREATED, cerrarlo lo deja en
    CLOSED; cerrar un incidente inexistente o ya CLOSED es rechazado explícitamente
    (`CloseIncidentService`, `IncidentAlreadyClosedException`, `IncidentNotFoundException`).
  - **RPC gRPC / endpoint REST — pendiente**: no existe ningún RPC `CloseIncident` en
    `contracts/grpc/incident_service.proto` ni endpoint en el Gateway (DEC-014).
  - **JWT/RBAC — pendiente**: no se valida que el actor sea el Técnico de mantenimiento; requiere
    JWT/RBAC real (Sprint 4, sin implementar).
  - **Causa/comentario de resolución (RN-007) — pendiente**: no se exige ni persiste en la capa
    hoy implementada.
  - **Evidencia académica — pendiente**: ningún documento de `docs/academic/` la referencia.
  - Esta historia **no se marca como completa** mientras falte cualquiera de los cuatro puntos
    pendientes de arriba.
- Prioridad: Must have.
- Estimación: por estimar; depende de que exista JWT/RBAC (Sprint 4).
- Dependencias: HU-025; JWT/RBAC (Sprint 4, sin HU propia todavía).
- Sprint: parcialmente implementado, fuera de sprint formal; el RPC/REST/JWT/RBAC restante se
  ubicaría en Sprint 4/5 según se decida — no confirmado.
- Estado técnico: **parcial** (ver desglose arriba); test existente: `CloseIncidentServiceTest`.
- Estado académico: no evidenciada.

### HU-022 — Exportación planificada de observabilidad a Azure
**Como** equipo de proyecto, **quiero** configurar la exportación de OpenTelemetry hacia
Application Insights/Azure Monitor y Azure Monitor Logs/Log Analytics, **para** contar con
observabilidad centralizada en el entorno cloud planificado, sin duplicar deliberadamente toda
la telemetría local.
- Criterios de aceptación:
  - El exportador de OpenTelemetry puede configurarse hacia Application Insights/Azure Monitor
    sin cambios en la instrumentación de negocio (HU-021).
  - Azure Monitor Logs/Log Analytics queda documentado como destino previsto para consulta
    operativa de logs cloud.
  - No se afirma suscripción Azure operativa, dashboards activos ni alertas activas como parte
    de esta historia; el alcance es la configuración planificada, no la ejecución.
  - Alertas iniciales candidatas (error rate, dependencia no disponible, latencia degradada,
    fallo de publicación/consumo de mensajería, ausencia de telemetría) quedan documentadas sin
    umbrales numéricos (`docs/operations/observability-strategy.md`).
- Prioridad: Could have.
- Estimación: por estimar en Sprint Planning.
- Dependencias: HU-021.
- Sprint: 7 (exportación planificada a Azure; fuera de alcance de APF1).
