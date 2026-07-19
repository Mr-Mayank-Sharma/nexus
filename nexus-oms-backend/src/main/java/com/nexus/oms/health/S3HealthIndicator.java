package com.nexus.oms.health;

import com.amazonaws.services.s3.AmazonS3;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@ConditionalOnBean(AmazonS3.class)
public class S3HealthIndicator implements HealthIndicator {

    private final AmazonS3 s3Client;

    public S3HealthIndicator(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public Health health() {
        try {
            s3Client.listBuckets();
            return Health.up().build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
