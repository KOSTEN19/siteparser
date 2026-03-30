FROM maven:3.9.9-eclipse-temurin-8 AS build
ARG MODULE
WORKDIR /workspace
COPY pom.xml .
COPY common common
COPY scheduler-service scheduler-service
COPY crawler-service crawler-service
COPY indexer-service indexer-service
COPY analytics-service analytics-service
COPY api-service api-service
RUN echo "Building module: ${MODULE}" \
    && mvn -B -pl common,${MODULE} -am clean package -DskipTests

FROM eclipse-temurin:8-jre
ARG MODULE
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/${MODULE}-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"] 