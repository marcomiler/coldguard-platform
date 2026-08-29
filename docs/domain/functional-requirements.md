# Requisitos funcionales

| ID | Requisito | CU relacionados | RN relacionadas |
|---|---|---|---|
| RF-001 | Registrar organización, sede y unidad de frío | CU-001 | — |
| RF-002 | Registrar sensores asociados | CU-007 | RN-001 |
| RF-003 | Recibir telemetría simulada | CU-002 | RN-002 |
| RF-004 | Evaluar lecturas contra perfil operativo | CU-002 | RN-001, RN-002 |
| RF-005 | Crear incidentes automáticos | CU-003 | RN-003, RN-004, RN-005 |
| RF-006 | Asignar y gestionar estados de incidentes | CU-004, CU-005, CU-006 | RN-006, RN-007, RN-014 |
| RF-007 | Permitir al Supervisor de operaciones escalar un incidente y al sistema registrar la transición y notificar al Técnico de mantenimiento | CU-005 | RN-012, RN-014 |
| RF-008 | Enviar notificaciones | CU-003, CU-005 | — |
| RF-009 | Consultar métricas operativas | CU-008 | — |
| RF-010 | Gestionar perfil operativo y umbrales | CU-011 | RN-001 |
| RF-011 | Registrar criticidad del activo | CU-012 | RN-009, RN-013 |
| RF-012 | Consultar y actualizar activos, sensores y perfiles operativos; incluye consultar el historial técnico y administrativo del sensor | CU-013, CU-021 | RN-001, RN-009, RN-008 |
| RF-013 | Gestionar asignaciones de acceso (rol de usuario) | CU-014 | RN-008 |
| RF-014 | Inyectar telemetría de prueba mediante endpoint interno protegido | CU-015 | RN-001, RN-002, RN-015 |
| RF-015 | Autenticar usuarios y autorizar accesos por rol (RBAC) en los endpoints del backend (APF2) | CU-016 | RN-008, RN-016 |
| RF-016 | Gestionar el ciclo de vida operativo del sensor (cambiar estado, registrar calibración/verificación, reasignar a otro activo, retirar lógicamente) | CU-017, CU-018, CU-019, CU-020 | RN-017, RN-018, RN-008 |
| RF-017 | Detectar y registrar la pérdida de conectividad de un sensor por ausencia de telemetría dentro de la frecuencia esperada configurable, sin crear un incidente térmico automáticamente | CU-022 | RN-020 |
| RF-018 | Consultar la bitácora de auditoría de transiciones relevantes registradas por los servicios, en modo restringido de solo lectura | CU-009 | RN-008 |
