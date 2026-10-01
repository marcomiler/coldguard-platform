# SPEC-001 — Plataforma base y build local reproducible

## Objetivo

Dejar el monorepo en un estado donde **los 5 servicios (más el simulador de SPEC-010) compilan,
se empaquetan en imagen y arrancan con Docker Compose con healthchecks en verde**, y fijar las
piezas transversales que todos los specs posteriores reutilizan (DRY): build de contratos,
correlación, modelo de error, configuración tipada y certificados mTLS para todos los canales.

## Trazabilidad

RNF-001, RNF-002, RNF-006, RNF-008; ADR-001, ADR-003, ADR-006; DEC-010, DEC-011, DEC-012;
`docs/infrastructure/docker-strategy.md`.

## Estado actual verificado

- `apps/asset-service/Dockerfile`, `apps/telemetry-service/Dockerfile` y
  `apps/notification-service/Dockerfile` usan `eclipse-temurin:25-jdk` como etapa de build e
  invocan `mvn`, que esa imagen no incluye; además copian solo `pom.xml` raíz + el `pom.xml` del
  propio módulo, mientras que el `pom.xml` raíz declara los 5 `<modules>` → el reactor falla.
  `gateway` e `incident-service` sí usan `maven:3.9-eclipse-temurin-25` y copian los 5 POM.
- Los tres Dockerfiles rotos difieren entre sí (usuarios `app`/`spring`, GID, `chown`), sin
  `curl` para healthcheck.
- La configuración de `protobuf-maven-plugin` + `os-maven-plugin` + propiedades
  `grpc.version`/`protobuf.version` está **duplicada** en `apps/gateway/pom.xml` y
  `apps/incident-service/pom.xml`; el BOM `spring-grpc-dependencies:1.0.3` también.
- Solo `incident-service` tiene healthcheck en Compose; `gateway` depende de él pero nadie
  depende de `gateway`, `asset`, `telemetry` ni `notification` con `service_healthy`.
- gRPC de Incident Service usa el puerto por defecto 9090 → al ejecutar con
  `mvn spring-boot:run` en el host choca con Prometheus publicado en `9090`.
- `.env.example` no incluye variables de RabbitMQ para los servicios, SMTP, JWT ni puertos.
- `generate-dev-certs.sh` solo emite certificados para `gateway` (cliente) e `incident-service`
  (servidor).

## Alcance

### 1. Build Maven

1. **Root `pom.xml`**:
   - Mover a `<dependencyManagement>` el BOM `org.springframework.grpc:spring-grpc-dependencies`
     (versión actual `1.0.3`, ya verificada en DEC-012) — se elimina de cada módulo.
   - Mover a `<properties>` `grpc.version` y `protobuf.version` (valores actuales `1.77.1` /
     `4.33.4`, alineados al BOM).
   - Mover a `<build><pluginManagement>` la configuración completa de `protobuf-maven-plugin`
     (`protoSourceRoot = ${maven.multiModuleProjectDirectory}/contracts/grpc` o ruta relativa
     equivalente, `compile` + `compile-custom`) y declarar `os-maven-plugin` como extensión en la
     raíz. Cada módulo que genera stubs solo declara `<plugin><artifactId>protobuf-maven-plugin`
     sin configuración.
   - Mover a `<pluginManagement>` la configuración de `maven-surefire-plugin` con
     `<excludedGroups>${excludedGroups}</excludedGroups>` y la propiedad `excludedGroups=integration`
     (hoy solo en incident-service) para que todos los módulos compartan la convención.
   - Añadir `<module>` para `libs/coldguard-commons` **solo si se aprueba D-01**, y para
     `simulator/sensor-simulator` (SPEC-010).
   - Mantener en la raíz únicamente dependencias usadas por **todos** los módulos (criterio ya
     documentado en el propio POM); `spring-boot-starter-web` deja de ser universal si el
     simulador no expone HTTP → revisar en SPEC-010 (el simulador puede declarar
     `spring-boot-starter` + actuator mínimo).
