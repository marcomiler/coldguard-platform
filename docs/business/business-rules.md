# Reglas de negocio

- RN-001: cada sensor tiene un perfil operativo activo.
  - El perfil operativo (umbrales, ventanas de persistencia) se gestiona mediante RF-010 / CU-011.
- RN-002: una lectura fuera de rango genera una anomalía.
- RN-003: la severidad depende de magnitud, persistencia y criticidad del activo.
  - La severidad se materializa como una combinación de impacto (RN-010) y urgencia (RN-011); ver RN-013.
- RN-004: no se crean incidentes equivalentes duplicados mientras exista uno abierto.
  - Dos anomalías son equivalentes si comparten el mismo activo, el mismo sensor y el mismo tipo de anomalía, y ya existe un incidente abierto con esa combinación.
- RN-005: una condición persistente actualiza el incidente existente.
  - "Persistente" significa que llegan nuevas lecturas fuera de rango para la misma combinación activo/sensor/tipo de anomalía del RN-004 mientras el incidente sigue abierto.
- RN-006: el SLA inicia al crear el incidente.
  - El reloj de reconocimiento se detiene con el evento `IncidentAcknowledged`. El tiempo de resolución (MTTR) se mide hasta el evento `IncidentClosed`.
- RN-007: cerrar un incidente exige causa y comentario de resolución.
  - El cierre se registra mediante CU-006 y emite el evento `IncidentClosed`.
- RN-008: toda transición relevante es auditable.
- RN-009: la criticidad del activo se clasifica en bajo, medio, alto o crítico.
  - Se registra al dar de alta o actualizar el activo (RF-011 / CU-012) y es un insumo de RN-013.
- RN-010: el impacto de una anomalía se clasifica en bajo, medio, alto o crítico, según el alcance del daño potencial (pérdida de producto, incumplimiento normativo, riesgo sobre el activo).
- RN-011: la urgencia de una anomalía se clasifica en baja, media, alta o inmediata, según la velocidad de respuesta requerida, derivada de la magnitud y persistencia de la lectura fuera de rango.
- RN-012: la prioridad del incidente (P1, P2, P3 o P4) se determina mediante la matriz impacto/urgencia:

  | Impacto \ Urgencia | Inmediata | Alta | Media | Baja |
  |---|---|---|---|---|
  | Crítico | P1 | P1 | P2 | P2 |
  | Alto | P1 | P2 | P2 | P3 |
  | Medio | P2 | P3 | P3 | P4 |
  | Bajo | P3 | P4 | P4 | P4 |

- RN-013: la criticidad del activo (RN-009) es un insumo directo del impacto (RN-010); la magnitud y persistencia de la anomalía son insumos directos de la urgencia (RN-011). No existe un mapeo directo de severidad a prioridad: la prioridad siempre se deriva de la matriz impacto/urgencia (RN-012).
- RN-014: la prioridad de un incidente puede recalcularse si cambian las condiciones que la originaron (nuevas lecturas, cambio de criticidad del activo, escalación). Todo recálculo de prioridad es una transición auditable (RN-008).
