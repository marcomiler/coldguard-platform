# Estrategia de Docker

El MVP corre localmente vía `deploy/local/docker-compose.yml` (`.claude/rules/infra.md`); no se
requiere ningún servicio cloud para ejecutarlo. Comandos de arranque en
`docs/operations/runbooks.md`. Este documento fija la estrategia de Docker Compose local
(servicios, redes, volúmenes, variables de entorno). Implementado en SPEC-001: un `Dockerfile` por
servicio con plantilla común, healthchecks de readiness, `depends_on` con `service_healthy`,
imágenes de infraestructura con versión fija, límite de memoria por servicio Java y perfil
`observability` opcional. El servicio `sensor-simulator` está en el perfil `sim` (`docker compose --profile sim up -d`), con su escenario generado por `deploy/scripts/generate-simulator-scenario.sh`.

## Servicios previstos

Uno por contenedor C4 de `docs/architecture/container-diagram.md`, más las dependencias de
infraestructura ya confirmadas en `docs/architecture/tech-stack.md`:

| Servicio Compose | Contenedor C4 / rol | Imagen prevista |
|---|---|---|
| `gateway` | Gateway | Build propio (Java 25, Spring Boot) |
| `asset-service` | Asset Service | Build propio |
| `telemetry-service` | Telemetry Service | Build propio |
| `incident-service` | Incident Service (incl. Identity & Access, Audit Log, consultas operativas) | Build propio |
| `notification-service` | Notification Service | Build propio |
| `sensor-simulator` | Sensor Simulator | Build propio |
| `postgres` | PostgreSQL (ADR-006) | Imagen oficial `postgres`, versión a fijar |
| `rabbitmq` | RabbitMQ (ADR-004) | Imagen oficial `rabbitmq`, con plugin de management |
| `mailpit` | Adaptador de notificaciones — solo pruebas locales | Imagen oficial `mailpit` |
| `prometheus` | Observabilidad — métricas | Imagen oficial `prometheus` |
| `grafana` | Observabilidad — paneles | Imagen oficial `grafana` |
| `loki` | Observabilidad — logs | Imagen oficial `loki` |
| `otel-collector` | Observabilidad — punto único OTLP de trazas | Imagen oficial `opentelemetry-collector` |
| `tempo` | Observabilidad — trazas | Imagen oficial `tempo` |
| `alloy` | Observabilidad — recolección de logs de contenedores hacia Loki | Imagen oficial `alloy` |

Topología de observabilidad (perfil `observability`, DEC-019): los servicios exportan trazas por
OTLP/HTTP a `otel-collector`, que las reenvía a `tempo`; Prometheus raspa `/actuator/prometheus` de
cada servicio y el plugin `rabbitmq_prometheus` del broker (incluida la profundidad por cola);
`alloy` lee el stdout JSON de los contenedores y lo envía a `loki`; Grafana provisiona datasources
(con enlace de `traceId` a Tempo) y tres dashboards desde `observability/grafana/`. Imágenes
fijadas: `otel/opentelemetry-collector:0.114.0`, `grafana/tempo:2.6.1`, `grafana/alloy:v1.5.1`.

## Build de imágenes (servicios propios)

- **Estrategia**: multi-stage build por servicio (etapa `build` con Maven/JDK 25, etapa `runtime`
  con solo el JRE y el artefacto empaquetado), para mantener la imagen final liviana. Un
  `Dockerfile` por módulo Maven del monorepo (ADR-001), no un `Dockerfile` compartido.
- **Tags locales**: `coldguard/<servicio>:local` para desarrollo (p. ej. `coldguard/gateway:local`).
  No se define aún un esquema de tags para publicación en un registro (no hay registro
  configurado; queda fuera de alcance del MVP local).
- **Registro de imágenes**: no aplica en el MVP local; las imágenes se construyen y ejecutan solo
  en la máquina de desarrollo. Un registro (y su estrategia de tags semánticos) queda como decisión
  futura, ligada al despliegue en Azure planificado (`docs/architecture/deployment-view.md`).

## Redes

- Una red bridge única definida por Compose (nombre a fijar al implementar, p. ej.
  `coldguard-net`), compartida por todos los servicios del stack local. No se define segmentación
  adicional (por ejemplo, red separada para observabilidad) para el MVP: mantiene la topología
  simple mientras el número de servicios sea manejable.
- Ningún servicio se expone fuera de `localhost` salvo los puertos que el desarrollador necesite
  publicar explícitamente (Gateway, Grafana, Mailpit UI, RabbitMQ management); los servicios
  internos (Asset, Telemetry, Incident, Notification) no necesitan puerto publicado al host salvo
  para depuración.

## Volúmenes

Solo para los almacenes con estado; los servicios backend son sin estado y no requieren volumen:

| Volumen | Servicio | Propósito |
|---|---|---|
| `postgres-data` | `postgres` | Persistencia de datos entre reinicios del stack local |
| `rabbitmq-data` | `rabbitmq` | Persistencia de colas/mensajes entre reinicios |
| `grafana-data` | `grafana` | Persistencia de dashboards/config de Grafana |
| `loki-data` | `loki` | Persistencia de logs indexados |

Mailpit y Prometheus pueden ejecutar sin volumen persistente para el MVP (datos efímeros
aceptables); se revisará si el equipo necesita retener histórico de métricas entre reinicios.

## Variables de entorno

- Cada servicio lee su configuración sensible (credenciales de PostgreSQL, RabbitMQ, SMTP de
  Mailpit) desde variables de entorno provistas por un archivo `.env` **no versionado**, excluido
  por `.gitignore` (DEC-010, `docs/architecture/deployment-view.md`).
- `.env.example` documenta las claves esperadas por servicio, sin valores reales — pendiente de
  crear junto con `deploy/local/docker-compose.yml` (no se crea en este cambio, que es solo
  documentación).
- Ningún secreto se documenta en este archivo, en código, en imágenes de contenedor ni en logs
  (`.claude/rules/security.md`).

## Healthchecks

Cada servicio backend expone un healthcheck (candidato: Spring Boot Actuator `/actuator/health`)
usado por Docker Compose para ordenar el arranque (`depends_on` con condición `service_healthy`)
antes de que dependan de PostgreSQL/RabbitMQ ya disponibles. El endpoint exacto y su configuración
quedan para la implementación del compose file, no para este documento de estrategia.

## Pendiente (no bloquea Sprint 1)

- Distribución de recursos (CPU/memoria) por contenedor: no definida (mismo TODO de
  `docs/architecture/deployment-view.md`).
- Colector OpenTelemetry intermedio: resuelto (`otel-collector`, perfil `observability`).
- `deploy/local/docker-compose.yml`, `Dockerfile` por servicio y `.env.example`: implementación
  futura, no incluida en este cambio de documentación.
