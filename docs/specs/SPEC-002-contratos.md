# SPEC-002 — Contratos gRPC y catálogo físico de eventos

## Objetivo

Definir, antes de implementar los servicios, **todos los contratos internos** del MVP: servicios
gRPC versionados (ADR-003, RNF-006) y el formato físico de los eventos de dominio sobre RabbitMQ
(ADR-004, ADR-005, ADR-009), cerrando el TODO "payload físico/serializado final" de
`docs/domain/commands-events.md`.

## Trazabilidad

RNF-006; ADR-003, ADR-004, ADR-005, ADR-009; DEC-003, DEC-012, DEC-013;
`docs/domain/commands-events.md`, `docs/architecture/event-flow.md`.

## Estado actual verificado

- Existe un único contrato: `contracts/grpc/incident_service.proto`, paquete
  `com.coldguard.incident.v1`, `java_package com.coldguard.incident.grpc.v1`, RPC
  `CreateIncident` y `CloseIncident`; timestamps como `string` ISO-8601.
- No existe ningún contrato de eventos; DEC-012 fijó "JSON simple versionado (campo
  `eventVersion`)".

## Decisiones que este spec asume (ver `README.md`)

D-03 (identidad en metadata), D-04 (estados de incidente), D-05 (`TelemetryThresholdBreached` por
RabbitMQ), D-06 (productor de `SensorConnectivityLost`), D-07 (`GetSensorEvaluationContext`),
D-14 (eventos de actualización). Además:

- **D-17 (nueva)**: `TelemetryReceived` **no se publica en RabbitMQ**. Su único consumidor
  catalogado es el propio Telemetry Service; se materializa como la persistencia de la lectura y
  un contador Micrometer (KPI "lecturas por minuto", `sla-kpi.md`). Publicar cada lectura al
  broker multiplicaría el tráfico sin consumidor. Registrar como nota en `commands-events.md`.

## 1. Organización de `contracts/`

```
contracts/
  grpc/
    common/v1/common.proto            # paginación, enums compartidos (Criticality)
    asset/v1/asset_service.proto
    telemetry/v1/telemetry_service.proto
    incident/v1/incident_service.proto  # movido desde contracts/grpc/incident_service.proto
    identity/v1/identity_service.proto  # servido por incident-service
    audit/v1/audit_log_service.proto    # servido por incident-service
    metrics/v1/operational_metrics_service.proto  # servido por incident-service
  events/
    README.md                         # convenciones de envelope, routing, versionado
    asset/*.v1.schema.json
    telemetry/*.v1.schema.json
    incident/*.v1.schema.json
    notification/*.v1.schema.json
```

- Mover `incident_service.proto` a `incident/v1/` **no cambia** paquete proto ni `java_package`:
  es compatible para clientes (ADR-003). Ajustar `protoSourceRoot` (SPEC-001).
- Convención: `package com.coldguard.<context>.v1;`, `option java_package =
  "com.coldguard.<context>.grpc.v1"; option java_multiple_files = true;`.
- **Nuevos** contratos usan `google.protobuf.Timestamp` para instantes; el contrato v1 existente de
  Incident conserva `string` ISO-8601 en los campos ya publicados (cambiar el tipo sería
  incompatible). Los campos nuevos que se agreguen a Incident v1 usan `Timestamp`.
- Reglas de evolución (documentar en `contracts/grpc/README.md`): nunca reutilizar ni renumerar un
  tag; campos eliminados se marcan `reserved`; agregar valores de enum al final; cambios
  incompatibles → paquete `v2`.

## 2. Tipos comunes (`common/v1/common.proto`)

```proto
message PageRequest { int32 page = 1; int32 size = 2; }          // page base 0
message PageInfo   { int32 page = 1; int32 size = 2; int64 total_elements = 3; int32 total_pages = 4; }
message CursorPageRequest { string cursor = 1; int32 size = 2; } // para tablas de alto volumen
message CursorPageInfo    { string next_cursor = 1; bool has_more = 2; }
enum Criticality { CRITICALITY_UNSPECIFIED = 0; CRITICALITY_LOW = 1; CRITICALITY_MEDIUM = 2; CRITICALITY_HIGH = 3; CRITICALITY_CRITICAL = 4; }
```

