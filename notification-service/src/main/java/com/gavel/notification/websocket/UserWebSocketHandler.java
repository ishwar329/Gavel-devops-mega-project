package com.gavel.notification.websocket;

import com.gavel.notification.hub.NotificationHub;
import com.gavel.shared.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;

@Component
public class UserWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(UserWebSocketHandler.class);
    private static final int SEND_TIME_LIMIT = 5000;
    private static final int BUFFER_SIZE_LIMIT = 64 * 1024;
    private static final String USER_ID_ATTR = "userId";

    private final NotificationHub hub;
    private final JwtTokenProvider jwtTokenProvider;

    public UserWebSocketHandler(NotificationHub hub, JwtTokenProvider jwtTokenProvider) {
        this.hub = hub;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = extractUserId(session);
        if (userId == null) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception ignored) {
            }
            return;
        }

        WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT, BUFFER_SIZE_LIMIT);
        session.getAttributes().put(USER_ID_ATTR, userId);
        session.getAttributes().put("decorated", decorated);
        hub.registerUser(userId, decorated);
        log.info("User WebSocket connected for user {}, session {}", userId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get(USER_ID_ATTR);
        WebSocketSession decorated = (WebSocketSession) session.getAttributes().get("decorated");
        if (userId != null && decorated != null) {
            hub.unregisterUser(userId, decorated);
        }
        log.info("User WebSocket closed for user {}, session {}", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // read-only connection, discard client messages
    }

    private String extractUserId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;

        String query = uri.getQuery();
        if (query == null) return null;

        String token = null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                token = kv[1];
                break;
            }
        }

        if (token == null || token.isEmpty()) return null;

        try {
            Claims claims = jwtTokenProvider.parseToken(token);
            return claims.getSubject();
        } catch (Exception e) {
            log.warn("Invalid JWT in WebSocket connection: {}", e.getMessage());
            return null;
        }
    }
}
