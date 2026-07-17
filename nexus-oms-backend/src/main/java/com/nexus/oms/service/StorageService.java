package com.nexus.oms.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.nexus.oms.config.StorageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final AmazonS3 s3Client;
    private final String bucket;

    public StorageService(AmazonS3 s3Client, StorageConfig storageConfig) {
        this.s3Client = s3Client;
        this.bucket = storageConfig.getBucket();
    }

    public String upload(String key, byte[] data, String contentType) {
        var metadata = new ObjectMetadata();
        metadata.setContentLength(data.length);
        if (contentType != null) {
            metadata.setContentType(contentType);
        }

        try (var is = new ByteArrayInputStream(data)) {
            PutObjectResult result = s3Client.putObject(bucket, key, is, metadata);
            log.debug("Uploaded {} to s3://{}/{} (etag={})", key, bucket, key, result.getETag());
            return key;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload " + key, e);
        }
    }

    public byte[] download(String key) {
        try (S3Object s3Object = s3Client.getObject(bucket, key);
             S3ObjectInputStream is = s3Object.getObjectContent()) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to download " + key, e);
        }
    }

    public void delete(String key) {
        s3Client.deleteObject(bucket, key);
        log.debug("Deleted s3://{}/{}", bucket, key);
    }

    public boolean exists(String key) {
        return s3Client.doesObjectExist(bucket, key);
    }

    public String getUrl(String key) {
        return s3Client.getUrl(bucket, key).toString();
    }

    public String getBucket() {
        return bucket;
    }
}
