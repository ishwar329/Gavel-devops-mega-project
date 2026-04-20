package com.gavel.payment.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.shared.events.PaymentFailedEvent;
import com.gavel.shared.events.PaymentProcessedEvent;
import com.gavel.shared.events.RefundProcessedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentEventPublisherTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private StreamOperations<String, Object, Object> streamOps;

    private PaymentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        publisher = new PaymentEventPublisher(redisTemplate, new ObjectMapper());
    }

    @Test
    void publishPaymentProcessed_sendsToCorrectStream() {
        PaymentProcessedEvent event = new PaymentProcessedEvent("p-1", "a-1", "u-1", 1000, "now");

        publisher.publishPaymentProcessed(event);

        ArgumentCaptor<StringRecord> captor = ArgumentCaptor.forClass(StringRecord.class);
        verify(streamOps).add(captor.capture());
        assertEquals("payment:processed", captor.getValue().getStream());
        assertTrue(captor.getValue().getValue().get("payload").contains("p-1"));
    }

    @Test
    void publishPaymentFailed_sendsToCorrectStream() {
        PaymentFailedEvent event = new PaymentFailedEvent("p-1", "a-1", "u-1", 1000, "declined", "now");

        publisher.publishPaymentFailed(event);

        ArgumentCaptor<StringRecord> captor = ArgumentCaptor.forClass(StringRecord.class);
        verify(streamOps).add(captor.capture());
        assertEquals("payment:failed", captor.getValue().getStream());
    }

    @Test
    void publishRefundProcessed_sendsToCorrectStream() {
        RefundProcessedEvent event = new RefundProcessedEvent("p-1", "a-1", "u-1", 1000, "now");

        publisher.publishRefundProcessed(event);

        ArgumentCaptor<StringRecord> captor = ArgumentCaptor.forClass(StringRecord.class);
        verify(streamOps).add(captor.capture());
        assertEquals("refund:processed", captor.getValue().getStream());
    }
}
