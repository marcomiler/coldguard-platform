# Modelo de amenazas

Alcance: autenticación, autorización y propagación de identidad del backend (SPEC-004). Metodología
informal por superficie de ataque; no es un análisis STRIDE exhaustivo.

## Activos

- Credenciales de los usuarios y la clave privada de firma del JWT.
- La integridad de la identidad que llega a los servicios internos (quién hizo qué, RN-008).
- Las operaciones restringidas por rol, en particular el cierre de incidentes (RN-019).

## Límites de confianza

1. Cliente externo → Gateway (REST, JWT). Es el único componente que valida el token.
2. Gateway → servicios internos (gRPC sobre mTLS). Los servicios confían en la identidad solo si
   viene del certificado `gateway`.
3. Servicios internos entre sí y con PostgreSQL/RabbitMQ: red compartida de Compose, sin
   aislamiento adicional.

## Amenazas y mitigaciones

| Amenaza | Mitigación | Riesgo residual |
|---|---|---|
| Adivinar contraseñas por fuerza bruta | Bloqueo por intentos en Identity | Sin limitador de tasa en el Gateway; el bloqueo permite bloquear a un usuario ajeno (denegación de servicio dirigida) |
| Enumerar usuarios por el mensaje o el tiempo de respuesta | Mismo `401` y una sola comparación de hash incluso si el usuario no existe | Diferencias de latencia por red no se han medido |
| Falsificar o alterar un JWT | Firma RS256 verificada con la clave pública; emisor y expiración validados | Sin revocación: un token robado es válido hasta `exp` (TTL configurable, sin refresh) |
| Robo de la clave privada de firma | Solo existe en el contenedor del Gateway; fuera del repositorio y de `certs/` | Un compromiso del Gateway la expone |
| Acceder a una ruta sin el rol | Deny-by-default; `RbacPolicyTest` recorre cada fila | Una ruta nueva no declarada queda cerrada, no abierta |
| Suplantar a un usuario llamando directo a un servicio interno con `x-actor-*` | El servicio acepta esa metadata solo del certificado `gateway`; el resto se ignora | Un compromiso del Gateway o de su clave mTLS permite suplantar a cualquiera |
| Llamar a un servicio interno sin certificado | Rechazo en el handshake mTLS | Los certificados de desarrollo los emite una CA local |
| Escalar privilegios con un rol desconocido en `x-actor-roles` | Los nombres que no están en el enum de roles no conceden nada | — |
| Fuga de secretos por logs | `LogSecretsTest`; el login y el interceptor nunca registran contraseña ni token | Cubre los flujos probados; código nuevo debe añadir su caso |
| Abuso de CORS desde otro origen | Lista explícita, sin `*` y sin credenciales | Depende de configurar bien la variable de entorno |
| Acceder a Actuator desde fuera | Puerto de management separado y no publicado | En Compose, otros contenedores sí lo alcanzan |
| Crear incidentes por el endpoint técnico | Cerrado por defecto; abierto solo con la bandera y solo `PLATFORM_ADMIN` | La bandera está activa en Compose local |
| Un administrador deja al sistema sin administradores | El último `PLATFORM_ADMIN` habilitado no puede perder el rol ni ser deshabilitado; la comprobación bloquea filas frente a cambios concurrentes | Una cuenta de administrador comprometida sí puede degradar a los demás |
| Cambios de identidad sin traza | Cada cambio genera su `AuditEntry` en la misma transacción | **Abierto** hasta SPEC-007: hoy el recorder no guarda nada |

## Supuestos

- La red de Compose es de desarrollo; no se asume aislamiento entre contenedores.
- Los usuarios demo con contraseña compartida existen solo en local.
- Nada de esto se ha desplegado en Azure.
