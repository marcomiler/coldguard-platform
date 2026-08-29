# Registro de riesgos

Dos naturalezas de riesgo conviven en esta tabla: **controles de proceso** (R-001 a R-004, ya
aplicados como práctica continua del proyecto) y **decisiones arquitectónicas** (R-005 a R-009).

| ID | Riesgo | Mitigación | Naturaleza | Estado | ADR relacionada |
|---|---|---|---|---|---|
| R-001 | Alcance excesivo | MVP y vertical slices | Control de proceso, continuo | Abierto (control continuo) | — |
| R-002 | Complejidad gRPC | Contratos pequeños y pruebas | Control de proceso, continuo | Abierto (control continuo) | ADR-003 |
| R-003 | Costos cloud | No aplicar Terraform sin aprobación | Control de proceso, continuo | Abierto (control continuo) | — (`.claude/rules/infra.md`) |
| R-004 | Falta de evidencia operativa | Runbooks, capturas y escenarios | Control de proceso, continuo | Abierto (control continuo) | — |
| R-005 | Mecanismo de comunicación Incident Service → Notification Service no decidido (síncrono gRPC vs. evento) | Definir ADR antes de implementar Notification Service | Decisión arquitectónica | Mitigado por decisión; pendiente validación en implementación | ADR-005 |
| R-006 | Estrategia de persistencia no decidida (PostgreSQL compartido vs. instancia/esquema por servicio) | Definir ADR antes de implementar el primer vertical slice con persistencia | Decisión arquitectónica | Mitigado por decisión; pendiente validación en implementación | ADR-006 |
| R-007 | Mecanismo de autenticación/autorización no decidido | Definir ADR antes del sprint de seguridad | Decisión arquitectónica | Mitigado por decisión; pendiente validación en implementación | ADR-007 |
| R-008 | Responsabilidades del Gateway no decididas | Definir ADR antes de implementar el Gateway | Decisión arquitectónica | Mitigado por decisión; pendiente validación en implementación | ADR-008 |
| R-009 | Publicación confiable de eventos no decidida (Transactional Outbox u otra estrategia) | Definir ADR antes de implementar publicación de eventos en Incident/Telemetry Service | Decisión arquitectónica | Mitigado por decisión; pendiente validación en implementación | ADR-009 |
| R-010 | Ambigüedad del proveedor cloud (Azure vs. AWS vs. por decidir) | Confirmar el proveedor cloud objetivo antes de cualquier trabajo de Terraform o de servicios administrados | Decisión de producto/arquitectura | **Mitigado por decisión: Azure confirmado como proveedor objetivo**, con Azure Container Apps y Azure Database for PostgreSQL Flexible Server como plataformas planificadas de cómputo/persistencia (DEC-002); pendiente de ejecución (aprovisionamiento y despliegue) | — (decisión confirmada; sin ADR de selección de proveedor todavía) |
| R-011 | Configuración incorrecta de telemetría o exposición de datos sensibles en logs/trazas/eventos (tokens, credenciales, cadenas de conexión, secretos, payloads completos) | Reglas documentadas de campos permitidos/prohibidos y telemetría de negocio mínima (`docs/operations/observability-strategy.md`, RNF-008, `.claude/rules/security.md`); revisión de código antes de habilitar exportación a Application Insights/Azure Monitor | Riesgo técnico/operativo | Abierto — mitigación documental vigente; sin verificación de implementación | — |
| R-012 | Protocolo de ingesta de telemetría sin decidir podía bloquear el diseño de Telemetry Service en Sprint 5 | Confirmar el protocolo antes del sprint de implementación | Decisión de arquitectura | **Mitigado por decisión: DEC-003** — gRPC para Sensor Simulator, REST interno protegido para CU-015 | ADR-003 |
| R-013 | Proveedor productivo de notificaciones sin decidir podía retrasar Sprint 6 | Confirmar el proveedor antes del sprint de notificaciones | Decisión de producto/arquitectura | **Mitigado por decisión: DEC-007** — Azure Communication Services Email planificado para Sprint 6; Mailpit permanece solo para pruebas locales | — |
| R-014 | RBAC declarado (ADR-007) pero no operacionalizado: sin mapeo explícito rol→endpoint, puede llegar a Sprint 4 sin criterio de implementación | Definir el mapeo rol→endpoint antes de implementar el Gateway y los endpoints protegidos en Sprint 4 | Riesgo técnico/operativo | Abierto — depende de implementación, no de una decisión documental adicional | ADR-007 |