- `size` máximo validado por el servidor (p. ej. 100, configurable); `size = 0` → default.
- `total_elements` solo en listados de bajo volumen (activos, sensores, usuarios, incidentes);
  lecturas y auditoría usan cursor (keyset) para evitar `COUNT(*)` y `OFFSET` costosos.
- `Criticality` ya existe en Incident v1 con los mismos valores; Incident v1 mantiene su propio
  enum (no se rompe), los contratos nuevos importan el común.

## 3. Asset Service (`asset/v1/asset_service.proto`)

Servidor: asset-service. Clientes: Gateway, Telemetry Service.

| RPC | CU / RF | Notas |
|---|---|---|
| `CreateOrganization`, `ListOrganizations` | CU-001 / RF-001 | D-13 |
| `CreateSite`, `ListSites(organization_id)` | CU-001 / RF-001 | D-13 |
| `RegisterAsset` | CU-001, CU-012 / RF-001, RF-011 | incluye `criticality` obligatoria; emite `AssetRegistered` |
| `UpdateAsset` | CU-013, CU-012 / RF-012, RF-011 | nombre, descripción, criticidad; emite `AssetUpdated` (D-14) |
| `GetAsset`, `ListAssets(site_id?, PageRequest)` | CU-013 / RF-012 | |
| `RegisterSensor` | CU-007 / RF-002 | `asset_id`, `serial_number`, `model`, `measurement_unit`; estado inicial ACTIVO |
| `UpdateSensor` | CU-013 / RF-012 | solo datos técnicos (no estado ni activo) |
| `GetSensor`, `ListSensors(asset_id?, status?, PageRequest)` | CU-013 / RF-012 | |
| `UpsertOperationalProfile(sensor_id, …)` | CU-011 / RF-010 | emite `OperationalProfileUpdated` (D-14) |
| `GetOperationalProfile(sensor_id)` | CU-013 / RF-012 | |
| `ChangeSensorStatus(sensor_id, target_status, reason)` | CU-017 / RF-016 | |
| `RecordCalibration(sensor_id, kind, performed_at, valid_until, reason)` | CU-019 / RF-016 | `kind`: CALIBRATION / VERIFICATION |
| `ReassignSensor(sensor_id, target_asset_id, reason)` | CU-018 / RF-016 | |
| `RetireSensor(sensor_id, reason)` | CU-020 / RF-016 | |
| `GetSensorHistory(sensor_id, CursorPageRequest)` | CU-021 / RF-012 | |
| `GetSensorEvaluationContext(sensor_id)` | soporte RF-004 (D-07) | uso interno de Telemetry; **no** se expone en el Gateway |
| `GetSensorEvaluationContexts(repeated sensor_id)` | soporte RF-004 (D-07) | variante por lote (opcional, recomendada): una llamada por lote de ingesta en vez de una por sensor; sensores inexistentes se omiten de la respuesta |

Mensajes clave:

```proto
enum SensorStatus { SENSOR_STATUS_UNSPECIFIED = 0; ACTIVE = 1; IN_MAINTENANCE = 2; INACTIVE = 3; RETIRED = 4; }

message OperationalProfile {
  string sensor_id = 1;
  double min_temperature = 2;          // umbral inferior (RN-001)
  double max_temperature = 3;          // umbral superior (RN-001)
  string unit = 4;                     // p. ej. "CELSIUS"
  MagnitudeBands magnitude_bands = 5;  // RN-011 (D-12)
  PersistenceWindow persistence = 6;   // RN-005 / RN-011 (D-12)
  google.protobuf.Duration expected_reading_interval = 7; // RN-020 (D-12)
  google.protobuf.Duration calibration_validity = 8;      // RN-018 (D-12); opcional por perfil
  int64 version = 9;                   // concurrencia optimista / invalidación de caché
  google.protobuf.Timestamp updated_at = 10;
}
// Desviación absoluta fuera de rango a partir de la cual la magnitud sube de nivel.
message MagnitudeBands { double medium_from = 1; double high_from = 2; double critical_from = 3; }
// Lecturas fuera de rango consecutivas dentro de la ventana que convierten la condición en persistente.
message PersistenceWindow { int32 min_consecutive_breaches = 1; google.protobuf.Duration window = 2; }

message SensorEvaluationContext {
  string sensor_id = 1;
  string asset_id = 2;
  com.coldguard.common.v1.Criticality asset_criticality = 3;
  SensorStatus status = 4;
  OperationalProfile profile = 5;      // ausente → lectura no evaluable (RN-001)
}
```

