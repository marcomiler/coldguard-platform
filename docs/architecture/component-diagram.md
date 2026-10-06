# Diagrama de componentes

Se profundizan los contenedores de mayor valor arquitectónico: **Incident Service** y, desde la
implementación de SPEC-005, **Asset Service** (sección al final). Los demás (Telemetry Service,
Notification Service) permanecen a nivel de contenedor
(`docs/architecture/container-diagram.md`) hasta que el equipo decida profundizarlos.

Componentes **conceptuales**, con responsabilidades claras. **No se adopta arquitectura hexagonal
formal como patrón obligatorio del MVP** (DEC-008, `docs/planning/decisions-log.md`): se mantiene
separación por módulos/capas, con interfaces o puertos únicamente en los límites externos
(persistencia, mensajería, correo, observabilidad) — no se presentan clases ni paquetes Java; son
la descomposición lógica mínima necesaria para razonar sobre CU-003 a CU-006, más los tres módulos
internos adicionales descritos abajo (Identity & Access, Audit Log, consultas operativas).

```mermaid
flowchart TB
  subgraph IS[Incident Service]
    API[API / Command Handler]
    Engine[Motor de evaluación y priorización]
    AppSvc[Servicio de aplicación:<br/>reconocimiento, escalamiento, cierre]
    Repo[Repositorio de incidentes]
    Publisher[Publicador de eventos]
    Audit[Auditoría / correlación]
    PersistAdapter[Adaptador de persistencia]
    MsgAdapter[Adaptador de mensajería]
    subgraph Modules["Módulos internos (DEC-008, no microservicios separados)"]
      IAMod[Identity & Access<br/>RF-013/CU-014]
      ALMod[Audit Log<br/>RF-018/CU-009]
      MetricsMod[Consultas operativas<br/>RF-009/CU-008]
    end
  end

  GW[Gateway] -->|gRPC| API
  TS[Telemetry Service] -->|TelemetryThresholdBreached| API
  API --> Engine
  Engine -->|RN-003 a RN-005, RN-012 a RN-014| Repo
  API --> AppSvc
  AppSvc -->|RN-006, RN-007, RN-019| Repo
  Repo --> PersistAdapter --> DB[(PostgreSQL, esquema incident)]
  AppSvc --> Audit
  Engine --> Audit
  Repo -.->|evento a publicar, misma transacción, ADR-009| Publisher
  Publisher --> MsgAdapter --> MQ[RabbitMQ]
  Audit -.->|correlación, RNF-002| MQ
  GW -->|gRPC| IAMod
  GW -->|gRPC| ALMod
  IAMod --> PersistAdapter
  ALMod --> PersistAdapter
  MetricsMod --> Repo
```

## Componentes y responsabilidad

| Componente | Responsabilidad |
|---|---|
| API / Command Handler | Recibe comandos vía gRPC desde el Gateway y eventos desde Telemetry Service; los traduce a operaciones del dominio de incidente. No contiene lógica de negocio propia. |
| Motor de evaluación y priorización | Aplica RN-003 (severidad), RN-004/RN-005 (equivalencia y persistencia), RN-012 a RN-014 (impacto/urgencia/prioridad/recálculo) al crear o actualizar un incidente (CU-003). |
| Servicio de aplicación (reconocimiento, escalamiento, cierre) | Orquesta CU-004 (reconocer), CU-005 (escalar, solicitado/confirmado por el Supervisor de operaciones — sin escalamiento automático por SLA) y CU-006 (cierre técnico, exclusivo del Técnico de mantenimiento, RN-019). |
| Repositorio de incidentes | Puerto de dominio para leer/escribir el estado del incidente; oculta el detalle de persistencia al resto de los componentes. |
| Publicador de eventos | Implementa el patrón Transactional Outbox (ADR-009): el evento se registra en la misma transacción que el cambio de estado, y un proceso independiente lo publica en RabbitMQ. |
| Auditoría / correlación | Registra cada transición relevante con actor, timestamp y motivo (RN-008), propagando correlation ID (RNF-002). |
| Adaptador de persistencia | Puerto/adaptador hacia PostgreSQL (esquema `incident`, ADR-006); ports and adapters, sin acceso directo desde el resto de los componentes. |
| Adaptador de mensajería | Puerto/adaptador hacia RabbitMQ (ADR-004); usado por el Publicador de eventos. |
| Identity & Access (módulo interno) | Dueño de usuarios, roles y asignaciones de acceso (RF-013/CU-014, DEC-004); esquema lógico de persistencia propio (`identity`), accedido a través del mismo adaptador de persistencia. No es un microservicio separado (DEC-008). |
| Audit Log (módulo interno) | Consulta restringida de solo lectura de la bitácora de auditoría (RF-018/CU-009, DEC-005); esquema lógico de persistencia propio (`auditlog`). No es un microservicio separado (DEC-008). |
| Consultas operativas (módulo interno) | Atiende RF-009/CU-008 (métricas de negocio: SLA, prioridad, volumen de incidentes) leyendo del Repositorio de incidentes (DEC-006). No es un microservicio separado (DEC-008). |

