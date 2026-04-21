package com.gavel.auction.repository;

import com.gavel.auction.model.AuctionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

@Component
public class AuctionTemplateRepository {

    private static final Logger log = LoggerFactory.getLogger(AuctionTemplateRepository.class);
    private static final String TABLE_NAME = "AuctionTemplates";

    private final DynamoDbClient dynamoDbClient;

    public AuctionTemplateRepository(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    public void save(AuctionTemplate t) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("template_id", s(t.getTemplateId()));
        item.put("seller_id", s(t.getSellerId()));
        item.put("shop_id", s(t.getShopId()));
        item.put("item_id", s(t.getItemId()));
        item.put("item_title", s(nvl(t.getItemTitle())));
        item.put("shop_name", s(nvl(t.getShopName())));
        item.put("shop_lat", n(t.getShopLat()));
        item.put("shop_lng", n(t.getShopLng()));
        item.put("retail_price", n(t.getRetailPrice()));
        item.put("max_price", n(t.getMaxPrice()));
        item.put("min_increment", n(t.getMinIncrement()));
        item.put("quantity", n(t.getQuantity()));
        item.put("image_url", s(nvl(t.getImageUrl())));
        item.put("shop_logo_url", s(nvl(t.getShopLogoUrl())));
        item.put("description", s(nvl(t.getDescription())));
        item.put("category", s(nvl(t.getCategory())));
        item.put("duration_minutes", n(t.getDurationMinutes()));
        item.put("start_bid", n(t.getStartBid()));
        item.put("pickup_offset_minutes", n(t.getPickupOffsetMinutes()));
        item.put("pickup_window_minutes", n(t.getPickupWindowMinutes()));
        item.put("schedule_type", s(nvl(t.getScheduleType())));
        item.put("schedule_days", s(nvl(t.getScheduleDays())));
        item.put("schedule_time", s(nvl(t.getScheduleTime())));
        item.put("active", AttributeValue.builder().bool(t.isActive()).build());
        item.put("created_at", s(nvl(t.getCreatedAt())));
        item.put("next_run_at", s(nvl(t.getNextRunAt())));

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build());
    }

    public AuctionTemplate getById(String templateId) {
        var resp = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("template_id", s(templateId)))
                .build());
        if (!resp.hasItem()) return null;
        return fromItem(resp.item());
    }

    public List<AuctionTemplate> listByShop(String shopId) {
        var resp = dynamoDbClient.query(QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName("shop_id-index")
                .keyConditionExpression("shop_id = :sid")
                .expressionAttributeValues(Map.of(":sid", s(shopId)))
                .build());
        List<AuctionTemplate> result = new ArrayList<>();
        for (Map<String, AttributeValue> item : resp.items()) {
            result.add(fromItem(item));
        }
        return result;
    }

    public List<AuctionTemplate> listActive() {
        var resp = dynamoDbClient.scan(ScanRequest.builder()
                .tableName(TABLE_NAME)
                .filterExpression("active = :a")
                .expressionAttributeValues(Map.of(":a", AttributeValue.builder().bool(true).build()))
                .build());
        List<AuctionTemplate> result = new ArrayList<>();
        for (Map<String, AttributeValue> item : resp.items()) {
            result.add(fromItem(item));
        }
        return result;
    }

    public void updateNextRunAt(String templateId, String nextRunAt) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("template_id", s(templateId)))
                .updateExpression("SET next_run_at = :nra")
                .expressionAttributeValues(Map.of(":nra", s(nextRunAt)))
                .build());
    }

    public void setActive(String templateId, boolean active) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("template_id", s(templateId)))
                .updateExpression("SET active = :a")
                .expressionAttributeValues(Map.of(":a", AttributeValue.builder().bool(active).build()))
                .build());
    }

    public void delete(String templateId) {
        dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("template_id", s(templateId)))
                .build());
    }

    private AuctionTemplate fromItem(Map<String, AttributeValue> item) {
        AuctionTemplate t = new AuctionTemplate();
        t.setTemplateId(getS(item, "template_id"));
        t.setSellerId(getS(item, "seller_id"));
        t.setShopId(getS(item, "shop_id"));
        t.setItemId(getS(item, "item_id"));
        t.setItemTitle(getS(item, "item_title"));
        t.setShopName(getS(item, "shop_name"));
        t.setShopLat(getN(item, "shop_lat"));
        t.setShopLng(getN(item, "shop_lng"));
        t.setRetailPrice((long) getN(item, "retail_price"));
        t.setMaxPrice((long) getN(item, "max_price"));
        t.setMinIncrement((long) getN(item, "min_increment"));
        t.setQuantity((int) getN(item, "quantity"));
        t.setImageUrl(getS(item, "image_url"));
        t.setShopLogoUrl(getS(item, "shop_logo_url"));
        t.setDescription(getS(item, "description"));
        t.setCategory(getS(item, "category"));
        t.setDurationMinutes((int) getN(item, "duration_minutes"));
        t.setStartBid((long) getN(item, "start_bid"));
        t.setPickupOffsetMinutes((int) getN(item, "pickup_offset_minutes"));
        t.setPickupWindowMinutes((int) getN(item, "pickup_window_minutes"));
        t.setScheduleType(getS(item, "schedule_type"));
        t.setScheduleDays(getS(item, "schedule_days"));
        t.setScheduleTime(getS(item, "schedule_time"));
        t.setActive(getB(item, "active"));
        t.setCreatedAt(getS(item, "created_at"));
        t.setNextRunAt(getS(item, "next_run_at"));
        return t;
    }

    private AttributeValue s(String val) {
        return AttributeValue.builder().s(val).build();
    }

    private AttributeValue n(double val) {
        return AttributeValue.builder().n(String.valueOf(val)).build();
    }

    private String nvl(String val) { return val != null ? val : ""; }

    private String getS(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        return v != null && v.s() != null ? v.s() : "";
    }

    private double getN(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        if (v == null || v.n() == null) return 0;
        try { return Double.parseDouble(v.n()); } catch (Exception e) { return 0; }
    }

    private boolean getB(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        return v != null && v.bool() != null && v.bool();
    }
}
