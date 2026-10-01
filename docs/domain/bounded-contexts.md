# Contextos delimitados (bounded contexts)

Los límites siguientes se derivan directamente de la ownership de esquema ya decidida en
`docs/architecture/container-diagram.md` (DEC-004, DEC-005, DEC-006, DEC-008, ADR-006) y del
catálogo de eventos de `docs/domain/commands-events.md`. No se inventan límites, entidades ni
reglas adicionales a los ya trazados por RF/RN/CU en `docs/domain/domain-model.md` y
`docs/domain/aggregates.md`.

El Gateway **no** es un bounded context de dominio: es el único punto de entrada REST, sin lógica
de negocio ni agregación propia (ADR-008). No aparece en la tabla siguiente.

## Contextos

| Bounded context | Servicio (contenedor) | Agregados propios | Esquema PostgreSQL | Eventos que publica | Eventos que consume |
|---|---|---|---|---|---|
| Asset | Asset Service | `Asset` / `Sensor`, `OperationalProfile` | `asset` | `AssetRegistered`, `SensorStatusChanged`, `SensorReassigned`, `SensorCalibrationRecorded`, `SensorCalibrationExpired`, `SensorRetired` | — (ninguno confirmado) |
| Telemetry | Telemetry Service | `TelemetryReading` | `telemetry` | `TelemetryThresholdBreached`, `SensorConnectivityLost` (DEC-016; `TelemetryReceived` es interno, no se publica) | — |
| Incident | Incident Service (incluye los 3 módulos internos siguientes) | `Incident` | `incident` | `IncidentCreated`, `IncidentAcknowledged`, `IncidentEscalated`, `IncidentClosed`, `NotificationRequested`, `NotificationFailed` | `TelemetryThresholdBreached` |
| Incident · Identity & Access (módulo interno, DEC-004/DEC-008) | Incident Service | `UserAccessAssignment` | `identity` (esquema lógico) | — (no catalogados; ver TODO de `commands-events.md`) | — |
| Incident · Audit Log (módulo interno, DEC-005/DEC-008) | Incident Service | `AuditRecord` (incl. `SensorLifecycleAudit`) | `auditlog` (esquema lógico) | — | Registra transiciones de `Sensor`, `Incident`, `UserAccessAssignment` (RN-008); no publica eventos propios |
| Incident · consultas operativas (módulo interno, DEC-006/DEC-008) | Incident Service | Sin agregado propio; lee del agregado `Incident` (RF-009/CU-008) | `incident` (mismo esquema, solo lectura) | — | — |
| Notification | Notification Service | Sin agregado de dominio propio confirmado (solicitudes/estado de envío, RF-008) | `notification` (DEC-012; estado de envío e idempotencia) | — | `IncidentCreated`, `IncidentEscalated` (vía `NotificationRequested`, RabbitMQ) |

## Reglas de límite

- Ningún contexto accede directamente a las tablas de otro; cada uno posee su esquema lógico y se
  comunica por gRPC (síncrono) o eventos (asíncrono) — no hay joins ni FK entre esquemas (ADR-006).
- `Identity & Access`, `Audit Log` y `consultas operativas` son **módulos internos** de Incident
  Service, no microservicios ni bounded contexts desplegables por separado (DEC-008); se listan
  como sub-filas porque tienen esquema lógico y responsabilidad propios, no porque sean contextos
  independientes en el sentido de despliegue.
- Incident Service **no** llama directamente a Notification Service: publica en RabbitMQ vía
  Transactional Outbox (ADR-005, ADR-009); Notification Service consume desde ahí. Está prohibido
  dibujar o implementar una llamada directa Incident → Notification.
- El Sensor Simulator y el endpoint interno protegido de pruebas (CU-015) son productores de
  telemetría hacia el contexto Telemetry; no son bounded contexts propios de este backend (el
  simulador es un sistema externo, ver `docs/architecture/system-context.md`).

## Pendiente (no bloquea Sprint 1)

- Si Notification Service necesita esquema propio (p. ej. para registrar estado de envío/reintentos
  de forma persistente) no está confirmado en `container-diagram.md`; no se inventa aquí.
- Eventos de los módulos Identity & Access y consultas operativas: no catalogados todavía
  (`docs/domain/commands-events.md`, TODO).
