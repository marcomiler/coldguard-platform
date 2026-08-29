# Alcance del MVP

Consolida el alcance declarado en `docs/academic/01-propuesta-proyecto.md`, `CLAUDE.md` y
`docs/academic/apf1-mapping.md`.

## Objetivo del MVP

Demostrar el recorrido completo de negocio — recibir telemetría, evaluarla, crear y priorizar
incidentes, notificar y dejar evidencia auditable (`docs/domain/domain-model.md`, "Proceso
objetivo") — como un backend funcional que corre íntegramente en local, sin depender de ningún
servicio cloud (RNF-001).

## Capacidades funcionales incluidas

Cada una respaldada por un RF ya documentado en `docs/domain/functional-requirements.md`, con su
caso de uso (`docs/domain/use-cases.md`):

| Capacidad | RF | CU |
|---|---|---|
| Registrar organización, sede y unidad de frío | RF-001 | CU-001 |
| Registrar sensores asociados | RF-002 | CU-007 |
| Recibir telemetría simulada | RF-003 | CU-002 |
| Evaluar lecturas contra el perfil operativo | RF-004 | CU-002 |
| Crear incidentes automáticos | RF-005 | CU-003 |
| Asignar y gestionar estados de incidentes | RF-006 | CU-004, CU-005, CU-006 |
| Escalar por incumplimiento de SLA | RF-007 | CU-005 |
| Enviar notificaciones | RF-008 | CU-003, CU-005 |
| Consultar métricas operativas | RF-009 | CU-008 |
| Gestionar perfil operativo y umbrales | RF-010 | CU-011 |
| Registrar criticidad del activo | RF-011 | CU-012 |
| Consultar bitácora de auditoría (RN-008), solo lectura restringida | RF-018 | CU-009 |
| Consultar y actualizar activos, sensores y perfiles operativos | RF-012 | CU-013 |
| Gestionar asignaciones de acceso (rol de usuario) | RF-013 | CU-014 |
| Inyectar telemetría de prueba mediante endpoint interno protegido | RF-014 | CU-015 |
| Autenticar usuarios y autorizar accesos por rol (RBAC), backend real — **solo APF2** | RF-015 | CU-016 |
| Gestionar el ciclo de vida operativo del sensor (estado, calibración/verificación, reasignación, retiro lógico) | RF-016 | CU-017, CU-018, CU-019, CU-020 |
| Detectar y registrar pérdida de conectividad de un sensor (sin crear incidente automáticamente) | RF-017 | CU-022 |
| Consultar historial técnico y administrativo del sensor | RF-012 | CU-021 |

## Capacidades explícitamente fuera de alcance

- **Frontend y prototipos de UI**: viven en el repositorio `coldguard-frontend` (ADR-002); este
  repositorio no implementa interfaz de usuario. Los prototipos esperados (sin lógica de negocio
  real) están descritos como historias en `docs/product/product-backlog.md` (HU-013 a HU-016 y
  HU-018; HU-017 es una historia de gobernanza documental, no un prototipo), no implementados aquí.
- **CU-010** (módulo de mantenimiento preventivo): no se define en este MVP
  (`docs/domain/use-cases.md`). El Técnico de mantenimiento participa en CU-005 (notificado) y
  como actor principal de CU-006 (cierre técnico); no es actor de CU-004.
- **Autenticación y autorización real de backend (RF-015/CU-016) en APF1**: en APF1 el login es
  solo una demostración de frontend (RN-016); la implementación real con Spring Security queda
  fuera de alcance hasta APF2. Ver "Fases de entrega: APF1 vs. APF2" abajo.
- **Capacidades IoT avanzadas de hardware real**, explícitamente fuera de alcance del ciclo de
  vida operativo del sensor (RF-016) por decisión confirmada:
  - Actualización remota de firmware.
  - Aprovisionamiento automático en IoT Hub.
  - Gestión de certificados por dispositivo.
  - Telemetría de batería física.
  - Geolocalización del dispositivo.
  - Comandos bidireccionales a hardware real.

  El ciclo de vida operativo del sensor en este MVP es un modelo de dominio (estado, calibración
  registrada, reasignación, retiro lógico) y su gestión administrativa — no una integración con
  hardware físico real.

## Fases de entrega: APF1 vs. APF2 (autenticación y autorización)

Decisión confirmada, no una alternativa a evaluar:

| Fase | Qué incluye | Qué NO incluye |
|---|---|---|
| **APF1** | Frontend con login funcional de **demostración**, navegación protegida por rol, estados de sesión, acceso denegado y cierre de sesión (`coldguard-frontend`, ADR-002). | Ningún control de seguridad productivo. No hay backend real de autenticación. No se presenta el login de APF1 como control de seguridad productivo (RN-016). |
| **APF2** | Seguridad real en el backend: Spring Security, autenticación, tokens, RBAC en los endpoints (RF-015, CU-016), hash de contraseñas, secretos mediante variables de entorno. Coherente con ADR-007 (JWT + RBAC) y ADR-008 (el Gateway valida el JWT en el borde). | — |

Esta fase no crea una alternativa arquitectónica nueva: implementa, en el backend, el mecanismo
que ADR-007 ya había decidido para el MVP; lo que esta decisión confirma es el momento (APF2, no
APF1) en que esa implementación real ocurre.

## Supuestos

- Se asume que el simulador de telemetría (actor de CU-002) es representativo del comportamiento
  de sensores físicos reales; esta equivalencia no está validada contra hardware real, y no existe
  un documento que la confirme. Es un supuesto de diseño del MVP, no una decisión validada.
- La asignación de RF a un microservicio concreto (Asset, Telemetry, Incident, Notification) sigue
  la agrupación ya usada en las épicas del backlog (`docs/product/product-backlog.md`, EPIC-01 a
  EPIC-04), pero **no existe un documento de arquitectura que formalice esa asignación** como
  decisión cerrada. Se trata como supuesto de trabajo; el detalle está en
  "Dependencias técnicas y decisiones relacionadas" más abajo.

## Restricciones

- El MVP debe ejecutarse completo con Docker Compose, sin depender de ningún servicio cloud
  (RNF-001, `deploy/local/docker-compose.yml`).
- No se crean recursos Azure sin aprobación humana explícita, y no se ejecuta `terraform apply`
  sin aprobación explícita (`.claude/rules/infra.md`).
- REST solo en el borde del sistema (Gateway); la comunicación síncrona interna es gRPC (ADR-003);
  no se expone REST directamente desde los servicios de dominio.
- El frontend vive en un repositorio separado (ADR-002); este repositorio no lo incluye ni lo
  sustituye con una UI propia.

## Decisiones pendientes

No resueltas en este documento; requieren una decisión explícita del equipo antes de avanzar:

- **CU-010**: no se define en este MVP (no existe módulo de mantenimiento preventivo) — ver
  `docs/domain/use-cases.md`.
- Valores numéricos agregados de SLA/KPI (MTTA/MTTR globales, throughput, disponibilidad) no
  están confirmados por negocio — ver `docs/quality/sla-kpi.md`.
- **Evento de dominio** para actualización de activos/sensores/perfiles (CU-013) y para
  asignación de accesos (CU-014): no catalogado; ver `docs/domain/commands-events.md`.

### Resuelto por decisión

- RF-009/CU-008 (métricas operativas): atendido por un módulo de consultas operativas dentro de
  Incident Service (DEC-006, `docs/planning/decisions-log.md`).
- RF-018/CU-009 (auditoría, RN-008): atendido por el módulo interno Audit Log, dentro de Incident
  Service (DEC-005, DEC-008).
- RF-013/CU-014 (asignaciones de acceso): atendido por el módulo interno Identity & Access, dentro
  de Incident Service (DEC-004, DEC-008); explícitamente no asignado a Asset Service.
- **Frecuencia esperada configurable** que determina la pérdida de conectividad (RN-020): no
  tiene un valor numérico definido.
- **Criterio de vencimiento de calibración/verificación** (RN-018): debe ser configurable; no se
  fija ningún valor numérico ni periodicidad global.
- **Qué constituye una "acción equivalente documentada"** cuando la tarea programada de
  vencimiento de calibración no ejecuta directamente la transición a EN_MANTENIMIENTO (RN-018):
  no definido.

- Vacíos documentales, consolidados por referencia desde `docs/academic/apf1-mapping.md`:
  - RF-009 sigue sin evento ni criterio de prueba propio (`docs/domain/traceability-matrix.md`);
    su servicio dueño sí quedó resuelto (ver "Resuelto por decisión" arriba).
  - **Resuelto por decisión**: CU-009 (bitácora de auditoría) ya tiene RF asociado (RF-018,
    DEC-005). El evento `AssetRegistered` ya está asociado explícitamente a CU-001, aunque sigue
    sin consumidor confirmado. `IncidentResolved` se eliminó del catálogo (Decisión E):
    `IncidentClosed` es el único evento de cierre técnico en el MVP.

No se agrega funcionalidad fuera de este alcance sin una decisión explícita registrada (edición
de este documento con motivo, o un ADR nuevo si es una decisión arquitectónica).

## Dependencias técnicas y decisiones relacionadas

Listas puramente técnicas — no son alcance funcional, son lo que hace posible el alcance
funcional de arriba:

- Microservicios (Asset, Telemetry, Incident, Notification), gRPC interno, eventos asíncronos,
  Gateway REST en el borde, RabbitMQ, PostgreSQL, Docker Compose — ver `CLAUDE.md`.
- Observabilidad (OpenTelemetry, Micrometer, Prometheus, Grafana, Loki) y auditoría de
  transiciones relevantes (RN-008).
- Terraform modular preparado para Azure, pero **sin aplicar** durante el MVP (RNF-007,
  `.claude/rules/infra.md`).
- Mapeo servicio → RF/CU, nombrado en `docs/architecture/container-diagram.md` y ADR-001
  (monorepo backend). Marcado como supuesto de trabajo (ver "Supuestos" arriba), no como
  asignación formalizada:

  | Servicio | RF que atiende | CU que soporta |
  |---|---|---|
  | Asset Service | RF-001, RF-002, RF-010, RF-011, RF-012, RF-016 | CU-001, CU-007, CU-011, CU-012, CU-013, CU-017, CU-018, CU-019, CU-020, CU-021 |
  | Telemetry Service | RF-003, RF-004, RF-014, RF-017 | CU-002, CU-015, CU-022 |
  | Incident Service | RF-005, RF-006, RF-007, RF-009, RF-013, RF-018 — incluye los módulos internos de consultas operativas (DEC-006), Identity & Access (DEC-004) y Audit Log (DEC-005); ninguno es microservicio separado (DEC-008) | CU-003, CU-004, CU-005, CU-006, CU-008, CU-009, CU-014 |
  | Notification Service | RF-008 | (referenciado desde CU-003/CU-005, ver vacío documental) |
  | Gateway | RF-015 (autenticación/RBAC, **solo APF2**) — responsabilidades fijadas en ADR-008: auth, routing, propagación de contexto | CU-016 |

  RF-012 y RF-014 se asignan a Asset Service y Telemetry Service, respectivamente, por la misma
  lógica de agrupamiento que el resto de la tabla (supuesto de trabajo, no decisión formalizada —
  ver "Supuestos"). RF-015 se asigna al Gateway porque ADR-007/ADR-008 ya establecen que es el
  único componente que valida JWT en el borde; esa sí es la decisión ya tomada. RF-009,
  RF-013/CU-014 y RF-018/CU-009 ya tienen ownership resuelto por decisión (DEC-004, DEC-005,
  DEC-006); su granularidad (módulo interno de Incident Service, no microservicio separado) quedó
  fijada por DEC-008, sin arquitectura hexagonal formal.
- ADR relevantes: ADR-001, ADR-002, ADR-003, ADR-007, ADR-008, ADR-009.
