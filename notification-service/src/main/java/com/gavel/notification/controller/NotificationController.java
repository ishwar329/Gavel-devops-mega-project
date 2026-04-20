package com.gavel.notification.controller;

import com.gavel.notification.hub.NotificationHub;
import com.gavel.notification.model.StoredNotification;
import com.gavel.notification.store.NotificationStore;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class NotificationController {

    private final NotificationStore store;
    private final NotificationHub hub;

    public NotificationController(NotificationStore store, NotificationHub hub) {
        this.store = store;
        this.hub = hub;
    }

    @GetMapping("/notifications")
    public ResponseEntity<?> listNotifications(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        List<StoredNotification> notifications = store.list(userId);
        int unreadCount = store.unreadCount(userId);
        return ResponseEntity.ok(Map.of(
                "notifications", notifications,
                "unread_count", unreadCount
        ));
    }

    @PostMapping("/notifications/read")
    public ResponseEntity<?> markAllRead(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        store.markAllRead(userId);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> metrics() {
        return ResponseEntity.ok(hub.getMetrics());
    }
}
