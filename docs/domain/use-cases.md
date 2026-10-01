# Casos de uso

## CU-001 Registrar unidad
Actor: Administrador de plataforma.
Trazabilidad: RF-001; evento `AssetRegistered`.

## CU-002 Recibir telemetría
Actor: Simulador (sensor-simulator, autónomo; productor principal de telemetría).
Trazabilidad: RF-003, RF-004; RN-001, RN-002; eventos `TelemetryReceived`, `TelemetryThresholdBreached`.

Escenarios que el sensor-simulator debe poder producir: nominal, fuera de rango, recuperación,
persistencia y pérdida de conectividad. Los primeros cuatro ya tienen regla de dominio asociada
(RN-002 anomalía, RN-005 persistencia). La pérdida de conectividad se detecta por ausencia de
telemetría esperada, no por un dato que el simulador produzca: ver CU-022 (actor Sistema), que
cubre esa detección porque no puede representarse claramente dentro de este CU (actor distinto:
Sistema, no Simulador).

## CU-003 Crear incidente automático
Actor: Sistema.
Trazabilidad: RF-005, RF-008; RN-003, RN-004, RN-005, RN-012; evento `IncidentCreated`.

El sistema notifica a los actores correspondientes según la política de notificación vigente
(RF-008, eventos `NotificationRequested`/`NotificationFailed`, `docs/domain/commands-events.md`).
Matriz de notificación: no confirmada por negocio. Placeholder académico vigente (DEC-018):
`IncidentCreated` notifica a los Supervisores de operaciones habilitados; `IncidentEscalated` a los
Técnicos de mantenimiento habilitados — ver `docs/domain/traceability-matrix.md` (RF-008).

## CU-004 Reconocer y coordinar atención de incidente
Actor principal: Supervisor de operaciones.
Actor secundario: Operador.
Trazabilidad: RF-006; RN-006; evento `IncidentAcknowledged`.

El Supervisor de operaciones es responsable de la coordinación operativa y reconoce el incidente;
el Operador ejecuta y registra acciones operativas iniciales o de contención dentro de su ámbito,
y puede aportar contexto operativo, pero no reconoce el incidente como responsable principal.

Flujo: el Supervisor de operaciones reconoce el incidente, lo que detiene el reloj de
reconocimiento de RN-006 al emitirse `IncidentAcknowledged`; el Operador puede haber ejecutado o
registrado acciones operativas iniciales antes o durante el reconocimiento, dentro de su ámbito,
sin ser quien reconoce formalmente.

## CU-005 Escalar incidente
Actor principal: Supervisor de operaciones.
Actor secundario: Sistema.
Trazabilidad: RF-007, RF-008; RN-012, RN-014; evento `IncidentEscalated`.

El Supervisor de operaciones solicita o confirma el escalamiento; el Sistema registra la
transición, emite `IncidentEscalated` y notifica al Técnico de mantenimiento. El escalamiento no
es una acción autónoma del Sistema: el MVP no declara escalamiento automático por vencimiento de
SLA. El SLA sirve para seguimiento, priorización y medición (RN-006, `docs/quality/sla-kpi.md`),
no como automatismo de escalamiento en el MVP.

## CU-006 Cerrar incidente
Actor principal: Técnico de mantenimiento.
Actor secundario: Supervisor de operaciones (informado del cierre).
Contribuye con contexto, sin ser actor formal de este CU: Operador.
Trazabilidad: RF-006; RN-007, RN-019; evento `IncidentClosed`.

El Técnico de mantenimiento es el único rol humano que puede cerrar técnicamente un incidente
(RN-019): registra diagnóstico, intervención, causa y comentario de resolución exigidos por
RN-007. El sistema emite `IncidentClosed` y conserva la auditoría (RN-008).

## CU-007 Registrar sensor
Actor: Administrador de plataforma.
Trazabilidad: RF-002; RN-001.

## CU-008 Consultar métricas operativas
Actor: Supervisor.
Trazabilidad: RF-009.

## CU-009 Consultar bitácora de auditoría
Actor: Auditor.
Trazabilidad: RF-018; RN-008. Consulta restringida de solo lectura atendida por el módulo interno
Audit Log, dentro de Incident Service (DEC-008) (`docs/architecture/container-diagram.md`).

