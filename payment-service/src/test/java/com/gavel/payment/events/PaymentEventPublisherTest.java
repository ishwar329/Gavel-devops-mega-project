package com.gavel.payment.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.shared.events.PaymentFailedEvent;
import com.gavel.shared.events.PaymentProcessedEvent;
import com.gavel.shared.events.RefundProcessedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentEventPublisherTest {

    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private PaymentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("t", 0), 0, 0, 0, 0, 0);
        SendResult<String, String> sendResult = new SendResult<>(new ProducerRecord<>("t", ""), metadata);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        publisher = new PaymentEventPublisher(kafkaTemplate, new ObjectMapper());
    }

    @Test
    void publishPaymentProcessed_sendsToCorrectTopic() {
        PaymentProcessedEvent event = new PaymentProcessedEvent("p-1", "a-1", "u-1", 1000, "now");

        publisher.publishPaymentProcessed(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());
        assertEquals("payment.processed", topicCaptor.getValue());
        assertEquals("p-1", keyCaptor.getValue());
        assertTrue(valueCaptor.getValue().contains("p-1"));
    }

    @Test
    void publishPaymentFailed_sendsToCorrectTopic() {
        PaymentFailedEvent event = new PaymentFailedEvent("p-1", "a-1", "u-1", 1000, "declined", "now");

        publisher.publishPaymentFailed(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), eq("p-1"), anyString());
        assertEquals("payment.failed", topicCaptor.getValue());
    }

    @Test
    void publishRefundProcessed_sendsToCorrectTopic() {
        RefundProcessedEvent event = new RefundProcessedEvent("p-1", "a-1", "u-1", 1000, "now");

        publisher.publishRefundProcessed(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), eq("p-1"), anyString());
        assertEquals("refund.processed", topicCaptor.getValue());
    }
}
