# Modelo de incidentes (operación)

Este es el documento operativo de gestión de incidentes ("quién hace qué"), distinto del modelo de
dominio. El ciclo de vida y las reglas de dominio del incidente ya están documentados en
`docs/product/business-rules.md` (RN-003 a RN-014, RN-019) y `docs/domain/commands-events.md`
(`IncidentCreated`, `IncidentAcknowledged`, `IncidentEscalated`, `IncidentClosed`).

## Quién hace qué (decisión confirmada de roles y flujo operativo de incidentes)

Perspectiva operativa de "quién atiende", derivada exclusivamente de
`docs/product/business-rules.md` (RN-006, RN-007, RN-019) y `docs/domain/use-cases.md`
(CU-003 a CU-006); no se agregan procedimientos, contención específica ni escalamiento automático
por SLA no confirmados.

| Paso operativo | Responsable | Rol |
|---|---|---|
| Creación del incidente a partir de una condición que cumple reglas de detección | Sistema | Automático (CU-003) |
| Notificación inicial | Sistema, según la política de notificación vigente (sin matriz de notificación confirmada) | Automático (CU-003, RF-008) |
| Reconocimiento y coordinación de la atención | Supervisor de operaciones | Principal (CU-004) |
| Acciones operativas iniciales o de contención dentro de su ámbito; aporte de contexto | Operador | Secundario (CU-004); contribuye sin ser actor formal en CU-006 |
| Solicitud o confirmación del escalamiento | Supervisor de operaciones | Principal (CU-005) |
| Registro de la transición de escalamiento, emisión del evento y notificación al Técnico | Sistema | Secundario, no autónomo (CU-005) |
| Diagnóstico, intervención técnica, causa y comentario de resolución | Técnico de mantenimiento | Principal (CU-006) — único rol humano que cierra (RN-019) |
| Información del cierre | Supervisor de operaciones | Secundario, informado (CU-006) |
| Consulta de la bitácora de auditoría de incidentes | Auditor | Solo consulta (CU-009), sin permisos de cambio |

El Administrador de plataforma no participa en la operación diaria de incidentes (CU-003 a
CU-006); su alcance es la gestión de activos, sensores, perfiles y accesos
(`docs/product/stakeholders.md`).

## TODO

Este documento cubre quién es responsable de cada paso, no procedimientos manuales de
contingencia detallados (checklists paso a paso, tiempos de espera operativos) ni acciones de
contención específicas — no están confirmados por el equipo y no se inventan aquí. Tampoco se
define aquí ninguna matriz de notificación (qué canal o mensaje recibe cada actor).
