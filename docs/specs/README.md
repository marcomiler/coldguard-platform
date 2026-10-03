# Specs de implementación — MVP local

Conjunto de especificaciones para completar el desarrollo de ColdGuard **en ejecución local con
Docker Compose** (RNF-001). No cubren despliegue en Azure (fase posterior) ni pruebas unitarias /
de integración / de contrato (se especificarán al final, en un spec dedicado de testing). Cada
spec indica la validación manual/smoke mínima para comprobar que el slice funciona en local.

Los specs **no implementan código**: describen qué construir, en qué orden, con qué contratos y
bajo qué criterios de aceptación. Todo lo que no está respaldado por la documentación del
repositorio se marca explícitamente como **Decisión requerida** o **Placeholder académico**.

## Fuentes analizadas

`CLAUDE.md`, `.claude/rules/*`, `docs/product/*`, `docs/domain/*`, `docs/architecture/*` (ADR-001
a ADR-009), `docs/planning/decisions-log.md` (DEC-001 a DEC-014), `docs/quality/*`,
`docs/operations/*`, `docs/infrastructure/docker-strategy.md`, y el código real de `apps/*`,
`contracts/grpc/`, `deploy/local/` y `deploy/scripts/` (estado al 2026-09-30).

## Diagnóstico del estado actual (verificado en el repositorio)

| Área | Estado real | Hueco principal |
|---|---|---|
| Gateway | `POST /api/v1/incidents`, `POST /api/v1/incidents/{id}/close`; Spring Security con JWT "fail-closed" (sin emisor configurado); cliente gRPC con mTLS hacia Incident Service | Sin login/emisión de JWT, sin RBAC del resto de endpoints, sin rutas hacia Asset/Telemetry, sin correlación/trazas, modelo de error ad hoc |
| Incident Service | Crear incidente (RN-004, RN-012), cerrar (CREATED→CLOSED) vía gRPC; Flyway `V1`, `V2`; mTLS servidor | Causa/comentario/fecha de cierre **no se persisten**; sin Outbox ni RabbitMQ; sin reconocer/escalar; sin consumidor de `TelemetryThresholdBreached`; sin Identity & Access, Audit Log ni consultas operativas; sin `@Transactional` en cierre |
| Asset Service | Solo `package-info.java` | Todo (RF-001, RF-002, RF-010, RF-011, RF-012, RF-016) |
| Telemetry Service | Solo `package-info.java` | Todo (RF-003, RF-004, RF-014, RF-017) |
| Notification Service | Solo `package-info.java`; sin esquema | Todo (RF-008) |
| Sensor Simulator | No existe (`simulator/` no está en el repo) | Todo (actor de CU-002) |
| Contratos | `contracts/grpc/incident/v1/incident_service.proto` y contratos por contexto (SPEC-002 implementado) | — |
| Docker | Compose con 5 servicios + infraestructura | Dockerfiles de asset/telemetry/notification **no construyen** (imagen `eclipse-temurin:25-jdk` sin Maven; copian un solo `pom.xml` de módulo y el reactor raíz exige los 5); sin healthchecks salvo incident; conflicto de puerto 9090 (Prometheus vs. gRPC de Incident al correr en host) |
| Observabilidad | Prometheus con self-scrape; Grafana y Loki sin provisionar | Sin Micrometer/Prometheus, sin OpenTelemetry, sin logs JSON, sin envío a Loki, sin dashboards |
| Reglas `.claude/` | `java-spring.md` y `security.md` dicen que no hay `.proto` ni librería gRPC ni configuración de seguridad | Desactualizadas frente a DEC-012 y al código actual (actualizar en SPEC-011) |

## Mapa de specs y orden de ejecución

