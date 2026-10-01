# SPEC-006 — Telemetry Service

## Objetivo

Recibir telemetría del Sensor Simulator (gRPC) y de la inyección controlada de pruebas (CU-015),
persistirla como evidencia, aplicar elegibilidad por estado del sensor, evaluarla contra el perfil
operativo, detectar persistencia y pérdida de conectividad, y publicar
`TelemetryThresholdBreached` y `SensorConnectivityLost` por Outbox.

## Trazabilidad

RF-003, RF-004, RF-014, RF-017; CU-002, CU-015, CU-022; RN-001, RN-002, RN-005, RN-011, RN-015,
RN-017, RN-018, RN-020; ADR-003, ADR-006, ADR-009; DEC-003; `docs/architecture/data-flow.md`,
`docs/architecture/event-flow.md`.

## Estado actual verificado

`apps/telemetry-service` vacío (placeholders); `application.yml` con `currentSchema=telemetry`.

## Decisiones requeridas

- **D-05** `TelemetryThresholdBreached` por RabbitMQ; **D-06** Telemetry produce
  `SensorConnectivityLost`; **D-07** contexto de evaluación por gRPC a Asset con caché; **D-08**
  CU-015 entra por el Gateway; **D-12** valores de demo; **D-16** sin purga de lecturas;
  **D-17** `TelemetryReceived` no se publica.
- **Modelado de persistencia (RN-005/RN-011)** — propuesta: una condición es *persistente*
  cuando hay al menos `persistence_min_consecutive` lecturas elegibles fuera de rango
  **consecutivas** del mismo tipo de anomalía dentro de `persistence_window`. Una lectura elegible
  dentro de rango reinicia la racha (escenario "recuperación" de CU-002). Confirmar con el PO.
- **Conectividad restablecida** — no existe evento catalogado. Propuesta: al llegar una lectura
  de un sensor marcado sin conectividad, se limpia la marca, se registra log de negocio y métrica,
  **sin** evento nuevo. Si el PO quiere trazabilidad en Audit Log, catalogar
  `SensorConnectivityRestored` (siguiente fila libre en `commands-events.md`).
- **Alcance de la detección de conectividad** — propuesta: solo sensores ACTIVOS con al menos una
  lectura previa (un sensor RETIRADO o INACTIVO no "pierde" conectividad; un sensor que nunca
  envió datos no tiene referencia temporal). Limitación documentada.

## Modelo y persistencia (esquema `telemetry`)

Dependencias: Flyway (+ postgresql), `spring-grpc-server-spring-boot-starter`,
`spring-grpc-client-spring-boot-starter` (hacia Asset), `spring-boot-starter-amqp`,
`spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine` (versión gestionada por el
BOM de Spring Boot — **verificar** al implementar).

```sql
CREATE TABLE telemetry.telemetry_reading (
  id UUID PRIMARY KEY,                 -- reading_id del productor → idempotencia
  sensor_id UUID NOT NULL, asset_id UUID NOT NULL,
  recorded_at TIMESTAMPTZ NOT NULL, received_at TIMESTAMPTZ NOT NULL,
  value NUMERIC(8,3) NOT NULL, unit VARCHAR(20) NOT NULL,
  source VARCHAR(20) NOT NULL,         -- SIMULATOR | TEST_INJECTION (evidencia, no altera evaluación)
  eligible BOOLEAN NOT NULL, ineligibility_reason VARCHAR(30) NULL,  -- SENSOR_NOT_ACTIVE | NO_PROFILE
  breached BOOLEAN NOT NULL, anomaly_type VARCHAR(30) NULL, magnitude VARCHAR(10) NULL,
  correlation_id VARCHAR(100) NULL);
CREATE INDEX ix_reading_sensor_time ON telemetry.telemetry_reading (sensor_id, recorded_at DESC, id DESC);

CREATE TABLE telemetry.sensor_condition (
  sensor_id UUID PRIMARY KEY, asset_id UUID NOT NULL, sensor_status VARCHAR(20) NOT NULL,
  expected_interval_seconds INT NOT NULL,
  last_reading_at TIMESTAMPTZ NOT NULL, last_evaluated_recorded_at TIMESTAMPTZ NULL,
  breach_anomaly_type VARCHAR(30) NULL, breach_streak INT NOT NULL DEFAULT 0,
  breach_streak_started_at TIMESTAMPTZ NULL,
  connectivity_lost_at TIMESTAMPTZ NULL,
  version BIGINT NOT NULL);
CREATE INDEX ix_condition_connectivity ON telemetry.sensor_condition (last_reading_at)
  WHERE connectivity_lost_at IS NULL AND sensor_status = 'ACTIVE';
```

+ `outbox_event` y `processed_message` (SPEC-003). `sensor_id`/`asset_id` son referencias lógicas
a Asset (sin FK entre esquemas, ADR-006).

`sensor_condition` es un modelo de lectura local (estado de racha y conectividad), no una copia del
agregado `Sensor`.