2. **Plugin de formateo/estático** (quality gate "Formatting and static analysis pass" de
   `CLAUDE.md`): **Decisión menor requerida** — no hay herramienta elegida en el repo. Candidatas:
   Spotless (formato) y Checkstyle o Error Prone (análisis). Se recomienda Spotless con
   `palantir-java-format` o `google-java-format` en modo `check` ligado a `verify`. Verificar
   compatibilidad con Java 25 antes de fijar versión; registrar como DEC.
3. **`.editorconfig`** (hoy vacío, 0 bytes): definir indentación 4 espacios Java, 2 YAML/JSON,
   LF, UTF-8, `insert_final_newline`.

### 2. Librería técnica compartida (condicionada a D-01)

Si se aprueba D-01, crear `libs/coldguard-commons` (jar, sin `spring-boot-maven-plugin`, sin
dominio de negocio) con paquetes:

| Paquete | Contenido |
|---|---|
| `com.coldguard.commons.correlation` | `CorrelationContext` (lectura/escritura de `correlationId` en MDC), filtro servlet `CorrelationIdFilter`, `ClientInterceptor`/`ServerInterceptor` gRPC que copian `x-correlation-id` ↔ MDC, `MessagePostProcessor` AMQP |
| `com.coldguard.commons.grpc` | Interceptor de servidor que publica identidad propagada (`x-actor-id`, `x-actor-roles`) en `io.grpc.Context` (SPEC-004), utilitario de deadline por defecto |
| `com.coldguard.commons.outbox` | Entidad base/tabla, `OutboxWriter`, `OutboxRelay` (SPEC-003) |
| `com.coldguard.commons.inbox` | Registro de mensajes procesados para idempotencia (SPEC-003) |
| `com.coldguard.commons.events` | `EventEnvelope` record + serialización Jackson 3 (SPEC-002) |

Auto-configuración con `@AutoConfiguration` + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`,
cada bloque activable por propiedad (`coldguard.outbox.enabled`, etc.) para no forzar
dependencias (AMQP) en módulos que no las usan (Gateway).

Si D-01 se rechaza: el mismo diseño se replica por servicio dentro de `infrastructure/` con
nombres idénticos, y se acepta la duplicación de forma explícita en el DEC.

### 3. Dockerfiles (uno por módulo, plantilla común)

Plantilla única para todos los servicios Java (incluido el simulador), parametrizada solo por
nombre de módulo y puerto de health:

- Etapa `build`: `maven:3.9-eclipse-temurin-25` (misma imagen que hoy usan gateway/incident).
  1. Copiar **todos** los `pom.xml` (raíz, `apps/*`, `libs/*`, `simulator/*`) y `contracts/`.
  2. `mvn -q -f pom.xml -pl <módulo> -am dependency:go-offline` con cache mount de `/root/.m2`
     (capa cacheada mientras no cambien los POM).
  3. Copiar `src/` del módulo (y de `libs/*` si D-01) y `mvn -q -pl <módulo> -am package
     -DskipTests`.
- Etapa `runtime`: `eclipse-temurin:25-jre`, usuario no root uniforme (mismo UID/GID en todas
  las imágenes; conservar GID 1000 por la razón documentada en los Dockerfiles actuales: lectura
  de certificados montados), `curl` instalado solo para healthcheck, `ENTRYPOINT` con
  `JAVA_TOOL_OPTIONS` sobreescribible (p. ej. `-XX:MaxRAMPercentage=75`) definido en Compose.
- Opcional (rendimiento de arranque): usar el layout por capas de Spring Boot
  (`java -Djarmode=tools -jar app.jar extract --layers`) para que el cambio de código no
  invalide la capa de dependencias. **Pendiente de verificación** del comando exacto en la
  documentación de Spring Boot 4.1 antes de adoptarlo.
- `.dockerignore` en la raíz: excluir `**/target`, `.git`, `deploy/local/certs`, `.env*`,
  `docs/`, `.idea`, `.vscode`.

### 4. Puertos y configuración por servicio

| Servicio | HTTP (actuator / REST) | gRPC servidor | Publicado al host |
|---|---|---|---|
| gateway | 8080 | — | 8080 |
| asset-service | 8081 | 9091 | no (solo depuración opcional) |
| telemetry-service | 8082 | 9092 | no |
| incident-service | 8083 | 9093 | no |
| notification-service | 8084 | — | no |
| sensor-simulator | 8085 (actuator) | — | no |

- Fijar `spring.grpc.server.port` explícito por servicio vía `GRPC_SERVER_PORT` (elimina el
  choque con Prometheus en 9090). Actualizar `INCIDENT_SERVICE_GRPC_PORT` en Gateway y README.
- Todas las propiedades sensibles por variable de entorno, sin default distinto de los valores
  de desarrollo ya usados (`coldguard`).
- `spring.threads.virtual.enabled=true` en todos los servicios (I/O-bound). Documentar en el DEC
  de plataforma que no se usa `synchronized` alrededor de I/O bloqueante.
- `server.shutdown=graceful` y `spring.lifecycle.timeout-per-shutdown-phase` configurable, para
  que el relay de Outbox y los consumidores AMQP terminen el lote en curso.

### 5. `deploy/local/docker-compose.yml`

- Healthcheck uniforme por servicio Java: `curl -sf http://localhost:<puerto>/actuator/health/readiness`.
- `depends_on` con `condition: service_healthy` según el grafo real:
  `gateway → asset, telemetry, incident`; `telemetry → asset, rabbitmq, postgres`;
  `incident → postgres, rabbitmq`; `notification → postgres, rabbitmq, mailpit`;
  `sensor-simulator → telemetry`.
