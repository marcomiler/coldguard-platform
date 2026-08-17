# Casos de uso

## CU-001 Registrar unidad
Actor: Supervisor.
Trazabilidad: RF-001.

## CU-002 Recibir telemetría
Actor: Simulador.
Trazabilidad: RF-003, RF-004; RN-001, RN-002; eventos `TelemetryReceived`, `TelemetryThresholdBreached`.

## CU-003 Crear incidente automático
Actor: Sistema.
Trazabilidad: RF-005, RF-008; RN-003, RN-004, RN-005, RN-012; evento `IncidentCreated`.

## CU-004 Atender incidente
Actor: Operador.
Actor secundario: Técnico de mantenimiento, cuando la atención requiere intervención física sobre el activo.
Trazabilidad: RF-006; RN-006; evento `IncidentAcknowledged`.

## CU-005 Escalar incidente
Actor: Sistema.
Notifica a: Técnico de mantenimiento, según la regla de escalación vigente.
Trazabilidad: RF-007, RF-008; RN-012, RN-014; evento `IncidentEscalated`.

## CU-006 Cerrar incidente
Actor: Operador.
Trazabilidad: RF-006; RN-007; evento `IncidentClosed`.

## CU-007 Registrar sensor
Actor: Supervisor.
Trazabilidad: RF-002; RN-001.

## CU-008 Consultar métricas operativas
Actor: Supervisor.
Trazabilidad: RF-009.

## CU-009 Consultar bitácora de auditoría
Actor: Auditor.
Trazabilidad: RN-008.

> Nota: CU-010 no se define en este MVP. No existe un módulo de mantenimiento preventivo; el Técnico de mantenimiento participa como actor secundario en CU-004 y como destinatario de notificación en CU-005.

## CU-011 Configurar perfil operativo y umbrales
Actor: Supervisor.
Trazabilidad: RF-010; RN-001.

## CU-012 Registrar criticidad de activo
Actor: Supervisor.
Trazabilidad: RF-011; RN-009, RN-013.
