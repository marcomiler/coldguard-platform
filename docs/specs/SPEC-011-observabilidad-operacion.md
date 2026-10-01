# SPEC-011 — Observabilidad local, operación y cierre documental

## Objetivo

Hacer el stack local **observable y operable**: métricas, trazas distribuidas y logs estructurados
correlacionables en Prometheus/Grafana/Loki (+ backend de trazas), eventos de negocio mínimos,
scripts de arranque/seed/smoke end-to-end, runbooks, y la actualización de la documentación y
reglas que quedaron desactualizadas.

## Trazabilidad

RNF-002, RNF-004, RNF-008; RF-009 (KPI técnicos de soporte); HU-021; R-011;
`docs/operations/observability-strategy.md`, `docs/operations/runbooks.md`,
`docs/quality/sla-kpi.md`, `docs/infrastructure/docker-strategy.md`.

## Estado actual verificado

- `observability/prometheus/prometheus.yml`: solo self-scrape.
- Ningún módulo tiene `micrometer-registry-prometheus` ni dependencias de OpenTelemetry;
  actuator expone solo `health,info`.
- Grafana y Loki se levantan sin datasources, dashboards ni fuente de logs.
- `docs/operations/runbooks.md` tiene 13 líneas; `docs/security/*`,
  `docs/operations/backup-recovery-plan.md`, `capacity-plan.md` están vacíos (1 línea).

## Decisión requerida — D-11 (topología local)

Recomendación:

```
servicios ──OTLP (trazas)──▶ otel-collector ──▶ Tempo (trazas)
servicios ──/actuator/prometheus◀── Prometheus (scrape) ──▶ Grafana
servicios ──stdout JSON──▶ Docker ──▶ Grafana Alloy ──▶ Loki ──▶ Grafana
RabbitMQ ──:15692/metrics◀── Prometheus (plugin rabbitmq_prometheus)
```

- **Trazas**: OpenTelemetry Collector como punto único OTLP (permite cambiar de destino —
  p. ej. Application Insights en la fase Azure — solo por configuración, principio 1 de
  `observability-strategy.md`); backend local candidato **Grafana Tempo** (se integra con Grafana
  existente). Alternativa: Jaeger.
- **Métricas**: scrape directo de Prometheus (ya existe), sin pasar por el collector (KISS).
- **Logs**: los servicios escriben JSON a stdout (12-factor); **Grafana Alloy** lee los logs de
  los contenedores y los envía a Loki. Alternativa: exportar logs por OTLP al collector y de ahí a
  Loki (endpoint OTLP nativo de Loki 3) — evita montar el socket de Docker pero acopla la
  configuración de logging de la app al SDK. Promtail **no** se recomienda (en fase de
  deprecación según Grafana — **verificar** estado al implementar).
- Fijar versiones de imagen de collector, Tempo y Alloy (SPEC-001).

## 1. Métricas

- Dependencia `io.micrometer:micrometer-registry-prometheus` en los 6 módulos; exponer
  `health,info,prometheus` en el **puerto de management** (no publicado al host).
- Tags comunes: `application` (= `spring.application.name`), `environment=local`.
- Histogramas de latencia (percentiles en Prometheus, no en cliente) para HTTP server (Gateway),
  gRPC server/client y listeners AMQP — habilitar
  `management.metrics.distribution.percentiles-histogram` solo para esos meters (evita explosión
  de series).
- Métricas de negocio/técnicas definidas en los specs (nombres consolidados):

| Métrica | Tipo | Spec |
|---|---|---|
| `coldguard.outbox.pending`, `coldguard.outbox.oldest.age.seconds` | gauge | 003 |
| `coldguard.outbox.published`, `coldguard.outbox.publish.failures` | counter | 003 |
| `coldguard.telemetry.readings{source,outcome,eligible,breached}` | counter | 006 (KPI lecturas/min) |
| `coldguard.telemetry.connectivity.lost` | counter | 006 |
| `coldguard.asset.calibration.expired` | counter | 005 |
| `coldguard.incident.created{priority}`, `.acknowledged`, `.escalated`, `.closed` | counter | 007 |
| `coldguard.notification.sent`, `.failed{category}`, `.recipients.missing` | counter | 007, 008 |
| `coldguard.simulator.readings.sent`, `.dropped` | counter | 010 |

- Etiquetas **nunca** con valores de alta cardinalidad (`incidentId`, `sensorId`, `readingId`).
- Dependencias (RNF-004): pool Hikari y `RabbitMQ` vía métricas propias de Spring/Micrometer;
  métricas del broker vía plugin `rabbitmq_prometheus` (habilitarlo en la imagen/config de
  RabbitMQ; profundidad de colas y DLQ). Exportador de PostgreSQL (`postgres-exporter`) opcional.
