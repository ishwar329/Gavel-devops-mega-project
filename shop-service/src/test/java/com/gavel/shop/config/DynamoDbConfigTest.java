package com.gavel.shop.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DynamoDbConfigTest {

    @Test
    void dynamoDbClient_withoutEndpoint_createsClient() {
        DynamoDbConfig config = new DynamoDbConfig();
        ReflectionTestUtils.setField(config, "endpoint", "");
        ReflectionTestUtils.setField(config, "region", "us-east-1");

        DynamoDbClient client = config.dynamoDbClient();

        assertThat(client).isNotNull();
        client.close();
    }

    @Test
    void dynamoDbClient_withEndpoint_createsClientWithOverride() {
        DynamoDbConfig config = new DynamoDbConfig();
        ReflectionTestUtils.setField(config, "endpoint", "http://localhost:8000");
        ReflectionTestUtils.setField(config, "region", "us-east-1");

        DynamoDbClient client = config.dynamoDbClient();

        assertThat(client).isNotNull();
        client.close();
    }

    @Test
    void dynamoDbEnhancedClient_createsEnhancedClient() {
        DynamoDbConfig config = new DynamoDbConfig();
        DynamoDbClient mockClient = mock(DynamoDbClient.class);

        DynamoDbEnhancedClient enhancedClient = config.dynamoDbEnhancedClient(mockClient);

        assertThat(enhancedClient).isNotNull();
    }
}
