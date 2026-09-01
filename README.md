# ColdGuard Platform

Backend y documentación principal de ColdGuard.

## Objetivo
ColdGuard convierte telemetría de cadena de frío en incidentes priorizados, alertas, acciones y evidencia auditable.

## Repositorios
- `coldguard-platform`: backend, documentación, contratos, simulador, infraestructura y operación.
- `coldguard-frontend`: capa de presentación web.

## Regla de alcance
No crear recursos reales en Azure durante el MVP sin aprobación explícita.

## Estructura del monorepo (ADR-001)

```
apps/
  gateway/               # Borde REST: JWT, routing a gRPC interno, propagación de trazas (ADR-008)
  asset-service/         # Asset/Sensor/OperationalProfile (esquema `asset`, ADR-006)
  telemetry-service/     # TelemetryReading (esquema `telemetry`, ADR-006)
  incident-service/      # Incident + módulos internos Identity & Access, Audit Log, consultas
                          # operativas (DEC-004, DEC-005, DEC-006, DEC-008)
  notification-service/  # Adaptador de notificaciones desacoplado de proveedor (RF-008)
contracts/               # Contratos gRPC/Protobuf y catálogo de eventos versionados
deploy/local/            # docker-compose.yml y .env.example del stack local
infra/                   # Terraform modular, preparado para Azure, sin aplicar (RNF-007)
observability/           # Configuración de Prometheus/Grafana/Loki
simulator/                # Sensor Simulator (fuera de alcance de este scaffolding)
docs/                    # Documentación académica, de producto, dominio y arquitectura
```

### Herencia Maven

El `pom.xml` raíz vive en la raíz del repositorio (no dentro de `apps/`) y encadena a
`spring-boot-starter-parent`; cada `apps/<servicio>/pom.xml` declara `<parent>` apuntando al
`pom.xml` raíz (`relativePath: ../../pom.xml`). El criterio para dónde declarar cada dependencia
está documentado como comentario dentro del propio `pom.xml` raíz: qué va en sus `<dependencies>`
directas (solo lo que usan los 5 módulos sin excepción), qué se deja específico de un módulo, y por
qué el `spring-boot-maven-plugin` se mantiene en cada módulo y no en la raíz. Un módulo nuevo debe
seguir el mismo patrón.

Cada módulo de `apps/` sigue el mismo paquete base `com.coldguard.<servicio>` con capas
`config`, `api`, `application`, `domain`, `infrastructure` — separación por capas, sin arquitectura
hexagonal formal (DEC-008); solo los bordes externos (persistencia, mensajería, correo,
observabilidad) usan interfaz/adaptador.

## Setup local

Requisitos: JDK 25, Maven 4.1+, Docker y Docker Compose.

1. Compilar y ejecutar las pruebas del monorepo:

   ```bash
   mvn verify
   ```

2. Copiar las variables de entorno locales (nunca se versiona `.env` real):

   ```bash
   cp deploy/local/.env.example deploy/local/.env
   ```

3. Levantar el stack local (PostgreSQL, RabbitMQ, Mailpit, Prometheus, Grafana, Loki y los 5
   servicios backend):

   ```bash
   docker compose --env-file deploy/local/.env -f deploy/local/docker-compose.yml up --build
   ```

   Puertos locales: Gateway `8080`, PostgreSQL `5432`, RabbitMQ `5672`/consola `15672`, Mailpit UI
   `8025`, Prometheus `9090`, Grafana `3000`, Loki `3100`.

No se crean recursos Azure ni se ejecuta `terraform apply` como parte de este setup local
(`.claude/rules/infra.md`).


## Incident slice local run

### Regenerate gRPC sources

```bash
rm -rf apps/incident-service/target
rm -rf apps/gateway/target

mvn -pl apps/incident-service,apps/gateway clean generate-sources
```

If the IDE still shows unresolved imports, reimport the Maven project and refresh generated
sources under:

- `target/generated-sources/protobuf/java`
- `target/generated-sources/protobuf/grpc-java`

### Run locally

```bash
docker compose -f deploy/local/docker-compose.yml up -d postgres
mvn -pl apps/incident-service spring-boot:run
mvn -pl apps/gateway spring-boot:run
```

#### Down docker locally
```bash
docker compose -f deploy/local/docker-compose.yml down
```

### Quick test

```bash
curl -i -X POST http://localhost:8080/api/v1/incidents \
  -H "Content-Type: application/json" \
  -d '{"assetId":"asset-1","assetCriticality":"CRITICALITY_HIGH","sensorId":"sensor-1","anomalyType":"high-temperature","magnitude":"MAGNITUDE_HIGH","persistent":false,"correlationId":"corr-1"}'
```

## Scripts locales (bootstrap y validación)

Prerrequisitos: bash, Docker y Docker Compose, JDK 25, Maven 4.1+. En Windows, usar WSL2 — no hay soporte nativo.

### Levantar PostgreSQL (`deploy/scripts/bootstrap.sh`)

```bash
deploy/scripts/bootstrap.sh
```

Copia `deploy/local/.env` desde `.env.example` solo si no existe todavía, levanta únicamente el
contenedor `postgres` (`docker compose -f deploy/local/docker-compose.yml up -d postgres`) y
espera su healthcheck con un timeout explícito (60s por defecto, configurable con
`HEALTHCHECK_TIMEOUT_SECONDS`). No genera fuentes gRPC ni limpia contenedores/volúmenes.

Para detenerlo manualmente cuando termines:

```bash
docker compose -f deploy/local/docker-compose.yml stop postgres
```

Esto conserva los datos (volumen `postgres-data`). No ejecutes `docker compose ... down -v`
salvo que quieras borrar todos los datos locales.

### Correr tests (`deploy/scripts/run-tests.sh`)

```bash
deploy/scripts/run-tests.sh unit          # solo unitarios, sin PostgreSQL
deploy/scripts/run-tests.sh integration   # solo integración, requiere PostgreSQL local ya levantado
deploy/scripts/run-tests.sh all           # unitarios y luego integración
```

El modo `integration` requiere que `bootstrap.sh` (o el `docker compose up -d postgres` manual de
arriba) ya esté corriendo; si no detecta el contenedor `coldguard-postgres` listo, falla con un
mensaje indicando cómo levantarlo. El script no levanta PostgreSQL por sí mismo ni limpia nada al
terminar.
