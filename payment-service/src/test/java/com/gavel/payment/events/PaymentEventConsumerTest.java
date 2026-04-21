package com.gavel.payment.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.payment.service.PaymentService;
import com.gavel.shared.events.AuctionClosedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock private PaymentService paymentService;

    private ObjectMapper objectMapper;
    private PaymentEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new PaymentEventConsumer(paymentService, objectMapper);
    }

    @Test
    void onAuctionClosed_validPayload_initiatesPayment() throws Exception {
        AuctionClosedEvent event = new AuctionClosedEvent(
                "a-1", "u-1", 100L, Map.of("u-1", 100L), 1, "i-1", "Test Item", "s-1", "2026-04-20T10:00:00Z");
        String payload = objectMapper.writeValueAsString(event);

        consumer.onAuctionClosed(payload);

        verify(paymentService).initiatePayment(argThat(e ->
                e.auctionId().equals("a-1") &&
                        e.winnerId().equals("u-1") &&
                        e.winningBid() == 100L));
    }

    @Test
    void onAuctionClosed_invalidJson_discardsWithoutPayment() {
        consumer.onAuctionClosed("{invalid-json");

        verify(paymentService, never()).initiatePayment(any());
    }

    @Test
    void onAuctionClosed_paymentFails_throwsException() throws Exception {
        AuctionClosedEvent event = new AuctionClosedEvent(
                "a-1", "u-1", 100L, Map.of("u-1", 100L), 1, "i-1", "Test Item", "s-1", "2026-04-20T10:00:00Z");
        String payload = objectMapper.writeValueAsString(event);

        doThrow(new RuntimeException("Payment initiation failed"))
                .when(paymentService).initiatePayment(any());

        assertThrows(RuntimeException.class, () -> consumer.onAuctionClosed(payload));
        verify(paymentService).initiatePayment(any());
    }
}
