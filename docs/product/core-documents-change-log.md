# Registro de cambios — documentos núcleo de producto

Revisión enfocada en 4 documentos: `docs/product/vision.md`, `docs/product/problem-statement.md`,
`docs/product/scope-mvp.md`, `docs/product/business-rules.md`. No se reorganizaron carpetas, no
se crearon decisiones arquitectónicas nuevas, no se agregaron datos, resultados, métricas,
fuentes ni requisitos inexistentes. Toda la trazabilidad previa hacia ADRs, RF, CU, RN y eventos
se conservó; donde se agregó una referencia nueva, es adicional, no un reemplazo.

## `docs/product/vision.md`

**Cambios realizados:**
- Reordenado a la estructura fija: propósito, problema que atiende, usuarios objetivo, propuesta
  de valor, capacidades principales, diferenciador de producto, éxito esperado del MVP,
  referencias técnicas y trazabilidad.
- La sección "Diferenciador" original (microservicios/gRPC/eventos/cloud-ready) se reclasificó
  como "Habilitadores técnicos", subordinada al diferenciador de producto real (detección
  automática + priorización objetiva + trazabilidad auditable).
- Se agregó una nota de lenguaje explícita: el documento académico fuente dice "prevenir
  incidentes y pérdidas"; este documento usa "reducir el riesgo operativo" / "respuesta más
  oportuna" porque el sistema detecta y prioriza, no elimina la posibilidad física de falla.
  "Éxito esperado del MVP" usa el mismo lenguaje conservador.
- Se agregó la sección "Capacidades principales" (lista funcional derivada de RF-001 a RF-011 y
  RN-008), ausente antes.
- Se agregó la sección "Usuarios objetivo" (antes "A quién sirve", contenido equivalente, título
  alineado al orden pedido).

**Información que se preservó:**
- La tabla completa de decisión→habilitador (ADR-001, ADR-003, ADR-004, ADR-005, ADR-009,
  RNF-002/004, RN-008, RNF-007) — sin quitar ninguna fila, solo reubicada bajo "Habilitadores
  técnicos".
- El texto íntegro de "Solución" (ahora "Propuesta de valor").
- El enlace a `docs/quality/sla-kpi.md` y su `TODO` de valores agregados no confirmados.
- El enlace a `docs/domain/domain-model.md` (proceso objetivo).

**TODOs no resueltos:**
- Valores numéricos objetivo agregados de SLA/KPI (MTTA/MTTR globales, throughput,
  disponibilidad) — pendientes de validación de negocio, ya señalado antes de esta revisión.

## `docs/product/problem-statement.md`

**Cambios realizados:**
- Reestructurado en 5 secciones explícitas: problema central, causas o modos de falla,
  consecuencias potenciales, limitación del proceso actual, respuesta propuesta.
- Se agregó una nota de "Supuesto no validado" al inicio: la afirmación de pérdida de productos
  sensibles proviene de la propuesta académica y no está respaldada por una fuente externa
  (estudio, incidente registrado, dato de negocio); se marca explícitamente como supuesto, no
  como hecho medido.
- Se agregó la sección "Limitación del proceso actual", ausente antes — marcada explícitamente
  como supuesto no confirmado (no existe una descripción documentada del proceso previo a
  ColdGuard), con un `TODO` para documentarlo si el equipo decide validarlo.
- "Por qué esto importa" se renombró a "Consecuencias potenciales" y se aclaró que son
  clasificaciones de impacto potencial (RN-010), no resultados medidos.

**Información que se preservó:**
- Los 4 modos de falla enunciados en la propuesta original, sin ampliarlos.
- La relación con RN-006, RN-010, RN-011, RN-012.
- Los 3 puntos de contacto con casos de uso (CU-002, CU-003, CU-004/CU-005) en "Respuesta
  propuesta" (antes "Cómo responde ColdGuard").

**TODOs no resueltos:**
- Documentar el proceso operativo actual (pre-ColdGuard) si el equipo decide validarlo — nuevo,
  agregado en esta revisión porque no existía antes ninguna mención de esta limitación de fuente.

## `docs/product/scope-mvp.md`

**Cambios realizados:**
- Dividido en las 7 secciones pedidas: objetivo del MVP, capacidades funcionales incluidas,
  capacidades explícitamente fuera de alcance, supuestos, restricciones, decisiones pendientes,
  dependencias técnicas y decisiones relacionadas.
- Las listas puramente técnicas (microservicios, gRPC, RabbitMQ, PostgreSQL, Docker Compose,
  Terraform, observabilidad) se movieron íntegras a "Dependencias técnicas y decisiones
  relacionadas"; ya no aparecen mezcladas con la descripción de alcance funcional.
- La tabla servicio → RF/CU (antes bajo "Servicios en alcance") se trasladó a "Dependencias
  técnicas..." y su advertencia de "inferencia razonable, no decisión cerrada" se formalizó como
  una entrada explícita en la nueva sección "Supuestos".
