# SPEC-005 — Asset Service

## Objetivo

Implementar el bounded context Asset: organización/sede/unidad de frío (activo), criticidad,
sensores, perfil operativo, ciclo de vida operativo del sensor con su historial y la tarea
programada de vencimiento de calibración; publicar sus eventos de dominio por Outbox.

## Trazabilidad

RF-001, RF-002, RF-010, RF-011, RF-012, RF-016; CU-001, CU-007, CU-011, CU-012, CU-013, CU-017 a
CU-021; RN-001, RN-008, RN-009, RN-017, RN-018; ADR-003, ADR-006, ADR-009;
`docs/domain/state-machines.md`, `docs/quality/acceptance-criteria.md` (RF-016),
HU-019, HU-020.

## Estado actual verificado

`apps/asset-service` solo contiene `AssetServiceApplication` y `package-info.java` vacíos;
`application.yml` apunta a `currentSchema=asset` con `ddl-auto: none`; sin Flyway, sin gRPC.

## Decisiones requeridas

- **D-12** valores de demo (bandas, ventana, intervalo esperado, validez de calibración) →
  placeholders académicos en el seed (SPEC-011).
- **D-13** modelo `Organization → Site → Asset`.
- **D-14** eventos `AssetUpdated`, `OperationalProfileUpdated`.
- **Sensor sin calibración registrada** (nueva): el diagrama inicia en ACTIVO sin exigir
  calibración. Recomendación: `RegisterSensor` acepta una calibración inicial **opcional**; la
  tarea de vencimiento solo evalúa sensores con al menos una calibración; volver a ACTIVO desde
  INACTIVO/EN_MANTENIMIENTO sí exige evidencia (RN-018). Confirmar con el PO.
- **"Acción equivalente documentada" (TODO de RN-018)**: este spec elige que la tarea programada
  **ejecuta la transición directamente** a EN_MANTENIMIENTO, con lo que el TODO no aplica.
  Registrar la elección.

## Modelo y persistencia (esquema `asset`)

Dependencias nuevas: `spring-boot-starter-flyway`, `flyway-database-postgresql`,
`spring-grpc-server-spring-boot-starter`, `spring-boot-starter-amqp`; `ddl-auto: validate`.

