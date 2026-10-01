# SPEC-008 — Notification Service

## Objetivo

Consumir `NotificationRequested`, enviar el correo a cada destinatario mediante un adaptador
desacoplado de proveedor (SMTP → Mailpit en local), registrar el estado de envío de forma
idempotente y publicar `NotificationFailed` cuando el envío falla de forma definitiva.

## Trazabilidad

RF-008; CU-003, CU-005; ADR-004, ADR-005, ADR-006, ADR-009; DEC-007 (Mailpit solo local;
proveedor productivo fuera de esta fase), DEC-012 (esquema `notification`).

## Estado actual verificado

Módulo vacío (placeholders); `application.yml` sin esquema (`currentSchema` no fijado); Compose ya
inyecta `SMTP_HOST=mailpit`, `SMTP_PORT=1025` y depende de `mailpit`.

## Decisiones requeridas

- **D-10** destinatarios (resueltos por Incident Service; este servicio no conoce usuarios ni
  roles).
- **Semántica de entrega**: el envío de correo no es transaccional con PostgreSQL. Propuesta:
  **al menos una vez** — si el proceso cae después de enviar y antes de marcar `SENT`, la
  re-entrega puede duplicar un correo. Se acepta y se documenta (coherente con ADR-009).
- **Esquema** `notification`: DEC-012 ya lo decide; actualizar `bounded-contexts.md` (hoy "sin
  esquema propio confirmado").

## Persistencia (esquema `notification`)

```sql
CREATE TABLE notification.notification (
  id UUID PRIMARY KEY,
  notification_request_id UUID NOT NULL,
  incident_id UUID NOT NULL, notification_type VARCHAR(30) NOT NULL, priority VARCHAR(5) NULL,
  status VARCHAR(20) NOT NULL,                -- PENDING | SENT | PARTIALLY_SENT | FAILED
  created_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ NULL, version BIGINT NOT NULL);
CREATE UNIQUE INDEX ux_notification_request ON notification.notification (notification_request_id);

CREATE TABLE notification.notification_delivery (
  id UUID PRIMARY KEY, notification_id UUID NOT NULL REFERENCES notification.notification(id),
  recipient_user_id VARCHAR(100) NOT NULL,     -- no se almacena el correo
  status VARCHAR(20) NOT NULL,                -- PENDING | SENT | FAILED
  attempts INT NOT NULL DEFAULT 0, last_error_category VARCHAR(30) NULL,
  sent_at TIMESTAMPTZ NULL);
CREATE UNIQUE INDEX ux_delivery_recipient ON notification.notification_delivery (notification_id, recipient_user_id);
```

+ `outbox_event` (para `NotificationFailed`) y `processed_message` si se usa el guard genérico
(SPEC-003). `spring.datasource.url` con `currentSchema=notification`; Flyway
`schemas: notification`.

## Diseño

Capas:
- `application`: `HandleNotificationRequested`, puerto `NotificationSender`
  (`send(Recipient, NotificationContent)` → resultado `Sent | TransientFailure | PermanentFailure`,
  sealed), puerto `NotificationTemplateRenderer`.
- `infrastructure`: `SmtpNotificationSender` (`spring-boot-starter-mail`, `JavaMailSender`),
  seleccionado por `coldguard.notification.channel=smtp`; el futuro adaptador de Azure
  Communication Services implementará el mismo puerto (DEC-007) sin tocar `application`.
- Plantillas de texto plano en `src/main/resources/templates/` (`incident-created.txt`,
  `incident-escalated.txt`) renderizadas con sustitución simple; sin motor de plantillas nuevo
  (KISS). Contenido: tipo, `incidentId`, prioridad, `assetId`, `sensorId`. Sin datos sensibles.

Flujo del consumidor (`notification-service.notification-requested`):

1. Validar envelope/versión (inválido → DLQ).
2. **Tx 1**: insertar `notification` + `notification_delivery` (PENDING) si no existe por
   `notification_request_id`; si existe y está `SENT`/`FAILED` → no hacer nada (duplicado) y
   confirmar.
3. **Fuera de transacción**: enviar a cada destinatario con `PENDING` (correo individual por
   destinatario, sin exponer la lista completa en `To`).
4. **Tx 2**: actualizar cada `notification_delivery` y el estado agregado.
   - Todos enviados → `SENT`.
   - Fallo **permanente** de algún destinatario (dirección rechazada) → ese delivery `FAILED`,
     `NotificationFailed(PERMANENT)` al Outbox; el resto continúa.
   - Fallo **transitorio** (conexión, timeout) → se lanza excepción reintentable: la re-entrega
     del broker (backoff de SPEC-003) reintenta **solo** los `PENDING`.
5. Reintentos agotados → el *recoverer* marca pendientes como `FAILED` y escribe
   `NotificationFailed(RETRIES_EXHAUSTED)` en el Outbox antes de enviar el mensaje a DLQ.

Timeouts SMTP obligatorios (propiedades JavaMail `mail.smtp.connectiontimeout`,
`mail.smtp.timeout`, `mail.smtp.writetimeout` vía `spring.mail.properties`), para que un servidor
lento no bloquee el consumidor indefinidamente. Remitente configurable.

Logs: `notificationRequestId`, `incidentId`, tipo, resultado; **nunca** el correo del destinatario
ni el cuerpo completo (RNF-008).

Salud: el indicador de salud de correo no debe formar parte del grupo de *liveness* (un Mailpit
caído no debe reiniciar el servicio); los fallos se manejan con reintentos.

## Configuración

```yaml
server.port: 8084
spring:
  datasource.url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:coldguard}?currentSchema=notification
  flyway.schemas: notification
  mail:
    host: ${SMTP_HOST:localhost}
    port: ${SMTP_PORT:1025}
    properties:
      mail.smtp.connectiontimeout: 5000
      mail.smtp.timeout: 5000
      mail.smtp.writetimeout: 5000
coldguard.notification:
  channel: smtp
  from: ${NOTIFICATION_FROM:coldguard-local@example.invalid}
```

## Criterios de aceptación (validación local manual)

1. Al crear un incidente, Mailpit (`http://localhost:8025`) muestra un correo por cada Supervisor
   de operaciones habilitado; al escalar, uno por cada Técnico de mantenimiento.
2. Re-publicar el mismo `NotificationRequested` no genera correos adicionales (estado `SENT`).
3. Con Mailpit detenido, el mensaje se reintenta; al reiniciarlo antes de agotar reintentos, el
   correo llega una vez; si se agotan, la notificación queda `FAILED`, se publica
   `NotificationFailed` y el mensaje queda en la DLQ.
4. Un `NotificationFailed` aparece en la bitácora de auditoría (SPEC-007).
5. Ningún log contiene direcciones de correo.

## Tareas

1. POM: `spring-boot-starter-mail`, `spring-boot-starter-amqp`, Flyway.
2. Migraciones `V1__notification_schema.sql`, `V2__outbox_inbox.sql`.
3. Puerto/adaptador de envío, plantillas, consumidor, recoverer.
4. Configuración y healthchecks (Compose).
5. Actualizar `bounded-contexts.md` y `tech-stack.md` (Mailpit pasa de "Planificada" a
   implementado en local).

## Riesgos

- Duplicados de correo posibles por la semántica al-menos-una-vez (aceptado).
- `notification_delivery` crece con cada notificación; sin retención definida (misma situación
  que D-16).
