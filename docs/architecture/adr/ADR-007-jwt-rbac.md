# ADR-007 Autenticación y autorización: JWT con Spring Security y RBAC

## Contexto
RNF-003 (`docs/requirements/non-functional-requirements.md`) exige "RBAC, autenticación y auditoría", pero ningún documento definía el mecanismo concreto. Quedó registrado como riesgo R-007. El stack del proyecto (CLAUDE.md) es Java 25 / Spring Boot.

## Problema
¿Qué mecanismo de autenticación y autorización se usa en el MVP para cumplir RNF-003, siendo consistente con "REST at the edge, gRPC internally" y sin depender de un proveedor de identidad cloud (para no introducir dependencia de Azure en el MVP)?

## Opciones consideradas
1. **Sesiones de servidor con cookies**: sencillo, pero encaja peor con un Gateway REST desacoplado de los servicios internos y con llamadas gRPC internas.
2. **JWT con Spring Security, emitido y validado localmente, con roles embebidos (RBAC)**: stateless, se propaga naturalmente entre el Gateway y los servicios internos, es coherente con contratos gRPC (metadata) y no requiere un proveedor de identidad externo en el MVP.
3. **OIDC delegado a un proveedor externo (ej. Azure AD/Entra ID)**: introduce dependencia cloud no aprobada para el MVP, contradice la política de "no Azure sin aprobación explícita".

## Decisión
Se adopta la **opción 2**: JWT emitido y validado con Spring Security, con roles (RBAC) embebidos en el token, para autenticación y autorización en el MVP. Los roles reflejan los actores del dominio (Supervisor, Operador, Técnico de mantenimiento, Auditor, Administrador) definidos en `docs/business/business-analysis.md`.

## Consecuencias
- El Gateway (ADR-008) es responsable de validar el JWT en el borde; los servicios internos confían en la identidad/roles propagados (vía metadata gRPC) o revalidan el token según se defina en implementación.
- RBAC se aplica por caso de uso: cada CU de `docs/requirements/use-cases.md` queda asociado a un rol autorizado (a completar en la implementación, sin inventar roles nuevos fuera de los actores ya definidos).
- Requiere una política de expiración/rotación de tokens y manejo seguro de la clave de firma (sin commitear secretos, regla ya vigente en CLAUDE.md).
- La auditoría (RN-008) debe registrar el usuario/rol autenticado en cada transición relevante.

## Riesgos
- Revocación de tokens antes de expiración no está resuelta (JWT stateless no soporta revocación inmediata sin estado adicional); debe abordarse en implementación o aceptarse como limitación del MVP.
- Gestión de claves de firma en entorno local (Docker Compose) requiere disciplina para no exponerlas en logs ni en el repositorio.
- Falta aún definir el mapeo explícito rol→caso de uso; sin eso, RBAC queda declarado pero no operacionalizado.

## Related ADRs
- ADR-008 (el Gateway es responsable de validar el JWT emitido según esta decisión, en el borde del sistema).

## Evolución futura a Azure
En una eventual migración cloud, la emisión de JWT puede delegarse a Azure AD/Entra ID (OIDC) sin cambiar el modelo de autorización basado en roles ya validado en el MVP, siempre que los claims de rol se mantengan compatibles. No se crean recursos Azure como parte de esta decisión; queda sujeta a aprobación explícita.
