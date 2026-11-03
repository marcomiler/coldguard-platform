# SPEC-015 — Automatización de despliegues, CD (APF3 · Cap. 9.2)

## Estado (2026-11-03)

Escrito y verificado con `actionlint`; **no ejecutado contra Azure**. Según ADR-012 y DEC-026: GHCR
en lugar de ACR; `release-images.yml` (compuerta de CI, Trivy, publicación con `sha-<commit>` y
`vX.Y.Z`); `azure-dev.yml` reemplaza a `infra-plan.yml` y `deploy-azure.yml` (acción `apply` o
`destroy`, trabajos `plan` y `change` bajo el *environment* `azure-dev`, con smoke posterior). Sin
rollback automático: el rollback es un `apply` con la etiqueta anterior. Documentos:
`cd-pipeline.md` y `deployment-checklist.md`.

## Objetivo

Automatizar con GitHub Actions la publicación de imágenes y el despliegue hacia Azure Container Apps
(y el `plan` de Terraform), con autenticación sin secretos de larga vida (OIDC), aprobación humana
por entorno y rollback documentado. El workflow se entrega **completo y revisable**; su ejecución
real contra Azure requiere aprobación (SPEC-016/017).

## Trazabilidad

APF3 Cap. 9.2; DEC-002, DEC-009, DEC-010; `.claude/rules/infra.md`, `.claude/rules/terraform.md`,
`.claude/rules/security.md`; SPEC-013 (cambios), SPEC-014 (CI), SPEC-016 (infra).

## Estado actual verificado

- No hay workflows de CD; `docs/infrastructure/deployment-checklist.md` y `terraform-plan.md`
  tienen una línea.
- Los 5 `Dockerfile` construyen en local; no se publican imágenes a ningún registro.
- Azure no está provisionado; `az` está denegado para el asistente.

## Decisiones requeridas

| ID | Decisión | Recomendación | Registro |
|---|---|---|---|
| D-22 | Registro de imágenes | Azure Container Registry con Managed Identity (`AcrPull`) para Container Apps; alternativa GHCR (requiere credencial de pull como secreto). Decidir según costo y simplicidad | Se incluye en el ADR-012 de SPEC-016 |
| D-23 | Autenticación de GitHub a Azure | OIDC con identidad federada (sin `client secret`); un *environment* de GitHub por destino (`azure-dev`) con **revisores requeridos** | Se incluye en el ADR-012 |
| D-24 | Estrategia de versionado de imágenes | Etiqueta inmutable `sha-<commit>` + `vX.Y.Z` en releases (alineado a `release-plan.md`: v0.3 para APF3, v1.0 final); nunca `latest` para desplegar | DEC |

## Diseño de workflows

1. **`release-images.yml`** (disparo: tag `v*` o `workflow_dispatch`): build Buildx de las 5
   imágenes, escaneo de imágenes (Trivy; fallo en críticos con exenciones documentadas), SBOM
   opcional, push al registro (D-22). Requiere que CI haya pasado sobre el commit.
2. **`infra-plan.yml`** (PR que toca `infra/**`): `terraform fmt/validate/plan` con backend remoto de
   solo lectura vía OIDC; publica el `plan` como comentario/artefacto (sin valores sensibles).
   **Nunca ejecuta `apply`.**
3. **`deploy-azure.yml`** (`workflow_dispatch` con entrada `version`; *environment* protegido):
   - Pasos: login OIDC → `terraform plan` de verificación → pausa por aprobación del environment →
     actualización de revisión de cada Container App a la etiqueta indicada → espera de
     `readiness` → `smoke-e2e` contra el FQDN del Gateway (SPEC-017) → resumen del despliegue.
   - Si el smoke falla: **rollback automático** a la revisión anterior (Container Apps conserva
     revisiones) y el job termina en rojo.
   - `concurrency` por entorno para evitar despliegues simultáneos.
4. **`terraform-apply`**: **no se automatiza** en este spec. El `apply` lo ejecuta una persona con
   aprobación explícita (`.claude/rules/terraform.md`); el workflow solo puede preparar y mostrar el
   `plan`. Si el equipo decide automatizarlo después, requiere DEC propio.

Secretos y variables:

- Solo *variables* de repositorio/entorno con identificadores públicos de la federación (client id,
  tenant id, subscription id como `vars`, no en el código) — nunca valores de secreto en el
  repositorio ni en `tfvars` versionados.
- Los secretos de aplicación viven en Key Vault y se leen con Managed Identity (DEC-010).

## Documentación

- `docs/infrastructure/deployment-checklist.md`: precondiciones, orden de despliegue, verificación y
  rollback (completar el stub).
- `docs/infrastructure/cd-pipeline.md` (nuevo): diagrama de los tres workflows, entornos, aprobaciones.
- Enlaces con SPEC-013: todo despliegue genera un `CHG-NNN`.

## Criterios de aceptación

1. `actionlint` y `terraform validate` pasan sobre los workflows y módulos.
2. Con `act` o un *dry-run* documentado, `release-images.yml` construye las 5 imágenes sin publicar.
3. Los workflows de Azure están **inactivos por diseño** (requieren environment y variables
   inexistentes) hasta que SPEC-016 se aplique; el documento lo declara.
4. Revisión de seguridad (`coldguard-security`): sin secretos, permisos mínimos, acciones fijadas,
   OIDC con subject restringido a la rama/entorno.
5. Tras la aprobación y el `apply` (SPEC-016), un despliegue real de la versión final deja evidencia
   en SPEC-017.

## Tareas

1. Registrar D-22/D-23 (ADR-012) y D-24 (DEC).
2. Escribir los tres workflows y el doc de CD.
3. Completar `deployment-checklist.md` y `terraform-plan.md`.
4. Revisión de seguridad y `actionlint`.
5. Preparar el listado de variables de entorno de GitHub que el humano debe crear (sin valores).

## Riesgos

- No poder probar el CD sin Azure real: se declara el límite y se prueba lo local (build, lint,
  validate).
- Costo de ejecución de imágenes/escaneos: limitar disparadores.

## Fuera de alcance

`terraform apply` automatizado; despliegues multi-región; canary/blue-green.