`google.protobuf.Duration`/`Timestamp` son tipos well-known incluidos con `protoc`.

Errores (mapeo único en asset-service): `NOT_FOUND` (sensor/activo inexistente),
`FAILED_PRECONDITION` (transición no permitida, reasignación fuera de EN_MANTENIMIENTO, calibración
faltante o vencida, sensor RETIRADO), `INVALID_ARGUMENT` (validación de campos),
`ALREADY_EXISTS` (número de serie duplicado), `ABORTED` (conflicto de versión optimista),
`PERMISSION_DENIED` (rol no autorizado, SPEC-004). Detalle legible en `description`, código de
negocio estable en trailer `x-error-code` (p. ej. `SENSOR_TRANSITION_NOT_ALLOWED`) para que el
Gateway lo propague sin parsear texto.

## 4. Telemetry Service (`telemetry/v1/telemetry_service.proto`)

Servidor: telemetry-service. Clientes: sensor-simulator (ingesta), Gateway (CU-015 según D-08, y
consultas).

| RPC | CU / RF | Notas |
|---|---|---|
| `IngestReadings(IngestReadingsRequest)` | CU-002, CU-015 / RF-003, RF-014 | unario por lote; misma ruta de evaluación para ambos orígenes (RN-015) |
| `ListReadings(sensor_id, from, to, CursorPageRequest)` | soporte RF-012 (historial técnico, CU-021) — D-15 | |
| `ListConnectivityStatus(only_lost, PageRequest)` | CU-022 / RF-017 | visibilidad desde monitoreo (RN-020) |

```proto
enum ReadingSource { READING_SOURCE_UNSPECIFIED = 0; SIMULATOR = 1; TEST_INJECTION = 2; }
message Reading {
  string reading_id = 1;               // generado por el productor → idempotencia de ingesta
  string sensor_id = 2;
  google.protobuf.Timestamp recorded_at = 3;  // timestamp de origen
  double value = 4;
  string unit = 5;
}
message IngestReadingsRequest { ReadingSource source = 1; repeated Reading readings = 2; }  // tamaño máx. de lote validado
message IngestReadingsResponse { repeated ReadingResult results = 1; }
message ReadingResult {
  string reading_id = 1;
  enum Outcome { OUTCOME_UNSPECIFIED = 0; ACCEPTED = 1; DUPLICATE = 2; REJECTED = 3; }
  Outcome outcome = 2;
  bool eligible = 3;                   // false si el sensor no está ACTIVO (RN-017/RN-018)
  bool breached = 4;                   // true si generó anomalía (RN-002)
  string rejection_code = 5;           // p. ej. SENSOR_NOT_FOUND, UNIT_MISMATCH
}
```

- `source` se persiste como evidencia; **no** altera la evaluación (RN-015).
- Un lote parcialmente inválido no aborta las lecturas válidas (resultado por lectura).

## 5. Incident Service (`incident/v1/incident_service.proto`, extensión compatible)

Agregar (sin modificar tags existentes):

