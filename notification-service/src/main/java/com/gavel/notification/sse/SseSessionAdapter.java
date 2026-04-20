package com.gavel.notification.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class SseSessionAdapter implements WebSocketSession {

    private static final Logger log = LoggerFactory.getLogger(SseSessionAdapter.class);

    private final String id = UUID.randomUUID().toString();
    private final String auctionId;
    private final SseEmitter emitter;
    private volatile boolean open = true;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public SseSessionAdapter(String auctionId, SseEmitter emitter) {
        this.auctionId = auctionId;
        this.emitter = emitter;
    }

    @Override
    public void sendMessage(WebSocketMessage<?> message) throws IOException {
        if (!open) return;
        try {
            emitter.send(SseEmitter.event().data(message.getPayload()));
        } catch (Exception e) {
            open = false;
            throw new IOException(e);
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public URI getUri() {
        return URI.create("/auctions/" + auctionId + "/subscribe/sse");
    }

    @Override
    public HttpHeaders getHandshakeHeaders() {
        return new HttpHeaders();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Principal getPrincipal() {
        return null;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return null;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return null;
    }

    @Override
    public String getAcceptedProtocol() {
        return null;
    }

    @Override
    public void setTextMessageSizeLimit(int messageSizeLimit) {}

    @Override
    public int getTextMessageSizeLimit() {
        return 0;
    }

    @Override
    public void setBinaryMessageSizeLimit(int messageSizeLimit) {}

    @Override
    public int getBinaryMessageSizeLimit() {
        return 0;
    }

    @Override
    public List<WebSocketExtension> getExtensions() {
        return List.of();
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() throws IOException {
        open = false;
        emitter.complete();
    }

    @Override
    public void close(CloseStatus status) throws IOException {
        close();
    }
}
