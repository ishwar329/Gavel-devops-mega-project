package com.gavel.shared.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisStreamPublisherTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private StreamOperations<String, Object, Object> streamOps;

    private ObjectMapper objectMapper;
    private RedisStreamPublisher publisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        publisher = new RedisStreamPublisher(redisTemplate, objectMapper);
        lenient().when(redisTemplate.opsForStream()).thenReturn(streamOps);
    }

    @Test
    void publish_sendsToCorrectStream() {
        RecordId expectedId = RecordId.of("1-0");
        when(streamOps.add(any(StringRecord.class))).thenReturn(expectedId);

        record TestEvent(String name, int value) {}
        RecordId result = publisher.publish("test:stream", new TestEvent("hello", 42));

        assertEquals(expectedId, result);

        ArgumentCaptor<StringRecord> captor = ArgumentCaptor.forClass(StringRecord.class);
        verify(streamOps).add(captor.capture());

        StringRecord record = captor.getValue();
        assertEquals("test:stream", record.getStream());
        assertEquals("hello", record.getValue().get("name"));
        assertEquals("42", record.getValue().get("value"));
    }

    @Test
    void publish_serializesComplexObject() {
        when(streamOps.add(any(StringRecord.class))).thenReturn(RecordId.of("1-0"));

        record Event(String auctionId, long amount) {}
        publisher.publish("bid:placed", new Event("a-1", 5000));

        ArgumentCaptor<StringRecord> captor = ArgumentCaptor.forClass(StringRecord.class);
        verify(streamOps).add(captor.capture());

        assertEquals("a-1", captor.getValue().getValue().get("auctionId"));
        assertEquals("5000", captor.getValue().getValue().get("amount"));
    }
}
