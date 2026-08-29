# Stakeholders y actores

## Actores

- Supervisor de operaciones.
- Operador.
- Técnico de mantenimiento.
- Auditor.
- Administrador de plataforma.
- Simulador de sensores (sensor-simulator).

El Administrador de plataforma gestiona activos, sensores, perfiles operativos y asignaciones de
acceso (rol de usuario). Los demás roles mantienen separación de responsabilidades según su
función; este rol no reemplaza las capacidades de Supervisor de operaciones, Operador, Técnico de
mantenimiento o Auditor.

## Perfil: Administrador de plataforma

### Responsabilidades

1. Registrar sensores.
2. Consultar sensores y su historial.
3. Actualizar datos técnicos y configuración operativa permitida.
4. Asociar un sensor a un activo.
5. Reasignar un sensor entre activos, preservando el historial de asociaciones.
6. Cambiar el estado operativo del sensor.
7. Registrar una calibración o verificación.
8. Retirar lógicamente un sensor.
9. Gestionar activos, perfiles operativos y asignaciones de acceso.
10. Consultar la bitácora de cambios administrativos sobre sensores.

### Trazabilidad de responsabilidades a casos de uso

| Responsabilidad | Caso de uso |
|---|---|
| 1. Registrar sensores | CU-007 |
| 2. Consultar sensores y su historial | CU-013 (principal), CU-021 |
| 3. Actualizar datos técnicos y configuración operativa | CU-013 (principal) |
| 4. Asociar sensor a activo | CU-007, CU-012 |
| 5. Reasignar sensor entre activos | CU-018 (exige que el sensor esté en EN_MANTENIMIENTO) |
| 6. Cambiar estado operativo del sensor | CU-017 |
| 7. Registrar calibración o verificación | CU-019 |
| 8. Retirar lógicamente un sensor | CU-020 |
| 9. Gestionar activos, perfiles y accesos | CU-001, CU-011, CU-012, CU-013 (todos principal), CU-014 |
| 10. Consultar bitácora de cambios administrativos | CU-021 |

### Permisos y restricciones

- El Administrador de plataforma **puede** ejecutar las 10 responsabilidades de arriba sobre
  sensores, activos, perfiles operativos y asignaciones de acceso, incluyendo el registro de
  unidades (CU-001), sensores (CU-007), perfiles operativos (CU-011) y criticidad de activos
  (CU-012).
- **Queda fuera de la operación diaria de incidentes**:
  - No reconoce ni coordina la atención de un incidente (CU-004 es del Supervisor de
    operaciones, con el Operador como actor secundario).
  - No solicita ni ejecuta un escalamiento (CU-005 es del Supervisor de operaciones, con el
    Sistema como actor secundario que registra la transición técnica).
  - No cierra un incidente por sí solo ni modifica evidencia técnica de resolución (CU-006 es del
    Técnico de mantenimiento; RN-019).
  - No reemplaza la supervisión operacional del Supervisor de operaciones (consulta de métricas,
    CU-008; supervisión de incidentes y SLA) — ver "Perfil: Supervisor de operaciones" más abajo.
  - No consulta la bitácora general de auditoría de incidentes en lugar del Auditor (CU-009 sigue
    siendo del Auditor, en modo consulta, sin permisos de cambio; CU-021 es específico de
    sensores, no reemplaza CU-009).
- Todo cambio ejecutado por el Administrador de plataforma sobre un sensor (estado, calibración,
  reasignación, retiro) es auditable con usuario, fecha, motivo y valores anterior/posterior
  (RN-017, RN-008).
- Un sensor con historial de lecturas, eventos o incidentes no puede eliminarse físicamente por
  el Administrador de plataforma ni por ningún otro actor; solo puede retirarse lógicamente
  (RN-017).

## Perfil: Supervisor de operaciones

Es responsable de la coordinación operativa de incidentes; es actor formal (principal) de CU-004
y CU-005. Ya no es actor de CU-001, CU-007, CU-011 ni CU-012 (ahora Administrador de plataforma);
en CU-013 es actor secundario.

### Responsabilidades

- Es responsable de la coordinación operativa de incidentes.
- Puede reconocer incidentes (CU-004).
- Puede solicitar o confirmar el escalamiento al Técnico de mantenimiento (CU-005).
- Consulta estado, SLA y métricas operativas (CU-008).

