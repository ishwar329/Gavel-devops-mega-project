package com.gavel.payment.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.payment.service.PaymentService;
import com.gavel.shared.events.AuctionClosedEvent;
import com.gavel.shared.kafka.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.AUCTION_CLOSED, groupId = "payment-service")
    public void onAuctionClosed(String payload) {
        AuctionClosedEvent event;
        try {
            event = objectMapper.readValue(payload, AuctionClosedEvent.class);
        } catch (Exception e) {
            log.error("Unmarshal auction_closed error (discarding): {}", e.getMessage());
            return;
        }

        try {
            paymentService.initiatePayment(event);
        } catch (Exception e) {
            log.error("Initiate payment error for auction {}: {}", event.auctionId(), e.getMessage());
            throw e;
        }
    }
}
