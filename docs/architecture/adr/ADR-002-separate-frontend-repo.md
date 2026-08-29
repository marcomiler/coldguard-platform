# ADR-002 Frontend en repositorio separado

## Contexto
CLAUDE.md declara explícitamente que el frontend pertenece al repositorio `coldguard-frontend`. El C4 (`docs/architecture/container-diagram.md`) modela `FE[Frontend Repo]` como un contenedor externo que consume el Gateway del backend. ADR-001 ya define el monorepo backend.

## Problema
¿La capa de presentación debe vivir dentro del monorepo backend o en un repositorio propio, dado que tiene un stack, ciclo de entrega y dependencias distintos a los del backend?

## Opciones consideradas
1. **Frontend dentro del monorepo backend** (ej. carpeta `apps/frontend`): una sola versión y despliegue conjunto, pero mezcla el stack de frontend (Node/npm) con el stack backend (Java/Maven) y acopla sus ciclos de release.
2. **Frontend en repositorio separado** (elegido): ciclos de entrega, dependencias y despliegue independientes; el frontend trata al backend como cualquier otro cliente REST externo.
3. **Monorepo poliglota con tooling compartido** (ej. Nx/Turborepo): sobreingeniería para el alcance académico del MVP.

## Decisión
La capa de presentación se desarrolla en un repositorio separado, `coldguard-frontend`, desacoplado de `coldguard-platform`. El frontend consume exclusivamente el contrato REST expuesto por el Gateway (ver ADR-008).

## Consecuencias
- El frontend depende únicamente del contrato REST del Gateway; los cambios internos de los servicios backend (gRPC, eventos) no lo afectan mientras el contrato REST externo no cambie.
- Se requiere documentar y versionar el contrato REST del Gateway (por ejemplo, OpenAPI) para que ambos repositorios evolucionen de forma coordinada; esto queda pendiente como trabajo futuro ligado a ADR-008.
- Los pipelines de CI/CD de frontend y backend son independientes.

## Riesgos
- Desincronización entre el contrato REST real del Gateway y lo que el frontend espera, si no existe documentación de contrato compartida y versionada.
- Mayor esfuerzo de coordinación entre repositorios para funcionalidades que requieren cambios en ambos lados simultáneamente.

## Related ADRs
- ADR-001 (monorepo backend).
- ADR-008 (Gateway como único punto de entrada REST consumido por el frontend).

## Evolución futura a Azure
**Confirmado**: Azure es el proveedor cloud objetivo para el despliegue planificado; el
aprovisionamiento y despliegue permanecen pendientes de ejecución. Sin impacto directo en esta
decisión: el frontend puede desplegarse en un servicio de hosting estático o SSR (por ejemplo, Azure Static Web Apps) de forma independiente del backend, siempre que consuma el mismo contrato REST del Gateway. No se crean recursos Azure como parte de esta decisión.
