# SPEC-016 — Infraestructura Azure con Terraform (APF3 · soporte de 9.2 y "Despliegue versión final")

## Objetivo

Diseñar, escribir y validar la infraestructura de Azure como código (módulos Terraform), dejarla
lista para `plan`, y aprovisionarla **solo tras aprobación humana explícita**. Sin aprobación, el
entregable es código revisable y `plan`, y el informe lo declara así.

## Trazabilidad

DEC-002, DEC-007, DEC-009, DEC-010; ADR-004, ADR-006, ADR-009; RNF-001, RNF-007;
`.claude/rules/infra.md`, `.claude/rules/terraform.md`, `.claude/rules/security.md`;
`docs/architecture/deployment-view.md` (Vista B), `docs/infrastructure/environments.md`.

## Estado actual verificado

- `infra/modules/`, `infra/environments/dev/`, `infra/environments/local/`: solo `.gitkeep`.
- `docs/infrastructure/terraform-plan.md`: una línea. Azure "planned, not provisioned".
- Topología ya decidida: Azure Container Apps (backend y RabbitMQ como contenedor), PostgreSQL
  Flexible Server, Key Vault + Managed Identity, Application Insights/Log Analytics, ACS Email
  (DEC-007, opcional en este spec).

## Decisión requerida — ADR-012 (infraestructura Azure)

Cambia arquitectura de despliegue: requiere ADR antes de codificar (`new-adr`, 8 secciones). Debe
cerrar:

| Punto | Recomendación |
|---|---|
| Entorno | Un único entorno `dev` (`azure-planned` en `environments.md`) en un solo grupo de recursos; sin prod separada (MVP académico) |
| Cómputo | Container Apps Environment + 5 apps (Gateway con ingress externo; el resto interno) + RabbitMQ como app interna con volumen/almacenamiento persistente |
| mTLS gRPC interno | Cómo se entregan certificados en ACA (secretos de Key Vault referenciados) y cómo se conserva el CN `gateway`; **verificar** compatibilidad de gRPC/HTTP2 interno en ACA al diseñar |
| Datos | PostgreSQL Flexible Server (ADR-006: una instancia, un esquema por servicio), acceso privado o reglas de firewall mínimas, backups nativos activados |
| Secretos | Key Vault + Managed Identity asignada por app (DEC-010); ninguna credencial en `tfvars` versionado |
| Estado de Terraform | Backend remoto `azurerm` en Storage Account con bloqueo; nunca estado en Git |
| Registro de imágenes | D-22 de SPEC-015 (ACR con `AcrPull` recomendado) |
| Autenticación CI/CD | D-23 de SPEC-015 (OIDC federado) |
| Observabilidad | Log Analytics + Application Insights; el Collector de SPEC-011 exporta por configuración |
| Costos | Presupuesto y *tags* (`project`, `env`, `owner`, `ttl`), alerta de presupuesto y plan de desmontaje (`destroy` con aprobación) |
| Regiones/SKUs | Elegir la más barata disponible para los servicios; los valores concretos son variables, no constantes |

## Estructura de código

```
infra/
  modules/
    network/            # VNet/subredes si el ADR elige acceso privado
    observability/      # Log Analytics, Application Insights
    registry/           # ACR (si D-22 = ACR)
    keyvault/           # Key Vault + access policies/RBAC
    postgres/           # Flexible Server, bases/esquemas, parámetros
    containerapps-env/  # Environment + logging
    container-app/      # módulo reutilizable (imagen, env, secretos desde KV, scale, probes)
    rabbitmq/           # app + almacenamiento
  environments/
    dev/                # composición, variables, outputs, backend.tf (sin valores sensibles)
    local/              # documenta que local usa Compose; sin recursos
  README.md
```

Reglas: versiones de provider y Terraform fijadas; variables tipadas con descripción y validación;
`outputs` sin secretos (`sensitive = true` donde aplique); nada de IDs de suscripción/tenant en el
código; `terraform.tfvars.example` sin valores reales; `.gitignore` cubre `*.tfstate*`, `*.tfplan`,
`*.tfvars` con secretos y `.terraform/`.

## Flujo con aprobación

1. Escribir módulos y composición `dev`.
2. `terraform fmt -check`, `terraform init -backend=false`, `terraform validate` (permitidos sin
   credenciales; es lo que corre en CI, SPEC-014).
3. `terraform plan` solo con credenciales del humano y **aprobación previa**; la salida se revisa y
   se archiva sin valores sensibles.
4. **`terraform apply`: lo ejecuta el humano** tras revisar el `plan`, con un `CHG-NNN` (SPEC-013).
   El asistente no lo ejecuta y no usa `az`.
5. Verificación posterior y registro de recursos creados, con *tags* y presupuesto.
6. Desmontaje (`destroy`) también bajo aprobación al terminar la entrega, para controlar costos.

## Criterios de aceptación

1. ADR-012 aprobado y enlazado antes del primer módulo.
2. `fmt`, `init -backend=false` y `validate` pasan en cada módulo y en `environments/dev`.
3. Un `plan` revisado por el humano (si hay credenciales) queda archivado en
   `docs/academic/apf3/evidencias/`, sin secretos.
4. No hay estado, `tfvars` con secretos ni credenciales en Git (`gitleaks` limpio).
5. `docs/infrastructure/terraform-plan.md`, `environments.md` y `deployment-view.md` reflejan el
   estado real: "código listo" vs. "aprovisionado"; nunca "desplegado" sin evidencia del `apply`.
6. Revisión `coldguard-devops` + `coldguard-security` sin hallazgos bloqueantes.

## Tareas

1. Redactar ADR-012 con `new-adr` (y DEC de costos/desmontaje si procede).
2. Módulos, composición `dev`, `README` de infra.
3. Validación local y revisión de seguridad.
4. Preparar la lista de prerrequisitos humanos: suscripción, rol, federación OIDC, backend de
   estado, presupuesto.
5. **Pedir aprobación** para `plan` y luego para `apply` (puntos de parada explícitos).

## Riesgos

- Sin suscripción o sin aprobación, no hay despliegue real: el informe lo declarará.
- Costos y recursos huérfanos: *tags*, presupuesto y `destroy` planificado.
- Límites de ACA con gRPC interno/mTLS o almacenamiento de RabbitMQ: se verifican al escribir el
  ADR; si no son viables se documenta la alternativa en el propio ADR.

## Fuera de alcance

Multi-entorno, alta disponibilidad y DR (Sprint 7); ACS Email (opcional, solo con aprobación
aparte, DEC-007).
