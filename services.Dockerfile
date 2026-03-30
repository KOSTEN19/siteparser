FROM maven:3.9.9-eclipse-temurin-8 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common common
COPY scheduler-service scheduler-service
COPY crawler-service crawler-service
COPY indexer-service indexer-service
COPY analytics-service analytics-service
COPY api-service api-service
RUN mvn -B -DskipTests package

FROM eclipse-temurin:8-jre AS scheduler-runtime
WORKDIR /app
COPY --from=build /workspace/scheduler-service/target/scheduler-service-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM eclipse-temurin:8-jre AS crawler-runtime
WORKDIR /app
COPY --from=build /workspace/crawler-service/target/crawler-service-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM eclipse-temurin:8-jre AS indexer-runtime
WORKDIR /app
COPY --from=build /workspace/indexer-service/target/indexer-service-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM eclipse-temurin:8-jre AS analytics-runtime
WORKDIR /app
COPY --from=build /workspace/analytics-service/target/analytics-service-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM eclipse-temurin:8-jre AS api-runtime
WORKDIR /app
COPY --from=build /workspace/api-service/target/api-service-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]