| Orden | Spec | Cubre | Depende de |
|---|---|---|---|
| 1 | [SPEC-001 Plataforma base y build local](SPEC-001-plataforma-base.md) | Build reproducible, Dockerfiles, Compose, convenciones transversales | — |
| 2 | [SPEC-002 Contratos gRPC y catálogo físico de eventos](SPEC-002-contratos.md) | `.proto` de todos los servicios, envelope JSON, topología RabbitMQ | SPEC-001 |
| 3 | [SPEC-003 Mensajería confiable (Outbox + consumidor idempotente)](SPEC-003-mensajeria-confiable.md) | ADR-009, idempotencia, reintentos, DLQ | SPEC-001, SPEC-002 |
| 4 | [SPEC-004 Identidad, autenticación y RBAC](SPEC-004-identidad-seguridad.md) | RF-013, RF-015, CU-014, CU-016, R-014 | SPEC-002 |
| 5 | [SPEC-005 Asset Service](SPEC-005-asset-service.md) | RF-001, RF-002, RF-010, RF-011, RF-012, RF-016 | SPEC-002, SPEC-003, SPEC-004 |
| 6 | [SPEC-006 Telemetry Service](SPEC-006-telemetry-service.md) | RF-003, RF-004, RF-014, RF-017 | SPEC-005 |
| 7 | [SPEC-007 Incident Service (completar)](SPEC-007-incident-service.md) | RF-005, RF-006, RF-007, RF-009, RF-018 | SPEC-003, SPEC-004, SPEC-006 |
| 8 | [SPEC-008 Notification Service](SPEC-008-notification-service.md) | RF-008 | SPEC-003, SPEC-007 |
| 9 | [SPEC-009 Gateway REST completo](SPEC-009-gateway-api.md) | Borde REST de todos los CU, ADR-008 | SPEC-004 a SPEC-008 (incremental) |
| 10 | [SPEC-010 Sensor Simulator](SPEC-010-sensor-simulator.md) | Actor de CU-002, escenarios de demo | SPEC-006 |
| 11 | [SPEC-011 Observabilidad y operación local](SPEC-011-observabilidad-operacion.md) | RNF-002, RNF-004, RNF-008, runbooks, seed, smoke E2E | Transversal; cierra tras SPEC-010 |

SPEC-009 se implementa **de forma incremental**: cada spec de servicio (005–008) incluye la tarea
de exponer sus rutas en el Gateway siguiendo las convenciones fijadas en SPEC-009.

## Decisiones requeridas antes de implementar

Ningún spec las da por tomadas. Cada una indica una **recomendación**; la regla del proyecto exige
registrarlas (ADR nuevo, actualización de ADR o DEC en `docs/planning/decisions-log.md`) **antes**
de codificar el slice afectado (`.claude/rules/architecture.md`, `.claude/rules/workflow.md`).