```sql
CREATE TABLE asset.organization (id UUID PRIMARY KEY, name VARCHAR(120) NOT NULL, created_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL);
CREATE UNIQUE INDEX ux_organization_name ON asset.organization (lower(name));

CREATE TABLE asset.site (id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES asset.organization(id),
  name VARCHAR(120) NOT NULL, address VARCHAR(250), created_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL);
CREATE UNIQUE INDEX ux_site_org_name ON asset.site (organization_id, lower(name));

CREATE TABLE asset.asset (id UUID PRIMARY KEY, site_id UUID NOT NULL REFERENCES asset.site(id),
  name VARCHAR(120) NOT NULL, description VARCHAR(500), criticality VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL);
CREATE INDEX ix_asset_site ON asset.asset (site_id);

CREATE TABLE asset.sensor (id UUID PRIMARY KEY, serial_number VARCHAR(80) NOT NULL, model VARCHAR(80),
  measurement_unit VARCHAR(20) NOT NULL, asset_id UUID NOT NULL REFERENCES asset.asset(id),
  status VARCHAR(20) NOT NULL, status_changed_at TIMESTAMPTZ NOT NULL,
  last_calibration_recorded_at TIMESTAMPTZ NULL, last_calibration_valid_until TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL);
CREATE UNIQUE INDEX ux_sensor_serial ON asset.sensor (lower(serial_number));
CREATE INDEX ix_sensor_asset ON asset.sensor (asset_id);
CREATE INDEX ix_sensor_calibration_due ON asset.sensor (last_calibration_valid_until)
  WHERE status IN ('ACTIVE','INACTIVE') AND last_calibration_valid_until IS NOT NULL;

CREATE TABLE asset.operational_profile (sensor_id UUID PRIMARY KEY REFERENCES asset.sensor(id),
  min_temperature NUMERIC(6,2) NOT NULL, max_temperature NUMERIC(6,2) NOT NULL, unit VARCHAR(20) NOT NULL,
  magnitude_medium_from NUMERIC(6,2) NOT NULL, magnitude_high_from NUMERIC(6,2) NOT NULL,
  magnitude_critical_from NUMERIC(6,2) NOT NULL,
  persistence_min_consecutive INT NOT NULL, persistence_window_seconds INT NOT NULL,
  expected_interval_seconds INT NOT NULL, calibration_validity_seconds BIGINT NULL,
  updated_at TIMESTAMPTZ NOT NULL, updated_by VARCHAR(100) NOT NULL, version BIGINT NOT NULL,
  CONSTRAINT ck_profile_range CHECK (min_temperature < max_temperature),
  CONSTRAINT ck_profile_bands CHECK (0 < magnitude_medium_from AND magnitude_medium_from < magnitude_high_from AND magnitude_high_from < magnitude_critical_from),
  CONSTRAINT ck_profile_persistence CHECK (persistence_min_consecutive >= 1 AND persistence_window_seconds > 0),
  CONSTRAINT ck_profile_interval CHECK (expected_interval_seconds > 0));

CREATE TABLE asset.calibration_record (id UUID PRIMARY KEY, sensor_id UUID NOT NULL REFERENCES asset.sensor(id),
  kind VARCHAR(20) NOT NULL, performed_at TIMESTAMPTZ NOT NULL, valid_until TIMESTAMPTZ NOT NULL,
  recorded_at TIMESTAMPTZ NOT NULL, recorded_by VARCHAR(100) NOT NULL, reason VARCHAR(500) NOT NULL);
CREATE INDEX ix_calibration_sensor ON asset.calibration_record (sensor_id, recorded_at DESC);

-- append-only: solo INSERT (RN-017)
CREATE TABLE asset.sensor_assignment_history (id UUID PRIMARY KEY, sensor_id UUID NOT NULL REFERENCES asset.sensor(id),
  asset_id UUID NOT NULL, previous_asset_id UUID NULL, assigned_at TIMESTAMPTZ NOT NULL,
  assigned_by VARCHAR(100) NOT NULL, reason VARCHAR(500) NOT NULL);
CREATE INDEX ix_assignment_sensor ON asset.sensor_assignment_history (sensor_id, assigned_at DESC);

-- historial administrativo del sensor (fuente de CU-021)
CREATE TABLE asset.sensor_lifecycle_audit (id UUID PRIMARY KEY, sensor_id UUID NOT NULL REFERENCES asset.sensor(id),
  action VARCHAR(40) NOT NULL, previous_value JSONB NULL, new_value JSONB NULL, reason VARCHAR(500) NULL,
  actor_type VARCHAR(10) NOT NULL, actor_id VARCHAR(100) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL);
CREATE INDEX ix_lifecycle_sensor ON asset.sensor_lifecycle_audit (sensor_id, occurred_at DESC, id DESC);
```

+ `outbox_event` (SPEC-003). FK solo dentro del esquema `asset` (ADR-006).

- `last_calibration_*` se desnormaliza en `sensor` (mismo agregado) para que la tarea de
  vencimiento y los guards no necesiten subconsultas por sensor.
- Sin `DELETE` sobre `sensor`, `calibration_record`, `sensor_assignment_history` ni
  `sensor_lifecycle_audit` en ningún repositorio (RN-017). Opcional: `REVOKE DELETE` al rol de BD
  del servicio si se adoptan roles por servicio (SPEC-001).
- `valid_until` de una calibración = `performed_at + calibration_validity` del perfil; si el
  perfil no lo define, `coldguard.asset.calibration.default-validity` (placeholder académico,
  D-12). El cliente no puede fijarlo arbitrariamente.

## Dominio (`com.coldguard.asset.domain`)

- `Criticality` (LOW, MEDIUM, HIGH, CRITICAL — RN-009), `SensorStatus` (ACTIVE, IN_MAINTENANCE,
  INACTIVE, RETIRED), `CalibrationKind` (CALIBRATION, VERIFICATION).
