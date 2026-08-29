# Reglas de negocio

Reglas RN-001 a RN-020. Trazabilidad completa (RF, CU, evento, prueba futura) en
`docs/domain/traceability-matrix.md`.

## Glosario

| Término | Definición |
|---|---|
| Activo | Equipo de frío monitoreado, con una criticidad clasificada en bajo/medio/alto/crítico (RN-009). |
| Sensor | Dispositivo asociado a un activo; tiene un perfil operativo activo (RN-001) y genera lecturas. |
| Lectura | Dato de telemetría recibido de un sensor (RF-003), evaluado contra el perfil operativo del sensor (RF-004). |
| Anomalía | Lectura fuera de rango del perfil operativo vigente (RN-002). |
| Condición persistente | Nuevas lecturas fuera de rango para la misma combinación activo/sensor/tipo de anomalía, mientras existe un incidente abierto con esa combinación (RN-004, RN-005). |
| Incidente abierto | Incidente ya creado (evento `IncidentCreated`) que aún no ha sido cerrado (evento `IncidentClosed`); es la referencia contra la que se evalúa la equivalencia de nuevas anomalías (RN-004). |
| Impacto | Clasificación del alcance del daño potencial de una anomalía — bajo/medio/alto/crítico (RN-010). |
| Urgencia | Clasificación de la velocidad de respuesta requerida — baja/media/alta/inmediata (RN-011). |
| Prioridad | Nivel P1–P4 asignado al incidente, derivado siempre de la matriz impacto × urgencia (RN-012), nunca de un mapeo directo de severidad. |
| Estado operativo del sensor | Uno de ACTIVO, EN_MANTENIMIENTO, INACTIVO o RETIRADO (RN-017); determina si el sensor puede producir lecturas elegibles. |
| Calibración o verificación | Registro que confirma que un sensor mide correctamente; su vencimiento obliga a pasar el sensor a EN_MANTENIMIENTO (RN-018); una calibración/verificación válida es condición para volver a ACTIVO. |
| Retiro lógico | Transición del sensor a RETIRADO que lo excluye de generar lecturas elegibles, sin eliminar su historial de lecturas, eventos, incidentes o asociaciones (RN-017). No existe eliminación física de un sensor con historial. |
| Lectura elegible | Lectura de un sensor en estado ACTIVO, apta para evaluación de anomalías y para creación o actualización de incidentes (RN-017). Las lecturas de sensores en EN_MANTENIMIENTO, INACTIVO o RETIRADO se conservan como evidencia técnica pero no son elegibles (RN-018). |

## Índice de reglas