> Nota: CU-010 no se define en este MVP. No existe un módulo de mantenimiento preventivo; el Técnico de mantenimiento participa en CU-005 (notificado) y como actor principal de CU-006 (cierre técnico).

## CU-011 Configurar perfil operativo y umbrales
Actor: Administrador de plataforma.
Trazabilidad: RF-010; RN-001.

## CU-012 Registrar criticidad de activo
Actor: Administrador de plataforma.
Trazabilidad: RF-011; RN-009, RN-013.

## CU-013 Consultar y actualizar activos, sensores y perfiles operativos
Actor principal: Administrador de plataforma.
Actor secundario: Supervisor de operaciones (consulta, dentro de sus funciones de supervisión
operacional).
Trazabilidad: RF-012; RN-001, RN-009.

Eventos: `AssetUpdated` y `OperationalProfileUpdated` (DEC-020, `docs/domain/commands-events.md`);
el resto de cambios técnicos del sensor se auditan en `SensorLifecycleAudit`.

Para el ciclo de vida operativo del sensor específicamente (estado, calibración, reasignación,
retiro), ver los casos de uso especializados CU-017 a CU-020; para el historial técnico y
administrativo del sensor, ver CU-021. Este CU-013 cubre la consulta/actualización general de
datos de activos, sensores y perfiles, no esas operaciones específicas.

## CU-014 Gestionar asignaciones de acceso
Actor: Administrador de plataforma.
Trazabilidad: RF-013; RN-008 (toda asignación de acceso es una transición auditable).

Evento: `UserAccessAssignmentChanged` (DEC-020, `docs/domain/commands-events.md`).

## CU-015 Inyectar telemetría de prueba
Actor principal: Administrador de plataforma.
Trazabilidad: RF-014; RN-001, RN-002, RN-015; eventos `TelemetryReceived`,
`TelemetryThresholdBreached` (mismos que CU-002, por RN-015: la telemetría de prueba se evalúa
igual que la del sensor-simulator).

El endpoint interno protegido es solo para pruebas controladas, diagnóstico y demostración; el
sensor-simulator autónomo (CU-002) continúa como productor principal de telemetría de la
demostración. Este CU no sustituye a CU-002, es un canal secundario de inyección controlada.

## CU-016 Autenticar usuario y autorizar acceso por rol
Actor: todos los actores humanos (Supervisor de operaciones, Operador, Técnico de mantenimiento,
Auditor, Administrador de plataforma).
Trazabilidad: RF-015; RN-008, RN-016.

**Alcance por fase**: en APF1 este caso de uso se demuestra solo en el frontend (login funcional
de demostración, sin backend real de seguridad — ver RN-016); la implementación real de
autenticación y RBAC en los endpoints del backend corresponde a APF2 (ADR-007, ADR-008).

## CU-017 Cambiar estado operativo del sensor
Actor principal: Administrador de plataforma.

Precondiciones:
- El sensor existe y tiene un estado operativo actual (RN-017).
- El usuario autenticado tiene el rol Administrador de plataforma.
- Si el destino es ACTIVO: existe evidencia vigente de calibración/verificación no vencida
  (RN-018). Si el origen es EN_MANTENIMIENTO, esa evidencia debe provenir de una calibración o
  verificación registrada explícitamente después de entrar a EN_MANTENIMIENTO (CU-019). Si el
  origen es INACTIVO, no se exige una calibración/verificación nueva, pero la vigente no debe
  estar vencida (RN-018).

Flujo principal:
1. El Administrador de plataforma selecciona el sensor y el nuevo estado operativo (ACTIVO,
   EN_MANTENIMIENTO, INACTIVO o RETIRADO).
2. El sistema valida que la transición esté permitida y que se cumplan sus precondiciones
   (`docs/domain/state-machines.md`, máquina de estados de Sensor).
3. El sistema registra el cambio con usuario responsable, fecha y hora, motivo, valor anterior y
   valor posterior (RN-017).
4. El sistema actualiza el estado operativo del sensor.

