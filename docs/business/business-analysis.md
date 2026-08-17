# Análisis de negocio

## Actores
- Supervisor de operaciones.
- Operador.
- Técnico de mantenimiento.
- Auditor.
- Administrador.
- Simulador de sensores.

## Proceso objetivo
1. Recibir telemetría.
2. Validar sensor y activo.
3. Evaluar umbrales y persistencia.
4. Crear o actualizar incidente.
5. Calcular prioridad y SLA.
6. Notificar y escalar.
7. Registrar atención y evidencia.

## Trazabilidad actor → caso de uso

| Actor | Casos de uso |
|---|---|
| Supervisor de operaciones | CU-001, CU-007, CU-008, CU-011, CU-012 |
| Operador | CU-004, CU-006 |
| Técnico de mantenimiento | CU-004 (actor secundario), CU-005 (notificado) |
| Auditor | CU-009 |
| Administrador | Pendiente — sin caso de uso definido en este MVP; requiere decisión de alcance separada (gestión de usuarios/roles) |
| Simulador de sensores | CU-002 |
| Sistema | CU-003, CU-005 |
