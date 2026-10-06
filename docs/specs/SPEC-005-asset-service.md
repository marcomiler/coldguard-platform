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

## Implementación

### Entrega 1 — Esquema, gRPC con identidad y datos maestros (2026-10-05)

Hecho:
- **Esquema** (`V3__create_asset_tables.sql`; `V1` y `V2` ya eran las tablas de mensajería, no
  `V1` como dice el spec): todas las tablas de la sección "Modelo y persistencia", con los `CHECK`
  del perfil y los índices únicos sin distinguir mayúsculas.
- **Dominio**: `Organization`, `Site`, `Asset`, `Sensor`, `OperationalProfile` (invariantes
  idénticas a los `CHECK`, `NUMERIC(6,2)` con rechazo de valores fuera de rango),
  `CalibrationRecord` y las excepciones con código de negocio estable.
- **Casos de uso**: `AssetCatalogService` (organización, sede, activo, `AssetRegistered`,
  `AssetUpdated`), `SensorService` (alta con asignación inicial, calibración inicial opcional y
  perfil opcional; datos técnicos; consultas) y `OperationalProfileService`
  (`OperationalProfileUpdated`). Los comandos exigen `PLATFORM_ADMIN` y las consultas también
  `OPERATIONS_SUPERVISOR`, comprobado en `application`.
- **gRPC**: 14 de los 22 RPC de `AssetService`, con `AssetGrpcExceptionHandler` y el trailer
  `x-error-code`; el resto responde `UNIMPLEMENTED` hasta las entregas 2 y 3. Servidor con mTLS en
  el puerto 9091; la identidad se acepta solo del certificado `gateway`.
- **Compose**: asset-service monta los certificados, publica gRPC solo dentro de la red y recibe
  `ASSET_CALIBRATION_DEFAULT_VALIDITY` (`P90D`, placeholder académico de DEC-022).
- `Actor`, `Role` y `ActorServerInterceptor` pasaron a `coldguard-commons`
  (`com.coldguard.commons.security`) para compartirlos con Asset sin duplicar la comprobación del
  certificado; Incident los importa de ahí.

Decisiones tomadas en la implementación:
- **JDBC en lugar de JPA** para los repositorios (`JdbcClient`), como el módulo Identity: el
  bloqueo optimista es explícito (`UPDATE ... WHERE id = ? AND version = ?`) y no hay entidades
  que mapear. El spec nombra "adaptador JPA"; el puerto no cambia si se prefiere JPA después.
- **Versiones desde 1** en todos los agregados: `version = 0` queda para "aún no existe" (así lo
  pide el contrato del perfil, "0 al crear") y `OperationalProfileUpdated.profileVersion` exige
  ser al menos 1.
- **Códigos de negocio nuevos** (documentados en `contracts/grpc/README.md`):
  `ORGANIZATION_NOT_FOUND`, `PROFILE_NOT_FOUND`, `ORGANIZATION_NAME_DUPLICATED`,
  `SITE_NAME_DUPLICATED`.
- Un id que no es UUID se trata como no encontrado, no como argumento inválido: no puede existir.
- Una actualización que no cambia nada (activo, sensor o perfil idéntico) no escribe ni publica.
- La calibración inicial deja dos entradas de historial (`REGISTERED` y `CALIBRATION_RECORDED`).

Verificación: 74 tests en asset-service (dominio, casos de uso con almacén en memoria, cableado
gRPC, manejador de errores, e integración contra PostgreSQL y RabbitMQ reales: migración,
unicidad, bloqueo optimista, rollback de un alta fallida, contenido del outbox y `CHECK` del
perfil). Los eventos se comprueban contra `contracts/events/asset` (constantes del envelope,
campos obligatorios, tipos y enums). En Compose: alta de organización, sede, activo y sensor con
calibración y perfil por gRPC con el certificado `gateway`; el certificado del simulador con
identidad de administrador recibe `PermissionDenied`; sin certificado el handshake falla; serial
duplicado → `AlreadyExists` con `SENSOR_SERIAL_DUPLICATED`; caducidad de la calibración =
`performed_at` + 90 días.

Hallazgo fuera de Asset: los eventos quedan en el outbox y se reintentan con
`UnroutableMessageException` mientras ninguna cola esté enlazada. La cola `incident-service.audit`
(que recibe `asset.*`) se declara con `Declarables`, y Spring AMQP solo la crea cuando el servicio
abre una conexión; Incident aún no tiene consumidores (SPEC-007), así que nadie la declara. Con
una cola enlazada a mano los tres eventos se publican en orden y con su `aggregateVersion`. Debe
resolverse con los consumidores de SPEC-007; hasta entonces los eventos de Asset se reintentan y,
tras `max-attempts`, quedan aparcados.

Pendiente:
- ~~Entrega 2~~: hecha, ver abajo.
- Entrega 3: tarea programada de vencimiento de calibración.
- Entrega 4: rutas del Gateway (canal gRPC `asset-service`), y actualizar `component-diagram.md`.
- Los tests de arranque dependen de los certificados de desarrollo locales
  (`deploy/scripts/generate-dev-certs.sh`), igual que los de incident-service.

