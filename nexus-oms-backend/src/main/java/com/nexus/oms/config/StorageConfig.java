package com.nexus.oms.config;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "storage.s3.enabled", havingValue = "true", matchIfMissing = true)
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Value("${storage.s3.endpoint:}")
    private String endpoint;

    @Value("${storage.s3.region:us-east-1}")
    private String region;

    @Value("${storage.s3.access-key:}")
    private String accessKey;

    @Value("${storage.s3.secret-key:}")
    private String secretKey;

    @Value("${storage.s3.bucket:nexus-shipping-labels}")
    private String bucket;

    @Bean
    public AmazonS3 s3Client() {
        var credentials = new BasicAWSCredentials(accessKey, secretKey);
        var builder = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withPathStyleAccessEnabled(true);

        if (endpoint != null && !endpoint.isBlank()) {
            builder.withEndpointConfiguration(
                    new AwsClientBuilder.EndpointConfiguration(endpoint, region));
        } else {
            builder.withRegion(region);
        }

        return builder.build();
    }

    public String getBucket() {
        return bucket;
    }
}
