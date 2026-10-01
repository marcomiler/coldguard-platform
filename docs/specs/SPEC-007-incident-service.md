# SPEC-007 — Incident Service (completar) + módulos Audit Log y consultas operativas

## Objetivo

Completar el ciclo de vida del incidente (creación automática desde telemetría, actualización por
persistencia, reconocimiento, escalamiento, cierre con evidencia persistida), cálculo de SLA,
solicitud de notificaciones, y los módulos internos **Audit Log** (RF-018) y **consultas
operativas** (RF-009). Identity & Access se especifica en SPEC-004.

## Trazabilidad

RF-005, RF-006, RF-007, RF-008 (solicitud), RF-009, RF-018; CU-003 a CU-006, CU-008, CU-009;
RN-003 a RN-014, RN-019; ADR-005, ADR-006, ADR-009; DEC-005, DEC-006, DEC-008, DEC-012, DEC-013,
DEC-014; `docs/quality/sla-kpi.md`, `docs/quality/acceptance-criteria.md` (RF-006),
`docs/operations/incident-model.md`; HU-023 a HU-026.

## Estado actual verificado (y deuda a corregir)

| Hallazgo | Ubicación | Acción en este spec |
|---|---|---|
| `cause`/`resolutionComment` validados pero **descartados**; `closed_at` de la respuesta es `Instant.now()` no persistido | `CloseIncidentService`, `IncidentGrpcService.toCloseResponse` | Persistir (migración V3) |
| Sin `@Transactional`; `findById` + `update` no atómicos; `update()` crea entidad nueva (sin control de concurrencia) | `CloseIncidentService`, `IncidentRepositoryAdapter.update` | Transacción en caso de uso; cargar entidad gestionada y modificarla; `@Version` |
| Mapeo dominio↔entidad duplicado en `save` y `update` | `IncidentRepositoryAdapter` | `IncidentPersistenceMapper` único |
| Manejo de excepciones manual en cada RPC **y** handler registrado como bean; MDC por método | `IncidentGrpcService` | Solo el handler global; correlación por interceptor (SPEC-001) |
| `PriorityCalculator` es lógica de dominio pura ubicada en `application` | `application/PriorityCalculator` | Mover a `domain` (`PriorityPolicy`) sin cambiar su comportamiento |
| Rol comparado como string `"ROLE_MAINTENANCE_TECHNICIAN"` | `CloseIncidentService` | `Actor` + `Role` (SPEC-004) |
| Índice de unicidad solo cubre `status = 'CREATED'` | `V2__restrict_unique_open_incident.sql` | Cubrir todo estado abierto (V4) |
| Sin Outbox, sin eventos, sin consumidor | — | SPEC-003 + este spec |

## Decisiones requeridas

- **D-04 — Estados y guards (propuesta, amplía DEC-013)**:
  - Estados: `CREATED`, `ACKNOWLEDGED`, `ESCALATED`, `CLOSED`. "Abierto" = cualquiera distinto
    de `CLOSED` (RN-004).
  - `acknowledge`: solo si `acknowledged_at IS NULL` y no cerrado; registra `acknowledged_at/by`;
    el estado pasa a `ACKNOWLEDGED` solo si estaba en `CREATED` (si ya estaba `ESCALATED`, se
    conserva `ESCALATED`). Detiene el reloj de reconocimiento (RN-006).
  - `escalate`: desde cualquier estado abierto, repetible ("opcional/recurrente",
    `state-machines.md`); incrementa `escalation_count`, actualiza `last_escalated_at`; motivo
    obligatorio. Nunca automático (CU-005).
  - `close`: desde cualquier estado abierto; exige causa y comentario (RN-007) y rol Técnico
    (RN-019). Cerrar sin reconocimiento previo **no** está prohibido por la documentación; el
    incidente queda excluido del cálculo de MTTA.
