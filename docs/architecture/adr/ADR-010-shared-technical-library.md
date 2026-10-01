# ADR-010 Librería técnica compartida del monorepo

## Contexto
ADR-001 fija un monorepo con un módulo Maven por servicio, y DEC-011 prohíbe colapsar servicios en
un módulo compartido. Los specs de implementación del MVP local (`docs/specs/`) introducen
mecanismos técnicos idénticos en varios servicios: Transactional Outbox y su relay (ADR-009),
registro de mensajes procesados para consumidores idempotentes, propagación de correlación e
identidad en gRPC/AMQP, y el envelope físico de eventos. Implementarlos por separado en cuatro
servicios duplicaría código sensible a errores de concurrencia y consistencia.

## Problema
¿Se duplica la infraestructura técnica transversal en cada servicio, o se comparte mediante un
módulo común, sin romper los límites de bounded context ni el aislamiento de servicios?

## Opciones consideradas
1. **Duplicar por servicio**: sin acoplamiento de build, pero cuatro copias de Outbox/relay/inbox
   con riesgo de divergencia y correcciones repetidas (viola DRY).
2. **Módulo compartido con lógica de dominio**: reduce más código, pero acopla los contextos y
   contradice DEC-011 y la regla de no crear imports cruzados entre servicios.
3. **Módulo compartido solo técnico** (elegido): `libs/coldguard-commons`, sin ninguna regla de
   negocio ni modelo de dominio.

## Decisión
Se crea el módulo Maven `libs/coldguard-commons` (jar, sin `spring-boot-maven-plugin`), agregado al
reactor raíz. Contiene exclusivamente infraestructura técnica: correlación (`correlationId`,
interceptores gRPC, filtro HTTP), propagación de identidad por metadata gRPC, Outbox (escritor y
relay), registro de mensajes procesados (inbox) y el envelope de eventos. Se activa por
auto-configuración de Spring Boot, con cada bloque habilitable por propiedad
(`coldguard.outbox.enabled`, etc.) para no forzar dependencias (p. ej. AMQP en el Gateway).

Reglas de frontera:
- No contiene entidades, eventos ni reglas de dominio de ningún bounded context.
- Ningún servicio importa clases de otro servicio; solo dependen de `coldguard-commons`.
- Las tablas (`outbox_event`, `processed_message`) siguen viviendo en el esquema de cada servicio
  (ADR-006); el módulo aporta el código, no el almacenamiento.

## Consecuencias
- Un único lugar para corregir y evolucionar Outbox, idempotencia y correlación.
- El build debe copiar `libs/*` en los Dockerfiles y reactor (SPEC-001).
- Un cambio en `coldguard-commons` afecta a todos los servicios que lo usan; requiere versionado
  disciplinado del monorepo (ADR-001) y revisión cuidadosa.

## Riesgos
- Tentación de mover lógica de negocio al módulo "por conveniencia", erosionando los límites de
  contexto; debe vigilarse en revisión de código.
- Acoplamiento de ciclo de vida: todos los servicios se actualizan con el mismo cambio técnico.
- Auto-configuración mal acotada podría activar beans no deseados en servicios que no los usan.

## Related ADRs
- ADR-001 (monorepo backend; esta decisión añade un módulo técnico sin cambiar la estructura).
- ADR-006 (las tablas técnicas viven en el esquema de cada servicio).
- ADR-009 (el Outbox y su relay se implementan en este módulo).

## Evolución futura a Azure
**Confirmado**: Azure es el proveedor cloud objetivo para el despliegue planificado; el
aprovisionamiento y despliegue permanecen pendientes de ejecución. El módulo es independiente del
entorno: no cambia con la migración. Si el destino de mensajería cambiara (ADR-004), solo se
reemplazaría el adaptador de publicación dentro del módulo, sin tocar los servicios. No se crean
recursos Azure como parte de esta decisión.
