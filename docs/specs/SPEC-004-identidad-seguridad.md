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
  `deploy/local/jwt/`, no versionado; fuera de `certs/` porque este se monta en todos los servicios
  internos y la clave privada solo debe verla el Gateway), rutas por `JWT_PRIVATE_KEY_PATH` /
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
- **Requisito (no opcional)**: el interceptor de servidor **solo acepta** metadata de identidad si
  el certificado del par mTLS corresponde a la identidad `gateway` (leer la sesión SSL del
  transporte gRPC); de cualquier otro cliente (p. ej. simulador) se ignora y la llamada se trata
  como sin identidad. Sin esto, cualquier servicio interno con certificado válido podría
  suplantar a un usuario con `x-actor-roles`, y la defensa en profundidad (RN-019) no se cumple.
  **Pendiente de verificación** de la API exacta para leer el certificado del par en Spring gRPC
  1.0.x: se resuelve al inicio del paso 2, antes de escribir el interceptor. Si la API no lo
  permite, se detiene el paso y se propone alternativa (p. ej. un interceptor de transporte
  propio) antes de continuar; no se degrada a "confiar en la metadata".
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

## Plan de entrega

Cuatro pasos entregables por separado, cada uno con su propio commit y validación; un paso no
empieza hasta que el anterior pasa sus criterios. Los números de criterio son los de la sección
anterior.

| Paso | Alcance | Criterios que cierra | Depende de |
|---|---|---|---|
| 1. Identity y login | D-02 en ADR-007; proto `identity/v1` (SPEC-002); módulo Identity en incident-service (migración, dominio, casos de uso, gRPC, bootstrap, auditoría); `POST /auth/login` en el Gateway con claves JWT (script, encoder, decoder fail-fast) | 1, 2, 7 (el 5 solo en parte, ver nota) | SPEC-002 |
| 2. Propagación de identidad | D-03 en ADR-007/ADR-008; verificar la API de certificado del par mTLS; interceptores cliente/servidor (`x-actor-id`, `x-actor-roles`) con aceptación solo desde `gateway`; `Actor` en `application`; migrar `CloseIncidentService` a `Role` (commons si D-01) | 4 | Paso 1 |
| 3. RBAC deny-by-default | `SecurityConfig` con la tabla rol→endpoint, CORS, puerto de management separado | 3 | Pasos 1 y 2 |
| 4. Documentación y cierre | `docs/security/authn-authz.md`, `security-controls.md`, `threat-model.md`; actualizar `.claude/rules/security.md`; verificar el criterio 6 (logs sin secretos) con un test automatizado además de la revisión manual | 6 | Pasos 1 a 3 |

Notas:
- **Dependencia circular detectada**: el criterio 5 y la auditoría de los casos de uso de Identity
  requieren el módulo Audit Log (`AuditRecorder`, SPEC-007), que hoy no existe y que a su vez
  depende de SPEC-004. Propuesta pendiente de aprobación: en el paso 1, Identity depende del
  puerto `AuditRecorder` definido en `application` y se prueba con un doble; el criterio 5 se
  cierra cuando SPEC-007 aporte el adaptador real. Hasta entonces no se afirma que la auditoría de
  Identity esté implementada.
- El paso 3 va después del 2 porque las rutas protegidas dependen de que la identidad ya se
  propague y se verifique en los servicios; así no queda un estado intermedio con RBAC en el borde
  pero sin defensa en profundidad.
- Los pasos 1 y 2 modifican ADR-007 (D-02 y D-03): se registran con `new-adr` en su paso, no todos
  al inicio.
- Si el paso 2 detiene el trabajo por la verificación del certificado, los pasos 3 y 4 no avanzan.

## Riesgos

- Entregar el RBAC (paso 3) sin la aceptación restringida a `gateway` (paso 2) dejaría la
  identidad suplantable desde cualquier servicio interno con certificado válido; por eso el orden
  de los pasos es obligatorio.
