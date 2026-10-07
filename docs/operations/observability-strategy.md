# Estrategia de observabilidad

## Propósito

Definir cómo ColdGuard instrumenta, correlaciona y consulta señales de observabilidad (trazas,
métricas, logs y eventos de negocio) de forma consistente entre el entorno local y el entorno
Azure planificado, sin acoplar el código de aplicación a un proveedor específico. Esta estrategia
no afirma que exista telemetría real, dashboards activos, alertas activas ni una suscripción
Azure operativa: describe el diseño previsto.

## Principios

1. **Instrumentación vendor-neutral con OpenTelemetry.** El código de aplicación se instrumenta
   contra la API de OpenTelemetry, no contra un SDK propietario de un proveedor. El destino de la
   telemetría (local o Azure) se decide por configuración de exportador, no por cambios en el
   código de negocio.
2. **Señales correlacionables.** Toda traza, métrica, log y evento de negocio relevante debe
   poder vincularse a la misma operación mediante identificadores de correlación comunes (ver
   más abajo).
3. **Minimización de datos sensibles.** Nunca se registran tokens, credenciales, cadenas de
   conexión, secretos ni payloads completos de telemetría en logs, trazas o eventos de negocio.
4. **Observabilidad como soporte operativo, no como fin en sí misma.** Las señales existen para
   detectar y diagnosticar condiciones operativas (errores, dependencias caídas, degradación,
   pérdida de conectividad de sensores), no como métricas de vanidad.

## Señales

| Señal | Qué captura | Instrumentación prevista | Destino previsto |
|---|---|---|---|
| Trazas | Recorrido de una operación a través de los servicios (Gateway → servicio → base de datos/broker) | OpenTelemetry (SDK de trazas) | Local: colector/consulta compatible con OpenTelemetry. Azure: Application Insights/Azure Monitor |
| Métricas | Contadores y medidores de aplicación (tasas de solicitud, errores, latencia) y de dependencias (PostgreSQL, RabbitMQ) | OpenTelemetry + Micrometer | Local: Prometheus, visualizado en Grafana. Azure: Application Insights/Azure Monitor |
| Logs | Eventos de ejecución estructurados en JSON, con identificadores de correlación | Framework de logging vigente (Spring Boot / SLF4J), formato JSON estructurado | Local: Loki, visualizado en Grafana. Azure: Azure Monitor Logs / Log Analytics |
| Eventos de negocio | Hechos de dominio relevantes para operación (ver "Eventos de negocio mínimos") | Registrados junto con logs/trazas, correlacionados con `traceId`/`correlationId` | Mismo destino que logs y trazas, según entorno |

## Por entorno

| Entorno | Métricas | Logs | Trazas | Notas |
|---|---|---|---|---|
| Local (desarrollo) | Prometheus | Loki | OpenTelemetry (exportador local/consulta compatible) | Prometheus, Grafana y Loki se mantienen para desarrollo local, portabilidad y evidencia académica; no se exige desplegar Loki ni Grafana autogestionado en Azure |
| Azure (planificado) | Application Insights / Azure Monitor | Azure Monitor Logs / Log Analytics | Application Insights / Azure Monitor | OpenTelemetry exporta hacia Application Insights/Azure Monitor; no se duplica deliberadamente toda métrica/log local en servicios adicionales de Azure |

Azure Managed Prometheus y Azure Managed Grafana quedan como **opciones futuras**, no como parte
confirmada del MVP.

## Fuentes y destinos previstos

- **Fuentes**: Gateway, Asset Service, Telemetry Service, Incident Service, Notification Service
  (todos los contenedores backend, `docs/architecture/container-diagram.md`).
- **Destino local**: Prometheus (métricas), Grafana (visualización), Loki (logs).
- **Destino Azure planificado**: Application Insights / Azure Monitor (trazas, métricas, logs de
  aplicación y excepciones), Azure Monitor Logs / Log Analytics (consulta operativa de logs).

## Campos de correlación

| Permitidos | Prohibidos |
|---|---|
| `traceId` | Tokens JWT |
| `spanId` | Credenciales |
| `correlationId` | Cadenas de conexión |
| `incidentId` (cuando corresponda) | Secretos (claves, contraseñas) |
| `sensorId` (cuando corresponda) | Payloads completos de telemetría |
| `assetId` (cuando corresponda) | — |

