# Registro de decisiones

Registro vivo, continuo, de decisiones puntuales de arquitectura y producto (`DEC-001` en
adelante). Desde el cierre del lote de arquitectura (Rondas 1-5,
`docs/architecture/architecture-consistency-report.md`, ahora congelado como bitácora histórica),
este archivo es el **único lugar donde se registran nuevas decisiones de arquitectura puntuales**
— no se reabre el reporte de consistencia para cada ajuste menor. El reporte solo vuelve a editarse
al cerrar un lote completo nuevo con su propia auditoría de consistencia.

## DEC-001 — Estrategia de repositorios y fuente de verdad

**Fecha:** TODO
**Estado:** Aprobada

### Contexto
ColdGuard requiere separar la experiencia de usuario de la plataforma backend, sin perder trazabilidad entre alcance, reglas de negocio, casos de uso y contratos.

### Decisión
Se mantendrán dos repositorios:
- `coldguard-platform`: dominio, servicios backend, infraestructura, observabilidad y documentación operativa.
- `coldguard-frontend`: interfaz, prototipos, experiencia de usuario y pruebas de interfaz.

El repositorio backend será la fuente de verdad para dominio, reglas de negocio, casos de uso, requisitos y contratos. El frontend consumirá esa documentación y mantendrá referencias a los identificadores funcionales aplicables.

### Consecuencias
- Se requiere versionar y publicar contratos API antes de integrar.
- Los prototipos deben referenciar los CU y RF que representan.
- Se reduce el acoplamiento entre despliegue de frontend y backend.

## DEC-002 — Plataforma Azure planificada (cómputo, persistencia, mensajería)

**Fecha:** Por confirmar
**Estado:** Aprobada (planificación; sin aprovisionamiento)

