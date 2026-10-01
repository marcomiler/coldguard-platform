# SPEC-004 — Identidad, autenticación y RBAC

## Objetivo

Implementar la seguridad real de backend prevista para APF2: usuarios y roles (módulo Identity &
Access dentro de Incident Service), login con emisión de JWT, validación en el borde (Gateway),
RBAC por endpoint (cierra R-014) y propagación de identidad hacia los servicios internos para
auditoría (RN-008) y defensa en profundidad (RN-019).

## Trazabilidad

RF-013, RF-015; CU-014, CU-016; RN-008, RN-016, RN-019; RNF-003; ADR-007, ADR-008; DEC-004,
DEC-008, DEC-010, DEC-011, DEC-014; R-014; `docs/product/stakeholders.md`.

## Estado actual verificado

- `SecurityConfig` (Gateway): solo `POST /api/v1/incidents/*/close` exige
  `ROLE_MAINTENANCE_TECHNICIAN`; todo lo demás `permitAll()`. El `JwtDecoder` real depende de
  `app.security.jwt.issuer-uri` (no configurado) → hoy todo token real falla con 401.
- `ActorRoleClientInterceptor` propaga **solo** la autoridad `ROLE_MAINTENANCE_TECHNICIAN` en
  `x-actor-role`; `ActorRoleServerInterceptor` (Incident) la expone en `io.grpc.Context`.
- `CloseIncidentService` compara el rol con el string `"ROLE_MAINTENANCE_TECHNICIAN"`.
- No existe esquema `identity`, ni usuarios, ni hash de contraseñas, ni endpoint de login.

## Decisiones requeridas

- **D-02 (actualizar ADR-007)** — Emisión del JWT. Recomendación: el **Gateway firma** el token
  (RS256) después de que Identity & Access verifique las credenciales por gRPC. Motivos: un solo
  componente maneja la clave privada; los servicios internos siguen sin parsear JWT
  (`.claude/rules/security.md`); Incident Service no necesita librerías JOSE ni el starter de
  seguridad (DEC-011). Alternativa: Identity firma y expone la clave pública — más piezas sin
  beneficio en local.
- **D-03 (actualizar ADR-007, cierra el punto abierto de DEC-014)** — Propagación por metadata
  gRPC `x-actor-id` + `x-actor-roles` sobre el canal mTLS existente.
- **Rol del Operador**: CU-004/CU-006 dicen que "registra acciones operativas iniciales" y "aporta
  contexto", pero no existe ningún comando, RF ni evento para ello. Este spec le da **solo
  lectura de incidentes**; cualquier comando de Operador requiere primero un RF/CU nuevo.
- **Lectura de incidentes por el Auditor**: no está definida; este spec la restringe a la
  bitácora (CU-009). Confirmar con el PO.

## Diseño

### 1. Identity & Access (incident-service, paquete `com.coldguard.incident.identity`)

Sub-paquetes con las mismas capas (`api`, `application`, `domain`, `infrastructure`) para
respetar la frontera de módulo interno (DEC-008); ningún otro módulo de Incident accede a sus
repositorios, solo a su API de aplicación.

Esquema `identity` (Flyway, `spring.flyway.schemas` incluye `identity`):

```sql
CREATE TABLE identity.user_account (
    id               UUID PRIMARY KEY,
    username         VARCHAR(60)  NOT NULL,
    email            VARCHAR(254) NOT NULL,
    display_name     VARCHAR(120) NOT NULL,
    password_hash    VARCHAR(200) NOT NULL,
    enabled          BOOLEAN      NOT NULL,
    failed_attempts  INT          NOT NULL DEFAULT 0,
    locked_until     TIMESTAMPTZ  NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL
);
CREATE UNIQUE INDEX ux_user_account_username ON identity.user_account (lower(username));
CREATE UNIQUE INDEX ux_user_account_email    ON identity.user_account (lower(email));

CREATE TABLE identity.user_role (
    user_id     UUID        NOT NULL REFERENCES identity.user_account(id),
    role        VARCHAR(40) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    assigned_by VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id, role)
);
CREATE INDEX ix_user_role_role ON identity.user_role (role);
```

