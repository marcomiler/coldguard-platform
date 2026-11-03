# Plan de Terraform para Azure

Estado: **código escrito y validado; no aprovisionado.** Ningún recurso de Azure existe ni se ha
ejecutado `plan` o `apply`. El diseño y sus justificaciones están en ADR-012; el procedimiento
operativo, en `infra/README.md`.

## Estructura

| Capa | Contenido | Ciclo de vida |
|---|---|---|
| `infra/foundation` | Storage Account del estado remoto, identidad con credencial federada de GitHub (OIDC), grupo de recursos vacío del entorno y presupuesto con alertas (10, 15 y 25 USD) | Se aplica una vez; no se destruye |
| `infra/environments/dev` | Log Analytics, Key Vault, PostgreSQL Flexible Server, entorno de Container Apps, RabbitMQ, Mailpit, los cuatro servicios internos y el Gateway | Efímero: se crea y se destruye por ventana de uso |
| `infra/modules` | `observability`, `keyvault`, `postgres`, `containerapps-env`, `container-app`, `pki` | Reutilizados por `dev` |

## Validación realizada (sin credenciales)

`terraform fmt -check`, `init -backend=false` y `validate` pasan en los seis módulos y en las dos
capas, con Terraform 1.13 y azurerm 4.81; el mismo control corre en CI. Gitleaks no encuentra
secretos en el código de infraestructura.

## Aprobaciones pendientes

| Paso | Quién | Estado |
|---|---|---|
| Registrar los cuatro proveedores de recursos | Persona con rol Owner | Pendiente |
| `plan` y `apply` de `foundation` | Persona con rol Owner | Pendiente |
| `plan` y `apply` de `dev` (con CHG) | Persona, o workflow con aprobación del *environment* `azure-dev` | Pendiente |
| `destroy` de `dev` al terminar cada ventana | Persona, o workflow con aprobación | Pendiente |

## Puntos sin verificar hasta el primer `apply`

mTLS de aplicación sobre ingress TCP de Container Apps; un mismo volumen de secretos montado en
varias carpetas; disponibilidad del SKU Burstable de PostgreSQL en la región; límite de descargas de
Docker Hub para la imagen de RabbitMQ.