### Entrega 2 — Ciclo de vida del sensor, historial y contexto de evaluación (2026-10-06)

Hecho:
- **Máquina de estados** en `Sensor` (`changeStatus`, `reassignTo`, `withCalibration`), con los
  guards de RN-017 y RN-018: RETIRED es terminal; un cambio al mismo estado se rechaza; a ACTIVE
  desde IN_MAINTENANCE exige una calibración registrada **después** de entrar y aún vigente
  (`CALIBRATION_EVIDENCE_REQUIRED` / `CALIBRATION_EXPIRED`); desde INACTIVE basta una vigente, sin
  registro nuevo; reasignar solo en IN_MAINTENANCE y a otro activo (`REASSIGNMENT_NOT_ALLOWED`).
  Registrar una calibración nunca cambia el estado.
- **Casos de uso** (`SensorLifecycleService`): `changeStatus`, `retire` (mismo camino que cambiar a
  RETIRED), `recordCalibration` (caducidad derivada: validez del perfil o valor por defecto) y
  `reassign`. Todos exigen `PLATFORM_ADMIN` y un motivo, y dejan en la misma transacción una
  entrada de historial (quién, cuándo, por qué, anterior/posterior) y sus eventos:
  `SensorStatusChanged`, `SensorRetired` + `SensorStatusChanged`, `SensorCalibrationRecorded`,
  `SensorReassigned`.
- **Historial** (`GetSensorHistory`): solo `PLATFORM_ADMIN`, del más reciente al más antiguo,
  paginado por cursor `(occurred_at, id)` opaco; el historial de un sensor retirado sigue siendo
  consultable y no existe forma de borrarlo.
- **Contexto de evaluación** (`GetSensorEvaluationContext(s)`): una sola consulta
  sensor ⋈ activo ⋈ perfil; el lote tiene un máximo configurable (500, placeholder de DEC-022) y
  omite los sensores que no existen. Solo para llamadores de sistema.
- **Llamadores de sistema** en `coldguard-commons`: el interceptor reconoce como actor
  `system:<cn>` a los pares cuyo CN figura en `coldguard.security.system-callers` (Asset:
  `telemetry-service`); el Gateway nunca puede propagar un actor `system:`; un actor de sistema no
  tiene roles. Documentado en `docs/security/`.
- Todos los RPC de `AssetService` están implementados.

Decisiones tomadas en la implementación:
- Operar sobre un sensor RETIRED (calibrar, reasignar, cambiar estado) se rechaza con los códigos
  de transición/reasignación ya definidos; no se añadió un código nuevo.
- `INACTIVE → ACTIVE` sin ninguna calibración registrada se rechaza como `CALIBRATION_EXPIRED`
  (DEC-022 exige evidencia vigente para volver a ACTIVO).
- Un lote de contexto con ids que no son UUID los omite, igual que a los inexistentes.
- Las entradas de historial escritas por una misma operación comparten el instante (el alta deja
  `REGISTERED`, `CALIBRATION_RECORDED` y `PROFILE_UPDATED` a la vez); entre ellas el orden lo
  fija el `id`, estable entre páginas pero sin significado cronológico.

Verificación: 139 tests en asset-service (la tabla completa de los 16 pares de estados, los
escenarios de evidencia de RN-018 con reloj controlado, historial por cursor, contexto de
evaluación, cableado gRPC con códigos de negocio, e integración contra PostgreSQL y RabbitMQ
reales). Entre ellos: la secuencia de 9 eventos de un ciclo completo con `aggregate_version`
1..9, el cursor sobre filas reales con empates (tres entradas del alta en el mismo instante) y una
carrera entre dos cambios de estado simultáneos que deja exactamente uno aplicado. En Compose, el
ciclo completo por gRPC con el certificado `gateway`; el contexto lo lee `telemetry-service`, y lo
rechazan el Gateway con un administrador, el Gateway haciéndose pasar por `system:telemetry-service`,
`telemetry-service` intentando cambiar un estado y `sensor-simulator`.

Pendiente: ~~entrega 3~~ y ~~entrega 4~~, hechas, ver abajo.

### Entrega 3 — Tarea de vencimiento de calibración (2026-10-06)

Hecho:
- `CalibrationExpiryService`: recorre por teclado `(vencimiento, id)` los sensores ACTIVOS o
  INACTIVOS con calibración vencida (índice parcial `ix_sensor_calibration_due`) y mueve cada uno a
  EN_MANTENIMIENTO con el actor de sistema `calibration-expiry-job` y el motivo fijo
  "calibración/verificación vencida". Una transacción por sensor (un fallo no revierte el lote), la
  elegibilidad se revalida dentro de ella, y el recorrido termina aunque un sensor falle siempre.
  Publica `SensorCalibrationExpired` y `SensorStatusChanged`; el historial queda con `actorType`
  SYSTEM.
