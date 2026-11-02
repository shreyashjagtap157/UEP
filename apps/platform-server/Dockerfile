# Multi-stage UEP API image. The Maven compiler target is Java 21.
# OCI runtime secrets and external endpoints are injected by the deployment environment.

FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /app/var/uploads /app/var/recordings \
    && chown -R 10001:10001 /app
COPY --from=build --chown=10001:10001 /app/target/*.jar /app/app.jar

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=production

USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
