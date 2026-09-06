# ============================================
# Multi-stage Dockerfile for SmartBank
# Builds and runs on Render, Railway, Fly.io, AWS, Azure, GCP
# ============================================

# Stage 1: Build stage
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy Maven wrapper & pom.xml first for Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
# Download dependencies
RUN ./mvnw dependency:go-offline -B || true

# Copy source code and build jar
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create a non-root user for security
RUN groupadd -r smartbank && useradd -r -g smartbank smartbank

# Copy the built jar from the build stage
COPY --from=build /app/target/smartbank-*.jar app.jar

# Set permissions
RUN chown -R smartbank:smartbank /app
USER smartbank

# Expose default port
EXPOSE 8080

# Configure environment variables
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=default

# Run the application
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