```proto
enum IncidentStatus { INCIDENT_STATUS_UNSPECIFIED = 0; CREATED = 1; CLOSED = 2; ACKNOWLEDGED = 3; ESCALATED = 4; } // D-04

rpc AcknowledgeIncident(AcknowledgeIncidentRequest) returns (IncidentView);   // CU-004
rpc EscalateIncident(EscalateIncidentRequest) returns (IncidentView);         // CU-005
rpc GetIncident(GetIncidentRequest) returns (IncidentView);                   // D-15
rpc ListIncidents(ListIncidentsRequest) returns (ListIncidentsResponse);      // D-15

message AcknowledgeIncidentRequest { string incident_id = 1; string note = 2; }
message EscalateIncidentRequest   { string incident_id = 1; string reason = 2; }
message ListIncidentsRequest {
  repeated IncidentStatus statuses = 1; repeated Priority priorities = 2;
  string asset_id = 3; string sensor_id = 4;
  google.protobuf.Timestamp created_from = 5; google.protobuf.Timestamp created_to = 6;
  com.coldguard.common.v1.PageRequest page = 7;
}
message IncidentView {
  string incident_id = 1; IncidentStatus status = 2;
  string asset_id = 3; string sensor_id = 4; string anomaly_type = 5;
  Impact impact = 6; Urgency urgency = 7; Priority priority = 8;
  google.protobuf.Timestamp created_at = 9;
  google.protobuf.Timestamp ack_due_at = 10; google.protobuf.Timestamp resolve_due_at = 11;
  google.protobuf.Timestamp acknowledged_at = 12; string acknowledged_by = 13;
  google.protobuf.Timestamp last_escalated_at = 14; int32 escalation_count = 15;
  google.protobuf.Timestamp closed_at = 16; string closed_by = 17;
  string cause = 18; string resolution_comment = 19;
  int32 occurrence_count = 20; google.protobuf.Timestamp last_occurrence_at = 21; // RN-005
}
message ListIncidentsResponse { repeated IncidentView incidents = 1; com.coldguard.common.v1.PageInfo page = 2; }
```

- `CloseIncidentResponse.closed_at` (string) pasa a reflejar el valor **persistido** (hoy es la
  hora de construcción de la respuesta).
- La identidad del actor **nunca** es campo de request: llega por metadata (D-03, SPEC-004).
- `CreateIncident` (entrada técnica de DEC-012) se conserva; su exposición en el Gateway queda
  detrás de una propiedad (SPEC-009).

## 6. Identity, Audit y Métricas (servidos por incident-service)

`identity/v1/identity_service.proto` (SPEC-004):

| RPC | Uso |
|---|---|
| `VerifyCredentials(username, password) → AuthenticatedUser{user_id, username, roles[], display_name}` | Login (CU-016). Errores: `UNAUTHENTICATED` genérico (sin distinguir usuario inexistente vs. contraseña errónea) |
| `CreateUser`, `GetUser`, `ListUsers(PageRequest)` | CU-014 |
| `AssignRole(user_id, role, reason)`, `RevokeRole(user_id, role, reason)` | CU-014, auditable |
| `SetUserEnabled(user_id, enabled, reason)` | CU-014 (baja lógica de acceso) |
| `ListUserContacts(role) → [user_id, email]` | interno (resolución de destinatarios D-10); no expuesto en Gateway |

`enum Role { ROLE_UNSPECIFIED = 0; OPERATIONS_SUPERVISOR = 1; OPERATOR = 2; MAINTENANCE_TECHNICIAN = 3; AUDITOR = 4; PLATFORM_ADMIN = 5; }`
— exactamente los cinco actores humanos de `stakeholders.md` (no se inventan roles). Nota de
compatibilidad: el Gateway actual espera el claim `roles` con valor `MAINTENANCE_TECHNICIAN`
(`ROLE_` lo agrega el conversor); estos nombres lo mantienen.

`audit/v1/audit_log_service.proto` (SPEC-007): `ListAuditRecords(filters{entity_type, entity_id,
actor_id, action, from, to}, CursorPageRequest) → [AuditRecordView]`, solo lectura (RF-018).

`metrics/v1/operational_metrics_service.proto` (SPEC-007):
`GetIncidentMetrics(from, to) → {count_by_status, count_by_priority, mtta_seconds, mttr_seconds,
sla_ack_compliance_by_priority, sla_resolution_compliance_by_priority}` (RF-009).

## 7. Eventos: envelope, transporte y topología

### Envelope (JSON, UTF-8)

```json
{
  "eventId": "uuid",
  "eventType": "IncidentCreated",
  "eventVersion": 1,
  "occurredAt": "2026-09-30T12:00:00Z",
  "producer": "incident-service",
  "aggregateType": "Incident",
  "aggregateId": "uuid",
  "correlationId": "string",
  "actor": { "type": "USER | SYSTEM", "id": "string" },
  "payload": { }
}
```