**Disparo alternativo (sin caso de uso propio)**: la transición hacia EN_MANTENIMIENTO por
vencimiento de calibración/verificación también puede ejecutarse mediante una tarea programada
(actor Sistema) que evalúa la vigencia periódicamente (RN-018); esa tarea aplica el mismo flujo
de registro auditable (usuario responsable = proceso automático, fecha y hora, motivo =
"calibración/verificación vencida"). No se modela como un CU separado porque no existe un evento
de dominio que asignarle sin inventarlo.

Reglas aplicables: RN-017; RN-018 (evidencia de calibración/verificación vigente, según el
origen); RN-008.

Postcondiciones:
- El sensor queda en el nuevo estado.
- Si el nuevo estado no es ACTIVO, sus lecturas dejan de ser elegibles para evaluación de
  anomalías y para creación/actualización de incidentes (RN-017, RN-018).
- El cambio queda registrado de forma auditable (`SensorLifecycleAudit`,
  `docs/domain/domain-model.md`).

Excepciones:
- Transición no permitida por la máquina de estados → se rechaza, sin cambio de estado.
- Transición a ACTIVO desde EN_MANTENIMIENTO sin una calibración/verificación registrada
  explícitamente después de entrar a EN_MANTENIMIENTO → se rechaza (RN-018). Registrar una
  calibración válida (CU-019) no reactiva el sensor por sí sola; este CU-017 es el paso explícito
  que ejecuta el retorno a ACTIVO.
- Transición a ACTIVO desde INACTIVO con la calibración/verificación vigente vencida → se rechaza
  (RN-018).
- Intento de transicionar un sensor en RETIRADO → se rechaza; RETIRADO es estado terminal para el
  MVP (`docs/domain/state-machines.md`), salvo decisión futura documentada.

Trazabilidad: RF-016; RN-017, RN-018, RN-008; evento `SensorStatusChanged` (y
`SensorCalibrationExpired` cuando el disparo es la tarea programada de vencimiento).

## CU-018 Reasignar sensor a otro activo
Actor principal: Administrador de plataforma.

Precondiciones:
- El sensor existe y está en estado EN_MANTENIMIENTO (RN-017) — la reasignación solo está
  permitida en ese estado.
- El activo destino existe (RF-001, RF-011).

Flujo principal:
1. El Administrador de plataforma selecciona el sensor (en EN_MANTENIMIENTO) y el activo destino.
2. El sistema registra la reasignación preservando el historial de asociaciones previas
   (`SensorAssignmentHistory`, `docs/domain/domain-model.md`) — no se sobrescribe la asociación
   anterior, se agrega una nueva entrada.
3. El sistema registra usuario responsable, fecha y hora, motivo, valor anterior (activo previo)
   y valor posterior (activo nuevo) (RN-017).

Reglas aplicables: RN-017 (precondición de estado EN_MANTENIMIENTO para reasignar), RN-008.

Postcondiciones:
- El sensor queda asociado al nuevo activo, permaneciendo en EN_MANTENIMIENTO (la reasignación no
  cambia el estado operativo; volver a ACTIVO requiere CU-017 con evidencia de calibración
  vigente, RN-018).
- El historial de asociaciones anteriores se conserva íntegro.

Excepciones:
- El activo destino no existe o no está registrado → se rechaza.
- El sensor no está en EN_MANTENIMIENTO → se rechaza (RN-017).

Trazabilidad: RF-016; RN-017; evento `SensorReassigned`.

## CU-019 Registrar calibración o verificación de sensor
Actor principal: Administrador de plataforma.

Precondiciones:
- El sensor existe.

Flujo principal:
1. El Administrador de plataforma registra una calibración o verificación del sensor.
2. El sistema registra usuario responsable, fecha y hora y motivo (RN-017), dejando constancia de
   que la calibración/verificación es válida a los efectos de RN-018.
3. Este registro, por sí solo, no cambia el estado del sensor a ACTIVO. Si el sensor estaba en
   EN_MANTENIMIENTO, el registro es condición necesaria (evidencia vigente) pero no suficiente: el
   retorno a ACTIVO se ejecuta explícitamente mediante CU-017 (cambiar estado operativo), como un
   paso separado.

