# SPEC-019 — Cierre de APF3: retrospectiva, mapeo, evidencias y alineación documental (Cap. 10.3 y transversal)

## Objetivo

Cerrar la entrega: retrospectiva del Sprint 6 (10.3), mapeo APF3 → evidencias, índice de
artefactos, alineación de roadmap/release/backlog con la nueva numeración y estado real, y una
revisión final de consistencia, para que el informe APF3 no tenga huecos ni afirme lo no hecho.

## Trazabilidad

APF3 Cap. 8 a 10; `docs/academic/apf1-mapping.md` (formato de referencia),
`docs/academic/report-artifacts-index.md`, `docs/planning/roadmap.md`, `release-plan.md`,
`docs/product/product-backlog.md`, `.claude/skills/coldguard-academic-trace`, `consistency-check`.

## Estado actual verificado

- No existe `apf3-mapping.md` ni carpeta `docs/academic/apf3/`; `docs/academic/apf2/` tiene solo
  `figuras`.
- `report-artifacts-index.md` es del informe APF1 (backlog HU-001 a HU-020, DEC-001).
- `roadmap.md` y `release-plan.md` usan la numeración técnica antigua de sprints; el backlog de
  GitHub (HU-046 a HU-085) sigue una numeración propia. El archivo
  `docs/planning/github-backlog-sprints-5-8.md` redactado en la sesión anterior **no está en el
  disco**; debe regenerarse si se quiere conservar.
- No existe retrospectiva del Sprint 6 ni de sprints anteriores en el repositorio (verificar).

## Entregables

### 1. Retrospectiva del Sprint 6 (10.3) — `docs/planning/retro-sprint-6.md`
- Objetivo del sprint y resultado real (hecho / parcial / no hecho, con enlaces a evidencia).
- Métricas **medibles de verdad**: historias y puntos cerrados vs. planificados, commits/PR, runs de
  CI, defectos. Sin valores inventados; "no medido" cuando no hay dato.
- Qué funcionó, qué no, causas raíz, acciones de mejora con responsable y fecha (carry-over a
  Sprint 7).
- Riesgos materializados y actualización de `risk-register.md` (nuevos IDs, sin renumerar).

### 2. Mapeo y evidencias — `docs/academic/apf3-mapping.md` y `docs/academic/apf3/`
- Tabla: capítulo/sección APF3 → documento fuente → evidencia → estado (Completo / Parcial /
  Planificado) → nota honesta. Cubre 8.1, 8.2, 9.1, 9.2, 10.1, 10.2, 10.3 y "Despliegue versión
  final".
- `docs/academic/apf3/evidencias/`: capturas y salidas referenciadas (runs de CI, smoke, planes,
  dashboards, pruebas de carga), sin secretos ni datos personales ni logs crudos con tokens.
- `docs/academic/apf3/figuras/`: diagramas del pipeline CI/CD y de la topología Azure (Mermaid
  exportado), coherentes con ADR-012.
- Esqueleto del informe APF3 por capítulos con enlaces a los documentos fuente (no se duplica
  contenido; se referencia).

### 3. Índice de artefactos
- Actualizar `report-artifacts-index.md` con la sección APF3 (o crear un índice separado) con
  estado real por artefacto.

### 4. Alineación documental (cambio estructural → audit/propuesta/aprobación, `documentation.md`)
- `roadmap.md`: reconciliar la numeración de sprints con la estructura vigente
  (Sprint 5: backend operativo e infraestructura; Sprint 6: gestión del servicio, CI/CD y despliegue;
  Sprint 7: operación; Sprint 8: cierre) y las fechas del calendario.
- `release-plan.md`: v0.3 = APF3, v1.0 = proyecto final; estado real (sin marcar "entregada" sin
  evidencia).
- `product-backlog.md`: añadir HU-046 a HU-085 o referenciar el backlog de GitHub; corregir el estado
  de HU-019 a HU-026 ya implementadas.
- Regenerar `docs/planning/github-backlog-sprints-5-8.md` (si el usuario lo confirma) y, opcional,
  el script de creación de issues con `gh`.
- `README.md` raíz: estado, enlaces a CI/CD e infraestructura.
- Actualizar `.claude/rules/terraform.md` y `infra.md` si SPEC-016 cambia el estado ("placeholders
  only" deja de ser cierto) — con cambio mínimo y aprobación.

### 5. Revisión final
- Ejecutar `consistency-check`, `architecture-auditor` (por ADR-012), `coldguard-academic-trace`
  y `verify-backend`; registrar resultados.
- Lista de verificación "sin huecos": cada sección APF3 con enlace a evidencia; cada afirmación
  de despliegue con su prueba; cada placeholder rotulado.

## Criterios de aceptación

1. Cada sección del índice APF3 tiene fila en `apf3-mapping.md` con estado verificable.
2. Ningún documento afirma "desplegado" sin evidencia (grep de "desplegad" revisado).
3. `roadmap.md`, `release-plan.md` y `product-backlog.md` coinciden en numeración y fechas.
4. La retrospectiva usa solo datos reales o marca "no medido".
5. `consistency-check` y `verify-backend` pasan; el informe de cierre lista archivos cambiados,
   comandos, validaciones y riesgos pendientes.

## Tareas

1. Auditoría rápida del estado final de SPEC-012 a SPEC-018.
2. Retrospectiva; mapeo; evidencias; figuras; índice.
3. Propuesta de alineación documental → aprobación → edición.
4. Revisión final y reporte.
5. (Solo si el usuario lo pide) commit por spec, en inglés, sin push automático.

## Riesgos

- Dejar este spec para el último día: empezar el esqueleto y el mapeo en cuanto se terminen los
  primeros specs, y completar con evidencias al final.
- Divergencia entre backlog de GitHub y documentación: el spec obliga a reconciliar.

## Fuera de alcance

Informe final del proyecto (Cap. XI a XIV: incidentes, monitoreo, DRP, conclusiones), cubierto
por los specs siguientes del Sprint 7 y 8.
