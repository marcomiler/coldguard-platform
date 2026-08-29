# Requisitos no funcionales

- RNF-001: ejecución reproducible con Docker Compose.
- RNF-002: correlación y trazabilidad completa. Trazas distribuidas correlacionables mediante
  OpenTelemetry (`traceId`, `spanId`, `correlationId`), propagadas por el Gateway a través de
  todos los servicios — ver `docs/operations/observability-strategy.md`.
- RNF-003: RBAC, autenticación y auditoría.
- RNF-004: logs estructurados y métricas. Logs en formato JSON estructurado y correlacionables;
  métricas de aplicación y de dependencias (PostgreSQL, RabbitMQ) mediante OpenTelemetry y
  Micrometer — ver `docs/operations/observability-strategy.md`.
- RNF-005: pruebas unitarias, integración y contrato.
- RNF-006: contratos gRPC versionados.
- RNF-007: Terraform modular preparado para Azure (proveedor cloud objetivo confirmado para el
  despliegue planificado; aprovisionamiento y despliegue pendientes de ejecución). Azure Container
  Apps (cómputo) y Azure Database for PostgreSQL Flexible Server (persistencia) son las
  plataformas planificadas (DEC-002, `docs/planning/decisions-log.md`); ningún recurso está
  aprovisionado.
- RNF-008: prohibición de secretos y datos sensibles en telemetría (nunca registrar tokens JWT,
  credenciales, cadenas de conexión, secretos ni payloads completos de telemetría en logs,
  trazas o eventos de negocio), y disponibilidad observable mediante health checks por servicio
  — ver `docs/operations/observability-strategy.md`.
