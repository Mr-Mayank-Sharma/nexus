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
# Digest-pinned base for a deterministic OS layer. The floating :17-jre tag
# drifts between Ubuntu releases (moved to 26.04 in Oct 2026), and a live
# `apt-get upgrade` layer made the Trivy OS gate non-reproducible: results
# depended on the Ubuntu repo state at build time and on the Actions build
# cache, so the same Dockerfile could scan clean or fail depending on when the
# layer was last cached. Temurin republishes this tag regularly with patched
# packages; Dependabot opens digest-bump PRs to keep it fresh, and the Trivy
# gate verifies each bump before merge.
FROM eclipse-temurin:17-jre@sha256:207ecae0b2b104dfc6dfa763d1e9cd2041cb344800a180e24cb1a28ed533ff90
WORKDIR /app

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
