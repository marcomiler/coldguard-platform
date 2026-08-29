FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY pom.xml /workspace/pom.xml
COPY apps/gateway/pom.xml /workspace/apps/gateway/pom.xml
COPY apps/gateway/src /workspace/apps/gateway/src
WORKDIR /workspace/apps/gateway
RUN --mount=type=cache,target=/root/.m2 mvn -q -f /workspace/pom.xml -pl apps/gateway -am package -DskipTests

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN addgroup --system spring && adduser --system --ingroup spring --home /app spring
COPY --from=build /workspace/apps/gateway/target/*.jar /app/app.jar
RUN chown -R spring:spring /app

USER spring:spring
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
