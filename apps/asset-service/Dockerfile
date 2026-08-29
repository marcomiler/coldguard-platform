FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY pom.xml /workspace/pom.xml
COPY apps/asset-service/pom.xml /workspace/apps/asset-service/pom.xml
COPY apps/asset-service/src /workspace/apps/asset-service/src
WORKDIR /workspace/apps/asset-service
RUN --mount=type=cache,target=/root/.m2 mvn -q -f /workspace/pom.xml -pl apps/asset-service -am package -DskipTests

FROM eclipse-temurin:25-jre AS runtime
RUN groupadd --system app && useradd --system --gid app --create-home --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/apps/asset-service/target/*.jar /app/app.jar

USER app

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