- Variables AMQP por servicio que publica/consume: `RABBITMQ_HOST`, `RABBITMQ_PORT`,
  `RABBITMQ_USER`, `RABBITMQ_PASSWORD`.
- Mailpit: healthcheck HTTP en `8025` (endpoint de readiness de Mailpit — **pendiente de
  verificación** del path exacto en su documentación) y fijar tag de imagen (hoy `latest`).
- Fijar versiones de imágenes de infraestructura (hoy `latest` en Mailpit, Prometheus, Grafana,
  Loki; `3-management` en RabbitMQ): reproducibilidad (RNF-001). Elegir versiones estables al
  implementar y registrarlas en `docker-strategy.md`.
- Límites de memoria (`mem_limit` o `deploy.resources.limits.memory`) por servicio Java para
  evitar que el stack completo agote la memoria del host.
- Perfiles Compose (`profiles:`) para arrancar subconjuntos sin editar el archivo:
  `core` (postgres, rabbitmq, mailpit + 5 servicios), `observability` (prometheus, grafana, loki,
  collector), `sim` (sensor-simulator).
- Inicialización de PostgreSQL (recomendado, refuerza ADR-006 sin cambiarlo): script en
  `deploy/local/postgres/init/` que crea un rol por servicio con permisos solo sobre su esquema
  (`asset`, `telemetry`, `incident`, `identity`, `auditlog`, `notification`). Requiere variables
  de contraseña por servicio en `.env.example`. **Si el equipo prefiere un único usuario**, se
  mantiene el actual y se documenta la limitación.

### 6. Certificados mTLS para todos los canales internos

Extender `deploy/scripts/generate-dev-certs.sh` (misma CA, mismo comportamiento `--force`):

| Identidad | Rol TLS | SAN |
|---|---|---|
| gateway | cliente | `gateway` |
| asset-service | servidor (de Gateway y Telemetry) | `asset-service`, `localhost`, `host.docker.internal`, `127.0.0.1` |
| telemetry-service | servidor (de Gateway y simulador) y cliente (hacia Asset) | `telemetry-service`, `localhost`, … |
| incident-service | servidor (de Gateway) | sin cambios |
| sensor-simulator | cliente | `sensor-simulator` |

Cada servicio servidor exige `client-auth: require` y confía solo en la CA local (patrón ya
implementado en incident-service). Configuración de bundles SSL repetida → definir un bloque YAML
común documentado y replicado por servicio (Spring no comparte YAML entre módulos sin D-01).

### 7. `.env.example`

