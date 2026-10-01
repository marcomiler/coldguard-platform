# SPEC-003 — Mensajería confiable: Transactional Outbox y consumidor idempotente

## Objetivo

Implementar una sola vez (o con diseño idéntico por servicio si D-01 se rechaza) el mecanismo de
**publicación confiable** (ADR-009) y de **consumo idempotente con ack posterior al commit**
(`.claude/rules/testing.md`, `CLAUDE.md`), que reutilizan Asset, Telemetry, Incident y
Notification.

## Trazabilidad

ADR-004, ADR-005, ADR-006, ADR-009; RNF-002, RNF-004; `docs/operations/observability-strategy.md`
(alerta "fallo de publicación o consumo de mensajería").

## Estado actual verificado

Ningún módulo declara `spring-boot-starter-amqp`; no existe tabla `outbox` ni consumidor.
Compose ya levanta RabbitMQ (`rabbitmq:3-management`) con healthcheck.

## Diseño

### 1. Dependencias

`spring-boot-starter-amqp` en los servicios que publican o consumen (asset, telemetry, incident,
notification). Gateway no.

### 2. Tabla Outbox (una por esquema emisor, ADR-006)

Migración Flyway en cada servicio emisor (`asset`, `telemetry`, `incident`, `notification`):

```sql
CREATE TABLE <schema>.outbox_event (
    id              UUID PRIMARY KEY,           -- = eventId del envelope
    aggregate_type  VARCHAR(60)  NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    event_type      VARCHAR(80)  NOT NULL,
    event_version   INT          NOT NULL,
    routing_key     VARCHAR(120) NOT NULL,
    payload         JSONB        NOT NULL,      -- envelope completo serializado
    headers         JSONB        NOT NULL,      -- correlation-id, traceparent
    created_at      TIMESTAMPTZ  NOT NULL,
    published_at    TIMESTAMPTZ  NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(500) NULL
);
CREATE INDEX ix_outbox_event_pending ON <schema>.outbox_event (created_at) WHERE published_at IS NULL;
```

- Índice parcial: el relay solo recorre pendientes; el costo no crece con el histórico publicado.
- `last_error` truncado y sin datos sensibles (solo clase de excepción + mensaje acotado).

### 3. Escritura (`OutboxWriter`)

- Puerto en `application` (p. ej. `DomainEventPublisher.publish(DomainEvent)`), adaptador en
  `infrastructure` que serializa el envelope y hace `INSERT` en `outbox_event`.
- Se invoca **dentro** de la transacción del caso de uso (`@Transactional` en el servicio de
  aplicación). Si la transacción hace rollback, el evento desaparece con ella (atomicidad
  estado+evento, ADR-009).
- `correlationId` y `traceparent` se toman del contexto actual (MDC / OpenTelemetry) al escribir.
- El dominio emite eventos como records (`sealed interface <Context>Event permits …`); el mapeo a
  envelope/routing key vive en un único componente por servicio (`EventEnvelopeFactory`), no en
  cada caso de uso (DRY).

### 4. Relay (`OutboxRelay`)

- Tarea programada (`@Scheduled(fixedDelayString = "${coldguard.outbox.poll-interval:500ms}")`)
  con habilitación por propiedad.
- Algoritmo por ciclo, en una transacción corta:
  1. `SELECT … FROM outbox_event WHERE published_at IS NULL ORDER BY created_at LIMIT :batch FOR UPDATE SKIP LOCKED`
     (permite más de una instancia sin doble publicación concurrente; sin librería de locks).
  2. Publicar cada fila con **publisher confirms** (`spring.rabbitmq.publisher-confirm-type=correlated`,
     `publisher-returns=true`, `template.mandatory=true`) y esperar confirmación con timeout
     configurable.
  3. Confirmadas → `published_at = now()`; fallidas → `attempts + 1`, `last_error`, se reintentan
     en el siguiente ciclo (backoff simple por `attempts`, configurable, con tope).
- Orden: se publica en orden de `created_at`; si una fila falla, las siguientes del mismo
  `aggregate_id` en el lote se posponen para no invertir el orden por agregado.
- Duplicados posibles (crash entre confirm y `UPDATE`) son aceptados: los consumidores son
  idempotentes (ADR-009).
- **Limpieza**: segunda tarea programada que borra filas publicadas con antigüedad mayor a
  `coldguard.outbox.retention` (configurable, p. ej. 7 días en local) por lotes
  (`DELETE … WHERE id IN (SELECT … LIMIT n)`), para evitar crecimiento indefinido.
- Métricas Micrometer (SPEC-011): `coldguard.outbox.pending` (gauge), `coldguard.outbox.oldest.age.seconds`
  (gauge), `coldguard.outbox.published` / `coldguard.outbox.publish.failures` (counters por
  `event_type`).

### 5. Declaración de topología

- Productor: declara `TopicExchange coldguard.events` y `coldguard.events.dlx` (durables) vía
  beans `Declarables`.