`incidentId`, `sensorId` y `assetId` se agregan solo cuando la operación los tiene disponibles;
no se fuerzan en operaciones donde no aplican.

## Eventos de negocio mínimos previstos

`IncidentCreated`, `IncidentAcknowledged`, `IncidentEscalated`, `IncidentClosed`,
`SensorConnectivityLost` (`docs/domain/commands-events.md`). Para cada uno se registra
únicamente: nombre del evento, timestamp, identificadores correlacionables (`traceId`,
`correlationId`, `incidentId`/`sensorId`/`assetId` según aplique) y atributos de negocio no
sensibles necesarios (por ejemplo, prioridad del incidente). No se registran datos sensibles ni
payloads completos.

## Alertas iniciales planificadas

Sin umbrales numéricos: no están aprobados por el equipo y no se inventan aquí. Las siguientes
son condiciones candidatas a alerta, sujetas a validación durante la implementación:

- Tasa de error elevada (error rate) en un servicio backend.
- Dependencia no disponible (PostgreSQL o RabbitMQ inalcanzable).
- Latencia degradada en una operación crítica (por ejemplo, creación de incidente).
- Fallo de publicación o consumo de mensajería (RabbitMQ, incluida la publicación vía
  Transactional Outbox, ADR-009).
- Ausencia de telemetría esperada de un sensor (`SensorConnectivityLost`, RN-020).

> **Nota explícita**: los umbrales y las alertas listadas arriba son iniciales y candidatas;
> están sujetos a validación durante la implementación. No se afirma que exista ninguna alerta
> activa, dashboard activo, métrica real ni resultado de prueba de rendimiento.

## Estado de la implementación local

Implementado y verificado en el stack local (`deploy/scripts/up.sh --with-observability`):

| Señal | Cómo está |
|---|---|
| Métricas | `micrometer-registry-prometheus` en todos los módulos; `/actuator/prometheus` con las etiquetas `application` y `environment`; histogramas solo para HTTP servidor, gRPC y consumidores AMQP; broker por `rabbitmq_prometheus` (profundidad por cola y DLQ). Métricas propias: `coldguard.outbox.*`, `coldguard.telemetry.readings`, `coldguard.telemetry.connectivity.lost`, `coldguard.asset.calibration.expired`, `coldguard.incident.opened/acknowledged/escalated/closed{priority}`, `coldguard.notification.sent/failed{category}/recipients.missing`, `coldguard.simulator.readings.*`. Ninguna etiqueta lleva ids de alta cardinalidad. El contador de incidentes creados se llama `opened`: el cliente de Prometheus reserva el sufijo `_created` |
| Trazas | Spring Boot + OpenTelemetry por OTLP/HTTP al Collector (100 % local, `TRACING_EXPORT_ENABLED`). Una traza cruza el Gateway → Incident por gRPC y Simulador → Telemetry → RabbitMQ → Incident: el Outbox guarda el `traceparent` del productor y el relay lo envía como header (la observación de `RabbitTemplate` está apagada a propósito para no pisarlo). No se trazan `/actuator/**` ni el relay/limpieza del Outbox |
| Logs | JSON por línea en stdout (`logging.structured.format.console=logstash`), con `traceId`, `spanId` y `correlationId`; nivel por `LOGGING_LEVEL_COM_COLDGUARD`; no se registran cuerpos de petición |
| Eventos de negocio | `BusinessEventLogger` (commons) escribe una línea estructurada (`event.name`, ids, prioridad) **después del commit**; un cambio revertido no deja rastro |
| Salud | `liveness` y `readiness`; `readiness` incluye solo dependencias propias (base de datos y broker), nunca otros servicios; el correo no forma parte de ninguno |
| Grafana | Datasources (Prometheus, Loki, Tempo, con enlace `traceId` → Tempo) y tres dashboards (*Services overview*, *Messaging*, *Business*) provisionados desde `observability/grafana/` |

No hay reglas de alerta: los umbrales no están aprobados; los dashboards muestran las cinco
condiciones candidatas para revisión manual.

## Qué no afirma este documento

No existe suscripción Azure operativa, Application Insights, Log Analytics, Azure Monitor,
Prometheus administrado, Grafana administrado, Key Vault, pipeline de GitHub Actions operativo,
alertas activas, costos ni resultados de pruebas de rendimiento. Lo implementado es solo el stack
**local** descrito arriba; el destino Azure sigue siendo diseño previsto.