- `observability/prometheus/prometheus.yml`: un job por servicio (`<servicio>:<management-port>`)
  + RabbitMQ + collector.

## 2. Trazas

- Instrumentación vendor-neutral: `spring-boot-starter-opentelemetry` (Spring Boot 4,
  `.claude/rules/java-spring.md` lo lista como disponible, no adoptado) — **verificar** artefacto
  y propiedades OTLP exactas en la documentación de Spring Boot 4.1.1 antes de usarlo.
- Export OTLP a `otel-collector` (endpoint por variable `OTEL_EXPORTER_OTLP_ENDPOINT` o propiedad
  equivalente); muestreo 100 % en local (configurable).
- Cobertura de propagación (`traceparent` W3C):
  - HTTP entrante del Gateway (automático con observabilidad de Spring MVC).
  - gRPC cliente/servidor: **pendiente de verificación** de la integración de Spring gRPC 1.0.x
    con Micrometer Observation; si no la trae, usar interceptores que propaguen `traceparent` en
    metadata.
  - AMQP: observación de `RabbitTemplate` y listeners (propiedades `observation-enabled` de Spring
    AMQP — **verificar** nombres en Boot 4.1).
  - Outbox: el `traceparent` se guarda en `outbox_event.headers` al escribir y el relay lo envía
    como header, de modo que la traza del consumidor se enlaza con la operación original pese al
    salto asíncrono.
- Spans manuales solo donde aporten (evaluación de lote de telemetría, tarea de conectividad,
  tarea de calibración), sin atributos sensibles.

## 3. Logs estructurados

- Logging estructurado nativo de Spring Boot (`logging.structured.format.console`, formato `ecs`
  o `logstash`) — elegir uno para todos los servicios. Incluye `traceId`/`spanId` del contexto y
  `correlationId` del MDC (SPEC-001).
- Campos de contexto permitidos (`observability-strategy.md`): `traceId`, `spanId`,
  `correlationId`, `incidentId`, `sensorId`, `assetId` cuando apliquen. Prohibidos: tokens,
  credenciales, cadenas de conexión, secretos, payloads completos de telemetría, correos de
  destinatarios, contraseñas/hashes.
- No registrar cuerpos de request/response; niveles por paquete configurables por variable
  (`LOGGING_LEVEL_COM_COLDGUARD`).
- **Eventos de negocio mínimos** (`IncidentCreated`, `IncidentAcknowledged`, `IncidentEscalated`,
  `IncidentClosed`, `SensorConnectivityLost`): un `BusinessEventLogger` común emite una línea
  estructurada (`event.name`, timestamp, ids correlacionables, atributos no sensibles como
  `priority`) **después del commit** (`TransactionSynchronization`/`@TransactionalEventListener`
  `AFTER_COMMIT`) para no registrar hechos revertidos.

## 4. Salud

- Grupos `liveness` (proceso) y `readiness` (DB, RabbitMQ donde aplique; nunca dependencias
  aguas abajo vía gRPC, para evitar cascadas). Mail fuera de liveness (SPEC-008).
- Healthchecks de Compose sobre `readiness` (SPEC-001).

## 5. Grafana (provisionado como código)

```
observability/grafana/provisioning/datasources/datasources.yml   # Prometheus, Loki, Tempo (+ enlaces traceId→Tempo)
observability/grafana/provisioning/dashboards/dashboards.yml
observability/grafana/dashboards/
  services-overview.json   # tasa, errores, latencia (RED) por servicio; JVM; Hikari
  messaging.json           # outbox pending/edad, colas y DLQ RabbitMQ, consumo/fallos
  business.json            # incidentes por prioridad/estado, lecturas/min, conectividad, notificaciones
```

- Correlación en Grafana: campo derivado en Loki que enlaza `traceId` con Tempo.
- **Alertas**: no se crean reglas con umbrales (no aprobados, `observability-strategy.md`); los
  dashboards muestran las cinco condiciones candidatas para revisión manual.

## 6. Operación local (scripts y runbooks)

Scripts en `deploy/scripts/` (bash, `set -euo pipefail`, mensajes claros, sin borrar volúmenes
salvo flag explícito); prerrequisitos `docker`, `curl`, `jq`, `openssl`:

