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

# Set environment variables with defaults
ENV SPRING_PROFILES_ACTIVE=production
ENV VITE_LOCAL_MODE=false
ENV SUPABASE_DB_URL=${SUPABASE_DB_URL}
ENV SUPABASE_DB_USER=${SUPABASE_DB_USER}
ENV SUPABASE_DB_PASS=${SUPABASE_DB_PASS}

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]