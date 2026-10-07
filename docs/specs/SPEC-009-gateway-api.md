# SPEC-009 — Gateway: API REST completa y convenciones de borde

## Objetivo

Fijar las convenciones del borde REST (único punto de entrada, ADR-008) y el catálogo completo de
endpoints del MVP, de modo que cada spec de servicio (005–008) exponga sus rutas de forma
homogénea, sin lógica de negocio en el Gateway y sin código repetido por servicio.

## Trazabilidad

ADR-002, ADR-003, ADR-007, ADR-008; RNF-002, RNF-003, RNF-008; DEC-001 ("versionar y publicar
contratos API antes de integrar"), DEC-012, DEC-014; R-014.

## Estado actual verificado

- `IncidentController` usa directamente enums generados por protobuf en los DTO REST
  (`CRITICALITY_HIGH`, `IMPACT_HIGH`, `PRIORITY_…`): el contrato REST queda acoplado al `.proto`.
- Validación manual (`requireNonNull`) en el controlador; sin Bean Validation.
- Una excepción Java por código gRPC y por servicio (`IncidentAlreadyExistsException`,
  `IncidentNotFoundException`, …) y un `@RestControllerAdvice` específico de incidentes →
  escalaría a N servicios × M códigos.
- `ErrorResponse(error, message)` ad hoc; sin OpenAPI; sin deadlines en el stub gRPC.

## Convenciones

### Recursos y verbos

- Prefijo `/api/v1`; sustantivos en plural; acciones de negocio como sub-recursos con `POST`
  (`/incidents/{id}/acknowledgement`, `/sensors/{id}/retirement`) — no verbos en la ruta salvo el
  ya publicado `/incidents/{id}/close` (se mantiene por compatibilidad).
- Creación → `201` + header `Location`; acción sobre estado → `200` con el recurso actualizado;
  consultas → `200`.
- JSON camelCase; instantes ISO-8601 UTC; **enums REST propios** sin prefijo protobuf
  (`HIGH`, `P1`, `ACTIVE`). El mapeo REST↔proto vive en mappers del Gateway. Cambio respecto de
  la API actual de incidentes: aceptable ahora porque los prototipos de `coldguard-frontend` usan
  datos estáticos (Sprint 2); comunicarlo igualmente.
- Paginación: `?page=0&size=20` → `{ "items": [...], "page": { "page", "size", "totalElements",
  "totalPages" } }`; cursor: `?cursor=…&size=…` → `{ "items": [...], "nextCursor", "hasMore" }`.
  `size` máximo validado (configurable).

### Validación

- DTO de entrada como `record` con Bean Validation (`@NotBlank`, `@Size`, `@NotNull`,
  `@PastOrPresent`…) y `@Valid` en el controlador. El Gateway valida **forma** (campos
  obligatorios, longitudes, formatos); las **reglas de negocio** (RN-xxx) solo en los servicios
  (ADR-008).
- Límite de tamaño de cuerpo de petición configurable (protege de payloads abusivos, en especial
  `POST /telemetry/test-readings`) — **verificar** en la documentación de Spring Boot 4.1 /
  Tomcat embebido la propiedad o el filtro adecuado antes de implementarlo; además, el número de
  lecturas por lote se valida con `@Size(max = …)`.

### Errores (Problem Details, RFC 9457)

- `@RestControllerAdvice` **único** que produce `ProblemDetail` con `type`, `title`, `status`,
  `detail`, y extensiones `code` (estable) y `correlationId`.
- Traducción **genérica** de gRPC a HTTP en un solo componente (`GrpcStatusHttpMapper`), usada por
  todos los clientes:

| gRPC | HTTP | `code` |
|---|---|---|
| `INVALID_ARGUMENT` | 400 | trailer `x-error-code` o `INVALID_REQUEST` |
| `UNAUTHENTICATED` | 401 | `UNAUTHENTICATED` |
| `PERMISSION_DENIED` | 403 | `FORBIDDEN` |
| `NOT_FOUND` | 404 | trailer o `NOT_FOUND` |
| `ALREADY_EXISTS` | 409 | trailer o `ALREADY_EXISTS` (incidentes: `INCIDENT_ALREADY_EXISTS` + `existingIncidentId`) |
| `FAILED_PRECONDITION` | 409 | trailer (p. ej. `SENSOR_TRANSITION_NOT_ALLOWED`, `INCIDENT_ALREADY_CLOSED`) |
| `ABORTED` | 409 | `CONCURRENT_MODIFICATION` |
| `DEADLINE_EXCEEDED` | 504 | `UPSTREAM_TIMEOUT` |
| `UNAVAILABLE` | 503 | `UPSTREAM_UNAVAILABLE` |
| otros | 502 | `UPSTREAM_ERROR` |

- Los códigos legibles que hoy usa el frontend para incidentes (`INCIDENT_ALREADY_EXISTS`,
  `INCIDENT_NOT_FOUND`, `INCIDENT_ALREADY_CLOSED`, `INCIDENT_CLOSE_FORBIDDEN`) se conservan como
  valores de `code` vía trailer `x-error-code` emitido por Incident Service.
- Nunca se reenvía al cliente la `description` interna de un `INTERNAL`/`UNKNOWN` (puede filtrar
  detalles); se registra en log con `correlationId`.
- Se eliminan las excepciones por servicio del paquete `gateway.infrastructure` en favor de una
  única `DownstreamCallException(status, code, details)`.

### Clientes gRPC

- Un canal por servicio (`asset-service`, `telemetry-service`, `incident-service`), mTLS
  (SPEC-001), configurado en `application.yml`; stubs bloqueantes como beans singleton.
- Llamada envuelta por un componente común (`GrpcInvoker`) que aplica deadline configurable por
  canal (`coldguard.gateway.downstream.<servicio>.deadline`) y traduce excepciones — un solo lugar
  (DRY). Con hilos virtuales habilitados, los stubs bloqueantes no consumen hilos de plataforma.
- Interceptores globales: correlación (`x-correlation-id`, `traceparent` vía OpenTelemetry) e
  identidad (`x-actor-id`, `x-actor-roles`, SPEC-004).
- Sin reintentos automáticos en el Gateway para comandos (no idempotentes en general); las
  consultas pueden reintentarse una vez ante `UNAVAILABLE` (configurable). Sin circuit breaker en
  el MVP (no hay librería elegida); registrar como evolución.

### Seguridad, CORS, actuator

Según SPEC-004 (tabla RBAC deny-by-default, CORS por variable, management port separado).

### Documentación de la API (DEC-001: publicar contratos antes de integrar)

- OpenAPI 3 generado desde el código. Candidato: `springdoc-openapi` — **pendiente de verificación**
  de una versión compatible con Spring Boot 4.1.1 / Spring Framework 7 antes de adoptarlo; si no
  existe, mantener un `contracts/rest/openapi.yaml` escrito a mano y validado en revisión.
- Esquema de seguridad Bearer JWT declarado; ejemplos de error Problem Details.
- La especificación exportada se versiona en `contracts/rest/openapi.yaml` para que
  `coldguard-frontend` la consuma.

## Catálogo de endpoints (MVP local)

| Método y ruta | Servicio / RPC | Spec |
|---|---|---|
| `POST /api/v1/auth/login` | Identity `VerifyCredentials` + firma JWT | 004 |
| `GET/POST /api/v1/organizations` | Asset | 005 |
| `GET/POST /api/v1/organizations/{id}/sites` | Asset | 005 |
| `GET/POST /api/v1/assets`, `GET/PATCH /api/v1/assets/{id}` | Asset | 005 |
| `GET/POST /api/v1/sensors`, `GET/PATCH /api/v1/sensors/{id}` | Asset | 005 |
| `GET/PUT /api/v1/sensors/{id}/profile` | Asset | 005 |
| `POST /api/v1/sensors/{id}/status` | Asset `ChangeSensorStatus` | 005 |
| `POST /api/v1/sensors/{id}/calibrations` | Asset `RecordCalibration` | 005 |
| `POST /api/v1/sensors/{id}/reassignment` | Asset `ReassignSensor` | 005 |
| `POST /api/v1/sensors/{id}/retirement` | Asset `RetireSensor` | 005 |
| `GET /api/v1/sensors/{id}/history` | Asset `GetSensorHistory` | 005 |
| `GET /api/v1/sensors/{id}/readings` | Telemetry `ListReadings` | 006 |
| `GET /api/v1/sensors/connectivity` | Telemetry `ListConnectivityStatus` | 006 |
| `POST /api/v1/telemetry/test-readings` | Telemetry `IngestReadings` (`TEST_INJECTION`) | 006 |
| `GET /api/v1/incidents`, `GET /api/v1/incidents/{id}` | Incident `ListIncidents`/`GetIncident` | 007 |
| `POST /api/v1/incidents` (técnico, propiedad `technical-endpoints.enabled`) | Incident `CreateIncident` | 007 |
| `POST /api/v1/incidents/{id}/acknowledgement` | Incident `AcknowledgeIncident` | 007 |
| `POST /api/v1/incidents/{id}/escalation` | Incident `EscalateIncident` | 007 |
| `POST /api/v1/incidents/{id}/close` | Incident `CloseIncident` | 007 |
| `GET /api/v1/metrics/incidents` | Metrics `GetIncidentMetrics` | 007 |
| `GET /api/v1/audit-records` | Audit `ListAuditRecords` | 007 |
| `GET/POST /api/v1/users`, `GET /api/v1/users/{id}` | Identity | 004 |
| `POST/DELETE /api/v1/users/{id}/roles/{role}` | Identity `AssignRole`/`RevokeRole` | 004 |
| `PUT /api/v1/users/{id}/enabled` | Identity `SetUserEnabled` | 004 |

`CreateOrganization`/`CreateSite` en el catálogo de rutas y en SPEC-002 corresponden a D-13.
Roles por ruta: tabla de SPEC-004 (fuente única; este catálogo no la duplica).

## Estructura del módulo

```
com.coldguard.gateway
  api/<recurso>/      Controller + DTO request/response (records) + mapper REST↔proto
  api/error/          ProblemDetails advice, GrpcStatusHttpMapper
  config/             Security, CORS, canales gRPC, propiedades tipadas
  infrastructure/     GrpcInvoker, interceptores de identidad/correlación, JWT issuer
```

Los controladores no contienen `if` de negocio: validar forma → mapear → invocar → mapear.

## Criterios de aceptación

1. Todos los endpoints del catálogo responden según la tabla RBAC y devuelven Problem Details en
   error, con `correlationId` igual al header de respuesta `X-Correlation-Id`.
2. Ningún DTO REST importa clases de `com.coldguard.*.grpc.*` (verificable con `grep`).
3. Con un servicio interno detenido, sus rutas devuelven 503 en ≤ deadline configurado; el resto
   de rutas sigue funcionando.
4. Existe `contracts/rest/openapi.yaml` actualizado con todos los endpoints.
5. El Gateway no contiene reglas RN (revisión: ningún cálculo de prioridad, estado ni guard).

## Avance

Desde SPEC-005 (entrega 4) existe la infraestructura común y la usa el recurso Asset:
`GrpcInvoker` (deadline por servicio, `coldguard.gateway.downstream.<servicio>.deadline`),
`DownstreamCallException` única, `GrpcStatusHttpMapper` (la tabla de arriba, con el código de
negocio del trailer intacto y sin reenviar nunca la descripción de un error interno),
`ApiExceptionHandler` único con Problem Details (`code`, `correlationId`, `errors` por campo sin
eco del valor), DTO como `record` con Bean Validation y enums REST propios, paginación por
página y por cursor. El advice se aplica por paquete (`api.asset`); Incident, Identity y Auth
siguen con sus excepciones y advices propios hasta migrarlos (la lista de usuarios ya usa el sobre
de paginación común). `contracts/rest/openapi.yaml` existe, escrito a mano (no se verificó una
versión de `springdoc-openapi` compatible con Spring Boot 4.1.1): cubre todo el catálogo, con cada
operación marcada `implemented` o `planned`, y `OpenApiContractTest` impide que se separe del
código (las rutas coinciden en ambos sentidos, los roles coinciden con la cadena de seguridad real,
las referencias resuelven y ningún esquema compartido usa enums con prefijo de protocolo). Los
ejemplos de error y el esquema Bearer están declarados. Incident, Identity y Auth ya siguen las convenciones: DTO como `record` con Bean Validation y enums
REST sin prefijo (el cambio de `POST /incidents` y `POST /incidents/{id}/close` es incompatible con
la forma anterior y se comunica en `contracts/rest/openapi.yaml` 0.3.0), una sola
`DownstreamCallException` (se eliminaron las excepciones por servicio), códigos de negocio por el
trailer `x-error-code` (con `existingIncidentId` para `INCIDENT_ALREADY_EXISTS`) y el advice único
(Auth conserva el suyo solo para que todo fallo de login sea el mismo 401). El cuerpo de una
petición se limita con `RequestBodyLimitFilter` (`coldguard.gateway.max-request-body-size`, 1 MB →
413 `REQUEST_TOO_LARGE`); las consultas se reintentan `coldguard.gateway.query-retries` veces (1)
ante `UNAVAILABLE`, los comandos nunca. Pendiente: trazas con `traceparent` (SPEC-011) y el circuit
breaker (evolución).

## Tareas

1. Infraestructura común (GrpcInvoker, mapper de estados, advice, interceptores) y migración de
   las rutas de incidentes existentes a las nuevas convenciones.
2. Rutas por servicio a medida que SPEC-004 a SPEC-008 se implementan.
3. OpenAPI (tras verificar compatibilidad) y exportación a `contracts/rest/`.
4. Actualizar `container-diagram.md` (Gateway → Telemetry ahora existe por D-08).

## Riesgos

- Cambio de formato de enums y errores del API de incidentes ya publicado.
- Sin circuit breaker, un servicio lento consume hilos virtuales hasta el deadline; el deadline
  acotado es la mitigación del MVP.
