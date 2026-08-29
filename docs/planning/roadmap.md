# Roadmap de sprints

Detalle completo de Sprint 1 y Sprint 2 en `docs/planning/sprint-1.md` y
`docs/planning/sprint-2.md`. Plan de versiones asociado en `docs/planning/release-plan.md`.

## Sprint 0 — Incepción técnica

- **Objetivo**: preparar el entorno y las reglas del proyecto antes de iniciar la planificación formal.
- **Capacidades/entregables esperados**: `CLAUDE.md` (alcance, stack, arquitectura, flujo de
  trabajo, puertas de calidad) y la configuración de reglas del proyecto (`.claude/rules/`).
- **Evidencia esperada**: commit `4628e40` (`docs: initialize ColdGuard project foundation`).
- **Relación con APF/proyecto final**: previo a APF1; fase fundacional, no es un entregable APF
  por sí mismo.

Detalle: `docs/planning/sprint-0.md`.

## Sprint 1 — Definición del producto, análisis, arquitectura y planificación

- **Objetivo**: fijar los fundamentos del proyecto como base verificable para la implementación y
  los prototipos de frontend.
- **Capacidades/entregables esperados**: propuesta de proyecto, análisis de negocio, requisitos
  funcionales y no funcionales, casos de uso, reglas de negocio, arquitectura C4 y ADRs, registro
  de riesgos, SLA/KPI, matriz de trazabilidad y backlog de producto.
- **Evidencia esperada**: `docs/product/product-backlog.md` (HU-001 a HU-012),
  `docs/academic/apf1-mapping.md`.
- **Relación con APF/proyecto final**: APF1.

Detalle: `docs/planning/sprint-1.md`.

## Sprint 2 — Prototipos frontend y cierre de decisiones funcionales

- **Objetivo**: producir prototipos navegables de las pantallas clave del MVP y cerrar las
  decisiones funcionales pendientes antes de iniciar la implementación de backend.
- **Capacidades/entregables esperados**: prototipos de alta de activos/sensores/perfiles, ciclo
  de vida del sensor, tablero y detalle de incidentes, reconocimiento/escalamiento/cierre técnico,
  métricas operativas, bitácora de auditoría, y login de demostración por roles (HU-013 a HU-018).
- **Evidencia esperada**: prototipos en `coldguard-frontend`, `docs/academic/apf1-mapping.md`
  actualizado.
- **Relación con APF/proyecto final**: APF1.

Detalle: `docs/planning/sprint-2.md`.

## Sprint 3 — Asset Service, PostgreSQL y primer vertical slice

- **Objetivo**: primer backend funcional real, con persistencia.
- **Capacidades/entregables esperados**: Asset Service (RF-001, RF-002, RF-010, RF-011, RF-012,
  RF-016), persistencia en PostgreSQL con ownership lógico por servicio (ADR-006), y el ciclo de
  vida operativo del sensor (CU-017 a CU-021) con sus eventos (`SensorStatusChanged`,
  `SensorReassigned`, `SensorCalibrationRecorded`, `SensorCalibrationExpired`, `SensorRetired`).
- **Evidencia esperada**: código y pruebas del vertical slice, migraciones de esquema.
- **Relación con APF/proyecto final**: APF2 (v0.2).

## Sprint 4 — Seguridad y gRPC

- **Objetivo**: implementar la seguridad real de backend y los contratos internos.
- **Capacidades/entregables esperados**: autenticación y RBAC en endpoints con Spring Security
  (RF-015, CU-016, ADR-007, ADR-008), contratos gRPC versionados (ADR-003); mapeo explícito
  rol→endpoint (pendiente real, ver `docs/quality/risk-register.md` R-014). La implementación
  completa del módulo interno Identity & Access (RF-013, CU-014) queda en Sprint 5, junto con
  Incident Service (DEC-004, DEC-008), ya que no se adopta arquitectura hexagonal formal ni un
  microservicio separado para ese módulo.