Reglas aplicables: RN-018, RN-008.

Postcondiciones:
- Queda un registro de calibración/verificación (`CalibrationOrVerificationRecord`,
  `docs/domain/domain-model.md`) asociado al sensor.
- El estado operativo del sensor no cambia como efecto directo de este CU.

Excepciones:
- **TODO**: el criterio de vencimiento de una calibración/verificación (periodicidad, unidad de
  tiempo) está pendiente de definir; debe ser configurable (RN-018), sin una cifra global
  obligatoria.

Trazabilidad: RF-016; RN-018; evento `SensorCalibrationRecorded`.

## CU-020 Retirar lógicamente un sensor
Actor principal: Administrador de plataforma.

Precondiciones:
- El sensor existe.

Flujo principal:
1. El Administrador de plataforma solicita el retiro lógico del sensor.
2. El sistema registra usuario responsable, fecha y hora y motivo (RN-017).
3. El sistema transiciona el sensor a RETIRADO (`docs/domain/state-machines.md`).

Reglas aplicables: RN-017 (retiro lógico; un sensor con historial de lecturas, eventos o
incidentes no puede eliminarse físicamente). El retiro no está restringido a un estado de origen
específico (a diferencia de la reasignación, CU-018, que sí exige EN_MANTENIMIENTO); puede
ejecutarse desde ACTIVO, EN_MANTENIMIENTO o INACTIVO (`docs/domain/state-machines.md`).

Postcondiciones:
- El sensor queda en estado RETIRADO (terminal para el MVP).
- Su historial de lecturas, eventos, incidentes y asociaciones (`SensorAssignmentHistory`) se
  conserva íntegro.
- El sensor deja de generar lecturas elegibles.

Excepciones:
- Intento de eliminación física de un sensor con historial → no soportado, se rechaza (RN-017).

Trazabilidad: RF-016; RN-017, RN-008; evento `SensorRetired`.

## CU-021 Consultar historial técnico y administrativo del sensor
Actor principal: Administrador de plataforma.

Precondiciones:
- El sensor existe.

Flujo principal:
1. El Administrador de plataforma consulta el sensor.
2. El sistema muestra el historial administrativo (`SensorLifecycleAudit`,
   `docs/domain/domain-model.md`): cambios de estado, calibraciones/verificaciones,
   reasignaciones y retiros, cada uno con usuario responsable, fecha y hora, motivo, y valor
   anterior/posterior cuando corresponda. Incluye las transiciones a EN_MANTENIMIENTO ejecutadas
   por la tarea programada de vencimiento de calibración (RN-018), identificando como responsable
   al proceso automático, no a un usuario humano.

Reglas aplicables: RN-008, RN-017.

Postcondiciones: ninguna (operación de solo lectura).

Excepciones: ninguna identificada.

Trazabilidad: RF-012 (extendido); RN-008, RN-017.

## CU-022 Detectar y registrar pérdida de conectividad de sensor
Actor principal: Sistema.

Precondiciones:
- El sensor tiene una frecuencia esperada de telemetría configurada.

Flujo principal:
1. El Sistema evalúa, para cada sensor, si ha llegado telemetría (`TelemetryReceived`) dentro de
   la frecuencia esperada configurable.
2. Si no ha llegado dentro de esa ventana, el Sistema registra la pérdida de conectividad y emite
   `SensorConnectivityLost`.

Reglas aplicables: RN-020.

Postcondiciones:
- Queda un registro visible y auditable de la pérdida de conectividad, consultable desde
  monitoreo (RN-008, RN-020).
- No se crea un incidente térmico como efecto de este CU.
- El `SensorStatus` (RN-017) no cambia como efecto directo de este CU: la pérdida de conectividad
  es una condición operativa distinta del ciclo de vida del sensor
  (`docs/domain/domain-model.md`).

Excepciones: ninguna identificada.

Trazabilidad: RF-017; RN-020; evento `SensorConnectivityLost`.

**TODO**: la frecuencia esperada configurable (cada cuánto se espera una lectura) no tiene un
valor numérico definido; no se fija aquí.
