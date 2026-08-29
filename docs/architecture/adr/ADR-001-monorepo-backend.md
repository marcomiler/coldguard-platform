# ADR-001 Monorepo backend

## Contexto
ColdGuard es un proyecto académico ejecutado por un equipo pequeño, con entregables por sprint (`docs/planning/roadmap.md`). El backend está compuesto por múltiples servicios (Asset, Telemetry, Incident, Notification), contratos gRPC/Protobuf compartidos, un simulador de telemetría e infraestructura (Docker Compose, Terraform). ADR-002 ya establece que el frontend vive en un repositorio separado.

## Problema
¿Cómo se organiza el código y la documentación del backend (servicios, contratos, simulador, infraestructura) para un equipo pequeño con ciclos de sprint cortos, sin incurrir en la sobrecarga de coordinación de múltiples repositorios?

## Opciones consideradas
1. **Multi-repo por servicio** (un repo por Asset/Telemetry/Incident/Notification Service): aislamiento fuerte de despliegue, pero sobrecarga de coordinación excesiva para un equipo pequeño y un proyecto académico con ciclos de sprint cortos.
2. **Monorepo total incluyendo frontend**: máxima simplicidad de un solo repo, pero mezcla ciclos de entrega y dependencias de UI con las del backend — rechazado (ver ADR-002).
3. **Monorepo backend** (elegido): balance entre cohesión de contratos compartidos y desacoplamiento del frontend.

## Decisión
Se usa un repositorio backend central (`coldguard-platform`) que aloja: servicios backend, contratos (`contracts/`), simulador, infraestructura (`infra/`, `deploy/`) y documentación (académica, negocio, requisitos, arquitectura, operaciones).

## Consecuencias
- Los contratos gRPC/Protobuf compartidos entre servicios viven en un solo lugar, facilitando su versionado conjunto (RNF-006).
- El pipeline de CI/CD debe filtrar por servicio afectado para no reconstruir todo el monorepo en cada cambio.
- Facilita mantener ADRs, C4 y documentación de negocio sincronizados con el código real, al vivir en el mismo repositorio.

## Riesgos
- Con el crecimiento del número de servicios, el monorepo puede volverse difícil de navegar o el pipeline de CI puede volverse lento sin triggers basados en path.
- Riesgo de acoplamiento accidental entre servicios al compartir repositorio; mitigado por los límites lógicos de servicio (ADR-006) y la regla de ports/adapters de CLAUDE.md.

## Related ADRs
- ADR-002 (frontend en repositorio separado).
- ADR-003 (gRPC interno, contratos versionados dentro de este monorepo).
- ADR-006 (persistencia con ownership lógico por servicio dentro del monorepo).

## Evolución futura a Azure
**Confirmado**: Azure es el proveedor cloud objetivo para el despliegue planificado de
ColdGuard; el aprovisionamiento y despliegue permanecen pendientes de ejecución. La estructura de
monorepo no cambia con esa migración; cada servicio puede desplegarse de forma independiente (por ejemplo, como contenedores separados en Azure Container Apps o AKS) a partir de subcarpetas del mismo repositorio, sin requerir separación de repositorios. No se crean recursos Azure como parte de esta decisión.