| Regla | Resumen | Sección |
|---|---|---|
| RN-001 | Cada sensor tiene un perfil operativo activo (umbrales y ventanas de persistencia configurables). | Perfil operativo y detección de anomalías |
| RN-002 | Una lectura fuera de rango genera una anomalía. | Perfil operativo y detección de anomalías |
| RN-004 | No se duplican incidentes equivalentes mientras exista uno abierto. | Ciclo de vida del incidente |
| RN-005 | Una condición persistente actualiza el incidente existente en vez de crear uno nuevo. | Ciclo de vida del incidente |
| RN-006 | El SLA inicia al crear el incidente; el reconocimiento y el MTTR se miden por evento. | Ciclo de vida del incidente |
| RN-007 | Cerrar un incidente exige causa y comentario de resolución. | Ciclo de vida del incidente |
| RN-003 | La severidad depende de magnitud, persistencia y criticidad del activo. | Severidad, impacto, urgencia y prioridad |
| RN-009 | La criticidad del activo se clasifica en bajo/medio/alto/crítico. | Severidad, impacto, urgencia y prioridad |
| RN-010 | El impacto de una anomalía se clasifica en bajo/medio/alto/crítico. | Severidad, impacto, urgencia y prioridad |
| RN-011 | La urgencia se clasifica en baja/media/alta/inmediata (umbrales configurables). | Severidad, impacto, urgencia y prioridad |
| RN-012 | La prioridad (P1–P4) se determina por la matriz impacto/urgencia. | Severidad, impacto, urgencia y prioridad |
| RN-013 | Criticidad → impacto y magnitud/persistencia → urgencia; no hay mapeo directo severidad→prioridad. | Severidad, impacto, urgencia y prioridad |
| RN-008 | Toda transición relevante es auditable. | Recálculo y auditoría |
| RN-014 | La prioridad puede recalcularse si cambian las condiciones que la originaron. | Recálculo y auditoría |
| RN-015 | La telemetría inyectada por el endpoint interno de pruebas se evalúa igual que la del sensor-simulator. | Origen de telemetría |
| RN-016 | El login de APF1 es una demostración de frontend, no un control de seguridad productivo. | Autenticación y autorización |
| RN-017 | Todo sensor mantiene un estado operativo (ACTIVO/EN_MANTENIMIENTO/INACTIVO/RETIRADO); solo ACTIVO genera lecturas elegibles; todo cambio se audita; el retiro es lógico, nunca elimina historial. | Ciclo de vida operativo del sensor |
| RN-018 | Calibración/verificación vencida obliga a pasar a EN_MANTENIMIENTO; en ese estado las lecturas se conservan como evidencia pero no son elegibles; volver a ACTIVO exige calibración válida y cambio de estado documentado. | Ciclo de vida operativo del sensor |
| RN-019 | Solo el Técnico de mantenimiento puede cerrar técnicamente un incidente; ningún otro rol humano ejecuta el cierre ni modifica evidencia técnica de resolución. | Ciclo de vida del incidente |
| RN-020 | La ausencia de telemetría dentro de la frecuencia esperada configurada genera un evento operativo de pérdida de conectividad; no crea por sí mismo un incidente térmico. | Conectividad del sensor |

## Perfil operativo y detección de anomalías

- RN-001: cada sensor tiene un perfil operativo activo.
  - El perfil operativo (umbrales, ventanas de persistencia) se gestiona mediante RF-010 / CU-011.
  - Los umbrales y ventanas de persistencia son parámetros configurables por sensor a través de
    RF-010/CU-011; esta regla no fija valores numéricos.
  - El perfil operativo incluye rangos térmicos (los umbrales de esta regla, expresados en
    temperatura), ventanas de persistencia (también de esta regla) y la criticidad del activo
    asociado (RN-009). El perfil se puede consultar y actualizar mediante RF-012/CU-013, además
    de registrarse mediante RF-010/CU-011.
- RN-002: una lectura fuera de rango genera una anomalía.
  - El "rango" es el definido por el perfil operativo configurable del sensor (RN-001), no un
    umbral global fijo compartido entre sensores.

## Ciclo de vida del incidente

- RN-004: no se crean incidentes equivalentes duplicados mientras exista uno abierto.
  - Dos anomalías son equivalentes si comparten el mismo activo, el mismo sensor y el mismo tipo de anomalía, y ya existe un incidente abierto con esa combinación.
- RN-005: una condición persistente actualiza el incidente existente.
  - "Persistente" significa que llegan nuevas lecturas fuera de rango para la misma combinación activo/sensor/tipo de anomalía del RN-004 mientras el incidente sigue abierto.
  - La ventana de persistencia que define qué tan seguido o durante cuánto tiempo deben
    repetirse esas lecturas para considerarse "persistente" es parte del perfil operativo
    (RN-001); esta regla no fija un número de lecturas ni una duración.
- RN-006: el SLA inicia al crear el incidente.
  - El reloj de reconocimiento se detiene con el evento `IncidentAcknowledged`. El tiempo de resolución (MTTR) se mide hasta el evento `IncidentClosed`.
  - El reconocimiento (CU-004, "Reconocer y coordinar atención de incidente") lo ejecuta el
    Supervisor de operaciones, con el Operador como actor secundario.
- RN-007: cerrar un incidente exige causa y comentario de resolución.
  - El cierre se registra mediante CU-006 y emite el evento `IncidentClosed`.
  - El cierre lo ejecuta el Técnico de mantenimiento, único rol humano habilitado para hacerlo
    (RN-019). El Supervisor de operaciones es informado del cierre; el Operador puede aportar
    contexto, sin ejecutar el cierre.