### Contexto
La Ronda 2 del reporte de consistencia arquitectónica había confirmado a Azure como proveedor
cloud objetivo, sin nombrar servicios concretos de cómputo, base de datos ni mensajería
(`docs/architecture/architecture-consistency-report.md`, pendiente #1 y #7). El equipo requiere
precisar esos servicios para poder planificar Sprint 7 sin bloquear el diseño.

### Decisión
- Cómputo de backend y sensor-simulator: **Azure Container Apps**, como plataforma planificada de
  despliegue de contenedores.
- Persistencia: **Azure Database for PostgreSQL Flexible Server**, como plataforma planificada
  para PostgreSQL (ADR-006).
- Mensajería: **RabbitMQ se conserva como broker del MVP** (ADR-004); no se sustituye por Azure
  Service Bus. RabbitMQ podrá desplegarse inicialmente como contenedor en Azure Container Apps o
  mediante una alternativa gestionada si el presupuesto lo permite; la elección operativa concreta
  queda como decisión futura, sin bloquear la arquitectura del MVP.

### Alternativas descartadas
- Azure Kubernetes Service (AKS) para cómputo: mayor complejidad operativa que Azure Container
  Apps para el alcance académico del MVP; queda descartado como opción principal, sin cerrar la
  puerta a una decisión futura distinta.
- Azure Service Bus como broker: descartado explícitamente; contradice CLAUDE.md (RabbitMQ como
  broker del MVP) y no fue solicitado por esta ronda de decisiones.

### Consecuencias
- Ningún recurso Azure se crea como parte de esta decisión; el aprovisionamiento y despliegue
  permanecen pendientes de ejecución y de aprobación explícita (`.claude/rules/infra.md`).
- `docs/architecture/deployment-view.md`, `tech-stack.md`, ADR-003, ADR-004 y ADR-006 se
  actualizan para nombrar estos servicios como planificados, no implementados.

### Trazabilidad
RNF-007; ADR-003, ADR-004, ADR-006; `docs/architecture/deployment-view.md`, `tech-stack.md`.

## DEC-003 — Protocolo de ingesta de telemetría

**Fecha:** Por confirmar
**Estado:** Aprobada

### Contexto
`docs/architecture/container-diagram.md` dejaba como TODO explícito el protocolo entre
Sensor Simulator/endpoint de pruebas y Telemetry Service (pendiente #2 del reporte de consistencia
arquitectónica).

### Decisión
- Sensor Simulator → Telemetry Service: **gRPC** interno (ADR-003).
- Inyección manual/controlada de telemetría (CU-015): **endpoint REST interno protegido**, no
  público, no una integración con sensor físico. Actor: Administrador de plataforma.

### Alternativas descartadas
- REST para ambos canales: descartado para el canal del simulador porque ADR-003 ya fija gRPC
  para comunicación síncrona interna de alta frecuencia.
- gRPC para el canal de pruebas manuales: descartado por simplicidad operativa de un endpoint de
  diagnóstico/demostración, sin alto volumen.

### Consecuencias
- `docs/architecture/container-diagram.md` deja de marcar el protocolo como pendiente.
- RN-015 (evaluación idéntica de telemetría de prueba) no cambia: ambos canales llegan a la misma
  lógica de evaluación en Telemetry Service.

### Trazabilidad
RF-003, RF-014; CU-002, CU-015; ADR-003; `docs/architecture/container-diagram.md`, `data-flow.md`.

## DEC-004 — Identity & Access como módulo/servicio lógico

**Fecha:** Por confirmar
**Estado:** Aprobada

### Contexto
RF-013/CU-014 (asignaciones de acceso) no tenía servicio asignado (pendiente #3 del reporte de
consistencia arquitectónica); `docs/product/scope-mvp.md` lo dejaba fuera de la tabla de
asignación servicio→RF.

### Decisión
Un módulo o servicio lógico **Identity & Access** es dueño de usuarios, roles y asignaciones de
acceso de RF-013/CU-014. No se asigna esta responsabilidad a Asset Service. El Gateway sigue
siendo el único componente que valida el JWT y propaga contexto de seguridad (ADR-007, ADR-008);
Identity & Access es dueño de los datos de usuarios/roles, no del borde de autenticación. La
autenticación/autorización real de endpoints pertenece a APF2 (RF-015/CU-016), sin cambios.

### Alternativas descartadas
- Asignar RF-013/CU-014 a Asset Service: descartado explícitamente por esta decisión, para no
  mezclar gestión de accesos con gestión de activos/sensores.
- Delegar la responsabilidad íntegramente al Gateway: descartado; el Gateway se mantiene delgado
  (ADR-008), sin lógica de negocio de gestión de usuarios/roles.

### Consecuencias
- `docs/architecture/container-diagram.md` y `component-diagram.md` representan Identity & Access
  como módulo/servicio lógico con ownership definido.
- `docs/product/scope-mvp.md` actualiza la tabla de asignación servicio→RF/CU.
- No se crea un ADR nuevo: esta decisión concreta un vacío de asignación, no introduce un patrón
  arquitectónico distinto de los ya decididos (ADR-006, ADR-007, ADR-008).

### Trazabilidad
RF-013, RF-015; CU-014, CU-016; ADR-007, ADR-008; `docs/architecture/container-diagram.md`,
`component-diagram.md`, `docs/product/scope-mvp.md`.

**Nota de actualización (DEC-008)**: Identity & Access se aloja como **módulo interno dentro de
Incident Service**, no como microservicio separado. El ownership de RF-013/CU-014 fijado en esta
decisión no cambia; cambia únicamente su forma de despliegue.

## DEC-005 — Audit Log como módulo/servicio lógico y RF-018

**Fecha:** Por confirmar
**Estado:** Aprobada

### Contexto
CU-009 (consultar bitácora de auditoría) no tenía RF asociado (pendiente reportado en rondas
anteriores, ver "Historial consolidado" en `docs/architecture/architecture-consistency-report.md`);
RN-008/CU-009 tampoco tenía servicio asignado (pendiente #3 del reporte de consistencia
arquitectónica).

### Decisión
- Cada servicio genera sus propios registros de auditoría de sus acciones relevantes (RN-008), sin
  cambio.
- Un módulo o servicio lógico **Audit Log** centraliza la consulta restringida de CU-009. Se crea
  **RF-018** — "Consultar la bitácora de auditoría de transiciones relevantes registradas por los
  servicios (RN-008), en modo restringido de solo lectura" — trazado a CU-009 y RN-008.
- En el MVP, Audit Log puede usar PostgreSQL compartido con ownership lógico de esquema, coherente
  con ADR-006. No se declara almacenamiento inmutable criptográfico, WORM, cumplimiento
  regulatorio ni retención legal: eso queda fuera de alcance del MVP.

### Alternativas descartadas
- Reutilizar el RF de otro servicio para CU-009: descartado; se prefiere un RF propio para no
  mezclar trazabilidad de auditoría con la de otro dominio.
- Almacenamiento inmutable/WORM en el MVP: descartado explícitamente por alcance académico y falta
  de requisito de cumplimiento confirmado.

### Consecuencias
- `docs/domain/functional-requirements.md` agrega RF-018 (siguiente ID libre).
- `docs/domain/use-cases.md` (CU-009) y `docs/domain/traceability-matrix.md` actualizan la
  trazabilidad de RF-018.
- `docs/product/scope-mvp.md` actualiza la tabla de capacidades y la de asignación servicio→RF/CU.
- `docs/architecture/container-diagram.md`/`component-diagram.md` representan Audit Log como
  módulo/servicio lógico.

### Trazabilidad
RF-018 (nuevo); CU-009; RN-008; ADR-006; `docs/domain/functional-requirements.md`,
`docs/domain/use-cases.md`, `docs/domain/traceability-matrix.md`.

**Nota de actualización (DEC-008)**: Audit Log se aloja como **módulo interno dentro de Incident
Service**, no como microservicio separado. El ownership de RF-018/CU-009 fijado en esta decisión
no cambia; cambia únicamente su forma de despliegue.

## DEC-006 — Consulta de métricas operativas dentro de Incident Service

**Fecha:** Por confirmar
**Estado:** Aprobada

### Contexto
RF-009/CU-008 (consultar métricas operativas) no tenía servicio asignado (pendiente #3 del reporte
de consistencia arquitectónica); `docs/architecture/component-diagram.md` lo listaba como "sin
asignar".

### Decisión
CU-008/RF-009 será atendido por un **módulo de consultas operativas dentro de Incident Service**
durante el MVP (no un microservicio de métricas independiente). Grafana y Azure Monitor/
Application Insights siguen siendo destinos de consulta de observabilidad técnica, no
microservicios propios ni la fuente de RF-009 (que es una consulta de negocio: SLA, prioridad,
volumen de incidentes).

### Alternativas descartadas
- Servicio de métricas independiente: descartado explícitamente; no se justifica su complejidad
  operativa para el alcance del MVP.
- Delegar RF-009 a Grafana directamente: descartado; Grafana consulta observabilidad técnica
  (RNF-002/RNF-004), no reemplaza una consulta de negocio con RBAC propio (CU-008, actor
  Supervisor de operaciones).

### Consecuencias
- `docs/architecture/component-diagram.md` y `container-diagram.md` documentan el módulo de
  consultas operativas dentro de Incident Service.
- `docs/product/scope-mvp.md` actualiza la tabla de asignación servicio→RF/CU con RF-009 →
  Incident Service.

### Trazabilidad
RF-009; CU-008; `docs/architecture/component-diagram.md`, `docs/product/scope-mvp.md`.

**Nota de actualización (DEC-008)**: confirmado — el módulo de consultas operativas ya estaba
definido como interno a Incident Service desde esta decisión; DEC-008 lo reafirma junto con
Identity & Access y Audit Log bajo el mismo principio arquitectónico (sin hexagonal formal, sin
microservicios adicionales para estos tres módulos).

## DEC-007 — Proveedor productivo de notificaciones

**Fecha:** Por confirmar
**Estado:** Aprobada (planificación; sin recurso creado)

### Contexto
El adaptador de notificaciones no tenía proveedor productivo decidido (pendiente #4 del reporte de
consistencia arquitectónica); `docs/architecture/tech-stack.md` lo marcaba "Por decidir".

### Decisión
- **Mailpit** es solo herramienta prevista para desarrollo local (sin cambio).
- **Azure Communication Services Email** es el proveedor productivo planificado, previsto para
  Sprint 6 y el despliegue final en Azure.
- Notification Service conserva un adaptador desacoplado de proveedor (puerto/adaptador, sin
  acoplar el dominio a un SDK concreto).

### Alternativas descartadas
- SES, SendGrid o SMTP productivo genérico: no se seleccionan; Azure Communication Services Email
  se alinea con Azure como proveedor cloud objetivo (DEC-002).

### Consecuencias
- No se afirma que el recurso Azure Communication Services esté creado ni que se hayan enviado
  correos reales.
- `docs/architecture/tech-stack.md`, `deployment-view.md`, `system-context.md`,
  `docs/planning/roadmap.md` (Sprint 6) y `release-plan.md` (v0.3) actualizan el estado del
  adaptador de notificaciones.

### Trazabilidad
RF-008; `docs/architecture/tech-stack.md`, `docs/planning/roadmap.md`.

## DEC-008 — Arquitectura interna del MVP: sin hexagonal formal, módulos internos en Incident Service

**Fecha:** Por confirmar
**Estado:** Aprobada

### Contexto
`docs/architecture/component-diagram.md` dejaba abierta la pregunta de si adoptar arquitectura
hexagonal formal (pendiente #5 del reporte de consistencia arquitectónica); DEC-004, DEC-005 y
DEC-006 habían asignado el ownership de Identity & Access, Audit Log y las métricas operativas sin
fijar si serían microservicios separados o módulos internos.

### Decisión
- **No se adopta arquitectura hexagonal formal** como patrón obligatorio del MVP. Se mantiene
  separación por módulos/capas, con interfaces o puertos únicamente en los límites externos:
  persistencia (PostgreSQL), mensajería (RabbitMQ), correo (adaptador de notificaciones) y
  observabilidad (OpenTelemetry).
- **Identity & Access, Audit Log y el módulo de consultas operativas (métricas) son módulos
  internos de Incident Service**, no microservicios separados. Se colocan en el mismo proceso que
  Incident Service para minimizar la cantidad de contenedores del MVP, conservando ownership
  lógico de esquema de persistencia propio para Identity & Access y Audit Log (ADR-006).

### Alternativas descartadas
- Arquitectura hexagonal formal (puertos y adaptadores en cada capa interna): descartada por
  sobrecarga de diseño no justificada para el alcance académico del MVP.
- Identity & Access y Audit Log como microservicios independientes (una caja C4 propia cada uno):
  descartada; aumentaría el número de contenedores sin beneficio claro en esta fase.
- Alojar los tres módulos en Asset Service: descartada; DEC-004 ya había excluido explícitamente
  esta opción para Identity & Access, para no mezclar gestión de accesos con gestión de activos.

### Consecuencias
- `docs/architecture/container-diagram.md`, `component-diagram.md` y `deployment-view.md` dejan de
  representar Identity & Access y Audit Log como cajas C4 separadas; se documentan como módulos
  internos de Incident Service.
- La tabla de contenedores de `container-diagram.md` conserva los esquemas lógicos de persistencia
  separados (`identity`, `auditlog`) aunque el proceso sea el mismo que Incident Service.
- El pendiente #5 del reporte de consistencia arquitectónica queda resuelto.

### Trazabilidad
RF-013, RF-018, RF-009; CU-008, CU-009, CU-014; `docs/architecture/container-diagram.md`,
`component-diagram.md`, `deployment-view.md`; DEC-004, DEC-005, DEC-006 (actualizadas con nota).

## DEC-009 — RabbitMQ como contenedor planificado en Azure Container Apps

**Fecha:** Por confirmar
**Estado:** Aprobada (planificación; sin despliegue)

### Contexto
DEC-002 había dejado la operación de RabbitMQ en Azure como "contenedor en Azure Container Apps o
alternativa gestionada, decisión futura". El equipo confirma la opción concreta para la
presentación final.

### Decisión
Para la presentación final, **RabbitMQ se desplegará como contenedor planificado dentro de Azure
Container Apps**, junto con el resto de los servicios backend (DEC-002). No se usa Azure Service
Bus ni ninguna alternativa de mensajería gestionada en el MVP.

### Alternativas descartadas
- Azure Service Bus: descartado explícitamente (ya lo estaba desde ADR-004/DEC-002; esta decisión
  lo reafirma sin ambigüedad).
- Alternativa de mensajería gestionada (por ejemplo, un RabbitMQ administrado de terceros):
  descartada para el MVP; queda fuera de alcance, no como opción abierta.

### Consecuencias
- `docs/architecture/deployment-view.md` (Vista B) deja de presentar la operación de RabbitMQ en
  Azure como ambigua; se fija como contenedor en Azure Container Apps.
- No se crea ni configura ningún recurso Azure como parte de esta decisión.

### Trazabilidad
ADR-004; DEC-002; `docs/architecture/deployment-view.md`.

## DEC-010 — Estrategia de gestión de secretos

**Fecha:** Por confirmar
**Estado:** Aprobada (planificación; sin recursos creados)

### Contexto
`docs/architecture/deployment-view.md` dejaba la gestión de secretos como "sin servicio Azure
concreto seleccionado". El equipo confirma la estrategia planificada para cerrar el vacío.

### Decisión
- **Local**: variables de entorno mediante un archivo `.env` no versionado (excluido por
  `.gitignore`), con `.env.example` documentando las claves esperadas sin valores reales.
- **Azure planificado**: **Azure Key Vault** para almacenamiento de secretos, con **Managed
  Identity** para que los servicios desplegados en Azure Container Apps accedan a Key Vault sin
  credenciales embebidas.
- Prohibido documentar secretos reales en código, imágenes de contenedor, el repositorio o logs
  (regla ya vigente, `.claude/rules/security.md`).

### Alternativas descartadas
- Secretos embebidos en variables de entorno de Azure Container Apps sin Key Vault: descartado;
  Key Vault + Managed Identity es más seguro y es el patrón estándar de Azure para este caso.
- Un `.env` versionado o secretos en `docker-compose.yml`: descartado explícitamente; contradice
  las reglas de seguridad ya vigentes del proyecto.

### Consecuencias
- No se afirma que Key Vault, identidades administradas, permisos o secretos existan: es diseño
  planificado, no una implementación ni un recurso Azure creado.
- `docs/architecture/deployment-view.md` (Vista B) y `tech-stack.md` reflejan esta estrategia.

### Trazabilidad
RNF-003; `.claude/rules/security.md`; `docs/architecture/deployment-view.md`, `tech-stack.md`.

## DEC-011 — Decisiones técnicas del scaffolding inicial (Sprint 1)

**Fecha:** 2026-08-29
**Estado:** Aprobada

### Contexto
El scaffolding inicial del monorepo backend (`apps/gateway`, `apps/asset-service`,
`apps/telemetry-service`, `apps/incident-service`, `apps/notification-service`) requería fijar
decisiones técnicas puntuales no cubiertas todavía por `docs/architecture/tech-stack.md`, cuyo TODO
señalaba explícitamente: "Decisiones de stack específicas por servicio (librerías internas,
versiones fijadas de dependencias): no definidas todavía".

### Decisión
- **Gestor de build**: Maven (ya confirmado en `CLAUDE.md`, no es una decisión nueva). Un módulo
  Maven por servicio bajo `apps/`, reactor agregado por un `pom.xml` raíz (ADR-001).
- **Versión de Spring Boot — registro original (2026-08-29), incorrecto**: se fijó la versión
  incorrecta inicial, afirmando que "Spring Boot 4.x aún no está publicado" en
  Maven Central. **Este dato era incorrecto**: Spring Boot 4.0 llevaba publicado desde el 20 de
  noviembre de 2025 y Spring Boot 4.1 desde el 10 de junio de 2026, meses antes de esta decisión.
  La consulta a Maven Central de esa ronda usó una API/índice que no devolvía resultados para
  `v:4*`, y el resultado se aceptó sin contrastarlo con otra fuente. **Corregido en la ronda
  siguiente** (mismo día, 2026-08-29): ver "Corrección posterior" más abajo.
- **`groupId`/`artifactId`**: `com.coldguard` como `groupId` de todos los módulos; `artifactId`
  igual al nombre de carpeta (`gateway`, `asset-service`, etc.). Paquete base
  `com.coldguard.<servicio>` (p. ej. `com.coldguard.asset`).
- **Convención de paquetes por módulo**: `config`, `api`, `application`, `domain`,
  `infrastructure`, sin arquitectura hexagonal formal (DEC-008); son placeholders de
  `package-info.java` sin lógica de negocio.
- **Spring Security solo en `gateway`**: el resto de los servicios no incluye
  `spring-boot-starter-security` como dependencia, porque la validación de JWT es responsabilidad
  exclusiva del Gateway (ADR-007, ADR-008); los servicios internos confían en la identidad
  propagada y no reimplementan auth de borde.
- **Esquema de `notification-service` sin confirmar**: `application.yml` de este módulo no fija
  `currentSchema` en la URL JDBC porque `docs/domain/bounded-contexts.md` no confirma un esquema
  propio para Notification Service. Queda como decisión pendiente, no inventada aquí.
- **Micrometer/Prometheus aún no cableado**: los módulos no incluyen todavía
  `micrometer-registry-prometheus`; `observability/prometheus/prometheus.yml` solo hace
  self-scrape hasta que esa dependencia se agregue en una ronda posterior.

### Corrección posterior (2026-08-29, ronda siguiente)

**Versión de Spring Boot — corregida**: `4.1.1` (artefacto
`org.springframework.boot:spring-boot-starter-parent:4.1.1`), publicada en Maven Central el
**20 de agosto de 2026** (verificado vía `central.sonatype.com/solrsearch`, campo `timestamp`
`1787230308000` → `2026-08-20T00:11:48Z`). Es la última versión estable de la línea 4.1.x: existe
`4.1.0` (publicada 2026-06-10) y milestones/RC previos (`4.1.0-M1..M4`, `4.1.0-RC1`), pero ninguna
versión posterior a `4.1.1` (`4.1.2` no existe; `4.2.0-M1` es solo milestone, no GA). Se prefiere
la línea 4.1.x sobre 4.0.x (última: `4.0.8`, misma fecha de publicación) porque 4.1 es la línea
minor estable más reciente.

Se aplica a `pom.xml` raíz (`spring-boot.version`) y a los cinco `apps/*/pom.xml`
(`<parent><version>`).

**Compatibilidad Java 25**: Spring Boot 4.1 soporta el rango Java 17-26; Java 25 (`CLAUDE.md`,
confirmado, no se reabre aquí) cae dentro de ese rango. No se cambia `maven.compiler.release`
(ya fijado en `25` desde el scaffolding inicial).

**Revisión de breaking changes de Spring Boot 4** (RN/AC no aplica; revisión técnica de scaffolding):
ninguno de los tres cambios conocidos (Jackson 3 como mínimo, JUnit 4 retirado del starter de test,
Undertow retirado como servidor embebido soportado) requiere ajuste — el scaffolding ya no fijaba
ninguna versión propia de Jackson, los cinco módulos de test ya usaban JUnit 5/Jupiter
(`org.junit.jupiter.api.Test`), y ningún módulo declaraba `spring-boot-starter-undertow` (todos
usan Tomcat, el valor por defecto de `spring-boot-starter-web`). Verificado por revisión estática
(`grep` sobre los seis `pom.xml` y los cinco `*ApplicationTests.java`), sin ejecutar `mvn`.

### Alternativas descartadas
- Gradle como gestor de build: descartado; `CLAUDE.md` ya fija Maven en la sección "Technology",
  no hay motivo para introducir un segundo gestor de build.
- Incluir `spring-boot-starter-security` en todos los servicios "por si acaso": descartado;
  contradice ADR-007/ADR-008 (el Gateway es el único componente que valida JWT).
- Fijar `4.0.8` en vez de `4.1.1`: descartado; `4.1.x` es la línea minor estable más reciente y no
  hay motivo documentado para preferir la línea anterior.

### Consecuencias
- `docs/architecture/tech-stack.md` actualiza su fila de "Framework backend" y su TODO para
  reflejar la versión corregida (`4.1.1`) y la fecha de publicación verificada.
- `.claude/rules/java-spring.md` referencia esta corrección; debe actualizarse en una ronda
  posterior para dejar de tratar la sección "Spring Boot 4.1" como pendiente.
- Ningún comando de build, Docker o Terraform se ejecutó como parte de este scaffolding ni de esta
  corrección (`.claude/rules/infra.md`); no se verificó compilación real, solo revisión estática de
  dependencias.

### Trazabilidad
ADR-001, ADR-006, ADR-007, ADR-008; DEC-008; `docs/architecture/tech-stack.md`,
`docs/domain/bounded-contexts.md`, `docs/infrastructure/docker-strategy.md`,
`.claude/rules/java-spring.md`.

## DEC-012 — Vertical slice habilitador: creación de incidente vía entrada técnica REST

**Fecha:** 2026-08-29
**Estado:** Aprobada

### Contexto
El equipo definió el vertical slice oficial del MVP para demostrar el backbone
REST→gRPC→PostgreSQL→Transactional Outbox→evento→consumidor (ADR-003, ADR-005, ADR-006, ADR-008,
ADR-009). CU-003 "Crear incidente automático" (RF-005) tiene actor Sistema, disparado por el
evento `TelemetryThresholdBreached`; Telemetry Service y Asset Service no existen todavía
(`docs/planning/roadmap.md`: Sprint 3, 4 y 5). Se requiere una entrada de datos que no dependa de
esos servicios para no bloquear la demostración del backbone.

### Decisión
- El slice se implementa como demostración técnica del backbone de RF-005/CU-003 (parcial), con
  un endpoint REST explícitamente técnico/interno que recibe datos de anomalía ya clasificados
  (impacto y urgencia, no telemetría cruda), sin JWT (ADR-007 sigue pendiente de Sprint 4).
- Se adelanta parte del alcance de Sprint 5 (Incident Service, RabbitMQ, Transactional Outbox)
  como **excepción controlada y documentada** de `docs/planning/roadmap.md`, no como
  reordenamiento silencioso del roadmap; el roadmap no se reescribe.
- Notification Service obtiene esquema propio `notification` en PostgreSQL (ADR-006), mínimo
  para idempotencia y registro de procesamiento del evento — sin envío real de notificación
  (Mailpit queda fuera de este slice).
- RN-004 (no duplicar incidentes equivalentes mientras exista uno abierto) se expone como HTTP
  409 Conflict en el borde REST y como `ALREADY_EXISTS` (código gRPC 6) en el contrato interno —
  mapeo estándar REST↔gRPC para conflicto de estado.
- Formato del evento `IncidentCreated`: JSON simple versionado (campo `eventVersion`), sin Avro,
  Protobuf de eventos ni schema registry.
- Librería gRPC: Spring gRPC (`org.springframework.grpc:spring-grpc-spring-boot-starter`,
  compatible con Spring Boot 4.1.x según su documentación oficial, verificado antes de esta
  decisión).
- Herramienta de migración: Flyway, con `spring-boot-starter-flyway` +
  `flyway-database-postgresql` (estructura modular de auto-configuración de Spring Boot 4,
  verificado antes de esta decisión), por servicio, por esquema lógico (ADR-006).

### Alternativas descartadas
- Esperar a que Telemetry Service y Asset Service existan antes de cualquier slice de Incident
  Service: descartado; bloquearía la demostración del backbone asíncrono hasta completar el
  Sprint 5 entero.
- Omitir esquema propio de Notification Service y usar solo memoria o logs: descartado; se
  necesita persistencia real para probar idempotencia ante redelivery (`.claude/rules/testing.md`).
- `grpc-java` crudo en vez de Spring gRPC: descartado sin evidencia de incompatibilidad real con
  Spring Boot 4.1.1; Spring gRPC es la opción con auto-configuración nativa de la versión ya
  fijada (DEC-011) y compatibilidad documentada oficialmente.
- `flyway-core` sin el starter modular: descartado; Spring Boot 4 ya no autoconfigura Flyway solo
  con `flyway-core` en el classpath, requiere `spring-boot-starter-flyway`.

### Consecuencias
- `docs/planning/roadmap.md` no se reescribe; esta excepción queda documentada aquí, no como
  reordenamiento del Sprint 5.
- `docs/domain/bounded-contexts.md` deja de listar el esquema de Notification Service como
  pendiente una vez implementado.
- `docs/domain/commands-events.md` deja de marcar el formato físico de `IncidentCreated` como
  TODO una vez implementado.
- `docs/architecture/tech-stack.md` debe reflejar Flyway y Spring gRPC como herramientas ya
  seleccionadas para este slice, en una ronda de documentación posterior a la implementación.
- No se crea ningún recurso Azure ni se modifica `.claude/rules/infra.md`.

### Trazabilidad
RF-005; CU-003 (parcial); RN-004, RN-010, RN-011, RN-012; ADR-003, ADR-005, ADR-006, ADR-008,
ADR-009; `docs/planning/roadmap.md` (Sprint 5); `docs/domain/bounded-contexts.md`.

## DEC-013 — Ciclo de vida mínimo de Incident: CREATED -> CLOSED

**Fecha:** 2026-08-31
**Estado:** Aprobada

### Contexto
El slice de creación de incidentes (DEC-012) dejó `IncidentStatus` con un único valor, `CREATED`,
y una constraint única global (`ux_incident_open`) que impedía reutilizar la combinación
asset/sensor/anomaly_type incluso después de que el incidente correspondiente dejara de estar
abierto. RN-004 exige unicidad solo "mientras exista uno abierto"; RN-007 y RN-019 confirman que
existe un cierre técnico (`IncidentClosed`, único evento de cierre en el MVP, ver
`docs/domain/commands-events.md`), pero el mecanismo de autorización y el caso de uso completo de
cierre (Técnico de mantenimiento, causa, comentario de resolución) no forman parte de este slice.

### Decisión
- `IncidentStatus` se amplía a `CREATED` y `CLOSED`, con una única transición soportada,
  `CREATED -> CLOSED` (`Incident.close()`), sin introducir `ACKNOWLEDGED` ni `ESCALATED` como
  estados: quedan como eventos futuros hasta que existan operaciones y guards propios.
- La constraint única global `ux_incident_open` se reemplaza por un índice único parcial
  `(asset_id, sensor_id, anomaly_type) WHERE status = 'CREATED'` (migración
  `V2__restrict_unique_open_incident.sql`), preservando la garantía de RN-004 sin bloquear la
  reutilización de la misma combinación una vez cerrado el incidente anterior.
- Se agrega un mecanismo mínimo de cierre en `application`/`infrastructure`
  (`CloseIncidentCommand`, `CloseIncidentService`) para poder ejercer y probar la transición.
  **No** se expone por gRPC nuevo RPC, REST ni Gateway, y **no** se resuelve autorización
  (RN-019) en este slice.
- El enum `IncidentStatus` del contrato gRPC (`contracts/grpc/incident_service.proto`) gana
  `CLOSED = 2`, conservando `INCIDENT_STATUS_UNSPECIFIED = 0` y `CREATED = 1` sin cambios. Se
  registra como DEC y no como ADR: es una decisión operativa dentro de la arquitectura ya
  decidida (ADR-003), no una decisión arquitectónica nueva. Agregar un valor de enum sin alterar
  nombres ni números existentes es un cambio compatible según ADR-003 ("cambios incompatibles...
  requieren versionado explícito"); no se abre paquete `v2`.

### Opciones consideradas
- Mantener `IncidentStatus` en un único valor hasta abrir un contrato gRPC v2: descartado:
  posponía indefinidamente una capacidad ya requerida por RN-004/RN-007 sin necesidad, dado que
  agregar el valor de enum es compatible.
- Cambiar dominio + persistencia + contrato gRPC + Gateway + autorización en una sola iteración:
  descartado por alcance; RN-019 (autorización de cierre) no está resuelto todavía y mezclarlo
  aquí habría ampliado el slice más allá de lo aprobado.

### Consecuencias
- `findOpenIncidentId` pasa a filtrar explícitamente por `status = CREATED`.
- Un incidente `CLOSED` no bloquea la creación de uno nuevo para la misma combinación
  asset/sensor/anomaly_type.
- La migración `V1` no se modifica; el cambio se aplica exclusivamente vía `V2`.

### Trazabilidad
RN-004, RN-007, RN-019; ADR-003; `docs/domain/commands-events.md`; DEC-012.

## DEC-014 — Alcance pospuesto: exposición de cierre de incidente (`CloseIncident`) por REST/gRPC

**Fecha:** 2026-08-31
**Estado:** Aprobada (decisión de alcance/planificación, no de mecanismo técnico)

### Contexto
DEC-013 dejó el ciclo de vida `CREATED -> CLOSED` implementado y validado en dominio y
persistencia de Incident Service, sin exponerlo por Gateway/REST ni por un RPC gRPC dedicado
(fuera de alcance explícito de esa decisión). Un análisis posterior (sin cambios de código)
evaluó cómo exponer el cierre por REST y encontró que la autorización de RN-019 (solo el Técnico
de mantenimiento puede cerrar) depende de JWT/RBAC real en el Gateway (ADR-007), que
`.claude/rules/security.md` confirma **no implementado todavía** (Gateway acepta toda petición;
seguridad real está planificada para Sprint 4, `docs/planning/roadmap.md`). El mecanismo de
propagación de identidad del Gateway a los servicios internos queda explícitamente abierto en
ADR-007 ("vía metadata gRPC... o revalidan el token según se defina en implementación").

### Decisión
- Se pospone la implementación del RPC `CloseIncident` y del endpoint REST correspondiente hasta
  Sprint 4, cuando exista JWT/RBAC real en el Gateway. No se implementa en esta iteración.
- El payload HTTP del cierre **no** aceptará `actorId` ni `role` como campos de entrada: la
  identidad del actor debe derivarse únicamente del JWT validado en el Gateway, nunca de un dato
  autodeclarado por el llamador.
- Se confirma la separación de responsabilidades para cuando se implemente:
  - el Gateway autentica el JWT, extrae identidad/rol y aplica el rechazo 403 (ADR-007, ADR-008);
  - Incident Service recibe la identidad mediante el mecanismo interno que se apruebe en su
    momento, sin parsear JWT ni headers crudos (`.claude/rules/security.md`);
  - `domain`/`application` de Incident Service no dependen de JWT, Spring Security ni headers
    HTTP; el dominio valida únicamente la transición `CREATED -> CLOSED` (ya implementada en
    DEC-013), no la autorización del actor.
- **No se fija todavía** el mecanismo de propagación de identidad (metadata gRPC vs. revalidación
  de token): sigue siendo el punto abierto de ADR-007. La propuesta de usar metadata gRPC hecha en
  el análisis previo queda registrada como propuesta a confirmar junto con la implementación real
  de seguridad en Sprint 4, no como decisión tomada.

### Consecuencias
- El RPC `CloseIncident`, sus mensajes en `contracts/grpc/incident_service.proto`, el endpoint
  REST del Gateway y los mapeos de estado HTTP/gRPC asociados quedan fuera del alcance hasta
  Sprint 4.
- Cuando se implemente JWT/RBAC (Sprint 4), debe resolverse antes de codificar `CloseIncident`:
  el mecanismo de propagación de identidad Gateway → Incident Service (actualización de ADR-007,
  no un ADR nuevo, según el criterio ya usado en sus propias secciones "Actualización posterior").
- No se crea código ni se modifica ningún `.proto` como parte de este DEC.

### Trazabilidad
RN-019, RN-007; ADR-007, ADR-008; DEC-013; `docs/planning/roadmap.md` (Sprint 4);
`.claude/rules/security.md`.

## DEC-015 — Ciclo de vida ampliado del incidente: CREATED → ACKNOWLEDGED → ESCALATED → CLOSED
**Fecha:** 2026-09-30
**Estado:** Aprobada
### Contexto
DEC-013 dejó solo `CREATED` y `CLOSED`; `docs/domain/state-machines.md` no formaliza la máquina del
incidente. CU-004 (reconocer) y CU-005 (escalar) exigen estados y guards para implementarse
(`docs/specs/SPEC-007-incident-service.md`).
### Decisión
- Estados: `CREATED`, `ACKNOWLEDGED`, `ESCALATED`, `CLOSED`. "Abierto" (RN-004) es cualquier estado
  distinto de `CLOSED`.
- `acknowledge`: una sola vez, sobre un incidente abierto; detiene el reloj de reconocimiento
  (RN-006). Si ya estaba `ESCALATED`, conserva ese estado.
- `escalate`: desde cualquier estado abierto, repetible, con motivo obligatorio; nunca automático
  (CU-005).
- `close`: desde cualquier estado abierto, con causa y comentario (RN-007) y rol Técnico de
  mantenimiento (RN-019). Cerrar sin reconocimiento previo no está prohibido; queda fuera del
  cálculo de MTTA.
- Un evento de persistencia (RN-005) actualiza el incidente abierto (contador y última
  ocurrencia); la urgencia se recalcula sin desescalar y todo recálculo de prioridad se audita
  (RN-014).
- El enum gRPC `IncidentStatus` gana `ACKNOWLEDGED = 3` y `ESCALATED = 4` (cambio compatible,
  ADR-003).
### Alternativas descartadas
- Mantener `ACKNOWLEDGED`/`ESCALATED` solo como eventos: no permite consultar el estado actual ni
  aplicar guards.
### Consecuencias
- El índice único de incidentes abiertos pasa a cubrir todo estado distinto de `CLOSED`.
- `state-machines.md` formaliza la máquina del incidente en el cierre documental.
### Trazabilidad
RF-005 a RF-007; CU-004 a CU-006; RN-004 a RN-007, RN-014, RN-019; DEC-013; ADR-003.

## DEC-016 — Transporte y productores de eventos de telemetría y conectividad
**Fecha:** 2026-09-30
**Estado:** Aprobada
### Contexto
`docs/architecture/data-flow.md` y `container-diagram.md` indican "gRPC, si aplica" para
`TelemetryThresholdBreached`, mientras `event-flow.md` y ADR-009 lo tratan como evento por
RabbitMQ. `bounded-contexts.md` asigna `SensorConnectivityLost` a Asset; `event-flow.md` y
`container-diagram.md` a Telemetry.
### Decisión
- `TelemetryThresholdBreached` viaja por RabbitMQ vía Outbox desde Telemetry Service hacia Incident
  Service (ADR-004, ADR-009). No hay llamada gRPC Telemetry → Incident.
- `SensorConnectivityLost` lo produce Telemetry Service (es quien conoce la última lectura). Sigue
  sin crear incidente (RN-020).
- `TelemetryReceived` no se publica en RabbitMQ: su único consumidor es el propio Telemetry
  Service; se materializa como lectura persistida y contador Micrometer.
### Consecuencias
- Se corrigen `data-flow.md`, `container-diagram.md` y `bounded-contexts.md`.
- Incident Service crea y actualiza incidentes desde un consumidor idempotente.
### Trazabilidad
RF-003, RF-004, RF-005, RF-017; CU-002, CU-003, CU-022; RN-020; ADR-004, ADR-005, ADR-009.

## DEC-017 — Contexto de evaluación de telemetría y entrada de CU-015
**Fecha:** 2026-09-30
**Estado:** Aprobada
### Contexto
Telemetry necesita perfil operativo, estado del sensor y criticidad del activo (datos de Asset)
para evaluar lecturas. DEC-003 describe CU-015 como "REST interno protegido", pero la regla de
arquitectura reserva el REST de negocio al Gateway.
### Decisión
- Telemetry obtiene el contexto por gRPC a Asset (`GetSensorEvaluationContext`), con caché local
  acotada (TTL y tamaño configurables) e invalidación por eventos de Asset. Consistencia eventual,
  sin tablas ni FK entre esquemas (ADR-006).
- CU-015 se expone como ruta REST del Gateway restringida a Administrador de plataforma, que invoca
  el mismo RPC de ingesta de Telemetry (origen `TEST_INJECTION`), garantizando RN-015. Precisa
  DEC-003: sigue siendo REST protegido, no público, pero atraviesa el Gateway.
### Consecuencias
- Si Asset no está disponible, solo fallan lotes de sensores no cacheados.
- `container-diagram.md` refleja Gateway → Telemetry.
### Trazabilidad
RF-004, RF-014; CU-002, CU-015; RN-001, RN-015; DEC-003; ADR-003, ADR-006, ADR-008.

## DEC-018 — Auditoría por eventos, destinatarios y parámetros de demostración
**Fecha:** 2026-09-30
**Estado:** Aprobada (con valores placeholder académico, pendiente de confirmación del PO)
### Decisión
- **Audit Log**: recibe transiciones de otros servicios consumiendo eventos de dominio desde
  RabbitMQ hacia el esquema `auditlog`; Incident y su módulo Identity escriben in-process en la
  misma transacción (RN-008).
- **Destinatarios de notificación** (no existe matriz confirmada): placeholder académico —
  `IncidentCreated` notifica a usuarios con rol Supervisor de operaciones; `IncidentEscalated` al
  Técnico de mantenimiento. Incident Service resuelve los correos desde Identity & Access.
- **Valores de demostración** (bandas de magnitud, ventana de persistencia, intervalo esperado de
  lectura, validez de calibración, periodicidad de la tarea de vencimiento, SLA de demo): se
  configuran por perfil o propiedad; los valores de seed son placeholder académico, no requisitos
  de negocio confirmados.
- **Consultas de incidentes** (listado y detalle): se tratan como soporte de CU-004, CU-005, CU-006
  y del tablero (HU-014); no crean un CU nuevo.
- **Retención de lecturas**: sin purga en el MVP local; índices y paginación obligatorios.
### Consecuencias
- Ninguno de estos valores se presenta como confirmado en documentación ni pruebas.
### Trazabilidad
RF-008, RF-009, RF-018; CU-003, CU-005, CU-009; RN-008, RN-011, RN-018, RN-020;
`docs/quality/sla-kpi.md`; DEC-005, DEC-008.

## DEC-019 — Topología local de observabilidad
**Fecha:** 2026-09-30
**Estado:** Aprobada
### Decisión
- Trazas: OpenTelemetry Collector como punto único OTLP hacia un backend local (Grafana Tempo).
- Métricas: scrape directo de Prometheus (`/actuator/prometheus` en puerto de management) y
  plugin `rabbitmq_prometheus` del broker.
- Logs: JSON a stdout, recolectados por Grafana Alloy hacia Loki.
- Grafana con datasources y dashboards provisionados como código.
### Alternativas descartadas
- Jaeger como backend de trazas; Promtail (en deprecación, a verificar al implementar).
### Consecuencias
- Cambiar el destino a Application Insights en la fase Azure solo exige reconfigurar el collector.
- Cierra el TODO de `docker-strategy.md` y `deployment-view.md`. No se crean recursos Azure.
### Implementación (SPEC-011)
- Verificado contra Spring Boot 4.1.1: `spring-boot-starter-opentelemetry` (sin el registro OTLP de
  métricas, excluido porque las métricas se raspan), observación de Spring gRPC servidor/cliente y de
  los listeners de Spring AMQP; Grafana Alloy sustituye a Promtail. Imágenes: collector 0.114.0,
  Tempo 2.6.1, Alloy 1.5.1.
- La observación de `RabbitTemplate` se deja apagada: el relay del Outbox publica desde un hilo sin
  traza y pisaría el `traceparent` guardado con el evento.
- El contador de incidentes creados se llama `coldguard.incident.opened` (el sufijo `_created` está
  reservado por el cliente de Prometheus).
### Trazabilidad
RNF-002, RNF-004, RNF-008; `docs/operations/observability-strategy.md`; HU-021.

## DEC-020 — Modelo Organización/Sede/Activo y eventos de actualización
**Fecha:** 2026-09-30
**Estado:** Aprobada
### Decisión
- RF-001 se modela como `Organization` 1—N `Site` 1—N `Asset` (unidad de frío), con atributos
  mínimos definidos en `docs/specs/SPEC-005-asset-service.md`.
- Se catalogan tres eventos nuevos (siguiente fila libre, sin renumerar): `AssetUpdated` y
  `OperationalProfileUpdated` (Asset Service; CU-013, CU-011, CU-012) y
  `UserAccessAssignmentChanged` (Incident Service, módulo Identity & Access; CU-014, RN-008).
### Consecuencias
- `domain-model.md`, `commands-events.md`, `use-cases.md` se actualizan en el cierre documental.
### Trazabilidad
RF-001, RF-010 a RF-013; CU-001, CU-011 a CU-014; RN-008; DEC-004.

## DEC-021 — Estrategia técnica de implementación local
**Fecha:** 2026-09-30
**Estado:** Aprobada
### Decisión
- **Formato y análisis estático**: Spotless con `google-java-format` en modo `check`, ligado a
  `verify` (quality gate de `CLAUDE.md`). La compatibilidad con Java 25 se verifica al implementar
  SPEC-001; si fallara, se fija otra versión del formateador, no se desactiva el gate.
- **Ramas**: una rama de feature por spec (`feat/spec-NNN-<slug>`); los commits de documentación
  siguen la convención vigente de `git-workflow.md`. Un commit por spec como mínimo, mensajes en
  inglés, sin push sin confirmación.
- **Verificaciones técnicas** marcadas "pendiente de verificación" en los specs (reintentos de
  Spring AMQP, OpenAPI, starter de OpenTelemetry, observación de Spring gRPC/AMQP, lectura del
  certificado mTLS del par, imágenes de Mailpit/RabbitMQ): se resuelven con una prueba mínima al
  inicio del spec correspondiente y el resultado se anota en este registro si cambia el diseño.
### Trazabilidad
RNF-005; `CLAUDE.md` (quality gates); `docs/specs/SPEC-001-plataforma-base.md`.

## DEC-022 — Cierre de supuestos funcionales de los specs (rol de PO delegado)
**Fecha:** 2026-09-30
**Estado:** Aprobada como placeholder académico; el Product Owner puede revisarla
### Contexto
Los specs dejaron supuestos sin respaldo documental. El equipo delegó su cierre en una decisión
tomada con criterio de viabilidad técnica. No son requisitos de negocio confirmados.
### Decisión
- **Sensor sin calibración inicial** (RN-018): el alta acepta una calibración inicial opcional. La
  tarea de vencimiento solo evalúa sensores con al menos una calibración. Volver a ACTIVO desde
  EN_MANTENIMIENTO o INACTIVO sí exige evidencia vigente.
- **"Acción equivalente documentada"** (TODO de RN-018): la tarea programada ejecuta la transición a
  EN_MANTENIMIENTO directamente, con actor de sistema `calibration-expiry-job`. El TODO deja de
  aplicar.
- **Persistencia de anomalías** (RN-005, RN-011): una condición es persistente con al menos
  `min_consecutive` lecturas elegibles fuera de rango, consecutivas, del mismo tipo y dentro de
  `window`. Una lectura elegible en rango reinicia la racha.
- **Conectividad restablecida**: sin evento nuevo; se limpia la marca y se registra log de negocio
  y métrica. La detección aplica solo a sensores ACTIVOS con al menos una lectura previa.
- **SLA de P4**: reconocimiento en 1 día (sin calendario laboral en el MVP) y sin objetivo de
  resolución, según `docs/quality/sla-kpi.md`.
- **Recálculo de prioridad por cambio de criticidad del activo** (RN-014): fuera del MVP local. Sí
  se recalcula por nuevas ocurrencias.
- **Roles**: el Operador solo lee incidentes (no hay comando ni RF para sus acciones); el Auditor
  solo consulta la bitácora (CU-009).
- **Valores de demostración (placeholder académico, configurables)**:

| Parámetro | Valor demo |
|---|---|
| Rango térmico del perfil de ejemplo | 2.0 a 8.0 °C |
| Bandas de magnitud (desviación) | media ≥ 1.0, alta ≥ 3.0, crítica ≥ 6.0 °C |
| Persistencia | 3 lecturas consecutivas en 5 minutos |
| Intervalo esperado de lectura | 5 s; tolerancia de conectividad ×3 |
| Validez de calibración por defecto | 90 días (demo de vencimiento con valor corto en el seed) |
| Tarea de vencimiento de calibración | cada 5 minutos |
| SLA de demo | tabla de `sla-kpi.md` (P1 5 min/30 min, P2 15 min/2 h, P3 1 h/8 h, P4 1 día) |
| Tamaño máximo de página / lote de ingesta | 100 / 500 |
### Consecuencias
- Estos valores no se presentan como confirmados en documentación, demo ni pruebas.
- Un cambio de PO sobre cualquiera solo afecta configuración o un DEC nuevo, no el diseño.
### Trazabilidad
RN-005, RN-011, RN-014, RN-018, RN-020; `docs/specs/SPEC-004`, `005`, `006`, `007`;
DEC-015, DEC-018.

## Referencias a decisiones registradas en otros documentos

Decisiones confirmadas posteriores al cierre de Sprint 1, documentadas en su lugar natural
(no se duplican aquí como DEC formales):

- Gestión de activos, sensores, perfiles operativos y accesos — RF-012, RF-013; CU-013, CU-014
  (`docs/domain/functional-requirements.md`, `docs/domain/use-cases.md`,
  `docs/product/stakeholders.md`).
- Origen de telemetría: sensor-simulator autónomo y endpoint interno protegido de pruebas —
  RF-014, CU-015, RN-015 (`docs/product/business-rules.md`).
- Autenticación y autorización por fases: demostración de frontend en APF1 (RN-016), backend real
  con Spring Security en APF2 — RF-015, CU-016 (`docs/product/business-rules.md`,
  `docs/product/scope-mvp.md`).
- Ciclo de vida operativo del sensor — RF-016, RN-017, RN-018 (`docs/product/business-rules.md`,
  `docs/domain/state-machines.md`).
- Unificación de responsabilidades (Administrador de plataforma) y roles del flujo operativo de
  incidentes — RN-019 (`docs/product/business-rules.md`, `docs/product/stakeholders.md`).
- **Parámetros operativos configurables sin valores globales fijos** — RN-011 (umbrales de
  urgencia), RN-018 (periodicidad de vencimiento de calibración) y RN-020 (frecuencia esperada de
  telemetría) ya documentan explícitamente que estos valores son configurables por perfil
  operativo/sensor y que ningún valor numérico global se fija sin confirmación de negocio
  (`docs/product/business-rules.md`). No se registra un DEC nuevo para esto: el repositorio ya lo
  documenta con claridad. Los valores de demostración se definirán antes de la presentación final
  y se marcarán explícitamente como supuestos académicos, no como requisitos de negocio
  confirmados.
