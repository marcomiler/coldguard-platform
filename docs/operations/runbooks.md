# Runbook local

Todo lo de este documento es para el stack **local** (Docker Compose). No describe ningún entorno
Azure: no existe ninguno aprovisionado. Los scripts están en `deploy/scripts/`; requieren `docker`
(con `compose`), `curl`, `jq` y `openssl`.

## 1. Arranque y parada

| Qué | Comando |
|---|---|
| Stack base (PostgreSQL, RabbitMQ, Mailpit, 5 servicios) | `deploy/scripts/up.sh` |
| + observabilidad (Prometheus, Grafana, Loki, Tempo, OpenTelemetry Collector, Alloy) | `deploy/scripts/up.sh --with-observability` |
| + simulador (siembra la demo y arranca el Sensor Simulator) | `deploy/scripts/up.sh --with-simulator` |
| Todo, generando datos sin intervención | `deploy/scripts/up.sh --with-observability --with-simulator` |
| Datos de demostración (idempotente, por el Gateway) | `deploy/scripts/seed-demo.sh` |
| Verificación extremo a extremo | `deploy/scripts/smoke-e2e.sh` |
| Detener (conserva los datos) | `deploy/scripts/down.sh` |
| Detener y **borrar volúmenes** (pide escribir `yes`) | `deploy/scripts/down.sh --purge` |
| Copia de seguridad por esquema | `deploy/scripts/backup-db.sh` |

`up.sh` crea `deploy/local/.env` desde el ejemplo si falta, genera una contraseña local para los
usuarios demo (`DEMO_USERS_PASSWORD`, nunca se imprime), genera certificados y claves si faltan,
construye las imágenes y espera a que todo contenedor con healthcheck esté `healthy`
(`UP_TIMEOUT_SECONDS`, 300 s por defecto). Con `--with-observability` activa el export de trazas en
todos los servicios (`TRACING_EXPORT_ENABLED=true`).

Perfiles de Compose: `observability` y `sim`. Sin el primero, los servicios no exportan trazas y no
hay errores de exportación en los logs.

## 2. Certificados y claves JWT

- Se generan con `deploy/scripts/generate-dev-certs.sh` (CA local, un certificado por identidad
  interna y el par RS256 del Gateway). Solo para desarrollo: nunca se versionan
  (`deploy/local/certs/`, `deploy/local/jwt/`).
- Regenerar todo: `deploy/scripts/generate-dev-certs.sh --force` y reiniciar el stack. Solo el
  par JWT: `--jwt-only` (los tokens emitidos antes dejan de ser válidos).
- Síntoma de certificados vencidos o de otra CA: el Gateway responde `503 UPSTREAM_UNAVAILABLE` y
  los servicios loguean fallos de handshake TLS.

## 3. Usuarios demo (solo local)

Incident Service crea un usuario por rol al arrancar (`IDENTITY_BOOTSTRAP_ENABLED=true` en Compose),
todos con la contraseña `DEMO_USERS_PASSWORD` de `deploy/local/.env`:
`admin`, `supervisor`, `operator`, `technician`, `auditor`. Cambiar la contraseña en `.env` **no**
cambia la de usuarios ya creados: hay que borrar los datos (sección 9); no existe todavía un cambio de contraseña por la API.

## 4. Diagnóstico con `grpcurl` sobre mTLS

Los puertos gRPC (9091, 9092, 9093) no se publican al host (ADR-003): se llama desde la red de
Compose, o se publica temporalmente uno con un override local, nunca en el archivo versionado.

```bash
# Una llamada real a Incident Service con el certificado del Gateway (los servicios no exponen
# reflexión: se pasan los .proto de contracts/grpc):
docker run --rm --network coldguard-network \
  -v "$PWD/deploy/local/certs:/certs:ro" -v "$PWD/contracts/grpc:/proto:ro" \
  fullstorydev/grpcurl -cacert /certs/ca/ca.crt \
  -cert /certs/gateway/gateway.crt -key /certs/gateway/gateway.key \
  -import-path /proto -proto incident/v1/incident_service.proto \
  -d '{"incident_id":"00000000-0000-0000-0000-000000000000"}' \
  incident-service:9093 com.coldguard.incident.v1.IncidentService/GetIncident
# -> Code: NotFound (la llamada llegó); sin -cert/-key: "Failed to dial target host" (mTLS lo rechaza)
```

Sin certificado de cliente la conexión se rechaza en el handshake (mTLS). Para llamar como un
usuario hay que enviar la identidad (`-H 'x-actor-id: ...' -H 'x-actor-roles: ...'`): las llamadas del Gateway la llevan
en como metadata `x-actor-id` / `x-actor-roles`, que el servidor solo acepta si el
certificado del par es el del Gateway.

## 5. Dónde mirar cada señal

