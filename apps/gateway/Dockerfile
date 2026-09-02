FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml /workspace/pom.xml
COPY apps/gateway/pom.xml /workspace/apps/gateway/pom.xml
COPY apps/asset-service/pom.xml /workspace/apps/asset-service/pom.xml
COPY apps/telemetry-service/pom.xml /workspace/apps/telemetry-service/pom.xml
COPY apps/incident-service/pom.xml /workspace/apps/incident-service/pom.xml
COPY apps/notification-service/pom.xml /workspace/apps/notification-service/pom.xml
COPY contracts /workspace/contracts
COPY apps/gateway/src /workspace/apps/gateway/src
WORKDIR /workspace/apps/gateway
RUN --mount=type=cache,target=/root/.m2 mvn -q -f /workspace/pom.xml -pl apps/gateway -am package -DskipTests

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# GID 1000 (the base image's pre-existing "ubuntu" group) matches the primary group of the
# default single Linux user in the documented local development setup, so the group-readable,
# read-only mounted certificate files under deploy/local/certs stay readable by this container
# without widening access to other host accounts.
RUN adduser --system --gid 1000 --home /app spring
COPY --from=build /workspace/apps/gateway/target/*.jar /app/app.jar
RUN chown -R spring:1000 /app

USER spring:1000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