| Script | Función |
|---|---|
| `up.sh [--with-observability] [--with-simulator]` | Verifica `.env` (lo crea desde el ejemplo si falta), genera certificados/claves si faltan, `docker compose --profile … up -d --build`, espera healthchecks con timeout |
| `seed-demo.sh` | Vía **Gateway** (nunca SQL directo): login admin demo, crea organización, sede, activos (criticidades variadas), sensores, perfiles con valores **placeholder académico** (D-12), y genera `deploy/local/simulator/scenario.yml` con los ids reales. Idempotente: si ya existen (por nombre/serial), los reutiliza |
| `smoke-e2e.sh` | Recorrido completo: login por rol → `POST /telemetry/test-readings` fuera de rango (CU-015) → espera el incidente → reconoce (Supervisor) → escala (Supervisor) → verifica correo en Mailpit (API HTTP de Mailpit — **verificar** ruta) → cierra (Técnico) → verifica auditoría (Auditor) y métricas (Supervisor). Sale con código ≠ 0 ante cualquier paso fallido |
| `down.sh [--purge]` | Detiene el stack; `--purge` borra volúmenes con confirmación interactiva |
| `backup-db.sh` (opcional, adelanto de Sprint 7) | `pg_dump` por esquema a un directorio no versionado |

`run-tests.sh` y `test-mtls-stack.sh` existentes se ajustan a los nuevos puertos (SPEC-001); la
ampliación de pruebas queda para el spec final de testing.

`docs/operations/runbooks.md` — secciones nuevas: arranque/parada por perfiles; regenerar
certificados y claves JWT; usuarios demo (solo local); diagnóstico con `grpcurl` sobre mTLS
(ADR-003 lo exige como mitigación); Outbox atascado (consulta de pendientes, causas típicas);
reprocesar mensajes de DLQ desde la consola de RabbitMQ (mover a la cola original tras corregir
la causa); reset de datos locales; dónde mirar cada señal en Grafana.

## 7. Cierre documental (una vez implementados los specs)

| Documento | Cambio |
|---|---|
| `docs/planning/decisions-log.md` | DEC por cada D-xx aprobada (las de arquitectura, en ADR) |
| `docs/architecture/tech-stack.md` | Estados "Planificada" → implementado en local (observabilidad, Mailpit, Flyway, Spring gRPC, Outbox) |
| `docs/architecture/container-diagram.md`, `data-flow.md`, `event-flow.md`, `component-diagram.md`, `deployment-view.md` | D-05, D-06, D-08, D-11; componentes de Asset/Telemetry/Notification |
| `docs/domain/commands-events.md`, `state-machines.md`, `bounded-contexts.md`, `domain-model.md`, `traceability-matrix.md` | D-04, D-06, D-13, D-14, D-17; formato físico de eventos |
| `docs/infrastructure/docker-strategy.md`, `environments.md` | Estado real del stack local |
| `docs/operations/runbooks.md`, `observability-strategy.md`, `capacity-plan.md` | Sección 6, parámetros de pools/prefetch |
| `docs/security/authn-authz.md`, `security-controls.md`, `threat-model.md` | Contenido real (SPEC-004) |
| `docs/product/product-backlog.md` | Estado técnico de HU-019 a HU-021, HU-023 a HU-026 |
| `.claude/rules/java-spring.md`, `.claude/rules/security.md`, `.claude/rules/testing.md` | Hoy afirman que no hay `.proto`, librería gRPC ni configuración de seguridad; están desactualizadas frente a DEC-012 y al código |
| `README.md` | Puertos, perfiles, scripts, simulador |

## Criterios de aceptación (validación local manual)

1. `deploy/scripts/up.sh --with-observability --with-simulator` + `seed-demo.sh` deja el sistema
   generando datos sin intervención.
2. En Grafana: los tres dashboards muestran datos; desde un log de Incident Service con `traceId`
   se navega a la traza en Tempo que incluye Gateway → Incident (gRPC) o Telemetry → RabbitMQ →
   Incident (asíncrono, vía `traceparent` del Outbox).
3. `smoke-e2e.sh` termina con código 0.
4. Búsqueda en Loki de patrones de token (`eyJ`), `password`, `Authorization` y direcciones de
   correo no devuelve resultados (R-011, RNF-008).
5. Prometheus muestra todos los targets `UP`.

## Tareas

1. Registrar D-11.
2. Dependencias y configuración de métricas/trazas/logs en los 6 módulos (si D-01: valores por
   defecto en commons).
3. Collector, Tempo, Alloy en Compose (perfil `observability`); plugin Prometheus de RabbitMQ.
4. Provisioning y dashboards de Grafana; `prometheus.yml`.
5. `BusinessEventLogger` post-commit.
6. Scripts y runbooks.
7. Cierre documental (tabla de la sección 7).

## Riesgos

- Varias integraciones (OpenTelemetry starter, observación de Spring gRPC/AMQP) están marcadas
  como pendientes de verificación para Spring Boot 4.1.1: validarlas al inicio para no rediseñar
  después.
- El stack completo (6 servicios + 9 contenedores de infraestructura/observabilidad) es exigente
  en memoria: los perfiles de Compose permiten levantar solo lo necesario.
