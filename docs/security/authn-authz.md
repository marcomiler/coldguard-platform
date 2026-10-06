# Autenticación y autorización

Fuente de verdad del mapeo rol→endpoint. La política se implementa en una única clase,
`apps/gateway/.../config/SecurityConfig.java`, y la verifica `RbacPolicyTest`, que recorre esta
misma tabla; si cambia una, cambia la otra. Diseño y decisiones: `docs/specs/SPEC-004-identidad-seguridad.md`,
ADR-007 y ADR-008.

## Autenticación

1. `POST /api/v1/auth/login` con `{ "username", "password" }`.
2. El Gateway pide a Identity & Access (Incident Service, gRPC sobre mTLS) que verifique las
   credenciales. Cualquier fallo devuelve el mismo `401` genérico, exista o no el usuario.
3. Si son válidas, el Gateway firma un JWT (RS256) con `iss`, `sub` (id de usuario),
   `preferred_username`, `roles`, `iat`, `exp` y `jti`. No hay refresh token.
4. Cada petición lleva `Authorization: Bearer <token>`. El Gateway valida firma, emisor y
   expiración (con tolerancia de reloj configurable). Sin clave pública configurada no arranca.
5. Tras `coldguard.identity.lockout.max-attempts` fallos consecutivos la cuenta queda bloqueada
   durante `lockout.duration`, también para la contraseña correcta.

## Roles

Los cinco actores humanos de `docs/product/stakeholders.md`: `OPERATIONS_SUPERVISOR`, `OPERATOR`,
`MAINTENANCE_TECHNICIAN`, `AUDITOR`, `PLATFORM_ADMIN`. No se definen otros.

## Mapeo rol→endpoint (prefijo `/api/v1`)

Política **deny-by-default**: toda ruta no listada se rechaza (`401` sin token válido, `403` con
token). Las reglas se evalúan en orden, de la más específica a la más general.

| Ruta | Método | Rol(es) | CU |
|---|---|---|---|
| `/auth/login` | POST | público | CU-016 |
| `/organizations`, `/sites`, `/assets` (alta/edición) | POST, PUT, PATCH | `PLATFORM_ADMIN` | CU-001, CU-012, CU-013 |
| `/organizations`, `/sites`, `/assets`, `/sensors` (lectura) | GET | `PLATFORM_ADMIN`, `OPERATIONS_SUPERVISOR` | CU-013 |
| `/sensors` (alta/edición), `/sensors/{id}/profile` | POST, PUT, PATCH | `PLATFORM_ADMIN` | CU-007, CU-011, CU-013 |
| `/sensors/{id}/status`, `/calibrations`, `/reassignment`, `/retirement` | POST | `PLATFORM_ADMIN` | CU-017 a CU-020 |
| `/sensors/{id}/history` | GET | `PLATFORM_ADMIN` | CU-021 |
| `/sensors/{id}/readings` | GET | `PLATFORM_ADMIN`, `OPERATIONS_SUPERVISOR` | soporte RF-012 |
| `/sensors/connectivity` | GET | `PLATFORM_ADMIN`, `OPERATIONS_SUPERVISOR` | CU-022 |
| `/telemetry/test-readings` | POST | `PLATFORM_ADMIN` | CU-015 |
| `/incidents`, `/incidents/{id}` | GET | `OPERATIONS_SUPERVISOR`, `OPERATOR`, `MAINTENANCE_TECHNICIAN` | soporte CU-004 a CU-006 |
| `/incidents` (creación técnica) | POST | `PLATFORM_ADMIN`, solo con `coldguard.gateway.technical-endpoints.enabled=true` (cerrada por defecto) | DEC-012 |
| `/incidents/{id}/acknowledgement` | POST | `OPERATIONS_SUPERVISOR` | CU-004 |
| `/incidents/{id}/escalation` | POST | `OPERATIONS_SUPERVISOR` | CU-005 |
| `/incidents/{id}/close` | POST | `MAINTENANCE_TECHNICIAN` | CU-006, RN-019 |
| `/metrics/incidents` | GET | `OPERATIONS_SUPERVISOR` | CU-008 |
| `/audit-records` | GET | `AUDITOR` | CU-009 |
| `/users`, `/users/{id}/roles`, `/users/{id}/enabled` | GET, POST, DELETE | `PLATFORM_ADMIN` | CU-014 |

Notas:
- El Operador solo lee incidentes: CU-004/CU-006 mencionan que "aporta contexto", pero no existe
  comando, RF ni evento para ello. Un comando del Operador requiere antes un RF/CU nuevo.
