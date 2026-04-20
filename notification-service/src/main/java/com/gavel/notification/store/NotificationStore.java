package com.gavel.notification.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.notification.model.StoredNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
public class NotificationStore {

    private static final Logger log = LoggerFactory.getLogger(NotificationStore.class);
    private static final int MAX_NOTIFICATIONS = 20;
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public NotificationStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void add(String userId, StoredNotification notification) {
        String zsetKey = "notifications:" + userId;
        String dedupKey = "notifications:" + userId + ":dedup";
        String unreadKey = "notifications:" + userId + ":unread";

        try {
            String json = objectMapper.writeValueAsString(notification);

            String existing = (String) redisTemplate.opsForHash().get(dedupKey, notification.id());
            if (existing != null) {
                redisTemplate.opsForZSet().remove(zsetKey, existing);
            }

            redisTemplate.opsForZSet().add(zsetKey, json, notification.createdAt());
            redisTemplate.opsForHash().put(dedupKey, notification.id(), json);

            Long size = redisTemplate.opsForZSet().zCard(zsetKey);
            if (size != null && size > MAX_NOTIFICATIONS) {
                redisTemplate.opsForZSet().removeRange(zsetKey, 0, size - MAX_NOTIFICATIONS - 1);
            }

            redisTemplate.expire(zsetKey, TTL);
            redisTemplate.expire(dedupKey, TTL);
            redisTemplate.expire(unreadKey, TTL);

            redisTemplate.opsForValue().increment(unreadKey);
        } catch (Exception e) {
            log.error("Failed to store notification for user {}: {}", userId, e.getMessage());
        }
    }

    public List<StoredNotification> list(String userId) {
        String zsetKey = "notifications:" + userId;
        try {
            Set<String> entries = redisTemplate.opsForZSet().reverseRange(zsetKey, 0, MAX_NOTIFICATIONS - 1);
            if (entries == null || entries.isEmpty()) {
                return Collections.emptyList();
            }
            List<StoredNotification> result = new ArrayList<>();
            for (String json : entries) {
                result.add(objectMapper.readValue(json, StoredNotification.class));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to list notifications for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public void markAllRead(String userId) {
        String zsetKey = "notifications:" + userId;
        String dedupKey = "notifications:" + userId + ":dedup";
        String unreadKey = "notifications:" + userId + ":unread";

        try {
            Set<String> entries = redisTemplate.opsForZSet().reverseRange(zsetKey, 0, -1);
            if (entries == null || entries.isEmpty()) {
                return;
            }

            for (String json : entries) {
                StoredNotification notif = objectMapper.readValue(json, StoredNotification.class);
                if (!notif.read()) {
                    StoredNotification updated = notif.withRead(true);
                    String updatedJson = objectMapper.writeValueAsString(updated);

                    redisTemplate.opsForZSet().remove(zsetKey, json);
                    redisTemplate.opsForZSet().add(zsetKey, updatedJson, notif.createdAt());
                    redisTemplate.opsForHash().put(dedupKey, notif.id(), updatedJson);
                }
            }

            redisTemplate.opsForValue().set(unreadKey, "0");
            redisTemplate.expire(unreadKey, TTL);
        } catch (Exception e) {
            log.error("Failed to mark all read for user {}: {}", userId, e.getMessage());
        }
    }

    public int unreadCount(String userId) {
        String unreadKey = "notifications:" + userId + ":unread";
        try {
            String count = redisTemplate.opsForValue().get(unreadKey);
            return count != null ? Integer.parseInt(count) : 0;
        } catch (Exception e) {
            log.error("Failed to get unread count for user {}: {}", userId, e.getMessage());
            return 0;
        }
    }
}
