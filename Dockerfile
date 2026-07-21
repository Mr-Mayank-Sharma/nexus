FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app
COPY nexus-oms-backend/pom.xml .
COPY nexus-oms-backend/src src/
RUN apk add --no-cache maven && mvn package -Dmaven.test.skip=true -q

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/oms-1.0.0.jar app.jar

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
