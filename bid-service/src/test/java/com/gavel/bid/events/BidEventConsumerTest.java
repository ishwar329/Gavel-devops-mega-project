package com.gavel.bid.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.bid.service.BidService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BidEventConsumerTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private BidService bidService;
    @Mock private StreamOperations<String, Object, Object> streamOps;

    private ObjectMapper objectMapper;
    private BidEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        consumer = new BidEventConsumer(redisTemplate, bidService, objectMapper, 2);
    }

    private boolean invokeHandleMessage(String stream, String payload) throws Exception {
        Method method = BidEventConsumer.class.getDeclaredMethod("handleMessage", String.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(consumer, stream, payload);
    }

    private boolean invokeHandleBidPlaced(String payload) throws Exception {
        Method method = BidEventConsumer.class.getDeclaredMethod("handleBidPlaced", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(consumer, payload);
    }

    private boolean invokeHandleAuctionClosed(String payload) throws Exception {
        Method method = BidEventConsumer.class.getDeclaredMethod("handleAuctionClosed", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(consumer, payload);
    }

    @Test
    void handleBidPlaced_validPayload_callsRecordBid() throws Exception {
        String payload = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
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
        verify(bidService).recordBid(any());
    }

    @Test
    void handleBidPlaced_invalidJson_discardsAndReturnsTrue() throws Exception {
        boolean result = invokeHandleBidPlaced("not-json");
        assertTrue(result);
        verify(bidService, never()).recordBid(any());
    }

    @Test
    void handleBidPlaced_recordBidFails_returnsFalse() throws Exception {
        String payload = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
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
            put("bid_accepted_at", "");
            put("timestamp", Instant.now().toString());
        }});

        doThrow(new RuntimeException("fail")).when(bidService).recordBid(any());

        boolean result = invokeHandleBidPlaced(payload);
        assertFalse(result);
    }

    @Test
    void handleAuctionClosed_singleWinner_marksWinnerBid() throws Exception {
        String payload = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
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
        verify(bidService).markWinnerBid("a-1", "u-1");
    }

    @Test
    void handleAuctionClosed_multiWinners_marksAllWinners() throws Exception {
        String payload = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("auction_id", "a-1");
            put("winner_id", "");
            put("winning_bid", 0);
            put("winners", java.util.Map.of("u-1", 500, "u-2", 600));
            put("quantity", 2);
            put("item_id", "i-1");
            put("item_title", "Lamp");
            put("shop_id", "s-1");
            put("closed_at", Instant.now().toString());
        }});

        boolean result = invokeHandleAuctionClosed(payload);

        assertTrue(result);
        verify(bidService).markWinnerBid("a-1", "u-1");
        verify(bidService).markWinnerBid("a-1", "u-2");
    }

    @Test
    void handleAuctionClosed_noWinner_returnsTrue() throws Exception {
        String payload = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
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
        verify(bidService, never()).markWinnerBid(anyString(), anyString());
    }

    @Test
    void handleAuctionClosed_invalidJson_discardsAndReturnsTrue() throws Exception {
        boolean result = invokeHandleAuctionClosed("{bad");
        assertTrue(result);
        verify(bidService, never()).markWinnerBid(anyString(), anyString());
    }

    @Test
    void handleMessage_bidPlacedStream_delegatesToHandleBidPlaced() throws Exception {
        String payload = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
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
        verify(bidService).recordBid(any());
    }

    @Test
    void handleMessage_unknownStream_returnsTrue() throws Exception {
        boolean result = invokeHandleMessage("unknown:stream", "{}");
        assertTrue(result);
    }

    @Test
    void handleBidPlaced_invalidTimestamp_usesInstantNow() throws Exception {
        String payload = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
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
            put("bid_accepted_at", "");
            put("timestamp", "not-a-timestamp");
        }});

        boolean result = invokeHandleBidPlaced(payload);
        assertTrue(result);
        verify(bidService).recordBid(any());
    }

    @Test
    void handleAuctionClosed_markWinnerFails_returnsFalse() throws Exception {
        String payload = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("auction_id", "a-1");
            put("winner_id", "u-1");
            put("winning_bid", 1000);
            put("quantity", 1);
            put("item_id", "i-1");
            put("item_title", "Lamp");
            put("shop_id", "s-1");
            put("closed_at", Instant.now().toString());
        }});

        doThrow(new RuntimeException("fail")).when(bidService).markWinnerBid("a-1", "u-1");

        boolean result = invokeHandleAuctionClosed(payload);
        assertFalse(result);
    }

    @Test
    void stop_setsRunningFalse() {
        consumer.stop();
        // just verifying no exception
    }
}
