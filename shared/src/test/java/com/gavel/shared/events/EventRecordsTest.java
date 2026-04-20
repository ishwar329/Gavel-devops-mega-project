package com.gavel.shared.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventRecordsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void bidPlacedEvent_serializesAndDeserializes() throws Exception {
        BidPlacedEvent event = new BidPlacedEvent(
                "a-1", "b-1", "i-1", "Lamp", "s-1", "Thrift", "u-1",
                500, 400, "u-2", "2026-04-20T10:00:00Z", "2026-04-20T10:00:01Z"
        );

        String json = objectMapper.writeValueAsString(event);
        BidPlacedEvent deserialized = objectMapper.readValue(json, BidPlacedEvent.class);

        assertThat(deserialized.auctionId()).isEqualTo("a-1");
        assertThat(deserialized.bidId()).isEqualTo("b-1");
        assertThat(deserialized.amount()).isEqualTo(500);
        assertThat(deserialized.userId()).isEqualTo("u-1");
    }

    @Test
    void auctionClosedEvent_serializesAndDeserializes() throws Exception {
        AuctionClosedEvent event = new AuctionClosedEvent(
                "a-1", "u-1", 1000, Map.of("u-1", 1000L), 1,
                "i-1", "Lamp", "s-1", "2026-04-20T11:00:00Z"
        );

        String json = objectMapper.writeValueAsString(event);
        AuctionClosedEvent deserialized = objectMapper.readValue(json, AuctionClosedEvent.class);

        assertThat(deserialized.auctionId()).isEqualTo("a-1");
        assertThat(deserialized.winnerId()).isEqualTo("u-1");
        assertThat(deserialized.winningBid()).isEqualTo(1000);
        assertThat(deserialized.winners()).containsEntry("u-1", 1000L);
    }

    @Test
    void paymentProcessedEvent_serializesAndDeserializes() throws Exception {
        PaymentProcessedEvent event = new PaymentProcessedEvent(
                "pay-1", "a-1", "u-1", 1000, "2026-04-20T12:00:00Z"
        );

        String json = objectMapper.writeValueAsString(event);
        PaymentProcessedEvent deserialized = objectMapper.readValue(json, PaymentProcessedEvent.class);

        assertThat(deserialized.paymentId()).isEqualTo("pay-1");
        assertThat(deserialized.auctionId()).isEqualTo("a-1");
        assertThat(deserialized.userId()).isEqualTo("u-1");
        assertThat(deserialized.amount()).isEqualTo(1000);
    }

    @Test
    void paymentFailedEvent_serializesAndDeserializes() throws Exception {
        PaymentFailedEvent event = new PaymentFailedEvent(
                "pay-2", "a-2", "u-2", 500, "Insufficient funds", "2026-04-20T13:00:00Z"
        );

        String json = objectMapper.writeValueAsString(event);
        PaymentFailedEvent deserialized = objectMapper.readValue(json, PaymentFailedEvent.class);

        assertThat(deserialized.paymentId()).isEqualTo("pay-2");
        assertThat(deserialized.reason()).isEqualTo("Insufficient funds");
    }

    @Test
    void refundProcessedEvent_serializesAndDeserializes() throws Exception {
        RefundProcessedEvent event = new RefundProcessedEvent(
                "pay-3", "a-3", "u-3", 750, "2026-04-20T14:00:00Z"
        );

        String json = objectMapper.writeValueAsString(event);
        RefundProcessedEvent deserialized = objectMapper.readValue(json, RefundProcessedEvent.class);

        assertThat(deserialized.paymentId()).isEqualTo("pay-3");
        assertThat(deserialized.auctionId()).isEqualTo("a-3");
        assertThat(deserialized.amount()).isEqualTo(750);
    }
}
