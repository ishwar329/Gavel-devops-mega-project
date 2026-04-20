package com.gavel.notification.hub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.notification.model.Metrics;
import com.gavel.notification.model.StoredNotification;
import com.gavel.notification.store.NotificationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class NotificationHub {

    private static final Logger log = LoggerFactory.getLogger(NotificationHub.class);
    private static final int LATENCY_BUFFER_SIZE = 10000;

    private final ConcurrentHashMap<String, Set<WebSocketSession>> auctionClients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<WebSocketSession>> userClients = new ConcurrentHashMap<>();
    private final AtomicLong connectionCount = new AtomicLong(0);
    private final AtomicLong broadcastCount = new AtomicLong(0);

    private final double[] latencyBuffer = new double[LATENCY_BUFFER_SIZE];
    private int latencyIndex = 0;
    private int latencyCount = 0;
    private final Object latencyLock = new Object();

    private final NotificationStore store;
    private final ObjectMapper objectMapper;

    public NotificationHub(NotificationStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public void registerAuction(String auctionId, WebSocketSession session) {
        auctionClients.computeIfAbsent(auctionId, k -> ConcurrentHashMap.newKeySet()).add(session);
        connectionCount.incrementAndGet();
    }

    public void unregisterAuction(String auctionId, WebSocketSession session) {
        Set<WebSocketSession> sessions = auctionClients.get(auctionId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                auctionClients.remove(auctionId, Collections.emptySet());
            }
        }
        connectionCount.decrementAndGet();
    }

    public void registerUser(String userId, WebSocketSession session) {
        userClients.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        connectionCount.incrementAndGet();
    }

    public void unregisterUser(String userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = userClients.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                userClients.remove(userId, Collections.emptySet());
            }
        }
        connectionCount.decrementAndGet();
    }

    public void broadcast(String auctionId, Map<String, Object> message, String bidAcceptedAt) {
        Set<WebSocketSession> sessions = auctionClients.get(auctionId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        if (bidAcceptedAt != null && !bidAcceptedAt.isEmpty()) {
            try {
                Instant accepted = Instant.parse(bidAcceptedAt);
                double latencyMs = (Instant.now().toEpochMilli() - accepted.toEpochMilli());
                recordLatency(latencyMs);
            } catch (Exception ignored) {
            }
        }

        message.put("delivered_at", Instant.now().toString());

        try {
            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(textMessage);
                    }
                } catch (Exception e) {
                    log.error("Failed to send to session {}: {}", session.getId(), e.getMessage());
                }
            }
            broadcastCount.incrementAndGet();
        } catch (Exception e) {
            log.error("Failed to serialize broadcast message: {}", e.getMessage());
        }
    }

    public void sendToUser(String userId, Map<String, Object> message) {
        Set<WebSocketSession> sessions = userClients.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(textMessage);
                    }
                } catch (Exception e) {
                    log.error("Failed to send to user session {}: {}", session.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to serialize user message: {}", e.getMessage());
        }
    }

    public void storeAndPushNotification(String userId, StoredNotification notification) {
        store.add(userId, notification);
        int unreadCount = store.unreadCount(userId);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "notification");
        message.put("notification", notification);
        message.put("unread_count", unreadCount);

        sendToUser(userId, message);
    }

    public Metrics getMetrics() {
        double avg = 0;
        double p99 = 0;

        synchronized (latencyLock) {
            if (latencyCount > 0) {
                int count = Math.min(latencyCount, LATENCY_BUFFER_SIZE);
                double sum = 0;
                double[] sorted = new double[count];
                System.arraycopy(latencyBuffer, 0, sorted, 0, count);
                Arrays.sort(sorted);

                for (double v : sorted) {
                    sum += v;
                }
                avg = sum / count;
                p99 = sorted[(int) Math.floor(count * 0.99)];
            }
        }

        return new Metrics(connectionCount.get(), broadcastCount.get(), avg, p99);
    }

    private void recordLatency(double latencyMs) {
        synchronized (latencyLock) {
            latencyBuffer[latencyIndex % LATENCY_BUFFER_SIZE] = latencyMs;
            latencyIndex = (latencyIndex + 1) % LATENCY_BUFFER_SIZE;
            latencyCount++;
        }
    }
}
