package com.gavel.notification.sse;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseBridge {

    private final Set<SseSessionAdapter> adapters = ConcurrentHashMap.newKeySet();

    public void register(SseSessionAdapter adapter) {
        adapters.add(adapter);
    }

    public void unregister(SseSessionAdapter adapter) {
        adapters.remove(adapter);
    }
}
