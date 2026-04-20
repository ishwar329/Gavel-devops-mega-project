package com.gavel.notification.config;

import com.gavel.notification.websocket.AuctionWebSocketHandler;
import com.gavel.notification.websocket.UserWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WebSocketConfigTest {

    @Test
    void registerWebSocketHandlers_registersHandlers() {
        AuctionWebSocketHandler auctionHandler = mock(AuctionWebSocketHandler.class);
        UserWebSocketHandler userHandler = mock(UserWebSocketHandler.class);
        WebSocketConfig config = new WebSocketConfig(auctionHandler, userHandler);

        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(any(), anyString())).thenReturn(registration);
        when(registration.setAllowedOrigins(anyString())).thenReturn(registration);

        config.registerWebSocketHandlers(registry);

        verify(registry).addHandler(auctionHandler, "/auctions/{auctionId}/subscribe");
        verify(registry).addHandler(userHandler, "/notifications/subscribe");
        verify(registration, times(2)).setAllowedOrigins("*");
    }
}
