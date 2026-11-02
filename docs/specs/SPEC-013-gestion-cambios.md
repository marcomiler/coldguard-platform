# SPEC-013 — Gestión de cambios (APF3 · Cap. 8.2)

## Objetivo

Definir el proceso de gestión de cambios de ColdGuard (tipos, evaluación de riesgo, aprobación,
ventanas, rollback y registro) apoyado en las herramientas ya usadas: ramas de feature, pull
requests, GitHub Actions y, para Azure, `terraform plan` revisado y `apply` aprobado.

## Trazabilidad

APF3 Cap. 8.2; ITIL "Change Enablement"; `.claude/rules/git-workflow.md`,
`.claude/rules/terraform.md`, `.claude/rules/workflow.md` (ADR vs DEC),
`docs/planning/definition-of-done.md`, `docs/quality/risk-register.md`, SPEC-014 (CI), SPEC-015 (CD).

## Estado actual verificado

- No existe documento de gestión de cambios.
- Ya hay reglas dispersas que son de hecho política de cambios: commits solo a petición, sin
  `--no-verify`, `push` con confirmación, ADR/DEC para decisiones, `apply/destroy/import` solo con
  aprobación humana.
- `.github/ISSUE_TEMPLATE/` tiene plantillas, pero ninguna de cambio ni de solicitud.

## Decisión requerida — D-19 (modelo de cambios)

Recomendación:

| Tipo | Ejemplo | Aprobación | Registro |
|---|---|---|---|
| Estándar | Ajuste de dashboard, documentación, dependencia menor con CI verde | Revisión de PR (1 aprobación o autorrevisión documentada en equipo de 1–2) | PR |
| Normal | Nueva funcionalidad, migración Flyway, cambio de contrato gRPC/evento | PR + checklist de riesgo + ADR/DEC si es arquitectónico | Issue de cambio + PR |
| Emergencia | Corrección de un incidente en el entorno desplegado | Aprobación verbal posterior registrada ≤ 24 h | Issue de cambio retroactivo + post-mortem |
| Infraestructura | Cualquier `terraform apply/destroy/import`, recurso Azure | **Aprobación humana explícita previa**, `plan` adjunto | Issue + `plan` revisado + environment protegido (SPEC-015) |

Registro: DEC nuevo. Si se introduce un patrón arquitectónico (p. ej. política de despliegue
gradual), ADR aparte.

## Contenido a producir (`docs/operations/change-management.md`)

1. Alcance, definiciones y relación con solicitudes (SPEC-012) e incidentes (SPEC-018/Sprint 7).
2. Tipos de cambio (tabla anterior) y criterios de clasificación.
3. **Evaluación de riesgo e impacto**: checklist (datos, contratos, seguridad, observabilidad,
   costo Azure, reversibilidad) enlazada con `risk-register.md`; matriz impacto × probabilidad
   reutilizando las escalas de RN-010/RN-011.
4. **Flujo**: Mermaid (solicitud → evaluación → aprobación → implementación en rama → CI → revisión
   → despliegue → verificación → cierre/rollback).
5. **Ventanas y congelamiento**: congelamiento previo a APF3/final (placeholder académico).
6. **Plan de rollback por capa**: aplicación (redeploy de la imagen/revisión anterior de Container
   Apps), base de datos (Flyway no se edita; migración compensatoria, `restore` de backup según
   SPEC de continuidad), infraestructura (revertir commit de Terraform + `plan`; `destroy`
   solo con aprobación).
7. **Registro de cambios (CAB mínimo)**: tabla `CHG-NNN` con fecha, tipo, descripción, riesgo,
   aprobador, PR, resultado. Se siembra con cambios reales del repositorio (p. ej. adopción de
   observabilidad, DEC-019; CI) como evidencia, sin fabricar.
8. **Métricas**: lead time, tasa de fallos de cambio, tiempo de recuperación (definidos; valores
   medidos solo si existen datos reales).

## Plantillas y automatización

- `.github/ISSUE_TEMPLATE/change-request.md` (tipo, riesgo, rollback, aprobador, ventana).
- `.github/pull_request_template.md` con checklist: tests, Spotless, sin secretos, ADR/DEC si aplica,
  documentación, plan de rollback.
- `CODEOWNERS` solo si el equipo tiene más de un revisor (decisión local; no se crea vacío).

## Criterios de aceptación

1. El documento cubre los cuatro tipos con aprobador y registro; ningún paso contradice las reglas
   `.claude/rules/*`.
2. Existen las plantillas y se probó que GitHub las ofrece al crear issue/PR.
3. El registro `CHG-NNN` tiene al menos dos entradas reales con enlace a PR o commit. **Hecho el
   2026-11-02: CHG-001 a CHG-006 (cinco retroactivas del historial y la prueba de reversión).**
4. El plan de rollback de aplicación se **prueba en local** (volver a la imagen anterior con
   Compose) y se anexa la salida. **Hecho el 2026-11-02:** cambio y reversión del Gateway, 9 s,
   comportamiento verificado (HTTP 400 → 403 → 400).
5. `consistency-check` sin hallazgos nuevos.

## Tareas

1. Registrar D-19 (DEC).
2. Redactar `change-management.md`.
3. Plantillas de issue y PR.
4. Registro `CHG-NNN` inicial y prueba de rollback local.
5. Referencias cruzadas desde `service-requests.md`, `runbooks.md` y `definition-of-done.md`.

## Riesgos

- Proceso excesivo para un equipo pequeño: se mantiene mínimo y se declara el uso de
  autorrevisión documentada.
- Rollback de Azure no verificable hasta que exista despliegue (SPEC-017): se declara como
  "diseñado, validado en local".

## Fuera de alcance

CAB formal, herramienta ITSM, despliegues canary/blue-green (se mencionan como evolución).
