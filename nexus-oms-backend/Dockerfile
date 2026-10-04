# =============================================================================
# Nexus OMS Backend — Production Dockerfile
# Spring Boot 3 + Java 17, multi-stage build
# =============================================================================

# ---- Stage 1: Build with Maven ----
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy pom first for layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -q || true

# Copy source and build the fat JAR
COPY src src/
RUN mvn package -Dmaven.test.skip=true -q

# ---- Stage 2: Runtime (slim JRE) ----
# NOTE: use non-alpine JRE — the -alpine variant has no arm64 build
FROM eclipse-temurin:17-jre
WORKDIR /app

# Patch OS packages in the runtime layer.
# Trivy gates on HIGH/CRITICAL and the JRE image ships Debian/Ubuntu packages with
# upstream fixes, so upgrade explicitly instead of depending on when the floating
# :17-jre tag was last republished. `--no-install-recommends` keeps the layer lean.
RUN apt-get update \
 && apt-get upgrade -y --no-install-recommends \
 && rm -rf /var/lib/apt/lists/*

# Non-root user for security
RUN groupadd -r nexus && useradd -r -g nexus nexus

# Copy the built JAR
COPY --from=builder /app/target/oms-1.0.0.jar app.jar

# Run as non-root
USER nexus

EXPOSE 8080

# Production JVM tuning: container-aware memory, fast entropy, graceful shutdown
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
