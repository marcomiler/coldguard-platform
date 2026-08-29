# Stack tecnológico

Declarado en `CLAUDE.md` (sección "Technology") y precisado por las decisiones registradas en
`docs/planning/decisions-log.md`. Tabla por entorno (local previsto vs. Azure planificado); ningún
recurso cloud está aprovisionado.

| Capa | Local previsto | Azure planificado | Estado |
|---|---|---|---|
| Lenguaje/runtime backend | Java 25 | Java 25 (mismo runtime) | **Confirmada** (`CLAUDE.md`) |
| Framework backend | Spring Boot 4.1.1 (DEC-011, corregida) | Spring Boot 4.1.1 (mismo runtime) | **Confirmada** (DEC-011, corrección del 2026-08-29): versión verificada contra Maven Central, publicada el 20/08/2026; sustituye al registro previo erróneo ("4.x no publicado") |
| Cómputo | Docker Compose (`deploy/local/docker-compose.yml`, RNF-001) | Azure Container Apps (DEC-002) | Local: **Planificada**. Azure: **Planificada**; ningún recurso creado |
| Persistencia | PostgreSQL, una instancia con ownership lógico de esquema por servicio/módulo (`asset`, `telemetry`, `incident`, `identity`, `auditlog`), ADR-006, DEC-008 | Azure Database for PostgreSQL Flexible Server (DEC-002) | Local: **Confirmada**. Azure: **Planificada**; ningún recurso creado |
| Mensajería | RabbitMQ (ADR-004) | RabbitMQ, contenedor planificado en Azure Container Apps (DEC-009); sin Azure Service Bus, sin alternativa gestionada | Local: **Confirmada**. Azure: **Planificada**; ningún recurso creado |
| Comunicación síncrona interna | gRPC / Protocol Buffers, contratos versionados en `contracts/` | Sin cambio (gRPC interno se conserva) | **Confirmada** (ADR-003, RNF-006) |
| Borde | REST, único protocolo expuesto al frontend vía Gateway | Sin cambio | **Confirmada** (ADR-008) |
| Ingesta de telemetría | gRPC desde Sensor Simulator; REST interno protegido para CU-015 (DEC-003) | Sin cambio | **Confirmada** |
| Frontend | Repositorio separado `coldguard-frontend` | Sin cambio | **Confirmada** como decisión de separación de repositorios (ADR-002) |
| Identity & Access, Audit Log, consultas operativas | Módulos internos de Incident Service (DEC-004, DEC-005, DEC-006, DEC-008); no microservicios separados; sin arquitectura hexagonal formal | Sin cambio de ownership ni de granularidad | **Confirmada** |
| Autenticación/autorización | JWT + Spring Security, RBAC; implementación real en APF2 | Mecanismo sin cambio; posible delegación futura de emisión a un proveedor de identidad de Azure, sin selección de servicio | Mecanismo **Confirmada** (ADR-007); implementación **Planificada** para APF2 |
| Publicación confiable de eventos | Transactional Outbox | Sin cambio | **Confirmada** (ADR-009) |
| Gestión de secretos | `.env` no versionado + `.env.example` (DEC-010) | Azure Key Vault + Managed Identity (DEC-010) | **Planificada**; sin secretos, identidades ni permisos creados |
| Observabilidad — trazas | OpenTelemetry (SDK de trazas) | Application Insights / Azure Monitor | **Planificada**; no implementada (RNF-002, RNF-008) |
| Observabilidad — métricas | Prometheus + Micrometer, visualizado en Grafana | Application Insights / Azure Monitor | **Planificada**; no implementada (RNF-004, RNF-008) |
| Observabilidad — logs | Loki, visualizado en Grafana | Azure Monitor Logs / Log Analytics | **Planificada**; no implementada (RNF-004, RNF-008) |
| Observabilidad — paneles administrados | No aplica | Azure Managed Prometheus, Azure Managed Grafana | **Opción futura**, no confirmada como parte del MVP |
| Notificaciones — adaptador | Interfaz abstracta desacoplada de proveedor (RF-008) | Sin cambio (mismo puerto/adaptador) | **Confirmada** la interfaz desacoplada |
| Notificaciones — prueba/producción | Mailpit, solo pruebas locales | Azure Communication Services Email, planificado para Sprint 6 y despliegue final (DEC-007) | Local: **Planificada**. Azure: **Planificada**; sin recurso creado, sin correos reales enviados |
| Infraestructura como código | No aplica en local | Terraform modular preparado, sin aplicar (RNF-007) | **Planificada**; `terraform apply` no ejecutado |
| Simulación de cloud local | LocalStack, si aporta valor | No aplica | **Planificada**, sin evidencia de uso |
| Control de versiones / CI | GitHub / GitHub Actions | Sin cambio | **Confirmada** (`CLAUDE.md`) |
| Seguridad de repositorio | GitHub security (Dependabot, code scanning) | Sin cambio | **Condicionada** al tipo de repositorio y al plan/licencia de GitHub |
| Proveedor cloud | No aplica | **Azure**, proveedor objetivo del despliegue planificado y la presentación final | **Confirmada** como proveedor objetivo; aprovisionamiento y despliegue pendientes de ejecución (RNF-007) |

Ningún recurso cloud se crea sin aprobación humana explícita, y no se ejecuta `terraform apply`
sin aprobación explícita (`.claude/rules/infra.md`). Ninguna fila de esta tabla afirma una
implementación, despliegue, prueba de rendimiento o costo ya realizado. Confirmar Azure como
proveedor, o nombrar Azure Container Apps, PostgreSQL Flexible Server, Key Vault, Managed Identity
o Azure Communication Services Email como planificados, no implica que exista suscripción Azure,
identidades, permisos, secretos ni ningún recurso ya creado.

## TODO

Decisiones de stack específicas por servicio (librerías internas, versiones fijadas de
dependencias) — parcialmente resuelto por el scaffolding inicial (DEC-011: versión de Spring Boot,
groupId/artifactId, convención de paquetes). Pendiente: esquema lógico de `notification-service`
(sin confirmar en `docs/domain/bounded-contexts.md`) y el cableado de
`micrometer-registry-prometheus` para exponer `/actuator/prometheus`.
