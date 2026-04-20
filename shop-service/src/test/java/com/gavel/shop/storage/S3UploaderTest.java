package com.gavel.shop.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3UploaderTest {

    @Mock private S3Client s3Client;

    private S3Uploader uploader;

    @BeforeEach
    void setUp() {
        uploader = new S3Uploader(s3Client, "test-bucket");
    }

    @Test
    void upload_callsS3PutObject() {
        byte[] data = "image-data".getBytes();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        uploader.upload("images/test.jpg", "image/jpeg", new ByteArrayInputStream(data), data.length);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

        PutObjectRequest req = captor.getValue();
        assertEquals("test-bucket", req.bucket());
        assertEquals("images/test.jpg", req.key());
        assertEquals("image/jpeg", req.contentType());
        assertEquals((long) data.length, req.contentLength());
    }

    @Test
    void ensureBucket_bucketExists_doesNotCreate() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());

        uploader.ensureBucket();

        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void ensureBucket_bucketMissing_createsAndSetsPolicy() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("not found").build());
        when(s3Client.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(CreateBucketResponse.builder().build());
        when(s3Client.putBucketPolicy(any(PutBucketPolicyRequest.class)))
                .thenReturn(PutBucketPolicyResponse.builder().build());

        uploader.ensureBucket();

        verify(s3Client).createBucket(any(CreateBucketRequest.class));
        ArgumentCaptor<PutBucketPolicyRequest> policyCaptor = ArgumentCaptor.forClass(PutBucketPolicyRequest.class);
        verify(s3Client).putBucketPolicy(policyCaptor.capture());
        assertTrue(policyCaptor.getValue().policy().contains("test-bucket"));
    }
}
