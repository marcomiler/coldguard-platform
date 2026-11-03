# ADR-012 Infraestructura Azure de costo mínimo, efímera y aprovisionada con Terraform

**Estado:** Propuesto el 2026-11-03; aprobado por el equipo en las decisiones de ejecución (apply por
workflow con aprobación y local, región `canadacentral`). No hay recursos Azure creados.

## Contexto
DEC-002, DEC-009 y DEC-010 fijaron los servicios planificados de Azure (Azure Container Apps,
PostgreSQL Flexible Server, RabbitMQ como contenedor, Key Vault con Managed Identity) pero dejaron
abierta la forma concreta de aprovisionarlos y operarlos. El despliegue de la versión final es un
entregable de APF3 y de la presentación final.

Restricciones reales del equipo:

- Suscripción **Azure for Students** con 100 USD de crédito. Objetivo de gasto del proyecto completo
  (backend y frontend): **10 USD**, tope aceptado **25 USD**.
- La política de la suscripción solo permite las regiones `canadacentral`, `northcentralus`,
  `chilecentral`, `mexicocentral` y `westus`.
- El equipo tiene rol Owner en la suscripción. Los proveedores `Microsoft.App`,
  `Microsoft.DBforPostgreSQL`, `Microsoft.OperationalInsights` y `Microsoft.Web` deben registrarse
  (gratuito).
- El sistema solo se necesita encendido en ventanas cortas: evidencia para el informe (horas) y la
  demostración de la presentación (1 a 2 horas como máximo, con la presentación en 15 minutos).
- El repositorio es público y el equipo dispone de GitHub Student Developer Pack.
- `.claude/rules/infra.md` y `.claude/rules/terraform.md`: ningún recurso ni `apply`/`destroy`
  sin aprobación humana explícita; nada de estado, credenciales ni secretos en Git.
- La mensajería gestionada (Azure Service Bus) y el correo gestionado (Azure Communication Services)
  se difieren; el frontend (React, repositorio aparte, ADR-002) se despliega después del backend.

## Problema
¿Cómo desplegar el backend en Azure al menor costo posible, de forma reproducible, de modo que pueda
crearse y destruirse por ventanas de uso, con aprobación humana explícita en cada cambio de
infraestructura, conservando el mTLS interno (ADR-003), el Outbox con RabbitMQ (ADR-004, ADR-009) y la
política de secretos (DEC-010)?

## Opciones consideradas
1. **Máquina virtual única con Docker Compose.** Muy barata y reutiliza el Compose local, pero
   contradice DEC-002, deja sin evidencia los servicios administrados y no demuestra Infraestructura
   como Código más allá de una VM. Descartada.
2. **Azure Kubernetes Service.** Descartado por DEC-002 (complejidad y costo fijo del plano de control).
3. **Container Apps (plan Consumption) con PostgreSQL Flexible Server, RabbitMQ en contenedor y Key
   Vault** (elegida). Cumple DEC-002, DEC-009 y DEC-010; se factura por segundo de uso, lo que
   favorece el ciclo crear, evidenciar y destruir.
4. **Container Apps con PostgreSQL también en contenedor.** Ahorra unos centavos por hora pero pierde
   copias de seguridad administradas y contradice DEC-002. Descartada.
5. **Registro de imágenes: Azure Container Registry.** Tiene costo fijo diario y exige permisos de
   descarga. **GitHub Container Registry público** es gratuito, el repositorio ya es público y las
   imágenes no contienen secretos (se leen de Key Vault en ejecución). Elegido GHCR.
6. **Certificados mTLS y clave JWT.** Subirlos a mano a Key Vault rompe la automatización de cada
   encendido. Generarlos con el proveedor `tls` de Terraform los deja en el estado. Elegida la segunda
   opción, con el estado protegido (ver Riesgos).
7. **Estado de Terraform local o remoto.** El local se pierde y no permite `plan` desde CI. Elegido un
   backend remoto `azurerm` en una Storage Account propia.
8. **Quién ejecuta `apply`/`destroy`.** Solo local limita la evidencia de automatización; solo workflow
   obliga a depender de GitHub para todo. Elegidas **ambas vías**: local con `az login` y workflow de
   GitHub Actions con aprobación explícita.

## Decisión
**Capas de Terraform y ciclo de vida.**

