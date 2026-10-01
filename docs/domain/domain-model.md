# Modelo de dominio

## Proceso objetivo

1. Recibir telemetría.
2. Validar sensor y activo.
3. Evaluar umbrales y persistencia.
4. Crear o actualizar incidente.
5. Calcular prioridad y SLA.
6. Notificar y escalar.
7. Registrar atención y evidencia.

Reglas de negocio asociadas a cada paso: `docs/product/business-rules.md` (RN-001 a RN-019).
Casos de uso que instancian este proceso: `docs/domain/use-cases.md`.

## Trazabilidad de cada paso

| Paso | CU | RF | RN | Evento(s) |
|---|---|---|---|---|
| 1. Recibir telemetría | CU-002 | RF-003 | RN-002 | `TelemetryReceived` |
| 2. Validar sensor y activo | *(sin CU/RF dedicado)* | — | RN-001 (precondición: perfil operativo activo) | — |
| 3. Evaluar umbrales y persistencia | CU-002 | RF-004 | RN-001, RN-002 | `TelemetryThresholdBreached` |
| 4. Crear o actualizar incidente | CU-003 | RF-005 | RN-003, RN-004, RN-005 | `IncidentCreated` |
| 5. Calcular prioridad y SLA | CU-003 (creación), CU-005 (recálculo) | RF-007 | RN-006, RN-009 a RN-014 | — |
| 6. Notificar y escalar | CU-005 | RF-007, RF-008 | RN-012, RN-014 | `IncidentEscalated`, `NotificationRequested`, `NotificationFailed` |
| 7. Registrar atención y evidencia | CU-004 (reconocer/coordinar, Supervisor de operaciones), CU-006 (cerrar, Técnico de mantenimiento), CU-009 (consultar auditoría, RF-018) | RF-006, RF-018 | RN-006, RN-007, RN-008, RN-019 | `IncidentAcknowledged`, `IncidentClosed` |

Vacíos conocidos que esta tabla no resuelve (ver `docs/academic/apf1-mapping.md`):
- Paso 2 no tiene un caso de uso ni requisito funcional propio; es una precondición implícita de RN-001.
- El paso 6 vincula `NotificationRequested`/`NotificationFailed` por el catálogo de eventos
  (`docs/domain/commands-events.md`) y RF-008, pero `docs/domain/use-cases.md` no menciona esos
  eventos explícitamente en la trazabilidad de CU-003/CU-005.

## Conceptos de dominio nombrados (sin modelo de datos formal)

| Concepto | Dónde se nombra |
|---|---|
| Organización, sede, unidad de frío | RF-001, CU-001 |
| Sensor | RF-002, RN-001, CU-007 |
| Perfil operativo (rangos térmicos, ventana de persistencia, criticidad del activo asociado) | RN-001, RF-010, CU-011; consulta/actualización vía RF-012, CU-013 |
| Activo y su criticidad (bajo/medio/alto/crítico) | RN-009, RF-011, CU-012 |
| Anomalía (magnitud, persistencia) | RN-002, RN-010, RN-011 |
| Lectura de telemetría (identificador, sensor, activo asociado, timestamp de origen, valor, unidad, datos de correlación) | RF-003, RF-004; correlación ver RNF-002 |
| Incidente (severidad, impacto, urgencia, prioridad P1–P4) | RN-003 a RN-007, RN-009 a RN-014 |
| Registro de auditoría (transición, actor, timestamp) | RN-008, CU-009 |
| Asignación de acceso (rol de usuario) | RF-013, CU-014 |
| `SensorStatus` (ACTIVO, EN_MANTENIMIENTO, INACTIVO, RETIRADO) | RN-017, RN-018, CU-017 |
| `SensorAssignmentHistory` (historial de asociaciones sensor↔activo) | RN-017, CU-018 |
| `CalibrationOrVerificationRecord` (registro de calibración/verificación) | RN-018, CU-019 |
| `SensorLifecycleAudit` (bitácora de cambios administrativos del sensor) | RN-017, RN-008, CU-021 |
| Pérdida de conectividad (condición operativa, distinta de `SensorStatus`) | RN-020, RF-017, CU-022, evento `SensorConnectivityLost` |

### Relaciones e invariantes del ciclo de vida del sensor

