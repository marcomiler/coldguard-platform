# ADR-005 Comunicación Incident Service → Notification Service mediante eventos

## Contexto
El C4 (`docs/architecture/container-diagram.md`) muestra una relación directa entre Incident Service y Notification Service, mientras que `docs/domain/commands-events.md` define `NotificationRequested` y `NotificationFailed` como eventos. Esta ambigüedad quedó registrada como riesgo R-005 en `docs/quality/risk-register.md`.

## Problema
¿Cómo debe comunicarse Incident Service con Notification Service para disparar notificaciones (RF-008), sin acoplar el flujo de incidentes a la disponibilidad del canal de notificación (email vía Mailpit) ni violar la regla de "eventos para workflows cross-service" de CLAUDE.md?

## Opciones consideradas
1. **gRPC síncrono** (ADR-003 aplicado directamente): simple, pero acopla la creación/escalación de incidentes a la disponibilidad de Notification Service; un fallo de notificación bloquearía o degradaría el flujo de incidentes.
2. **Evento asíncrono en RabbitMQ**: Incident Service publica `NotificationRequested` al ocurrir `IncidentCreated`/`IncidentEscalated`; Notification Service consume el evento de forma independiente y emite `NotificationFailed` si falla.
3. **Híbrido** (gRPC con reintentos locales): añade complejidad de resiliencia en el propio Incident Service sin ganar desacoplamiento real.

## Decisión
Se adopta la **opción 2**: Incident Service → Notification Service se comunican mediante eventos asíncronos en RabbitMQ (`NotificationRequested`, `NotificationFailed`), no mediante llamada gRPC directa.

## Consecuencias
- Notification Service puede fallar o estar caído sin bloquear la creación/escalación de incidentes.
- Se requiere que los consumidores sean idempotentes (regla ya establecida en CLAUDE.md).
- El C4 (`docs/architecture/container-diagram.md`) debe actualizarse en una edición futura para reflejar `IS → MQ → NS` en vez de `IS → NS` directo.
- La confiabilidad de la publicación del evento depende de ADR-009 (Transactional Outbox).

## Riesgos
- Retardo entre la creación del incidente y el envío efectivo de la notificación (eventual consistency).
- Necesidad de monitoreo específico sobre la cola de `NotificationRequested` para detectar acumulación o consumidores caídos.
- Sin Outbox (ADR-009) implementado, existe riesgo de pérdida de eventos si falla la publicación tras el commit de la transacción del incidente.

## Related ADRs
- ADR-003 (gRPC reservado para comunicación síncrona interna; esta decisión es la excepción explícita asíncrona).
- ADR-004 (RabbitMQ es el broker local sobre el que viajan estos eventos).
- ADR-009 (Transactional Outbox garantiza la publicación confiable de `NotificationRequested`).

## Evolución futura a Azure
**Confirmado**: Azure es el proveedor cloud objetivo para el despliegue planificado; el
aprovisionamiento y despliegue permanecen pendientes de ejecución. En esa migración, RabbitMQ es,
en principio, reemplazable por un servicio de mensajería equivalente en Azure (ver ADR-004; sin
selección de servicio concreto todavía) manteniendo el mismo contrato de eventos
(`NotificationRequested`, `NotificationFailed`), sin cambios en la lógica de dominio de Incident
Service ni Notification Service. No se crean recursos Azure como parte de esta decisión; queda
sujeta a aprobación explícita.
