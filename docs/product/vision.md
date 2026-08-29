# Visión del producto

> Fuente original: `docs/academic/01-propuesta-proyecto.md` (secciones "Nombre", "Solución" y
> "Diferenciador").
>
> **Nota de lenguaje**: el documento académico fuente describe el objetivo como "prevenir
> incidentes y pérdidas". Este documento técnico usa un lenguaje más conservador ("reducir el
> riesgo operativo", "permitir una respuesta más oportuna") porque el sistema detecta, prioriza y
> notifica anomalías — no elimina físicamente la posibilidad de falla del equipo de frío. No es
> una contradicción del documento fuente, es una precisión de alcance para uso técnico.

## Propósito

**ColdGuard** es una plataforma de monitoreo de cadena de frío. Busca reducir el riesgo
operativo asociado a excursiones de temperatura y otras anomalías en unidades de frío,
permitiendo una respuesta más oportuna que la que depende únicamente de revisión manual.

## Problema que atiende

Detalle completo en `docs/product/problem-statement.md`. En síntesis: productos sensibles pueden
verse afectados por fallas térmicas, fallas de energía, apertura de puertas o respuesta tardía
ante una condición anómala ya detectada; este último punto (respuesta tardía) es el que ColdGuard
ataca de forma más directa.

## Usuarios objetivo

Actores definidos, con su trazabilidad completa a casos de uso, en
`docs/product/stakeholders.md`: Supervisor de operaciones, Operador, Técnico de mantenimiento,
Auditor, Administrador de plataforma y Simulador de sensores (sensor-simulator).

## Propuesta de valor

La plataforma recibe telemetría, evalúa perfiles operativos, detecta anomalías, crea incidentes
priorizados, aplica SLA, notifica responsables y conserva evidencia auditable.

## Capacidades principales

Enumeradas en términos funcionales, no técnicos; cada una está respaldada por un requisito
funcional (RF) ya documentado en `docs/domain/functional-requirements.md`:

- Registrar organizaciones, sedes, unidades de frío y sensores asociados (RF-001, RF-002).
- Recibir telemetría y evaluarla contra el perfil operativo configurado (RF-003, RF-004).
- Crear incidentes automáticamente y gestionar su ciclo de vida (RF-005, RF-006).
- Escalar por incumplimiento de SLA (RF-007) y notificar a los responsables (RF-008).
- Consultar métricas operativas (RF-009).
- Configurar perfiles operativos y umbrales por sensor (RF-010).
- Registrar la criticidad de cada activo (RF-011).
- Consultar y actualizar activos, sensores y perfiles operativos (RF-012).
- Gestionar asignaciones de acceso por rol (RF-013), con ownership del módulo interno Identity &
  Access, dentro de Incident Service (DEC-004, DEC-008).
- Inyectar telemetría de prueba mediante un endpoint interno protegido, para pruebas y demostraciones (RF-014).
- Dejar un registro auditable de toda transición relevante (RN-008) y consultarlo de forma
  restringida (RF-018), mediante el módulo interno Audit Log, dentro de Incident Service (DEC-008).

**Nota de fase**: la autenticación real de usuarios (RF-015) se implementa en APF2 con Spring
Security, RBAC en endpoints y hash de contraseñas. En APF1 solo existe un login de demostración
en el frontend, que no debe presentarse como un control de seguridad productivo (RN-016,
`docs/product/business-rules.md`).

## Diferenciador de producto

Lo que distingue a ColdGuard a nivel de producto es la combinación de **detección automática**,
**priorización objetiva** (matriz impacto/urgencia, RN-012, en vez de un criterio manual) y
**trazabilidad auditable** de cada incidente, de principio a fin.

### Habilitadores técnicos

Microservicios, gRPC, eventos y una arquitectura cloud-ready **no son el beneficio para el
usuario final**: son las decisiones de arquitectura que hacen posible el diferenciador de
producto de arriba. Cada una ya está tomada, no es aspiracional:

| Habilitador técnico | Decisión que lo respalda |
|---|---|
| Microservicios (Asset, Telemetry, Incident, Notification) | ADR-001 (monorepo backend con servicios separados) |
| gRPC interno | ADR-003 (gRPC para comunicación síncrona interna; REST solo en el borde) |
| Eventos | ADR-004 (RabbitMQ), ADR-005 (Incident → Notification asíncrono), ADR-009 (Transactional Outbox) |
| Observabilidad | RNF-002, RNF-004 (`docs/quality/non-functional-requirements.md`); stack en `CLAUDE.md` |
| Auditoría | RN-008 (`docs/product/business-rules.md`) — toda transición relevante es auditable |
| Cloud-ready sin cloud en el MVP | RNF-007 — Terraform modular preparado para Azure, sin aplicar (`.claude/rules/infra.md`) |

## Éxito esperado del MVP

El MVP se considera exitoso si permite evaluar una respuesta más oportuna a las anomalías que el
proceso manual (detección → creación de incidente → priorización → notificación → cierre
auditable), no si demuestra una reducción de pérdidas reales — eso requeriría datos de operación
real que este MVP académico no produce.

Los KPI candidatos (MTTA, MTTR, cumplimiento de SLA por prioridad, lecturas procesadas por
minuto, disponibilidad) están en `docs/quality/sla-kpi.md`. **TODO**: los valores numéricos
objetivo agregados (no los SLA por incidente individual, que sí están definidos) siguen
pendientes de validación de negocio; no se afirman aquí como logrados ni como metas confirmadas.

## Referencias técnicas y trazabilidad

- Proceso de extremo a extremo: `docs/domain/domain-model.md` (sección "Proceso objetivo").
- Actores y trazabilidad actor → caso de uso: `docs/product/stakeholders.md`.
- Reglas de negocio: `docs/product/business-rules.md`.
- Alcance detallado del MVP: `docs/product/scope-mvp.md`.
- Requisitos funcionales y no funcionales: `docs/domain/functional-requirements.md`,
  `docs/quality/non-functional-requirements.md`.
- ADR relevantes: ADR-001, ADR-003, ADR-004, ADR-005, ADR-007, ADR-008, ADR-009
  (`docs/architecture/adr/`).
