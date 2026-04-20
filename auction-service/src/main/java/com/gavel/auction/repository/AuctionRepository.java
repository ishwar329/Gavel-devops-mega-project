package com.gavel.auction.repository;

import com.gavel.auction.model.Auction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class AuctionRepository {

    private static final Logger log = LoggerFactory.getLogger(AuctionRepository.class);
    private static final String TABLE_NAME = "Auctions";

    private final StringRedisTemplate redisTemplate;
    private final DynamoDbClient dynamoDbClient;
    private final String closeAndReadWinnerLua;
    private final String rollbackCloseLua;

    public AuctionRepository(StringRedisTemplate redisTemplate, DynamoDbClient dynamoDbClient) {
        this.redisTemplate = redisTemplate;
        this.dynamoDbClient = dynamoDbClient;
        try {
            this.closeAndReadWinnerLua = new ClassPathResource("lua/close_and_read_winner.lua")
                    .getContentAsString(StandardCharsets.UTF_8);
            this.rollbackCloseLua = new ClassPathResource("lua/rollback_close.lua")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Lua scripts", e);
        }
    }

    public void create(Auction a) {
        String hashKey = "auction:" + a.getAuctionId();
        Map<String, String> fields = auctionToMap(a);
        redisTemplate.opsForHash().putAll(hashKey, fields);
        redisTemplate.opsForSet().add("auctions:active", a.getAuctionId());
        redisTemplate.opsForSet().add("shop:" + a.getShopId() + ":auctions", a.getAuctionId());

        if (a.getShopLat() != 0 || a.getShopLng() != 0) {
            redisTemplate.opsForGeo().add("shops:geo",
                    new org.springframework.data.geo.Point(a.getShopLng(), a.getShopLat()),
                    a.getShopId());
        }

        persistToDynamo(a);
    }

    public Auction getById(String auctionId) {
        String hashKey = "auction:" + auctionId;
        Map<Object, Object> data = redisTemplate.opsForHash().entries(hashKey);
        if (data != null && !data.isEmpty()) {
            return mapToAuction(data);
        }

        Auction fromDynamo = loadFromDynamo(auctionId);
        if (fromDynamo == null) return null;

        String lockKey = "rebuild:auction:" + auctionId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(2));
        if (Boolean.TRUE.equals(acquired)) {
            redisTemplate.opsForHash().putAll(hashKey, auctionToMap(fromDynamo));
        }
        return fromDynamo;
    }

    public List<Auction> list(String status) {
        Set<String> ids = redisTemplate.opsForSet().members("auctions:active");
        if (ids == null || ids.isEmpty()) return Collections.emptyList();

        List<Auction> result = new ArrayList<>();
        for (String id : ids) {
            Auction a = getById(id);
            if (a == null) continue;
            if (status == null || status.isEmpty() || status.equals(a.getStatus())) {
                result.add(a);
            }
        }
        return result;
    }

    public List<Auction> listByShop(String shopId) {
        Set<String> ids = redisTemplate.opsForSet().members("shop:" + shopId + ":auctions");
        if (ids == null || ids.isEmpty()) return Collections.emptyList();

        List<Auction> result = new ArrayList<>();
        for (String id : ids) {
            Auction a = getById(id);
            if (a != null) result.add(a);
        }
        return result;
    }

    public List<String> getShopIdsNear(double lat, double lng, double radiusKm) {
        var results = redisTemplate.opsForGeo().radius("shops:geo",
                new org.springframework.data.geo.Circle(
                        new org.springframework.data.geo.Point(lng, lat),
                        new org.springframework.data.geo.Distance(radiusKm,
                                org.springframework.data.redis.connection.RedisGeoCommands.DistanceUnit.KILOMETERS)));
        if (results == null) return Collections.emptyList();

        List<String> shopIds = new ArrayList<>();
        results.forEach(r -> {
            if (shopIds.size() < 200) {
                shopIds.add(r.getContent().getName());
            }
        });
        return shopIds;
    }

    public void open(String auctionId) {
        redisTemplate.opsForHash().put("auction:" + auctionId, "status", "OPEN");
    }

    @SuppressWarnings("unchecked")
    public CloseResult atomicCloseAndReadWinner(String auctionId) {
        String hashKey = "auction:" + auctionId;
        String bidsKey = "auction:" + auctionId + ":bids";

        DefaultRedisScript<List> script = new DefaultRedisScript<>(closeAndReadWinnerLua, List.class);
        List<String> result = redisTemplate.execute(script, List.of(hashKey, bidsKey));

        if (result == null || result.size() < 3) {
            return new CloseResult(null, null);
        }

        String status = result.get(0).toString();
        String data = result.get(1).toString();
        String extra = result.get(2).toString();

        if ("ERR_NOT_FOUND".equals(status)) return new CloseResult(null, null);
        if ("ERR_NOT_OPEN".equals(status)) return new CloseResult(null, null);

        if ("OK".equals(status)) {
            long amount = 0;
            try { amount = (long) Double.parseDouble(extra); } catch (Exception ignored) {}
            if (data.isEmpty()) {
                return new CloseResult(Map.of(), null);
            }
            return new CloseResult(Map.of(data, amount), null);
        }

        if ("OK_MULTI".equals(status)) {
            Map<String, Long> winners = new LinkedHashMap<>();
            if (!data.isEmpty()) {
                for (String pair : data.split(",")) {
                    String[] parts = pair.split(":");
                    if (parts.length == 2) {
                        winners.put(parts[0], (long) Double.parseDouble(parts[1]));
                    }
                }
            }
            return new CloseResult(winners, null);
        }

        return new CloseResult(null, null);
    }

    @SuppressWarnings("unchecked")
    public void rollbackClose(String auctionId) {
        String hashKey = "auction:" + auctionId;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(rollbackCloseLua, Long.class);
        redisTemplate.execute(script, List.of(hashKey));
    }

    public void persistClosedState(String auctionId) {
        try {
            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of("auction_id", AttributeValue.builder().s(auctionId).build()))
                    .updateExpression("SET #s = :status")
                    .expressionAttributeNames(Map.of("#s", "status"))
                    .expressionAttributeValues(Map.of(":status", AttributeValue.builder().s("CLOSED").build()))
                    .build());
        } catch (Exception e) {
            log.warn("Failed to persist CLOSED state to DynamoDB for {}: {}", auctionId, e.getMessage());
        }
    }

    public void cleanupRedis(String auctionId) {
        redisTemplate.opsForSet().remove("auctions:active", auctionId);
        redisTemplate.expire("auction:" + auctionId, Duration.ofHours(24));
        redisTemplate.delete("auction:" + auctionId + ":bids");
    }

    public Map<String, Long> getDynamoWinners(String auctionId) {
        try {
            var resp = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of("auction_id", AttributeValue.builder().s(auctionId).build()))
                    .build());
            if (!resp.hasItem()) return Map.of();

            Map<String, AttributeValue> item = resp.item();
            if (item.containsKey("winners") && item.get("winners").hasM()) {
                Map<String, Long> winners = new LinkedHashMap<>();
                item.get("winners").m().forEach((k, v) ->
                        winners.put(k, Long.parseLong(v.n())));
                return winners;
            }

            String bidder = item.containsKey("highest_bidder") ? item.get("highest_bidder").s() : "";
            String amountStr = item.containsKey("current_highest") ? item.get("current_highest").n() : "0";
            if (!bidder.isEmpty()) {
                return Map.of(bidder, Long.parseLong(amountStr));
            }
            return Map.of();
        } catch (Exception e) {
            log.warn("DynamoDB fallback failed for {}: {}", auctionId, e.getMessage());
            return Map.of();
        }
    }

    public void updateDynamoWinner(String auctionId, String bidderId, long amount) {
        try {
            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of("auction_id", AttributeValue.builder().s(auctionId).build()))
                    .updateExpression("SET winners.#b = :amt")
                    .expressionAttributeNames(Map.of("#b", bidderId))
                    .expressionAttributeValues(Map.of(":amt", AttributeValue.builder().n(String.valueOf(amount)).build()))
                    .build());
        } catch (DynamoDbException e) {
            if (e.getMessage() != null && e.getMessage().contains("document path")) {
                dynamoDbClient.updateItem(UpdateItemRequest.builder()
                        .tableName(TABLE_NAME)
                        .key(Map.of("auction_id", AttributeValue.builder().s(auctionId).build()))
                        .updateExpression("SET winners = :w")
                        .expressionAttributeValues(Map.of(":w", AttributeValue.builder()
                                .m(Map.of(bidderId, AttributeValue.builder().n(String.valueOf(amount)).build()))
                                .build()))
                        .build());
            } else {
                log.warn("Failed to update DynamoDB winner for auction {}: {}", auctionId, e.getMessage());
            }
        } catch (Exception e) {
            log.warn("Failed to update DynamoDB winner for auction {}: {}", auctionId, e.getMessage());
        }
    }

    private void persistToDynamo(Auction a) {
        try {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("auction_id", AttributeValue.builder().s(a.getAuctionId()).build());
            item.put("seller_id", AttributeValue.builder().s(nvl(a.getSellerId())).build());
            item.put("item_id", AttributeValue.builder().s(nvl(a.getItemId())).build());
            item.put("item_title", AttributeValue.builder().s(nvl(a.getItemTitle())).build());
            item.put("shop_id", AttributeValue.builder().s(nvl(a.getShopId())).build());
            item.put("shop_name", AttributeValue.builder().s(nvl(a.getShopName())).build());
            if (a.getShopLat() != 0) item.put("shop_lat", AttributeValue.builder().n(String.valueOf(a.getShopLat())).build());
            if (a.getShopLng() != 0) item.put("shop_lng", AttributeValue.builder().n(String.valueOf(a.getShopLng())).build());
            item.put("retail_price", AttributeValue.builder().n(String.valueOf(a.getRetailPrice())).build());
            item.put("max_price", AttributeValue.builder().n(String.valueOf(a.getMaxPrice())).build());
            item.put("min_increment", AttributeValue.builder().n(String.valueOf(a.getMinIncrement())).build());
            item.put("quantity", AttributeValue.builder().n(String.valueOf(a.getQuantity())).build());
            item.put("image_url", AttributeValue.builder().s(nvl(a.getImageUrl())).build());
            item.put("shop_logo_url", AttributeValue.builder().s(nvl(a.getShopLogoUrl())).build());
            item.put("description", AttributeValue.builder().s(nvl(a.getDescription())).build());
            item.put("category", AttributeValue.builder().s(nvl(a.getCategory())).build());
            item.put("pickup_start", AttributeValue.builder().s(nvl(a.getPickupStart())).build());
            item.put("pickup_end", AttributeValue.builder().s(nvl(a.getPickupEnd())).build());
            item.put("start_time", AttributeValue.builder().s(nvl(a.getStartTime())).build());
            item.put("end_time", AttributeValue.builder().s(nvl(a.getEndTime())).build());
            item.put("current_highest", AttributeValue.builder().n(String.valueOf(a.getCurrentHighest())).build());
            item.put("bid_count", AttributeValue.builder().n(String.valueOf(a.getBidCount())).build());
            item.put("highest_bidder", AttributeValue.builder().s(nvl(a.getHighestBidder())).build());
            item.put("status", AttributeValue.builder().s(a.getStatus()).build());
            item.put("version", AttributeValue.builder().n(String.valueOf(a.getVersion())).build());

            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(item)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to persist auction {} to DynamoDB: {}", a.getAuctionId(), e.getMessage());
        }
    }

    private Auction loadFromDynamo(String auctionId) {
        try {
            var resp = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of("auction_id", AttributeValue.builder().s(auctionId).build()))
                    .build());
            if (!resp.hasItem()) return null;

            Map<String, AttributeValue> item = resp.item();
            Auction a = new Auction();
            a.setAuctionId(getS(item, "auction_id"));
            a.setSellerId(getS(item, "seller_id"));
            a.setItemId(getS(item, "item_id"));
            a.setItemTitle(getS(item, "item_title"));
            a.setShopId(getS(item, "shop_id"));
            a.setShopName(getS(item, "shop_name"));
            a.setShopLat(getN(item, "shop_lat"));
            a.setShopLng(getN(item, "shop_lng"));
            a.setRetailPrice((long) getN(item, "retail_price"));
            a.setMaxPrice((long) getN(item, "max_price"));
            a.setMinIncrement((long) getN(item, "min_increment"));
            a.setQuantity((int) getN(item, "quantity"));
            a.setImageUrl(getS(item, "image_url"));
            a.setShopLogoUrl(getS(item, "shop_logo_url"));
            a.setDescription(getS(item, "description"));
            a.setCategory(getS(item, "category"));
            a.setPickupStart(getS(item, "pickup_start"));
            a.setPickupEnd(getS(item, "pickup_end"));
            a.setStartTime(getS(item, "start_time"));
            a.setEndTime(getS(item, "end_time"));
            a.setCurrentHighest((long) getN(item, "current_highest"));
            a.setBidCount((long) getN(item, "bid_count"));
            a.setHighestBidder(getS(item, "highest_bidder"));
            a.setStatus(getS(item, "status"));
            a.setVersion((long) getN(item, "version"));
            return a;
        } catch (Exception e) {
            log.warn("Failed to load auction {} from DynamoDB: {}", auctionId, e.getMessage());
            return null;
        }
    }

    private Map<String, String> auctionToMap(Auction a) {
        Map<String, String> m = new HashMap<>();
        m.put("auction_id", nvl(a.getAuctionId()));
        m.put("seller_id", nvl(a.getSellerId()));
        m.put("item_id", nvl(a.getItemId()));
        m.put("item_title", nvl(a.getItemTitle()));
        m.put("shop_id", nvl(a.getShopId()));
        m.put("shop_name", nvl(a.getShopName()));
        m.put("shop_lat", String.valueOf(a.getShopLat()));
        m.put("shop_lng", String.valueOf(a.getShopLng()));
        m.put("retail_price", String.valueOf(a.getRetailPrice()));
        m.put("max_price", String.valueOf(a.getMaxPrice()));
        m.put("min_increment", String.valueOf(a.getMinIncrement()));
        m.put("quantity", String.valueOf(a.getQuantity()));
        m.put("image_url", nvl(a.getImageUrl()));
        m.put("shop_logo_url", nvl(a.getShopLogoUrl()));
        m.put("description", nvl(a.getDescription()));
        m.put("category", nvl(a.getCategory()));
        m.put("pickup_start", nvl(a.getPickupStart()));
        m.put("pickup_end", nvl(a.getPickupEnd()));
        m.put("start_time", nvl(a.getStartTime()));
        m.put("end_time", nvl(a.getEndTime()));
        m.put("current_highest", String.valueOf(a.getCurrentHighest()));
        m.put("bid_count", String.valueOf(a.getBidCount()));
        m.put("highest_bidder", nvl(a.getHighestBidder()));
        m.put("status", nvl(a.getStatus()));
        m.put("version", String.valueOf(a.getVersion()));
        return m;
    }

    private Auction mapToAuction(Map<Object, Object> data) {
        Auction a = new Auction();
        a.setAuctionId(str(data, "auction_id"));
        a.setSellerId(str(data, "seller_id"));
        a.setItemId(str(data, "item_id"));
        a.setItemTitle(str(data, "item_title"));
        a.setShopId(str(data, "shop_id"));
        a.setShopName(str(data, "shop_name"));
        a.setShopLat(dbl(data, "shop_lat"));
        a.setShopLng(dbl(data, "shop_lng"));
        a.setRetailPrice(lng(data, "retail_price"));
        a.setMaxPrice(lng(data, "max_price"));
        a.setMinIncrement(lng(data, "min_increment"));
        a.setQuantity((int) lng(data, "quantity"));
        a.setImageUrl(str(data, "image_url"));
        a.setShopLogoUrl(str(data, "shop_logo_url"));
        a.setDescription(str(data, "description"));
        a.setCategory(str(data, "category"));
        a.setPickupStart(str(data, "pickup_start"));
        a.setPickupEnd(str(data, "pickup_end"));
        a.setStartTime(str(data, "start_time"));
        a.setEndTime(str(data, "end_time"));
        a.setCurrentHighest(lng(data, "current_highest"));
        a.setBidCount(lng(data, "bid_count"));
        a.setHighestBidder(str(data, "highest_bidder"));
        a.setStatus(str(data, "status"));
        a.setVersion(lng(data, "version"));
        return a;
    }

    private String str(Map<Object, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : "";
    }

    private long lng(Map<Object, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return 0;
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0; }
    }

    private double dbl(Map<Object, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return 0;
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0; }
    }

    private String nvl(String s) { return s != null ? s : ""; }

    private String getS(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        return v != null && v.s() != null ? v.s() : "";
    }

    private double getN(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        if (v == null || v.n() == null) return 0;
        try { return Double.parseDouble(v.n()); } catch (Exception e) { return 0; }
    }

    public record CloseResult(Map<String, Long> winners, String error) {}
}
