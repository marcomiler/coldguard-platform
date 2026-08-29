---
name: coldguard-academic-trace
description: Tracks APF1/APF2/APF3/proyecto final deliverable evidence against docs/planning/roadmap.md y docs/academic/, generalizando el mapeo hoy limitado a APF1. Use when the user asks about academic-deliverable completeness, evidence for a given APF phase, or "what's missing for APF1/APF2/APF3/entrega final".
---

Absorbe la capacidad del antiguo comando `/prepare-apf1` (eliminado por duplicidad estructural,
`.claude/README.md`) y generaliza `docs/academic/apf1-mapping.md` a las fases que todavía no
tienen su propio mapeo: APF2, APF3 y proyecto final.

## Fuente de verdad — no duplicar

- **APF1**: `docs/academic/apf1-mapping.md` (matriz de estado detallada) y
  `docs/academic/report-artifacts-index.md` (índice de artefactos) son la única fuente de verdad
  para APF1. Este skill **referencia** esos dos archivos, no repite su contenido ni mantiene una
  copia paralela de la lista de entregables.
- **Fases académicas**: `docs/planning/roadmap.md` — cada sprint trae una etiqueta "Relación con
  APF/proyecto final" (p. ej. "APF1", "APF2 (v0.2)", "APF3 (v0.3)", "proyecto final (v1.0)"). Esa
  etiqueta es la que determina qué sprints corresponden a qué fase; no se mantiene una lista
  separada aquí que pueda desincronizarse del roadmap.

## Lógica: dado una fase académica consultada

1. Leer `docs/planning/roadmap.md` y listar los sprints cuya etiqueta "Relación con APF/proyecto
   final" corresponde a la fase pedida (APF1, APF2, APF3, o proyecto final).
2. Para APF1 específicamente: no recalcular nada — leer directamente
   `docs/academic/apf1-mapping.md` y `docs/academic/report-artifacts-index.md`, que ya están
   completos y vigentes, y reportar su contenido/vacíos pendientes tal cual están documentados
   allí.
3. Para APF2, APF3 o proyecto final: a partir de los sprints identificados en el paso 1, derivar
   qué entregables espera esa fase (mismo criterio que usa `apf1-mapping.md` para APF1: propuesta,
   análisis de negocio, RF/RNF, casos de uso, catálogo de eventos, arquitectura, riesgos, SLA/KPI,
   plan de sprints, prototipos, etc. — solo los que la fase en cuestión realmente requiera según el
   roadmap, no la lista completa de APF1 por defecto).
4. Para cada entregable esperado de la fase consultada, verificar contra archivos reales del
   repositorio y reportar exactamente uno de estos tres estados:
   - **Presente**: el archivo/evidencia existe y cubre lo esperado.
   - **Incompleto**: existe pero le falta contenido específico — decir qué falta, no solo marcar
     la casilla.
   - **Faltante / no verificable**: no existe evidencia real, o la evidencia vive fuera de este
     repositorio (p. ej. `coldguard-frontend`) y no hay enlace/commit/referencia verificable desde
     aquí. **Nunca** se asume que algo existe sin poder verificarlo — ver la regla ya aplicada al
     "Prototipos y frontend inicial" en `apf1-mapping.md` como el caso de referencia para este
     patrón.

## Cuándo crear un mapeo nuevo (y cuándo no)

- `docs/academic/apf2-mapping.md`, `apf3-mapping.md` y `proyecto-final-mapping.md` **no existen
  todavía y no se crean por adelantado**. Se crean únicamente cuando esa fase arranca de verdad
  (según el roadmap), siguiendo la misma estructura que `apf1-mapping.md` (tabla Entregable /
  Archivo-evidencia / Estado / Observaciones, más una sección de "vacíos reales pendientes").
- Antes de que la fase arranque, este skill solo **reporta** el estado en la conversación — no
  escribe un archivo de mapeo nuevo por su cuenta. Crear el archivo es una acción explícita que
  espera aprobación, igual que cualquier otra escritura a `docs/`.

## Explícitamente fuera de alcance (delegar, no reimplementar)

- **Validación mecánica de enlaces Markdown e integridad de IDs** (RF-/RN-/CU-/ADR-/DEC-
  duplicados o renumerados): usar la skill `consistency-check`, no reimplementar esa lógica aquí.
- **Auditoría de consistencia entre `docs/architecture`, `docs/domain` y los ADR**: usar el agente
  `architecture-auditor`, no reimplementar esa lógica aquí.
- **Reglas de disciplina de IDs** (nunca reusar/renumerar/eliminar un ID, estructura de 8 secciones
  de ADR, etc.): viven en `.claude/rules/documentation.md`, siempre cargadas — este skill no las
  repite ni las redefine.

## Reglas

- No modificar `docs/architecture`, `docs/domain`, los ADR ni `docs/planning/decisions-log.md`
  como parte de este reporte — es una auditoría de completitud de entregables, no una revisión de
  arquitectura ni de dominio.
- No inventar evidencia: si un entregable no se puede confirmar contra un archivo real, se marca
  "faltante" o "no verificable", nunca se asume que existe.
- No ejecutar infraestructura ni generar código de aplicación como parte de este reporte.
- Este reporte es de solo lectura: propone contenido para un mapeo nuevo (cuando corresponda) y
  espera aprobación antes de escribirlo — no escribe `docs/academic/*.md` sin que se apruebe.

**Este skill no reabre decisiones de arquitectura cerradas; para eso, consulta al agente
architecture-auditor.**