- RN-019: solo el Técnico de mantenimiento puede cerrar técnicamente un incidente.
  - Ningún otro rol humano (Supervisor de operaciones, Operador, Auditor, Administrador de
    plataforma) ejecuta el cierre ni modifica la evidencia técnica de resolución (diagnóstico,
    intervención, causa, comentario de resolución) registrada por el Técnico (RN-007).
  - El Supervisor de operaciones puede ser informado del cierre (CU-006, actor secundario) y el
    Operador puede aportar contexto operativo, pero ninguno de los dos ejecuta la transición.
  - Esta restricción es de rol/autorización, complementaria a RN-007 (que exige el contenido del
    cierre, no especifica quién lo ejecuta).

## Severidad, impacto, urgencia y prioridad

- RN-003: la severidad depende de magnitud, persistencia y criticidad del activo.
  - La severidad se materializa como una combinación de impacto (RN-010) y urgencia (RN-011); ver RN-013.
- RN-009: la criticidad del activo se clasifica en bajo, medio, alto o crítico.
  - Se registra al dar de alta o actualizar el activo (RF-011 / CU-012) y es un insumo de RN-013.
- RN-010: el impacto de una anomalía se clasifica en bajo, medio, alto o crítico, según el alcance del daño potencial (pérdida de producto, incumplimiento normativo, riesgo sobre el activo).
- RN-011: la urgencia de una anomalía se clasifica en baja, media, alta o inmediata, según la velocidad de respuesta requerida, derivada de la magnitud y persistencia de la lectura fuera de rango.
  - Los umbrales de magnitud y las ventanas de persistencia que alimentan esta clasificación
    provienen del perfil operativo configurable del sensor (RN-001), no de valores numéricos
    fijos definidos en esta regla.
- RN-012: la prioridad del incidente (P1, P2, P3 o P4) se determina mediante la matriz impacto/urgencia:

  | Impacto \ Urgencia | Inmediata | Alta | Media | Baja |
  |---|---|---|---|---|
  | Crítico | P1 | P1 | P2 | P2 |
  | Alto | P1 | P2 | P2 | P3 |
  | Medio | P2 | P3 | P3 | P4 |
  | Bajo | P3 | P4 | P4 | P4 |

- RN-013: la criticidad del activo (RN-009) es un insumo directo del impacto (RN-010); la magnitud y persistencia de la anomalía son insumos directos de la urgencia (RN-011). No existe un mapeo directo de severidad a prioridad: la prioridad siempre se deriva de la matriz impacto/urgencia (RN-012).

## Recálculo y auditoría

- RN-008: toda transición relevante es auditable.
- RN-014: la prioridad de un incidente puede recalcularse si cambian las condiciones que la originaron (nuevas lecturas, cambio de criticidad del activo, escalación). Todo recálculo de prioridad es una transición auditable (RN-008).

## Origen de telemetría

- RN-015: la telemetría recibida por el endpoint interno de inyección de pruebas (RF-014, CU-015) se evalúa exactamente igual que la del sensor-simulator (RF-003, RF-004, CU-002): mismo perfil operativo (RN-001), misma detección de anomalías (RN-002). No existe una ruta de evaluación distinta para datos de prueba ni demostraciones.

## Autenticación y autorización

- RN-016: el login de APF1 es una demostración funcional del frontend (navegación protegida por rol, estados de sesión, acceso denegado, cierre de sesión), sin backend real de seguridad. No se presenta como un control de seguridad productivo. La autenticación y autorización reales (Spring Security, tokens, RBAC en endpoints, hash de contraseñas, secretos por variables de entorno) se implementan en APF2 (RF-015, CU-016, ADR-007, ADR-008).

## Ciclo de vida operativo del sensor

Relacionadas con RN-001 (perfil operativo del sensor), RN-002 (anomalía a partir de una lectura),
RN-004 y RN-005 (equivalencia y persistencia de anomalías, que dependen de que la lectura sea
elegible) y RN-008 (auditoría).