### Restricciones

- **No puede** cerrar técnicamente un incidente ni modificar evidencia técnica de resolución
  (diagnóstico, intervención, causa, comentario de resolución) — eso es exclusivo del Técnico de
  mantenimiento (CU-006, RN-019). El Supervisor solo es informado del cierre (actor secundario de
  CU-006).
- No ejecuta el registro de organización/sede/unidad/sensores/perfiles/criticidad (CU-001,
  CU-007, CU-011, CU-012, CU-013 principal) — eso es del Administrador de plataforma; el
  Supervisor conserva CU-013 como actor secundario (consulta).

### Trazabilidad a casos de uso

| Responsabilidad | Caso de uso |
|---|---|
| Coordinación operativa de incidentes; reconocer incidentes | CU-004 (principal) |
| Solicitar o confirmar escalamiento | CU-005 (principal) |
| Consultar estado, SLA y métricas operativas | CU-008 |
| Consultar activos/sensores/perfiles (sin registrar) | CU-013 (secundario) |

## Perfil: Operador

### Responsabilidades

- Ejecuta y registra acciones operativas iniciales o de contención dentro de su ámbito.
- Puede aportar contexto operativo al incidente (CU-004, CU-006).

### Restricciones

- No reconoce, escala ni cierra incidentes como responsable principal. Es actor secundario de
  CU-004 (junto al Supervisor de operaciones, que reconoce) y contribuye con contexto en CU-006
  (sin ser actor secundario formal de ese CU — ver `docs/domain/use-cases.md`).

## Perfil: Técnico de mantenimiento

### Responsabilidades

- Atiende incidentes escalados que requieren diagnóstico o intervención técnica (notificado vía
  CU-005).
- Registra diagnóstico, intervención, causa y comentario de resolución (CU-006, RN-007).
- Es el único rol humano que puede cerrar técnicamente un incidente (CU-006, RN-019).

### Trazabilidad a casos de uso

| Responsabilidad | Caso de uso |
|---|---|
| Recibir notificación de escalamiento | CU-005 (notificado, actor secundario junto con el Sistema) |
| Diagnosticar, intervenir y cerrar técnicamente | CU-006 (principal) |
| Gestión del ciclo de vida operativo del sensor | *(no aplica — es responsabilidad del Administrador de plataforma, CU-017 a CU-020)* |

## Perfil: Auditor

- Único actor de CU-009 (consultar bitácora de auditoría), en modo **solo lectura**.
- No ejecuta transiciones de incidentes (reconocimiento, escalamiento, cierre) ni cambios sobre
  activos, sensores o accesos; consulta lo que otros roles ya registraron de forma auditable
  (RN-008).

## Trazabilidad actor → caso de uso

Casos de uso detallados en `docs/domain/use-cases.md`.

| Actor | Casos de uso |
|---|---|
| Supervisor de operaciones | CU-004 (principal, reconocer/coordinar), CU-005 (principal, solicitar/confirmar escalamiento), CU-006 (actor secundario, informado), CU-008 (principal); CU-013 (actor secundario, consulta) |
| Operador | CU-004 (actor secundario); contribuye con contexto en CU-006 sin ser actor formal |
| Técnico de mantenimiento | CU-005 (notificado, actor secundario junto con el Sistema), CU-006 (principal) |
| Auditor | CU-009, en modo consulta, sin permisos de cambio |
| Administrador de plataforma | CU-001, CU-007, CU-011, CU-012, CU-013 (todos principal), CU-014 (principal), CU-015 (principal), CU-017, CU-018, CU-019, CU-020, CU-021 (principal); fuera de la operación diaria de incidentes (CU-003 a CU-006) |
| Simulador de sensores | CU-002 |
| Sistema | CU-003 (principal), CU-005 (actor secundario: registra la transición, emite el evento y notifica, sin iniciar el escalamiento de forma autónoma), CU-022 (principal: detecta pérdida de conectividad); también ejecuta la detección automática de vencimiento de calibración (RN-018), documentada como parte de CU-017, sin CU propio |
| Todos los actores humanos | CU-016 (autenticación y autorización por rol; alcance real en APF2, ver RN-016) |