## Flujo de ingesta (`IngestReadings`, una transacción por lote)

1. Validar lote: tamaño ≤ `coldguard.telemetry.ingest.max-batch-size`; `reading_id` UUID;
   `recorded_at` no en el futuro más allá de una tolerancia configurable; `source` definido.
   Lecturas inválidas → `REJECTED` con `rejection_code`, sin abortar el resto.
2. Resolver contexto de evaluación para los `sensor_id` **distintos** del lote:
   caché Caffeine (TTL y tamaño máximo configurables) → faltantes vía gRPC a Asset (una llamada
   por sensor no cacheado, o el RPC batch opcional `GetSensorEvaluationContexts` si se agrega a
   SPEC-002). Deadline gRPC configurable.
   - Sensor inexistente → `REJECTED / SENSOR_NOT_FOUND` (no se persiste: no hay activo asociado).
   - Asset no disponible (`UNAVAILABLE`/`DEADLINE_EXCEEDED`) para un sensor no cacheado → la
     petición completa falla con `UNAVAILABLE` **antes** de persistir; el productor reintenta (las
     lecturas son idempotentes por `reading_id`).
   - Unidad distinta a la del perfil → `REJECTED / UNIT_MISMATCH`.
3. Insertar lecturas con `INSERT … ON CONFLICT (id) DO NOTHING` en batch (`JdbcClient`/
   `JdbcTemplate.batchUpdate`, no `save()` uno a uno); filas no insertadas → `DUPLICATE` (no se
   reevalúan ni reemiten eventos).
4. Por sensor (ordenando por `sensor_id` para evitar interbloqueos), `SELECT … FOR UPDATE` de
   `sensor_condition` (crear si no existe) y procesar sus lecturas nuevas en orden de
   `recorded_at`:
   - Elegibilidad (RN-017/RN-018): `status != ACTIVE` → `eligible=false`,
     `SENSOR_NOT_ACTIVE`; sin perfil → `eligible=false`, `NO_PROFILE` (RN-001). Se persisten igual
     (evidencia), no se evalúan.
   - Evaluación (RN-002), idéntica para `SIMULATOR` y `TEST_INJECTION` (RN-015):
     `value > max` → `TEMPERATURE_ABOVE_MAX`, desviación `value - max`;
     `value < min` → `TEMPERATURE_BELOW_MIN`, desviación `min - value`.
   - Magnitud (RN-011): desviación `< medium_from` → LOW, `< high_from` → MEDIUM,
     `< critical_from` → HIGH, si no CRITICAL.
   - Racha/persistencia (propuesta de arriba): mismo tipo y dentro de la ventana desde
     `breach_streak_started_at` → `streak+1`; si no, reinicia a 1. `persistent = streak >=
     min_consecutive`. Lectura elegible en rango → `streak=0`.
   - Lecturas tardías (`recorded_at <= last_evaluated_recorded_at`): se persisten y marcan
     `breached` pero **no** modifican la racha ni emiten eventos.
   - Por cada lectura elegible fuera de rango (no tardía): `TelemetryThresholdBreached` al Outbox
     con `assetCriticality` del contexto (SPEC-002). Simplicidad (KISS): un evento por lectura con
     anomalía; Incident Service agrega (RN-004/RN-005). Si el volumen lo exige, se podrá emitir solo
     en cambios de racha/magnitud sin cambiar el contrato.
   - Actualizar `last_reading_at` (cualquier lectura, elegible o no) y limpiar
     `connectivity_lost_at` si estaba marcado (ver decisión "Conectividad restablecida").
5. Commit; responder resultados por lectura. Contador Micrometer
   `coldguard.telemetry.readings{source,outcome,eligible,breached}` (KPI de lecturas/minuto,
   D-17).

Nunca se registra el payload completo de lecturas en logs (RNF-008): solo `sensorId`,
`readingId`, conteos y resultado.

## Invalidación de caché (consumo de eventos de Asset)

Cola `telemetry-service.asset-changes` (SPEC-002) con consumidor idempotente (SPEC-003):

| Evento | Acción |
|---|---|
| `SensorStatusChanged`, `SensorRetired` | evict del sensor + actualizar `sensor_condition.sensor_status` |
| `SensorReassigned` | evict del sensor + actualizar `sensor_condition.asset_id` |
| `OperationalProfileUpdated` | evict del sensor + `expected_interval_seconds` en `sensor_condition` |
| `AssetUpdated` | evict de todos los sensores del activo (o `clear()` si no se indexa por activo: evento poco frecuente) |

El TTL de la caché acota la desactualización si un evento se retrasa (consistencia eventual,
`CLAUDE.md`).

## Detección de pérdida de conectividad (CU-022, RN-020)

