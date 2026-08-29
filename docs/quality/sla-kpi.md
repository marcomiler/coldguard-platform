# SLA y KPI

## SLA inicial

Valores objetivo académicos iniciales del MVP, sujetos a validación posterior del negocio.
La prioridad (P1–P4) se determina mediante la matriz impacto/urgencia definida en RN-012 (`docs/product/business-rules.md`); no existe un mapeo directo de severidad a prioridad.

| Prioridad | Reconocimiento (hasta `IncidentAcknowledged`) | Resolución objetivo (hasta `IncidentClosed`) |
|---|---|---|
| P1 | <= 5 minutos | <= 30 minutos |
| P2 | <= 15 minutos | <= 2 horas |
| P3 | <= 1 hora | <= 8 horas |
| P4 | revisión <= 1 día hábil | — |

Nota sobre P4: la tabla solo define un tiempo de revisión (`<= 1 día hábil`), no un tiempo de
resolución — no es un dato omitido, es la definición tal como está acordada; no se completa aquí
con un valor no confirmado.

## KPI

| KPI | Cómo se calcula | Evento(s) que lo alimentan |
|---|---|---|
| MTTA | Tiempo desde `IncidentCreated` hasta `IncidentAcknowledged` | `IncidentCreated`, `IncidentAcknowledged` |
| MTTR | Tiempo desde `IncidentCreated` hasta `IncidentClosed` | `IncidentCreated`, `IncidentClosed` |
| Cumplimiento de SLA por prioridad | Compara MTTA/MTTR real contra la tabla de SLA de arriba, agrupado por la prioridad (RN-012) vigente al momento de cada evento | `IncidentCreated`, `IncidentAcknowledged`, `IncidentClosed` |
| Lecturas procesadas por minuto | Conteo de telemetría recibida en la ventana de tiempo | `TelemetryReceived` |
| Disponibilidad del servicio | **TODO**: no depende de un evento de dominio; es una métrica de infraestructura (uptime), observable mediante health checks (RNF-008) — sin definición operativa ni umbral todavía; ver `docs/operations/observability-strategy.md` | — |

Catálogo completo de eventos: `docs/domain/commands-events.md`.

> Nota: los valores numéricos objetivo para MTTA/MTTR agregados, throughput y disponibilidad aún no están definidos por el negocio; los umbrales de esta tabla son solo los SLA por incidente individual acordados como punto de partida académico.