Agregar (sin valores reales): `RABBITMQ_*` para servicios, `SMTP_HOST`/`SMTP_PORT`,
`JWT_PRIVATE_KEY_PATH`/`JWT_PUBLIC_KEY_PATH`/`JWT_TTL` (SPEC-004),
`DEMO_USERS_PASSWORD` para seed (SPEC-011), contraseñas por rol de BD si se adopta el punto 5,
`JAVA_TOOL_OPTIONS`. Cada clave con comentario de propósito.

### 8. Modelo de error y correlación (transversal)

- Gateway: respuestas de error en **Problem Details (RFC 9457)** usando el soporte nativo de
  Spring (`ProblemDetail`), con extensiones `code` (string estable, p. ej.
  `INCIDENT_ALREADY_EXISTS`) y `correlationId`. Sustituye al `ErrorResponse` ad hoc actual
  (cambio de contrato REST: documentar en SPEC-009 y avisar a `coldguard-frontend`).
- Servicios gRPC: un único `GrpcExceptionHandler` por servicio que mapea la jerarquía de
  excepciones de su contexto; los endpoints gRPC **no** capturan excepciones manualmente (hoy
  `IncidentGrpcService` llama al handler a mano además de registrarlo como bean — se elimina la
  duplicación).
- `correlationId`: generado en el Gateway si no llega header `X-Correlation-Id` (UUID); propagado
  en metadata gRPC `x-correlation-id` y header AMQP `correlation-id`; colocado en MDC por
  interceptores (no por cada método, como hoy en `IncidentGrpcService.createIncident`).

## Fuera de alcance

Observabilidad (SPEC-011), contratos nuevos (SPEC-002), CI.

## Criterios de aceptación

1. `mvn -q -DskipTests verify` desde la raíz termina con éxito con los 5 servicios (+ simulador
   cuando exista) y sin configuración de protobuf duplicada en módulos.
2. `docker compose --env-file deploy/local/.env -f deploy/local/docker-compose.yml --profile core up --build`
   deja los 5 servicios en estado `healthy`.
3. Ningún puerto gRPC queda publicado al host; Prometheus en 9090 no colisiona con ningún
   servicio ejecutado en el host.
4. Las 6 imágenes Java comparten la misma estructura de Dockerfile y usuario no root.
5. `grep -R "latest" deploy/local/docker-compose.yml` no devuelve imágenes sin versión.
6. Una petición al Gateway sin `X-Correlation-Id` devuelve el header generado en la respuesta y el
   mismo valor aparece en el log de Incident Service.

## Tareas (orden sugerido)

1. Registrar D-01 (ADR) y la elección de formateador (DEC).
2. Refactor de POM raíz + módulos (pluginManagement, BOM, propiedades).
3. Plantilla de Dockerfile + `.dockerignore`; aplicar a los 5 módulos.
4. Puertos gRPC explícitos + variables de entorno; actualizar README (sección "Incident slice
   local run") con el nuevo puerto.
5. Compose: healthchecks, `depends_on`, perfiles, versiones fijas, límites, AMQP env.
6. Certificados para todas las identidades.
7. `.env.example`, `.editorconfig`.
8. Correlación + Problem Details (si D-01: en commons; si no: en Gateway y por servicio).

## Archivos afectados

`pom.xml`, `apps/*/pom.xml`, `apps/*/Dockerfile`, `.dockerignore` (nuevo), `.editorconfig`,
`apps/*/src/main/resources/application.yml`, `deploy/local/docker-compose.yml`,
`deploy/local/.env.example`, `deploy/local/postgres/init/*.sql` (nuevo, opcional),
`deploy/scripts/generate-dev-certs.sh`, `README.md`, `docs/infrastructure/docker-strategy.md`
(deja de decir "sin implementar").

## Riesgos

- Cambiar el formato de error del Gateway rompe consumidores existentes del frontend → coordinar.
- Cambiar el puerto gRPC de Incident requiere regenerar nada (los certificados no dependen del
  puerto), pero sí actualizar `test-mtls-stack.sh` y README.