- **Actualización por persistencia (RN-005/RN-014, propuesta)**: un `TelemetryThresholdBreached`
  equivalente (mismo activo/sensor/tipo) con incidente abierto **actualiza** el incidente:
  `occurrence_count + 1`, `last_occurrence_at`; la urgencia se recalcula y se conserva la mayor
  (no se desescala automáticamente); si la prioridad cambia, se recalculan `ack_due_at` y
  `resolve_due_at` desde `created_at` (el SLA inicia al crear, RN-006) y se audita
  `PRIORITY_RECALCULATED` (RN-014). No se emite evento (no catalogado).
- **Recálculo por cambio de criticidad del activo (RN-014)**: requiere consumir `AssetUpdated`;
  **fuera de alcance** de este spec salvo decisión explícita.
- **Mapeo magnitud/persistencia → urgencia**: se conserva el mapeo actual de `PriorityCalculator`
  (marcado en el propio código como placeholder no confirmado). Confirmar con el PO (D-12).
- **SLA P4**: `sla-kpi.md` define "revisión ≤ 1 día hábil" y sin objetivo de resolución. Propuesta:
  `ack` P4 configurable (placeholder `P1D`, aproximación de "día hábil" sin calendario laboral) y
  `resolve_due_at = NULL`. Confirmar.
- **D-09** Audit Log alimentado por eventos + escritura in-process; **D-10** destinatarios;
  **D-15** endpoints de consulta.

## 1. Persistencia (esquema `incident`)

```sql
-- V3__incident_lifecycle.sql
ALTER TABLE incident.incident
  ADD COLUMN acknowledged_at TIMESTAMPTZ NULL, ADD COLUMN acknowledged_by VARCHAR(100) NULL,
  ADD COLUMN last_escalated_at TIMESTAMPTZ NULL, ADD COLUMN escalation_count INT NOT NULL DEFAULT 0,
  ADD COLUMN closed_at TIMESTAMPTZ NULL, ADD COLUMN closed_by VARCHAR(100) NULL,
  ADD COLUMN cause VARCHAR(500) NULL, ADD COLUMN resolution_comment VARCHAR(2000) NULL,
  ADD COLUMN ack_due_at TIMESTAMPTZ NULL, ADD COLUMN resolve_due_at TIMESTAMPTZ NULL,
  ADD COLUMN occurrence_count INT NOT NULL DEFAULT 1, ADD COLUMN last_occurrence_at TIMESTAMPTZ NULL,
  ADD COLUMN last_magnitude VARCHAR(10) NULL, ADD COLUMN persistent BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN source_reading_id UUID NULL,
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
-- NOT VALID: no se validan filas CLOSED antiguas creadas antes de persistir la evidencia
ALTER TABLE incident.incident ADD CONSTRAINT ck_incident_closed_evidence
  CHECK (status <> 'CLOSED' OR (cause IS NOT NULL AND resolution_comment IS NOT NULL AND closed_at IS NOT NULL)) NOT VALID;

-- V4__unique_open_incident_any_open_status.sql
DROP INDEX incident.ux_incident_open_created;
CREATE UNIQUE INDEX ux_incident_open ON incident.incident (asset_id, sensor_id, anomaly_type) WHERE status <> 'CLOSED';
CREATE INDEX ix_incident_status_priority_created ON incident.incident (status, priority, created_at DESC);
CREATE INDEX ix_incident_created ON incident.incident (created_at);
```

+ `outbox_event`, `processed_message` (SPEC-003). Las migraciones `V1`/`V2` no se tocan.

## 2. Dominio (`com.coldguard.incident.domain`)

- `Incident` pasa a modelo con comportamiento: `acknowledge(actor, clock)`,
  `escalate(actor, reason, clock)`, `close(actor, cause, comment, clock)`,
  `registerOccurrence(magnitude, persistent, priorityPolicy, slaPolicy, clock)`; cada método
  valida guards (D-04) y devuelve el nuevo estado + eventos de dominio
  (`sealed interface IncidentEvent`).