(FK dentro del mismo esquema/módulo es válida; ADR-006 prohíbe FK **entre** esquemas.)

Dominio:
- `Role` enum: `OPERATIONS_SUPERVISOR`, `OPERATOR`, `MAINTENANCE_TECHNICIAN`, `AUDITOR`,
  `PLATFORM_ADMIN` (los cinco actores humanos; no se crean otros).
- `UserAccount` con invariantes: username/email no vacíos y normalizados; al menos un rol para
  poder autenticarse; un usuario deshabilitado o bloqueado no autentica.

Casos de uso (`application`):
| Caso | Reglas |
|---|---|
| `VerifyCredentials` | Hash con `PasswordEncoder`; respuesta `UNAUTHENTICATED` **genérica** (no revela si el usuario existe); incrementa `failed_attempts`, bloquea hasta `locked_until` al superar `coldguard.identity.lockout.max-attempts` (configurable) durante `lockout.duration`; reinicia contador al éxito. Comparación de tiempo constante también cuando el usuario no existe (hash ficticio) para evitar enumeración por tiempo |
| `CreateUser` | Solo `PLATFORM_ADMIN`; contraseña inicial validada por política mínima configurable (longitud); auditable |
| `AssignRole` / `RevokeRole` | Solo `PLATFORM_ADMIN`; motivo obligatorio; auditable con valor anterior/posterior (RN-008); un admin no puede revocarse a sí mismo el último rol `PLATFORM_ADMIN` existente (evita bloqueo total del sistema) |
| `SetUserEnabled` | Solo `PLATFORM_ADMIN`; auditable |
| `ListUserContacts(role)` | Interno para D-10; solo usuarios habilitados |

Hash de contraseñas: `org.springframework.security:spring-security-crypto` (jar standalone, **no**
el starter de seguridad, compatible con DEC-011) con `BCryptPasswordEncoder` (fuerza
configurable) envuelto en `DelegatingPasswordEncoder` para poder migrar algoritmo sin romper
hashes existentes.

Auditoría: cada cambio escribe un `AuditRecord` (SPEC-007) **en la misma transacción** mediante
la API del módulo Audit Log. Evento `UserAccessAssignmentChanged` (D-14) solo si se decide
publicarlo; no tiene consumidor externo.

Bootstrap local: `ApplicationRunner` activable con `coldguard.identity.bootstrap.enabled=true`
(solo en Compose/local) que crea, si no existen, un usuario demo por rol con contraseña tomada de
`DEMO_USERS_PASSWORD`; **falla el arranque** si está habilitado y la variable está vacía. Nunca
hay contraseñas en migraciones ni en el repositorio.

### 2. Login y emisión de JWT (Gateway)

- `POST /api/v1/auth/login` `{ "username", "password" }` → `200 { "accessToken", "tokenType":
  "Bearer", "expiresIn" }`; `401` Problem Details genérico ante cualquier fallo.
- El Gateway llama `IdentityService.VerifyCredentials` (gRPC, mTLS, deadline corto) y firma con
  `NimbusJwtEncoder` (ya disponible vía `spring-boot-starter-oauth2-resource-server`).
- Claims: `iss` (configurable, p. ej. `coldguard-local`), `sub` = user id, `preferred_username`,
  `roles` (lista con los nombres del enum, sin prefijo — compatible con el
  `JwtAuthenticationConverter` actual), `iat`, `exp`, `jti`.
- TTL configurable (`coldguard.security.jwt.ttl`); **sin refresh token** en el MVP. La revocación
  previa a expiración no se implementa: se acepta como limitación documentada (riesgo ya listado en
  ADR-007).
- Claves: par RSA en PEM generado por `deploy/scripts/generate-dev-certs.sh` (nuevo directorio
  `deploy/local/certs/jwt/`, no versionado), rutas por `JWT_PRIVATE_KEY_PATH` /
  `JWT_PUBLIC_KEY_PATH`, montado solo en el contenedor del Gateway.
