package com.gavel.auction.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.shared.events.AuctionClosedEvent;
import com.gavel.shared.events.BidPlacedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionEventPublisherTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private StreamOperations<String, Object, Object> streamOps;

    private AuctionEventPublisher publisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        publisher = new AuctionEventPublisher(redisTemplate, objectMapper);
    }

    @Test
    void publishBidPlaced_sendsToCorrectStream() {
        BidPlacedEvent event = new BidPlacedEvent(
                "a-1", "b-1", "item-1", "Item", "shop-1", "Shop",
                "user-1", 500, 400, "prev-user", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z"
        );

        publisher.publishBidPlaced(event);

        ArgumentCaptor<StringRecord> captor = ArgumentCaptor.forClass(StringRecord.class);
        verify(streamOps).add(captor.capture());
        assertEquals("bid:placed", captor.getValue().getStream());
        assertTrue(captor.getValue().getValue().containsKey("payload"));
    }

    @Test
    void publishAuctionClosed_sendsToCorrectStream() {
        AuctionClosedEvent event = new AuctionClosedEvent(
                "a-1", "winner-1", 1000, Map.of("winner-1", 1000L),
                1, "item-1", "Item", "shop-1", "2026-01-01T00:00:00Z"
        );

        publisher.publishAuctionClosed(event);

        ArgumentCaptor<StringRecord> captor = ArgumentCaptor.forClass(StringRecord.class);
        verify(streamOps).add(captor.capture());
        assertEquals("auction:closed", captor.getValue().getStream());
    }

    @Test
    void publishBidPlaced_redisFailure_throwsRuntime() {
        when(streamOps.add(any(StringRecord.class))).thenThrow(new RuntimeException("connection refused"));

        BidPlacedEvent event = new BidPlacedEvent(
                "a-1", "b-1", "item-1", "Item", "shop-1", "Shop",
                "user-1", 500, 400, "", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z"
        );

        assertThrows(RuntimeException.class, () -> publisher.publishBidPlaced(event));
    }

    @Test
    void publishBidPlaced_payloadContainsEventData() throws Exception {
        BidPlacedEvent event = new BidPlacedEvent(
                "a-1", "b-1", "item-1", "Item", "shop-1", "Shop",
                "user-1", 500, 400, "prev", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z"
        );

        publisher.publishBidPlaced(event);

        ArgumentCaptor<StringRecord> captor = ArgumentCaptor.forClass(StringRecord.class);
        verify(streamOps).add(captor.capture());
        String payload = captor.getValue().getValue().get("payload");
        assertTrue(payload.contains("a-1"));
        assertTrue(payload.contains("user-1"));
        assertTrue(payload.contains("500"));
    }
}
