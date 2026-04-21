package com.gavel.payment.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.shared.events.PaymentFailedEvent;
import com.gavel.shared.events.PaymentProcessedEvent;
import com.gavel.shared.events.RefundProcessedEvent;
import com.gavel.shared.kafka.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PaymentEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishPaymentProcessed(PaymentProcessedEvent event) {
        publish(Topics.PAYMENT_PROCESSED, event.paymentId(), event);
    }

    public void publishPaymentFailed(PaymentFailedEvent event) {
        publish(Topics.PAYMENT_FAILED, event.paymentId(), event);
    }

    public void publishRefundProcessed(RefundProcessedEvent event) {
        publish(Topics.REFUND_PROCESSED, event.paymentId(), event);
    }

    private void publish(String topic, String key, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, key, payload);
            log.debug("Published to {} key={}", topic, key);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event for topic {}: {}", topic, e.getMessage());
        }
    }
}