- `actor` obligatorio en eventos derivados de una acción humana o de una tarea programada
  (RN-008, RN-017: "usuario responsable — persona o proceso automático"); para procesos
  automáticos `type=SYSTEM`, `id` = nombre del proceso (p. ej. `calibration-expiry-job`).
- Propiedades AMQP: `message_id = eventId`, `type = eventType`, `content_type =
  application/json`, `delivery_mode = persistent`, headers `correlation-id`, `traceparent`
  (W3C), `event-version`.
- **Prohibido** en payload y headers: tokens, contraseñas/hashes, cadenas de conexión, payload
  completo de telemetría (RNF-008). Correos de destinatarios solo en `NotificationRequested`
  (necesarios para enviar) y nunca se registran en logs.

### Topología RabbitMQ

- Exchange `coldguard.events` (topic, durable) — único, declarado por cada productor.
- Exchange `coldguard.events.dlx` (topic, durable) para dead-letter.
- Routing key: `<context>.<evento-en-kebab>`.
- Cada **consumidor** declara su cola durable, sus bindings, `x-dead-letter-exchange =
  coldguard.events.dlx` y su DLQ `<cola>.dlq`.

| Evento | Routing key | Productor | Cola(s) consumidora(s) |
|---|---|---|---|
| `AssetRegistered` | `asset.asset-registered` | asset | `incident-service.audit` |
| `AssetUpdated` (D-14) | `asset.asset-updated` | asset | `incident-service.audit`, `telemetry-service.asset-changes` |
| `OperationalProfileUpdated` (D-14) | `asset.operational-profile-updated` | asset | `incident-service.audit`, `telemetry-service.asset-changes` |
| `SensorStatusChanged` | `asset.sensor-status-changed` | asset | `incident-service.audit`, `telemetry-service.asset-changes` |
| `SensorReassigned` | `asset.sensor-reassigned` | asset | `incident-service.audit`, `telemetry-service.asset-changes` |
| `SensorCalibrationRecorded` | `asset.sensor-calibration-recorded` | asset | `incident-service.audit` |
| `SensorCalibrationExpired` | `asset.sensor-calibration-expired` | asset | `incident-service.audit` |
| `SensorRetired` | `asset.sensor-retired` | asset | `incident-service.audit`, `telemetry-service.asset-changes` |
| `TelemetryThresholdBreached` | `telemetry.threshold-breached` | telemetry | `incident-service.telemetry-threshold-breached` |
| `SensorConnectivityLost` | `telemetry.sensor-connectivity-lost` | telemetry (D-06) | `incident-service.audit` |
| `IncidentCreated` | `incident.incident-created` | incident | (sin consumidor externo hoy; queda disponible) |
| `IncidentAcknowledged` | `incident.incident-acknowledged` | incident | — |
| `IncidentEscalated` | `incident.incident-escalated` | incident | — |
| `IncidentClosed` | `incident.incident-closed` | incident | — |
| `NotificationRequested` | `incident.notification-requested` | incident | `notification-service.notification-requested` |
| `NotificationFailed` | `notification.notification-failed` | notification | `incident-service.audit` |

Nota: `event-flow.md` dibuja `IncidentCreated` llegando a Notification Service; ADR-005 decide
que Notification consume `NotificationRequested`. Este spec sigue ADR-005 (Incident emite ambos
eventos en la misma transacción; Notification solo escucha `NotificationRequested`). Corregir el
diagrama en la actualización documental (SPEC-011).

### Payloads v1 (campos mínimos)