- Se agregó la sección "Supuestos" (ausente antes), con dos entradas: representatividad del
  simulador de telemetría frente a sensores reales, y la asignación servicio↔RF no formalizada.
- Se agregó la sección "Restricciones" (ausente antes), consolidando RNF-001, la política de no
  aplicar Terraform/crear recursos Azure sin aprobación, REST-en-el-borde/gRPC-interno (ADR-003),
  y la separación de repositorio del frontend (ADR-002).
- "Capacidades funcionales incluidas" ahora es una tabla RF↔CU explícita (antes era una mención
  indirecta dentro de "Dentro del alcance").

**Información que se preservó:**
- La separación frontend/backend (ADR-002) — mantenida y reforzada en "Capacidades
  explícitamente fuera de alcance" y en "Restricciones".
- La decisión de MVP local sin cloud aplicado (RNF-001, RNF-007, `.claude/rules/infra.md`) —
  mantenida en "Objetivo del MVP", "Restricciones" y "Dependencias técnicas...".
- Los 3 vacíos documentales ya detectados (RF-009 sin evento/prueba, CU-009 sin RF,
  `AssetRegistered`/`IncidentResolved` sin CU) — conservados íntegros dentro de "Decisiones
  pendientes".
- Las 3 decisiones pendientes ya existentes (rol Administrador, CU-010, valores SLA/KPI
  agregados).

**TODOs no resueltos:**
- Todos los de la versión anterior, sin resolver: rol Administrador, CU-010, valores SLA/KPI
  agregados, servicio dueño de RF-009/CU-009, y los 3 vacíos documentales de trazabilidad.

## `docs/product/business-rules.md`

**Cambios realizados:**
- Se agregó un glosario inicial con 9 términos (activo, sensor, lectura, anomalía, condición
  persistente, incidente abierto, impacto, urgencia, prioridad), definidos únicamente a partir de
  lo ya establecido por las reglas existentes.
- Se agregó un índice resumido de las 14 reglas (RN-001 a RN-014) con resumen de una línea y su
  sección de agrupamiento.
- RN-001, RN-002, RN-005 y RN-011 recibieron una precisión explícita de que los umbrales y
  ventanas de persistencia son **configurables** por sensor (vía RF-010/CU-011), sin definir
  ningún valor numérico.
- El agrupamiento temático en 4 secciones (introducido en una revisión anterior) se conservó sin
  cambios de fondo.

**Información que se preservó:**
- El texto y significado íntegro de las 14 reglas (RN-001 a RN-014), sin renumerar ni reescribir
  su contenido normativo.
- La matriz impacto/urgencia (RN-012) con sus valores P1–P4 exactamente iguales.
- Todas las referencias a RF (RF-010, RF-011), CU (CU-006, CU-011, CU-012) y eventos
  (`IncidentAcknowledged`, `IncidentClosed`).

**TODOs no resueltos:**
- Ninguno nuevo. No había `TODO` previos en este documento porque las 14 reglas ya estaban
  completas; esta revisión solo agregó estructura y precisión de configurabilidad.

## Ronda 2 — decisiones confirmadas (gestión de activos, origen de telemetría, autenticación)

Tres decisiones confirmadas por el equipo, sin alternativas a evaluar. Se propagaron a 13
documentos indicados como mínimo, más 3 adicionales (`docs/domain/functional-requirements.md`,
`docs/domain/commands-events.md`, `docs/README.md`) necesarios para que la trazabilidad quedara
consistente. No se reorganizaron carpetas, no se crearon ADRs nuevos, no se inventó ningún valor
numérico, resultado de desempeño, fuente externa o control no indicado por las decisiones.

### Decisiones aplicadas

1. **Gestión de activos y sensores**: registrar/consultar/actualizar activos, sensores y
   perfiles operativos (rangos térmicos, ventanas de persistencia, criticidad); cambios
   auditables; rol **Administrador de plataforma** confirmado con alcance (gestión de activos,
   sensores, perfiles y asignaciones de acceso).
2. **Origen de telemetría**: sensor-simulator autónomo como productor principal + endpoint
   interno protegido para inyección de prueba; mismo pipeline de evaluación para ambos orígenes;
   escenarios de simulador confirmados (nominal, fuera de rango, recuperación, persistencia,
   pérdida de conectividad); forma del mensaje de telemetría confirmada (identificador, sensor,
   activo, timestamp de origen, valor, unidad, datos de correlación).
3. **Autenticación y autorización por fases**: APF1 = login de demostración en frontend (sin
   seguridad real de backend); APF2 = seguridad real (Spring Security, tokens, RBAC en endpoints,
   hash de contraseñas, secretos por variables de entorno). El login de APF1 no se presenta en
   ningún documento como control de seguridad productivo.

### IDs nuevos agregados (numeración continuada, ninguno reutilizado ni renumerado)

