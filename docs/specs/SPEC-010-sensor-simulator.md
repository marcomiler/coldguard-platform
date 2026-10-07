# SPEC-010 — Sensor Simulator

## Objetivo

Construir el productor principal de telemetría de la demostración (actor de CU-002): un proceso
autónomo que emite lecturas por gRPC a Telemetry Service reproduciendo los cinco escenarios
exigidos — nominal, fuera de rango, recuperación, persistencia y pérdida de conectividad.

## Trazabilidad

RF-003; CU-002 (escenarios), CU-022 (pérdida de conectividad como ausencia de datos); RN-002,
RN-005, RN-015, RN-020; DEC-002 (el simulador es un contenedor más), DEC-003 (gRPC);
`docs/infrastructure/docker-strategy.md` (servicio `sensor-simulator`), `scope-mvp.md`
(supuesto: el simulador es representativo, no validado contra hardware).

## Estado actual verificado

No existe `simulator/` en el repositorio (el README lo menciona como "fuera de alcance de este
scaffolding"); no hay servicio `sensor-simulator` en `docker-compose.yml`.

## Decisiones requeridas

- **Ubicación del módulo**: `simulator/sensor-simulator` como módulo Maven del reactor raíz
  (coherente con la estructura del README y con ADR-001: un módulo por componente desplegable).
- **Cómo conoce el simulador los sensores**: no tiene acceso a Asset (no está en la arquitectura).
  Propuesta: archivo de escenario YAML generado por el script de seed (SPEC-011) con los
  `sensorId` reales creados vía Gateway; el archivo generado **no se versiona**; se versiona una
  plantilla de ejemplo.

## Diseño

### Módulo

- `simulator/sensor-simulator`, paquete `com.coldguard.simulator` con capas `config`,
  `application` (planificador y generadores), `domain` (escenarios, valores),
  `infrastructure` (cliente gRPC).
- Dependencias: `spring-boot-starter` + actuator (health para Compose; puerto HTTP 8085 solo
  interno), `spring-grpc-client-spring-boot-starter`, protobuf de `telemetry/v1`. No usa
  PostgreSQL ni RabbitMQ. Si `spring-boot-starter-web` se mantiene como dependencia universal del
  POM raíz, este módulo lo hereda (aceptable para exponer actuator); si no, declarar el mínimo
  necesario para actuator HTTP.

### Escenario (YAML)

```yaml
coldguard.simulator:
  enabled: true
  tick-interval: 1s
  random-seed: 42            # opcional: demos reproducibles
  max-batch-size: 200
  buffer:
    max-pending-readings: 5000   # acota memoria si Telemetry no está disponible
  sensors:
    - sensor-id: "<uuid generado por seed>"
      unit: CELSIUS
      interval: 5s
      baseline: 4.0          # valor nominal
      noise: 0.3             # ± variación nominal
      scenario: NOMINAL
    - sensor-id: "<uuid>"
      unit: CELSIUS
      interval: 5s
      baseline: 4.0
      noise: 0.3
      scenario: PERSISTENCE
      breach-value: 11.5     # valor fuera de rango a emitir
      start-after: 30s
    - sensor-id: "<uuid>"
      scenario: RECOVERY
      breach-value: 9.0
      start-after: 20s
      breach-readings: 2     # lecturas fuera de rango antes de volver a nominal
    - sensor-id: "<uuid>"
      scenario: CONNECTIVITY_LOSS
      start-after: 60s
      silence-duration: 5m   # deja de emitir; luego reanuda nominal
```

| Escenario | Comportamiento |
|---|---|
| `NOMINAL` | `baseline ± noise` (dentro del rango del perfil configurado por el seed) |
| `OUT_OF_RANGE` | tras `start-after`, una lectura con `breach-value`, luego nominal |
| `RECOVERY` | `breach-readings` lecturas fuera de rango y luego nominal (reinicia la racha, SPEC-006) |
| `PERSISTENCE` | tras `start-after`, `breach-value` sostenido (supera `min_consecutive` del perfil) |
| `CONNECTIVITY_LOSS` | tras `start-after`, no emite durante `silence-duration` (la detección la hace Telemetry, CU-022) |

Los valores (`baseline`, `breach-value`, intervalos) son **placeholders de demostración** (D-12);
deben ser coherentes con el perfil que crea el seed, que es la única fuente de los umbrales.

Validación del escenario al arrancar (`@ConfigurationProperties` + `@Validated`, records): falla
rápido si falta `sensor-id`, intervalos ≤ 0, o un escenario no reconoce sus parámetros.

### Emisión

1. Un único planificador (`tick-interval`) calcula qué sensores tienen lectura vencida y genera
   cada `Reading` con `reading_id` UUID nuevo y `recorded_at` = instante de generación.
2. Agrupa las lecturas del tick en una sola llamada `IngestReadings(source=SIMULATOR)` (lotes de
   `max-batch-size`).
3. Ante `UNAVAILABLE`/`DEADLINE_EXCEEDED`: conserva las lecturas en un buffer acotado y las
   reenvía con backoff exponencial **con los mismos `reading_id`** (Telemetry deduplica, SPEC-006).
   Si el buffer se llena, descarta las más antiguas y lo registra (métrica
   `coldguard.simulator.readings.dropped`).
4. Resultados `REJECTED` se registran por `sensorId` + `rejection_code` (sin valores), no se
   reintentan.
5. Deadline gRPC configurable; canal con mTLS como cliente `sensor-simulator` (SPEC-001).

Sin estado persistente: al reiniciar, los escenarios comienzan de nuevo (aceptable para demo).

### Docker / Compose

- Dockerfile con la plantilla común (SPEC-001).
- Servicio `sensor-simulator` en el perfil Compose `sim`, `depends_on: telemetry-service
  (service_healthy)`, monta `deploy/local/simulator/scenario.yml` (generado) en solo lectura y
  los certificados.
- `deploy/local/simulator/scenario.example.yml` versionado como plantilla.

## Criterios de aceptación (validación local manual)

1. Con el perfil `sim`, Telemetry recibe lecturas de todos los sensores configurados al ritmo
   indicado (visible en métricas de SPEC-011 y en `GET /api/v1/sensors/{id}/readings`).
2. `PERSISTENCE` genera un incidente que se actualiza (sin duplicarse) mientras persiste.
3. `RECOVERY` genera anomalías que no alcanzan persistencia.
4. `CONNECTIVITY_LOSS` produce un `SensorConnectivityLost` y ningún incidente.
5. Detener Telemetry 30 s y reanudarlo: el simulador reenvía el buffer; no hay lecturas
   duplicadas en BD.
6. Con `random-seed` fijo, dos ejecuciones producen la misma secuencia de valores.

## Tareas

1. Registrar ubicación del módulo y mecanismo de escenario.
2. Módulo Maven + `<module>` en el POM raíz.
3. Configuración tipada + generadores por escenario (estrategia por escenario, `sealed`).
4. Planificador, cliente gRPC con buffer y backoff.
5. Dockerfile + servicio Compose + plantilla de escenario.
6. Generación del archivo de escenario desde el seed (SPEC-011).
7. Actualizar README (quitar "fuera de alcance de este scaffolding").

## Riesgos

- El simulador y el perfil del seed deben mantenerse coherentes; un `breach-value` dentro del
  rango no genera anomalías (el seed es la única fuente de umbrales).

## Avance

Implementado en `simulator/sensor-simulator` (módulo del reactor raíz): configuración tipada y
validada al arrancar (falla con todos los problemas a la vez), un comportamiento por escenario
(`sealed`, sin estado compartido), planificador por intervalo de sensor, buffer acotado con descarte
de los más antiguos, reintento con backoff exponencial y los mismos `reading_id`, y cliente gRPC con
mTLS como `sensor-simulator`. El archivo de escenario lo genera
`deploy/scripts/generate-simulator-scenario.sh` a partir de `demo-seed.json` (no se versiona; la
plantilla es `deploy/local/simulator/scenario.example.yml`). Compose: perfil `sim`
(`docker compose --profile sim up -d`). Métricas Micrometer: `coldguard.simulator.readings.sent`,
`.dropped` y `.rejected`.
