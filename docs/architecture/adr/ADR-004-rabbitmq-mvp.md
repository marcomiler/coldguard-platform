# ADR-004 RabbitMQ para el MVP

## Contexto
CLAUDE.md declara RabbitMQ como broker local del MVP, con Azure Service Bus como evolución cloud. `docs/architecture/events.md` define el catálogo de eventos del dominio (`IncidentCreated`, `IncidentAcknowledged`, `IncidentEscalated`, `IncidentClosed`, `NotificationRequested`, `NotificationFailed`, entre otros). ADR-005 decide que Incident Service → Notification Service se comunican por evento, y ADR-009 decide Transactional Outbox para la publicación confiable de esos eventos.

## Problema
¿Qué broker de mensajería se usa en el MVP para soportar los workflows cross-service basados en eventos, ejecutándose de forma reproducible en local (RNF-001) y sin depender de un servicio cloud?

## Opciones consideradas
1. **Apache Kafka**: mayor throughput y capacidad de replay de eventos, pero sobredimensionado y más complejo de operar localmente para el alcance académico del MVP.
2. **Azure Service Bus directamente**: introduce dependencia cloud desde el MVP, contradice la política de "no Azure sin aprobación explícita" (CLAUDE.md) y el requisito de ejecución reproducible con Docker Compose (RNF-001).
3. **RabbitMQ local** (elegido): ligero, fácil de levantar con Docker Compose, suficiente para los patrones de mensajería (colas, exchanges) que requiere el catálogo de eventos actual.

## Decisión
El MVP usa RabbitMQ, ejecutado localmente vía Docker Compose (`docs/operations/runbook-local.md`), como broker de eventos para todos los workflows cross-service definidos en `docs/architecture/events.md`.

## Consecuencias
- Todos los eventos de `docs/architecture/events.md` se publican y consumen vía RabbitMQ en el entorno local.
- Los servicios deben implementar consumidores idempotentes (regla de CLAUDE.md), ya que RabbitMQ combinado con Transactional Outbox (ADR-009) garantiza entrega *al menos una vez*, no exactamente una vez.
- El diseño de colas/exchanges debe mantenerse simple y documentado junto con el catálogo de eventos existente.

## Riesgos
- RabbitMQ, como único broker local, es un punto de fallo compartido por todos los workflows cross-service del MVP.
- Migrar a Azure Service Bus más adelante requiere validar que los patrones de mensajería usados (exchanges, routing keys) tengan equivalente directo, o ajustar el diseño de mensajería.

## Related ADRs
- ADR-003 (gRPC reservado para comunicación síncrona; RabbitMQ cubre la asíncrona).
- ADR-005 (Incident Service → Notification Service vía eventos en RabbitMQ).
- ADR-009 (Transactional Outbox para publicación confiable sobre RabbitMQ).

## Evolución futura a Azure
RabbitMQ es reemplazable por Azure Service Bus manteniendo el mismo catálogo de eventos y contratos de mensaje, sin cambios en la lógica de dominio de los servicios. No se crean recursos Azure como parte de esta decisión; queda sujeta a aprobación explícita.