**Sobre R-005 a R-009**: cada ADR ya resolvió la incertidumbre de diseño que originó el riesgo;
por eso su estado cambia de "abierto" a "Mitigado por decisión; pendiente validación en
implementación". No se cierran del todo porque la validación real (que el código y las pruebas
confirmen que la decisión funciona como se diseñó) todavía no existe — no se inventa esa
evidencia aquí.

**Confirmación de cierre por decisión (auditoría de planificación)**: los tres riesgos de
arquitectura señalados como potencialmente abiertos —mensajería en Azure (RabbitMQ), gestión de
secretos e Identity & Access— **no tienen una fila propia en este registro**; quedaron resueltos
directamente por DEC-002, DEC-008, DEC-009 y DEC-010 sin haber llegado a registrarse como riesgo
abierto en ningún momento. No hay ninguna fila de esta tabla que los describa como "Abierto"; no
se requiere corrección.

## Detalle de riesgos abiertos (causa, impacto, probabilidad, dueño, seguimiento)

Solo para los riesgos con Estado "Abierto" en la tabla de arriba (R-001 a R-004, R-011, R-014). Los
riesgos "Mitigado por decisión" (R-005 a R-010, R-012, R-013) no requieren este detalle: ya no
están en seguimiento activo de causa/probabilidad, solo pendientes de validación en
implementación (ver nota arriba).

| ID | Causa | Impacto | Probabilidad | Dueño (rol) | Sprint de seguimiento |
|---|---|---|---|---|---|
| R-001 | Ambición del alcance académico del MVP frente al tiempo disponible del equipo | Alto — retrasa la entrega de sprints y puede forzar recortes de último momento | Media | Product Owner | Continuo (revisión en cada Sprint Planning) |
| R-002 | Curva de aprendizaje del equipo con gRPC/Protocol Buffers y generación de stubs | Medio — puede retrasar los contratos internos de Sprint 4/5 | Media | Tech Lead / Arquitecto | Sprint 4 |
| R-003 | Riesgo de aprovisionar recursos Azure sin control de presupuesto ni aprobación | Alto (financiero) si se materializara — mitigado por control de proceso, no por diseño técnico | Baja (control de proceso ya vigente: sin `terraform apply` sin aprobación explícita) | Product Owner + Tech Lead (aprobación conjunta) | Sprint 7 (única ventana donde se planifica Azure) |
| R-004 | Riesgo de cerrar sprints sin runbooks, capturas ni evidencia operativa verificable | Medio — afecta la evaluación académica (APF) y la trazabilidad operativa | Media | Scrum Master | Continuo (revisión en cada cierre de sprint) |
| R-011 | Instrumentación de OpenTelemetry mal configurada podría filtrar tokens, credenciales o payloads completos en logs/trazas | Alto (seguridad) si ocurriera | Media — mitigación documental vigente (RNF-008, reglas de campos permitidos/prohibidos), sin verificación de implementación todavía | Tech Lead (seguridad) | Sprint 5 (instrumentación local, HU-021) |
| R-014 | ADR-007 fija el mecanismo de RBAC pero no el mapeo concreto rol→endpoint | Alto — bloquea la implementación de seguridad real si no se resuelve antes de construir los endpoints protegidos | Media | Tech Lead / Arquitecto | Sprint 4 |
