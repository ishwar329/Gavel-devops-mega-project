package com.gavel.notification.controller;

import com.gavel.notification.hub.NotificationHub;
import com.gavel.notification.model.Metrics;
import com.gavel.notification.model.StoredNotification;
import com.gavel.notification.store.NotificationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationStore store;

    @Mock
    private NotificationHub hub;

    @Mock
    private Authentication authentication;

    private NotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(store, hub);
    }

    // --- listNotifications ---

    @Test
    void listNotifications_returnsNotificationsAndUnreadCount() {
        when(authentication.getPrincipal()).thenReturn("user-1");

        StoredNotification n1 = new StoredNotification(
                "n1", "outbid", "a1", "Vintage Lamp",
                "You were outbid!", "/auctions/a1", 5000, 1000L, false
        );
        StoredNotification n2 = new StoredNotification(
                "n2", "won", "a2", "Old Book",
                "You won!", "/auctions/a2", 10000, 2000L, true
        );

        when(store.list("user-1")).thenReturn(List.of(n1, n2));
        when(store.unreadCount("user-1")).thenReturn(1);

        ResponseEntity<?> response = controller.listNotifications(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);

        @SuppressWarnings("unchecked")
        List<StoredNotification> notifications = (List<StoredNotification>) body.get("notifications");
        assertEquals(2, notifications.size());
        assertEquals(1, body.get("unread_count"));

        verify(store).list("user-1");
        verify(store).unreadCount("user-1");
    }

    @Test
    void listNotifications_emptyList_returnsOkWithEmptyNotifications() {
        when(authentication.getPrincipal()).thenReturn("user-2");
        when(store.list("user-2")).thenReturn(List.of());
        when(store.unreadCount("user-2")).thenReturn(0);

        ResponseEntity<?> response = controller.listNotifications(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);

        @SuppressWarnings("unchecked")
        List<StoredNotification> notifications = (List<StoredNotification>) body.get("notifications");
        assertTrue(notifications.isEmpty());
        assertEquals(0, body.get("unread_count"));
    }

    // --- markAllRead ---

    @Test
    void markAllRead_callsStoreAndReturnsOk() {
        when(authentication.getPrincipal()).thenReturn("user-1");

        ResponseEntity<?> response = controller.markAllRead(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(true, body.get("ok"));

        verify(store).markAllRead("user-1");
    }

    // --- metrics ---

    @Test
    void metrics_returnsHubMetrics() {
        Metrics expectedMetrics = new Metrics(5, 100, 12.5, 45.0);
        when(hub.getMetrics()).thenReturn(expectedMetrics);

        ResponseEntity<?> response = controller.metrics();

        assertEquals(HttpStatus.OK, response.getStatusCode());

        Metrics body = (Metrics) response.getBody();
        assertNotNull(body);
        assertEquals(5, body.activeConnections());
        assertEquals(100, body.totalBroadcasts());
        assertEquals(12.5, body.avgDeliveryLatencyMs());
        assertEquals(45.0, body.p99DeliveryLatencyMs());

        verify(hub).getMetrics();
    }

    @Test
    void metrics_zeroActivity_returnsZeroMetrics() {
        Metrics zeroMetrics = new Metrics(0, 0, 0.0, 0.0);
        when(hub.getMetrics()).thenReturn(zeroMetrics);

        ResponseEntity<?> response = controller.metrics();

        assertEquals(HttpStatus.OK, response.getStatusCode());

        Metrics body = (Metrics) response.getBody();
        assertNotNull(body);
        assertEquals(0, body.activeConnections());
        assertEquals(0, body.totalBroadcasts());
        assertEquals(0.0, body.avgDeliveryLatencyMs());
        assertEquals(0.0, body.p99DeliveryLatencyMs());
    }
}
