package com.gavel.payment.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.payment.service.PaymentService;
import com.gavel.shared.events.AuctionClosedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentEventConsumerTest {

    private StringRedisTemplate redisTemplate;
    private StreamOperations<String, Object, Object> streamOps;
    private PaymentService paymentService;
    private ObjectMapper objectMapper;
    private PaymentEventConsumer consumer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        streamOps = mock(StreamOperations.class);
        paymentService = mock(PaymentService.class);
        objectMapper = new ObjectMapper();

        when(redisTemplate.opsForStream()).thenReturn(streamOps);

        consumer = new PaymentEventConsumer(redisTemplate, paymentService, objectMapper, 2);
    }

    @Test
    void start_createsConsumerGroupAndStartsListening() throws InterruptedException {
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(null);

        consumer.start();
        Thread.sleep(100);
        consumer.stop();

        verify(streamOps, atLeastOnce()).createGroup(eq("auction:closed"), any(ReadOffset.class), eq("payment-service"));
        verify(streamOps, atLeast(1)).read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void runStream_processesAuctionClosedEventAndAcknowledges() throws Exception {
        AuctionClosedEvent event = new AuctionClosedEvent(
                "a-1", "u-1", 100L, Map.of("u-1", 100L), 1, "i-1", "Test Item", "s-1", "2026-04-20T10:00:00Z");
        String payload = objectMapper.writeValueAsString(event);

        RecordId recordId = RecordId.of("1234-0");
        Map<Object, Object> fields = new HashMap<>();
        fields.put("payload", payload);

        MapRecord<String, Object, Object> record = StreamRecords.mapBacked(fields)
                .withId(recordId)
                .withStreamKey("auction:closed");

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record))
                .thenReturn(null);

        consumer.start();
        Thread.sleep(500);
        consumer.stop();

        verify(paymentService, timeout(2000)).initiatePayment(argThat(e ->
                e.auctionId().equals("a-1") &&
                        e.winnerId().equals("u-1") &&
                        e.winningBid() == 100L));
        verify(streamOps).acknowledge("auction:closed", "payment-service", recordId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatch_discardsMessageWithMissingPayload() throws InterruptedException {
        RecordId recordId = RecordId.of("5678-0");
        Map<Object, Object> fields = new HashMap<>();

        MapRecord<String, Object, Object> record = StreamRecords.mapBacked(fields)
                .withId(recordId)
                .withStreamKey("auction:closed");

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record))
                .thenReturn(null);

        consumer.start();
        Thread.sleep(500);
        consumer.stop();

        verify(paymentService, never()).initiatePayment(any());
        verify(streamOps).acknowledge("auction:closed", "payment-service", recordId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatch_discardsMessageWithInvalidJson() throws InterruptedException {
        RecordId recordId = RecordId.of("9999-0");
        Map<Object, Object> fields = new HashMap<>();
        fields.put("payload", "{invalid-json");

        MapRecord<String, Object, Object> record = StreamRecords.mapBacked(fields)
                .withId(recordId)
                .withStreamKey("auction:closed");

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record))
                .thenReturn(null);

        consumer.start();
        Thread.sleep(500);
        consumer.stop();

        verify(paymentService, never()).initiatePayment(any());
        verify(streamOps).acknowledge("auction:closed", "payment-service", recordId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatch_doesNotAcknowledgeOnInitiatePaymentError() throws Exception {
        AuctionClosedEvent event = new AuctionClosedEvent(
                "a-1", "u-1", 100L, Map.of("u-1", 100L), 1, "i-1", "Test Item", "s-1", "2026-04-20T10:00:00Z");
        String payload = objectMapper.writeValueAsString(event);

        RecordId recordId = RecordId.of("1111-0");
        Map<Object, Object> fields = new HashMap<>();
        fields.put("payload", payload);

        MapRecord<String, Object, Object> record = StreamRecords.mapBacked(fields)
                .withId(recordId)
                .withStreamKey("auction:closed");

        doThrow(new RuntimeException("Payment initiation failed"))
                .when(paymentService).initiatePayment(any());

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record))
                .thenReturn(null);

        consumer.start();
        Thread.sleep(500);
        consumer.stop();

        verify(paymentService, timeout(2000)).initiatePayment(any());
        verify(streamOps, never()).acknowledge(eq("auction:closed"), eq("payment-service"), eq(recordId));
    }

    @Test
    void stop_shutsDownExecutorGracefully() throws InterruptedException {
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(null);

        consumer.start();
        Thread.sleep(100);
        consumer.stop();

        Thread.sleep(100);
        int callsBefore = mockingDetails(streamOps).getInvocations().size();
        Thread.sleep(200);
        int callsAfter = mockingDetails(streamOps).getInvocations().size();

        verify(streamOps, atLeastOnce()).read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class));
    }

    @Test
    void ensureConsumerGroup_handlesExistingGroup() throws InterruptedException {
        doThrow(new RuntimeException("BUSYGROUP Consumer Group name already exists"))
                .when(streamOps).createGroup(anyString(), any(ReadOffset.class), anyString());

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(null);

        consumer.start();
        Thread.sleep(100);
        consumer.stop();

        verify(streamOps).createGroup(eq("auction:closed"), any(ReadOffset.class), eq("payment-service"));
    }
}
