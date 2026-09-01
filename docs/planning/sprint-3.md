# Sprint 3

## Objetivo del sprint
Implementar el primer Asset Service funcional con persistencia propia.

## Historias comprometidas
| Historia | Resumen | Estado |
|---|---|---|
| HU-019 | Gestión de estado, calibración y reasignación de sensores (división a/b/c a ejecutar en refinamiento) | Por estimar |
| HU-020 | Retiro lógico y consulta de historial de sensores (división a/b a ejecutar en refinamiento) | Por estimar |

## Definición de terminado (DoD)
Se acordará en el Sprint Planning de Sprint 3, junto con la división de HU-019/HU-020.

## Riesgos (candidatos, no confirmados en `risk-register.md` — requieren revisión)
- Periodicidad de vencimiento de calibración (RN-018) sin valor numérico definido (TODO en
  `docs/product/business-rules.md`) puede bloquear la transición automática a EN_MANTENIMIENTO si
  no se resuelve antes de codificar.
- La máquina de estados del sensor tiene guards no triviales (p. ej. EN_MANTENIMIENTO→ACTIVO
  exige una calibración registrada *después* de entrar a EN_MANTENIMIENTO, no basta una vigente
  previa) — riesgo de implementación incorrecta si no se testea cada transición de
  `docs/domain/state-machines.md` explícitamente.
- R-006 (estrategia de persistencia, ADR-006) sigue "pendiente de validación en implementación";
  Asset Service es el segundo servicio en aplicar el patrón de esquema lógico compartido — una
  desviación de ese patrón rompería la decisión ya tomada.

## Entregables
Asset Service, migraciones PostgreSQL propias, máquina de estados del sensor implementada.

## Retrospectiva
Pendiente.
