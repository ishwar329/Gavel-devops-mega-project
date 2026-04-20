package com.gavel.user.repository;

import com.gavel.user.model.User;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class UserRepository {

    private final DynamoDbTable<User> table;
    private final DynamoDbClient dynamoDbClient;

    public UserRepository(DynamoDbEnhancedClient enhancedClient, DynamoDbClient dynamoDbClient) {
        this.table = enhancedClient.table("Users", TableSchema.fromBean(User.class));
        this.dynamoDbClient = dynamoDbClient;
    }

    public void save(User user) {
        table.putItem(builder -> builder
                .item(user)
                .conditionExpression(software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                        .expression("attribute_not_exists(userId)")
                        .build()));
    }

    public Optional<User> findById(String userId) {
        User user = table.getItem(Key.builder().partitionValue(userId).build());
        return Optional.ofNullable(user);
    }

    public Optional<User> findByEmail(String email) {
        var index = table.index("email-index");
        var results = index.query(QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(email).build()))
                .limit(1)
                .build());
        for (var page : results) {
            if (!page.items().isEmpty()) {
                return Optional.of(page.items().get(0));
            }
        }
        return Optional.empty();
    }

    public void updateProfile(String userId, String username, String avatarUrl) {
        Map<String, AttributeValue> key = Map.of("userId", AttributeValue.fromS(userId));
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":username", AttributeValue.fromS(username));

        String updateExpr = "SET username = :username";
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            updateExpr += ", avatarUrl = :avatarUrl";
            values.put(":avatarUrl", AttributeValue.fromS(avatarUrl));
        }

        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName("Users")
                .key(key)
                .updateExpression(updateExpr)
                .expressionAttributeValues(values)
                .build());
    }

    public void updateRole(String userId, String role) {
        Map<String, AttributeValue> key = Map.of("userId", AttributeValue.fromS(userId));
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName("Users")
                .key(key)
                .updateExpression("SET #r = :role")
                .expressionAttributeNames(Map.of("#r", "role"))
                .expressionAttributeValues(Map.of(":role", AttributeValue.fromS(role)))
                .build());
    }

    public void addToWatchlist(String userId, String auctionId) {
        Map<String, AttributeValue> key = Map.of("userId", AttributeValue.fromS(userId));
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName("Users")
                .key(key)
                .updateExpression("ADD watchlist :ids")
                .expressionAttributeValues(Map.of(":ids", AttributeValue.fromSs(List.of(auctionId))))
                .conditionExpression("attribute_exists(userId)")
                .build());
    }

    public void removeFromWatchlist(String userId, String auctionId) {
        Map<String, AttributeValue> key = Map.of("userId", AttributeValue.fromS(userId));
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName("Users")
                .key(key)
                .updateExpression("DELETE watchlist :ids")
                .expressionAttributeValues(Map.of(":ids", AttributeValue.fromSs(List.of(auctionId))))
                .build());
    }

    public List<String> getWatchlist(String userId) {
        Map<String, AttributeValue> key = Map.of("userId", AttributeValue.fromS(userId));
        var response = dynamoDbClient.getItem(builder -> builder
                .tableName("Users")
                .key(key)
                .projectionExpression("watchlist"));
        if (response.item() == null || response.item().isEmpty()) {
            return List.of();
        }
        AttributeValue watchlist = response.item().get("watchlist");
        if (watchlist == null || !watchlist.hasSs()) {
            return List.of();
        }
        return watchlist.ss();
    }
}