- **Evidencia esperada**: pruebas de autenticación/RBAC, contratos en `contracts/`.
- **Relación con APF/proyecto final**: APF2 (v0.2).

## Sprint 5 — Telemetría, RabbitMQ e Incident Service

- **Objetivo**: implementar la ingesta de telemetría y la gestión de incidentes con mensajería
  asíncrona.
- **Capacidades/entregables esperados**: Telemetry Service (RF-003, RF-004, RF-014, RF-017;
  CU-002, CU-015, CU-022 — incluye detección de pérdida de conectividad, sin crear incidente
  automáticamente) con ingesta gRPC desde el Sensor Simulator y REST interno protegido para
  CU-015 (DEC-003), RabbitMQ como broker de eventos (ADR-004), Incident Service (RF-005, RF-006,
  RF-007, RF-008, RF-009; CU-003 a CU-006, CU-008 — incluye los tres módulos internos, no
  microservicios separados: consultas operativas de métricas (DEC-006), Identity & Access
  (RF-013, CU-014, DEC-004) y Audit Log (RF-018, CU-009, DEC-005), cada uno con su propio esquema
  lógico compartiendo la instancia PostgreSQL, DEC-008) con los roles confirmados (Supervisor de
  operaciones reconoce y escala, Técnico de mantenimiento cierra técnicamente); instrumentación
  base con OpenTelemetry (trazas y métricas correlacionables, RNF-002/RNF-008) en los servicios
  que se construyen en este sprint, exportando al stack local (Prometheus, Grafana, Loki) —
  `docs/operations/observability-strategy.md`.
- **Evidencia esperada**: pruebas de integración, eventos de dominio publicados y consumidos;
  trazas/métricas visibles en el stack local de observabilidad.
- **Relación con APF/proyecto final**: proyecto final (v1.0) — cubre eventos e incidentes.

## Sprint 6 — Notificaciones, CI y capacidad

- **Objetivo**: completar las notificaciones y fortalecer la integración continua y la capacidad
  del sistema.
- **Capacidades/entregables esperados**: notificaciones a los actores correspondientes (RF-008
  restante), incluyendo la integración planificada del adaptador de notificaciones con Azure
  Communication Services Email como proveedor productivo (DEC-007; Mailpit permanece como opción
  de prueba local), pipeline de CI, pruebas de capacidad/rendimiento.
- **Evidencia esperada**: pipeline de CI ejecutándose, resultados de pruebas de capacidad. No se
  afirma que el recurso Azure Communication Services esté creado ni que se hayan enviado correos
  reales hasta que exista evidencia verificable.
- **Relación con APF/proyecto final**: APF3 (v0.3).

## Sprint 7 — Operación final, observabilidad, continuidad y backups

- **Objetivo**: cerrar el alcance operativo del MVP, incluyendo el diseño planificado de
  despliegue en Azure para la presentación final.
- **Capacidades/entregables esperados**: validación del stack local de observabilidad
  (RNF-002, RNF-004, RNF-008), configuración planificada del exportador de OpenTelemetry hacia
  Application Insights/Azure Monitor y Azure Monitor Logs/Log Analytics (sin afirmar suscripción
  Azure operativa), alertas iniciales candidatas (`docs/operations/observability-strategy.md`),
  diseño planificado de RabbitMQ como contenedor en Azure Container Apps (DEC-009) y de la
  estrategia de secretos con Azure Key Vault + Managed Identity (DEC-010) — ninguno desplegado,
  plan de continuidad y backups (`docs/operations/backup-recovery-plan.md`), evidencia operativa
  (runbooks, capturas, escenarios).
- **Evidencia esperada**: dashboards de observabilidad local, configuración planificada de
  exportación a Azure documentada, runbooks completos, evidencia de backup y recuperación.
- **Relación con APF/proyecto final**: proyecto final (v1.0).
