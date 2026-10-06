# Controles de seguridad

Estado de cada control verificado contra el repositorio (SPEC-004). "Implementado" significa que
existe código y pruebas; lo previsto pero ausente figura como pendiente.

| Control | Estado | Dónde / evidencia |
|---|---|---|
| Autenticación con JWT firmado (RS256) | Implementado | `AuthController`, `JwtTokenIssuer`; `AuthControllerTest`, `JwtTokenIssuerTest` |
| Validación del JWT en el borde, fail-fast sin claves | Implementado | `JwtConfig`; `JwtConfigStartupTest` |
| Respuesta de login genérica (sin enumerar usuarios) | Implementado | `VerifyCredentialsService` (una sola comparación de hash en todas las rutas); `AuthControllerTest` |
| Bloqueo por intentos fallidos | Implementado | Identity & Access; `IdentityPersistenceIntegrationTest` |
| Contraseñas con hash adaptativo | Implementado | `DelegatingPasswordEncoder` + BCrypt (`spring-security-crypto`) |
| RBAC deny-by-default en el Gateway | Implementado | `SecurityConfig`; `RbacPolicyTest` recorre cada fila de `authn-authz.md` |
| Propagación de identidad solo desde el Gateway | Implementado | `ActorMetadataClientInterceptor`, `ActorServerInterceptor`; `ActorServerInterceptorTlsTest` (handshake mTLS real) |
| mTLS entre Gateway y servicios internos | Implementado | `MutualTlsHandshakeTest`; CA local de `deploy/scripts/generate-dev-certs.sh` |
| Defensa en profundidad por rol en `application` | Parcial | Solo `CloseIncidentService` (RN-019) |
| Clave privada de firma solo en el Gateway | Implementado | `deploy/local/jwt/` (no versionado), montado solo en el contenedor del Gateway |
| Sin secretos, tokens ni contraseñas en logs | Implementado para login y peticiones autenticadas | `LogSecretsTest`; revisión manual del stack en Compose (SPEC-004) |
| Actuator fuera del puerto público | Implementado | `management.server.port`; `RbacPolicyTest` |
| CORS con orígenes explícitos | Implementado | `SecurityConfig`; `RbacPolicyTest` |
| Auditoría de cambios de identidad (RN-008) | **Pendiente** | Los cambios ya generan su `AuditEntry` (actor, motivo, valor anterior y posterior) en la misma transacción, pero `AuditRecorder` no persiste nada hasta SPEC-007; el servicio lo avisa con un `WARN` al arrancar |
| Administración de usuarios y roles solo para `PLATFORM_ADMIN` | Implementado | `UserAdministrationService` (rol exigido también en Identity), `UserController`; `UserAdministrationServiceTest`, `IdentityPersistenceIntegrationTest`, `UserControllerTest` |
| El último administrador habilitado no se puede degradar ni deshabilitar | Implementado | `UserAdministrationService`, con bloqueo de filas en `JdbcUserAccountRepository` |
| Revocación de tokens antes de expirar | No implementado (limitación aceptada del MVP) | ADR-007 |
| Refresh tokens, MFA, recuperación de contraseña | Fuera de alcance | SPEC-004 |
| Limitador de tasa en el Gateway | No implementado (mejora futura) | El bloqueo por intentos vive en Identity |
| Aislamiento de red entre servicios en Compose | No implementado | Todos comparten una red; la aceptación de identidad depende del certificado, no de la red |
| Gestión de secretos en Azure (Key Vault) | Planificado, no aprovisionado | `.claude/rules/security.md` |

## Usuarios demo

Solo en local: el bootstrap (`IDENTITY_BOOTSTRAP_ENABLED`) crea un usuario por rol con la
contraseña de `DEMO_USERS_PASSWORD`, y el arranque falla si falta. No se versionan contraseñas ni
se crean usuarios en migraciones. Una contraseña compartida no es aceptable fuera de local.
El bootstrap no actualiza usuarios existentes: si cambia `DEMO_USERS_PASSWORD`, hay que recrear el
volumen de PostgreSQL.
