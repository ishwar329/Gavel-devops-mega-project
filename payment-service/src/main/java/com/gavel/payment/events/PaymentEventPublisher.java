package com.gavel.payment.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.shared.events.PaymentFailedEvent;
import com.gavel.shared.events.PaymentProcessedEvent;
import com.gavel.shared.events.RefundProcessedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private static final String STREAM_PAYMENT_PROCESSED = "payment:processed";
    private static final String STREAM_PAYMENT_FAILED = "payment:failed";
    private static final String STREAM_REFUND_PROCESSED = "refund:processed";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PaymentEventPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishPaymentProcessed(PaymentProcessedEvent event) {
        publish(STREAM_PAYMENT_PROCESSED, event);
    }

    public void publishPaymentFailed(PaymentFailedEvent event) {
        publish(STREAM_PAYMENT_FAILED, event);
    }

    public void publishRefundProcessed(RefundProcessedEvent event) {
        publish(STREAM_REFUND_PROCESSED, event);
    }

    private void publish(String stream, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            var record = StreamRecords.string(Map.of("payload", payload)).withStreamKey(stream);
            redisTemplate.opsForStream().add(record);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event for stream {}: {}", stream, e.getMessage());
        }
    }
}
