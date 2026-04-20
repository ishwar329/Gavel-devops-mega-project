package com.gavel.user.repository;

import com.gavel.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class UserRepositoryTest {

    private DynamoDbEnhancedClient enhancedClient;
    private DynamoDbClient dynamoDbClient;
    private DynamoDbTable<User> table;
    private DynamoDbIndex<User> emailIndex;
    private UserRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        enhancedClient = mock(DynamoDbEnhancedClient.class);
        dynamoDbClient = mock(DynamoDbClient.class);
        table = mock(DynamoDbTable.class);
        emailIndex = mock(DynamoDbIndex.class);

        when(enhancedClient.table(eq("Users"), any(TableSchema.class))).thenReturn(table);
        when(table.index("email-index")).thenReturn(emailIndex);

        repository = new UserRepository(enhancedClient, dynamoDbClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void save_insertsUserWithCondition() {
        User user = new User();
        user.setUserId("u-1");
        user.setEmail("test@example.com");

        repository.save(user);

        verify(table).putItem(any(java.util.function.Consumer.class));
    }

    @Test
    void findById_returnsUserWhenExists() {
        User user = new User();
        user.setUserId("u-1");
        when(table.getItem(any(Key.class))).thenReturn(user);

        Optional<User> result = repository.findById("u-1");

        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo("u-1");
        verify(table).getItem(any(Key.class));
    }

    @Test
    void findById_returnsEmptyWhenNotFound() {
        when(table.getItem(any(Key.class))).thenReturn(null);

        Optional<User> result = repository.findById("u-999");

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByEmail_returnsUserWhenExists() {
        User user = new User();
        user.setEmail("alice@example.com");

        PageIterable<User> pageIterable = mock(PageIterable.class);
        software.amazon.awssdk.enhanced.dynamodb.model.Page<User> page = mock(software.amazon.awssdk.enhanced.dynamodb.model.Page.class);
        when(page.items()).thenReturn(List.of(user));
        when(pageIterable.iterator()).thenReturn((java.util.Iterator) List.of(page).iterator());
        when(emailIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);

        Optional<User> result = repository.findByEmail("alice@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByEmail_returnsEmptyWhenNotFound() {
        PageIterable<User> pageIterable = mock(PageIterable.class);
        software.amazon.awssdk.enhanced.dynamodb.model.Page<User> page = mock(software.amazon.awssdk.enhanced.dynamodb.model.Page.class);
        when(page.items()).thenReturn(List.of());
        when(pageIterable.iterator()).thenReturn((java.util.Iterator) List.of(page).iterator());
        when(emailIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);

        Optional<User> result = repository.findByEmail("nobody@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void updateProfile_updatesUsernameOnly() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        repository.updateProfile("u-1", "newname", null);

        verify(dynamoDbClient).updateItem(argThat((UpdateItemRequest req) ->
                req.tableName().equals("Users") &&
                        req.updateExpression().equals("SET username = :username") &&
                        req.expressionAttributeValues().containsKey(":username") &&
                        !req.expressionAttributeValues().containsKey(":avatarUrl")));
    }

    @Test
    void updateProfile_updatesUsernameAndAvatar() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        repository.updateProfile("u-1", "newname", "https://cdn.example.com/avatar.jpg");

        verify(dynamoDbClient).updateItem(argThat((UpdateItemRequest req) ->
                req.tableName().equals("Users") &&
                        req.updateExpression().contains("username = :username") &&
                        req.updateExpression().contains("avatarUrl = :avatarUrl") &&
                        req.expressionAttributeValues().containsKey(":username") &&
                        req.expressionAttributeValues().containsKey(":avatarUrl")));
    }

    @Test
    void updateRole_updatesUserRole() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        repository.updateRole("u-1", "admin");

        verify(dynamoDbClient).updateItem(argThat((UpdateItemRequest req) ->
                req.tableName().equals("Users") &&
                        req.updateExpression().equals("SET #r = :role") &&
                        req.expressionAttributeNames().get("#r").equals("role") &&
                        req.expressionAttributeValues().get(":role").s().equals("admin")));
    }

    @Test
    void addToWatchlist_addsAuctionId() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        repository.addToWatchlist("u-1", "a-100");

        verify(dynamoDbClient).updateItem(argThat((UpdateItemRequest req) ->
                req.tableName().equals("Users") &&
                        req.updateExpression().equals("ADD watchlist :ids") &&
                        req.conditionExpression().equals("attribute_exists(userId)")));
    }

    @Test
    void removeFromWatchlist_removesAuctionId() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        repository.removeFromWatchlist("u-1", "a-100");

        verify(dynamoDbClient).updateItem(argThat((UpdateItemRequest req) ->
                req.tableName().equals("Users") &&
                        req.updateExpression().equals("DELETE watchlist :ids")));
    }

    @Test
    void getWatchlist_returnsListWhenExists() {
        GetItemResponse response = GetItemResponse.builder()
                .item(Map.of("watchlist", AttributeValue.fromSs(List.of("a-1", "a-2", "a-3"))))
                .build();
        when(dynamoDbClient.getItem(any(java.util.function.Consumer.class))).thenReturn(response);

        List<String> watchlist = repository.getWatchlist("u-1");

        assertThat(watchlist).containsExactly("a-1", "a-2", "a-3");
    }

    @Test
    void getWatchlist_returnsEmptyWhenNoItem() {
        GetItemResponse response = GetItemResponse.builder().build();
        when(dynamoDbClient.getItem(any(java.util.function.Consumer.class))).thenReturn(response);

        List<String> watchlist = repository.getWatchlist("u-999");

        assertThat(watchlist).isEmpty();
    }

    @Test
    void getWatchlist_returnsEmptyWhenNoWatchlistAttribute() {
        GetItemResponse response = GetItemResponse.builder()
                .item(Map.of("userId", AttributeValue.fromS("u-1")))
                .build();
        when(dynamoDbClient.getItem(any(java.util.function.Consumer.class))).thenReturn(response);

        List<String> watchlist = repository.getWatchlist("u-1");

        assertThat(watchlist).isEmpty();
    }
}
