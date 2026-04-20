package com.gavel.shop.repository;

import com.gavel.shop.model.Item;
import com.gavel.shop.model.Review;
import com.gavel.shop.model.Shop;
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

import java.util.*;

@Repository
public class ShopRepository {

    private final DynamoDbTable<Shop> shopTable;
    private final DynamoDbTable<Item> itemTable;
    private final DynamoDbTable<Review> reviewTable;
    private final DynamoDbClient dynamoDbClient;

    public ShopRepository(DynamoDbEnhancedClient enhancedClient, DynamoDbClient dynamoDbClient) {
        this.shopTable = enhancedClient.table("Shops", TableSchema.fromBean(Shop.class));
        this.itemTable = enhancedClient.table("Items", TableSchema.fromBean(Item.class));
        this.reviewTable = enhancedClient.table("Reviews", TableSchema.fromBean(Review.class));
        this.dynamoDbClient = dynamoDbClient;
    }

    public void saveShop(Shop shop) {
        shopTable.putItem(shop);
    }

    public Optional<Shop> findShopById(String shopId) {
        return Optional.ofNullable(shopTable.getItem(Key.builder().partitionValue(shopId).build()));
    }

    public List<Shop> findShopsByOwnerId(String ownerId) {
        var index = shopTable.index("owner_id-index");
        var results = index.query(QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(ownerId).build()))
                .build());
        List<Shop> shops = new ArrayList<>();
        results.forEach(page -> shops.addAll(page.items()));
        return shops;
    }

    public void saveItem(Item item) {
        itemTable.putItem(item);
    }

    public Optional<Item> findItemById(String itemId) {
        return Optional.ofNullable(itemTable.getItem(Key.builder().partitionValue(itemId).build()));
    }

    public List<Item> findItemsByShopId(String shopId) {
        var index = itemTable.index("shop_id-index");
        var results = index.query(QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(shopId).build()))
                .build());
        List<Item> items = new ArrayList<>();
        results.forEach(page -> items.addAll(page.items()));
        return items;
    }

    public void saveReview(Review review) {
        reviewTable.putItem(review);
    }

    public Optional<Review> findReviewById(String reviewId) {
        return Optional.ofNullable(reviewTable.getItem(Key.builder().partitionValue(reviewId).build()));
    }

    public List<Review> findReviewsByShopId(String shopId) {
        var index = reviewTable.index("shop_id-index");
        var results = index.query(QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(shopId).build()))
                .scanIndexForward(false)
                .build());
        List<Review> reviews = new ArrayList<>();
        results.forEach(page -> reviews.addAll(page.items()));
        return reviews;
    }

    public Optional<Review> findReviewByAuctionAndReviewer(String auctionId, String reviewerId) {
        var index = reviewTable.index("auction_id-reviewer-index");
        var results = index.query(QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(auctionId).sortValue(reviewerId).build()))
                .limit(1)
                .build());
        for (var page : results) {
            if (!page.items().isEmpty()) {
                return Optional.of(page.items().get(0));
            }
        }
        return Optional.empty();
    }

    public void updateReviewReply(String reviewId, String reply, String updatedAt) {
        Map<String, AttributeValue> key = Map.of("reviewId", AttributeValue.fromS(reviewId));
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":r", AttributeValue.fromS(reply));
        values.put(":ua", AttributeValue.fromS(updatedAt));

        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName("Reviews")
                .key(key)
                .updateExpression("SET sellerReply = :r, updatedAt = :ua")
                .expressionAttributeValues(values)
                .build());
    }
}
