package com.gavel.init;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.URI;
import java.util.List;

public class InitTables {

    public static void main(String[] args) {
        String endpoint = System.getenv("DYNAMODB_ENDPOINT");
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "http://localhost:8000";
        }

        DynamoDbClient db = DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("local", "local")))
                .build();

        createTable(db, "Users",
                List.of(attr("userId", ScalarAttributeType.S), attr("email", ScalarAttributeType.S)),
                List.of(key("userId", KeyType.HASH)),
                List.of(gsi("email-index", List.of(key("email", KeyType.HASH)))));

        createTable(db, "Shops",
                List.of(attr("shopId", ScalarAttributeType.S), attr("ownerId", ScalarAttributeType.S)),
                List.of(key("shopId", KeyType.HASH)),
                List.of(gsi("owner_id-index", List.of(key("ownerId", KeyType.HASH)))));

        createTable(db, "Items",
                List.of(attr("itemId", ScalarAttributeType.S), attr("shopId", ScalarAttributeType.S)),
                List.of(key("itemId", KeyType.HASH)),
                List.of(gsi("shop_id-index", List.of(key("shopId", KeyType.HASH)))));

        createTable(db, "payments",
                List.of(attr("paymentId", ScalarAttributeType.S),
                        attr("auctionId", ScalarAttributeType.S),
                        attr("userId", ScalarAttributeType.S),
                        attr("createdAt", ScalarAttributeType.S)),
                List.of(key("paymentId", KeyType.HASH)),
                List.of(gsi("auction-index", List.of(key("auctionId", KeyType.HASH))),
                        gsi("user-index", List.of(key("userId", KeyType.HASH), key("createdAt", KeyType.RANGE)))));

        createTable(db, "Reviews",
                List.of(attr("reviewId", ScalarAttributeType.S),
                        attr("shopId", ScalarAttributeType.S),
                        attr("auctionId", ScalarAttributeType.S),
                        attr("reviewerId", ScalarAttributeType.S),
                        attr("createdAt", ScalarAttributeType.S)),
                List.of(key("reviewId", KeyType.HASH)),
                List.of(gsi("shop_id-index", List.of(key("shopId", KeyType.HASH), key("createdAt", KeyType.RANGE))),
                        gsi("auction_id-reviewer-index", List.of(key("auctionId", KeyType.HASH), key("reviewerId", KeyType.RANGE)))));
    }

    private static void createTable(DynamoDbClient db, String tableName,
                                    List<AttributeDefinition> attrs,
                                    List<KeySchemaElement> keySchema,
                                    List<GlobalSecondaryIndex> gsis) {
        try {
            db.createTable(CreateTableRequest.builder()
                    .tableName(tableName)
                    .attributeDefinitions(attrs)
                    .keySchema(keySchema)
                    .globalSecondaryIndexes(gsis)
                    .provisionedThroughput(ProvisionedThroughput.builder()
                            .readCapacityUnits(5L)
                            .writeCapacityUnits(5L)
                            .build())
                    .build());
            System.out.println("Created " + tableName);
        } catch (Exception e) {
            System.out.println(tableName + ": " + e.getMessage());
        }
    }

    private static AttributeDefinition attr(String name, ScalarAttributeType type) {
        return AttributeDefinition.builder().attributeName(name).attributeType(type).build();
    }

    private static KeySchemaElement key(String name, KeyType type) {
        return KeySchemaElement.builder().attributeName(name).keyType(type).build();
    }

    private static GlobalSecondaryIndex gsi(String name, List<KeySchemaElement> keys) {
        return GlobalSecondaryIndex.builder()
                .indexName(name)
                .keySchema(keys)
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                .provisionedThroughput(ProvisionedThroughput.builder()
                        .readCapacityUnits(5L)
                        .writeCapacityUnits(5L)
                        .build())
                .build();
    }
}
