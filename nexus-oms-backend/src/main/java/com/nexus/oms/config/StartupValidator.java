package com.nexus.oms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class StartupValidator {

    private static final Logger log = LoggerFactory.getLogger(StartupValidator.class);

    private final ApplicationContext applicationContext;

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${storage.s3.endpoint:}")
    private String s3Endpoint;

    @Value("${storage.s3.access-key:}")
    private String s3AccessKey;

    @Value("${storage.s3.secret-key:}")
    private String s3SecretKey;

    public StartupValidator(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void validate() {
        boolean failed = false;

        String dbPassword = System.getenv("DB_PASSWORD");
        if (dbPassword == null || dbPassword.isBlank()) {
            log.error("FATAL: DB_PASSWORD environment variable is not set.");
            failed = true;
        }

        String jwtEnv = System.getenv("JWT_SECRET");
        if (jwtEnv == null || jwtEnv.isBlank()) {
            log.warn("JWT_SECRET environment variable is not set; using default (not for production).");
        }

        if (jwtSecret == null || jwtSecret.isBlank()) {
            log.error("FATAL: app.jwt.secret property is not set (JWT_SECRET).");
            failed = true;
        } else if (jwtSecret.length() < 32) {
            log.error("FATAL: app.jwt.secret must be at least 32 characters, but was {} characters.", jwtSecret.length());
            failed = true;
        }

        if (s3Endpoint == null || s3Endpoint.isBlank() || s3Endpoint.contains("localhost")) {
            log.warn("S3 endpoint is localhost; not suitable for production.");
        }
        if (s3AccessKey == null || s3AccessKey.isBlank() || "minioadmin".equals(s3AccessKey)) {
            log.warn("S3 access key is default value; not suitable for production.");
        }
        if (s3SecretKey == null || s3SecretKey.isBlank() || "minioadmin".equals(s3SecretKey)) {
            log.warn("S3 secret key is default value; not suitable for production.");
        }

        if (failed) {
            log.error("Startup validation failed. Set all required environment variables and restart.");
            SpringApplication.exit(applicationContext, () -> 1);
        } else {
            log.info("Startup validation passed: all required secrets are configured.");
        }
    }
}