| ID | Decisión | Recomendación | Registro | Bloquea |
|---|---|---|---|---|
| D-01 | ¿Librería técnica compartida (outbox, consumidor idempotente, correlación, modelo de error) o duplicar por servicio? | Módulo Maven `libs/coldguard-commons` **sin dominio**, solo infraestructura técnica | ADR nuevo (afecta ADR-001) | SPEC-003 |
| D-02 | ¿Quién emite el JWT en local? | Gateway firma (RS256) tras verificar credenciales por gRPC contra Identity & Access; Identity no maneja claves | Actualización de ADR-007 | SPEC-004 |
| D-03 | Mecanismo de propagación de identidad Gateway → servicios | Metadata gRPC (`x-actor-id`, `x-actor-roles`) sobre canal mTLS ya existente | Actualización de ADR-007 (cierra el punto abierto de DEC-014) | SPEC-004 |
| D-04 | Estados del incidente más allá de CREATED/CLOSED | `CREATED → ACKNOWLEDGED → ESCALATED → CLOSED` con guards de SPEC-007 | DEC nuevo (amplía DEC-013) | SPEC-007 |
| D-05 | Transporte de `TelemetryThresholdBreached` (`data-flow.md` dice "gRPC, si aplica"; `event-flow.md` dice RabbitMQ) | Evento por RabbitMQ vía Outbox (ADR-009 ya lista a Telemetry como emisor) | DEC nuevo + corrección de `data-flow.md`/`container-diagram.md` | SPEC-006, SPEC-007 |
| D-06 | Productor de `SensorConnectivityLost` (`bounded-contexts.md` dice Asset; `event-flow.md` y `container-diagram.md` dicen Telemetry) | Telemetry Service (es quien conoce la última lectura) | DEC nuevo + corrección de `bounded-contexts.md` | SPEC-006 |
| D-07 | ¿Cómo obtiene Telemetry el perfil operativo, estado del sensor y criticidad del activo? | gRPC a Asset (`GetSensorEvaluationContext`) con caché local acotada (TTL configurable) | DEC nuevo | SPEC-006 |
| D-08 | Ubicación del endpoint REST de CU-015 (DEC-003 dice "REST interno protegido"; la regla dice que solo el Gateway expone REST de negocio) | Ruta en el Gateway restringida a Administrador de plataforma → gRPC a Telemetry (mismo camino de evaluación, RN-015) | DEC que precisa DEC-003 | SPEC-006, SPEC-009 |
| D-09 | Cómo recibe Audit Log las transiciones de otros servicios | Consumo de eventos de dominio desde RabbitMQ hacia el esquema `auditlog`; Incident y su módulo Identity escriben in-process | DEC nuevo | SPEC-007 |
| D-10 | Destinatarios de notificación (no existe matriz, TODO en CU-003) | Placeholder académico: `IncidentCreated` → usuarios con rol Supervisor de operaciones; `IncidentEscalated` → Técnico de mantenimiento; Incident resuelve correos desde Identity & Access | Confirmación del PO + edición de `use-cases.md` | SPEC-007, SPEC-008 |
| D-11 | Topología de observabilidad local (TODO en `docker-strategy.md`/`deployment-view.md`) | OpenTelemetry Collector como punto único OTLP; trazas a un backend local compatible (candidato: Grafana Tempo); logs a Loki; métricas por scrape de Prometheus | DEC nuevo + `tech-stack.md` | SPEC-011 |
| D-12 | Valores de demostración (bandas de magnitud, ventana de persistencia, validez de calibración, frecuencia esperada, SLA) | Se configuran por perfil/sensor; valores de seed marcados como **placeholder académico** | Confirmación del PO (ya prevista en `decisions-log.md`, "Parámetros operativos configurables") | SPEC-005, SPEC-006 |
| D-13 | Modelo de organización/sede/unidad de frío (RF-001 sin atributos definidos) | `Organization` 1—N `Site` 1—N `Asset` (unidad de frío); atributos mínimos de SPEC-005 | Edición de `domain-model.md` | SPEC-005 |
| D-14 | Eventos de actualización de activo/perfil y de asignación de acceso (TODO en `commands-events.md`) | Catalogar `AssetUpdated`, `OperationalProfileUpdated`, `UserAccessAssignmentChanged` con siguiente ID/fila libre | Edición de `commands-events.md` (+ CU-013/CU-014) | SPEC-004, SPEC-005, SPEC-006 |
| D-15 | Endpoints de consulta de incidentes (listado/detalle) no tienen CU propio | Tratarlos como soporte de CU-004/005/006 y del tablero (HU-014) | Nota en `use-cases.md` | SPEC-007, SPEC-009 |
| D-16 | Retención de lecturas de telemetría | Sin purga en el MVP local; índice y paginación obligatorios | DEC o nota en `capacity-plan.md` | SPEC-006 |
| D-17 | ¿Se publica `TelemetryReceived` en RabbitMQ? (su único consumidor catalogado es el propio Telemetry Service) | No: se materializa como lectura persistida + contador Micrometer; publicarlo multiplicaría tráfico sin consumidor | Nota en `commands-events.md` | SPEC-002, SPEC-006 |

Cada spec lista además decisiones locales de menor alcance (p. ej. guards de D-04, modelado de
persistencia RN-005, sensores sin calibración inicial, SLA P4) con su propuesta.

## Estado de las decisiones