- `@Scheduled(fixedDelayString = "${coldguard.telemetry.connectivity.check-interval}")`.
- Selección por lotes con `FOR UPDATE SKIP LOCKED`: `connectivity_lost_at IS NULL AND
  sensor_status = 'ACTIVE' AND last_reading_at + (expected_interval_seconds * :tolerance) * interval '1 second' < now()`.
  `tolerance` (factor ≥ 1, configurable) evita falsos positivos por jitter.
- Por sensor: `connectivity_lost_at = now()`, `SensorConnectivityLost` al Outbox con actor
  `SYSTEM/connectivity-monitor`, log de evento de negocio (SPEC-011).
- **No** crea incidente ni cambia `SensorStatus` (RN-020, CU-022).
- Idempotente: un sensor ya marcado no se vuelve a emitir hasta que se restablezca.

## API gRPC

`telemetry/v1/telemetry_service.proto` (SPEC-002): `IngestReadings`, `ListReadings` (cursor
`(recorded_at, id)`), `ListConnectivityStatus` (paginado). `ListReadings` exige rango de fechas
con amplitud máxima configurable para no recorrer todo el histórico.

Autorización: `IngestReadings` acepta al simulador (cliente mTLS `sensor-simulator`) o al Gateway
con actor `PLATFORM_ADMIN` (CU-015, D-08); consultas según tabla RBAC (SPEC-004).

## Gateway (incremental)

- `POST /api/v1/telemetry/test-readings` (`PLATFORM_ADMIN`) → `IngestReadings` con
  `source=TEST_INJECTION`. El Gateway genera `reading_id` si el cliente no lo envía.
- `GET /api/v1/sensors/{id}/readings?from&to&cursor&size`, `GET /api/v1/sensors/connectivity`.

## Configuración

```yaml
spring.grpc.server.port: ${GRPC_SERVER_PORT:9092}
spring.grpc.client.channels.asset-service.address: static://${ASSET_SERVICE_HOST:localhost}:${ASSET_SERVICE_GRPC_PORT:9091}
coldguard.telemetry:
  ingest:
    max-batch-size: 500
    future-tolerance: 30s
  evaluation-context-cache:
    ttl: 60s
    max-size: 10000
  asset-call-deadline: 2s
  connectivity:
    enabled: true
    check-interval: 30s          # placeholder académico (D-12)
    tolerance-factor: 1.5        # placeholder académico (D-12)
  readings-query:
    max-range: 7d
```

Hibernate/JDBC: `spring.jpa.properties.hibernate.jdbc.batch_size` y `order_inserts` si algún
camino usa JPA para escritura masiva; Hikari `maximum-pool-size` configurable.

## Criterios de aceptación (validación local manual)

1. Lote con lecturas en rango de un sensor ACTIVO → todas `ACCEPTED`, `breached=false`, sin
   eventos.
2. Lectura fuera de rango → `TelemetryThresholdBreached` en RabbitMQ con magnitud según bandas
   del perfil y `persistent=false`; tras `min_consecutive` lecturas fuera de rango consecutivas
   dentro de la ventana → `persistent=true`; una lectura en rango reinicia la racha.
3. Mismo lote reenviado → todas `DUPLICATE`, sin eventos nuevos.
4. Sensor EN_MANTENIMIENTO/INACTIVO/RETIRADO → lecturas persistidas con `eligible=false`, sin
   eventos (RN-018).
5. Misma lectura inyectada por `POST /api/v1/telemetry/test-readings` y por el simulador produce
   la misma evaluación (RN-015); solo difiere `source`.
6. Dejar de enviar lecturas de un sensor ACTIVO más allá de `expected_interval × tolerance` →
   un único `SensorConnectivityLost`; ningún incidente creado; `SensorStatus` sin cambio; al
   reanudar, la marca se limpia.
7. Con Asset Service detenido, un lote de un sensor no cacheado → `UNAVAILABLE` sin lecturas
   persistidas; de un sensor cacheado → se procesa.
8. Cambiar el estado del sensor en Asset se refleja en la elegibilidad sin esperar al TTL
   (invalidación por evento).

## Tareas

1. Registrar D-05, D-06, D-07, D-08, D-16, D-17 y las decisiones de este spec.
2. POM, configuración, migraciones (`V1__telemetry_schema.sql`, `V2__outbox_inbox.sql`).
3. Dominio: `ReadingEvaluator` (puro: rango, magnitud, racha), `SensorCondition`, eventos.
4. Cliente gRPC a Asset + caché + consumidor de invalidación.
5. Caso de uso de ingesta (batch, idempotente, bloqueo por sensor).
6. Tarea de conectividad.
7. Endpoint gRPC + handler de errores; rutas del Gateway.
8. Corregir `data-flow.md`/`container-diagram.md` (D-05) y `bounded-contexts.md` (D-06).

## Riesgos

- Crecimiento de `telemetry_reading` sin retención (D-16); el índice compuesto mantiene acotadas
  las consultas por sensor. Particionado por tiempo queda como evolución.
- La propuesta de racha/persistencia es una interpretación de RN-005/RN-011; cambiarla después
  afecta urgencia y prioridad de incidentes.
