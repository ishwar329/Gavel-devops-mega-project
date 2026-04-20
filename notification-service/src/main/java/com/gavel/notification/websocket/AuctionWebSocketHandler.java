package com.gavel.notification.websocket;

import com.gavel.notification.hub.NotificationHub;
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
public class AuctionWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AuctionWebSocketHandler.class);
    private static final int SEND_TIME_LIMIT = 5000;
    private static final int BUFFER_SIZE_LIMIT = 64 * 1024;
    private static final String AUCTION_ID_ATTR = "auctionId";

    private final NotificationHub hub;

    public AuctionWebSocketHandler(NotificationHub hub) {
        this.hub = hub;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String auctionId = extractAuctionId(session);
        if (auctionId == null) {
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (Exception ignored) {
            }
            return;
        }

        WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT, BUFFER_SIZE_LIMIT);
        session.getAttributes().put(AUCTION_ID_ATTR, auctionId);
        session.getAttributes().put("decorated", decorated);
        hub.registerAuction(auctionId, decorated);
        log.info("WebSocket connected for auction {}, session {}", auctionId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String auctionId = (String) session.getAttributes().get(AUCTION_ID_ATTR);
        WebSocketSession decorated = (WebSocketSession) session.getAttributes().get("decorated");
        if (auctionId != null && decorated != null) {
            hub.unregisterAuction(auctionId, decorated);
        }
        log.info("WebSocket closed for auction {}, session {}", auctionId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // read-only connection, discard client messages
    }

    private String extractAuctionId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        String path = uri.getPath();
        // expected: /auctions/{auctionId}/subscribe
        String[] parts = path.split("/");
        if (parts.length >= 3 && "auctions".equals(parts[1])) {
            return parts[2];
        }
        return null;
    }
}