- `infra/foundation` (se crea una vez y **no se destruye**): grupo de recursos persistente
  `rg-coldguard-foundation` con la Storage Account del estado (cuenta privada, sin acceso público,
  autenticación Entra), la identidad administrada asignada por el usuario con credenciales federadas de
  GitHub (OIDC, sin secretos), el grupo de recursos vacío del entorno `rg-coldguard-dev` (un grupo vacío
  no cuesta) y el presupuesto con alertas. La aplica una persona con rol Owner.
- `infra/environments/dev` (**efímero**, se crea y destruye por ventanas): todo lo que cuesta, dentro
  de `rg-coldguard-dev`, que queda vacío tras el `destroy`.
- Módulos en `infra/modules/`: `observability`, `keyvault`, `postgres`, `containerapps-env`,
  `container-app` (reutilizable) y `pki` (certificados mTLS y clave JWT).

**Recursos del entorno `dev` y dimensionamiento inicial (todo parametrizable por variables).**

| Recurso | Configuración |
|---|---|
| Región | Variable, por defecto `canadacentral` (dentro de las permitidas) |
| Log Analytics | Una sola workspace, retención mínima, cuota diaria acotada |
| Container Apps Environment | Plan Consumption, logs a la workspace |
| Gateway | Ingress externo HTTPS; 0,5 vCPU y 1 GiB; mínimo y máximo de réplicas en 1 |
| Asset, Telemetry, Incident y Notification | Ingress interno `tcp` para gRPC con mTLS de aplicación; 0,5 vCPU y 1 GiB; 1 réplica |
| RabbitMQ | Contenedor con ingress `tcp` interno; 0,25 vCPU y 0,5 GiB; sin disco persistente (el Outbox reenvía) |
| Mailpit | Contenedor; ingress externo solo si se quiere mostrar el buzón en la demo |
| Sensor Simulator | Contenedor opcional (variable `deploy_simulator`) |
| PostgreSQL Flexible Server | Burstable de menor tamaño, almacenamiento mínimo, sin alta disponibilidad, una sola zona, TLS obligatorio, acceso público limitado a servicios de Azure |
| Key Vault | Autorización por políticas de acceso (no RBAC, para no requerir asignaciones de rol); eliminación definitiva al destruir |
| Etiquetas | `project`, `env`, `owner`, `ttl` en todos los recursos |

**Imágenes.** Se publican en GHCR (paquetes públicos) con etiqueta inmutable `sha-<commit>` y `vX.Y.Z`
en versiones; nunca se despliega `latest`.

**Secretos.** Contraseñas de base de datos y de usuarios de demostración con `random_password`;
certificados mTLS (con los mismos nombres de servicio que el Compose local como SAN) y par RS256 del
Gateway con `tls_*`; todo se guarda en Key Vault y las apps lo reciben por referencia con Managed
Identity, montado como volumen de secretos. El entorno de Azure usa una CA de demostración propia, no
una CA de producción.

**Ejecución.** Se admiten dos vías: (a) local con `az login` y aprobación del propietario antes de cada
`apply`/`destroy`; (b) workflows de GitHub Actions disparados a mano (`workflow_dispatch`) que ejecutan
`apply` o `destroy` con OIDC y **se detienen hasta que un revisor apruebe el *environment* protegido
`azure-dev`**. Esa aprobación es la aprobación humana explícita que exigen las reglas del proyecto. Los
PRs que tocan `infra/**` ejecutan solo `fmt`, `validate` y `plan` de solo lectura.

**Política de costo.**

- El entorno solo se levanta en ventanas planificadas: evidencia, ensayo y demostración.
- Alerta de presupuesto en 10, 15 y 25 USD; si se cruza el primer umbral se destruye antes de
  continuar.
- Estimación inicial, **a verificar con la calculadora oficial de precios antes del primer `apply`**:
  con el dimensionamiento anterior, de 0,4 a 0,5 USD por hora encendido, y un cupo mensual gratuito de
  Container Apps que cubre parte de las primeras horas. Con tres ventanas de unas 2 a 3 horas el gasto
  previsto queda por debajo del objetivo de 10 USD. Es una estimación, no una medición.
- Al terminar cada ventana: `destroy` del entorno `dev`. La capa `foundation` solo conserva el estado
  y la identidad.