- El cambio de nombre de metadata (`x-actor-role` → `x-actor-roles`) rompe la compatibilidad entre
  versiones de Gateway/Incident: desplegar ambos juntos (en local siempre ocurre).
- Usuarios demo con contraseña compartida solo son aceptables en local; documentarlo en el
  runbook.

## Implementación

### Paso 1 — Identity y login (parcial, 2026-10-05)

Implementada la rebanada de login; el resto del paso 1 sigue pendiente.

Hecho:
- **D-02** ya estaba registrado en ADR-007 (tercera actualización); no hizo falta un ADR nuevo.
- **Identity** (`com.coldguard.incident.identity`): migración `V5` (esquema `identity`),
  `UserAccount` y `Role` en dominio, `VerifyCredentialsService` (una sola comparación de hash en
  todas las rutas, bloqueo por intentos, rechazo genérico), `ProvisionUserService`, adaptador JDBC,
  `IdentityGrpcService.VerifyCredentials` y bootstrap local (`IDENTITY_BOOTSTRAP_ENABLED`, falla el
  arranque sin `DEMO_USERS_PASSWORD`). Un rechazo es un resultado, no una excepción, para que el
  intento fallido se confirme en la transacción.
- **Puerto `AuditRecorder`** en `application` (aprobado): hoy lo implementa un recorder que no
  guarda nada y avisa con un `WARN` al arrancar. **Los cambios de Identity todavía no se auditan**;
  el criterio 5 sigue abierto hasta que SPEC-007 aporte el adaptador real.
- **Gateway**: `POST /api/v1/auth/login`, `JwtTokenIssuer` (RS256, claims de la sección 2),
  `JwtDecoder` con la clave pública, issuer y tolerancia de reloj; se eliminaron `issuer-uri` y
  `pendingJwtDecoder`. Arranque fail-fast sin claves o sin issuer.
- **Claves**: `deploy/scripts/generate-dev-certs.sh` (también `--jwt-only`) genera el par en
  `deploy/local/jwt/`, que solo monta el Gateway en Compose.

Pendiente del paso 1:
- RPC `CreateUser`, `GetUser`, `ListUsers`, `AssignRole`, `RevokeRole`, `SetUserEnabled` y
  `ListUserContacts`: hoy responden `UNIMPLEMENTED`. Requieren la identidad del actor (paso 2) para
  restringirlos a `PLATFORM_ADMIN`.
- Validación manual con Compose de los criterios 1, 2 y 7 (hoy cubiertos por pruebas automáticas:
  login y bloqueo contra PostgreSQL real, emisión y validación del token, arranque fail-fast).

### Paso 2 — Propagación de identidad (parcial, 2026-10-05)

Hecho:
- **Verificación pendiente resuelta**: gRPC expone el certificado del par mediante
  `Grpc.TRANSPORT_ATTR_SSL_SESSION` (grpc-api 1.77.1); un test con handshake mTLS real lo confirma.
  No hizo falta alternativa.
- **Gateway**: `ActorMetadataClientInterceptor` reemplaza a `ActorRoleClientInterceptor`; con
  autenticación JWT envía `x-actor-id` (`sub`) y `x-actor-roles` (solo autoridades `ROLE_*`, sin
  prefijo) en toda llamada gRPC; sin JWT no envía nada.
- **Incident Service**: `ActorServerInterceptor` reemplaza a `ActorRoleServerInterceptor` y solo
  acepta la identidad si el CN del certificado del par es `gateway`; de otro cliente con
  certificado válido la ignora. `Actor` (`application`) viaja en `CloseIncidentCommand`, y
  `CloseIncidentService` valida `Role.MAINTENANCE_TECHNICIAN` en lugar de un string.
- Pruebas: unitarias del interceptor (certificado simulado), de transporte mTLS real
  (`ActorServerInterceptorTlsTest`, acepta `gateway`, ignora otro certificado) y de cableado
  (`PERMISSION_DENIED` con identidad de un par no confiable).