- Un `Sensor` tiene exactamente un `SensorStatus` vigente en cada momento (RN-017); las
  transiciones permitidas entre estados están definidas en `docs/domain/state-machines.md`.
- Solo un `Sensor` con `SensorStatus = ACTIVO` produce lecturas elegibles para evaluación de
  anomalías (RN-002) y para creación/actualización de incidentes (RN-004, RN-005). Las lecturas
  de un sensor en EN_MANTENIMIENTO, INACTIVO o RETIRADO se conservan como evidencia técnica, no
  se descartan, pero no participan en esa evaluación (RN-018).
- `SensorAssignmentHistory` es de solo-adición (append-only): reasignar un sensor a otro activo
  (CU-018) agrega una entrada nueva, nunca sobrescribe ni elimina las anteriores. La reasignación
  solo está permitida cuando `SensorStatus = EN_MANTENIMIENTO`; fuera de ese estado se rechaza
  (RN-017).
- `CalibrationOrVerificationRecord` se asocia a un `Sensor`; una calibración/verificación vencida
  es la condición que obliga la transición a EN_MANTENIMIENTO (RN-018), ya sea por acción del
  Administrador de plataforma (CU-017) o detectada automáticamente por una tarea programada
  (actor Sistema, periodicidad configurable, sin cifra global fija). Registrar un
  `CalibrationOrVerificationRecord` válido (CU-019) no cambia el `SensorStatus` por sí solo; el
  retorno a ACTIVO es siempre un paso explícito de CU-017, que exige evidencia vigente: una
  calibración registrada después de entrar a EN_MANTENIMIENTO (si el origen es ese estado), o una
  calibración vigente no vencida sin necesidad de un registro nuevo (si el origen es INACTIVO).
- `SensorLifecycleAudit` agrega, de forma auditable (usuario, fecha, motivo, valor anterior/
  posterior), todo cambio de estado, registro de calibración/verificación, reasignación y retiro
  lógico de un sensor (RN-017, RN-008). Es la fuente que consulta CU-021.
- El retiro es lógico y no elimina historial: transicionar un sensor a RETIRADO (CU-020) no borra
  sus lecturas, eventos, incidentes, `SensorAssignmentHistory` ni `SensorLifecycleAudit`. Un
  sensor con historial no puede eliminarse físicamente (RN-017). RETIRADO es un estado terminal
  para el MVP (`docs/domain/state-machines.md`).

## Agregados y responsabilidades

Agregados nombrados a partir de los conceptos ya documentados arriba; no se agregan atributos ni
invariantes no confirmados por RF/RN/CU:

| Agregado | Responsabilidad |
|---|---|
| `Asset` / `Sensor` | Identidad del activo y del sensor, su asociación (`SensorAssignmentHistory`), la criticidad del activo (RN-009) y el estado operativo del sensor (`SensorStatus`, RN-017). |
| `OperationalProfile` | Perfil operativo del sensor: rangos térmicos (umbrales), ventana de persistencia (RN-001). |
| `TelemetryReading` | Lectura de telemetría (identificador, sensor, activo, timestamp, valor, unidad, correlación) y su elegibilidad para evaluación (RN-017, RN-018). |
| `Incident` | Ciclo de vida del incidente: creación, severidad/impacto/urgencia/prioridad, reconocimiento, escalamiento, cierre (RN-003 a RN-007, RN-009 a RN-014, RN-019). |
| `AuditRecord` | Registro auditable de una transición relevante: actor, timestamp, motivo, valor anterior/posterior (RN-008). Incluye `SensorLifecycleAudit` como especialización para sensores. |
| `UserAccessAssignment` | Asignación de rol de usuario (RF-013, CU-014). |

## Relaciones de negocio

- Un `Sensor` pertenece a un `Asset` en un momento dado; esa relación cambia solo mediante
  reasignación (CU-018) y se conserva en `SensorAssignmentHistory` (append-only).
- Un `Sensor` tiene un `OperationalProfile` vigente (RN-001), del cual dependen tanto la detección
  de anomalías (RN-002) como la clasificación de urgencia (RN-011).
- Una `TelemetryReading` de un `Sensor` en `SensorStatus = ACTIVO` puede originar una anomalía
  (RN-002) y, de ahí, un `Incident` (RN-003, RN-004, RN-005). Una lectura de un sensor en otro
  estado se conserva pero no participa en esa cadena (RN-018).
