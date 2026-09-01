# Matriz de trazabilidad

Matriz requisito → caso de uso → regla de negocio → evento → prueba futura.
Fuente: `docs/domain/functional-requirements.md`, `docs/domain/use-cases.md`,
`docs/product/business-rules.md`, `docs/domain/commands-events.md`, `.claude/rules/testing.md`.

Donde la trazabilidad documental actual es ambigua o incompleta, se deja constancia en la columna
Observación en lugar de completar el vacío con un valor no confirmado por los documentos fuente.

| RF | Requisito | CU | RN | Evento(s) | Prueba futura (ver `.claude/rules/testing.md`) | Observación |
|---|---|---|---|---|---|---|
| RF-001 | Registrar organización, sede y unidad de frío | CU-001 | — | `AssetRegistered` | Prueba de integración de alta de organización/sede/unidad (fuera de RN-001..014, sin regla de dominio aislable) | `AssetRegistered` está asociado explícitamente a CU-001; sigue sin consumidor confirmado |
| RF-002 | Registrar sensores asociados | CU-007 | RN-001 | *(no catalogado)* | Unit test de dominio: sensor con perfil operativo activo (RN-001), sin infraestructura | — |
| RF-003 | Recibir telemetría simulada | CU-002 | RN-002 | `TelemetryReceived` | Contract test gRPC/mensaje (si aplica al simulador) + unit test de detección de anomalía (RN-002) | — |
| RF-004 | Evaluar lecturas contra perfil operativo | CU-002 | RN-001, RN-002 | `TelemetryThresholdBreached` | Unit test de evaluación de umbrales y ventana de persistencia (RN-001, RN-002), con matriz de casos límite (dentro/fuera de rango, borde de umbral) | — |
| RF-005 | Crear incidentes automáticos | CU-003 | RN-003, RN-004, RN-005 | `IncidentCreated` | Unit tests de dominio: severidad por magnitud/persistencia/criticidad (RN-003), no duplicación de incidente equivalente (RN-004), actualización de incidente ante condición persistente (RN-005) | El Sistema notifica según la política vigente (RF-008); no existe una matriz de notificación definida |
| RF-006 | Asignar y gestionar estados de incidentes | CU-004, CU-005, CU-006 | RN-006, RN-007, RN-014, RN-019 | `IncidentAcknowledged`, `IncidentEscalated`, `IncidentClosed` | Unit tests de reloj de SLA (RN-006), cierre con causa/comentario obligatorios (RN-007), recálculo auditable de prioridad (RN-014), rechazo de cierre por un actor distinto de Técnico de mantenimiento (RN-019); pruebas de idempotencia y redelivery para los consumidores de estos eventos (entrega al menos una vez, ADR-009) | CU-004: "Reconocer y coordinar atención de incidente", actor principal Supervisor de operaciones, secundario Operador. CU-005: actor principal Supervisor de operaciones, secundario Sistema. CU-006: actor principal Técnico de mantenimiento, secundario Supervisor de operaciones (informado); el Operador aporta contexto sin ser actor formal |
| RF-007 | Permitir al Supervisor de operaciones escalar un incidente y al sistema registrar la transición y notificar al Técnico de mantenimiento | CU-005 | RN-012, RN-014 | `IncidentEscalated` | Matriz de prueba dedicada con las 16 combinaciones impacto × urgencia (RN-012) + test de recálculo de prioridad ante cambio de condiciones (RN-014); test de que el escalamiento requiere solicitud/confirmación del Supervisor de operaciones, no un disparo autónomo del Sistema | El SLA sirve para seguimiento, priorización y medición; el MVP no declara escalamiento automático por vencimiento de SLA |
| RF-008 | Enviar notificaciones | CU-003, CU-005 | — | `NotificationRequested`, `NotificationFailed` | Pruebas de idempotencia del consumidor de notificaciones y de comportamiento ante entrega al menos una vez (RabbitMQ + Outbox, ADR-004/ADR-009) | `use-cases.md` no menciona explícitamente `NotificationRequested` en la trazabilidad de CU-003/CU-005 aunque RF-008 sí las relaciona; no existe una matriz de notificación definida |
| RF-009 | Consultar métricas operativas | CU-008 | — | No requiere evento propio: es una consulta de solo lectura/agregación sobre eventos ya catalogados (`IncidentCreated`, `IncidentAcknowledged`, `IncidentClosed` — `docs/domain/commands-events.md`), no un comando (justificación documentada, HU-017) | Criterio de prueba propuesto, **pendiente de implementar**: tests de agregación del módulo de consultas operativas (DEC-006) que calculen MTTA, MTTR y cumplimiento de SLA por prioridad a partir de esos tres eventos, coherentes con `docs/quality/sla-kpi.md`, sin valores numéricos objetivo inventados | — |
| RF-010 | Gestionar perfil operativo y umbrales | CU-011 | RN-001 | *(no catalogado)* | Unit test de configuración de perfil operativo/umbrales (RN-001) | — |
| RF-011 | Registrar criticidad del activo | CU-012 | RN-009, RN-013 | *(no catalogado; `AssetRegistered` posible candidato, no confirmado)* | Unit tests de clasificación de criticidad del activo (RN-009) y su uso como insumo directo del impacto (RN-013) | Mismo vacío que RF-001 respecto de `AssetRegistered` |
| RF-018 | Consultar la bitácora de auditoría de transiciones relevantes (solo lectura, restringido) | CU-009 | RN-008 | *(aplica a todas las transiciones auditables, no a un evento específico)* | Test de auditoría: toda transición relevante (creación, reconocimiento, escalación, cierre, recálculo de prioridad) debe quedar registrada con actor y timestamp (RN-008); test de restricción de acceso de solo lectura al módulo Audit Log | Resuelto por decisión (DEC-005): RF-018 asignado a CU-009, atendido por el módulo interno Audit Log, dentro de Incident Service (DEC-008) |
| RF-012 | Consultar y actualizar activos, sensores y perfiles operativos; incluye consultar el historial técnico y administrativo del sensor | CU-013, CU-021 | RN-001, RN-009, RN-008 | *(no catalogado; ver TODO en `docs/domain/commands-events.md`)* | Unit test de consulta/actualización de perfil operativo y criticidad (RN-001, RN-009), sin infraestructura; test de consulta de bitácora administrativa del sensor (CU-021) | Actor principal: Administrador de plataforma; Supervisor de operaciones como secundario (consulta) |
| RF-013 | Gestionar asignaciones de acceso (rol de usuario) | CU-014 | RN-008 | *(no catalogado; ver TODO en `docs/domain/commands-events.md`)* | Test de auditoría de asignación de acceso (actor, rol, timestamp), análogo al de RN-008 | Sin servicio asignado explícitamente (ver `docs/product/scope-mvp.md`) |
| RF-014 | Inyectar telemetría de prueba mediante endpoint interno protegido | CU-015 | RN-001, RN-002, RN-015 | `TelemetryReceived`, `TelemetryThresholdBreached` | Test de integración del endpoint protegido + verificación de que la telemetría inyectada sigue el mismo camino de evaluación que la del sensor-simulator (RN-015) | Actor principal: Administrador de plataforma |
| RF-015 | Autenticar usuarios y autorizar accesos por rol (RBAC) en los endpoints del backend (APF2) | CU-016 | RN-008, RN-016 | *(sin evento de dominio; autenticación no se modela como evento en este catálogo)* | Pruebas de autenticación, RBAC por endpoint y hash de contraseñas (RNF-003, ADR-007); fuera de alcance de APF1 (RN-016) | Login de APF1 es solo demostración de frontend, sin backend real (RN-016) |
| RF-016 | Gestionar el ciclo de vida operativo del sensor (estado, calibración/verificación, reasignación, retiro lógico) | CU-017, CU-018, CU-019, CU-020 | RN-017, RN-018, RN-008 | `SensorStatusChanged`, `SensorReassigned`, `SensorCalibrationRecorded`, `SensorCalibrationExpired`, `SensorRetired` | Unit tests de la máquina de estados del sensor (transiciones permitidas/no permitidas, `docs/domain/state-machines.md`); test de elegibilidad de lecturas por estado (RN-017, RN-018); test de que un sensor con historial no se elimina físicamente (RN-017); test de que CU-018 exige EN_MANTENIMIENTO; test de que registrar una calibración válida (CU-019) no reactiva por sí sola | No se creó un caso de uso propio para la detección automática de vencimiento (actor Sistema); se documenta como parte de CU-017/RN-018 y del evento `SensorCalibrationExpired` |
| RF-017 | Detectar y registrar la pérdida de conectividad de un sensor por ausencia de telemetría esperada, sin crear un incidente térmico automáticamente | CU-022 | RN-020 | `SensorConnectivityLost` | Test de detección de ausencia de telemetría dentro de la frecuencia esperada configurable; test de que no se crea un incidente como efecto directo | Frecuencia esperada configurable sin valor numérico definido |

