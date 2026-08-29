# Proceso de refinamiento de backlog

Formaliza cómo y cuándo se detallan historias de usuario en `docs/product/product-backlog.md`, a
partir del hallazgo de la auditoría de planificación: detallar historias de sprints futuros por
adelantado genera retrabajo cuando cambian decisiones de sprints anteriores. Este documento fija
la regla en adelante.

## Regla central

**Solo se detalla el sprint N+1 — el siguiente a iniciar —, nunca más de uno por adelantado.**

- Las épicas de sprints posteriores a N+1 permanecen a nivel de épica en la tabla de
  `docs/product/product-backlog.md` ("Épicas"), con su RF/CU cubierto y su sprint objetivo, **sin
  historias de usuario detalladas** hasta que les toque ser N+1.
- Ejemplo vigente al momento de escribir este documento: `EPIC-13` (observabilidad) y `EPIC-14`
  (pérdida de conectividad) están asignadas a Sprint 5 y permanecen a nivel de épica; no se
  detallan historias para ellas hasta el refinamiento previo a Sprint 5.

## Cuándo se ejecuta

El refinamiento del sprint N+1 se ejecuta **antes de iniciar ese sprint** (al cierre del sprint N,
o como ceremonia dedicada previa a su Sprint Planning) — nunca durante la planificación de un
sprint anterior ni varios sprints por adelantado.

## Qué se detalla

Para cada épica del sprint N+1:

1. Se redactan las historias de usuario en formato `Como/quiero/para`, con todos los campos
   obligatorios de `docs/planning/definition-of-ready.md`.
2. Se aplica la auditoría INVEST de ese mismo documento; toda historia "No lista" se corrige antes
   de entrar a Sprint Planning o queda explícitamente fuera del sprint.
3. Si una historia toca más de un CU o más de una RN, se evalúa la división según la plantilla de
   `docs/planning/definition-of-ready.md` §3 — la división se **ejecuta en este mismo
   refinamiento**, no antes.
4. Se agrega la trazabilidad RF/RN/CU/ADR/DEC correspondiente y se verifica contra
   `docs/domain/traceability-matrix.md`.

## Qué se revisa antes de aceptar el detalle

1. **Consistencia con arquitectura cerrada**: ninguna historia nueva puede contradecir una
   decisión ya registrada en `docs/architecture/architecture-consistency-report.md` (estado
   CERRADO, Rondas 1-5) — Azure como proveedor, RabbitMQ como broker, Identity & Access/Audit
   Log/métricas como módulos internos de Incident Service, Key Vault + Managed Identity
   planificado, observabilidad por entorno, etc. Si el refinamiento detecta que una decisión de
   arquitectura necesita cambiar, **no se reescribe el reporte de arquitectura ni se decide desde
   el backlog**: se registra una entrada nueva en `docs/planning/decisions-log.md` y, si aplica,
   se referencia desde la historia.
2. **Consistencia con `docs/planning/decisions-log.md`**: cualquier decisión puntual posterior al
   cierre del lote de arquitectura (DEC-011 en adelante) se revisa y se refleja en las historias
   del sprint que la implementen.
3. **Definición de Listo**: toda historia debe cumplir
   `docs/planning/definition-of-ready.md` antes de salir del refinamiento hacia Sprint Planning.

## Qué NO se hace en un refinamiento

- No se detallan historias de sprints posteriores a N+1, aunque su épica ya exista.
- No se renumeran ni eliminan IDs existentes (RF, RN, RNF, CU, ADR, DEC, riesgo, épica, historia).
- No se marca ninguna historia como completada durante el refinamiento — eso ocurre solo al
  cumplirse `docs/planning/definition-of-done.md`, después de ejecutar el trabajo.
- No se reabren decisiones de arquitectura ya cerradas; se documentan como hallazgo y se elevan a
  `decisions-log.md` si corresponde un ajuste puntual.

## Roles (por rol, no por persona)

| Rol | Responsabilidad en el refinamiento |
|---|---|
| Product Owner | Propone y prioriza las historias del sprint N+1; decide re-alcance o retiro de historias obsoletas |
| Scrum Master | Facilita la sesión, verifica que se cumpla esta regla (solo N+1) y la Definición de Listo |
| Equipo técnico | Estima, valida factibilidad, propone divisiones cuando una historia falla "Pequeña" |

## Excepción: auditorías de cierre de lote

Una auditoría de cierre de lote (como la que originó este documento) puede detallar más de un
sprint por adelantado si su objetivo explícito es dejar el backlog completo listo para revisión —
eso es una excepción documentada, no la regla operativa. La regla operativa de este documento
aplica a partir de la fecha de creación de este proceso, hacia adelante.