## Cómo atraviesan los CU y eventos principales

| CU | Componentes que participan | Eventos |
|---|---|---|
| CU-003 (crear incidente automático) | API → Motor de evaluación y priorización → Repositorio → Publicador de eventos | `TelemetryThresholdBreached` (entrada), `IncidentCreated` (salida) |
| CU-004 (reconocer y coordinar) | API → Servicio de aplicación → Repositorio → Auditoría → Publicador de eventos | `IncidentAcknowledged` |
| CU-005 (escalar) | API → Servicio de aplicación → Repositorio → Auditoría → Publicador de eventos | `IncidentEscalated` |
| CU-006 (cerrar) | API → Servicio de aplicación → Repositorio → Auditoría → Publicador de eventos | `IncidentClosed` |

## Asset Service

Mismo criterio que arriba: componentes **conceptuales** con responsabilidad clara, separación por
capas y puertos solo en los límites externos (DEC-008); no se presentan clases ni paquetes.

```mermaid
flowchart TB
  subgraph AS[Asset Service]
    API2[API gRPC<br/>identidad y errores]
    Catalog[Catálogo:<br/>organización, sede, activo]
    Sensors[Registro de sensores<br/>y perfil operativo]
    Lifecycle[Ciclo de vida del sensor:<br/>estado, calibración,<br/>reasignación, retiro]
    Expiry[Tarea de vencimiento<br/>de calibración]
    Hist[Historial del sensor]
    Ctx[Contexto de evaluación]
    Repo2[Repositorios]
    Pub2[Publicador de eventos]
  end

  GW[Gateway] -->|gRPC| API2
  TS[Telemetry Service] -->|gRPC, solo lectura| Ctx
  API2 --> Catalog
  API2 --> Sensors
  API2 --> Lifecycle
  API2 --> Hist
  API2 --> Ctx
  Expiry -->|actor de sistema| Lifecycle
  Catalog --> Repo2
  Sensors --> Repo2
  Lifecycle --> Repo2
  Hist --> Repo2
  Ctx --> Repo2
  Repo2 --> DB2[(PostgreSQL, esquema asset)]
  Catalog -.->|misma transacción, ADR-009| Pub2
  Sensors -.-> Pub2
  Lifecycle -.-> Pub2
  Pub2 --> MQ2[RabbitMQ]
```

