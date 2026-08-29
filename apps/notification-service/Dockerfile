FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY pom.xml /workspace/pom.xml
COPY apps/notification-service/pom.xml /workspace/apps/notification-service/pom.xml
COPY apps/notification-service/src /workspace/apps/notification-service/src
WORKDIR /workspace/apps/notification-service
RUN --mount=type=cache,target=/root/.m2 mvn -q \
  -f /workspace/pom.xml \
  -pl apps/notification-service \
  -am package \
  -DskipTests

FROM eclipse-temurin:25-jre AS runtime

RUN groupadd --system app && useradd --system --gid app --create-home --home-dir /home/app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/apps/notification-service/target/*.jar /app/app.jar
RUN chown -R app:app /app
USER app

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
