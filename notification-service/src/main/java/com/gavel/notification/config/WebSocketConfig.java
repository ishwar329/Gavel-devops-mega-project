package com.gavel.notification.config;

import com.gavel.notification.websocket.AuctionWebSocketHandler;
import com.gavel.notification.websocket.UserWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AuctionWebSocketHandler auctionWebSocketHandler;
    private final UserWebSocketHandler userWebSocketHandler;

    public WebSocketConfig(AuctionWebSocketHandler auctionWebSocketHandler,
                           UserWebSocketHandler userWebSocketHandler) {
        this.auctionWebSocketHandler = auctionWebSocketHandler;
        this.userWebSocketHandler = userWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(auctionWebSocketHandler, "/auctions/{auctionId}/subscribe")
                .setAllowedOrigins("*");
        registry.addHandler(userWebSocketHandler, "/notifications/subscribe")
                .setAllowedOrigins("*");
    }
}
