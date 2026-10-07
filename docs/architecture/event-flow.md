# Flujo de eventos

Todos los nombres de evento están alineados con `docs/domain/commands-events.md`, que es la
fuente de verdad del catálogo. Decisiones que respaldan este flujo: ADR-004 (RabbitMQ), ADR-005
(Incident Service → Notification Service asíncrono, vía evento, no comunicación directa), ADR-009
(Transactional Outbox para publicación confiable).

## Categorías de eventos

| Categoría | Eventos | Productor |
|---|---|---|
| Telemetría | `TelemetryReceived`, `TelemetryThresholdBreached` | Telemetry Service |
| Conectividad | `SensorConnectivityLost` | Telemetry Service, como proceso de sistema (detección por ausencia de telemetría esperada, CU-022; DEC-016) |
| Incidentes | `IncidentCreated`, `IncidentAcknowledged`, `IncidentEscalated`, `IncidentClosed` | Incident Service |
| Notificación | `NotificationRequested`, `NotificationFailed` | Incident Service (solicitud) / Notification Service (resultado) |
| Ciclo de vida del sensor | `SensorStatusChanged`, `SensorReassigned`, `SensorCalibrationRecorded`, `SensorCalibrationExpired`, `SensorRetired` | Asset Service (o Sistema, para `SensorCalibrationExpired`) |

`IncidentResolved` **no** forma parte de este catálogo: no existe una transición o estado del
incidente distinto de `IncidentClosed` que lo justifique; `IncidentClosed` es el único evento de
cierre técnico en el MVP.

`SensorConnectivityLost` es un evento **operativo**: por sí mismo **no crea un incidente
térmico** en el MVP; queda disponible para monitoreo y auditoría (RN-020).

De estos eventos, `IncidentCreated`, `IncidentAcknowledged`, `IncidentEscalated`,
`IncidentClosed` y `SensorConnectivityLost` conforman la telemetría de negocio mínima prevista
(`docs/operations/observability-strategy.md`): se registran con nombre, timestamp,
identificadores correlacionables (`traceId`, `correlationId`, y `incidentId`/`sensorId`/
`assetId` cuando corresponda) y atributos no sensibles necesarios — nunca tokens, credenciales,
cadenas de conexión, secretos ni payloads completos.

## Flujo de telemetría a incidente

```mermaid
sequenceDiagram
  participant Sim as Sensor Simulator / Endpoint de pruebas
  participant TS as Telemetry Service
  participant MQ as RabbitMQ
  participant IS as Incident Service
  participant NS as Notification Service

  Sim->>TS: TelemetryReceived
  TS->>TS: evalúa perfil operativo (RN-001, RN-002)
  TS-->>MQ: TelemetryThresholdBreached (si aplica)
  MQ-->>IS: TelemetryThresholdBreached
  IS->>IS: crea/actualiza incidente (RN-003 a RN-005)
  IS-->>MQ: IncidentCreated + NotificationRequested
  MQ-->>NS: NotificationRequested
  NS-->>MQ: NotificationFailed (si el envío falla)
```

`Incident Service` **no** llama directamente a `Notification Service`: publica en `RabbitMQ`, y
`Notification Service` consume desde ahí (ADR-005). Prohibido dibujar `IS → NS` directo.

## Flujo de conectividad (sin incidente automático)

```mermaid
sequenceDiagram
  participant TS as Telemetry Service
  participant Mon as Monitoreo

  TS->>TS: evalúa ausencia de telemetría esperada (RN-020)
  TS-->>Mon: SensorConnectivityLost
  Note over Mon: Visible y auditable;<br/>no crea un incidente térmico por sí solo
```

## Flujo de reconocimiento, escalamiento y cierre

```mermaid
sequenceDiagram
  participant Sup as Supervisor de operaciones
  participant IS as Incident Service
  participant MQ as RabbitMQ
  participant NS as Notification Service
  participant Tec as Técnico de mantenimiento

  Sup->>IS: reconoce el incidente (CU-004)
  IS-->>MQ: IncidentAcknowledged
  Sup->>IS: solicita/confirma escalamiento (CU-005)
  Note over IS: No hay escalamiento automático<br/>por vencimiento de SLA en el MVP
  IS-->>MQ: IncidentEscalated + NotificationRequested
  MQ-->>NS: NotificationRequested
  NS->>Tec: notificación
  Tec->>IS: diagnostica, interviene y cierra (CU-006, RN-019)
  IS-->>MQ: IncidentClosed
```

## Flujo del ciclo de vida del sensor

```mermaid
sequenceDiagram
  participant Adm as Administrador de plataforma
  participant AS as Asset Service
  participant Sys as Sistema (tarea programada)

  Adm->>AS: cambia estado (CU-017)
  AS-->>AS: SensorStatusChanged
  Sys->>AS: detecta vencimiento de calibración (RN-018)
  AS-->>AS: SensorCalibrationExpired
  Adm->>AS: registra calibración/verificación (CU-019)
  AS-->>AS: SensorCalibrationRecorded
  Adm->>AS: reasigna sensor (CU-018, exige EN_MANTENIMIENTO)
  AS-->>AS: SensorReassigned
  Adm->>AS: retira lógicamente (CU-020)
  AS-->>AS: SensorRetired
```

## Consultas operativas y de auditoría (no son eventos, se mencionan por consistencia)

RF-009/CU-008 (métricas operativas) y RF-018/CU-009 (bitácora de auditoría) son **consultas**, no
eventos de dominio: no aparecen en el catálogo de arriba. Su ownership está fijado por decisión
(DEC-005, DEC-006, `docs/planning/decisions-log.md`): RF-009 mediante el módulo de consultas
operativas de Incident Service, y RF-018 mediante el módulo/servicio lógico Audit Log, que
consulta los registros de auditoría que cada servicio ya genera (RN-008) — ver
`docs/architecture/container-diagram.md`.

## TODO

Formato de mensaje, versionado y topología (exchanges, colas, dead-letter): definidos en
`contracts/events/README.md`. Los reintentos del consumidor, las DLQ y el Outbox están implementados (SPEC-003); el flujo de
reproceso está en `docs/operations/runbooks.md`.
