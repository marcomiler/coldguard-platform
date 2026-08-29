FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY pom.xml /workspace/pom.xml
COPY apps/incident-service/pom.xml /workspace/apps/incident-service/pom.xml
COPY apps/incident-service/src /workspace/apps/incident-service/src
WORKDIR /workspace/apps/incident-service
RUN --mount=type=cache,target=/root/.m2 mvn -q -f /workspace/pom.xml -pl apps/incident-service -am package -DskipTests

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring --create-home --home-dir /app --uid 1001 spring
COPY --from=build /workspace/apps/incident-service/target/*.jar /app/app.jar
RUN chown -R spring:spring /app

USER spring
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