| Evento | Payload |
|---|---|
| `AssetRegistered` | `assetId`, `siteId`, `name`, `criticality` |
| `AssetUpdated` | `assetId`, `changedFields[]`, `previous{}`, `current{}` (solo campos cambiados) |
| `OperationalProfileUpdated` | `sensorId`, `profileVersion`, `previous{}`, `current{}` |
| `SensorStatusChanged` | `sensorId`, `assetId`, `previousStatus`, `newStatus`, `reason` |
| `SensorReassigned` | `sensorId`, `previousAssetId`, `newAssetId`, `reason` |
| `SensorCalibrationRecorded` | `sensorId`, `calibrationId`, `kind`, `performedAt`, `validUntil`, `reason` |
| `SensorCalibrationExpired` | `sensorId`, `calibrationId`, `expiredAt`, `detectedAt` |
| `SensorRetired` | `sensorId`, `assetId`, `previousStatus`, `reason` |
| `TelemetryThresholdBreached` | `readingId`, `sensorId`, `assetId`, `assetCriticality`, `anomalyType` (`TEMPERATURE_ABOVE_MAX` / `TEMPERATURE_BELOW_MIN`), `value`, `unit`, `thresholdMin`, `thresholdMax`, `deviation`, `magnitude` (`LOW..CRITICAL`), `persistent`, `recordedAt` |
| `SensorConnectivityLost` | `sensorId`, `assetId`, `lastReadingAt`, `expectedIntervalSeconds`, `detectedAt` |
| `IncidentCreated` | `incidentId`, `assetId`, `sensorId`, `anomalyType`, `impact`, `urgency`, `priority`, `createdAt`, `ackDueAt`, `resolveDueAt` |
| `IncidentAcknowledged` | `incidentId`, `acknowledgedAt` (actor en envelope) |
| `IncidentEscalated` | `incidentId`, `escalatedAt`, `reason`, `priority`, `escalationCount` |
| `IncidentClosed` | `incidentId`, `closedAt`, `cause`, `resolutionComment` |
| `NotificationRequested` | `notificationRequestId`, `incidentId`, `notificationType` (`INCIDENT_CREATED`/`INCIDENT_ESCALATED`), `priority`, `assetId`, `sensorId`, `recipients[{userId, email}]` |
| `NotificationFailed` | `notificationRequestId`, `incidentId`, `failureCategory` (`PERMANENT`/`RETRIES_EXHAUSTED`), `attempts` |

`assetCriticality` viaja en `TelemetryThresholdBreached` para que Incident calcule el impacto
(RN-013) **sin** llamar a Asset Service (evita acoplamiento síncrono Incident→Asset).

### Versionado

- Cambios aditivos (campo opcional nuevo) → misma `eventVersion`; consumidores ignoran campos
  desconocidos.
- Cambio incompatible → `eventVersion + 1`, nuevo JSON Schema; el consumidor que reciba una
  versión mayor no soportada la envía a DLQ (no la descarta en silencio).
- Cada payload tiene su JSON Schema en `contracts/events/` (fuente para los futuros tests de
  contrato).

## Criterios de aceptación

1. Todos los `.proto` compilan en los módulos que los usan (generación vía pluginManagement de
   SPEC-001) y el Gateway/servicios existentes siguen compilando tras mover el proto de Incident.
2. Ningún tag ni nombre de enum existente de Incident v1 cambia (diff revisado).
3. `contracts/events/` contiene un JSON Schema por evento de la tabla y `README.md` con envelope,
   routing y versionado.
4. `docs/domain/commands-events.md` refleja: formato físico definido, D-14 (eventos nuevos con su
   CU), D-17 (`TelemetryReceived` interno), productor de `SensorConnectivityLost` (D-06).

## Tareas

1. Registrar D-03, D-04, D-05, D-06, D-07, D-14, D-17.
2. Crear `common`, `asset`, `telemetry`, `identity`, `audit`, `metrics`; extender y mover `incident`.
3. Crear `contracts/grpc/README.md` y `contracts/events/` (README + schemas).
4. Actualizar `commands-events.md`, `event-flow.md`, `data-flow.md`, `bounded-contexts.md`.

## Riesgos

- Mover el `.proto` exige regenerar fuentes (`mvn clean generate-sources`, ya documentado en el
  README) en IDEs.
- `MagnitudeBands`/`PersistenceWindow` son una **propuesta de modelado** de RN-011/RN-005: la
  documentación solo exige que sean configurables por perfil, sin fijar forma. Confirmar con el PO
  junto a D-12.
