# syntax=docker/dockerfile:1
# Build multi-stage : on compile avec Maven, on n'embarque qu'un JRE au final.
# Le contexte de build est la RACINE du projet (voir docker-compose.yml) car le
# module a besoin du pom.xml parent.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl eureka-server -am -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/eureka-server/target/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
