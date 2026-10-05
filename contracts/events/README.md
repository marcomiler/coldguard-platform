# Contratos de eventos

Formato físico de los eventos de dominio publicados en RabbitMQ. El catálogo conceptual (qué
significa cada evento, quién lo produce y por qué) vive en `docs/domain/commands-events.md`; este
directorio define cómo viajan.

- `envelope.v1.schema.json`: envelope común.
- `<contexto>/<Evento>.v1.schema.json`: un JSON Schema (draft 2020-12) por evento. Cada uno
  referencia al envelope y fija `eventType`, `eventVersion`, `producer`, `aggregateType` y el
  `payload`.

## Envelope

```json
{
  "eventId": "uuid",
  "eventType": "IncidentCreated",
  "eventVersion": 1,
  "occurredAt": "2026-09-30T12:00:00Z",
  "producer": "incident-service",
  "aggregateType": "Incident",
  "aggregateId": "uuid",
  "aggregateVersion": 1,
  "correlationId": "corr-123",
  "actor": { "type": "USER | SYSTEM", "id": "string" },
  "payload": {}
}
```

- `actor` es obligatorio en todos los eventos: la persona que ejecutó la acción (`USER`) o el
  proceso automático (`SYSTEM`, con el nombre del proceso, p. ej. `calibration-expiry-job`,
  `connectivity-monitor`).
- `aggregateVersion` (entero ≥ 1) es la secuencia del evento dentro de su agregado, asignada por el
  productor en la misma transacción que el cambio de estado. Los consumidores con estado por
  agregado aplican un evento solo si es el siguiente esperado (ADR-011). Es opcional en el
  esquema (cambio aditivo de `v1`); los productores de `coldguard-commons` siempre lo emiten.
- `correlationId` es opcional y solo admite `[A-Za-z0-9._-]{1,100}`.
- El envelope no admite campos adicionales; el `payload` sí (los consumidores ignoran campos que
  no conocen).
- **Prohibido** en cualquier campo o header: tokens, contraseñas o hashes, cadenas de conexión y
  payloads completos de telemetría. Los correos solo viajan en `NotificationRequested`.

### Propiedades y headers AMQP

| Propiedad | Valor |
|---|---|
| `message_id` | `eventId` |
| `type` | `eventType` |
| `content_type` | `application/json` |
| `delivery_mode` | persistente |
| header `correlation-id` | `correlationId` |
| header `traceparent` | contexto de traza W3C |
| header `event-version` | `eventVersion` |

## Topología RabbitMQ

- Exchange `coldguard.events` (topic, durable): único, declarado por cada productor.
- Exchange `coldguard.events.dlx` (topic, durable): dead-letter.
- Routing key: `<contexto>.<evento-en-kebab>`.
- Cada consumidor declara su cola durable con `x-dead-letter-exchange = coldguard.events.dlx` y su
  DLQ `<cola>.dlq`.

| Evento | Routing key | Productor | Colas consumidoras |
|---|---|---|---|
| `AssetRegistered` | `asset.asset-registered` | asset-service | `incident-service.audit` |
| `AssetUpdated` | `asset.asset-updated` | asset-service | `incident-service.audit`, `telemetry-service.asset-changes` |
| `OperationalProfileUpdated` | `asset.operational-profile-updated` | asset-service | `incident-service.audit`, `telemetry-service.asset-changes` |
| `SensorStatusChanged` | `asset.sensor-status-changed` | asset-service | `incident-service.audit`, `telemetry-service.asset-changes` |
| `SensorReassigned` | `asset.sensor-reassigned` | asset-service | `incident-service.audit`, `telemetry-service.asset-changes` |
| `SensorCalibrationRecorded` | `asset.sensor-calibration-recorded` | asset-service | `incident-service.audit` |
| `SensorCalibrationExpired` | `asset.sensor-calibration-expired` | asset-service | `incident-service.audit` |
| `SensorRetired` | `asset.sensor-retired` | asset-service | `incident-service.audit`, `telemetry-service.asset-changes` |
| `TelemetryThresholdBreached` | `telemetry.threshold-breached` | telemetry-service | `incident-service.telemetry-threshold-breached` |
| `SensorConnectivityLost` | `telemetry.sensor-connectivity-lost` | telemetry-service | `incident-service.audit` |
| `IncidentCreated` | `incident.incident-created` | incident-service | `incident-service.lifecycle-events` (retención; sin consumidor hoy) |
| `IncidentAcknowledged` | `incident.incident-acknowledged` | incident-service | `incident-service.lifecycle-events` (retención; sin consumidor hoy) |
| `IncidentEscalated` | `incident.incident-escalated` | incident-service | `incident-service.lifecycle-events` (retención; sin consumidor hoy) |
| `IncidentClosed` | `incident.incident-closed` | incident-service | `incident-service.lifecycle-events` (retención; sin consumidor hoy) |
| `NotificationRequested` | `incident.notification-requested` | incident-service | `notification-service.notification-requested` |
| `NotificationFailed` | `notification.notification-failed` | notification-service | `incident-service.audit` |

Notification Service consume `NotificationRequested`, no `IncidentCreated` (ADR-005): Incident
Service emite ambos en la misma transacción. `TelemetryReceived` no se publica (es interno de
Telemetry Service). `UserAccessAssignmentChanged` se audita en proceso dentro de Incident Service
y no viaja por RabbitMQ.

## Versionado

- Cambio aditivo (campo opcional nuevo en `payload`): misma `eventVersion`; los consumidores
  ignoran campos desconocidos.
- Cambio incompatible (renombrar, eliminar, cambiar tipo o significado): `eventVersion + 1` y un
  schema nuevo `<Evento>.v2.schema.json`; el productor publica ambas versiones mientras algún
  consumidor use la anterior.
- Un consumidor que recibe una `eventVersion` que no soporta la envía a la DLQ; nunca la descarta
  en silencio.
- Estos schemas son la fuente para las pruebas de contrato de productores y consumidores.