- Validación: `NimbusJwtDecoder.withPublicKey(...)` con validadores de `iss`, `exp`/`nbf` y
  tolerancia de reloj configurable. Se eliminan `issuer-uri` y `pendingJwtDecoder` (sustituidos por
  configuración obligatoria: el Gateway **no arranca** sin clave pública — fail-fast).
- Nunca se registra el token, la contraseña ni el header `Authorization` (RNF-008); el filtro de
  logging de peticiones (si existe) enmascara esos campos.
- Protección básica contra fuerza bruta: el bloqueo por intentos vive en Identity (arriba). No se
  agrega limitador de tasa en el Gateway en el MVP (no hay librería elegida); registrar como
  mejora futura.

### 3. RBAC en el Gateway (cierra R-014)

Política **deny-by-default**: `anyRequest().denyAll()`; cada ruta declarada explícitamente. Las
reglas viven en una única clase de configuración (tabla legible), no dispersas en controladores.

| Ruta (prefijo `/api/v1`) | Método | Rol(es) | CU |
|---|---|---|---|
| `/auth/login` | POST | público | CU-016 |
| `/organizations`, `/sites`, `/assets` (alta/edición) | POST, PUT/PATCH | `PLATFORM_ADMIN` | CU-001, CU-012, CU-013 |
| `/organizations`, `/sites`, `/assets`, `/sensors` (lectura) | GET | `PLATFORM_ADMIN`, `OPERATIONS_SUPERVISOR` | CU-013 |
| `/sensors` (alta/edición), `/sensors/{id}/profile` (PUT) | POST, PUT/PATCH | `PLATFORM_ADMIN` | CU-007, CU-011, CU-013 |
| `/sensors/{id}/status`, `/calibrations`, `/reassignment`, `/retirement` | POST | `PLATFORM_ADMIN` | CU-017 a CU-020 |
| `/sensors/{id}/history` | GET | `PLATFORM_ADMIN` | CU-021 |
| `/sensors/{id}/readings` | GET | `PLATFORM_ADMIN`, `OPERATIONS_SUPERVISOR` | soporte RF-012 (D-15) |
| `/sensors/connectivity` | GET | `PLATFORM_ADMIN`, `OPERATIONS_SUPERVISOR` | CU-022 (visibilidad RN-020) |
| `/telemetry/test-readings` | POST | `PLATFORM_ADMIN` | CU-015 (D-08) |
| `/incidents` (listado/detalle) | GET | `OPERATIONS_SUPERVISOR`, `OPERATOR`, `MAINTENANCE_TECHNICIAN` | soporte CU-004 a CU-006 (D-15) |
| `/incidents` (creación técnica) | POST | `PLATFORM_ADMIN`, solo si `coldguard.gateway.technical-endpoints.enabled=true` | DEC-012 |
| `/incidents/{id}/acknowledgement` | POST | `OPERATIONS_SUPERVISOR` | CU-004 |
| `/incidents/{id}/escalation` | POST | `OPERATIONS_SUPERVISOR` | CU-005 |
| `/incidents/{id}/close` | POST | `MAINTENANCE_TECHNICIAN` | CU-006, RN-019 |
| `/metrics/incidents` | GET | `OPERATIONS_SUPERVISOR` | CU-008 |
| `/audit-records` | GET | `AUDITOR` | CU-009 |
| `/users`, `/users/{id}/roles`, `/users/{id}/enabled` | GET, POST, DELETE | `PLATFORM_ADMIN` | CU-014 |
| `/actuator/**` | — | no expuesto en el puerto público (puerto de management separado, ver abajo) | — |

Esta tabla debe copiarse a `docs/security/authn-authz.md` (hoy vacío) como fuente de verdad del
mapeo rol→endpoint.

Actuator del Gateway en puerto separado (`management.server.port`, p. ej. 8090, no publicado al
host) para que `/actuator/prometheus` sea alcanzable por Prometheus en la red de Compose pero no
desde el host vía 8080.

