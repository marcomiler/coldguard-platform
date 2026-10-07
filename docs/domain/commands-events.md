# Eventos de dominio

Catálogo estructurado. Payload conceptual: campos de negocio mínimos para razonar sobre el
evento, no un diseño de base de datos ni un contrato serializado final.

| Evento | Productor | Consumidor / propósito | CU / RF / RN asociado | Payload conceptual |
|---|---|---|---|---|
| `AssetRegistered` | Asset Service (CU-001, CU-012) | Sin consumidor confirmado; asociado explícitamente a CU-001 (alta de unidad) | RF-001, RF-011; CU-001, CU-012 | Identificador de activo, criticidad |
| `TelemetryReceived` | Telemetry Service, a partir de CU-002 (sensor-simulator) o CU-015 (endpoint de pruebas, RN-015) | Telemetry Service — evalúa contra el perfil operativo. Evento interno: no se publica en RabbitMQ (DEC-016) | RF-003, RF-014; CU-002, CU-015; RN-002 | Sensor, activo, timestamp de origen, valor, unidad, datos de correlación |
| `TelemetryThresholdBreached` | Telemetry Service | Incident Service — puede originar `IncidentCreated` | RF-004; CU-002; RN-001, RN-002 | Sensor, activo, magnitud de la desviación, timestamp |
| `SensorConnectivityLost` | Telemetry Service, como proceso de sistema (CU-022, detección por ausencia de telemetría esperada; DEC-016) | Monitoreo/operación — visibilidad y auditoría; **no** crea un incidente térmico | RF-017; CU-022; RN-020 | Sensor, activo, última lectura recibida, frecuencia esperada configurada |
| `IncidentCreated` | Incident Service (CU-003) | Notification Service (notificación inicial); inicia el reloj de SLA (RN-006) | RF-005; CU-003; RN-003, RN-004, RN-005, RN-012 | Incidente, activo, sensor, severidad, impacto, urgencia, prioridad |
| `IncidentAcknowledged` | Incident Service, a partir de CU-004 (Supervisor de operaciones reconoce) | Detiene el reloj de reconocimiento (RN-006) | RF-006; CU-004; RN-006 | Incidente, usuario responsable, timestamp |
| `IncidentEscalated` | Incident Service, a partir de CU-005 (Supervisor de operaciones solicita/confirma; el Sistema registra la transición) | Notification Service — notifica al Técnico de mantenimiento | RF-007; CU-005; RN-012, RN-014 | Incidente, usuario que solicita/confirma, timestamp |
| `IncidentClosed` | Incident Service, a partir de CU-006 (Técnico de mantenimiento) | Detiene el MTTR (RN-006); único evento de cierre técnico en el MVP | RF-006; CU-006; RN-007, RN-019 | Incidente, causa, comentario de resolución, usuario responsable, timestamp |
| `NotificationRequested` | Incident Service (CU-003, CU-005) | Notification Service | RF-008; CU-003, CU-005 | Incidente, tipo de notificación, destinatario (según política vigente, no confirmada como matriz) |
| `NotificationFailed` | Notification Service | Reintento/observabilidad | RF-008 | Incidente, motivo de falla |
| `SensorStatusChanged` | Asset Service, a partir de CU-017 (Administrador de plataforma) o de la tarea programada de vencimiento de calibración (actor Sistema, RN-018) | `SensorLifecycleAudit` (CU-021); afecta la elegibilidad de lecturas (RN-017, RN-018) | RF-016; CU-017; RN-017, RN-018 | Sensor, estado anterior, estado nuevo, usuario responsable (persona o proceso automático), motivo, timestamp |
| `SensorReassigned` | Asset Service (CU-018) | `SensorAssignmentHistory` (CU-018, CU-021) | RF-016; CU-018; RN-017 | Sensor, activo anterior, activo nuevo, usuario responsable, motivo, timestamp |
| `SensorCalibrationRecorded` | Asset Service (CU-019) | `CalibrationOrVerificationRecord` (CU-019, CU-021); evidencia para el retorno a ACTIVO (RN-018, vía CU-017) | RF-016; CU-019; RN-018 | Sensor, usuario responsable, motivo, timestamp |
| `SensorCalibrationExpired` | Sistema (tarea programada de vencimiento de calibración, RN-018) | Dispara o documenta la transición a EN_MANTENIMIENTO (CU-017) | RF-016; RN-018 | Sensor, timestamp de vencimiento detectado |
| `SensorRetired` | Asset Service (CU-020) | `SensorLifecycleAudit` (CU-021); el sensor deja de generar lecturas elegibles | RF-016; CU-020; RN-017 | Sensor, usuario responsable, motivo, timestamp |
| `AssetUpdated` | Asset Service (CU-013, CU-012) | Audit Log; invalidación de caché en Telemetry Service (DEC-017) | RF-012, RF-011; CU-013, CU-012; RN-008, RN-009 | Activo, campos cambiados, valores anterior/posterior |
| `OperationalProfileUpdated` | Asset Service (CU-011, CU-013) | Audit Log; invalidación de caché en Telemetry Service (DEC-017) | RF-010, RF-012; CU-011, CU-013; RN-001, RN-008 | Sensor, versión del perfil, valores anterior/posterior |
| `UserAccessAssignmentChanged` | Incident Service, módulo Identity & Access (CU-014) | Audit Log (escritura in-process); sin consumidor externo | RF-013; CU-014; RN-008 | Usuario, rol, acción (asignar/revocar/habilitar), usuario responsable, motivo |