| Componente | Responsabilidad |
|---|---|
| API gRPC | Traduce las llamadas del Gateway a casos de uso y los errores de negocio a estados gRPC con un código estable (`x-error-code`). Resuelve quién llama a partir del certificado mTLS: el Gateway propaga al usuario; un servicio interno configurado (Telemetry) es un actor de sistema. No contiene reglas de negocio. |
| Catálogo | Organización → sede → unidad de frío (activo), con su criticidad obligatoria (RF-001, RF-012, D-13). Publica `AssetRegistered` y `AssetUpdated`. |
| Registro de sensores y perfil operativo | Alta del sensor con asignación inicial, calibración inicial y perfil opcionales; datos técnicos; perfil operativo con control de versión (RF-002, RF-010, RF-011). Publica `OperationalProfileUpdated`. |
| Ciclo de vida del sensor | Máquina de estados ACTIVO / EN_MANTENIMIENTO / INACTIVO / RETIRADO con sus guards de evidencia de calibración (RN-017, RN-018), registro de calibraciones, reasignación y retiro lógico. Toda operación exige motivo y publica `SensorStatusChanged`, `SensorCalibrationRecorded`, `SensorReassigned` o `SensorRetired`. |
| Tarea de vencimiento de calibración | Periódica y configurable; mueve a EN_MANTENIMIENTO los sensores ACTIVOS o INACTIVOS con calibración vencida, una transacción por sensor, con el actor de sistema `calibration-expiry-job` (RN-018, DEC-022). Publica `SensorCalibrationExpired`. |
| Historial del sensor | Quién, cuándo, por qué y valor anterior/posterior de cada cambio (RN-017); solo inserción, paginado por cursor, consultable también tras el retiro (CU-021). |
| Contexto de evaluación | Proyección de solo lectura (sensor, activo, criticidad, perfil) para Telemetry (DEC-017); nunca para usuarios. |
| Repositorios | Puerto hacia PostgreSQL (esquema `asset`, ADR-006); control de versión optimista; sin operación de borrado sobre sensores, calibraciones ni historial. |
| Publicador de eventos | Transactional Outbox (ADR-009): el evento se escribe en la misma transacción que el cambio de estado. |

| CU | Componentes que participan | Eventos |
|---|---|---|
| CU-001, CU-012, CU-013 (catálogo y criticidad) | API → Catálogo → Repositorios → Publicador | `AssetRegistered`, `AssetUpdated` |
| CU-007, CU-011 (alta de sensor y perfil) | API → Registro de sensores → Repositorios → Publicador | `SensorCalibrationRecorded` (si trae calibración), `OperationalProfileUpdated` |
| CU-017 (cambio de estado) | API (o tarea de vencimiento) → Ciclo de vida → Repositorios → Publicador | `SensorStatusChanged` (+ `SensorCalibrationExpired` si lo origina el vencimiento) |
| CU-018, CU-019, CU-020 (reasignar, calibrar, retirar) | API → Ciclo de vida → Repositorios → Publicador | `SensorReassigned`, `SensorCalibrationRecorded`, `SensorRetired` + `SensorStatusChanged` |
| CU-021 (historial) | API → Historial → Repositorios | — |

## Resuelto por decisión

- RF-013/CU-014 (gestión de asignaciones de acceso): módulo interno **Identity & Access**, dentro
  de Incident Service (DEC-004, DEC-008).
- RF-009/CU-008 (consultar métricas operativas): módulo interno de consultas operativas, dentro de
  Incident Service (DEC-006, DEC-008).
- RF-018/CU-009 (consultar bitácora de auditoría, solo lectura restringida): módulo interno
  **Audit Log**, dentro de Incident Service (DEC-005, DEC-008).
- Arquitectura hexagonal formal: **no se adopta** como patrón obligatorio del MVP (DEC-008); se
  mantiene separación por módulos/capas con puertos solo en los límites externos.

El nivel de detalle de componente de los tres módulos internos se limita a la tabla de arriba; su
descomposición en clases/paquetes es diseño técnico posterior, no bloqueado por esta decisión.

## TODO

- Componentes internos de Telemetry Service y Notification Service: no profundizados en esta
  fase (los de Asset Service están en la sección anterior).
- Clases y paquetes técnicos concretos de Identity & Access, Audit Log y consultas operativas:
  diseño técnico posterior.
