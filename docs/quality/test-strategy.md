# Estrategia de pruebas

Las reglas obligatorias de testing del proyecto ya están definidas en `.claude/rules/testing.md`
y no se duplican aquí. Este documento las organiza en niveles, con herramientas propuestas y
cobertura objetivo — sin resultados reales, porque todavía no hay código implementado.

## Niveles de prueba

| Nivel | Qué cubre | Infraestructura | Regla de origen |
|---|---|---|---|
| Unitaria — dominio | Reglas de negocio RN-001 a RN-014 (incluida la matriz de prioridad RN-012), aisladas de infraestructura | Ninguna (sin RabbitMQ, PostgreSQL ni SDKs de Azure) | `.claude/rules/testing.md` |
| Unitaria — aplicación | Lógica de orquestación de cada servicio (comandos, validaciones) que no depende de reglas de negocio puras | Ninguna, con dobles de prueba para puertos (repositorio, publicador de eventos) | Deriva de "ports and adapters" (`.claude/rules/architecture.md`) |
| Integración | Adaptadores reales: persistencia (PostgreSQL, esquema por servicio, ADR-006), mensajería (RabbitMQ, ADR-004), consumidores de eventos | Infraestructura real en contenedor, efímera por ejecución (candidato: Testcontainers) | `.claude/rules/testing.md` |
| Contrato (gRPC) | Contratos definidos en `contracts/` — compatibilidad de mensajes/servicios entre Gateway y servicios internos, y entre Sensor Simulator y Telemetry Service | Servidor/cliente gRPC de prueba, sin infraestructura de negocio completa | `.claude/rules/testing.md`, ADR-003, RNF-006 |
| Idempotencia y redelivery | Consumidores de eventos (Notification Service, y cualquier consumidor futuro) ante entrega at-least-once (RabbitMQ + Outbox, ADR-009) | Infraestructura de mensajería real o simulada, con reenvío/duplicado forzado | `.claude/rules/testing.md`, ADR-009 |
| Matriz de prioridad | Las 16 combinaciones impacto × urgencia → P1–P4 (RN-012) | Ninguna (unitaria de dominio) | `.claude/rules/testing.md` |

## Herramientas propuestas

| Herramienta | Uso previsto |
|---|---|
| JUnit 5 | Framework base de pruebas unitarias e integración, todos los niveles |
| Mockito | Dobles de prueba para puertos (repositorio, publicador de eventos, adaptador de notificaciones) en pruebas unitarias de aplicación |
| Spring Boot Test | Pruebas de integración de componentes Spring (contexto, configuración, Actuator) |
| Testcontainers | Candidato para levantar PostgreSQL y RabbitMQ reales y efímeros en pruebas de integración; confirmación de adopción pendiente del equipo |
| Herramienta de contract testing gRPC | Candidato a definir (p. ej. pruebas de cliente/servidor gRPC generadas desde `contracts/`); no seleccionada todavía |

Ninguna herramienta de esta tabla está configurada en el repositorio todavía (sin `pom.xml` de
módulos de servicio en este momento); esta es la estrategia prevista, no una confirmación de
adopción ya implementada.

## Cobertura objetivo

Sin cifra numérica fija: `.claude/rules/documentation.md` exige marcar como pendiente/placeholder
académico cualquier objetivo de SLA/KPI no confirmado por el equipo, y una meta de cobertura
porcentual entra en esa misma categoría. Enfoque cualitativo mientras no se confirme una cifra:

- Prioridad de cobertura por **regla de negocio** (RN-001 a RN-019) y por **camino crítico** del
  proceso de 7 pasos (`docs/domain/domain-model.md`), no por porcentaje de líneas de código por
  servicio.
- La matriz de prioridad (RN-012) se considera cubierta solo cuando las 16 combinaciones
  impacto × urgencia tienen una prueba dedicada, sin excepción.
- Todo consumidor de eventos se considera cubierto solo cuando tiene prueba de idempotencia y de
  redelivery, no solo de "camino feliz".
- Una cifra numérica de cobertura (p. ej. "80% de líneas") se agregará aquí únicamente cuando el
  equipo la confirme explícitamente; hasta entonces se marca como pendiente.

## Pendiente (no bloquea Sprint 1)

- Estrategia de pruebas end-to-end entre servicios (fuera de contrato/integración por servicio):
  no definida por el equipo.
- Selección final de herramienta de contract testing gRPC y confirmación de adopción de
  Testcontainers: pendientes.
- Cifra numérica de cobertura objetivo: pendiente de confirmación del equipo, no inventada aquí.