D-01 a D-17 aprobadas y registradas: ADR-010, actualización de ADR-007 y DEC-015 a DEC-020. Los
supuestos funcionales locales de cada spec y la estrategia técnica (formateador, ramas,
verificaciones) quedaron cerrados en DEC-021 y DEC-022 (valores placeholder académico). Donde un
spec dice "confirmar con el PO", rige DEC-022.

## Convenciones transversales (aplican a todos los specs)

1. **Capas** (DEC-008, DEC-011): `api` (gRPC/REST, mapeo), `application` (casos de uso, puertos),
   `domain` (modelo puro, sin Spring/JPA/AMQP), `infrastructure` (JPA, AMQP, SMTP, gRPC clientes),
   `config`. El dominio no importa nada de `org.springframework`, `jakarta.persistence`, `io.grpc`
   ni `com.rabbitmq`.
2. **Sin identificadores de documentación en código**: no se citan ADR/DEC/RN/RF/CU en código,
   POM, `.proto` ni configuración salvo cuando omitirlos haga la implementación insegura u opaca
   (`.claude/rules/workflow.md`).
3. **Records** para comandos, eventos, DTO y value objects; **clases** mutables solo para entidades
   JPA. Mapeo dominio↔JPA y dominio↔gRPC en clases `*Mapper` dedicadas (no inline en servicios ni
   en endpoints gRPC — hoy `IncidentGrpcService` mezcla ambos).
4. **Sealed + switch exhaustivo** para máquinas de estado (sensor, incidente) y resultados de
   evaluación; nunca `default` silencioso.
5. **Excepciones**: jerarquía por bounded context en `domain`/`application`; las de
   infraestructura (`DataAccessException`, `AmqpException`, `StatusRuntimeException`) se traducen
   en el adaptador. Un único traductor excepción→`Status` gRPC por servicio y un único traductor
   `Status`→HTTP en el Gateway (DRY).
6. **Transacciones**: `@Transactional` en el servicio de aplicación (no en repositorios ni
   endpoints); `readOnly = true` en consultas. Cambio de estado + registro de Outbox + auditoría
   in-process en la **misma** transacción.
7. **Rendimiento**: sin `open-in-view`; sin relaciones JPA entre agregados; consultas de listado
   con proyecciones y paginación obligatoria (tamaño máximo configurable); índices declarados en
   la misma migración que la tabla; stubs/canales gRPC singleton con deadline por llamada; pools
   (Hikari, prefetch AMQP) configurables por entorno; hilos virtuales habilitados en servicios
   I/O-bound evitando `synchronized` en caminos bloqueantes (`.claude/rules/java-spring.md`).
8. **Configuración**: todo valor ajustable vía `application.yml` + variable de entorno con default
   seguro para local; ninguna credencial con default distinto del de desarrollo documentado en
   `.env.example`; propiedades tipadas con `@ConfigurationProperties` (records) y validadas.
9. **Correlación**: `correlationId` y `traceparent` viajan en header HTTP → metadata gRPC → header
   AMQP; nunca como campo de negocio nuevo en los mensajes (el campo `correlation_id` del contrato
   v1 de Incident se mantiene por compatibilidad).
10. **Migraciones**: Flyway por servicio y por esquema (`V<n>__descripcion.sql`); nunca se edita
    una migración ya aplicada.
11. **Validación local de cada spec**: `mvn -q verify -DskipTests` compila; `docker compose ...
    up --build` levanta con healthchecks en verde; smoke manual descrito en cada spec.

## Fuera de alcance de este conjunto

- Despliegue en Azure, Terraform, Key Vault, Azure Communication Services (fase posterior).
- Pruebas unitarias, de integración, de contrato y de capacidad (spec final de testing).
- CI (`.github/workflows/ci.yml`) salvo que un cambio de build lo rompa; se ajusta en la fase de
  despliegue.
- CU-010 (no definido en el MVP) y capacidades IoT avanzadas (`scope-mvp.md`).
- Frontend (`coldguard-frontend`, ADR-002).
