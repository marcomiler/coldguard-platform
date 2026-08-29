# Declaración del problema

> Fuente original: `docs/academic/01-propuesta-proyecto.md` (sección "Problema").
>
> **Supuesto no validado**: la afirmación de que "productos sensibles pueden perderse" por estos
> modos de falla proviene de la propuesta académica original y no está respaldada aquí por una
> fuente externa (estudio, incidente registrado, dato de negocio). Se trata como un supuesto de
> partida del proyecto, no como un hecho medido. No se agrega una fuente externa que no exista.

## Problema central

Productos sensibles pueden perderse por fallas térmicas, energía, apertura de puertas o respuesta
tardía.

## Causas o modos de falla

Tal como se enuncian en la propuesta original, sin ampliarlos con causas no documentadas:

- Fallas térmicas (equipo de frío fuera de rango operativo).
- Fallas de energía.
- Apertura de puertas (pérdida de cadena de frío por exposición).
- Respuesta tardía ante una condición anómala ya detectada.

Los tres primeros son modos de falla física del activo; el cuarto es una limitación de proceso
(tiempo de reacción humana), no una falla del equipo. Es el que ColdGuard ataca de forma más
directa mediante el reloj de SLA (RN-006, `docs/product/business-rules.md`) y la priorización
automática (RN-012).

## Consecuencias potenciales

Estas son clasificaciones de impacto **potencial**, ya formalizadas en RN-010
(`docs/product/business-rules.md`), no resultados medidos de un incidente real:

- Pérdida de producto.
- Incumplimiento normativo.
- Riesgo sobre el activo.

La velocidad de respuesta requerida frente a esas consecuencias es lo que RN-011 formaliza como
"urgencia". Impacto y urgencia combinados determinan la prioridad del incidente (RN-012).

## Limitación del proceso actual

**Supuesto, no un dato confirmado**: no existe en el repositorio una descripción documentada del
proceso operativo previo a ColdGuard (por ejemplo, con qué frecuencia se revisa manualmente un
sensor, o qué tan rápido reacciona hoy un operador). Se asume que la detección y la escalación
dependen hoy de revisión manual no asistida por umbrales automáticos ni por priorización
objetiva, porque eso es lo que el proceso objetivo de ColdGuard (`docs/domain/domain-model.md`)
introduce como novedad — pero esta comparación "antes/después" no está respaldada por una
descripción explícita del proceso anterior. **TODO**: documentar el proceso actual si el equipo
decide validarlo, en vez de asumirlo.

## Respuesta propuesta

El proceso de negocio que ColdGuard ejecuta frente a este problema está documentado en
`docs/domain/domain-model.md` (sección "Proceso objetivo"). Los puntos de contacto directos son:

- `CU-002` (recibir y evaluar telemetría) — detecta la anomalía sin depender de que un operador la note.
- `CU-003` (crear incidente automático) — evita que la detección dependa de una acción manual de registro.
- `CU-004`/`CU-005` (reconocer-coordinar/escalar incidente, Supervisor de operaciones) — acota la "respuesta tardía" con un reloj de SLA y escalación.
