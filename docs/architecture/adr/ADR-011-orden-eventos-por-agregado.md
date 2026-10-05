# ADR-011 Orden de eventos por agregado mediante `aggregateVersion`

## Contexto
RabbitMQ entrega *al menos una vez* (ADR-004) y el Transactional Outbox publica por polling
(ADR-009). Ninguno de los dos garantiza que un consumidor vea los eventos de un mismo agregado en
el orden en que se produjeron: un reintento o una redelivery reubica un mensaje detrás de otros,
un consumidor con más de un hilo los procesa en paralelo, varias instancias del relay pueden
publicar una fila posterior antes que una bloqueada, y un evento estacionado por no ser enrutable
deja de bloquear a los siguientes. El envelope `v1` solo lleva `occurredAt`, que no permite
detectar huecos ni descartar eventos obsoletos de forma fiable. Los consumidores que mantienen
estado por agregado (p. ej. el ciclo de vida del incidente) necesitan esa garantía.

## Problema
¿Cómo garantizan los consumidores un procesamiento correcto por agregado frente a duplicados,
reordenamiento y huecos, sin depender del orden de entrega del broker ni de una única instancia
de relay, y sin transacciones distribuidas?

## Opciones consideradas
1. **Confiar en el orden del broker** (una cola, un consumidor, una instancia del relay): simple,
   pero se rompe con reintentos, redelivery y estacionamientos, y no sobrevive a escalar ni a
   migrar a otro broker.
2. **Particionado/ordenación por clave en el broker** (consistent-hash exchange, colas por
   agregado, Kafka): ordena la entrega pero añade plugins o infraestructura, y no resuelve el
   reordenamiento por reintento. Sobredimensionado para el MVP (ADR-004).
3. **Versión por agregado en el envelope + cursor en el consumidor** (elegida): el productor
   asigna una secuencia monótona por agregado dentro de la transacción de negocio; el consumidor
   aplica un evento solo si es el siguiente esperado.

## Decisión
Se adopta la opción 3.

- **Contrato**: `envelope.v1` incorpora `aggregateVersion` (entero ≥ 1), cambio aditivo y
  compatible. Es opcional mientras los productores se migran; los productores de `commons` lo
  emiten siempre.
- **Productor**: cada esquema emisor tiene `aggregate_sequence(aggregate_type, aggregate_id,
  last_version)`. `OutboxWriter` hace un upsert con incremento en la misma transacción que el
  cambio de estado; el bloqueo de fila serializa a los escritores del mismo agregado. No se deriva
  de `max()` sobre el outbox porque la retención borra filas publicadas. La versión viaja también
  en la columna `aggregate_version` de `outbox_event`.
- **Consumidor**: cada esquema consumidor que lo necesite tiene `aggregate_cursor(consumer,
  aggregate_type, aggregate_id, last_version)`, actualizado en la misma transacción que
  `processed_message`. Con `v ≤ last` el evento es obsoleto y se descarta; con `v = last + 1` se
  aplica y avanza el cursor; con `v > last + 1` hay un hueco y se lanza un error transitorio que
  activa el reintento con backoff y, agotados los intentos, la DLQ. Un evento sin
  `aggregateVersion` se trata como sin orden (compatibilidad).
- **Consumidores sin dependencia de orden** (p. ej. auditoría, solo registra) no usan el cursor.
- **Eventos estacionados**: el relay deja de saltarse un evento estacionado y **bloquea** a los
  siguientes de su agregado hasta que un humano lo resuelva (reactivar o descartar), en lugar de
  publicar los posteriores fuera de orden. Un evento no enrutable es un error de configuración, no
  un estado normal; el agregado bloqueado se expone en `coldguard.outbox.parked`.

## Consecuencias
- Correcto frente a duplicados, reordenamiento y varias instancias de relay, y portable a otro
  broker.
- Una tabla más por esquema emisor y otra por esquema consumidor con estado; el upsert de
  secuencia añade una escritura por evento y serializa los escritores concurrentes del mismo
  agregado.
- Un hueco prolongado detiene el procesamiento de ese agregado (no el de los demás) hasta
  resolverse; requiere el runbook de DLQ y de eventos estacionados (SPEC-011).
- Los eventos publicados antes de este cambio no tienen versión; sus agregados parten de
  `last_version = 0` en el cursor al primer evento versionado (esperable en local, sin migración
  de histórico).

## Riesgos
- Un evento estacionado o perdido por una purga manual deja un hueco permanente; se mitiga con la
  alerta sobre `coldguard.outbox.parked` y el runbook.
- Contención de la fila de secuencia en agregados con muchas escrituras concurrentes; aceptable
  al volumen del MVP.
- Un productor que no use `commons` y omita la versión queda sin garantía de orden hasta que se
  haga obligatoria.

## Related ADRs
- ADR-004 (entrega al menos una vez; esta decisión no depende del orden del broker).
- ADR-009 (Transactional Outbox: se extiende con la secuencia por agregado y el bloqueo de
  estacionados).
- ADR-006 (las tablas de secuencia y cursor viven en el esquema de cada servicio).
- ADR-010 (la implementación vive en la biblioteca técnica compartida).

## Evolución futura a Azure
**Confirmado**: Azure es el proveedor cloud objetivo; el aprovisionamiento permanece pendiente.
El mecanismo es independiente del broker, por lo que se mantiene si RabbitMQ se sustituye por un
servicio de mensajería equivalente. Si éste ofreciera ordenación por sesión o partición, podría
complementarla pero no reemplazaría la detección de huecos y duplicados en el consumidor. No se
crean recursos Azure como parte de esta decisión.