- **RF-012** a **RF-015** en `docs/domain/functional-requirements.md`.
- **CU-013** a **CU-016** en `docs/domain/use-cases.md`.
- **RN-015** y **RN-016** en `docs/product/business-rules.md` (continúan desde RN-014).
- **HU-018** en `docs/product/product-backlog.md`, y **EPIC-09/EPIC-10/EPIC-11**.

### Documentos afectados y qué cambió en cada uno

| Documento | Qué cambió |
|---|---|
| `docs/domain/functional-requirements.md` | Agregadas RF-012 a RF-015, con su CU y RN relacionadas. |
| `docs/domain/use-cases.md` | Agregados CU-013 a CU-016; CU-002 ampliado con escenarios del simulador y TODO de "pérdida de conectividad". |
| `docs/domain/commands-events.md` | Nota de que la telemetría de prueba usa los mismos eventos que CU-002; TODO de eventos no catalogados para CU-013/CU-014/CU-016. |
| `docs/domain/traceability-matrix.md` | Agregadas filas RF-012 a RF-015 con Observación explícita donde falta evento, servicio o actor. |
| `docs/product/business-rules.md` | Agregadas RN-015 y RN-016; RN-001 ampliada con la composición confirmada del perfil operativo (rangos térmicos + criticidad); índice y numeración del encabezado actualizados a RN-001–016. |
| `docs/product/stakeholders.md` | "Administrador" → "Administrador de plataforma" con alcance confirmado; tabla actor→CU actualizada (CU-013, CU-014, CU-016); actor de CU-015 marcado como no confirmado. |
| `docs/product/scope-mvp.md` | Nueva tabla de capacidades RF-012 a RF-015; nueva sección "Fases de entrega: APF1 vs. APF2"; ítem "rol Administrador" movido de pendiente a resuelto; tabla de mapeo servicio→RF actualizada; nuevas decisiones pendientes (actor de CU-015, servicio de RF-013, eventos no catalogados). |
| `docs/product/product-backlog.md` | Agregadas EPIC-09, EPIC-10, EPIC-11; agregada HU-018 (login demo, Sprint 2); HU-004 y HU-017 anotadas con la resolución del rol Administrador (sin reescribir su estado histórico). |
| `docs/domain/domain-model.md` | Nueva sección "Capacidades de soporte" (CU-013 a CU-016); tabla de conceptos ampliada con "Lectura de telemetría" (campos confirmados) y "Asignación de acceso"; perfil operativo aclarado con rangos térmicos. |
| `docs/architecture/system-context.md` | Simulador renombrado a "sensor-simulator" con sus escenarios; nota sobre el endpoint interno de pruebas (no es un nodo de contexto nuevo); nota de que la autenticación real de borde llega en APF2. |
| `docs/architecture/container-diagram.md` | Diagrama actualizado con `Simulator` y `TestEndpoint` alimentando a `Telemetry Service`; nota de fase sobre `RF-015`/Gateway (autenticación solo en APF2). |
| `docs/planning/sprint-0.md` | Nota aclarando que estas decisiones llegaron después del cierre de Sprint 1, no de este (inexistente) Sprint 0. |
| `docs/planning/sprint-1.md` | Nueva sección "Decisiones posteriores a este cierre" (no reabre el cierre ya registrado). |
| `docs/planning/sprint-2.md` | HU-018 agregada a la tabla de historias comprometidas y a la DoD, con la aclaración de que no es un control de seguridad productivo. |
| `docs/README.md` | Rangos de ID en el índice actualizados (RF-001 a RF-015, CU-001 a CU-016, RN-001 a RN-016). |

### Información que se preservó

- Todas las reglas RN-001 a RN-014, sin cambio de numeración ni de significado.
- Todos los RF-001 a RF-011 y CU-001 a CU-012, sin cambio.
- El estado histórico "hecho" de HU-001 a HU-017 y de los cierres de Sprint 1/Sprint 2 (solo se
  anexaron notas de continuidad, no se reescribió lo ya cerrado).
- Los vacíos documentales previos que **no** se resuelven con estas decisiones (RF-009 sin
  evento/prueba propia, CU-009 sin RF, `AssetRegistered`/`IncidentResolved` sin CU, CU-010 no
  definido, valores agregados de SLA/KPI, desincronización del diagrama de contenedores con
  ADR-005).

### TODOs no resueltos (nuevos, agregados en esta ronda)

- Actor de CU-015 (inyección de telemetría de prueba) no confirmado.
- Evento de dominio para "pérdida de conectividad" del sensor: no catalogado.
- Evento de dominio para actualización de activos/sensores/perfiles (CU-013) y para asignación de
  accesos (CU-014): no catalogado.
- Servicio que atiende RF-013/CU-014 (asignaciones de acceso): no asignado explícitamente.

## Ronda 3 — decisión confirmada: ciclo de vida operativo del sensor