## Verificación de cobertura

Auditoría de consistencia transversal; se reportan huérfanos sin inventar una solución.

- **Cada RF tiene al menos un CU**: verificado, RF-001 a RF-018 tienen todos al menos un CU
  asociado. RF-018 (nuevo, DEC-005) resuelve la excepción antes reportada: CU-009 (consultar
  bitácora de auditoría) ya tiene RF asignado.
- **Cada RN relevante tiene un CU o proceso del sistema**: RN-010 (clasificación de impacto) y
  RN-011 (clasificación de urgencia) no aparecen listadas en la columna RN de ninguna fila RF de
  esta matriz. No están huérfanas de proceso — son reglas de clasificación que alimentan RN-012
  (usada por CU-003 y CU-005) y RN-013 — pero no tienen una columna RN propia en esta matriz. Se
  reporta como observación de formato, no como vacío funcional: ambas están documentadas y usadas
  en `docs/product/business-rules.md`.
- **Cada evento tiene productor y propósito**: verificado contra `docs/domain/commands-events.md`
  para los 15 eventos vigentes. `AssetRegistered` ya está asociado explícitamente a CU-001
  (Resuelto por decisión); sigue sin consumidor confirmado — señalado, no resuelto aquí.
- **Sin duplicados de ID**: verificado que RF-001 a RF-018, CU-001 a CU-022 (CU-010 reservado, sin
  definir) y RN-001 a RN-020 no tienen colisiones.

## Notas

- Cualquier vacío detectado (celdas marcadas "no catalogado" u "Observación") debe resolverse
  editando los documentos fuente correspondientes.
- La columna "Prueba futura" es una referencia de alcance para el backlog de pruebas
  (`.claude/rules/testing.md`); no implica que la prueba exista todavía.
