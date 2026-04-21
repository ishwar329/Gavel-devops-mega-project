package com.gavel.auction.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.shared.events.AuctionClosedEvent;
import com.gavel.shared.events.BidPlacedEvent;
import com.gavel.shared.kafka.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuctionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuctionEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public AuctionEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishBidPlaced(BidPlacedEvent event) {
        publish(Topics.BID_PLACED, event.auctionId(), event);
    }

    public void publishAuctionClosed(AuctionClosedEvent event) {
        publish(Topics.AUCTION_CLOSED, event.auctionId(), event);
    }

    private void publish(String topic, String key, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, key, payload);
            log.debug("Published to {} key={}", topic, key);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to publish event to " + topic, e);
        }
    }
}
