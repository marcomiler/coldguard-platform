FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY pom.xml /workspace/pom.xml
COPY apps/telemetry-service/pom.xml /workspace/apps/telemetry-service/pom.xml
COPY apps/telemetry-service/src /workspace/apps/telemetry-service/src
WORKDIR /workspace/apps/telemetry-service
RUN --mount=type=cache,target=/root/.m2 mvn -q -f /workspace/pom.xml -pl apps/telemetry-service -am package -DskipTests

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring --create-home --home-dir /app spring
COPY --from=build --chown=spring:spring /workspace/apps/telemetry-service/target/*.jar /app/app.jar
USER spring:spring
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
