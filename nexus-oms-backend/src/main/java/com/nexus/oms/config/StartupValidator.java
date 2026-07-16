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
            log.error("FATAL: JWT_SECRET environment variable is not set.");
            failed = true;
        }

        if (jwtSecret == null || jwtSecret.isBlank()) {
            log.error("FATAL: app.jwt.secret property is not set (NEXUS_JWT_SECRET).");
            failed = true;
        } else if (jwtSecret.length() < 32) {
            log.error("FATAL: app.jwt.secret must be at least 32 characters, but was {} characters.", jwtSecret.length());
            failed = true;
        }

        if (failed) {
            log.error("Startup validation failed. Set all required environment variables and restart.");
            SpringApplication.exit(applicationContext, () -> 1);
        } else {
            log.info("Startup validation passed: all required secrets are configured.");
        }
    }
}
