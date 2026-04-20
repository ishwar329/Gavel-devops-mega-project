package com.gavel.notification.websocket;

import com.gavel.notification.hub.NotificationHub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionWebSocketHandlerTest {

    @Mock private NotificationHub hub;
    @Mock private WebSocketSession session;

    private AuctionWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AuctionWebSocketHandler(hub);
    }

    @Test
    void afterConnectionEstablished_validUri_registersAuction() throws Exception {
        when(session.getUri()).thenReturn(new URI("/auctions/a-1/subscribe"));
        Map<String, Object> attrs = new HashMap<>();
        when(session.getAttributes()).thenReturn(attrs);

        handler.afterConnectionEstablished(session);

        verify(hub).registerAuction(eq("a-1"), any());
    }

    @Test
    void afterConnectionEstablished_nullUri_closesSession() throws Exception {
        when(session.getUri()).thenReturn(null);

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.BAD_DATA);
        verify(hub, never()).registerAuction(anyString(), any());
    }

    @Test
    void afterConnectionEstablished_invalidPath_closesSession() throws Exception {
        when(session.getUri()).thenReturn(new URI("/invalid"));

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.BAD_DATA);
        verify(hub, never()).registerAuction(anyString(), any());
    }

    @Test
    void afterConnectionClosed_unregistersAuction() throws Exception {
        Map<String, Object> attrs = new HashMap<>();
        WebSocketSession decorated = mock(WebSocketSession.class);
        attrs.put("auctionId", "a-1");
        attrs.put("decorated", decorated);
        when(session.getAttributes()).thenReturn(attrs);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(hub).unregisterAuction("a-1", decorated);
    }

    @Test
    void afterConnectionClosed_noAttributes_doesNotUnregister() throws Exception {
        Map<String, Object> attrs = new HashMap<>();
        when(session.getAttributes()).thenReturn(attrs);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(hub, never()).unregisterAuction(anyString(), any());
    }

    @Test
    void handleTextMessage_doesNothing() throws Exception {
        handler.handleTextMessage(session, new TextMessage("hello"));
        verifyNoInteractions(hub);
    }
}
