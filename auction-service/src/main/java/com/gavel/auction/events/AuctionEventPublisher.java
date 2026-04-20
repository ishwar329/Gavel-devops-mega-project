package com.gavel.auction.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.shared.events.AuctionClosedEvent;
import com.gavel.shared.events.BidPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuctionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuctionEventPublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AuctionEventPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishBidPlaced(BidPlacedEvent event) {
        publish("bid:placed", event);
    }

    public void publishAuctionClosed(AuctionClosedEvent event) {
        publish("auction:closed", event);
    }

    private void publish(String stream, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            RecordId id = redisTemplate.opsForStream().add(
                    StreamRecords.string(Map.of("payload", payload)).withStreamKey(stream));
            log.debug("Published to {}: {}", stream, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish event to " + stream, e);
        }
    }
}
