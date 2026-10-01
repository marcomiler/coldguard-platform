# ADR-007 Autenticación y autorización: JWT con Spring Security y RBAC

## Contexto
RNF-003 (`docs/quality/non-functional-requirements.md`) exige "RBAC, autenticación y auditoría", pero ningún documento definía el mecanismo concreto. Quedó registrado como riesgo R-007. El stack del proyecto (CLAUDE.md) es Java 25 / Spring Boot.

## Problema
¿Qué mecanismo de autenticación y autorización se usa en el MVP para cumplir RNF-003, siendo consistente con "REST at the edge, gRPC internally" y sin depender de un proveedor de identidad cloud (para no introducir dependencia de Azure en el MVP)?

## Opciones consideradas
1. **Sesiones de servidor con cookies**: sencillo, pero encaja peor con un Gateway REST desacoplado de los servicios internos y con llamadas gRPC internas.
2. **JWT con Spring Security, emitido y validado localmente, con roles embebidos (RBAC)**: stateless, se propaga naturalmente entre el Gateway y los servicios internos, es coherente con contratos gRPC (metadata) y no requiere un proveedor de identidad externo en el MVP.
3. **OIDC delegado a un proveedor externo (ej. Azure AD/Entra ID)**: introduce dependencia cloud no aprobada para el MVP, contradice la política de "no Azure sin aprobación explícita".

## Decisión
Se adopta la **opción 2**: JWT emitido y validado con Spring Security, con roles (RBAC) embebidos en el token, para autenticación y autorización en el MVP. Los roles reflejan los actores del dominio (Supervisor, Operador, Técnico de mantenimiento, Auditor, Administrador) definidos en `docs/product/stakeholders.md`.

## Consecuencias
- El Gateway (ADR-008) es responsable de validar el JWT en el borde; los servicios internos confían en la identidad/roles propagados (vía metadata gRPC) o revalidan el token según se defina en implementación.
- RBAC se aplica por caso de uso: cada CU de `docs/domain/use-cases.md` queda asociado a un rol autorizado (a completar en la implementación, sin inventar roles nuevos fuera de los actores ya definidos).
- Requiere una política de expiración/rotación de tokens y manejo seguro de la clave de firma (sin commitear secretos, regla ya vigente en CLAUDE.md).
- La auditoría (RN-008) debe registrar el usuario/rol autenticado en cada transición relevante.

## Riesgos
- Revocación de tokens antes de expiración no está resuelta (JWT stateless no soporta revocación inmediata sin estado adicional); debe abordarse en implementación o aceptarse como limitación del MVP.
- Gestión de claves de firma en entorno local (Docker Compose) requiere disciplina para no exponerlas en logs ni en el repositorio.
- Falta aún definir el mapeo explícito rol→caso de uso; sin eso, RBAC queda declarado pero no operacionalizado.

## Related ADRs
- ADR-008 (el Gateway es responsable de validar el JWT emitido según esta decisión, en el borde del sistema).

## Evolución futura a Azure
**Confirmado**: Azure es el proveedor cloud objetivo para el despliegue planificado; el
aprovisionamiento y despliegue permanecen pendientes de ejecución. En esa migración, la emisión
de JWT es candidata ilustrativa a delegarse a un proveedor de identidad de Azure (por ejemplo,
Entra ID) sin cambiar el modelo de autorización basado en roles ya validado en el MVP, siempre
que los claims de rol se mantengan compatibles; no hay una selección de servicio concreto
todavía. No se crean recursos Azure como parte de esta decisión; queda sujeta a aprobación
explícita.

## Actualización posterior
**Fecha por confirmar** (DEC-004, `docs/planning/decisions-log.md`).

Un módulo o servicio lógico **Identity & Access** queda confirmado como dueño de usuarios, roles y
asignaciones de acceso (RF-013/CU-014); no se asigna esta responsabilidad a Asset Service. Esto no
cambia la decisión de este ADR: el Gateway sigue siendo el único componente que valida el JWT en
el borde y propaga la identidad (ADR-008); Identity & Access es dueño de los datos de usuarios y
roles, no del mecanismo de validación en el borde. La autenticación/autorización real de endpoints
sigue perteneciendo a APF2 (RF-015/CU-016), sin cambio de fase.

**Segunda actualización (DEC-008)**: Identity & Access se aloja como **módulo interno de Incident
Service**, no como microservicio separado ni bajo arquitectura hexagonal formal. No cambia el
ownership fijado arriba ni la responsabilidad del Gateway en el borde; cambia únicamente la forma
de despliegue del módulo.

**Tercera actualización (D-02 y D-03, `docs/specs/README.md`)**: se cierran los dos puntos que este
ADR dejaba abiertos para la implementación local.
- **Emisión del JWT**: el Gateway firma el token (RS256) después de que Identity & Access (módulo
  interno de Incident Service) verifique las credenciales por gRPC. Identity & Access no maneja
  claves de firma; los servicios internos siguen sin parsear JWT ni añadir el starter de seguridad.
  El par de claves local se genera con los scripts de desarrollo y nunca se versiona.
- **Propagación de identidad**: el Gateway envía la identidad validada como metadata gRPC
  (`x-actor-id`, `x-actor-roles`) sobre el canal mTLS existente; nunca como campo de request ni
  reenviando el token. Los servicios internos solo aceptan esa metadata del cliente con identidad
  `gateway`. Esto cierra el punto abierto de DEC-014.
- Sin refresh tokens ni revocación previa a la expiración en el MVP: se acepta como limitación
  (riesgo ya listado arriba). No cambia el modelo RBAC ni el ownership de usuarios y roles.
- No se crea ningún recurso Azure.
