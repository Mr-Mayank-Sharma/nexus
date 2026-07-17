package com.nexus.oms.health;

import com.amazonaws.services.s3.AmazonS3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3HealthIndicatorTest {

    @Mock
    private AmazonS3 s3Client;

    private S3HealthIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new S3HealthIndicator(s3Client);
    }

    @Test
    void health_whenS3IsUp_returnsUp() {
        when(s3Client.listBuckets()).thenReturn(java.util.Collections.emptyList());

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
    }

    @Test
    void health_whenS3IsDown_returnsDown() {
        when(s3Client.listBuckets()).thenThrow(new RuntimeException("Connection refused"));

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
    }
}
