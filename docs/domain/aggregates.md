# Agregados

Detalle de los agregados ya nombrados en `docs/domain/domain-model.md` (sección "Agregados y
responsabilidades"). Este documento no agrega atributos, invariantes ni relaciones no confirmados
por RF/RN/CU — solo estructura lo ya trazado allí para que sea consultable por agregado. El modelo
de datos formal (atributos concretos, tipos, claves) sigue pendiente (ver `TODO` en
`docs/domain/domain-model.md`).

## `Asset` / `Sensor`

- **Responsabilidad**: identidad del activo y del sensor, su asociación (`SensorAssignmentHistory`),
  la criticidad del activo (RN-009) y el estado operativo del sensor (`SensorStatus`, RN-017).
- **Invariantes**:
  - Un `Sensor` tiene exactamente un `SensorStatus` vigente en cada momento (RN-017); transiciones
    permitidas en `docs/domain/state-machines.md`.
  - Solo un `Sensor` con `SensorStatus = ACTIVO` produce lecturas elegibles para evaluación de
    anomalías y creación/actualización de incidentes (RN-002, RN-004, RN-005, RN-018).
  - `SensorAssignmentHistory` es de solo-adición (append-only); la reasignación (CU-018) solo se
    permite con `SensorStatus = EN_MANTENIMIENTO` (RN-017).
  - El retiro (CU-020) es lógico: no elimina lecturas, eventos, incidentes ni historial. RETIRADO
    es un estado terminal para el MVP.
  - Un sensor con historial no puede eliminarse físicamente (RN-017).
- **Trazabilidad**: RN-009, RN-017, RN-018; RF-001, RF-002, RF-010 a RF-012, RF-016; CU-001,
  CU-007, CU-012, CU-017 a CU-021.
- **Eventos que produce/consume**: `AssetRegistered`, `SensorStatusChanged`, `SensorReassigned`,
  `SensorCalibrationRecorded`, `SensorCalibrationExpired`, `SensorRetired`, `SensorConnectivityLost`
  (`docs/domain/commands-events.md`).

## `OperationalProfile`

- **Responsabilidad**: perfil operativo del sensor — rangos térmicos (umbrales) y ventana de
  persistencia (RN-001).
- **Invariantes**: un `Sensor` tiene un `OperationalProfile` vigente, del cual dependen tanto la
  detección de anomalías (RN-002) como la clasificación de urgencia (RN-011).
- **Trazabilidad**: RN-001, RN-002, RN-011; RF-010, RF-012; CU-011, CU-013.

## `TelemetryReading`

- **Responsabilidad**: lectura de telemetría (identificador, sensor, activo, timestamp de origen,
  valor, unidad, datos de correlación) y su elegibilidad para evaluación (RN-017, RN-018).
- **Invariantes**: una lectura de un sensor en `EN_MANTENIMIENTO`, `INACTIVO` o `RETIRADO` se
  conserva como evidencia técnica, pero no participa en la evaluación de anomalías ni en la
  creación/actualización de incidentes (RN-018).
- **Trazabilidad**: RN-002, RN-010, RN-011, RN-017, RN-018; RF-003, RF-004; CU-002, CU-015.
- **Eventos**: `TelemetryReceived`, `TelemetryThresholdBreached`.

## `Incident`

- **Responsabilidad**: ciclo de vida del incidente — creación, severidad/impacto/urgencia/
  prioridad, reconocimiento, escalamiento, cierre.
- **Invariantes**:
  - Un `Incident` está siempre asociado a un `Asset` y a un `Sensor`; su prioridad depende de la
    criticidad del `Asset` (RN-009, RN-013) y de la magnitud/persistencia de la lectura (RN-010,
    RN-011).
  - `IncidentClosed` es el único evento de cierre técnico en el MVP; no existe `IncidentResolved`
    como estado o transición distinta (`docs/domain/commands-events.md`).
  - El cierre técnico es exclusivo del Técnico de mantenimiento (CU-006, RN-019).
- **Trazabilidad**: RN-003 a RN-007, RN-009 a RN-014, RN-019; RF-005 a RF-007; CU-003 a CU-006.
- **Eventos**: `IncidentCreated`, `IncidentAcknowledged`, `IncidentEscalated`, `IncidentClosed`,
  `NotificationRequested`, `NotificationFailed`.

## `AuditRecord`

- **Responsabilidad**: registro auditable de una transición relevante — actor, timestamp, motivo,
  valor anterior/posterior (RN-008). Incluye `SensorLifecycleAudit` como especialización para
  sensores.
- **Invariantes**: todo cambio relevante sobre `Sensor`, `Incident` o `UserAccessAssignment`
  produce un `AuditRecord` (RN-008); `SensorLifecycleAudit` es la fuente que consulta CU-021.
- **Trazabilidad**: RN-008, RN-017; RF-018; CU-009, CU-021.
- **Propiedad**: módulo interno Audit Log de Incident Service (DEC-005, DEC-008,
  `docs/planning/decisions-log.md`) — no es un agregado propio de un microservicio separado.

## `UserAccessAssignment`

- **Responsabilidad**: asignación de rol de usuario (RF-013, CU-014).
- **Trazabilidad**: RF-013; CU-014.
- **Propiedad**: módulo interno Identity & Access de Incident Service (DEC-004, DEC-008).

## No son agregados

- **Evento** (p. ej. `TelemetryReceived`, `IncidentCreated`): mecanismo de comunicación entre
  agregados/servicios, no una entidad de dominio con identidad y ciclo de vida propios.
- **Anomalía**: condición de negocio detectada (lectura fuera de rango del `OperationalProfile`
  vigente, RN-002), no una entidad persistida con su propio agregado; se representa como el evento
  `TelemetryThresholdBreached`.
- **Pérdida de conectividad**: condición operativa sobre `Sensor` (RN-020), distinta de
  `SensorStatus`; no crea ni modifica el agregado `Incident`.

Ver `docs/domain/domain-model.md` ("Diferencia entre evento, anomalía e incidente" y "Conectividad
como condición operativa distinta del ciclo de vida") para el detalle de estas distinciones.

## Pendiente (no bloquea Sprint 1)

- Atributos concretos, tipos y claves de cada agregado: modelo de datos formal pendiente
  (`docs/domain/domain-model.md`, TODO).
- Criterio de vencimiento de calibración/verificación y frecuencia esperada configurable
  (RN-018, RN-020): pendientes de definir, sin cifra numérica fija.