| Pregunta | Dónde |
|---|---|
| ¿Todo está arriba? | Grafana → *ColdGuard - Services overview* → "Targets up"; Prometheus `http://localhost:9090/targets` |
| ¿Hay errores o latencia en el borde? | *Services overview*: RED del Gateway (tasa, 5xx, p50/p95/p99) |
| ¿Un servicio llama lento a otro? | *Services overview*: latencia gRPC servidor/cliente por servicio |
| ¿Mensajes atascados o muertos? | *Messaging*: pendientes y edad del Outbox, colas, **DLQ** |
| ¿Qué está pasando en el negocio? | *Business*: incidentes por prioridad, lecturas/min, conectividad, notificaciones y los eventos de negocio (logs) |
| ¿Qué hizo una operación concreta? | Loki (Explore): `{service="incident-service"} \| json \| correlationId="..."`; el `traceId` del log enlaza a la traza en Tempo |
| ¿Por dónde pasó una operación? | Tempo (Explore → búsqueda): Gateway → Incident por gRPC; Telemetry → RabbitMQ → Incident (asíncrono, enlazado por el `traceparent` del Outbox) |
| ¿Llegó el correo? | Mailpit `http://localhost:8025` |
| ¿Estado del broker? | RabbitMQ `http://localhost:15672` |

Las alertas no están definidas: los umbrales no están aprobados. Los dashboards muestran las cinco
condiciones candidatas (error rate, dependencia caída, latencia, fallo de mensajería, ausencia de
telemetría) para revisión manual (`docs/operations/observability-strategy.md`).

Cada petición lleva `X-Correlation-Id` (se genera si falta) y las respuestas de error lo repiten en
el cuerpo (`correlationId`): es el hilo para encontrar sus logs y su traza.

## 6. Outbox atascado

Síntoma: *Messaging* muestra `Pending events` creciendo o `Age of the oldest pending event` alto, o
los consumidores no reciben eventos.

```bash
docker exec coldguard-postgres psql -U coldguard -d coldguard -c \
  "select event_type, attempts, parked_at is not null as parked, left(last_error,80) as error
     from <esquema>.outbox_event where published_at is null order by created_at limit 20"
```

(`<esquema>` es `incident`, `telemetry`, `asset` o `notification`.)

Causas típicas:
- **RabbitMQ caído o inalcanzable**: el relay reintenta cada ciclo sin aparcar nada; al volver el
  broker se vacía solo.
- **Routing key sin cola enlazada** (`no queue is bound`): tras `max-attempts` el evento queda
  *aparcado* (`parked_at`) y bloquea los siguientes de **su agregado**. Corregir el enlace (la
  topología se declara en cada servicio consumidor) y desaparcar:
  `update <esquema>.outbox_event set parked_at = null, attempts = 0, next_attempt_at = now() where parked_at is not null`.
- **Esquema o migración sin aplicar**: el servicio no arranca; ver sus logs.

## 7. Reprocesar mensajes de una DLQ

Una DLQ (`<cola>.dlq`) guarda mensajes que el consumidor no puede procesar (formato inválido, versión
no soportada, referencia imposible) o cuyos reintentos se agotaron. Nada se pierde en silencio:
*Messaging* muestra su profundidad.

1. Identificar la causa: leer el mensaje en la consola de RabbitMQ (`Queues` → `<cola>.dlq` →
   `Get messages`, sin acusar) y buscar en Loki el consumidor por su hora.
2. Corregir la causa (código, configuración, dato faltante).
3. Reencolar: en la consola, *Move messages* de la DLQ a la cola original (o *Shovel*), o
   republicar el cuerpo con el mismo `eventId`: los consumidores son idempotentes y no duplican.
4. Un mensaje que sigue siendo inválido debe descartarse a propósito (*Purge*), anotando por qué.

Notificaciones: si se agotaron los reintentos de correo, la solicitud quedó `FAILED`, se publicó
`NotificationFailed` y aparece en la bitácora (`NOTIFICATION_FAILED`). Reenviar exige una solicitud
nueva (otro `notificationRequestId`).

## 8. Pruebas de resiliencia manuales

- **Telemetry detenido**: `docker stop coldguard-telemetry-service`; el simulador acumula las lecturas
  (buffer acotado, backoff hasta 30 s) y las reenvía al volver, sin duplicados.
- **Mailpit detenido**: las solicitudes de correo se reintentan; si vuelve a tiempo, el correo llega
  una vez; si no, `NotificationFailed (RETRIES_EXHAUSTED)`.
- **Incident Service detenido**: `/incidents`, `/users` y el login responden 503; `/assets` y
  `/sensors` siguen funcionando.

## 9. Reset de datos locales

`deploy/scripts/down.sh --purge` borra PostgreSQL, RabbitMQ, Grafana, Loki y Tempo (pide
confirmación). Después: `up.sh`, `seed-demo.sh`. Los ids de `deploy/local/demo-seed.json` cambian.
Nunca ejecutar `docker compose down -v` a mano sobre datos que se quieran conservar; antes,
`deploy/scripts/backup-db.sh`.

## 10. Problemas frecuentes

| Síntoma | Causa y salida |
|---|---|
| `up.sh` falla en `failed to authorize ... TLS handshake timeout` | Red hacia Docker Hub; reintentar |
| Un contenedor queda `unhealthy` | `docker logs <contenedor>`; el healthcheck usa `/actuator/health/readiness` (base de datos y broker propios, nunca otros servicios) |
| Grafana sin datos | Prometheus `/targets`; el job `sensor-simulator` está DOWN sin el perfil `sim` (esperado) |
| Sin trazas en Tempo | ¿Se arrancó con `--with-observability`? Sin ese flag no se exportan |
| `smoke-e2e.sh` falla en el correo | Mailpit caído o el servicio de notificaciones sin consumir; ver la cola `notification-service.notification-requested` |
