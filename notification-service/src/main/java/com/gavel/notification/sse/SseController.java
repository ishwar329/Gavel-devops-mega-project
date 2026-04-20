package com.gavel.notification.sse;

import com.gavel.notification.hub.NotificationHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);

    private final NotificationHub hub;
    private final SseBridge sseBridge;

    public SseController(NotificationHub hub, SseBridge sseBridge) {
        this.hub = hub;
        this.sseBridge = sseBridge;
    }

    @GetMapping("/auctions/{auctionId}/subscribe/sse")
    public SseEmitter subscribe(@PathVariable String auctionId) {
        SseEmitter emitter = new SseEmitter(0L);

        SseSessionAdapter adapter = new SseSessionAdapter(auctionId, emitter);
        hub.registerAuction(auctionId, adapter);
        sseBridge.register(adapter);

        emitter.onCompletion(() -> {
            hub.unregisterAuction(auctionId, adapter);
            sseBridge.unregister(adapter);
            log.info("SSE completed for auction {}", auctionId);
        });
        emitter.onTimeout(() -> {
            hub.unregisterAuction(auctionId, adapter);
            sseBridge.unregister(adapter);
            log.info("SSE timed out for auction {}", auctionId);
        });
        emitter.onError(e -> {
            hub.unregisterAuction(auctionId, adapter);
            sseBridge.unregister(adapter);
        });

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("auction_id", auctionId)));
        } catch (Exception e) {
            log.error("Failed to send initial SSE event for auction {}: {}", auctionId, e.getMessage());
        }

        return emitter;
    }
}