- El Auditor lee solo la bitácora. **Pendiente de confirmar con el PO.**
- Hoy el Gateway implementa `POST /auth/login`, los endpoints de incidentes (creación y cierre), la
  administración de usuarios (`/users`), los recursos de Asset (`/organizations`, `/assets`,
  `/sensors` y sus sub-recursos) y los de Telemetry (`/telemetry/test-readings`,
  `/sensors/{id}/readings`, `/sensors/connectivity`); el resto de las filas (métricas, bitácora,
  consulta y gestión de incidentes) están declaradas en la política pero la ruta aún no existe y responde
  `404` a quien tenga el rol.
- `GET /sensors/connectivity` (Telemetry) es una ruta literal y tiene precedencia sobre
  `GET /sensors/{id}` (Asset); ambas exigen los mismos roles.

## Administración de usuarios (`/users`)

| Operación | Ruta | Reglas |
|---|---|---|
| Listar (paginado, `page` y `size`, máximo 100) | `GET /users` | Ordenado por usuario |
| Consultar | `GET /users/{id}` | `404` si el id no existe o no es un UUID |
| Crear | `POST /users` | Contraseña inicial con la política mínima de longitud; usuario o correo repetido → `409` |
| Asignar rol | `POST /users/{id}/roles` `{ role, reason }` | Motivo obligatorio; repetir una asignación no cambia nada ni audita |
| Revocar rol | `DELETE /users/{id}/roles` `{ role, reason }` | Motivo obligatorio |
| Habilitar o deshabilitar | `POST /users/{id}/enabled` `{ enabled, reason }` | Motivo obligatorio |

- El rol `PLATFORM_ADMIN` se exige dos veces: en el Gateway (tabla de arriba) y en Identity a
  partir del actor propagado, que rechaza con `PERMISSION_DENIED` cualquier otra llamada.
- El último administrador habilitado no puede perder el rol `PLATFORM_ADMIN` ni ser deshabilitado
  (`409`, `USER_STATE_CONFLICT`). La comprobación bloquea las filas de los demás administradores
  para que dos cambios concurrentes no puedan dejar el sistema sin ninguno.
- Las respuestas nunca incluyen contraseña ni hash, y los errores no repiten el cuerpo recibido.
- `ListUserContacts` existe solo para uso interno (D-10) y por ahora también exige
  `PLATFORM_ADMIN`; el Gateway no lo expone.

## Propagación de identidad hacia los servicios internos

- El Gateway añade `x-actor-id` (el `sub` del JWT) y `x-actor-roles` (roles separados por coma, sin
  prefijo `ROLE_`) como metadata gRPC en cada llamada saliente con autenticación JWT. Nunca como
  campo de request. Sin JWT no añade nada.
- Un servicio interno **solo acepta** esa metadata si el certificado mTLS del par tiene CN
  `gateway`. De cualquier otro cliente con certificado válido (p. ej. el simulador) la ignora y la
  llamada se trata como sin actor. Los servicios internos nunca leen `Authorization`.
- **Llamadores de sistema.** Un servicio interno que llama con su propio certificado y no en nombre
  de un usuario (hoy, Telemetry leyendo el contexto de evaluación de Asset, y el simulador `sensor-simulator` enviando lecturas a Telemetry) es un actor
  `system:<nombre>`. Se reconoce por el CN de su certificado, solo si figura en
  `coldguard.security.system-callers` del servicio que recibe la llamada (Asset: `telemetry-service`; Telemetry: `sensor-simulator`);
  cualquier metadata de identidad que envíe se ignora. El Gateway nunca puede propagar un actor
  `system:`. Un llamador de sistema no tiene roles: no puede ejecutar comandos de usuarios.
- Defensa en profundidad: los casos de uso sensibles validan el rol en `application` (hoy,
  `CloseIncidentService` exige `MAINTENANCE_TECHNICIAN`, RN-019). Una llamada sin actor se rechaza
  con `PERMISSION_DENIED`.
- El interceptor, `Actor` y `Role` viven en `coldguard-commons` (`com.coldguard.commons.security`) y
  se registran por autoconfiguración en todo servicio con servidor gRPC; hoy lo usan Incident y
  Asset. Telemetry deberá usarlo cuando exponga gRPC.

## CORS y superficie pública

- Orígenes permitidos por `COLDGUARD_CORS_ALLOWED_ORIGINS` (lista explícita; `*` impide el
  arranque; vacío = ninguno). Métodos y cabeceras explícitos (`Authorization`, `Content-Type`,
  `X-Correlation-Id`), sin credenciales.
- Actuator escucha en un puerto de management separado (`MANAGEMENT_PORT`, 8090), no publicado al
  host; en el puerto público `/actuator/**` responde `401`.
