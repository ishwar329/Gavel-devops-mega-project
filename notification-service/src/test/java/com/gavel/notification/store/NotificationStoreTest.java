package com.gavel.notification.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.notification.model.StoredNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationStoreTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ZSetOperations<String, String> zsetOps;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private ValueOperations<String, String> valueOps;

    private ObjectMapper objectMapper;
    private NotificationStore store;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(redisTemplate.opsForZSet()).thenReturn(zsetOps);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        store = new NotificationStore(redisTemplate, objectMapper);
    }

    private StoredNotification makeNotification(String id) {
        return new StoredNotification(id, "outbid", "a-1", "Lamp",
                "You were outbid!", "/auction/a-1", 500, System.currentTimeMillis(), false);
    }

    @Test
    void add_storesNotificationInZsetAndDedup() {
        StoredNotification notif = makeNotification("n-1");

        when(hashOps.get(anyString(), eq("n-1"))).thenReturn(null);
        when(zsetOps.zCard(anyString())).thenReturn(1L);

        store.add("u-1", notif);

        verify(zsetOps).add(eq("notifications:u-1"), anyString(), eq((double) notif.createdAt()));
        verify(hashOps).put(eq("notifications:u-1:dedup"), eq("n-1"), anyString());
        verify(valueOps).increment("notifications:u-1:unread");
        verify(redisTemplate, times(3)).expire(anyString(), any(Duration.class));
    }

    @Test
    void add_existingDedup_removesOldEntry() throws Exception {
        StoredNotification notif = makeNotification("n-1");
        String oldJson = objectMapper.writeValueAsString(notif);

        when(hashOps.get(anyString(), eq("n-1"))).thenReturn(oldJson);
        when(zsetOps.zCard(anyString())).thenReturn(1L);

        store.add("u-1", notif);

        verify(zsetOps).remove("notifications:u-1", oldJson);
    }

    @Test
    void add_exceedsMax_trimsOldEntries() {
        StoredNotification notif = makeNotification("n-1");

        when(hashOps.get(anyString(), eq("n-1"))).thenReturn(null);
        when(zsetOps.zCard("notifications:u-1")).thenReturn(25L);

        store.add("u-1", notif);

        verify(zsetOps).removeRange("notifications:u-1", 0, 4);
    }

    @Test
    void list_emptySet_returnsEmptyList() {
        when(zsetOps.reverseRange("notifications:u-1", 0, 19)).thenReturn(null);

        List<StoredNotification> result = store.list("u-1");
        assertTrue(result.isEmpty());
    }

    @Test
    void list_withEntries_returnsNotifications() throws Exception {
        StoredNotification notif = makeNotification("n-1");
        String json = objectMapper.writeValueAsString(notif);

        when(zsetOps.reverseRange("notifications:u-1", 0, 19))
                .thenReturn(new LinkedHashSet<>(List.of(json)));

        List<StoredNotification> result = store.list("u-1");
        assertEquals(1, result.size());
        assertEquals("n-1", result.get(0).id());
    }

    @Test
    void markAllRead_updatesUnreadNotifications() throws Exception {
        StoredNotification notif = new StoredNotification("n-1", "outbid", "a-1", "Lamp",
                "Outbid!", "/auction/a-1", 500, 1000L, false);
        String json = objectMapper.writeValueAsString(notif);

        when(zsetOps.reverseRange("notifications:u-1", 0, -1))
                .thenReturn(new LinkedHashSet<>(List.of(json)));

        store.markAllRead("u-1");

        verify(zsetOps).remove("notifications:u-1", json);
        verify(zsetOps).add(eq("notifications:u-1"), argThat(s -> s.contains("\"read\":true")), eq(1000.0));
        verify(valueOps).set("notifications:u-1:unread", "0");
    }

    @Test
    void markAllRead_alreadyRead_doesNotUpdate() throws Exception {
        StoredNotification notif = new StoredNotification("n-1", "outbid", "a-1", "Lamp",
                "Outbid!", "/auction/a-1", 500, 1000L, true);
        String json = objectMapper.writeValueAsString(notif);

        when(zsetOps.reverseRange("notifications:u-1", 0, -1))
                .thenReturn(new LinkedHashSet<>(List.of(json)));

        store.markAllRead("u-1");

        verify(zsetOps, never()).remove(eq("notifications:u-1"), eq(json));
        verify(valueOps).set("notifications:u-1:unread", "0");
    }

    @Test
    void markAllRead_emptySet_returnsEarly() {
        when(zsetOps.reverseRange("notifications:u-1", 0, -1)).thenReturn(null);

        store.markAllRead("u-1");

        verify(valueOps, never()).set(anyString(), eq("0"));
    }

    @Test
    void unreadCount_validCount_returnsNumber() {
        when(valueOps.get("notifications:u-1:unread")).thenReturn("5");
        assertEquals(5, store.unreadCount("u-1"));
    }

    @Test
    void unreadCount_nullCount_returnsZero() {
        when(valueOps.get("notifications:u-1:unread")).thenReturn(null);
        assertEquals(0, store.unreadCount("u-1"));
    }

    @Test
    void unreadCount_exception_returnsZero() {
        when(valueOps.get("notifications:u-1:unread")).thenThrow(new RuntimeException("redis down"));
        assertEquals(0, store.unreadCount("u-1"));
    }

    @Test
    void add_exception_doesNotPropagate() {
        StoredNotification notif = makeNotification("n-1");
        when(hashOps.get(anyString(), anyString())).thenThrow(new RuntimeException("redis down"));

        assertDoesNotThrow(() -> store.add("u-1", notif));
    }

    @Test
    void list_exception_returnsEmptyList() {
        when(zsetOps.reverseRange(anyString(), anyLong(), anyLong())).thenThrow(new RuntimeException("redis down"));

        List<StoredNotification> result = store.list("u-1");
        assertTrue(result.isEmpty());
    }
}