**Fuera de este ADR (se difiere).** Azure Service Bus y Azure Communication Services (ADR posterior que
modificaría DEC-009 y DEC-007), el frontend (Static Web Apps Free o sitio estático en Storage según lo
que permita la política de regiones), el dominio personalizado y la exportación de telemetría a Azure
Monitor.

## Consecuencias
- Cumple los servicios decididos en DEC-002, DEC-009 y DEC-010 sin reabrirlos y reduce la superficie de
  decisión a la forma de aprovisionarlos.
- El costo depende de las horas encendido, no del tiempo de calendario; el presupuesto se controla
  destruyendo, no ajustando tamaños.
- Cada ventana exige crear de nuevo la base de datos: se pierden los datos entre ventanas. Los datos de
  demostración se vuelven a sembrar por el Gateway. El Outbox mitiga la pérdida de mensajes de RabbitMQ
  entre reinicios, no entre ventanas.
- El encendido completo tarda más que el stack local (creación de PostgreSQL y de las apps); debe
  medirse en el primer despliegue y planificar la demostración con ese margen.
- Se requieren cambios de documentación y de reglas: `deployment-view.md`, `environments.md`,
  `terraform-plan.md`, `.claude/rules/terraform.md` y `.claude/rules/infra.md` hoy describen
  `infra/` como marcadores vacíos y Azure como solo planificado; se actualizarán con un cambio mínimo y
  solo cuando exista código y evidencia, sin afirmar que algo esté desplegado antes del `apply`.
- SPEC-015 y SPEC-016 se ajustan: GHCR en lugar de ACR, capa `foundation`, ejecución por workflow con
  aprobación y local.

## Riesgos
- **Sobrecosto por olvidar un `destroy`.** Mitigación: presupuesto con alertas, etiqueta `ttl`,
  recordatorio explícito al cerrar cada ventana y `destroy` por workflow.
- **Restricción de capacidad o de oferta de PostgreSQL Flexible Server en la región** (frecuente en
  suscripciones de estudiante). Mitigación: región como variable; si el `apply` falla con restricción de
  oferta se prueba otra región permitida.
- **mTLS gRPC sobre ingress `tcp` de Container Apps no verificado.** Es el supuesto más frágil: debe
  comprobarse en el primer `plan` y `apply`. Si no se conserva el mTLS de aplicación ni los nombres de
  servicio como SAN, se registra una enmienda a este ADR con la alternativa.
- **Secretos en el estado de Terraform** (certificados, claves y contraseñas generadas). Mitigación:
  estado en Storage privada con autenticación Entra, sin acceso público, nunca en Git; la CA es solo de
  demostración y se regenera en cada encendido.
- **PostgreSQL con acceso público restringido** en lugar de red privada, para evitar el costo y la
  complejidad de la integración de red. Mitigación: TLS obligatorio, regla de firewall mínima y
  contraseña en Key Vault.
- **Imágenes públicas en GHCR:** no deben contener secretos; la revisión de seguridad de la imagen y el
  escaneo de CI lo verifican.
- **Key Vault con eliminación lógica** puede bloquear el nombre tras un `destroy`. Mitigación: purga al
  destruir y nombre con sufijo aleatorio.
- **Dimensionamiento insuficiente de las JVM** (0,5 vCPU y 1 GiB) o arranque lento. Mitigación:
  parámetros por variable y medición en el primer despliegue.
- **Crédito de estudiante o proveedores sin registrar.** Mitigación: registrar los cuatro proveedores
  antes del primer `plan` y vigilar el saldo.

## Related ADRs
ADR-002 (frontend en repositorio separado), ADR-003 (gRPC con mTLS), ADR-004 (RabbitMQ en el MVP),
ADR-006 (PostgreSQL compartido con esquema por servicio), ADR-007 (JWT y RBAC), ADR-008 (Gateway),
ADR-009 (Transactional Outbox). Decisiones relacionadas: DEC-002, DEC-007, DEC-009, DEC-010.

## Evolución futura a Azure
Este ADR **es** el diseño del despliegue inicial. Las evoluciones previstas, cada una con su propio ADR
o DEC y sin crearse ahora: Azure Service Bus como broker (sustituiría a RabbitMQ y a DEC-009), Azure
Communication Services Email, frontend en Static Web Apps, red privada para PostgreSQL y Key Vault,
alta disponibilidad con redundancia de zona y réplicas, exportación de OpenTelemetry a Application
Insights, dominio personalizado con certificado administrado y un segundo entorno.