Pendiente del paso 2:
- Interceptor y `Actor` en asset-service y telemetry-service: hoy no tienen servidor gRPC.
- `Actor.system(...)` en consumidores AMQP y tareas programadas, cuando existan casos de uso que
  lo necesiten.
- `Role` vive en `identity.domain` y `application` lo importa; si otro servicio necesita el enum,
  moverlo a commons.

Validación manual con Compose (2026-10-05, stack completo healthy, `DEMO_USERS_PASSWORD` pasada por
entorno):
- Criterio 4: cierre con token de `supervisor` → 403; sin token → 401; con `technician` → 200.
  Llamada gRPC directa a `incident-service:9093` con `grpcurl` y el certificado de
  `sensor-simulator`, enviando `x-actor-roles: MAINTENANCE_TECHNICIAN` → `PermissionDenied`; con el
  certificado `gateway` la identidad se acepta y el incidente se cierra.
- Criterio 1: login correcto para los cinco roles; contraseña errónea y usuario inexistente
  devuelven el mismo 401.
- Criterio 2: tras 8 intentos fallidos, la contraseña correcta devuelve 401; otro usuario sigue
  entrando.
- Criterio 6 (parcial, solo este recorrido): 695 líneas de logs del stack sin la contraseña demo,
  `Bearer`, `Authorization:` ni tokens JWT. El test automatizado sigue pendiente (paso 4).

### Paso 3 — RBAC deny-by-default (2026-10-05)

Hecho:
- `SecurityConfig` declara la tabla completa de la sección 3 con `anyRequest().denyAll()` al final;
  las reglas, de la más específica a la más general, viven en un solo método.
- `POST /incidents` (creación técnica) solo existe con
  `coldguard.gateway.technical-endpoints.enabled=true` (`COLDGUARD_TECHNICAL_ENDPOINTS_ENABLED`),
  cerrada por defecto; Compose local la activa.
- CORS por `COLDGUARD_CORS_ALLOWED_ORIGINS`, métodos y cabeceras explícitos, sin credenciales; un
  `*` impide el arranque.
- El puerto de management (8090) ya estaba separado; `EndpointRequest` solo coincide en ese puerto.
- `RbacPolicyTest` recorre las 33 filas de la tabla: rol permitido → ni 401 ni 403; los otros
  roles → 403; sin token → 401; más rutas no listadas (incluido `/actuator`) → denegadas.
- Validado en Compose: ruta desconocida 401/403, `GET /incidents` como auditor 403,
  `POST /incidents` como `technician` 403 y como `admin` pasa la seguridad, healthchecks del
  puerto 8090 sanos.

Nota: la tabla declara rutas cuyos controladores aún no existen; para quien tiene el rol responden
404. `POST /incidents/{id}/close` con un id que no es UUID responde 502 (error interno de
Incident Service), independiente de la seguridad; queda por corregir.

### Paso 4 — Documentación y cierre (2026-10-05)

Hecho:
- `docs/security/authn-authz.md` (tabla rol→endpoint, autenticación, propagación, CORS),
  `security-controls.md` y `threat-model.md`. Las tres tenían solo la nota "Diferido a Sprint 3-4";
  se completaron porque este spec lo pide en el paso 4.
- `.claude/rules/security.md` y R-014 en `docs/quality/risk-register.md` actualizados.
- Criterio 6: `LogSecretsTest` ejecuta login correcto y fallido y peticiones autenticadas con
  logging de seguridad en TRACE y comprueba que ni la contraseña, ni el token, ni `Bearer eyJ…`
  aparecen en el log; además verifica que el log se capturó.

Pendiente del spec: RPC de administración de usuarios y auditoría real de Identity (criterio 5,
SPEC-007); confirmar con el PO el alcance del Auditor y del Operador; interceptores en asset y
telemetry cuando tengan gRPC.
