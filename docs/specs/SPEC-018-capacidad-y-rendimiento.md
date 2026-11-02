# SPEC-018 — Capacidad y monitoreo de rendimiento (APF3 · Cap. 10.1 y 10.2)

## Objetivo

Completar el plan de capacidad (dimensionamiento local y Azure, crecimiento) y producir pruebas de
carga reproducibles con resultados, observados en Grafana, que sustenten conclusiones sobre
capacidad y rendimiento.

## Trazabilidad

APF3 Cap. 10.1 y 10.2; RNF-004, RNF-002; `docs/quality/sla-kpi.md`, DEC-018 (retención de
lecturas), DEC-022; `docs/operations/capacity-plan.md`, `observability-strategy.md`; SPEC-011
(dashboards), SPEC-016 (SKUs).

## Estado actual verificado

- `capacity-plan.md` (53 líneas): memoria/CPU puntual del 2026-10-07, parámetros y crecimiento de
  datos. Declara que **no hay resultados de carga ni objetivos confirmados**.
- Dashboards de Grafana cubren pool Hikari, Outbox, colas/DLQ, latencia y errores del borde.
- No hay herramienta de carga adoptada en el repositorio (verificar con `grep` al empezar; una
  búsqueda previa no se completó).
- El simulador genera 1 tick/s, lotes de 200 y buffer de 5000 lecturas.

## Decisión requerida — D-25 (herramienta y objetivos)

Recomendación: **k6** (contenedor `grafana/k6` fijado por versión, sin instalar nada en el host),
con salida a Prometheus (remote write) o a JSON/CSV archivado. Alternativa: usar el propio
simulador con más sensores para la ruta de ingesta. Los umbrales de aceptación son **placeholder
académico** derivados de los parámetros ya configurados; no se presentan como SLA. Registro: DEC.

## 10.1 Capacidad (`docs/operations/capacity-plan.md`, ampliado)

1. **Línea base local**: repetir la medición con fecha nueva y escenarios (reposo, 5, 50 y N
   sensores) con `docker stats`.
2. **Dimensionamiento Azure**: tabla por componente (réplicas mín/máx, vCPU, memoria, SKU de
   PostgreSQL, almacenamiento) coherente con SPEC-016; costos solo como estimación con la
   calculadora oficial, rotulados "estimación, no medición".
3. **Modelo de crecimiento**: lecturas/día por sensor × sensores × retención (DEC-018), tamaño de
   fila medido, proyección a 30/90/365 días, con la fórmula visible.
4. **Cuellos de botella y límites**: pool Hikari, prefetch/concurrency AMQP, Outbox, lote de
   ingesta (500), memoria JVM; cuál se satura primero según las pruebas.
5. **Reglas de escalado propuestas** (ACA: por HTTP concurrente / longitud de cola), como diseño.

## 10.2 Monitoreo de rendimiento

Escenarios en `tests/performance/` (nuevo directorio; sin credenciales, datos ficticios):

| Escenario | Qué mide | Métricas objetivo |
|---|---|---|
| `ingest-ramp` | Ingesta gRPC/lote de telemetría con rampa de sensores | lecturas/s sostenidas, latencia p95, errores, tiempo de evaluación |
| `incident-flow` | Creación→reconocer→cerrar vía Gateway | latencia p95 por endpoint, error rate |
| `read-heavy` | Listados/consultas con paginación | p95, uso de Hikari |
| `soak` (corto) | 15–30 min de carga constante | memoria JVM, crecimiento de Outbox, fugas |
| `spike` | Ráfaga de lecturas fuera de rango | latencia extremo a extremo hasta notificación, DLQ vacía |

Procedimiento:

1. Autenticación vía Gateway con usuarios demo (solo local) o variables en Azure.
2. Ejecutar con el perfil `observability` activo; registrar ventana de tiempo para ubicarla en
   Grafana.
3. Para cada corrida guardar: configuración, resumen de k6, capturas de los tres dashboards, y
   `docker stats`/métricas de Container Apps.
4. Un panel nuevo "Rendimiento" en Grafana (provisionado como código) con latencia p50/p95/p99,
   throughput, errores y saturación (los cinco condiciones candidatas de
   `observability-strategy.md`); **sin reglas de alerta** hasta que se aprueben umbrales (Sprint 7).
5. Si hay Azure (SPEC-017): una corrida corta y acotada contra Azure, con aprobación de costo.

## Entregable analítico

Sección "Resultados y conclusiones" en `capacity-plan.md` y un `performance-report.md` en
`docs/operations/`: tabla de resultados por escenario, comparación contra umbrales placeholder,
hallazgos (cuellos de botella), acciones y límites del estudio (hardware, local vs. Azure).

## Criterios de aceptación

1. Los 5 escenarios se ejecutan de forma reproducible con un único comando documentado.
2. Hay al menos una corrida completa archivada por escenario (resumen + capturas).
3. Ningún número de objetivo se presenta como SLA; todo umbral está marcado placeholder.
4. `capacity-plan.md` contiene línea base, dimensionamiento Azure, fórmula de crecimiento y
   cuellos de botella con evidencia.
5. El panel de rendimiento está provisionado y muestra datos durante una corrida.
6. `consistency-check` sin hallazgos nuevos; no se commitean resultados con secretos ni logs
   (`.claude/rules/git-workflow.md`).

## Tareas

1. Registrar D-25 (DEC); fijar imagen de k6.
2. Escribir escenarios y script `deploy/scripts/run-perf.sh`.
3. Panel Grafana de rendimiento.
4. Ejecutar corridas, capturar evidencia, analizar.
5. Ampliar `capacity-plan.md` y crear `performance-report.md`.

## Riesgos

- Hardware del equipo limita la carga: se declara en las limitaciones.
- Medir en WSL/Docker Desktop no es representativo de Azure: no se extrapola sin decirlo.
- Carga contra Azure puede generar costos: requiere aprobación y duración acotada.

## Fuera de alcance

Alertas con umbral y SLO formales (Sprint 7, Cap. XII); pruebas de caos y HA.
