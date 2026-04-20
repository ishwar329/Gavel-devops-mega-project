package com.gavel.shared.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

public class RedisStreamPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisStreamPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public RecordId publish(String streamKey, Object event) {
        Map<String, String> fields = objectMapper.convertValue(event, new TypeReference<>() {});
        var record = StreamRecords.string(fields).withStreamKey(streamKey);
        return redisTemplate.opsForStream().add(record);
    }
}
