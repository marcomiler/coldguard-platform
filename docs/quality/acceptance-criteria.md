# Criterios de aceptación

> TODO: no existe un documento consolidado de criterios de aceptación transversales en el árbol
> original. Los criterios de aceptación ya documentados viven por historia de usuario en
> `docs/product/product-backlog.md` y por sprint en `docs/planning/sprint-1.md` /
> `docs/planning/sprint-2.md`. Este documento debería consolidar criterios de aceptación a nivel
> de RF/CU cuando el equipo los defina, sin inventarlos aquí.

## RF-006 — Flujo operativo de incidentes: roles confirmados

Derivados de RN-006, RN-007, RN-019 y CU-004/CU-005/CU-006
(`docs/product/business-rules.md`, `docs/domain/use-cases.md`); no se agregan criterios de
escalamiento automático por SLA, acciones de contención específicas, ni métricas no confirmadas.

- **CU-004 (reconocer y coordinar)**: un reconocimiento ejecutado por el Supervisor de
  operaciones detiene el reloj de reconocimiento (RN-006) al emitir `IncidentAcknowledged`. El
  Operador, como actor secundario, puede haber registrado acciones operativas iniciales antes o
  durante el reconocimiento, pero no es quien lo ejecuta formalmente.
- **CU-005 (escalar)**: un escalamiento solo se ejecuta a partir de una solicitud o confirmación
  del Supervisor de operaciones; un test que intente representar un escalamiento iniciado sin esa
  solicitud/confirmación no corresponde al flujo confirmado (no se declara escalamiento
  automático por SLA en el MVP). Al escalar, el Sistema registra la transición, emite
  `IncidentEscalated` y notifica al Técnico de mantenimiento.
- **CU-006 (cerrar)**: un intento de cierre ejecutado por un actor distinto del Técnico de
  mantenimiento se rechaza (RN-019). El cierre exige causa técnica y comentario de resolución
  (RN-007) y emite `IncidentClosed`, conservando auditoría (RN-008). El Supervisor de operaciones
  queda informado del cierre (actor secundario); el Operador puede aportar contexto, sin poder
  ejecutar ni bloquear el cierre.
- **Separación de responsabilidades**: el Administrador de plataforma no participa en ningún paso
  de CU-003 a CU-006 (queda fuera de la operación diaria de incidentes); el Auditor solo consulta
  (CU-009), sin ejecutar ni modificar ninguna transición de incidente.

## RF-016 — Ciclo de vida operativo del sensor (primer contenido consolidado)

Derivados directamente de RN-017, RN-018 y CU-017 a CU-021
(`docs/product/business-rules.md`, `docs/domain/use-cases.md`,
`docs/domain/state-machines.md`); no se agregan criterios no respaldados por esas fuentes.

- **CU-017 (cambiar estado)**: una transición no permitida por la máquina de estados
  (`docs/domain/state-machines.md`) se rechaza sin modificar el estado; toda transición aceptada
  registra usuario responsable, fecha y hora, motivo, valor anterior y valor posterior.
- **CU-017, retorno a ACTIVO desde EN_MANTENIMIENTO**: se rechaza si no existe una calibración o
  verificación registrada explícitamente después de haber entrado a EN_MANTENIMIENTO.
- **CU-017, retorno a ACTIVO desde INACTIVO**: se rechaza si la calibración/verificación vigente
  está vencida; se acepta sin exigir un registro nuevo si la vigente no está vencida.
- **CU-018 (reasignar sensor)**: se rechaza si el sensor no está en EN_MANTENIMIENTO al momento de
  la solicitud; al aceptarse, la asociación anterior se conserva en `SensorAssignmentHistory`, no
  se sobrescribe.
- **CU-019 (registrar calibración/verificación)**: el registro por sí solo no cambia el
  `SensorStatus` del sensor; un test que registre una calibración válida y luego consulte el
  estado debe seguir mostrando el estado anterior (p. ej. EN_MANTENIMIENTO) hasta que se ejecute
  CU-017 explícitamente.
- **CU-020 (retiro lógico)**: un sensor con historial de lecturas, eventos o incidentes no admite
  eliminación física bajo ninguna condición; el retiro deja el sensor en RETIRADO conservando ese
  historial íntegro.
- **RN-017/RN-018, elegibilidad de lecturas**: una lectura de un sensor en EN_MANTENIMIENTO,
  INACTIVO o RETIRADO se almacena, pero no participa en la evaluación de anomalías (RN-002) ni en
  la creación/actualización de incidentes (RN-004, RN-005); una lectura de un sensor ACTIVO sí.
- **Detección automática de vencimiento (RN-018)**: no tiene un criterio de aceptación cuantitativo
  todavía — la periodicidad es configurable y no está fijada (`TODO` en `docs/product/business-rules.md`).
