package com.gavel.shared.redis;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class RedisStreamConsumer {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final StringRedisTemplate redisTemplate;
    private final String streamKey;
    private final String groupName;
    private final String consumerName;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    protected RedisStreamConsumer(StringRedisTemplate redisTemplate,
                                  String streamKey,
                                  String groupName,
                                  String consumerName) {
        this.redisTemplate = redisTemplate;
        this.streamKey = streamKey;
        this.groupName = groupName;
        this.consumerName = consumerName;
    }

    public void start() {
        ensureConsumerGroup();
        running.set(true);
        executor.submit(this::pollLoop);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        executor.shutdown();
    }

    @SuppressWarnings("unchecked")
    private void pollLoop() {
        while (running.get()) {
            try {
                List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                        Consumer.from(groupName, consumerName),
                        StreamReadOptions.empty().count(10).block(Duration.ofSeconds(2)),
                        StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                );
                if (records != null) {
                    for (MapRecord<String, Object, Object> record : records) {
                        try {
                            Map<String, String> fields = new HashMap<>();
                            record.getValue().forEach((k, v) -> fields.put(k.toString(), v.toString()));
                            handleMessage(record.getId(), fields);
                            redisTemplate.opsForStream().acknowledge(streamKey, groupName, record.getId());
                        } catch (Exception e) {
                            log.error("Error processing message {}: {}", record.getId(), e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.error("Error reading stream {}: {}", streamKey, e.getMessage());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    protected abstract void handleMessage(RecordId recordId, Map<String, String> fields);

    private void ensureConsumerGroup() {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), groupName);
        } catch (Exception e) {
            log.debug("Consumer group {} may already exist: {}", groupName, e.getMessage());
        }
    }
}
