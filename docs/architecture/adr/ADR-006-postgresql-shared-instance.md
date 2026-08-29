# ADR-006 Estrategia de persistencia: una instancia PostgreSQL con ownership lógico por servicio

## Contexto
El C4 (`docs/architecture/container-diagram.md`) muestra Asset Service, Telemetry Service e Incident Service apuntando todos a `DB[(PostgreSQL)]`, sin especificar si es una instancia compartida sin aislamiento o instancias separadas. Quedó registrado como riesgo R-006.

## Problema
¿Cómo se persisten los datos de cada servicio en el MVP, dado que CLAUDE.md exige "ports and adapters" y límites de servicio explícitos, pero el runbook local (`docs/operations/runbooks.md`) solo levanta una dependencia PostgreSQL vía Docker Compose?

## Opciones consideradas
1. **Una instancia PostgreSQL por servicio** (contenedor propio por servicio): aislamiento fuerte, pero multiplica el consumo de recursos locales y la complejidad del `docker-compose.yml` para un MVP académico.
2. **Una instancia PostgreSQL compartida, sin separación lógica**: mínimo esfuerzo, pero permite acoplamiento accidental entre servicios (consultas cruzadas, dependencias implícitas de esquema).
3. **Una instancia PostgreSQL compartida, con ownership lógico de esquema/tablas por servicio**: cada servicio (Asset, Telemetry, Incident) tiene su propio esquema (`asset`, `telemetry`, `incident`) y solo accede a sus propias tablas; no hay joins ni foreign keys entre esquemas de distintos servicios.

## Decisión
Se adopta la **opción 3**: una instancia PostgreSQL local, con ownership lógico de esquemas/tablas por servicio durante el MVP. Cada servicio gestiona sus propias migraciones dentro de su esquema y no accede directamente a tablas de otro esquema.

## Consecuencias
- Simplifica `docker-compose.yml` (un solo contenedor PostgreSQL) manteniendo el principio de límites de servicio a nivel lógico.
- Cualquier dato que un servicio necesite de otro debe obtenerse vía gRPC (síncrono) o evento (asíncrono), nunca por acceso directo a esquema ajeno — refuerza "ports and adapters".
- Requiere disciplina de migraciones por esquema (ej. Flyway/Liquibase con `schema` por servicio) para evitar colisiones de nombres de tabla.
- El C4 debe actualizarse en una edición futura para reflejar los tres esquemas dentro de la misma instancia.

## Riesgos
- Un solo punto de fallo de infraestructura (la instancia PostgreSQL) afecta a los tres servicios simultáneamente en el entorno local.
- Riesgo de que, por conveniencia, alguien introduzca un join o FK cruzando esquemas, rompiendo el aislamiento lógico; debe verificarse en revisión de código.
- Migraciones concurrentes de distintos servicios sobre la misma instancia requieren coordinación de despliegue.

## Related ADRs
- ADR-001 (monorepo backend; los límites lógicos de esquema aquí definidos refuerzan los límites de servicio dentro de ese monorepo).
- ADR-009 (la tabla de Outbox de cada servicio vive dentro de su propio esquema lógico, respetando este ownership).

## Evolución futura a Azure
**Confirmado**: Azure es el proveedor cloud objetivo para el despliegue planificado; el
aprovisionamiento y despliegue permanecen pendientes de ejecución. Ningún servicio Azure concreto
de base de datos está seleccionado todavía: en esa migración, cada esquema lógico es candidato
ilustrativo a convertirse en una base de datos administrada independiente por servicio, sin
cambios en el modelo de datos de cada servicio (ya que hoy no hay dependencias cruzadas de
esquema). No se crean recursos Azure como parte de esta decisión; queda sujeta a aprobación
explícita y a un ADR de migración posterior.

## Actualización posterior
**Fecha por confirmar** (DEC-002, `docs/planning/decisions-log.md`).

**Azure Database for PostgreSQL Flexible Server** queda confirmado como la plataforma planificada
para PostgreSQL, precisando el candidato ilustrativo genérico de la sección anterior. Esto no
cambia el modelo de ownership lógico de esquema por servicio decidido en este ADR (una instancia,
sin joins/FK cruzados); Audit Log (DEC-005) sigue el mismo patrón: PostgreSQL compartido con
ownership lógico de esquema propio, sin almacenamiento inmutable ni WORM. Ningún recurso Azure se
crea como parte de esta actualización.
