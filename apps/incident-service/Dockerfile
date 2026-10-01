# Common template for every ColdGuard service image; only the module name differs.
# Build context is the repository root (see deploy/local/docker-compose.yml).
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
# The root reactor declares every module, so all POMs must be present for any single-module build.
COPY pom.xml /workspace/pom.xml
COPY libs/coldguard-commons/pom.xml /workspace/libs/coldguard-commons/pom.xml
COPY apps/gateway/pom.xml /workspace/apps/gateway/pom.xml
COPY apps/asset-service/pom.xml /workspace/apps/asset-service/pom.xml
COPY apps/telemetry-service/pom.xml /workspace/apps/telemetry-service/pom.xml
COPY apps/incident-service/pom.xml /workspace/apps/incident-service/pom.xml
COPY apps/notification-service/pom.xml /workspace/apps/notification-service/pom.xml
COPY contracts /workspace/contracts
COPY libs/coldguard-commons/src /workspace/libs/coldguard-commons/src
COPY apps/incident-service/src /workspace/apps/incident-service/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -f /workspace/pom.xml -pl apps/incident-service -am package -DskipTests -Dspotless.check.skip=true

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# curl is needed by the Docker Compose healthcheck (Actuator over HTTP).
# GID 1000 (the base image's pre-existing "ubuntu" group) matches the primary group of the
# default single Linux user in the documented local development setup, so the group-readable,
# read-only mounted certificate files under deploy/local/certs stay readable by this container
# without widening access to other host accounts.
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --gid 1000 --create-home --home-dir /app --uid 1001 spring
COPY --from=build /workspace/apps/incident-service/target/*.jar /app/app.jar
RUN chown -R spring:1000 /app

USER spring
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
