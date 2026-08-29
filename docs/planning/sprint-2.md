# Sprint 2

Detalle del Sprint 2 resumido en `docs/planning/roadmap.md`. El alcance de este sprint es
prototipado de frontend (repositorio `coldguard-frontend`, ADR-002) y refinamiento de requisitos
y dominio en este repositorio; no incluye implementación de backend ni microservicios.

## Objetivo del sprint

Producir prototipos navegables (sin lógica de negocio real) que cubran:

- Configuración de activos, sensores y perfiles operativos.
- Ciclo de vida del sensor (estado, calibración, reasignación, retiro).
- Tablero y detalle de incidentes.
- Reconocimiento, escalamiento y cierre técnico de incidentes.
- Métricas operativas.
- Bitácora de auditoría.
- Login de demostración por roles.

Y cerrar las ambigüedades documentales detectadas al final de Sprint 1 antes de iniciar la
implementación (Sprint 3).

### Actores por capacidad prototipada

| Capacidad | Rol | Casos de uso de referencia |
|---|---|---|
| Configuración de activos, sensores y perfiles; ciclo de vida del sensor | Administrador de plataforma | CU-001, CU-007, CU-011, CU-012, CU-013, CU-014, CU-017 a CU-021 |
| Reconocimiento, coordinación y escalamiento de incidentes | Supervisor de operaciones | CU-004, CU-005 |
| Acciones de contención y aporte de contexto | Operador | CU-004 (secundario), CU-006 (contribuye sin ser actor formal) |
| Diagnóstico y cierre técnico | Técnico de mantenimiento | CU-006 |
| Consulta de métricas operativas | Supervisor de operaciones | CU-008 |
| Consulta de bitácora de auditoría | Auditor | CU-009 |
| Login y navegación protegida por rol (demostración) | Todos los actores humanos | CU-016 |

**Gap señalado, no resuelto aquí**: no existe todavía una historia de usuario dedicada al
prototipo de ciclo de vida del sensor (estado, calibración, reasignación, retiro) ni al de
configuración conjunta de activos/sensores/perfiles más allá de HU-013 (alta inicial); queda como
decisión pendiente del equipo si se agrega una historia nueva o se amplía el alcance de HU-013.

## Historias comprometidas

Ver detalle completo (criterios de aceptación, prioridad, dependencias) en `docs/product/product-backlog.md`.

| Historia | Resumen | Repositorio |
|---|---|---|
| HU-013 | Prototipo de alta de unidad y sensores | `coldguard-frontend` |
| HU-014 | Prototipo de tablero de incidentes | `coldguard-frontend` |
| HU-015 | Prototipo de métricas operativas | `coldguard-frontend` |
| HU-016 | Prototipo de bitácora de auditoría | `coldguard-frontend` |
| HU-017 | Cierre de ambigüedades detectadas en Sprint 1 | `coldguard-platform` (este repositorio) |
| HU-018 | Prototipo de login y navegación protegida por rol (demostración APF1, sin seguridad real) | `coldguard-frontend` |

HU-018 no representa un control de seguridad productivo (RN-016).

## Definición de terminado (DoD)

- Cada prototipo (HU-013 a HU-016) usa datos de ejemplo estáticos, sin llamar a un backend real.
- Cada prototipo referencia explícitamente el/los CU y RF que valida (trazabilidad hacia este repositorio).
- Ningún prototipo introduce un campo, estado o flujo que no esté cubierto por un CU o RN ya documentado;
  si el prototipo revela una necesidad nueva, se registra como propuesta de RF/CU pendiente, no se construye directamente.
- HU-017 resuelve cada vacío listado en `docs/academic/apf1-mapping.md` con una decisión explícita
  (edición del documento fuente correspondiente o un ADR nuevo), documentada con referencia al ID que afecta.
- No se implementa backend, no se crean microservicios, no se ejecuta `terraform apply`, no se crean recursos cloud.
- HU-018 (login demo) no se presenta en ningún entregable como control de seguridad productivo
  (RN-016); la seguridad real de backend (RF-015, CU-016) queda fuera de alcance de este sprint y
  de APF1 en general.

## Riesgos

| ID | Riesgo | Relevancia en Sprint 2 |
|---|---|---|
| R-001 | Alcance excesivo | El prototipo puede tentar a incluir pantallas o campos fuera del MVP; mitigar validando cada pantalla contra un CU existente |
| R-004 | Falta de evidencia operativa | Los prototipos son la primera evidencia visual del flujo end-to-end; capturarlos como parte de los entregables |
| Nuevo (candidato) | Prototipo de frontend se adelanta a una decisión de contrato REST del Gateway (ADR-008 deja pendiente OpenAPI) | Evaluar si corresponde registrar un riesgo nuevo en `risk-register.md` antes de construir los prototipos con datos "reales" de forma/contrato |

## Entregables

- Prototipos navegables en `coldguard-frontend` (fuera de este repositorio; referenciar aquí solo su existencia y enlace/commit).
- Resolución documentada de HU-017 (ediciones a `functional-requirements.md`, `use-cases.md`, `events.md`, `risk-register.md`
  o ADR nuevo, según corresponda a cada vacío).
- Actualización de `docs/academic/apf1-mapping.md` reflejando el cierre de los vacíos resueltos en este sprint.

## Retrospectiva

Pendiente de completar por el equipo al cerrar el sprint.

- **Qué funcionó bien:** _pendiente_.
- **Qué se puede mejorar:** _pendiente_.
- **Acciones para el próximo sprint:** _pendiente_ (candidato: confirmar contrato REST del Gateway antes de Sprint 3/4).