- Un `Incident` está siempre asociado a un `Asset` y a un `Sensor`; su prioridad depende de la
  criticidad del `Asset` (RN-009, RN-013) y de la magnitud/persistencia de la lectura (RN-010,
  RN-011).
- Todo cambio relevante sobre `Sensor`, `Incident` o `UserAccessAssignment` produce un
  `AuditRecord` (RN-008).
- La pérdida de conectividad (RN-020) es una relación entre `Sensor` y el paso del tiempo sin
  `TelemetryReading` nueva; no es una relación con `Incident` ni con `OperationalProfile`.

### Diferencia entre evento, anomalía e incidente

No son sinónimos y no se intercambian entre sí:

- **Evento**: hecho de dominio publicado por un componente (por ejemplo, `TelemetryReceived`,
  `SensorStatusChanged`, `IncidentCreated`). Es el mecanismo de comunicación, no el hecho de
  negocio en sí.
- **Anomalía**: una `TelemetryReading` fuera de rango del `OperationalProfile` vigente (RN-002).
  Es una condición de negocio detectada, no necesariamente publicada como su propio evento (se
  representa mediante `TelemetryThresholdBreached`).
- **Incidente**: la entidad de negocio creada a partir de una anomalía que cumple las reglas de
  creación (RN-003, RN-004, RN-005); tiene su propio ciclo de vida (creado, reconocido, escalado,
  cerrado) y es lo único que se prioriza (RN-012) y se cierra formalmente (RN-007, RN-019). Una
  anomalía no es un incidente hasta que RF-005/CU-003 lo crea.

### Conectividad como condición operativa distinta del ciclo de vida

La pérdida de conectividad (RN-020, evento `SensorConnectivityLost`) **no** es un valor de
`SensorStatus` (RN-017: ACTIVO, EN_MANTENIMIENTO, INACTIVO, RETIRADO) ni lo modifica. Un sensor
puede estar ACTIVO y sin conectividad reciente al mismo tiempo; ambos conceptos se registran y
consultan por separado, y no se mezclan en un único atributo o transición.

## Capacidades de soporte (fuera del proceso principal)

No forman parte del recorrido lineal de 7 pasos de arriba, pero son necesarias para operarlo:

| Capacidad | CU | RF |
|---|---|---|
| Consultar y actualizar activos, sensores y perfiles operativos | CU-013 | RF-012 |
| Gestionar asignaciones de acceso (rol de usuario) — módulo interno Identity & Access, dentro de Incident Service (DEC-008) | CU-014 | RF-013 |
| Consultar bitácora de auditoría (solo lectura, restringido) — módulo interno Audit Log, dentro de Incident Service (DEC-008) | CU-009 | RF-018 |
| Inyectar telemetría de prueba (endpoint interno protegido) | CU-015 | RF-014 |
| Autenticar usuario y autorizar acceso por rol (backend real, **solo APF2**) | CU-016 | RF-015 |
| Cambiar estado operativo del sensor | CU-017 | RF-016 |
| Reasignar sensor a otro activo | CU-018 | RF-016 |
| Registrar calibración o verificación de sensor | CU-019 | RF-016 |
| Retirar lógicamente un sensor | CU-020 | RF-016 |
| Consultar historial técnico y administrativo del sensor | CU-021 | RF-012 |
| Detectar y registrar pérdida de conectividad de sensor | CU-022 | RF-017 |

## TODO

- Modelo de datos / entidades de dominio: RF-001 se modela como `Organization` 1—N `Site` 1—N
  `Asset` (DEC-020); atributos concretos y claves en `docs/specs/SPEC-005-asset-service.md`. El
  resto de agregados se detalla en los specs de su servicio.
- Criterio de vencimiento de calibración/verificación (periodicidad, unidad de tiempo): pendiente
  de definir; debe ser configurable (RN-018).
- Frecuencia esperada configurable que determina la pérdida de conectividad (RN-020): pendiente
  de definir, sin fijar un valor numérico.
- Qué constituye una "acción equivalente documentada" cuando la tarea programada detecta un
  vencimiento y no ejecuta la transición a EN_MANTENIMIENTO directamente: pendiente de definir.