- `Sensor` (modelo de dominio inmutable o con métodos que devuelven nuevo estado + eventos):
  - `changeStatus(target, reason, actor, clock)`: tabla de transiciones exhaustiva
    (`switch` sobre `(current, target)`), guards:
    - origen RETIRED → `SensorTransitionNotAllowed` (terminal).
    - `target == current` → no permitido (sin no-op silencioso).
    - → ACTIVE desde IN_MAINTENANCE: `last_calibration_recorded_at > status_changed_at`
      **y** `last_calibration_valid_until > now`; si no → `CalibrationEvidenceRequired`.
    - → ACTIVE desde INACTIVE: `last_calibration_valid_until > now`; si no →
      `CalibrationExpired`.
    - → RETIRED: delega en `retire(...)`.
  - `recordCalibration(...)`: no cambia `status` (CU-019, criterio de aceptación explícito).
  - `reassignTo(assetId, …)`: solo IN_MAINTENANCE; destino distinto del actual.
  - `retire(...)`: desde cualquier estado no terminal.
  - Cada operación produce eventos de dominio (`sealed interface AssetDomainEvent`) y una entrada
    de `SensorLifecycleAudit` con actor, fecha, motivo, valor anterior/posterior (RN-017).
- `OperationalProfile` (record) con validación de invariantes equivalente a las `CHECK`
  (falla temprano con mensaje claro antes de llegar a la BD) y método
  `classifyMagnitude(deviation)` **no** — la clasificación vive en Telemetry (SPEC-006); Asset solo
  almacena la configuración.
- Motivo (`reason`) obligatorio y no vacío en CU-017 a CU-020 (RN-017 exige motivo).

## Casos de uso (`application`) — todos `@Transactional`

| Caso | Eventos (Outbox) | Auditoría local |
|---|---|---|
| `CreateOrganization`, `CreateSite` | — (sin evento catalogado) | — |
| `RegisterAsset` | `AssetRegistered` | — |
| `UpdateAsset` (incl. criticidad, CU-012) | `AssetUpdated` (D-14) | — |
| `RegisterSensor` (+ asignación inicial + calibración inicial opcional + perfil opcional) | — (no catalogado) / `SensorCalibrationRecorded` si trae calibración | `REGISTERED` |
| `UpdateSensor` (datos técnicos) | — | `TECHNICAL_DATA_UPDATED` |
| `UpsertOperationalProfile` | `OperationalProfileUpdated` (D-14) | `PROFILE_UPDATED` |
| `ChangeSensorStatus` | `SensorStatusChanged` (+ `SensorRetired` si destino RETIRED) | `STATUS_CHANGED` |
| `RecordCalibration` | `SensorCalibrationRecorded` | `CALIBRATION_RECORDED` |
| `ReassignSensor` | `SensorReassigned` | `REASSIGNED` |
| `RetireSensor` | `SensorRetired` + `SensorStatusChanged` | `RETIRED` |
| `GetSensorHistory` (readOnly) | — | lectura, paginación por cursor `(occurred_at, id)` |
| `GetSensorEvaluationContext` (readOnly) | — | una sola consulta `sensor ⋈ asset ⋈ operational_profile` (mismo esquema) con proyección |

Sobre `SensorStatusChanged` para RETIRED: `state-machines.md` exige que **toda** transición emita
`SensorStatusChanged`; el retiro además emite `SensorRetired`. Ambos en la misma transacción.

Concurrencia: `@Version` en entidades; conflicto → `ABORTED` gRPC → 409 en el Gateway.

Autorización (defensa en profundidad, SPEC-004): todos los comandos exigen
`PLATFORM_ADMIN` en el `Actor`; las consultas aceptan también `OPERATIONS_SUPERVISOR` donde la
tabla RBAC lo indica; `GetSensorEvaluationContext` exige llamador de sistema (Telemetry).

## Tarea programada de vencimiento de calibración (RN-018)

- `@Scheduled(cron = "${coldguard.asset.calibration-expiry.cron}")`, habilitable por propiedad;
  periodicidad configurable (placeholder académico, D-12).
- Consulta paginada por lotes sobre `ix_sensor_calibration_due`: sensores ACTIVE o INACTIVE con
  `last_calibration_valid_until < now`.
- Cada sensor en **su propia transacción** (un fallo no revierte el lote): transición a
  IN_MAINTENANCE con `Actor.system("calibration-expiry-job")`, motivo fijo "calibración/verificación
  vencida" (texto de CU-017), eventos `SensorCalibrationExpired` + `SensorStatusChanged`,
  auditoría local.
- Idempotente por construcción: un sensor ya IN_MAINTENANCE no vuelve a seleccionarse.
- Métrica: contador de sensores transicionados por ejecución (SPEC-011).

## API gRPC

