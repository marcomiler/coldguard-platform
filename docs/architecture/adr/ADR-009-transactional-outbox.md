# ADR-009 Publicación confiable de eventos mediante Transactional Outbox

## Contexto
El modelo de eventos (`docs/domain/commands-events.md`) es central para los workflows cross-service (`TelemetryThresholdBreached`, `IncidentCreated`, `IncidentAcknowledged`, `IncidentEscalated`, `IncidentClosed`, `NotificationRequested`, `NotificationFailed`), y ADR-005 decide que Incident Service → Notification Service se comunican por evento. CLAUDE.md exige "evitar transacciones distribuidas" y "preferir consistencia eventual". Quedó registrado como riesgo R-009.

## Problema
¿Cómo se garantiza que un servicio (ej. Incident Service) no pierda un evento cuando falla entre el commit de su transacción de base de datos y la publicación del mensaje en RabbitMQ, sin usar una transacción distribuida (2PC) entre PostgreSQL y RabbitMQ?

## Opciones consideradas
1. **Publicar el evento después del commit, sin garantía adicional**: simple, pero si el proceso falla entre el commit y la publicación, el evento se pierde silenciosamente (ej. un `IncidentCreated` sin `NotificationRequested` correspondiente).
2. **Transacción distribuida (2PC) entre PostgreSQL y RabbitMQ**: elimina la pérdida de eventos, pero contradice explícitamente la regla de CLAUDE.md de evitar transacciones distribuidas, y añade complejidad y acoplamiento fuerte al broker.
3. **Transactional Outbox**: el servicio escribe el evento en una tabla `outbox` dentro de la misma transacción local que el cambio de estado de dominio (ej. crear el incidente); un proceso independiente (poller o CDC) lee la tabla `outbox` y publica a RabbitMQ, marcando el registro como publicado.

## Decisión
Se adopta la **opción 3**: Transactional Outbox para la publicación confiable de eventos en los servicios que emiten eventos de dominio (Telemetry Service, Incident Service).

## Consecuencias
- El cambio de estado de dominio y el registro del evento a publicar quedan atómicos dentro de la misma transacción PostgreSQL (coherente con ADR-006: outbox vive en el esquema del servicio emisor).
- Se requiere un mecanismo de publicación (poller periódico como opción más simple para el MVP) que lea la tabla `outbox` y publique a RabbitMQ, marcando o eliminando entradas publicadas.
- Los consumidores deben seguir siendo idempotentes (regla ya vigente), ya que Outbox garantiza *al menos una entrega*, no entrega exactamente una vez.
- Introduce una tabla adicional por servicio emisor y un componente de publicación a operar y monitorear.

## Riesgos
- Si el poller de outbox falla o se detiene, los eventos se acumulan sin publicarse; requiere monitoreo (métricas de rezago de la tabla outbox).
- Duplicados posibles en el borde (reintento de publicación) si el poller falla justo después de publicar pero antes de marcar como publicado; mitigado por idempotencia en consumidores.
- Complejidad adicional en el MVP académico: debe evaluarse el esfuerzo de implementación frente al alcance de los sprints definidos en `docs/planning/roadmap.md`.

## Related ADRs
- ADR-004 (RabbitMQ es el broker destino de la publicación confiable descrita aquí).
- ADR-005 (Incident Service → Notification Service depende de este patrón para no perder eventos).
- ADR-006 (la tabla `outbox` de cada servicio vive dentro de su propio esquema lógico).
- ADR-011 (orden por agregado: `aggregateVersion` y bloqueo de eventos estacionados).

## Evolución futura a Azure
**Confirmado**: Azure es el proveedor cloud objetivo para el despliegue planificado; el
aprovisionamiento y despliegue permanecen pendientes de ejecución. En esa migración, el patrón
Outbox se mantiene igual; solo cambiaría el destino de publicación (ver ADR-004; sin selección de
servicio concreto todavía), y el poller es candidato ilustrativo a evolucionar hacia una solución
de Change Data Capture nativa de Azure si se justifica. No se crean recursos Azure como parte de
esta decisión; queda sujeta a aprobación explícita.
