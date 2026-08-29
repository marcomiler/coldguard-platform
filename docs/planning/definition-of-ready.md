# Definición de Listo (Definition of Ready)

Criterios que debe cumplir una historia de usuario antes de poder entrar a Sprint Planning.
Aplicados por primera vez en la auditoría de planificación del lote de Sprint 1-2
(`docs/product/product-backlog.md`); este documento formaliza esos criterios para su uso
recurrente en cada refinamiento (ver `docs/planning/refinement-process.md`).

Una historia que no cumple todos los criterios de este documento se marca **"No lista"** y no
entra a Sprint Planning hasta resolverse.

## 1. Criterios INVEST

| Criterio | Qué verifica | Cómo se audita |
|---|---|---|
| **I — Independiente** | La historia puede desarrollarse y entregar valor sin bloquear ni ser bloqueada innecesariamente por otra historia del mismo sprint | Revisar la sección "Dependencias"; una dependencia hacia un sprint anterior es aceptable, hacia el mismo sprint debe ser mínima y explícita, hacia un sprint posterior es un error (dependencia invertida) |
| **N — Negociable** | El **cómo** no está sobreespecificado; el equipo puede proponer la solución técnica dentro del alcance de la historia | Revisar que la historia describa comportamiento/resultado, no una implementación obligatoria |
| **V — Valiosa** | Tiene un valor de negocio o técnico explícito, articulado en el **para** de la historia (`Como/quiero/para`) | El campo **para** debe nombrar un beneficio concreto, no solo repetir el **quiero** |
| **E — Estimable** | El equipo puede darle una estimación (story points, talla o "por estimar" documentado explícitamente si aún no se ha estimado) | Debe existir el campo "Estimación"; no puede estar ausente |
| **S — Pequeña (Small)** | Completable dentro de un sprint, idealmente 1-3 días de trabajo por persona; toca como máximo una regla de negocio (RN) o un caso de uso (CU) principal | Si toca más de un CU o más de una RN de forma sustancial, se recomienda dividir (ver plantilla de división abajo) antes de estimarla como una sola historia |
| **T — Testable/Verificable** | Tiene criterios de aceptación verificables, en formato Given/When/Then o lista de condiciones concretas — nunca una frase única y ambigua | Un criterio de aceptación que no permita un veredicto binario (cumple/no cumple) falla este criterio |

## 2. Campos obligatorios de toda historia

Ninguna historia entra a Sprint Planning sin estos campos completos en
`docs/product/product-backlog.md`:

1. **Formato** `Como / quiero / para` (rol, capacidad, valor).
2. **Valor de negocio o técnico explícito** — el **para** debe nombrarlo, no delegarlo a "se
   entiende que sirve para...".
3. **Criterios de aceptación verificables** — Given/When/Then o lista de condiciones; nunca una
   sola frase genérica.
4. **Dependencias** — historias, documentos o servicios previos; "ninguna" si no aplica, nunca se
   omite el campo.
5. **Prioridad** — MoSCoW (Must/Should/Could/Won't have) u otro esquema explícito y consistente
   con el resto del backlog.
6. **Trazabilidad** — RF/RN/CU/ADR/DEC relevantes, cuando aplique. Una historia de implementación
   sin ningún RF/CU asociado es un hallazgo (huérfana), no un estado válido.
7. **Estimación** — story points, talla, o explícitamente "por estimar en Sprint Planning" si
   todavía no se ha estimado. Nunca se omite el campo ni se inventa un número sin que el equipo lo
   haya dado.
8. **Sprint asignado** — el sprint objetivo, o "Sprint 3+" si la implementación aún no tiene un
   sprint exacto fijado (consistente con el resto del backlog para épicas de alcance futuro).

## 3. Plantilla de división cuando falla "Pequeña"

Cuando una historia toca más de una regla de negocio o más de un caso de uso principal, se
documenta la recomendación de división (sin ejecutarla hasta la aprobación humana o el
refinamiento correspondiente):

```
### Historia original: HU-XXX
Motivo de división: toca CU-A (RN-1) y CU-B (RN-2) en un solo alcance.

Propuesta:
- HU-XXXa — [alcance de CU-A]
- HU-XXXb — [alcance de CU-B]

Estado: recomendación registrada, división pendiente de aprobación / de ejecutarse en el
refinamiento previo al sprint correspondiente (ver `docs/planning/refinement-process.md`).
```

## 4. Consistencia con arquitectura y dominio (verificación adicional)

Antes de declarar una historia "Lista", además de INVEST se verifica:

- **No contradice ninguna decisión de arquitectura ya cerrada**
  (`docs/architecture/architecture-consistency-report.md`, estado CERRADO) ni ninguna entrada de
  `docs/planning/decisions-log.md`. Si se detecta una contradicción, la historia se marca "No
  lista" y el hallazgo se reporta — **no se reabre la decisión de arquitectura desde el backlog**.
- El RF/CU que la origina existe y está vigente en `docs/domain/functional-requirements.md` /
  `use-cases.md`; ningún ID se inventa ni se reutiliza.
- Si la historia introduce una capacidad nueva no cubierta por ningún RF/CU existente, se
  registra primero el vacío en `docs/domain/traceability-matrix.md` antes de detallar la
  historia — no se detalla una historia sobre una capacidad no trazada.