`asset/v1/asset_service.proto` (SPEC-002). `AssetGrpcService` solo traduce request→comando y
resultado→response vía `AssetGrpcMapper`; un `GrpcExceptionHandler` único mapea:
`AssetNotFound`/`SensorNotFound` → `NOT_FOUND`; `SensorTransitionNotAllowed`,
`CalibrationEvidenceRequired`, `CalibrationExpired`, `ReassignmentNotAllowed` →
`FAILED_PRECONDITION` (+ trailer `x-error-code`); validación → `INVALID_ARGUMENT`; serial
duplicado → `ALREADY_EXISTS`; `OptimisticLockingFailure` → `ABORTED`; rol → `PERMISSION_DENIED`.

## Gateway (incremental, convenciones de SPEC-009)

Recursos REST bajo `/api/v1`: `organizations`, `sites`, `assets`, `sensors`,
`sensors/{id}/profile`, `sensors/{id}/status`, `sensors/{id}/calibrations`,
`sensors/{id}/reassignment`, `sensors/{id}/retirement`, `sensors/{id}/history`. Roles según tabla
de SPEC-004. Nuevo canal gRPC `asset-service` con mTLS.

## Configuración

```yaml
spring.grpc.server.port: ${GRPC_SERVER_PORT:9091}
spring.flyway.schemas: asset
coldguard.asset:
  calibration:
    default-validity: ${ASSET_CALIBRATION_DEFAULT_VALIDITY:}   # placeholder académico (D-12)
  calibration-expiry:
    enabled: true
    cron: ${ASSET_CALIBRATION_EXPIRY_CRON:0 */5 * * * *}      # placeholder académico (D-12)
    batch-size: 100
  page:
    max-size: 100
```

Si `default-validity` está vacío y el perfil no define validez, `RecordCalibration` se rechaza
con `FAILED_PRECONDITION` (`CALIBRATION_VALIDITY_NOT_CONFIGURED`) en vez de inventar un valor.

## Criterios de aceptación (validación local manual vía Gateway)

Derivados de `docs/quality/acceptance-criteria.md` (RF-016) y HU-019/HU-020:

1. Alta de organización → sede → activo (criticidad obligatoria) → sensor (ACTIVO) → perfil;
   `AssetRegistered` y `OperationalProfileUpdated` aparecen en RabbitMQ.
2. Transición no permitida (p. ej. RETIRADO→ACTIVO, ACTIVO→ACTIVO) → 409/422 sin cambio de
   estado ni auditoría.
3. EN_MANTENIMIENTO→ACTIVO sin calibración posterior a la entrada → rechazado; tras
   `RecordCalibration` el estado **sigue** EN_MANTENIMIENTO; luego `ChangeSensorStatus(ACTIVE)` →
   aceptado.
4. INACTIVO→ACTIVO con calibración vigente → aceptado sin registro nuevo; con calibración
   vencida → rechazado.
5. Reasignar fuera de EN_MANTENIMIENTO → rechazado; en EN_MANTENIMIENTO → aceptado, el sensor
   sigue EN_MANTENIMIENTO y el historial conserva la asociación anterior.
6. Retiro → RETIRADO; el historial completo (estados, calibraciones, reasignaciones) sigue
   consultable; no existe endpoint de borrado.
7. Con una calibración de validez corta y el cron de demo, el sensor pasa solo a
   EN_MANTENIMIENTO; el historial muestra actor de sistema y motivo "calibración/verificación
   vencida"; se publican `SensorCalibrationExpired` y `SensorStatusChanged`.
8. Toda entrada de historial tiene actor, fecha, motivo y valor anterior/posterior cuando
   corresponde.

## Tareas

1. Registrar D-12, D-13, D-14 y las dos decisiones de este spec.
2. POM, configuración, migraciones `V1__asset_schema.sql`, `V2__outbox.sql`.
3. Dominio (modelo, máquina de estados, eventos, excepciones).
4. Casos de uso + puerto de persistencia + adaptador JPA + mappers.
5. Endpoint gRPC + handler de errores + interceptores (identidad, correlación).
6. Tarea de vencimiento.
7. Rutas en el Gateway.
8. Actualizar `domain-model.md` (D-13), `commands-events.md` (D-14), `component-diagram.md`
   (componentes de Asset, hoy TODO).

## Riesgos

- Guards de la máquina de estados no triviales (riesgo ya señalado en `sprint-3.md`): cubrirlos
  explícitamente en el spec de pruebas final.
- `NUMERIC(6,2)` asume temperaturas en rango ±9999.99; ajustar si se usan otras magnitudes.
