package com.gavel.shared.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaPublisherTest {

    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper;
    private KafkaPublisher publisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        publisher = new KafkaPublisher(kafkaTemplate, objectMapper);
    }

    @Test
    void publish_sendsToCorrectTopicWithKey() {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("test.topic", 0), 0, 0, 0, 0, 0);
        SendResult<String, String> sendResult = new SendResult<>(
                new ProducerRecord<>("test.topic", "key-1", "{}"), metadata);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        record TestEvent(String name, int value) {}
        publisher.publish("test.topic", "key-1", new TestEvent("hello", 42));

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());
        assertEquals("test.topic", topicCaptor.getValue());
        assertEquals("key-1", keyCaptor.getValue());
        assertTrue(valueCaptor.getValue().contains("hello"));
        assertTrue(valueCaptor.getValue().contains("42"));
    }

    @Test
    void publish_serializesComplexObject() {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("bid.placed", 0), 0, 0, 0, 0, 0);
        SendResult<String, String> sendResult = new SendResult<>(
                new ProducerRecord<>("bid.placed", "a-1", "{}"), metadata);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        record Event(String auctionId, long amount) {}
        publisher.publish("bid.placed", "a-1", new Event("a-1", 5000));

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("bid.placed"), eq("a-1"), valueCaptor.capture());
        assertTrue(valueCaptor.getValue().contains("a-1"));
        assertTrue(valueCaptor.getValue().contains("5000"));
    }
}
