# Registro de riesgos

| ID | Riesgo | Mitigación |
|---|---|---|
| R-001 | Alcance excesivo | MVP y vertical slices |
| R-002 | Complejidad gRPC | Contratos pequeños y pruebas |
| R-003 | Costos cloud | No aplicar Terraform sin aprobación |
| R-004 | Falta de evidencia operativa | Runbooks, capturas y escenarios |
| R-005 | Mecanismo de comunicación Incident Service → Notification Service no decidido (síncrono gRPC vs. evento) | Definir ADR antes de implementar Notification Service |
| R-006 | Estrategia de persistencia no decidida (PostgreSQL compartido vs. instancia/esquema por servicio) | Definir ADR antes de implementar el primer vertical slice con persistencia |
| R-007 | Mecanismo de autenticación/autorización no decidido | Definir ADR antes del sprint de seguridad |
| R-008 | Responsabilidades del Gateway no decididas | Definir ADR antes de implementar el Gateway |
| R-009 | Publicación confiable de eventos no decidida (Transactional Outbox u otra estrategia) | Definir ADR antes de implementar publicación de eventos en Incident/Telemetry Service |
