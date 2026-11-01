# Multi-stage Dockerfile for UEP Backend (Spring Boot + Java 21)

# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Run
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Expose application port
EXPOSE 8080

# Database credentials and provider URLs are runtime secrets, supplied by Fly.io.
ENV SPRING_PROFILES_ACTIVE=production

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]