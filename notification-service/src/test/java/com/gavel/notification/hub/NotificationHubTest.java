package com.gavel.notification.hub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gavel.notification.model.Metrics;
import com.gavel.notification.model.StoredNotification;
import com.gavel.notification.store.NotificationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationHubTest {

    @Mock
    private NotificationStore store;

    private ObjectMapper objectMapper;

    private NotificationHub hub;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        hub = new NotificationHub(store, objectMapper);
    }

    // --- registerAuction / unregisterAuction ---

    @Test
    void registerAuction_increasesConnectionCount() {
        WebSocketSession session = mockOpenSession("s1");

        hub.registerAuction("auction-1", session);

        Metrics metrics = hub.getMetrics();
        assertEquals(1, metrics.activeConnections());
    }

    @Test
    void unregisterAuction_decreasesConnectionCount() {
        WebSocketSession session = mockOpenSession("s1");

        hub.registerAuction("auction-1", session);
        hub.unregisterAuction("auction-1", session);

        Metrics metrics = hub.getMetrics();
        assertEquals(0, metrics.activeConnections());
    }

    @Test
    void unregisterAuction_noSessionsRegistered_doesNotThrow() {
        WebSocketSession session = mockOpenSession("s1");

        assertDoesNotThrow(() -> hub.unregisterAuction("auction-unknown", session));
    }

    @Test
    void registerAuction_multipleSessions_tracksAll() {
        WebSocketSession s1 = mockOpenSession("s1");
        WebSocketSession s2 = mockOpenSession("s2");

        hub.registerAuction("auction-1", s1);
        hub.registerAuction("auction-1", s2);

        Metrics metrics = hub.getMetrics();
        assertEquals(2, metrics.activeConnections());
    }

    // --- registerUser / unregisterUser ---

    @Test
    void registerUser_increasesConnectionCount() {
        WebSocketSession session = mockOpenSession("s1");

        hub.registerUser("user-1", session);

        Metrics metrics = hub.getMetrics();
        assertEquals(1, metrics.activeConnections());
    }

    @Test
    void unregisterUser_decreasesConnectionCount() {
        WebSocketSession session = mockOpenSession("s1");

        hub.registerUser("user-1", session);
        hub.unregisterUser("user-1", session);

        Metrics metrics = hub.getMetrics();
        assertEquals(0, metrics.activeConnections());
    }

    @Test
    void unregisterUser_noSessionsRegistered_doesNotThrow() {
        WebSocketSession session = mockOpenSession("s1");

        assertDoesNotThrow(() -> hub.unregisterUser("user-unknown", session));
    }

    // --- broadcast ---

    @Test
    void broadcast_noSessionsRegistered_returnsEarly() throws Exception {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "bid_accepted");

        hub.broadcast("auction-no-sessions", message, null);

        Metrics metrics = hub.getMetrics();
        assertEquals(0, metrics.totalBroadcasts());
    }

    @Test
    void broadcast_openSessions_sendsMessage() throws Exception {
        WebSocketSession session = mockOpenSession("s1");
        hub.registerAuction("auction-1", session);

        Map<String, Object> message = new HashMap<>();
        message.put("type", "bid_accepted");

        hub.broadcast("auction-1", message, null);

        verify(session).sendMessage(any(TextMessage.class));
        Metrics metrics = hub.getMetrics();
        assertEquals(1, metrics.totalBroadcasts());
    }

    @Test
    void broadcast_closedSession_skipped() throws Exception {
        WebSocketSession closedSession = mock(WebSocketSession.class);
        lenient().when(closedSession.getId()).thenReturn("closed-1");
        when(closedSession.isOpen()).thenReturn(false);

        hub.registerAuction("auction-1", closedSession);

        Map<String, Object> message = new HashMap<>();
        message.put("type", "bid_accepted");

        hub.broadcast("auction-1", message, null);

        verify(closedSession, never()).sendMessage(any(TextMessage.class));
        Metrics metrics = hub.getMetrics();
        assertEquals(1, metrics.totalBroadcasts());
    }

    @Test
    void broadcast_withBidAcceptedAt_recordsLatency() throws Exception {
        WebSocketSession session = mockOpenSession("s1");
        hub.registerAuction("auction-1", session);

        Map<String, Object> message = new HashMap<>();
        message.put("type", "bid_accepted");

        String bidAcceptedAt = Instant.now().minusMillis(50).toString();
        hub.broadcast("auction-1", message, bidAcceptedAt);

        Metrics metrics = hub.getMetrics();
        assertTrue(metrics.avgDeliveryLatencyMs() > 0, "Expected positive average latency");
        assertTrue(metrics.p99DeliveryLatencyMs() > 0, "Expected positive p99 latency");
    }

    @Test
    void broadcast_withNullBidAcceptedAt_noLatencyRecorded() throws Exception {
        WebSocketSession session = mockOpenSession("s1");
        hub.registerAuction("auction-1", session);

        Map<String, Object> message = new HashMap<>();
        message.put("type", "bid_accepted");

        hub.broadcast("auction-1", message, null);

        Metrics metrics = hub.getMetrics();
        assertEquals(0.0, metrics.avgDeliveryLatencyMs());
        assertEquals(0.0, metrics.p99DeliveryLatencyMs());
    }

    @Test
    void broadcast_withEmptyBidAcceptedAt_noLatencyRecorded() throws Exception {
        WebSocketSession session = mockOpenSession("s1");
        hub.registerAuction("auction-1", session);

        Map<String, Object> message = new HashMap<>();
        message.put("type", "bid_accepted");

        hub.broadcast("auction-1", message, "");

        Metrics metrics = hub.getMetrics();
        assertEquals(0.0, metrics.avgDeliveryLatencyMs());
        assertEquals(0.0, metrics.p99DeliveryLatencyMs());
    }

    @Test
    void broadcast_withInvalidBidAcceptedAt_noLatencyRecorded() throws Exception {
        WebSocketSession session = mockOpenSession("s1");
        hub.registerAuction("auction-1", session);

        Map<String, Object> message = new HashMap<>();
        message.put("type", "bid_accepted");

        hub.broadcast("auction-1", message, "not-a-timestamp");

        Metrics metrics = hub.getMetrics();
        assertEquals(0.0, metrics.avgDeliveryLatencyMs());
        assertEquals(0.0, metrics.p99DeliveryLatencyMs());
    }

    @Test
    void broadcast_sendThrowsException_doesNotPropagateAndStillCounts() throws Exception {
        WebSocketSession failingSession = mockOpenSession("fail-1");
        doThrow(new RuntimeException("connection reset")).when(failingSession).sendMessage(any());

        WebSocketSession goodSession = mockOpenSession("good-1");

        hub.registerAuction("auction-1", failingSession);
        hub.registerAuction("auction-1", goodSession);

        Map<String, Object> message = new HashMap<>();
        message.put("type", "bid_accepted");

        assertDoesNotThrow(() -> hub.broadcast("auction-1", message, null));
        verify(goodSession).sendMessage(any(TextMessage.class));
    }

    // --- sendToUser ---

    @Test
    void sendToUser_noSessions_returnsEarly() throws Exception {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "notification");

        hub.sendToUser("user-absent", message);
        // No exception, no sessions to verify
    }

    @Test
    void sendToUser_openSessions_sendsMessage() throws Exception {
        WebSocketSession session = mockOpenSession("u1");
        hub.registerUser("user-1", session);

        Map<String, Object> message = new HashMap<>();
        message.put("type", "notification");

        hub.sendToUser("user-1", message);

        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendToUser_closedSession_skipped() throws Exception {
        WebSocketSession closedSession = mock(WebSocketSession.class);
        lenient().when(closedSession.getId()).thenReturn("closed-u1");
        when(closedSession.isOpen()).thenReturn(false);

        hub.registerUser("user-1", closedSession);

        Map<String, Object> message = new HashMap<>();
        message.put("type", "notification");

        hub.sendToUser("user-1", message);

        verify(closedSession, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendToUser_sendThrowsException_doesNotPropagate() throws Exception {
        WebSocketSession failingSession = mockOpenSession("fail-u1");
        doThrow(new RuntimeException("broken pipe")).when(failingSession).sendMessage(any());

        hub.registerUser("user-1", failingSession);

        Map<String, Object> message = new HashMap<>();
        message.put("type", "notification");

        assertDoesNotThrow(() -> hub.sendToUser("user-1", message));
    }

    // --- storeAndPushNotification ---

    @Test
    void storeAndPushNotification_callsStoreAndSendsToUser() throws Exception {
        WebSocketSession session = mockOpenSession("u1");
        hub.registerUser("user-1", session);

        StoredNotification notification = new StoredNotification(
                "notif-1", "outbid", "auction-1", "Vintage Lamp",
                "You were outbid!", "/auctions/auction-1", 5000, System.currentTimeMillis(), false
        );

        when(store.unreadCount("user-1")).thenReturn(3);

        hub.storeAndPushNotification("user-1", notification);

        verify(store).add("user-1", notification);
        verify(store).unreadCount("user-1");
        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void storeAndPushNotification_noUserSessions_stillStoresNotification() {
        StoredNotification notification = new StoredNotification(
                "notif-2", "won", "auction-2", "Old Book",
                "You won!", "/auctions/auction-2", 10000, System.currentTimeMillis(), false
        );

        when(store.unreadCount("user-2")).thenReturn(1);

        hub.storeAndPushNotification("user-2", notification);

        verify(store).add("user-2", notification);
        verify(store).unreadCount("user-2");
    }

    // --- getMetrics ---

    @Test
    void getMetrics_noActivity_returnsZeros() {
        Metrics metrics = hub.getMetrics();

        assertEquals(0, metrics.activeConnections());
        assertEquals(0, metrics.totalBroadcasts());
        assertEquals(0.0, metrics.avgDeliveryLatencyMs());
        assertEquals(0.0, metrics.p99DeliveryLatencyMs());
    }

    @Test
    void getMetrics_afterBroadcasts_returnsCorrectCounts() throws Exception {
        WebSocketSession session = mockOpenSession("s1");
        hub.registerAuction("auction-1", session);

        Map<String, Object> msg1 = new HashMap<>();
        msg1.put("type", "bid");
        Map<String, Object> msg2 = new HashMap<>();
        msg2.put("type", "bid");

        hub.broadcast("auction-1", msg1, null);
        hub.broadcast("auction-1", msg2, null);

        Metrics metrics = hub.getMetrics();
        assertEquals(1, metrics.activeConnections());
        assertEquals(2, metrics.totalBroadcasts());
    }

    @Test
    void getMetrics_mixedAuctionAndUserConnections() {
        WebSocketSession s1 = mockOpenSession("s1");
        WebSocketSession s2 = mockOpenSession("s2");
        WebSocketSession s3 = mockOpenSession("s3");

        hub.registerAuction("auction-1", s1);
        hub.registerUser("user-1", s2);
        hub.registerUser("user-2", s3);

        Metrics metrics = hub.getMetrics();
        assertEquals(3, metrics.activeConnections());
    }

    // --- helper ---

    private WebSocketSession mockOpenSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        lenient().when(session.getId()).thenReturn(id);
        lenient().when(session.isOpen()).thenReturn(true);
        return session;
    }
}
