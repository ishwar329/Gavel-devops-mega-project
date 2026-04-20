package com.gavel.shop.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

class S3ConfigTest {

    @Test
    void s3Client_withoutEndpoint_createsClient() {
        S3Config config = new S3Config();
        ReflectionTestUtils.setField(config, "endpoint", "");
        ReflectionTestUtils.setField(config, "accessKey", "minioadmin");
        ReflectionTestUtils.setField(config, "secretKey", "minioadmin");
        ReflectionTestUtils.setField(config, "region", "us-east-1");

        S3Client client = config.s3Client();

        assertThat(client).isNotNull();
        client.close();
    }

    @Test
    void s3Client_withEndpoint_createsClientWithOverride() {
        S3Config config = new S3Config();
        ReflectionTestUtils.setField(config, "endpoint", "http://localhost:9000");
        ReflectionTestUtils.setField(config, "accessKey", "test");
        ReflectionTestUtils.setField(config, "secretKey", "test123");
        ReflectionTestUtils.setField(config, "region", "us-east-1");

        S3Client client = config.s3Client();

        assertThat(client).isNotNull();
        client.close();
    }
}
