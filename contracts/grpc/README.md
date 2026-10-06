# Contratos gRPC

Fuente única de los contratos internos sincrónicos del backend. Cada servicio genera sus stubs en
el build Maven a partir de estos archivos (`protobuf-maven-plugin`, configurado en el `pom.xml`
raíz).

## Estructura

```
common/v1/      tipos compartidos: paginación, Criticality
incident/v1/    IncidentService           servidor: incident-service   clientes: gateway
asset/v1/       AssetService              servidor: asset-service      clientes: gateway, telemetry-service
telemetry/v1/   TelemetryService          servidor: telemetry-service  clientes: gateway, sensor-simulator
identity/v1/    IdentityService           servidor: incident-service*  clientes: gateway
audit/v1/       AuditLogService           servidor: incident-service*  clientes: gateway
metrics/v1/     OperationalMetricsService servidor: incident-service*  clientes: gateway
```

\* Módulos internos de Incident Service, expuestos como servicios gRPC distintos para que cada uno
tenga su propio contrato (no son microservicios separados).

Cada módulo Maven compila solo lo que implementa o consume (`<includes>` en su `pom.xml`); el
resto se importa sin generarse.

## Convenciones

- `package com.coldguard.<contexto>.v1;` y
  `option java_package = "com.coldguard.<contexto>.grpc.v1"; option java_multiple_files = true;`.
- Enums con valor `*_UNSPECIFIED = 0` y valores prefijados con el nombre del enum. Excepciones
  deliberadas: `IncidentStatus`, `Priority` (contrato v1 ya publicado) y `Role` (vocabulario
  estable de roles del JWT).
- Instantes con `google.protobuf.Timestamp` y duraciones con `google.protobuf.Duration`. El
  contrato v1 original de Incident conserva `string` ISO-8601 en `created_at` / `closed_at` de sus
  respuestas ya publicadas.
- Actualizaciones parciales con campos `optional`; concurrencia optimista con `expected_version`.
- **Identidad del actor**: nunca es un campo de request. Llega como metadata `x-actor-id` y
  `x-actor-roles` (ADR-007). Correlación: metadata `x-correlation-id`.
- **Paginación**: `PageRequest`/`PageInfo` (offset, solo bajo volumen) y
  `CursorPageRequest`/`CursorPageInfo` (keyset, para lecturas, auditoría e historial). El tamaño
  máximo lo valida cada servidor.
- Contratos de solo lectura (`AuditLogService`) no exponen ningún RPC de escritura.
- Campos sensibles (`password`, `initial_password`) solo viajan de entrada, nunca se devuelven ni
  se registran en logs.

## Errores

Código gRPC estándar + `description` legible + trailer `x-error-code` con un código de negocio
estable que el Gateway propaga sin interpretar el texto.

| gRPC | Uso |
|---|---|
| `INVALID_ARGUMENT` | validación de campos, lote o rango demasiado grande |
| `NOT_FOUND` | recurso inexistente |
| `ALREADY_EXISTS` | duplicado (número de serie, usuario, incidente abierto equivalente) |
| `FAILED_PRECONDITION` | regla de negocio o transición no permitida |
| `ABORTED` | conflicto de versión optimista |
| `PERMISSION_DENIED` | rol no autorizado |
| `UNAUTHENTICATED` | credenciales inválidas (mensaje genérico) |
| `UNAVAILABLE` / `DEADLINE_EXCEEDED` | dependencia no disponible o lenta |

Códigos de negocio definidos hasta ahora (un código publicado nunca cambia de significado):

| Contexto | `x-error-code` |
|---|---|
| Asset | `ASSET_NOT_FOUND`, `SITE_NOT_FOUND`, `SENSOR_NOT_FOUND`, `SENSOR_SERIAL_DUPLICATED`, `ORGANIZATION_NOT_FOUND`, `PROFILE_NOT_FOUND`, `ORGANIZATION_NAME_DUPLICATED`, `SITE_NAME_DUPLICATED`, `SENSOR_TRANSITION_NOT_ALLOWED`, `CALIBRATION_EVIDENCE_REQUIRED`, `CALIBRATION_EXPIRED`, `CALIBRATION_VALIDITY_NOT_CONFIGURED`, `REASSIGNMENT_NOT_ALLOWED`, `CONCURRENT_MODIFICATION` |
| Telemetry | `SENSOR_NOT_FOUND`, `UNIT_MISMATCH`, `BATCH_TOO_LARGE`, `RANGE_TOO_WIDE` |
| Incident | `INCIDENT_ALREADY_EXISTS`, `INCIDENT_NOT_FOUND`, `INCIDENT_ALREADY_CLOSED`, `INCIDENT_ALREADY_ACKNOWLEDGED`, `INCIDENT_CLOSE_FORBIDDEN`, `INVALID_INCIDENT_REQUEST` |
| Identity | `USER_NOT_FOUND`, `USERNAME_TAKEN`, `LAST_ADMIN_REVOCATION_FORBIDDEN` |

Cada spec de servicio puede agregar códigos; se documentan aquí al hacerlo.

## Evolución y versionado

- Nunca reutilizar ni renumerar un tag; un campo eliminado se marca `reserved` (número y nombre).
- Los valores de enum nuevos se agregan al final, sin cambiar los existentes.
- Agregar campos, mensajes o RPCs es compatible. Cambiar el tipo de un campo, renombrar o eliminar
  un RPC, o cambiar el significado de un campo es incompatible: requiere un paquete nuevo
  (`.../v2/`) conviviendo con `v1` durante la migración.
- Antes de modificar un contrato publicado, comparar contra la versión anterior de `git` (tags,
  nombres y RPCs existentes deben seguir presentes y sin cambios).