- Idempotente por construcción: un sensor ya en mantenimiento no se vuelve a seleccionar.
- `CalibrationExpiryScheduler` (`@Scheduled`, cron `coldguard.asset.calibration-expiry.cron`,
  habilitable con `...enabled`, lote `...batch-size`) y los contadores
  `coldguard.asset.calibration.expired` y `coldguard.asset.calibration.expiry.failures`.
- Pensada para una sola instancia (documentado en el código).

Decisiones tomadas en la implementación:
- Un sensor con vencimiento pero sin registro de calibración se cuenta como fallo, no se omite en
  silencio: indica un dato inconsistente.
- El historial de un cambio automático guarda el nombre del proceso (`calibration-expiry-job`),
  igual que el actor del evento, sin el prefijo interno `system:`.

Verificación: tests unitarios (elegibilidad, un vencimiento exactamente ahora no cuenta,
idempotencia, aislamiento de fallos, lotes pequeños que terminan, revalidación) e integración
contra PostgreSQL real (recorrido por teclado con caducidades idénticas, una fila inconsistente
que se revierte sola sin dejar rastro mientras los demás sensores se mueven, secuencia de eventos
con `aggregate_version`) y un test donde la tarea se dispara sola con su propio cron cada segundo,
incluido el contador. No se validó manualmente en Compose con el cron de 5 minutos.

### Entrega 4 — Rutas del Gateway (2026-10-06)

Hecho:
- Recursos REST de Asset según SPEC-009: `GET/POST /organizations`,
  `GET/POST /organizations/{id}/sites`, `GET/POST /assets`, `GET/PATCH /assets/{id}`,
  `GET/POST /sensors`, `GET/PATCH /sensors/{id}`, `GET/PUT /sensors/{id}/profile`,
  `POST /sensors/{id}/status|calibrations|reassignment|retirement` y
  `GET /sensors/{id}/history`. Roles según la tabla de `docs/security/authn-authz.md`; `RbacPolicyTest`
  sigue recorriendo la tabla con los controladores reales.
- Canal gRPC `asset-service` con mTLS y su deadline (`coldguard.gateway.downstream.asset-service`,
  5 s); en Compose, `ASSET_SERVICE_HOST` y `ASSET_SERVICE_GRPC_PORT`.
- Infraestructura común de SPEC-009 (ver ese spec, "Avance").
- Enums REST propios (`HIGH`, `IN_MAINTENANCE`, `VERIFICATION`), duraciones como segundos,
  `201` + `Location` en activo y sensor, sobres de paginación por página y por cursor.
- `component-diagram.md` con los componentes de Asset; `state-machines.md` con los TODO resueltos.

Decisiones tomadas en la implementación:
- El Gateway valida solo la forma (obligatorios, longitudes, formatos). No repite
  `@PastOrPresent` ni positividad de duraciones: el servicio es la única autoridad y un reloj
  adelantado del cliente no debe dar un rechazo que el servicio no daría.
- Organización y sede no devuelven `Location`: no existe `GET` por id para ellas en el catálogo;
  las calibraciones tampoco (no son un recurso direccionable).
- PATCH: un campo ausente o `null` queda como está; una descripción en blanco la borra.

Verificación: 158 tests en gateway (rutas y mapeo de cada recurso, validación sin eco del valor,
enums fuera del vocabulario REST rechazados, tabla completa de estados gRPC→HTTP, ninguna
descripción interna llega al cliente, `correlationId` igual a la cabecera, `GrpcInvoker` contra un
servidor real con deadline y trailer, y un test que impide que un tipo REST importe clases
generadas de gRPC). En Compose por REST con login real: el ciclo completo, los conflictos con su
código, el historial paginado, RBAC por rol, y con asset-service detenido sus rutas dan `503` en
0,04 s mientras login, usuarios e incidentes siguen funcionando.

### Estado de los criterios de aceptación

| # | Criterio | Estado |
|---|---|---|
| 1 | Alta organización → sede → activo → sensor → perfil, con sus eventos en RabbitMQ | Alta verificada por REST; los eventos quedan en el outbox y se publican cuando una cola está enlazada (ver el hallazgo de la entrega 1; lo cierra SPEC-007) |
| 2 | Transición no permitida: 409 sin cambio ni auditoría | Cumplido |
| 3 | EN_MANTENIMIENTO → ACTIVO exige calibración posterior; calibrar no reactiva | Cumplido |
| 4 | INACTIVO → ACTIVO con calibración vigente sí, vencida no | Cumplido (tests unitarios e integración) |
| 5 | Reasignar solo en mantenimiento y conservar la asociación anterior | Cumplido |
| 6 | Retiro terminal con historial consultable y sin borrado | Cumplido |
| 7 | Una calibración corta lleva el sensor a mantenimiento por sí sola, con actor de sistema | Cumplido en test de integración con cron; sin validación manual en Compose |
| 8 | Toda entrada de historial con actor, fecha, motivo y valor anterior/posterior | Cumplido |

Pendiente de SPEC-005: ninguno propio. Dependen de otros specs: la publicación efectiva de los
eventos (SPEC-007), `GET /sensors/{id}/readings` y `GET /sensors/connectivity` (SPEC-006).