- `PriorityPolicy` (antes `PriorityCalculator`, comportamiento idéntico): criticidad→impacto
  (RN-013), magnitud/persistencia→urgencia, matriz 4×4 (RN-012).
- `SlaPolicy` (puerto con implementación basada en configuración): `ackDueAt(priority,
  createdAt)`, `resolveDueAt(priority, createdAt)` (puede ser vacío para P4).
- Validación RN-007 en el dominio (`close`): causa y comentario no vacíos y con longitud máxima.
- RN-019 se valida en `application` (autorización de actor), no en el dominio (DEC-014).

## 3. Casos de uso (`application`, todos `@Transactional`)

| Caso | Origen | Efectos (misma transacción) |
|---|---|---|
| `HandleThresholdBreach` | consumidor `incident-service.telemetry-threshold-breached` | Si no hay abierto equivalente → crear (impacto, urgencia, prioridad, SLA) + `IncidentCreated` + `NotificationRequested(INCIDENT_CREATED)` + auditoría `CREATED`. Si hay abierto → `registerOccurrence` + auditoría (`OCCURRENCE_REGISTERED`, `PRIORITY_RECALCULATED` si aplica). Carrera con el índice único → reintentar una vez por la rama de actualización en transacción nueva |
| `CreateIncident` (técnico, DEC-012) | gRPC `CreateIncident` | Igual que la rama "crear"; actor = usuario autenticado |
| `AcknowledgeIncident` | gRPC | Rol `OPERATIONS_SUPERVISOR`; `IncidentAcknowledged`; auditoría |
| `EscalateIncident` | gRPC | Rol `OPERATIONS_SUPERVISOR`; motivo obligatorio; `IncidentEscalated` + `NotificationRequested(INCIDENT_ESCALATED)`; auditoría |
| `CloseIncident` | gRPC | Rol `MAINTENANCE_TECHNICIAN` (RN-019); persiste causa/comentario/`closed_at`/`closed_by`; `IncidentClosed`; auditoría |
| `GetIncident`, `ListIncidents` | gRPC (readOnly) | Proyección DTO; filtros de SPEC-002; paginación con tamaño máximo |

Destinatarios (D-10, placeholder académico): `NotificationRequested` se construye consultando la
API de aplicación de Identity & Access (`ListUserContacts(role)`) **en el mismo proceso**; si no
hay destinatarios habilitados, no se emite el evento, se registra un warning sin datos personales
y se incrementa la métrica `coldguard.notification.recipients.missing`.

Idempotencia del consumidor: `processed_message` (SPEC-003) + `source_reading_id` para trazar el
origen. Errores permanentes (payload inválido, criticidad/magnitud desconocidas) → DLQ.

## 4. Módulo Audit Log (`com.coldguard.incident.auditlog`, esquema `auditlog`)

```sql
CREATE TABLE auditlog.audit_record (
  id UUID PRIMARY KEY,
  occurred_at TIMESTAMPTZ NOT NULL, recorded_at TIMESTAMPTZ NOT NULL,
  source_service VARCHAR(40) NOT NULL, entity_type VARCHAR(40) NOT NULL, entity_id VARCHAR(100) NOT NULL,
  action VARCHAR(60) NOT NULL,
  actor_type VARCHAR(10) NOT NULL, actor_id VARCHAR(100) NOT NULL,
  reason VARCHAR(500) NULL, previous_value JSONB NULL, new_value JSONB NULL,
  correlation_id VARCHAR(100) NULL, source_event_id UUID NULL);
CREATE UNIQUE INDEX ux_audit_source_event ON auditlog.audit_record (source_event_id) WHERE source_event_id IS NOT NULL;
CREATE INDEX ix_audit_entity ON auditlog.audit_record (entity_type, entity_id, occurred_at DESC, id DESC);
CREATE INDEX ix_audit_actor  ON auditlog.audit_record (actor_id, occurred_at DESC, id DESC);
CREATE INDEX ix_audit_time   ON auditlog.audit_record (occurred_at DESC, id DESC);
```

