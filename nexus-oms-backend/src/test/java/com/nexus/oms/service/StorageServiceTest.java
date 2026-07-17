package com.nexus.oms.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.nexus.oms.config.StorageConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private AmazonS3 s3Client;

    @Mock
    private StorageConfig storageConfig;

    private StorageService storageService;

    @BeforeEach
    void setUp() {
        when(storageConfig.getBucket()).thenReturn("test-bucket");
        storageService = new StorageService(s3Client, storageConfig);
    }

    @Test
    void upload_sendsToS3() {
        byte[] data = "label-pdf-content".getBytes();
        PutObjectResult putResult = new PutObjectResult();
        putResult.setETag("etag-123");
        when(s3Client.putObject(anyString(), anyString(), any(), any(ObjectMetadata.class)))
                .thenReturn(putResult);

        String key = storageService.upload("labels/order-123.pdf", data, "application/pdf");

        assertEquals("labels/order-123.pdf", key);
        ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(s3Client).putObject(eq("test-bucket"), eq("labels/order-123.pdf"), any(), metadataCaptor.capture());
        assertEquals(data.length, metadataCaptor.getValue().getContentLength());
        assertEquals("application/pdf", metadataCaptor.getValue().getContentType());
    }

    @Test
    void upload_withoutContentType() {
        byte[] data = "plain-data".getBytes();
        when(s3Client.putObject(anyString(), anyString(), any(), any(ObjectMetadata.class)))
                .thenReturn(new PutObjectResult());

        storageService.upload("labels/file.txt", data, null);

        ArgumentCaptor<ObjectMetadata> captor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(s3Client).putObject(anyString(), anyString(), any(), captor.capture());
        assertNull(captor.getValue().getContentType());
    }

    @Test
    void download_retrievesFromS3() {
        byte[] expected = "file-content".getBytes();
        S3Object s3Object = mock(S3Object.class);
        when(s3Object.getObjectContent())
                .thenReturn(new S3ObjectInputStream(new ByteArrayInputStream(expected), null));
        when(s3Client.getObject("test-bucket", "labels/doc.pdf")).thenReturn(s3Object);

        byte[] result = storageService.download("labels/doc.pdf");

        assertArrayEquals(expected, result);
    }

    @Test
    void delete_removesFromS3() {
        storageService.delete("labels/old.pdf");

        verify(s3Client).deleteObject("test-bucket", "labels/old.pdf");
    }

    @Test
    void exists_checksS3() {
        when(s3Client.doesObjectExist("test-bucket", "labels/doc.pdf")).thenReturn(true);

        assertTrue(storageService.exists("labels/doc.pdf"));
        verify(s3Client).doesObjectExist("test-bucket", "labels/doc.pdf");
    }

    @Test
    void exists_whenMissing_returnsFalse() {
        when(s3Client.doesObjectExist("test-bucket", "labels/missing.pdf")).thenReturn(false);

        assertFalse(storageService.exists("labels/missing.pdf"));
    }

    @Test
    void getUrl_returnsUrlString() throws Exception {
        when(s3Client.getUrl("test-bucket", "labels/doc.pdf"))
                .thenReturn(new URL("https://s3.amazonaws.com/test-bucket/labels/doc.pdf"));

        String url = storageService.getUrl("labels/doc.pdf");

        assertEquals("https://s3.amazonaws.com/test-bucket/labels/doc.pdf", url);
    }

    @Test
    void getBucket_returnsConfiguredBucket() {
        assertEquals("test-bucket", storageService.getBucket());
    }
}