- RN-017: todo sensor debe mantener un estado operativo: ACTIVO, EN_MANTENIMIENTO, INACTIVO o
  RETIRADO.
  - Solo los sensores en estado ACTIVO pueden generar lecturas elegibles para evaluación de
    anomalías (RN-002) y creación o actualización de incidentes (RN-004, RN-005).
  - Todo cambio de estado, registro de calibración o verificación, reasignación a otro activo y
    retiro lógico debe registrar: usuario responsable; fecha y hora; motivo; valor anterior y
    valor posterior cuando corresponda (consistente con RN-008: toda transición relevante es
    auditable).
  - Un sensor con historial de lecturas, eventos o incidentes no puede eliminarse físicamente; el
    retiro es siempre lógico (transición a RETIRADO), no un borrado de registro.
  - La reasignación de un sensor a otro activo (CU-018) solo está permitida cuando el sensor está
    en estado EN_MANTENIMIENTO. Fuera de ese estado, la reasignación se rechaza.
  - Cambios de estado, reasignaciones, calibraciones/verificaciones y retiros emiten,
    respectivamente, `SensorStatusChanged`, `SensorReassigned`, `SensorCalibrationRecorded` y
    `SensorRetired` (`docs/domain/commands-events.md`).
- RN-018: si la calibración o verificación del sensor está vencida, el sensor debe pasar a
  EN_MANTENIMIENTO.
  - Este cambio de estado puede ser ejecutado manualmente por el Administrador de plataforma
    (CU-017) o detectado automáticamente por una tarea programada (actor Sistema) que evalúa
    periódicamente la vigencia de la calibración/verificación de cada sensor; en ambos casos
    aplica el mismo registro auditable de RN-017 (usuario responsable — persona o proceso
    automático —, fecha y hora, motivo, valor anterior/posterior). La periodicidad o criterio de
    vencimiento debe ser configurable; no se fija ninguna cifra global obligatoria. Si la tarea
    programada detecta el vencimiento y no ejecuta la transición directamente, debe generar una
    acción equivalente documentada (auditable de la misma forma). La detección de vencimiento
    emite `SensorCalibrationExpired`; el cambio de estado resultante emite `SensorStatusChanged`
    (`docs/domain/commands-events.md`).
  - Las lecturas recibidas en EN_MANTENIMIENTO, INACTIVO o RETIRADO pueden conservarse como
    evidencia técnica, pero no son elegibles para detección de anomalías (RN-002), creación de
    incidentes (RN-004) ni actualización de incidentes existentes (RN-005).
  - Registrar una calibración o verificación válida (CU-019) no cambia automáticamente el estado
    del sensor a ACTIVO. El retorno a ACTIVO se ejecuta explícitamente mediante CU-017 (cambiar
    estado operativo) y exige evidencia vigente de calibración o verificación:
    - Desde EN_MANTENIMIENTO: exige una calibración o verificación registrada explícitamente
      después de entrar a ese estado (CU-019) — no basta con que hubiera una vigente antes.
    - Desde INACTIVO: no exige una calibración/verificación nueva, pero la vigente no debe estar
      vencida.
  - **TODO**: la periodicidad o criterio exacto de vencimiento de una calibración/verificación
    está pendiente de definir; debe ser configurable, sin fijar ningún valor numérico.
  - **TODO**: qué constituye una "acción equivalente documentada" (cuando la tarea programada no
    ejecuta la transición directamente) está pendiente de definir.

## Conectividad del sensor

- RN-020: la ausencia de telemetría dentro de la frecuencia esperada configurada del sensor debe
  generar un evento operativo de pérdida de conectividad (`SensorConnectivityLost`,
  `docs/domain/commands-events.md`); dicho evento no crea por sí mismo un incidente térmico en el
  MVP.
  - La pérdida de conectividad es una condición operativa distinta del estado del ciclo de vida
    del sensor (`SensorStatus`, RN-017): un sensor puede estar ACTIVO y, al mismo tiempo, sin
    conectividad reciente. No se mezclan ambos conceptos.
  - Debe ser visible, auditable y manejable desde monitoreo (RN-008).
  - **TODO**: la frecuencia esperada configurable (cada cuánto se espera una lectura) no tiene un
    valor numérico definido; debe ser configurable por sensor, sin fijar una cifra global.
