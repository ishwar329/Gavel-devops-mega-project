package com.gavel.shop.repository;

import com.gavel.shop.model.Item;
import com.gavel.shop.model.Review;
import com.gavel.shop.model.Shop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShopRepositoryTest {

    private DynamoDbEnhancedClient enhancedClient;
    private DynamoDbClient dynamoDbClient;
    private DynamoDbTable<Shop> shopTable;
    private DynamoDbTable<Item> itemTable;
    private DynamoDbTable<Review> reviewTable;
    private DynamoDbIndex<Shop> shopOwnerIndex;
    private DynamoDbIndex<Item> itemShopIndex;
    private DynamoDbIndex<Review> reviewShopIndex;
    private DynamoDbIndex<Review> reviewAuctionIndex;
    private ShopRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        enhancedClient = mock(DynamoDbEnhancedClient.class);
        dynamoDbClient = mock(DynamoDbClient.class);
        shopTable = mock(DynamoDbTable.class);
        itemTable = mock(DynamoDbTable.class);
        reviewTable = mock(DynamoDbTable.class);
        shopOwnerIndex = mock(DynamoDbIndex.class);
        itemShopIndex = mock(DynamoDbIndex.class);
        reviewShopIndex = mock(DynamoDbIndex.class);
        reviewAuctionIndex = mock(DynamoDbIndex.class);

        when(enhancedClient.table(eq("Shops"), any(TableSchema.class))).thenReturn(shopTable);
        when(enhancedClient.table(eq("Items"), any(TableSchema.class))).thenReturn(itemTable);
        when(enhancedClient.table(eq("Reviews"), any(TableSchema.class))).thenReturn(reviewTable);
        when(shopTable.index("owner_id-index")).thenReturn(shopOwnerIndex);
        when(itemTable.index("shop_id-index")).thenReturn(itemShopIndex);
        when(reviewTable.index("shop_id-index")).thenReturn(reviewShopIndex);
        when(reviewTable.index("auction_id-reviewer-index")).thenReturn(reviewAuctionIndex);

        repository = new ShopRepository(enhancedClient, dynamoDbClient);
    }

    @Test
    void saveShop_persistsShop() {
        Shop shop = new Shop();
        shop.setShopId("s-1");

        repository.saveShop(shop);

        verify(shopTable).putItem(shop);
    }

    @Test
    void findShopById_returnsShopWhenExists() {
        Shop shop = new Shop();
        shop.setShopId("s-1");
        when(shopTable.getItem(any(Key.class))).thenReturn(shop);

        Optional<Shop> result = repository.findShopById("s-1");

        assertThat(result).isPresent();
        assertThat(result.get().getShopId()).isEqualTo("s-1");
    }

    @Test
    void findShopById_returnsEmptyWhenNotFound() {
        when(shopTable.getItem(any(Key.class))).thenReturn(null);

        Optional<Shop> result = repository.findShopById("s-999");

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findShopsByOwnerId_returnsAllShopsForOwner() {
        Shop s1 = new Shop();
        s1.setShopId("s-1");
        Shop s2 = new Shop();
        s2.setShopId("s-2");

        PageIterable<Shop> pageIterable = mock(PageIterable.class);
        var page = mock(software.amazon.awssdk.enhanced.dynamodb.model.Page.class);
        when(page.items()).thenReturn(List.of(s1, s2));

        doAnswer(inv -> {
            java.util.function.Consumer<software.amazon.awssdk.enhanced.dynamodb.model.Page<Shop>> action = inv.getArgument(0);
            action.accept(page);
            return null;
        }).when(pageIterable).forEach(any(java.util.function.Consumer.class));

        when(shopOwnerIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);

        List<Shop> shops = repository.findShopsByOwnerId("u-1");

        assertThat(shops).hasSize(2);
        assertThat(shops).extracting(Shop::getShopId).containsExactly("s-1", "s-2");
    }

    @Test
    void saveItem_persistsItem() {
        Item item = new Item();
        item.setItemId("i-1");

        repository.saveItem(item);

        verify(itemTable).putItem(item);
    }

    @Test
    void findItemById_returnsItemWhenExists() {
        Item item = new Item();
        item.setItemId("i-1");
        when(itemTable.getItem(any(Key.class))).thenReturn(item);

        Optional<Item> result = repository.findItemById("i-1");

        assertThat(result).isPresent();
        assertThat(result.get().getItemId()).isEqualTo("i-1");
    }

    @Test
    void findItemById_returnsEmptyWhenNotFound() {
        when(itemTable.getItem(any(Key.class))).thenReturn(null);

        Optional<Item> result = repository.findItemById("i-999");

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findItemsByShopId_returnsAllItemsForShop() {
        Item i1 = new Item();
        i1.setItemId("i-1");
        Item i2 = new Item();
        i2.setItemId("i-2");

        PageIterable<Item> pageIterable = mock(PageIterable.class);
        var page = mock(software.amazon.awssdk.enhanced.dynamodb.model.Page.class);
        when(page.items()).thenReturn(List.of(i1, i2));

        doAnswer(inv -> {
            java.util.function.Consumer<software.amazon.awssdk.enhanced.dynamodb.model.Page<Item>> action = inv.getArgument(0);
            action.accept(page);
            return null;
        }).when(pageIterable).forEach(any(java.util.function.Consumer.class));

        when(itemShopIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);

        List<Item> items = repository.findItemsByShopId("s-1");

        assertThat(items).hasSize(2);
        assertThat(items).extracting(Item::getItemId).containsExactly("i-1", "i-2");
    }

    @Test
    void saveReview_persistsReview() {
        Review review = new Review();
        review.setReviewId("r-1");

        repository.saveReview(review);

        verify(reviewTable).putItem(review);
    }

    @Test
    void findReviewById_returnsReviewWhenExists() {
        Review review = new Review();
        review.setReviewId("r-1");
        when(reviewTable.getItem(any(Key.class))).thenReturn(review);

        Optional<Review> result = repository.findReviewById("r-1");

        assertThat(result).isPresent();
        assertThat(result.get().getReviewId()).isEqualTo("r-1");
    }

    @Test
    void findReviewById_returnsEmptyWhenNotFound() {
        when(reviewTable.getItem(any(Key.class))).thenReturn(null);

        Optional<Review> result = repository.findReviewById("r-999");

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findReviewsByShopId_returnsAllReviewsForShop() {
        Review r1 = new Review();
        r1.setReviewId("r-1");
        Review r2 = new Review();
        r2.setReviewId("r-2");

        PageIterable<Review> pageIterable = mock(PageIterable.class);
        var page = mock(software.amazon.awssdk.enhanced.dynamodb.model.Page.class);
        when(page.items()).thenReturn(List.of(r1, r2));

        doAnswer(inv -> {
            java.util.function.Consumer<software.amazon.awssdk.enhanced.dynamodb.model.Page<Review>> action = inv.getArgument(0);
            action.accept(page);
            return null;
        }).when(pageIterable).forEach(any(java.util.function.Consumer.class));

        when(reviewShopIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);

        List<Review> reviews = repository.findReviewsByShopId("s-1");

        assertThat(reviews).hasSize(2);
        assertThat(reviews).extracting(Review::getReviewId).containsExactly("r-1", "r-2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findReviewByAuctionAndReviewer_returnsReviewWhenExists() {
        Review review = new Review();
        review.setReviewId("r-1");

        PageIterable<Review> pageIterable = mock(PageIterable.class);
        var page = mock(software.amazon.awssdk.enhanced.dynamodb.model.Page.class);
        when(page.items()).thenReturn(List.of(review));
        when(pageIterable.iterator()).thenReturn((java.util.Iterator) List.of(page).iterator());
        when(reviewAuctionIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);

        Optional<Review> result = repository.findReviewByAuctionAndReviewer("a-1", "u-1");

        assertThat(result).isPresent();
        assertThat(result.get().getReviewId()).isEqualTo("r-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findReviewByAuctionAndReviewer_returnsEmptyWhenNotFound() {
        PageIterable<Review> pageIterable = mock(PageIterable.class);
        var page = mock(software.amazon.awssdk.enhanced.dynamodb.model.Page.class);
        when(page.items()).thenReturn(List.of());
        when(pageIterable.iterator()).thenReturn((java.util.Iterator) List.of(page).iterator());
        when(reviewAuctionIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);

        Optional<Review> result = repository.findReviewByAuctionAndReviewer("a-999", "u-999");

        assertThat(result).isEmpty();
    }

    @Test
    void updateReviewReply_updatesReplyAndTimestamp() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        repository.updateReviewReply("r-1", "Thanks for the feedback!", "2026-04-20T10:00:00Z");

        verify(dynamoDbClient).updateItem(argThat((UpdateItemRequest req) ->
                req.tableName().equals("Reviews") &&
                        req.updateExpression().equals("SET sellerReply = :r, updatedAt = :ua") &&
                        req.expressionAttributeValues().get(":r").s().equals("Thanks for the feedback!") &&
                        req.expressionAttributeValues().get(":ua").s().equals("2026-04-20T10:00:00Z")));
    }
}
