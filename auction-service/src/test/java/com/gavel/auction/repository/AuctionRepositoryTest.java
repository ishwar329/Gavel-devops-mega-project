package com.gavel.auction.repository;

import com.gavel.auction.model.Auction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.script.RedisScript;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuctionRepositoryTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private DynamoDbClient dynamoDbClient;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private SetOperations<String, String> setOps;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private GeoOperations<String, String> geoOps;

    private AuctionRepository repository;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForGeo()).thenReturn(geoOps);

        // AuctionRepository loads Lua scripts from classpath in constructor
        repository = new AuctionRepository(redisTemplate, dynamoDbClient);
    }

    private Auction makeAuction(String id) {
        Auction a = new Auction();
        a.setAuctionId(id);
        a.setSellerId("seller-1");
        a.setItemId("item-1");
        a.setItemTitle("Lamp");
        a.setShopId("shop-1");
        a.setShopName("Thrift");
        a.setShopLat(40.7);
        a.setShopLng(-74.0);
        a.setRetailPrice(1000);
        a.setMaxPrice(800);
        a.setMinIncrement(50);
        a.setQuantity(1);
        a.setStatus("OPEN");
        a.setStartTime("2025-01-01T00:00:00Z");
        a.setEndTime("2025-01-02T00:00:00Z");
        return a;
    }

    @Test
    void create_storesInRedisAndDynamo() {
        Auction a = makeAuction("a-1");

        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().build());

        repository.create(a);

        verify(hashOps).putAll(eq("auction:a-1"), anyMap());
        verify(setOps).add("auctions:active", "a-1");
        verify(setOps).add("shop:shop-1:auctions", "a-1");
        verify(geoOps).add(eq("shops:geo"), any(), eq("shop-1"));
        verify(dynamoDbClient).putItem(any(PutItemRequest.class));
    }

    @Test
    void create_zeroCoords_skipsGeoAdd() {
        Auction a = makeAuction("a-1");
        a.setShopLat(0);
        a.setShopLng(0);

        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().build());

        repository.create(a);

        verify(geoOps, never()).add(eq("shops:geo"), any(), anyString());
    }

    @Test
    void getById_foundInRedis_returnsAuction() {
        Map<Object, Object> data = new HashMap<>();
        data.put("auction_id", "a-1");
        data.put("seller_id", "seller-1");
        data.put("item_id", "item-1");
        data.put("item_title", "Lamp");
        data.put("shop_id", "shop-1");
        data.put("shop_name", "Thrift");
        data.put("shop_lat", "40.7");
        data.put("shop_lng", "-74.0");
        data.put("retail_price", "1000");
        data.put("max_price", "800");
        data.put("min_increment", "50");
        data.put("quantity", "1");
        data.put("image_url", "");
        data.put("shop_logo_url", "");
        data.put("description", "");
        data.put("category", "");
        data.put("pickup_start", "");
        data.put("pickup_end", "");
        data.put("start_time", "");
        data.put("end_time", "");
        data.put("current_highest", "500");
        data.put("bid_count", "3");
        data.put("highest_bidder", "u-1");
        data.put("status", "OPEN");
        data.put("version", "3");

        when(hashOps.entries("auction:a-1")).thenReturn(data);

        Auction result = repository.getById("a-1");

        assertNotNull(result);
        assertEquals("a-1", result.getAuctionId());
        assertEquals("OPEN", result.getStatus());
        assertEquals(500, result.getCurrentHighest());
    }

    @Test
    void getById_notInRedis_fallsToDynamo() {
        when(hashOps.entries("auction:a-1")).thenReturn(Collections.emptyMap());

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("auction_id", AttributeValue.builder().s("a-1").build());
        item.put("seller_id", AttributeValue.builder().s("seller-1").build());
        item.put("item_id", AttributeValue.builder().s("item-1").build());
        item.put("item_title", AttributeValue.builder().s("Lamp").build());
        item.put("shop_id", AttributeValue.builder().s("shop-1").build());
        item.put("shop_name", AttributeValue.builder().s("Thrift").build());
        item.put("retail_price", AttributeValue.builder().n("1000").build());
        item.put("max_price", AttributeValue.builder().n("800").build());
        item.put("min_increment", AttributeValue.builder().n("50").build());
        item.put("quantity", AttributeValue.builder().n("1").build());
        item.put("current_highest", AttributeValue.builder().n("0").build());
        item.put("bid_count", AttributeValue.builder().n("0").build());
        item.put("highest_bidder", AttributeValue.builder().s("").build());
        item.put("status", AttributeValue.builder().s("OPEN").build());
        item.put("version", AttributeValue.builder().n("0").build());

        GetItemResponse resp = GetItemResponse.builder().item(item).build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(resp);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        Auction result = repository.getById("a-1");

        assertNotNull(result);
        assertEquals("a-1", result.getAuctionId());
        verify(hashOps).putAll(eq("auction:a-1"), anyMap());
    }

    @Test
    void getById_notInRedisOrDynamo_returnsNull() {
        when(hashOps.entries("auction:a-1")).thenReturn(Collections.emptyMap());

        GetItemResponse resp = GetItemResponse.builder().build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(resp);

        Auction result = repository.getById("a-1");
        assertNull(result);
    }

    @Test
    void list_emptyActiveSet_returnsEmptyList() {
        when(setOps.members("auctions:active")).thenReturn(null);
        assertTrue(repository.list(null).isEmpty());
    }

    @Test
    void list_filtersbyStatus() {
        when(setOps.members("auctions:active")).thenReturn(Set.of("a-1", "a-2"));

        Map<Object, Object> openData = makeRedisAuctionData("a-1", "OPEN");
        Map<Object, Object> closedData = makeRedisAuctionData("a-2", "CLOSED");

        when(hashOps.entries("auction:a-1")).thenReturn(openData);
        when(hashOps.entries("auction:a-2")).thenReturn(closedData);

        List<Auction> result = repository.list("OPEN");
        assertEquals(1, result.size());
        assertEquals("a-1", result.get(0).getAuctionId());
    }

    @Test
    void listByShop_returnsAuctions() {
        when(setOps.members("shop:s-1:auctions")).thenReturn(Set.of("a-1"));
        when(hashOps.entries("auction:a-1")).thenReturn(makeRedisAuctionData("a-1", "OPEN"));

        List<Auction> result = repository.listByShop("s-1");
        assertEquals(1, result.size());
    }

    @Test
    void open_setsStatusInRedis() {
        repository.open("a-1");
        verify(hashOps).put("auction:a-1", "status", "OPEN");
    }

    @Test
    void persistClosedState_updatesInDynamo() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        repository.persistClosedState("a-1");

        verify(dynamoDbClient).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void persistClosedState_dynamoException_doesNotThrow() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(new RuntimeException("dynamo error"));

        assertDoesNotThrow(() -> repository.persistClosedState("a-1"));
    }

    @Test
    void cleanupRedis_removesFromActiveAndDeletesBids() {
        repository.cleanupRedis("a-1");

        verify(setOps).remove("auctions:active", "a-1");
        verify(redisTemplate).expire(eq("auction:a-1"), any());
        verify(redisTemplate).delete("auction:a-1:bids");
    }

    @Test
    void getDynamoWinners_noItem_returnsEmpty() {
        GetItemResponse resp = GetItemResponse.builder().build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(resp);

        Map<String, Long> result = repository.getDynamoWinners("a-1");
        assertTrue(result.isEmpty());
    }

    @Test
    void getDynamoWinners_withWinnersMap_returnsMap() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("auction_id", AttributeValue.builder().s("a-1").build());
        item.put("winners", AttributeValue.builder()
                .m(Map.of("u-1", AttributeValue.builder().n("1000").build()))
                .build());

        GetItemResponse resp = GetItemResponse.builder().item(item).build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(resp);

        Map<String, Long> result = repository.getDynamoWinners("a-1");
        assertEquals(1, result.size());
        assertEquals(1000L, result.get("u-1"));
    }

    @Test
    void getDynamoWinners_singleWinner_returnsMap() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("auction_id", AttributeValue.builder().s("a-1").build());
        item.put("highest_bidder", AttributeValue.builder().s("u-1").build());
        item.put("current_highest", AttributeValue.builder().n("500").build());

        GetItemResponse resp = GetItemResponse.builder().item(item).build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(resp);

        Map<String, Long> result = repository.getDynamoWinners("a-1");
        assertEquals(1, result.size());
        assertEquals(500L, result.get("u-1"));
    }

    @Test
    void getDynamoWinners_exception_returnsEmpty() {
        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenThrow(new RuntimeException("dynamo error"));

        Map<String, Long> result = repository.getDynamoWinners("a-1");
        assertTrue(result.isEmpty());
    }

    private Map<Object, Object> makeRedisAuctionData(String id, String status) {
        Map<Object, Object> data = new HashMap<>();
        data.put("auction_id", id);
        data.put("seller_id", "seller-1");
        data.put("item_id", "item-1");
        data.put("item_title", "Lamp");
        data.put("shop_id", "shop-1");
        data.put("shop_name", "Thrift");
        data.put("shop_lat", "0.0");
        data.put("shop_lng", "0.0");
        data.put("retail_price", "1000");
        data.put("max_price", "800");
        data.put("min_increment", "50");
        data.put("quantity", "1");
        data.put("image_url", "");
        data.put("shop_logo_url", "");
        data.put("description", "");
        data.put("category", "");
        data.put("pickup_start", "");
        data.put("pickup_end", "");
        data.put("start_time", "");
        data.put("end_time", "");
        data.put("current_highest", "0");
        data.put("bid_count", "0");
        data.put("highest_bidder", "");
        data.put("status", status);
        data.put("version", "0");
        return data;
    }
}
