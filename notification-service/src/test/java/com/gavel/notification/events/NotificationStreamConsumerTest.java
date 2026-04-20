package com.gavel.notification.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.notification.hub.NotificationHub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationStreamConsumerTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private NotificationHub hub;
    @Mock private StreamOperations<String, Object, Object> streamOps;

    private ObjectMapper objectMapper;
    private NotificationStreamConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        consumer = new NotificationStreamConsumer(redisTemplate, hub, objectMapper, 2);
    }

    private boolean invokeHandleMessage(String stream, String payload) throws Exception {
        Method method = NotificationStreamConsumer.class.getDeclaredMethod("handleMessage", String.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(consumer, stream, payload);
    }

    private boolean invokeHandleBidPlaced(String payload) throws Exception {
        Method method = NotificationStreamConsumer.class.getDeclaredMethod("handleBidPlaced", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(consumer, payload);
    }

    private boolean invokeHandleAuctionClosed(String payload) throws Exception {
        Method method = NotificationStreamConsumer.class.getDeclaredMethod("handleAuctionClosed", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(consumer, payload);
    }

    @Test
    void handleBidPlaced_broadcastsToAuction() throws Exception {
        String payload = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("auction_id", "a-1");
            put("bid_id", "b-1");
            put("item_id", "i-1");
            put("item_title", "Lamp");
            put("shop_id", "s-1");
            put("shop_name", "Thrift");
            put("user_id", "u-1");
            put("amount", 500);
            put("previous_highest", 400);
            put("previous_bidder", "u-2");
            put("bid_accepted_at", Instant.now().toString());
            put("timestamp", Instant.now().toString());
        }});

        boolean result = invokeHandleBidPlaced(payload);

        assertTrue(result);
        verify(hub).broadcast(eq("a-1"), anyMap(), anyString());
        verify(hub).storeAndPushNotification(eq("u-2"), any());
    }

    @Test
    void handleBidPlaced_noPreviousBidder_noPushNotification() throws Exception {
        String payload = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("auction_id", "a-1");
            put("bid_id", "b-1");
            put("item_id", "i-1");
            put("item_title", "Lamp");
            put("shop_id", "s-1");
            put("shop_name", "Thrift");
            put("user_id", "u-1");
            put("amount", 500);
            put("previous_highest", 0);
            put("previous_bidder", "");
            put("bid_accepted_at", "");
            put("timestamp", Instant.now().toString());
        }});

        boolean result = invokeHandleBidPlaced(payload);

        assertTrue(result);
        verify(hub).broadcast(eq("a-1"), anyMap(), eq(""));
        verify(hub, never()).storeAndPushNotification(anyString(), any());
    }

    @Test
    void handleBidPlaced_invalidJson_returnsTrue() throws Exception {
        boolean result = invokeHandleBidPlaced("{bad json");
        assertTrue(result);
        verify(hub, never()).broadcast(anyString(), anyMap(), any());
    }

    @Test
    void handleAuctionClosed_singleWinner_broadcastsAndPushes() throws Exception {
        String payload = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("auction_id", "a-1");
            put("winner_id", "u-1");
            put("winning_bid", 1000);
            put("quantity", 1);
            put("item_id", "i-1");
            put("item_title", "Lamp");
            put("shop_id", "s-1");
            put("closed_at", Instant.now().toString());
        }});

        boolean result = invokeHandleAuctionClosed(payload);

        assertTrue(result);
        verify(hub).broadcast(eq("a-1"), anyMap(), isNull());
        verify(hub).storeAndPushNotification(eq("u-1"), any());
    }

    @Test
    void handleAuctionClosed_multipleWinners_pushesToAll() throws Exception {
        String payload = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("auction_id", "a-1");
            put("winner_id", "");
            put("winning_bid", 0);
            put("winners", Map.of("u-1", 500, "u-2", 600));
            put("quantity", 2);
            put("item_id", "i-1");
            put("item_title", "Lamp");
            put("shop_id", "s-1");
            put("closed_at", Instant.now().toString());
        }});

        boolean result = invokeHandleAuctionClosed(payload);

        assertTrue(result);
        verify(hub).broadcast(eq("a-1"), anyMap(), isNull());
        verify(hub, times(2)).storeAndPushNotification(anyString(), any());
    }

    @Test
    void handleAuctionClosed_noWinner_broadcastsOnly() throws Exception {
        String payload = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("auction_id", "a-1");
            put("winner_id", "");
            put("winning_bid", 0);
            put("quantity", 1);
            put("item_id", "i-1");
            put("item_title", "Lamp");
            put("shop_id", "s-1");
            put("closed_at", Instant.now().toString());
        }});

        boolean result = invokeHandleAuctionClosed(payload);

        assertTrue(result);
        verify(hub).broadcast(eq("a-1"), anyMap(), isNull());
        verify(hub, never()).storeAndPushNotification(anyString(), any());
    }

    @Test
    void handleAuctionClosed_invalidJson_returnsTrue() throws Exception {
        boolean result = invokeHandleAuctionClosed("{bad");
        assertTrue(result);
        verify(hub, never()).broadcast(anyString(), anyMap(), any());
    }

    @Test
    void handleMessage_bidPlacedStream_delegates() throws Exception {
        String payload = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("auction_id", "a-1");
            put("bid_id", "b-1");
            put("item_id", "i-1");
            put("item_title", "X");
            put("shop_id", "s-1");
            put("shop_name", "S");
            put("user_id", "u-1");
            put("amount", 100);
            put("previous_highest", 0);
            put("previous_bidder", "");
            put("bid_accepted_at", "");
            put("timestamp", Instant.now().toString());
        }});

        boolean result = invokeHandleMessage("bid:placed", payload);
        assertTrue(result);
    }

    @Test
    void handleMessage_auctionClosedStream_delegates() throws Exception {
        String payload = objectMapper.writeValueAsString(new LinkedHashMap<>() {{
            put("auction_id", "a-1");
            put("winner_id", "");
            put("winning_bid", 0);
            put("quantity", 1);
            put("item_id", "i-1");
            put("item_title", "Lamp");
            put("shop_id", "s-1");
            put("closed_at", Instant.now().toString());
        }});

        boolean result = invokeHandleMessage("auction:closed", payload);
        assertTrue(result);
    }

    @Test
    void handleMessage_unknownStream_returnsTrue() throws Exception {
        boolean result = invokeHandleMessage("other:stream", "{}");
        assertTrue(result);
    }

    @Test
    void stop_setsRunningFalse() {
        consumer.stop();
    }
}