CORS: orígenes permitidos por `COLDGUARD_CORS_ALLOWED_ORIGINS` (frontend local), métodos y headers
explícitos (incluye `Authorization`, `Content-Type`, `X-Correlation-Id`), `exposedHeaders`
`X-Correlation-Id`. Sin `*` con credenciales.

### 4. Propagación de identidad (Gateway → servicios)

- Reemplazar `ActorRoleClientInterceptor` por un interceptor genérico que, si hay autenticación
  JWT, agrega `x-actor-id` (= `sub`) y `x-actor-roles` (roles del token, separados por coma, sin
  prefijo `ROLE_`) a **toda** llamada gRPC saliente. Sin autenticación → no agrega nada.
- Servidor (asset, telemetry, incident): interceptor que construye `Actor(id, Set<Role>)` en
  `io.grpc.Context`. La capa `api` lo lee y lo pasa dentro del comando (`application` recibe un
  `Actor` value object; no conoce gRPC ni JWT).
- Endurecimiento recomendado: el interceptor de servidor **solo acepta** metadata de identidad si
  el certificado del par mTLS corresponde a la identidad `gateway` (leer la sesión SSL del
  transporte gRPC); de cualquier otro cliente (p. ej. simulador) se ignora. Evita que un cliente
  interno con certificado válido se haga pasar por un usuario. **Pendiente de verificación** de la
  API exacta para leer el certificado del par en Spring gRPC 1.0.x.
- Defensa en profundidad: los casos de uso sensibles validan el rol en `application`
  (`CloseIncidentService` ya lo hace para RN-019 → migrar de string a `Role`). El Gateway sigue
  siendo la barrera principal (ADR-008).
- Llamadas de sistema (consumidores AMQP, tareas programadas, ingesta del simulador) usan
  `Actor.system("<proceso>")`.

## Fuera de alcance

Refresh tokens, revocación, MFA, recuperación de contraseña, proveedor de identidad externo
(evolución Azure, ADR-007), limitador de tasa en el Gateway.

## Criterios de aceptación (validación local manual)

1. Login con usuario demo de cada rol devuelve un JWT firmado; contraseña errónea y usuario
   inexistente devuelven el **mismo** 401.
2. Tras `max-attempts` fallos, el usuario queda bloqueado durante `lockout.duration` aunque
   luego envíe la contraseña correcta.
3. Cada fila de la tabla RBAC: rol permitido → no 401/403; otro rol → 403; sin token → 401.
   Cualquier ruta no listada → 403/401 (deny-by-default).
4. Cerrar un incidente con token de `OPERATIONS_SUPERVISOR` → 403 en el Gateway; una llamada gRPC
   directa (desde un contenedor con certificado válido pero sin identidad de gateway) a
   `CloseIncident` → `PERMISSION_DENIED`.
5. Asignar/revocar un rol genera un registro de auditoría con actor, fecha, motivo y valor
   anterior/posterior.
6. `grep` de logs del stack completo no encuentra contraseñas, tokens ni headers `Authorization`.
7. El Gateway no arranca si falta la clave pública/privada configurada.

## Tareas

1. Registrar D-02 y D-03 (actualizaciones de ADR-007 y ADR-008).
2. Proto `identity/v1` (SPEC-002) + módulo Identity en incident-service (migración, dominio,
   casos de uso, endpoint gRPC, bootstrap).
3. Script de claves JWT; configuración y `SecurityConfig` nuevo en Gateway (login, encoder,
   decoder, tabla RBAC, CORS, management port).
4. Interceptores de identidad cliente/servidor (commons si D-01).
5. Migrar `CloseIncidentService` a `Role`.
6. Documentar `docs/security/authn-authz.md`, `security-controls.md`, `threat-model.md` (hoy
   vacíos) con lo implementado; actualizar `.claude/rules/security.md` (hoy dice "no
   implementado").

## Riesgos

- El cambio de nombre de metadata (`x-actor-role` → `x-actor-roles`) rompe la compatibilidad entre
  versiones de Gateway/Incident: desplegar ambos juntos (en local siempre ocurre).
- Usuarios demo con contraseña compartida solo son aceptables en local; documentarlo en el
  runbook.
