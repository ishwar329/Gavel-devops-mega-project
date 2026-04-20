package com.gavel.notification.websocket;

import com.gavel.notification.hub.NotificationHub;
import com.gavel.shared.security.JwtTokenProvider;
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
class UserWebSocketHandlerTest {

    @Mock private NotificationHub hub;
    private JwtTokenProvider tokenProvider;
    @Mock private WebSocketSession session;

    private UserWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider("this-is-a-test-secret-key-that-is-long-enough-for-hmac");
        handler = new UserWebSocketHandler(hub, tokenProvider);
    }

    @Test
    void afterConnectionEstablished_validToken_registersUser() throws Exception {
        String token = tokenProvider.generateToken("u-1", "alice", "alice@test.com", "buyer");
        when(session.getUri()).thenReturn(new URI("/user/notifications?token=" + token));
        Map<String, Object> attrs = new HashMap<>();
        when(session.getAttributes()).thenReturn(attrs);

        handler.afterConnectionEstablished(session);

        verify(hub).registerUser(eq("u-1"), any());
    }

    @Test
    void afterConnectionEstablished_noQuery_closesSession() throws Exception {
        when(session.getUri()).thenReturn(new URI("/user/notifications"));

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(hub, never()).registerUser(anyString(), any());
    }

    @Test
    void afterConnectionEstablished_noToken_closesSession() throws Exception {
        when(session.getUri()).thenReturn(new URI("/user/notifications?other=value"));

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(hub, never()).registerUser(anyString(), any());
    }

    @Test
    void afterConnectionEstablished_invalidToken_closesSession() throws Exception {
        when(session.getUri()).thenReturn(new URI("/user/notifications?token=invalid-jwt"));

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(hub, never()).registerUser(anyString(), any());
    }

    @Test
    void afterConnectionEstablished_emptyToken_closesSession() throws Exception {
        when(session.getUri()).thenReturn(new URI("/user/notifications?token="));

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(hub, never()).registerUser(anyString(), any());
    }

    @Test
    void afterConnectionEstablished_nullUri_closesSession() throws Exception {
        when(session.getUri()).thenReturn(null);

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(hub, never()).registerUser(anyString(), any());
    }

    @Test
    void afterConnectionClosed_unregistersUser() throws Exception {
        Map<String, Object> attrs = new HashMap<>();
        WebSocketSession decorated = mock(WebSocketSession.class);
        attrs.put("userId", "u-1");
        attrs.put("decorated", decorated);
        when(session.getAttributes()).thenReturn(attrs);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(hub).unregisterUser("u-1", decorated);
    }

    @Test
    void afterConnectionClosed_noAttributes_doesNotUnregister() throws Exception {
        Map<String, Object> attrs = new HashMap<>();
        when(session.getAttributes()).thenReturn(attrs);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(hub, never()).unregisterUser(anyString(), any());
    }

    @Test
    void handleTextMessage_doesNothing() throws Exception {
        handler.handleTextMessage(session, new TextMessage("hello"));
        verifyNoInteractions(hub);
    }
}
