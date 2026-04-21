package com.gavel.auction.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.shared.events.AuctionClosedEvent;
import com.gavel.shared.events.BidPlacedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionEventPublisherTest {

    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private AuctionEventPublisher publisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("t", 0), 0, 0, 0, 0, 0);
        SendResult<String, String> sendResult = new SendResult<>(new ProducerRecord<>("t", ""), metadata);
        lenient().when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        publisher = new AuctionEventPublisher(kafkaTemplate, objectMapper);
    }

    @Test
    void publishBidPlaced_sendsToCorrectTopic() {
        BidPlacedEvent event = new BidPlacedEvent(
                "a-1", "b-1", "item-1", "Item", "shop-1", "Shop",
                "user-1", 500, 400, "prev-user", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z"
        );

        publisher.publishBidPlaced(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());
        assertEquals("bid.placed", topicCaptor.getValue());
        assertEquals("a-1", keyCaptor.getValue());
        assertTrue(valueCaptor.getValue().contains("a-1"));
    }

    @Test
    void publishAuctionClosed_sendsToCorrectTopic() {
        AuctionClosedEvent event = new AuctionClosedEvent(
                "a-1", "winner-1", 1000, Map.of("winner-1", 1000L),
                1, "item-1", "Item", "shop-1", "2026-01-01T00:00:00Z"
        );

        publisher.publishAuctionClosed(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), anyString());
        assertEquals("auction.closed", topicCaptor.getValue());
        assertEquals("a-1", keyCaptor.getValue());
    }

    @Test
    void publishBidPlaced_payloadContainsEventData() {
        BidPlacedEvent event = new BidPlacedEvent(
                "a-1", "b-1", "item-1", "Item", "shop-1", "Shop",
                "user-1", 500, 400, "prev", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z"
        );

        publisher.publishBidPlaced(event);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), anyString(), valueCaptor.capture());
        String payload = valueCaptor.getValue();
        assertTrue(payload.contains("a-1"));
        assertTrue(payload.contains("user-1"));
        assertTrue(payload.contains("500"));
    }
}
