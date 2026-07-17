package com.nexus.oms.integration;

import com.amazonaws.services.s3.AmazonS3;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestConfiguration
@Profile("test")
public class TestMockConfiguration {

    @MockBean
    public KafkaTemplate<String, String> kafkaTemplate;

    @Bean
    public StringRedisTemplate stringRedisTemplate() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(ops.increment(anyString())).thenReturn(1L);
        when(ops.increment(anyString(), anyLong())).thenReturn(1L);
        when(template.opsForValue()).thenReturn(ops);
        when(template.expire(anyString(), anyLong(), any())).thenReturn(true);
        return template;
    }

    @Bean
    public AmazonS3 amazonS3() {
        AmazonS3 s3 = mock(AmazonS3.class);
        when(s3.listBuckets()).thenReturn(java.util.Collections.emptyList());
        when(s3.doesBucketExistV2(anyString())).thenReturn(true);
        return s3;
    }
}
