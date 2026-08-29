# Definición de Hecho (Definition of Done)

Criterios generales aplicables a **cualquier historia, de cualquier sprint**, antes de poder
marcarla como completada. Ninguna historia se marca "hecho" en `docs/product/product-backlog.md`
sin cumplir todos los puntos que le apliquen.

Este documento no marca ninguna historia como completada por sí mismo; define el criterio que se
usará cuando el equipo cierre trabajo real.

## 1. Código y pruebas (cuando la historia produce código)

- Código revisado: pull request aprobado por al menos otra persona del equipo (o por el rol que
  el equipo defina para revisión), sin comentarios bloqueantes sin resolver.
- Pruebas unitarias, de integración y de contrato correspondientes pasando, según el alcance
  definido en `.claude/rules/testing.md` (dominio sin infraestructura, idempotencia de
  consumidores, contratos gRPC, matriz de prioridad P1-P4 cuando aplique).
- Sin código comentado, `TODO` sin ticket asociado, ni credenciales o valores de configuración
  hardcodeados.

## 2. Documentación

- Documentación funcional/técnica actualizada si la historia cambia comportamiento: RF, RN, CU,
  eventos (`docs/domain/`), o un ADR si la historia concreta o modifica una decisión
  arquitectónica ya cerrada (nunca reabriéndola: una historia de implementación documenta, no
  redecide arquitectura — ver `docs/architecture/architecture-consistency-report.md`, estado
  CERRADO, y `docs/planning/decisions-log.md`).
- Ningún ID (RF, RN, RNF, CU, ADR, DEC, riesgo, épica, historia) se renumera ni se elimina al
  documentar el cambio.
- Trazabilidad verificada en `docs/domain/traceability-matrix.md`: la historia no deja un RF/CU
  huérfano ni introduce una fila sin RN/evento cuando corresponde.

## 3. Seguridad

- Sin secretos, tokens, credenciales, cadenas de conexión ni payloads sensibles expuestos en
  código, commits, logs, trazas o documentación (`.claude/rules/security.md`).
- Si la historia toca auditoría (RN-008) o accesos, la transición queda registrada con actor y
  timestamp según lo ya decidido (Identity & Access, Audit Log como módulos internos de Incident
  Service, DEC-004/DEC-005/DEC-008).

## 4. Enlaces y consistencia documental

- Si la historia modifica o crea archivos en `/docs`, se ejecuta una pasada de validación de
  enlaces Markdown sobre los archivos tocados: 0 rutas rotas nuevas.
- No se reintroduce contenido de `docs/academic/_legacy/`, `migration-report.md` ni
  `enrichment-report.md` (permanecen retirados, ver
  `docs/architecture/architecture-consistency-report.md`).

## 5. Cumplimiento de la propia historia

- Todos los criterios de aceptación declarados en la historia (Given/When/Then o lista de
  condiciones) se cumplen y son verificables por alguien distinto de quien implementó — no basta
  con la afirmación de quien hizo el trabajo.
- Si la historia es de observabilidad, infraestructura o cloud, no se afirma suscripción Azure,
  recurso aprovisionado, alerta activa, dashboard activo ni costo, salvo que exista evidencia
  verificable (commit, captura, ejecución real) — consistente con las restricciones vigentes de
  `.claude/rules/infra.md` y `CLAUDE.md`.

## 6. Cierre formal

- El campo "Estado" de la historia en `docs/product/product-backlog.md` se actualiza únicamente
  cuando **todos** los puntos anteriores que le apliquen están satisfechos, con evidencia
  verificable enlazada (commit, PR, captura, documento actualizado) — nunca por presunción.
- Una historia parcialmente cumplida no se marca "hecho"; se dejan explícitos los puntos
  pendientes en la propia historia o en el reporte de cierre del sprint correspondiente.
