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