+ `auditlog.processed_message`. Flyway del servicio gestiona `incident`, `identity` y `auditlog`
(`spring.flyway.schemas`), cada módulo con su carpeta de migraciones
(`db/migration/incident`, `db/migration/identity`, `db/migration/auditlog`) para no mezclar
ownership — **verificar** que el orden de versiones entre carpetas no colisione (usar prefijos o
rangos de versión distintos, o instancias Flyway separadas por esquema).

- **Escritura in-process** (Incident, Identity): `AuditRecorder.record(AuditEntry)` en la misma
  transacción que el cambio.
- **Escritura por eventos** (D-09): cola `incident-service.audit` (bindings de SPEC-002); un
  `AuditEventMapper` por tipo de evento traduce envelope → `AuditEntry` (actor, motivo, valores
  anterior/posterior desde el payload); `source_event_id` evita duplicados. Eventos sin mapeo
  explícito → DLQ (no se pierden ni se adivinan).
- **Inmutable desde la aplicación**: el repositorio no expone `update`/`delete`. No se declara
  WORM ni retención legal (DEC-005).
- **Consulta (RF-018/CU-009)**: `ListAuditRecords` solo lectura, rol `AUDITOR`, filtros
  (entidad, actor, acción, rango de fechas obligatorio con amplitud máxima configurable), cursor
  `(occurred_at, id)`.
- Nunca se guardan contraseñas, hashes ni tokens en `previous_value`/`new_value` (los cambios de
  usuario registran solo roles/estado habilitado).

## 5. Módulo consultas operativas (`com.coldguard.incident.metrics`, RF-009/CU-008)

- `GetIncidentMetrics(from, to)` en una sola consulta SQL de agregación (cláusulas `FILTER`) sobre
  `incident.incident` con índice por `created_at`, sin cargar entidades:
  - Conteo por estado y por prioridad de incidentes creados en el rango.
  - MTTA: promedio `acknowledged_at - created_at` (solo reconocidos).
  - MTTR: promedio `closed_at - created_at` (solo cerrados).
  - Cumplimiento SLA por prioridad: `% acknowledged_at <= ack_due_at` y
    `% closed_at <= resolve_due_at` (excluye `resolve_due_at IS NULL`).
- Rango obligatorio con amplitud máxima configurable. Rol `OPERATIONS_SUPERVISOR`.
- Los objetivos agregados (MTTA/MTTR globales) **no** se comparan contra cifras inventadas
  (`sla-kpi.md`): la consulta devuelve valores medidos, no juicios de cumplimiento global.
- HU-017 (evento de RF-009): RF-009 es una consulta, no un comando → no requiere evento;
  documentarlo en `traceability-matrix.md`.

## 6. API gRPC y errores

`IncidentGrpcService` (v1 extendido), `IdentityGrpcService` (SPEC-004), `AuditLogGrpcService`,
`OperationalMetricsGrpcService`: solo mapeo vía mappers dedicados. Handler único:
`IncidentAlreadyOpen` → `ALREADY_EXISTS` (+ trailer `existing-incident-id`, ya existente);
`IncidentNotFound` → `NOT_FOUND`; `IncidentAlreadyClosed`, `AlreadyAcknowledged` →
`FAILED_PRECONDITION` (+ `x-error-code`); `ActorNotAuthorized` → `PERMISSION_DENIED`;
validación → `INVALID_ARGUMENT`; conflicto de versión → `ABORTED`.

## 7. Configuración

