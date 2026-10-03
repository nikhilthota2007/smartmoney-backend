# ---- Build stage: compile, run the test suite, package the jar ----
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /build

# Download dependencies in their own layer so it stays cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

# "package" runs the tests first, so a failing test fails the image build
COPY src ./src
RUN mvn -B -ntp package

# ---- Runtime stage: slim JRE 21 on Alpine, running as a non-root user ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S -G spring spring
COPY --from=build /build/target/*.jar app.jar
USER spring:spring

EXPOSE 8080

# Alpine ships BusyBox wget, so the probe needs no extra packages.
# Uses $PORT when set, matching server.port=${PORT:8080} in application.properties.
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -q -O /dev/null "http://127.0.0.1:${PORT:-8080}/actuator/health" || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
