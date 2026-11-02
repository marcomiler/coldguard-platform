# SPEC-012 — Catálogo de solicitudes de servicio (APF3 · Cap. 8.1)

## Objetivo

Definir y documentar el catálogo de solicitudes de servicio de ColdGuard (qué puede pedir cada actor
a la plataforma, quién lo atiende, con qué prioridad y en qué plazo objetivo) y su procedimiento de
atención, enlazados a lo que el sistema ya soporta.

## Trazabilidad

APF3 Cap. 8.1; ITIL "Request Fulfilment" (la bibliografía final se consolida en SPEC-019);
`docs/product/stakeholders.md`, `docs/domain/use-cases.md`, `docs/security/authn-authz.md`,
`docs/quality/sla-kpi.md`, `docs/operations/incident-model.md`, `docs/operations/runbooks.md`.

## Estado actual verificado

- `docs/operations/service-requests.md` tiene **una línea** ("Diferido a Sprint 5-7").
- Las solicitudes ya existen como casos de uso y rutas del Gateway (alta de activos y sensores,
  calibración, reasignación, administración de usuarios, reprocesar evaluación CU-015, exportar
  métricas/auditoría), pero no están catalogadas como servicio.
- No existe matriz de destinatarios ni plazos confirmados por negocio: rige DEC-022 (valores
  placeholder académico).

## Decisión requerida — D-18 (plazos y modelo del catálogo)

Recomendación: catálogo de **solicitudes de servicio** distinto de **incidentes** (un incidente es
una interrupción o desviación; una solicitud es un pedido estándar y planificable). Plazos objetivo
de atención marcados **placeholder académico** (nunca como compromiso del negocio). Registro: DEC
nuevo en `docs/planning/decisions-log.md`.

## Contenido a producir (`docs/operations/service-requests.md`)

1. **Alcance y diferencia** solicitud vs. incidente vs. cambio (referencia a SPEC-013 para cambios).
2. **Catálogo** — una tabla con columnas: ID `SR-NNN`, nombre, descripción, solicitante (rol de
   `stakeholders.md`; no se inventan roles), aprobador (si aplica), responsable de atención, ruta
   REST del Gateway o procedimiento operativo, CU/RF relacionados, prioridad por defecto (P1–P4,
   RN-012), plazo objetivo (placeholder), evidencia que deja (auditoría `auditlog`, métrica o ticket).
   Entradas mínimas, todas verificables en el repositorio:
   - Alta/baja/actualización de organización, sede y activo.
   - Alta, asignación, reasignación y retiro de sensor; registro de calibración.
   - Alta y cambio de roles de usuario (`PLATFORM_ADMIN`).
   - Reprocesar evaluación de lecturas de prueba (CU-015).
   - Consulta/exportación de auditoría y métricas operativas.
   - Restablecer entorno demo (`reset-demo.sh`), respaldo de datos (`backup-db.sh`) y regeneración
     de certificados/claves JWT (runbooks existentes).
   - Solicitudes de acceso a observabilidad (Grafana) — solo si existe rol/usuario definido; si no,
     se marca "no soportada en el MVP".
3. **Flujo de atención**: diagrama Mermaid (recepción → clasificación → aprobación → ejecución →
   verificación → cierre) y estados del ticket (se reutiliza la plantilla de issue de GitHub de
   SPEC-013 para solicitudes).
4. **Matriz RACI** por solicitud (solicitante, aprobador, ejecutor, informado).
5. **Medición**: qué KPI de `sla-kpi.md` alimenta cada solicitud (cumplimiento del plazo objetivo,
   volumen por tipo). Sin números inventados: pendiente/placeholder según
   `.claude/rules/documentation.md`.
6. **Solicitudes no soportadas** (fuera del MVP) para evitar prometer capacidades inexistentes.

## Plantilla de issue

`.github/ISSUE_TEMPLATE/service-request.md`: campos catálogo ID, solicitante, justificación, entorno
(local/azure), aprobación requerida, evidencia de cierre. Sin datos personales ni secretos.

## Criterios de aceptación

1. Cada fila del catálogo apunta a una ruta, script o runbook **existente** (comprobable con
   `consistency-check`); lo no soportado está en la lista de "no soportadas".
2. Todos los roles citados existen en `stakeholders.md`.
3. Ningún plazo se presenta sin la marca placeholder académico.
4. Al menos tres solicitudes del catálogo se ejecutan de punta a punta en local y su evidencia real
   (respuestas, bitácora, tamaños de volcado) está incluida en el propio documento. **Hecho el
   2026-11-02: SR-007, SR-004 y SR-015.**
5. `consistency-check` no reporta enlaces rotos ni IDs huérfanos.

## Tareas

1. Registrar D-18 (DEC).
2. Redactar el catálogo, el flujo y la RACI.
3. Crear la plantilla de issue.
4. Ejecutar y capturar evidencia de tres solicitudes (se archiva en `docs/academic/apf3/evidencias/`,
   ver SPEC-019).
5. Pasar `consistency-check`.

## Riesgos

- Plazos y destinatarios sin confirmación del negocio: mitigado con la marca placeholder y DEC-022.
- Catálogo inflado con solicitudes que el MVP no soporta: se limita a lo verificable.

## Fuera de alcance

Herramienta ITSM externa; portal de autoservicio; SLA contractuales.