```yaml
spring.grpc.server.port: ${GRPC_SERVER_PORT:9093}
spring.flyway.schemas: incident,identity,auditlog
coldguard.incident:
  sla:                       # placeholders académicos (sla-kpi.md)
    P1: { ack: PT5M,  resolve: PT30M }
    P2: { ack: PT15M, resolve: PT2H }
    P3: { ack: PT1H,  resolve: PT8H }
    P4: { ack: P1D }          # aproximación de "1 día hábil"; sin objetivo de resolución
  page.max-size: 100
coldguard.auditlog.query.max-range: 31d
coldguard.metrics.query.max-range: 93d
```

## 8. Gateway (incremental)

`GET /api/v1/incidents`, `GET /api/v1/incidents/{id}`,
`POST /api/v1/incidents/{id}/acknowledgement`, `POST /api/v1/incidents/{id}/escalation`
(`{ "reason" }`), `POST /api/v1/incidents/{id}/close` (existente; respuesta con `closedAt`
persistido), `POST /api/v1/incidents` (técnico, detrás de propiedad),
`GET /api/v1/metrics/incidents?from&to`, `GET /api/v1/audit-records?…`, rutas de usuarios
(SPEC-004).

## Criterios de aceptación (validación local manual)

Derivados de `acceptance-criteria.md` (RF-006) y HU-023 a HU-026:

1. Una lectura fuera de rango (simulador o CU-015) crea **un** incidente CREATED con impacto,
   urgencia, prioridad y `ack_due_at`/`resolve_due_at`; aparecen `IncidentCreated` y
   `NotificationRequested`.
2. Nuevas lecturas equivalentes mientras está abierto **no** crean otro incidente: incrementan
   `occurrence_count`; si la persistencia sube la urgencia y cambia la prioridad, queda auditado
   el recálculo.
3. Redelivery del mismo `TelemetryThresholdBreached` no altera `occurrence_count` dos veces.
4. Reconocer con Supervisor → `ACKNOWLEDGED`, `IncidentAcknowledged`; segundo reconocimiento →
   409; con otro rol → 403.
5. Escalar con Supervisor (motivo obligatorio) → `ESCALATED`, `IncidentEscalated`,
   `NotificationRequested(INCIDENT_ESCALATED)` dirigido a técnicos; repetible.
6. Cerrar con Técnico → `CLOSED` con causa, comentario, `closed_at`, `closed_by` persistidos y
   devueltos por `GET /incidents/{id}`; sin causa/comentario → 400; con otro rol → 403; ya
   cerrado → 409.
7. Tras cerrar, una nueva anomalía equivalente crea un incidente nuevo (RN-004 solo aplica a
   abiertos).
8. `GET /api/v1/audit-records` (Auditor) muestra creación, reconocimiento, escalamiento, cierre,
   cambios de sensor (desde Asset) y `SensorConnectivityLost`, cada uno con actor, fecha y
   motivo; con otro rol → 403.
9. `GET /api/v1/metrics/incidents` devuelve conteos, MTTA, MTTR y cumplimiento SLA coherentes
   con los incidentes de la demo.

## Tareas

1. Registrar D-04, D-09, D-10, D-15 y las decisiones de este spec.
2. Migraciones V3/V4 + esquemas `auditlog`/`identity` (organización de carpetas Flyway).
3. Refactor de deuda (tabla inicial) sin cambiar el comportamiento existente.
4. Dominio ampliado + `SlaPolicy`.
5. Casos de uso + consumidor de umbral + Outbox.
6. Módulo Audit Log (escritor, consumidor, consulta).
7. Módulo de métricas.
8. Contratos/endpoints gRPC + rutas del Gateway.
9. Actualizar `state-machines.md` (máquina del incidente con D-04), `commands-events.md`,
   `traceability-matrix.md`, `component-diagram.md`, HU-026 (deja de ser parcial).

## Riesgos

- D-04 cambia el significado de "abierto" en el índice único: la migración V4 debe ejecutarse en
  la misma versión que el código que entiende los nuevos estados.
- La cola de auditoría concentra eventos de todos los contextos: monitorear su profundidad
  (SPEC-011).