- Consumidor: declara su `Queue` durable con `x-dead-letter-exchange=coldguard.events.dlx` y
  `x-dead-letter-routing-key=<cola>.dlq`, la DLQ y los `Binding` de SPEC-002. Nombres en
  `@ConfigurationProperties` (no literales repetidos).
- Opcional (recomendado): `x-queue-type=quorum` para colas de negocio. **Pendiente de
  verificación** de compatibilidad con la versión de imagen RabbitMQ que se fije en SPEC-001.

### 6. Consumidor idempotente (`InboxGuard`)

Tabla por esquema consumidor:

```sql
CREATE TABLE <schema>.processed_message (
    message_id   UUID        NOT NULL,
    consumer     VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (message_id, consumer)
);
```

Flujo en el listener:

1. Deserializar envelope; si `eventVersion` no soportada o JSON inválido → `AmqpRejectAndDontRequeueException`
   (va a DLQ; error permanente).
2. Abrir transacción (`TransactionTemplate` o servicio `@Transactional`):
   - `INSERT INTO processed_message … ON CONFLICT DO NOTHING`; si 0 filas → duplicado: no se
     aplica el efecto, se confirma la transacción vacía.
   - Aplicar el efecto de negocio (+ Outbox si el efecto emite eventos, + auditoría).
3. Commit. **El ack se envía después** del retorno del listener (modo `AUTO` de Spring AMQP con
   transacción local terminada dentro del listener) → si falla antes del commit, no hay ack y
   RabbitMQ re-entrega.
- Limpieza de `processed_message` con retención configurable (mayor que la ventana razonable de
  redelivery).

### 7. Reintentos y clasificación de errores

- Reintento **stateless** en el consumidor con backoff exponencial y número máximo de intentos
  configurables; agotados → rechazo sin requeue → DLQ. En Spring Boot 3.x esto se configuraba con
  `spring.rabbitmq.listener.simple.retry.*` sobre Spring Retry; **pendiente de verificación** de
  los nombres de propiedad y del mecanismo de retry vigente en Spring AMQP para Spring Boot 4.1.1
  antes de codificar (el ecosistema Spring 7 movió parte del soporte de reintentos al core).
- Errores **permanentes** (validación, versión no soportada, referencia de negocio imposible) se
  lanzan como una excepción propia no reintentable (clasificada como tal en la política de
  reintentos) para ir directo a DLQ.
- Errores **transitorios** (`TransientDataAccessException`, `CannotGetJdbcConnectionException`,
  timeouts SMTP) se reintentan.
- `prefetch` y `concurrency` del contenedor configurables por servicio (defaults conservadores en
  local; documentar en `capacity-plan.md`).
- Con hilos virtuales habilitados, verificar que el contenedor de listeners los use o configurar
  su `TaskExecutor` — **pendiente de verificación** en la documentación de Spring AMQP para Boot
  4.1.

### 8. Configuración (`application.yml` de cada servicio)

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER:coldguard}
    password: ${RABBITMQ_PASSWORD:coldguard}
    publisher-confirm-type: correlated
    publisher-returns: true
    template.mandatory: true
coldguard:
  outbox:
    enabled: true
    poll-interval: 500ms
    batch-size: 100
    confirm-timeout: 5s
    retention: 7d
  inbox:
    retention: 7d
```

## Fuera de alcance

CDC/Debezium (ADR-009 lo deja como evolución); reprocesamiento automático desde DLQ (se hace
manual desde la consola de RabbitMQ, documentado en el runbook de SPEC-011).

## Criterios de aceptación (validación local manual)

1. Con RabbitMQ detenido, una operación que emite evento **termina con éxito** y deja la fila en
   `outbox_event` sin `published_at`; al reiniciar RabbitMQ, el relay la publica sin intervención.
2. Republicar manualmente (consola RabbitMQ) el mismo mensaje dos veces produce **un solo**
   efecto de negocio y una sola fila en `processed_message`.
3. Un mensaje con JSON inválido termina en la DLQ de la cola consumidora sin bloquear los
   siguientes.
4. Matar el contenedor consumidor durante el procesamiento no pierde el mensaje (redelivery).
5. El gauge `coldguard.outbox.pending` es visible en `/actuator/prometheus` (tras SPEC-011).

## Tareas

1. D-01 aprobado → implementar en `libs/coldguard-commons` con auto-configuración; si no, en
   `infrastructure` de cada servicio con nombres idénticos.
2. Migraciones `outbox_event` / `processed_message` en cada esquema que lo necesite.
3. Configuración AMQP + declaración de topología.
4. Documentar runbook de DLQ (SPEC-011).

## Riesgos

- `FOR UPDATE SKIP LOCKED` exige que el relay corra en transacción; con hilos virtuales evitar
  bloqueos con `synchronized` en el publicador.
- Sin limpieza, `outbox_event` y `processed_message` crecen indefinidamente (mitigado por las
  tareas de retención).