## Notas

- `IncidentResolved` se eliminó del catálogo: no existe una transición o estado del incidente
  distinto de `IncidentClosed` que lo justifique. `IncidentClosed` es el único evento de cierre
  técnico de incidente en el MVP (Decisión E).
- `SensorCalibrationExpired` no tiene un caso de uso propio: la detección automática de
  vencimiento se documenta como parte de CU-017/RN-018 (actor Sistema, tarea programada), no como
  un CU separado.
- `AssetRegistered` está asociado explícitamente a CU-001 (`docs/domain/use-cases.md`); sigue sin
  un consumidor confirmado, señalado, no resuelto aquí.

- Una anomalía equivalente (mismo activo, sensor y tipo) sobre un incidente abierto **no emite
  evento**: actualiza el incidente (contador de ocurrencias, urgencia sin desescalar y, si cambia
  la prioridad, los vencimientos de SLA) y queda en el Audit Log (`OCCURRENCE_REGISTERED`,
  `PRIORITY_RECALCULATED`; RN-005, RN-014).
- `NotificationRequested` solo se emite si hay al menos un destinatario habilitado con el rol
  correspondiente (placeholder académico, DEC-018); si no, se registra un aviso sin datos
  personales y se incrementa la métrica `coldguard.notification.recipients.missing`.
- El Audit Log recibe los eventos de Asset, Telemetry y Notification por la cola
  `incident-service.audit`; los de Incident e Identity se escriben en proceso. Un evento sin
  mapeo explícito va a la DLQ.

## TODO

- Periodicidad/frecuencia esperada configurable que determina cuándo se considera "pérdida de
  conectividad" (RN-020): pendiente de definir, sin fijar un valor numérico.
- Eventos de autenticación (CU-016): no catalogados; el login no es una transición de negocio
  auditable en el MVP. Los de asignación de acceso y actualización de activos/perfiles quedaron
  catalogados arriba (DEC-020).
- Frecuencia esperada y criterio de vencimiento: valores de demostración como placeholder
  académico (DEC-022), sin cifra de negocio confirmada.

## Formato físico

Definido en `docs/specs/SPEC-002-contratos.md`: envelope JSON versionado (`eventVersion`),
exchange `coldguard.events` (topic), routing key `<contexto>.<evento-en-kebab>`, un JSON Schema
por evento en `contracts/events/` y publicación vía Transactional Outbox
(ADR-009). Es diseño decidido, aún no implementado.
