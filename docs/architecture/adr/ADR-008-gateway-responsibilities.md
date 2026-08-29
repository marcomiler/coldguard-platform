# ADR-008 Responsabilidades del Gateway

## Contexto
El C4 (`docs/architecture/container-diagram.md`) incluye un `GW[Gateway]` como punto de entrada, y CLAUDE.md fija la regla "REST at the edge, gRPC internally", pero ningún documento detallaba qué hace exactamente el Gateway. Quedó registrado como riesgo R-008. RNF-002 exige correlación/trazabilidad completa y RNF-003 exige RBAC/autenticación (ver ADR-007).

## Problema
¿Qué responsabilidades tiene el Gateway, y cuáles quedan explícitamente fuera de su alcance, para evitar que absorba lógica de dominio que le corresponde a Asset/Telemetry/Incident/Notification Service?

## Opciones consideradas
1. **Gateway "delgado"**: solo expone REST, valida JWT (ADR-007), enruta a los servicios internos vía gRPC y propaga correlation/trace context (RNF-002). Sin reglas de negocio.
2. **Gateway "grueso"** (BFF con lógica de agregación/orquestación de negocio): mayor riesgo de duplicar o desalinear reglas de negocio (RN-001 a RN-014) que ya viven en los servicios de dominio.
3. **Sin Gateway, exposición REST directa por cada servicio**: contradice el C4 existente y la regla "REST at the edge, gRPC internally", y dispersa la validación de JWT en cada servicio.

## Decisión
Se adopta la **opción 1**: el Gateway es el punto de entrada REST del sistema, responsable de:
- Validar el JWT (ADR-007) y rechazar peticiones no autenticadas/no autorizadas antes de enrutar.
- Enrutar (routing) las peticiones REST hacia las llamadas gRPC internas correspondientes.
- Propagar correlation ID y trace context (RNF-002) hacia los servicios internos.

El Gateway **no** contiene reglas de negocio (RN-001 a RN-014 permanecen exclusivamente en los servicios de dominio).

## Consecuencias
- Los servicios internos (Asset, Telemetry, Incident, Notification) permanecen desacoplados de HTTP/REST, exponiendo solo contratos gRPC (ADR-003).
- Cualquier cambio de regla de negocio se hace en el servicio de dominio correspondiente, no en el Gateway.
- El Gateway se convierte en un componente crítico de disponibilidad: si cae, cae el único punto de entrada del sistema.

## Riesgos
- Tentación futura de agregar lógica de agregación/orquestación en el Gateway "por conveniencia", erosionando el límite definido aquí; debe vigilarse en revisión de código.
- Punto único de fallo para autenticación: un bug en la validación de JWT del Gateway bloquea todo acceso externo.
- Propagación incorrecta de correlation/trace context rompería RNF-002 sin que sea evidente hasta depurar un incidente en producción.

## Related ADRs
- ADR-002 (el frontend, en repositorio separado, consume al Gateway como único punto de entrada REST).
- ADR-003 (el Gateway traduce las peticiones REST externas a llamadas gRPC internas).
- ADR-007 (el Gateway valida el JWT emitido según esa decisión, antes de enrutar).

## Evolución futura a Azure
**Confirmado**: Azure es el proveedor cloud objetivo para el despliegue planificado; el
aprovisionamiento y despliegue permanecen pendientes de ejecución. En esa migración, el Gateway
es candidato ilustrativo a evolucionar hacia un servicio de gestión de API de Azure, manteniendo
las mismas tres responsabilidades (auth, routing, propagación de contexto) sin trasladar lógica
de dominio hacia la capa cloud; no hay una selección de servicio concreto todavía. No se crean
recursos Azure como parte de esta decisión; queda sujeta a aprobación explícita.

## Actualización posterior
**Fecha por confirmar** (DEC-004, `docs/planning/decisions-log.md`).

Identity & Access (usuarios, roles, asignaciones de acceso de RF-013/CU-014) queda confirmado como
un módulo separado del Gateway, no como una responsabilidad del Gateway. Esto refuerza la
decisión de este ADR: el Gateway permanece delgado (auth en el borde, routing, propagación de
contexto), sin absorber la gestión de usuarios/roles ni ninguna otra lógica de negocio.

**Segunda actualización (DEC-008)**: Identity & Access se aloja como módulo interno de Incident
Service, no como microservicio propio. Sigue sin ser responsabilidad del Gateway, que conserva
exactamente las mismas tres responsabilidades (auth, routing, propagación de contexto).