Una decisión confirmada, sin alternativas evaluadas: estado operativo del sensor
(ACTIVO/EN_MANTENIMIENTO/INACTIVO/RETIRADO), elegibilidad de lecturas, auditoría de cambios,
retiro lógico, regla complementaria de calibración vencida, y las 10 responsabilidades
confirmadas del Administrador de plataforma. Se propagó a los 10 documentos pedidos como mínimo,
más `docs/domain/functional-requirements.md` y `docs/domain/commands-events.md` (necesarios para
consistencia de trazabilidad).

### Conflicto de numeración detectado y resuelto

La decisión nombraba la regla nueva como **"RN-015"**. Ese ID ya estaba en uso (ronda anterior:
"la telemetría inyectada por el endpoint interno de pruebas se evalúa igual que la del
sensor-simulator"). Por la regla de no reutilizar/renumerar IDs existentes
(`.claude/rules/documentation.md`), la regla de ciclo de vida del sensor se registró como
**RN-017** (regla principal) y **RN-018** (regla complementaria de calibración) — el contenido y
significado de la decisión no cambia, solo el identificador. Se dejó una nota explícita de este
conflicto en `docs/product/business-rules.md` y en `docs/domain/traceability-matrix.md`.

### IDs nuevos agregados (numeración continuada desde el último ID libre de cada catálogo)

- **RN-017**, **RN-018** en `docs/product/business-rules.md` (continúan desde RN-016).
- **RF-016** en `docs/domain/functional-requirements.md` (continúa desde RF-015); **RF-012**
  actualizado para incluir CU-021.
- **CU-017** a **CU-021** en `docs/domain/use-cases.md` (continúan desde CU-016; CU-010 sigue
  reservado/no definido, sin usarse).
- **EPIC-12**, **HU-019**, **HU-020** en `docs/product/product-backlog.md`.

### Documentos afectados y qué cambió en cada uno

| Documento | Qué cambió |
|---|---|
| `docs/product/business-rules.md` | Agregadas RN-017 y RN-018 (sección nueva "Ciclo de vida operativo del sensor"); índice actualizado; glosario ampliado con 4 términos (estado operativo del sensor, calibración o verificación, retiro lógico, lectura elegible); RN-012 (matriz impacto/urgencia) no se tocó. |
| `docs/domain/functional-requirements.md` | Agregado RF-016; RF-012 actualizado para incluir CU-021 y RN-008. |
| `docs/domain/use-cases.md` | Agregados CU-017 a CU-021, cada uno con actor principal, precondiciones, flujo principal, reglas aplicables, postcondiciones y excepciones; CU-013 actualizado con referencia cruzada a los nuevos CU especializados. |
| `docs/product/stakeholders.md` | Nueva sección "Perfil: Administrador de plataforma" con las 10 responsabilidades confirmadas, su trazabilidad a CU, y permisos/restricciones explícitos frente a los demás roles; tabla actor→CU actualizada. |
| `docs/domain/domain-model.md` | Agregados los conceptos `SensorStatus`, `SensorAssignmentHistory`, `CalibrationOrVerificationRecord`, `SensorLifecycleAudit`, con sus relaciones e invariantes (incluyendo que el retiro es lógico y no elimina historial); tabla de capacidades de soporte y TODOs ampliados. |
| `docs/domain/state-machines.md` | Agregada la máquina de estados completa de Sensor (diagrama, tabla de transiciones/precondiciones, efecto sobre elegibilidad de lecturas); la máquina de estados del Incidente (ya existente) no se modificó, solo se le dio su propio encabezado. |
| `docs/domain/traceability-matrix.md` | Agregada fila RF-016; fila RF-012 actualizada con CU-021; nota explícita del conflicto de numeración resuelto. |
| `docs/product/product-backlog.md` | Agregada EPIC-12 y las historias HU-019, HU-020, con criterios de aceptación verificables y exclusiones IoT explícitas; alcance limitado a Sprint 3+ (fuera de APF1), consistente con el resto de EPIC-09/10/11. |
| `docs/product/scope-mvp.md` | Agregadas RF-016 y la consulta de historial (RF-012/CU-021) a "Capacidades funcionales incluidas"; agregada la lista de 6 exclusiones IoT explícitas; tabla de mapeo servicio→RF actualizada (Asset Service); nuevas decisiones pendientes (reasignación en estado no-ACTIVO, calibración automática vs. manual, criterio de vencimiento). |
| `docs/architecture/system-context.md` | Nota agregada: solo telemetría de sensores ACTIVO es elegible para evaluación; no cambia qué produce el simulador ni la comunicación de contexto. |
| `docs/architecture/container-diagram.md` | Nota agregada sobre `Telemetry Service` aplicando la elegibilidad de lecturas (RN-017/RN-018); no se agregó ningún nodo, conexión, ni se alteró ninguna decisión de mensajería o despliegue existente. |
| `docs/domain/commands-events.md` | TODO ampliado para cubrir los eventos no catalogados de CU-017 a CU-020. |

### Información que se preservó

- RN-001 a RN-016, sin cambio de numeración ni de significado.
- La matriz impacto/urgencia de RN-012, exactamente igual.
- RF-001 a RF-015 y CU-001 a CU-016, sin cambio.
- La máquina de estados del Incidente ya documentada (sin modificar, solo con encabezado propio
  para dar lugar a la de Sensor).
- Todo el historial de rondas anteriores en este mismo change-log.

### TODOs no resueltos (nuevos, agregados en esta ronda)

- Criterio de vencimiento de calibración/verificación (periodicidad, unidad de tiempo): no
  definido; no se fija ningún valor numérico.
- Si la transición INACTIVO → ACTIVO exige también calibración/verificación válida (como sí lo
  exige explícitamente EN_MANTENIMIENTO → ACTIVO).
- Si un sensor puede reasignarse (CU-018) estando en un estado distinto de ACTIVO.
- Si registrar una calibración válida (CU-019) cambia automáticamente el estado a ACTIVO, o si el
  cambio de estado es siempre un paso explícito adicional (CU-017).
- Si la verificación de vencimiento de calibración es un chequeo automático del sistema o depende
  de revisión manual periódica del Administrador de plataforma.
- Eventos de dominio no catalogados para CU-017 a CU-020 (cambio de estado, calibración,
  reasignación, retiro) y para "pérdida de conectividad" (CU-002, ya señalado en ronda anterior).
- Servicio que atiende RF-013/CU-014 (asignaciones de acceso): sigue sin asignar (ya señalado en
  ronda anterior, no forma parte de esta decisión).

## Ronda 4 — unificación de responsabilidades y cierre del ciclo de vida del sensor

Dos decisiones confirmadas: (1) unificar bajo Administrador de plataforma las responsabilidades
de registro/configuración de activos, sensores y perfiles, y mover CU-006 a Técnico de
mantenimiento; (2) cerrar las ambigüedades de RN-017/RN-018 dejadas como `TODO` en la ronda
anterior (INACTIVO→ACTIVO, reasignación, no reactivación automática, detección automática de
vencimiento). Se tocaron los 12 documentos pedidos; ninguno adicional fue necesario esta vez.

### Auditoría de RN-015 a RN-018 (requerida explícitamente en esta ronda)

Se revisaron los cuatro IDs uno por uno: RN-015 (telemetría de prueba), RN-016 (login APF1),
RN-017 (estado y trazabilidad del sensor), RN-018 (calibración). **No se encontró ninguna
colisión entre ellos** — cada uno tiene contenido y origen distintos, y ya estaban correctamente
diferenciados desde la Ronda 3. El único conflicto de numeración real fue el ya documentado en la
Ronda 3 (la decisión de ciclo de vida del sensor nombraba su regla "RN-015", que ya estaba en uso;
se resolvió entonces con RN-017/RN-018). Esta ronda no renumeró ningún ID adicional.

### Decisión de no crear un CU para la detección automática de vencimiento

La decisión permitía crear un CU nuevo (actor Sistema) "solamente si es indispensable" y exigía
que, de crearse, tuviera "RF, regla, eventos y trazabilidad consistentes". No fue posible asignarle
un evento de dominio sin inventar un nombre no confirmado (violaría la regla de no inventar
controles/identificadores no indicados). Siguiendo la alternativa que la propia decisión ofrecía,
**no se creó el CU**: el comportamiento (tarea programada, actor Sistema, evalúa vigencia,
transiciona a EN_MANTENIMIENTO o genera una acción equivalente documentada) se documentó como
parte de CU-017 y RN-018.

### Tabla de reglas y casos de uso modificados

| ID | Cambio |
|---|---|
| RN-017 | Se agregó la precondición de que la reasignación (CU-018) solo es válida en EN_MANTENIMIENTO. Significado original sin alterar. |
| RN-018 | Se cerraron 3 ambigüedades: (a) INACTIVO→ACTIVO exige calibración vigente no vencida, sin registro nuevo; (b) registrar una calibración válida no reactiva automáticamente; (c) la detección de vencimiento puede ser manual o automática (tarea programada, actor Sistema, periodicidad configurable). Significado original sin alterar. |
| CU-001 | Actor: Supervisor de operaciones → Administrador de plataforma. |
| CU-006 | Actor: Operador (único) → Técnico de mantenimiento (principal) + Operador (secundario, aporta contexto, no cierra). |
| CU-007 | Actor: Supervisor de operaciones → Administrador de plataforma. |
| CU-011 | Actor: Supervisor de operaciones → Administrador de plataforma. |
| CU-012 | Actor: Supervisor de operaciones → Administrador de plataforma. |
| CU-013 | Actor principal/secundario invertidos: ahora Administrador de plataforma (principal) / Supervisor de operaciones (secundario). |
| CU-017 | Precondiciones y excepciones ampliadas con la regla de evidencia vigente por origen (EN_MANTENIMIENTO vs. INACTIVO) y el disparo alternativo por tarea programada. |
| CU-018 | Se agregó la precondición obligatoria de estado EN_MANTENIMIENTO. |
| CU-019 | Se confirmó explícitamente que el registro no reactiva automáticamente el sensor. |
| CU-020 | Aclaración menor: el retiro no está restringido a un estado de origen específico. |
| CU-021 | Aclaración menor: el historial incluye las transiciones ejecutadas por la tarea programada. |

### Documentos afectados y qué cambió en cada uno

| Documento | Qué cambió |
|---|---|
| `docs/domain/use-cases.md` | Cambios de actor en CU-001, CU-006, CU-007, CU-011, CU-012, CU-013; CU-017/CU-018/CU-019 actualizados con las reglas cerradas; CU-020/CU-021 con aclaraciones menores. |
| `docs/product/business-rules.md` | RN-017 con nueva precondición de reasignación; RN-018 reescrita para cerrar las 3 ambigüedades, con 2 `TODO` nuevos más acotados (periodicidad configurable, "acción equivalente documentada"). |
| `docs/product/stakeholders.md` | Nueva sección "Perfil: Supervisor de operaciones" (funciones retenidas, con nota de interpretación marcada para validación humana); tabla de responsabilidades y tabla actor→CU actualizadas; restricciones reescritas para reflejar que Administrador de plataforma ahora sí registra activos/sensores/perfiles. |
| `docs/domain/domain-model.md` | Invariantes de `SensorAssignmentHistory` y `CalibrationOrVerificationRecord` actualizados con las reglas cerradas; TODOs resueltos removidos, 2 nuevos más acotados agregados. |
| `docs/domain/state-machines.md` | Diagrama y tabla de transiciones actualizados (INACTIVO→ACTIVO, EN_MANTENIMIENTO→ACTIVO diferenciados; nota de que la reasignación no es una transición de estado); TODOs resueltos removidos. |
| `docs/domain/commands-events.md` | Nota sobre el actor de CU-006; nota explícita de por qué no se cataloga un evento para la detección automática de vencimiento. |
| `docs/domain/traceability-matrix.md` | Observaciones actualizadas en las filas de RF-001, RF-002, RF-006, RF-010, RF-011, RF-012, RF-016 con los cambios de actor y el resultado de la auditoría de RN-015 a RN-018. |
| `docs/quality/acceptance-criteria.md` | Primer contenido real del documento: criterios de aceptación verificables para RF-016/CU-017 a CU-021, derivados de RN-017/RN-018. |
| `docs/product/product-backlog.md` | HU-013 corregida (actor); HU-019 actualizada con las reglas cerradas (EN_MANTENIMIENTO obligatorio para reasignar, no reactivación automática, INACTIVO sin calibración nueva). |
| `docs/product/scope-mvp.md` | 3 TODOs de "Decisiones pendientes" resueltos y removidos; 2 nuevos más acotados agregados (periodicidad de vencimiento, "acción equivalente documentada"). |
| `docs/architecture/system-context.md` | Aclaración de que el nodo genérico "Operador/Supervisor" también representa a Técnico de mantenimiento, Auditor y Administrador de plataforma a nivel de contexto; no se redibujó el diagrama. |

### Información que se preservó

- RN-001 a RN-016, sin cambio de numeración ni de significado; RN-017/RN-018 se cerraron
  (agregaron precisión), no se reescribieron desde cero.
- Todos los RF y CU no mencionados en la tabla de arriba, sin cambio.
- CU-004, CU-005, CU-008, CU-009: no se tocaron — la decisión no pedía cambiarlos, y la mención de
  "reconocimiento, escalamiento o decisión operativa según los CU existentes" se interpretó como
  una reafirmación, no como una reasignación de actor (ver nota de interpretación en
  `docs/product/stakeholders.md`, marcada para validación humana).
- El historial completo de Rondas 1 a 3 en este mismo change-log.

### TODOs no resueltos (nuevos o reformulados en esta ronda)

- Periodicidad o criterio exacto de vencimiento de calibración/verificación: debe ser
  configurable; no se fija ningún valor numérico.
- Qué constituye una "acción equivalente documentada" cuando la tarea programada no ejecuta la
  transición a EN_MANTENIMIENTO directamente.
- Los TODOs de rondas anteriores no afectados por esta decisión siguen abiertos (actor de CU-015,
  eventos de dominio para CU-013/CU-014/CU-017 a CU-020, servicio dueño de RF-013).

### Requiere validación humana (no se asumió, no se propuso variante)

- **Interpretación de "reconocimiento, escalamiento o decisión operativa según los CU existentes"**
  para el Supervisor de operaciones: se interpretó como "sin cambio de actor en CU-004/CU-005".
  Si la intención era otra (agregar al Supervisor como actor de esos CU), requiere confirmación
  explícita del equipo — no se implementó una alternativa no solicitada.
- **Decisión de no crear un CU para la detección automática de vencimiento**: se tomó siguiendo la
  alternativa que la propia decisión ofrecía (documentar como parte de CU-017/RN-018) por no poder
  nombrar un evento sin inventarlo. Si el equipo confirma un nombre de evento, corresponde crear
  el CU en una revisión posterior.

## Ronda 5 — roles y flujo operativo de incidentes

Decisión confirmada que redefine los actores de CU-004, CU-005 y CU-006, y resuelve la nota de
interpretación que había quedado abierta en la Ronda 4 sobre el rol del Supervisor de operaciones
en el flujo de incidentes. No se agregaron roles nuevos; no se declaró escalamiento automático
por SLA; no se inventaron acciones de contención específicas, métricas ni integraciones.

### Resolución de la nota de interpretación pendiente (Ronda 4)

La Ronda 4 dejó marcado como "requiere validación humana" si el Supervisor de operaciones debía
ser actor formal de CU-004/CU-005. Esta decisión lo confirma explícitamente: **sí**, es actor
principal de ambos. Se actualizó `docs/product/stakeholders.md` para reflejarlo y se retiró la
nota de interpretación (ya resuelta, no queda como ambigüedad abierta).

### Nueva regla

**RN-019** (continúa la numeración desde RN-018): solo el Técnico de mantenimiento puede cerrar
técnicamente un incidente; ningún otro rol humano ejecuta el cierre ni modifica evidencia técnica
de resolución. Complementaria a RN-007 (que exige el contenido del cierre, no quién lo ejecuta).
Trazabilidad: CU-006, RF-006.

### Tabla: caso de uso, actor principal, actor secundario, regla aplicable

| Caso de uso | Actor principal | Actor secundario | Regla aplicable |
|---|---|---|---|
| CU-003 Crear incidente automático | Sistema | — | RN-003, RN-004, RN-005, RN-012 |
| CU-004 Reconocer y coordinar atención de incidente (antes "Atender incidente") | Supervisor de operaciones | Operador | RN-006 |
| CU-005 Escalar incidente | Supervisor de operaciones | Sistema (registra transición, notifica; no autónomo) | RN-012, RN-014 |
| CU-006 Cerrar incidente | Técnico de mantenimiento | Supervisor de operaciones (informado); Operador aporta contexto (no es actor formal) | RN-007, RN-019 |

### Documentos afectados y qué cambió en cada uno

| Documento | Qué cambió |
|---|---|
| `docs/domain/use-cases.md` | CU-003 precisa notificación sin inventar matriz; CU-004 renombrado y con nuevo actor (Supervisor principal, Operador secundario, Técnico ya no es actor de este CU); CU-005 con nuevo actor (Supervisor principal, Sistema secundario, explícitamente no autónomo, sin SLA automático); CU-006 con actor secundario precisado (Supervisor informado, Operador contribuye sin ser actor formal). |
| `docs/product/business-rules.md` | RN-006 y RN-007 actualizadas mínimamente con el actor confirmado (sin alterar su contenido normativo); agregada RN-019 (nueva) con su entrada en el índice. |
| `docs/product/stakeholders.md` | Resuelta la nota de interpretación pendiente; reescrito el perfil de Supervisor de operaciones (responsabilidades y restricciones confirmadas); agregados los perfiles de Operador y Técnico de mantenimiento (nuevo, mismo patrón que los perfiles existentes); agregado perfil de Auditor reafirmando modo consulta; restricción del Administrador de plataforma ampliada para cubrir CU-004/CU-005/CU-006; tabla actor→CU reescrita. |
| `docs/domain/traceability-matrix.md` | Filas RF-005, RF-006, RF-007, RF-008 actualizadas: RN-019 agregada a RF-006; nota explícita de que RF-007 no implica escalamiento automático; nota de que RF-005/RF-008 no tienen una matriz de notificación confirmada. |
| `docs/product/product-backlog.md` | HU-014 corregida (actor: Operador → Supervisor de operaciones), con criterio de aceptación ampliado para distinguir reconocer/escalar (Supervisor) de cerrar (Técnico). |
| `docs/quality/acceptance-criteria.md` | Nueva sección "RF-006 — Flujo operativo de incidentes: roles confirmados", con criterios verificables para CU-004/CU-005/CU-006 y la separación de responsabilidades de Administrador de plataforma y Auditor. |
| `docs/operations/incident-model.md` | Primer contenido real del documento (antes solo `TODO`): tabla "Quién hace qué" con el responsable de cada paso operativo, derivada de RN-006/RN-007/RN-019 y CU-003 a CU-006. |
| `docs/domain/domain-model.md` | Fila de la tabla "Proceso objetivo" (paso 7) actualizada con los actores y RN-019; no estaba en la lista de documentos a actualizar, pero contenía una referencia de actor que habría quedado desactualizada. |
| `docs/domain/commands-events.md` | Notas de `IncidentAcknowledged`, `IncidentClosed` e `IncidentEscalated` actualizadas con los actores confirmados; mismo motivo que arriba (no estaba en la lista, pero tenía referencias de actor obsoletas). |
| `docs/product/scope-mvp.md` | Corregida la mención de que el Técnico de mantenimiento era actor secundario de CU-004 (ya no lo es); mismo motivo. |
| `docs/product/problem-statement.md` | Etiqueta "atender/escalar" actualizada a "reconocer-coordinar/escalar" con su actor; mismo motivo. |

### Información que se preservó

- RN-001 a RN-018, sin cambio de numeración ni de significado (RN-006/RN-007 solo ganaron una
  precisión de actor, su contenido normativo original permanece).
- CU-001, CU-002, CU-007 a CU-021: sin cambio.
- La matriz impacto/urgencia (RN-012): sin tocar.
- El Administrador de plataforma se mantiene fuera de la operación diaria de incidentes (ya lo
  estaba; esta ronda lo hizo explícito en más lugares).
- El Auditor se mantiene en modo consulta, sin permisos de cambio (sin alterar su alcance, solo
  reafirmado).
- Todo el historial de Rondas 1 a 4 en este mismo change-log.

### TODOs no resueltos

- Los TODOs de rondas anteriores no afectados por esta decisión siguen abiertos (actor de CU-015,
  eventos no catalogados para CU-013/014/017 a 020, servicio dueño de RF-013, periodicidad de
  vencimiento de calibración).
- No existe una matriz de notificación confirmada (qué actor recibe qué notificación, por qué
  canal) — señalado, no inventado.
- No existen procedimientos manuales de contingencia ni acciones de contención específicas
  documentadas para el Operador — señalado en `docs/operations/incident-model.md`, no inventado.

## Enlaces internos

Ninguno de los 4 documentos usa enlaces de anclaje (`#sección`) hacia otro documento del
repositorio (verificado con búsqueda repo-wide antes de esta revisión), por lo que el cambio de
encabezados no rompe referencias externas. Las referencias cruzadas entre estos 4 documentos y el
resto de `/docs` son por ruta de archivo, no por encabezado, y se verificaron con una segunda
pasada: no se introdujo ninguna ruta inexistente.

**Ronda 2**: se repitió la misma verificación sobre los 16 archivos tocados (13 pedidos + 3
adicionales por consistencia). No se introdujo ninguna ruta rota nueva; en ese momento, las únicas
rutas inexistentes detectadas eran notas históricas "Migrado desde..." que citaban un C4 no
dividido (`c4.md`, previo a la separación en `system-context.md`/`container-diagram.md`/
`component-diagram.md`/`deployment-view.md`), un modelo de datos físico aún no creado
(`data-model.md`) y una copia de análisis de negocio (`business-analysis.md`, previo a
`docs/product/stakeholders.md` y `docs/domain/domain-model.md`).

**Ronda 3**: se repitió la verificación sobre los 12 archivos tocados (10 pedidos + 2 adicionales
por consistencia: `functional-requirements.md`, `commands-events.md`). Se verificó además que
ningún ID (RN, RF, CU) quedara duplicado en sus catálogos tras las inserciones. No se introdujo
ninguna ruta rota nueva ni ningún ID repetido; las mismas 3 rutas históricas de siempre seguían
siendo las únicas inexistentes referenciadas, y eran intencionales.

**Nota posterior** (fuera de la numeración de rondas de este documento; ver
`docs/planning/planning-cleanup-report.md` y `docs/architecture/architecture-consistency-report.md`
para el saneamiento de enlaces correspondiente): las notas "Migrado desde..." que citaban esas 3
rutas ya se retiraron de los documentos vivos (`container-diagram.md`, `deployment-view.md`,
`system-context.md`, `domain-model.md`, `observability-requirements.md`, entre otros). Esta
entrada queda como registro histórico de que esas rutas fueron intencionales en su momento; no
describe el estado actual del repositorio. El
modelo de datos físico (equivalente de `data-model.md`) sigue sin existir — se documenta como
artefacto pendiente en la sección "Deudas / documentación pendiente" de
`docs/architecture/architecture-consistency-report.md`, sin enlace activo hacia una ruta
inexistente.

**Ronda 4**: se repitió la verificación sobre los 11 archivos con referencias a rutas (de los 12
tocados; `docs/quality/acceptance-criteria.md` no cita rutas de otros documentos). Se confirmó
además que ningún encabezado `## CU-XXX` quedó duplicado en `docs/domain/use-cases.md` y que
RN-001 a RN-018 siguen definidas exactamente una vez en `docs/product/business-rules.md`. No se
introdujo ninguna ruta rota nueva; las mismas 4 rutas históricas de siempre siguen siendo las
únicas inexistentes referenciadas.

**Ronda 5**: se repitió la verificación sobre los 11 archivos tocados (7 de la lista pedida +
4 adicionales por referencias de actor obsoletas: `domain-model.md`, `commands-events.md`,
`scope-mvp.md`, `problem-statement.md`). Se confirmó que RN-001 a RN-019 siguen definidas
exactamente una vez y que ningún encabezado `## CU-XXX` quedó duplicado. No se introdujo ninguna
ruta rota nueva; las mismas 4 rutas históricas de siempre siguen siendo las únicas inexistentes
referenciadas.
