package com.gavel.shared.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisStreamConsumerTest {

    private StringRedisTemplate redisTemplate;
    private StreamOperations<String, Object, Object> streamOps;
    private TestConsumer consumer;
    private CountDownLatch messageHandled;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        streamOps = mock(StreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        messageHandled = new CountDownLatch(1);
        consumer = new TestConsumer(redisTemplate, "test:stream", "test-group", "consumer-1", messageHandled);
    }

    @Test
    void start_createsConsumerGroupAndStartsPolling() throws InterruptedException {
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(null);

        consumer.start();
        Thread.sleep(100);
        consumer.stop();

        verify(streamOps, atLeastOnce()).createGroup(eq("test:stream"), any(ReadOffset.class), eq("test-group"));
        verify(streamOps, atLeast(1)).read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void pollLoop_processesMessagesAndAcknowledges() throws InterruptedException {
        RecordId recordId = RecordId.of("1234-0");
        Map<Object, Object> fields = new HashMap<>();
        fields.put("type", "test-event");
        fields.put("data", "payload");

        MapRecord<String, Object, Object> record = StreamRecords.mapBacked(fields)
                .withId(recordId)
                .withStreamKey("test:stream");

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record))
                .thenReturn(null);

        consumer.start();
        boolean handled = messageHandled.await(2, TimeUnit.SECONDS);
        consumer.stop();

        assertThat(handled).isTrue();
        assertThat(consumer.lastRecordId).isEqualTo(recordId);
        assertThat(consumer.lastFields).containsEntry("type", "test-event");
        assertThat(consumer.lastFields).containsEntry("data", "payload");
        verify(streamOps).acknowledge("test:stream", "test-group", recordId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pollLoop_continuesOnHandlerException() throws InterruptedException {
        RecordId recordId = RecordId.of("5678-0");
        Map<Object, Object> fields = Map.of("error", "trigger");

        MapRecord<String, Object, Object> record = StreamRecords.mapBacked(fields)
                .withId(recordId)
                .withStreamKey("test:stream");

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record))
                .thenReturn(null);

        CountDownLatch errorHandled = new CountDownLatch(1);
        TestConsumer errorConsumer = new TestConsumer(redisTemplate, "test:stream", "test-group", "consumer-2", errorHandled) {
            @Override
            protected void handleMessage(RecordId id, Map<String, String> fields) {
                errorHandled.countDown();
                throw new RuntimeException("handler error");
            }
        };

        errorConsumer.start();
        boolean handled = errorHandled.await(2, TimeUnit.SECONDS);
        errorConsumer.stop();

        assertThat(handled).isTrue();
        verify(streamOps, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
    }

    @Test
    void stop_shutsDownExecutorAndStopsPolling() throws InterruptedException {
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(null);

        consumer.start();
        Thread.sleep(100);
        consumer.stop();

        Thread.sleep(100);
        int callsBefore = mockingDetails(streamOps).getInvocations().size();
        Thread.sleep(200);
        int callsAfter = mockingDetails(streamOps).getInvocations().size();

        assertThat(callsAfter).isEqualTo(callsBefore);
    }

    @Test
    void ensureConsumerGroup_handlesExistingGroup() {
        doThrow(new RuntimeException("BUSYGROUP Consumer Group name already exists"))
                .when(streamOps).createGroup(anyString(), any(ReadOffset.class), anyString());

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(null);

        consumer.start();
        consumer.stop();

        verify(streamOps).createGroup(eq("test:stream"), any(ReadOffset.class), eq("test-group"));
    }

    static class TestConsumer extends RedisStreamConsumer {
        private final CountDownLatch latch;
        RecordId lastRecordId;
        Map<String, String> lastFields;

        public TestConsumer(StringRedisTemplate redisTemplate, String streamKey, String groupName,
                            String consumerName, CountDownLatch latch) {
            super(redisTemplate, streamKey, groupName, consumerName);
            this.latch = latch;
        }

        @Override
        protected void handleMessage(RecordId recordId, Map<String, String> fields) {
            this.lastRecordId = recordId;
            this.lastFields = fields;
            latch.countDown();
        }
    }
}
