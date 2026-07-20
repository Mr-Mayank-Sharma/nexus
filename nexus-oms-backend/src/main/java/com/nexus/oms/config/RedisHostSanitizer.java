package com.nexus.oms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class RedisHostSanitizer implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(RedisHostSanitizer.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String host = environment.getProperty("spring.data.redis.host");
        if (host != null && host.startsWith("redis://")) {
            String sanitized = host.substring("redis://".length());
            Map<String, Object> overrides = new HashMap<>();
            overrides.put("spring.data.redis.host", sanitized);
            environment.getPropertySources().addLast(new MapPropertySource("redisHostSanitized", overrides));
            log.warn("Stripped 'redis://' prefix from Redis host: {} -> {}", host, sanitized);
        }
    }
}
