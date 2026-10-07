# Plan de capacidad (entorno local)

Alcance: el stack local de Docker Compose. **No hay resultados de pruebas de carga ni objetivos de
rendimiento confirmados por el negocio**: lo siguiente describe los parámetros configurados y lo
medido en una máquina de desarrollo, no una garantía. Los valores numéricos son placeholders
académicos, configurables.

## Memoria y CPU (medición puntual)

Instantánea de `docker stats` con el stack completo (perfiles `observability` y `sim`), 2026-10-07,
Docker Desktop con 7,7 GiB, simulador generando 5 sensores:

| Contenedor | Memoria | Límite |
|---|---|---|
| asset / telemetry / incident / notification / gateway / sensor-simulator | 300–490 MiB cada uno | 768 MiB (`JAVA_SERVICE_MEM_LIMIT`, JVM con `MaxRAMPercentage=75`) |
| PostgreSQL / RabbitMQ | 92 / 149 MiB | sin límite |
| Tempo / Grafana / Loki / Prometheus / Alloy / Collector | 273 / 98 / 68 / 67 / 68 / 49 MiB | sin límite |
| **Total aproximado** | **≈ 3,2 GiB** | |

Sin los perfiles `observability` y `sim` el stack base (5 servicios, PostgreSQL, RabbitMQ, Mailpit)
ocupa ≈ 2,3 GiB. CPU en reposo por debajo del 5 % por contenedor.

## Parámetros configurados

| Parámetro | Valor | Dónde |
|---|---|---|
| Pool de conexiones PostgreSQL | valor por defecto de Hikari (10 por servicio) | sin configurar; panel *Hikari* del dashboard de servicios |
| Consumidores AMQP | `prefetch` 10, `concurrency` 1 por cola | `RABBITMQ_PREFETCH`, `RABBITMQ_CONCURRENCY` |
| Reintentos del consumidor | 3, con espera de 1 s duplicándose hasta 10 s | `RABBITMQ_RETRY_MAX` |
| Outbox: relevo | cada 500 ms, lotes de 100, confirmación 5 s | `coldguard.outbox.*` |
| Retención del Outbox y de la tabla de mensajes procesados | 7 días | `coldguard.outbox.retention`, `coldguard.inbox.retention` |
| Lote de ingesta de telemetría | máximo 500 lecturas | `coldguard.telemetry.ingest.max-batch-size` |
| Consulta de lecturas | rango máximo 7 días, páginas de 100 | `coldguard.telemetry.readings-query` |
| Listados | página máxima 100 | `coldguard.incident.page`, telemetría |
| Auditoría / métricas | rango máximo 31 / 93 días | `coldguard.auditlog.query`, `coldguard.metrics.query` |
| Simulador | un tick por segundo, lotes de 200, buffer de 5000 lecturas | `coldguard.simulator.*` |
| Cuerpo máximo de petición del Gateway | 1 MB | `coldguard.gateway.max-request-body-size` |
| Hilos | virtuales para I/O (`spring.threads.virtual.enabled`) | todos los servicios |

## Crecimiento de datos

- Lecturas de telemetría: sin purga en el MVP local (DEC-018); índice y paginación obligatorios. Un
  sensor a una lectura cada 5 s produce ≈ 17 000 filas al día.
- `notification_delivery`, bitácora y `outbox_event` (publicados) crecen sin retención propia más
  allá de lo indicado; el volumen de PostgreSQL se conserva entre arranques.
- Loki y Tempo guardan 24 h de trazas (Tempo) y lo que quepa en su volumen (Loki); se borran con
  `deploy/scripts/down.sh --purge`.

## Qué observar

Los dashboards de Grafana cubren cada uno de estos límites: pool de conexiones agotado
(`Hikari pending`), cola de Outbox creciente (`Pending events`, edad), consumidores atrasados (colas
y DLQ), latencia y errores del borde. No se definen alertas con umbral (no aprobados).
