# SLA y KPI

## SLA inicial

Valores objetivo académicos iniciales del MVP, sujetos a validación posterior del negocio.
La prioridad (P1–P4) se determina mediante la matriz impacto/urgencia definida en RN-012 (`docs/business/business-rules.md`); no existe un mapeo directo de severidad a prioridad.

| Prioridad | Reconocimiento (hasta `IncidentAcknowledged`) | Resolución objetivo (hasta `IncidentClosed`) |
|---|---|---|
| P1 | <= 5 minutos | <= 30 minutos |
| P2 | <= 15 minutos | <= 2 horas |
| P3 | <= 1 hora | <= 8 horas |
| P4 | revisión <= 1 día hábil | — |

## KPI

- MTTA (tiempo hasta `IncidentAcknowledged`)
- MTTR (tiempo hasta `IncidentClosed`)
- Cumplimiento de SLA por prioridad
- Lecturas procesadas por minuto
- Disponibilidad del servicio

> Nota: los valores numéricos objetivo para MTTA/MTTR agregados, throughput y disponibilidad aún no están definidos por el negocio; los umbrales de esta tabla son solo los SLA por incidente individual acordados como punto de partida académico.
