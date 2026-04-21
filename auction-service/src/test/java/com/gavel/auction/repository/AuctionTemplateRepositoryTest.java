package com.gavel.auction.repository;

import com.gavel.auction.model.AuctionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionTemplateRepositoryTest {

    @Mock private DynamoDbClient dynamoDbClient;

    private AuctionTemplateRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AuctionTemplateRepository(dynamoDbClient);
    }

    private AuctionTemplate makeTemplate(String id) {
        AuctionTemplate t = new AuctionTemplate();
        t.setTemplateId(id);
        t.setSellerId("seller-1");
        t.setShopId("shop-1");
        t.setItemId("item-1");
        t.setItemTitle("Bread");
        t.setShopName("Bakery");
        t.setShopLat(40.7);
        t.setShopLng(-74.0);
        t.setRetailPrice(1000);
        t.setMaxPrice(5000);
        t.setMinIncrement(100);
        t.setQuantity(1);
        t.setImageUrl("http://img.com/1.jpg");
        t.setShopLogoUrl("http://img.com/logo.jpg");
        t.setDescription("Daily bread");
        t.setCategory("Bakery");
        t.setDurationMinutes(60);
        t.setStartBid(500);
        t.setPickupOffsetMinutes(30);
        t.setPickupWindowMinutes(60);
        t.setScheduleType("daily");
        t.setScheduleDays("");
        t.setScheduleTime("08:00");
        t.setActive(true);
        t.setCreatedAt("2026-01-01T00:00:00Z");
        t.setNextRunAt("2026-01-02T08:00:00Z");
        return t;
    }

    private Map<String, AttributeValue> makeDynamoItem(String id) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("template_id", AttributeValue.builder().s(id).build());
        item.put("seller_id", AttributeValue.builder().s("seller-1").build());
        item.put("shop_id", AttributeValue.builder().s("shop-1").build());
        item.put("item_id", AttributeValue.builder().s("item-1").build());
        item.put("item_title", AttributeValue.builder().s("Bread").build());
        item.put("shop_name", AttributeValue.builder().s("Bakery").build());
        item.put("shop_lat", AttributeValue.builder().n("40.7").build());
        item.put("shop_lng", AttributeValue.builder().n("-74.0").build());
        item.put("retail_price", AttributeValue.builder().n("1000").build());
        item.put("max_price", AttributeValue.builder().n("5000").build());
        item.put("min_increment", AttributeValue.builder().n("100").build());
        item.put("quantity", AttributeValue.builder().n("1").build());
        item.put("image_url", AttributeValue.builder().s("http://img.com/1.jpg").build());
        item.put("shop_logo_url", AttributeValue.builder().s("http://img.com/logo.jpg").build());
        item.put("description", AttributeValue.builder().s("Daily bread").build());
        item.put("category", AttributeValue.builder().s("Bakery").build());
        item.put("duration_minutes", AttributeValue.builder().n("60").build());
        item.put("start_bid", AttributeValue.builder().n("500").build());
        item.put("pickup_offset_minutes", AttributeValue.builder().n("30").build());
        item.put("pickup_window_minutes", AttributeValue.builder().n("60").build());
        item.put("schedule_type", AttributeValue.builder().s("daily").build());
        item.put("schedule_days", AttributeValue.builder().s("").build());
        item.put("schedule_time", AttributeValue.builder().s("08:00").build());
        item.put("active", AttributeValue.builder().bool(true).build());
        item.put("created_at", AttributeValue.builder().s("2026-01-01T00:00:00Z").build());
        item.put("next_run_at", AttributeValue.builder().s("2026-01-02T08:00:00Z").build());
        return item;
    }

    @Test
    void save_persistsToDynamo() {
        AuctionTemplate t = makeTemplate("t-1");
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().build());

        repository.save(t);

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());
        assertEquals("AuctionTemplates", captor.getValue().tableName());
        assertEquals("t-1", captor.getValue().item().get("template_id").s());
        assertEquals("seller-1", captor.getValue().item().get("seller_id").s());
        assertTrue(captor.getValue().item().get("active").bool());
    }

    @Test
    void save_nullFields_persistsEmptyStrings() {
        AuctionTemplate t = new AuctionTemplate();
        t.setTemplateId("t-2");
        t.setSellerId("seller-1");
        t.setShopId("shop-1");
        t.setItemId("item-1");
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().build());

        repository.save(t);

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());
        assertEquals("", captor.getValue().item().get("item_title").s());
        assertEquals("", captor.getValue().item().get("description").s());
    }

    @Test
    void getById_found_returnsTemplate() {
        GetItemResponse resp = GetItemResponse.builder().item(makeDynamoItem("t-1")).build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(resp);

        AuctionTemplate result = repository.getById("t-1");

        assertNotNull(result);
        assertEquals("t-1", result.getTemplateId());
        assertEquals("seller-1", result.getSellerId());
        assertEquals("shop-1", result.getShopId());
        assertEquals("Bread", result.getItemTitle());
        assertEquals("daily", result.getScheduleType());
        assertEquals("08:00", result.getScheduleTime());
        assertEquals(60, result.getDurationMinutes());
        assertEquals(500, result.getStartBid());
        assertEquals(30, result.getPickupOffsetMinutes());
        assertEquals(60, result.getPickupWindowMinutes());
        assertTrue(result.isActive());
    }

    @Test
    void getById_notFound_returnsNull() {
        GetItemResponse resp = GetItemResponse.builder().build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(resp);

        assertNull(repository.getById("t-1"));
    }

    @Test
    void getById_missingNumericFields_defaultsToZero() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("template_id", AttributeValue.builder().s("t-1").build());
        item.put("seller_id", AttributeValue.builder().s("s").build());
        item.put("shop_id", AttributeValue.builder().s("sh").build());
        item.put("active", AttributeValue.builder().bool(false).build());
        GetItemResponse resp = GetItemResponse.builder().item(item).build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(resp);

        AuctionTemplate result = repository.getById("t-1");

        assertEquals(0, result.getRetailPrice());
        assertEquals(0, result.getDurationMinutes());
        assertFalse(result.isActive());
    }

    @Test
    void getById_missingBoolField_defaultsToFalse() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("template_id", AttributeValue.builder().s("t-1").build());
        GetItemResponse resp = GetItemResponse.builder().item(item).build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(resp);

        AuctionTemplate result = repository.getById("t-1");
        assertFalse(result.isActive());
    }

    @Test
    void listByShop_returnsTemplates() {
        QueryResponse resp = QueryResponse.builder()
                .items(List.of(makeDynamoItem("t-1"), makeDynamoItem("t-2")))
                .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(resp);

        List<AuctionTemplate> result = repository.listByShop("shop-1");

        assertEquals(2, result.size());
        verify(dynamoDbClient).query(any(QueryRequest.class));
    }

    @Test
    void listByShop_empty_returnsEmptyList() {
        QueryResponse resp = QueryResponse.builder().items(List.of()).build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(resp);

        List<AuctionTemplate> result = repository.listByShop("shop-1");
        assertTrue(result.isEmpty());
    }

    @Test
    void listActive_returnsActiveTemplates() {
        ScanResponse resp = ScanResponse.builder()
                .items(List.of(makeDynamoItem("t-1")))
                .build();
        when(dynamoDbClient.scan(any(ScanRequest.class))).thenReturn(resp);

        List<AuctionTemplate> result = repository.listActive();

        assertEquals(1, result.size());
        verify(dynamoDbClient).scan(any(ScanRequest.class));
    }

    @Test
    void listActive_empty_returnsEmptyList() {
        ScanResponse resp = ScanResponse.builder().items(List.of()).build();
        when(dynamoDbClient.scan(any(ScanRequest.class))).thenReturn(resp);

        assertTrue(repository.listActive().isEmpty());
    }

    @Test
    void updateNextRunAt_callsDynamo() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        repository.updateNextRunAt("t-1", "2026-01-03T08:00:00Z");

        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(captor.capture());
        assertEquals("AuctionTemplates", captor.getValue().tableName());
        assertEquals("SET next_run_at = :nra", captor.getValue().updateExpression());
    }

    @Test
    void setActive_callsDynamo() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        repository.setActive("t-1", false);

        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(captor.capture());
        assertEquals("SET active = :a", captor.getValue().updateExpression());
    }

    @Test
    void delete_callsDynamo() {
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class)))
                .thenReturn(DeleteItemResponse.builder().build());

        repository.delete("t-1");

        ArgumentCaptor<DeleteItemRequest> captor = ArgumentCaptor.forClass(DeleteItemRequest.class);
        verify(dynamoDbClient).deleteItem(captor.capture());
        assertEquals("AuctionTemplates", captor.getValue().tableName());
        assertEquals("t-1", captor.getValue().key().get("template_id").s());
    }
}
